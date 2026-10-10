# Licensing backend contract (direct build)

The app talks to one HTTPS endpoint. Its address comes from `app/licensing.json`
(git-ignored; copy `app/licensing.json.example`). Without that file the app builds and says
"Licensing not configured", and nothing is enforced.

## Request  (POST, JSON)

    { "action": "activate" | "check", "key": "ABCD-1234-...", "deviceId": "<32 hex chars>", "app": "lia" }

If `apiKey` is set in the config it is sent as the header `X-Api-Key`.

- `activate`: first use of a key. Bind the key to `deviceId` (or refuse if bound elsewhere).
- `check`: the periodic check (at most once every 3 hours). Same device only.

## Response  (HTTP 200, JSON)

| status            | meaning                                   | extra fields                      |
|-------------------|-------------------------------------------|-----------------------------------|
| `ok`              | key is valid                              | `plan` (text), `expiresAt` (ms since 1970; ignored for plan `lifetime`) |
| `blocked`         | admin blocked the key: inactive at once   |                                   |
| `unknown_key`     | no such key: the app forgets it           | `message` (optional)              |
| `device_mismatch` | key is bound to another device            | `message` (optional)              |

## Fail-open

Any other answer (HTTP error, timeout, no internet, unreadable body, unknown status) is treated as
"could not check": the stored state is kept and the user is NOT locked out. Activation is the one
exception: it needs the server, so offline activation fails.
