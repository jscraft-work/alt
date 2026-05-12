#!/usr/bin/env bash
# Fake openclaw/nanobot subprocess used by LlmSubprocessAdapterTest
# and LlmNewsAssessmentAdapterTest.
# Behavior is selected via the FAKE_MODE environment variable.
# All CLI args are accepted; if FAKE_DUMP_FILE env is set, the argv is
# written there (one arg per line) for the test to inspect.

set -u

mode="${FAKE_MODE:-success}"

if [[ -n "${FAKE_DUMP_FILE:-}" ]]; then
    : > "$FAKE_DUMP_FILE"
    for a in "$@"; do
        printf '%s\n' "$a" >> "$FAKE_DUMP_FILE"
    done
fi

case "$mode" in
    success_openclaw)
        printf '{"payloads":[{"text":"{\\"cycleStatus\\":\\"HOLD\\",\\"summary\\":\\"ok\\"}"}]}'
        exit 0
        ;;
    success_nanobot)
        printf '\xf0\x9f\x90\x88 nanobot\nhello-world'
        exit 0
        ;;
    timeout)
        sleep 999
        exit 0
        ;;
    auth_error)
        echo "Token refresh failed: refresh_token_reused" 1>&2
        exit 1
        ;;
    non_zero_exit)
        printf '{"payloads":[{"text":"partial"}]}'
        exit 1
        ;;
    empty)
        exit 0
        ;;
    invalid_output)
        printf '{"payloads":[{"text":"Error calling provider: response failed"}]}'
        exit 0
        ;;
    news_useful_bool)
        printf '{"payloads":[{"text":"{\\"useful\\": true}"}]}'
        exit 0
        ;;
    news_not_useful_bool)
        printf '{"payloads":[{"text":"{\\"useful\\": false}"}]}'
        exit 0
        ;;
    news_useful_string)
        printf '{"payloads":[{"text":"{\\"useful\\": \\"true\\"}"}]}'
        exit 0
        ;;
    news_not_useful_string_caps)
        printf '{"payloads":[{"text":"{\\"useful\\": \\"False\\"}"}]}'
        exit 0
        ;;
    news_preamble)
        printf '{"payloads":[{"text":"Sure, here is my analysis: {\\"useful\\": true}"}]}'
        exit 0
        ;;
    news_not_json)
        printf '{"payloads":[{"text":"not even close to json"}]}'
        exit 0
        ;;
    news_missing_field)
        printf '{"payloads":[{"text":"{\\"summary\\": \\"...\\"}"}]}'
        exit 0
        ;;
    news_numeric)
        printf '{"payloads":[{"text":"{\\"useful\\": 1}"}]}'
        exit 0
        ;;
    news_useful_nanobot)
        printf '\xf0\x9f\x90\x88 nanobot\n{"useful": true}'
        exit 0
        ;;
    *)
        echo "unknown FAKE_MODE: $mode" 1>&2
        exit 99
        ;;
esac
