Sources for the PNGs in `../images/` (same base name): `.mmd` files are Mermaid, render with `npx -p @mermaid-js/mermaid-cli mmdc -i X.mmd -o ../images/X.png -s 2 -b white`.
`.svg` files are hand-placed diagrams; render at 2x with any headless Chrome, e.g. `chrome --headless --screenshot=../images/X.png --force-device-scale-factor=2 --window-size=<svg width>,<svg height> X.svg`.
Edit the source, re-render, and commit both; the PNG is what Requirements_and_Design.md shows.
