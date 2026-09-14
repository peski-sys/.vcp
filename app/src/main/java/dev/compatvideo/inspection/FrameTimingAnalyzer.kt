package dev.compatvideo.inspection

import kotlin.math.abs

object FrameTimingAnalyzer {
    fun analyze(
        presentationTimesUs: List<Long>,
        completeTrack: Boolean,
    ): FrameTimingInfo {
        val orderedTimes = presentationTimesUs.distinct().sorted()
        if (orderedTimes.size < 3) {
            return FrameTimingInfo(
                classification = FrameRateClassification.INSUFFICIENT_DATA,
                averageFramesPerSecond = null,
                minimumFramesPerSecond = null,
                maximumFramesPerSecond = null,
                sampledFrameCount = orderedTimes.size,
                completeTrack = completeTrack,
            )
        }

        val deltas = orderedTimes.zipWithNext { first, second -> second - first }
            .filter { it > 0 }
        if (deltas.size < 2) {
            return FrameTimingInfo(
                classification = FrameRateClassification.INSUFFICIENT_DATA,
                averageFramesPerSecond = null,
                minimumFramesPerSecond = null,
                maximumFramesPerSecond = null,
                sampledFrameCount = orderedTimes.size,
                completeTrack = completeTrack,
            )
        }

        val averageDelta = (orderedTimes.last() - orderedTimes.first()).toDouble() /
            (orderedTimes.size - 1)
        val medianDelta = deltas.sorted()[deltas.size / 2].toDouble()
        val maximumDeviation = deltas.maxOf { abs(it - medianDelta) } / medianDelta

        return FrameTimingInfo(
            classification = if (maximumDeviation > VARIABLE_RATE_THRESHOLD) {
                FrameRateClassification.VARIABLE_SAMPLED
            } else {
                FrameRateClassification.CONSTANT_SAMPLED
            },
            averageFramesPerSecond = MICROS_PER_SECOND / averageDelta,
            minimumFramesPerSecond = MICROS_PER_SECOND / deltas.max().toDouble(),
            maximumFramesPerSecond = MICROS_PER_SECOND / deltas.min().toDouble(),
            sampledFrameCount = orderedTimes.size,
            completeTrack = completeTrack,
        )
    }

    private const val MICROS_PER_SECOND = 1_000_000.0
    private const val VARIABLE_RATE_THRESHOLD = 0.03
}
