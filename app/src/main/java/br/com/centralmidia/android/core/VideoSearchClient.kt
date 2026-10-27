package br.com.centralmidia.android.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale

class VideoSearchClient {
    data class VideoItem(
        val title: String,
        val sourceId: String,
        val sourceName: String,
        val publishedAt: Long,
        val link: String,
        val summary: String = "",
        val matchedTerm: String = "",
        val matchedDemand: String = "",
        val isNew: Boolean = false,
    )

    data class Progress(
        val completed: Int,
        val total: Int,
        val found: Int,
        val errors: Int,
        val currentSource: String,
        val currentQuery: String,
    )

    data class Outcome(
        val items: List<VideoItem>,
        val errors: Int,
        val completed: Int,
        val total: Int,
        val cancelled: Boolean,
    )

    fun search(
        sources: List<VideoSource>,
        terms: List<String>,
        demands: List<Triple<Long, String, String>>,
        fromMs: Long,
        toMs: Long,
        cancelled: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ): Outcome {
        val collected = linkedMapOf<String, VideoItem>()
        val jobs = buildJobs(sources, terms, demands)
        var completed = 0
        var errors = 0

        for (job in jobs) {
            if (cancelled()) break
            onProgress(Progress(
                completed, jobs.size, collected.size, errors,
                job.source.name, job.query.ifBlank { "varredura da fonte" },
            ))

            val candidates = runCatching {
                if (job.scanSource) collectSource(job.source)
                else collectForQuery(job.source, job.query)
            }.getOrElse {
                errors++
                emptyList()
            }

            if (cancelled()) break

            // O Windows enriquece os links candidatos abrindo a própria página do vídeo.
            // Sem isso, muitos portais entregam apenas título/link: a data fica 0 e o
            // resumo não contém a tag, então o filtro Android descartava o resultado.
            var resolvedInJob = 0
            val resolveLimit = if (job.scanSource) MAX_SCAN_RESOLVE_PER_JOB else MAX_RESOLVED_PER_QUERY
            for ((candidateIndex, candidate) in candidates.withIndex()) {
                if (cancelled()) break
                if ((candidateIndex + 1) % 4 == 0) {
                    onProgress(Progress(
                        completed, jobs.size, collected.size, errors,
                        job.source.name, "Verificando dados dos vídeos (${candidateIndex + 1}/${candidates.size})",
                    ))
                }

                val shallowBody = "${candidate.title} ${candidate.summary}"
                val shallowTermMatch = terms.any { phraseMatches(shallowBody, it) }
                val shallowDemandMatch = demands.any { demand ->
                    sourceMatchesDemand(job.source, demand.second) && phraseMatches(shallowBody, demand.third)
                }
                val requiresPageResolution = candidate.publishedAt <= 0L ||
                    (!shallowTermMatch && !shallowDemandMatch) || candidate.summary.length < MIN_USEFUL_SUMMARY

                var prepared = candidate
                if (requiresPageResolution && resolvedInJob < resolveLimit && isSpecificVideoUrl(job.source, candidate.link)) {
                    resolvedInJob++
                    prepared = runCatching {
                        resolveDirectVideoPage(job.source, candidate)
                    }.getOrElse { candidate }
                }

                val enriched = match(prepared, job.source, terms, demands, fromMs, toMs) ?: continue
                val key = canonical(enriched.link)
                val previous = collected[key]
                collected[key] = if (previous == null) enriched else merge(previous, enriched)
            }

            completed++
            onProgress(Progress(
                completed, jobs.size, collected.size, errors,
                job.source.name, job.query.ifBlank { "varredura da fonte" },
            ))
        }

        return Outcome(
            items = collected.values.sortedByDescending { it.publishedAt },
            errors = errors,
            completed = completed,
            total = jobs.size,
            cancelled = cancelled(),
        )
    }

    private data class Job(
        val source: VideoSource,
        val query: String,
        val scanSource: Boolean,
    )

