# Operator Guide

## First Deployment
1. Register traders, export-compliance officers, tech-orders, and
   counterparties.
2. Import tech-order, counterparty, credit, classification, license, and
   sanctions/denied-party-screening history.
3. Seed the per-jurisdiction spec-basis catalog (`techtrade.facts`) for
   the jurisdictions you actually trade in, citing real official
   classification-list sources only.
4. Run read-only spec-basis validation per jurisdiction.
5. Configure classification / license / sanctions / denied-party
   escalation and accounts-receivable accounts.
6. Publish a dry-run dispatch/release/invoice and audit export.

## Minimum Trading Controls
- spec-basis validation before any verification, dispatch, release, or
  invoice
- full counterparty-diligence evidence (credit-clearance record,
  contract/PO, sanctions-screening record, denied-party-screening
  record) before any dispatch or release
- an actual ECCN (or equivalent) classification on file before any
  dispatch or release -- never inferred, never defaulted
- for a classified item, an actual license or applicable License
  Exception on file whenever the classification requires one for the
  order's EFFECTIVE destination (see `techtrade.governor/effective-
  destination` for the deemed-export-aware resolution)
- credit-clearance, contract-on-file, classification, license, sanctions
  and denied-party-list checks before any dispatch or release; sanctions
  and denied-party checks before any invoice
- classification / license / sanctions / denied-party escalation gate
- audit export for every dispatch, release, invoice, and hold
- backup manual dispatch, release, and invoicing process

## A Day in the Life: Intake → Verify → Dispatch-OR-Release → Settle → Audit

Wholesale of Computers, Computer Peripheral Equipment and Software
(ISIC 4651, `cloud-itonami-isic-4651`) runs on the same intake / advise
/ govern / decide / commit-or-hold loop as every itonami blueprint, but
here the loop is concrete: a regional computer-and-software wholesaler
needs to bring a tech-order (say, a 24-unit rack-server sale to a data
centre in the UK) from intake through classification verification to a
physical dispatch and an invoice settlement -- OR, for a software
release to a visiting foreign-national engineer, through a technology
release instead. Walking through both paths, end to end:

1. **Intake.** The trader books the tech-order through `:forms`:
   order-id, item-description, item-type (hardware / software /
   encryption-item), delivery-mode (physical-shipment / technology-
   release), destination-country, end-user, counterparty, price,
   contract-terms, and the order's own diligence record (credit-
   cleared?, sanctions-screened?, denied-party-screened?). This creates
   a tech-order record at `:order/intake` status. The TechTradeAdvisor
   only normalizes the patch; it does not invent the order-id,
   counterparty, destination, item-type, or any commercial/diligence
   value.
2. **Verify.** The TechTradeAdvisor drafts a per-jurisdiction GENERIC
   counterparty-diligence evidence checklist (`:classification/verify`)
   from `techtrade.facts`, citing the jurisdiction's official spec-basis
   (owner authority, legal basis, the SPECIFIC classification-list
   provision, provenance) and listing the required evidence
   (credit-clearance record, contract/PO, sanctions-screening record,
   denied-party-screening record). The `:tech-export-governor` sign-off
   gate must clear: it checks the jurisdiction actually has an official
   spec-basis on file (never invent one). A jurisdiction with no
   spec-basis is a HARD hold at the governor node -- it never even
   reaches a human. This verification always escalates to a human for
   approval; it is never auto. Separately (outside this actor, by a
   real export-compliance function), the item is classified against the
   real control list and the `:eccn`/`:license-required?`/`:license-
   authorized?` facts are recorded on the order -- the governor
   independently re-reads these at dispatch/release time rather than
   trusting the advisor's own summary of them.
3. **Dispatch (hardware) OR Release (software/technology).** Before real
   computer hardware/peripherals can leave the wholesaler's control, or
   before real controlled software/source code/technical data can be
   released to an end-user, the `:tech-export-governor` sign-off gate
   runs the full HARD check set against the order's own ground truth:
   the spec-basis exists, the evidence checklist is complete, the
   counterparty's credit has been cleared, contract-terms are on file,
   the item HAS been classified against a real control list
   (`:eccn-classification-missing` fires if not), a classified item's
   required license IS authorized when one is required
   (`:license-required-unauthorized` fires if not -- evaluated against
   the release's EFFECTIVE destination, which for a deemed-export
   release is the RECIPIENT'S nationality, not the order's own shipment
   destination), the counterparty has passed sanctions screening, the
   counterparty/end-user has passed denied-party-list screening, and the
   order has not already been dispatched/released. Any failure is a HARD
   hold that a human cannot override. If every check is clean, the
   proposal STILL always escalates to a human trading supervisor /
   export-compliance officer -- neither `:delivery/dispatch` nor
   `:technology/release` ever auto-commits at any phase. On approval,
   the dispatch record (`<JURISDICTION>-DISPATCH-000001`) or release
   record (`<JURISDICTION>-RELEASE-000001`) is drafted and the order's
   `:dispatched?`/`:released?` flag is set.
4. **Settle.** Once the order has actually been fulfilled (dispatched OR
   released), the invoice is settled (`:invoice/settle`): the money side
   of the trade, custody / financial transfer. The governor re-checks
   the spec-basis, the evidence completeness, the sanctions screening,
   the denied-party screening, and that this order's invoice has not
   already been settled. As with the dispatch/release, a clean invoice
   STILL always escalates to a human -- `:invoice/settle` never
   auto-commits. On approval the invoice record is drafted
   (`<JURISDICTION>-INVOICE-000001`) and the order's `:invoiced?` flag
   is set.
5. **Audit.** The verification, the dispatch/release sign-off, the
   dispatch/release record, the invoice sign-off, and the invoice record
   are all appended to the `:audit-ledger` -- immutable and exportable,
   so a counterparty, auditor, or regulatory (including BIS) dispute can
   be traced back to the exact spec-basis citation, evidence checklist,
   classification, license basis, and supervisor sign-off that
   authorized the dispatch/release and invoice. If something is wrong
   with the counterparty or the item (a credit deterioration, a
   sanctions or denied-party hit, a classification never completed, a
   license required and missing), that gets raised as a flag and routed
   through the escalation gate instead of being silently suppressed --
   a dispatch/release for that order then waits on governor sign-off of
   the flag's resolution.

Any deviation from this loop is exactly what the Trust Controls in
`docs/business-model.md` exist to catch: an order verified against a
fabricated spec-basis, a dispatch/release started with incomplete
evidence, an uncleared counterparty credit or a contract gap, an item
dispatched with NO classification on file, a classified item dispatched
without an authorized license when one was required, sanctions or
denied-party screening suppressed to force a dispatch/release through,
or an invoice posted without a human sign-off.

## Feel the Decision Gate: `clojure -M:dev:run`

This vertical has no companion playable prototype. The fastest hands-on
way to feel why the `:tech-export-governor` gate exists -- and why it
carries TWO checks where the general-trading sibling carries one -- is
the bundled demo, which walks a clean hardware order through intake →
verify → dispatch → settle, a clean deemed-export software release
through intake → verify → release → settle (each dispatch/release/
settle pausing for human approval), and then exercises every HARD-hold
failure mode in isolation:

- a jurisdiction with no official spec-basis → HOLD (`:no-spec-basis`),
- a counterparty whose credit has not been cleared → HOLD
  (`:credit-uncleared`),
- an order with no contract-terms on file → HOLD (`:contract-missing`),
- an item that has NEVER been classified against a control list at all
  → HOLD (`:eccn-classification-missing`),
- a DIFFERENT, classified item (ECCN 5A002) whose destination requires a
  license with none authorized → HOLD (`:license-required-
  unauthorized`) -- proving these are two genuinely separate failure
  modes,
- a counterparty that has not passed OFAC-style sanctions screening →
  HOLD (`:counterparty-sanctions-flag-unresolved`),
- a DIFFERENT counterparty that has not passed denied-party-list
  (Entity List/Denied Persons List) screening → HOLD (`:denied-party-
  list-flag-unresolved`) -- proving this is ALSO a distinct failure mode
  from generic sanctions screening,
- a deemed-export software release whose classification requires a
  license for the RECIPIENT'S nationality (not the order's own shipment
  destination) with none authorized → HOLD (`:license-required-
  unauthorized`, via `effective-destination`),
- a double dispatch of the same order → HOLD (`:already-dispatched`),
- a double release of the same order → HOLD (`:already-released`),
- a double invoice of the same order → HOLD (`:already-invoiced`).

Each HOLD settles at the governor node and never reaches a human
approver -- the same failure mode the audit ledger is built to catch and
the minimum trading controls above are built to prevent. It is not a
substitute for those controls, but it is the fastest way for a new
operator (or a reviewer) to feel, hands-on, why the gate exists before
touching a real deployment.

## Certification
Certified operators must prove spec-basis-grounded verification,
evidence-backed dispatch/release readiness (credit-clearance,
contract-on-file, ECCN classification, license authorization when
required, sanctions screening, denied-party screening), and human review
for every dispatch-, release-, and invoice-affecting action.
