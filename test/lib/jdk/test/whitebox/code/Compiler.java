/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.whitebox.code;

import java.lang.reflect.Executable;
import jdk.test.whitebox.WhiteBox;

/**
 * API to obtain information about enabled JIT compilers
 * retrieved from the VM with the WhiteBox API.
 */
public class Compiler {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    /**
     * Check if C2 is used as JIT compiler.
     *
     * C2 is enabled if following conditions are true:
     * - we are not in Interpreter mode
     * - we are in Server compilation mode
     * - TieredCompilation is not used or TieredStopAtLevel is greater than 3
     *
     * @return true if C2 is used as JIT compiler.
     */
    public static boolean isC2Enabled() {
        Boolean useCompiler = WB.getBooleanVMFlag("UseCompiler");
        if (useCompiler == null || !useCompiler) {
            return false;
        }
        Boolean serverMode = WB.getBooleanVMFlag("ProfileInterpreter");
        if (serverMode == null || !serverMode) {
            return false;
        }

        Boolean tieredCompilation = WB.getBooleanVMFlag("TieredCompilation");
        Long compLevel = WB.getIntxVMFlag("TieredStopAtLevel");
        // if TieredCompilation is enabled and compilation level is <= 3 then C2 is not used
        if (tieredCompilation != null && tieredCompilation &&
            compLevel != null && compLevel <= 3) {
            return false;
        }

        return true;
    }

    /*
     * Check if C1 is used as JIT compiler.
     *
     * C1 is enabled if following conditions are true:
     * - we are not in Interpreter mode
     * - we are not in Server compilation mode
     * - TieredCompilation is used in Server mode
     *
     * @return true if C1 is used as JIT compiler.
     */
    public static boolean isC1Enabled() {
        Boolean useCompiler = WB.getBooleanVMFlag("UseCompiler");
        if (useCompiler == null || !useCompiler) {
            return false;
        }
        Boolean serverMode = WB.getBooleanVMFlag("ProfileInterpreter");
        if (serverMode == null || !serverMode) {
            return true; // Client mode
        }

        Boolean tieredCompilation = WB.getBooleanVMFlag("TieredCompilation");
        // C1 is not used in server mode if TieredCompilation is off.
        if (tieredCompilation != null && !tieredCompilation) {
            return false;
        }
        return true;
    }

    /*
     * Determine if the compiler corresponding to the compilation level 'compLevel'
     * provides an intrinsic for 'class'.'method'.
     */
    public static boolean isIntrinsicAvailable(int compLevel, String klass, String method, Class<?>... parameterTypes) {
        Executable intrinsicMethod;
        try {
            intrinsicMethod = Class.forName(klass).getDeclaredMethod(method, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Test bug, '" + method + "' method unavailable. " + e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Test bug, '" + klass + "' class unavailable. " + e);
        }
        return WB.isIntrinsicAvailable(intrinsicMethod, compLevel);
    }
}
