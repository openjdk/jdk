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
 * @test TestOnOutOfMemoryError
 * @summary Test using -XX:OnOutOfMemoryError=<cmd>
 * @modules java.base/jdk.internal.misc
 * @library /testlibrary
 * @build TestOnOutOfMemoryError
 * @run main TestOnOutOfMemoryError
 * @bug 8078470
 */

import jdk.test.lib.*;

public class TestOnOutOfMemoryError {

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            // This should guarantee to throw:
            //  java.lang.OutOfMemoryError: Requested array size exceeds VM limit
            Object[] oa = new Object[Integer.MAX_VALUE];
            return;
        }

        // else this is the main test
        String msg = "Test Succeeded";
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
           "-XX:OnOutOfMemoryError=echo " + msg,
           TestOnOutOfMemoryError.class.getName(),
           "throwOOME");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        /* Actual output should look like this:
           #
           # java.lang.OutOfMemoryError: Requested array size exceeds VM limit
           # -XX:OnOutOfMemoryError="echo Test Succeeded"
           #   Executing /bin/sh -c "echo Test Succeeded"...
           Test Succeeded
           Exception in thread "main" java.lang.OutOfMemoryError: Requested array size exceeds VM limit
           at OOME.main(OOME.java:3)

           So we don't want to match on the "# Executing ..." line, and they
           both get written to stdout.
        */
        output.shouldContain("Requested array size exceeds VM limit");
        output.stdoutShouldMatch("^" + msg); // match start of line only
        System.out.println("PASSED");
    }
}
