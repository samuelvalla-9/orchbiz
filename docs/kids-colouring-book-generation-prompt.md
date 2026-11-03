# Kids Colouring Book Generation Prompt (KDP / Etsy)

> **Scope note:** This is a standalone reference document. It is **not** part of the OrchBiz client workflow, catalogue or approval gates.

This guide gives a reusable, tool‑agnostic prompt system for producing **commercially publishable** children's colouring books with AI image generators, for sale as paperbacks on **Amazon KDP** or as printable downloads on **Etsy**. It covers what a good colouring page needs, what to leave out, age‑specific settings, the prompts themselves, per‑tool adjustments, quality checks, print preparation and marketplace compliance.

---

## 1. How the workflow fits together

```
Phase 1  Book planner prompt (any chat LLM)
           -> theme, title ideas, character bible, page list with scene briefs
Phase 2  Page image prompt (image generator), one page at a time
           -> raw line-art images
Phase 3  QA checklist per page -> regenerate or apply fix-up prompts
Phase 4  Post-processing -> pure black/white, 300 DPI, safe area
Phase 5  Layout -> front matter, single-sided pages, PDF (KDP) or PDF/PNG pack (Etsy)
Phase 6  Cover prompt + layout -> title text added in a layout tool
Phase 7  Compliance -> AI disclosure, IP check, listing copy
```

Generate **one page per request**. Asking for a whole page grid in one image gives low resolution, merged pages and inconsistent style.

---

## 2. What a colouring page needs, and what it doesn't

### Needed

| Requirement | Why |
|---|---|
| **Pure black line art on a pure white background** | Prints cleanly; no grey ink or muddy areas on KDP's B/W interior. |
| **Closed shapes** (every region fully enclosed) | Kids can fill each area; open gaps look broken and fail flood‑fill. |
| **Uniform, bold outlines** with weight matched to age | Thick lines help small hands stay inside; thin lines disappear after thresholding. |
| **Generous white space and large fillable regions** | Crayons and markers need room; tiny slivers frustrate kids. |
| **One clear focal subject, centred**, with breathing room to the edges | Reads instantly; keeps art inside print margins and away from the spine. |
| **Front‑facing, friendly, simple cartoon style** | Appeals to kids; easier for AI to render cleanly. |
| **Consistent style and recurring characters** across the book | Buyers expect a cohesive product; reviews punish random styles. |
| **Variety in poses, scenes and composition** | Avoids near‑duplicate pages (bad reviews, possible KDP "poor customer experience" issues). |
| **Portrait orientation** matching the trim (8.5×11 in is standard; 8.5×8.5 in suits toddlers) | Fits the page without awkward cropping. |
| **High resolution** (final 300 DPI at trim size) | KDP needs 300 DPI for sharp print. |
| **Diverse, inclusive human characters** where people appear | Broader appeal; avoids stereotypes. |

### Not needed. Explicitly exclude

| Exclude | Why |
|---|---|
| Colour, grey tones, gradients, shading, shadows, hatching, stippling, cross‑hatching, textures | Kids are meant to add colour; grey prints as muddy dots. |
| Filled black areas (except tiny pupils/nose) | Wastes ink, removes colourable area, can bleed through. |
| 3D rendering, realism, photo look, sketchy or rough pencil lines, double lines | Not colourable; breaks after thresholding. |
| **Any text, letters, numbers, logos, signatures, watermarks** inside the art | AI garbles text. Add titles and labels later in a layout tool. |
| Page borders and frames drawn by the generator | Often uneven and at risk of trimming. Add borders in layout if you want them. |
| Busy or full‑bleed backgrounds for ages 2–6 | Overwhelming; tiny regions. |
| Tiny details, fine fur/hair strands, dense patterns (for young ages) | Can't be coloured with crayons. |
| Scary, violent, gory, weapons, sexualised or unsafe content | Not child‑appropriate; violates marketplace rules. |
| Copyrighted or trademarked characters, brands or styles (Disney, Pokémon, Bluey, Paw Patrol, Marvel, etc.) | IP infringement leads to takedowns and account risk. |
| Cropped subjects cut by the image edge | Looks unfinished and conflicts with margins. |
| Anatomy errors (extra limbs/fingers, merged bodies) | Common AI artefact; obvious to buyers. |

