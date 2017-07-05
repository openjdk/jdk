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
 * @test ProtectionDomainVerificationTest
 * @bug 8149064
 * @library /testlibrary
 * @build jdk.test.lib.OutputAnalyzer jdk.test.lib.Platform jdk.test.lib.ProcessTools
 * @run driver ProtectionDomainVerificationTest
 */

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.test.lib.ProcessTools;

public class ProtectionDomainVerificationTest {

    public static void main(String... args) throws Exception {

        // -Xlog:protectiondomain=trace
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xlog:protectiondomain=trace",
                                                                  "-Xmx64m",
                                                                  Hello.class.getName());
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldContain("[protectiondomain] Checking package access");
        out.shouldContain("[protectiondomain] pd set count = #");

        // -Xlog:protectiondomain=debug
        pb = ProcessTools.createJavaProcessBuilder("-Xlog:protectiondomain=debug",
                                                                  "-Xmx64m",
                                                                  Hello.class.getName());
        out = new OutputAnalyzer(pb.start());
        out.shouldContain("[protectiondomain] Checking package access");
        out.shouldNotContain("pd set count = #");
    }

    public static class Hello {
        public static void main(String[] args) {
            System.out.print("Hello!");
        }
    }
}
