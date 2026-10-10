#!/usr/bin/env bash
set -euo pipefail
export STONEBANNER_PERSISTENCE_SCENARIO=construction
exec bash "$(dirname "$0")/verify-persistence-restart.sh" "$@"
