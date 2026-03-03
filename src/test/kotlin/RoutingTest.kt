                                                                                                                
package com.example.com                                                                                                   
                
import io.ktor.client.request.*                                                                                           
import io.ktor.http.*                                                                                                     
import io.ktor.server.testing.*                                                                                           
import kotlin.test.Test                                                                                                   
import kotlin.test.assertEquals

/* This file contains the tests for routing and the pages. */

class RoutingTest {
    // Test for Login page
    @Test
    fun testLoginPageReturns200() = testApplication {
        application {
            module()
        }
        val response = client.get("/login")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testLoginWithTestuser() = testApplication {
        application { module() }
        
        val client = createClient { followRedirects = false }

        val response = client.post("/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("username=joe&password=secret")
        }

        // Should redirect to profile
        assertEquals(HttpStatusCode.Found, response.status, "Login should redirect the user to profile")
        assertEquals("/profile", response.headers["Location"], "Login should redirect to the profile page")
    }

    // Test for /logout - checks redirect happens and session is cleared
    @Test
    fun testLogoutRedirectsToHome() = testApplication {
        application { module() }

        val client = createClient { followRedirects = false }

        // First set a session by logging in
        client.post("/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("username=joe&password=secret")
        }

        // Now logout
        val response = client.get("/logout")

        // Should redirect to home
        assertEquals(HttpStatusCode.Found, response.status,"Logout should redirect the user")
        assertEquals("/", response.headers["Location"],"Logout should redirect to the home page")

        // Session should be cleared - /profile should now redirect to login
        val profileResponse = client.get("/profile") 
        assertEquals(HttpStatusCode.Found, profileResponse.status,"After logout, /profile should redirect as session should be cleared")
        assertEquals("/login", profileResponse.headers["Location"],"After logout, user should be sent back to /login")
    }
    
    // Test for home page including /search-flights
    @Test
    fun testHomePageReturns200() = testApplication {
        application {
            module()
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // Test for profile -> login page redirect if the user has not logged in.
    @Test
    fun testProfileRedirectsWhenNotLoggedIn() = testApplication {
        application {
            module()
        }

        val client = createClient {
          followRedirects = false
        }

        val response = client.get("/profile")
        assertEquals(HttpStatusCode.Found, response.status, "Expected /profile to redirect unauthenticated usrs to /login") // 302 redirect to /login
    }
    @Test
    fun testProfileReidrectsToLoginWhenNotLoggedIn() = testApplication {
       application {
            module()
        }
        val client = createClient {
            followRedirects = false
        }
        val response = client.get("/profile")
        assertEquals("/login", response.headers["Location"], "Expected redirect to go to /login page.")
    }
}