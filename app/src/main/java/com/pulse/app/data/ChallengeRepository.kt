package com.pulse.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import com.pulse.app.core.*
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class ChallengeRepository(context: Context) {
    private val store = context.pulseStore
    private val key = stringPreferencesKey("challenges_v1")
    val challenges = store.data.map { ChallengeCodec.decode(it[key] ?: "[]") }
    // DataStore serializes definition, events, completion and reward acknowledgement together.
    suspend fun update(transform: (List<PersonalChallenge>) -> List<PersonalChallenge>) {
        store.edit { it[key] = ChallengeCodec.encode(transform(ChallengeCodec.decode(it[key] ?: "[]"))) }
    }
    suspend fun create(c: PersonalChallenge) { require(c.validation() == null); update { list -> require(list.none { it.id == c.id }); list + c } }
    suspend fun evaluate(snapshot: HealthSnapshot, today: LocalDate) = update { it.map { c -> ChallengeEvaluator.evaluate(c, snapshot, today) } }
    suspend fun add(id: String, amount: Double, today: LocalDate, snapshot: HealthSnapshot) {
        require(amount.isFinite() && amount > 0)
        update { list -> list.map { c ->
            if(c.id != id) c else {
                require(c.tracking != ChallengeTracking.AUTOMATIC && c.completed == null && c.status == ChallengeStatus.ACTIVE && today in c.start..c.end)
                ChallengeEvaluator.evaluate(c.copy(events = c.events + ChallengeEvent(date=today, amount=if(c.tracking == ChallengeTracking.CHECKBOX) 1.0 else amount)), snapshot, today)
            }
        } }
    }
    suspend fun undo(id: String, snapshot: HealthSnapshot, today: LocalDate) = update { list -> list.map { c ->
        // Earned rewards are permanent. Undo is available before completion only.
        if(c.id == id && c.completed == null && c.status == ChallengeStatus.ACTIVE && c.tracking != ChallengeTracking.AUTOMATIC)
            ChallengeEvaluator.evaluate(c.copy(events=c.events.dropLast(1)),snapshot,today) else c
    } }
}

object ChallengeCodec {
    fun encode(items: List<PersonalChallenge>): String = JSONArray().apply { items.forEach { c -> put(JSONObject().apply {
        put("version",1); put("id",c.id); put("name",c.name); put("description",c.description); put("category",c.category); put("icon",c.icon)
        put("tracking",c.tracking.name); put("metric",c.metric.name); put("rule",c.rule.name); put("target",c.target); put("unit",c.unit)
        put("duration",c.duration); put("required",c.requiredDays); put("start",c.start.toString()); put("zone",c.zone); put("source",c.source.name)
        put("status",c.status.name); put("progress",c.progress); put("completed",c.completed?.toString()); put("ack",c.rewardAcknowledged)
        put("events",JSONArray().apply { c.events.forEach { e -> put(JSONObject().put("id",e.id).put("date",e.date.toString()).put("amount",e.amount)) } })
    }) } }.toString()
    fun decode(raw: String): List<PersonalChallenge> {
        val a=JSONArray(raw)
        return (0 until a.length()).map { i -> val o=a.getJSONObject(i); val events=o.getJSONArray("events")
            require(o.getInt("version")==1)
            PersonalChallenge(id=o.getString("id"),name=o.getString("name"),description=o.getString("description"),category=o.getString("category"),icon=o.getString("icon"),
                tracking=ChallengeTracking.valueOf(o.getString("tracking")),metric=Metric.valueOf(o.getString("metric")),rule=ChallengeRule.valueOf(o.getString("rule")),target=o.getDouble("target"),unit=o.getString("unit"),
                duration=o.getInt("duration"),requiredDays=o.getInt("required"),start=LocalDate.parse(o.getString("start")),zone=o.getString("zone"),source=DataMode.valueOf(o.getString("source")),
                events=(0 until events.length()).map { j -> val e=events.getJSONObject(j); ChallengeEvent(e.getString("id"),LocalDate.parse(e.getString("date")),e.getDouble("amount")) },
                status=ChallengeStatus.valueOf(o.getString("status")),progress=o.getDouble("progress"),completed=if(o.isNull("completed")) null else LocalDate.parse(o.getString("completed")),rewardAcknowledged=o.getBoolean("ack"))
        }
    }
}
