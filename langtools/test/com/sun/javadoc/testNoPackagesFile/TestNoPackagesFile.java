/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug      4475679
 * @summary  Verify that packages.html is no longer generated since it is no
 *           longer used.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestNoPackagesFile
 * @run main TestNoPackagesFile
 */

public class TestNoPackagesFile extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4475679";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR,
        SRC_DIR + FS + "C.java"
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestNoPackagesFile tester = new TestNoPackagesFile();
        run(tester, ARGS, NO_TEST, NO_TEST);
        if ((new java.io.File(BUG_ID + FS + "packages.html")).exists()) {
            throw new Error("Test Fails: packages file should not be " +                "generated anymore.");
        } else {
            System.out.println("Test passes:  packages.html not found.");
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
