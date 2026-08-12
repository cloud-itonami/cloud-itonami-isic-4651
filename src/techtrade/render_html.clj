(ns techtrade.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: the page that used to
  live at `docs/samples/operator-console.html` was a hand-written
  scaffold artefact committed with the initial commit -- no generator
  existed, so nothing on it was traceable to a real run. This namespace
  replaces it by driving the REAL actor stack
  (`techtrade.operation` -> `techtrade.governor` -> `techtrade.store`)
  and rendering whatever actually came out.

  EVERY id, number, ECCN, verdict, rule name, record_id and count on the
  generated page is read back out of the store / ledger / run audit
  channel after the scenario has actually executed. Nothing is typed in
  by hand: the tech-order directory is `store/all-tech-orders`, the
  export-control posture columns are the tech-order's own facts read
  through `governor/effective-destination`, the HARD-hold table is the
  ledger's own `:governor-hold` facts (rule + detail strings come from
  the governor itself), the phase gate table is `phase/phases`, the
  jurisdiction table is `facts/catalog` + `facts/coverage`, and the
  actuation records are the three `registry`-built histories.

  `-main` REFUSES to write the file when the scenario produced zero HARD
  governor holds -- a console that shows no real hold is exactly the
  failure mode this item exists to prevent, so it fails loudly rather
  than emitting a page that looks fine.

  Deterministic: no timestamps, no clock reads, no randomness, and every
  collection is sorted explicitly (tech-orders by numeric id suffix,
  jurisdictions and rule names by string) rather than relying on map
  iteration order. Two consecutive runs are byte-identical.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [techtrade.facts :as facts]
            [techtrade.governor :as governor]
            [techtrade.operation :as op]
            [techtrade.phase :as phase]
            [techtrade.store :as store]
            [langgraph.graph :as g]))

(def ^:private operator
  "The human operating this actor -- an export-compliance officer, at the
  most permissive rollout phase (3) so that nothing on the generated page
  can be blamed on a deliberately restrictive phase gate."
  {:actor-id "op-1" :actor-role :export-compliance-officer :phase 3})

(def ^:private approver-id "op-1")

;; ----------------------------- the scenario -----------------------------

