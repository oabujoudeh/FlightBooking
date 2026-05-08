package com.flightbooking

/**
 * Data Access Object for user complaint operations.
 *
 * Handles submission, retrieval, and admin reply for complaints.
 * Admin-facing queries join the users table to include the submitter's email.
 */
object ComplaintDAO {
    /**
     * Inserts a new complaint for the given user.
     *
     * @param userId the ID of the user submitting the complaint
     * @param content the complaint message
     * @return true if the complaint was saved successfully, otherwise false
     */
    fun submitComplaint(
        userId: Int,
        content: String,
    ): Boolean {
        val sql = "INSERT INTO complaints (user_id, content) VALUES (?, ?)"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, userId)
                    stmt.setString(2, content)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns all complaints submitted by a specific user, most recent first.
     *
     * @param userId the ID of the user whose complaints to retrieve
     * @return a list of complaint maps with keys `complaintId`, `content`, `status`,
     *         `reply`, and `createdAt`; empty on error or if none found
     */
    fun getComplaintsForUser(userId: Int): List<Map<String, Any?>> {
        val sql =
            """
            SELECT complaint_id, content, status, reply, created_at
            FROM complaints
            WHERE user_id = ?
            ORDER BY created_at DESC
            """.trimIndent()
        val results = mutableListOf<Map<String, Any?>>()
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, userId)
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            results.add(
                                mapOf(
                                    "complaintId" to rs.getInt("complaint_id"),
                                    "content" to rs.getString("content"),
                                    "status" to rs.getString("status"),
                                    "reply" to rs.getString("reply"),
                                    "createdAt" to rs.getString("created_at"),
                                ),
                            )
                        }
                    }
                }
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Returns all complaints across all users, most recent first.
     *
     * Intended for the admin complaints page. Each entry includes the submitting
     * user's email, retrieved via a join on the users table.
     *
     * @return a list of complaint maps with keys `complaintId`, `content`, `status`,
     *         `reply`, `createdAt`, and `userEmail`; empty on error or if none found
     */
    fun getAllComplaints(): List<Map<String, Any?>> {
        val sql =
            """
            SELECT c.complaint_id, c.content, c.status, c.reply, c.created_at, u.email
            FROM complaints c
            JOIN users u ON c.user_id = u.user_id
            ORDER BY c.created_at DESC
            """.trimIndent()
        val results = mutableListOf<Map<String, Any?>>()
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            results.add(
                                mapOf(
                                    "complaintId" to rs.getInt("complaint_id"),
                                    "content" to rs.getString("content"),
                                    "status" to rs.getString("status"),
                                    "reply" to rs.getString("reply"),
                                    "createdAt" to rs.getString("created_at"),
                                    "userEmail" to rs.getString("email"),
                                ),
                            )
                        }
                    }
                }
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Saves an admin reply to a complaint and marks it as `'resolved'`.
     *
     * @param complaintId the ID of the complaint to reply to
     * @param reply the admin's reply text
     * @return true if the update succeeded, otherwise false
     */
    fun replyToComplaint(
        complaintId: Int,
        reply: String,
    ): Boolean {
        val sql = "UPDATE complaints SET reply = ?, status = 'resolved' WHERE complaint_id = ?"
        return try {
            Database.getConnection().use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, reply)
                    stmt.setInt(2, complaintId)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
