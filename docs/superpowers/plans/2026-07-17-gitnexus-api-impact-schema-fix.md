# GitNexus `api_impact` Schema Fix Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (or implement task-by-task). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop OpenCode from rejecting GitNexus MCP tool `gitnexus_api_impact` with `tool parameter root must be an object type (root schema is an anyOf/oneOf union with a non-object branch)`.

**Architecture:** Config-only fix in OpenCode MCP settings. Pin GitNexus to a version whose `api_impact` `inputSchema` has no top-level `anyOf`/`oneOf`/`allOf`. No application code changes in `gpstest`.

**Tech Stack:** OpenCode MCP (`opencode.json`), npm `gitnexus` package, Windows PowerShell.

## Root Cause (confirmed)

GitNexus **1.6.9** (`npm latest`) advertises:

```js
// dist/mcp/tools.js — api_impact
inputSchema: {
  type: 'object',
  properties: { route, file, method, repo },
  required: [],
  anyOf: [{ required: ['route'] }, { required: ['file'] }],
}
```

Anthropic / Bedrock / some OpenCode schema converters reject top-level `anyOf` on tool schemas. OpenCode surfaces the user-visible error above.

| Source                    | Status                                                    |
| ------------------------- | --------------------------------------------------------- |
| GitNexus #2487 + PR #2489 | Fixed on `main` (remove top-level `anyOf`)                |
| GitNexus #2525            | Open (OpenCode + 1.6.9)                                   |
| OpenCode #35516           | Anthropic path lacks MCP schema sanitization              |
| npm `latest`              | **1.6.9** (broken)                                        |
| npm `rc`                  | **1.6.10-rc.44** (no `anyOf`/`oneOf`/`allOf` in tools.js) |

## Global Constraints

- Prefer config pin over patching npx cache.
- Do not disable GitNexus unless pin fails.
- Do not invent a stable `1.6.10` until npm publishes it; use RC or wait.
- After MCP change, OpenCode must be restarted (or MCP server reloaded) for the new process to start.
- Secondary: this repo has **no GitNexus index** yet; graph tools need `gitnexus analyze` later (out of scope for schema error).

---

### Task 1: Pin GitNexus MCP to fixed RC

**Files:**

- Modify: `C:\Users\zsl43\.config\opencode\opencode.json`

**Current:**

```json
"gitnexus": {
  "command": ["npx", "-y", "gitnexus", "mcp"],
  "enabled": true,
  "type": "local"
}
```

- [ ] **Step 1: Edit MCP command to pin RC**

Replace command with:

```json
"gitnexus": {
  "command": ["npx", "-y", "gitnexus@1.6.10-rc.44", "mcp"],
  "enabled": true,
  "type": "local"
}
```

- [ ] **Step 2: Verify package resolves and schema is clean**

```powershell
npx -y gitnexus@1.6.10-rc.44 --version
# expect: 1.6.10-rc.44 (or equivalent version output)

# Locate installed package tools.js (npx cache) and confirm no anyOf on api_impact:
# Search under %LOCALAPPDATA%\npm-cache\_npx for gitnexus\dist\mcp\tools.js
# Expect: zero matches for anyOf / oneOf / allOf in that file
```

- [ ] **Step 3: Restart OpenCode MCP**

Fully quit and relaunch OpenCode (or use UI/CLI to reload MCP servers) so the new `npx ... gitnexus@1.6.10-rc.44 mcp` process starts.

- [ ] **Step 4: Smoke-test MCP load**

In a new session:

1. Confirm GitNexus tools list loads without schema rejection.
2. Call a simple tool, e.g. `list_repos` (or skill workflow that lists repos).
3. Confirm **`api_impact` is no longer rejected at schema registration** (the original error must not appear when tools are advertised).

**Expected:** No `tool parameter root must be an object type` for `gitnexus_api_impact`.

**Note:** Calling `api_impact` may still return a _runtime_ error if neither `route` nor `file` is provided, or if no API routes are indexed — that is separate from the schema bug.

---

### Task 2: Fallback if RC pin fails

Only if Task 1 fails (npx install error, MCP still fails to start, or schema still rejected):

- [ ] **Step 1: Temporary disable**

```json
"gitnexus": {
  "command": ["npx", "-y", "gitnexus@1.6.10-rc.44", "mcp"],
  "enabled": false,
  "type": "local"
}
```

Restart OpenCode. Error disappears because the tool is not registered.

- [ ] **Step 2: Optional model workaround**

If GitNexus must stay on 1.6.9: prefer non-Anthropic models that tolerate `anyOf` (provider-dependent). Not preferred long-term.

- [ ] **Step 3: Do not use**

- Patching files under npx cache (ephemeral, overwritten by next `-y` install).
- Waiting only on OpenCode #35516 without pinning GitNexus.

---

### Task 3: Optional follow-ups (out of scope for the error, do if user wants GitNexus graph features)

- [ ] When npm publishes stable **>1.6.9**, repin:

```json
"command": ["npx", "-y", "gitnexus@1.6.10", "mcp"]
```

(or drop the version pin if `latest` is confirmed fixed)

- [ ] Index this repo for real impact analysis:

```powershell
npx -y gitnexus@1.6.10-rc.44 analyze
npx -y gitnexus@1.6.10-rc.44 status
```

- [ ] For symbol blast radius use `impact` / skill `gitnexus-impact-analysis`, not `api_impact` (API-route oriented).

---

## Verification Checklist

```
- [ ] opencode.json pins gitnexus@1.6.10-rc.44 (or newer fixed release)
- [ ] OpenCode restarted / MCP reloaded
- [ ] No schema error for gitnexus_api_impact on tool load
- [ ] list_repos (or equivalent) works
- [ ] Document: RC pin is temporary until stable >1.6.9 ships
```

## Rollback

Revert `opencode.json` gitnexus command to:

```json
"command": ["npx", "-y", "gitnexus", "mcp"]
```

and restart OpenCode.
