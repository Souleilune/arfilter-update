// ===== UPDATED: CsvReportManager.kt =====
package com.example.arfilter.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.arfilter.detector.RepData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow

/**
 * Enhanced CSV report manager with overlay line integration
 * Generates detailed CSV files with overlay line-based rep metrics
 */
class CsvReportManager(private val context: Context) {

    companion object {
        private const val TAG = "CsvReportManager"
        private const val FILE_PROVIDER_AUTHORITY = "com.example.arfilter.fileprovider"
    }

    /**
     * Generate and save CSV report from rep data with overlay line information
     */
    suspend fun generateReport(
        repDataList: List<RepData>,
        sessionInfo: SessionInfo
    ): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = generateFileName(sessionInfo)
            val file = createEnhancedCsvFile(fileName, repDataList, sessionInfo)

            Log.d(TAG, "Enhanced CSV report generated: ${file.absolutePath}")
            return@withContext file.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate CSV report", e)
            return@withContext null
        }
    }

    /**
     * Share the generated CSV file
     */
    suspend fun shareReport(filePath: String): Boolean = withContext(Dispatchers.Main) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File not found: $filePath")
                return@withContext false
            }

            val uri = FileProvider.getUriForFile(
                context,
                FILE_PROVIDER_AUTHORITY,
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "PowerLifting AR Coach - Overlay Line Rep Analysis")
                putExtra(Intent.EXTRA_TEXT, "Your rep analysis data from PowerLifting AR Coach with overlay line-based detection")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Rep Analysis Report")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to share CSV report", e)
            return@withContext false
        }
    }

    private fun createEnhancedCsvFile(
        fileName: String,
        repDataList: List<RepData>,
        sessionInfo: SessionInfo
    ): File {
        val downloadsDir = File(context.getExternalFilesDir(null), "PowerLifting_Reports")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val file = File(downloadsDir, fileName)

        FileWriter(file).use { writer ->
            // Enhanced Session Header with overlay line info
            writer.append("# PowerLifting AR Coach - Overlay Line Rep Analysis Report\n")
            writer.append("# Generated: ${sessionInfo.timestamp}\n")
            writer.append("# Exercise: ${sessionInfo.exercise}\n")
            writer.append("# Tempo: ${sessionInfo.tempo}\n")
            writer.append("# Total Reps: ${repDataList.size}\n")
            writer.append("# Session Duration: ${sessionInfo.duration}\n")
            writer.append("# Average Quality Score: ${calculateAverageQuality(repDataList)}\n")
            writer.append("# Rep Detection Method: AR Overlay Line Crossing Analysis\n")
            writer.append("# Note: Reps counted based on barbell crossing the AR movement guide line\n")
            sessionInfo.overlayLineHeight?.let {
                writer.append("# Overlay Line Height: ${it}dp\n")
            }
            sessionInfo.rangeOfMotion?.let {
                writer.append("# Range of Motion: ${it}dp\n")
            }
            writer.append("\n")

            // Enhanced CSV Header with overlay line metrics
            writer.append(getEnhancedCsvHeader())
            writer.append("\n")

            // Rep Data with overlay line analysis
            repDataList.forEach { repData ->
                writer.append(repData.toCsvRow())
                writer.append("\n")
            }

            // Enhanced Summary Statistics
            writer.append("\n")
            writer.append("# OVERLAY LINE ANALYSIS SUMMARY\n")
            writer.append("Metric,Value,Notes\n")
            writer.append("Total Reps,${repDataList.size},Detected by overlay line crossing\n")
            writer.append("Average Quality Score,${String.format("%.1f", calculateAverageQuality(repDataList))},Based on line adherence\n")
            writer.append("Best Rep Quality,${repDataList.maxOfOrNull { it.qualityScore } ?: 0f},Highest overlay line adherence\n")
            writer.append("Average Duration,${String.format("%.1f", repDataList.map { it.duration }.average())} sec,Time per rep\n")
            writer.append("Average Vertical Range,${String.format("%.1f", repDataList.map { it.verticalRange }.average())} cm,Movement range\n")
            writer.append("Average Path Deviation,${String.format("%.2f", repDataList.map { it.pathDeviation }.average())} cm,Deviation from overlay line\n")
            writer.append("Consistency Rating,${calculateConsistencyRating(repDataList)},Based on deviation variance\n")

            // Movement Pattern Analysis
            writer.append("\n")
            writer.append("# MOVEMENT PATTERN ANALYSIS\n")
            writer.append("Rep_Number,Quality_Grade,Path_Deviation_cm,Duration_sec,Notes\n")
            repDataList.forEach { repData ->
                val notes = when {
                    repData.qualityScore >= 90 -> "Excellent form"
                    repData.qualityScore >= 80 -> "Good form"
                    repData.qualityScore >= 70 -> "Acceptable form"
                    repData.qualityScore >= 60 -> "Needs improvement"
                    else -> "Poor form"
                }
                writer.append("${repData.repNumber},${repData.getQualityGrade()},${String.format("%.2f", repData.pathDeviation)},${String.format("%.1f", repData.duration)},$notes\n")
            }

            // Recommendations
            writer.append("\n")
            writer.append("# TRAINING RECOMMENDATIONS\n")
            val recommendations = generateRecommendations(repDataList, sessionInfo)
            recommendations.forEach { recommendation ->
                writer.append("# $recommendation\n")
            }
        }

        return file
    }

    private fun getEnhancedCsvHeader(): String {
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

    private fun generateFileName(sessionInfo: SessionInfo): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val exercise = sessionInfo.exercise.replace(" ", "_")
        return "PowerLifting_${exercise}_OverlayLine_${timestamp}.csv"
    }

    private fun calculateAverageQuality(repDataList: List<RepData>): Float {
        return if (repDataList.isNotEmpty()) {
            repDataList.map { it.qualityScore }.average().toFloat()
        } else 0f
    }

    private fun calculateConsistencyRating(repDataList: List<RepData>): String {
        if (repDataList.isEmpty()) return "N/A"

        val deviations = repDataList.map { it.pathDeviation }
        val avgDeviation = deviations.average()
        val variance = deviations.map { (it - avgDeviation).pow(2) }.average()
        val standardDeviation = kotlin.math.sqrt(variance).toFloat()

        return when {
            standardDeviation < 0.5f -> "Excellent"
            standardDeviation < 1.0f -> "Good"
            standardDeviation < 1.5f -> "Fair"
            else -> "Needs Work"
        }
    }

    private fun generateRecommendations(repDataList: List<RepData>, sessionInfo: SessionInfo): List<String> {
        val recommendations = mutableListOf<String>()

        if (repDataList.isEmpty()) {
            recommendations.add("No reps detected - ensure barbell is visible and crosses the overlay line")
            return recommendations
        }

        val avgQuality = calculateAverageQuality(repDataList)
        val avgDeviation = repDataList.map { it.pathDeviation }.average()
        val avgDuration = repDataList.map { it.duration }.average()

        // Quality-based recommendations
        when {
            avgQuality >= 85 -> recommendations.add("Excellent form! Maintain this consistency")
            avgQuality >= 75 -> recommendations.add("Good form overall. Focus on reducing path deviation")
            avgQuality >= 65 -> recommendations.add("Form needs improvement. Work on bar path consistency")
            else -> recommendations.add("Poor form detected. Consider reducing weight and focusing on technique")
        }

        // Path deviation recommendations
        if (avgDeviation > 2.0f) {
            recommendations.add("High path deviation detected. Focus on keeping the bar in line with the overlay guide")
        }

        // Tempo recommendations
        if (avgDuration < 2.0) {
            recommendations.add("Reps are too fast. Slow down and focus on control")
        } else if (avgDuration > 6.0) {
            recommendations.add("Reps are too slow. Work on smooth, controlled movement")
        }

        // Exercise-specific recommendations
        when (sessionInfo.exercise) {
            "Squat" -> {
                recommendations.add("For squats: Focus on hitting depth consistently below the overlay line")
            }
            "Bench Press" -> {
                recommendations.add("For bench press: Ensure bar touches chest level consistently")
            }
            "Deadlift" -> {
                recommendations.add("For deadlifts: Keep bar close to body throughout the movement")
            }
        }

        return recommendations
    }

    /**
     * Get all saved reports
     */
    fun getSavedReports(): List<File> {
        val reportsDir = File(context.getExternalFilesDir(null), "PowerLifting_Reports")
        return if (reportsDir.exists()) {
            reportsDir.listFiles()?.filter { it.extension == "csv" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * Delete old reports (keep only last 10)
     */
    fun cleanupOldReports() {
        val reports = getSavedReports()
        if (reports.size > 10) {
            reports.drop(10).forEach { file ->
                try {
                    file.delete()
                    Log.d(TAG, "Deleted old report: ${file.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete old report: ${file.name}", e)
                }
            }
        }
    }
}

/**
 * Enhanced session information with overlay line data
 */
data class SessionInfo(
    val exercise: String,
    val tempo: String,
    val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
    val duration: String,
    val overlayLineHeight: Float? = null,
    val rangeOfMotion: Float? = null
)