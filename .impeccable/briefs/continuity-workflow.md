# Unified customer and staff workflow

Mode: Operate. Scope: frontend workflow refinement, retaining the existing visual system. No backend implementation changes.

THESIS: A customer understands a change before answering; a staff member reviews that same response and evidence.

OWN-WORLD: Keep the existing white banking surfaces, navy text/actions, quiet sage help surfaces, and amber only for facts needing customer context. Use the established Pretendard family and accessible controls.

STORY: Initial or changed preferences are separate from each alert. Change/evidence → customer response → confirmed bank handoff → authorized staff review → guidance or closure → internal follow-up.

FIRST VIEWPORT: Identify the current task and expose the change selection quickly. Do not duplicate the page title with a large promotional hero. On the staff surface, customer response and comparison facts share the primary reading region.

FORM: Code-led refinement of the incumbent Operate UI; no new visual world, concept roll, comp, raster, or external design generation. The existing source and user requests pin this direction.

TRUTH: Backend controller/service contracts and API documentation determine states, role access, data and available actions. Operational member and public demo APIs remain separate. Shared intent comes only from the staff summary endpoint. The UI never invents automatic detection promotion, immediate staff delivery, free-text opinion access or customer-visible staff progress.

FINISH: Inspect customer/staff desktop and mobile together, plus large text and high contrast. Mutation contracts are tested with mocks; live synthetic data is read without changing customer responses or case states.
