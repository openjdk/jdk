/*
 * Copyright Amazon.com Inc. or its affiliates. All rights reserved.
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
 * This tests object array sizes by running gtests with different settings.
 */

/* @test id=with-coops-with-ccp
 * @summary Run object array size tests with compressed oops and compressed class pointers
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @run main/native GTestWrapper --gtest_filter=objArrayOop -XX:+UseCompressedClassPointers -XX:+UseCompressedOops
 */
/* @test id=with-coops-no-ccp
 * @summary Run object array size tests with compressed oops and compressed class pointers
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @run main/native GTestWrapper --gtest_filter=objArrayOop -XX:-UseCompressedClassPointers -XX:+UseCompressedOops
 */
/* @test id=no-coops-with-ccp
 * @summary Run object array size tests with compressed oops and compressed class pointers
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @run main/native GTestWrapper --gtest_filter=objArrayOop -XX:+UseCompressedClassPointers -XX:-UseCompressedOops
 */
/* @test id=no-coops-no-ccp
 * @summary Run object array size tests with compressed oops and compressed class pointers
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @run main/native GTestWrapper --gtest_filter=objArrayOop -XX:-UseCompressedClassPointers -XX:-UseCompressedOops
 */

/* @test id=with-coops-with-ccp-large-align
 * @summary Run object array size tests with compressed oops and compressed class pointers
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @run main/native GTestWrapper --gtest_filter=objArrayOop -XX:+UseCompressedClassPointers -XX:+UseCompressedOops -XX:ObjAlignmentInBytes=256
 */
/* @test id=with-coops-no-ccp-large-align
 * @summary Run object array size tests with compressed oops and compressed class pointers
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @run main/native GTestWrapper --gtest_filter=objArrayOop -XX:-UseCompressedClassPointers -XX:+UseCompressedOops -XX:ObjAlignmentInBytes=256
 */
/* @test id=no-coops-with-ccp-large-align
 * @summary Run object array size tests with compressed oops and compressed class pointers
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @run main/native GTestWrapper --gtest_filter=objArrayOop -XX:+UseCompressedClassPointers -XX:-UseCompressedOops -XX:ObjAlignmentInBytes=256
 */
/* @test id=no-coops-no-ccp-large-align
 * @summary Run object array size tests with compressed oops and compressed class pointers
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @run main/native GTestWrapper --gtest_filter=objArrayOop -XX:-UseCompressedClassPointers -XX:-UseCompressedOops -XX:ObjAlignmentInBytes=256
 */
