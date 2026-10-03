"""Minimal OpenAI-compatible chat server for the real-agent test.

Usage: fake_llm.py PORT LOGFILE TOOL_NAME
Turn 1 answers with a tool call to TOOL_NAME, turn 2 echoes the tool result.
"""
import json, sys, http.server, threading, os

LOG = sys.argv[2]
TOOL = sys.argv[3]

class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def _send_json(self, obj):
        b = json.dumps(obj).encode(); self.send_response(200)
        self.send_header("Content-Type","application/json"); self.send_header("Content-Length",str(len(b)))
        self.end_headers(); self.wfile.write(b)
    def do_GET(self):
        if self.path.endswith("/models"):
            return self._send_json({"object":"list","data":[{"id":"fake-model","object":"model"}]})
        self._send_json({})
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length",0))) or b"{}")
        with open(LOG,"a") as f: f.write(json.dumps({"path":self.path,"tools":[t["function"]["name"] for t in body.get("tools",[])],"roles":[m["role"] for m in body["messages"]],"last":body["messages"][-1]}) + "\n")
        msgs = body["messages"]
        tool_result = next((m for m in reversed(msgs) if m["role"]=="tool"), None)
        if tool_result is None:
            msg = {"role":"assistant","content":None,"tool_calls":[{"id":"call_1","type":"function","function":{"name":TOOL,"arguments":json.dumps({"message":"from-agent"})}}]}
            fin = "tool_calls"
        else:
            msg = {"role":"assistant","content":"RESULT=" + str(tool_result["content"])}
            fin = "stop"
        if body.get("stream"):
            self.send_response(200); self.send_header("Content-Type","text/event-stream"); self.end_headers()
            def ev(d): self.wfile.write(("data: "+json.dumps(d)+"\n\n").encode()); self.wfile.flush()
            delta = {"role":"assistant"}
            if msg.get("content"): delta["content"]=msg["content"]
            if msg.get("tool_calls"):
                delta["tool_calls"]=[{"index":0,**msg["tool_calls"][0]}]
            ev({"id":"c1","object":"chat.completion.chunk","model":"fake-model","choices":[{"index":0,"delta":delta,"finish_reason":None}]})
            ev({"id":"c1","object":"chat.completion.chunk","model":"fake-model","choices":[{"index":0,"delta":{},"finish_reason":fin}]})
            self.wfile.write(b"data: [DONE]\n\n"); self.wfile.flush()
        else:
            self._send_json({"id":"c1","object":"chat.completion","model":"fake-model","choices":[{"index":0,"message":msg,"finish_reason":fin}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}})

http.server.ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), H).serve_forever()
