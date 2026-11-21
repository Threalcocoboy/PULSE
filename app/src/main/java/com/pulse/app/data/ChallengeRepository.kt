package com.pulse.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import com.pulse.app.core.*
import com.pulse.app.core.Preferences
import com.pulse.app.integration.googlehealth.GoogleHealthCipher
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Base64

class ChallengeRepository(context: Context) {
    private val store = context.pulseStore
    private val key = stringPreferencesKey("challenges_v1")
    private val googleKey = stringPreferencesKey("google_health_challenges_v1")
    private val cipher = GoogleHealthCipher("pulse.google.health.challenges.v1")
    private val encryptionContext = "google_health_challenges_v1".toByteArray()
    val challenges = store.data.map(::decode)

    private fun decode(prefs: androidx.datastore.preferences.core.Preferences): List<PersonalChallenge> {
        val existing = ChallengeCodec.decode(prefs[key] ?: "[]")
        val encrypted = prefs[googleKey] ?: return existing
        val bytes = cipher.decrypt(Base64.getDecoder().decode(encrypted), encryptionContext)
        return existing + ChallengeCodec.decode(String(bytes, Charsets.UTF_8))
    }
    // DataStore serializes definition, events, completion and reward acknowledgement together.
    suspend fun update(transform: (List<PersonalChallenge>) -> List<PersonalChallenge>) {
        store.edit { prefs ->
            val old = decode(prefs)
            val updated = transform(old)
            if (updated == old) return@edit
            val (google, existing) = updated.partition { it.source == DataMode.GOOGLE_HEALTH }
            prefs[key] = ChallengeCodec.encode(existing)
            if (google.isEmpty()) prefs.remove(googleKey) else {
                val data = ChallengeCodec.encode(google).toByteArray(Charsets.UTF_8)
                prefs[googleKey] = Base64.getEncoder().encodeToString(cipher.encrypt(data, encryptionContext))
            }
        }
    }
    internal fun clearGoogleKey() = cipher.deleteKey()
    internal suspend fun forgetGoogleProgress(scope: String) = update { list ->
        list.map { challenge ->
            if (challenge.sourceScope == scope && challenge.tracking.automatic && challenge.completed == null &&
                challenge.status in setOf(ChallengeStatus.ACTIVE, ChallengeStatus.AWAITING_DATA)) {
                challenge.copy(progress = 0.0, status = ChallengeStatus.AWAITING_DATA)
            } else challenge
        }
    }
    suspend fun create(c: PersonalChallenge) { require(c.validation() == null); update { list -> require(list.none { it.id == c.id }); list + c } }
    suspend fun evaluate(snapshot: HealthSnapshot, today: LocalDate, preferences: Preferences = Preferences()) =
        update { it.map { c -> ChallengeEvaluator.evaluate(c, snapshot, today, preferences) } }
    suspend fun add(id: String, amount: Double, today: LocalDate, snapshot: HealthSnapshot) {
        require(amount.isFinite() && amount > 0)
        update { list -> list.map { c ->
            if(c.id != id) c else {
                require(!c.tracking.automatic && c.completed == null && c.status == ChallengeStatus.ACTIVE && today in c.start..c.end)
                ChallengeEvaluator.evaluate(c.copy(events = c.events + ChallengeEvent(date=today, amount=if(c.tracking == ChallengeTracking.CHECKBOX) 1.0 else amount)), snapshot, today)
            }
        } }
    }
    suspend fun undo(id: String, snapshot: HealthSnapshot, today: LocalDate) = update { list -> list.map { c ->
        // Earned rewards are permanent. Undo is available before completion only.
        if(c.id == id && c.completed == null && c.status == ChallengeStatus.ACTIVE && !c.tracking.automatic)
            ChallengeEvaluator.evaluate(c.copy(events=c.events.dropLast(1)),snapshot,today) else c
    } }
}

