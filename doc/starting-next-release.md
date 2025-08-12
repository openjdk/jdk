% Explanation of start of release changes

## Overview

The start of release changes, the changes that turn JDK _N_ into JDK
(_N_+1), are primarily small updates to various files along with new files to
store symbol information to allow `javac --release N ...` to run on
JDK (_N_+1).

The updates include changes to files holding meta-data about the
release, files under the `src` directory for API and tooling updates,
and incidental updates under the `test` directory.

## Details and file updates

As a matter of policy, there are a number of semantically distinct
concepts which get incremented separately at the start of a new
release:

* Feature value of `Runtime.version()`
* Highest source version modeled by `javax.lang.model.SourceVersion`
* Highest class file format major version recognized by the platform
* Highest `-source`/`-target`/`--release` argument recognized by
  `javac` and related tools

The expected file updates are listed below. Additional files may need
to be updated for a particular release.

### Meta-data files

* `jcheck/conf`: update meta-data used by `jcheck` and the Skara tooling
* `make/conf/version-numbers.conf`: update to meta-data used in the build

### `src` files

* `src/hotspot/share/classfile/classFileParser.cpp`: add a `#define`
  for the new version
* `src/java.base/share/classes/java/lang/classfile/ClassFile.java`:
  add a constant for the new class file format version
* `src/java.base/share/classes/java/lang/reflect/ClassFileFormatVersion.java`:
   add an `enum` constant for the new class file format version
* `src/java.compiler/share/classes/javax/lang/model/SourceVersion.java`:
  add an `enum` constant for the new source version
* `src/java.compiler/share/classes/javax/lang/model/util/*` visitors: Update
  `@SupportedSourceVersion` annotations to latest value. Note this update
  is done in lieu of introducing another set of visitors for each Java
  SE release.
* `src/jdk.compiler/share/classes/com/sun/tools/javac/code/Source.java`:
   add an `enum` constant for the new source version internal to `javac`
* `src/jdk.compiler/share/classes/com/sun/tools/javac/jvm/ClassFile.java`:
   add an `enum` constant for the new class file format version internal to `javac`
* `src/jdk.compiler/share/classes/com/sun/tools/javac/jvm/Target.java`:
   add an `enum` constant for the new target version internal to `javac`
* `src/jdk.compiler/share/classes/com/sun/tools/javac/processing/PrintingProcessor.java`
   update printing processor to support the new source version
* The symbol information for `--release` is stored as new text files in the
  `src/jdk.compiler/share/data/symbols` directory, one file per
  module. The README file in that directory contains directions on how
  to create the files.

### `test` files

* `test/langtools/tools/javac/api/TestGetSourceVersions.java`: add new `SourceVersion` constant to test matrix.
* `test/langtools/tools/javac/classfiles/ClassVersionChecker.java`: add new enum constant for the new class file version
* `test/langtools/tools/javac/lib/JavacTestingAbstractProcessor.java`
   update annotation processor extended by `javac` tests to cover the new source version
* `test/langtools/tools/javac/preview/classReaderTest/Client.nopreview.out` and `test/langtools/tools/javac/preview/classReaderTest/Client.preview.out`: update expected messages for preview errors and warnings

