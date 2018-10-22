Changelog
=========

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
