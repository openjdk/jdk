/*
 * Copyright 2009 Google, Inc.  All Rights Reserved.
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
 * @bug      5093723
 * @summary  REGRESSION: ClassCastException in SingleIndexWriter
 * @library  ../lib/
 * @build    JavadocTester
 * @build    T5093723
 * @run main T5093723
 */

public class T5093723 extends JavadocTester {

    private static final String BUG_ID = "5093723";

    private static final String[] ARGS = new String[] {
        "-d", BUG_ID + ".out", "-source", "5", "-Xdoclint:none",
        SRC_DIR + "/DocumentedClass.java",
        SRC_DIR + "/UndocumentedClass.java"
    };

    public static void main(String... args) {
        T5093723 tester = new T5093723();
        if (tester.runJavadoc(ARGS) != 0)
          throw new AssertionError("non-zero return code from javadoc");
    }

    public String getBugId() {
        return BUG_ID;
    }

    public String getBugName() {
        return getClass().getName();
    }
}
