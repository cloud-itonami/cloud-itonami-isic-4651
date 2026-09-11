(ns techtrade.phase
  "Phase 0->3 staged rollout for the computer-and-software-wholesale
  actor.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- tech-order intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-verify  -- adds classification-verification
                                 writes, still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:order/intake` (no capital risk / no
                                 export-control risk yet) may auto-
                                 commit. `:delivery/dispatch`/
                                 `:technology/release`/`:invoice/settle`
                                 NEVER auto-commit, at any phase.

  `:delivery/dispatch`/`:technology/release`/`:invoice/settle` are
  deliberately ABSENT from every phase's `:auto` set, including phase 3
  -- a permanent structural fact, not a rollout milestone still to come.
  Physically dispatching real computer hardware/peripherals to a
  counterparty, electronically releasing real controlled software/
  source code/technical data (including a deemed-export release), and
  settling a real invoice (real money moving between counterparty and
  wholesaler) are the THREE real-world acts this actor performs; all
  three are always a human trading supervisor's call.
  `techtrade.governor`'s high-stakes gate enforces the same invariant
  independently -- two layers, not one, agree on this. Like every prior
  sibling's phase 3 `:auto` set, this domain has only ONE member
  (`:order/intake`) -- no separate no-capital-risk lifecycle distinct
  from the tech-order itself.")

(def read-ops  #{})
(def write-ops #{:order/intake :classification/verify
                 :delivery/dispatch :technology/release :invoice/settle})

;; NOTE the invariant: `:delivery/dispatch`/`:technology/release`/
;; `:invoice/settle` are members of `write-ops` (governor-gated like any
;; write) but are NEVER members of any phase's `:auto` set below. Do not
;; add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                :auto #{}}
   1 {:label "assisted-intake"  :writes #{:order/intake}                                    :auto #{}}
   2 {:label "assisted-verify"  :writes #{:order/intake :classification/verify}             :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:order/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:delivery/dispatch`/`:technology/release`/`:invoice/settle` are
    never auto-eligible at any phase, so they always escalate once the
    governor clears them (or hold if the governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Tech Export Governor verdict to a base disposition before the
  phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
