/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2023 SAP SE. All rights reserved.
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
 * Note: This runs the metaspace-related parts of gtest in configurations which
 *  are not tested explicitly in the standard gtest. Hence, there is no "default-ndebug"
 *  since that would be equivalent to the normal gtest for release builds.
 */

/* @test id=default-debug
 * @bug 8251158
 * @summary Run metaspace-related gtests with all default but verifications enabled
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @requires vm.debug
 * @requires vm.flagless
 * @run main/native GTestWrapper --gtest_filter=metaspace* -XX:+UnlockDiagnosticVMOptions -XX:VerifyMetaspaceInterval=1
 */

/* @test id=no-ccs
 * @summary Run metaspace-related gtests with compressed class pointers off
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @requires vm.bits == 64
 * @requires vm.flagless
 * @run main/native GTestWrapper --gtest_filter=metaspace* -XX:+UnlockDiagnosticVMOptions -XX:-UseCompressedClassPointers
 */

/* @test id=UseCompactObjectHeaders
 * @summary Run metaspace-related gtests with tiny classpointers
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.xml
 * @requires vm.bits == 64
 * @requires vm.flagless
 * @requires vm.debug
 * @run main/native GTestWrapper --gtest_filter=metaspace* -XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders
 */
