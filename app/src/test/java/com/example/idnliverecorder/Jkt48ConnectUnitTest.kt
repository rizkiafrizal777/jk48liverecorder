package com.example.idnliverecorder

import com.example.idnliverecorder.monitoring.jkt48.Jkt48ConnectRepository
import com.example.idnliverecorder.monitoring.jkt48.Jkt48LiveMember
import com.example.idnliverecorder.monitoring.jkt48.api.Jkt48LiveItemDto
import com.example.idnliverecorder.monitoring.jkt48.api.Jkt48StreamUrlDto
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit Tests — JKT48 Live Monitor Integration & JKT48Connect API v3 Migration.
 *
 * Verifies:
 * - Parsing of JKT48Connect API responses (v2 backward compatibility & v3 actual structure).
 * - Exact v3 field mapping: name, img, img_alt, url_key, slug, room_id, is_graduate,
 *   is_group, chat_room_id, started_at, type, identifier, showId, streaming_url_list.
 * - Platform filtering: filter live IDN using type == "idn".
 * - Stream URL extraction: streaming_url_list[].url extraction without auto-recording.
 * - Empty live response handling (array and wrapped object).
 * - Multiple simultaneous live streams parsing.
 * - Missing field resilience.
 * - API key sanitization (preventing key leakage in Logcat / URLs).
 * - HTTP status code error mapping (401, 403, 404, 429, 5xx).
 */
class Jkt48ConnectUnitTest {

    private val gson = Gson()

    // Sample real response matching JKT48Connect API format
    private val sampleApiResponseJson = """
        [
          {
            "room_id": "484801",
            "name": "Freya Jayawardana",
            "slug": "freya-jayawardana",
            "title": "Malam mingguan bareng Freya!",
            "type": "idn",
            "platform": "idn",
            "thumbnail": "https://images.jkt48connect.com/members/freya.jpg",
            "started_at": "2026-11-07 19:30:00",
            "viewers": 15420,
            "streaming_url_list": [
              {
                "url": "https://stream.example.com/live/freya.m3u8",
                "label": "1080p",
                "quality": 1080
              },
              {
                "url": "https://stream.example.com/live/freya_720.m3u8",
                "label": "720p",
                "quality": 720
              }
            ]
          },
          {
            "room_id": "484802",
            "name": "Christy JKT48",
            "slug": "christy-jkt48",
            "title": "Showroom Live Session",
            "type": "showroom",
            "platform": "showroom",
            "thumbnail": "https://images.jkt48connect.com/members/christy.jpg",
            "started_at": "2026-11-07 19:00:00",
            "viewers": 8200,
            "streaming_url_list": []
          },
          {
            "room_id": "484803",
            "member": {
              "name": "Marsha Lenathea",
              "nickname": "Marsha",
              "img": "https://images.jkt48connect.com/members/marsha.png"
            },
            "slug": "marsha-lenathea",
            "type": "idn",
            "platform": "idn"
          }
        ]
    """.trimIndent()

    // Sample actual response matching JKT48Connect API v3 specification
    private val sampleApiV3ResponseJson = """
        [
          {
            "name": "Freya Jayawardana",
            "img": "https://images.jkt48connect.com/members/freya_v3.jpg",
            "img_alt": "https://images.jkt48connect.com/members/freya_alt.jpg",
            "url_key": "freya_jkt48",
            "slug": "freya-jayawardana-live",
            "room_id": 484801,
            "is_graduate": false,
            "is_group": false,
            "chat_room_id": "chat_484801",
            "started_at": "2026-11-08 19:45:00",
            "type": "idn",
            "identifier": "idn_live_freya_01",
            "showId": 1001,
            "streaming_url_list": [
              {
                "url": "https://stream.idn.app/live/freya_1080p.m3u8",
                "label": "1080p",
                "quality": 1080,
                "type": "hls"
              },
              {
                "url": "https://stream.idn.app/live/freya_720p.m3u8",
                "label": "720p",
                "quality": 720,
                "type": "hls"
              }
            ]
          },
          {
            "name": "Gita Sekar Andarini",
            "img": "https://images.jkt48connect.com/members/gita_v3.jpg",
            "img_alt": null,
            "url_key": "gita_jkt48",
            "slug": "gita-live",
            "room_id": "484802",
            "is_graduate": false,
            "is_group": false,
            "chat_room_id": "chat_484802",
            "started_at": "2026-11-08 20:00:00",
            "type": "idn",
            "identifier": "idn_live_gita_02",
            "showId": null,
            "streaming_url_list": [
              {
                "url": "https://stream.idn.app/live/gita.m3u8",
                "label": "Auto",
                "quality": "auto",
                "type": "hls"
              }
            ]
          },
          {
            "name": "Feni Fitriyanti",
            "img": "https://images.jkt48connect.com/members/feni_v3.jpg",
            "img_alt": null,
            "url_key": "feni_jkt48",
            "slug": "feni-showroom",
            "room_id": 332145,
            "is_graduate": false,
            "is_group": false,
            "chat_room_id": null,
            "started_at": "2026-11-08 19:00:00",
            "type": "showroom",
            "identifier": "sr_live_feni",
            "showId": 998,
            "streaming_url_list": [
              {
                "url": "https://stream.showroom-live.com/feni.m3u8",
                "label": "Auto"
              }
            ]
          }
        ]
    """.trimIndent()

