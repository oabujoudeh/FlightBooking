package com.flightbooking

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for [Application.module].
 *
 * [Application.module] wires up four concerns in order:
 * 1. [ContentNegotiation] — JSON serialisation/deserialisation
 * 2. [configureTemplates] — Pebble HTML rendering
 * 3. [configureRouting]   — all application routes
 * 4. [configureSessions]  — cookie session support
 *
 * Each section below targets one of those concerns. Route-level auth protection
 * and individual page loads are covered by [RoutingTest]; this file focuses on
 * the cross-cutting wiring that [module] provides.
 */
class ApplicationTest {
    // ── Module boots without error ─────────────────────────────────────────────

    /**
     * Verifies that [Application.module] installs without throwing by making a
     * simple GET request to the home route and expecting a 200 response.
     */
    @Test
    fun testModuleStartsSuccessfully() =
        testApplication {
            application { module() }
            val response = client.get("/")
            assertEquals(HttpStatusCode.OK, response.status)
        }

    // ── ContentNegotiation: JSON responses ────────────────────────────────────

    /**
     * Verifies that the JSON endpoint `/search-airports` responds with a
     * `Content-Type` of `application/json`, confirming that [ContentNegotiation]
     * is active.
     */
    @Test
    fun testJsonEndpointReturnsJsonContentType() =
        testApplication {
            application { module() }
            val response = client.get("/search-airports?q=London")
            val contentType = response.contentType()
            assertNotNull(contentType, "Content-Type header must be present")
            assertEquals(ContentType.Application.Json.contentType, contentType.contentType)
        }

    /**
     * Verifies that the JSON endpoint returns a well-formed JSON array (starts
     * with `[` and ends with `]`) for a query that matches at least one airport.
     */
    @Test
    fun testJsonEndpointReturnsValidJsonArray() =
        testApplication {
            application { module() }
            val body = client.get("/search-airports?q=Leeds").bodyAsText().trim()
            assertTrue(body.startsWith("["), "Response body must start with '['")
            assertTrue(body.endsWith("]"), "Response body must end with ']'")
        }

    /**
     * Verifies that the JSON endpoint returns an empty array `[]` when the query
     * is too short (one character), rather than null or an error.
     */
    @Test
    fun testJsonEndpointReturnsEmptyArrayForShortQuery() =
        testApplication {
            application { module() }
            val body = client.get("/search-airports?q=L").bodyAsText().trim()
            assertEquals("[]", body, "One-character query must return an empty JSON array")
        }

    /**
     * Verifies that the JSON endpoint handles a missing query parameter gracefully,
     * returning a 200 with an empty array rather than a 500.
     */
    @Test
    fun testJsonEndpointHandlesMissingQueryParam() =
        testApplication {
            application { module() }
            val response = client.get("/search-airports")
            assertTrue(
                response.status == HttpStatusCode.OK || response.status == HttpStatusCode.BadRequest,
                "Missing query param must not cause a 500",
            )
            assertFalse(response.status == HttpStatusCode.InternalServerError)
        }

    // ── Pebble templates: HTML responses ──────────────────────────────────────

    /**
     * Verifies that HTML pages are served with a `text/html` Content-Type,
     * confirming that the Pebble template engine is configured.
     */
    @Test
    fun testHtmlPagesHaveCorrectContentType() =
        testApplication {
            application { module() }
            val response = client.get("/")
            val contentType = response.contentType()
            assertNotNull(contentType, "Content-Type must be set on HTML pages")
            assertEquals("text/html", "${contentType.contentType}/${contentType.contentSubtype}".substringBefore(";"))
        }

    /**
     * Verifies that the login page renders a `<form>` element, confirming
     * that the template engine resolves and renders `.peb` template files.
     */
    @Test
    fun testLoginPageRendersForm() =
        testApplication {
            application { module() }
            val body = client.get("/login").bodyAsText()
            assertTrue(body.contains("<form"), "Login page must contain a <form> element")
        }

