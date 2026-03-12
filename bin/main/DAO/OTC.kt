package com.flightbooking
import java.time.LocalDateTime
import java.time.Duration
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

object Otc{
    private val otcStorage = ConcurrentHashMap<String, OtcEntry>()
    data class OtcEntry(val code:String, val expiryTime: LocalDateTime)

    fun generateAndSave(email:String):String{
        // generate a random code of 6 digits
        val code = String.format("%06d", Random().nextInt(1000000))
        // valide for 5 mins
        val expiryTime = LocalDateTime.now().plusMinutes(5)
        // save to storage
        otcStorage[email] = OtcEntry(code, expiryTime)
        return code
    }

    fun verify(email: String, inputCode:String):Boolean{
        val entry = otcStorage[email] ?: return false

        // check if the time is expired, if expired remove the entrey and return false
        if(LocalDateTime.now().isAfter(entry.expiryTime)){ 
            otcStorage.remove(email)
            return false
        }

        // check the validation of the code, if valid return true
        if(entry.code == inputCode){
            otcStorage.remove(email)
            return true
        }
        else{
            return false
        }
        return false
   }
}