/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

/*
 * This runs the "compressedKlass" class of gtests.
 * Note: we try to trigger bugs by enforcing the JVM to use zero-based mode. To increase the chance of zero-based
 * mode, we start with CDS disabled, a small class space and a large (albeit uncommitted, to save memory) heap. The
 * JVM will likely place the class space in low-address territory.
 * (If it does not manage to do this, the test will still succeed, but it won't alert us on regressions)
 */

/* @test id=use-zero-based-encoding
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @run main/native GTestWrapper --gtest_filter=CompressedKlass* -XX:-UseCompactObjectHeaders -Xlog:metaspace* -Xmx6g -Xms128m -Xshare:off -XX:CompressedClassSpaceSize=128m
 */

/* @test id=ccp_off
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @run main/native GTestWrapper --gtest_filter=CompressedKlass* -XX:-UseCompressedClassPointers -Xlog:metaspace* -Xmx6g -Xms128m
 */

/* @test id=use-zero-based-encoding-coh
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @run main/native GTestWrapper --gtest_filter=CompressedKlass* -XX:+UseCompactObjectHeaders -Xlog:metaspace* -Xmx6g -Xms128m -Xshare:off -XX:CompressedClassSpaceSize=128m
 */

/* @test id=use-zero-based-encoding-coh-large-class-space
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @run main/native GTestWrapper --gtest_filter=CompressedKlass* -XX:+UseCompactObjectHeaders -Xlog:metaspace* -Xmx6g -Xms128m -Xshare:off -XX:CompressedClassSpaceSize=4g
 */