---

## 3. Age‑band parameters

Pick one band per book. Insert the row's values into the `{...}` placeholders in the prompts below.

| Parameter | **2–4 (Toddler)** | **4–6 (Preschool)** | **6–9 (Early school)** | **9–12 (Older kids)** |
|---|---|---|---|---|
| `{LINE_WEIGHT}` | extra‑thick, very bold uniform outlines | thick, bold uniform outlines | medium‑bold clean outlines | medium clean outlines, some thinner inner detail lines |
| `{REGION_COUNT}` | about 5–15 large regions | about 15–35 regions | about 35–80 regions | about 80–200 regions |
| `{MIN_REGION}` | nothing smaller than a child's thumbprint | nothing smaller than a fingertip | nothing smaller than a pencil eraser | nothing smaller than a grain of rice; avoid microscopic slivers |
| `{SUBJECT_RULE}` | one single large object or character, fills ~70% of page | one main character plus 1–2 simple props | 1–3 characters doing an action | full scene with characters, action and environment |
| `{BACKGROUND_RULE}` | no background at all, plain white | minimal: simple ground line, maybe a sun or cloud | simple background scene with a few large elements | detailed background allowed, still with clear colourable shapes; optional large decorative patterns |
| `{STYLE}` | super simple, chunky, rounded kawaii shapes | simple cute cartoon | friendly cartoon illustration | detailed cartoon / storybook illustration, optional mandala‑style patterns |
| Suggested trim | 8.5×8.5 in or 8.5×11 in | 8.5×11 in | 8.5×11 in | 8.5×11 in |
| Suggested designs | 25–40 | 30–50 | 40–50 | 40–60 |
| Themes that sell | animals, vehicles, fruit, shapes, toys | farm, dinosaurs, unicorns, trucks, ocean | dinosaurs, space, mermaids, robots, sports, holidays | fantasy, kawaii food, cities, nature, patterns, adventure |

> Print colouring pages **single‑sided** (blank back of each design) so markers don't bleed onto the next picture. A 40‑design book is then ~80+ interior pages. KDP's paperback minimum is 24 pages.

---

## 4. Phase 1: Book planner prompt (chat LLM)

Paste into ChatGPT, Gemini, Claude, Copilot or a similar tool. Fill in the brackets.

```text
You are an expert children's colouring book designer and Amazon KDP / Etsy publisher.

Plan a commercially publishable colouring book with these inputs:
- Theme: [e.g. "Friendly Dinosaurs on Adventure"]
- Age band: [2–4 | 4–6 | 6–9 | 9–12]
- Number of designs: [e.g. 40]
- Trim size: [8.5x11 in portrait | 8.5x8.5 in square]
- Marketplace: [Amazon KDP paperback | Etsy printable PDF | both]
- Language/market: [e.g. US English -> "coloring"; UK -> "colouring"]

Rules:
- Original content only. No copyrighted or trademarked characters, brands, logos or recognisable franchise styles.
- Child-safe, positive and gentle. No violence, weapons, scary or gory content, nothing unsafe to imitate.
- Detail level must suit the age band: [paste the age-band row from the table].
- Every page must be a distinct scene: vary subject, action, setting and composition. No near-duplicates.
- Where people appear, show diverse, inclusive characters without stereotypes.
- No text inside illustrations (titles and labels will be added later in layout).

Produce:
1. 10 title + subtitle options (keyword-rich but honest; no trademarked terms; no claims like "bestseller" or "award-winning").
2. A CHARACTER BIBLE for 1–4 recurring original characters: name, species/type, body shape, distinctive features, outfit/accessories, expression style. Each description must be a fixed, copy-pasteable sentence of 25–40 words reused verbatim in every prompt.
3. A STYLE LOCK sentence (one line) describing the visual style to reuse verbatim on every page.
4. A PAGE LIST as a table with columns: #, page title (for my reference only), subject, action, setting, props (max 3), background rule, composition (close-up / medium / wide; centred / left / right), difficulty (1–3 within the band).
   Order pages from easier to harder and spread themes/settings evenly.
5. Front/back matter ideas: "This book belongs to" page design, a colour-test page, and a short friendly back-page message (no reviews or fabricated claims).
6. 7 KDP backend keywords and 13 Etsy tags (no trademarks, no competitor names).

Output tables in Markdown. Do not generate images.
```