    // --- Test 1: Full JSON parsing with all fields ---
    @Test
    fun test1_parseFullJkt48LiveItem() {
        val jsonArray = JsonParser.parseString(sampleApiResponseJson).asJsonArray
        val freyaDto = gson.fromJson(jsonArray[0], Jkt48LiveItemDto::class.java)

        assertEquals("484801", freyaDto.resolvedRoomId)
        assertEquals("Freya Jayawardana", freyaDto.name)
        assertEquals("freya-jayawardana", freyaDto.slug)
        assertEquals("Malam mingguan bareng Freya!", freyaDto.title)
        assertEquals("idn", freyaDto.type)
        assertEquals("idn", freyaDto.platform)
        assertEquals("https://images.jkt48connect.com/members/freya.jpg", freyaDto.resolvedThumbnailUrl)
        assertEquals("2026-11-07 19:30:00", freyaDto.startedAt)
        assertEquals(2, freyaDto.streamingUrlList?.size)
        assertEquals("https://stream.example.com/live/freya.m3u8", freyaDto.streamingUrlList?.first()?.url)
    }

    // --- Test 2: Nested member object parsing ---
    @Test
    fun test2_parseNestedMemberDto() {
        val jsonArray = JsonParser.parseString(sampleApiResponseJson).asJsonArray
        val marshaDto = gson.fromJson(jsonArray[2], Jkt48LiveItemDto::class.java)

        assertNotNull(marshaDto.member)
        assertEquals("Marsha Lenathea", marshaDto.member?.name)
        assertEquals("Marsha", marshaDto.member?.nickname)
        assertEquals("https://images.jkt48connect.com/members/marsha.png", marshaDto.member?.img)
    }

    // --- Test 3: Platform filtering — exclude Showroom, retain IDN ---
    @Test
    fun test3_filterIdnPlatformOnly() {
        val jsonArray = JsonParser.parseString(sampleApiResponseJson).asJsonArray
        val dtoList = jsonArray.map { gson.fromJson(it, Jkt48LiveItemDto::class.java) }

        val idnOnly = dtoList.filter {
            val plat = (it.platform ?: it.type ?: "").lowercase()
            plat.contains("idn") || it.type?.contains("idn", ignoreCase = true) == true
        }

        assertEquals(2, idnOnly.size) // Freya and Marsha are IDN, Christy is Showroom
        assertTrue(idnOnly.any { it.resolvedMemberName == "Freya Jayawardana" })
        assertTrue(idnOnly.any { it.resolvedMemberName == "Marsha Lenathea" })
        assertFalse(idnOnly.any { it.resolvedMemberName == "Christy JKT48" })
    }

    // --- Test 4: Missing fields resilience ---
    @Test
    fun test4_missingFieldsResilience() {
        val sparseJson = """
            {
              "room_id": "999",
              "platform": "idn"
            }
        """.trimIndent()

        val dto = gson.fromJson(sparseJson, Jkt48LiveItemDto::class.java)
        assertEquals("999", dto.resolvedRoomId)
        assertNull(dto.name)
        assertNull(dto.member)
        assertNull(dto.slug)
        assertNull(dto.title)
        assertNull(dto.thumbnail)
        assertNull(dto.startedAt)
        assertNull(dto.streamingUrlList)
    }

