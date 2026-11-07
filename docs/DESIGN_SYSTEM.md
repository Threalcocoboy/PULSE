# Design system

The supplied `pulse logo.png` is packaged unchanged and shown in onboarding. A small Canvas dot wordmark is used in compact headers. No dashboard reference image or licensed font was supplied; the rest follows the written brief.

- Background `#090909`; surface `#171717`; elevated `#222222`.
- Text white / `#A0A0A0`; outline `#343434`; emphasis `#FF3535`.
- Light mode uses warm off-white, white surfaces and dark text, with a darker red primary for contrast.
- 0.2 typography roles: Android system serif editorial headings (26–40sp), proportional sans-serif body/labels/numerals, mono only for concise technical dates/metadata, Canvas dot branding. No licensed Nothing fonts are bundled. Sentence case, normal tracking and readable numerical weights. System font scaling remains enabled.
- 4dp rhythm, 16–20dp horizontal padding, 8dp widget gaps, 12dp card radii. Purposeful true circles and pills.
- Exactly three bottom destinations. Settings is a secondary Home route. Insets keep content clear of system bars.
- Health rings, lines, bars, heatmaps and sparklines draw individual circles on Compose Canvas. Missing observations leave gaps; over-goal rings clamp at one revolution while the number can exceed 100%.
- Studio outlines are dashed, selected states red. Rearrangement is 240ms, disabled by system/app reduced-motion settings. Native haptics honor the preference.
- Monthly medals now use flat tonal fills, one border, sparse dots, a small geometric seasonal symbol and large `MM/YY`. No texture/bevel/concentric rim. One native vector renderer supplies both Compose art and PNG shares; four palettes remain distinct, unavailable states readable.

`PulseTokens` centralizes spacing, corner/padding/touch/icon/dot dimensions, fonts and reflow duration. Home source status is a compact tappable row; History and Collection use only a small demo label. Periods use a grouped segment; metric tabs have an explicit More arrow. Unselected chart dots are gray, selected/latest red. Bottom navigation is 64dp plus system insets.

Charts include numerical summaries and semantic descriptions. Interactive charts offer next/previous accessibility actions. Standard controls use native Material accessibility behavior with custom PULSE styling. Studio has a configuration sheet and accessible move/resize alternatives. Full TalkBack and very large text audits remain pending; see known issues.