---

## 5. Phase 2: Master page image prompt (template)

Use one prompt per page. Copy the **CHARACTER BIBLE** and **STYLE LOCK** text verbatim each time for consistency.

```text
Children's colouring book page, black and white line art only.

SUBJECT: {SUBJECT} {ACTION} {SETTING}.
CHARACTERS: {CHARACTER_BIBLE_TEXT}
PROPS: {PROPS}
STYLE: {STYLE_LOCK}. {STYLE}, for kids aged {AGE_BAND}.

LINE ART RULES:
- {LINE_WEIGHT}, pure solid black (#000000) lines on a pure white (#FFFFFF) background.
- Every shape fully closed so each area can be coloured in; no gaps in outlines.
- {REGION_COUNT}; {MIN_REGION}.
- Clean vector-like smooth lines, consistent line thickness, no sketchy or double lines.
- Completely uncoloured: no shading, no grey, no gradients, no shadows, no hatching, no stippling, no textures, no solid black fills (except tiny eye pupils).

COMPOSITION:
- {SUBJECT_RULE}. {BACKGROUND_RULE}.
- Portrait orientation, subject centred with a generous empty white margin on all sides (nothing touching or cut off by the edges).
- Flat 2D illustration, front or three-quarter view, friendly happy expressions, anatomically correct (correct number of limbs, fingers and eyes).

DO NOT INCLUDE: text, letters, numbers, words, logos, signatures, watermarks, page border, frame, colour, grey tones, shading, realistic or 3D rendering, photo, scary or violent elements, copyrighted characters.
```

### Reusable negative prompt block

For tools that accept a separate negative prompt:

```text
color, colour, colored, grey, gray, grayscale shading, gradient, shadow, shading, hatching, cross-hatching, stippling, texture, halftone, solid black fill, filled areas, 3d, render, realistic, photo, photorealistic, painting, watercolor, sketch, rough pencil, messy lines, double lines, broken lines, open shapes, thin faint lines, blurry, noise, jpeg artifacts, text, letters, words, numbers, typography, logo, signature, watermark, border, frame, cropped, cut off, out of frame, extra limbs, extra fingers, deformed, mutated, disfigured, duplicate, scary, creepy, violent, weapon, blood
```

### Fix‑up / edit prompts (for tools that support image editing)

- *"Keep the exact drawing but remove all grey shading and fills; output only clean solid black outlines on pure white."*
- *"Close every gap in the outlines so each area is a fully enclosed shape. Do not add new details."*
- *"Thicken all outlines to a uniform bold weight. Keep everything else identical."*
- *"Remove all text, letters and any border. Keep the illustration unchanged."*
- *"Simplify: remove small background details and merge tiny areas into larger shapes suitable for ages {AGE_BAND}."*
- *"Shrink the illustration slightly so there is an empty white margin on all four sides; nothing should touch the edges."*

---

## 6. Tool‑specific adapters

Generators interpret prompts differently. Keep the master content and adjust as follows.

### ChatGPT / GPT‑Image (OpenAI)
- Strong instruction‑following; the full master prompt works as‑is.
- Request **portrait** size (e.g. 1024×1536) explicitly.
- For character consistency, keep one chat per book. Upload or reference the first approved page: *"Use the same character design and line style as the reference image."*
- If it adds grey shading, use the fix‑up edit prompt instead of regenerating from scratch.
- Ask for **one** image per request.

### Midjourney
- Prefers shorter, comma‑separated prompts. Compress the master prompt:

```text
children's coloring book page, {SUBJECT} {ACTION} {SETTING}, {STYLE}, {LINE_WEIGHT}, clean black outline line art on white background, closed shapes, large simple areas, no shading, centered with white margin --ar 17:22 --style raw --stylize 50 --no color, gray, shading, gradient, shadow, hatching, texture, text, letters, watermark, border, frame, realistic, 3d
```

