Changelog
=========

## 0.7.0 (1/8/2020)

Library

 * Added support for scaling x-axis and y-axis independently 
 * Added support for locking scales on each supported axis
 * Added support for centering per axis
 * Fixed a bug where the scrollbar positions would become out of sync with the current transformation when the size of the GesturePane changes
 * Fixed a bug where scrollbar can scroll out of bounds if the opposite side scrollbar is not enabled
 * Fixed a bug where setting new content will not remove the old one    

## 0.6.0 (28/4/2020)

Library

 * Replaced vbar/hbar enabled with proper ScrollBarPolicy similar to ScrollPane's implementation 
 * Fixed an issue where scrollbars cannot be smaller than the preferred dimension

**Note: Changes are not source or binary incompatible to previous versions.**

## 0.5.0 (30/9/2019)


Library 

 * Fixed a severe layout bug where layout passes are infinitely trampolined, causing high CPU usage or unbounded memory usage
 * Much better API documentation coverage
 * MinScale/MaxScale and ScrollZoomFactor have defaults as documented constants

Sample

 * Add instructions on running with Java11+ and OpenJFX11+

## 0.4.0 (22/9/2019)

Library

 * Exposed the `changing` property to check whether there is an ongoing gesture or animation
 * Shortcut based zooming now also has start and end events(based on the shortcut key press)
 * Control will now properly focus when interacted upon
 * Fixed a bug where touchscreen enabled Windows devices will receive double start and end events
 * Fixed a bug where scrollbars will not generate change events
 
Sample

* Fixed an issue in samples where webkit related error prevents launch

## 0.3.0 (21/10/2018)

Library

 * Exposed getter for the underlying Affine transformation
 * Added `FitMode.UNBOUNDED` to allow for unconstrained translation while scale is smaller than viewport
 * Added `cover()` which depending on the current `FitMode` will ensure the image is centered and at the minimum possible scale
 * Fixed a bug where binding to `currentScale` only scales the X axis
 * Fixed a bug where animated `zoomTo` and `zoomBy` does not respect min and max
 * Fixed a bug where scrollbar would only cover the max dimension minus the scrollbar dimension
 * Fixed a bug where zooming to min scale with `FitMode.COVER` causes scrollbar to go out of sync
 * Fixed a bug where layout bound changes or disabled scrollbar with `FitMode.COVER` causes transforms to go out of bound 
 * Fixed a bug where zoom gesture at min scale generates more events than expected
 * Tested with OpenJFX11

Sample

 * Fixed WebView demo(switched to SVG)
 * Fixed ComplexNode demo to not block mouse events
 * Right click to reset zoom
 
Release

 * Bumped maven plugins and wrapper to latest version
 * Added automatic module name `net.kurobako.gesturefx`

## 0.2.0 (10/09/2017)

Library

 * Improved AffineEvent API with named methods and delta values
 * Fixed an issue where the corner of hbar and vbar is transparent
 * Fixed an issue where CHANGE_STARTED and CHANGE_FINISHED will not be fired in some cases
 
Sample

 * Added Swing demo
 * Handle missing HostService gracefully 

## 0.1.0 (07/08/2017)

 * Initial release
