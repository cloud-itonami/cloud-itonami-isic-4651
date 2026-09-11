(ns techtrade.facts
  "Per-jurisdiction dual-use export-control regulatory catalog -- the
  G2-style spec-basis table the Tech Export Governor checks every
  `:classification/verify` proposal against ('did the advisor cite an
  OFFICIAL public source for THIS jurisdiction's export-control-
  classification regime, or did it invent one?').

  UNLIKE the general-trading sibling's own `shosha.facts` (which cites
  each jurisdiction's export-control STATUTE at the level of 'does this
  jurisdiction have a citable export-control regime' -- FEFTA, the EAR,
  the Export Control Order 2008, Regulation (EU) 2021/821, generally),
  this catalog cites the SPECIFIC CLASSIFICATION-LIST PROVISION each
  jurisdiction uses to determine whether a given computer/peripheral/
  software item is itself controlled -- the technical mechanic a
  computer-and-software wholesaler's compliance function actually
  applies order to order, not just the statute's existence. A general-
  trading house asks 'does this jurisdiction regulate exports at all,
  and has SOME export-control classification been recorded'; a
  computer-and-software wholesaler must ask 'against WHICH control list,
  under WHICH category, does THIS specific item classify' -- the
  Commerce Control List / ECCN system (USA), 輸出貿易管理令別表第一
  (Japan), Regulation (EU) 2021/821 Annex I (Germany/EU), and the UK
  Strategic Export Control Lists (UK) are each a REAL, distinct
  classification list, not merely 'the export-control law of this
  country'.

  Each entry below is a REAL jurisdiction with a REAL export-control-
  classification regime:

  - USA (the PRIMARY regime for this vertical): the Bureau of Industry
    and Security (BIS)'s Export Administration Regulations (EAR, 15
    C.F.R. Parts 730-774). Computer hardware classifies under Commerce
    Control List (CCL) Category 4 (Computers); encryption hardware/
    software/technology classifies under CCL Category 5 Part 2
    (Information Security) -- ECCNs such as 5A002 (encryption
    hardware), 5D002 (encryption software), 5E002 (encryption
    technology). An item not listed on the CCL classifies EAR99 (still
    subject to end-use/end-user/embargo restrictions, but no CCL-based
    license requirement by classification alone). Whether a license is
    actually REQUIRED for a given ECCN + destination is read off the
    Commerce Country Chart (15 C.F.R. Part 738); License Exceptions (15
    C.F.R. Part 740, e.g. ENC for encryption items) can eliminate a
    license requirement that the Country Chart would otherwise impose.
    Restricted/denied parties are screened against the Entity List (15
    C.F.R. Part 744, Supplement No. 4) and the Denied Persons List (15
    C.F.R. Part 764) -- a DIFFERENT regulatory mechanism from OFAC's
    transaction-blocking sanctions programs (see `techtrade.governor`'s
    `denied-party-list-flag-unresolved-violations` docstring for why
    this actor's governor carries a check dedicated to this, distinct
    from the generic OFAC-style sanctions check). The deemed-export rule
    (release of controlled technology or source code to a foreign
    national, including one physically located INSIDE the United
    States, is deemed to be an export to that person's most recent
    country of citizenship or permanent residency) is at 15 C.F.R.
    §734.13.
  - JPN: 経済産業省 (METI) 貿易経済協力局 安全保障貿易管理課 administers
    輸出貿易管理令 (Export Trade Control Order) 別表第一 (Appended Table
    1) -- Japan's OWN dual-use control list, structured in categories
    that mirror the multilaterally-coordinated Wassenaar Arrangement
    Dual-Use List (the same multilateral lineage the EAR's CCL and the
    EU's Annex I share). The classification act itself is called 該非
    判定 (gaihi-hantei, 'applicability determination') -- does this
    specific computer/peripheral/software item fall within an Appended
    Table 1 item number, or not. Japan additionally operates its own
    catch-all control (キャッチオール規制, Export Trade Control Order
    Article 4) as an end-use/end-user-based backstop for items NOT on
    Appended Table 1, structurally analogous to (but legally distinct
    from) the EAR's own end-use/end-user-based catch-all provisions.
  - DEU: Bundesamt für Wirtschaft und Ausfuhrkontrolle (BAFA) enforces
    Regulation (EU) 2021/821 (the dual-use export-control recast,
    directly applicable in every EU member state) Annex I -- SPECIFICALLY
    Category 4 (Computers) and Category 5 Part 2 (Information Security),
    the SAME category numbers as the EAR's CCL because both derive from
    the Wassenaar Arrangement list. The 2021 recast additionally
    introduced Article 5 human-rights-related 'catch-all' due-diligence
    obligations for cyber-surveillance items not otherwise listed on
    Annex I -- a genuinely new (2021) provision specifically relevant to
    network-monitoring/interception hardware and software a computer-
    and-software wholesaler might carry.
  - GBR: the Export Control Joint Unit (ECJU), Department for Business
    and Trade, administers the UK Strategic Export Control Lists under
    the Export Control Order 2008 (SI 2008/3231) -- the UK's own
    post-Brexit dual-use list, structurally equivalent (same Category 4
    / Category 5 Part 2 numbering) to the EU Annex I list it diverged
    from, because both remain Wassenaar-derived.

  The required-evidence set (credit-clearance record, contract/PO,
  sanctions-screening (OFAC/equivalent) record, denied-party-screening
  (Entity List/Denied Persons List or equivalent) record) mirrors the
  GENERIC counterparty-diligence evidence a computer-and-software
  wholesale compliance function demands before ANY order proceeds --
  it deliberately does NOT include an export-classification-record
  item: unlike the general-trading sibling (which folds 'export-control
  classification (ECCN/HS-code) record' into its own generic per-
  jurisdiction checklist), THIS vertical's classification/license
  determination is evaluated by two SEPARATE, dedicated governor checks
  (`eccn-classification-missing-violations` /
  `license-required-unauthorized-violations`), not a checklist item --
  see `techtrade.governor` namespace docstring Decision-equivalent
  reasoning for why these are genuinely two distinct failure modes, not
  one.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` is the GENERIC
  counterparty-diligence evidence set (credit-clearance record,
  contract/PO, sanctions-screening record, denied-party-screening
  record); `:legal-basis` / `:owner-authority` / `:provenance` are the
  G2 citation the governor requires before any `:classification/verify`
  proposal can commit. `:classification-list` names the SPECIFIC
  control-list provision this jurisdiction uses to classify a
  computer/peripheral/software item -- the detail that differentiates
  this catalog from the general-trading sibling's coarser per-
  jurisdiction export-control citation."
  {"USA" {:name "USA"
          :owner-authority "Bureau of Industry and Security (BIS), U.S. Department of Commerce"
          :legal-basis "Export Administration Regulations (15 C.F.R. Parts 730-774)"
          :classification-list "Commerce Control List (CCL), Category 4 (Computers) and Category 5 Part 2 (Information Security); ECCN or EAR99"
          :provenance "https://www.bis.doc.gov/"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"
                              "denied-party-screening (Entity List/Denied Persons List) record"]}
   "JPN" {:name "JPN"
          :owner-authority "経済産業省 (METI) 貿易経済協力局 安全保障貿易管理課"
          :legal-basis "輸出貿易管理令 (Export Trade Control Order)"
          :classification-list "輸出貿易管理令別表第一 (Appended Table 1) 該非判定 (gaihi-hantei); キャッチオール規制 (Article 4 catch-all)"
          :provenance "https://www.meti.go.jp/policy/anpo/"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"
                              "denied-party-screening (Entity List/Denied Persons List) record"]}
   "DEU" {:name "DEU"
          :owner-authority "Bundesamt für Wirtschaft und Ausfuhrkontrolle (BAFA)"
          :legal-basis "Regulation (EU) 2021/821 (dual-use export-control recast)"
          :classification-list "Annex I, Category 4 (Computers) and Category 5 Part 2 (Information Security); Article 5 cyber-surveillance catch-all"
          :provenance "https://www.bafa.de/"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"
                              "denied-party-screening (Entity List/Denied Persons List) record"]}
   "GBR" {:name "GBR"
          :owner-authority "Export Control Joint Unit (ECJU), Department for Business and Trade"
          :legal-basis "Export Control Order 2008 (SI 2008/3231)"
          :classification-list "UK Strategic Export Control Lists, Category 4 (Computers) and Category 5 Part 2 (Information Security)"
          :provenance "https://www.gov.uk/guidance/beginners-guide-to-export-controls"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"
                              "denied-party-screening (Entity List/Denied Persons List) record"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to dispatch,
  release, or settle an invoice on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry. Never report a missing jurisdiction
  as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-4651 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `techtrade.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
