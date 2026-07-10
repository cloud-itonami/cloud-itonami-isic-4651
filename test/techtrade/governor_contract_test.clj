(ns techtrade.governor-contract-test
  "The governor contract as executable tests. The single invariant
  under test:

    TechTradeAdvisor never dispatches computer hardware/peripherals,
    releases controlled technology, or settles an invoice the Tech
    Export Governor would reject, `:delivery/dispatch`/`:technology/
    release`/`:invoice/settle` NEVER auto-commit at any phase,
    `:order/intake` (no direct capital/export-control risk) MAY
    auto-commit when clean, and every decision (commit OR hold) leaves
    exactly one ledger fact.

  This file ALSO proves the fleet-differentiating claims from
  `techtrade.governor`'s namespace docstring end-to-end:
  `eccn-classification-missing` (to-5) and `license-required-
  unauthorized` (to-6) are genuinely TWO SEPARATE failure modes, not one
  collapsed 'export-license-uncleared' boolean the way the general-
  trading sibling models it; `counterparty-sanctions-flag-unresolved`
  (to-7) and `denied-party-list-flag-unresolved` (to-8) are genuinely
  TWO SEPARATE failure modes, not one generic sanctions check; and the
  deemed-export doctrine (to-10) is actually load-bearing, not just a
  docstring claim -- `techtrade.governor/effective-destination` reads
  the release recipient's nationality, not the order's own
  `:destination-country`."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [techtrade.governor :as governor]
            [techtrade.store :as store]
            [techtrade.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :export-compliance-officer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through classification verify -> approve, leaving a
  classification assessment on file. Uses distinct thread-ids per call
  site by suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :classification/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :order/intake :subject "to-1"
                   :patch {:id "to-1" :counterparty "Northfield Data Centres Ltd"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Northfield Data Centres Ltd" (:counterparty (store/tech-order db "to-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest classification-verify-always-needs-approval
  (testing "classification verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :classification/verify :subject "to-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "to-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a classification/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :classification/verify :subject "to-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "to-2")) "no assessment written"))))