(def ^:private scenario
  "One ordered step per actor run. `:approve?` means 'if this run
  escalates to a human, the export-compliance officer approves it' --
  whether it actually escalates is decided by the governor and the phase
  gate at run time, never asserted here. Steps without `:approve?` are
  expected to HARD-hold; that expectation is not encoded either, it is
  simply read back off the ledger afterwards.

  Every subject id below exists in `techtrade.store/demo-data`."
  [;; --- to-1: a full clean physical-dispatch lifecycle -----------------
   {:thread "to-1-intake"   :approve? false
    :note "clean hardware order, phase-3 auto-commit (no capital risk yet)"
    :request {:op :order/intake :subject "to-1"
              :patch {:id "to-1" :counterparty "Northfield Data Centres Ltd"}}}
   {:thread "to-1-verify"   :approve? true
    :note "per-jurisdiction diligence checklist (USA / BIS)"
    :request {:op :classification/verify :subject "to-1"}}
   {:thread "to-1-dispatch" :approve? true
    :note "physical cross-border dispatch -- always a human call"
    :request {:op :delivery/dispatch :subject "to-1"}}
   {:thread "to-1-invoice"  :approve? true
    :note "invoice settlement -- always a human call"
    :request {:op :invoice/settle :subject "to-1"}}

   ;; --- to-9: a full clean deemed-export technology-release lifecycle --
   {:thread "to-9-verify"   :approve? true
    :note "checklist for the software release order"
    :request {:op :classification/verify :subject "to-9"}}
   {:thread "to-9-release"  :approve? true
    :note "deemed-export release (EAR99, recipient JPN) -- always a human call"
    :request {:op :technology/release :subject "to-9"}}
   {:thread "to-9-invoice"  :approve? true
    :note "invoice settlement for the released software"
    :request {:op :invoice/settle :subject "to-9"}}

   ;; --- HARD holds, one failure mode per order -------------------------
   {:thread "to-2-verify"   :approve? false
    :note "jurisdiction ATL has no official spec-basis in techtrade.facts"
    :request {:op :classification/verify :subject "to-2"}}
   {:thread "to-2-dispatch" :approve? false
    :note "and so no diligence evidence can ever be on file for it"
    :request {:op :delivery/dispatch :subject "to-2"}}

   {:thread "to-3-verify"   :approve? true
    :note "checklist commits -- isolating the credit failure mode"
    :request {:op :classification/verify :subject "to-3"}}
   {:thread "to-3-dispatch" :approve? false
    :note "counterparty credit never cleared"
    :request {:op :delivery/dispatch :subject "to-3"}}

   {:thread "to-4-verify"   :approve? true
    :note "checklist commits -- isolating the contract failure mode"
    :request {:op :classification/verify :subject "to-4"}}
   {:thread "to-4-dispatch" :approve? false
    :note "no contract-terms on file"
    :request {:op :delivery/dispatch :subject "to-4"}}

   {:thread "to-5-verify"   :approve? true
    :note "checklist commits -- isolating the unclassified failure mode"
    :request {:op :classification/verify :subject "to-5"}}
   {:thread "to-5-dispatch" :approve? false
    :note "item never classified against any control list at all"
    :request {:op :delivery/dispatch :subject "to-5"}}

   {:thread "to-6-verify"   :approve? true
    :note "checklist commits -- isolating the unlicensed failure mode"
    :request {:op :classification/verify :subject "to-6"}}
   {:thread "to-6-dispatch" :approve? false
    :note "classified 5A002, license required, none on file"
    :request {:op :delivery/dispatch :subject "to-6"}}

   {:thread "to-7-verify"   :approve? true
    :note "checklist commits -- isolating the sanctions failure mode"
    :request {:op :classification/verify :subject "to-7"}}
   {:thread "to-7-dispatch" :approve? false
    :note "counterparty never passed OFAC-style sanctions screening"
    :request {:op :delivery/dispatch :subject "to-7"}}

   {:thread "to-8-verify"   :approve? true
    :note "checklist commits -- isolating the denied-party failure mode"
    :request {:op :classification/verify :subject "to-8"}}
   {:thread "to-8-dispatch" :approve? false
    :note "counterparty never screened against the Entity/Denied Persons List"
    :request {:op :delivery/dispatch :subject "to-8"}}

   {:thread "to-10-verify"  :approve? true
    :note "checklist commits -- isolating the deemed-export license failure mode"
    :request {:op :classification/verify :subject "to-10"}}
   {:thread "to-10-release" :approve? false
    :note "5D002 source code released to a QQQ national -- license determination runs against the RECIPIENT'S nationality, not the order's own destination-country"
    :request {:op :technology/release :subject "to-10"}}

   ;; --- double-actuation guards ---------------------------------------
   {:thread "to-1-dispatch-again" :approve? false
    :note "same order, second physical dispatch"
    :request {:op :delivery/dispatch :subject "to-1"}}
   {:thread "to-9-release-again"  :approve? false
    :note "same order, second technology release"
    :request {:op :technology/release :subject "to-9"}}
   {:thread "to-1-invoice-again"  :approve? false
    :note "same order, second invoice settlement"
    :request {:op :invoice/settle :subject "to-1"}}])

(defn- run-step!
  "Executes one scenario step against the real compiled actor. When the
  step is marked `:approve?` AND the actor actually paused at
  `:request-approval` (disposition `:escalate`), resumes it with a real
  human approval. Whether the pause happened is read off the run's own
  state -- never assumed."
  [actor {:keys [thread request approve?] :as step}]
  (let [r0 (g/run* actor {:request request :context operator} {:thread-id thread})
        escalated? (= :escalate (get-in r0 [:state :disposition]))
        r (if (and approve? escalated?)
            (g/run* actor {:approval {:status :approved :by approver-id}}
                    {:thread-id thread :resume? true})
            r0)]
    (assoc step :escalated? escalated? :result r)))

