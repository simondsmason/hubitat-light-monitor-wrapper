# Hubitat Light Monitor Wrapper

A Hubitat Elevation app that monitors light switches to ensure they successfully turn on/off as commanded and retries if necessary. Includes refresh capability to ensure accurate state reporting and is optimized for environments with connectivity issues.

## Features

- **Automatic Monitoring**: Monitors light switches to confirm they reach their desired state
- **Smart Retry Logic**: Automatically retries failed commands with configurable limits
- **Refresh Capability**: Sends refresh commands to ensure accurate state reporting
- **Cooldown Protection**: Prevents rapid retry loops with configurable cooldown periods
- **Timeout Handling**: Configurable timeout to prevent stuck monitoring sessions
- **Manual Monitoring**: API function to manually start monitoring from other apps
- **Push Notifications**: Optional notifications for monitoring failures
- **Debug Logging**: Comprehensive logging with auto-disable capability
- **Connectivity Optimized**: Default settings optimized for environments with connectivity issues

## Installation

1. In your Hubitat Elevation hub, go to **Apps Code**
2. Click **+ New App**
3. Copy and paste the contents of `Hubitat-Light-Wrapper.groovy`
4. Click **Save**
5. Go to **Apps** and click **+ Add App**
6. Select **Light Monitor Wrapper** from the list
7. Configure your settings and install

## Configuration

### Monitoring Settings

- **Status Check Interval (default: 30s)**: How often to check if the light reached the desired state
- **Wait After Refresh (default: 30s)**: How long to wait after sending a refresh command before checking state
- **Maximum Retries (default: 5)**: Maximum number of retry attempts before giving up
- **Command Timeout (default: 240s)**: Maximum time to spend monitoring a single command
- **Cooldown Period (default: 60s)**: Minimum time between monitoring sessions for the same device

### Notifications

- **Send Push Notification on Failure**: Enable push notifications when monitoring fails
- **Notification Devices**: Select devices to receive failure notifications

### Logging

- **Enable Debug Logging**: Enable detailed debug logging
- **Debug Log Auto-Disable After**: Automatically disable debug logging after a specified time

## Usage

### Automatic Monitoring

The app automatically monitors any light switch events from external sources (not its own commands). When a light is turned on/off by another automation or manual control, the app will:

1. Start monitoring the light
2. Send a refresh command to get accurate state
3. Check if the light reached the desired state
4. Retry the command if needed
5. Give up after maximum retries

### Manual Monitoring

You can manually start monitoring from other apps using the `startMonitoring` function:

```groovy
// Start monitoring a light to turn it on
app.startMonitoring(device, "on")

// Start monitoring a light to turn it off  
app.startMonitoring(device, "off")
```

## Default Settings Explained

The default settings are optimized for environments with connectivity issues:

- **30s intervals**: Reduces network congestion and gives devices time to recover
- **240s timeout**: Provides adequate time for monitoring cycles without premature cancellation
- **60s cooldown**: Prevents rapid retry loops while allowing reasonable responsiveness
- **5 retries**: Balances persistence with avoiding infinite loops

## Change History

- **1.00** - Initial release
- **1.01** - Added refresh capability to ensure accurate state reporting before comparing states
- **1.02** - Fixed retry loop issue where app would monitor events from other sources and get stuck in infinite retry cycles
- **1.03** - Added connectivity issue detection and handling to prevent monitoring during device connectivity problems
- **1.04** - Enhanced connectivity detection to work with different device types (WiFi/Hue, Zigbee, Z-Wave) and added detailed device diagnostics
- **1.05** - Optimized default settings for environments with connectivity issues (30s/30s/240s/60s intervals)

## Troubleshooting

### Lights Not Responding

1. Check if the lights are included in the monitoring configuration
2. Verify the lights are online and responding to basic commands
3. Check the logs for connectivity issues or error messages
4. Consider increasing the check interval or timeout settings

### Too Many Retries

1. Check for connectivity issues with the lights
2. Verify the lights are not being controlled by other automations
3. Consider increasing the cooldown period
4. Check if the lights are in the correct state before sending commands

### Performance Issues

1. Reduce the number of lights being monitored
2. Increase the check interval to reduce polling frequency
3. Disable debug logging if not needed
4. Check for network congestion or WiFi issues

## Contributing

Feel free to submit issues, feature requests, or pull requests to improve this app.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support, please:
1. Check the troubleshooting section above
2. Review the logs in your Hubitat hub
3. Open an issue on GitHub with detailed information about your setup and the problem you're experiencing

## Author

Simon Mason - Hubitat community developer 