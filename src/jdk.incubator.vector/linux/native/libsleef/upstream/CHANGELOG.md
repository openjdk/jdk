## 3.6.1 - 2024-06-10

This patch release provides important bug fixes, including a fix
for API compatibility with 3.5 (#534).
The support and test for some features is still limited, as
documented in [README](./README.md), however significant progress
was made in order to test on Linux, macOS and Windows.

### Added
- Add support for RISC-V in DFT, QUAD and inline headers (#503,
  #522).
- Add GHA workflow to run CI tests on Windows x86 (#540) and macOS
  x86/aarch64 (#543). And update test matrix.
- Add GHA workflows to run examples in CI (#550).

### Changed
- Cleanup/Improve support for RISC-V in LIBM (#520, #521).
- Update supported environment in documentation (#529, #549),
  including website and test matrix from README.

### Fixed
- Major fix and cleanup of CMakeLists.txt (#531).
- Fix compatibility issue after removal of quad and long double
  sincospi (#545). Restores functions that are missing in 3.6.
- Various bug fixes (#528, #533, #536, #537).

## 3.6 - 2024-02-14

This release follows a long period of inactivity. The library is now
being actively maintained. However, the support and test for some
features is currently limited, as documented in [README](./README.md).

### Added
- Add documentation for the quad precision math library
- Enable generation of inline header file for CUDA (PR #337)
- Add support for System/390 z15 support (PR #343)
- Add support for POWER 9 (PR #360)
- Add quad-precision functions (PR #375, #377, #380, #381, #382, #383,
  #385, #386, #387)
- Add preliminary support for iOS and Android (PR #388, #389)
- Add OpenMP pragmas to the function declarations in sleef.h to enable
  auto-vectorization by GCC (PR #404, #406)
- Add new public CI test infrastructure using GitHub Actions (PR #476)
- Add support for RISC-V in libm (PR #477)

### Removed
- Remove old CI scripts based on Travis/Jenkins/Appveyor (PR #502)

### Changed
- Optimise error functions (PR #370)
- Update CMake package config (PR #412)
- Update documentation and move doc/website to main repository (PR #504,
  #513)
- Add SLEEF_ prefix to user-facing CMake options (PR #509)
- Disable SVE on Darwin (PR #512)

### Fixed
- Fix parallel builds with GNU make (PR #491)
- Various bug fixes (PR #492, #499, #508)

## 3.5.1 - 2020-09-15
### Changed
- Fixed a bug in handling compiler options

## 3.5 - 2020-09-01
- IBM System/390 support is added.
- The library can be built with Clang on Windows.
- Static libraries with LTO can be generated.
- Alternative division and sqrt methods can be chosen with AArch64.
- Header files for inlining the whole SLEEF functions can be generated.
- IEEE remainder function is added.
- GCC-10 can now build SLEEF with SVE support.

## 3.4.1 - 2019-10-01
### Changed
- Fixed accuracy problem with tan_u35, atan_u10, log2f_u35 and exp10f_u10.
  https://github.com/shibatch/sleef/pull/260
  https://github.com/shibatch/sleef/pull/265
  https://github.com/shibatch/sleef/pull/267
- SVE intrinsics that are not supported in newer ACLE are replaced.
  https://github.com/shibatch/sleef/pull/268
- FMA4 detection problem is fixed.
  https://github.com/shibatch/sleef/pull/262
- Compilation problem under Windows with MinGW is fixed.
  https://github.com/shibatch/sleef/pull/266

## 3.4 - 2019-04-28
### Added
- Faster and low precision functions are added.
  https://github.com/shibatch/sleef/pull/229
- Functions that return consistent results across platforms are
  added
  https://github.com/shibatch/sleef/pull/216
  https://github.com/shibatch/sleef/pull/224
- Quad precision math library(libsleefquad) is added
  https://github.com/shibatch/sleef/pull/235
  https://github.com/shibatch/sleef/pull/237
  https://github.com/shibatch/sleef/pull/240
- AArch64 Vector Procedure Call Standard (AAVPCS) support.
### Changed
- Many functions are now faster
- Testers are now faster

## 3.3.1 - 2018-08-20
### Added
- FreeBSD support is added
### Changed
- i386 build problem is fixed
- Trigonometric functions now evaluate correctly with full FP
  domain.
  https://github.com/shibatch/sleef/pull/210

## 3.3 - 2018-07-06
### Added
- SVE target support is added to libsleef.
  https://github.com/shibatch/sleef/pull/180
- SVE target support is added to DFT. With this patch, DFT operations
  can be carried out using 256, 512, 1024 and 2048-bit wide vectors
  according to runtime availability of vector registers and operators.
  https://github.com/shibatch/sleef/pull/182
- 3.5-ULP versions of sinh, cosh, tanh, sinhf, coshf, tanhf, and the
  corresponding testing functionalities are added.
  https://github.com/shibatch/sleef/pull/192
- Power VSX target support is added to libsleef.
  https://github.com/shibatch/sleef/pull/195
- Payne-Hanek like argument reduction is added to libsleef.
  https://github.com/shibatch/sleef/pull/197

## 3.2 - 2018-02-26
### Added
- The whole build system of the project migrated from makefiles to
  cmake. In particualr this includes `libsleef`, `libsleefgnuabi`,
  `libdft` and all the tests.
- Benchmarks that compare `libsleef` vs `SVML` on X86 Linux are
  available in the project tree under src/libm-benchmarks directory.
- Extensive upstream testing via Travis CI and Appveyor, on the
  following systems:
  * OS: Windows / Linux / OSX.
  * Compilers: gcc / clang / MSVC.
  * Targets: X86 (SSE/AVX/AVX2/AVX512F), AArch64 (Advanced SIMD), ARM
    (NEON). Emulators like QEMU or SDE can be used to run the tests.
- Added the following new vector functions (with relative testing):
  * `log2`
- New compatibility tests have been added to check that
  `libsleefgnuabi` exports the GNUABI symbols correctly.
- The library can be compiled to an LLVM bitcode object.
- Added masked interface to the library to support AVX512F masked
  vectorization.

### Changed
- Use native instructions if available for `sqrt`.
- Fixed fmax and fmin behavior on AArch64:
  https://github.com/shibatch/sleef/pull/140
- Speed improvements for `asin`, `acos`, `fmod` and `log`. Computation
  speed of other functions are also improved by general optimization.
  https://github.com/shibatch/sleef/pull/97
- Removed `libm` dependency.

### Removed
- Makefile build system

## 3.1 - 2017-07-19
- Added AArch64 support
- Implemented the remaining C99 math functions : lgamma, tgamma,
  erf, erfc, fabs, copysign, fmax, fmin, fdim, trunc, floor, ceil,
  round, rint, modf, ldexp, nextafter, frexp, hypot, and fmod.
- Added dispatcher for x86 functions
- Improved reduction of trigonometric functions
- Added support for 32-bit x86, Cygwin, etc.
- Improved tester

## 3.0 - 2017-02-07
- New API is defined
- Functions for DFT are added
- sincospi functions are added
- gencoef now supports single, extended and quad precision in addition to double precision
- Linux, Windows and Mac OS X are supported
- GCC, Clang, Intel Compiler, Microsoft Visual C++ are supported
- The library can be compiled as DLLs
- Files needed for creating a debian package are now included

## 2.120 - 2017-01-30
- Relicensed to Boost Software License Version 1.0

## 2.110 - 2016-12-11
- The valid range of argument is extended for trig functions
- Specification of each functions regarding to the domain and accuracy is added
- A coefficient generation tool is added
- New testing tools are introduced
- Following functions returned incorrect values when the argument is very large or small : exp, pow, asinh, acosh
- SIMD xsin and xcos returned values more than 1 when FMA is enabled
- Pure C cbrt returned incorrect values when the argument is negative
- tan_u1 returned values with more than 1 ulp of error on rare occasions
- Removed support for Java language(because no one seems using this)

## 2.100 - 2016-12-04
- Added support for AVX-512F and Clang Extended Vectors.

## 2.90 - 2016-11-27
- Added ilogbf. All the reported bugs(listed below) are fixed.
- Log function returned incorrect values when the argument is very small.
- Signs of returned values were incorrect when the argument is signed zero.
- Tester incorrectly counted ULP in some cases.
- ilogb function returned incorrect values in some cases.

## 2.80 - 2013-05-18
- Added support for ARM NEON. Added higher accuracy single
  precision functions : sinf_u1, cosf_u1, sincosf_u1, tanf_u1, asinf_u1,
  acosf_u1, atanf_u1, atan2f_u1, logf_u1, and cbrtf_u1.

## 2.70 - 2013-04-30
- Added higher accuracy functions : sin_u1, cos_u1, sincos_u1,
  tan_u1, asin_u1, acos_u1, atan_u1, atan2_u1, log_u1, and
  cbrt_u1. These functions evaluate the corresponding function with at
  most 1 ulp of error.

## 2.60 - 2013-03-26
- Added the remaining single precision functions : powf, sinhf,
  coshf, tanhf, exp2f, exp10f, log10f, log1pf. Added support for FMA4
  (for AMD Bulldozer). Added more test cases. Fixed minor bugs (which
  degraded accuracy in some rare cases).

## 2.50 - 2013-03-12
- Added support for AVX2. SLEEF now compiles with ICC.

## 2.40 - 2013-03-07
- Fixed incorrect denormal/nonnumber handling in ldexp, ldexpf,
  sinf and cosf. Removed support for Go language.

## 2.31 - 2012-07-05
- Added sincosf.

## 2.30 - 2012-01-20
- Added single precision functions : sinf, cosf, tanf, asinf,
  acosf, atanf, logf, expf, atan2f and cbrtf.

## 2.20 - 2012-01-09
- Added exp2, exp10, expm1, log10, log1p, and cbrt.

## 2.10 - 2012-01-05
- asin() and acos() are back.
- Added ilogb() and ldexp().
- Added hyperbolic functions.
- Eliminated dependency on frexp, ldexp, fabs, isnan and isinf.

## 2.00 - 2011-12-30
- All of the algorithm has been updated.
- Both accuracy and speed are improved since version 1.10.
- Denormal number handling is also improved.

## 1.10 - 2010-06-22
- AVX support is added. Accuracy tester is added.

## 1.00 - 2010-05-15
- Initial release
