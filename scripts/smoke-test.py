"""Walks the main flow through the gateway against running services: create a user, set
skills and profile, fetch jobs, match, run applying, show the results.

    python scripts/smoke-test.py
"""
import json, sys, time, uuid, urllib.request, urllib.error

sys.stdout.reconfigure(encoding="utf-8")

BASE = "http://localhost:8080"

def call(method, path, body=None, user=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if user:
        req.add_header("X-User-Id", user)
    try:
        with urllib.request.urlopen(req) as r:
            text = r.read().decode()
            return r.status, json.loads(text) if text else None
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        return e.code, (json.loads(text) if text.startswith("{") else text)

def wait(path, user=None):
    for _ in range(120):
        code, run = call("GET", path, user=user)
        if run.get("status") != "RUNNING":
            return run
        time.sleep(2)
    raise SystemExit("timed out on " + path)

def step(name, code, body=None):
    print(f"{'OK ' if code < 400 else 'ERR'} {code}  {name}")
    if code >= 400:
        print("     ", body)

code, user = call("POST", "/api/v1/dev/users", {"email": f"satya+{uuid.uuid4().hex[:6]}@example.com"})
step("create user", code, user)
uid = user["id"]

code, body = call("PUT", "/api/v1/me/skills", {"skills": [
    {"name": n, "years": 2} for n in ["java", "spring boot", "mysql", "redis", "docker", "rest", "git"]]}, uid)
step("set skills", code, body)
code, body = call("PUT", "/api/v1/me/profile", {
    "fullName": "Satya", "location": "Berlin", "currentTitle": "Backend Developer",
    "experienceYears": 3, "expectedSalary": 1200000, "noticePeriodDays": 30,
    "targetRoles": ["Backend Developer", "Software Engineer", "Java Developer"],
    "preferredLocations": ["Berlin", "Munich", "Remote"],
    "excludedCompanies": [], "excludedKeywords": ["senior director"],
    "remoteOk": True, "minMatchScore": 40, "dailyApplyLimit": 10, "autoApplyEnabled": True}, uid)
step("update profile", code, body)


code, run = call("POST", "/api/v1/admin/jobs/fetch-runs")
step("start job fetch", code, run)
if code == 202:
    run = wait("/api/v1/admin/jobs/fetch-runs/" + run["id"])
    print("     fetch:", run["status"], "-", run.get("message") or [s.get("message") for s in run.get("sources", [])])

code, page = call("GET", "/api/v1/jobs?q=java&limit=3")
step("search jobs 'java'", code, page)
for j in page.get("items", []):
    print("     -", j["title"], "@", j["company"], "|", j["location"])

code, run = call("POST", "/api/v1/me/matches/runs", user=uid)
step("start match run", code, run)
run = wait("/api/v1/me/matches/runs/" + run["id"], uid)
print("     match run:", {k: run.get(k) for k in ("status", "jobsConsidered", "matchesCreated", "excluded", "belowThreshold", "message")})

code, page = call("GET", "/api/v1/me/matches?limit=5", user=uid)
step("top matches", code, page)
for m in page.get("items", []):
    print(f"     {m['score']:>3}  {m.get('title')} @ {m.get('company')} | {m.get('location')}")

code, run = call("POST", "/api/v1/me/applications/runs", user=uid)
step("start apply run", code, run)
run = wait("/api/v1/me/applications/runs/" + run["id"], uid)
print("     apply run:", {k: v for k, v in run.items() if k not in ("id", "userId")})

code, stats = call("GET", "/api/v1/me/applications/stats", user=uid)
step("application stats", code, stats)
print("     ", stats)
code, needs = call("GET", "/api/v1/me/applications/needs-you", user=uid)
step("needs-you queue", code, needs)
items = needs if isinstance(needs, list) else needs.get("items", [])
for n in items[:3]:
    app = n.get("application", n)
    print(f"     - {app.get('title')} ({app.get('riskBand')}) | {n.get('reason') or app.get('needsYouReason')}")
