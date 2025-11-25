# About SLEEF

This directory contains the source code for the SLEEF library, the
**SIMD Library for Evaluating Elementary Functions**. For more information on
SLEEF, see https://sleef.org/.

The currently imported libsleef sources is version 3.6.1, which has
git tag `3.6.1` and git commit hash `6ee14bcae5fe92c2ff8b000d5a01102dab08d774`.

# About the libsleef integration in the JDK

The upstream original source code is available in
`src/jdk.incubator.vector/unix/native/libsleef/upstream`. However, this code is
not directly usable in the JDK build system, but is instead used as the base for
the generation of additional souce code files. This generation is done by
the libsleef CMake files. If this should have been done at build time, it would
have meant adding CMake as a required dependency to build the JDK.

Instead, we create these generated files only once, when we import a new
version of the libsleef source code, and check in the generated files into
the JDK source tree. The generated files reside in
`src/jdk.incubator.vector/unix/native/libsleef/generated`.

# Import instructions

To update the version of libsleef that is used in the JDK, clone
`https://github.com/shibatch/sleef.git`, and copy all files, except the `docs`,
`.github` and `.git` directories, into
`src/jdk.incubator.vector/unix/native/libsleef/upstream`.

The libsleef source code does not follow the JDK whitespace rules as enforced by
jcheck. You will need to remove trailing whitespace, and expand tabs to 8
spaces in the imported source code.

Update the note above with information about what version you import.

You will need to repeat the process below for each of the platforms in the JDK
that uses libsleef; currently this is aarch64 and riscv64. The rest of this
instruction assumes you are doing this on linux/x64; at this point, any other
setup is not supported. Also, make sure you have CMake installed.

First, run configure for cross-compiling to your selected target platform
(e.g. aarch64).

Run `make update-sleef-source` to process the upstream source code and
store the generated files in the `generated` directory.

Now, you can repeat this for the next platform. For instance, you can
create a separate configuration using `configure --with-conf-name=riscv64` and
then generate the updated libsleef source code by
`make update-sleef-source CONF=riscv64`.

Finally, verify with git that the local changes made to the files in
`src/jdk.incubator.vector/unix/native/libsleef/generated` look okay.
