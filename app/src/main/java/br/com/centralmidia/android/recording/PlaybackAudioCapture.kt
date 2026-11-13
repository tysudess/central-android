package br.com.centralmidia.android.recording

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max

/** Captura o áudio reproduzido por outros aplicativos (Android 10/API 29+). */
internal class PlaybackAudioCapture(
    projection: MediaProjection,
    private val outputFile: File,
) {
    companion object {
        private const val SAMPLE_RATE = 48_000
        private const val CHANNELS = 2
        private const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_STEREO
        private const val BYTES_PER_SAMPLE = 2
        private const val CHUNK_BYTES = 1_920 // 10 ms, 48 kHz, estéreo, PCM 16-bit
        private const val BIT_RATE = 128_000
        private const val AAC_OBJECT_LC = 2

        /** Combina o áudio AAC capturado com as faixas do vídeo gravado. */
        fun mergeWithVideo(videoFile: File, audioFile: File): File {
            val outputFile = File(
                videoFile.parentFile,
                "with-audio-${System.currentTimeMillis()}.mp4",
            )
            val videoExtractor = MediaExtractor()
            val audioExtractor = MediaExtractor()
            val muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            var muxerStarted = false

            data class ExtractorState(
                val extractor: MediaExtractor,
                val outputTracks: Map<Int, Int>,
            )

            try {
                videoExtractor.setDataSource(videoFile.absolutePath)
                audioExtractor.setDataSource(audioFile.absolutePath)

                val maxInput = intArrayOf(1_048_576)
                fun addTracks(
                    extractor: MediaExtractor,
                    allowVideo: Boolean,
                    allowAudio: Boolean,
                ): Map<Int, Int> {
                    val outputTracks = mutableMapOf<Int, Int>()
                    for (index in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(index)
                        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                        val keep = (allowVideo && mime.startsWith("video/")) ||
                            (allowAudio && mime.startsWith("audio/"))
                        if (!keep) continue
                        outputTracks[index] = muxer.addTrack(format)
                        extractor.selectTrack(index)
                        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            maxInput[0] = max(
                                maxInput[0],
                                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE),
                            )
                        }
                        if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_ROTATION)) {
                            runCatching { muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION)) }
                        }
                    }
                    return outputTracks
                }

                // Registra a faixa interna primeiro para que ela seja a trilha de áudio principal.
                val audioTracks = addTracks(audioExtractor, allowVideo = false, allowAudio = true)
                val videoTracks = addTracks(videoExtractor, allowVideo = true, allowAudio = true)
                check(audioTracks.isNotEmpty()) { "O arquivo temporário não contém uma faixa de áudio válida." }
                check(videoTracks.values.isNotEmpty()) { "O vídeo original não contém uma faixa de vídeo válida." }

                muxer.start()
                muxerStarted = true

                val states = listOf(
                    ExtractorState(audioExtractor, audioTracks),
                    ExtractorState(videoExtractor, videoTracks),
                )
                val buffer = ByteBuffer.allocateDirect(maxInput[0].coerceAtMost(16 * 1024 * 1024))
                val info = MediaCodec.BufferInfo()

                while (true) {
                    val next = states
                        .filter { it.extractor.sampleTrackIndex >= 0 && it.extractor.sampleTime >= 0L }
                        .minByOrNull { it.extractor.sampleTime }
                        ?: break

                    val inputTrack = next.extractor.sampleTrackIndex
                    val outputTrack = next.outputTracks[inputTrack]
                    if (outputTrack == null) {
                        next.extractor.advance()
                        continue
                    }

                    buffer.clear()
                    val sampleSize = next.extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        next.extractor.advance()
                        continue
                    }

                    info.set(
                        0,
                        sampleSize,
                        next.extractor.sampleTime,
                        next.extractor.sampleFlags,
                    )
                    muxer.writeSampleData(outputTrack, buffer, info)
                    next.extractor.advance()
                }

                muxer.stop()
                muxerStarted = false
                check(outputFile.exists() && outputFile.length() > 0L) {
                    "Não foi possível finalizar o vídeo com áudio."
                }
                return outputFile
            } catch (error: Throwable) {
                runCatching { outputFile.delete() }
                throw error
            } finally {
                if (muxerStarted) runCatching { muxer.stop() }
                runCatching { muxer.release() }
                runCatching { videoExtractor.release() }
                runCatching { audioExtractor.release() }
            }
        }
    }

    @Volatile
    private var stopRequested = false

    @Volatile
    private var paused = false

    @Volatile
    var failure: Throwable? = null
        private set

    private var muxerStarted = false
    private var muxerTrack = -1
    private var framesSubmitted = 0L

    private val audioRecord: AudioRecord
    private val encoder: MediaCodec
    private val muxer: MediaMuxer
    private var worker: Thread? = null

    init {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "A captura do áudio interno exige Android 10 ou superior."
        }
        outputFile.parentFile?.mkdirs()

        val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val inputFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_MASK)
            .build()

        val minimumBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_MASK,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBuffer > 0) { "O aparelho não disponibilizou entrada de áudio para captura." }

        audioRecord = AudioRecord.Builder()
            .setAudioFormat(inputFormat)
            .setBufferSizeInBytes(max(minimumBuffer * 2, CHUNK_BYTES * 4))
            .setAudioPlaybackCaptureConfig(playbackConfig)
            .build()
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            "Não foi possível inicializar a captura do áudio interno."
        }

        val encodedFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            SAMPLE_RATE,
            CHANNELS,
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, AAC_OBJECT_LC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK_BYTES * 4)
        }

        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(encodedFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun start() {
        check(worker == null) { "A captura de áudio já foi iniciada." }
        var encoderStarted = false
        var recordStarted = false
        try {
            encoder.start()
            encoderStarted = true
            audioRecord.startRecording()
            recordStarted = true
            worker = Thread(::captureLoop, "Central-Playback-Audio").apply {
                isDaemon = true
                start()
            }
        } catch (error: Throwable) {
            if (recordStarted) runCatching { audioRecord.stop() }
            runCatching { audioRecord.release() }
            if (encoderStarted) runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { muxer.release() }
            throw error
        }
    }

    fun setPaused(value: Boolean) {
        paused = value
    }

    /** Encerra a captura e retorna o MP4 de áudio quando ele foi gerado. */
    fun stopAndGetFile(): File? {
        stopRequested = true
        // Interrompe a leitura bloqueante imediatamente para que a finalização
        // não mantenha o serviço parado aguardando novos dados de áudio.
        runCatching { audioRecord.stop() }
        worker?.join(5_000L)
        if (worker?.isAlive == true) {
            worker?.interrupt()
            worker?.join(1_000L)
        }
        if (worker?.isAlive == true) {
            runCatching { outputFile.delete() }
            return null
        }
        failure?.let {
            runCatching { outputFile.delete() }
            throw IllegalStateException("Falha durante a captura do áudio interno: ${it.message}", it)
        }
        return outputFile.takeIf { it.exists() && it.length() > 0L }
    }

    private fun captureLoop() {
        val pcm = ByteArray(CHUNK_BYTES)
        try {
            while (!stopRequested) {
                val read = audioRecord.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                if (read < 0) {
                    if (stopRequested) break
                    error("Falha ao ler o áudio interno (código $read).")
                }

                if (read > 0 && !paused && !stopRequested) {
                    queuePcm(pcm, read)
                }
                drainEncoder(waitForData = false)
            }

            queueEndOfStream()
            drainEncoder(waitForData = true)
        } catch (error: Throwable) {
            failure = error
        } finally {
            runCatching { audioRecord.stop() }
            runCatching { audioRecord.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun queuePcm(pcm: ByteArray, byteCount: Int) {
        // Não descarta blocos quando o codificador está ocupado: isso criaria
        // cortes e dessincronização perceptíveis entre o áudio e o vídeo.
        val deadline = System.currentTimeMillis() + 1_000L
        var inputIndex = MediaCodec.INFO_TRY_AGAIN_LATER
        while (inputIndex < 0 && System.currentTimeMillis() < deadline) {
            inputIndex = encoder.dequeueInputBuffer(10_000L)
            if (inputIndex < 0) drainEncoder(waitForData = false)
        }
        check(inputIndex >= 0) { "O codificador de áudio não liberou um bloco de entrada." }

        val input = encoder.getInputBuffer(inputIndex)
            ?: error("O codificador de áudio não disponibilizou o buffer de entrada.")
        input.clear()
        val safeCount = byteCount.coerceAtMost(input.remaining())
        check(safeCount > 0) { "O bloco de áudio recebido está vazio." }
        input.put(pcm, 0, safeCount)

        val frames = safeCount / (CHANNELS * BYTES_PER_SAMPLE)
        val presentationTimeUs = framesSubmitted * 1_000_000L / SAMPLE_RATE
        framesSubmitted += frames
        encoder.queueInputBuffer(inputIndex, 0, safeCount, presentationTimeUs, 0)
    }

    private fun queueEndOfStream() {
        val deadline = System.currentTimeMillis() + 3_000L
        while (System.currentTimeMillis() < deadline) {
            val inputIndex = encoder.dequeueInputBuffer(10_000L)
            if (inputIndex >= 0) {
                val presentationTimeUs = framesSubmitted * 1_000_000L / SAMPLE_RATE
                encoder.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    presentationTimeUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                return
            }
        }
        error("Não foi possível finalizar a codificação do áudio.")
    }

    private fun drainEncoder(waitForData: Boolean) {
        val info = MediaCodec.BufferInfo()
        var outputEnded = false
        val deadline = System.currentTimeMillis() + 5_000L
        while (!outputEnded) {
            val outputIndex = encoder.dequeueOutputBuffer(info, if (waitForData) 10_000L else 0L)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!waitForData) return
                    check(System.currentTimeMillis() < deadline) {
                        "O codificador não concluiu a faixa de áudio."
                    }
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted) { "O formato do áudio mudou durante a gravação." }
                    muxerTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outputIndex >= 0 -> {
                    val output = encoder.getOutputBuffer(outputIndex)
                    val codecConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (info.size > 0 && output != null && muxerStarted && !codecConfig) {
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        muxer.writeSampleData(muxerTrack, output, info)
                    }
                    outputEnded = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    encoder.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
    }

}
