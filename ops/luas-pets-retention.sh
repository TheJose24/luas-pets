#!/usr/bin/env bash
set -euo pipefail

RELEASE_ROOT="/proyects/jenkins/releases/luas-pets"
BACKUP_ROOT="/proyects/backups/luas-pets-db"

KEEP_RELEASES=5
KEEP_ROLLBACKS=2
KEEP_BACKUPS=7

echo "===== LUAS PETS RETENTION DRY-RUN ====="

production_image="$(cat "$RELEASE_ROOT/production-image" 2>/dev/null || true)"
latest_image="$(cat "$RELEASE_ROOT/latest-image" 2>/dev/null || true)"
running_image="$(docker inspect luas-pets-app --format '{{.Config.Image}}' 2>/dev/null || true)"

echo
echo "Protected:"
echo "  production: $production_image"
echo "  latest:     $latest_image"
echo "  running:    $running_image"

echo
echo "===== RELEASE DIRECTORIES ====="

mapfile -t release_dirs < <(
  find "$RELEASE_ROOT" \
    -mindepth 1 \
    -maxdepth 1 \
    -type d \
    -printf '%T@ %p\n' \
    | sort -nr \
    | awk '{print $2}'
)

for i in "${!release_dirs[@]}"; do
  dir="${release_dirs[$i]}"
  sha="$(basename "$dir")"
  image="$(cat "$dir/image" 2>/dev/null || true)"

  if (( i < KEEP_RELEASES )); then
    echo "KEEP   release $sha $image"
  elif [[ "$image" == "$production_image" ||
          "$image" == "$latest_image" ||
          "$image" == "$running_image" ]]; then
    echo "KEEP   protected release $sha $image"
  else
    echo "DELETE release $sha $image"
  fi
done

echo
echo "===== ROLLBACK TAGS ====="

mapfile -t rollback_tags < <(
  docker images \
    --format '{{.Repository}}:{{.Tag}}' \
    | grep '^luas-pets-app:rollback-' \
    | sort -r \
    || true
)

for i in "${!rollback_tags[@]}"; do
  tag="${rollback_tags[$i]}"

  if (( i < KEEP_ROLLBACKS )); then
    echo "KEEP   $tag"
  else
    echo "DELETE $tag"
  fi
done

echo
echo "===== DATABASE BACKUPS ====="

mapfile -t backups < <(
  find "$BACKUP_ROOT" \
    -maxdepth 1 \
    -type f \
    -name 'luas-pets-pre-deploy-*.sql' \
    -printf '%T@ %p\n' \
    | sort -nr \
    | awk '{print $2}'
)

for i in "${!backups[@]}"; do
  backup="${backups[$i]}"

  if (( i < KEEP_BACKUPS )); then
    echo "KEEP   $backup"
  else
    echo "DELETE $backup"
  fi
done

echo
echo "DRY-RUN COMPLETE — nothing was deleted."
