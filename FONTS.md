# Fonts

| Font | Role | Where | Status |
|---|---|---|---|
| Instrument Serif (regular + italic) | display, headline, "voice" lines | `app/src/main/res/font/instrument_serif_regular.ttf`, `instrument_serif_italic.ttf` | bundled |
| Manrope (variable, weights 400-800) | title, body, label, caption | `app/src/main/res/font/manrope.ttf` | **add it** (see below) |

Both are licensed under the SIL Open Font License 1.1. The licence texts are in
`app/src/main/assets/licenses/` (`OFL-InstrumentSerif.txt`, `OFL-Manrope.txt`).
Instrument Serif's copyright line was read from the font files themselves.
For Manrope, replace `OFL-Manrope.txt` with the `OFL.txt` that comes in the Manrope download.

## Adding Manrope
1. Download the family from Google Fonts (fonts.google.com/specimen/Manrope) and unzip it.
2. Copy the VARIABLE font (`Manrope-VariableFont_wght.ttf`) to `app/src/main/res/font/manrope.ttf`
   (lowercase letters, digits and underscores only; no brackets).
3. Copy its `OFL.txt` over `app/src/main/assets/licenses/OFL-Manrope.txt`.

Until `manrope.ttf` exists the app uses the system sans-serif for body text, so it builds and runs either way.
