# ADR-0001: TechTradeAdvisor ⊣ Tech Export Governor architecture

## Status

Accepted. `cloud-itonami-isic-4651` published directly as `:implemented`
in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-4651` publishes an OSS business blueprint for
wholesale of computers, computer peripheral equipment and software
(tech-order intake, per-jurisdiction contract / export-control-
classification / sanctions / denied-party regulatory verification,
physical dispatch or technology release, and invoice settlement). Like
every prior actor in this fleet, the blueprint alone is not an
implementation: this ADR records the governed-actor architecture that
establishes it as real, tested code, following the same langgraph
StateGraph + independent Governor + Phase 0->3 rollout pattern
established by `cloud-itonami-isic-6511` (life insurance) and applied
across the PRINCIPAL wholesale-trading siblings: `cloud-itonami-isic-
4671` (fuel wholesale, single-commodity excise/sanctions focus),
`cloud-itonami-isic-4690` (general/diversified wholesale trading,
coarse multi-commodity export-control/sanctions focus -- this build's
closest thematic cousin, and the sibling this build most deliberately
differentiates from), `cloud-itonami-isic-4662` (metal wholesale,
metal-type-gated conflict-minerals provenance), `cloud-itonami-isic-
4641` (textile wholesale, jurisdiction-gated forced-labor rebuttable
presumption), and `cloud-itonami-isic-4669` (waste wholesale,
hazard-classification-gated bilateral Prior Informed Consent).

ISIC 4651 is a PRINCIPAL trading model like all five siblings above --
the wholesaler takes title and resells. Its defining regulatory
exposure is genuinely more specific and technical than the general-
trading sibling's own export-control check: a computer-and-software
wholesaler specializes in EXACTLY the category of goods (computers,
peripherals, information-security/encryption items) that real export-
control regimes single out for their own dedicated, technical
classification list (the US Commerce Control List's Category 4/
Category 5 Part 2; the structurally equivalent JPN/DEU/GBR lists). This
build's defining design decision (Decision 4) is splitting what the
general-trading sibling correctly folds into ONE boolean
(`:export-license-cleared?`) into TWO genuinely distinct HARD checks --
see Decision 4 for the full reasoning. This build's second defining
design decision (Decision 3) is a THREE-member actuation set, not two --
the deemed-export doctrine (15 C.F.R. §734.13) means the controlled
event for software/technology items is not always a physical
cross-border shipment.

Like every principal-trading sibling, this vertical has NO bespoke
domain capability library in `kotoba-lang` to wrap (verified: no
`kotoba-lang/techtrade`-style repo exists, and `kotoba-lang/robotics` is
the generic cross-cutting robotics contract every cloud-itonami vertical
already uses, not a domain-specific library for this vertical). This
build therefore uses self-contained domain logic. The tech-trading
checks (credit-clearance, contract-on-file, ECCN classification,
license authorization, sanctions-screening, denied-party-screening) are
direct entity boolean/value reads in `techtrade.governor`, off dedicated
`:credit-cleared?` / `:contract-terms` / `:eccn` / `:license-required?`
/ `:license-authorized?` / `:sanctions-screened?` / `:denied-party-
screened?` facts on the `tech-order` record -- NO pure range-check
functions are needed.

This blueprint's own `:itonami.blueprint/governor` keyword,
`:tech-export-governor`, is grep-verified UNIQUE among the actor fleet
repos checked (no `tech-export`/`compute-trading` match via GitHub code
search across the `cloud-itonami` org at build time) -- no
naming-collision precedent question, a fresh independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:tech-export-governor` is grep-verified unique via GitHub code search
across the `cloud-itonami` org at build time (no `techtrade`/
`tech-export-governor`/`compute-trading-governor` match). This build
follows the SAME governed-actor architecture as every prior actor, but
with its own distinct governor identity.

### Decision 2: self-contained domain logic, direct entity booleans/values (no `kotoba-lang/techtrade` to wrap, and no range-check functions to host)

