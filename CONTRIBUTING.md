# Contributing

`cloud-itonami-isic-4651` accepts contributions to the OSS blueprint, the
Tech Export Governor, decision-rule tests, documentation and operator
model.

## Development
The capability layer is SELF-CONTAINED. There is no pre-existing bespoke
computer-and-software-wholesale capability library to wrap; the
counterparty-credit / contract-on-file / ECCN-classification / license-
authorization / sanctions-screening / denied-party-screening checks live
directly in `techtrade.governor`. This repo holds the business blueprint,
the langgraph-clj actor and the operator contracts.

```bash
clojure -M:dev:test
clojure -M:lint
```

## Rules
- Do not commit real counterparty, credit, classification/license, or
  sanctions/denied-party-screening data.
- Keep physical dispatch, technology release and invoice settlement
  behind the Tech Export Governor.
- Treat export-control workflows as high-risk: add tests for spec-basis,
  evidence completeness, credit clearance, contract-on-file, ECCN
  classification, license authorization, sanctions screening, denied-
  party screening and audit logging.
- Keep `eccn-classification-missing` and `license-required-unauthorized`
  as two SEPARATE checks -- do not collapse them back into one boolean
  (see `docs/adr/0001-architecture.md` Decision 4 for why).
- Never fabricate a jurisdiction's export-control-classification
  requirements in `techtrade.facts` -- cite a real official source or
  leave the jurisdiction out of the catalog.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which governor invariant is
affected, how it was tested, whether operator or certification docs need
updates.
