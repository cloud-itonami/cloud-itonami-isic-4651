# Governance

`cloud-itonami-isic-4651` is an OSS open-business blueprint for wholesale
of computers, computer peripheral equipment and software.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a tech-order whose jurisdiction has no official export-control-
  classification spec-basis can never be verified, dispatched, released
  or invoiced.
- the Tech Export Governor remains independent of the advisor.
- hard governor violations (a fabricated spec-basis, incomplete
  counterparty-diligence evidence, an uncleared counterparty credit, a
  missing contract, an item never classified against a control list, a
  classified item requiring a license for its destination/end-user with
  none authorized, an unresolved OFAC-style sanctions flag, an
  unresolved denied-party (Entity List/Denied Persons List) flag, a
  double dispatch, a double release or a double invoice) cannot be
  overridden by human approval.
- `eccn-classification-missing` and `license-required-unauthorized`
  remain two SEPARATE checks -- never collapsed back into one boolean.
- every intake, classification verification, dispatch, release,
  settlement and hold is auditable.
- counterparty, credit, classification, license and sanctions/denied-
  party-screening data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:
- bypassing dispatch, release, or invoice-settlement policy checks
- mishandling counterparty, credit, classification/license, or sanctions/
  denied-party-screening data
- misrepresenting certification status
- failing to respond to security incidents
