% Explanation of start of release changes


## Overview

The start of release changes, the changes the turn JDK N into JDK
(N+1), are primarily small updates to various files along new files to
store symbol information to allow `javac --release N ...` to run on
JDK (N+1).

As a matter of policy, there are a number of semantically distinct
concepts which separately get incremented at the start of a new release:

* Feature value of `Runtime.version()`
* Highest class file format major version recognized by the platform
* Highest `-source`/`-target'/`--release` argument recognized by `javac`



In more detail, updated files include:

* `jcheck/conf`: update meta-data used by jcheck and the Skara tooling
* `make/conf/version-numbers.conf`: update to meta-data used in the build
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

The symbol information is stored as new text files in the
`src/jdk.compiler/share/data/symbols` directory, one file per module.
