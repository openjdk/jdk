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

/*
 * @test
 * @bug 8050167
 * @summary Test that error is not occurred during printing problematic frame
 * @library /testlibrary
 * @build com.oracle.java.testlibrary.*
 * @run driver ProblematicFrameTest
 */

import com.oracle.java.testlibrary.*;

import sun.misc.Unsafe;
import com.oracle.java.testlibrary.Utils;

public class ProblematicFrameTest {
    private static class Crasher {
        public static void main(String[] args) {
            Utils.getUnsafe().getInt(0);
        }
    }

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xmx64m", "-XX:-TransmitErrorReport", "-XX:-CreateMinidumpOnCrash", Crasher.class.getName());
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotMatch("error occurred during error reporting \\(printing problematic frame\\)");
    }
}
