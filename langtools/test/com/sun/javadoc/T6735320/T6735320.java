/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6735320
 * @summary javadoc throws exception if serialField value is missing
 * @library  ../lib/
 * @build    JavadocTester T6735320
 * @run main T6735320
 */
public class T6735320 extends JavadocTester {

    private static final String BUG_ID = "6735320";
    private static final String[] ARGS = new String[]{
        "-d", BUG_ID + ".out",
        SRC_DIR + FS + "SerialFieldTest.java"
    };

    public String getBugId() {
        return BUG_ID;
    }

    public String getBugName() {
        return getClass().getName();
    }

    public static void main(String... args) {
        T6735320 tester = new T6735320();
        if (tester.runJavadoc(ARGS) != 0) {
            throw new AssertionError("non-zero return code from javadoc");
        }
        if (tester.getErrorOutput().contains("StringIndexOutOfBoundsException")) {
            throw new AssertionError("javadoc threw StringIndexOutOfBoundsException");
        }
    }
}
