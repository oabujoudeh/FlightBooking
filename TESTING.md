# Testing Guide

This guide covers how to write tests for the FlightBooking project so the whole team can write consistent, readable tests.

---

## Running Tests

```bash
./gradlew test
```

Test results are saved to `build/reports/tests/test/index.html` — open this in a browser for a full report.

---

## Where Tests Live

All tests go in `src/test/kotlin/`. Mirror the same package as the file you're testing:

```
src/
  main/kotlin/
    Routing.kt
  test/kotlin/
    RoutingTest.kt      ← tests for Routing.kt
```

---

## Basic Test Structure

Every test class uses `@Test` to mark a test function and `testApplication` to spin up a fake version of the server — no need to actually start it.

```kotlin
class RoutingTest {

    @Test
    fun testLoginPageReturns200() = testApplication {
        application { module() }

        val response = client.get("/login")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
```

- `testApplication` — starts a fake in-memory server for the test
- `application { module() }` — loads your app exactly as it runs in production
- `client.get("/login")` — makes a fake HTTP request
- `assertEquals(expected, actual)` — checks the result

---

## Assertions

### Check a status code
```kotlin
assertEquals(HttpStatusCode.OK, response.status)
assertEquals(HttpStatusCode.Found, response.status) // 302 redirect
assertEquals(HttpStatusCode.BadRequest, response.status)
```

### Add a message so failures are clear to the team
Always add a message as the third argument so whoever sees the failure knows what went wrong:
```kotlin
assertEquals(HttpStatusCode.Found, response.status,
    "Expected /profile to redirect unauthenticated users to /login")
```

### Check where a redirect goes
```kotlin
assertEquals("/login", response.headers["Location"],
    "Expected redirect to go to /login, not somewhere else")
```

### Check the response body contains something
```kotlin
val body = response.bodyAsText()
assertTrue(body.contains("Login"), "Expected login page to contain a Login heading")
```

### Rule something out with assertNotEquals
```kotlin
assertNotEquals(HttpStatusCode.OK, response.status,
    "/profile should never return 200 for a logged out user")
```

### Fail with a clear message
Use `fail()` when you want full control over the message:
```kotlin
if (response.status != HttpStatusCode.Found) {
    fail("Got ${response.status} — /profile should redirect to /login when not logged in")
}
```

---

## Redirects

By default the test client **follows redirects**. If you want to check that a redirect happens (i.e. check for a 302), disable it:

```kotlin
// Check the redirect itself (302)
val client = createClient { followRedirects = false }
val response = client.get("/profile")
assertEquals(HttpStatusCode.Found, response.status)

// Check where it ends up after following the redirect (200)
val client = createClient { followRedirects = true }
val response = client.get("/profile")
assertEquals(HttpStatusCode.OK, response.status)
```

---

## POST Requests

To test a form submission, send the parameters as a form body:

```kotlin
@Test
fun testLoginPostRedirectsOnSuccess() = testApplication {
    application { module() }

    val client = createClient { followRedirects = false }

    val response = client.post("/login") {
        contentType(ContentType.Application.FormUrlEncoded)
        setBody("username=joe&password=secret")
    }

    assertEquals(HttpStatusCode.Found, response.status,
        "Valid login should redirect to profile")
}
```

---

## Naming Conventions

Good test names tell you exactly what is being tested and what the expected outcome is. Use this pattern:

```
test[What][ExpectedResult]
```

| Good | Bad |
|---|---|
| `testProfileRedirectsWhenNotLoggedIn` | `testProfile` |
| `testLoginPageReturns200` | `test1` |
| `testSearchFlightsReturnsResults` | `testSearch` |

---

## Real Examples from This Project

```kotlin
// ✅ Page loads
@Test
fun testLoginPageReturns200() = testApplication {
    application { module() }
    val response = client.get("/login")
    assertEquals(HttpStatusCode.OK, response.status,
        "Login page should always be accessible")
}

// ✅ Auth protection - check the redirect status
@Test
fun testProfileRedirectsWhenNotLoggedIn() = testApplication {
    application { module() }
    val client = createClient { followRedirects = false }
    val response = client.get("/profile")
    assertEquals(HttpStatusCode.Found, response.status,
        "Unauthenticated users should be redirected away from /profile")
}

// ✅ Auth protection - check it goes to the right page
@Test
fun testProfileRedirectsToLoginWhenNotLoggedIn() = testApplication {
    application { module() }
    val client = createClient { followRedirects = false }
    val response = client.get("/profile")
    assertEquals("/login", response.headers["Location"],
        "Unauthenticated users should be sent to /login")
}
```

---

## Quick Reference

| What you want to check | How |
|---|---|
| Page returns 200 | `assertEquals(HttpStatusCode.OK, response.status)` |
| Page redirects (302) | `assertEquals(HttpStatusCode.Found, response.status)` with `followRedirects = false` |
| Redirect goes to right place | `assertEquals("/login", response.headers["Location"])` |
| Page body contains text | `assertTrue(response.bodyAsText().contains("text"))` |
| POST a form | `setBody("key=value")` with `ContentType.Application.FormUrlEncoded` |
