(ns techtrade.techtradeadvisor
  "TechTradeAdvisor client -- the *contained intelligence node* for the
  computer-and-software-wholesale (ISIC 4651) actor.

  It normalizes tech-order intake, drafts a per-jurisdiction GENERIC
  counterparty-diligence evidence checklist, drafts the physical-
  dispatch action, drafts the technology-release action (including
  deemed-export releases), and drafts the invoice-settlement action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real dispatch/release/settlement. Every output
  is censored downstream by `techtrade.governor` before anything
  touches the SSoT, and `:delivery/dispatch`/`:technology/release`/
  `:invoice/settle` proposals NEVER auto-commit at any phase -- see
  README `Actuation`.

  The advisor MAY summarize what it believes an item's classification
  and license posture is (informationally, for a human reviewer's
  benefit, in `:rationale`) but this is NEVER what the governor
  actually checks: `techtrade.governor`'s `eccn-classification-missing-
  violations`/`license-required-unauthorized-violations` independently
  re-read the tech-order's own `:eccn`/`:license-required?`/`:license-
  authorized?` ground truth directly, so a compromised or mistaken
  advisor citation can never substitute for the real facts -- the SAME
  discipline the metal-wholesale sibling's advisor establishes for its
  own conflict-minerals citation.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :delivery/dispatch | :technology/release | :invoice/settle | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [techtrade.facts :as facts]
            [techtrade.governor :as governor]
            [techtrade.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the order-id, counterparty, destination, item-type or
  any physical/commercial value. High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "コンピュータ・周辺機器・ソフトウェア卸売オーダー記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :order/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-classification
  "Per-jurisdiction GENERIC counterparty-diligence evidence checklist
  draft (credit-clearance record, contract/PO, sanctions-screening
  record, denied-party-screening record). `:no-spec?` injects the
  failure mode we must defend against: proposing a checklist for a
  jurisdiction with NO official spec-basis in `techtrade.facts` -- the
  Tech Export Governor must reject this (never invent a jurisdiction's
  requirements). The advisor ALSO summarizes the order's own reported
  classification/license posture informationally (for the human
  reviewer), but this is never what the governor's classification/
  license checks actually read -- see namespace docstring."
  [db {:keys [subject no-spec?]}]
  (let [to (store/tech-order db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction to))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "techtrade.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :classification-assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案"
                        (when to (str " / 該非判定=" (or (:eccn to) "未実施"))))
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb)
                        " / 分類リスト: " (:classification-list sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :classification-assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- propose-dispatch
  "Draft the actual PHYSICAL-DISPATCH action -- shipping real computer
  hardware/peripherals to a counterparty. ALWAYS `:stake :delivery/
  dispatch` -- this is a REAL-WORLD act, never a draft the actor may
  auto-run. See README `Actuation`: no phase ever adds this op to a
  phase's `:auto` set (`techtrade.phase`); the governor also always
  escalates on `:delivery/dispatch`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [to (store/tech-order db subject)
        credit-ok? (and to (true? (:credit-cleared? to)))
        contract-ok? (and to (some? (:contract-terms to))
                          (not= "" (:contract-terms to)))
        classified? (and to (some? (:eccn to)) (not= (:eccn to) ""))
        license-ok? (and to (or (not (true? (:license-required? to)))
                                (true? (:license-authorized? to))))
        sanctions-ok? (and to (true? (:sanctions-screened? to)))
        denied-party-ok? (and to (true? (:denied-party-screened? to)))]
    {:summary    (str subject " 向け出荷提案"
                      (when to (str " (counterparty=" (:counterparty to)
                                    ", ECCN=" (or (:eccn to) "未分類") ")")))
     :rationale  (if to
                   (str "credit-cleared?=" credit-ok?
                        " contract-on-file?=" contract-ok?
                        " classified?=" classified?
                        " license-ok?=" license-ok?
                        " sanctions-screened?=" sanctions-ok?
                        " denied-party-screened?=" denied-party-ok?)
                   "tech-orderが見つかりません")
     :cites      (if to [subject] [])
     :effect     :order/mark-dispatched
     :value      {:tech-order-id subject}
     :stake      :delivery/dispatch
     :confidence (if (and credit-ok? contract-ok? classified? license-ok?
                          sanctions-ok? denied-party-ok?) 0.9 0.3)}))

(defn- propose-release
  "Draft the actual TECHNOLOGY-RELEASE action -- releasing real
  controlled software / source code / technical data to an end-user,
  including a deemed-export release (15 C.F.R. §734.13) to a foreign
  national physically located inside the exporting jurisdiction. ALWAYS
  `:stake :technology/release` -- this is a REAL-WORLD act, never a
  draft the actor may auto-run. See README `Actuation`: no phase ever
  adds this op to a phase's `:auto` set (`techtrade.phase`); the
  governor also always escalates on `:technology/release`. Two
  independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [to (store/tech-order db subject)
        credit-ok? (and to (true? (:credit-cleared? to)))
        contract-ok? (and to (some? (:contract-terms to))
                          (not= "" (:contract-terms to)))
        classified? (and to (some? (:eccn to)) (not= (:eccn to) ""))
        license-ok? (and to (or (not (true? (:license-required? to)))
                                (true? (:license-authorized? to))))
        sanctions-ok? (and to (true? (:sanctions-screened? to)))
        denied-party-ok? (and to (true? (:denied-party-screened? to)))]
    {:summary    (str subject " 向けリリース提案"
                      (when to (str " (counterparty=" (:counterparty to)
                                    ", ECCN=" (or (:eccn to) "未分類")
                                    ", みなし輸出=" (boolean (:deemed-export? to))
                                    ", 実効仕向地=" (governor/effective-destination to) ")")))
     :rationale  (if to
                   (str "credit-cleared?=" credit-ok?
                        " contract-on-file?=" contract-ok?
                        " classified?=" classified?
                        " license-ok?=" license-ok?
                        " sanctions-screened?=" sanctions-ok?
                        " denied-party-screened?=" denied-party-ok?)
                   "tech-orderが見つかりません")
     :cites      (if to [subject] [])
     :effect     :order/mark-released
     :value      {:tech-order-id subject}
     :stake      :technology/release
     :confidence (if (and credit-ok? contract-ok? classified? license-ok?
                          sanctions-ok? denied-party-ok?) 0.9 0.3)}))

(defn- propose-invoice
  "Draft the actual INVOICE-SETTLEMENT action -- settling a real
  computer-and-software-wholesale invoice (the money side of the trade,
  custody/financial transfer). ALWAYS `:stake :invoice/settle` -- this
  is a REAL-WORLD act (real money moves between counterparty and
  wholesaler), never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`techtrade.phase`); the governor also always escalates on
  `:invoice/settle`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [to (store/tech-order db subject)
        fulfilled? (and to (or (:dispatched? to) (:released? to)))
        sanctions-ok? (and to (true? (:sanctions-screened? to)))
        denied-party-ok? (and to (true? (:denied-party-screened? to)))]
    {:summary    (str subject " 向け請求提案"
                      (when to (str " (counterparty=" (:counterparty to) ")")))
     :rationale  (if to
                   (str "fulfilled(dispatched-or-released)?=" fulfilled?
                        " sanctions-screened?=" sanctions-ok?
                        " denied-party-screened?=" denied-party-ok?)
                   "tech-orderが見つかりません")
     :cites      (if to [subject] [])
     :effect     :order/mark-invoiced
     :value      {:tech-order-id subject}
     :stake      :invoice/settle
     :confidence (if (and fulfilled? sanctions-ok? denied-party-ok?) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :order/intake        (normalize-intake db request)
    :classification/verify (verify-classification db request)
    :delivery/dispatch   (propose-dispatch db request)
    :technology/release  (propose-release db request)
    :invoice/settle      (propose-invoice db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたはコンピュータ・周辺機器・ソフトウェア卸売事業者の出荷・"
       "リリース・請求エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:order/upsert|:classification-assessment/set|"
       ":order/mark-dispatched|:order/mark-released|:order/mark-invoiced) "
       ":stake(:delivery/dispatch か :technology/release か :invoice/settle か nil) "
       ":confidence(0..1)。\n"
       "重要: 登録されていない法域の輸出管理分類要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"
       "ECCN分類・輸出許可の要否・取引先信用審査・契約有無・制裁スクリーニング・"
       "該当者リストスクリーニングの状態を偽って報告してはいけません。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :classification/verify {:tech-order (store/tech-order st subject)}
    :delivery/dispatch     {:tech-order (store/tech-order st subject)}
    :technology/release    {:tech-order (store/tech-order st subject)}
    :invoice/settle        {:tech-order (store/tech-order st subject)}
    {:tech-order (store/tech-order st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Tech Export Governor
  escalates/holds -- an LLM hiccup can never auto-dispatch hardware,
  auto-release technology, or auto-settle an invoice."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :techtradeadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
