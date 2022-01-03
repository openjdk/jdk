/*
 * Copyright (c) 2021, Amazon.com Inc. or its affiliates. All rights reserved.
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

import java.util.Iterator;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.dcmd.PidJcmdExecutor;

/*
 * @test CodeHeapAnalyticsMethodNames
 * @summary Test Compiler.CodeHeap_Analytics output has qualified method names
 * in the 'METHOD NAMES' section.
 * @bug 8275729
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CodeHeapAnalyticsMethodNames
 */

public class CodeHeapAnalyticsMethodNames {

    public static void main(String args[]) throws Exception {
        PidJcmdExecutor executor = new PidJcmdExecutor();
        OutputAnalyzer out = executor.execute("Compiler.CodeHeap_Analytics");
        out.shouldHaveExitValue(0);
        Iterator<String> iter = out.asLines().listIterator();
        boolean methodNamesSectionFound = false;
        while (iter.hasNext()) {
            String line = iter.next();
            if (line.contains("M E T H O D   N A M E S")) {
                methodNamesSectionFound = true;
                break;
            }
        }
        boolean nMethodFound = false;
        while (iter.hasNext()) {
            String line = iter.next();
            if (line.startsWith("0x") && line.contains("nMethod")) {
                nMethodFound = true;
                if (line.contains("java.lang.invoke.MethodHandle")) {
                    return;
                }
            }
        }
        if (methodNamesSectionFound && nMethodFound) {
            throw new RuntimeException("No java.lang.invoke.MethodHandle found.");
        }
    }
}
