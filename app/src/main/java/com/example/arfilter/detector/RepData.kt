// ===== UPDATED: RepData.kt =====
package com.example.arfilter.detector

import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced rep data class with overlay line adherence metrics
 * Tracks how well each rep follows the AR overlay line guidance
 */
data class RepData(
    val repNumber: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val exercise: String,
    val tempo: String,

    // Movement Quality Metrics
    val totalDistance: Float,           // Total bar path distance
    val verticalRange: Float,           // Pure vertical displacement
    val avgVelocity: Float,             // Average movement speed
    val peakVelocity: Float,           // Fastest point in lift
    val pathDeviation: Float,          // How much bar drifted from overlay line
    val duration: Float,               // Total rep duration in seconds

    // Phase Breakdown
    val eccentricDuration: Float,      // Time going down
    val pauseDuration: Float,          // Time at bottom
    val concentricDuration: Float,     // Time going up

    // Quality Score (0-100) - now based on overlay line adherence
    val qualityScore: Float,           // Overall rep quality based on overlay line

    // NEW: Overlay Line Specific Metrics
    val overlayLineAdherence: Float = calculateOverlayLineAdherence(pathDeviation, qualityScore)
) {

    fun getFormattedTimestamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    fun getFormattedDuration(): String {
        return String.format("%.1f", duration)
    }

    fun getFormattedDistance(): String {
        return String.format("%.2f", totalDistance)
    }

    fun getQualityGrade(): String {
        return when {
            qualityScore >= 90 -> "A"
            qualityScore >= 80 -> "B"
            qualityScore >= 70 -> "C"
            qualityScore >= 60 -> "D"
            else -> "F"
        }
    }

    fun getOverlayLineAdherenceGrade(): String {
        return when {
            overlayLineAdherence >= 90 -> "Excellent"
            overlayLineAdherence >= 80 -> "Good"
            overlayLineAdherence >= 70 -> "Fair"
            overlayLineAdherence >= 60 -> "Poor"
            else -> "Very Poor"
        }
    }

    /**
     * Convert to CSV row format with overlay line adherence
     */
    fun toCsvRow(): String {
        return listOf(
            repNumber,
            getFormattedTimestamp(),
            exercise,
            tempo,
            String.format("%.2f", totalDistance),
            String.format("%.2f", verticalRange),
            String.format("%.2f", avgVelocity),
            String.format("%.2f", peakVelocity),
            String.format("%.2f", pathDeviation),
            getFormattedDuration(),
            String.format("%.1f", eccentricDuration),
            String.format("%.1f", pauseDuration),
            String.format("%.1f", concentricDuration),
            String.format("%.0f", qualityScore),
            getQualityGrade(),
            String.format("%.1f", overlayLineAdherence)
        ).joinToString(",")
    }

    /**
     * Get detailed rep analysis summary
     */
    fun getDetailedSummary(): String {
        return """
            Rep #$repNumber Analysis:
            - Exercise: $exercise ($tempo tempo)
            - Duration: ${getFormattedDuration()}s
            - Path Deviation: ${String.format("%.2f", pathDeviation)}cm from overlay line
            - Quality Score: ${String.format("%.0f", qualityScore)}/100 (${getQualityGrade()})
            - Overlay Line Adherence: ${String.format("%.1f", overlayLineAdherence)}% (${getOverlayLineAdherenceGrade()})
            - Phase Breakdown: ${String.format("%.1f", eccentricDuration)}s down, ${String.format("%.1f", pauseDuration)}s pause, ${String.format("%.1f", concentricDuration)}s up
        """.trimIndent()
    }

    /**
     * Get performance insights based on overlay line data
     */
    fun getPerformanceInsights(): List<String> {
        val insights = mutableListOf<String>()

        // Quality insights
        when {
            qualityScore >= 90 -> insights.add("✅ Excellent form - maintain this consistency")
            qualityScore >= 80 -> insights.add("✅ Good form - minor improvements possible")
            qualityScore >= 70 -> insights.add("⚠️ Acceptable form - focus on consistency")
            qualityScore >= 60 -> insights.add("⚠️ Form needs work - consider reducing weight")
            else -> insights.add("❌ Poor form - focus on technique over weight")
        }

        // Path deviation insights
        when {
            pathDeviation < 1.0f -> insights.add("✅ Excellent bar path - stayed close to overlay line")
            pathDeviation < 2.0f -> insights.add("✅ Good bar path - minor deviations from overlay line")
            pathDeviation < 3.0f -> insights.add("⚠️ Moderate deviation - focus on following overlay line")
            else -> insights.add("❌ High deviation - work on bar path consistency")
        }

        // Tempo insights
        when {
            duration < 1.5f -> insights.add("⚠️ Rep too fast - slow down for better control")
            duration > 8.0f -> insights.add("⚠️ Rep too slow - work on smooth movement")
            else -> insights.add("✅ Good tempo - maintain this pace")
        }

        // Phase balance insights
        val totalPhaseTime = eccentricDuration + pauseDuration + concentricDuration
        if (totalPhaseTime > 0) {
            val eccentricPercent = (eccentricDuration / totalPhaseTime) * 100
            val concentricPercent = (concentricDuration / totalPhaseTime) * 100

            if (eccentricPercent < 30) {
                insights.add("⚠️ Eccentric phase too fast - slow down the negative")
            }
            if (concentricPercent > 60) {
                insights.add("⚠️ Concentric phase too slow - work on explosive power")
            }
        }

        return insights
    }

    /**
     * CSV header row with overlay line metrics
     */
    companion object {
        /**
         * Calculate overlay line adherence percentage
         */
        private fun calculateOverlayLineAdherence(pathDeviation: Float, qualityScore: Float): Float {
            // Higher quality score and lower deviation = better adherence
            val deviationFactor = kotlin.math.max(0f, 100f - (pathDeviation * 50f))
            return ((qualityScore * 0.7f) + (deviationFactor * 0.3f)).coerceIn(0f, 100f)
        }

        fun getCsvHeader(): String {
            return listOf(
                "Rep_Number",
                "Timestamp",
                "Exercise",
                "Tempo",
                "Total_Distance_cm",
                "Vertical_Range_cm",
                "Avg_Velocity_cm_s",
                "Peak_Velocity_cm_s",
                "Path_Deviation_cm",
                "Duration_sec",
                "Eccentric_Duration_sec",
                "Pause_Duration_sec",
                "Concentric_Duration_sec",
                "Quality_Score",
                "Grade",
                "Overlay_Line_Adherence"
            ).joinToString(",")
        }
    }
}