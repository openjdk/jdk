/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8167446
 * @summary Commandline options PermSize and MaxPermSize should be recognized but ignored.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver PermGenFlagsTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class PermGenFlagsTest {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:PermSize=22k",
                                                                  "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Ignoring option PermSize; support was removed in 8.0");
        output.shouldHaveExitValue(0);

        pb = ProcessTools.createJavaProcessBuilder("-XX:MaxPermSize=22k",
                                                   "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Ignoring option MaxPermSize; support was removed in 8.0");
        output.shouldHaveExitValue(0);
    }
}
