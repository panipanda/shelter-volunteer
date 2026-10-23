# Fonts

Both families are SIL Open Font License 1.1, self-hosted as woff2 and split by script subset
(latin, latin-ext, cyrillic, cyrillic-ext) as served by Google Fonts. Each licence file must stay
next to the fonts.

- `literata-600-*.woff2`: Literata, weight 600 only, with the optical-size axis. Used for `h1`, `h2`,
  the wordmark and cat names. Copyright 2017 The Literata Project Authors. Licence: [OFL-Literata.txt](OFL-Literata.txt).
  Source: https://fonts.google.com/specimen/Literata
- `source-sans-3-*.woff2`: Source Sans 3, variable weight 400–600, one file per subset. Used for everything else.
  Copyright 2010-2024 Adobe. Licence: [OFL-SourceSans3.txt](OFL-SourceSans3.txt).
  Source: https://fonts.google.com/specimen/Source+Sans+3

To fetch again, request `https://fonts.googleapis.com/css2?family=Literata:opsz,wght@7..72,600&family=Source+Sans+3:wght@400..600`
with a current Chrome User-Agent (other agents get a single TTF) and download the `woff2` URL of each subset.
