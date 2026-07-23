(ns techtrade.store
  "SSoT for the computer-and-software-wholesale actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every prior `cloud-itonami-isic-*` actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/techtrade/store_contract_test.clj), which is the whole point:
  the actor, the Tech Export Governor and the audit ledger never know
  which SSoT they run on.

  UNLIKE every principal-trading sibling in this fleet (fuel-wholesale,
  general-trading, metal-wholesale, textile-wholesale, waste-wholesale --
  each with exactly TWO real-world actuation events, dispatch + invoice),
  this vertical has THREE: `:delivery/dispatch` (a physical cross-border
  shipment of hardware), `:technology/release` (an electronic/documentary
  release of controlled software, source code or technical data --
  including the deemed-export scenario, 15 C.F.R. §734.13, where the
  recipient is a foreign national physically located INSIDE the
  exporting jurisdiction but the release is still deemed an export to
  their home country), and `:invoice/settle` (the money side). A single
  `tech-order` uses EITHER the dispatch path OR the release path
  (`:delivery-mode`), never both -- see `techtrade.governor` namespace
  docstring for the full reasoning. Dedicated double-actuation-guard
  booleans (`:dispatched?`/`:released?`/`:invoiced?`, never a `:status`
  value) prevent re-running any of the three against the same order.

  The ledger stays append-only on every backend: which tech-order was
  verified for a jurisdiction with no official spec-basis, which order
  had NO export-control classification on file at all, which order was
  classified but lacked an authorized license for its destination/end-
  user, which counterparty had an unresolved OFAC-style sanctions flag
  or an unresolved denied-party (Entity List/Denied Persons List) flag,
  which order was dispatched, released, or invoiced, on what
  jurisdictional and classification basis, approved by whom -- always a
  query over an immutable log."
  (:require [techtrade.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (tech-order [s id])
  (all-tech-orders [s])
  (assessment-of [s tech-order-id] "committed classification assessment, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only physical-dispatch history (techtrade.registry drafts)")
  (release-history [s] "the append-only technology-release history (techtrade.registry drafts)")
  (invoice-history [s] "the append-only invoice history (techtrade.registry drafts)")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-release-sequence [s jurisdiction] "next release-number sequence for a jurisdiction")
  (next-invoice-sequence [s jurisdiction] "next invoice-number sequence for a jurisdiction")
  (tech-order-already-dispatched? [s tech-order-id] "has this order already been physically dispatched?")
  (tech-order-already-released? [s tech-order-id] "has this order's technology already been released?")
  (tech-order-already-invoiced? [s tech-order-id] "has this order already been invoiced?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-tech-orders [s tech-orders] "replace/seed the tech-order directory (map id->tech-order)"))

;; ----------------------------- demo data -----------------------------

(defn- base-order
  "The neutral, clean tech-order shape (every field in its safe state),
  so each demo order below isolates exactly ONE failure mode by
  overriding a single field. `:eccn` \"EAR99\" means 'reviewed and
  determined not on the control list' (a REAL, valid classification
  outcome, distinct from `nil` which means 'never classified at all' --
  see `techtrade.governor`'s `eccn-classification-missing-violations`
  vs `license-required-unauthorized-violations`)."
  [overrides]
  (merge {:id "to-1" :order-id "TO-2026-0001"
          :item-description "Rack-mount enterprise servers, 24-unit lot"
          :item-type :hardware
          :delivery-mode :physical-shipment
          :destination-country "GBR" :end-user "Northfield Data Centres Ltd"
          :counterparty "Northfield Data Centres Ltd"
          :price 480000.00 :contract-terms "FCA warehouse, net 30 days"
          :credit-cleared? true :sanctions-screened? true
          :denied-party-screened? true
          :eccn "EAR99" :license-required? false :license-authorized? false
          :deemed-export? false :release-recipient-nationality nil
          :dispatched? false :released? false :invoiced? false
          :jurisdiction "USA" :status :intake
          :dispatch-number nil :release-number nil :invoice-number nil}
         overrides))

(defn demo-data
  "A small, self-contained tech-order set covering all THREE actuation
  lifecycles (dispatch, release, invoice settlement) plus the Tech
  Export Governor's own checks, so the actor + tests run offline. Each
  violation order isolates exactly ONE failure mode (the rest stay
  clean) following the 'exercise the failure mode directly, never only
  via a happy-path actuation' discipline every sibling governor's demo
  data establishes. `to-5` and `to-6` deliberately isolate the TWO
  distinct failure modes this vertical's domain-defining check splits
  into (unclassified vs. classified-but-unlicensed) -- see
  `techtrade.governor`. `to-9`/`to-10` isolate the `:technology/release`
  path, including a genuine deemed-export scenario (`to-10`) where the
  license determination runs against the release recipient's
  nationality, not the order's own `:destination-country`."
  []
  {:tech-orders
   (into {}
         (for [o [(base-order {:id "to-1" :order-id "TO-2026-0001"})

                  (base-order {:id "to-2" :order-id "TO-2026-0002"
                               :counterparty "Atlantis Peripherals Ltd"
                               :end-user "Atlantis Peripherals Ltd"
                               :jurisdiction "ATL"})

                  (base-order {:id "to-3" :order-id "TO-2026-0003"
                               :counterparty "Cedar Compute Resellers"
                               :end-user "Cedar Compute Resellers"
                               :credit-cleared? false})

                  (base-order {:id "to-4" :order-id "TO-2026-0004"
                               :counterparty "Delta Peripheral Distribution BV"
                               :end-user "Delta Peripheral Distribution BV"
                               :contract-terms nil})

                  (base-order {:id "to-5" :order-id "TO-2026-0005"
                               :counterparty "Eagle Computer Wholesale SA"
                               :end-user "Eagle Computer Wholesale SA"
                               :eccn nil})

                  (base-order {:id "to-6" :order-id "TO-2026-0006"
                               :item-description "Network-encryption appliance, rack-mount"
                               :counterparty "Falcon Secure Networks Inc"
                               :end-user "Falcon Secure Networks Inc"
                               :item-type :encryption-item
                               :eccn "5A002" :license-required? true
                               :license-authorized? false})

                  (base-order {:id "to-7" :order-id "TO-2026-0007"
                               :counterparty "Granite Systems Trading"
                               :end-user "Granite Systems Trading"
                               :sanctions-screened? false})

                  (base-order {:id "to-8" :order-id "TO-2026-0008"
                               :counterparty "Harbor Peripheral Traders"
                               :end-user "Harbor Peripheral Traders"
                               :denied-party-screened? false})

                  (base-order {:id "to-9" :order-id "TO-2026-0009"
                               :item-description "Storage-virtualization management suite, electronic release"
                               :item-type :software
                               :delivery-mode :technology-release
                               :counterparty "Indigo Cloud Systems KK"
                               :end-user "Indigo Cloud Systems KK"
                               :eccn "EAR99" :license-required? false
                               :deemed-export? true
                               :release-recipient-nationality "JPN"})

                  (base-order {:id "to-10" :order-id "TO-2026-0010"
                               :item-description "Full-disk-encryption source code, electronic release to visiting engineer"
                               :item-type :encryption-item
                               :delivery-mode :technology-release
                               :counterparty "Juniper Research Partners LLC"
                               :end-user "Juniper Research Partners LLC (visiting engineer, on-site)"
                               :eccn "5D002" :license-required? true
                               :license-authorized? false
                               :deemed-export? true
                               :release-recipient-nationality "QQQ"})]]
           [(:id o) o]))})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-order!
  "Backend-agnostic `:order/mark-dispatched` -- looks up the tech-order
  via the protocol and drafts the physical-dispatch record, and returns
  {:result .. :tech-order-patch ..} for the caller to persist."
  [s tech-order-id]
  (let [to (tech-order s tech-order-id)
        seq-n (next-dispatch-sequence s (:jurisdiction to))
        result (registry/register-dispatch-record tech-order-id (:jurisdiction to) seq-n)]
    {:result result
     :tech-order-patch {:dispatched? true
                        :dispatch-number (get result "dispatch_number")}}))

(defn- release-order!
  "Backend-agnostic `:order/mark-released` -- looks up the tech-order via
  the protocol and drafts the technology-release record, and returns
  {:result .. :tech-order-patch ..} for the caller to persist."
  [s tech-order-id]
  (let [to (tech-order s tech-order-id)
        seq-n (next-release-sequence s (:jurisdiction to))
        result (registry/register-release-record tech-order-id (:jurisdiction to) seq-n)]
    {:result result
     :tech-order-patch {:released? true
                        :release-number (get result "release_number")}}))

(defn- invoice-order!
  "Backend-agnostic `:order/mark-invoiced` -- looks up the tech-order via
  the protocol and drafts the invoice record, and returns
  {:result .. :tech-order-patch ..} for the caller to persist."
  [s tech-order-id]
  (let [to (tech-order s tech-order-id)
        seq-n (next-invoice-sequence s (:jurisdiction to))
        result (registry/register-invoice-record tech-order-id (:jurisdiction to) seq-n)]
    {:result result
     :tech-order-patch {:invoiced? true
                        :invoice-number (get result "invoice_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (tech-order [_ id] (get-in @a [:tech-orders id]))
  (all-tech-orders [_] (sort-by :id (vals (:tech-orders @a))))
  (assessment-of [_ tech-order-id] (get-in @a [:assessments tech-order-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatches @a))
  (release-history [_] (:releases @a))
  (invoice-history [_] (:invoices @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-release-sequence [_ jurisdiction] (get-in @a [:release-sequences jurisdiction] 0))
  (next-invoice-sequence [_ jurisdiction] (get-in @a [:invoice-sequences jurisdiction] 0))
  (tech-order-already-dispatched? [_ tech-order-id] (boolean (get-in @a [:tech-orders tech-order-id :dispatched?])))
  (tech-order-already-released? [_ tech-order-id] (boolean (get-in @a [:tech-orders tech-order-id :released?])))
  (tech-order-already-invoiced? [_ tech-order-id] (boolean (get-in @a [:tech-orders tech-order-id :invoiced?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (swap! a update-in [:tech-orders (:id value)] merge value)

      :classification-assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :order/mark-dispatched
      (let [tech-order-id (first path)
            {:keys [result tech-order-patch]} (dispatch-order! s tech-order-id)
            jurisdiction (:jurisdiction (tech-order s tech-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:tech-orders tech-order-id] merge tech-order-patch)
                       (update :dispatches registry/append result))))
        result)

      :order/mark-released
      (let [tech-order-id (first path)
            {:keys [result tech-order-patch]} (release-order! s tech-order-id)
            jurisdiction (:jurisdiction (tech-order s tech-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:release-sequences jurisdiction] (fnil inc 0))
                       (update-in [:tech-orders tech-order-id] merge tech-order-patch)
                       (update :releases registry/append result))))
        result)

      :order/mark-invoiced
      (let [tech-order-id (first path)
            {:keys [result tech-order-patch]} (invoice-order! s tech-order-id)
            jurisdiction (:jurisdiction (tech-order s tech-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:invoice-sequences jurisdiction] (fnil inc 0))
                       (update-in [:tech-orders tech-order-id] merge tech-order-patch)
                       (update :invoices registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-tech-orders [s tech-orders] (when (seq tech-orders) (swap! a assoc :tech-orders tech-orders)) s))

(defn seed-db
  "A MemStore seeded with the demo tech-order set. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :dispatch-sequences {} :dispatches []
                           :release-sequences {} :releases []
                           :invoice-sequences {} :invoices []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment payloads, ledger facts, dispatch/
  release/invoice records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:tech-order/id                        {:db/unique :db.unique/identity}
   :assessment/tech-order-id             {:db/unique :db.unique/identity}
   :ledger/seq                           {:db/unique :db.unique/identity}
   :dispatch/seq                         {:db/unique :db.unique/identity}
   :release/seq                          {:db/unique :db.unique/identity}
   :invoice/seq                          {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction       {:db/unique :db.unique/identity}
   :release-sequence/jurisdiction        {:db/unique :db.unique/identity}
   :invoice-sequence/jurisdiction        {:db/unique :db.unique/identity}})

;; Every tech-order field is stored as its own Datomic attr so a governor
;; pull reads the exact ground truth (no blob decode). Boolean fields
;; are coerced on read so a missing attr reads back as false (parity
;; with MemStore). Keyword-valued fields round-trip via `ls/enc`/`ls/dec*`
;; (stored as an EDN string) so `:item-type`/`:delivery-mode` survive
;; the pull as keywords, not bare strings. [field-key tx-attr kind]
;; kind ∈ #{:plain :bool :kw}
(def ^:private tech-order-fields
  [[:id :tech-order/id :plain]
   [:order-id :tech-order/order-id :plain]
   [:item-description :tech-order/item-description :plain]
   [:item-type :tech-order/item-type :kw]
   [:delivery-mode :tech-order/delivery-mode :kw]
   [:destination-country :tech-order/destination-country :plain]
   [:end-user :tech-order/end-user :plain]
   [:counterparty :tech-order/counterparty :plain]
   [:price :tech-order/price :plain]
   [:contract-terms :tech-order/contract-terms :plain]
   [:credit-cleared? :tech-order/credit-cleared? :bool]
   [:sanctions-screened? :tech-order/sanctions-screened? :bool]
   [:denied-party-screened? :tech-order/denied-party-screened? :bool]
   [:eccn :tech-order/eccn :plain]
   [:license-required? :tech-order/license-required? :bool]
   [:license-authorized? :tech-order/license-authorized? :bool]
   [:deemed-export? :tech-order/deemed-export? :bool]
   [:release-recipient-nationality :tech-order/release-recipient-nationality :plain]
   [:dispatched? :tech-order/dispatched? :bool]
   [:released? :tech-order/released? :bool]
   [:invoiced? :tech-order/invoiced? :bool]
   [:jurisdiction :tech-order/jurisdiction :plain]
   [:status :tech-order/status :kw]
   [:dispatch-number :tech-order/dispatch-number :plain]
   [:release-number :tech-order/release-number :plain]
   [:invoice-number :tech-order/invoice-number :plain]])

(defn- tech-order->tx [to]
  (reduce (fn [tx [k attr kind]]
            (let [v (get to k)]
              (cond-> tx
                (some? v) (assoc attr (if (= kind :kw) (ls/enc v) v)))))
          {:tech-order/id (:id to)}
          tech-order-fields))

(def ^:private tech-order-pull (mapv second tech-order-fields))

(defn- pull->tech-order [m]
  (when (:tech-order/id m)
    (reduce (fn [to [k attr kind]]
              (let [v (get m attr)]
                (cond
                  (= kind :bool)  (assoc to k (boolean v))
                  (= kind :kw)    (cond-> to (some? v) (assoc k (ls/dec* v)))
                  (some? v)       (assoc to k v)
                  :else           to)))
            {:id (:tech-order/id m)}
            tech-order-fields)))

(defrecord DatomicStore [conn]
  Store
  (tech-order [_ id]
    (pull->tech-order (d/pull (d/db conn) tech-order-pull [:tech-order/id id])))
  (all-tech-orders [_]
    (->> (d/q '[:find [?id ...] :where [?e :tech-order/id ?id]] (d/db conn))
         (map #(pull->tech-order (d/pull (d/db conn) tech-order-pull [:tech-order/id %])))
         (sort-by :id)))
  (assessment-of [_ tech-order-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?toid
                :where [?a :assessment/tech-order-id ?toid] [?a :assessment/payload ?p]]
              (d/db conn) tech-order-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (dispatch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :dispatch/seq ?s] [?e :dispatch/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (release-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :release/seq ?s] [?e :release/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (invoice-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :invoice/seq ?s] [?e :invoice/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-release-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :release-sequence/jurisdiction ?j] [?e :release-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-invoice-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :invoice-sequence/jurisdiction ?j] [?e :invoice-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (tech-order-already-dispatched? [s tech-order-id]
    (boolean (:dispatched? (tech-order s tech-order-id))))
  (tech-order-already-released? [s tech-order-id]
    (boolean (:released? (tech-order s tech-order-id))))
  (tech-order-already-invoiced? [s tech-order-id]
    (boolean (:invoiced? (tech-order s tech-order-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (d/transact! conn [(tech-order->tx value)])

      :classification-assessment/set
      (d/transact! conn [{:assessment/tech-order-id (first path) :assessment/payload (ls/enc payload)}])

      :order/mark-dispatched
      (let [tech-order-id (first path)
            {:keys [result tech-order-patch]} (dispatch-order! s tech-order-id)
            jurisdiction (:jurisdiction (tech-order s tech-order-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(tech-order->tx (assoc tech-order-patch :id tech-order-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch/seq (count (dispatch-history s)) :dispatch/record (ls/enc (get result "record"))}])
        result)

      :order/mark-released
      (let [tech-order-id (first path)
            {:keys [result tech-order-patch]} (release-order! s tech-order-id)
            jurisdiction (:jurisdiction (tech-order s tech-order-id))
            next-n (inc (next-release-sequence s jurisdiction))]
        (d/transact! conn
                     [(tech-order->tx (assoc tech-order-patch :id tech-order-id))
                      {:release-sequence/jurisdiction jurisdiction :release-sequence/next next-n}
                      {:release/seq (count (release-history s)) :release/record (ls/enc (get result "record"))}])
        result)

      :order/mark-invoiced
      (let [tech-order-id (first path)
            {:keys [result tech-order-patch]} (invoice-order! s tech-order-id)
            jurisdiction (:jurisdiction (tech-order s tech-order-id))
            next-n (inc (next-invoice-sequence s jurisdiction))]
        (d/transact! conn
                     [(tech-order->tx (assoc tech-order-patch :id tech-order-id))
                      {:invoice-sequence/jurisdiction jurisdiction :invoice-sequence/next next-n}
                      {:invoice/seq (count (invoice-history s)) :invoice/record (ls/enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (ls/enc fact)}])
    fact)
  (with-tech-orders [s tech-orders]
    (when (seq tech-orders) (d/transact! conn (mapv tech-order->tx (vals tech-orders)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:tech-orders ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [tech-orders]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-tech-orders s tech-orders))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo tech-order set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