object ChallengeCodec {
    fun encode(items: List<PersonalChallenge>): String = JSONArray().apply { items.forEach { c -> put(JSONObject().apply {
        put("version",3); put("id",c.id); put("name",c.name); put("description",c.description); put("category",c.category); put("icon",c.icon)
        put("tracking",c.tracking.name); put("metric",c.metric.name); put("rule",c.rule.name); put("target",c.target); put("unit",c.unit)
        put("duration",c.duration); put("required",c.requiredDays); put("start",c.start.toString()); put("zone",c.zone); put("source",c.source.name)
        put("status",c.status.name); put("progress",c.progress); put("completed",c.completed?.toString()); put("ack",c.rewardAcknowledged)
        put("measure",c.measure?.name); put("condition",c.condition.name); put("qualifier",c.qualifier)
        put("time",c.timeMinutes); put("tolerance",c.toleranceMinutes); put("rangeMin",c.rangeMin); put("rangeMax",c.rangeMax)
        put("goals",JSONArray(c.goals.map { it.name })); put("goalMatch",c.goalMatch.name); put("sourceScope",c.sourceScope)
        put("goalIds", JSONArray(c.goalIds.toList())); put("goalCadence", c.goalCadence?.name); put("goalTitles", JSONArray(c.goalTitles))
        put("events",JSONArray().apply { c.events.forEach { e -> put(JSONObject().put("id",e.id).put("date",e.date.toString()).put("amount",e.amount)) } })
    }) } }.toString()
    fun decode(raw: String): List<PersonalChallenge> {
        val a=JSONArray(raw)
        return (0 until a.length()).map { i -> val o=a.getJSONObject(i); val events=o.getJSONArray("events")
            require(o.getInt("version") in 1..3)
            fun string(key: String): String? = if (o.isNull(key)) null else o.getString(key)
            val goals = o.optJSONArray("goals")
            fun strings(key: String): List<String> = o.optJSONArray(key)?.let { array -> (0 until array.length()).map(array::getString) }.orEmpty()
            PersonalChallenge(id=o.getString("id"),name=o.getString("name"),description=o.getString("description"),category=o.getString("category"),icon=o.getString("icon"),
                tracking=ChallengeTracking.valueOf(o.getString("tracking")),metric=Metric.valueOf(o.getString("metric")),rule=ChallengeRule.valueOf(o.getString("rule")),target=o.getDouble("target"),unit=o.getString("unit"),
                duration=o.getInt("duration"),requiredDays=o.getInt("required"),start=LocalDate.parse(o.getString("start")),zone=o.getString("zone"),source=DataMode.fromStored(o.getString("source")),
                events=(0 until events.length()).map { j -> val e=events.getJSONObject(j); ChallengeEvent(e.getString("id"),LocalDate.parse(e.getString("date")),e.getDouble("amount")) },
                status=ChallengeStatus.valueOf(o.getString("status")),progress=o.getDouble("progress"),completed=if(o.isNull("completed")) null else LocalDate.parse(o.getString("completed")),rewardAcknowledged=o.getBoolean("ack"),
                measure=string("measure")?.let(ChallengeMeasure::valueOf),
                condition=string("condition")?.let(ChallengeCondition::valueOf) ?: ChallengeCondition.AT_LEAST,
                qualifier=string("qualifier"), timeMinutes=if(o.isNull("time")) null else o.getInt("time"),
                toleranceMinutes=o.optInt("tolerance",30), rangeMin=if(o.isNull("rangeMin")) null else o.getDouble("rangeMin"),
                rangeMax=if(o.isNull("rangeMax")) null else o.getDouble("rangeMax"),
                goals=if(goals==null) emptySet() else (0 until goals.length()).map { Metric.valueOf(goals.getString(it)) }.toSet(),
                goalMatch=string("goalMatch")?.let(GoalMatch::valueOf) ?: GoalMatch.ALL, sourceScope=string("sourceScope"),
                goalIds=strings("goalIds").toSet(), goalCadence=string("goalCadence")?.let(GoalCadence::valueOf), goalTitles=strings("goalTitles"))
        }
    }
}
