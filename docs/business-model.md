# Business Model: Wholesale of Computers, Computer Peripheral Equipment and Software

## Classification
- Repository: `cloud-itonami-isic-4651`
- ISIC Rev.5: `4651` -- wholesale of computers, computer peripheral
  equipment and software
- Domain: `downstream/computer-hardware-software-wholesale`
- Social impact: export-control compliance, data security, transparency
- Governor: `:tech-export-governor`
- License: AGPL-3.0-or-later

## Scope
This actor covers tech-order intake through per-jurisdiction
counterparty / export-control-classification / sanctions / denied-party
regulatory verification, physical hardware/peripheral dispatch OR
electronic technology release (including deemed-export releases of
controlled software/source code/technical data to a foreign national),
and invoice settlement for a computer-and-software wholesaler. It does
**not**, by itself, hold any export authorization, license, or operating
authority required to run a computer-and-software-wholesale business in
a given jurisdiction, perform the actual physical warehouse pick/pack or
the actual code-repository/access-control release mechanics, or judge
trading-book economics (fulfillment routing and trading-book
optimization is a follow-up slice, not this R0). Whoever deploys a live
instance supplies the jurisdiction-specific operating authority, the
real warehouse-management/ERP dispatch integration, the real release/
access-control integration, and bears that jurisdiction's liability --
the software supplies the governed, spec-cited, audited execution
scaffold so the operator does not have to build the compliance layer
from scratch.

## Customer
- regional and independent computer-hardware / peripheral / software
  wholesalers and value-added resellers (VARs)
- IT distributors and system integrators leaving closed trade-compliance
  / ERP SaaS
- enterprise buyers and channel partners who need an auditable, spec-
  cited, export-classification-verified trade record
- export-compliance officers and counsel who need a structured,
  queryable classification/license audit trail rather than a shared
  spreadsheet

## Offer
- tech-order intake and directory management (hardware / software /
  encryption-item, destination country, end-user, counterparty)
- per-jurisdiction contract / export-classification / sanctions /
  denied-party regulatory verification with an official spec-basis
  citation, naming the SPECIFIC classification-list provision (not
  merely 'this jurisdiction has an export-control law')
- physical dispatch (hardware) gated on full evidence, an actual ECCN
  classification on file, license authorization when required, a
  credit-cleared counterparty, contract-terms on file, and passed
  sanctions AND denied-party screening
- technology release (software/source code/technical data, including
  deemed-export releases) gated on the SAME checks, evaluated against
  the release's EFFECTIVE destination -- the recipient's own
  nationality for a deemed-export release, not the order's own shipment
  destination
- invoice settlement (custody / financial transfer) with double-invoice
  prevention
- evidence checklisting (credit-clearance record, contract/PO,
  sanctions-screening record, denied-party-screening record)
- classification, license and sanctions/denied-party exception workflows
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per trading desk / export-compliance seat
- support retainer with SLA
- ERP and accounts-receivable integration
- export-classification advisory referral fee (out of scope for this
  R0's governed actuation, but a natural monetization surface on top of
  the audited order book)

## The `:tech-export-governor` Decision Rule