- `--ar 17:22` matches 8.5×11 in (use `--ar 1:1` for square books). Low `--stylize` and `--style raw` reduce artistic embellishment (shading, texture).
- Use **character reference** / **style reference** features (e.g. `--cref`, `--sref` or the current equivalents) with an approved page URL to keep characters and style consistent.
- Upscale only approved images.

### Google Gemini / Imagen
- Works well with natural‑language master prompts; state "black and white line art, no colour" early and repeat it in the exclusions.
- Use its image‑editing mode with an approved page as reference to keep characters consistent and to apply fix‑up prompts.
- Specify portrait aspect ratio (3:4) where the interface allows.

### Stable Diffusion (SDXL) / Flux
- **SDXL:** put the master prompt in the positive field and the negative block in the negative field. Typical starting points: CFG ~5–7, 30–40 steps, portrait resolution such as 896×1152. Line‑art or colouring‑book LoRAs greatly improve output; check each LoRA's licence permits commercial use.
- **Flux:** generally does **not** use negative prompts effectively. Phrase exclusions positively ("pure white background, only black outlines, uncoloured"), and keep guidance moderate.
- **ControlNet** (lineart, scribble, canny) lets you lock composition from a rough sketch and keep characters consistent; a fixed seed helps reproduce style.
- Check the model's licence (some Flux variants restrict commercial use).

---

## 7. Cover, front matter and back matter

### Front cover art prompt (full colour)

```text
Bright, cheerful full-colour children's book cover illustration for a colouring book about {THEME}, featuring {CHARACTER_BIBLE_TEXT} {COVER_ACTION}. {STYLE_LOCK}, vibrant saturated colours, clean shapes, friendly expressions, high contrast, appealing to kids aged {AGE_BAND} and their parents.
Leave a large clean empty area across the top third for the title, and an empty strip near the bottom for the subtitle (do NOT write any text).
Portrait orientation, important elements well away from the edges (they will be trimmed).
No text, letters, logos, watermarks, borders or copyrighted characters.
```

- Add **all title text in a layout tool** (Canva, Affinity, InDesign, Photoshop, KDP Cover Creator). Never let the generator write it.
- Show 2–4 interior page thumbnails (black line art) on the back cover; buyers like to see the inside.
- KDP covers are a single wraparound file (back + spine + front + bleed). Use KDP's **cover calculator/template** for your exact page count and paper type to get the spine width.

### Interior front/back matter
1. **"This book belongs to"** page: line‑art frame around a blank name area (text added in layout).
2. **Copyright page** (in layout): © year, author/publisher name, "All rights reserved", optional AI disclosure statement.
3. **Colour test page**: blank grid of large shapes for testing crayons/markers.
4. Designs, single‑sided, each followed by a blank page (optionally a light "place a sheet behind this page" tip).
5. Optional back page: friendly thank‑you note and a gentle request to leave an honest review. Never fabricate reviews or claims.

---

## 8. Worked examples (one per age band)

### Ages 2–4: "Happy Farm Friends"
```text
Children's colouring book page, black and white line art only.
SUBJECT: one big smiling cartoon cow standing and waving, front view.
STYLE: super simple, chunky, rounded kawaii shapes, for kids aged 2–4.
LINE ART RULES: extra-thick, very bold uniform pure black outlines on pure white; every shape fully closed; about 8–12 large regions; nothing smaller than a child's thumbprint; no shading, grey, gradients, textures or solid black fills (except tiny pupils).
COMPOSITION: single large character filling ~70% of the page, no background at all, centred with wide white margin, nothing touching the edges. Correct anatomy (four legs, two eyes).
DO NOT INCLUDE: text, letters, numbers, border, frame, colour, shading, realistic or 3D rendering, watermark.
```

### Ages 4–6: "Dino Day Out"
```text
Children's colouring book page, black and white line art only.
SUBJECT: a cute baby triceratops named Tops (round body, three short rounded horns, big friendly eyes, small spotted pattern on back) holding a balloon while walking on a grassy path.
PROPS: a balloon, a small flower.
STYLE: simple cute cartoon, for kids aged 4–6.
LINE ART RULES: thick, bold uniform black outlines on pure white; all shapes closed; about 20–30 regions; nothing smaller than a fingertip; no shading, grey, gradients, hatching or textures.
COMPOSITION: one main character plus two props, minimal background with a simple ground line, one sun and one cloud; centred with a generous white margin.
DO NOT INCLUDE: text, letters, numbers, border, colour, shading, scary teeth, realistic or 3D style, watermark.
```

