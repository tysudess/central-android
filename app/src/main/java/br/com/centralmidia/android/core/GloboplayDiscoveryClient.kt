package br.com.centralmidia.android.core

import org.jsoup.Jsoup
import org.jsoup.Connection
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Descobre vídeos do Globoplay em links HTML e em JSON/estado inicial embutido.
 *
 * O coletor desktop não depende somente de <a href>: ele também procura IDs /v/...
 * em scripts, páginas de edições e páginas /cenas/. Esta classe reproduz o núcleo
 * de descoberta por HTML/script no Android, sem atribuir data de publicação quando
 * ela não aparece na fonte. A página individual poderá enriquecer o candidato depois.
 */
internal class GloboplayDiscoveryClient {
    data class JarvisOutcome(
        val candidatesBySourceId: Map<String, List<VideoSearchClient.VideoItem>>,
        val queriesAttempted: Int,
        val failedQueries: Int,
    )

    /**
     * Consulta uma vez o mecanismo GraphQL público usado pelo desktop para os
     * telejornais nacionais. Os resultados são agrupados por programa e depois
     * incorporados à varredura da fonte correspondente.
     */
    fun collectJarvis(
        sources: List<VideoSource>,
        rawQueries: List<String>,
        cancelled: () -> Boolean,
        onProgress: (String) -> Unit,
    ): JarvisOutcome {
        val eligible = sources.filter { it.id in CORE_NATIONAL_IDS }
        if (eligible.isEmpty() || rawQueries.none { it.isNotBlank() }) return JarvisOutcome(emptyMap(), 0, 0)

        val buckets = eligible.associate { it.id to linkedMapOf<String, VideoSearchClient.VideoItem>() }.toMutableMap()
        var consecutiveFailures = 0
        var failedQueries = 0
        var queriesAttempted = 0
        val queries = expandQueries(rawQueries)
        for ((index, query) in queries.withIndex()) {
            if (cancelled()) break
            queriesAttempted++
            onProgress("Globoplay Jarvis • ${index + 1}/${queries.size} • $query")
            val response = runCatching {
                val payload = JSONObject()
                    .put("operationName", "MonitorGloboplaySearch")
                    .put("variables", JSONObject().put("q", query).put("page", 1))
                    .put("query", GRAPHQL_QUERY)
                Jsoup.connect(JARVIS_ENDPOINT)
                    .method(Connection.Method.POST)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .header("x-platform-id", "web")
                    .header("x-device-id", "desktop")
                    .header("x-client-version", "2024.12-5")
                    .userAgent(BROWSER_UA)
                    .timeout(JARVIS_TIMEOUT_MS)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .requestBody(payload.toString())
                    .execute()
                    .body()
            }.getOrNull()
            val resources = if (response.isNullOrBlank()) null else runCatching {
                val root = JSONObject(response)
                val graphErrors = root.optJSONArray("errors")
                if (graphErrors != null && graphErrors.length() > 0) null
                else root.optJSONObject("data")?.optJSONObject("search")
                    ?.optJSONObject("videos")?.optJSONArray("resources")
            }.getOrNull()
            if (resources == null) {
                consecutiveFailures++
                failedQueries++
                if (consecutiveFailures >= MAX_CONSECUTIVE_JARVIS_FAILURES) break
                continue
            }
            consecutiveFailures = 0
            for (i in 0 until resources.length()) {
                val resource = resources.optJSONObject(i) ?: continue
                val videoId = resource.optString("id").trim()
                val headline = resource.optString("headline").trim()
                val description = resource.optString("description").trim()
                val program = resource.optJSONObject("title")?.optString("headline")?.trim().orEmpty()
                if (videoId.length < 5 || !usefulTitle(headline) || program.isBlank()) continue
                val source = matchCoreProgram(eligible, program) ?: continue
                val programId = resource.optJSONObject("title")?.optString("originProgramId")?.trim().orEmpty()
                val summary = listOf(
                    description,
                    "Programa: $program",
                    "Programa ID: $programId".takeIf { programId.isNotBlank() }.orEmpty(),
                    "Descoberto via Globoplay Jarvis",
                ).filter { it.isNotBlank() }.distinct().joinToString(" • ").take(1400)
                val item = VideoSearchClient.VideoItem(
                    title = headline.take(220),
                    sourceId = source.id,
                    sourceName = source.name,
                    publishedAt = 0L,
                    link = "https://globoplay.globo.com/v/$videoId/",
                    summary = summary,
                )
                buckets[source.id]?.putIfAbsent(canonical(item.link), item)
            }
        }
        return JarvisOutcome(
            candidatesBySourceId = buckets.mapValues { (_, items) -> items.values.toList() },
            queriesAttempted = queriesAttempted,
            failedQueries = failedQueries,
        )
    }