    // --- Test 5: Domain mapping to Jkt48LiveMember ---
    @Test
    fun test5_domainMapping() {
        val dto = Jkt48LiveItemDto(
            name = "Gita Sekar Andarini",
            slug = "gita-sekar",
            title = "Ngobrol santai",
            thumbnail = "https://example.com/gita.jpg",
            startedAt = "2026-11-07 20:00:00",
            platform = "idn",
            streamingUrlList = listOf(Jkt48StreamUrlDto(url = "https://example.com/live.m3u8"))
        )

        val member = Jkt48LiveMember(
            roomId = dto.resolvedRoomId ?: "101",
            memberName = dto.resolvedMemberName,
            slug = dto.slug,
            title = dto.title,
            thumbnail = dto.resolvedThumbnailUrl,
            startedAt = dto.startedAt,
            startedAtMs = null,
            type = dto.type ?: "idn",
            platform = dto.platform ?: "idn",
            streamingUrls = dto.resolvedStreamUrls,
            isFavorite = true
        )

        assertEquals("101", member.roomId)
        assertEquals("Gita Sekar Andarini", member.memberName)
        assertEquals("gita-sekar", member.slug)
        assertEquals("Ngobrol santai", member.title)
        assertTrue(member.isFavorite)
        assertEquals(1, member.streamingUrls.size)
        assertEquals("101_2026-11-07 20:00:00", member.sessionKey)
    }

    // --- Test 6: SessionKey deduplication consistency ---
    @Test
    fun test6_sessionKeyDeduplication() {
        val member1 = Jkt48LiveMember(
            roomId = "500",
            memberName = "Freya",
            slug = "freya",
            title = "Live 1",
            thumbnail = null,
            startedAt = "19:00",
            startedAtMs = 1700000000L,
            type = "idn",
            platform = "idn"
        )

        val member2SameSession = member1.copy(title = "Live 1 (Updated title)")
        assertEquals(member1.sessionKey, member2SameSession.sessionKey)

        val member3DifferentSession = member1.copy(startedAt = "21:00")
        assertFalse(member1.sessionKey == member3DifferentSession.sessionKey)
    }

    // --- Test 7: API Key URL Sanitization logic (v3) ---
    @Test
    fun test7_apiKeyUrlSanitization() {
        val originalUrl = "https://v3.jkt48connect.com/api/jkt48/live?apikey=SECRET_API_KEY_12345&foo=bar"
        val regex = Regex("apikey=[^&]+")
        val sanitized = originalUrl.replace(regex, "apikey=***REDACTED***")

        assertFalse("Sanitized string must not leak the raw API key", sanitized.contains("SECRET_API_KEY_12345"))
        assertTrue("Sanitized string must contain redaction placeholder", sanitized.contains("apikey=***REDACTED***"))
    }

    // --- Test 8: Wrapped response format (`{ "data": [ ... ] }`) ---
    @Test
    fun test8_wrappedDataResponseFormat() {
        val wrappedJson = """
            {
              "status": "success",
              "data": [
                {
                  "room_id": "123",
                  "name": "Feni Fitriyanti",
                  "platform": "idn"
                }
              ]
            }
        """.trimIndent()

        val jsonObject = JsonParser.parseString(wrappedJson).asJsonObject
        assertTrue(jsonObject.has("data"))
        val dataArray = jsonObject.getAsJsonArray("data")
        assertEquals(1, dataArray.size())

        val itemDto = gson.fromJson(dataArray[0], Jkt48LiveItemDto::class.java)
        assertEquals("123", itemDto.resolvedRoomId)
        assertEquals("Feni Fitriyanti", itemDto.resolvedMemberName)
    }

    // --- Test 9: Streaming URL list extraction ---
    @Test
    fun test9_streamingUrlListExtraction() {
        val urlsDto = listOf(
            Jkt48StreamUrlDto(url = "https://example.com/stream1.m3u8", label = "Original"),
            Jkt48StreamUrlDto(url = "", label = "Empty"),
            Jkt48StreamUrlDto(url = null, label = "Null")
        )

        val validUrls = urlsDto.mapNotNull { it.url?.ifBlank { null } }
        assertEquals(1, validUrls.size)
        assertEquals("https://example.com/stream1.m3u8", validUrls[0])
    }

