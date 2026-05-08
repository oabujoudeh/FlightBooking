package com.flightbooking

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/* tests for the complaint system
   covers submitting complaints, fetching them, and admin replies */

class ComplaintDAOTest {

    // the admin account we know exists in the test database
    private val knownUserId = UserDAO.getUserID("tnvn3422@leeds.ac.uk")


    /**
    * Tests for submitting a complaint.
    *
    * These checks make sure a valid complaint saves correctly and that
    * an invalid user ID causes the insert to fail.
    */
    @Test
    fun testSubmitComplaintReturnsTrue() {
        val result = ComplaintDAO.submitComplaint(knownUserId, "test complaint from automated test")
        assertTrue(result, "valid complaint should save successfully")
    }

    @Test
    fun testSubmitComplaintWithInvalidUserReturnsFalse() {
        // user id -1 doesnt exist so the foreign key should cause it to fail
        val result = ComplaintDAO.submitComplaint(-1, "this shouldnt work")
        assertFalse(result, "complaint with a non-existent user id should fail")
    }


    /**
    * Tests for getting a user's complaints.
    *
    * These checks make sure the list comes back properly, returns empty for
    * unknown users, and that each entry has the keys we expect.
    */
    @Test
    fun testGetComplaintsForUserReturnsList() {
        // make sure there is at least one complaint first
        ComplaintDAO.submitComplaint(knownUserId, "complaint for list test")
        val result = ComplaintDAO.getComplaintsForUser(knownUserId)
        assertNotNull(result, "should return a list not null")
    }

    @Test
    fun testGetComplaintsForUnknownUserReturnsEmpty() {
        val result = ComplaintDAO.getComplaintsForUser(-1)
        assertTrue(result.isEmpty(), "unknown user should have no complaints")
    }

    @Test
    fun testGetComplaintsForUserHasExpectedKeys() {
        ComplaintDAO.submitComplaint(knownUserId, "complaint for key check test")
        val result = ComplaintDAO.getComplaintsForUser(knownUserId)
        if (result.isNotEmpty()) {
            val entry = result.first()
            assertTrue(entry.containsKey("complaintId"), "should have complaintId")
            assertTrue(entry.containsKey("content"), "should have content")
            assertTrue(entry.containsKey("status"), "should have status")
            assertTrue(entry.containsKey("reply"), "should have reply")
            assertTrue(entry.containsKey("createdAt"), "should have createdAt")
        }
    }

    @Test
    fun testNewComplaintHasPendingStatus() {
        ComplaintDAO.submitComplaint(knownUserId, "complaint for status check")
        val result = ComplaintDAO.getComplaintsForUser(knownUserId)
        if (result.isNotEmpty()) {
            val latest = result.first()
            assertTrue(latest["status"] == "pending", "new complaint should be pending not resolved")
        }
    }


    /**
    * Tests for the admin complaints view.
    *
    * These checks make sure all complaints are returned and that each
    * entry has the keys the admin page needs.
    */
    @Test
    fun testGetAllComplaintsReturnsList() {
        val result = ComplaintDAO.getAllComplaints()
        assertNotNull(result, "should return a list not null")
    }

    @Test
    fun testGetAllComplaintsHasExpectedKeys() {
        ComplaintDAO.submitComplaint(knownUserId, "complaint for admin key check")
        val result = ComplaintDAO.getAllComplaints()
        if (result.isNotEmpty()) {
            val entry = result.first()
            assertTrue(entry.containsKey("complaintId"), "should have complaintId")
            assertTrue(entry.containsKey("content"), "should have content")
            assertTrue(entry.containsKey("status"), "should have status")
            assertTrue(entry.containsKey("reply"), "should have reply")
            assertTrue(entry.containsKey("createdAt"), "should have createdAt")
            assertTrue(entry.containsKey("userEmail"), "should have userEmail for the admin view")
        }
    }


    /**
    * Tests for replying to a complaint.
    *
    * These checks make sure a valid reply saves and changes the status to
    * resolved, and that replying to a non-existent complaint fails.
    */
    @Test
    fun testReplyToComplaintReturnsTrue() {
        ComplaintDAO.submitComplaint(knownUserId, "complaint for reply test")
        val complaints = ComplaintDAO.getComplaintsForUser(knownUserId)
        if (complaints.isNotEmpty()) {
            val complaintId = complaints.first()["complaintId"] as Int
            val result = ComplaintDAO.replyToComplaint(complaintId, "thank you for your feedback")
            assertTrue(result, "replying to a valid complaint should return true")
        }
    }

    @Test
    fun testReplyToComplaintSetsStatusToResolved() {
        ComplaintDAO.submitComplaint(knownUserId, "complaint for resolved status test")
        val complaints = ComplaintDAO.getComplaintsForUser(knownUserId)
        if (complaints.isNotEmpty()) {
            val complaintId = complaints.first()["complaintId"] as Int
            ComplaintDAO.replyToComplaint(complaintId, "we have looked into this")
            val updated = ComplaintDAO.getComplaintsForUser(knownUserId)
            val updatedComplaint = updated.find { it["complaintId"] == complaintId }
            assertTrue(updatedComplaint?.get("status") == "resolved", "status should be resolved after a reply")
        }
    }

    @Test
    fun testReplyToComplaintWithInvalidIdReturnsFalse() {
        val result = ComplaintDAO.replyToComplaint(-1, "this shouldnt work")
        assertFalse(result, "replying to a complaint that doesnt exist should fail")
    }
}
