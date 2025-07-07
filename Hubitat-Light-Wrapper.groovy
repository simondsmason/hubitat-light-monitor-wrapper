/**
 *  Light Monitor App
 *
 *  Description: Monitors light switches to ensure they successfully turn on/off as commanded
 *  and retries if necessary. Includes refresh capability to ensure accurate state reporting.
 *
 *  Copyright 2025
 *
 *  Change History:
 *  1.00 - Initial release
 *  1.01 - Added refresh capability to ensure accurate state reporting before comparing states
 *  1.02 - Fixed retry loop issue where app would monitor events from other sources and get stuck in infinite retry cycles
 *  1.03 - Added connectivity issue detection and handling to prevent monitoring during device connectivity problems
 *  1.04 - Enhanced connectivity detection to work with different device types (WiFi/Hue, Zigbee, Z-Wave) and added detailed device diagnostics
 *  1.05 - Optimized default settings for environments with connectivity issues (30s/30s/240s/60s intervals)
 *
 */

definition(
    name: "Light Monitor Wrapper",
    namespace: "simonmason",
    author: "Simon Mason",
    description: "Monitors lights to confirm they turn on/off as commanded and retries if necessary. Optimized for environments with connectivity issues.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Light Monitor Configuration", install: true, uninstall: true) {
        section("Select Lights to Monitor") {
            input "lightSwitches", "capability.switch", title: "Select Light Switches", multiple: true, required: true
        }
        
        section("Monitoring Settings") {
            input "checkInterval", "number", title: "Status Check Interval (seconds) (default: 30)", defaultValue: 30, required: true
            input "refreshWait", "number", title: "Wait After Refresh (seconds) (default: 30)", defaultValue: 30, required: true
            input "maxRetries", "number", title: "Maximum Retries (default: 5)", defaultValue: 5, required: true
            input "commandTimeout", "number", title: "Command Timeout (seconds) (default: 240)", defaultValue: 240, required: true
            input "cooldownPeriod", "number", title: "Cooldown Period Between Monitoring Sessions (seconds) (default: 60)", defaultValue: 60, required: true
        }
        
        section("Notifications") {
            input "sendPushNotification", "bool", title: "Send Push Notification on Failure?", defaultValue: false
            input "notificationDevices", "capability.notification", title: "Notification Devices", multiple: true, required: false
        }
        
        section("Logging") {
            input "debugMode", "bool", title: "Enable Debug Logging?", defaultValue: false
            input "debugDuration", "enum", title: "Debug Log Auto-Disable After", 
                options: [[0:"Never"],[30:"30 minutes"],[60:"1 hour"],[120:"2 hours"],[180:"3 hours"],[360:"6 hours"],[720:"12 hours"],[1440:"24 hours"]], 
                defaultValue: 30, required: true
        }
    }
}

def installed() {
    initialize()
    if (debugMode) {
        scheduleDebugLogDisable()
    }
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
    if (debugMode) {
        scheduleDebugLogDisable()
    }
}

def initialize() {
    // Store device IDs for verification
    state.configuredDeviceIds = lightSwitches?.collect { it.id.toString() } ?: []
    logDebug("Configured Device IDs: ${state.configuredDeviceIds}")
    
    subscribe(lightSwitches, "switch", lightSwitchHandler)
    state.monitoringState = [:]
    state.lastCommandTime = [:] // Track last command time for cooldown
    state.connectivityFailures = [:] // Track connectivity failures per device
    logInfo("Light Monitor initialized with ${lightSwitches.size()} light switches")
    if (debugMode) logDebug("Debug logging enabled")
    
    // Log all configured devices for verification
    if (debugMode) {
        lightSwitches.each { device ->
            logDebug("Configured device: ID=${device.id}, Name=${device.displayName}")
        }
    }
}

def scheduleDebugLogDisable() {
    if (debugDuration.toInteger() > 0) {
        logDebug("Debug logging will be automatically disabled after ${debugDuration} minutes")
        runIn(debugDuration.toInteger() * 60, disableDebugLog)
    }
}

def disableDebugLog() {
    app.updateSetting("debugMode", [value: "false", type: "bool"])
    logInfo("Debug logging automatically disabled")
}

def logDebug(msg) {
    if (debugMode) {
        log.debug "${app.name}: ${msg}"
    }
}

def logInfo(msg) {
    log.info "${app.name}: ${msg}"
}

def logWarn(msg) {
    log.warn "${app.name}: ${msg}"
}

def logError(msg) {
    log.error "${app.name}: ${msg}"
}

