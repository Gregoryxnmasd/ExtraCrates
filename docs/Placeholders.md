# ExtraCrates Placeholders

This plugin exposes placeholders through PlaceholderAPI (identifier: `extracrates`).
Each placeholder is safe to use even when the player is not opening a crate: in those
cases the configured fallback values from `config.yml` are returned instead of errors.

## Placeholder list

| Placeholder | Description | Fallback key |
| --- | --- | --- |
| `%extracrates_opening%` | `true`/`false` depending on whether the player is opening a crate. | `placeholders.opening-true` / `placeholders.opening-false` |
| `%extracrates_crate_id%` | ID of the crate currently being opened. | `placeholders.no-crate` |
| `%extracrates_crate_name%` | Display name of the crate currently being opened. | `placeholders.no-crate` |
| `%extracrates_reward_id%` | ID of the currently selected reward. | `placeholders.no-reward` |
| `%extracrates_reward_name%` | Display name of the currently selected reward. | `placeholders.no-reward` |
| `%extracrates_rerolls_remained%` | Remaining rerolls (same as `rerolls_left`). | `placeholders.no-rerolls` / `placeholders.unlimited-rerolls` |
| `%extracrates_rerolls_left%` | Remaining rerolls for the current crate. | `placeholders.no-rerolls` / `placeholders.unlimited-rerolls` |
| `%extracrates_rerolls_used%` | Rerolls used so far. | `placeholders.no-rerolls` |
| `%extracrates_rerolls_max%` | Max rerolls allowed (or unlimited placeholder). | `placeholders.no-rerolls` / `placeholders.unlimited-rerolls` |

## Configuration defaults

Update these values in `config.yml` to customize the fallback values:

```yaml
placeholders:
  none: "none"
  no-session: "none"
  no-crate: "none"
  no-reward: "none"
  no-rerolls: "0"
  unlimited-rerolls: "∞"
  opening-true: "true"
  opening-false: "false"
```