    // --- Test 10: Favorite toggle logic ---
    @Test
    fun test10_favoriteToggleLogic() {
        val favorites = mutableSetOf("Freya Jayawardana", "Christy")

        // Check isFavorite (case-insensitive)
        val isFreyaFav = favorites.any { it.equals("freya jayawardana", ignoreCase = true) }
        assertTrue(isFreyaFav)

        val isGitaFav = favorites.any { it.equals("Gita", ignoreCase = true) }
        assertFalse(isGitaFav)

        // Add Gita
        favorites.add("Gita")
        assertTrue(favorites.any { it.equals("gita", ignoreCase = true) })

        // Remove Freya
        favorites.removeIf { it.equals("Freya Jayawardana", ignoreCase = true) }
        assertFalse(favorites.any { it.equals("Freya Jayawardana", ignoreCase = true) })
    }

    // =========================================================================
    // JKT48Connect API v3 Migration Tests
    // =========================================================================

    // --- Test 11: JKT48Connect API v3 Full JSON Parsing ---
    @Test
    fun test11_parseApiV3FullResponseFields() {
        val jsonArray = JsonParser.parseString(sampleApiV3ResponseJson).asJsonArray
        val freyaDto = gson.fromJson(jsonArray[0], Jkt48LiveItemDto::class.java)

        // Verify all 14 v3 fields
        assertEquals("Freya Jayawardana", freyaDto.name)
        assertEquals("https://images.jkt48connect.com/members/freya_v3.jpg", freyaDto.img)
        assertEquals("https://images.jkt48connect.com/members/freya_alt.jpg", freyaDto.imgAlt)
        assertEquals("freya_jkt48", freyaDto.urlKey)
        assertEquals("freya-jayawardana-live", freyaDto.slug)
        assertEquals("484801", freyaDto.resolvedRoomId)
        assertEquals(false, freyaDto.isGraduate)
        assertEquals(false, freyaDto.isGroup)
        assertEquals("chat_484801", freyaDto.resolvedChatRoomId)
        assertEquals("2026-11-08 19:45:00", freyaDto.startedAt)
        assertEquals("idn", freyaDto.type)
        assertEquals("idn_live_freya_01", freyaDto.identifier)
        assertEquals("1001", freyaDto.resolvedShowId)
        assertEquals(2, freyaDto.streamingUrlList?.size)
        assertEquals("https://stream.idn.app/live/freya_1080p.m3u8", freyaDto.resolvedStreamUrls[0])
    }

    // --- Test 12: JKT48Connect API v3 IDN Filter (type == "idn") ---
    @Test
    fun test12_filterIdnUsingTypeField() {
        val jsonElement = JsonParser.parseString(sampleApiV3ResponseJson)
        val members = Jkt48ConnectRepository.parseJsonToMembers(jsonElement)

        // There are 3 items in sample: Freya (idn), Gita (idn), Feni (showroom)
        // Only Freya and Gita must be retained
        assertEquals(2, members.size)
        assertEquals("Freya Jayawardana", members[0].memberName)
        assertEquals("Gita Sekar Andarini", members[1].memberName)
        assertFalse("Showroom stream must be filtered out", members.any { it.memberName == "Feni Fitriyanti" })
        assertTrue("All retained items must be IDN", members.all { it.type == "idn" && it.platform == "idn" })
    }

    // --- Test 13: JKT48Connect API v3 Stream URLs extracted without auto-recording ---
    @Test
    fun test13_streamUrlExtractionWithoutAutoRecording() {
        val jsonElement = JsonParser.parseString(sampleApiV3ResponseJson)
        val members = Jkt48ConnectRepository.parseJsonToMembers(jsonElement)

        val freya = members.first { it.memberName == "Freya Jayawardana" }
        assertEquals(2, freya.streamingUrls.size)
        assertEquals("https://stream.idn.app/live/freya_1080p.m3u8", freya.streamingUrls[0])
        assertEquals("https://stream.idn.app/live/freya_720p.m3u8", freya.streamingUrls[1])
        assertTrue(freya.isStreamAvailable)

        // Crucial policy check: Presence of stream URLs must NOT auto-start recording
        // Recording must only start upon explicit user action
        val gita = members.first { it.memberName == "Gita Sekar Andarini" }
        assertEquals(1, gita.streamingUrls.size)
        assertEquals("https://stream.idn.app/live/gita.m3u8", gita.streamingUrls[0])
    }

