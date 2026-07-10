(ns techtrade.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean hardware order
  through intake -> classification verification -> physical dispatch
  (escalate/approve/commit) -> invoice settlement (escalate/approve/
  commit), a clean deemed-export software release through the SAME
  loop via `:technology/release` instead of `:delivery/dispatch`, then
  shows HARD-hold scenarios: a jurisdiction with no spec-basis, a
  counterparty whose credit has not been cleared, an order with no
  contract-terms on file, an item that has NEVER been classified at all
  (`:eccn-classification-missing`), a DIFFERENT order that HAS been
  classified but requires a license for its destination with none on
  file (`:license-required-unauthorized` -- proving these are two
  genuinely separate failure modes, not one collapsed check), a
  counterparty that has not passed OFAC-style sanctions screening, a
  DIFFERENT counterparty that has not passed denied-party-list
  (Entity List/Denied Persons List) screening (proving this is ALSO
  distinct from generic sanctions screening), a deemed-export release
  whose classification requires a license for the RECIPIENT'S
  nationality (not the order's own destination-country) with none on
  file, a double dispatch, a double release, and a double invoice.

  Like every sibling actor's domain checks, this actor's checks
  (`credit-uncleared`, `contract-missing`, `eccn-classification-
  missing`, `license-required-unauthorized`,
  `counterparty-sanctions-flag-unresolved`, `denied-party-list-flag-
  unresolved`) are evaluated directly at `:delivery/dispatch`/
  `:technology/release` (and the two screening checks at `:invoice/
  settle` too) rather than via a separate screening op -- a real
  dispatch/release decision validates counterparty credit, contract-on-
  file, export classification, license authorization, sanctions
  screening and denied-party screening at the point of the act itself,
  not as a discrete pre-screening ceremony. Each check is still
  exercised directly and independently below, one order per HARD-hold
  scenario, following the SAME 'exercise the failure mode directly,
  never only via a happy-path actuation' discipline `parksafety`'s
  ADR-2607071922 Decision 5 and every sibling since establish."
  (:require [langgraph.graph :as g]
            [techtrade.store :as store]
            [techtrade.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :export-compliance-officer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== order/intake to-1 (USA, hardware, clean) ==")
    (println (exec-op actor "t1" {:op :order/intake :subject "to-1"
                                  :patch {:id "to-1" :counterparty "Northfield Data Centres Ltd"}} operator))

    (println "== classification/verify to-1 (escalates -- human approves) ==")
    (println (exec-op actor "t2" {:op :classification/verify :subject "to-1"} operator))
    (println (approve! actor "t2"))

    (println "== delivery/dispatch to-1 (always escalates -- :delivery/dispatch) ==")
    (let [r (exec-op actor "t3" {:op :delivery/dispatch :subject "to-1"} operator)]
      (println r)
      (println "-- human export-compliance officer approves --")
      (println (approve! actor "t3")))

    (println "== invoice/settle to-1 (always escalates -- :invoice/settle) ==")
    (let [r (exec-op actor "t4" {:op :invoice/settle :subject "to-1"} operator)]
      (println r)
      (println "-- human export-compliance officer approves --")
      (println (approve! actor "t4")))

    (println "== classification/verify to-2 (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t5" {:op :classification/verify :subject "to-2"} operator))

    (println "== classification/verify to-3 (escalates -- sets up the credit-uncleared test) ==")
    (println (exec-op actor "t6" {:op :classification/verify :subject "to-3"} operator))
    (println (approve! actor "t6"))

    (println "== delivery/dispatch to-3 (credit not cleared -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :delivery/dispatch :subject "to-3"} operator))

    (println "== classification/verify to-4 (escalates -- sets up the contract-missing test) ==")
    (println (exec-op actor "t8" {:op :classification/verify :subject "to-4"} operator))
    (println (approve! actor "t8"))

    (println "== delivery/dispatch to-4 (no contract-terms on file -> HARD hold) ==")
    (println (exec-op actor "t9" {:op :delivery/dispatch :subject "to-4"} operator))

    (println "== classification/verify to-5 (escalates -- sets up the eccn-classification-missing test) ==")
    (println (exec-op actor "t10" {:op :classification/verify :subject "to-5"} operator))
    (println (approve! actor "t10"))

    (println "== delivery/dispatch to-5 (item NEVER classified -> HARD hold :eccn-classification-missing) ==")
    (println (exec-op actor "t11" {:op :delivery/dispatch :subject "to-5"} operator))

    (println "== classification/verify to-6 (escalates -- sets up the license-required-unauthorized test) ==")
    (println (exec-op actor "t12" {:op :classification/verify :subject "to-6"} operator))
    (println (approve! actor "t12"))

    (println "== delivery/dispatch to-6 (classified 5A002, license required, NONE on file -> HARD hold :license-required-unauthorized, DISTINCT from to-5's hold) ==")
    (println (exec-op actor "t13" {:op :delivery/dispatch :subject "to-6"} operator))

    (println "== classification/verify to-7 (escalates -- sets up the sanctions test) ==")
    (println (exec-op actor "t14" {:op :classification/verify :subject "to-7"} operator))
    (println (approve! actor "t14"))

    (println "== delivery/dispatch to-7 (sanctions screening not passed -> HARD hold) ==")
    (println (exec-op actor "t15" {:op :delivery/dispatch :subject "to-7"} operator))

    (println "== classification/verify to-8 (escalates -- sets up the denied-party test) ==")
    (println (exec-op actor "t16" {:op :classification/verify :subject "to-8"} operator))
    (println (approve! actor "t16"))

    (println "== delivery/dispatch to-8 (denied-party-list screening not passed -> HARD hold, DISTINCT from to-7's sanctions hold) ==")
    (println (exec-op actor "t17" {:op :delivery/dispatch :subject "to-8"} operator))

    (println "== classification/verify to-9 (escalates -- clean deemed-export software release) ==")
    (println (exec-op actor "t18" {:op :classification/verify :subject "to-9"} operator))
    (println (approve! actor "t18"))

    (println "== technology/release to-9 (deemed export, EAR99, always escalates -- :technology/release) ==")
    (let [r (exec-op actor "t19" {:op :technology/release :subject "to-9"} operator)]
      (println r)
      (println "-- human export-compliance officer approves --")
      (println (approve! actor "t19")))

    (println "== invoice/settle to-9 ==")
    (let [r (exec-op actor "t20" {:op :invoice/settle :subject "to-9"} operator)]
      (println r)
      (println (approve! actor "t20")))

    (println "== classification/verify to-10 (escalates -- sets up the deemed-export license test) ==")
    (println (exec-op actor "t21" {:op :classification/verify :subject "to-10"} operator))
    (println (approve! actor "t21"))

    (println "== technology/release to-10 (deemed export to recipient nationality QQQ, ECCN 5D002, license required, NONE on file -> HARD hold :license-required-unauthorized via effective-destination, NOT the order's own :destination-country) ==")
    (println (exec-op actor "t22" {:op :technology/release :subject "to-10"} operator))

    (println "== delivery/dispatch to-1 AGAIN (double-dispatch -> HARD hold) ==")
    (println (exec-op actor "t23" {:op :delivery/dispatch :subject "to-1"} operator))

    (println "== technology/release to-9 AGAIN (double-release -> HARD hold) ==")
    (println (exec-op actor "t24" {:op :technology/release :subject "to-9"} operator))

    (println "== invoice/settle to-1 AGAIN (double-invoice -> HARD hold) ==")
    (println (exec-op actor "t25" {:op :invoice/settle :subject "to-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft physical-dispatch records ==")
    (doseq [r (store/dispatch-history db)] (println r))

    (println "== draft technology-release records ==")
    (doseq [r (store/release-history db)] (println r))

    (println "== draft invoice records ==")
    (doseq [r (store/invoice-history db)] (println r))))