    private fun buildJobs(
        sources: List<VideoSource>,
        terms: List<String>,
        demands: List<Triple<Long, String, String>>,
    ): List<Job> = buildList {
        sources.forEach { source ->
            val queries = linkedSetOf<String>()
            terms.filter { it.isNotBlank() }.forEach { queries += it }
            demands.filter { sourceMatchesDemand(source, it.second) }
                .map { it.third.trim() }
                .filter { it.isNotBlank() }
                .forEach { queries += it }

            // Portais com modo de varredura também devem receber consultas por termo.
            // Antes, alguns eram varridos pela página inicial e só depois filtrados localmente.
            val portalSupportsTermSearch =
                "globoplay" in source.id.lowercase(Locale.ROOT) ||
                    source.id.startsWith("video-r7-") ||
                    source.id.startsWith("video-band") ||
                    source.id == "video-sbt-news" ||
                    source.id == "video-cnn-brasil"

            if (isSourceScanMode(source)) {
                add(Job(source, "", true))
                if (portalSupportsTermSearch && source.searchUrlTemplate.isNotBlank()) {
                    queries.forEach { add(Job(source, it, false)) }
                }
            } else {
                queries.forEach { add(Job(source, it, false)) }
            }
        }
    }

    private fun isSourceScanMode(source: VideoSource): Boolean {
        val joined = "${source.id} ${source.landingUrl} ${source.searchUrlTemplate}".lowercase(Locale.ROOT)
        return source.youtubeHandle.isNotBlank() ||
            "globoplay" in joined ||
            source.id.startsWith("video-r7-") ||
            source.id.startsWith("video-band") ||
            source.id == "video-sbt-news" ||
            source.id == "video-cnn-brasil"
    }

    private fun collectSource(source: VideoSource): List<VideoItem> {
        if (source.youtubeHandle.isNotBlank()) {
            val youtube = runCatching { collectYouTube(source) }.getOrDefault(emptyList())
            if (youtube.isNotEmpty()) return youtube
        }

        val globoplaySource =
            "globoplay" in source.id.lowercase(Locale.ROOT) ||
                "globoplay.globo.com" in source.landingUrl.lowercase(Locale.ROOT) ||
                "globoplay.globo.com" in source.searchUrlTemplate.lowercase(Locale.ROOT)
        val urls = linkedSetOf<String>()
        // Algumas fontes regionais usam landingUrl apontando para um vídeo fixo/antigo.
        if (globoplaySource && source.searchUrlTemplate.isNotBlank()) {
            urls += buildSearchUrl(source, "")
        }
        if (source.landingUrl.isNotBlank()) urls += source.landingUrl
        if (source.youtubeHandle.isNotBlank()) {
            val handle = source.youtubeHandle.trim()
            val normalized = if (handle.startsWith("@")) handle else "@$handle"
            urls += "https://www.youtube.com/$normalized/videos"
        }
        if (urls.isEmpty() && source.searchUrlTemplate.isNotBlank()) {
            urls += buildSearchUrl(source, source.searchPrefix)
        }

        val out = linkedMapOf<String, VideoItem>()
        val attemptedUrls = linkedSetOf<String>()
        var lastFailure: Throwable? = null
        urls.forEach { url ->
            attemptedUrls += url
            runCatching {
                parseDocument(fetch(url), source, url, 44).forEach { item ->
                    out.putIfAbsent(canonical(item.link), item)
                }
            }.onFailure { lastFailure = it }
        }
        // Se a página inicial não expõe itens, tenta a busca cadastrada para a fonte.
        if (out.isEmpty() && source.searchUrlTemplate.isNotBlank() && source.searchPrefix.isNotBlank()) {
            val fallbackUrl = buildSearchUrl(source, "")
            if (fallbackUrl !in attemptedUrls) {
                runCatching {
                    parseDocument(fetch(fallbackUrl), source, fallbackUrl, 44).forEach { item ->
                        out.putIfAbsent(canonical(item.link), item)
                    }
                }.onFailure { lastFailure = it }
            }
        }
        if (out.isEmpty()) lastFailure?.let { throw it }
        return out.values.toList()
    }