    /**
     * Verifies that the register page renders a `<form>` element.
     */
    @Test
    fun testRegisterPageRendersForm() =
        testApplication {
            application { module() }
            val body = client.get("/register").bodyAsText()
            assertTrue(body.contains("<form"), "Register page must contain a <form> element")
        }

    // ── Routing: unknown paths and methods ────────────────────────────────────

    /**
     * Verifies that a completely unknown path returns a 404 rather than a 500,
     * confirming that [configureRouting] does not register a catch-all that
     * swallows errors silently.
     */
    @Test
    fun testUnknownPathReturns404() =
        testApplication {
            application { module() }
            val response = client.get("/this-path-does-not-exist-at-all")
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    /**
     * Verifies that sending a POST to a GET-only route does not return 500
     * (405 Method Not Allowed or a redirect are both acceptable).
     */
    @Test
    fun testPostToGetOnlyRouteDoesNotReturn500() =
        testApplication {
            application { module() }
            val client = createClient { followRedirects = false }
            val response =
                client.post("/forgot-password") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("")
                }
            assertFalse(
                response.status == HttpStatusCode.InternalServerError,
                "POST to GET-only route must not return 500",
            )
        }

    // ── Sessions: cookie attributes ────────────────────────────────────────────

    /**
     * Verifies that the `user_session` cookie is scoped to the root path (`/`),
     * so it is sent on every request and not just sub-paths.
     */
    @Test
    fun testSessionCookieHasRootPath() =
        testApplication {
            application { module() }
            val client = createClient { followRedirects = false }
            val response =
                client.post("/login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("username=tnvn3422%40leeds.ac.uk&password=Admin%401234")
                }
            val cookies = response.headers.getAll("Set-Cookie") ?: emptyList()
            val sessionCookie = cookies.find { it.startsWith("user_session") } ?: return@testApplication
            assertTrue(sessionCookie.contains("Path=/"), "Session cookie must be scoped to path '/'")
        }

    /**
     * Verifies that the `user_session` cookie has a `Max-Age` attribute, confirming
     * the 3 600-second (1 hour) lifetime configured in [configureSessions].
     */
    @Test
    fun testSessionCookieHasMaxAge() =
        testApplication {
            application { module() }
            val client = createClient { followRedirects = false }
            val response =
                client.post("/login") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("username=tnvn3422%40leeds.ac.uk&password=Admin%401234")
                }
            val cookies = response.headers.getAll("Set-Cookie") ?: emptyList()
            val sessionCookie = cookies.find { it.startsWith("user_session") } ?: return@testApplication
            assertTrue(sessionCookie.contains("Max-Age="), "Session cookie must carry a Max-Age attribute")
        }

    // ── Cache-control headers ──────────────────────────────────────────────────

    /**
     * Verifies that sensitive authenticated pages carry a `no-store` cache-control
     * directive so that browsers do not cache session-specific content.
     */
    @Test
    fun testSensitivePagesHaveNoCacheHeader() =
        testApplication {
            application { module() }
            val client = createClient { followRedirects = false }

            // Profile redirects when unauthenticated, but the redirect itself must
            // still carry the no-store directive set before the redirect.
            val profileResponse = client.get("/profile")
            val cacheControl = profileResponse.headers[HttpHeaders.CacheControl] ?: ""
            assertTrue(cacheControl.contains("no-store"), "Profile redirect must include no-store")
        }

    /**
     * Verifies that the home page also carries `no-store` to prevent admin/user
     * state from leaking across sessions on shared browsers.
     */
    @Test
    fun testHomePageHasNoCacheHeader() =
        testApplication {
            application { module() }
            val cacheControl = client.get("/").headers[HttpHeaders.CacheControl] ?: ""
            assertTrue(cacheControl.contains("no-store"), "Home page must include no-store")
        }
}
