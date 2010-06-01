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

/*
 * @test
 * @bug 4210388
 * @summary Javadoc declares interfaces to be "abstract".
 * @author jamieh
 * @library ../lib/
 * @build ModifierAbstract
 * @build JavadocTester
 * @build TestModifier
 * @run main TestModifier
 */

public class TestModifier extends JavadocTester {

    private static final String BUG_ID = "4210388";
    private static final String[][] TEST = NO_TEST;
    private static final String[][] NEGATED_TEST = NO_TEST;
    private static final String[] ARGS =
        new String[] {
            "-sourcepath", SRC_DIR,
            "-docletpath", SRC_DIR, "-doclet", "ModifierAbstract",
            SRC_DIR + FS + "Interface.java", SRC_DIR + FS + "Test.java"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestModifier tester = new TestModifier();
        if (run(tester, ARGS, TEST, NEGATED_TEST) != 0) {
            throw new Error("Javadoc error occured during execution.");
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getBugId() {
        return BUG_ID;
    }

    /**
     * {@inheritDoc}
     */
    public String getBugName() {
        return getClass().getName();
    }
}
