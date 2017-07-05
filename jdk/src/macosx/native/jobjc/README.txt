#title JObjC
#
# Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

JObjC core provides a pure Java interface for calling C functions and
sending ObjC messages. Given some information, it can marshal types
automatically.

It also parses BridgeSupport to generate Java wrappers around
Framework bundles. These wrappers rely on the core to provide access
to the C constants, enums, structs, functions, ObjC classes, etc of a
framework.

* How to build it

Your best option is `ant all`. There's an Xcode "B&I" target that
works for buildit.

You'll need a recent JavaNativeFoundation, and perhaps some other
things. Everything is usually there on SnowLeopard (or Leopard after
the common ~javabuild/bin/update runs).

The build process is quite involved. Xcode takes care of the native
parts, ant takes care of the Java parts, and there's an unholy mix of
external targets and hidden dependencies that keep Xcode and ant (and
buildit on top of that) from stepping on each other. So a warning: the
ant and Xcode targets don't have proper dependencies set up because of
this. They have some dependencies configured, but not the entire
chain. This is because of the jumping back and forth between
externals. If you run the aggregate targets (Xcode B&I, ant all, ant
test, ant bench), everything's is good. But if you manually invoke
individual targets, chances are you'll miss something. Let's go over
it all step by step:

** ant gen-pcoder

The PrimitiveCoder subclasses have a lot of boiler plate which
simplifies the generated MixedPrimitiveCoder classes. So instead of
maintaining it, I maintain a tiny Haskell script that spits out the
Java code. This ant target runs that script if Haskell is available on
the system. If it isn't available, this will silently fail. That's
okay, because chances are the PrimitiveCoder.java that you got from
svn is current and does not need to be updated.

** ant build-core / Xcode build-core-java

Build core simply builds the JObjC core java classes, and also
generates headers for the JNI for Xcode.

** ant build-core-native / Xcode build-core-native

Xcode builds the native core, using the headers from the Java core. It
generates libJObjC.dylib.

** ant build-generator / Xcode build-generator-java

ant builds the generator.

** ant run-generator / Xcode run-generator

ant runs the generator, using the core Java and native classes.

What is rungen? And what's run-generator-old? run-generator-old is the
preferred way to run the generator from ant, but there's a strange bug
when running from buildit that causes run-generator-old to
freeze. Pratik was helping me debug it, inspecting the stack and
snooping dtrace probes, but we never found the reason for the
block. So I figured that maybe if I just add a layer of indirection
maybe it'll work around that. And it did. Sad but true.

** ant build-generated / Xcode build-generated-java

Build the generator output.

** ant build-additions / Xcode build-additions-java

Builds java additions.

** ant build-additions-native / Xcode build-additions-native

This builds a new version of libJObjC.dylib. It will rebuild
everything from the core, and include everything from additions.

** ant assemble-product / Xcode assemble-product-java

Create a jar, copy products to destination, etc.

* How to test it

The test cases also contain a Java component and a native component,
and are built similarly to the above. The benchmarks are built
together with the tests. So "ant build-test" and "ant
build-test-native" will build both the benchmarks and the test. "ant
test" will run the test. "ant bench" will run benchmarks. If you only
want to run a specific benchmark, you can pass a regexp in the
environment variable BENCH_MATCH.

<src>
ant test
ant bench
BENCH_MATCH=Foo ant bench
</src>

Test and bench reports will end up in
build/JObjC.build/Debug/test-reports/

* How to use it

Include the jar in your classpath and set your java.library.path to
the directory that contains the dylib. Same thing for app bundles.
