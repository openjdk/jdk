/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


/* @test
 * @bug 6813059
 * @summary
 */

import java.io.*;
import java.util.*;

// Simple test of -XDshouldStopPolicy.
// For each of the permissable values, we compile a file with an error in it,
// then using -XDverboseCompilePolicy we check that the compilation gets as
// far as expected, but no further.

public class Test {
    enum ShouldStopPolicy {
        BLANK(false, null, "attr"),
        PROCESS(true, null, "attr"),
        ATTR(true, "attr", "flow"),
        FLOW(true, "flow", "desugar"),
        TRANSTYPES(true, "desugar", "generate"),
        LOWER(true, "desugar", "generate"),
        GENERATE(true, "generate", null);
        ShouldStopPolicy(boolean needOption, String expect, String dontExpect) {
            this.needOption = needOption;
            this.expect = expect;
            this.dontExpect = dontExpect;
        }
        boolean needOption;
        String expect;
        String dontExpect;
    }

    enum CompilePolicy {
        BYFILE,
        BYTODO
    }

    public static void main(String... args) throws Exception {
        new Test().run();
    }

    public void run() throws Exception {
        for (CompilePolicy cp: CompilePolicy.values()) {
            for (ShouldStopPolicy ssp: ShouldStopPolicy.values()) {
                test(cp, ssp);
            }
        }

        if (errors > 0)
            throw new Exception(errors + " errors occurred");
    }

    public void test(CompilePolicy cp, ShouldStopPolicy ssp) {
        System.err.println();
        System.err.println("test " + cp + " " + ssp);
        List<String> args = new ArrayList<String>();
        args.add("-XDverboseCompilePolicy");
        args.add("-XDcompilePolicy=" + cp.toString().toLowerCase());
        args.add("-d");
        args.add(".");
        if (ssp.needOption)
            args.add("-XDshouldStopPolicy=" + ssp);
        args.add(new File(System.getProperty("test.src", "."), "A.java").getPath());

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        System.err.println("compile " + args);
        int rc = com.sun.tools.javac.Main.compile(args.toArray(new String[args.size()]), pw);
        if (rc == 0)
            throw new Error("compilation succeeded unexpectedly");
        //System.err.println(sw);

        // The following is a workaround for the current javac implementation,
        // that in bytodo mode, it will still attribute files after syntax errors.
        // Changing that behavior may surprise existing users, so for now, we
        // work around it.
        if (cp == CompilePolicy.BYTODO && ssp == ShouldStopPolicy.PROCESS)
            ssp = ShouldStopPolicy.ATTR;

        boolean foundExpected = (ssp.expect == null);
        String[] lines = sw.toString().split("\n");
        for (String line: lines) {
            if (ssp.expect != null && line.startsWith("[" + ssp.expect))
                foundExpected = true;
            if (ssp.dontExpect != null && line.startsWith("[" + ssp.dontExpect)) {
                error("Unexpected output: " + ssp.dontExpect + "\n" + sw);
                return;
            }
        }

        if (!foundExpected)
            error("Expected output not found: " + ssp.expect + "\n" + sw);
    }

    void error(String message) {
        System.err.println(message);
        errors++;
    }

    int errors;
}












// These tests test the ability of the compiler to continue in the face of
// errors, accordining to the shouldStopPolicy

/* @ test /nodynamiccopyright/
 * @bug 6813059
 * @summary
 * @compile/fail/ref=flow.out       -XDrawDiagnostics -XDcompilePolicy=byfile -XDverboseCompilePolicy -XDshouldStopPolicy=FLOW       Test.java

 * @compile/fail/ref=default.out    -XDrawDiagnostics -XDcompilePolicy=byfile -XDverboseCompilePolicy                                Test.java
 * @compile/fail/ref=enter.out      -XDrawDiagnostics -XDcompilePolicy=byfile -XDverboseCompilePolicy -XDshouldStopPolicy=ENTER      Test.java
 * @compile/fail/ref=attr.out       -XDrawDiagnostics -XDcompilePolicy=byfile -XDverboseCompilePolicy -XDshouldStopPolicy=ATTR       Test.java
 * @compile/fail/ref=transtypes.out -XDrawDiagnostics -XDcompilePolicy=byfile -XDverboseCompilePolicy -XDshouldStopPolicy=TRANSTYPES Test.java
 * @compile/fail/ref=lower.out      -XDrawDiagnostics -XDcompilePolicy=byfile -XDverboseCompilePolicy -XDshouldStopPolicy=LOWER      Test.java
 * @compile/fail/ref=generate.out   -XDrawDiagnostics -XDcompilePolicy=byfile -XDverboseCompilePolicy -XDshouldStopPolicy=GENERATE   Test.java
 */

/*
class Test {
    void m1() {
        System.err.println("hello");
        0 // syntax error
        System.err.println("world");
    }

    void m2() {
    }
}

class Test2 {
}
*/

