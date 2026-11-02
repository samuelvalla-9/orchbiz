# Customer Intake Questionnaire Specification (Google Form / Excel)

This document defines the exact questionnaire structure to use in **Google Forms** (or directly in **Excel** during a live discovery call).

When a response is submitted:
1. Export the responses from Google Sheets/Forms as `.xlsx` or `.csv` (matching [`intake/questionnaire-template.csv`](../intake/questionnaire-template.csv)). Rename the form's question columns to the **Column Key** values below (a `Timestamp` column is mapped to `submission_date` automatically).
2. Place the file inside `clients/<client-slug>/intake/` (create the folder with `.\tools\orchbiz.ps1 init-client <client-slug>`).
3. Run `.\tools\orchbiz.ps1 parse-intake <client-slug>`. It splits list fields, converts consent to a boolean, derives budget amount/currency, validates against [`intake/intake.schema.json`](../intake/intake.schema.json) and writes `intake/intake.validated.json`. The **Business Analyst Agent** then flags any missing or ambiguous fields before architecture planning begins.

---

## Questionnaire Sections & Fields

### Section 1: Business Profile & Niche
| Column Key | Google Form Question | Input Type | Required | Notes / Agent Usage |
|---|---|---|---|---|
| `submission_date` | Timestamp / Date | Auto / Date | Yes | Audit tracking |
| `client_slug` | Client Short ID (Operator filled, e.g., `apex-coaching`) | Short Text | Yes | Used for `clients/<client_slug>/` directory |
| `business_name` | Official Business / Institute Name | Short Text | Yes | Used across website, emails, and handover docs |
| `business_niche` | Business Category / Niche | Dropdown | Yes | e.g., `Coaching & Training Institute`, `Test Prep Academy`, `Skill Bootcamp`, `Local Clinic`, `Other` |
| `tagline` | Existing Tagline or Value Proposition (if any) | Short Text | No | Brand & Content Agent refines or drafts options if blank |
| `business_summary` | Describe what your business does in 3–5 sentences | Paragraph | Yes | Core input for Business Analyst & Brand Agent |
| `target_audience` | Who is your ideal customer/student? (Age, location, goals) | Paragraph | Yes | Used for landing page copy & enquiry classification |
| `primary_location` | City / Region / Service Area (or "Online Only") | Short Text | Yes | Used for local SEO, timezone, and contact details |

### Section 2: Package & Module Selection
| Column Key | Google Form Question | Input Type | Required | Notes / Agent Usage |
|---|---|---|---|---|
| `selected_package` | Selected Package Tier | Dropdown | Yes | `starter-lead-to-appointment` (Default), `custom` |
| `website_modules` | Website Modules Needed (Comma-separated) | Checkboxes | Yes | `business-landing-page`, `services-and-pricing`, `enquiry-form`, `appointment-booking`, `faq-assistant`, `lead-dashboard`, `social-content-calendar` |
| `automation_modules` | Automation Modules Needed (Comma-separated) | Checkboxes | Yes | `capture-and-store-lead`, `notify-business-owner`, `classify-enquiry`, `draft-follow-up`, `schedule-appointment`, `send-approved-reminder`, `weekly-performance-report` |

### Section 3: Services, Courses & Pricing
| Column Key | Google Form Question | Input Type | Required | Notes / Agent Usage |
|---|---|---|---|---|
| `core_offerings` | List your top 3–5 services/courses (Name, duration, brief description, price/fee range) | Paragraph | Yes | Populates `services-and-pricing` module and enquiry dropdowns |
| `key_differentiators` | Why do customers choose you over competitors? (Provide only verifiable facts) | Paragraph | Yes | Brand & Content Agent uses this; flags any unverified claims |
| `verified_testimonials` | Paste 1–3 real customer/student testimonials with name & role (Leave blank if none—we will not invent any) | Paragraph | No | Guardrail: Never fabricate testimonials |
| `top_faqs` | List 3–5 common questions prospects ask before joining | Paragraph | No | Brand Agent formats these and suggests additional niche FAQs for review |

