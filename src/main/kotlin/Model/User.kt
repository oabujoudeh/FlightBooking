package com.flightbooking

/**
 * Stores the details for one user.
 *
 * @property userId the unique identifier for the user; null if not yet persisted
 * @property firstName the user's first name
 * @property middleName the user's middle name; null if not provided
 * @property lastName the user's last name
 * @property email the user's email address, used for login and notifications
 * @property passwordHash the bcrypt-hashed password for the user
 * @property createdAt the timestamp of when the account was created; null if not set
 */
data class User(
    val userId: Int? = null,
    val firstName: String,
    val middleName: String? = null,
    val lastName: String,
    val email: String,
    val passwordHash: String,
    val createdAt: String? = null,
)
