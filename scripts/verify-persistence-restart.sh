#!/usr/bin/env bash
# Real save/stop/restart acceptance on an isolated world; never starts the user's run/world.
set -euo pipefail
cd "$(dirname "$0")/.."
probe_scenario=${STONEBANNER_PERSISTENCE_SCENARIO:-forestry}
case "$probe_scenario" in
  forestry) probe_dir=run-acceptance-persistence; probe_world=stonebanner-persistence-probe; probe_port=25576; probe_seed=11251 ;;
  construction) probe_dir=run-acceptance-construction; probe_world=stonebanner-construction-probe; probe_port=25577; probe_seed=11252 ;;
  *) echo "Unknown persistence scenario: $probe_scenario" >&2; exit 2 ;;
esac
if [[ ! -f "$probe_dir/eula.txt" ]] || ! grep -Eq $'^eula=true\r?$' "$probe_dir/eula.txt"; then
  echo "Accept Minecraft EULA in $probe_dir/eula.txt before running this isolated probe." >&2
  exit 2
fi
exec 8> "$probe_dir/.probe.lock"
if ! flock -n 8; then
  echo "Another persistence probe is already running." >&2
  exit 2
fi
if [[ -e "$probe_dir/persistence-probe.properties" || -d "$probe_dir/$probe_world" ]]; then
  echo "Probe requires a fresh directory. Preserve the previous evidence and world before starting again." >&2
  exit 2
fi
cat > "$probe_dir/server.properties" <<PROPERTIES
server-ip=127.0.0.1
server-port=$probe_port
online-mode=false
level-name=$probe_world
level-type=minecraft:flat
level-seed=$probe_seed
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","features":false,"lakes":false}
generate-structures=false
max-players=1
view-distance=3
simulation-distance=3
enable-rcon=false
enable-query=false
PROPERTIES
for probe_stage in prepare resume completed; do
  rm -f "$probe_dir/probe-$probe_stage.passed"
  if [[ -n "${STONEBANNER_GRADLE:-}" ]]; then
    probe_gradle=("$STONEBANNER_GRADLE")
  else
    probe_gradle=(bash ./gradlew)
  fi
  if ! timeout "${STONEBANNER_PROBE_TIMEOUT:-600}" "${probe_gradle[@]}" --no-daemon -Pstonebanner.qaPersistence "-Pstonebanner.persistenceScenario=$probe_scenario" "-Pstonebanner.persistenceStage=$probe_stage" runServer "$@" > "$probe_dir/$probe_stage.log" 2>&1; then
    echo "Gradle/server stage $probe_stage failed or timed out: inspect $probe_dir/$probe_stage.log" >&2
    exit 1
  fi
  if [[ ! -s "$probe_dir/probe-$probe_stage.passed" ]] || ! grep -Fq "STONEBANNER_PERSISTENCE_PASS $probe_stage" "$probe_dir/$probe_stage.log" || grep -Eq 'STONEBANNER_PERSISTENCE_FAIL' "$probe_dir/$probe_stage.log"; then
    echo "Persistence stage $probe_stage failed: inspect $probe_dir/$probe_stage.log" >&2
    exit 1
  fi
  cat "$probe_dir/probe-$probe_stage.passed"
done
echo 'Dedicated-server persistence restart: all three stages passed.'
