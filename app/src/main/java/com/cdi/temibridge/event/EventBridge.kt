package com.cdi.temibridge.event

import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.*
import com.robotemi.sdk.map.OnLoadMapStatusChangedListener
import com.robotemi.sdk.navigation.listener.OnCurrentPositionChangedListener
import com.robotemi.sdk.navigation.listener.OnDistanceToLocationChangedListener
import com.robotemi.sdk.navigation.listener.OnReposeStatusChangedListener
import com.robotemi.sdk.navigation.model.Position
import com.cdi.temibridge.server.ConnectionManager
import org.jetbrains.annotations.NotNull

class EventBridge(
    private val robot: Robot,
    private val connectionManager: ConnectionManager
) : Robot.TtsListener,
    Robot.AsrListener,
    OnGoToLocationStatusChangedListener,
    OnBeWithMeStatusChangedListener,
    OnConstraintBeWithStatusChangedListener,
    OnDetectionStateChangedListener,
    OnMovementStatusChangedListener,
    OnRobotReadyListener,
    OnLocationsUpdatedListener,
    Robot.NlpListener,
    Robot.WakeupWordListener,
    Robot.ConversationViewAttachesListener,
    OnDistanceToLocationChangedListener,
    OnReposeStatusChangedListener,
    OnUserInteractionChangedListener,
    OnCurrentPositionChangedListener,
    OnLoadMapStatusChangedListener,
    OnMovementVelocityChangedListener,
    OnRobotLiftedListener {

    companion object {
        private const val TAG = "EventBridge"
    }

    fun registerAll() {
        robot.addTtsListener(this)
        robot.addAsrListener(this)
        robot.addOnGoToLocationStatusChangedListener(this)
        robot.addOnBeWithMeStatusChangedListener(this)
        robot.addOnConstraintBeWithStatusChangedListener(this)
        robot.addOnDetectionStateChangedListener(this)
        robot.addOnMovementStatusChangedListener(this)
        robot.addOnRobotReadyListener(this)
        robot.addOnLocationsUpdatedListener(this)
        robot.addNlpListener(this)
        robot.addWakeupWordListener(this)
        robot.addConversationViewAttachesListener(this)
        robot.addOnDistanceToLocationChangedListener(this)
        robot.addOnReposeStatusChangedListener(this)
        robot.addOnUserInteractionChangedListener(this)
        robot.addOnCurrentPositionChangedListener(this)
        robot.addOnLoadMapStatusChangedListener(this)
        robot.addOnMovementVelocityChangedListener(this)
        robot.addOnRobotLiftedListener(this)
        Log.i(TAG, "All event listeners registered")
    }

    fun unregisterAll() {
        robot.removeTtsListener(this)
        robot.removeAsrListener(this)
        robot.removeOnGoToLocationStatusChangedListener(this)
        robot.removeOnBeWithMeStatusChangedListener(this)
        robot.removeOnConstraintBeWithStatusChangedListener(this)
        robot.removeOnDetectionStateChangedListener(this)
        robot.removeOnMovementStatusChangedListener(this)
        robot.removeOnRobotReadyListener(this)
        robot.removeOnLocationsUpdateListener(this)
        robot.removeNlpListener(this)
        robot.removeWakeupWordListener(this)
        robot.removeConversationViewAttachesListener(this)
        robot.removeOnDistanceToLocationChangedListener(this)
        robot.removeOnReposeStatusChangedListener(this)
        robot.removeOnUserInteractionChangedListener(this)
        robot.removeOnCurrentPositionChangedListener(this)
        robot.removeOnLoadMapStatusChangedListener(this)
        robot.removeOnMovementVelocityChangedListener(this)
        robot.removeOnRobotLiftedListener(this)
        Log.i(TAG, "All event listeners unregistered")
    }

    // TTS events
    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        connectionManager.sendNotification(
            "event.speech.ttsStatusChanged",
            mapOf(
                "text" to ttsRequest.speech,
                "status" to ttsRequest.status.name,
                "id" to ttsRequest.id
            )
        )
    }

    // ASR events
    override fun onAsrResult(@NotNull text: String) {
        connectionManager.sendNotification(
            "event.speech.asrResult",
            mapOf("text" to text)
        )
    }

    // Navigation events
    override fun onGoToLocationStatusChanged(
        @NotNull location: String,
        status: String,
        descriptionId: Int,
        description: String
    ) {
        connectionManager.sendNotification(
            "event.navigation.goToLocationStatusChanged",
            mapOf(
                "location" to location,
                "status" to status,
                "descriptionId" to descriptionId,
                "description" to description
            )
        )
    }

    // Current position changed
    override fun onCurrentPositionChanged(position: Position) {
        connectionManager.sendNotification(
            "event.navigation.currentPositionChanged",
            mapOf(
                "x" to position.x,
                "y" to position.y,
                "yaw" to position.yaw,
                "tiltAngle" to position.tiltAngle
            )
        )
    }

    // Load map status
    override fun onLoadMapStatusChanged(status: Int) {
        val statusName = when (status) {
            OnLoadMapStatusChangedListener.COMPLETE -> "complete"
            OnLoadMapStatusChangedListener.START -> "start"
            OnLoadMapStatusChangedListener.ERROR_UNKNOWN -> "error_unknown"
            OnLoadMapStatusChangedListener.ERROR_ABORT_FROM_ROBOX -> "error_abort_from_robox"
            OnLoadMapStatusChangedListener.ERROR_ABORT_ON_NOT_CHARGING -> "error_not_charging"
            OnLoadMapStatusChangedListener.ERROR_ABORT_BUSY -> "error_busy"
            OnLoadMapStatusChangedListener.ERROR_ABORT_ON_TIMEOUT -> "error_timeout"
            OnLoadMapStatusChangedListener.ERROR_PB_STREAM_FILE_INVALID -> "error_file_invalid"
            OnLoadMapStatusChangedListener.ERROR_GET_MAP_DATA -> "error_get_map_data"
            else -> "unknown_$status"
        }
        connectionManager.sendNotification(
            "event.navigation.loadMapStatusChanged",
            mapOf("status" to status, "statusName" to statusName)
        )
    }

    // Follow events
    override fun onBeWithMeStatusChanged(@NotNull status: String) {
        connectionManager.sendNotification(
            "event.follow.beWithMeStatusChanged",
            mapOf("status" to status)
        )
    }

    override fun onConstraintBeWithStatusChanged(isConstraint: Boolean) {
        connectionManager.sendNotification(
            "event.follow.constraintBeWithStatusChanged",
            mapOf("isConstraint" to isConstraint)
        )
    }

    override fun onDetectionStateChanged(state: Int) {
        connectionManager.sendNotification(
            "event.follow.detectionStateChanged",
            mapOf("state" to state)
        )
    }

    // Movement events
    override fun onMovementStatusChanged(@NotNull type: String, @NotNull status: String) {
        connectionManager.sendNotification(
            "event.movement.statusChanged",
            mapOf("type" to type, "status" to status)
        )
    }

    // System events
    override fun onRobotReady(isReady: Boolean) {
        connectionManager.sendNotification(
            "event.system.robotReady",
            mapOf("isReady" to isReady)
        )
    }

    // Location list updated
    override fun onLocationsUpdated(locations: List<String>) {
        connectionManager.sendNotification(
            "event.navigation.locationsUpdated",
            mapOf("locations" to locations)
        )
    }

    // NLP events
    override fun onNlpCompleted(nlpResult: com.robotemi.sdk.NlpResult) {
        connectionManager.sendNotification(
            "event.speech.nluResult",
            mapOf("action" to nlpResult.action, "params" to nlpResult.params)
        )
    }

    // Wakeup word events
    override fun onWakeupWord(wakeupWord: String, direction: Int) {
        connectionManager.sendNotification(
            "event.speech.wakeupWord",
            mapOf("wakeupWord" to wakeupWord, "direction" to direction)
        )
    }

    // Conversation view
    override fun onConversationAttaches(isAttached: Boolean) {
        connectionManager.sendNotification(
            "event.speech.conversationViewAttached",
            mapOf("isAttached" to isAttached)
        )
    }

    // Distance to location
    override fun onDistanceToLocationChanged(distances: Map<String, Float>) {
        connectionManager.sendNotification(
            "event.navigation.distanceToLocationChanged",
            mapOf("distances" to distances)
        )
    }

    // Repose status
    override fun onReposeStatusChanged(status: Int, description: String) {
        connectionManager.sendNotification(
            "event.navigation.reposeStatusChanged",
            mapOf("status" to status, "description" to description)
        )
    }

    // User interaction
    override fun onUserInteraction(isInteracting: Boolean) {
        connectionManager.sendNotification(
            "event.system.userInteraction",
            mapOf("isInteracting" to isInteracting)
        )
    }

    // Movement velocity
    override fun onMovementVelocityChanged(velocity: Float) {
        connectionManager.sendNotification(
            "event.movement.velocityChanged",
            mapOf("velocity" to velocity)
        )
    }

    // Robot lifted
    override fun onRobotLifted(isLifted: Boolean, reason: String) {
        connectionManager.sendNotification(
            "event.system.robotLifted",
            mapOf("isLifted" to isLifted, "reason" to reason)
        )
    }
}
