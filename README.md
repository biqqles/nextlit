# Nextlit
[Jump to source](app/src/main/java/eu/biqqles/nextlit/)

**Nextlit** is a simple app which activates the [LP5523](http://www.ti.com/product/LP5523)-controlled segmented LEDs on the back of the [Nextbit Robin](https://en.wikipedia.org/wiki/Nextbit_Robin) and employs them as a fancy notification light. It's something I'd seen quite a few people asking for, and after playing around with the kernel interface for a bit I realised that it's pretty simple to utilise these lights for purposes beyond their rather limited use in the stock ROM. Nextlit has been [featured on XDA](https://www.xda-developers.com/nextlit-nextbit-robin-led-notifications/).

Nextlit is very much a work in progress. The app allows you to preview the five patterns programmed into the LP5523 by Nextbit as well as some "custom" ones unique to the app, and select one to activate when a notification is received. You can configure whether the lights should be enabled while the screen is on, and additionally whether they should be activated by ongoing notifications (e.g. music player controls).

If you want to use the app for any more than just previewing the available patterns, you'll need to give it notification access. The application does not read or care about the contents of notifications; the only thing is does is count them. Make sure to disable charging indication in Settings if your ROM supports it and you want to see notifications while charging.

The app needs root in order to write to `/sys` but will work on any ROM. Nextlit is released under the Mozilla General Public License version 2.0.

### To do
- ~~‎custom patterns~~ [done]
- ~~make service persistent between reboots~~ [done]
- customisation: per-app settings, ~~show only when screen off~~ [done], show only for unseen notifications etc.
- user-defined patterns
- pattern execution emulation (on-screen previews)
- music visualisation

### Changelog

*1.2*
- Fix possible force close on unrooted devices
- Service is now persistent between reboots
- Add 'show for ongoing notifications' option (default behaviour is false)

*1.1*
- Add option to enable lights when screen on (default behaviour is false)

*1.0.1*
- Fix possible force close on service enable
- Fix service failing to stop

*1.0*
- Initial release
