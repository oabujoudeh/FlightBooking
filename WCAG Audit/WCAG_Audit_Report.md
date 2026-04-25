# WCAG 2.1 AA Accessibility Audit Report
**Tool:** axe DevTools (Chrome Extension)
**Standard:** WCAG 2.1 AA
**Date:** 25 April 2026
**Project:** EAJO Air — Flight Booking Web App

---

## What is WCAG?

The **Web Content Accessibility Guidelines (WCAG)** are an internationally recognised set of standards published by the World Wide Web Consortium (W3C). They define how web content should be designed and developed so that it is accessible to all users, including those with visual, auditory, motor, or cognitive disabilities.

WCAG is organised around four core principles — content must be **Perceivable**, **Operable**, **Understandable**, and **Robust** (POUR). Each principle contains specific success criteria rated at three conformance levels: A (minimum), AA (standard), and AAA (enhanced). Most organisations and legal frameworks target **WCAG 2.1 AA**, which is the standard we chose to use for our project.

Meeting WCAG AA ensures that users who rely on assistive technologies — such as screen readers, keyboard-only navigation, or high-contrast displays — can access and use the application effectively.

---

## Screen Reader & Focus Support

Every page in the EAJO Air application has been built with screen reader and keyboard accessibility in mind:

- **Focus indicators** are applied globally. Every interactive element (links, buttons, inputs) displays a visible blue outline (`2px solid #005fcc`) when focused via keyboard, making it clear which element is active.
- **Skip link** — a hidden "Skip to main content" link appears at the top of every page when focused. This allows keyboard and screen reader users to bypass the navigation bar and jump directly to the page content.
- **ARIA landmarks** — the application uses semantic HTML elements (`<nav>`, `<main>`) so screen readers can identify and navigate between page regions.
- **Screen-reader-only text** — visually hidden content (using the `.sr-only` class) is available where headings or labels are needed for assistive technology but aren't needed to users without visual impairment.

## Audit Summary
| Page | Initial Result | Final Result |
|---|---|---|
| Home Page | Pass | Pass |
| Login | Pass | Pass |
| Register | Pass | Pass |
| Search Flights | Fail (3 issues) | Pass |
| Book Flights | Fail (7 issues) | Pass |
| Profile | Fail (2 issues) | Pass |
| Edit Booking | Fail (5 issues) | Pass |
| Payment | Pass | Pass |
| Payment Success | Pass | Pass |


## Pages That Passed Initially

### Home Page
Scanned at `http://localhost:8080`

**Result:** 0 issues detected. No changes required.
<img width="800" alt="Home Page :" src="https://github.com/user-attachments/assets/b13044d0-e8fc-4484-b5fc-b74211978a04" />


### Login
Scanned at `http://localhost:8080/login`

**Result:** 0 issues detected. No changes required.
<img width="800" alt="Login :login" src="https://github.com/user-attachments/assets/ad481b89-588c-4484-9cd4-8102efb6db0a" />


### Register
Scanned at `http://localhost:8080/register`

**Result:** 0 issues detected. No changes required.
<img width="800" alt="Register :register" src="https://github.com/user-attachments/assets/e8f8ba6c-ba41-4779-9197-a767e4c469fe" />


### Payment
Scanned at `http://localhost:8080/payment`

**Result:** 0 issues detected. No changes required.


### Payment Success
Scanned at `http://localhost:8080/payment-success`

**Result:** 0 issues detected. No changes required.
<img width="800" alt="payment :payment" src="https://github.com/user-attachments/assets/a0200588-de76-4c45-8287-35b81fb57861" />

---

## Pages That Initially Failed


### Book Flights
Scanned at `http://localhost:8080/book-flights`

**Initial result:** 7 issues → **Fixed: 0 issues**

| # | Issue | WCAG Criterion | Severity |
|---|---|---|---|
| 1–3 | Elements must meet minimum color contrast ratio thresholds (EXIT label, 3 instances) | 1.4.3 Contrast (Minimum) | Serious |
| 4 | Main landmark should not be contained in another landmark | Best Practice | Moderate |
| 5 | Document should not have more than one main landmark | Best Practice | Moderate |
| 6 | Landmarks should have a unique role or role/label/title combination | Best Practice | Moderate |
| 7 | Page should contain a level-one heading | Best Practice | Moderate |

**Fixes applied to `confirmBooking.peb` and `style.css`:**

- **Double `<main>` landmark (issues 4, 5, 6):** The template had `<main class="layout">` nested inside the `<main id="main-content">` from `base.peb`. Changed `<main class="layout">` to `<div class="layout">` so only one `<main>` landmark exists on the page.
- **Missing `<h1>` (issue 7):** Added a visually hidden `<h1 class="sr-only">Complete Booking</h1>` at the top of the content block.
- **EXIT label contrast (issues 1–3):** The `.exit-label` text was coloured `#c19b2e` (gold) on a `#f3f1eb` background, giving a contrast ratio of only 2.32:1 against the required 4.5:1. Changed the colour to `#7a5c00` (dark amber), achieving approximately 5.6:1.
<img width="800" alt="book-flights :book-flights" src="https://github.com/user-attachments/assets/919becd7-3c56-445e-92dc-5e18f758c995" />
<img width="800" alt="book-flights :book-flights 2" src="https://github.com/user-attachments/assets/0e66f42b-8f6c-4b9a-b6a4-bff9d0947fdd" />

