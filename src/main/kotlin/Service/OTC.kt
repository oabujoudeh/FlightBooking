package com.flightbooking

import java.time.LocalDateTime
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

object OTC {
    private val otcStorage = ConcurrentHashMap<String, OtcEntry>()

    data class OtcEntry(
        val code: String,
        val expiryTime: LocalDateTime,
    )

    /**
    * Makes a 6-digit code, saves it, and returns it.
    *
    * The code is stored for the given email and expires after 5 minutes.
    *
    * @param email the email linked to the code
    * @return the generated code
    */
    fun generateAndSave(email: String): String {
        // generate a random code of 6 digits
        val code = String.format("%06d", Random().nextInt(1000000))
        // valide for 5 mins
        val expiryTime = LocalDateTime.now().plusMinutes(5)
        // save to storage
        otcStorage[email] = OtcEntry(code, expiryTime)
        return code
    }


    /**
    * Checks if a one-time code is correct for an email.
    *
    * It also removes the code if it has expired or if it was used correctly.
    *
    * @param email the email linked to the code
    * @param inputCode the code entered by the user
    * @return true if the code is correct, otherwise false
    */
    fun verify(email: String, inputCode:String):Boolean{
        val entry = otcStorage[email] ?: return false

        // check if the time is expired, if expired remove the entrey and return false
        if (LocalDateTime.now().isAfter(entry.expiryTime)) {
            otcStorage.remove(email)
            return false
        }

        // check the validation of the code, if valid return true
        if (entry.code == inputCode) {
            otcStorage.remove(email)
            return true
        } else {
            return false
        }
    }
}