def lightSwitchHandler(evt) {
    def deviceId = evt.deviceId.toString()
    def deviceName = evt.displayName
    def newState = evt.value
    
    // Check if this event was triggered by our own app (to prevent monitoring external events)
    if (evt.source == "APP_COMMAND" || evt.source == "COMMAND") {
        logDebug("Ignoring event from our own app command - Device: ${deviceName}, State: ${newState}")
        return
    }
    
    // Check cooldown period to prevent rapid retry loops
    def lastCommandTime = state.lastCommandTime[deviceId] ?: 0
    def timeSinceLastCommand = (now() - lastCommandTime) / 1000
    
    if (timeSinceLastCommand < cooldownPeriod) {
        logDebug("Ignoring event due to cooldown period - Device: ${deviceName}, Time since last command: ${timeSinceLastCommand.toInteger()}s, Cooldown: ${cooldownPeriod}s")
        return
    }
    
    // Check if we're already monitoring this device
    if (state.monitoringState.containsKey(deviceId)) {
        logDebug("Already monitoring device ${deviceName} - ignoring new event")
        return
    }
    
    // Always log when monitoring starts, regardless of debug mode
    log.info "${app.name}: MONITORING STARTED - Light ${deviceName} (ID: ${deviceId}) changing to ${newState} at ${new Date().format('yyyy-MM-dd HH:mm:ss')}"
    
    // Verify the device exists in our list - first with direct check
    def deviceExists = lightSwitches.find { it.id.toString() == deviceId }
    
    // Diagnostic logging for device verification
    if (!deviceExists) {
        logWarn("Device with ID ${deviceId} triggered an event but may not be in our monitored devices list")
        logDebug("Event details: deviceId=${deviceId}, name=${deviceName}, value=${newState}, source=${evt.source}")
        logDebug("Current configured device IDs: ${state.configuredDeviceIds}")
        
        // Check if ID is in our stored list (cross-check)
        if (state.configuredDeviceIds.contains(deviceId)) {
            logWarn("Device ID ${deviceId} is in our configuration list but couldn't be found directly - attempting to proceed")
            // Since we know this device should be monitored, we'll continue with the event
        } else {
            // Try to recover by checking all devices
            def foundDevice = false
            lightSwitches.each { device ->
                logDebug("Checking device: ${device.id} vs event ${deviceId}")
                if (device.id.toString() == deviceId) {
                    foundDevice = true
                    deviceExists = device
                }
            }
            
            if (!foundDevice) {
                logWarn("Could not find device with ID ${deviceId} in configuration - ignoring event")
                return
            }
        }
    }
    
    logDebug("Device event details - ID: ${deviceId}, Name: ${deviceName}, New State: ${newState}, Event Source: ${evt.source}")
    
    // Get the current state directly before setting up monitoring
    def currentValue = null
    try {
        if (deviceExists) {
            currentValue = deviceExists.currentValue("switch")
            logDebug("Initial device state verification - Current value: ${currentValue}")
        } else {
            // Try to get the device from all available devices if we couldn't find it in our list
            def allDevices = getChildDevices()
            def alternateDevice = allDevices.find { it.id.toString() == deviceId }
            if (alternateDevice) {
                currentValue = alternateDevice.currentValue("switch")
                logDebug("Found device in alternate list - Current value: ${currentValue}")
            }
        }
    } catch (Exception e) {
        logError("Error getting current state for device ${deviceName}: ${e.message}")
    }
    
    // Set up monitoring for this device
    state.monitoringState[deviceId] = [
        desiredState: newState,
        checkCount: 0,
        refreshCount: 0,
        lastCommand: newState,
        lastCheck: now(),
        startTime: now(),
        initialState: currentValue,
        deviceName: deviceName, // Store the name for reference even if device becomes unavailable
        waitingForRefresh: false,
        refreshSent: false,
        externalEvent: true // Mark this as an external event
    ]
    
    logDebug("Monitoring state initialized: ${state.monitoringState[deviceId]}")
    
    // Wait for the initial checkInterval before starting checks to give device time to update
    logDebug("Scheduling initial check for device ${deviceName} in ${checkInterval} seconds")
    runIn(checkInterval, "startRefreshProcess", [data: [deviceId: deviceId]])
    
    // Set up timeout check
    logDebug("Setting up timeout check for device ${deviceName} in ${commandTimeout} seconds")
    runIn(commandTimeout, "timeoutCheck", [data: [deviceId: deviceId]])
}

