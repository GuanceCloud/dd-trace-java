#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: $0 <base-agent.jar> <queue-fix.jar> <output-agent.jar>" >&2
  exit 2
fi

base_agent=$(realpath "$1")
queue_fix=$(realpath "$2")
output_agent=$(realpath -m "$3")

if [[ ! -f "$base_agent" || ! -f "$queue_fix" ]]; then
  echo "base agent or queue-fix JAR does not exist" >&2
  exit 2
fi

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

classes_dir="$work_dir/classes"
payload_dir="$work_dir/payload"
mkdir -p "$classes_dir" "$payload_dir"

unzip -q "$queue_fix" -d "$classes_dir"

class_count=0
while IFS= read -r -d '' class_file; do
  relative_path=${class_file#"$classes_dir/"}
  classdata_path="$payload_dir/inst/${relative_path%.class}.classdata"
  mkdir -p "$(dirname "$classdata_path")"
  cp "$class_file" "$classdata_path"
  class_count=$((class_count + 1))
done < <(
  find "$classes_dir/datadog/trace/agent" -type f \
    \( -name 'PendingTraceBuffer*.class' -o -name 'TraceProcessingWorker*.class' \) \
    -print0
)

if [[ $class_count -ne 9 ]]; then
  echo "expected 9 patched classes, found $class_count" >&2
  exit 1
fi

while IFS= read -r -d '' classdata_file; do
  read -r major_high major_low < <(od -An -t u1 -j 6 -N 2 "$classdata_file")
  class_major=$((major_high * 256 + major_low))
  if [[ $class_major -ne 51 ]]; then
    echo "$(basename "$classdata_file") has class major $class_major, expected 51" >&2
    exit 1
  fi
  if strings "$classdata_file" | grep -Fq 'org/slf4j/'; then
    echo "$(basename "$classdata_file") contains an unrelocated SLF4J reference" >&2
    exit 1
  fi
done < <(find "$payload_dir" -type f -name '*.classdata' -print0)

mkdir -p "$(dirname "$output_agent")"
cp "$base_agent" "$output_agent"

zip -q -d "$output_agent" \
  'inst/datadog/trace/agent/core/PendingTraceBuffer*.classdata' \
  'inst/datadog/trace/agent/common/writer/TraceProcessingWorker*.classdata'

(
  cd "$payload_dir"
  zip -q -r "$output_agent" inst
)

pending_entry='inst/datadog/trace/agent/core/PendingTraceBuffer$DelayingPendingTraceBuffer.classdata'
processor_entry='inst/datadog/trace/agent/common/writer/TraceProcessingWorker.classdata'

for entry in "$pending_entry" "$processor_entry"; do
  if ! unzip -Z1 "$output_agent" | grep -Fxq "$entry"; then
    echo "missing patched entry: $entry" >&2
    exit 1
  fi
  if ! unzip -p "$output_agent" "$entry" | strings | grep -Fq 'java/util/concurrent/ArrayBlockingQueue'; then
    echo "patched entry does not reference ArrayBlockingQueue: $entry" >&2
    exit 1
  fi
  if unzip -p "$output_agent" "$entry" | strings | grep -Fq 'MpscBlockingConsumerArrayQueue'; then
    echo "patched entry still references MpscBlockingConsumerArrayQueue: $entry" >&2
    exit 1
  fi
done

sha256sum "$output_agent" > "$output_agent.sha256"
echo "created $output_agent"
cat "$output_agent.sha256"
