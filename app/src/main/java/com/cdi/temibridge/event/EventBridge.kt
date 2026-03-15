package com.cdi.temibridge.event

import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.*
import com.robotemi.sdk.navigation.listener.OnDistanceToLocationChangedListener
import com.robotemi.sdk.navigation.listener.OnReposeStatusChangedListener
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
    OnUserInteractionChangedListener {

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
}
