package com.myiptv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.datetime.*
import kotlinx.datetime.format.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlin.time.Duration
import java.text.SimpleDateFormat
import java.util.Locale

fun parseXmltvTimeToEpoch(timeStr: String): Long {
    return try {
        // XMLTV standard format: 20260709060000 -0400
        val formatter = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val date = formatter.parse(timeStr)
        date?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}

val supabase = createSupabaseClient(
    supabaseUrl = "https://kwqbwdmmwwpufkownclf.supabase.co",
    supabaseKey = "sb_secret_fYe6ZPhFlQBOhP-RwN8uCQ_CSpmUlco"
) {
    install(Storage)
}

fun getPublicUrl(filename: String): String {
    return supabase.storage.from("Main").publicUrl(path = filename)
}

suspend fun getSignedUrl(filename : String): String {
    return supabase.storage.from("Main").createSignedUrl(
        path = filename,
        expiresIn = Duration.parse("20m") // Expires in 60 seconds
    )
}

suspend fun downloadFileToMemory(filename : String): ByteArray {
    // Fetches the file data from your bucket path into a ByteArray
    val fileBytes: ByteArray = supabase.storage
        .from("Main")
        .downloadAuthenticated(path = filename)

    return fileBytes
}

object UserLocaleDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UserLocaleDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDateTime {
        // 1. Fetch the raw JSON string
        val rawString = decoder.decodeString()

        // 2. Execute your exact parsing logic
        val instant = customFormat.parse(rawString).toInstantUsingOffset()
        val phoneZone = TimeZone.currentSystemDefault()

        return instant.toLocalDateTime(phoneZone)
    }

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        // Convert the local time to an Instant using the current system offset to save it back
        val currentOffset = TimeZone.currentSystemDefault().offsetAt(value.toInstant(TimeZone.UTC))
        val components = DateTimeComponents.Format { }.parse("") // Creates an empty DateTimeComponents instance
            .apply {
                setDateTime(value)
                setOffset(currentOffset)
            }
        encoder.encodeString(customFormat.format(components))
    }
}

// Define the reusable custom formatter
val customFormat = DateTimeComponents.Format {
    year(); monthNumber(); dayOfMonth()
    hour(); minute(); second()
    chars(" ")
    offset(UtcOffset.Formats.FOUR_DIGITS)
}

// Helper function to parse your specific string directly into an Instant
fun parseToInstant(input: String): Instant {
    return customFormat.parse(input).toInstantUsingOffset()
}

fun parseToLocalDateTime(input: String): LocalDateTime {
    val instant = customFormat.parse(input).toInstantUsingOffset()
    val phoneZone = TimeZone.currentSystemDefault() // Automatically detects device timezone
    return instant.toLocalDateTime(phoneZone)
}

fun testCompare() {
    val dateStr1 = "20260706030000 -0400"
    val dateStr2 = "20260706090000 +0100" // 1 hour later than dateStr1

    val instant1 = parseToInstant(dateStr1)
    val instant2 = parseToInstant(dateStr2)

    // 1. COMPARE TWO DATES
    // Instants are standardized to UTC, making chronological comparisons simple
    if (instant1 < instant2) {
        println("Date 1 happens before Date 2")
    } else if (instant1 > instant2) {
        println("Date 1 happens after Date 2")
    } else {
        println("Both dates represent the exact same moment")
    }

    // 2. CONVERT TO USER'S LOCAL PHONE TIME
    val phoneZone = TimeZone.currentSystemDefault() // Automatically detects device timezone
    val localDateTime: LocalDateTime = instant1.toLocalDateTime(phoneZone)

    println("User's Local Phone Time: $localDateTime")
}

data class Category(
    @JsonProperty("category_name") val category_name: String,
    @JsonProperty("category_id") val category_id: String,
    @JsonProperty("parent_id") val parent_id: Int,
    @JsonProperty("category_channels") val channels: List<Channel>,

)
data class Channel(
    @JsonProperty("num") val num: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("stream_type") val stream_type: String,
    @JsonProperty("stream_id") val stream_id: Int,
    @JsonProperty("stream_icon") val streamIcon: String,
    @JsonProperty("epg_channel_id") val epg_channel_id: String,
    @JsonProperty("added") val added: String,
    @JsonProperty("category_id") val category_id: String,
    @JsonProperty("custom_sid") val custom_sid: String,
    @JsonProperty("tv_archive") val tv_archive: Int,
    @JsonProperty("direct_source") val direct_source: String,
    @JsonProperty("tv_archive_duration") val tv_archive_duration: Int,
    @JsonProperty("stream_url") val streamUrl: String,
    @JsonProperty("epg") val epg: List<EPG>,

    )