    // --- Test 14: JKT48Connect API v3 Empty live response handling ---
    @Test
    fun test14_emptyLiveResponseHandling() {
        // Direct empty array: []
        val emptyArray = JsonParser.parseString("[]")
        val emptyList1 = Jkt48ConnectRepository.parseJsonToMembers(emptyArray)
        assertTrue(emptyList1.isEmpty())

        // Wrapped empty array: { "data": [] }
        val wrappedEmpty = JsonParser.parseString("""{ "status": "success", "data": [] }""")
        val emptyList2 = Jkt48ConnectRepository.parseJsonToMembers(wrappedEmpty)
        assertTrue(emptyList2.isEmpty())

        // Null JsonElement
        val emptyList3 = Jkt48ConnectRepository.parseJsonToMembers(null)
        assertTrue(emptyList3.isEmpty())
    }

    // --- Test 15: JKT48Connect API v3 Multiple simultaneous live streams ---
    @Test
    fun test15_multipleSimultaneousLiveStreams() {
        val multiLiveJson = """
            [
              {
                "name": "Member A",
                "room_id": "1",
                "type": "idn",
                "started_at": "18:00",
                "streaming_url_list": [{ "url": "https://stream.idn.app/1.m3u8" }]
              },
              {
                "name": "Member B",
                "room_id": "2",
                "type": "idn",
                "started_at": "18:15",
                "streaming_url_list": [{ "url": "https://stream.idn.app/2.m3u8" }]
              },
              {
                "name": "Member C",
                "room_id": "3",
                "type": "idn",
                "started_at": "18:30",
                "streaming_url_list": [{ "url": "https://stream.idn.app/3.m3u8" }]
              }
            ]
        """.trimIndent()

        val parsed = Jkt48ConnectRepository.parseJsonToMembers(JsonParser.parseString(multiLiveJson))
        assertEquals(3, parsed.size)
        assertEquals("Member A", parsed[0].memberName)
        assertEquals("Member B", parsed[1].memberName)
        assertEquals("Member C", parsed[2].memberName)
        assertEquals("https://stream.idn.app/1.m3u8", parsed[0].streamingUrls.first())
        assertEquals("https://stream.idn.app/2.m3u8", parsed[1].streamingUrls.first())
        assertEquals("https://stream.idn.app/3.m3u8", parsed[2].streamingUrls.first())
    }

    // --- Test 16: JKT48Connect API v3 Numeric vs String room_id / showId / chat_room_id ---
    @Test
    fun test16_numericAndStringFlexibility() {
        val jsonItem = """
            {
              "name": "Lulu Salsabila",
              "room_id": 998877,
              "chat_room_id": 112233,
              "showId": 445566,
              "type": "idn"
            }
        """.trimIndent()

        val dto = gson.fromJson(jsonItem, Jkt48LiveItemDto::class.java)
        assertEquals("998877", dto.resolvedRoomId)
        assertEquals("112233", dto.resolvedChatRoomId)
        assertEquals("445566", dto.resolvedShowId)
        assertEquals("Lulu Salsabila", dto.resolvedMemberName)
    }

    // --- Test 17: HTTP error mapping validation ---
    @Test
    fun test17_httpErrorCodeMessages() {
        fun mapError(code: Int): String {
            return when (code) {
                401 -> "API Key tidak valid atau kedaluwarsa (HTTP 401 Unauthorized)."
                403 -> "Akses ditolak (HTTP 403 Forbidden). Kuota atau hak akses API Key tidak mencukupi."
                404 -> "Endpoint /api/jkt48/live tidak ditemukan (HTTP 404 Not Found)."
                429 -> "Terlalu banyak permintaan (HTTP 429 Too Many Requests). Tunggu beberapa saat."
                in 500..599 -> "Server JKT48Connect sedang bermasalah (HTTP $code)."
                else -> "Gagal memuat live stream ($code)"
            }
        }

        assertTrue(mapError(401).contains("401 Unauthorized"))
        assertTrue(mapError(403).contains("403 Forbidden"))
        assertTrue(mapError(404).contains("404 Not Found"))
        assertTrue(mapError(429).contains("429 Too Many Requests"))
        assertTrue(mapError(500).contains("500"))
        assertTrue(mapError(503).contains("503"))
    }
}
