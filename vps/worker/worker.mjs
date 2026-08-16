import { promises as fs } from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const QUEUE_DIR = process.env.QUEUE_DIR || "/queue";
const DONE_DIR = process.env.DONE_DIR || path.join(QUEUE_DIR, "done");
const FAILED_DIR = process.env.FAILED_DIR || path.join(QUEUE_DIR, "failed");
const WORK_DIR = process.env.WORK_DIR || "/work";
const AGENT = process.env.AGENT || "orchestrator";
const PR_BASE_BRANCH = process.env.PR_BASE_BRANCH || "testing";
const MODEL = process.env.MODEL || "";
const ALLOWED = (process.env.ALLOWED_REPOS || "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);
const POLL_MS = Number(process.env.POLL_MS || 5000);

function run(cmd, args, opts = {}) {
  return new Promise((resolve) => {
    const child = spawn(cmd, args, { stdio: "inherit", env: process.env, ...opts });
    child.on("close", (code) => resolve(code ?? 0));
    child.on("error", (err) => {
      console.error(`[worker] ${cmd} error:`, err.message);
      resolve(1);
    });
  });
}

async function exists(p) {
  try {
    await fs.access(p);
    return true;
  } catch {
    return false;
  }
}

async function gh(repo, args) {
  return run("gh", ["--repo", repo, ...args]);
}

async function checkout(repo) {
  const dir = path.join(WORK_DIR, repo.replace("/", "__"));
  await fs.mkdir(dir, { recursive: true });
  const token = process.env.GITHUB_TOKEN || "";
  const url = token
    ? `https://x-access-token:${token}@github.com/${repo}.git`
    : `https://github.com/${repo}.git`;

  if (await exists(path.join(dir, ".git"))) {
    await run("git", ["-C", dir, "fetch", "--all", "--prune"]);
  } else {
    await run("git", ["clone", url, dir]);
  }

  await run("git", ["-C", dir, "checkout", PR_BASE_BRANCH]);
  await run("git", ["-C", dir, "pull", "--ff-only", "origin", PR_BASE_BRANCH]);
  return dir;
}

async function processTask(file) {
  const filePath = path.join(QUEUE_DIR, file);
  let task;
  try {
    task = JSON.parse(await fs.readFile(filePath, "utf8"));
  } catch (e) {
    console.error(`[worker] bad task ${file}:`, e.message);
    await fs.rename(filePath, path.join(FAILED_DIR, file)).catch(() => {});
    return;
  }

  if (ALLOWED.length && !ALLOWED.includes(task.repo)) {
    console.log(`[worker] skipping ${task.repo} (not in ALLOWED_REPOS)`);
    await fs.rename(filePath, path.join(DONE_DIR, file)).catch(() => {});
    return;
  }

  console.log(`[worker] processing issue #${task.issue} in ${task.repo}`);
  try {
    await gh(task.repo, [
      "issue", "comment", String(task.issue),
      "--body", `SDD pipeline started (base branch \`${PR_BASE_BRANCH}\`).`,
    ]);
  } catch {
    // comment is best-effort
  }

  let dir;
  try {
    dir = await checkout(task.repo);
  } catch (e) {
    console.error(`[worker] checkout failed:`, e.message);
    await fs.rename(filePath, path.join(FAILED_DIR, file)).catch(() => {});
    return;
  }

  const prompt = [
    `Execute the SDD pipeline for GitHub issue #${task.issue} in ${task.repo}.`,
    ``,
    `Issue title: ${task.title}`,
    `Issue body:`,
    task.body || "(empty)",
    ``,
    `Use base branch ${PR_BASE_BRANCH}. Follow AGENTS.md and your workflow`,
    `(analyst -> architect -> developer -> reviewer -> code-reviewer) with`,
    `feedback loops. Open a pull request to ${PR_BASE_BRANCH} and merge only`,
    `when reviewer=PASS and code-reviewer=APPROVE. Report the final outcome.`,
  ].join("\n");

  const args = ["run", "--agent", AGENT, "--dir", dir, "--auto"];
  if (MODEL) args.push("--model", MODEL);
  args.push(prompt);

  const code = await run("opencode", args);

  await fs.mkdir(DONE_DIR, { recursive: true });
  await fs.mkdir(FAILED_DIR, { recursive: true });

  if (code === 0) {
    await fs.rename(filePath, path.join(DONE_DIR, file)).catch(() => {});
    try {
      await gh(task.repo, [
        "issue", "comment", String(task.issue),
        "--body", "SDD pipeline completed successfully.",
      ]);
    } catch {}
  } else {
    await fs.rename(filePath, path.join(FAILED_DIR, file)).catch(() => {});
    try {
      await gh(task.repo, [
        "issue", "comment", String(task.issue),
        "--body", `SDD pipeline failed (exit code ${code}). Check worker logs.`,
      ]);
    } catch {}
  }
}

async function main() {
  await fs.mkdir(QUEUE_DIR, { recursive: true });
  await fs.mkdir(DONE_DIR, { recursive: true });
  await fs.mkdir(FAILED_DIR, { recursive: true });

  if (process.env.GIT_USER_NAME) {
    await run("git", ["config", "--global", "user.name", process.env.GIT_USER_NAME]);
  }
  if (process.env.GIT_USER_EMAIL) {
    await run("git", ["config", "--global", "user.email", process.env.GIT_USER_EMAIL]);
  }

  console.log(`[worker] watching ${QUEUE_DIR} (agent=${AGENT}, base=${PR_BASE_BRANCH})`);

  while (true) {
    try {
      const files = (await fs.readdir(QUEUE_DIR))
        .filter((f) => f.endsWith(".json"))
        .sort();
      if (files.length) {
        await processTask(files[0]);
      }
    } catch (e) {
      console.error("[worker] loop error:", e.message);
    }
    await new Promise((r) => setTimeout(r, POLL_MS));
  }
}

main();
