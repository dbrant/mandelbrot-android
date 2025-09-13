# mandelbrot-android

Simple, high-performance Mandelbrot explorer app for Android.

Offers two modes of rendering:

## Simple double-precision

- Limited zoom, CPU rendering.
- Drag to move around, and pinch to zoom in and out.
- Tap the Julia button in the toolbar to enable Julia mode. While the Julia window is visible, you can still drag and zoom the Mandelbrot, and also drag and zoom the Julia.
- Tap the Settings button in the toolbar to select the formula, adjust the number of iterations, and see coordinate information.
- Tap the Color Scheme button in the toolbar to change colors.

## Arbitrary precision with GMP

(adapted from https://github.com/HastingsGreer/mandeljs)

- Unlimited zoom.
- Rendering with GL shaders.
- JNI library (with GMP and MPFR) for arbitrary precision calculations.
- Tap to zoom in, tap toolbar button to zoom out.
- Tap the Settings button in the toolbar to change iterations and adjust colors.

## Building the "prebuilt" GMP and MPFR libraries

- This app uses the GMP and MPFR libraries, compiled from source, using the Android NDK build tools.
- Run the `buildgmp.sh` script, after updating it to point to the correct NDK directory, and the correct GMP and MPFR library sources.
- When the build finishes, it should generate a `prebuilt` directory; copy this to `app/src/main/cpp`.

## License

    Copyright 2014+ Dmitry Brant
    See LICENSE file in the repository.
