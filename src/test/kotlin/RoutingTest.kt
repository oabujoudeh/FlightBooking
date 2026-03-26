package com.flightbooking

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains


/* tests for checking our routes work properly
   mostly checking pages load and that auth protection is in place */

class RoutingTest {

    // just checking basic pages actually load without crashing

    @Test
    fun testHomePageLoads() = testApplication {
        application { module() }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testHomePageHasOurBranding() = testApplication {
        application { module() }

        val response = client.get("/")
        assertContains(response.bodyAsText(), "EAJO Air")
    }

    @Test
    fun testLoginPageLoads() = testApplication {
        application { module() }

        val response = client.get("/login")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testRegisterPageLoads() = testApplication {
        application { module() }

        val response = client.get("/register")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testForgotPasswordPageLoads() = testApplication {
        application { module() }

        val response = client.get("/forgot-password")
        assertEquals(HttpStatusCode.OK, response.status)
    }


    // login tests - making sure bad logins dont get through

    @Test
    fun testBadLoginShowsError() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }

        val response = client.post("/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("username=fake@email.com&password=wrongpassword")
        }

        // should stay on login page and show the error, not redirect
        assertEquals(HttpStatusCode.OK, response.status, "bad login shouldnt redirect anywhere")
        assertContains(response.bodyAsText(), "Invalid email or password")
    }


    // logout should send you back to the home page

    @Test
    fun testLogoutSendsYouHome() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }

        val response = client.get("/logout")

        assertEquals(HttpStatusCode.Found, response.status, "logout should redirect you")
        assertEquals("/", response.headers["Location"], "should go back to home page")
    }


    // these tests check that you cant access protected pages without logging in first
    // all of these should kick you to /login

    @Test
    fun testCantAccessProfileWithoutLogin() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }

        val response = client.get("/profile")

        assertEquals(HttpStatusCode.Found, response.status, "should redirect if not logged in")
        assertEquals("/login", response.headers["Location"], "should send to login page")
    }

    @Test
    fun testCantConfirmBookingWithoutLogin() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }

        val response = client.post("/confirm-booking") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("outboundFlightId=1&adults=1&children=0")
        }

        assertEquals(HttpStatusCode.Found, response.status, "should redirect to login")
        assertEquals("/login", response.headers["Location"])
    }

    @Test
    fun testCantCancelBookingWithoutLogin() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }

        val response = client.post("/cancel-booking") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("bookingId=1")
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/login", response.headers["Location"])
    }

    @Test
    fun testCantEditBookingWithoutLogin() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }

        val response = client.get("/edit-booking?id=1")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/login", response.headers["Location"])
    }

    @Test
    fun testCantUpdateBookingWithoutLogin() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }

        val response = client.post("/update-booking") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("bookingId=1&passengerCount=0")
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/login", response.headers["Location"])
    }


    // airport search endpoint tests

    @Test
    fun testAirportSearchNeedsTwoChars() = testApplication {
        application { module() }

        // only typed one letter, should give us nothing back
        val response = client.get("/search-airports?q=L")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText().trim(), "one letter shouldnt return anything")
    }

    @Test
    fun testAirportSearchFindsLondon() = testApplication {
        application { module() }

        val response = client.get("/search-airports?q=London")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "London", message = "searching london should find london airports")
    }


    // flight search with nothing filled in should just send you home

    @Test
    fun testFlightSearchWithNothingRedirects() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }

        // sending empty post, no params at all
        val response = client.post("/search-flights") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("")
        }

        assertEquals(HttpStatusCode.Found, response.status, "no params should just go back to home")
    }


    // admin chart routes - normal users shouldnt be able to see these
    // they should all redirect to home

    @Test
    fun testNonAdminCantSeeBookingsChart() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }

        val response = client.get("/admin/chart/bookings-over-time")

        assertEquals(HttpStatusCode.Found, response.status, "not an admin so should redirect")
        assertEquals("/", response.headers["Location"])
    }

    @Test
    fun testNonAdminCantSeeStatusChart() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }

        val response = client.get("/admin/chart/booking-status")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/", response.headers["Location"])
    }

    @Test
    fun testNonAdminCantSeeRoutesChart() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }

        val response = client.get("/admin/chart/busiest-routes")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/", response.headers["Location"])
    }
}
