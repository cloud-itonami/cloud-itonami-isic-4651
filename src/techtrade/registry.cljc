(ns techtrade.registry
  "Pure-function tech-dispatch + tech-release + tech-invoice record
  construction -- an append-only computer-and-software-wholesale
  book-of-record draft.

  Like every principal-trading sibling's own registry, this vertical's
  Tech Export Governor needs NO registry range-check functions at all:
  its domain checks (credit-uncleared, contract-missing, eccn-
  classification-missing, license-required-unauthorized, counterparty-
  sanctions-flag-unresolved, denied-party-list-flag-unresolved) are
  direct entity boolean/value reads in `techtrade.governor`, off
  dedicated facts on the `tech-order` record. So this namespace is
  RECORD CONSTRUCTION ONLY -- no pure range checks to host here.

  UNLIKE every prior sibling (which drafts exactly two kinds of record,
  dispatch + invoice), this namespace drafts THREE: a physical-dispatch
  record, a technology-release record (the deemed-export-aware
  electronic release of controlled software/source code/technical
  data), and an invoice record -- see `techtrade.store`/`techtrade.
  governor` namespace docstrings for why this vertical genuinely has a
  three-member actuation set rather than two.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a tech-dispatch, tech-release or tech-
  invoice record -- every operator/jurisdiction assigns its own
  reference format. This namespace does NOT invent one beyond a
  jurisdiction-scoped sequence number; it validates the record's
  required fields, the same honest, non-fabricating discipline
  `techtrade.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real warehouse-management/ERP/billing system. It builds
  the RECORD an operator would keep, not the act of dispatching real
  hardware, releasing real controlled technology, or settling a real
  invoice itself (that is `techtrade.operation`'s `:delivery/dispatch`/
  `:technology/release`/`:invoice/settle`, always human-gated -- see
  README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- record construction -----------------------------

(defn register-dispatch-record
  "Validate + construct the PHYSICAL-DISPATCH registration DRAFT -- the
  operator's own legal act of dispatching real computer hardware/
  peripherals to a counterparty. Pure function -- does not touch any
  real warehouse-management or ERP system; it builds the RECORD an
  operator would keep. `techtrade.governor` independently re-verifies
  the counterparty's credit-clearance, contract-on-file, export-
  classification, license-authorization, sanctions-screening, denied-
  party-screening and evidence-completeness ground truth, and blocks a
  double-dispatch of the same tech-order, before this is ever allowed
  to commit."
  [tech-order-id jurisdiction sequence]
  (when-not (and tech-order-id (not= tech-order-id ""))
    (throw (ex-info "tech-dispatch: tech_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "tech-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "tech-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper-case jurisdiction) "-DISPATCH-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "tech-dispatch-draft"
                "tech_order_id" tech-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "TechDispatch" dispatch-number dispatch-number)}))

(defn register-release-record
  "Validate + construct the TECHNOLOGY-RELEASE registration DRAFT -- the
  operator's own legal act of releasing controlled software / source
  code / technical data to an end-user, INCLUDING a deemed-export
  release (15 C.F.R. §734.13) to a foreign national physically located
  inside the exporting jurisdiction. Pure function -- does not touch any
  real code-repository, file-transfer or access-control system; it
  builds the RECORD an operator would keep. `techtrade.governor`
  independently re-verifies the SAME ground truth as a physical
  dispatch (credit-clearance, contract-on-file, classification, license
  authorization, sanctions-screening, denied-party-screening,
  evidence-completeness), evaluated against the release's own EFFECTIVE
  destination (the recipient's nationality when `:deemed-export?` is
  true, not the order's own `:destination-country`), and blocks a
  double-release of the same tech-order, before this is ever allowed to
  commit."
  [tech-order-id jurisdiction sequence]
  (when-not (and tech-order-id (not= tech-order-id ""))
    (throw (ex-info "tech-release: tech_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "tech-release: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "tech-release: sequence must be >= 0" {})))
  (let [release-number (str (str/upper-case jurisdiction) "-RELEASE-" (zero-pad sequence 6))
        record {"record_id" release-number
                "kind" "tech-release-draft"
                "tech_order_id" tech-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "release_number" release-number
     "certificate" (unsigned-certificate "TechRelease" release-number release-number)}))

(defn register-invoice-record
  "Validate + construct the INVOICE registration DRAFT -- the operator's
  own legal act of settling a real computer-and-software-wholesale
  invoice (the money side of the trade, custody/financial transfer).
  Pure function -- does not touch any real billing or accounts-
  receivable system; it builds the RECORD an operator would keep.
  `techtrade.governor` independently re-verifies the sanctions-
  screening, denied-party-screening and evidence-completeness ground
  truth, and blocks a double-invoice of the same tech-order, before
  this is ever allowed to commit."
  [tech-order-id jurisdiction sequence]
  (when-not (and tech-order-id (not= tech-order-id ""))
    (throw (ex-info "tech-invoice: tech_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "tech-invoice: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "tech-invoice: sequence must be >= 0" {})))
  (let [invoice-number (str (str/upper-case jurisdiction) "-INVOICE-" (zero-pad sequence 6))
        record {"record_id" invoice-number
                "kind" "tech-invoice-draft"
                "tech_order_id" tech-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "invoice_number" invoice-number
     "certificate" (unsigned-certificate "TechInvoice" invoice-number invoice-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
