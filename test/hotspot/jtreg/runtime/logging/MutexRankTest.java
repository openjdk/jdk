/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @bug 8294759
 * @summary Verify mutex rank logging works
 * @requires vm.flagless
 * @library /test/lib
 * @run driver MutexRankTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Platform;

public class MutexRankTest {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:vmmutex",
                                                                             "-version");
        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        oa.shouldContain("VM Mutex/Monitor ranks:");
        if (Platform.isDebugBuild()) {
            oa.shouldContain("Rank \"safepoint\"");
            oa.shouldContain("Heap_lock");
        } else {
            oa.shouldContain("Only known in debug builds");
        }
        oa.shouldHaveExitValue(0);
    }
}
