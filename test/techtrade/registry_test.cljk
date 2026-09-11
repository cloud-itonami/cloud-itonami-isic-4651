(ns techtrade.registry-test
  (:require [clojure.test :refer [deftest is]]
            [techtrade.registry :as r]))

;; The tech-trading domain checks (credit-clearance, contract-on-file,
;; ECCN classification, license authorization, sanctions-screening,
;; denied-party-screening) are direct entity booleans/values in the
;; governor, NOT pure registry range functions -- so this registry has
;; NO range-check suite to test (unlike the crude-extraction sibling's
;; reservoir/annular/water-cut/H2S functions). Only record construction
;; is here -- for THREE record kinds (dispatch, release, invoice),
;; unlike every prior sibling's two.

;; ----------------------------- register-dispatch-record -----------------------------

(deftest dispatch-is-a-draft-not-a-real-dispatch
  (let [result (r/register-dispatch-record "to-1" "USA" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest dispatch-assigns-dispatch-number
  (let [result (r/register-dispatch-record "to-1" "USA" 7)]
    (is (= (get result "dispatch_number") "USA-DISPATCH-000007"))
    (is (= (get-in result ["record" "tech_order_id"]) "to-1"))
    (is (= (get-in result ["record" "kind"]) "tech-dispatch-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest dispatch-validation-rules
  (is (thrown? Exception (r/register-dispatch-record "" "USA" 0)))
  (is (thrown? Exception (r/register-dispatch-record "to-1" "" 0)))
  (is (thrown? Exception (r/register-dispatch-record "to-1" "USA" -1))))

;; ----------------------------- register-release-record -----------------------------

(deftest release-is-a-draft-not-a-real-release
  (let [result (r/register-release-record "to-9" "USA" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest release-assigns-release-number
  (let [result (r/register-release-record "to-9" "USA" 3)]
    (is (= (get result "release_number") "USA-RELEASE-000003"))
    (is (= (get-in result ["record" "tech_order_id"]) "to-9"))
    (is (= (get-in result ["record" "kind"]) "tech-release-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest release-validation-rules
  (is (thrown? Exception (r/register-release-record "" "USA" 0)))
  (is (thrown? Exception (r/register-release-record "to-9" "" 0)))
  (is (thrown? Exception (r/register-release-record "to-9" "USA" -1))))

;; ----------------------------- register-invoice-record -----------------------------

(deftest invoice-is-a-draft-not-a-real-invoice
  (let [result (r/register-invoice-record "to-1" "USA" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest invoice-assigns-invoice-number
  (let [result (r/register-invoice-record "to-1" "USA" 7)]
    (is (= (get result "invoice_number") "USA-INVOICE-000007"))
    (is (= (get-in result ["record" "tech_order_id"]) "to-1"))
    (is (= (get-in result ["record" "kind"]) "tech-invoice-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest invoice-validation-rules
  (is (thrown? Exception (r/register-invoice-record "" "USA" 0)))
  (is (thrown? Exception (r/register-invoice-record "to-1" "" 0)))
  (is (thrown? Exception (r/register-invoice-record "to-1" "USA" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-dispatch-record "to-1" "USA" 0)
        hist (r/append [] c1)
        c2 (r/register-dispatch-record "to-2" "USA" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "USA-DISPATCH-000000" (get-in hist2 [0 "record_id"])))
    (is (= "USA-DISPATCH-000001" (get-in hist2 [1 "record_id"])))))
