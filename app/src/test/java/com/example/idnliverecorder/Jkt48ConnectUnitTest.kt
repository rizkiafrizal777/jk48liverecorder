package com.example.idnliverecorder

import com.example.idnliverecorder.monitoring.jkt48.Jkt48LiveMember
import com.example.idnliverecorder.monitoring.jkt48.api.Jkt48LiveItemDto
import com.example.idnliverecorder.monitoring.jkt48.api.Jkt48MemberDto
import com.example.idnliverecorder.monitoring.jkt48.api.Jkt48StreamUrlDto
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4A Unit Tests — JKT48 Live Monitor Integration
 *
 * Verifies parsing of JKT48Connect API responses, IDN platform filtering,
 * missing field resilience, favorite tracking, and deduplication logic.
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

    // --- Test 1: Full JSON parsing with all fields ---
    @Test
    fun test1_parseFullJkt48LiveItem() {
        val jsonArray = JsonParser.parseString(sampleApiResponseJson).asJsonArray
        val freyaDto = gson.fromJson(jsonArray[0], Jkt48LiveItemDto::class.java)

        assertEquals("484801", freyaDto.roomId)
        assertEquals("Freya Jayawardana", freyaDto.name)
        assertEquals("freya-jayawardana", freyaDto.slug)
        assertEquals("Malam mingguan bareng Freya!", freyaDto.title)
        assertEquals("idn", freyaDto.type)
        assertEquals("idn", freyaDto.platform)
        assertEquals("https://images.jkt48connect.com/members/freya.jpg", freyaDto.thumbnail)
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
        assertTrue(idnOnly.any { (it.name ?: it.member?.name) == "Freya Jayawardana" })
        assertTrue(idnOnly.any { (it.name ?: it.member?.name) == "Marsha Lenathea" })
        assertFalse(idnOnly.any { (it.name ?: it.member?.name) == "Christy JKT48" })
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
        assertEquals("999", dto.roomId)
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
            roomId = "101",
            name = "Gita Sekar Andarini",
            slug = "gita-sekar",
            title = "Ngobrol santai",
            thumbnail = "https://example.com/gita.jpg",
            startedAt = "2026-11-07 20:00:00",
            platform = "idn",
            streamingUrlList = listOf(Jkt48StreamUrlDto(url = "https://example.com/live.m3u8"))
        )

        val member = Jkt48LiveMember(
            roomId = dto.roomId ?: "",
            memberName = dto.name ?: "Unknown",
            slug = dto.slug,
            title = dto.title,
            thumbnail = dto.thumbnail,
            startedAt = dto.startedAt,
            startedAtMs = null,
            type = dto.type ?: "idn",
            platform = dto.platform ?: "idn",
            streamingUrls = dto.streamingUrlList?.mapNotNull { it.url } ?: emptyList(),
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

    // --- Test 7: API Key URL Sanitization logic ---
    @Test
    fun test7_apiKeyUrlSanitization() {
        val originalUrl = "https://v2.jkt48connect.com/api/jkt48/live/idn?apikey=SECRET_API_KEY_12345&foo=bar"
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
        assertEquals("123", itemDto.roomId)
        assertEquals("Feni Fitriyanti", itemDto.name)
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
}

