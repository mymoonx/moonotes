# Moonotes 0.6.3-alpha.6 — BTTV emote modifiers (Minecraft Java 26.3)

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
- `moonotes-network-0.6.3-alpha.4.jar` (recognition and drawable effects)

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

## Alpha.3: actual Minecraft/Streamotes render-hook check

After installing both previous JARs, an in-game tester reported the BTTV
effects still did not appear. Earlier offline tests showed that the markup
parser worked, but cannot prove the Streamotes glyph hook was executed.

Alpha.3 targets a more precise 26.3 method call:
`Font$PreparedTextBuilder.accept(int, Style, int)` forwards a prepared
`BakedGlyph` to its `accept(int, Style, BakedGlyph)` overload. Moonotes
now wraps that **final argument**, after Streamotes's own glyph substitution,
instead of using the old local-variable STORE injection.

For diagnosis this experimental build includes five **ephemeral, local-only**
counters in the copyable Moonotes diagnostics report:

```text
BTTV input/chat/marker/wrap/draw: A/B/C/D/E
```

- `input`: recognized and styled a BTTV pair in the chat input
- `chat`: decorated a visible chat message containing a BTTV pair
- `marker`: modifier insertion survived as far as the glyph hook
- `wrap`: the hook actually received a Streamotes EmoticonGlyph
- `draw`: a transformed effect's draw method was executed

Only counters are recorded — **no chat contents, emote names, identities or
server addresses**. Nothing is sent to Cloudflare or other networks.

### Manual reproduction

1. Keep the existing `moonotes-0.4.3.jar`, replace **only Network**
   with `moonotes-network-0.6.3-alpha.4.jar`. Keep Streamotes.
2. Open `/moonotes → Настройки → Отчёт`; copy the current BTTV counter line.
3. Type `WW` and verify normal emote works; type `w! WW` and inspect
   whether the image exists, and whether the modifier is applied.
4. Send `w! WW` and look at the resulting Minecraft chat line.
5. Copy the BTTV counter line again. The relative increases reveal
   exactly which stage failed.
6. If needed, attach `logs/latest.log` from this Minecraft launch
   (do not share server addresses, chat logs or private information
   without reviewing the file).

The **in-game rendering result is not yet verified**. A successful CI run
validates compilation and pure JVM regression checks only. If any counter
does not increase at the expected stage, investigate that runtime hook
instead of claiming that modifier support works.

## Alpha.4: startup crash repair

Minecraft crashed on the title-screen loading overlay when using
`moonotes-network-0.6.3-alpha.3.jar`. The provided crash report
identified `moonotes-network.mixins.json:BttvGlyphMixin`, and an
`InvalidInjectionException` saying that the old `@ModifyArg`
handler expected a different argument signature than the one declared.

The corrected hook uses `@ModifyArgs` and exactly **one** handler
parameter (`Args`). It reads the actual invocation arguments
(`int index`, `Style style`, `BakedGlyph glyph`) directly,
and replaces only argument 2 (the glyph) when a registered BTTV
modifier is present. There are no outer-method arguments in the
handler signature.

This change addresses the **confirmed startup crash**. It is
separate from confirming whether `w! WW` actually renders wide
in-game. If the game still fails to start, immediately revert only
Network to the last bootable `moonotes-network-0.6.3-alpha.2.jar`
and provide the new crash report. Core Moonotes stays `0.4.3`.

`BttvGlyphInjectionCheck` now verifies at build time that this
Mixin declares the expected one-Args handler and targets the correct
Minecraft 26.3 overload. This regression cannot prove runtime
Mixin application; in-game smoke testing is still required.

## Alpha.5: fix invisible h! / v! emotes (after in-game confirmation w! works)

The 0.6.3-alpha.4 tester confirmed `w!` works, but reported that
`h!`, `v!` and `z!` do not display as expected.

Root-cause candidate for `h!` and `v!`: negative X/Y scaling of the
whole quad reverses its vertex winding. The graphics renderer can cull
the back face instead of showing a flipped emote.

