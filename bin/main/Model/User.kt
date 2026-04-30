package com.flightbooking

data class User(
    val userId: Int? = null,
    val firstName: String,
    val middleName: String? = null,
    val lastName: String,
    val email: String,
    val passwordHash: String,
    val createdAt: String? = null,
)