def startRefreshProcess(data) {
    def deviceId = data.deviceId.toString()
    
    // Check if monitoring state exists
    if (!state.monitoringState.containsKey(deviceId)) {
        logDebug("No monitoring state found for device ID: ${deviceId} - Monitoring may have completed successfully")
        return
    }
    
    // Get device info
    def monitorData = state.monitoringState[deviceId]
    def deviceName = monitorData.deviceName ?: "Unknown Device"
    
    // Find device using multiple methods for reliability
    def device = null
    
    // Method 1: Check configured devices
    device = lightSwitches.find { it.id.toString() == deviceId }
    
    // Method 2: If not found, try child devices
    if (!device) {
        logDebug("Device ${deviceId} not found in primary list, checking all devices")
        def allDevices = getChildDevices()
        device = allDevices.find { it.id.toString() == deviceId }
    }
    
    // Method 3: If still not found, try to find by display name
    if (!device && deviceName != "Unknown Device") {
        logDebug("Attempting to find device by name: ${deviceName}")
        device = lightSwitches.find { it.displayName == deviceName }
    }
    
    // Check if device still exists
    if (!device) {
        logWarn("Device with ID ${deviceId} (${deviceName}) not found - Device may have been removed or is inaccessible")
        logDebug("Configured device IDs: ${state.configuredDeviceIds}")
        
        // Check if we should continue attempting to send commands or remove from monitoring
        if (monitorData.checkCount >= maxRetries / 2) {
            logWarn("MONITORING ABANDONED - Could not access device ${deviceName} after multiple attempts")
            state.monitoringState.remove(deviceId)
        } else {
            // Increase check count and try again
            monitorData.checkCount = monitorData.checkCount + 1
            state.monitoringState[deviceId] = monitorData
            
            // Schedule next check
            logDebug("Scheduling next check for device ${deviceName} in ${checkInterval} seconds")
            runIn(checkInterval, "startRefreshProcess", [data: [deviceId: deviceId]])
        }
        return
    }
    
    // Send refresh command to ensure accurate state reading
    logInfo("Sending REFRESH command to device ${deviceName} to get accurate state")
    
    try {
        device.refresh()
        monitorData.refreshSent = true
        monitorData.waitingForRefresh = true
        monitorData.refreshCount = monitorData.refreshCount + 1
        state.monitoringState[deviceId] = monitorData
        
        // Wait for refreshWait seconds before checking state
        logDebug("Waiting ${refreshWait} seconds after refresh before checking state for ${deviceName}")
        runIn(refreshWait, "checkLightStatus", [data: [deviceId: deviceId]])
    } catch (Exception e) {
        logError("Error sending refresh command to device ${deviceName}: ${e.message}")
        
        // If refresh fails, try to check status directly
        logDebug("Refresh failed, proceeding to direct status check for ${deviceName}")
        checkLightStatus(data)
    }
}

