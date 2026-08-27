/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package gc.testlibrary;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import jdk.test.lib.dcmd.JMXExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.NMethod;

public final class CodeCacheUtils {
    private CodeCacheUtils() {}

    /**
     * Compiles the given static no-argument method at level 1 and makes it
     * not-entrant. The method is invoked once to initialize its declaring class.
     *
     * @return the compile id of the not-entrant nmethod
     */
    public static int compileAndMakeNotEntrant(Method method) throws Exception {
        WhiteBox WB = WhiteBox.getWhiteBox();
        method.invoke(null);
        if (!WB.enqueueMethodForCompilation(method, 1 /* compLevel */)) {
            throw new AssertionError("Failed to enqueue target for compilation");
        }
        while (WB.isMethodQueuedForCompilation(method)) {
            Thread.sleep(50);
        }
        NMethod nmethod = NMethod.get(method, false);
        if (nmethod == null || nmethod.comp_level != 1) {
            throw new AssertionError("Target is not compiled at level 1");
        }

        int deoptimized = WB.deoptimizeMethod(method);
        if (deoptimized == 0) {
            throw new AssertionError("No target nmethod was made not-entrant");
        }
        return nmethod.compile_id;
    }

    /**
     * Checks whether Compiler.codelist contains the requested nmethod.
     *
     * Each line starts with these fields, followed by an address range:
     *
     *   compile_id comp_level state class.method(descriptor)
     *
     * The address range is not used. The command lists only nmethods that are
     * not unloading.
     */
    public static boolean codelistContains(int compileId, Method method,
                                           int expectedLevel, int expectedState) {
        OutputAnalyzer output = new JMXExecutor().execute("Compiler.codelist", true);
        MethodType methodType = MethodType.methodType(method.getReturnType(),
                                                      method.getParameterTypes());
        String methodName = method.getDeclaringClass().getName() + "." + method.getName()
                + methodType.descriptorString();
        boolean hasEntries = false;
        for (String line : output.asLines()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split(" ");
            if (parts.length < 4) {
                throw new AssertionError("Malformed Compiler.codelist entry: " + line);
            }

            int id;
            int level;
            int state;
            try {
                id = Integer.parseInt(parts[0]);
                level = Integer.parseInt(parts[1]);
                state = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                throw new AssertionError("Malformed Compiler.codelist entry: " + line, e);
            }
            hasEntries = true;

            if (id == compileId && parts[3].equals(methodName)) {
                if (level != expectedLevel || state != expectedState) {
                    throw new AssertionError("Unexpected target nmethod entry: " + line);
                }
                System.out.println("Found codelist entry: " + line);
                return true;
            }
        }
        if (!hasEntries) {
            throw new AssertionError("Compiler.codelist returned no entries");
        }
        return false;
    }
}
