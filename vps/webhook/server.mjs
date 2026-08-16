import http from "node:http";
import crypto from "node:crypto";
import { promises as fs } from "node:fs";
import path from "node:path";

const PORT = Number(process.env.PORT || 8080);
const SECRET = process.env.WEBHOOK_SECRET || "";
const TRIGGER = (process.env.TRIGGER_COMMAND || "/sdd").trim();
const TRIGGER_ON_OPEN =
  (process.env.TRIGGER_ON_OPEN || "false").toLowerCase() === "true";
const QUEUE_DIR = process.env.QUEUE_DIR || "/queue";

function verifySignature(raw, signature) {
  if (!SECRET) return true;
  if (!signature) return false;
  const expected =
    "sha256=" + crypto.createHmac("sha256", SECRET).update(raw).digest("hex");
  const a = Buffer.from(signature);
  const b = Buffer.from(expected);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

async function enqueue(task) {
  await fs.mkdir(QUEUE_DIR, { recursive: true });
  const name = `${Date.now()}-${Math.random().toString(36).slice(2)}.json`;
  await fs.writeFile(path.join(QUEUE_DIR, name), JSON.stringify(task, null, 2));
  return name;
}

const server = http.createServer(async (req, res) => {
  const route = (req.url || "").split("?")[0];

  if (req.method === "GET" && route === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ ok: true }));
    return;
  }

  if (req.method !== "POST" || route !== "/webhook") {
    res.writeHead(404).end();
    return;
  }

  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  const raw = Buffer.concat(chunks);
  const signature = req.headers["x-hub-signature-256"] || "";

  if (!verifySignature(raw, signature)) {
    res.writeHead(401, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "invalid signature" }));
    return;
  }

  let payload;
  try {
    payload = JSON.parse(raw.toString("utf8"));
  } catch {
    res.writeHead(400, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "invalid json" }));
    return;
  }

  const event = req.headers["x-github-event"] || "";
  const action = payload.action || "";
  const repo = payload.repository ? payload.repository.full_name : "";
  const issue = payload.issue || payload.pull_request || null;

  if (!repo || !issue) {
    res.writeHead(200).end();
    return;
  }

  const task = {
    repo,
    issue: issue.number,
    title: issue.title || "",
    body: issue.body || "",
    createdAt: new Date().toISOString(),
  };

  if (event === "issues" && action === "opened" && TRIGGER_ON_OPEN) {
    await enqueue(task);
  } else if (event === "issue_comment" && action === "created") {
    const comment = (payload.comment && payload.comment.body) || "";
    if (comment.includes(TRIGGER)) {
      task.comment = comment;
      await enqueue(task);
    }
  }

  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ ok: true }));
});

server.listen(PORT, () => {
  console.log(
    `[webhook] listening on :${PORT} (trigger="${TRIGGER}", on_open=${TRIGGER_ON_OPEN})`,
  );
});
