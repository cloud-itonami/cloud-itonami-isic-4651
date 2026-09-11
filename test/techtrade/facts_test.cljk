(ns techtrade.facts-test
  (:require [clojure.test :refer [deftest is]]
            [techtrade.facts :as facts]))

(deftest usa-has-a-spec-basis
  (is (some? (facts/spec-basis "USA")))
  (is (string? (:provenance (facts/spec-basis "USA"))))
  (is (string? (:classification-list (facts/spec-basis "USA")))))

(deftest all-four-seeded-jurisdictions-have-required-evidence-and-a-classification-list
  ;; every seeded jurisdiction actually has a real required-evidence set
  ;; AND a real classification-list citation reported honestly here
  (doseq [iso3 ["USA" "JPN" "DEU" "GBR"]]
    (is (seq (facts/evidence-checklist iso3)) (str iso3 " required-evidence"))
    (is (string? (:classification-list (facts/spec-basis iso3))) (str iso3 " classification-list"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["USA" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "USA"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "USA")]
    (is (facts/required-evidence-satisfied? "USA" all))
    (is (not (facts/required-evidence-satisfied? "USA" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

(deftest evidence-checklist-deliberately-excludes-export-classification
  ;; unlike the general-trading sibling's own checklist (which folds
  ;; 'export-control classification (ECCN/HS-code) record' in), this
  ;; vertical's classification/license determination is its OWN pair of
  ;; dedicated governor checks, not a checklist item -- see
  ;; techtrade.governor.
  (doseq [iso3 ["USA" "JPN" "DEU" "GBR"]]
    (is (not-any? #(re-find #"(?i)eccn|classification" %) (facts/evidence-checklist iso3))
        (str iso3 " checklist should not mention classification"))))
