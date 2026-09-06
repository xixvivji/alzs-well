# Staff review workbench

Mode: Operate. Code-led refinement of the existing white/navy staff portal.

THESIS: Understand the customer's response and change evidence, decide guidance or closure, then record follow-up without losing the same case context.

SOURCE: User-supplied `2026_금융_AI_Challenge_기획서_수정본.pdf` (2026-09-06), pages 2, 3 and 5, defines the product intent. Current `OperationalCaseController`, `OperationalCaseService`, `DeterministicCopilotAdapter` and `FINAL_BACKEND_API_SPEC.md` sections 6.7.1–6.7.5 remain authoritative for implementation. The PDF's Bedrock draft description is not evidence of a callable member-case API in this checkout.

FORM: A focused case detail replaces the expanded queue once selected; return to the unchanged queue with a visible action. Keep the case, customer response, baseline/current comparison and actual task status in a compact common header. Three accessible work areas: customer/evidence, review/decision, follow-up/history. These are navigation tabs, not claims of completed workflow steps. Desktop and mobile keep the same reading order. No new palette, raster assets, MagicPath exploration or alternate visual world is needed.

HIERARCHY: Required facts and customer response never hide behind technical disclosures. Shared intent remains explicitly fetched with server permission checks. Relevant backend template questions support review; search, note entry, source IDs and audit history are secondary. Guidance approval and case closure are distinct choices; approval retains its two-step confirmation. Follow-up results belong to their own schedule, not a shared field beneath unrelated rows.

VISUAL SEPARATION REFINEMENT: Per the user's clarification, distinguish areas through visible background and heading colors, not source badges or explanatory copy. Customer responses/consented intent use green #e7f2eb with dark green headings. Staff notes, guidance, closure and follow-up use blue #e9eff9 with navy headings. Observations, automatic history and reference material stay neutral. Retain descriptive task headings for non-color recognition and saved/draft context. All existing operations, states, permissions and tab navigation remain unchanged; never invent authors, generation claims or data.

REMOVE: No channel-switch, side-safety, prototype-role-switch or links/automatic fallback into /demo in the ordinary customer/staff journey. Legacy demo routes and backend functionality remain intact.

VERIFY: Build and lint, backend-contract regression tests, rendered state/permission tests, keyboard tabs, live read-only case detail on desktop/mobile/large-text/high-contrast. No live case mutations. At most two batched visual QA rounds.
