# Nextlit
[Jump to source](app/src/main/java/eu/biqqles/nextlit/) | [Screenshots](https://forum.xda-developers.com/devdb/project/?id=24361#screenshots)

The [LP5523](http://www.ti.com/product/LP5523)-controlled segmented LEDs on the rear of the [Nextbit Robin](https://en.wikipedia.org/wiki/Nextbit_Robin) are one of the device's most unique features. Yet they see very little use, being used only for effects relating to the now-defunct "Smart Storage" in the stock ROM and simply displaying a pattern during boot in custom ROMs. Nextlit rectifies this by employing these lights as a useful notification indicator.

Nextlit allows you to preview the five patterns programmed into the LP5523 by Nextbit plus another five unique to the app, and select one to activate when a notification is received. You can configure patterns for individual apps, letting you identify which app requires your attention without needing to wake your phone. Nextlit exploits the potential of the segmented LEDs to encode greater meaning than can be expressed with the standard notification light and looks cool while doing it.

If you want to use the app for any more than simply previewing the available patterns you'll need to give its service notification access. The application does not read nor care about the contents of notifications; the only thing it does is count them. You can see the relevant routines [here](app/src/main/java/eu/biqqles/nextlit/NotificationLightsService.java).

The app needs root access in order to access `/sys` but will work on any ROM. Nextlit has been [featured on XDA](https://www.xda-developers.com/nextlit-nextbit-robin-led-notifications/) and is free software, released under the Mozilla General Public License version 2.0.

### To do
- user-definable patterns
    - necessitates an [assembler for the LP5523's instruction set](http://www.ti.com/lit/zip/snvc151)
- music visualisation
- BCD clock
- (optionally) disable lights while charging

### Changelog
#### 2.0
- First release marked as 'stable'
- Completely overhauled UI
- Added app icon
- Added per-app notification configuration
- Added five new patterns: Breathe, Pulse, Chase, Blink and Dapple
- By default, notifications will now only activate the lights if they would normally activate the standard notification LED
- Calls and alarms now activate the lights
- Added service configuration options:
    - Obey Do not disturb policy
    - Mimic standard LED behaviour (experimental)
- Minor changes:
    - Improved service stability
    - Fixed preview not stopping if service not bound
    - Renamed 'show for ongoing' to 'show for all notifications' to better reflect its function
    - Preview now ends on app pause
    - Service will now disable if unbound

#### 1.2
- Fixed possible force close on unrooted devices
- Service is now persistent between reboots
- Added 'show for ongoing notifications' option

#### 1.1
- Added option to enable lights when screen on

#### 1.0.1
- Fixed possible force close on service enable
- Fixed service failing to stop

#### 1.0
- Initial release