    private fun expandQueries(rawQueries: List<String>): List<String> {
        val out = linkedMapOf<String, String>()
        fun add(value: String) {
            val cleaned = value.trim().replace(Regex("\\s+"), " ")
            val key = normalize(cleaned)
            if (cleaned.length >= 3 && key.isNotBlank()) out.putIfAbsent(key, cleaned)
        }
        rawQueries.forEach { raw ->
            val normalized = normalize(raw)
            if (normalized.isBlank()) return@forEach
            val tokens = normalized.split(' ').filter { it.isNotBlank() }
            if ("7" in tokens && "setembro" in tokens && tokens.any { it in setOf("desfile", "desfiles", "independencia", "comemoracoes") }) {
                listOf("7 setembro", "desfiles 7 setembro", "desfiles 7 setembro pais", "comemoracoes 7 setembro", "independencia 7 setembro").forEach(::add)
            } else {
                add(raw)
                val compact = tokens.filter { it !in STOP_WORDS }.joinToString(" ")
                if (compact.isNotBlank() && compact != normalized) add(compact)
                if (tokens.size == 1 && normalized.endsWith("es") && normalized.length >= 7) add(normalized.dropLast(2))
                else if (tokens.size == 1 && normalized.endsWith("s") && normalized.length >= 6) add(normalized.dropLast(1))
            }
        }
        return out.values.take(MAX_JARVIS_QUERIES)
    }

    private fun matchCoreProgram(sources: List<VideoSource>, programName: String): VideoSource? {
        val program = normalize(programName)
        if (program.isBlank()) return null
        // Primeiro tentamos o nome exato do programa; aliases genéricos como "Globo"
        // não podem capturar um resultado que pertence a outro telejornal.
        val primary = sources.mapNotNull { source ->
            val candidates = listOf(source.searchPrefix, source.name)
            val best = candidates.map(::normalize).filter { it.length >= 4 }
                .filter { current -> program == current || program.startsWith("$current ") || current.startsWith("$program ") }
                .maxByOrNull { it.length } ?: return@mapNotNull null
            source to best.length
        }.maxByOrNull { it.second }?.first
        if (primary != null) return primary
        return sources.firstOrNull { source ->
            source.aliases.filter { normalize(it).length >= 5 && normalize(it) !in GENERIC_LABELS }
                .any { candidate ->
                    val current = normalize(candidate)
                    program == current || program.startsWith("$current ") || current.startsWith("$program ")
                }
        }
    }

    fun collect(source: VideoSource): List<VideoSearchClient.VideoItem> {
        if (!isGloboplay(source)) return emptyList()

        val documents = linkedMapOf<String, Document>()
        val searchUrl = buildDiscoveryUrl(source)
        if (searchUrl.isNotBlank()) fetchSafely(searchUrl)?.let { documents.putIfAbsent(it.location(), it) }

        // Alguns registros antigos apontam para uma página de programa ou vídeo fixo.
        // Não usamos landingUrl como substituto da busca quando é um link /v/ de arquivo.
        val landing = source.landingUrl.trim()
        if (landing.isNotBlank() && isProgramPage(landing) && landing != searchUrl) {
            fetchSafely(landing)?.let { documents.putIfAbsent(it.location(), it) }
        }

        val programPages = linkedMapOf<String, Int>()
        documents.forEach { (base, doc) ->
            discoverProgramPages(source, doc, base).forEach { (url, score) ->
                programPages[url] = maxOf(programPages[url] ?: Int.MIN_VALUE, score)
            }
        }

        // A seção /cenas/ costuma exibir trechos que não aparecem na página de busca.
        // Limitar a duas páginas evita multiplicar o tempo por todas as fontes regionais.
        programPages.entries
            .sortedByDescending { it.value }
            .take(MAX_PROGRAM_PAGES_PER_SOURCE)
            .forEach { (programUrl, _) ->
                if (programUrl !in documents) fetchSafely(programUrl)?.let {
                    documents.putIfAbsent(it.location(), it)
                    documents.putIfAbsent(programUrl, it)
                }
                val scenesUrl = programUrl.trimEnd('/') + "/cenas/"
                if (scenesUrl !in documents) fetchSafely(scenesUrl)?.let {
                    documents.putIfAbsent(it.location(), it)
                    documents.putIfAbsent(scenesUrl, it)
                }
            }

        val out = linkedMapOf<String, VideoSearchClient.VideoItem>()
        documents.forEach { (base, doc) ->
            parseDocument(source, doc, base).forEach { candidate ->
                val key = canonical(candidate.link)
                val previous = out[key]
                if (previous == null || quality(candidate) > quality(previous)) out[key] = candidate
            }
        }

        return out.values
            .sortedWith(compareByDescending<VideoSearchClient.VideoItem> { quality(it) }
                .thenByDescending { it.publishedAt })
            .take(MAX_CANDIDATES_PER_SOURCE)
    }

