/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.    See the GNU General Public License
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
 * @bug 8140348
 * @summary safepoint=trace should have output from each log statement in the code
 * @library /testlibrary
 * @compile SafepointTestMain.java
 * @modules java.base/sun.misc
 *          java.management
 * @build SafepointTest
 * @run main SafepointTest
 */

import jdk.test.lib.*;

public class SafepointTest {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xlog:safepoint=trace", "SafepointTestMain");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Safepoint synchronization initiated. (");
        output.shouldContain(" thread(s) to block");
        output.shouldContain("Entering safepoint region: ");
        output.shouldContain("Leaving safepoint region");
        output.shouldContain("_at_poll_safepoint");
        output.shouldContain("... found polling page ");
        output.shouldHaveExitValue(0);
    }
}
