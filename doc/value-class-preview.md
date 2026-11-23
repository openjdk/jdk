% Migration of JDK Classes to Value Classes

## Introduction

The Value Objects feature introduces value objects and migrates suitable classes
to value classes. This means that when preview features are enabled, different
class files are used for the migrated classes in the Java class library.

To accomplish this, a built JDK uses *preview-specific* files in `META-INF/preview`,
which overrides the regular files of the same name. For example,
`META-INF/preview/java/lang/Integer.class` overrides `java/lang/Integer.class`.

The JDK generates preview-specific source files (they may use preview language
features), compiles class files from them, and distributes these class files in
`META-INF/preview`.

## The Build Process

### The Custom Handling for the Value Objects JEP

The Value Objects JEP requires a few select classes in the `java.base` module
to become value classes when preview features are enabled.

The build of `java.base` module first creates the source code of those value
classes, done in
[`GensrcValueClasses.gmk`](../make/modules/java.base/gensrc/GensrcValueClasses.gmk).

1. A hardcoded list of regular source files are selected for preview-specific
   generation.

2. Extract the content of each regular source file, search for any occurrences
   of `/*value*/ class` or `/*value*/ record`, and replace with `value class`
   or `value record`.

3. The replaced contents are written to the preview-specific generated files,
   located in `support/gensrc-valueclasses/java.base/`.  The regular source
   files remain unchanged in their original locations.

4. The general preview source to binary build pipeline recognizes the
   `support/gensrc-valueclasses/java.base/` directory as where the `java.base`
   module places its preview-specific source files.

### The General Preview Source to Binary Pipeline

Once the preview-specific source files are ready, they are picked up by the
build system into a fully automated pipeline handling all modules and all
outcome images.

1. The `GENERATED_PREVIEW_SUBDIRS` variable in [`make/common/Modules.gmk`](../make/common/Modules.gmk)
   indicates where the source files are found.

2. For each module that has preview-specific source files, a goal is created
   to compile these source files into class files.

3. The class files from each of these tasks reside in `support/preview/<module>`
   for each module.

4. These preview-specific class files and other resources are copied to the
   `META-INF/preview` directory of the regular output directory.

5. At run-time, jimage will pick up the preview-specific overrides from
   `META-INF/preview` only when preview features are enabled.

6. The interim javac used by the build system cannot pick up the
   preview-specific overrides; they must be supplied explicitly with the
   following javac flag for every single module where overrides are significant:

   ```
   --patch-module <module>=$(SUPPORT_OUTPUTDIR)/preview/<module>
   ```

   See [`BuildMicroBenchmarks.gmk`](../make/test/BuildMicrobenchmark.gmk) for an example.

### Non-Goals

The Value Objects JEP only plans to introduce value classes that are:

1. In the `java.base` module.

   There's no plan to migrate other classes in other modules.

2. Migrated from existing classes.

   These classes are available as identity classes when preview features are
   disabled. There's no plan to introduce completely new value classes.

Support for other value classes would require significant changes to the build
system.

## Testing

In addition to tests that require preview features to be enabled, tests that do
not depend on preview features wish to run with preview features enabled to
ensure compatibility:

1. Some tests wish to run against the Java SE class library with value classes.

   The jtreg tests may be run with `JTREG=VM_OPTIONS=--enable-preview`.

2. Some tests wish to run against their own classes migrated to value classes.

   The jtreg [VALUE_CLASS_PLUGIN](testing.html#VALUE_CLASS_PLUGIN) allows tests
   to migrate their own classes to value classes when running with the plugin.

## Wrapper Class Caches

Currently, wrapper class caches are retained even when preview features are
enabled to address performance losses. They have no semantic impact to value
objects.

In interpreter or C1 execution in Hotspot, allocations of a value object to the
heap as a full object with header happen when a value object is:

1. Loaded from a flat storage (field or array)
2. Created by a constructor
3. If C2 uses scalarized calling convention, at C2 to C1/interpreter calls and returns

Ideally, C2 can eliminate such allocations, but this does not work if the
resulting object is stored into references. Unfortunately, many uses of boxing
conversions store the resulting wrapper objects as references.

For the uses that store wrapper objects to references, if the boxing
conversion is:

1. Returning a value object from a flat cache array
2. Calling the value class constructor

Then we would have heap allocation on every single use.

To avoid the allocations, we fall back to returning a value object from a
reference cache array, from which the loaded reference is directly storable
into a destination that wants a reference without any allocation.

Since Hotspot may create flat arrays if an array of value objects is requested
by regular Java array creation mechanisms, we use `ValueClass.newReferenceArray`
to ensure we always create a reference cache array.

The cache array for value objects may be removed without notice if the
performance losses from allocations are no longer significant.

## Editing This Document

If you want to contribute changes to this document, edit `doc/value-class-preview.md`
and then run `make update-build-docs` to generate the same changes in
`doc/value-class-preview.html`.

---
# Override styles from the base CSS file that are not ideal for this document.
header-includes:
 - '<style type="text/css">pre, code, tt { color: #1d6ae5; }</style>'
---