def checkLightStatus(data) {
    def deviceId = data.deviceId.toString()
    
    // Check if monitoring state exists
    if (!state.monitoringState.containsKey(deviceId)) {
        logDebug("No monitoring state found for device ID: ${deviceId} - Monitoring may have completed successfully")
        return
    }
    
    // Get device info
    def monitorData = state.monitoringState[deviceId]
    def deviceName = monitorData.deviceName ?: "Unknown Device"
    
    // Find device using multiple methods for reliability
    def device = null
    
    // Method 1: Check configured devices
    device = lightSwitches.find { it.id.toString() == deviceId }
    
    // Method 2: If not found, try child devices
    if (!device) {
        logDebug("Device ${deviceId} not found in primary list, checking all devices")
        def allDevices = getChildDevices()
        device = allDevices.find { it.id.toString() == deviceId }
    }
    
    // Method 3: If still not found, try to find by display name
    if (!device && deviceName != "Unknown Device") {
        logDebug("Attempting to find device by name: ${deviceName}")
        device = lightSwitches.find { it.displayName == deviceName }
    }
    
    // Check if device still exists
    if (!device) {
        logWarn("Device with ID ${deviceId} (${deviceName}) not found - Device may have been removed or is inaccessible")
        logDebug("Configured device IDs: ${state.configuredDeviceIds}")
        
        // Check if we should continue attempting to send commands or remove from monitoring
        if (monitorData.checkCount >= maxRetries / 2) {
            logWarn("MONITORING ABANDONED - Could not access device ${deviceName} after multiple attempts")
            state.monitoringState.remove(deviceId)
        } else {
            // Increase check count and try again
            monitorData.checkCount = monitorData.checkCount + 1
            state.monitoringState[deviceId] = monitorData
            
            // Schedule next check
            logDebug("Scheduling next check for device ${deviceName} in ${checkInterval} seconds")
            runIn(checkInterval, "startRefreshProcess", [data: [deviceId: deviceId]])
        }
        return
    }
    
    // Update monitoring state to show we're no longer waiting for refresh
    monitorData.waitingForRefresh = false
    state.monitoringState[deviceId] = monitorData
    
    // Safely get current state
    def currentState = null
    try {
        currentState = device.currentValue("switch")
    } catch (Exception e) {
        logError("Error getting current state for device ${deviceName}: ${e.message}")
        // Continue with null state - we'll handle this below
    }
    
    def desiredState = monitorData.desiredState
    def elapsedTime = (now() - monitorData.startTime) / 1000
    
    // Log current check status
    if (currentState == null) {
        logWarn("Unable to determine current state for device ${deviceName} - Device may be offline")
    } else {
        logDebug("Checking device ${deviceName} after refresh - Current state: ${currentState}, Desired state: ${desiredState}, Elapsed time: ${elapsedTime}s")
    }
    
    // Update last check time
    monitorData.lastCheck = now()
    state.monitoringState[deviceId] = monitorData
    
    // Check if we've reached the desired state
    if (currentState == desiredState) {
        // Success! Light is in desired state
        logInfo("MONITORING COMPLETED - Light ${deviceName} successfully changed to ${desiredState} after ${elapsedTime.toInteger()} seconds and ${monitorData.checkCount} retries")
        logDebug("Removing monitoring state for device ${deviceName}")
        state.monitoringState.remove(deviceId)
    } else {
        // Check if we've reached max retries
        if (monitorData.checkCount >= maxRetries) {
            logWarn("MONITORING FAILED - Light ${deviceName} failed to change to ${desiredState} after ${maxRetries} attempts and ${elapsedTime.toInteger()} seconds")
            
            // Provide more detailed diagnostics
            if (currentState == null) {
                logWarn("Could not determine final device state - Device may be offline or unresponsive")
            } else {
                logDebug("Final device state: ${currentState}, Retry count: ${monitorData.checkCount}, Refresh count: ${monitorData.refreshCount}")
            }
            
            if (sendPushNotification && notificationDevices) {
                logDebug("Sending failure notification to ${notificationDevices.size()} devices")
                notificationDevices.each { 
                    it.deviceNotification("Warning: Light ${deviceName} failed to change to ${desiredState} state after ${maxRetries} attempts")
                }
            }
            
            logDebug("Removing monitoring state for device ${deviceName}")
            state.monitoringState.remove(deviceId)
        } else {
            // Increment retry count and try again
            monitorData.checkCount = monitorData.checkCount + 1
            state.monitoringState[deviceId] = monitorData
            
            logInfo("Light ${deviceName} not in desired state (${desiredState}). Current state: ${currentState ?: 'Unknown'}. Retry attempt ${monitorData.checkCount}")
            
            // Send the command again
            try {
                if (desiredState == "on") {
                    logDebug("Sending ON command to device ${deviceName}")
                    device.on()
                } else {
                    logDebug("Sending OFF command to device ${deviceName}")
                    device.off()
                }
                
                // Track the command time for cooldown
                state.lastCommandTime[deviceId] = now()
                logDebug("Updated last command time for device ${deviceName}")
                
            } catch (Exception e) {
                logError("Error sending command to device ${deviceName}: ${e.message}")
            }
            
            // Schedule next check with refresh after the checkInterval
            logDebug("Scheduling next refresh and check for device ${deviceName} in ${checkInterval} seconds")
            runIn(checkInterval, "startRefreshProcess", [data: [deviceId: deviceId]])
        }
    }
}

