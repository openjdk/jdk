/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8138651
 *
 * @requires !vm.graal.enabled
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   -XX:DisableIntrinsic=_loadFence,_fullFence
 *                   -XX:DisableIntrinsic=_storeStoreFence
 *                   -XX:CompileCommand=option,jdk.internal.misc.Unsafe::storeFence,ccstrlist,DisableIntrinsic,_getReferenceMO,_putReferenceMO
 *                   -XX:CompileCommand=option,jdk.internal.misc.Unsafe::loadFence,ccstrlist,DisableIntrinsic,_getPrimitiveBitsMO
 *                   compiler.intrinsics.IntrinsicDisabledTest
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   -XX:ControlIntrinsic=-_loadFence,-_fullFence
 *                   -XX:ControlIntrinsic=-_storeStoreFence
 *                   -XX:CompileCommand=ControlIntrinsic,jdk.internal.misc.Unsafe::storeFence,-_getReferenceMO,-_putReferenceMO
 *                   -XX:CompileCommand=ControlIntrinsic,jdk.internal.misc.Unsafe::loadFence,-_getPrimitiveBitsMO
 *                   compiler.intrinsics.IntrinsicDisabledTest
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   -XX:ControlIntrinsic=+_storeStoreFence,+_loadFence,+_fullFence
 *                   -XX:DisableIntrinsic=_loadFence,_fullFence
 *                   -XX:DisableIntrinsic=_storeStoreFence
 *                   -XX:CompileCommand=ControlIntrinsic,jdk.internal.misc.Unsafe::storeFence,+_getReferenceMO,+_putReferenceMO
 *                   -XX:CompileCommand=ControlIntrinsic,jdk.internal.misc.Unsafe::loadFence,+_getPrimitiveBitsMO
 *                   -XX:CompileCommand=DisableIntrinsic,jdk.internal.misc.Unsafe::storeFence,_getReferenceMO,_putReferenceMO
 *                   -XX:CompileCommand=DisableIntrinsic,jdk.internal.misc.Unsafe::loadFence,_getPrimitiveBitsMO
 *                   compiler.intrinsics.IntrinsicDisabledTest
*/

package compiler.intrinsics;

import jdk.test.lib.Platform;
import jdk.test.whitebox.WhiteBox;
import compiler.whitebox.CompilerWhiteBoxTest;

import java.lang.reflect.Executable;

public class IntrinsicDisabledTest {

    private static final WhiteBox wb = WhiteBox.getWhiteBox();

    /* Determine if tiered compilation is enabled. */
    private static final boolean TIERED_COMPILATION = wb.getBooleanVMFlag("TieredCompilation");

    private static final int TIERED_STOP_AT_LEVEL = wb.getIntxVMFlag("TieredStopAtLevel").intValue();

    /* This test uses several methods from java.lang.Math. The method
     * getMethod() returns a different Executable for each name.
     * These methods were selected because they can be intrinsified by
     *  both the C1 and the C2 compiler.
     */
    static Executable getMethod(String name, Class<?>... parameters) throws RuntimeException {
        try {
            return jdk.internal.misc.Unsafe.class.getDeclaredMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Test bug, method is unavailable. " + e);
        }
    }

