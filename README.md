# Nextlit
[Jump to source](app/src/main/java/eu/biqqles/nextlit/)

**Nextlit** is a simple app that activates the [LP5523](http://www.ti.com/product/LP5523)-controlled segmented LEDs on the back of the [Nextbit Robin](https://en.wikipedia.org/wiki/Nextbit_Robin) and employs them as a fancy notification light. It's something I've seen quite a few people asking for, and after playing around with the kernel interface for a bit I realised it is pretty simple to utilise them for purposes beyond their rather limited use in the stock ROM.

Nextlit is very much a work in progress. Functionality is pretty basic at the moment, and it's currently more of a technology demonstrator than anything. The app allows you to preview the five patterns programmed into the LP5523 by Nextbit, and select one to activate when a notification is received.

If you want to use the app for any more than just previewing the available patterns, you'll need to give it notification access. The application does not read or care about the contents of notifications; the only thing is does is count them. The notification service is not persistent between reboots, so you'll need to manually re-enable it in the app if you reboot. Currently the service makes no attempt to reduce battery drain, but it doesn't seem too bad in my limited testing.

Make sure to turn off battery pulse in Settings if your ROM supports it and you want to see the LEDs while charging.

The app needs root in order to write to `/sys`. It'll work on all LOS and AOSP -based ROMs, and *should* work on stock, though I haven't tested it.

### To do
- minimise battery drain
- ‎custom patterns (I think I know how to do this, but needs testing)
- customisation: show only when screen off, ‎app blacklist, etc.
