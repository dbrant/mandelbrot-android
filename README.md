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



## License

    Copyright 2014+ Dmitry Brant
    See LICENSE file in the repository.
