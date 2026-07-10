# cloud-itonami-isic-4651

Open Business Blueprint for **ISIC Rev.5 4651**: Wholesale of Computers,
Computer Peripheral Equipment and Software -- tech-order intake,
per-jurisdiction counterparty-diligence / export-control-classification
regulatory verification, physical hardware dispatch OR electronic
technology release (including deemed-export releases), and invoice
settlement for a computer-and-software wholesaler.

This repository publishes a computer-and-software-wholesale actor --
tech-order intake, per-jurisdiction contract / export-classification /
sanctions / denied-party regulatory verification, physical dispatch or
technology release, and invoice settlement -- as an OSS business that
any qualified operator can fork, deploy, run, improve and sell, so a
regional computer/peripheral/software wholesaler never surrenders
counterparty, credit, classification, license and sanctions-screening
data to a closed trade-compliance / ERP SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **TechTradeAdvisor ⊣
Tech Export Governor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:tech-export-governor`, is a
UNIQUE keyword fleet-wide (grep-verified: no other blueprint declares
it) -- a fresh, independent build.

**Like the fuel-wholesale / general-trading / metal-wholesale /
textile-wholesale / waste-wholesale siblings, this vertical is
SELF-CONTAINED**: there is no `kotoba-lang/techtrade` to delegate
export-classification validation to, so the credit-clearance /
contract-on-file / ECCN-classification / license-authorization /
sanctions-screening / denied-party-screening checks live as direct
entity boolean/value reads in `techtrade.governor` (off dedicated facts
on the `tech-order` record), rather than wrapping an external
capability library's own validated function.

> **Why an actor layer at all?** An LLM is great at drafting an order
> summary, normalizing records, and reading a credit file -- but it
> has **no notion of which jurisdiction's export-control-classification
> regime is official, no license to dispatch real hardware or release
> real controlled technology to an end-user or settle a real invoice,
> and no way to know on its own whether an item has actually been
> classified against a real control list, whether a classified item's
> destination/end-user combination actually requires a license (and if
> so whether one is on file), or whether OFAC / equivalent sanctions
> screening AND denied-party (Entity List/Denied Persons List)
> screening have actually been passed**. Letting it dispatch hardware,
> release technology, or settle an invoice directly invites fabricated
> regulatory citations, an unclassified or unlicensed item leaving the
> wholesaler's control, controlled technology reaching a denied party,
> and an invoice settling against a sanctioned party -- exposing the
> operator to real enforcement and financial liability, for whoever
> runs it. This project seals the TechTradeAdvisor into a single node
> and wraps it with an independent **Tech Export Governor**, a human
> **approval workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers tech-order intake through classification / sanctions
/ denied-party regulatory verification, physical dispatch or technology
release, and invoice settlement. It does **not**, by itself, hold any
export authorization, license, or operating authority required to run a
computer-and-software-wholesale business in a given jurisdiction, and it
does not claim to. It also does not perform the actual physical
warehouse pick/pack, the actual code-repository/file-transfer release
mechanics, or route optimization itself, or judge trading-book economics
-- fulfillment/route optimization (the blueprint's own `:optimization`
technology) is a follow-up slice, not in this R0. Whoever deploys and
operates a live instance (a qualified trading supervisor / export-
compliance officer) supplies any jurisdiction-specific operating
authority, the real warehouse/ERP dispatch integration, the real
release/access-control integration, and the real ERP / accounts-
receivable integrations, and bears that jurisdiction's liability -- the
software supplies the governed, spec-cited, audited execution scaffold
so that operator does not have to build the compliance layer from
scratch.

### Actuation

**Physically dispatching real computer hardware/peripherals,
electronically releasing real controlled software/source code/technical
data (including a deemed-export release), and settling a real invoice
are never autonomous, at any phase, by construction.** Two independent
layers enforce this (`techtrade.governor`'s `:delivery/dispatch`/
`:technology/release`/`:invoice/settle` high-stakes gate and
`techtrade.phase`'s phase table, which never puts any of the three ops
in any phase's `:auto` set) -- see `techtrade.phase`'s docstring and
`test/techtrade/phase_test.clj`'s
`delivery-dispatch-never-auto-at-any-phase`/
`technology-release-never-auto-at-any-phase`/
`invoice-settle-never-auto-at-any-phase`. The actor may draft, check and
recommend; a human trading supervisor / export-compliance officer is
always the one who actually dispatches hardware, releases technology, or
settles an invoice. Grounded in export-control doctrine (the same
discipline every regulator in `techtrade.facts` codifies: a real
dispatch, release and invoice settlement are human sign-off acts) -- a
genuine **THREE-member** actuation shape
(`#{:delivery/dispatch :technology/release :invoice/settle}`), unlike
every prior sibling's own two-member dual-actuation shape. A single
`tech-order` uses EITHER `:delivery/dispatch` (physical shipment) OR
`:technology/release` (electronic release, including deemed-export
releases) -- never both -- followed by `:invoice/settle` on the SAME
order. See `docs/adr/0001-architecture.md` Decision 3.

## The core contract

```
tech-order intake + jurisdiction facts (techtrade.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌─────────────────────────┐
   │ TechTradeAdvisor       │ ─────────────▶ │ Tech Export Governor     │  (independent system)
   │ (sealed)               │  + citations    │ spec-basis · evidence-  │
   └───────────────────────┘                 │ incomplete · credit-     │
          │                 commit ◀┼ uncleared · contract-missing ·  │
          │                         │ eccn-classification-missing ·   │
    record + ledger        escalate ┼ license-required-unauthorized · │
          │              (ALWAYS for│ counterparty-sanctions-flag-     │
          │       :delivery/        │ unresolved · denied-party-list-  │
          │       dispatch/         │ flag-unresolved · already-       │
          │       :technology/      │ dispatched/released/invoiced     │
          │       release/          └─────────────────────────┘
          │       :invoice/
          │       settle)
          ▼
      human approval
```

