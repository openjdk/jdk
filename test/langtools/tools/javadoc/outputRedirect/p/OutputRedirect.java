/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

package p;

import java.io.*;
import com.sun.tools.javadoc.Main;

public class OutputRedirect {

    public static void main(String[] args) {
        PrintStream originalOutput = System.out;
        try {
            doTest();
        } finally {
            // restore things
            System.setOut(originalOutput);
        }
    }

    static void doTest() {
        ByteArrayOutputStream redirectedOutput = new ByteArrayOutputStream();
        PrintStream originalOutput = System.out;

        // redirect System.out to a buffer
        System.setOut(new PrintStream(redirectedOutput));

        PrintWriter sink = new PrintWriter(new ByteArrayOutputStream());

        // execute javadoc
        int result = Main.execute("javadoc", sink, sink, sink,
                                  "com.sun.tools.doclets.standard.Standard",
                                  new String[] {"p"}
                                  );


        // test whether javadoc did any output to System.out
        if (redirectedOutput.toByteArray().length > 0) {
            originalOutput.println("Test failed; here's what javadoc wrote on its standard output:");
            originalOutput.println(redirectedOutput.toString());
            throw new Error("javadoc output wasn\'t properly redirected");
        } else if (result != 0) {
            throw new Error("javadoc run failed");
        } else {
            originalOutput.println("OK, good");
        }
    }
}
