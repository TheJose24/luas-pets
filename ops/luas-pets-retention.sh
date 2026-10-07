#!/usr/bin/env bash
set -euo pipefail

RELEASE_ROOT="/proyects/jenkins/releases/luas-pets"
BACKUP_ROOT="/proyects/backups/luas-pets-db"

KEEP_RELEASES=5
KEEP_ROLLBACKS=2
KEEP_BACKUPS=7

MODE="dry-run"

case "${1:-}" in
  "")
    ;;
  --apply)
    MODE="apply"
    ;;
  *)
    echo "Usage: $0 [--apply]" >&2
    exit 2
    ;;
esac

if [[ ! -d "$RELEASE_ROOT" ]]; then
  echo "ERROR: release root does not exist: $RELEASE_ROOT" >&2
  exit 1
fi

if [[ ! -d "$BACKUP_ROOT" ]]; then
  echo "ERROR: backup root does not exist: $BACKUP_ROOT" >&2
  exit 1
fi

production_image="$(cat "$RELEASE_ROOT/production-image" 2>/dev/null || true)"
latest_image="$(cat "$RELEASE_ROOT/latest-image" 2>/dev/null || true)"
running_image="$(
  docker inspect luas-pets-app \
    --format '{{.Config.Image}}' \
    2>/dev/null || true
)"

if [[ -z "$production_image" ]]; then
  echo "ERROR: production-image metadata is missing." >&2
  exit 1
fi

if [[ -z "$latest_image" ]]; then
  echo "ERROR: latest-image metadata is missing." >&2
  exit 1
fi

if [[ -z "$running_image" ]]; then
  echo "ERROR: luas-pets-app is not running or cannot be inspected." >&2
  exit 1
fi

echo "===== LUAS PETS RETENTION ====="
echo "Mode: $MODE"

echo
echo "Protected:"
echo "  production: $production_image"
echo "  latest:     $latest_image"
echo "  running:    $running_image"

delete_release() {
  local dir="$1"
  local image="$2"

  echo "DELETE release $(basename "$dir") $image"

  if [[ "$MODE" == "apply" ]]; then
    if [[ -n "$image" ]] && docker image inspect "$image" >/dev/null 2>&1; then
      docker image rm "$image"
    fi

    rm -rf -- "$dir"
  fi
}

delete_rollback() {
  local tag="$1"

  echo "DELETE $tag"

  if [[ "$MODE" == "apply" ]]; then
    docker image rm "$tag"
  fi
}

delete_backup() {
  local backup="$1"

  echo "DELETE $backup"

  if [[ "$MODE" == "apply" ]]; then
    rm -- "$backup"
  fi
}

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
    delete_release "$dir" "$image"
  fi
done

echo
echo "===== ROLLBACK TAGS ====="

mapfile -t rollback_tags < <(
  docker images \
    --format '{{.Repository}}:{{.Tag}}' \
    | grep -E '^luas-pets-app:rollback-[0-9]+-[0-9]{14}$' \
    | awk -F- '{print $NF "|" $0}' \
    | sort -r \
    | cut -d'|' -f2- \
    || true
)

for i in "${!rollback_tags[@]}"; do
  tag="${rollback_tags[$i]}"

  if (( i < KEEP_ROLLBACKS )); then
    echo "KEEP   $tag"
  else
    delete_rollback "$tag"
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
    delete_backup "$backup"
  fi
done

echo

if [[ "$MODE" == "apply" ]]; then
  echo "APPLY COMPLETE."
else
  echo "DRY-RUN COMPLETE — nothing was deleted."
fi