**The TechTradeAdvisor never dispatches hardware, releases technology,
or settles an invoice the Tech Export Governor would reject, and never
does so without a human sign-off.** Hard violations (fabricated
regulatory requirements; unsupported evidence; an uncleared counterparty
credit; no contract-terms on file; an item never classified against a
control list; a classified item requiring a license for its destination/
end-user with none authorized; an unresolved sanctions-screening flag;
an unresolved denied-party-list flag; a double dispatch/release/invoice)
force **hold** and *cannot* be approved past; a clean dispatch/release/
invoice proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dispatch + one clean deemed-export release + invoice lifecycles, plus HARD-hold cases, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

`blueprint.edn` sets `:itonami.blueprint/robotics true`, reasoned
specifically for the PHYSICAL half of this vertical's dispatch: an
automated storage-and-retrieval system (AS/RS) / goods-to-person
robotic shuttle picks and stages ESD-safe (electrostatic-discharge-
safe) computer-hardware/peripheral cartons at the wholesale
distribution center for `:delivery/dispatch`, under the actor, gated by
the independent **Tech Export Governor**. The governor never dispatches
hardware itself: a dispatch-clearing action must have cleared the same
sign-off a human trading supervisor would need -- a robot may stage a
carton at the dock, but only after the governor and a human supervisor
both agree it is safe to. This is a MIXED vertical, unlike every prior
sibling: the robotics premise applies ONLY to `:delivery/dispatch`
(physical hardware) -- `:technology/release` (electronic release of
software/source code/technical data, including deemed-export releases)
has NO physical robotic act at all. See `docs/business-model.md`
Robotics Premise for the full reasoning.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Tech Export Governor, dispatch/release/invoice draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`4651`). Like the fuel-wholesale / general-trading / metal-wholesale /
textile-wholesale / waste-wholesale siblings, this vertical is NOT
backed by a separate bespoke domain capability lib: the tech-trading
checks (credit-clearance, contract-on-file, ECCN classification,
license authorization, sanctions-screening, denied-party-screening) are
direct entity boolean/value reads in `techtrade.governor`, on top of the
generic robotics/identity/forms/dmn/bpmn/audit-ledger stack.

## Layout

| File | Role |
|---|---|
| `src/techtrade/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + dispatch AND release AND invoice history (triple history). The double-actuation guards check dedicated `:dispatched?`/`:released?`/`:invoiced?` booleans rather than a `:status` value |
| `src/techtrade/registry.cljc` | Dispatch/release/invoice draft records (record construction only -- the Tech Export Governor's checks are direct entity booleans/values, so there are no pure range-check functions to host here) |
| `src/techtrade/facts.cljc` | Per-jurisdiction export-control-CLASSIFICATION-LIST catalog (not merely 'has an export-control law') with an official spec-basis citation per entry, honest coverage reporting |
| `src/techtrade/techtradeadvisor.cljc` | **TechTradeAdvisor** -- `mock-advisor` ‖ `llm-advisor`; intake/classification-verification/dispatch/release/invoice proposals |
| `src/techtrade/governor.cljc` | **Tech Export Governor** -- 8 HARD checks (spec-basis · evidence-incomplete · credit-uncleared · contract-missing · eccn-classification-missing · license-required-unauthorized · counterparty-sanctions-flag-unresolved · denied-party-list-flag-unresolved) + 3 double-actuation guards + 1 soft (confidence/actuation gate) + `effective-destination` (the deemed-export-aware destination resolver) |
| `src/techtrade/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (dispatch/release/invoice always human; order intake is the ONLY auto-eligible op) |
| `src/techtrade/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/techtrade/sim.cljc` | demo driver |
| `test/techtrade/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers tech-order intake through classification / sanctions /
denied-party regulatory verification, physical dispatch or technology
release, and invoice settlement -- the core governed lifecycle:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Tech-order intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:order/intake`/`:classification/verify`) | Real warehouse-management/ERP/code-repository integration, fulfillment routing and trading-book economics |
| Physical dispatch, HARD-gated on full evidence, classification, license authorization, a credit-cleared counterparty, contract-terms on file, passed sanctions AND denied-party screening, and no double-dispatch (`:delivery/dispatch`) | |
| Technology release (including deemed-export releases), HARD-gated on the SAME checks, evaluated against the release's EFFECTIVE destination, and no double-release (`:technology/release`) | |
| Invoice settlement, HARD-gated on full evidence, passed sanctions AND denied-party screening, and no double-invoice (`:invoice/settle`) | |
| Immutable audit ledger for every intake/verification/dispatch/release/invoice decision | |

Extending coverage is additive: add the next gate (e.g. a re-export
screening check) as its own governed op with its own HARD checks and
tests, following the SAME "an independent governor re-verifies against
the actor's own records before any real-world act" pattern this repo's
flagship ops already establish.

## Jurisdiction coverage (honest)

`techtrade.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `techtrade.facts/catalog` --
currently 4 seeded (USA, JPN, DEU, GBR) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `techtrade.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `TechTradeAdvisor` + `Tech Export Governor` run as
real, tested code (see `Run` above), following the SAME governed-actor
architecture as the other prior actors across this fleet, with its own
distinct, independently-named governor and its own direct-entity-
boolean/value tech-trading checks (including the two-check ECCN-
classification split and the denied-party-list check, both with no
analog in any sibling). See `docs/adr/0001-architecture.md` for the
history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