---

### Profile
Scanned at `http://localhost:8080/profile`

**Initial result:** 2 issues → **Fixed: 0 issues**

| # | Issue | WCAG Criterion | Severity |
|---|---|---|---|
| 1 | Elements must meet minimum color contrast ratio thresholds (Logout button) | 1.4.3 Contrast (Minimum) | Serious |
| 2 | Page should contain a level-one heading | Best Practice | Moderate |

**Fixes applied to `profile.peb` and `style.css`:**

- **Button contrast (issue 1):** The `.profile-page .primary-btn` override set `color: white` on a `#d4af37` (gold) background, giving a contrast ratio of only 2.1:1. Changed the text colour to `var(--navy)` (`#0b1f3a`), achieving approximately 7.9:1.
- **Missing `<h1>` (issue 2):** The page title was marked up as `<h2>Your Profile</h2>`. Promoted it to `<h1>`. The two sub-section headings (`Your Bookings`, `Loyalty Points`) were then adjusted from `<h3>` down to `<h2>` to keep heading levels sequential.
<img width="800" alt="profile :profile" src="https://github.com/user-attachments/assets/ab1333cf-4e32-4d0c-82b4-ca49cbebc9e5" />
<img width="800" alt="profile :profile 2" src="https://github.com/user-attachments/assets/0cf7b4cc-1795-4b8f-9b50-1ce7e7ce3139" />

---

### Edit Booking
Scanned at `http://localhost:8080/edit-booking`

**Initial result:** 5 issues → **Fixed: 0 issues**

| # | Issue | WCAG Criterion | Severity |
|---|---|---|---|
| 1–2 | Elements must meet minimum color contrast ratio thresholds (Save Changes button, Cancel Booking button) | 1.4.3 Contrast (Minimum) | Serious |
| 3–4 | Form elements must have labels (Full Name input, ID Number input, per passenger) | 1.3.1 Info and Relationships | Serious |
| 5 | Page should contain a level-one heading | Best Practice | Moderate |

**Fixes applied to `editBooking.peb` and `style.css`:**

- **Save Changes button contrast (issue 1):** `.edit-booking-page .primary-btn` set `color: white` on `#d4af37` (gold), giving 2.1:1. Changed to `color: var(--navy)`, achieving approximately 7.9:1.
- **Cancel Booking button contrast (issue 2):** `.edit-booking-page .danger-btn` set `color: white` on `#e74c3c` (red), giving approximately 3.6:1. Darkened the background to `#c0392b`, achieving approximately 5.4:1. Hover state updated to `#922b21` accordingly.
- **Unlabelled form inputs (issues 3–4):** The `<label>` elements for Full Name and ID Number existed in the markup but were not programmatically linked to their inputs. Added matching `for` attributes to each label and `id` attributes to each input, using the loop index to keep them unique per passenger (e.g. `for="name_1"` / `id="name_1"`).
- **Missing `<h1>` and heading order (issue 5):** The page heading `Booking #{{ bookingId }}` was an `<h2>`. Promoted it to `<h1>`. The `Flights` and `Passengers` sub-headings were `<h3>` — stepped down to `<h2>` to maintain a sequential heading structure. CSS selectors updated to match.
<img width="800" alt="edit-booking :edit-booking" src="https://github.com/user-attachments/assets/33aaaba4-56dd-4c13-b091-1364bf284467" />
<img width="800" alt="edit-booking :edit-booking 2" src="https://github.com/user-attachments/assets/e1f0e842-4685-416c-9254-57622b3cbef9" />

---

### Search Flights
Scanned at `http://localhost:8080/search-flights`

**Initial result:** 3 issues — not yet resolved.

| # | Issue | WCAG Criterion | Severity |
|---|---|---|---|
| 1 | Main landmark should not be contained in another landmark | Best Practice | Moderate |
| 2 | Document should not have more than one main landmark | Best Practice | Moderate |
| 3 | Landmarks should have a unique role or role/label/title combination | Best Practice | Moderate |

**Root cause:** The `flights.peb` template uses `<main class="layout">` inside the content block, which itself renders inside the `<main id="main-content">` element in `base.peb`, producing two nested `<main>` landmarks.

- **Double `<main>` landmark (issues 1, 2, 3):** The template had `<main class="flights-layout">` nested inside the `<main id="main-content">` from `base.peb`, producing two `<main>` marks on the page. Changed `<main class="flights-layout">` to `<div class="flights-layout">` only one `<main>` landmark exists, removing the nesting conflict and making all landmarks unique. 

---