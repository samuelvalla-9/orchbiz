# Reusable Template Catalogue

This directory stores versioned, tested building blocks that specialist agents select and configure for each customer engagement.

## Core Rule

**Never generate customer systems from scratch when a catalogue module exists.**  
Agents must select approved template IDs from this catalogue, verify they belong to the customer's purchased package, and generate a validated configuration file.

---

## Catalogue Structure

```text
catalogue/
├── README.md
├── template.schema.json                      # Metadata schema every module manifest must satisfy
├── fx-rates.json                             # Operator-maintained currency rates for budget checks
├── packages/
│   └── starter-lead-to-appointment.json      # Bundled offer definitions
├── websites/                                 # Website & UI module templates
│   ├── business-landing-page/
│   ├── services-and-pricing/
│   ├── enquiry-form/
│   ├── appointment-booking/
│   ├── faq-assistant/
│   ├── lead-dashboard/
│   └── social-content-calendar/
└── automations/                              # n8n / workflow automation blueprints
    ├── capture-and-store-lead/
    ├── notify-business-owner/
    ├── classify-enquiry/
    ├── draft-follow-up/
    ├── schedule-appointment/
    ├── send-approved-reminder/
    └── weekly-performance-report/
```

## Module Manifest Contract (`manifest.json`)

Every module folder inside `catalogue/websites/<module-id>/` or `catalogue/automations/<module-id>/` must contain a `manifest.json` adhering to [`catalogue/template.schema.json`](template.schema.json), defining:
- `id` & `version` (Semantic Versioning)
- `category` (`website` | `automation` | `content`)
- `purpose`
- `inputSchema` & `outputSchema`
- `dependencies` (other module IDs required)
- `supportedIntegrations`
- `configurationFields`
- `estimatedMonthlyCostUSD` (operator estimate; summed by `tools/orchbiz.ps1 check-budget` and converted with `catalogue/fx-rates.json`)
- `permissionsRequired`
- `testCases`
- `knownLimitations`
- `rollbackApproach`
- `implementationStatus` (optional: `contract-only` | `in-development` | `tested`; `contract-only` output must be labelled as a draft)

All 14 module manifests exist at `0.1.0` with `implementationStatus: "contract-only"`: the contracts are defined but the template code and n8n blueprints are not built yet. Run `.\tools\orchbiz.ps1 validate-catalogue` after any change. Package definitions are validated against `schemas/package.schema.json`; `baseInfrastructure.estimatedMonthlyCostUSD` covers shared hosting cost.
