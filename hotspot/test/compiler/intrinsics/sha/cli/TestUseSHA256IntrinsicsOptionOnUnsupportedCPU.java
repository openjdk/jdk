/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 */

/**
 * @test
 * @bug 8035968
 * @summary Verify UseSHA256Intrinsics option processing on unsupported CPU,
 * @library /testlibrary /test/lib /compiler/testlibrary testcases
 * @modules java.base/sun.misc
 *          java.management
 * @build TestUseSHA256IntrinsicsOptionOnUnsupportedCPU
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   TestUseSHA256IntrinsicsOptionOnUnsupportedCPU
 */
public class TestUseSHA256IntrinsicsOptionOnUnsupportedCPU {
    public static void main(String args[]) throws Throwable {
        new SHAOptionsBase(
                new GenericTestCaseForUnsupportedSparcCPU(
                        SHAOptionsBase.USE_SHA256_INTRINSICS_OPTION),
                new GenericTestCaseForUnsupportedX86CPU(
                        SHAOptionsBase.USE_SHA256_INTRINSICS_OPTION),
                new GenericTestCaseForUnsupportedAArch64CPU(
                        SHAOptionsBase.USE_SHA256_INTRINSICS_OPTION),
                new UseSHAIntrinsicsSpecificTestCaseForUnsupportedCPU(
                        SHAOptionsBase.USE_SHA256_INTRINSICS_OPTION),
                new GenericTestCaseForOtherCPU(
                        SHAOptionsBase.USE_SHA256_INTRINSICS_OPTION)).test();
    }
}
