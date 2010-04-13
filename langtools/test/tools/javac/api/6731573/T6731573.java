/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * @test
 * @bug     6731573
 * @summary diagnostic output should optionally include source line
 * @author  Maurizio Cimadamore
 * @library ../lib
 */

import java.io.*;
import java.util.*;
import javax.tools.*;

public class T6731573 extends ToolTester {

    enum DiagnosticType {
        BASIC(null) {
            boolean shouldDisplaySource(SourceLine sourceLine) {
                return sourceLine != SourceLine.DISABLED;
            }
        },
        RAW("-XDrawDiagnostics") {
            boolean shouldDisplaySource(SourceLine sourceLine) {
                return sourceLine == SourceLine.ENABLED;
            }
        };

        String optValue;

        DiagnosticType(String optValue) {
            this.optValue = optValue;
        }

        abstract boolean shouldDisplaySource(SourceLine sourceLine);
    }

    enum SourceLine {
        STANDARD(null),
        ENABLED("-XDshowSource=true"),
        DISABLED("-XDshowSource=false");

        String optValue;

        SourceLine(String optValue) {
            this.optValue = optValue;
        }
    }

    void checkErrorLine(String output, boolean expected, List<String> options) {
        System.err.println("\noptions = "+options);
        System.err.println(output);
        boolean errLinePresent = output.contains("^");
        if (errLinePresent != expected) {
            throw new AssertionError("Error in diagnostic: error line" +
                    (expected ? "" : " not") + " expected but" +
                    (errLinePresent ? "" : " not") + " found");
        }
    }

    void exec(DiagnosticType diagType, SourceLine sourceLine) {
        final Iterable<? extends JavaFileObject> compilationUnits =
            fm.getJavaFileObjects(new File(test_src, "Erroneous.java"));
        StringWriter pw = new StringWriter();
        ArrayList<String> options = new ArrayList<String>();
        if (diagType.optValue != null)
            options.add(diagType.optValue);
        if (sourceLine.optValue != null)
            options.add(sourceLine.optValue);
        task = tool.getTask(pw, fm, null, options, null, compilationUnits);
        task.call();
        checkErrorLine(pw.toString(),
                diagType.shouldDisplaySource(sourceLine),
                options);
    }

    void test() {
        for (DiagnosticType dt : DiagnosticType.values()) {
            for (SourceLine sl : SourceLine.values()) {
                exec(dt, sl);
            }
        }
    }

    public static void main(String... args) throws Exception {
        new T6731573().test();
    }
}