def timeoutCheck(data) {
    def deviceId = data.deviceId.toString()
    
    // Check if monitoring state still exists
    if (!state.monitoringState.containsKey(deviceId)) {
        logDebug("Timeout check - Device ${deviceId} is no longer being monitored, likely completed successfully")
        return
    }
    
    // Get device info from multiple sources for reliability
    def device = lightSwitches.find { it.id.toString() == deviceId }
    def monitorData = state.monitoringState[deviceId]
    def deviceName = monitorData.deviceName ?: "Unknown Device"
    
    // If not found in primary list, try other methods
    if (!device) {
        logDebug("Timeout check: Device ${deviceId} not found in primary list, checking alternative sources")
        def allDevices = getChildDevices()
        device = allDevices.find { it.id.toString() == deviceId }
        
        if (!device && deviceName != "Unknown Device") {
            device = lightSwitches.find { it.displayName == deviceName }
        }
    }
    
    logDebug("Timeout check for device ${deviceName} - Desired state: ${monitorData.desiredState}, Check count: ${monitorData.checkCount}, Refresh count: ${monitorData.refreshCount}")
    
    // Check if we've had a recent check
    def timeSinceLastCheck = now() - monitorData.lastCheck
    logDebug("Time since last check: ${timeSinceLastCheck/1000} seconds")
    
    if (timeSinceLastCheck > (checkInterval * 1000 * 2)) {
        // We haven't checked recently, the monitoring might be stuck
        logWarn("MONITORING TIMEOUT - Light ${deviceName} command timed out after ${commandTimeout} seconds")
        logDebug("Monitoring appears stuck - last check was ${timeSinceLastCheck/1000} seconds ago")
        
        // Provide more detailed diagnostics
        if (device) {
            try {
                def finalState = device.currentValue("switch")
                logWarn("Final device state at timeout: ${finalState ?: 'Unknown'}")
            } catch (Exception e) {
                logError("Error getting final state for device ${deviceName}: ${e.message}")
            }
        } else {
            logWarn("Device ${deviceName} not found at timeout check - Device may have been removed from Hubitat")
        }
        
        if (sendPushNotification && notificationDevices) {
            logDebug("Sending timeout notification to ${notificationDevices.size()} devices")
            notificationDevices.each { 
                it.deviceNotification("Light ${deviceName} command timed out while attempting to change to ${monitorData.desiredState} state")
            }
        }
        
        // Clean up monitoring state for this device
        logDebug("Removing monitoring state for device ${deviceName} due to timeout")
        state.monitoringState.remove(deviceId)
    } else {
        logDebug("Timeout check passed - device ${deviceName} is still being actively monitored")
    }
}

// Function to manually start monitoring a light (can be called from other apps)
def startMonitoring(device, desiredState) {
    if (!device || !desiredState) {
        logError("startMonitoring called with invalid parameters - device: ${device}, desiredState: ${desiredState}")
        return false
    }
    
    def deviceId = device.id.toString()
    def deviceName = device.displayName
    
    // Check if we're already monitoring this device
    if (state.monitoringState.containsKey(deviceId)) {
        logDebug("Already monitoring device ${deviceName} - ignoring manual start request")
        return false
    }
    
    // Check cooldown period
    def lastCommandTime = state.lastCommandTime[deviceId] ?: 0
    def timeSinceLastCommand = (now() - lastCommandTime) / 1000
    
    if (timeSinceLastCommand < cooldownPeriod) {
        logDebug("Cannot start monitoring due to cooldown period - Device: ${deviceName}, Time since last command: ${timeSinceLastCommand.toInteger()}s, Cooldown: ${cooldownPeriod}s")
        return false
    }
    
    logInfo("MANUAL MONITORING STARTED - Light ${deviceName} (ID: ${deviceId}) to be set to ${desiredState} at ${new Date().format('yyyy-MM-dd HH:mm:ss')}")
    
    // Get current state
    def currentValue = null
    try {
        currentValue = device.currentValue("switch")
        logDebug("Current device state: ${currentValue}")
    } catch (Exception e) {
        logError("Error getting current state for device ${deviceName}: ${e.message}")
    }
    
    // Set up monitoring for this device
    state.monitoringState[deviceId] = [
        desiredState: desiredState,
        checkCount: 0,
        refreshCount: 0,
        lastCommand: desiredState,
        lastCheck: now(),
        startTime: now(),
        initialState: currentValue,
        deviceName: deviceName,
        waitingForRefresh: false,
        refreshSent: false,
        externalEvent: false // Mark this as a manual event
    ]
    
    logDebug("Manual monitoring state initialized: ${state.monitoringState[deviceId]}")
    
    // Send the initial command
    try {
        if (desiredState == "on") {
            logDebug("Sending initial ON command to device ${deviceName}")
            device.on()
        } else {
            logDebug("Sending initial OFF command to device ${deviceName}")
            device.off()
        }
        
        // Track the command time for cooldown
        state.lastCommandTime[deviceId] = now()
        logDebug("Updated last command time for device ${deviceName}")
        
    } catch (Exception e) {
        logError("Error sending initial command to device ${deviceName}: ${e.message}")
        state.monitoringState.remove(deviceId)
        return false
    }
    
    // Schedule initial check
    logDebug("Scheduling initial check for device ${deviceName} in ${checkInterval} seconds")
    runIn(checkInterval, "startRefreshProcess", [data: [deviceId: deviceId]])
    
    // Set up timeout check
    logDebug("Setting up timeout check for device ${deviceName} in ${commandTimeout} seconds")
    runIn(commandTimeout, "timeoutCheck", [data: [deviceId: deviceId]])
    
    return true
}