    private fun buildDiscoveryUrl(source: VideoSource): String {
        val template = source.searchUrlTemplate.trim()
        if (template.isBlank()) return source.landingUrl.trim()
        val state = source.state.trim().takeIf { it.length == 2 && !it.equals("BR", true) }.orEmpty()
        val query = listOf(source.searchPrefix.trim(), state).filter { it.isNotBlank() }.joinToString(" ")
        val encoded = URLEncoder.encode(query, "UTF-8")
        return template.replace("{query}", encoded).replace("{term}", encoded).replace("%s", encoded)
    }

    private fun fetchSafely(url: String): Document? = runCatching {
        Jsoup.connect(url)
            .userAgent(BROWSER_UA)
            .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.7")
            .referrer("https://www.google.com/")
            .timeout(14_000)
            .followRedirects(true)
            .ignoreHttpErrors(true)
            .maxBodySize(MAX_HTML_BODY_BYTES)
            .get()
    }.getOrNull()

    private fun parseDocument(
        source: VideoSource,
        doc: Document,
        baseUrl: String,
    ): List<VideoSearchClient.VideoItem> {
        val out = linkedMapOf<String, VideoSearchClient.VideoItem>()

        doc.select("a[href]").forEach { anchor ->
            val link = absolute(baseUrl, anchor.attr("href"))
            val id = videoId(link) ?: return@forEach
            val title = listOf(
                anchor.attr("aria-label"), anchor.attr("title"),
                anchor.selectFirst("img[alt]")?.attr("alt").orEmpty(), anchor.text(),
                anchor.parent()?.selectFirst("h1,h2,h3,h4,[class*=title],[class*=headline]")?.text().orEmpty(),
            ).firstOrNull { usefulTitle(it) }.orEmpty()
            val container = anchor.parent()?.takeIf { it.text().length in 20..1800 }
            val summary = listOfNotNull(
                container?.text()?.trim()?.takeIf { it.length >= 20 },
                source.searchPrefix.trim().takeIf { it.isNotBlank() },
            ).distinct().joinToString(" • ").take(1000)
            val item = VideoSearchClient.VideoItem(
                title = title.ifBlank { "Vídeo recente • ${source.name}" }.take(220),
                sourceId = source.id,
                sourceName = source.name,
                publishedAt = parseDateFrom(container?.text().orEmpty()),
                link = "https://globoplay.globo.com/v/$id/",
                summary = summary,
            )
            out.putIfAbsent(canonical(item.link), item)
        }

        // Correção principal: as páginas do Globoplay frequentemente guardam os IDs
        // nos scripts e no JSON inicial, não em anchors rastreáveis pelo Jsoup.
        val raw = normalizeEmbedded(doc.outerHtml())
        VIDEO_ID.findAll(raw).forEach { match ->
            val id = match.groupValues.getOrNull(1).orEmpty()
            if (id.length < 5) return@forEach
            val start = (match.range.first - CONTEXT_WINDOW).coerceAtLeast(0)
            val end = (match.range.last + CONTEXT_WINDOW).coerceAtMost(raw.length - 1)
            val context = raw.substring(start, (end + 1).coerceAtLeast(start))
            val title = nearestValue(context, TITLE_FIELD)
            val description = nearestValue(context, DESCRIPTION_FIELD)
            val publishedAt = parseDateFrom(context)
            val summary = listOf(description, source.searchPrefix.trim())
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" • ")
                .take(1200)
            val item = VideoSearchClient.VideoItem(
                title = title.takeIf { usefulTitle(it) }?.take(220)
                    ?: "Vídeo recente • ${source.name}",
                sourceId = source.id,
                sourceName = source.name,
                publishedAt = publishedAt,
                link = "https://globoplay.globo.com/v/$id/",
                summary = summary,
            )
            val key = canonical(item.link)
            val previous = out[key]
            if (previous == null || quality(item) > quality(previous)) out[key] = item
        }
        return out.values.toList()
    }

    private fun discoverProgramPages(source: VideoSource, doc: Document, base: String): List<Pair<String, Int>> {
        val out = linkedMapOf<String, Int>()
        doc.select("a[href]").forEach { anchor ->
            val url = absolute(base, anchor.attr("href"))
            val match = PROGRAM_URL.find(url) ?: return@forEach
            val page = "https://globoplay.globo.com/${match.groupValues[1]}/t/${match.groupValues[2]}"
            val label = "${anchor.attr("aria-label")} ${anchor.attr("title")} ${anchor.text()} $page"
            val score = programScore(source, label)
            if (score > 0) out[page] = maxOf(out[page] ?: Int.MIN_VALUE, score)
        }
        val raw = normalizeEmbedded(doc.outerHtml())
        PROGRAM_URL.findAll(raw).take(MAX_PROGRAM_LINKS_IN_HTML).forEach { match ->
            val page = "https://globoplay.globo.com/${match.groupValues[1]}/t/${match.groupValues[2]}"
            val score = programScore(source, page)
            if (score > 0) out[page] = maxOf(out[page] ?: Int.MIN_VALUE, score)
        }
        return out.entries.map { it.key to it.value }
    }

    private fun programScore(source: VideoSource, value: String): Int {
        val haystack = normalize(value)
        val wantedValues = listOf(source.searchPrefix, source.name) + source.aliases
        var score = 0
        wantedValues.map(::normalize)
            .filter { it.length >= 4 && it !in GENERIC_LABELS }
            .distinct()
            .forEach { wanted ->
                if (wanted in haystack) score += if (wanted == normalize(source.searchPrefix)) 40 else 10
                else {
                    val tokens = wanted.split(' ').filter { it.length >= 3 && it !in STOP_WORDS }
                    if (tokens.isNotEmpty() && tokens.count { it in haystack } == tokens.size) score += tokens.size
                }
            }
        return score
    }

    private fun nearestValue(context: String, field: Regex): String {
        val candidates = field.findAll(context).map { match ->
            val raw = match.groupValues.getOrNull(1).orEmpty()
            val decoded = decodeJsonText(raw)
            (kotlin.math.abs(match.range.first - context.length / 2)) to decoded
        }.filter { it.second.length >= 8 && it.second.length <= 1200 }
            .toList()
        return candidates.minByOrNull { it.first }?.second.orEmpty()
    }

    private fun parseDateFrom(context: String): Long {
        val center = context.length / 2
        val embedded = DATE_FIELD.findAll(context).mapNotNull { match ->
            val raw = decodeJsonText(match.groupValues.getOrNull(1).orEmpty())
            val parsed = parseDateValue(raw) ?: return@mapNotNull null
            kotlin.math.abs(match.range.first - center) to parsed
        }.minByOrNull { it.first }?.second
        if (embedded != null) return embedded

        // Edições/trechos podem expor a data como texto visível (27/10/2026),
        // em vez de datePublished no JSON. Só usamos a ocorrência mais próxima
        // do link que originou o candidato; nunca substituímos data ausente por agora.
        val visibleDates = listOf(
            Regex("\\b(\\d{2}/\\d{2}/\\d{4})(?:\\s+(\\d{1,2}:\\d{2}))?\\b"),
            Regex("\\b(\\d{4}-\\d{2}-\\d{2})(?:[T\\s](\\d{1,2}:\\d{2})(?::\\d{2})?)?\\b"),
        )
        return visibleDates.asSequence().flatMap { regex ->
            regex.findAll(context).mapNotNull { match ->
                val value = listOf(match.groupValues.getOrNull(1).orEmpty(), match.groupValues.getOrNull(2).orEmpty())
                    .filter { it.isNotBlank() }.joinToString(" ")
                val parsed = parseDateValue(value) ?: return@mapNotNull null
                kotlin.math.abs(match.range.first - center) to parsed
            }
        }.minByOrNull { it.first }?.second ?: 0L
    }

    private fun parseDateValue(raw: String): Long? {
        val normalized = raw.trim().replace(Regex("Z$"), "+0000")
            .replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ" to Locale.US,
            "yyyy-MM-dd'T'HH:mm:ssZ" to Locale.US,
            "yyyy-MM-dd'T'HH:mmZ" to Locale.US,
            "yyyy-MM-dd HH:mm:ss" to Locale.US,
            "yyyy-MM-dd" to Locale.US,
            "dd/MM/yyyy HH:mm" to Locale("pt", "BR"),
            "dd/MM/yyyy" to Locale("pt", "BR"),
        )
        for ((pattern, locale) in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, locale).apply { isLenient = false }.parse(normalized)
            }.getOrNull() ?: continue
            if (parsed.time in 1..System.currentTimeMillis()) return parsed.time
        }
        return null
    }

    private fun decodeJsonText(raw: String): String = raw
        .replace("\\n", " ").replace("\\r", " ").replace("\\t", " ")
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("\\u003a", ":", ignoreCase = true)
        .replace("\\u002f", "/", ignoreCase = true)
        .replace("\\/", "/").replace("\\\"", "\"")
        .replace(Regex("\\s+"), " ").trim()

    private fun normalizeEmbedded(value: String): String = value
        .replace("\\/", "/")
        .replace("\\u002F", "/", ignoreCase = true)
        .replace("\\u003A", ":", ignoreCase = true)
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("\\u003D", "=", ignoreCase = true)
        .replace("\\\"", "\"")

    private fun absolute(base: String, raw: String): String {
        val cleaned = raw.trim().replace("&amp;", "&")
        return runCatching { URI(base).resolve(cleaned).toString() }.getOrDefault(cleaned)
    }

    private fun videoId(url: String): String? = VIDEO_ID.find(url)?.groupValues?.getOrNull(1)

    private fun isGloboplay(source: VideoSource): Boolean =
        "globoplay" in source.id.lowercase(Locale.ROOT) ||
            "globoplay.globo.com" in source.landingUrl.lowercase(Locale.ROOT) ||
            "globoplay.globo.com" in source.searchUrlTemplate.lowercase(Locale.ROOT)

    private fun isProgramPage(url: String): Boolean = PROGRAM_URL.containsMatchIn(url)

    private fun canonical(url: String): String = url.trimEnd('/').lowercase(Locale.ROOT)

    private fun usefulTitle(raw: String): Boolean {
        val value = normalize(raw)
        return raw.trim().length >= 8 && value !in GENERIC_LABELS &&
            !value.startsWith("ver todos os videos") && !value.startsWith("ultimos videos")
    }

    private fun normalize(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT), Normalizer.Form.NFD,
    ).replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim().replace(Regex("\\s+"), " ")

    private fun quality(item: VideoSearchClient.VideoItem): Int =
        (if (item.title.startsWith("Vídeo recente")) 0 else 30) +
            (if (item.publishedAt > 0L) 8 else 0) + minOf(item.summary.length / 40, 20)

    companion object {
        private const val JARVIS_ENDPOINT = "https://cloud-jarvis.globo.com/graphql"
        private const val GRAPHQL_QUERY = "query MonitorGloboplaySearch(\$q:String!,\$page:Int) { search { videos(query:\$q,page:\$page) { page nextPage total hasNextPage resources { id headline description duration title { headline originProgramId } } } } }"
        private const val JARVIS_TIMEOUT_MS = 12_000
        private const val MAX_JARVIS_QUERIES = 28
        private const val MAX_CONSECUTIVE_JARVIS_FAILURES = 3
        private val CORE_NATIONAL_IDS = setOf(
            "globoplay-bom-dia-brasil", "globoplay-hora-1", "globoplay-jornal-hoje",
            "globoplay-jornal-nacional", "globoplay-jornal-da-globo",
        )
        private const val MAX_HTML_BODY_BYTES = 8_000_000
        private const val MAX_CANDIDATES_PER_SOURCE = 80
        private const val MAX_PROGRAM_PAGES_PER_SOURCE = 2
        private const val MAX_PROGRAM_LINKS_IN_HTML = 80
        private const val CONTEXT_WINDOW = 900
        private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36"
        private val VIDEO_ID = Regex("(?:https?://globoplay\\.globo\\.com)?/v/([0-9]{5,})/?", RegexOption.IGNORE_CASE)
        private val PROGRAM_URL = Regex("(?:https?://globoplay\\.globo\\.com/)?([^/\\s\"'<>]+?)/t/([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
        private val TITLE_FIELD = Regex("[\"'](?:headline|title|name)[\"']\\s*:\\s*[\"']((?:\\\\.|[^\"'\\\\]){8,240})[\"']", RegexOption.IGNORE_CASE)
        private val DESCRIPTION_FIELD = Regex("[\"'](?:description|summary|seoDescription)[\"']\\s*:\\s*[\"']((?:\\\\.|[^\"'\\\\]){8,1200})[\"']", RegexOption.IGNORE_CASE)
        private val DATE_FIELD = Regex("[\"'](?:datePublished|uploadDate|dateCreated|publishedAt|publicationDate|publishedDate|exhibitedAt)[\"']\\s*:\\s*[\"']([^\"'\\\\]{4,80})[\"']", RegexOption.IGNORE_CASE)
        private val GENERIC_LABELS = setOf("videos", "video", "todos os videos", "ultimos videos", "mais videos", "ver videos", "ao vivo", "globoplay", "jornalismo", "noticias", "noticia")
        private val STOP_WORDS = setOf("de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os")
    }
}