### Section 4: Brand Assets & Visual Identity
| Column Key | Google Form Question | Input Type | Required | Notes / Agent Usage |
|---|---|---|---|---|
| `brand_tone` | Preferred Brand Voice / Tone | Multi-select | Yes | e.g., `Professional & Authoritative`, `Warm & Supportive`, `Results-Driven`, `Modern & Energetic` |
| `primary_color` | Primary Brand Color (Hex code or color name) | Short Text | No | Defaults to niche-appropriate palette if blank |
| `secondary_color` | Secondary Brand Color (Hex code or color name) | Short Text | No | Used by Website Builder Agent |
| `logo_and_assets_url` | Link to Google Drive / Folder with Logo & Photos | URL / Text | No | Checked by Website Builder Agent; placeholders used if missing |

### Section 5: Lead Capture, Booking & Workflow Rules
| Column Key | Google Form Question | Input Type | Required | Notes / Agent Usage |
|---|---|---|---|---|
| `primary_cta` | Primary Call-to-Action on Website | Dropdown | Yes | e.g., `Book a Free Demo Class`, `Schedule a Counseling Call`, `Download Brochure`, `Request Callback` |
| `enquiry_fields` | Information to collect on the Enquiry Form | Checkboxes | Yes | e.g., `Full Name`, `Phone/WhatsApp`, `Email`, `Course/Service Interest`, `Preferred Batch/Timing`, `Message` |
| `notification_channels` | How should staff be notified of a new lead? | Checkboxes | Yes | `Email`, `WhatsApp (Approved Template)`, `Telegram/Slack`, `Dashboard Only` |
| `notification_recipients` | Staff Email(s) / Phone Number(s) for Lead Alerts | Short Text | Yes | Configured in `notify-business-owner` workflow |
| `booking_calendar_type` | Appointment / Demo Booking Method | Dropdown | Yes | `Google Calendar`, `Calendly Link`, `Built-in Slot Request`, `Manual Follow-up Only` |
| `business_hours` | Available Days & Hours for Appointments (including Timezone) | Short Text | Yes | e.g., `Mon-Sat, 10:00 AM - 7:00 PM IST` |
| `lead_storage_target` | Where should leads be stored besides the platform database? | Checkboxes | Yes | `Google Sheets`, `PostgreSQL Database (Built-in)`, `Airtable`, `HubSpot/Zoho CRM` |

### Section 6: Technical Infrastructure, Budget & Compliance
| Column Key | Google Form Question | Input Type | Required | Notes / Agent Usage |
|---|---|---|---|---|
| `existing_domain` | Existing Domain Name (if any, e.g., `example.com`) | Short Text | No | Used by Deployment & Handover Agent |
| `hosting_preference` | Hosting & Deployment Preference | Dropdown | Yes | `Client-Owned Cloud Account`, `Operator-Managed Hosting`, `Decide Later` |
| `setup_budget_limit` | Agreed Setup / Implementation Budget (Currency + Amount) | Short Text | Legacy | Optional free text; `orchbiz parse-intake` derives the two fields below from it when they are blank |
| `monthly_ops_budget` | Max Monthly Third-Party Tool/Hosting Budget | Short Text | Legacy | Optional free text, e.g. `INR 2500/month` |
| `setup_budget_amount` | Setup budget amount (number only) | Number | Yes | Solution Architect checks scope against budget |
| `setup_budget_currency` | Setup budget currency | Dropdown | Yes | ISO 4217 code: `INR`, `USD`, `EUR`, `GBP`, ... |
| `monthly_ops_budget_amount` | Max monthly tool/hosting budget (number only) | Number | Yes | `orchbiz check-budget` compares this with catalogue cost estimates |
| `monthly_ops_budget_currency` | Monthly budget currency | Dropdown | Yes | Converted to USD with the operator-maintained rates in [`catalogue/fx-rates.json`](../catalogue/fx-rates.json) |
| `target_launch_date` | Target Go-Live Date | Date | Yes | Drives project plan milestones |
| `ai_data_consent` | Client consents to processing non-sensitive business & enquiry text via approved AI APIs? | Yes / No | Yes | **Mandatory Operating Guardrail** |
| `operator_notes` | Operator Internal Notes / Special Constraints | Paragraph | No | Extra context from your discovery call |
