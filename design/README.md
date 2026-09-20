# CashMatrix design files

Vector screens of the app, exported as SVG so they open in almost any design tool. Live text and shapes stay editable in most of them.

| File | What |
|---|---|
| `00-design-tokens.svg` | Colours, type scale, shapes, buttons and the tiger logo |
| `screens/desktop-01…06-*.svg` | Login, Home, Calendar, Alerts, Settings and the Add dialog at 1280 px wide |
| `screens/mobile-01…06-*.svg` | The same at 390 px wide (phone), with the bottom navigation |
| `previews/*.png` | Pictures of each file, for a quick look without opening a design tool |

## Opening them

- **Figma**: drag an `.svg` file onto the canvas (or paste it). Frames, text and vectors come in as editable layers. Install the free **Inter** font if text looks off.
- **Penpot** (free, open source): File, then Import files, or drag the SVG onto the board.
- **Sketch, Illustrator, Affinity, Inkscape**: File, then Open (or Place) the SVG.
- **Canva**: Uploads, then choose the SVG. It arrives as a picture, so text is not editable there.

## What to know

- The screens are snapshots of the real running app with made-up demo data (no real bank details).
- Gradients and soft shadows are simplified to flat colours, because they don't survive SVG export from a web page. The live app has them: the green hero panel is a gradient with faint tiger-stripe lines.
- Form dropdowns and placeholder text are written out as plain text.
- The source of truth for the look is `frontend/src/index.css` (colours and spacing) and the components in `frontend/src/components/`. If you change the design in a tool, mirror it there.
