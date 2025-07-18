package com.example.arfilter.detector

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.example.arfilter.utils.CsvReportManager
import com.example.arfilter.utils.SessionInfo
import com.example.arfilter.viewmodel.ExerciseType
import com.example.arfilter.viewmodel.LiftPhase
import com.example.arfilter.viewmodel.Tempo

/**
 * Enhanced path manager with overlay line-based rep detection
 * Counts reps based on barbell crossing the AR overlay line
 */
class EnhancedPathManager(
    private val maxActivePaths: Int = 2,
    private val pathTimeoutMs: Long = 4000L,
    private val minPathPoints: Int = 15,
    private val minRepDistance: Float = 0.08f // Minimum vertical movement for a valid rep
) {
    private val activePaths = mutableListOf<BarPath>()
    private val completedReps = mutableListOf<BarPath>()
    private val repAnalyzer = OverlayLineRepAnalyzer()
    private var lastCleanupTime = 0L
    private var repCounter = 1
    private var sessionStartTime = 0L

    // OVERLAY LINE INTEGRATION
    private var overlayLineHeight: Float = 300f // Default from PowerliftingState
    private var rangeOfMotion: Float = 300f // Default from PowerliftingState
    private var currentExercise: ExerciseType = ExerciseType.SQUAT
    private var currentTempo: Tempo = Tempo.MODERATE
    private var currentPhase: LiftPhase = LiftPhase.READY
    private var canvasHeight: Float = 1920f // Typical screen height, will be updated

    companion object {
        private const val TAG = "EnhancedPathManager"
    }

    /**
     * Update overlay line parameters from PowerliftingState
     */
    fun updateOverlaySettings(
        lineHeight: Float,
        rangeOfMotion: Float,
        exercise: ExerciseType,
        tempo: Tempo,
        currentPhase: LiftPhase,
        canvasHeight: Float
    ) {
        this.overlayLineHeight = lineHeight
        this.rangeOfMotion = rangeOfMotion
        this.currentExercise = exercise
        this.currentTempo = tempo
        this.currentPhase = currentPhase
        this.canvasHeight = canvasHeight

        Log.d(TAG, "Updated overlay settings: lineHeight=$lineHeight, range=$rangeOfMotion, exercise=$exercise")
    }

    /**
     * Start a new session for rep tracking
     */
    fun startSession() {
        sessionStartTime = System.currentTimeMillis()
        completedReps.clear()
        activePaths.clear()
        repCounter = 1
        Log.d(TAG, "New rep tracking session started")
    }

    /**
     * Add detection and check for completed reps based on overlay line
     */
    fun addDetection(detection: Detection, currentTime: Long): List<BarPath> {
        val centerX = (detection.bbox.left + detection.bbox.right) / 2f
        val centerY = (detection.bbox.top + detection.bbox.bottom) / 2f
        val newPoint = PathPoint(centerX, centerY, currentTime)

        // Find closest active path or create new one
        val targetPath = findClosestPath(newPoint) ?: createNewPath(currentTime)

        // Add point to path
        targetPath.addPoint(newPoint)

        // Ensure path is in active list
        if (!activePaths.contains(targetPath)) {
            activePaths.add(targetPath)
        }

        // Check for completed reps based on overlay line
        checkForOverlayLineReps(currentTime)

        // Cleanup periodically
        if (currentTime - lastCleanupTime > 2000L) {
            cleanupOldPaths(currentTime)
            lastCleanupTime = currentTime
        }

        return activePaths.toList()
    }

    /**
     * Check if any paths represent completed reps based on overlay line crossing
     */
    private fun checkForOverlayLineReps(currentTime: Long) {
        val pathsToCheck = activePaths.filter { path ->
            val timeSinceLastPoint = currentTime - (path.points.lastOrNull()?.timestamp ?: 0L)
            val hasEnoughPoints = path.points.size >= minPathPoints
            val hasBeenStable = timeSinceLastPoint > 1500L // 1.5 seconds of stability

            hasEnoughPoints && hasBeenStable
        }

        pathsToCheck.forEach { path ->
            if (isOverlayLineRep(path)) {
                // Mark as completed rep
                val completedPath = path.copy()
                completedReps.add(completedPath)
                activePaths.remove(path)

                Log.d(TAG, "Overlay line rep detected! Rep #${repCounter} - Exercise: ${currentExercise.displayName}")
                repCounter++
            }
        }
    }

    /**
     * Check if a path represents a valid rep based on overlay line crossing
     */
    private fun isOverlayLineRep(path: BarPath): Boolean {
        val points = path.points
        if (points.size < minPathPoints) return false

        // Convert overlay line position to screen coordinates
        val overlayLineScreenY = convertOverlayToScreenY(overlayLineHeight)
        val rangeTopY = overlayLineScreenY - (rangeOfMotion / 2f) / canvasHeight
        val rangeBottomY = overlayLineScreenY + (rangeOfMotion / 2f) / canvasHeight

        Log.d(TAG, "Checking rep: overlayLineY=$overlayLineScreenY, rangeTop=$rangeTopY, rangeBottom=$rangeBottomY")

        // Check for sufficient movement within the overlay range
        val pointsInRange = points.filter { point ->
            point.y >= rangeTopY && point.y <= rangeBottomY
        }

        if (pointsInRange.size < minPathPoints / 2) {
            Log.d(TAG, "Path rejected: insufficient points in overlay range (${pointsInRange.size})")
            return false
        }

        // Check for overlay line crossings
        val lineCrossings = countOverlayLineCrossings(points, overlayLineScreenY)
        if (lineCrossings < 2) {
            Log.d(TAG, "Path rejected: insufficient overlay line crossings ($lineCrossings)")
            return false
        }

        // Check for complete rep pattern based on exercise type
        val hasValidRepPattern = checkExerciseSpecificPattern(points, overlayLineScreenY)
        if (!hasValidRepPattern) {
            Log.d(TAG, "Path rejected: invalid rep pattern for ${currentExercise.displayName}")
            return false
        }

        // Check duration (reasonable rep time)
        val duration = (points.last().timestamp - points.first().timestamp) / 1000f
        if (duration < 0.5f || duration > 15.0f) {
            Log.d(TAG, "Path rejected: invalid duration ($duration seconds)")
            return false
        }

        Log.d(TAG, "Valid overlay line rep: crossings=$lineCrossings, duration=$duration, exercise=${currentExercise.displayName}")
        return true
    }

    /**
     * Convert overlay line height (dp) to screen Y coordinate (0-1)
     */
    private fun convertOverlayToScreenY(lineHeightDp: Float): Float {
        // Overlay line is positioned relative to screen center
        // LineHeight = 300dp means 300dp from top of screen
        // Convert to normalized coordinate (0-1)
        val screenCenterY = 0.5f
        val offsetFromCenter = (lineHeightDp - 400f) / canvasHeight // 400dp is roughly screen center
        return (screenCenterY + offsetFromCenter).coerceIn(0f, 1f)
    }

    /**
     * Count how many times the barbell path crosses the overlay line
     */
    private fun countOverlayLineCrossings(points: List<PathPoint>, overlayLineY: Float): Int {
        if (points.size < 2) return 0

        var crossings = 0
        val threshold = 0.02f // 2% of screen height threshold for crossing

        for (i in 1 until points.size) {
            val prevPoint = points[i - 1]
            val currPoint = points[i]

            // Check if we crossed the overlay line
            if ((prevPoint.y < overlayLineY - threshold && currPoint.y > overlayLineY + threshold) ||
                (prevPoint.y > overlayLineY + threshold && currPoint.y < overlayLineY - threshold)) {
                crossings++
            }
        }

        return crossings
    }

    /**
     * Check for exercise-specific rep patterns
     */
    private fun checkExerciseSpecificPattern(points: List<PathPoint>, overlayLineY: Float): Boolean {
        return when (currentExercise) {
            ExerciseType.SQUAT -> checkSquatPattern(points, overlayLineY)
            ExerciseType.BENCH_PRESS -> checkBenchPressPattern(points, overlayLineY)
            ExerciseType.DEADLIFT -> checkDeadliftPattern(points, overlayLineY)
            ExerciseType.OVERHEAD_PRESS -> checkOverheadPressPattern(points, overlayLineY)
            ExerciseType.BARBELL_ROW -> checkBarbellRowPattern(points, overlayLineY)
        }
    }

    private fun checkSquatPattern(points: List<PathPoint>, overlayLineY: Float): Boolean {
        // Squat: Start high, go down below line, come back up above line
        val firstPoint = points.first()
        val lastPoint = points.last()
        val lowestPoint = points.minByOrNull { it.y } ?: return false

        return firstPoint.y < overlayLineY && // Start above line
                lowestPoint.y > overlayLineY && // Go below line
                lastPoint.y < overlayLineY && // End above line
                kotlin.math.abs(firstPoint.y - lastPoint.y) < 0.05f // Return to similar position
    }

    private fun checkBenchPressPattern(points: List<PathPoint>, overlayLineY: Float): Boolean {
        // Bench Press: Start low, go up above line, come back down below line
        val firstPoint = points.first()
        val lastPoint = points.last()
        val highestPoint = points.minByOrNull { it.y } ?: return false

        return firstPoint.y > overlayLineY && // Start below line
                highestPoint.y < overlayLineY && // Go above line
                lastPoint.y > overlayLineY && // End below line
                kotlin.math.abs(firstPoint.y - lastPoint.y) < 0.05f // Return to similar position
    }

    private fun checkDeadliftPattern(points: List<PathPoint>, overlayLineY: Float): Boolean {
        // Deadlift: Start at bottom, pull up above line, return to bottom
        val firstPoint = points.first()
        val lastPoint = points.last()
        val highestPoint = points.minByOrNull { it.y } ?: return false

        return firstPoint.y > overlayLineY && // Start below line (floor)
                highestPoint.y < overlayLineY && // Pull above line
                lastPoint.y > overlayLineY // Return to floor
    }

    private fun checkOverheadPressPattern(points: List<PathPoint>, overlayLineY: Float): Boolean {
        // Overhead Press: Start at shoulders, press up above line, return to shoulders
        val firstPoint = points.first()
        val lastPoint = points.last()
        val highestPoint = points.minByOrNull { it.y } ?: return false

        return firstPoint.y > overlayLineY && // Start below line (shoulders)
                highestPoint.y < overlayLineY && // Press above line
                lastPoint.y > overlayLineY && // Return to shoulders
                kotlin.math.abs(firstPoint.y - lastPoint.y) < 0.05f
    }

    private fun checkBarbellRowPattern(points: List<PathPoint>, overlayLineY: Float): Boolean {
        // Barbell Row: Start low, pull up to line, return low
        val firstPoint = points.first()
        val lastPoint = points.last()
        val highestPoint = points.minByOrNull { it.y } ?: return false

        return firstPoint.y > overlayLineY && // Start below line
                highestPoint.y <= overlayLineY + 0.02f && // Pull to or above line
                lastPoint.y > overlayLineY // Return below line
    }

    /**
     * Generate CSV report for completed reps
     */
    suspend fun generateReport(
        csvManager: CsvReportManager,
        exercise: String,
        tempo: String
    ): String? {
        if (completedReps.isEmpty()) {
            Log.d(TAG, "No completed reps to report")
            return null
        }

        val repDataList = completedReps.mapIndexedNotNull { index, barPath ->
            repAnalyzer.analyzeRep(
                barPath = barPath,
                exercise = exercise,
                tempo = tempo,
                repNumber = index + 1,
                overlayLineHeight = overlayLineHeight,
                rangeOfMotion = rangeOfMotion,
                exerciseType = currentExercise,
                canvasHeight = canvasHeight
            )
        }

        if (repDataList.isEmpty()) {
            Log.d(TAG, "No valid rep data to report")
            return null
        }

        val sessionDuration = if (sessionStartTime > 0) {
            val durationMs = System.currentTimeMillis() - sessionStartTime
            val minutes = durationMs / (1000 * 60)
            val seconds = (durationMs % (1000 * 60)) / 1000
            "${minutes}m ${seconds}s"
        } else "Unknown"

        val sessionInfo = SessionInfo(
            exercise = exercise,
            tempo = tempo,
            duration = sessionDuration
        )

        return csvManager.generateReport(repDataList, sessionInfo)
    }

    /**
     * Get statistics for current session
     */
    fun getSessionStats(): SessionStats {
        return SessionStats(
            totalReps = completedReps.size,
            averageQuality = if (completedReps.isNotEmpty()) {
                completedReps.mapNotNull { path ->
                    repAnalyzer.analyzeRep(
                        path,
                        currentExercise.displayName,
                        currentTempo.displayName,
                        0,
                        overlayLineHeight,
                        rangeOfMotion,
                        currentExercise,
                        canvasHeight
                    )?.qualityScore
                }.average().toFloat()
            } else 0f,
            sessionDuration = if (sessionStartTime > 0) {
                (System.currentTimeMillis() - sessionStartTime) / 1000f
            } else 0f
        )
    }

    // Original AutomaticPathManager methods (unchanged)
    private fun findClosestPath(newPoint: PathPoint): BarPath? {
        if (activePaths.isEmpty()) return null

        val recentPaths = activePaths.filter { path ->
            path.points.isNotEmpty() &&
                    newPoint.timestamp - path.points.last().timestamp < 2000L
        }

        return recentPaths.minByOrNull { path ->
            if (path.points.isNotEmpty()) {
                calculateDistance(newPoint, path.points.last())
            } else Float.MAX_VALUE
        }?.takeIf { path ->
            if (path.points.isNotEmpty()) {
                calculateDistance(newPoint, path.points.last()) < 0.1f
            } else false
        }
    }

    private fun calculateDistance(point1: PathPoint, point2: PathPoint): Float {
        val dx = point1.x - point2.x
        val dy = point1.y - point2.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun createNewPath(currentTime: Long): BarPath {
        if (activePaths.size >= maxActivePaths) {
            val oldestPath = activePaths.minByOrNull { path ->
                if (path.points.isNotEmpty()) path.points.last().timestamp else 0L
            }
            oldestPath?.let { activePaths.remove(it) }
        }

        return BarPath(
            color = getColorForPathIndex(activePaths.size),
            startTime = currentTime
        )
    }

    private fun cleanupOldPaths(currentTime: Long) {
        activePaths.removeAll { path ->
            val isOld = path.points.isEmpty() ||
                    currentTime - path.points.last().timestamp > pathTimeoutMs
            val isTooShort = path.points.size < minPathPoints &&
                    currentTime - path.startTime > 3000L

            isOld || isTooShort
        }

        activePaths.forEach { path ->
            if (path.points.size > 300) {
                val keepPoints = path.points.takeLast(200)
                path.points.clear()
                path.points.addAll(keepPoints)
            }
        }
    }

    private fun getColorForPathIndex(index: Int): Color {
        val colors = listOf(Color.Cyan, Color.Yellow, Color.Green, Color.Magenta)
        return colors[index % colors.size]
    }

    fun getCurrentPaths(): List<BarPath> = activePaths.toList()
    fun getCompletedReps(): List<BarPath> = completedReps.toList()
    fun getActivePathCount(): Int = activePaths.size
    fun getTotalPoints(): Int = activePaths.map { it.points.size }.sum()

    fun clearAllPaths() {
        activePaths.clear()
        completedReps.clear()
        repCounter = 1
        Log.d(TAG, "All paths and completed reps cleared")
    }
}

/**
 * Session statistics for display
 */
data class SessionStats(
    val totalReps: Int,
    val averageQuality: Float,
    val sessionDuration: Float
)