/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @summary Check os::random randomness
 * @library /test/lib
 * @requires vm.flagless
 * @requires vm.debug
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestOsRandom
 */

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestOsRandom {

    private final static class Tester {
        public static void main(String[] args) {
            Object o = new Object();
            System.out.println("Hash:" + System.identityHashCode(o));
        }
    }

    public static void main(String[] args) throws Exception {

        // Call JVM twice and let it trace out os::random results. We do this via
        // identity hash, whose seed is initialized - with hashCode=5 - with os::random.
        // Values must not repeat.

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:+UnlockExperimentalVMOptions", "-XX:hashCode=5",
                "-Xmx100M", Tester.class.getName());

        OutputAnalyzer o = new OutputAnalyzer(pb.start());
        o.reportDiagnosticSummary();
        long l1 = Long.parseLong(o.firstMatch("Hash:(\\d+).*", 1));

        o = new OutputAnalyzer(pb.start());
        o.reportDiagnosticSummary();
        long l2 = Long.parseLong(o.firstMatch("Hash:(\\d+).*", 1));

        if (l1 == l2) {
            throw new RuntimeException("Random values match?");
        }

    }

}


