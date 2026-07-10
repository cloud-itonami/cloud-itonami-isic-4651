(ns techtrade.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [techtrade.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "USA" (:jurisdiction (store/tech-order s "to-1"))))
      (is (= "Northfield Data Centres Ltd" (:counterparty (store/tech-order s "to-1"))))
      (is (= :hardware (:item-type (store/tech-order s "to-1"))))
      (is (= :physical-shipment (:delivery-mode (store/tech-order s "to-1"))))
      (is (= "EAR99" (:eccn (store/tech-order s "to-1"))))
      (is (= "ATL" (:jurisdiction (store/tech-order s "to-2"))))
      (is (false? (:credit-cleared? (store/tech-order s "to-3"))) "to-3 credit not cleared")
      (is (nil? (:contract-terms (store/tech-order s "to-4"))) "to-4 no contract-terms")
      (is (nil? (:eccn (store/tech-order s "to-5"))) "to-5 never classified")
      (is (= "5A002" (:eccn (store/tech-order s "to-6"))) "to-6 classified as encryption hardware")
      (is (true? (:license-required? (store/tech-order s "to-6"))))
      (is (false? (:license-authorized? (store/tech-order s "to-6"))) "to-6 classified but unlicensed")
      (is (false? (:sanctions-screened? (store/tech-order s "to-7"))) "to-7 sanctions not screened")
      (is (false? (:denied-party-screened? (store/tech-order s "to-8"))) "to-8 denied-party not screened")
      (is (true? (:deemed-export? (store/tech-order s "to-9"))))
      (is (= "JPN" (:release-recipient-nationality (store/tech-order s "to-9"))))
      (is (= :technology-release (:delivery-mode (store/tech-order s "to-9"))))
      (is (true? (:deemed-export? (store/tech-order s "to-10"))))
      (is (= "QQQ" (:release-recipient-nationality (store/tech-order s "to-10"))))
      (is (false? (:dispatched? (store/tech-order s "to-1"))))
      (is (false? (:released? (store/tech-order s "to-1"))))
      (is (false? (:invoiced? (store/tech-order s "to-1"))))
      (is (= ["to-1" "to-10" "to-2" "to-3" "to-4" "to-5" "to-6" "to-7" "to-8" "to-9"]
             (mapv :id (store/all-tech-orders s))))
      (is (nil? (store/assessment-of s "to-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/release-history s)))
      (is (= [] (store/invoice-history s)))
      (is (zero? (store/next-dispatch-sequence s "USA")))
      (is (zero? (store/next-release-sequence s "USA")))
      (is (zero? (store/next-invoice-sequence s "USA")))
      (is (false? (store/tech-order-already-dispatched? s "to-1")))
      (is (false? (store/tech-order-already-released? s "to-1")))
      (is (false? (store/tech-order-already-invoiced? s "to-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :order/upsert
                                 :value {:id "to-1" :counterparty "Northfield Data Centres Ltd"}})
        (is (= "Northfield Data Centres Ltd" (:counterparty (store/tech-order s "to-1"))))
        (is (= "USA" (:jurisdiction (store/tech-order s "to-1"))) "unrelated field preserved"))
      (testing "classification-assessment payloads commit and read back"
        (store/commit-record! s {:effect :classification-assessment/set :path ["to-1"]
                                 :payload {:jurisdiction "USA" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "USA" :checklist ["a" "b"]} (store/assessment-of s "to-1"))))
      (testing "physical dispatch drafts a record and advances the dispatch sequence"
        (store/commit-record! s {:effect :order/mark-dispatched :path ["to-1"]})
        (is (= "USA-DISPATCH-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "tech-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:dispatched? (store/tech-order s "to-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "USA")))
        (is (true? (store/tech-order-already-dispatched? s "to-1"))))
      (testing "technology release drafts a record and advances the release sequence"
        (store/commit-record! s {:effect :order/mark-released :path ["to-9"]})
        (is (= "USA-RELEASE-000000" (get (first (store/release-history s)) "record_id")))
        (is (= "tech-release-draft" (get (first (store/release-history s)) "kind")))
        (is (true? (:released? (store/tech-order s "to-9"))))
        (is (= 1 (count (store/release-history s))))
        (is (= 1 (store/next-release-sequence s "USA")))
        (is (true? (store/tech-order-already-released? s "to-9"))))
      (testing "invoice settlement drafts a record and advances the invoice sequence"
        (store/commit-record! s {:effect :order/mark-invoiced :path ["to-1"]})
        (is (= "USA-INVOICE-000000" (get (first (store/invoice-history s)) "record_id")))
        (is (= "tech-invoice-draft" (get (first (store/invoice-history s)) "kind")))
        (is (true? (:invoiced? (store/tech-order s "to-1"))))
        (is (= 1 (count (store/invoice-history s))))
        (is (= 1 (store/next-invoice-sequence s "USA")))
        (is (true? (store/tech-order-already-invoiced? s "to-1"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/tech-order s "nope")))
    (is (= [] (store/all-tech-orders s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/release-history s)))
    (is (= [] (store/invoice-history s)))
    (is (zero? (store/next-dispatch-sequence s "USA")))
    (is (zero? (store/next-release-sequence s "USA")))
    (is (zero? (store/next-invoice-sequence s "USA")))
    (store/with-tech-orders s {"x" {:id "x" :order-id "TO-X"
                                    :item-description "Test item" :item-type :hardware
                                    :delivery-mode :physical-shipment
                                    :destination-country "GBR" :end-user "c" :counterparty "c"
                                    :price 1000.0 :contract-terms "FCA warehouse, net 30 days"
                                    :credit-cleared? true :sanctions-screened? true
                                    :denied-party-screened? true
                                    :eccn "EAR99" :license-required? false :license-authorized? false
                                    :deemed-export? false :release-recipient-nationality nil
                                    :dispatched? false :released? false :invoiced? false
                                    :jurisdiction "USA" :status :intake
                                    :dispatch-number nil :release-number nil :invoice-number nil}})
    (is (= "c" (:counterparty (store/tech-order s "x"))))
    (is (= :hardware (:item-type (store/tech-order s "x"))) "keyword field round-trips through DatomicStore")))