This blueprint's `:itonami.blueprint/governor` is `:tech-export-
governor`. It is the single authority that stands between "computer
hardware could be dispatched" / "controlled technology could be
released" and "it is allowed to leave the wholesaler's control," and
between "an invoice could be settled" and "it is allowed to settle."
Every rule it enforces is traceable to the domain (Wholesale of
Computers, Computer Peripheral Equipment and Software, ISIC 4651) and to
the three `:social-impact` tags in `blueprint.edn` (`:export-control-
compliance`, `:data-security`, `:transparency`).

This is the rule the companion contract test
(`test/techtrade/governor_contract_test.clj`) encodes end-to-end: the
TechTradeAdvisor never dispatches hardware, releases technology, or
settles an invoice the Tech Export Governor would reject, `:delivery/
dispatch`/`:technology/release`/`:invoice/settle` NEVER auto-commit at
any phase, `:order/intake` (no direct capital/export-control risk) MAY
auto-commit when clean, and every decision (commit OR hold) leaves
exactly one ledger fact.

**Authorizes a physical dispatch (`:delivery/dispatch`), a technology
release (`:technology/release`), or an invoice settlement (`:invoice/
settle`) only when ALL of the following hold:**

1. **An official spec-basis citation exists for the jurisdiction** -- the
   governor will not authorize any `:classification/verify`, `:delivery/
   dispatch`, `:technology/release`, or `:invoice/settle` proposal whose
   jurisdiction has no entry in the `techtrade.facts` catalog
   (`:no-spec-basis`). This is the direct enforcement of `:transparency`:
   a jurisdiction whose export-control-CLASSIFICATION requirements
   cannot be traced to an OFFICIAL public source is never guessed.
2. **The jurisdiction's required evidence is fully on file** -- for a
   dispatch, release or invoice the order's jurisdiction must have been
   verified with a complete counterparty-diligence evidence checklist on
   record: the credit-clearance record, the contract / purchase order,
   the sanctions-screening (OFAC/equivalent) record, and the denied-
   party-screening (Entity List/Denied Persons List) record
   (`:evidence-incomplete`). Deliberately does NOT include the item's
   own ECCN classification -- that is checks 5/6 below.
3. **The counterparty's credit has been cleared** -- refuses to move real
   hardware or release real controlled technology when credit has NOT
   been cleared (`:credit-uncleared`). Evaluated at `:delivery/dispatch`
   and `:technology/release`.
4. **Contract-terms are on file** -- refuses to move real hardware or
   release real controlled technology against an undocumented trade
   (`:contract-missing`). Evaluated at `:delivery/dispatch` and
   `:technology/release`.
5. **The item has actually been classified against a real control
   list** -- the governor reads the dedicated `:eccn` fact and refuses
   to move the item when it has NEVER been classified at all (`:eccn`
   nil/blank -- distinct from `"EAR99"`, a REAL valid classification
   outcome) (`:eccn-classification-missing`). Evaluated at `:delivery/
   dispatch` and `:technology/release`.
6. **A classified item's required license is actually authorized** --
   WHEN the item IS classified AND that classification requires a
   license for the order's EFFECTIVE destination (the deemed-export-
   aware `techtrade.governor/effective-destination`, not necessarily the
   order's own `:destination-country`), a valid license or applicable
   License Exception must be on file (`:license-required-
   unauthorized`). Evaluated at `:delivery/dispatch` and `:technology/
   release`. **Checks 5 and 6 are TWO SEPARATE checks, not one boolean**
   -- see "Two genuinely different failure modes" below.
7. **The counterparty has passed OFAC / equivalent sanctions screening**
   -- an unresolved sanctions-screening flag is a HARD, un-overridable
   hold (`:counterparty-sanctions-flag-unresolved`). Evaluated
   UNCONDITIONALLY at `:delivery/dispatch`, `:technology/release` AND
   `:invoice/settle`.
8. **The counterparty/end-user has passed denied-party-list screening**
   -- an unresolved BIS Entity List / Denied Persons List (or
   equivalent) flag is a HARD, un-overridable hold (`:denied-party-
   list-flag-unresolved`), evaluated INDEPENDENTLY of check 7. Evaluated
   UNCONDITIONALLY at `:delivery/dispatch`, `:technology/release` AND
   `:invoice/settle`. **This check has no analog in any sibling** -- see
   "A dedicated denied-party check" below.
9. **The order has not already been dispatched/released, and the
   invoice has not already been settled** -- refused off dedicated
   `:dispatched?`/`:released?`/`:invoiced?` facts (never a `:status`
   value) (`:already-dispatched`/`:already-released`/`:already-
   invoiced`).

**Rejects (HOLD, un-overridable, never even reaches a human) when any of
the above fail.**

**Always escalates to a human (never auto-commits) for `:delivery/
dispatch`, `:technology/release` and `:invoice/settle`**, even when
every check above is clean -- a THREE-member high-stakes set (see
"Three actuation events, not two" below).

## Two genuinely different failure modes: unclassified vs. classified-but-unlicensed

The general-trading sibling (`cloud-itonami-isic-4690`, the sogo-shosha
archetype) models its OWN export-control exposure as a SINGLE boolean,
`:export-license-cleared?` -- coarse and jurisdiction-citation-based by
design, because a general/diversified trading house's defining exposure
is "has SOME export-control process been completed for this trade,"
evaluated at roughly the level of "does this jurisdiction have a real
export-control regime, and has this order gone through it." That is the
right level of resolution for a business that brokers steel today,
foodstuffs tomorrow, machinery the day after -- it never specializes
enough in any one item to reason about item-level classification detail.

A computer-and-software wholesaler is different. It specializes in
EXACTLY the category of goods (computers, peripherals, information-
security/encryption items) that real export-control regimes single out
for their own dedicated, technical classification list (EAR Category 4 /
Category 5 Part 2 in the US; the structurally equivalent categories in
JPN/DEU/GBR -- see `techtrade.facts`). For this vertical, "has export
control been handled" is not one fact -- it decomposes into two REAL,
sequential, and genuinely distinguishable questions a compliance officer
actually asks, in order:

1. **Has this item been classified against the control list at all?**
   An item that has never been through classification is not "probably
   fine" -- it is an open question the wholesaler cannot yet answer,
   full stop. This is `eccn-classification-missing`.
2. **GIVEN the classification, does this specific destination/end-user
   combination require a license, and if so is one on file?** This
   question cannot even be ASKED until question 1 has an answer -- but
   once it does, "classified as 5A002 (encryption hardware), license
   required for this destination, none on file" is a COMPLETELY
   different real-world posture from "never classified." The
   compliance officer has done real work (the classification), reached
   a real conclusion (a license is required), and the trade still
   cannot proceed. This is `license-required-unauthorized`.

Folding these into one boolean, as the general-trading sibling correctly
does for ITS coarser-grained vertical, would erase a distinction this
vertical's own regulatory structure actually makes -- and would erase it
from the AUDIT LEDGER too: a regulator or auditor reviewing this actor's
history needs to be able to tell "we never checked" apart from "we
checked, found a license was required, and shipped anyway without one"
-- these carry different enforcement postures under the EAR (49 U.S.C./15
C.F.R. Part 764 penalties for exporting without any required license
scale with the recipient's degree of knowledge/willfulness, and the
FACT PATTERN of "we classified it and knew a license was required" is
a materially worse showing than "we never got that far"). `techtrade.
governor` therefore keeps `eccn-classification-missing-violations` and
`license-required-unauthorized-violations` as TWO SEPARATE HARD checks,
proven genuinely distinct end-to-end by `test/techtrade/
governor_contract_test.clj`'s `eccn-classification-missing-is-held-and-
unoverridable` (to-5) and `license-required-unauthorized-is-a-
genuinely-different-failure-mode-from-eccn-classification-missing`
(to-6) -- each asserts the OTHER check's rule did NOT ALSO fire for the
same order.

### Encryption items (EAR Category 5 Part 2): folded in, not a third check

EAR Category 5 Part 2 (information security/encryption -- ECCNs like
5A002 hardware, 5D002 software, 5E002 technology) carries its own
sub-regime: License Exception ENC eligibility, mass-market treatment,
annual self-classification reporting to BIS. This build deliberately
does NOT add a third dedicated HARD check for encryption items.
Structurally, an encryption item's sub-regime is STILL a classify-then-
license-determine sequence -- "what ECCN does this encryption item
carry" (answered by check 5) then "does that ECCN require a license for
this destination, and if so is a valid license or License Exception
(such as ENC) on file" (answered by check 6). Adding a third check would
re-implement the SAME two-step logic under a different name rather than
capture a genuinely new failure mode. `techtrade.store/demo-data`'s
`to-6` (classified 5A002, license required, none authorized) and `to-10`
(classified 5D002 software, deemed-export release, license required,
none authorized) both exercise encryption items through the SAME
`license-required-unauthorized` check as any other controlled item,
proving no silent special-case bypass exists for this category.

## A dedicated denied-party check, distinct from generic sanctions screening

OFAC sanctions programs (the SAME `:sanctions-screened?` check every
sibling in this fleet carries) BLOCK TRANSACTIONS/DEALINGS with a
sanctioned party -- a Treasury-administered, essentially blanket
financial/dealing prohibition that applies regardless of what is being
traded. The BIS Entity List (15 C.F.R. Part 744, Supplement No. 4) and
Denied Persons List (15 C.F.R. Part 764) are a DIFFERENT regulatory
mechanism entirely: they restrict or prohibit EXPORTS OF ITEMS SUBJECT
TO THE EAR specifically, often with item-specific or license-specific
conditions (an Entity List "footnote 1 affiliates" listing, for
example, can impose an EAR license requirement on an otherwise-EAR99
item that generic OFAC screening would never flag, because EAR99 items
carry no OFAC nexus at all). A counterparty can clear OFAC screening
while still being Entity-Listed. Treating these as the same check would
silently drop real, export-control-specific risk a computer-and-
software wholesaler is squarely exposed to -- exactly the risk profile
that distinguishes this vertical from a generic trading house.
`techtrade.governor`'s `denied-party-list-flag-unresolved-violations`
is therefore its OWN dedicated HARD check, off the dedicated
`:denied-party-screened?` fact, evaluated UNCONDITIONALLY at all three
actuation ops -- proven distinct from generic sanctions screening by
`test/techtrade/governor_contract_test.clj`'s `denied-party-list-flag-
unresolved-is-a-genuinely-different-failure-mode-from-generic-
sanctions` (to-8, which explicitly passes OFAC screening while failing
denied-party screening).

## Three actuation events, not two: the deemed-export doctrine gets its own op

Every principal-trading sibling in this fleet performs exactly TWO
real-world actuation events: a physical dispatch, then an invoice
settlement. This vertical performs THREE:
`#{:delivery/dispatch :technology/release :invoice/settle}`.
`:technology/release` exists because, for software/source-code/
technical-data items, the controlled event is genuinely NOT always a
physical cross-border shipment. Under the deemed-export doctrine (15
C.F.R. §734.13), releasing controlled technology or source code to a
foreign national is DEEMED to be an export to that person's most recent
country of citizenship or permanent residency -- EVEN WHEN the release
happens entirely inside the exporting jurisdiction (a foreign national
engineer visiting the wholesaler's own domestic office, for example).
Modeling this as a variant of `:delivery/dispatch` would misrepresent
what actually happened: nothing physically crossed a border, no
shipment left a warehouse, no carrier was involved -- yet the release is
still, legally, an export requiring the same classification/license
scrutiny as if it had. `techtrade.governor/effective-destination`
encodes the doctrine directly: for a deemed-export order
(`:deemed-export?` true), it resolves to the release recipient's OWN
nationality (`:release-recipient-nationality`), not the order's
`:destination-country` -- so a deemed-export release cannot silently
evade classification/license scrutiny by pointing at an unconcerning
shipment destination while the real recipient is a foreign national
elsewhere. `test/techtrade/governor_contract_test.clj`'s
`deemed-export-license-check-reads-recipient-nationality-not-
destination-country` (to-10) proves this end-to-end: the order's own
`:destination-country` and its `:release-recipient-nationality` are
deliberately DIFFERENT values in the fixture, and the HARD hold fires
on the license check evaluated against the recipient's nationality.

## Required Technologies

`blueprint.edn`'s `:itonami.blueprint/required-technologies` for this business,
and what each one is actually load-bearing for here (not a generic capability
list):

| Technology | What it is FOR in Wholesale of Computers, Computer Peripheral Equipment and Software |
|---|---|
| `:robotics` | An automated storage-and-retrieval system (AS/RS) / goods-to-person robotic shuttle that picks and stages ESD-safe computer-hardware/peripheral cartons at the wholesale distribution center for `:delivery/dispatch`. Applies ONLY to the physical-dispatch path -- `:technology/release` has no analogous physical act. The governor never dispatches hardware itself: a dispatch-clearing action must have cleared the same sign-off a human trading supervisor would need (see Robotics Premise). |
| `:identity` | Trader, export-compliance-officer, and counterparty identity plus role-based access, so the governor's sign-off is tied to *who* authorized a dispatch, release, or invoice, not just *that* someone did. |
| `:forms` | Structured intake for tech-order booking, per-jurisdiction evidence capture (credit-clearance record, contract/PO, sanctions-screening record, denied-party-screening record), and classification / license / sanctions / denied-party exception submission -- the data the Decision Rule above actually evaluates comes in through these forms. |
| `:dmn` | Encodes the `:tech-export-governor` Decision Rule itself (spec-basis, evidence completeness, credit-clearance, contract-on-file, ECCN classification, license authorization, sanctions-screening, denied-party-screening, the triple double-actuation guards, the actuation gate) as an evaluable decision table rather than code buried in application logic -- this is what makes the governor auditable and swappable per-deployment. |
| `:bpmn` | Orchestrates the intake -> verify -> dispatch-OR-release -> settle -> audit loop end-to-end (see `docs/operator-guide.md`) across tech-order intake, classification verification, physical dispatch or technology release, and invoice settlement, including the classification/license/sanctions/denied-party escalation gates. |
| `:audit-ledger` | The immutable record of every verification, dispatch, release, invoice, classification flag, license flag, sanctions flag, denied-party flag, and hold -- this is what "an auditable, spec-cited trade record for every dispatch, release and invoice" (Trust Controls, below) actually means in practice, and the evidence an operator needs if a dispatch, release or invoice is later disputed by a counterparty, auditor, or regulator (including BIS). |
| `:optimization` | Fulfillment routing and trading-book optimization -- selects the profitable fulfillment strategy across the order book. This R0 build deliberately scopes optimization OUT (see README `Business-process coverage`); the capability is correctly marked required, the integration is a follow-up slice. |

There is NO bespoke `:techtrade` capability library in this stack: the
tech-trading checks (credit-clearance, contract-on-file, ECCN
classification, license authorization, sanctions-screening, denied-
party-screening) are direct entity boolean/value reads in `techtrade.
governor`, on top of the generic robotics/identity/forms/dmn/bpmn/
audit-ledger stack (see Capability layer).

## Trust Controls
- a jurisdiction with no official spec-basis can never be verified,
  dispatched, released, or invoiced against
- a dispatch or release never starts with incomplete counterparty-
  diligence evidence
- a dispatch or release never starts with an uncleared counterparty
  credit, no contract-terms on file, an item never classified against a
  control list, a classified item requiring a license for its
  destination/end-user with none authorized, an unresolved sanctions-
  screening flag, or an unresolved denied-party-list flag
- an invoice never settles against an unresolved sanctions-screening or
  denied-party-list flag
- classification / license / sanctions / denied-party flags cannot be
  silently suppressed
- the same order can never be dispatched, released, or invoiced twice
- a dispatch, release, or invoice never auto-commits; all three always
  need a human trading supervisor / export-compliance officer
- every dispatch, release and invoice (commit OR hold) leaves exactly
  one immutable ledger fact
- counterparty, credit, classification, license, and sanctions/denied-
  party data stays outside Git

## Jurisdiction coverage (honest)

`techtrade.facts/catalog` currently seeds 4 jurisdictions with an
official spec-basis, each citing the SPECIFIC classification-list
provision (not merely a general export-control statute): the United
States (Bureau of Industry and Security, Export Administration
Regulations, 15 C.F.R. Parts 730-774 -- Commerce Control List Category 4
(Computers) and Category 5 Part 2 (Information Security); Commerce
Country Chart, 15 C.F.R. Part 738; License Exceptions, Part 740; Entity
List, Part 744 Supplement No. 4; Denied Persons List, Part 764; the
deemed-export rule, 15 C.F.R. §734.13), Japan (METI 貿易経済協力局 安全
保障貿易管理課, 輸出貿易管理令別表第一 (Appended Table 1), 該非判定
(gaihi-hantei) classification, キャッチオール規制 catch-all control),
Germany (BAFA enforcing Regulation (EU) 2021/821 Annex I Category 4 and
Category 5 Part 2, plus the 2021 recast's Article 5 cyber-surveillance
catch-all), and the United Kingdom (ECJU, Export Control Order 2008,
UK Strategic Export Control Lists Category 4 and Category 5 Part 2).
This is a starting catalog to prove the governor contract end-to-end,
not a claim of global coverage (4 of ~194 jurisdictions worldwide).
Adding a jurisdiction is additive: one map entry in `techtrade.facts/
catalog`, citing a real official source -- never fabricate a
jurisdiction's requirements to make coverage look bigger.

**Honest uncertainty flag for independent verification**: this build is
confident about the CCL Category 4/Category 5 Part 2 structure, the
EAR99/ECCN classification mechanic, the Commerce Country Chart/License
Exception/Entity List/Denied Persons List existence and general
function, and the 15 C.F.R. §734.13 deemed-export citation (all from
training-knowledge, no live web access -- the same "no live web access,
cite only what is confident from training knowledge" discipline every
sibling's own jurisdiction catalog follows). Specific CURRENT License
Exception eligibility rules, the precise current text of any Country
Chart cell, and the precise current membership of the Entity List/
Denied Persons List change frequently and are NOT things this build
verifies -- an operator must confirm current BIS guidance before relying
on this catalog for a real classification/license determination.

## Maturity

`:implemented` -- `TechTradeAdvisor` + `Tech Export Governor` run as
real, tested code (`clojure -M:dev:test`: 46 tests / 265 assertions, 0
failures; lint clean), following the SAME governed-actor architecture as
the other prior actors across this fleet, with its own distinct,
independently-named governor and its own direct-entity-boolean/value
tech-trading checks. See `docs/adr/0001-architecture.md` for the design.

## Robotics Premise

`blueprint.edn` sets `:itonami.blueprint/robotics true` -- a deliberate,
vertical-specific call, reasoned separately for the TWO structurally
different actuation paths this vertical has (unlike every prior
sibling's single uniform dispatch mechanism):

- **`:delivery/dispatch` (physical hardware/peripherals)**: real
  computer-hardware/peripheral wholesale distribution centers commonly
  run automated storage-and-retrieval systems (AS/RS) -- goods-to-person
  robotic shuttles that pick and stage cartons -- with ESD-safe
  (electrostatic-discharge-safe) handling a genuinely load-bearing,
  well-precedented concern for electronic components specifically
  (unlike bulk commodities). This is directly analogous to the metal-
  wholesale sibling's automated crane/stacker-reclaimer citation and the
  fuel-wholesale sibling's loading-rack/valve robot citation. The
  governor never dispatches hardware itself: a dispatch-clearing action
  must have cleared the same sign-off a human trading supervisor would
  need.
- **`:technology/release` (software/source code/technical data,
  including deemed-export releases)**: has NO analogous physical
  robotic act at all -- an electronic release, a code-repository grant,
  or a briefing to a visiting engineer is digital/paperwork/access-
  control work, closer in kind to the general-trading sibling's
  logistics-coordination-referral scope disclaimer (`:robotics false`)
  or the company-incorporation sibling's pure digital/paperwork
  reasoning.

Because a meaningful share of this vertical's real-world dispatch volume
genuinely is physical hardware/peripheral movement through an automated
warehouse, `:robotics true` is the honest call for the vertical AS A
WHOLE -- but this is documented here as a MIXED, path-specific claim
rather than a blanket one, so it is never mistaken for a claim that
`:technology/release` itself involves any robotic act. A robot may stage
a carton at the dock, but only after the governor and a human supervisor
both agree it is safe to -- the same operating-state-machine-gated-by-
governor premise every cloud-itonami vertical restates (ADR-2607011000).