(defn run-demo!
  "Seeds a fresh `MemStore` and drives the whole `scenario` through one
  compiled OperationActor. Returns `{:db store :runs [step..]}` -- the
  store is the SSoT every table is read back out of, and the runs carry
  the per-run audit channel (which is where `:approval-requested` /
  `:approval-granted` facts live; the ledger only receives commits and
  holds)."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    {:db db
     :runs (mapv (partial run-step! actor) scenario)}))

;; ----------------------------- derived views -----------------------------

(defn hard-holds
  "Every HARD governor hold on the ledger. `:t :governor-hold` is the
  fact `techtrade.governor/hold-fact` writes and the `:hold` node
  appends; an approver rejection is a DIFFERENT fact (`:approval-
  rejected`, written by `techtrade.operation`) and is deliberately not
  counted here -- a hold a human caused is not a hold the human never
  saw."
  [db]
  (vec (filter #(= :governor-hold (:t %)) (store/ledger db))))

(defn- order-sort-key [id]
  (let [n (re-find #"\d+" (str id))]
    [(if n (Long/parseLong n) 0) (str id)]))

(defn- sorted-orders [db]
  (sort-by (comp order-sort-key :id) (store/all-tech-orders db)))

(defn- audit-of [run] (get-in run [:result :state :audit] []))

(defn- facts-of-type [run t] (filter #(= t (:t %)) (audit-of run)))

(defn- approval-rows-data
  "One row per run that actually reached the human-approval node, read
  off that run's own audit channel."
  [runs]
  (for [r runs
        :let [req (first (facts-of-type r :approval-requested))]
        :when req]
    {:op (:op req) :subject (:subject req) :reason (:reason req)
     :phase (:phase req) :confidence (:confidence req)
     :granted (first (facts-of-type r :approval-granted))
     :disposition (get-in r [:result :state :disposition])}))

(defn approver-attribution
  "DERIVED (never hard-coded) answer to 'where does the approving human's
  id actually land in this repo's SSoT?'. Walks each real store surface
  after the run and reports whether an `:approved-by` key survived into
  it. `techtrade.operation`'s `commit-record` puts `:approved-by` on the
  record's `:payload` only, never on `:value`, so which surfaces retain
  it depends entirely on which key that surface's `commit-record!` branch
  reads -- this function measures that rather than asserting it."
  [db runs]
  (let [orders (sorted-orders db)
        assessments (keep (fn [{:keys [id]}]
                            (when-let [a (store/assessment-of db id)]
                              [id a]))
                          orders)
        approvals (approval-rows-data runs)]
    [{:surface "tech-order record (`:order/upsert` -> `:value`)"
      :n (count orders)
      :retained (count (filter :approved-by orders))}
     {:surface "classification assessment (`:classification-assessment/set` -> `:payload`)"
      :n (count assessments)
      :retained (count (filter (comp :approved-by second) assessments))}
     {:surface "physical-dispatch record (`registry/register-dispatch-record`)"
      :n (count (store/dispatch-history db))
      :retained (count (filter #(get % "approved_by") (store/dispatch-history db)))}
     {:surface "technology-release record (`registry/register-release-record`)"
      :n (count (store/release-history db))
      :retained (count (filter #(get % "approved_by") (store/release-history db)))}
     {:surface "invoice record (`registry/register-invoice-record`)"
      :n (count (store/invoice-history db))
      :retained (count (filter #(get % "approved_by") (store/invoice-history db)))}
     {:surface "audit ledger `:committed` fact (`operation/commit-fact`)"
      :n (count (filter #(= :committed (:t %)) (store/ledger db)))
      :retained (count (filter #(and (= :committed (:t %)) (:approved-by %))
                               (store/ledger db)))}
     {:surface "run audit channel `:approval-granted` fact (in-memory, not persisted)"
      :n (count approvals)
      :retained (count (filter (comp :by :granted) approvals))}]))

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw-name [v] (if (keyword? v) (name v) (str v)))

(defn- code [v] (str "<code>" (esc (kw-name v)) "</code>"))

(defn- td [& cells] (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- ok [s] (str "<span class=\"ok\">" s "</span>"))
(defn- warn [s] (str "<span class=\"warn\">" s "</span>"))
(defn- crit [s] (str "<span class=\"critical\">" s "</span>"))
(defn- muted [s] (str "<span class=\"muted\">" s "</span>"))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>"
       (str/join (map #(str "<th>" % "</th>") headers))
       "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn- section [title lede body]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       (when lede (str "    <p class=\"muted\">" lede "</p>\n"))
       body
       "  </section>\n"))

(defn- yes-no [v] (if v (ok "yes") (muted "no")))

;; ----------------------------- sections -----------------------------

(defn- last-fact-for [ledger id]
  (last (filter #(= (:subject %) id) ledger)))

(defn- verdict-cell [ledger id]
  (let [f (last-fact-for ledger id)]
    (cond
      (nil? f) (muted "no activity")
      (= :committed (:t f)) (ok "committed")
      (= :governor-hold (:t f))
      (crit (str "HARD hold &middot; " (esc (str/join ", " (map kw-name (:basis f))))))
      :else (warn (esc (kw-name (:t f)))))))

(defn- summary-section [db runs]
  (let [ledger (store/ledger db)
        hs (hard-holds db)
        ph (:phase operator)]
    (section
     "Run summary"
     (str "Every figure below is counted off the objects this run actually produced &mdash; "
          "the store, the append-only ledger and each run's audit channel.")
     (table
      ["Property" "Value"]
      [(td "Actor" (code (:actor-id operator)))
       (td "Operator role" (code (:actor-role operator)))
       (td "Governor" (code :tech-export-governor))
       (td "Rollout phase" (str ph " &middot; " (code (:label (get phase/phases ph)))))
       (td "Governor confidence floor" (str "<span class=\"num\">" governor/confidence-floor "</span>"))
       (td "Permanently high-stakes ops (never auto-commit at any phase)"
           (str/join " " (map code (sort-by kw-name governor/high-stakes))))
       (td "Actor runs in this scenario" (str "<span class=\"num\">" (count runs) "</span>"))
       (td "Tech-orders in the store" (str "<span class=\"num\">" (count (store/all-tech-orders db)) "</span>"))
       (td "Ledger facts" (str "<span class=\"num\">" (count ledger) "</span>"))
       (td "Committed facts" (str "<span class=\"num\">" (count (filter #(= :committed (:t %)) ledger)) "</span>"))
       (td "HARD governor holds" (crit (str "<span class=\"num\">" (count hs) "</span>")))
       (td "Distinct HARD-hold rules exercised"
           (crit (str "<span class=\"num\">" (count (into #{} (mapcat :basis) hs)) "</span>")))
       (td "Human approvals granted"
           (str "<span class=\"num\">" (count (filter :granted (approval-rows-data runs))) "</span>"))
       (td "Physical-dispatch records" (str "<span class=\"num\">" (count (store/dispatch-history db)) "</span>"))
       (td "Technology-release records" (str "<span class=\"num\">" (count (store/release-history db)) "</span>"))
       (td "Invoice records" (str "<span class=\"num\">" (count (store/invoice-history db)) "</span>"))]))))

(defn- directory-section [db]
  (let [ledger (store/ledger db)]
    (section
     "Tech-order directory"
     (str "The seeded order book (<code>techtrade.store/demo-data</code>) as it stands "
          "after the run. &lsquo;Last verdict&rsquo; is the most recent ledger fact carrying that order&rsquo;s id.")
     (table
      ["Id" "Order" "Item" "Type" "Delivery mode" "Counterparty" "End-user" "Jurisdiction" "Price" "Last verdict"]
      (for [{:keys [id order-id item-description item-type delivery-mode
                    counterparty end-user jurisdiction price]} (sorted-orders db)]
        (td (code id) (esc order-id) (esc item-description)
            (code item-type) (code delivery-mode)
            (esc counterparty) (esc end-user) (esc jurisdiction)
            (str "<span class=\"num\">" (esc price) "</span>")
            (verdict-cell ledger id)))))))

(defn- posture-section [db]
  (section
   "Export-control posture"
   (str "Read straight off each order&rsquo;s own facts. &lsquo;Effective destination&rsquo; is "
        "<code>techtrade.governor/effective-destination</code> &mdash; for a deemed-export release "
        "(15 C.F.R. &sect;734.13) that is the recipient&rsquo;s nationality, not the order&rsquo;s "
        "own destination-country, which is the only reason <code>to-10</code> holds at all. "
        "<code>EAR99</code> is a real classification outcome; an empty ECCN means the item was never classified.")
   (table
    ["Id" "ECCN" "Destination" "Effective destination" "Deemed export?" "License required?"
     "License authorized?" "Credit cleared?" "Contract on file?" "Sanctions screened?" "Denied-party screened?"]
    (for [{:keys [id eccn destination-country deemed-export? license-required?
                  license-authorized? credit-cleared? contract-terms
                  sanctions-screened? denied-party-screened?] :as o} (sorted-orders db)]
      (td (code id)
          (if (str/blank? (str eccn)) (crit "never classified") (code eccn))
          (esc destination-country)
          (let [ed (governor/effective-destination o)]
            (if deemed-export? (warn (esc ed)) (esc ed)))
          (if deemed-export? (warn "deemed export") (muted "no"))
          (if license-required? (warn "yes") (muted "no"))
          (if license-required?
            (if license-authorized? (ok "yes") (crit "no license on file"))
            (muted "n/a"))
          (yes-no credit-cleared?)
          (if (str/blank? (str contract-terms)) (crit "missing") (ok (esc contract-terms)))
          (yes-no sanctions-screened?)
          (yes-no denied-party-screened?))))))

(defn- lifecycle-section [db]
  (section
   "Actuation state"
   (str "The three real-world acts this actor performs. Each is guarded by a dedicated boolean "
        "(<code>:dispatched?</code> / <code>:released?</code> / <code>:invoiced?</code>), never a "
        "<code>:status</code> value, so a second attempt is refused structurally. "
        "Reference numbers are assigned by <code>techtrade.registry</code> at commit time.")
   (table
    ["Id" "Dispatched?" "Dispatch number" "Released?" "Release number" "Invoiced?" "Invoice number"]
    (for [{:keys [id dispatched? dispatch-number released? release-number
                  invoiced? invoice-number]} (sorted-orders db)]
      (td (code id)
          (yes-no dispatched?) (if dispatch-number (code dispatch-number) (muted "&mdash;"))
          (yes-no released?) (if release-number (code release-number) (muted "&mdash;"))
          (yes-no invoiced?) (if invoice-number (code invoice-number) (muted "&mdash;")))))))

(defn- holds-section [db]
  (let [hs (hard-holds db)]
    (section
     (str "HARD governor holds (" (count hs) ")")
     (str "Un-overridable. A HARD hold never reaches the human-approval node at all &mdash; "
          "<code>techtrade.operation</code> routes <code>:hold</code> straight to the ledger, so there "
          "is no approver who could have waved any of these through. The rule names and the detail "
          "text below are produced by <code>techtrade.governor</code> itself.")
     (table
      ["Op" "Order" "Rule" "Confidence" "Governor detail"]
      (for [{:keys [op subject basis violations confidence]} hs]
        (td (code op) (code subject)
            (crit (esc (str/join ", " (map kw-name basis))))
            (str "<span class=\"num\">" (esc confidence) "</span>")
            (esc (str/join " / " (map :detail violations)))))))))

(defn- rule-coverage-section [db]
  (let [hs (hard-holds db)
        by-rule (group-by identity (mapcat :basis hs))]
    (section
     (str "Distinct HARD-hold rules exercised (" (count by-rule) ")")
     (str "Grouped from the same ledger facts. The point of the seed data is that each order "
          "isolates exactly one failure mode, so a rule firing here means that rule was reached on "
          "its own merits &mdash; in particular <code>:eccn-classification-missing</code> "
          "(<code>to-5</code>, never classified) and <code>:license-required-unauthorized</code> "
          "(<code>to-6</code>, classified 5A002 with no license) are two genuinely different real-world "
          "postures, not one collapsed check.")
     (table
      ["Rule" "Holds" "Orders" "Ops"]
      (for [[rule occurrences] (sort-by (comp kw-name key) by-rule)
            :let [rows (filter #(some #{rule} (:basis %)) hs)]]
        (td (crit (code rule))
            (str "<span class=\"num\">" (count occurrences) "</span>")
            (str/join " " (map (comp code :subject) rows))
            (str/join " " (distinct (map (comp code :op) rows)))))))))

(defn- approvals-section [runs]
  (let [rows (approval-rows-data runs)]
    (section
     (str "Human approvals (" (count (filter :granted rows)) " granted of " (count rows) " requested)")
     (str "Everything the governor cleared but that is still a real-world act pauses here. "
          "<code>langgraph</code>&rsquo;s <code>interrupt-before #{:request-approval}</code> stops the "
          "graph and a human resumes it; these rows come out of each run&rsquo;s own audit channel, "
          "not the ledger.")
     (table
      ["Op" "Order" "Escalation reason" "Phase" "Confidence" "Approved by" "Final disposition"]
      (for [{:keys [op subject reason phase confidence granted disposition]} rows]
        (td (code op) (code subject) (code reason)
            (str "<span class=\"num\">" phase "</span>")
            (str "<span class=\"num\">" (esc confidence) "</span>")
            (if granted (ok (esc (:by granted))) (muted "not granted"))
            (if (= :commit disposition) (ok "committed") (warn (esc (kw-name disposition))))))))))

(defn- records-section [db]
  (let [row (fn [kind r]
              (td (code kind) (code (get r "record_id")) (code (get r "tech_order_id"))
                  (esc (get r "jurisdiction"))
                  (if (get r "immutable") (ok "immutable") (warn "mutable"))))]
    (section
     "Committed actuation records"
     (str "Built by <code>techtrade.registry</code> at commit time and appended to the store&rsquo;s "
          "three histories. Every certificate this actor produces is an UNSIGNED draft &mdash; signature "
          "is the operator&rsquo;s act, not the actor&rsquo;s. Sequence numbers are jurisdiction-scoped.")
     (table
      ["Kind" "Record id" "Order" "Jurisdiction" "Immutability"]
      (concat (map (partial row :tech-dispatch-draft) (store/dispatch-history db))
              (map (partial row :tech-release-draft) (store/release-history db))
              (map (partial row :tech-invoice-draft) (store/invoice-history db)))))))

(defn- assessments-section [db]
  (let [rows (keep (fn [{:keys [id]}]
                     (when-let [a (store/assessment-of db id)] [id a]))
                   (sorted-orders db))]
    (section
     (str "Committed classification assessments (" (count rows) ")")
     (str "The per-jurisdiction diligence checklist the governor&rsquo;s "
          "<code>:evidence-incomplete</code> check reads. An order with no committed assessment cannot "
          "be dispatched, released or invoiced at all &mdash; which is why <code>to-2</code> holds twice.")
     (table
      ["Order" "Jurisdiction" "Spec-basis" "Legal basis" "Checklist items" "Approved by"]
      (for [[id {:keys [jurisdiction spec-basis legal-basis checklist approved-by]}] rows]
        (td (code id) (esc jurisdiction)
            (if spec-basis (esc spec-basis) (crit "none"))
            (esc (or legal-basis "&mdash;"))
            (str "<span class=\"num\">" (count checklist) "</span> &middot; "
                 (esc (str/join "; " checklist)))
            (if approved-by (ok (esc approved-by)) (muted "not retained"))))))))

(defn- attribution-section [db runs]
  (let [rows (approver-attribution db runs)
        retaining (filter #(pos? (:retained %)) rows)
        losing (filter #(and (pos? (:n %)) (zero? (:retained %))) rows)]
    (section
     "Approver attribution (measured, not asserted)"
     (str "<code>techtrade.operation/commit-record</code> attaches the approving human&rsquo;s id to the "
          "record&rsquo;s <code>:payload</code> only, never to its <code>:value</code>. Which SSoT "
          "surfaces therefore keep it depends on which key that surface&rsquo;s "
          "<code>commit-record!</code> branch reads. The table below is produced by walking the real "
          "store after this run, so it self-corrects if the actor changes.")
     (str
      (table
       ["SSoT surface" "Records" "Carrying an approver id"]
       (for [{:keys [surface n retained]} rows]
         (td surface
             (str "<span class=\"num\">" n "</span>")
             (cond (zero? n) (muted "&mdash; (no records)")
                   (= retained n) (ok (str "<span class=\"num\">" retained "</span> / all"))
                   (zero? retained) (crit "0 &mdash; approver id not retained")
                   :else (warn (str "<span class=\"num\">" retained "</span> of " n))))))
      "    <p>"
      (if (seq retaining)
        (str "Read plainly: an approving human&rsquo;s id "
             (ok "is") " retained by "
             (str/join ", " (map (comp esc :surface) retaining))
             ". ")
        (str (crit "No SSoT surface on this actor retains the approver id at all.") " "))
      (if (seq losing)
        (str "It is " (crit "not") " retained by "
             (str/join ", " (map (comp esc :surface) losing))
             " &mdash; for those, the page cannot tell you who signed off, and this notice is here so "
             "that silence is not mistaken for &lsquo;nobody approved&rsquo;. The approval did happen: "
             "it is visible in the run audit channel above, it is simply not carried into those "
             "persisted records.")
        "Every surface with records on it carries the approver id.")
      "</p>\n"))))

(defn- jurisdiction-section []
  (let [cov (facts/coverage)]
    (section
     (str "Jurisdiction spec-basis catalog (" (:covered cov) ")")
     (str "<code>techtrade.facts/catalog</code> &mdash; the official sources the governor requires a "
          "proposal to cite. A jurisdiction that is not in this table has NO spec-basis, full stop; "
          "the advisor must not invent one and the governor holds if it tries.")
     (str
      (table
       ["ISO3" "Owner authority" "Legal basis" "Classification list" "Provenance"]
       (for [[iso3 {:keys [owner-authority legal-basis classification-list provenance]}]
             (sort-by key facts/catalog)]
         (td (code iso3) (esc owner-authority) (esc legal-basis) (esc classification-list)
             (str "<code>" (esc provenance) "</code>"))))
      "    <p class=\"muted\">" (esc (:note cov)) "</p>\n"))))

(defn- phase-section []
  (section
   "Rollout phase gate"
   (str "<code>techtrade.phase/phases</code>, rendered as data. The three actuation ops are absent "
        "from every phase&rsquo;s auto set including phase 3 &mdash; a permanent structural fact, not a "
        "rollout milestone still to come. The governor&rsquo;s own high-stakes gate enforces the same "
        "invariant independently, so two layers agree.")
   (str
    (table
     ["Phase" "Label" "Ops allowed to write" "Ops allowed to auto-commit when clean"]
     (for [[n {:keys [label writes auto]}] (sort-by key phase/phases)]
       (td (str "<span class=\"num\">" n "</span>")
           (esc label)
           (if (seq writes) (str/join " " (map code (sort-by kw-name writes))) (muted "none"))
           (if (seq auto) (str/join " " (map code (sort-by kw-name auto))) (muted "none")))))
    (table
     ["Op" "Write op?" "Permanently high-stakes?" "Auto-commit at phase 3?"]
     (let [auto3 (:auto (get phase/phases 3))]
       (for [o (sort-by kw-name phase/write-ops)]
         (td (code o)
             (yes-no (contains? phase/write-ops o))
             (if (contains? governor/high-stakes o)
               (crit "yes &middot; always a human call")
               (muted "no"))
             (if (contains? auto3 o) (ok "yes, when governor-clean") (warn "no &middot; human approval")))))))))

(defn- ledger-section [db]
  (let [ledger (store/ledger db)]
    (section
     (str "Audit ledger (" (count ledger) " facts, append-only, in run order)")
     "Every commit and every hold this scenario produced, in the order the actor wrote them."
     (table
      ["#" "Fact" "Op" "Order" "Actor" "Basis"]
      (map-indexed
       (fn [i {:keys [t op subject actor basis disposition]}]
         (td (str "<span class=\"num\">" i "</span>")
             (if (= :governor-hold t) (crit (code t)) (ok (code t)))
             (code op) (code subject) (code actor)
             (esc (or (some->> basis (map kw-name) (str/join ", "))
                      (kw-name disposition)))))
       ledger)))))

;; ----------------------------- document -----------------------------

(defn render
  "Pure: `{:db .. :runs ..}` (from `run-demo!`) -> the whole HTML
  document as a string. No clock reads, no randomness, every collection
  sorted explicitly."
  [{:keys [db runs]}]
  (str
   "<!doctype html>\n"
   "<html lang=\"en\"><head><meta charset=\"utf-8\">\n"
   "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
   "<title>cloud-itonami-isic-4651 &middot; techtrade operator console</title>\n"
   "<style>\n" (jp-go-dds.skin/dds+skin) "\n</style>\n"
   "</head><body>\n"
   "<header class=\"bar\">\n"
   "  <h1>Wholesale of computers, computer peripheral equipment and software (ISIC 4651) &mdash; Operator Console</h1>\n"
   "  <span class=\"badge\">read-only sample &middot; governor-gated &middot; dispatch / release / settlement always human-approved</span>\n"
   "</header>\n"
   "<main>\n"
   "  <p class=\"subtitle\">Generated at build time by <code>techtrade.render-html</code> "
   "(<code>clojure -M:dev:render-html</code>) by running the real actor stack "
   "&mdash; <code>techtrade.operation</code> (a <code>langgraph</code> StateGraph) &rarr; "
   "<code>techtrade.governor</code> &rarr; <code>techtrade.store</code>. Nothing on this page is "
   "hand-written sample data: every id, ECCN, rule, record number and count below was read back out of "
   "the store, the append-only ledger or a run&rsquo;s audit channel after the scenario executed. "
   "The generator refuses to write this file if the scenario produced no HARD governor hold.</p>\n"
   (summary-section db runs)
   (directory-section db)
   (posture-section db)
   (lifecycle-section db)
   (holds-section db)
   (rule-coverage-section db)
   (approvals-section runs)
   (records-section db)
   (assessments-section db)
   (attribution-section db runs)
   (jurisdiction-section)
   (phase-section)
   (ledger-section db)
   "</main>\n"
   "<footer>\n"
   "  <p>cloud-itonami &middot; TechTradeAdvisor &#8867; <code>:tech-export-governor</code> &middot; "
   "styled with <a href=\"https://github.com/kotoba-lang/jp-go-digital-design-system\">jp-go-dds</a> "
   "(&#12487;&#12472;&#12479;&#12523;&#24193;&#12487;&#12470;&#12452;&#12531;&#12471;&#12473;&#12486;&#12512;). "
   "Regenerate with <code>clojure -M:dev:render-html</code>.</p>\n"
   "</footer>\n"
   "</body></html>\n"))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db runs] :as result} (run-demo!)
        hs (hard-holds db)]
    (when (empty? hs)
      (throw (ex-info "no governor hold fact on the ledger — refusing to write a console that shows no real hold"
                      {:ledger-facts (count (store/ledger db))})))
    (spit out (render result) :encoding "UTF-8")
    (println "wrote" out
             (str "(" (count runs) " actor runs, "
                  (count (store/ledger db)) " ledger facts, "
                  (count hs) " HARD holds across "
                  (count (into #{} (mapcat :basis) hs)) " distinct rules, "
                  (count (store/dispatch-history db)) " dispatch / "
                  (count (store/release-history db)) " release / "
                  (count (store/invoice-history db)) " invoice records)"))))
