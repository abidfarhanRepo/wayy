#!/usr/bin/env bash
set -euo pipefail
# runs placeholder agents for 6 hours. Replace run_agent() with real agent commands.
DURATION=$((6*60*60))
END=$(( $(date +%s) + DURATION ))
LOGFILE="$(dirname "$0")/run_agents_6h.log"

run_agent() {
  local id="$1"
  echo "[$(date --iso-8601=seconds)] starting agent $id" >> "$LOGFILE"
  # TODO: replace the following sleep with the actual agent invocation, e.g.:
  # python3 agents/agent_$id.py >> "$LOGFILE" 2>&1 &
  sleep 1
  echo "[$(date --iso-8601=seconds)] finished agent $id" >> "$LOGFILE"
}

echo "Starting agents loop for $DURATION seconds (6 hours) - log: $LOGFILE"
while [ "$(date +%s)" -lt "$END" ]; do
  # Launch agents sequentially or in parallel as needed; update the IDs as appropriate
  for id in 1 2 3; do
    run_agent "$id" &
    sleep 2
  done
  wait
  # small pause between iterations
  sleep 10
done

echo "Completed 6-hour run at $(date --iso-8601=seconds)" >> "$LOGFILE"
