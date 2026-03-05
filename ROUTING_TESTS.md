# Routing Tests - Issues To Create

A full list of GitHub issues to create for testing every route in `Routing.kt`.
Covers happy paths, error handling, session/auth security, data validation, and edge cases.

See `TESTING.md` for how to write each test.

---

## GET /

### Happy Path
- [ ] **Test home page returns 200**
  - `GET /` should return `HttpStatusCode.OK`
- [ ] **Test home page loads for unauthenticated user**
  - `GET /` without a session should still return 200 — it is a public page
- [ ] **Test home page loads for authenticated user**
  - `GET /` with an active session should return 200

### Session Handling
- [ ] **Test session message is cleared after home page load**
  - If a `UserSession` has a non-empty `message`, after `GET /` the message should be cleared from the session
- [ ] **Test home page shows correct navbar state when logged in**
  - Response body should contain "Profile" link and not "Login" when a session is active
- [ ] **Test home page shows login button when not logged in**
  - Response body should contain "Login" link when no session is present

---

## GET /login

### Happy Path
- [ ] **Test login page returns 200**
  - `GET /login` should return `HttpStatusCode.OK`
- [ ] **Test login page contains a form**
  - Response body should contain a `<form` element and `username` and `password` fields

### Error Handling
- [ ] **Test login page does not expose server errors**
  - Response body should not contain stack traces or internal error messages

---

## POST /login

### Happy Path
- [ ] **Test login with valid credentials redirects to profile**
  - `POST /login` with correct username and password should return 302 with `Location: /profile`
- [ ] **Test login sets a session cookie on success**
  - After a successful `POST /login` the response should include a `Set-Cookie` header with the session cookie

### Error Handling
- [ ] **Test login with wrong password returns error message**
  - `POST /login` with incorrect password should return 200 and the response body should contain an error message, not redirect
- [ ] **Test login with wrong username returns error message**
  - `POST /login` with a username that does not exist should return 200 with an error message
- [ ] **Test login with empty username field**
  - `POST /login` with `username=` (blank) should not redirect to profile
- [ ] **Test login with empty password field**
  - `POST /login` with `password=` (blank) should not redirect to profile
- [ ] **Test login with both fields empty**
  - `POST /login` with no params should not redirect and should return an error

### Data / Security
- [ ] **Test login does not accept SQL injection in username**
  - `POST /login` with `username=' OR '1'='1` should not authenticate the user
- [ ] **Test login does not accept SQL injection in password**
  - `POST /login` with `password=' OR '1'='1` should not authenticate the user
- [ ] **Test login with excessively long username is handled**
  - `POST /login` with a username of 10,000 characters should not crash the server — expect 200 or 400, not 500
- [ ] **Test login with excessively long password is handled**
  - Same as above but for password field
- [ ] **Test login with special characters in username is handled**
  - `POST /login` with `username=<script>alert(1)</script>` should not reflect the script back in the response body (XSS check)

---

## GET /logout

### Happy Path
- [ ] **Test logout redirects to home**
  - `GET /logout` should return 302 with `Location: /`
- [ ] **Test logout clears the session**
  - After `GET /logout`, `GET /profile` should redirect to `/login` confirming the session is destroyed
- [ ] **Test logout works when not logged in**
  - `GET /logout` without an existing session should still return 302 to `/` and not crash

---

## GET /profile

### Happy Path
- [ ] **Test profile returns 200 when logged in**
  - `GET /profile` with an active session should return `HttpStatusCode.OK`
- [ ] **Test profile page shows the correct username**
  - Response body should contain the username from the active session

### Auth / Security
- [ ] **Test profile redirects to login when not logged in**
  - `GET /profile` without a session should return 302 with `Location: /login`
- [ ] **Test profile cannot be accessed with a tampered session**
  - `GET /profile` with a forged or invalid session cookie should redirect to `/login`
- [ ] **Test profile does not expose other users' data**
  - A session for user A should only show user A's data in the response body

---

## POST /search-flights

### Happy Path
- [ ] **Test search returns 200 with all valid params**
  - `POST /search-flights` with `departure`, `destination`, `departureDate`, `adults=1`, `children=0`, `tripType=oneway` should return 200
- [ ] **Test search response body contains flight results**
  - Response body should contain flight data (e.g. "British Airways") from the fake results
- [ ] **Test search returns results for both one-way and return trip types**
  - `POST /search-flights` with `tripType=oneway` and separately `tripType=return` should both return 200

### Data Validation
- [ ] **Test search with missing departure field**
  - `POST /search-flights` without `departure` param should handle gracefully — no 500 error
- [ ] **Test search with missing destination field**
  - `POST /search-flights` without `destination` param should handle gracefully
- [ ] **Test search with missing departure date**
  - `POST /search-flights` without `departureDate` should handle gracefully
- [ ] **Test search with adults set to 0**
  - `POST /search-flights` with `adults=0` should either reject the search or handle gracefully — at least 1 adult is required
- [ ] **Test search with negative adults value**
  - `POST /search-flights` with `adults=-1` should not crash and should be rejected
- [ ] **Test search with non-numeric adults value**
  - `POST /search-flights` with `adults=abc` should handle gracefully and not throw an exception
- [ ] **Test search with return date before departure date**
  - `POST /search-flights` where `returnDate` is earlier than `departureDate` should return an error, not results
- [ ] **Test search with same departure and destination**
  - `POST /search-flights` with `departure=LHR&destination=LHR` should return an error

### Security
- [ ] **Test search with XSS in departure field**
  - `POST /search-flights` with `departure=<script>alert(1)</script>` should not reflect the script in the response body
- [ ] **Test search with very long departure string**
  - `POST /search-flights` with a 10,000 character departure value should not crash the server

### Session
- [ ] **Test search results are shown to unauthenticated users**
  - `POST /search-flights` without a session should still return 200 — searching flights should be public
- [ ] **Test search results are shown to authenticated users**
  - `POST /search-flights` with an active session should return 200 and still show results

---

## Static Files

- [ ] **Test CSS file is served correctly**
  - `GET /static/style.css` should return 200 with `Content-Type: text/css`
- [ ] **Test request for non-existent static file returns 404**
  - `GET /static/doesnotexist.css` should return `HttpStatusCode.NotFound`

---

## General / Server

- [ ] **Test unknown route returns 404**
  - `GET /somepagethatlddoesnotexist` should return `HttpStatusCode.NotFound`
- [ ] **Test server does not return 500 on any known route**
  - All routes should return expected status codes and never an internal server error under normal conditions