### Ages 6–9: "Space Pals"
```text
Children's colouring book page, black and white line art only.
SUBJECT: two friendly kid astronauts (one girl with curly hair puffs, one boy with glasses, both in rounded spacesuits with a star patch) planting big round flowers in a little garden on a small moon, a smiling robot helper beside them.
STYLE: friendly cartoon illustration, for kids aged 6–9.
LINE ART RULES: medium-bold clean black outlines on pure white; all shapes closed; about 50–70 regions; nothing smaller than a pencil eraser; no shading, grey, gradients, hatching, stars drawn as tiny dots, or textures.
COMPOSITION: 3 characters in action, simple background with a large ringed planet, a few big stars and a rocket; centred, white margin on all sides, nothing cut off.
DO NOT INCLUDE: text, letters, numbers, logos, flags with symbols, border, colour, shading, 3D, watermark.
```

### Ages 9–12: "Enchanted Treehouse Village"
```text
Children's colouring book page, black and white line art only.
SUBJECT: a whimsical treehouse village built in a giant oak, with rope bridges, round windows, lanterns, a friendly owl and a fox reading on a balcony.
STYLE: detailed storybook cartoon illustration with some large decorative leaf and swirl patterns, for kids aged 9–12.
LINE ART RULES: medium clean black outlines with thinner inner detail lines, pure white background; every shape closed; about 120–180 regions; avoid microscopic slivers; no shading, grey, gradients, hatching, stippling or solid fills.
COMPOSITION: full scene with a detailed but clearly colourable background; key elements kept inside a white margin, nothing important cropped.
DO NOT INCLUDE: text, signs with writing, numbers, border, colour, shading, realistic or 3D rendering, scary elements, watermark.
```

---

## 9. Per‑page QA checklist

Reject or fix any page that fails a check:

- [ ] Only pure black lines on pure white; no grey, shading, gradients or texture (zoom to 200%).
- [ ] All outlines closed. Quick test: paint‑bucket fill a few regions in an image editor; colour should not leak.
- [ ] Line weight consistent and appropriate for the age band; no faint or broken lines.
- [ ] Region sizes appropriate; no tiny slivers for young ages.
- [ ] No text, pseudo‑letters, numbers, logos, signatures or watermarks.
- [ ] Anatomy correct (limbs, fingers, eyes, tails); no merged or duplicated bodies.
- [ ] Subject complete and centred, with a clear white margin; nothing cut off.
- [ ] Characters match the character bible; style matches previous pages.
- [ ] Scene is distinct from other pages (no near‑duplicates).
- [ ] Child‑safe; nothing scary, violent or inappropriate; no resemblance to known IP.