    public static void test(int compLevel) {

        Executable loadFence  = getMethod("loadFence");
        Executable storeFence  = getMethod("storeFence");
        Executable storeStoreFence  = getMethod("storeStoreFence");
        Executable fullFence = getMethod("fullFence");
        Executable getReferenceMO = getMethod("getReferenceMO", byte.class, Object.class, long.class);
        Executable putReferenceMO  = getMethod("putReferenceMO", byte.class, Object.class, long.class, Object.class);
        Executable getPrimitiveBitsMO  = getMethod("getPrimitiveBitsMO", byte.class, byte.class, Object.class, long.class);
        Executable putPrimitiveBitsMO = getMethod("putPrimitiveBitsMO", byte.class, byte.class, Object.class, long.class, long.class);

        /* Test if globally disabling intrinsics works. */
        if (!wb.isIntrinsicAvailable(storeFence, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + storeFence.toGenericString() +
                                       "] is not available globally although it should be.");
        }

        if (wb.isIntrinsicAvailable(loadFence, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + loadFence.toGenericString() +
                                       "] is available globally although it should not be.");
        }

        if (wb.isIntrinsicAvailable(fullFence, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + fullFence.toGenericString() +
                                       "] is available globally although it should not be.");
        }

        if (wb.isIntrinsicAvailable(storeStoreFence, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + storeStoreFence.toGenericString() +
                                       "] is available globally although it should not be.");
        }

        /* Test if disabling intrinsics on a per-method level
         * works. The method for which intrinsics are
         * disabled (the compilation context) is storeFence. */
        if (!wb.isIntrinsicAvailable(putPrimitiveBitsMO, storeFence, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + putPrimitiveBitsMO.toGenericString() +
                                       "] is not available for intrinsification in [" +
                                       storeFence.toGenericString() + "] although it should be.");
        }

        if (wb.isIntrinsicAvailable(getReferenceMO, storeFence, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + getReferenceMO.toGenericString() +
                                       "] is available for intrinsification in [" +
                                       storeFence.toGenericString() + "] although it should not be.");
        }

        if (wb.isIntrinsicAvailable(putReferenceMO, storeFence, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + putReferenceMO.toGenericString() +
                                       "] is available for intrinsification in [" +
                                       storeFence.toGenericString() + "] although it should not be.");
        }

        if (wb.isIntrinsicAvailable(getPrimitiveBitsMO, loadFence, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + getPrimitiveBitsMO.toGenericString() +
                                       "] is available for intrinsification in [" +
                                       loadFence.toGenericString() + "] although it should not be.");
        }

        /* Test if disabling intrinsics on a per-method level
         * leaves those intrinsics enabled globally. */
        if (!wb.isIntrinsicAvailable(getReferenceMO, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + getReferenceMO.toGenericString() +
                                       "] is not available globally although it should be.");
        }

        if (!wb.isIntrinsicAvailable(putReferenceMO, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + putReferenceMO.toGenericString() +
                                       "] is not available globally although it should be.");
        }


        if (!wb.isIntrinsicAvailable(getPrimitiveBitsMO, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + getPrimitiveBitsMO.toGenericString() +
                                       "] is not available globally although it should be.");
        }

        /* Test if disabling an intrinsic globally disables it on a
         * per-method level as well. */
        if (!wb.isIntrinsicAvailable(storeFence, putPrimitiveBitsMO, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + storeFence.toGenericString() +
                                       "] is not available for intrinsification in [" +
                                       putPrimitiveBitsMO.toGenericString() + "] although it should be.");
        }

        if (wb.isIntrinsicAvailable(loadFence, putPrimitiveBitsMO, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + loadFence.toGenericString() +
                                       "] is available for intrinsification in [" +
                                       putPrimitiveBitsMO.toGenericString() + "] although it should not be.");
        }

        if (wb.isIntrinsicAvailable(fullFence, putPrimitiveBitsMO, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + fullFence.toGenericString() +
                                       "] is available for intrinsification in [" +
                                       putPrimitiveBitsMO.toGenericString() + "] although it should not be.");
        }

        if (wb.isIntrinsicAvailable(storeStoreFence, putPrimitiveBitsMO, compLevel)) {
            throw new RuntimeException("Intrinsic for [" + storeStoreFence.toGenericString() +
                                       "] is available for intrinsification in [" +
                                       putPrimitiveBitsMO.toGenericString() + "] although it should not be.");
        }
    }

    public static void main(String args[]) {
        if (Platform.isServer() && !Platform.isEmulatedClient() &&
                                   (TIERED_STOP_AT_LEVEL == CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION)) {
            if (TIERED_COMPILATION) {
                test(CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE);
            }
            test(CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        } else {
            test(CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE);
        }
    }
}
