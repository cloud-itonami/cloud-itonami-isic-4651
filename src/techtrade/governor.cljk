(ns techtrade.governor
  "Tech Export Governor -- the independent compliance layer that earns
  the TechTradeAdvisor the right to commit. The LLM has no notion of
  jurisdictional export-control-classification law, whether a
  counterparty's credit has actually been cleared, whether contract
  terms are actually on file, whether THIS item has actually been
  classified against a real control list, whether a classified item's
  destination/end-user combination actually requires a license (and if
  so whether one is actually on file), whether OFAC / equivalent
  sanctions screening AND denied-party (Entity List/Denied Persons
  List) screening have actually been passed, or when an act stops being
  a draft and becomes a real physical dispatch, a real technology
  release, or a real invoice settlement, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD.

  Like every principal-trading sibling's own governor, this computer-
  and-software-wholesale vertical has NO pre-existing tech-trading
  capability library to delegate to -- so the domain checks (credit-
  clearance, contract-on-file, export classification, license
  authorization, sanctions-screening, denied-party-screening) are
  direct entity boolean/value reads off the `tech-order` record,
  evaluated directly here, NOT delegated to a separate library's
  validated function.

  `:itonami.blueprint/governor` is `:tech-export-governor`, grep-
  verified UNIQUE fleet-wide -- no naming-collision precedent question,
  a fresh independent build following the SAME governed-actor
  architecture (langgraph StateGraph + independent Governor + Phase
  0->3 rollout) established by `cloud-itonami-isic-6511` and applied by
  the fuel-wholesale (`cloud-itonami-isic-4671`), general-trading
  (`cloud-itonami-isic-4690`), metal-wholesale
  (`cloud-itonami-isic-4662`), textile-wholesale
  (`cloud-itonami-isic-4641`) and waste-wholesale
  (`cloud-itonami-isic-4669`) siblings.

  CRITICAL STRUCTURAL DIFFERENCE #1 from the general-trading sibling's
  own domain-defining check: `shosha.governor`'s `export-license-
  uncleared-violations` is a SINGLE boolean read
  (`:export-license-cleared?`) -- coarse by design, because a general/
  diversified trading house's defining exposure is 'has SOME export-
  control classification/licensing process been completed', evaluated
  at the level of a jurisdiction-citation. A computer-and-software
  wholesaler's defining exposure is more specific and technical: BEFORE
  any license question can even be asked, the item itself must be
  CLASSIFIED against a real control list (an ECCN under the US Commerce
  Control List, or the structurally equivalent JPN/DEU/GBR list -- see
  `techtrade.facts`); ONLY THEN can a SEPARATE determination be made
  about whether the specific destination/end-user combination requires
  a license under that classification, and if so whether a valid
  license or License Exception is actually on file. 'Never classified
  at all' and 'classified, but the license this classification requires
  for this destination/end-user is missing' are TWO GENUINELY DIFFERENT
  real-world failure postures -- a compliance officer who sees the first
  cannot even begin to answer the licensing question yet; a compliance
  officer who sees the second has done the classification work and
  still cannot ship. This build therefore splits what the general-
  trading sibling folds into ONE boolean into TWO dedicated HARD checks,
  `eccn-classification-missing-violations` and `license-required-
  unauthorized-violations` below -- see each check's own docstring, and
  `docs/adr/0001-architecture.md` Decision 4 for the full reasoning
  (including why, unlike the metal-wholesale/textile-wholesale/waste-
  wholesale siblings' own two-sub-fact-folded-into-ONE-named-rule
  precedent for THEIR domain-defining checks, this build deliberately
  does NOT fold these two facts into one rule).

  CRITICAL STRUCTURAL DIFFERENCE #2: this vertical's high-stakes/
  actuation set has THREE members, not two --
  `#{:delivery/dispatch :technology/release :invoice/settle}` -- a
  fleet first. `:technology/release` exists because for software/
  technology/source-code items the controlled event is NOT necessarily
  a physical cross-border shipment: releasing controlled technology or
  source code to a foreign national is deemed an export to that
  person's home country EVEN WHEN the release happens physically inside
  the exporting jurisdiction (the deemed-export doctrine, 15 C.F.R.
  §734.13). See `techtrade.store`/`techtrade.operation` namespace
  docstrings and `docs/adr/0001-architecture.md` Decision 3.

  CRITICAL STRUCTURAL DIFFERENCE #3: `denied-party-list-flag-
  unresolved-violations` below is a check with NO analog in ANY prior
  sibling's governor -- see that check's own docstring for why a BIS
  Entity List / Denied Persons List screening is a genuinely different
  regulatory mechanism from generic OFAC-style sanctions screening, and
  why export control specifically warrants a dedicated check for it.

  Nine checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them. The confidence/actuation gate is SOFT: it asks
  a human to look (low confidence / actuation), and the human may
  approve -- but see `techtrade.phase`: for `:stake :delivery/dispatch`/
  `:technology/release`/`:invoice/settle` (a real dispatch, release, or
  invoice settlement) NO phase ever allows auto-commit either. Two
  independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source
                                       (`techtrade.facts`), or invent
                                       one?
    2. Evidence incomplete         -- for `:delivery/dispatch`/
                                       `:technology/release`/`:invoice/
                                       settle`, has the jurisdiction
                                       actually been verified with a full
                                       GENERIC counterparty-diligence
                                       evidence checklist on file
                                       (credit-clearance record,
                                       contract/PO, sanctions-screening
                                       record, denied-party-screening
                                       record)? Deliberately does NOT
                                       include export classification --
                                       that is checks 5/6 below.
    3. Credit uncleared            -- for `:delivery/dispatch`/
                                       `:technology/release`, the
                                       counterparty's credit has NOT been
                                       cleared (the leasing collateral-
                                       coverage discipline, applied to
                                       counterparty credit). Evaluated
                                       before the goods/technology
                                       leaves.
    4. Contract missing            -- for `:delivery/dispatch`/
                                       `:technology/release`, no
                                       contract-terms are on file for the
                                       order. Evaluated before the
                                       goods/technology leaves.
    5. ECCN classification missing -- for `:delivery/dispatch`/
                                       `:technology/release`, the item
                                       has NEVER been classified against
                                       a real control list at all
                                       (`:eccn` is nil/blank). THIS check
                                       has no analog in the general-
                                       trading sibling's SINGLE `export-
                                       license-uncleared` boolean -- see
                                       namespace docstring Difference #1.
    6. License required, unauthorized
                                    -- for `:delivery/dispatch`/
                                       `:technology/release`, the item
                                       HAS been classified (`:eccn`
                                       present) AND that classification
                                       requires a license for the
                                       order's EFFECTIVE destination
                                       (`:license-required?` true) AND no
                                       valid license or applicable
                                       License Exception is on file
                                       (`:license-authorized?` false).
                                       Genuinely DISTINCT failure mode
                                       from check 5 -- a classified,
                                       unlicensed item is a different
                                       real-world posture from an
                                       unclassified one. Deemed-export-
                                       aware: reads the release's
                                       EFFECTIVE destination (the
                                       recipient's nationality when
                                       `:deemed-export?` is true), not
                                       the order's own `:destination-
                                       country`.
    7. Counterparty sanctions flag
       unresolved                  -- for `:delivery/dispatch`/
                                       `:technology/release`/`:invoice/
                                       settle`, the counterparty has NOT
                                       passed OFAC / equivalent sanctions
                                       screening -- a HARD, un-
                                       overridable hold. Evaluated
                                       UNCONDITIONALLY at all three
                                       actuation ops.
    8. Denied-party list flag
       unresolved                  -- for `:delivery/dispatch`/
                                       `:technology/release`/`:invoice/
                                       settle`, the counterparty/end-user
                                       has NOT been screened against the
                                       BIS Entity List / Denied Persons
                                       List (or the equivalent
                                       restricted-party list under the
                                       order's own jurisdiction) -- a
                                       HARD, un-overridable hold,
                                       DISTINCT from generic sanctions
                                       screening (check 7). See this
                                       check's own docstring. Evaluated
                                       UNCONDITIONALLY at all three
                                       actuation ops.
    9. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:delivery/dispatch`/
                                       `:technology/release`/`:invoice/
                                       settle` (REAL acts) -> escalate.

  Three more guards, double-dispatch/double-release/double-invoice
  prevention, are enforced but NOT listed as numbered HARD checks above
  because they need no upstream comparison at all --
  `already-dispatched-violations`/`already-released-violations`/
  `already-invoiced-violations` refuse to dispatch/release/invoice the
  SAME tech-order twice, off dedicated `:dispatched?`/`:released?`/
  `:invoiced?` facts (never a `:status` value) -- the SAME 'check a
  dedicated boolean, not status' discipline every prior governor's
  guards establish, informed by `cloud-itonami-isic-6492`'s status-
  lifecycle bug (ADR-2607071320)."
  (:require [techtrade.facts :as facts]
            [techtrade.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean. A
  physical cross-border dispatch of real computer hardware/peripherals,
  an electronic release of real controlled software/source code/
  technical data (including a deemed-export release to a foreign
  national), and settling a real invoice (real money moving between
  counterparty and wholesaler) are the THREE real-world actuation
  events this actor performs -- a THREE-member set, unlike every
  sibling's own two-member dual-actuation shape (see namespace
  docstring Difference #2)."
  #{:delivery/dispatch :technology/release :invoice/settle})

(def dispatch-or-release
  "The two ops that move the goods/technology out of the wholesaler's
  control -- the set every dispatch-time-only check (credit, contract,
  classification, license) is evaluated against, mirroring the SINGLE
  `:delivery/dispatch` every sibling's own dispatch-time checks gate on,
  now split across the two mechanisms this vertical actually has."
  #{:delivery/dispatch :technology/release})

;; ----------------------------- effective destination -----------------------------

(defn effective-destination
  "The destination this order's classification/license determination
  must actually be evaluated against. For an ordinary physical dispatch
  or a non-deemed-export technology release, this is simply the order's
  own `:destination-country`. For a DEEMED-EXPORT release
  (`:deemed-export?` true -- release of controlled technology/source
  code to a foreign national, per 15 C.F.R. §734.13), the effective
  destination is the RECIPIENT'S OWN NATIONALITY
  (`:release-recipient-nationality`), regardless of where the recipient
  is physically standing when the release happens (including physically
  inside the exporting jurisdiction itself). This is the ONE function
  that actually encodes the deemed-export doctrine in checkable code --
  every check below that cares about 'destination' reads through this,
  not `:destination-country` directly, so a deemed-export order cannot
  silently evade classification/license scrutiny by pointing at an
  unconcerning `:destination-country` while the real recipient is
  elsewhere."
  [to]
  (if (true? (:deemed-export? to))
    (:release-recipient-nationality to)
    (:destination-country to)))

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:classification/verify` (or `:delivery/dispatch`/`:technology/
  release`/`:invoice/settle`) proposal with no spec-basis citation is a
  HARD violation -- never invent a jurisdiction's export-control-
  classification requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:classification/verify :delivery/dispatch :technology/release :invoice/settle} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:delivery/dispatch`/`:technology/release`/`:invoice/settle`, the
  jurisdiction's required GENERIC counterparty-diligence evidence
  (credit-clearance record, contract/PO, sanctions-screening record,
  denied-party-screening record) must actually be satisfied -- do not
  trust the advisor's self-reported confidence alone. Deliberately does
  NOT check export classification or license authorization -- those are
  `eccn-classification-missing-violations`/`license-required-
  unauthorized-violations` below, each its own dedicated check rather
  than a checklist item (see namespace docstring Difference #1)."
  [{:keys [op subject]} st]
  (when (contains? #{:delivery/dispatch :technology/release :invoice/settle} op)
    (let [to (store/tech-order st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction to) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(信用審査記録/契約書またはPO/制裁スクリーニング記録/該当者リストスクリーニング記録)が充足していない状態での提案"}]))))

(defn- credit-uncleared-violations
  "For `:delivery/dispatch`/`:technology/release`, refuses to move real
  computer hardware/peripherals or release real controlled technology
  to a counterparty whose credit has NOT been cleared -- counterparty
  credit not cleared (the leasing collateral-coverage discipline,
  applied to counterparty credit). Evaluated ahead of any physical or
  electronic handoff."
  [{:keys [op subject]} st]
  (when (contains? dispatch-or-release op)
    (let [to (store/tech-order st subject)]
      (when (not (true? (:credit-cleared? to)))
        [{:rule :credit-uncleared
          :detail (str subject " の取引先信用審査(credit-clearance)が未了 -- 出荷・リリース提案は進められない")}]))))

(defn- contract-missing-violations
  "For `:delivery/dispatch`/`:technology/release`, refuses to move real
  computer hardware/peripherals or release real controlled technology
  when no contract-terms are on file for the order."
  [{:keys [op subject]} st]
  (when (contains? dispatch-or-release op)
    (let [to (store/tech-order st subject)]
      (when (or (nil? (:contract-terms to)) (= "" (:contract-terms to)))
        [{:rule :contract-missing
          :detail (str subject " に契約条項(contract-terms)の記録が無い -- 出荷・リリース提案は進められない")}]))))

(defn- eccn-classification-missing-violations
  "For `:delivery/dispatch`/`:technology/release`, refuses to move the
  item when it has NEVER been classified against a real control list at
  all (`:eccn` is nil/blank -- distinct from `\"EAR99\"`, which IS a
  real, valid classification outcome meaning 'reviewed and determined
  not on the control list'). This is the FIRST of the two checks that
  replace the general-trading sibling's single `export-license-
  cleared?` boolean (see namespace docstring Difference #1): you cannot
  even ask whether a license is required until the item has been
  classified. A `nil` ECCN means classification never happened, full
  stop -- this actor will not guess."
  [{:keys [op subject]} st]
  (when (contains? dispatch-or-release op)
    (let [to (store/tech-order st subject)]
      (when (or (nil? (:eccn to)) (= "" (:eccn to)))
        [{:rule :eccn-classification-missing
          :detail (str subject " (" (name (:item-type to)) ") の該非判定/ECCN分類が未実施 -- "
                       "統制品目リスト(Commerce Control List等)に対する分類なしに出荷・リリース提案は進められない")}]))))

(defn- license-required-unauthorized-violations
  "For `:delivery/dispatch`/`:technology/release`, WHEN the item HAS
  been classified (`:eccn` present -- this check is a no-op when
  `eccn-classification-missing-violations` already fired, so the SAME
  order never double-counts both checks for the SAME underlying gap)
  AND that classification requires a license for the order's EFFECTIVE
  destination (`:license-required?` true, evaluated via
  `effective-destination` -- see that function's docstring for the
  deemed-export handling) AND no valid license or applicable License
  Exception is on file (`:license-authorized?` false), refuses to move
  the item. This is the SECOND of the two checks that replace the
  general-trading sibling's single `export-license-cleared?` boolean
  (see namespace docstring Difference #1) -- a classified-but-unlicensed
  item is a GENUINELY DIFFERENT real-world posture from an unclassified
  one: the classification work is done, the destination/end-user
  combination has been evaluated against it (e.g. via the Commerce
  Country Chart), and the answer is 'a license is required and none is
  on file'. NO-OP for an item whose classification does not require a
  license for its effective destination (`:license-required?` false --
  e.g. an EAR99 item, or a controlled item whose destination is outside
  the Country Chart's licensing requirement for its reasons-for-
  control), and encryption items (EAR Category 5 Part 2 -- ECCNs like
  5A002/5D002/5E002) are NOT given a third, separate HARD check: an
  encryption item's own sub-regime (License Exception ENC eligibility,
  self-classification reporting) is still, structurally, a classify-
  then-license-determination -- exactly what checks 5/6 already
  evaluate. See `docs/adr/0001-architecture.md` Decision 4 for the full
  reasoning on why this build folds encryption handling into the
  general ECCN mechanic rather than adding a third check."
  [{:keys [op subject]} st]
  (when (contains? dispatch-or-release op)
    (let [to (store/tech-order st subject)
          eccn (:eccn to)]
      (when (and (some? eccn) (not= eccn "")
                 (true? (:license-required? to))
                 (not (true? (:license-authorized? to))))
        [{:rule :license-required-unauthorized
          :detail (str subject " (ECCN=" eccn ", 仕向地=" (effective-destination to)
                       (when (true? (:deemed-export? to)) " [みなし輸出/deemed export]")
                       ") は輸出許可が必要だが有効な許可/ライセンス例外の記録が無い -- "
                       "出荷・リリース提案は進められない")}]))))

(defn- counterparty-sanctions-flag-unresolved-violations
  "For `:delivery/dispatch`/`:technology/release`/`:invoice/settle`, an
  unresolved sanctions-screening flag -- the counterparty has NOT
  passed OFAC / equivalent sanctions screening -- is a HARD, un-
  overridable hold. Evaluated UNCONDITIONALLY at all three actuation
  ops: neither hardware, technology nor money moves against an
  unscreened counterparty."
  [{:keys [op subject]} st]
  (when (contains? high-stakes op)
    (let [to (store/tech-order st subject)]
      (when (not (true? (:sanctions-screened? to)))
        [{:rule :counterparty-sanctions-flag-unresolved
          :detail (str subject " の取引先制裁スクリーニング(OFAC等)が未了 -- 出荷・リリース・請求提案は進められない")}]))))

(defn- denied-party-list-flag-unresolved-violations
  "For `:delivery/dispatch`/`:technology/release`/`:invoice/settle`, an
  unresolved denied-party-list flag -- the counterparty/end-user has NOT
  been screened against the BIS Entity List (15 C.F.R. Part 744,
  Supplement No. 4) / Denied Persons List (15 C.F.R. Part 764), or the
  equivalent restricted-party list under the order's own jurisdiction --
  is a HARD, un-overridable hold. THIS CHECK HAS NO ANALOG IN ANY PRIOR
  SIBLING'S GOVERNOR: it is a regulatory mechanism genuinely distinct
  from OFAC-style sanctions screening (check 7 above). OFAC sanctions
  programs BLOCK TRANSACTIONS/DEALINGS with a sanctioned party across
  essentially any goods/services -- a Treasury/financial-dealing
  prohibition. The Entity List and Denied Persons List instead restrict
  or prohibit EXPORTS OF ITEMS SUBJECT TO THE EAR to named parties --
  often with item-specific or license-specific conditions (e.g. an
  Entity List footnote-1 'affiliates' listing may require an EAR-license
  even for an otherwise-EAR99 item, something OFAC sanctions screening
  would never catch because EAR99 items are not otherwise restricted).
  A counterparty can clear generic OFAC screening while still being
  Entity-Listed (or vice versa in principle) -- treating these as the
  SAME check would silently drop real export-control-specific risk that
  a computer-and-software wholesaler is squarely exposed to. Evaluated
  UNCONDITIONALLY at all three actuation ops, off the dedicated
  `:denied-party-screened?` fact (never folded into `:sanctions-
  screened?`)."
  [{:keys [op subject]} st]
  (when (contains? high-stakes op)
    (let [to (store/tech-order st subject)]
      (when (not (true? (:denied-party-screened? to)))
        [{:rule :denied-party-list-flag-unresolved
          :detail (str subject " の該当者リスト(Entity List/Denied Persons List等)スクリーニングが未了 -- "
                       "出荷・リリース・請求提案は進められない")}]))))

(defn- already-dispatched-violations
  "For `:delivery/dispatch`, refuses to dispatch the SAME tech-order
  twice, off a dedicated `:dispatched?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (when (store/tech-order-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に出荷済み")}])))

(defn- already-released-violations
  "For `:technology/release`, refuses to release the SAME tech-order's
  technology twice, off a dedicated `:released?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :technology/release)
    (when (store/tech-order-already-released? st subject)
      [{:rule :already-released
        :detail (str subject " は既にリリース済み")}])))

(defn- already-invoiced-violations
  "For `:invoice/settle`, refuses to settle the SAME tech-order's
  invoice twice, off a dedicated `:invoiced?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :invoice/settle)
    (when (store/tech-order-already-invoiced? st subject)
      [{:rule :already-invoiced
        :detail (str subject " は既に請求済み")}])))

(defn check
  "Censors a TechTradeAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (credit-uncleared-violations request st)
                           (contract-missing-violations request st)
                           (eccn-classification-missing-violations request st)
                           (license-required-unauthorized-violations request st)
                           (counterparty-sanctions-flag-unresolved-violations request st)
                           (denied-party-list-flag-unresolved-violations request st)
                           (already-dispatched-violations request st)
                           (already-released-violations request st)
                           (already-invoiced-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