Keep a simple tracker (page #, prompt used, seed/tool, status, notes) so you can reproduce or replace pages later.

---

## 10. Post‑processing and print preparation

### Clean‑up
1. **Upscale** approved images 2–4× (built‑in upscalers, Real‑ESRGAN, Topaz, etc.).
2. **Convert to greyscale, then threshold** to pure 1‑bit black/white (start ~50%; adjust until lines are solid and speckles vanish). This removes stray grey and anti‑aliasing haze.
3. **Despeckle / erase** stray dots; manually close any small outline gaps.
4. Optional **vectorise** (Inkscape Trace Bitmap, Illustrator Image Trace, Vectorizer tools) for razor‑sharp lines at any size.
5. Export **PNG or high‑quality JPEG at 300 DPI**.

### Sizing for KDP (8.5×11 in, no‑bleed interior recommended)
- Colouring pages don't need art to the page edge, so use a **no‑bleed** interior. The PDF page size then equals the trim size.
- Full page at 300 DPI: **2550 × 3300 px**.
- KDP minimum margins (no bleed): outside/top/bottom **≥ 0.25 in**; inside (gutter) **0.375 in** for 24–150 pages, **0.5 in** for 151–300 pages.
- Practical recommendation: keep art inside a **0.5 in safe margin on all sides** (art area 7.5 × 10 in ≈ **2250 × 3000 px**). Satisfies both margins and spine safety, and frames the art nicely.
- If you choose **bleed** (e.g. for decorative full‑page borders): page size = trim + 0.125 in to width and + 0.25 in to height (8.625 × 11.25 in), and outside margins grow to ≥ 0.375 in.
- Interior: **black & white ink on white paper** (cream paper looks dull for colouring).

### Layout and export
- Assemble in Affinity Publisher, InDesign, Canva, Scribus or similar: front matter, then each design on a right‑hand (odd) page followed by a blank back page.
- Export a single **print PDF** (fonts embedded, images at 300 DPI, no crop marks, no transparency issues). Check with KDP's **print previewer** before publishing; fix any margin warnings.
- **Order and review a printed proof copy** before publishing to check line weight, margins and paper bleed‑through.

### Etsy printable downloads
- Provide **US Letter (8.5×11 in)** and **A4 (210×297 mm)** PDFs, 300 DPI, one design per page with a safe margin (home printers can't print to the edge).
- Optionally include a ZIP of individual PNGs. Keep files under Etsy's per‑file size limits.
- State the licence in the listing and PDF (e.g. personal/classroom use only; no resale or redistribution).

---

## 11. Compliance and marketplace rules

- **Amazon KDP AI disclosure:** KDP requires you to disclose **AI‑generated** content (text, images or translations), including **cover and interior artwork**, when publishing or republishing. Content created by an AI tool counts as AI‑generated even if heavily edited afterwards. Answer the AI questions in the KDP content step honestly.
- **Etsy:** Etsy's creativity standards allow items made with AI tools when the seller created the prompts and the result, but **AI use must be disclosed** in the listing (select the appropriate "made by" option and say so in the description). Check current Etsy policy before listing.
- **Intellectual property:** no copyrighted/trademarked characters, brand names, logos or obviously imitated franchise styles. This applies to art, title, subtitle, description and keywords. You are responsible for IP compliance even when an AI produced the image.
- **Generator terms:** confirm each tool, model and LoRA licence permits **commercial use** of outputs.
- **Child safety and offensive content:** keep all content gentle and age‑appropriate; marketplaces remove inappropriate or offensive children's content.
- **Quality and originality:** avoid near‑duplicate pages and copy‑paste books; KDP may remove titles that create a poor customer experience. Check KDP for any current limits on new title submissions.
- **Honest marketing:** no fake reviews, invented awards, "bestseller" claims or misleading page counts. Describe the number of **designs** and that pages are single‑sided.
- **Spelling and market:** use "coloring" in US listings/keywords and "colouring" for UK/AU/IN markets as appropriate.

---

## 12. Quick‑start summary

1. Choose theme, age band and trim; run the **planner prompt** (§4).
2. Lock the **character bible** and **style lock** text.
3. Generate each page with the **master prompt** (§5) plus your tool's adapter (§6).
4. Run the **QA checklist** (§9); fix or regenerate.
5. **Threshold, clean, size** to 300 DPI inside a 0.5 in safe margin (§10).
6. Lay out single‑sided with front matter; create the cover (§7).
7. Proof, disclose AI use, check IP, and publish (§11).

---

## Sources

- Amazon KDP Help: *Set trim size, bleed, and margins* (bleed 0.125 in; minimum margins by page count). <https://kdp.amazon.com/en_US/help/topic/GVBQ3CMEQW3W2VL6>
- Amazon KDP Help: *Content Guidelines* (AI‑generated content disclosure, IP, offensive content, poor customer experience). <https://kdp.amazon.com/en_US/help/topic/G200672390>
- Etsy Seller Handbook / Creativity Standards (AI disclosure for listings). <https://www.etsy.com/legal/creativity/>
- Image generator documentation: OpenAI image generation, Midjourney parameters (`--ar`, `--no`, `--style raw`, `--stylize`, character/style references), Google Gemini/Imagen, Stable Diffusion / Flux model cards and licences. Check each provider's current docs, as features change frequently.