Like the fuel-wholesale, general-trading, metal-wholesale, textile-
wholesale and waste-wholesale siblings, this computer-and-software-
wholesale vertical needs no range-check functions: there is no
pre-existing tech-trading capability library to delegate to, AND the
governor's domain checks are direct entity boolean/value reads off the
`tech-order` record's own dedicated facts -- not measured-value-vs-limit
range comparisons. So `techtrade.registry` is RECORD CONSTRUCTION ONLY
(now for THREE record kinds -- dispatch, release, invoice -- rather than
every prior sibling's two; see Decision 3), and `techtrade.governor`
reads the order's booleans/values directly.

### Decision 3: THREE-member actuation set -- `:delivery/dispatch`, `:technology/release`, `:invoice/settle`; the deemed-export doctrine gets its own op, not a variant of dispatch

Every principal-trading sibling before this one performs exactly TWO
real-world actuation events, SEQUENTIALLY on the same order entity: a
physical dispatch, then an invoice settlement. This build's `high-
stakes` set has THREE members --
`#{:delivery/dispatch :technology/release :invoice/settle}` -- a fleet
first.

**Why a genuinely new op, not a `:delivery/dispatch` variant or a
`:kind`-tagged single op.** The deemed-export rule (15 C.F.R. §734.13)
means that, for controlled software / source code / technical data,
release to a foreign national is DEEMED an export to that person's most
recent country of citizenship or permanent residency -- REGARDLESS of
where the release physically happens, including entirely inside the
exporting jurisdiction itself (a foreign-national engineer visiting the
wholesaler's own domestic office and being briefed on controlled source
code, for example). No carrier is involved, nothing crosses a customs
border, no warehouse dispatch occurs -- yet the release is, legally, an
export requiring the SAME classification/license scrutiny as a physical
shipment. Modeling this as a `:delivery/dispatch` proposal with a
`:deemed-export? true` flag would be honest about the FACTS but
dishonest about the ACT: `:delivery/dispatch`'s own name, and every
`:robotics`-premised sibling precedent's reasoning about what that op
IS, presuppose a physical handoff. Three options were considered:

- **Option A (rejected): model deemed export as a `:delivery/dispatch`
  variant**, distinguished only by a `:deemed-export?` flag on the same
  op. Rejected: this would force the governor's dispatch-time checks to
  branch internally on a flag rather than have the op ITSELF honestly
  name what kind of real-world act is happening, and would mean
  `:delivery/dispatch`'s own docstrings/tests/robotics-premise reasoning
  (which legitimately assume a physical warehouse handoff for THIS
  vertical -- see Decision 10) would need constant hedging for the
  deemed-export case that does not physically dispatch anything.
- **Option B (rejected): a single `:kind`-distinguished op** (matching
  the retail sibling's `order` shape), with `:kind :physical` vs.
  `:kind :deemed-export` as alternative actions on the SAME `:fulfill`
  op. Rejected: unlike the retail sibling's genuinely alternative
  actions (an order is EITHER shipped OR picked up, never both, and the
  choice does not change WHAT regulatory question is being asked), this
  vertical's dispatch and release are not just alternative fulfillment
  MODES of the same regulatory question -- they are two different real-
  world acts (a physical customs-border crossing vs. an electronic/
  documentary release potentially with no border crossing at all) that
  happen to be gated by an overlapping but not identical check set
  (both need credit/contract/classification/license/sanctions/denied-
  party checks; only `:delivery/dispatch` is reasoned about under the
  robotics premise).
- **Option C (chosen): two distinct ops sharing the SAME dispatch-time
  check set**, `#{:delivery/dispatch :technology/release}`
  (`techtrade.governor/dispatch-or-release`), each with its own
  dedicated double-actuation guard (`:dispatched?`/`:released?`), each
  named honestly for the real-world act it represents. A single
  `tech-order` uses EITHER path (via `:delivery-mode`), never both --
  `:item-type :hardware` orders go through `:delivery/dispatch`;
  `:item-type :software`/`:encryption-item` orders whose `:delivery-
  mode` is `:technology-release` go through `:technology/release`
  (including deemed-export releases, `:deemed-export? true`). This
  keeps every check function's own meaning honest (a `:delivery/
  dispatch` proposal really is a physical warehouse act; a `:technology/
  release` proposal really is an electronic/documentary one) while
  still sharing the identical credit/contract/classification/license/
  sanctions/denied-party check logic via the shared
  `dispatch-or-release` set, so no check had to be duplicated.

### Decision 4: `eccn-classification-missing` and `license-required-unauthorized` -- TWO separate HARD checks, not one boolean; the defining design decision of this build

This is the decision that most distinguishes this vertical from the
general-trading sibling (`cloud-itonami-isic-4690`), and it required
splitting a single check into two rather than merely adding a new
gating axis the way the metal-wholesale/textile-wholesale/waste-
wholesale siblings' own domain-defining checks do.

`shosha.governor`'s `export-license-uncleared-violations` reads ONE
boolean, `:export-license-cleared?` -- correct for that sibling's own
scope: a general/diversified trading house brokers steel today,
foodstuffs tomorrow, machinery the day after, and never specializes
enough in any one item category to reason about item-level
classification detail. "Has SOME export-control process been completed"
is the right resolution for that business.

A computer-and-software wholesaler cannot honestly collapse its own
exposure the same way, because real export-control regimes treat
computers and information-security/encryption items as their OWN
dedicated technical classification category (EAR CCL Category 4/
Category 5 Part 2, and the structurally equivalent JPN/DEU/GBR lists --
see `techtrade.facts`). A compliance officer at a computer wholesaler
asks two REAL, SEQUENTIAL, and genuinely distinguishable questions, not
one:

1. Has this item been classified against the control list AT ALL? An
   item that was never run through classification is not "probably
   fine" -- it is an open question the wholesaler cannot yet answer.
2. GIVEN the classification, does the specific destination/end-user
   combination require a license under it, and if so is a valid license
   or applicable License Exception on file? This question cannot even
   be asked until question 1 has an answer -- but once it does,
   "classified as 5A002, license required for this destination, none on
   file" is a materially different real-world (and enforcement) posture
   from "never classified at all."

Three design options were considered:

- **Option A (rejected): keep the general-trading sibling's single
  `:export-license-cleared?` boolean**, treating "not yet classified"
  and "classified but unlicensed" as the same failure. Rejected: this
  would erase a distinction the domain itself makes, AND erase it from
  the audit ledger -- a regulator or auditor needs to be able to tell
  "we never checked" apart from "we checked, found a license was
  required, and shipped anyway without one." Under the EAR (15 C.F.R.
  Part 764), enforcement posture scales with the exporter's degree of
  knowledge; a compliance officer who classified an item and KNEW a
  license was required presents a materially worse fact pattern than
  one who never got that far -- an audit trail that cannot distinguish
  the two is dishonest about what actually happened.
- **Option B (rejected): fold the classification/license evidence into
  the generic per-jurisdiction `evidence-incomplete-violations`
  checklist**, the way `techtrade.facts/catalog` folds credit-clearance/
  contract/sanctions/denied-party evidence. Rejected: unlike those four
  items (which are genuinely per-jurisdiction procedural requirements,
  the same shape regardless of what is being traded), classification
  and license status are properties of the ITEM and its DESTINATION, not
  properties of "which jurisdiction's paperwork process applies" -- and
  folding a two-step determination into a single checklist item would
  reproduce Option A's information loss inside a checklist instead of a
  boolean.
- **Option C (chosen): two SEPARATE dedicated HARD checks**,
  `eccn-classification-missing-violations` (fires when `:eccn` is
  nil/blank -- distinct from `"EAR99"`, a REAL valid classification
  outcome meaning 'reviewed, not on the control list') and
  `license-required-unauthorized-violations` (a NO-OP unless the item IS
  classified, so the two checks never double-fire for the same
  underlying gap; fires when classified AND `:license-required?` true
  AND `:license-authorized?` false, evaluated via `effective-
  destination` -- see Decision 3 -- to be deemed-export-aware). This is
  the ONLY design in this fleet's wholesale-trading cluster that splits
  its domain-defining concern into two checks rather than folding
  multiple sub-facts into one (contrast Decision 5 below, and the
  metal-wholesale/textile-wholesale/waste-wholesale siblings' own
  fold-two-sub-facts-into-one-rule precedent) -- because here the two
  facts are NOT two evidentiary arms of the SAME determination (as
  chain-of-custody + smelter-certification are for conflict-minerals
  provenance), they are two SEQUENTIAL, dependent, and separately
  actionable determinations. `test/techtrade/governor_contract_test.clj`'s
  `eccn-classification-missing-is-held-and-unoverridable` (to-5) and
  `license-required-unauthorized-is-a-genuinely-different-failure-mode-
  from-eccn-classification-missing` (to-6) each assert the OTHER rule
  did NOT also fire for the same order, proving the split is real, not
  cosmetic.

**Encryption items (EAR Category 5 Part 2): folded into the general
mechanic, deliberately not a third check.** EAR Category 5 Part 2
carries its own sub-regime (License Exception ENC eligibility,
mass-market treatment, self-classification reporting), but structurally
this is STILL a classify-then-license-determine sequence -- exactly what
checks 5/6 already evaluate (an encryption item's ECCN is still just an
ECCN; its License Exception is still just a form of license
authorization). Adding a third HARD check specifically for encryption
items would re-implement the SAME two-step logic under a new name
without capturing a genuinely new failure mode -- unlike Decision 4's
split (which captures two REAL sequential questions), a third
encryption-specific check would not correspond to a third REAL question
a compliance officer asks that isn't already "classified?" then
"licensed for this classification?". `techtrade.store/demo-data`'s
`to-6` (5A002 hardware) and `to-10` (5D002 software, deemed-export)
both exercise encryption items through the SAME two checks as any other
controlled item, with no silent special-case bypass.

### Decision 5: `denied-party-list-flag-unresolved` -- a NEW dedicated check, distinct from generic sanctions screening; the fleet's first split of "screening" into two mechanisms

Every prior sibling's governor carries exactly one screening check,
`counterparty-sanctions-flag-unresolved-violations`, reused verbatim
(the SAME open-flag-unresolved discipline) across this fleet. This
build adds a SECOND, genuinely distinct screening check specific to
export control: `denied-party-list-flag-unresolved-violations`, off a
dedicated `:denied-party-screened?` fact, deliberately NOT folded into
`:sanctions-screened?`.

OFAC sanctions programs (the mechanism every sibling's own
`:sanctions-screened?` check re-verifies) are a Treasury-administered,
essentially blanket prohibition on transactions/dealings with a
sanctioned party, applying regardless of what is being traded. The BIS
Entity List (15 C.F.R. Part 744, Supplement No. 4) and Denied Persons
List (15 C.F.R. Part 764) are a DIFFERENT regulatory mechanism: they
restrict or prohibit EXPORTS OF ITEMS SUBJECT TO THE EAR to named
parties specifically, sometimes with item-specific or license-specific
conditions (an Entity List "footnote 1 affiliates" listing, for
example, can impose an EAR license requirement on an otherwise-EAR99
item that carries no OFAC nexus at all). A counterparty can clear OFAC
screening while remaining Entity-Listed. Folding these into one check
would silently drop real, export-control-specific risk this vertical is
squarely exposed to -- exactly the kind of risk that distinguishes a
specialized tech wholesaler from a generic trading house.
`denied-party-list-flag-unresolved-violations` is evaluated
UNCONDITIONALLY at all three actuation ops, the SAME open-flag-
unresolved discipline the generic sanctions check establishes, applied
to a genuinely different fact. `test/techtrade/governor_contract_test.clj`'s
`denied-party-list-flag-unresolved-is-a-genuinely-different-failure-
mode-from-generic-sanctions` (to-8, which explicitly PASSES OFAC
screening while failing denied-party screening) proves the split is
real.

### Decision 6: `techtrade.facts` cites the SPECIFIC classification-list provision, not merely 'this jurisdiction has export-control law'

`shosha.facts` (the general-trading sibling) cites each jurisdiction's
export-control STATUTE generally (FEFTA; the EAR; the Export Control
Order 2008; Regulation (EU) 2021/821) -- the right resolution for a
business whose own defining check is a single coarse boolean (Decision
4). This vertical's catalog instead names the SPECIFIC classification-
list provision each jurisdiction uses to determine whether a given
computer/peripheral/software item is itself controlled: the US Commerce
Control List Category 4 (Computers) and Category 5 Part 2 (Information
Security); Japan's 輸出貿易管理令別表第一 (Appended Table 1) and the
該非判定 (gaihi-hantei) classification act; Germany/EU's Regulation (EU)
2021/821 Annex I Category 4 and Category 5 Part 2 specifically (plus the
2021 recast's Article 5 cyber-surveillance catch-all); and the UK
Strategic Export Control Lists' own Category 4/Category 5 Part 2
entries. This is the technical mechanic a computer-and-software
wholesaler's compliance function actually applies order to order, and
the direct textual differentiation this build's task required from the
general-trading sibling's coarser citation.

### Decision 7: dedicated triple double-actuation-guard booleans

`:dispatched?` / `:released?` / `:invoiced?` are THREE dedicated
booleans on the `tech-order` record, never a single `:status` value --
the same discipline every prior governor's guards establish, informed
by `cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320), extended here to three guards because this vertical
has three actuation ops (Decision 3) rather than two.

### Decision 8: Store protocol, MemStore + DatomicStore parity

`techtrade.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-backed),
proven to satisfy the same contract in
`test/techtrade/store_contract_test.clj`. Unlike every prior sibling's
`DatomicStore`, this one also round-trips KEYWORD-valued fields
(`:item-type`, `:delivery-mode`, `:status`) through an EDN-string
encoding (`tech-order-fields`'s `:kw` kind) rather than storing them as
bare strings -- necessary because this vertical's `:item-type`/
`:delivery-mode` distinction is structurally load-bearing (it determines
which of the two actuation ops applies, Decision 3), so losing the
keyword-ness on a Datomic round-trip would be a real parity bug, not
just a cosmetic one; `store_contract_test.clj`'s `datomic-empty-store-
is-usable` asserts `:item-type` reads back as a keyword specifically.
The ledger stays append-only on every backend: which tech-order was
verified for a jurisdiction with no official spec-basis, which order had
NO classification on file at all, which order was classified but lacked
an authorized license, which counterparty had an unresolved sanctions or
denied-party flag, which order was dispatched/released/invoiced, on what
classification and jurisdictional basis, approved by whom -- always a
query over an immutable log.

### Decision 9: Phase 0->3 with `:delivery/dispatch`/`:technology/release`/`:invoice/settle` NEVER auto

`techtrade.phase`'s phase table puts `:order/intake` (no direct capital
or export-control risk) in phase 3's `:auto` set as its only member;
`:delivery/dispatch`, `:technology/release` and `:invoice/settle` are
deliberately ABSENT from every phase's `:auto` set, including phase 3 --
a permanent structural fact. `techtrade.governor`'s high-stakes gate
enforces the same invariant independently: two layers agree that
actuation is always a human trading supervisor / export-compliance
officer's call.

### Decision 10: `:robotics true`, reasoned as a MIXED, path-specific claim -- a fleet first

`:itonami.blueprint/robotics` is `true`, a deliberate call reasoned
specifically for this vertical's PHYSICAL dispatch path only, following
the SAME kind-differentiated reasoning discipline the metal-wholesale
sibling's Decision 10 and the agri-wholesale/provision-trading siblings'
own reasoning establish -- but this build is the FIRST in the cluster to
reason the claim as explicitly MIXED across its own two actuation paths
rather than uniform across the whole vertical:

- **`:delivery/dispatch` (physical hardware/peripherals)**: computer-
  hardware/peripheral wholesale distribution centers commonly run
  automated storage-and-retrieval systems (AS/RS) -- goods-to-person
  robotic shuttles that pick and stage cartons -- with ESD-safe
  (electrostatic-discharge-safe) automated handling a genuinely
  load-bearing, well-precedented concern specific to electronic
  components (unlike bulk commodities), directly analogous to the
  metal-wholesale sibling's automated crane/stacker-reclaimer citation
  and the fuel-wholesale sibling's loading-rack/valve robot citation.
- **`:technology/release` (software/source code/technical data,
  including deemed-export releases)**: has NO analogous physical
  robotic act at all -- digital/paperwork/access-control work, closer in
  kind to the general-trading sibling's own `:robotics false` reasoning
  (a logistics-coordination referral with no physical dispatch act this
  actor's governor could gate a robot command against).

Two options were considered:

- **Option A (rejected): default `:robotics false`**, matching the
  general-trading sibling (the closest thematic cousin). Rejected: a
  meaningful share of this vertical's real-world dispatch volume
  genuinely IS physical hardware/peripheral movement through an
  automated warehouse -- unlike the general-trading sibling (pure
  intermediation, no analogous physical dispatch act at all) or the
  company-incorporation sibling (pure paperwork), this vertical's
  `:delivery/dispatch` path is a genuine physical act.
- **Option B (chosen): `:robotics true`, documented explicitly as
  applying ONLY to the `:delivery/dispatch` path**, not blanket across
  the vertical. This is the honest call: the vertical AS A WHOLE
  warrants `true` because a real automation claim exists and is
  load-bearing for one of its two actuation ops, but retrofitting the
  SAME claim onto `:technology/release` (which has no physical act at
  all) would be dishonest in the other direction -- the same "do not
  retrofit a robotics claim where none exists" discipline the general-
  trading and company-incorporation siblings establish, applied
  path-by-path within a single vertical rather than blueprint-by-
  blueprint across siblings.

## Alternatives considered

- **Wrapping a bespoke `kotoba-lang/techtrade` capability library.**
  Considered and explicitly ruled out: no such library exists, and
  `kotoba-lang/robotics` is generic, not tech-trading-specific. Forcing
  a false capability-library integration would be dishonest; this build
  correctly uses self-contained domain logic instead.
- **Hosting pure range-check functions in the registry.** Considered
  and ruled out: the tech-trading domain checks are direct entity
  booleans/values (credit cleared? contract on file? classified?
  license authorized? sanctions screened? denied-party screened?), not
  measured-value-vs-limit range comparisons, so there are no range
  checks to host. `techtrade.registry` is record construction only.
- **Modeling deemed export as a `:delivery/dispatch` variant, or a
  single `:kind`-distinguished fulfillment op.** Considered and
  rejected -- see Decision 3 Options A and B for the full reasoning:
  both would misrepresent what kind of real-world act a deemed-export
  release actually is.
- **Collapsing classification and license authorization into one
  boolean (matching the general-trading sibling), or folding them into
  the generic per-jurisdiction evidence checklist.** Considered and
  rejected -- see Decision 4 Options A and B: both would erase a
  distinction this vertical's own regulatory structure genuinely makes,
  and erase it from the audit trail.
- **A third, dedicated HARD check for encryption items (EAR Category 5
  Part 2).** Considered and rejected -- see Decision 4's encryption
  discussion: an encryption item's sub-regime is still a classify-then-
  license-determine sequence, already captured by the two-check split.
- **Folding denied-party-list screening into the generic sanctions
  check.** Considered and rejected -- see Decision 5: OFAC sanctions and
  BIS Entity List/Denied Persons List screening are different
  regulatory mechanisms with different trigger conditions; folding them
  would silently drop real export-control-specific risk.
- **Defaulting `:robotics` to `false`** (matching the general-trading
  sibling uniformly) or to `true` uniformly (matching the metal-
  wholesale sibling uniformly). Considered and rejected in favor of the
  explicitly MIXED, path-specific reasoning in Decision 10 -- neither
  uniform default is honest about this vertical's own two structurally
  different actuation paths.
- **Building fulfillment routing and trading-book optimization in this
  R0.** Rejected in favor of a scoped R0 slice (the `:optimization`
  capability is correctly marked required, the integration is a
  follow-up), consistent with this fleet's 'extending coverage is
  additive' convention.

## Consequences

- Fresh independent actor in this fleet, following the SAME governed-
  actor architecture as every prior sibling.
- Establishes the fleet's first THREE-member high-stakes/actuation set
  (`:delivery/dispatch`/`:technology/release`/`:invoice/settle`), a
  template for any future vertical whose real-world fulfillment
  genuinely splits across a physical and a non-physical channel.
- Establishes the fleet's first SPLIT domain-defining check
  (`eccn-classification-missing` / `license-required-unauthorized`),
  contrasted explicitly with the metal-wholesale/textile-wholesale/
  waste-wholesale siblings' own fold-two-sub-facts-into-one-rule
  precedent -- a template for any future vertical whose defining
  regulatory concern is genuinely two sequential, dependent
  determinations rather than two evidentiary arms of one determination.
- Establishes the fleet's first split of "screening" into two dedicated
  checks (`counterparty-sanctions-flag-unresolved` /
  `denied-party-list-flag-unresolved`).
- Establishes the fleet's first explicitly path-specific (rather than
  vertical-uniform) robotics-premise reasoning.
- `MemStore` || `DatomicStore` parity is proven by
  `test/techtrade/store_contract_test.clj`, including keyword-field
  round-trip parity (`:item-type`) not required by any prior sibling.
- 46 tests / 265 assertions pass; lint is clean; the demo
  (`clojure -M:dev:run`) walks one clean dispatch + invoice lifecycle,
  one clean deemed-export release + invoice lifecycle, nine HARD-hold
  scenarios (no spec-basis, credit-uncleared, contract-missing,
  eccn-classification-missing, license-required-unauthorized [DISTINCT
  from the prior], counterparty-sanctions-flag-unresolved, denied-
  party-list-flag-unresolved [DISTINCT from the prior], a deemed-export
  license hold via `effective-destination`, double-dispatch,
  double-release, double-invoice), end-to-end.
- `blueprint.edn`'s `:robotics true` is a reasoned, path-specific call
  documented in README and `docs/business-model.md`, not a default
  carried over from either extreme sibling precedent.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of the
  general governed-actor architecture pattern)
- `cloud-itonami-isic-4671/docs/adr/0001-architecture.md` (fuel-
  wholesale sibling; origin of the sequential dual-actuation shape and
  the self-contained-domain-logic pattern this build follows)
- `cloud-itonami-isic-4690/docs/adr/0001-architecture.md` (general-
  trading sibling; this build's closest thematic cousin and the sibling
  whose single `:export-license-cleared?` boolean this build's Decision
  4 deliberately splits into two)
- `cloud-itonami-isic-4662/docs/adr/0001-architecture.md` (metal-
  wholesale sibling; origin of the metal-type-gated, unconditional-
  across-jurisdiction domain check and the fold-two-sub-facts-into-one-
  rule precedent this build's Decision 4 deliberately does NOT follow)
- `cloud-itonami-isic-4641/docs/adr/0001-architecture.md` (textile-
  wholesale sibling; origin of the jurisdiction-gated rebuttable-
  presumption check shape)
- `cloud-itonami-isic-4669/docs/adr/0001-architecture.md` (waste-
  wholesale sibling; origin of the bilateral, government-to-government
  consent check shape)
- 15 C.F.R. Parts 730-774 (Export Administration Regulations); Commerce
  Control List Category 4 (Computers) and Category 5 Part 2 (Information
  Security); 15 C.F.R. Part 738 (Commerce Country Chart); 15 C.F.R. Part
  740 (License Exceptions, e.g. ENC); 15 C.F.R. Part 744, Supplement No.
  4 (Entity List); 15 C.F.R. Part 764 (Denied Persons List; enforcement);
  15 C.F.R. §734.13 (deemed-export rule) (USA, Bureau of Industry and
  Security, U.S. Department of Commerce)
- 輸出貿易管理令 (Export Trade Control Order) 別表第一 (Appended Table
  1); 該非判定 (gaihi-hantei) classification; キャッチオール規制
  (Article 4 catch-all control) (Japan, METI 貿易経済協力局 安全保障貿易
  管理課)
- Regulation (EU) 2021/821 (dual-use export-control recast), Annex I
  Category 4 (Computers) and Category 5 Part 2 (Information Security);
  Article 5 cyber-surveillance catch-all (EU; Germany, BAFA)
- Export Control Order 2008 (SI 2008/3231); UK Strategic Export Control
  Lists, Category 4 and Category 5 Part 2 (UK, ECJU)
- OFAC sanctions programs (31 C.F.R. Chapter V) (US, Treasury) -- cited
  for the generic `counterparty-sanctions-flag-unresolved` check, the
  SAME mechanism every sibling in this fleet re-verifies