(deftest dispatch-without-assessment-is-held
  (testing "delivery/dispatch before any classification verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :delivery/dispatch :subject "to-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest credit-uncleared-is-held-and-unoverridable
  (testing "a counterparty whose credit has not been cleared -> HOLD, and never reaches request-approval -- the leasing collateral-coverage discipline applied to counterparty credit"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "to-3")
          res (exec-op actor "t5" {:op :delivery/dispatch :subject "to-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:credit-uncleared} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest contract-missing-is-held-and-unoverridable
  (testing "an order with no contract-terms on file -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t6pre" "to-4")
          res (exec-op actor "t6" {:op :delivery/dispatch :subject "to-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:contract-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest eccn-classification-missing-is-held-and-unoverridable
  (testing "an item that has NEVER been classified against a control list at all -> HOLD :eccn-classification-missing"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "to-5")
          res (exec-op actor "t7" {:op :delivery/dispatch :subject "to-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:eccn-classification-missing} (-> (store/ledger db) last :basis)))
      (is (not (some #{:license-required-unauthorized} (-> (store/ledger db) last :basis)))
          "an unclassified item should not ALSO fire the license check for the same gap")
      (is (empty? (store/dispatch-history db))))))

(deftest license-required-unauthorized-is-a-genuinely-different-failure-mode-from-eccn-classification-missing
  (testing "a DIFFERENT order that HAS been classified (5A002) but requires a license for its destination with none on file -> HOLD :license-required-unauthorized, NOT :eccn-classification-missing"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "to-6")
          res (exec-op actor "t8" {:op :delivery/dispatch :subject "to-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:license-required-unauthorized} (-> (store/ledger db) last :basis)))
      (is (not (some #{:eccn-classification-missing} (-> (store/ledger db) last :basis)))
          "a classified-but-unlicensed item is a genuinely different posture from an unclassified one")
      (is (empty? (store/dispatch-history db))))))

(deftest counterparty-sanctions-flag-unresolved-is-held-and-unoverridable
  (testing "a counterparty that has not passed OFAC / equivalent sanctions screening -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "to-7")
          res (exec-op actor "t9" {:op :delivery/dispatch :subject "to-7"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:counterparty-sanctions-flag-unresolved} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest denied-party-list-flag-unresolved-is-a-genuinely-different-failure-mode-from-generic-sanctions
  (testing "a DIFFERENT counterparty that has not passed denied-party (Entity List/Denied Persons List) screening -> HOLD :denied-party-list-flag-unresolved, NOT the generic sanctions rule"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "to-8")
          res (exec-op actor "t10" {:op :delivery/dispatch :subject "to-8"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:denied-party-list-flag-unresolved} (-> (store/ledger db) last :basis)))
      (is (not (some #{:counterparty-sanctions-flag-unresolved} (-> (store/ledger db) last :basis)))
          "to-8 passes generic OFAC-style sanctions screening; only the denied-party-list check should fire")
      (is (empty? (store/dispatch-history db))))))

(deftest delivery-dispatch-always-escalates-then-human-decides
  (testing "a clean, fully-verified, classified, licensed, credit-cleared, contract-on-file, screened order still ALWAYS interrupts for human approval -- :delivery/dispatch is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t11pre" "to-1")
          r1 (exec-op actor "t11" {:op :delivery/dispatch :subject "to-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, dispatch record drafted"
        (let [r2 (approve! actor "t11")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:dispatched? (store/tech-order db "to-1"))))
          (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record"))))))

(deftest technology-release-always-escalates-then-human-decides
  (testing "a clean deemed-export software release still ALWAYS interrupts for human approval -- :technology/release is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "to-9")
          r1 (exec-op actor "t12" {:op :technology/release :subject "to-9"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, release record drafted"
        (let [r2 (approve! actor "t12")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:released? (store/tech-order db "to-9"))))
          (is (= 1 (count (store/release-history db))) "one draft release record"))))))

(deftest invoice-settle-always-escalates-then-human-decides
  (testing "a clean, already-dispatched order still ALWAYS interrupts for human approval -- :invoice/settle is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t13pre" "to-1")
          _ (exec-op actor "t13dispatch" {:op :delivery/dispatch :subject "to-1"} operator)
          _ (approve! actor "t13dispatch")
          r1 (exec-op actor "t13" {:op :invoice/settle :subject "to-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, invoice record drafted"
        (let [r2 (approve! actor "t13")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:invoiced? (store/tech-order db "to-1"))))
          (is (= 1 (count (store/invoice-history db))) "one draft invoice record"))))))

(deftest deemed-export-license-check-reads-recipient-nationality-not-destination-country
  (testing "a deemed-export release whose classification requires a license for the RECIPIENT'S nationality (not the order's own :destination-country) -> HOLD :license-required-unauthorized, via effective-destination"
    (let [[db actor] (fresh)
          to (store/tech-order db "to-10")]
      (is (true? (:deemed-export? to)))
      (is (not= (:destination-country to) (:release-recipient-nationality to))
          "the order's shipment destination and the deemed-export recipient's nationality are deliberately DIFFERENT in this fixture")
      (is (= (:release-recipient-nationality to) (governor/effective-destination to))
          "effective-destination reads the recipient's nationality for a deemed-export order")
      (let [_ (verify! actor "t14pre" "to-10")
            res (exec-op actor "t14" {:op :technology/release :subject "to-10"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:license-required-unauthorized} (-> (store/ledger db) last :basis)))
        (is (empty? (store/release-history db)))))))

(deftest effective-destination-is-plain-destination-country-for-non-deemed-export-orders
  (let [to (store/tech-order (first (fresh)) "to-1")]
    (is (false? (:deemed-export? to)))
    (is (= (:destination-country to) (governor/effective-destination to)))))

(deftest delivery-dispatch-double-dispatch-is-held
  (testing "dispatching the same tech-order twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t15pre" "to-1")
          _ (exec-op actor "t15a" {:op :delivery/dispatch :subject "to-1"} operator)
          _ (approve! actor "t15a")
          res (exec-op actor "t15" {:op :delivery/dispatch :subject "to-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/dispatch-history db))) "still only the one earlier dispatch"))))

(deftest technology-release-double-release-is-held
  (testing "releasing the same tech-order's technology twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t16pre" "to-9")
          _ (exec-op actor "t16a" {:op :technology/release :subject "to-9"} operator)
          _ (approve! actor "t16a")
          res (exec-op actor "t16" {:op :technology/release :subject "to-9"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-released} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/release-history db))) "still only the one earlier release"))))

(deftest invoice-settle-double-invoice-is-held
  (testing "settling the same tech-order's invoice twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t17pre" "to-1")
          _ (exec-op actor "t17dispatch" {:op :delivery/dispatch :subject "to-1"} operator)
          _ (approve! actor "t17dispatch")
          _ (exec-op actor "t17a" {:op :invoice/settle :subject "to-1"} operator)
          _ (approve! actor "t17a")
          res (exec-op actor "t17" {:op :invoice/settle :subject "to-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-invoiced} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/invoice-history db))) "still only the one earlier invoice"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :order/intake :subject "to-1"
                          :patch {:id "to-1" :counterparty "Northfield Data Centres Ltd"}} operator)
      (exec-op actor "b" {:op :classification/verify :subject "to-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
