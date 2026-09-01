# GCP Teardown Guide

Steps to fully decommission the game-server deployment from Google Cloud Run.

## GCP Resources

| Resource | Name |
|---|---|
| Project | `crafty-dynamics-495319-e2` |
| Region | `us-central1` |
| Cloud Run service | `game-server` |
| Cloud SQL instance | `codenza-games-db` |
| Artifact Registry repo | `game-server` |
| Service accounts | `game-server-sa`, `github-actions` |
| Secret Manager secrets | `game-server-db-user`, `game-server-db-password`, `game-server-db-name`, `newrelic-license-key` |

---

## Step 1 — Disable CI/CD

Remove `.github/workflows/deploy.yml` (or delete its `on: push` trigger) so that future merges to `main` no longer build or deploy to GCP.

The PR build (`.github/workflows/pr.yml`) has no GCP exposure and can be kept as-is.

---

## Step 2 — Delete the Cloud Run Service

```bash
gcloud run services delete game-server \
  --region=us-central1 \
  --project=crafty-dynamics-495319-e2
```

This stops all billing for the Cloud Run service immediately.

---

## Step 3 — Delete the Cloud SQL Instance

This is the primary cost driver. Deleting it is irreversible — export a dump first if you need the data.

```bash
# Optional: export a dump before deleting
gcloud sql export sql codenza-games-db \
  gs://your-bucket/codenza-games-db-final.sql \
  --database=<db-name> \
  --project=crafty-dynamics-495319-e2

# Delete the instance
gcloud sql instances delete codenza-games-db \
  --project=crafty-dynamics-495319-e2
```

---

## Step 4 — Delete Artifact Registry Images

```bash
# Delete all images in the repo
gcloud artifacts repositories delete game-server \
  --location=us-central1 \
  --project=crafty-dynamics-495319-e2
```

Or delete individual tags if you want to keep the repository:

```bash
gcloud artifacts docker images delete \
  us-central1-docker.pkg.dev/crafty-dynamics-495319-e2/game-server/game-server \
  --delete-tags \
  --project=crafty-dynamics-495319-e2
```

---

## Step 5 — Delete Secret Manager Secrets

```bash
for SECRET in game-server-db-user game-server-db-password game-server-db-name newrelic-license-key; do
  gcloud secrets delete $SECRET \
    --project=crafty-dynamics-495319-e2
done
```

---

## Step 6 — Remove Service Accounts

```bash
gcloud iam service-accounts delete \
  game-server-sa@crafty-dynamics-495319-e2.iam.gserviceaccount.com \
  --project=crafty-dynamics-495319-e2

gcloud iam service-accounts delete \
  github-actions@crafty-dynamics-495319-e2.iam.gserviceaccount.com \
  --project=crafty-dynamics-495319-e2
```

---

## Step 7 — Remove Workload Identity Federation

If the WIF pool was created specifically for this project:

```bash
# List pools to find the pool ID
gcloud iam workload-identity-pools list \
  --location=global \
  --project=crafty-dynamics-495319-e2

# Delete the provider first, then the pool
gcloud iam workload-identity-pools providers delete <PROVIDER_ID> \
  --workload-identity-pool=<POOL_ID> \
  --location=global \
  --project=crafty-dynamics-495319-e2

gcloud iam workload-identity-pools delete <POOL_ID> \
  --location=global \
  --project=crafty-dynamics-495319-e2
```

---

## Step 8 — Remove GitHub Secrets

In the GitHub repository settings, remove:

- `WIF_PROVIDER`
- `REPO_ACCESS_TOKEN` (shared with `pr.yml` — only remove if no longer needed for the PR build's game-database checkout)

---

## Step 9 — Shut Down the GCP Project (Optional)

If no other services are running under `crafty-dynamics-495319-e2`, shutting down the project cleans up everything and guarantees no residual billing:

```bash
gcloud projects delete crafty-dynamics-495319-e2
```

Projects enter a 30-day recovery window before permanent deletion.