data class EPG(
    @JsonProperty("title") val title: String,
    @JsonProperty("desc") val desc: String,
    @JsonProperty("start_time") val startTime: String,
    @JsonProperty("stop_time") val stopTime: String,

    )


class MyLiveTVProvider : MainAPI() { // All providers must be an instance of MainAPI
    override var mainUrl = "https://kwqbwdmmwwpufkownclf.supabase.co/"
    override var name = "IPTV Provider"
    override val supportedTypes = setOf(TvType.Live)

    private val jsonCatalogUrl = getPublicUrl("myCategories.json")
    private val jsonEpgUrl = getPublicUrl("epg.xml")

    override var lang = "en"

    // Enable this when your provider has a main page
    override val hasMainPage = true

    // Memory cache for the parsed categories
    private var cachedCategories: List<Category>? = null

    private suspend fun getCategories(): List<Category> {
        // If we already downloaded it, return the cache
        cachedCategories?.let { return it }

        // Otherwise, fetch and cache it
        val jsonRaw = app.get(jsonCatalogUrl).text
        val parsed = parseJson<List<Category>>(jsonRaw)
        cachedCategories = parsed
        return parsed
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {

        val categories = getCategories()

        // Map every JSON category entry to a dedicated horizontal shelf row
        val homePageLists = categories.map { group ->
            val searchResponses = group.channels.map { channel ->
                // Use TvType.Live here
                newLiveSearchResponse(channel.name, channel.streamUrl, TvType.Live) {
                    this.posterUrl = channel.streamIcon
                }
            }
            HomePageList(group.category_name, searchResponses)
        }

        return newHomePageResponse(homePageLists, hasNext = false)
    }

    // This function gets called when you search for something
    // The search function takes a 'query' string (whatever the user typed in the search bar)
    override suspend fun search(query: String): List<SearchResponse> {
        val categories = getCategories() // Uses cache if available

        return categories
            .flatMap { it.channels }
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { stream ->
                newLiveSearchResponse(stream.name, stream.streamUrl, TvType.Live) {
                    this.posterUrl = stream.streamIcon
                }
            }
    }

    override suspend fun load(url: String): LoadResponse? {
        // Fetch current EPG track data matching this stream if available
        val currentEpgText = try {
            val epgRaw = app.get(jsonEpgUrl).text
            val epgList = parseJson<List<EPG>>(epgRaw)

            // Find the active channel inside our catalog to grab its epgId
            val flatChannels = getCategories().flatMap { it.channels }
            val matchingChannel = flatChannels.find { it.streamUrl == url }
            val epgMatch = matchingChannel?.epg

            if (epgMatch != null) {
                // Get current system time in absolute milliseconds
                val nowMs = System.currentTimeMillis()

                // Filter down to the schedule for this channel
                val channelSchedule = epgMatch

                // 1. Find the program running RIGHT NOW
                val liveNow = channelSchedule.find { program ->
                    val startMs = parseXmltvTimeToEpoch(program.startTime)
                    val stopMs = parseXmltvTimeToEpoch(program.stopTime)
                    nowMs >= startMs && nowMs < stopMs
                }

                // 2. Find the program starting NEXT
                val upNext = liveNow?.let { currentShow ->
                    val currentShowStopMs = parseXmltvTimeToEpoch(currentShow.stopTime)

                    channelSchedule
                        .filter { program ->
                            parseXmltvTimeToEpoch(program.startTime) >= currentShowStopMs
                        }
                        .minByOrNull { parseXmltvTimeToEpoch(it.startTime) }
                }

                // 3. Construct your UI string layout
                buildString {
                    if (liveNow != null) {
                        appendLine("🔴 LIVE NOW: ${liveNow.title}")
                        if (!liveNow.desc.isNullOrBlank()) {
                            appendLine(liveNow.desc)
                        }
                    } else {
                        appendLine("🔴 LIVE NOW: Off-Air / No Schedule")
                    }

                    appendLine() // Visual separator space

                    if (upNext != null) {
                        appendLine("⏳ COMING UP NEXT: ${upNext.title}")
                    } else {
                        appendLine("⏳ COMING UP NEXT: Schedule Ends")
                    }
                }
            } else {
                "Live stream feed description unavailable."
            }
        } catch (e: Exception) {
            "Error rendering live EPG data window."
        }

        return newMovieLoadResponse(
            name = "Live Feed",
            url = url,
            type = TvType.Live,
            dataUrl = url
        ) {
            this.plot = currentEpgText
        }
    }
}