Alpha.5 replaces negative geometry scaling with a 4-vertex UV mirror:
- **Same world and screen vertex coordinates**: preserve original quad winding
  and front-facing visibility, no additional graphics buffers.
- **Mirror only the U coordinates** for horizontal (`h!`) and only the V
  coordinates for vertical (`v!`).
- **Mirror strictly within the actual U/V bounds of the current frame**.
  This avoids sampling a different animation frame in a Streamotes sprite sheet.
- `w!` retains its previous 2x positive X scale because it is proven in game.
- Adds `BttvMirroredVerticesCheck` regression verifying orientation, frame
  UV range, color and light output, alongside the existing modifier/parser tests.

The official BTTV `z!` behavior is specifically **remove the space before
the emote**. It is **not** horizontal/vertical scaling, and not 7TV zero-width
overlay. Thus `z! WW` at the very start has no visible special
transformation; test `WW z! WW` to see two adjacent emotes instead.
The edit box continues showing the unmodified literal text during input
for correct caret positioning.

**Installation:** replace only the Network JAR with
`moonotes-network-0.6.3-alpha.5.jar` and keep
`moonotes-0.4.3.jar` and original Streamotes. First test
`w! WW` for regression, then `h! WW`, `v! WW`, and
`WW z! WW` in sent chat. Do not merge draft PR until
the new 3 effects are confirmed in Minecraft itself.

## Alpha.6: one-letter aliases, clearer operator preview, FIFO loading delay

The Minecraft tester confirmed the previous modifier patch works, but showed
single-letter aliases like `q`, `v`, and Cyrillic `м` fail with a modifier.
The parser required at least two characters, and the displayed-chat fast path
ignored four-character commands such as `w! q`. Both limits now permit **one**
registered code point / character. This does **not** convert arbitrary letters
into images: the Streamotes registry must already have that exact alias.

New input feedback requires **no menu or new syntax**: the typed modifier
(`w!`, `h!`, `v!`, `z!`) is shown in muted gray while waiting for an emote,
and switches to cyan when the next alias is registered and the effect is active.
The original typed characters and caret positions are preserved. Moonotes
also recognises the incomplete prefix by itself, rather than styling it as a
potential Streamotes emote called `w!`.

The new uploaded test log from 2026-11-26 revealed repeatable ten-second
`HttpConnectTimeoutException` failures when Streamotes tried fetching the
**BTTV pseudo-emote images** `w!`, `h!`, `z!`, and `v!`. Streamotes'
`AsyncEmoticonLoader` is a single-threaded FIFO, so one such failed download
can delay subsequent valid emote images in the same queue. A narrow guard
in the existing Streamotes image-loader mixin bypasses only these four
**BTTV** pseudo-image requests. Other BTTV emotes, 7TV, FFZ and Twitch images
continue through the normal loader and cache unchanged. This removes the
identified ten-second queue blockers, but does not guarantee that all 7TV/FFZ
downloads will be immediate; the same log shows unrelated FFZ SSL errors.

### Smoke tests

- `w! q` — wide, one-letter registered emote
- `h! v` — mirror, one-letter registered emote
- `v! м` — flip, one-letter Cyrillic registered emote
- `w! re` — existing two-letter modifier regression
- `h!` while typing — gray operator, **not** a failed image download
- `w! unknown` while typing — gray operator and normal text, no false emote
- `w! q` — cyan operator once `q` resolves, the original text stays editable
- `WW z! WW` — remove the display-space separator
- Verify modifier aliases never hang other cached/uncached emotes.
- If a particular emote still takes several seconds, compare cold/warm
  load and collect a new timestamped `latest.log`.

**Installation:** keep `moonotes-0.4.3.jar` and Streamotes
`1.2.16+26.3`, replace only Network with
`moonotes-network-0.6.3-alpha.6.jar`. Do not install two Network JARs
simultaneously. Draft PR remains unmerged pending in-game confirmation.
