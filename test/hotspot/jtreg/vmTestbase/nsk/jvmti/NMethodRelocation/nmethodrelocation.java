/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package nsk.jvmti.NMethodRelocation;

import java.io.PrintStream;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Objects;

import nsk.share.*;
import nsk.share.jvmti.*;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.NMethod;
import jdk.test.whitebox.code.BlobType;

public class nmethodrelocation extends DebugeeClass {

    protected static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    /** Value of {@code -XX:CompileThreshold} */
    protected static final int COMPILE_THRESHOLD
        = Integer.parseInt(getVMOption("CompileThreshold", "10000"));

    /** Load native library if required. */
    static {
        loadLibrary("agentnmethodrelocation001");
    }

    /**
     * Returns value of VM option.
     *
     * @param name option's name
     * @return value of option or {@code null}, if option doesn't exist
     * @throws NullPointerException if name is null
     */
    protected static String getVMOption(String name) {
        Objects.requireNonNull(name);
        return Objects.toString(WHITE_BOX.getVMFlag(name), null);
    }

    /**
     * Returns value of VM option or default value.
     *
     * @param name         option's name
     * @param defaultValue default value
     * @return value of option or {@code defaultValue}, if option doesn't exist
     * @throws NullPointerException if name is null
     * @see #getVMOption(String)
     */
    protected static String getVMOption(String name, String defaultValue) {
        String result = getVMOption(name);
        return result == null ? defaultValue : result;
    }
    public static void main(String argv[]) throws Exception {
        argv = nsk.share.jvmti.JVMTITest.commonInit(argv);

        run();
    }

    static int status = Consts.TEST_PASSED;

    public static int run() throws Exception {
        Executable method = nmethodrelocation.class.getDeclaredMethod("compiledMethod");
        WHITE_BOX.testSetDontInlineMethod(method, true);

        compile();

        NMethod originalNMethod = NMethod.get(method, false);
        if (originalNMethod == null) {
            throw new AssertionError("Could not find original nmethod");
        }

        WHITE_BOX.relocateNMethodFromMethod(method, BlobType.MethodNonProfiled.id);

        NMethod relocatedNMethod = NMethod.get(method, false);
        if (relocatedNMethod == null) {
            throw new AssertionError("Could not find relocated nmethod");
        }

        if (originalNMethod.address == relocatedNMethod.address) {
            throw new AssertionError("Relocated nmethod same as original");
        }

        WHITE_BOX.deoptimizeAll();

        WHITE_BOX.fullGC();
        WHITE_BOX.fullGC();

        status = checkStatus(status);

        System.out.printf("Relocated nmethod from 0x%016x to 0x%016x%n", originalNMethod.code_begin, relocatedNMethod.code_begin);
        System.out.flush();

        return status;
    }

    private static void compile() {
        for (int i = 0; i < COMPILE_THRESHOLD; i++) {
            compiledMethod();
        }
    }

    public static long compiledMethod() {
        return 0;
    }
}
