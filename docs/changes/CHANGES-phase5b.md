# Phase 5b — Quiz no longer earns time

Requested change: playing the quiz must not increase screen time.

## Removed
- Bonus minutes for correct answers, the per-set reward and the daily cap (Settings fields, rules, storage keys, strings).
- The special "free time while rewards are active" screen-time rule.

## Now
- The quiz is plain screen time: counted as **Images** time, blocked by the daily limit, the Images limit and bedtime,
  and shows the 5-minute / 1-minute wind-down warnings.
- Opening it from Home uses the same check as Images mode.
- Settings keeps one quiz option: show / hide the Quiz card.
- Unchanged: the parent's remote `bonus` in `settings.json` (extra minutes granted by the parent) and the dashboard line for it.

## Tests: 82 (four reward-only tests removed; all remaining pass)