    private fun collectYouTube(source: VideoSource): List<VideoItem> {
        val handle = source.youtubeHandle.trim()
        val normalized = if (handle.startsWith("@")) handle else "@$handle"
        val pageUrl = "https://www.youtube.com/$normalized/videos"
        val page = fetch(pageUrl)
        val html = page.outerHtml()
        val capturedAt = System.currentTimeMillis()

        // Igual ao Windows: a aba /videos costuma trazer até 30-40 itens; o feed
        // sozinho normalmente expõe só os 15 mais recentes. As duas fontes são unidas.
        val tabItems = parseYouTubeVideosTab(source, html, capturedAt)
        val channelId = sequenceOf(
            Regex("\"channelId\"\\s*:\\s*\"(UC[A-Za-z0-9_-]{20,})\"").find(html)?.groupValues?.getOrNull(1),
            Regex("\"externalId\"\\s*:\\s*\"(UC[A-Za-z0-9_-]{20,})\"").find(html)?.groupValues?.getOrNull(1),
            Regex("\"browseId\"\\s*:\\s*\"(UC[A-Za-z0-9_-]{20,})\"").find(html)?.groupValues?.getOrNull(1),
            page.selectFirst("meta[itemprop=channelId]")?.attr("content"),
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

        val feedItems = if (channelId.isBlank()) {
            emptyList()
        } else {
            runCatching {
                val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
                val xml = Jsoup.connect(feedUrl)
                    .userAgent(BROWSER_UA)
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.7")
                    .timeout(18_000)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .maxBodySize(MAX_HTML_BODY_BYTES)
                    .parser(org.jsoup.parser.Parser.xmlParser())
                    .get()

                xml.select("entry").mapNotNull { entry ->
                    val title = entry.selectFirst("title")?.text()?.trim().orEmpty()
                    val link = entry.selectFirst("link[href]")?.attr("href")?.trim().orEmpty()
                    val published = entry.selectFirst("published")?.text()?.trim().orEmpty()
                    if (!usefulTitle(title) || !isYouTubeVideoLink(link)) return@mapNotNull null
                    val description = entry.selectFirst("description, media|description")?.text()?.trim().orEmpty()
                    VideoItem(
                        title = title,
                        sourceId = source.id,
                        sourceName = source.name,
                        publishedAt = parseDate(published) ?: 0L,
                        link = canonical(link),
                        summary = listOf(description, "Canal oficial • ${source.group}")
                            .filter { it.isNotBlank() }.distinct().joinToString(" • ").take(1000),
                    )
                }
            }.getOrDefault(emptyList())
        }

        // Feed tem data absoluta, portanto prevalece sobre a data relativa do HTML.
        val merged = linkedMapOf<String, VideoItem>()
        (feedItems + tabItems).forEach { item ->
            val key = canonical(item.link)
            val previous = merged[key]
            if (previous == null || (previous.publishedAt <= 0L && item.publishedAt > 0L)) {
                merged[key] = item
            }
        }

        // Alguns canais não expõem channelId ou feed. Mantém o fallback HTML comum.
        if (merged.isEmpty()) {
            parseDocument(page, source, pageUrl, MAX_YOUTUBE_ITEMS_PER_SCAN).forEach { item ->
                merged.putIfAbsent(canonical(item.link), item)
            }
        }
        return merged.values.take(MAX_YOUTUBE_ITEMS_PER_SCAN)
    }

    /** Lê o JSON público videoRenderer da aba YouTube, como parse_videos_tab no Windows. */
    private fun parseYouTubeVideosTab(
        source: VideoSource,
        html: String,
        capturedAt: Long,
    ): List<VideoItem> {
        if (html.isBlank()) return emptyList()
        val marker = Regex("\"videoRenderer\"\\s*:\\s*\\{")
        val found = linkedMapOf<String, VideoItem>()
        var cursor = 0

        while (cursor < html.length && found.size < MAX_YOUTUBE_ITEMS_PER_SCAN) {
            val match = marker.find(html, cursor) ?: break
            val openBrace = match.range.last
            cursor = openBrace + 1
            val json = balancedJsonObject(html, openBrace) ?: continue
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: continue
            val videoId = obj.optString("videoId").trim()
            if (videoId.length < 6) continue

            val title = jsonText(obj.optJSONObject("title"))
            if (!usefulTitle(title)) continue
            val publishedText = jsonText(obj.optJSONObject("publishedTimeText"))
            val description = jsonText(obj.optJSONObject("descriptionSnippet"))
            val link = "https://www.youtube.com/watch?v=$videoId"
            val summary = listOf(description, "Canal oficial • ${source.group} • Aba Vídeos")
                .filter { it.isNotBlank() }.distinct().joinToString(" • ").take(1000)

            found.putIfAbsent(
                canonical(link),
                VideoItem(
                    title = title.take(220),
                    sourceId = source.id,
                    sourceName = source.name,
                    publishedAt = parseRelativeTimestamp(publishedText, capturedAt),
                    link = link,
                    summary = summary,
                ),
            )
        }
        return found.values.toList()
    }

    /** Obtém o fim de um objeto JSON respeitando strings e escapes. */
    private fun balancedJsonObject(text: String, start: Int): String? {
        if (start !in text.indices || text[start] != '{') return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val ch = text[index]
            if (inString) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') inString = false
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun jsonText(value: JSONObject?): String {
        if (value == null) return ""
        value.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it.trim() }
        val runs = value.optJSONArray("runs")
        if (runs != null) {
            val joined = buildString {
                for (index in 0 until runs.length()) {
                    append(runs.optJSONObject(index)?.optString("text").orEmpty())
                }
            }.trim()
            if (joined.isNotBlank()) return joined
        }
        return value.optJSONObject("accessibility")
            ?.optJSONObject("accessibilityData")
            ?.optString("label")
            .orEmpty()
            .trim()
    }

    private fun parseRelativeTimestamp(raw: String, capturedAt: Long): Long {
        val text = normalize(raw)
        val amount = Regex("(?<!\\d)(\\d+)(?!\\d)").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: return 0L
        val multiplier = when {
            "segundo" in text || "second" in text -> 1_000L
            "minuto" in text || "minute" in text -> 60_000L
            "hora" in text || "hour" in text -> 60L * 60_000L
            "dia" in text || "day" in text -> 24L * 60L * 60_000L
            "semana" in text || "week" in text -> 7L * 24L * 60L * 60_000L
            "mes" in text || "month" in text -> 30L * 24L * 60L * 60_000L
            "ano" in text || "year" in text -> 365L * 24L * 60L * 60_000L
            else -> return 0L
        }
        return (capturedAt - amount * multiplier).coerceAtLeast(1L)
    }

    private fun isYouTubeVideoLink(link: String): Boolean = runCatching {
        val uri = URI(link)
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
    }.getOrDefault(false) && youtubeVideoId(URI(link)).length >= 6

    private fun collectForQuery(source: VideoSource, query: String): List<VideoItem> {
        if (source.searchUrlTemplate.isBlank()) return collectSource(source)
        val url = buildSearchUrl(source, query)
        return parseDocument(fetch(url), source, url, 40)
    }

    private fun buildSearchUrl(source: VideoSource, raw: String): String {
        val query = listOf(source.searchPrefix, raw)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        val encoded = URLEncoder.encode(query, "UTF-8")
        return source.searchUrlTemplate
            .replace("{query}", encoded)
            .replace("{term}", encoded)
            .replace("%s", encoded)
    }

    private fun fetch(url: String): Document = Jsoup.connect(url)
        .userAgent(BROWSER_UA)
        .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.7")
        .referrer("https://www.google.com/")
        .timeout(18_000)
        .followRedirects(true)
        .ignoreHttpErrors(true)
        .maxBodySize(MAX_HTML_BODY_BYTES)
        .get()

    private fun parseDocument(
        doc: Document,
        source: VideoSource,
        baseUrl: String,
        limit: Int,
    ): List<VideoItem> {
        val out = linkedMapOf<String, VideoItem>()
        doc.select("a[href]").forEach { anchor ->
            if (out.size >= limit) return@forEach
            val link = absolute(baseUrl, anchor.attr("href"))
            if (!isSpecificVideoUrl(source, link)) return@forEach
            val title = titleOf(anchor)
            if (!usefulTitle(title)) return@forEach
            val container = meaningfulContainer(anchor)
            val summary = container?.text()?.trim().orEmpty().take(700)
            val published = publishedAt(container ?: anchor)
            out.putIfAbsent(
                canonical(link),
                VideoItem(
                    title = title,
                    sourceId = source.id,
                    sourceName = source.name,
                    publishedAt = published,
                    link = link,
                    summary = summary,
                ),
            )
        }
        return out.values.toList()
    }

    private fun titleOf(anchor: Element): String {
        val values = listOf(
            anchor.attr("aria-label"),
            anchor.attr("title"),
            anchor.selectFirst("img[alt]")?.attr("alt").orEmpty(),
            anchor.text(),
            anchor.parent()?.selectFirst("h1,h2,h3,h4,[class*=title],[class*=headline]")?.text().orEmpty(),
        )
        return values.firstOrNull { usefulTitle(it.trim()) }?.trim().orEmpty()
    }

    private fun meaningfulContainer(anchor: Element): Element? {
        var current = anchor.parent()
        repeat(4) {
            if (current == null) return null
            val text = current!!.text()
            if (text.length in 20..1500) return current
            current = current!!.parent()
        }
        return anchor.parent()
    }

    private fun publishedAt(element: Element): Long {
        val rawValues = listOf(
            element.selectFirst("time[datetime]")?.attr("datetime").orEmpty(),
            element.selectFirst("meta[itemprop=datePublished]")?.attr("content").orEmpty(),
            element.selectFirst("meta[property=article:published_time]")?.attr("content").orEmpty(),
        )
        for (raw in rawValues) {
            if (raw.isBlank()) continue
            val parsed = parseDate(raw)
            if (parsed != null) return parsed
        }

        val text = element.text()
        val datePatterns = listOf(
            Regex("\\b(\\d{2}/\\d{2}/\\d{4})(?:\\s+(\\d{1,2}:\\d{2}))?\\b"),
            Regex("\\b(\\d{4}-\\d{2}-\\d{2})(?:[T\\s](\\d{1,2}:\\d{2})(?::\\d{2})?)?\\b"),
        )
        for (regex in datePatterns) {
            val match = regex.find(text) ?: continue
            val joined = listOfNotNull(
                match.groupValues.getOrNull(1),
                match.groupValues.getOrNull(2),
            ).filter { it.isNotBlank() }.joinToString(" ")
            val parsed = parseDate(joined)
            if (parsed != null) return parsed
        }
        // Não inventa que o vídeo foi publicado agora quando a página não informa data.
        return 0L
    }

    private fun parseDate(raw: String): Long? {
        val value = raw.trim()
        val normalizedIso = value
            .replace(Regex("Z$"), "+0000")
            .replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ" to Locale.US,
            "yyyy-MM-dd'T'HH:mm:ssZ" to Locale.US,
            "yyyy-MM-dd'T'HH:mmZ" to Locale.US,
            "dd/MM/yyyy HH:mm" to Locale("pt", "BR"),
            "dd/MM/yyyy" to Locale("pt", "BR"),
            "yyyy-MM-dd HH:mm" to Locale.US,
            "yyyy-MM-dd" to Locale.US,
            "EEE, dd MMM yyyy HH:mm:ss z" to Locale.US,
        )
        for ((pattern, locale) in patterns) {
            val candidate = if (pattern.contains("'T'")) normalizedIso else value
            val parsed = runCatching {
                SimpleDateFormat(pattern, locale).apply { isLenient = false }.parse(candidate)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }

    /**
     * Equivalente ao DirectVideoPageResolver do Windows. A listagem do portal
     * geralmente não contém a data real nem a descrição completa; a página
     * individual enriquece esses campos antes de filtrar o período e as tags.
     */
    private fun resolveDirectVideoPage(source: VideoSource, candidate: VideoItem): VideoItem {
        if (!isSpecificVideoUrl(source, candidate.link)) return candidate
        val doc = fetch(candidate.link)
        val finalPageUrl = doc.location().ifBlank { candidate.link }
        val canonicalCandidate = listOf(
            doc.selectFirst("link[rel=canonical]")?.absUrl("href").orEmpty(),
            doc.selectFirst("meta[property=og:url]")?.attr("content").orEmpty(),
            finalPageUrl,
        ).firstOrNull { isSpecificVideoUrl(source, it) && it.startsWith("http", ignoreCase = true) }
            .orEmpty().ifBlank { candidate.link }

        fun meta(vararg selectors: String): List<String> = selectors.mapNotNull { selector ->
            doc.selectFirst(selector)?.let { element ->
                (element.attr("content").ifBlank { element.attr("datetime") }
                    .ifBlank { element.text() }).trim().takeIf { it.isNotBlank() }
            }
        }

        val title = (meta(
            "meta[property=og:title]", "meta[name=twitter:title]", "meta[itemprop=name]",
        ) + listOfNotNull(doc.selectFirst("h1")?.text(), candidate.title, doc.title()))
            .firstOrNull { usefulTitle(it) }
            ?.trim()
            ?.take(220)
            .orEmpty()
            .ifBlank { candidate.title }

        val summaries = linkedSetOf<String>()
        summaries += meta(
            "meta[property=og:description]", "meta[name=twitter:description]",
            "meta[name=description]", "meta[itemprop=description]",
            "meta[property=og:video:tag]",
        )
        summaries += candidate.summary.trim()

        // Portais frequentemente colocam a descrição completa somente dentro de
        // VideoObject/JSON-LD ou do JSON inicial da página.
        val embeddedTextPattern = Regex(
            """["'](?:description|seoDescription|summary|caption|articleBody|headline|alternativeHeadline)["']\s*:\s*["']((?:\\.|[^"'\\]){2,1800})["']""",
            RegexOption.IGNORE_CASE,
        )
        doc.select("script[type=application/ld+json], script").take(80).forEach { script ->
            val raw = script.data().ifBlank { script.html() }
            embeddedTextPattern.findAll(raw).take(12).forEach { match ->
                val value = match.groupValues.getOrNull(1).orEmpty()
                    .replace("\\n", " ")
                    .replace("\\u0026", "&")
                    .replace("\\/", "/")
                    .replace("\\\"", "\"")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (value.length >= 20 && usefulTitle(value)) summaries += value
            }
        }

        val publishedValues = mutableListOf<String>()
        publishedValues += meta(
            "meta[property=article:published_time]", "meta[property=og:published_time]",
            "meta[name=date]", "meta[name=publish-date]", "meta[name=parsely-pub-date]",
            "meta[itemprop=datePublished]", "meta[itemprop=uploadDate]",
            "time[datetime]", "[itemprop=datePublished]", "[itemprop=uploadDate]",
        )
        doc.select("script[type=application/ld+json], script").take(80).forEach { script ->
            val raw = script.data().ifBlank { script.html() }
            Regex(
                """["'](?:datePublished|uploadDate|dateCreated|publishedAt|publicationDate|publishedDate)["']\s*:\s*["']([^"'\\]{4,80})["']""",
                RegexOption.IGNORE_CASE,
            ).findAll(raw).take(8).forEach { publishedValues += it.groupValues[1] }
        }
        val capturedAt = System.currentTimeMillis()
        val published = publishedValues.firstNotNullOfOrNull { raw ->
            parseDate(raw) ?: parseRelativeTimestamp(raw, capturedAt).takeIf { it > 0L }
        } ?: candidate.publishedAt

        val summary = summaries.map { it.trim() }
            .filter { it.length >= 12 }
            .distinct()
            .joinToString(" • ")
            .take(MAX_ENRICHED_SUMMARY_LENGTH)
            .ifBlank { candidate.summary }

        return candidate.copy(
            title = title,
            publishedAt = published,
            link = canonical(canonicalCandidate),
            summary = summary,
        )
    }

    private fun match(
        item: VideoItem,
        source: VideoSource,
        terms: List<String>,
        demands: List<Triple<Long, String, String>>,
        fromMs: Long,
        toMs: Long,
    ): VideoItem? {
        if (item.publishedAt < fromMs || item.publishedAt > toMs) return null
        val body = "${item.title} ${item.summary}"
        val matchedTerms = terms.filter { phraseMatches(body, it) }.distinct()
        val matchedDemand = demands.firstOrNull { demand ->
            sourceMatchesDemand(source, demand.second) && phraseMatches(body, demand.third)
        }
        if (matchedTerms.isEmpty() && matchedDemand == null) return null
        return item.copy(
            matchedTerm = matchedTerms.joinToString(", "),
            matchedDemand = matchedDemand?.let { "${it.second} • ${it.third}" }.orEmpty(),
        )
    }

    private fun merge(previous: VideoItem, incoming: VideoItem): VideoItem {
        val terms = (previous.matchedTerm.split(",") + incoming.matchedTerm.split(","))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return incoming.copy(
            title = if (incoming.title.length >= previous.title.length) incoming.title else previous.title,
            publishedAt = incoming.publishedAt.takeIf { it > 0L } ?: previous.publishedAt,
            summary = if (incoming.summary.length >= previous.summary.length) incoming.summary else previous.summary,
            matchedTerm = terms.joinToString(", "),
            matchedDemand = incoming.matchedDemand.ifBlank { previous.matchedDemand },
        )
    }

    private fun sourceMatchesDemand(source: VideoSource, vehicle: String): Boolean {
        if (vehicle.isBlank()) return true
        val wanted = normalize(vehicle).replace(" ", "")
        return (listOf(source.name, source.group) + source.aliases).any { candidate ->
            val actual = normalize(candidate).replace(" ", "")
            actual == wanted ||
                (wanted.length >= 4 && wanted in actual) ||
                (actual.length >= 4 && actual in wanted)
        }
    }

    private fun phraseMatches(text: String, phrase: String): Boolean {
        val haystack = normalize(text)
        val wanted = normalize(phrase)
        if (wanted.isBlank()) return false
        if (" $wanted " in " $haystack ") return true
        val hay = haystack.split(" ").filter { it.isNotBlank() }.toSet()
        val wantedTokens = wanted.split(" ").filter { it.isNotBlank() }
        if (wantedTokens.size == 1) return tokenEquivalent(hay, wantedTokens.first())
        val stop = setOf("de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas", "a", "o", "as", "os")
        val meaningful = wantedTokens.filter { it.length >= 3 && it !in stop }
        return meaningful.isNotEmpty() && meaningful.all { tokenEquivalent(hay, it) }
    }

    private fun tokenEquivalent(hay: Set<String>, wanted: String): Boolean {
        if (wanted in hay) return true
        if (wanted.length < 5) return false
        val variants = linkedSetOf(wanted)
        when {
            wanted.endsWith("r") -> variants += wanted + "es"
            wanted.endsWith("l") -> variants += wanted.dropLast(1) + "is"
            wanted.endsWith("m") -> variants += wanted.dropLast(1) + "ns"
            wanted.endsWith("ao") -> {
                variants += wanted.dropLast(2) + "oes"
                variants += wanted.dropLast(2) + "aes"
                variants += wanted.dropLast(2) + "aos"
            }
            !wanted.endsWith("s") -> variants += wanted + "s"
        }
        return hay.any { actual -> actual in variants || (actual.length >= 5 && wanted in reverseVariants(actual)) }
    }

    private fun reverseVariants(token: String): Set<String> {
        val values = linkedSetOf(token)
        if (token.endsWith("res") && token.length > 5) values += token.dropLast(2)
        if (token.endsWith("is") && token.length > 5) values += token.dropLast(2) + "l"
        if (token.endsWith("ns") && token.length > 5) values += token.dropLast(2) + "m"
        if (token.endsWith("s") && token.length > 5) values += token.dropLast(1)
        return values
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun usefulTitle(value: String): Boolean {
        val title = value.trim()
        if (title.length < 8) return false
        val normalized = normalize(title)
        return normalized !in setOf(
            "videos", "video", "todos os videos", "ultimos videos", "mais videos",
            "ver videos", "ver todos os videos", "ao vivo", "assistir ao vivo", "ver mais",
        )
    }

    private fun isSpecificVideoUrl(source: VideoSource, url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        if (host.isBlank()) return false

        if (host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")) {
            return youtubeVideoId(uri).length >= 6
        }

        val path = uri.path.orEmpty()
        val normalizedPath = path.lowercase(Locale.ROOT).trimEnd('/').ifBlank { "/" }
        if (
            normalizedPath in setOf("/videos", "/video", "/ao-vivo", "/busca", "/search", "/categorias/jornalismo") ||
            "/busca" in normalizedPath || "/search" in normalizedPath
        ) return false

        val globoplaySource =
            "globoplay" in source.id.lowercase(Locale.ROOT) ||
                "globoplay.globo.com" in source.landingUrl.lowercase(Locale.ROOT) ||
                "globoplay.globo.com" in source.searchUrlTemplate.lowercase(Locale.ROOT)
        if (globoplaySource) return Regex("/v/[0-9]+/?$", RegexOption.IGNORE_CASE).containsMatchIn(path)

        return when {
            source.id == "video-r7-record" ->
                hasSpecificSuffix(path, "/videos/") || hasSpecificSuffix(path, "/video/")
            source.id.startsWith("video-r7-") -> source.linkHints.any { hasSpecificSuffix(path, it) }
            source.id == "video-sbt-news" -> hasSpecificSuffix(path, "/videos/")
            source.id == "video-cnn-brasil" ->
                hasSpecificSuffix(path, "/videos/") || hasSpecificSuffix(path, "/video/")
            source.id.startsWith("video-band") -> source.linkHints.any { hasSpecificSuffix(path, it) }
            else -> source.linkHints.any { hasSpecificSuffix(path, it) }
        }
    }

    private fun hasSpecificSuffix(path: String, marker: String): Boolean {
        val lowerPath = path.lowercase(Locale.ROOT)
        val index = lowerPath.indexOf(marker.lowercase(Locale.ROOT))
        if (index < 0) return false
        val suffix = path.substring(index + marker.length).trim('/')
        if (suffix.length < 4) return false
        val normalized = normalize(suffix)
        return normalized.isNotBlank() && normalized !in setOf(
            "videos", "video", "ao vivo", "todos os videos", "ultimos videos",
        )
    }

    private fun youtubeVideoId(uri: URI): String {
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val path = uri.path.orEmpty()
        return when {
            host == "youtu.be" -> path.trim('/').substringBefore('/')
            path.equals("/watch", ignoreCase = true) ->
                uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.substringAfter('=').orEmpty()
            path.startsWith("/shorts/", ignoreCase = true) -> path.substringAfter("/shorts/").substringBefore('/')
            path.startsWith("/live/", ignoreCase = true) -> path.substringAfter("/live/").substringBefore('/')
            else -> ""
        }
    }

    private fun absolute(base: String, raw: String): String {
        val cleaned = raw.trim().replace("&amp;", "&")
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) return cleaned
        return runCatching { URI(base).resolve(cleaned).toString() }.getOrDefault(cleaned)
    }

    private fun canonical(url: String): String = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        if (host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")) {
            val id = youtubeVideoId(uri)
            if (id.isNotBlank()) return@runCatching "https://www.youtube.com/watch?v=$id"
        }
        URI(uri.scheme, uri.authority, uri.path, null, null)
            .toString().trimEnd('/').lowercase(Locale.ROOT)
    }.getOrDefault(url.trimEnd('/').lowercase(Locale.ROOT))

    companion object {
        private const val MAX_YOUTUBE_ITEMS_PER_SCAN = 40
        private const val MAX_SCAN_RESOLVE_PER_JOB = 40
        private const val MAX_RESOLVED_PER_QUERY = 8
        private const val MIN_USEFUL_SUMMARY = 50
        private const val MAX_ENRICHED_SUMMARY_LENGTH = 1800
        private const val MAX_HTML_BODY_BYTES = 8_000_000
        private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36"
    }
}
