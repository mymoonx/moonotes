# Moonotes 0.6.3-alpha.2 — BTTV emote modifiers (Minecraft Java 26.3)

Experimental first-stage implementation of four real BetterTTV prefix modifiers.
The syntax follows BetterTTV's documented `w! emote`, `h! emote`,
`v! emote`, `z! emote`, with a mandatory space between the modifier
and the next emote. This is **not** a Minecraft slash command.

## Effects

| Example | Client-side presentation |
| --- | --- |
| `w! WW` | Widened WW (2× visual X scale, additional line-width reserve) |
| `h! WW` | Mirrored WW left↔right |
| `v! WW` | Flipped WW top↔bottom |
| `z! WW` | No visual space immediately before the emote |
| `Hello w! WW` | Regular words and a widened WW |
| `w! missing` | Literal text; no registered emote means no change |
| `h! !vanish` | Horizontal flip of the registered emote named `!vanish` |

Every effect is **render-only**. Neither outgoing chat packets nor server
message contents are changed. Other Minecraft clients without Moonotes render
the literal text. BTTV's modifiers are client-side and don't require a server
plugin. In this first implementation they apply to **any emote already
registered in the local Streamotes registry**, not only BTTV-hosted emotes.

The source and animation remain Streamotes-owned. The draw layer changes
the rendered matrix for horizontal/vertical flips and widening, not the
PNG/WebP cache; it does not upload altered image copies.

## First milestone limitations

- **Ordinary chat only**: entry-field preview and received chat text.
  Commands, private-message bodies, books, signs, item names, and wheel
  reactions deliberately keep the earlier behavior and will get effect
  handling in later milestones.
- `z!` removes a preceding display space; it is **not** the same as 7TV's
  zero-width image layering. Real overlay support needs its own
  layout/rendering treatment.
- Wide rendering reserves blank display width approximated from the current
  Streamotes font metrics; precise wrapping with variable-width emotes
  remains a gameplay QA task.
- Modifier overlays must be tested in-game with **animated** emotes and
  separately colored server chat messages. Compilation and regression tests
  cannot prove that all Minecraft/Streamotes mixin injection points fire.
- Further FFZ effects (Hyper, Cursed, Rainbow, Jam, Bounce) and 7TV
  zero-width overlays are **not implemented** in this alpha.

## Installation and game verification

Build artifacts must contain both:
- `moonotes-0.4.3.jar` (input formatting)
- `moonotes-network-0.6.3-alpha.2.jar` (recognition and drawable effects)

Remove both older Moonotes files before installing. Keep the original
Streamotes mod plus Fabric API; use Minecraft Java 26.3/Java 25. No
Cloudflare redeploy is necessary.

Suggested manual checks:
1. Type `h! WW`, `v! WW`, `w! WW`, `z! WW`; see if the input preview
   shows the expected effect, then send and check the rendered chat.
2. Verify `!WW` still works in global chat. Unmodified `WW`,
   `!vanish` and `!setgame` must be unchanged.
3. Verify `h! missing` stays visible literally and
   `/msg Steve му` still follows the beta.4 behavior.
4. Test two nearby effects, line wrapping near the right edge, different
   colors in server formatting, chat scrolling, and an animated emote.
5. Verify signs/books remain unchanged. Cloudflare reactions still work.

## Implementation

- `BttvModifierSyntax`: pure, bounded exact token recognition.
- `BttvChatText`: non-mutating conversion of text spans into visual styles,
  retaining other style fragments and server click actions.
- `BttvEffectGlyph`: wraps only `xeed.mc.streamotes.EmoticonGlyph`,
  transforms its existing render matrix, retains animation and texture.
- `BttvInputPreview`: optional Network integration into Moonotes core.
- Non-required experimental mixins `BttvChatTextMixin` and `BttvGlyphMixin`.
- `BttvModifierSyntaxCheck` and `BttvInputPreviewCheck`: parser, safety and
  formatted input regressions.

Nothing in `reactions-worker/` or stable `main` has been deployed or modified
as part of this alpha feature branch.

## Alpha.2: repair for "w! emote not visible"

Initial in-game feedback: `w!` was not showing the emote as expected.
The first input formatter shortened `w! WW` into `WW` plus padding.
This does not preserve the chat EditBox's character positions and is unsafe
for caret positioning, highlighting, and horizontally scrolled input.

Alpha.2 keeps **every original character** visible while the player edits
the draft. It applies a Streamotes glyph style **only to the emote token**
and does not change the transmitted message. The actual sent-chat display
still removes modifier syntax visually and draws the effect.

An additional final-formatting hook runs from the Network JAR after the
existing edit-box formatters, so the *input preview* also works for players
who kept an older Moonotes core JAR. The normal Moonotes 0.4.3 core is still
recommended; **updating Network alone from alpha.1 to alpha.2 is sufficient
to test this fix**. No Cloudflare redeployment is needed.

Input text such as `w! WW` remains literally `w! WW` in the editing
field by design. This prevents the cursor from shifting, and the `WW`
portion should be an emote with wide rendering. In **received chat**,
the visual modifier prefix is hidden as before.

Manual checks: confirm `WW` alone is visible; then type `w! WW` and
verify that `WW` becomes an image without the input shifting; send it and
check whether the sent-chat image is widened. Check `h!`, `v!`, `z!`,
`!WW`, private messages and tab completion for regressions.
