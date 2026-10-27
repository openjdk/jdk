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

/*
 * @test
 * @summary Checks C1 flat array code with unloaded array klass because of OOME
 * @bug 8394660
 * @library /test/lib
 * @requires vm.compiler1.enabled
 * @requires vm.opt.AbortVMOnCompilationFailure != true
 * @run driver ${test.main.class}
 */

package compiler.c1;

import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestUnloadedArrayKlassAfterOOME_C1 {

    public static void main(String[] args) throws Exception {
        List<String> options = new ArrayList<String>();
        options.add("-XX:+PrintCompilation");
        options.add("-Xcomp");
        options.add("-XX:TieredStopAtLevel=1");
        options.add("-Xmx12m");
        options.add("-XX:CompileCommand=compileonly," + getTestClass() + "::testNew");
        options.add("-XX:CompileCommand=compileonly," + getTestClass() + "::testArrayCopy");
        options.add(getTestClass());

        OutputAnalyzer oa = ProcessTools.executeTestJava(options);

        oa.shouldHaveExitValue(0);
        // The OOM condition cannot be reliably reproduced with all GCs,
        // so no strong assertion, just some information printed in the log
        if (oa.contains("compilation bailout: encountered unloaded ciobjarrayklass due to out of memory error")) {
            IO.println("Compilation skipped because of OOM, good!");
        } else {
            IO.println("No compilation bailout detected, test unconclusive");
        }
    }

    // Test class that is invoked by the sub process
    public static String getTestClass() {
        return TestMain.class.getName();
    }

    public static class TestMain {
        static class Value {
            int value = 0;
        }

        static Object[] keepAlive;

        static Object[] testNew() {
            try {
                return new Value[10];
            } catch (OutOfMemoryError expected) {
                // Ignore
            }
            return null;
        }

        static void testArrayCopy(Object[] array) {
            try {
                System.arraycopy(array, 0, array, 1, array.length - 2);
            } catch (OutOfMemoryError expected) {
                // Ignore
            }
        }

        public static void main(String[] args) {
            Object[] array = new Object[10];
            Value value = new Value();
            try {
                // Fill up the heap
                while (true) {
                    keepAlive = new Object[] { keepAlive, new byte[1024] };
                }
            } catch (OutOfMemoryError expected) {
                // Ignore
            }
            testNew();
            testArrayCopy(array);
        }
    }
}
