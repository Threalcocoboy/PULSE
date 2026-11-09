package com.pulse.app.integration.garmin

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.net.URL
import java.time.Instant
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

enum class GarminError { KEY_REQUIRED, AUTHORIZATION, RATE_LIMIT, NETWORK, MALFORMED, UNSUPPORTED, SERVER }
class GarminFailure(val code: GarminError,val retryAfterSeconds: Long?=null): Exception(when(code) {
    GarminError.KEY_REQUIRED->"Import a Garmin Personal API key to connect."
    GarminError.AUTHORIZATION->"Garmin rejected the credential. Replace an invalid, expired or revoked key."
    GarminError.RATE_LIMIT->"Garmin rate limit reached. Wait before refreshing again."
    GarminError.NETWORK->"Garmin could not be reached. Check the connection and retry."
    GarminError.MALFORMED->"Garmin returned an unexpected response. Cached data is retained."
    GarminError.UNSUPPORTED->"This data endpoint is not available for this account."
    GarminError.SERVER->"Garmin is temporarily unavailable. Try again later."
})
class GarminResponse(val status: Int,val body: String="",val retryAfter: Long?=null) {
    override fun toString()="GarminResponse(status=$status, body=[redacted])"
}
fun interface GarminTransport { suspend fun send(method: String,path: String,authorization: String,accept: String): GarminResponse }
class OfficialGarminTransport : GarminTransport {
    override suspend fun send(method: String,path: String,authorization: String,accept: String): GarminResponse = withContext(Dispatchers.IO) {
        require(path.startsWith("/") && !path.startsWith("//") && !path.contains(".."))
        val connection=URL("https://personal.connectapi.garmin.com$path").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod=method;connection.instanceFollowRedirects=false
            connection.connectTimeout=15000;connection.readTimeout=20000
            connection.setRequestProperty("Authorization",authorization);connection.setRequestProperty("Accept",accept)
            if(method=="POST") {connection.doOutput=true;connection.outputStream.use { }}
            val status=connection.responseCode
            val body=if(status in 200..299)connection.inputStream.use {input->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){ensureActive();val count=input.read(buffer);if(count<0)break;if(output.size()+count>8_000_000)throw GarminFailure(GarminError.MALFORMED);output.write(buffer,0,count)}
                output.toByteArray().toString(Charsets.UTF_8)
            }else ""
            GarminResponse(status,body,connection.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(1,86400))
        } catch(e: CancellationException){throw e} catch(e: GarminFailure){throw e}
        catch(e: Exception){throw GarminFailure(GarminError.NETWORK)}finally{connection.disconnect()}
    }
}
class GarminToken(val value: String,val expires: Instant) {
    fun usable(now: Instant)=now.isBefore(expires.minusSeconds(60))
    override fun toString()="GarminToken([redacted])"
}
class GarminApi(private val transport: GarminTransport,private val key:()->String?,private val clock:()->Instant={Instant.now()}) {
    private var token: GarminToken?=null
    private var retryUntil: Instant?=null
    private val mutex=Mutex()
    suspend fun validateKey(candidate:String){GarminApi(transport,{candidate},clock).authorize()}
    fun reset(){token=null;retryUntil=null}
    suspend fun json(path: String): Any = mutex.withLock {
        retryUntil?.let {if(clock().isBefore(it))throw GarminFailure(GarminError.RATE_LIMIT,java.time.Duration.between(clock(),it).seconds)}
        var bearer=authorize()
        var result=transport.send("GET",path,"Bearer ${bearer.value}","application/json")
        if(result.status==401) {
            token=null;bearer=authorize();result=transport.send("GET",path,"Bearer ${bearer.value}","application/json")
        }
        check(result)
        try {val parsed=JSONTokener(result.body).nextValue();if(parsed !is JSONObject && parsed !is JSONArray)throw GarminFailure(GarminError.MALFORMED);parsed}
        catch(e: GarminFailure){throw e}catch(e: Exception){throw GarminFailure(GarminError.MALFORMED)}
    }
    private suspend fun authorize(): GarminToken {
        token?.takeIf {it.usable(clock())}?.let{return it}
        val secret=key()?.takeIf {it.isNotBlank()} ?: throw GarminFailure(GarminError.KEY_REQUIRED)
        val result=transport.send("POST","/personalapitoken-service/personal-api/personal-api-key/token","ApiKey $secret","*/*")
        check(result)
        try {
            // Official endpoint returns a raw JWT, not an invented JSON token envelope.
            val raw=result.body.trim();val parts=raw.split('.');require(parts.size==3)
            val claims=JSONObject(String(Base64.getUrlDecoder().decode(parts[1]),Charsets.UTF_8))
            val exp=Instant.ofEpochSecond(claims.getLong("exp"));require(exp.isAfter(clock().plusSeconds(60)))
            return GarminToken(raw,minOf(exp,clock().plusSeconds(86400))).also {token=it}
        } catch(e: Exception){throw GarminFailure(GarminError.MALFORMED)}
    }
    private fun check(response: GarminResponse) {
        if(response.status in 200..299)return
        val code=when(response.status){401,403->GarminError.AUTHORIZATION;404->GarminError.UNSUPPORTED;429->GarminError.RATE_LIMIT;else->GarminError.SERVER}
        if(code==GarminError.RATE_LIMIT)retryUntil=clock().plusSeconds(response.retryAfter ?: 60)
        throw GarminFailure(code,response.retryAfter)
    }
}
