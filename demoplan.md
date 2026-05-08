# Demo Plan

## Format

- 30 minute slot total
- 20 minutes for demo and poster combined (we aim for 15 demo, 5 poster)
- Remaining time is Q&A — one question per team member
- Audio will be recorded — speak clearly, use the microphone if one is available
- Notes and flashcards are allowed but avoid reading from a script
- Run through the full demo at least twice before the day

---

## Setup Checklist (before you arrive)

- [ ] App is running locally and tested on the demo device
- [ ] Database is pre-populated with real flight data, real-looking passenger names, and realistic bookings
- [ ] Four browser windows open — one per persona, logged in using private/incognito windows
- [ ] Admin account logged in and ready in a separate window
- [ ] Poster is open and ready to switch to immediately after the demo
- [ ] Any text that needs to be typed (names, IDs) is copied into a notepad ready to paste
- [ ] Timer set to 15 minutes for the demo section

---

## Persona Assignments

Each team member presents one persona walking through the system as that user would.

| Person | Persona | What they show |
|---|---|---|
| Ashley | Marcus Chen — Business Traveller | Search with filters, one-way flight booking, profile view |
| Joe | David O'Brien — Family Traveller | Booking for 5 passengers, seat map, adjacent seat selection |
| Edith | Priya Sharma — Airline Ops Agent | Admin dashboard, passenger booking lookup, frequent flyer status |
| Omar | James Bailey — Flight Analyst | Admin charts, booking stats, busiest routes, cancellation data |

---

## Demo Script

### Introduction (1 minute — Joe)

Briefly explain what EAJO Air is and who it is for. You can show the persona cards on screen or just describe them verbally. Hand off to Ashley to start.

---

### Joe — Marcus Chen (3 minutes)

Marcus is a management consultant who books 2-3 flights a month. He needs speed and reliable information.

1. Start on the home page — show the search form
2. Search for a one-way flight (e.g. Leeds to London, next week, 1 adult)
3. Point out the filter options — price, departure time
4. Select a flight and proceed to seat selection
5. Choose a seat and complete the booking
6. Open the profile page to show the confirmed booking appearing

Key things to say:
- Filters were designed specifically around what users asked for in our interviews — Maddie said "I just want to say show me flights under £900, max one stopover, departing after 10am"
- The booking confirmation appears immediately in the profile

---

### Ashley — David O'Brien (4 minutes)

David is a primary school teacher booking a family holiday for 5 people. His biggest concern is keeping his family seated together.

1. Start a new search — Leeds to Lanzarote, return trip, 2 adults 3 children
2. Show the return trip flow — both outbound and return sections
3. Proceed to seat selection — this is the key feature to show
4. Walk through the seat map — show the colour coding, the exit row highlighting
5. Select 5 adjacent seats across the family — show that the map makes this clear
6. Show that occupied seats cannot be selected
7. Complete the booking and show it on the profile

Key things to say:
- Our user interview (Participant B) said his youngest ended up 4 rows away from the family — "she was five at the time" — this is exactly what the seat map was designed to prevent
- The seat map highlights available adjacent seats so families do not have to guess

---

### Edith — Priya Sharma (3 minutes)

Priya is a customer service agent who works at the desk and on live chat. She needs to look up passengers quickly and identify frequent flyers.

1. Log into the admin account
2. Show the admin dashboard — point out the stats row
3. Navigate to the recent bookings table — search for a passenger by name
4. Show a booking record — passenger details, flight, seat
5. Point out where frequent flyer status would be visible
6. Show the cancellations table as an example of the tools available to staff

Key things to say:
- Priya told us in her interview that frequent flyer status is not always visible — she has had passengers tell her themselves "I'm a gold member"
- The admin view is designed to surface all the information a desk agent needs in one place

---

### Omar — James Bailey (4 minutes)

James is an operations analyst who needs booking data to make scheduling and resource decisions.

1. Still on the admin dashboard — show the charts section
2. Walk through the bookings over time chart — explain what it shows
3. Show the booking status breakdown pie chart
4. Show the busiest routes bar chart — explain how this informs route planning
5. Show the upcoming flights table
6. Briefly show the test suite passing (open the Gradle test report or show GitHub Actions) — this shows the system is reliable

Key things to say:
- Before this system, analysts like James would have to wait for manual data exports
- These charts are generated server-side from live data every time the page loads
- 79 automated tests all passing gives confidence the data being shown is accurate

---

### Handoff to Poster (immediate)

As soon as Omar finishes, switch directly to the poster. No gap.

---

## Contingency Plan

| Problem | Response |
|---|---|
| App crashes or won't start | Have a screen recording of the full flow as a backup — narrate over it |
| Database is empty or missing data | Have a SQL seed script ready to run in 30 seconds |
| Wrong account logged in | Use private windows to avoid this — have credentials written down |
| Seat map doesn't load | Explain the feature verbally and show the code in the DAO |
| Lost track of time | Whoever is speaking cuts to their key point and hands over |

---

## Q&A Preparation

Each person should be ready to answer questions related to the section they demonstrated. Questions will come from one of these five themes:

### Technical design
Likely asked of Joe or Omar. Be ready to talk about:
- Why we used Ktor and SQLite
- How the database schema is structured (routes → flights → seats → bookings)
- How the seat map is generated from AircraftConfig
- How connecting flights work

### Project design and decision making
Could be anyone. Be ready to talk about:
- Why we chose the features we did (trace back to personas and user stories)
- Design decisions — navy/gold palette, multi-page flow vs SPA, separate admins table
- Trade-offs we made (e.g. in-memory OTC codes, no connection pooling)

### Teamworking and continuous improvement
Could be anyone. Be ready to talk about:
- How we split work (user story ownership)
- How we used pull requests and branch protection
- How we iterated — e.g. cleanup sprint, adding KDoc, fixing resource leaks

### Testing and quality
Likely asked of Ashley or Edith. Be ready to talk about:
- The 79 automated tests and what they cover
- How GitHub Actions enforces tests on every PR
- WCAG accessibility audit and what we fixed
- Manual testing process

### Value and user experience
Likely asked of Edith or Ashley. Be ready to talk about:
- What users told us in interviews (Maddie, Ben, Cara)
- How specific features map to specific pain points
- The job stories — e.g. family seating directly addresses Ben's experience
- Frequent flyer recognition addressing Maddie's and Cara's quotes
