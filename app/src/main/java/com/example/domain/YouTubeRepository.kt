package com.example.domain

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class YouTubeTrack(
    val videoId: String,
    val title: String,
    val author: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val channelId: String = "",
    val viewCountText: String = "",
    val channelAvatarUrl: String = ""
)

data class YouTubeArtistChannel(
    val id: String,
    val name: String,
    val handle: String = "",
    val subscribers: String = "",
    val avatarUrl: String,
    val bannerUrl: String = "",
    val bio: String = "",
    val topTracks: List<YouTubeTrack> = emptyList(),
    val albums: List<String> = emptyList()
)

class YouTubeRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Curated top global artists with official avatar images & top discography
    val curatedArtists = listOf(
        YouTubeArtistChannel(
            id = "UCiT_U4C_zXU3Xv8BfL6-Bdg",
            name = "OneRepublic",
            handle = "@OneRepublic",
            subscribers = "34.2M Subscribers",
            avatarUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400&auto=format&fit=crop&q=80",
            bannerUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1200&auto=format&fit=crop&q=80",
            bio = "Grammy-nominated American pop rock band formed in Colorado Springs. Known for iconic hits worldwide.",
            topTracks = listOf(
                YouTubeTrack("hT_nvWreIhg", "Counting Stars", "OneRepublic", 257L, "https://img.youtube.com/vi/hT_nvWreIhg/hqdefault.jpg", viewCountText = "3.8B views"),
                YouTubeTrack("mNEUkkoUoIA", "I Ain't Worried (Top Gun: Maverick)", "OneRepublic", 148L, "https://img.youtube.com/vi/mNEUkkoUoIA/hqdefault.jpg", viewCountText = "420M views"),
                YouTubeTrack("qHm9MG9xw64", "Secrets", "OneRepublic", 228L, "https://img.youtube.com/vi/qHm9MG9xw64/hqdefault.jpg", viewCountText = "280M views"),
                YouTubeTrack("z0rxydSolwU", "Apologize", "OneRepublic ft. Timbaland", 208L, "https://img.youtube.com/vi/z0rxydSolwU/hqdefault.jpg", viewCountText = "600M views"),
                YouTubeTrack("Gg3pZpGv1zE", "Sunshine", "OneRepublic", 163L, "https://img.youtube.com/vi/Gg3pZpGv1zE/hqdefault.jpg", viewCountText = "85M views"),
                YouTubeTrack("12cehkWZ0DY", "Run", "OneRepublic", 168L, "https://img.youtube.com/vi/12cehkWZ0DY/hqdefault.jpg", viewCountText = "95M views")
            ),
            albums = listOf("Native (2013)", "Human (2021)", "Waking Up (2009)", "Artificial Paradise (2024)")
        ),
        YouTubeArtistChannel(
            id = "UC0C-w0YjGpqDXGB8Tr06V5A",
            name = "Ed Sheeran",
            handle = "@EdSheeran",
            subscribers = "54.8M Subscribers",
            avatarUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=400&auto=format&fit=crop&q=80",
            bannerUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=1200&auto=format&fit=crop&q=80",
            bio = "English singer-songwriter who has sold more than 150 million records worldwide.",
            topTracks = listOf(
                YouTubeTrack("JGwWNGJdvx8", "Shape of You", "Ed Sheeran", 264L, "https://img.youtube.com/vi/JGwWNGJdvx8/hqdefault.jpg", viewCountText = "6.1B views"),
                YouTubeTrack("2Vv-BfVoq4g", "Perfect", "Ed Sheeran", 263L, "https://img.youtube.com/vi/2Vv-BfVoq4g/hqdefault.jpg", viewCountText = "3.5B views"),
                YouTubeTrack("orJSJGHjBLI", "Bad Habits", "Ed Sheeran", 231L, "https://img.youtube.com/vi/orJSJGHjBLI/hqdefault.jpg", viewCountText = "580M views"),
                YouTubeTrack("Il0S8BoucSA", "Shivers", "Ed Sheeran", 207L, "https://img.youtube.com/vi/Il0S8BoucSA/hqdefault.jpg", viewCountText = "320M views"),
                YouTubeTrack("lp-EO5I60KA", "Thinking Out Loud", "Ed Sheeran", 297L, "https://img.youtube.com/vi/lp-EO5I60KA/hqdefault.jpg", viewCountText = "3.7B views"),
                YouTubeTrack("K0ibBPhiaG0", "Castle On The Hill", "Ed Sheeran", 261L, "https://img.youtube.com/vi/K0ibBPhiaG0/hqdefault.jpg", viewCountText = "540M views")
            ),
            albums = listOf("÷ Divide (2017)", "= Equals (2021)", "- Subtract (2023)", "x Multiply (2014)")
        ),
        YouTubeArtistChannel(
            id = "UCb2HGwORbGhT0So00W9YkXg",
            name = "The Weeknd",
            handle = "@TheWeeknd",
            subscribers = "35.1M Subscribers",
            avatarUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=400&auto=format&fit=crop&q=80",
            bannerUrl = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=1200&auto=format&fit=crop&q=80",
            bio = "Canadian singer, songwriter, and record producer known for sonic versatility and dark lyricism.",
            topTracks = listOf(
                YouTubeTrack("4NRXx6U8ABQ", "Blinding Lights", "The Weeknd", 200L, "https://img.youtube.com/vi/4NRXx6U8ABQ/hqdefault.jpg", viewCountText = "4.2B views"),
                YouTubeTrack("XXYlFuWEuKi", "Save Your Tears", "The Weeknd", 216L, "https://img.youtube.com/vi/XXYlFuWEuKi/hqdefault.jpg", viewCountText = "1.6B views"),
                YouTubeTrack("34Na4j8AVgA", "Starboy ft. Daft Punk", "The Weeknd", 230L, "https://img.youtube.com/vi/34Na4j8AVgA/hqdefault.jpg", viewCountText = "2.4B views"),
                YouTubeTrack("dqt8Z1k0oWQ", "Die For You", "The Weeknd", 234L, "https://img.youtube.com/vi/dqt8Z1k0oWQ/hqdefault.jpg", viewCountText = "350M views"),
                YouTubeTrack("KEI4qS4P-uY", "The Hills", "The Weeknd", 242L, "https://img.youtube.com/vi/KEI4qS4P-uY/hqdefault.jpg", viewCountText = "2.0B views")
            ),
            albums = listOf("After Hours (2020)", "Dawn FM (2022)", "Starboy (2016)", "Beauty Behind the Madness (2015)")
        ),
        YouTubeArtistChannel(
            id = "UCqECaJ8Gagnn7YCbPEzWH6g",
            name = "Taylor Swift",
            handle = "@TaylorSwift",
            subscribers = "59.4M Subscribers",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&auto=format&fit=crop&q=80",
            bannerUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=1200&auto=format&fit=crop&q=80",
            bio = "American singer-songwriter whose narrative songwriting has achieved worldwide critical acclaim.",
            topTracks = listOf(
                YouTubeTrack("ic8j13piAhQ", "Cruel Summer", "Taylor Swift", 218L, "https://img.youtube.com/vi/ic8j13piAhQ/hqdefault.jpg", viewCountText = "850M views"),
                YouTubeTrack("b1kbLwvqugk", "Anti-Hero", "Taylor Swift", 201L, "https://img.youtube.com/vi/b1kbLwvqugk/hqdefault.jpg", viewCountText = "230M views"),
                YouTubeTrack("e-ORhEE9VVg", "Blank Space", "Taylor Swift", 232L, "https://img.youtube.com/vi/e-ORhEE9VVg/hqdefault.jpg", viewCountText = "3.4B views"),
                YouTubeTrack("nfWlot6h_JM", "Shake It Off", "Taylor Swift", 242L, "https://img.youtube.com/vi/nfWlot6h_JM/hqdefault.jpg", viewCountText = "3.3B views"),
                YouTubeTrack("8xg3vE8Ie_E", "Love Story (Taylor's Version)", "Taylor Swift", 236L, "https://img.youtube.com/vi/8xg3vE8Ie_E/hqdefault.jpg", viewCountText = "190M views"),
                YouTubeTrack("-CmadmM5cOk", "Style", "Taylor Swift", 231L, "https://img.youtube.com/vi/-CmadmM5cOk/hqdefault.jpg", viewCountText = "820M views")
            ),
            albums = listOf("1989 (Taylor's Version)", "Midnights (2022)", "Lover (2019)", "Folklore (2020)")
        ),
        YouTubeArtistChannel(
            id = "UCwhkR4I_gqXgIq7p0u9-iJw",
            name = "Imagine Dragons",
            handle = "@ImagineDragons",
            subscribers = "31.6M Subscribers",
            avatarUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=400&auto=format&fit=crop&q=80",
            bannerUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1200&auto=format&fit=crop&q=80",
            bio = "American pop rock band from Las Vegas, consisting of lead singer Dan Reynolds, guitarist Wayne Sermon, and bassist Ben McKee.",
            topTracks = listOf(
                YouTubeTrack("7wtfhZwyrcc", "Believer", "Imagine Dragons", 204L, "https://img.youtube.com/vi/7wtfhZwyrcc/hqdefault.jpg", viewCountText = "2.6B views"),
                YouTubeTrack("fKopy74weus", "Thunder", "Imagine Dragons", 187L, "https://img.youtube.com/vi/fKopy74weus/hqdefault.jpg", viewCountText = "2.1B views"),
                YouTubeTrack("ktvTqknDobU", "Radioactive", "Imagine Dragons", 188L, "https://img.youtube.com/vi/ktvTqknDobU/hqdefault.jpg", viewCountText = "1.7B views"),
                YouTubeTrack("mWRsgZuwf_8", "Demons", "Imagine Dragons", 177L, "https://img.youtube.com/vi/mWRsgZuwf_8/hqdefault.jpg", viewCountText = "1.2B views"),
                YouTubeTrack("TO-_3tck2tg", "Bones", "Imagine Dragons", 165L, "https://img.youtube.com/vi/TO-_3tck2tg/hqdefault.jpg", viewCountText = "490M views"),
                YouTubeTrack("D9G1VoFL46I", "Enemy (from Arcane)", "Imagine Dragons X J.I.D", 173L, "https://img.youtube.com/vi/D9G1VoFL46I/hqdefault.jpg", viewCountText = "410M views")
            ),
            albums = listOf("Evolve (2017)", "Night Visions (2012)", "Mercury – Acts 1 & 2 (2022)", "Smoke + Mirrors (2015)")
        ),
        YouTubeArtistChannel(
            id = "UC2XdaAVUannp6vAgqcauZka",
            name = "Coldplay",
            handle = "@Coldplay",
            subscribers = "27.4M Subscribers",
            avatarUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=400&auto=format&fit=crop&q=80",
            bannerUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=1200&auto=format&fit=crop&q=80",
            bio = "British rock band formed in London in 1997. One of the best-selling music acts in history.",
            topTracks = listOf(
                YouTubeTrack("FM7MFYoylPA", "Something Just Like This", "The Chainsmokers & Coldplay", 247L, "https://img.youtube.com/vi/FM7MFYoylPA/hqdefault.jpg", viewCountText = "2.2B views"),
                YouTubeTrack("3YqPKLZF_WU", "My Universe", "Coldplay X BTS", 228L, "https://img.youtube.com/vi/3YqPKLZF_WU/hqdefault.jpg", viewCountText = "310M views"),
                YouTubeTrack("yKNxeF4KMsY", "Yellow", "Coldplay", 269L, "https://img.youtube.com/vi/yKNxeF4KMsY/hqdefault.jpg", viewCountText = "950M views"),
                YouTubeTrack("dvgZkm1xWPE", "Viva La Vida", "Coldplay", 242L, "https://img.youtube.com/vi/dvgZkm1xWPE/hqdefault.jpg", viewCountText = "980M views"),
                YouTubeTrack("YykjpeuMNEk", "Hymn For The Weekend", "Coldplay", 258L, "https://img.youtube.com/vi/YykjpeuMNEk/hqdefault.jpg", viewCountText = "2.0B views"),
                YouTubeTrack("1G4isv_Fylg", "Paradise", "Coldplay", 278L, "https://img.youtube.com/vi/1G4isv_Fylg/hqdefault.jpg", viewCountText = "1.8B views")
            ),
            albums = listOf("A Rush of Blood to the Head (2002)", "Viva la Vida (2008)", "Music of the Spheres (2021)", "Parachutes (2000)")
        ),
        YouTubeArtistChannel(
            id = "UC0WP5P-ufpRfjbNrmOWwLBQ",
            name = "Lofi Girl",
            handle = "@LofiGirl",
            subscribers = "14.3M Subscribers",
            avatarUrl = "https://img.youtube.com/vi/jfKfPfyJRdk/hqdefault.jpg",
            bannerUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200&auto=format&fit=crop&q=80",
            bio = "24/7 lofi hip hop beats to study/relax/work to. Peaceful soothing instrumental soundscapes.",
            topTracks = listOf(
                YouTubeTrack("jfKfPfyJRdk", "Lofi Hip Hop Radio - Beats to Relax/Study to", "Lofi Girl", 0L, "https://img.youtube.com/vi/jfKfPfyJRdk/hqdefault.jpg", viewCountText = "Live 24/7"),
                YouTubeTrack("4xDzrJKXOOY", "Synthwave Radio - Chill Beats to Relax to", "Lofi Girl", 0L, "https://img.youtube.com/vi/4xDzrJKXOOY/hqdefault.jpg", viewCountText = "Live 24/7"),
                YouTubeTrack("rUxyKA_-grg", "Peaceful Piano - Soft Beats for Sleep", "Lofi Girl", 3600L, "https://img.youtube.com/vi/rUxyKA_-grg/hqdefault.jpg", viewCountText = "45M views"),
                YouTubeTrack("5qap5aO4i9A", "Lofi Sleep Chill Beats - Restful Night", "Lofi Girl", 4200L, "https://img.youtube.com/vi/5qap5aO4i9A/hqdefault.jpg", viewCountText = "32M views")
            ),
            albums = listOf("Study Session 2024", "Morning Coffee Chill", "Late Night Vibes", "Rainy Days & Lofi")
        ),
        YouTubeArtistChannel(
            id = "UC-J-KZfRV8c13fWhhAQCdIg",
            name = "Alan Walker",
            handle = "@AlanWalker",
            subscribers = "45.7M Subscribers",
            avatarUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=400&auto=format&fit=crop&q=80",
            bannerUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=1200&auto=format&fit=crop&q=80",
            bio = "Norwegian DJ and record producer recognized for atmospheric electronic dance music.",
            topTracks = listOf(
                YouTubeTrack("60ItHLz5WEA", "Faded", "Alan Walker", 212L, "https://img.youtube.com/vi/60ItHLz5WEA/hqdefault.jpg", viewCountText = "3.6B views"),
                YouTubeTrack("1-xGerv5FOk", "Alone", "Alan Walker", 163L, "https://img.youtube.com/vi/1-xGerv5FOk/hqdefault.jpg", viewCountText = "1.4B views"),
                YouTubeTrack("ao40S_82gcw", "The Spectre", "Alan Walker", 193L, "https://img.youtube.com/vi/ao40S_82gcw/hqdefault.jpg", viewCountText = "1.1B views"),
                YouTubeTrack("M-P4Q3pkGKY", "Darkside ft. Au/Ra & Tomine Harket", "Alan Walker", 239L, "https://img.youtube.com/vi/M-P4Q3pkGKY/hqdefault.jpg", viewCountText = "720M views"),
                YouTubeTrack("dhYOPzcsbGM", "On My Way", "Alan Walker, Sabrina Carpenter & Farruko", 217L, "https://img.youtube.com/vi/dhYOPzcsbGM/hqdefault.jpg", viewCountText = "560M views")
            ),
            albums = listOf("Different World (2018)", "World of Walker (2021)", "Walkerverse Pt. I & II (2022)")
        ),
        YouTubeArtistChannel(
            id = "UCiGm_E4ZwYSHV3bcW1KEIrQ",
            name = "Billie Eilish",
            handle = "@BillieEilish",
            subscribers = "49.2M Subscribers",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&auto=format&fit=crop&q=80",
            bannerUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200&auto=format&fit=crop&q=80",
            bio = "Multiple Grammy & Academy Award-winning American singer and songwriter.",
            topTracks = listOf(
                YouTubeTrack("DyDfgMOUjCI", "bad guy", "Billie Eilish", 194L, "https://img.youtube.com/vi/DyDfgMOUjCI/hqdefault.jpg", viewCountText = "1.2B views"),
                YouTubeTrack("V1Pl8CzNzCw", "lovely (with Khalid)", "Billie Eilish, Khalid", 200L, "https://img.youtube.com/vi/V1Pl8CzNzCw/hqdefault.jpg", viewCountText = "1.8B views"),
                YouTubeTrack("viimfQi_pUw", "ocean eyes", "Billie Eilish", 196L, "https://img.youtube.com/vi/viimfQi_pUw/hqdefault.jpg", viewCountText = "600M views"),
                YouTubeTrack("d5gf9dXbPi0", "BIRDS OF A FEATHER", "Billie Eilish", 190L, "https://img.youtube.com/vi/d5gf9dXbPi0/hqdefault.jpg", viewCountText = "350M views")
            ),
            albums = listOf("HIT ME HARD AND SOFT (2024)", "Happier Than Ever (2021)", "WHEN WE ALL FALL ASLEEP (2019)")
        ),
        YouTubeArtistChannel(
            id = "UC-v0Y8yFvJ3pZpGv1zE",
            name = "Bruno Mars",
            handle = "@BrunoMars",
            subscribers = "39.8M Subscribers",
            avatarUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=400&auto=format&fit=crop&q=80",
            bannerUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=1200&auto=format&fit=crop&q=80",
            bio = "American singer-songwriter, multi-instrumentalist, and record producer celebrated for stage presence and retro showmanship.",
            topTracks = listOf(
                YouTubeTrack("OPf0YbXqDm0", "Uptown Funk ft. Bruno Mars", "Mark Ronson ft. Bruno Mars", 270L, "https://img.youtube.com/vi/OPf0YbXqDm0/hqdefault.jpg", viewCountText = "5.0B views"),
                YouTubeTrack("kPa7bsKwL-c", "Die With A Smile", "Lady Gaga & Bruno Mars", 252L, "https://img.youtube.com/vi/kPa7bsKwL-c/hqdefault.jpg", viewCountText = "400M views"),
                YouTubeTrack("UqyT8IEBbiY", "24K Magic", "Bruno Mars", 226L, "https://img.youtube.com/vi/UqyT8IEBbiY/hqdefault.jpg", viewCountText = "1.5B views"),
                YouTubeTrack("e-fA-gBCkj0", "Locked Out of Heaven", "Bruno Mars", 233L, "https://img.youtube.com/vi/e-fA-gBCkj0/hqdefault.jpg", viewCountText = "1.1B views")
            ),
            albums = listOf("24K Magic (2016)", "Unorthodox Jukebox (2012)", "Doo-Wops & Hooligans (2010)", "An Evening with Silk Sonic (2021)")
        )
    )

    // Pre-curated instant high quality tracks so the UI is visible IMMEDIATELY with zero wait time
    val curatedTrendingTracks = curatedArtists.flatMap { it.topTracks }.distinctBy { it.videoId }

    fun getInitialTracks(): List<YouTubeTrack> {
        return curatedTrendingTracks
    }

    fun getTopArtists(): List<YouTubeArtistChannel> {
        return curatedArtists
    }

    suspend fun getChannelDetails(authorOrName: String): YouTubeArtistChannel = withContext(Dispatchers.IO) {
        val cleanName = authorOrName.replace("ft.", "").replace("feat.", "").trim()
        
        // 1. Exact or partial match in curated artists
        val existing = curatedArtists.firstOrNull { 
            it.name.equals(cleanName, ignoreCase = true) ||
            cleanName.contains(it.name, ignoreCase = true) ||
            it.name.contains(cleanName, ignoreCase = true)
        }
        if (existing != null) return@withContext existing

        // 2. Fetch artist songs via Innertube search
        try {
            val query = "$cleanName songs"
            val results = searchInnertube(query)
            val top = if (results.isNotEmpty()) results.take(15) else curatedTrendingTracks.take(6)
            val thumb = top.firstOrNull()?.thumbnailUrl ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400&auto=format&fit=crop&q=80"
            
            return@withContext YouTubeArtistChannel(
                id = cleanName.hashCode().toString(),
                name = cleanName,
                handle = "@${cleanName.replace(" ", "")}",
                subscribers = "Official Artist Channel",
                avatarUrl = thumb,
                bannerUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1200&auto=format&fit=crop&q=80",
                bio = "Official music, albums, and discography from $cleanName.",
                topTracks = top,
                albums = listOf("Greatest Hits", "Popular Releases", "Singles & EPs")
            )
        } catch (_: Exception) {
            return@withContext YouTubeArtistChannel(
                id = cleanName.hashCode().toString(),
                name = cleanName,
                handle = "@${cleanName.replace(" ", "")}",
                subscribers = "YouTube Artist",
                avatarUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400&auto=format&fit=crop&q=80",
                bio = "Music and tracks by $cleanName",
                topTracks = curatedTrendingTracks.filter { it.author.contains(cleanName, ignoreCase = true) }.ifEmpty { curatedTrendingTracks.take(5) }
            )
        }
    }

    suspend fun searchTracks(query: String): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext curatedTrendingTracks

        // 1. Direct URL check: if user pasted a YouTube link or 11-char video ID
        extractVideoIdFromUrl(trimmed)?.let { videoId ->
            val track = getVideoDetails(videoId)
            return@withContext listOf(track)
        }

        // 2. Primary Search: YouTube Innertube API
        try {
            val searchResults = searchInnertube(trimmed)
            if (searchResults.isNotEmpty()) {
                return@withContext searchResults
            }
        } catch (_: Exception) {}

        // 3. Fallback: Search suggestions
        try {
            val suggestions = getSearchSuggestions(trimmed)
            if (suggestions.isNotEmpty()) {
                val fallbackResults = mutableListOf<YouTubeTrack>()
                for (s in suggestions.take(6)) {
                    val directTracks = searchInnertube(s)
                    if (directTracks.isNotEmpty()) {
                        fallbackResults.addAll(directTracks.take(3))
                    }
                }
                if (fallbackResults.isNotEmpty()) {
                    return@withContext fallbackResults.distinctBy { it.videoId }
                }
            }
        } catch (_: Exception) {}

        // 4. Local Curated Fallback filter
        val filtered = curatedTrendingTracks.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
            it.author.contains(trimmed, ignoreCase = true)
        }
        if (filtered.isNotEmpty()) {
            return@withContext filtered
        }

        curatedTrendingTracks
    }

    private fun searchInnertube(query: String): List<YouTubeTrack> {
        val jsonPayload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20240101.00.00")
                })
            })
            put("query", query)
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/search")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            return parseInnertubeResults(json)
        }
    }

    private fun parseInnertubeResults(json: JSONObject): List<YouTubeTrack> {
        val tracks = mutableListOf<YouTubeTrack>()

        fun scanForVideoRenderers(node: Any?) {
            when (node) {
                is JSONObject -> {
                    if (node.has("videoRenderer")) {
                        val vr = node.optJSONObject("videoRenderer")
                        if (vr != null) {
                            val videoId = vr.optString("videoId")
                            val title = getRunOrSimpleText(vr.optJSONObject("title")) ?: "Unknown Title"
                            val author = getRunOrSimpleText(vr.optJSONObject("ownerText") ?: vr.optJSONObject("longBylineText")) ?: "YouTube Artist"
                            val durText = vr.optJSONObject("lengthText")?.optString("simpleText", "") ?: ""
                            val durationSeconds = parseDurationToSeconds(durText)

                            val viewCount = getRunOrSimpleText(vr.optJSONObject("viewCountText")) ?: ""

                            val thumbArray = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val thumbUrl = if (thumbArray != null && thumbArray.length() > 0) {
                                thumbArray.optJSONObject(thumbArray.length() - 1)?.optString("url")
                                    ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                            } else {
                                "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                            }

                            if (videoId.isNotEmpty()) {
                                tracks.add(
                                    YouTubeTrack(
                                        videoId = videoId,
                                        title = title,
                                        author = author,
                                        durationSeconds = durationSeconds,
                                        thumbnailUrl = thumbUrl,
                                        viewCountText = viewCount
                                    )
                                )
                            }
                        }
                    } else {
                        val keys = node.keys()
                        while (keys.hasNext()) {
                            scanForVideoRenderers(node.opt(keys.next()))
                        }
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        scanForVideoRenderers(node.opt(i))
                    }
                }
            }
        }

        scanForVideoRenderers(json)
        return tracks.distinctBy { it.videoId }
    }

    private fun getRunOrSimpleText(obj: JSONObject?): String? {
        if (obj == null) return null
        val runs = obj.optJSONArray("runs")
        if (runs != null && runs.length() > 0) {
            val first = runs.optJSONObject(0)
            val text = first?.optString("text")
            if (!text.isNullOrEmpty()) return text
        }
        val simple = obj.optString("simpleText")
        if (!simple.isNullOrEmpty()) return simple
        return null
    }

    private fun parseDurationToSeconds(durationStr: String): Long {
        if (durationStr.isBlank()) return 0L
        val parts = durationStr.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0L
        }
    }

    private fun getSearchSuggestions(query: String): List<String> {
        return try {
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=${Uri.encode(query)}"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val array = JSONArray(body)
                if (array.length() > 1) {
                    val suggestionsJson = array.getJSONArray(1)
                    val list = mutableListOf<String>()
                    for (i in 0 until suggestionsJson.length()) {
                        list.add(suggestionsJson.getString(i))
                    }
                    list
                } else emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getVideoDetails(videoId: String): YouTubeTrack = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotEmpty()) {
                        val json = JSONObject(body)
                        val title = json.optString("title", "YouTube Video")
                        val author = json.optString("author_name", "YouTube Artist")
                        val thumb = json.optString("thumbnail_url", "https://img.youtube.com/vi/$videoId/hqdefault.jpg")
                        return@withContext YouTubeTrack(
                            videoId = videoId,
                            title = title,
                            author = author,
                            durationSeconds = 0L,
                            thumbnailUrl = thumb
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        YouTubeTrack(
            videoId = videoId,
            title = "YouTube Video ($videoId)",
            author = "YouTube",
            durationSeconds = 0L,
            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        )
    }

    fun extractVideoIdFromUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.length == 11 && trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return trimmed
        }
        val patterns = listOf(
            Regex("(?:https?://)?(?:www\\.|m\\.|music\\.)?youtube\\.com/watch\\?.*v=([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?://)?youtu\\.be/([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?://)?(?:www\\.|m\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11})"),
            Regex("(?:https?://)?(?:www\\.|m\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]{11})")
        )
        for (pattern in patterns) {
            val match = pattern.find(trimmed)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }
}
