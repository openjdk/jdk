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
 * @bug 6746458
 * @summary Verify correct separate compilation of classes with extended identifiers.
 * @author jrose
 * @ignore 6877225 test fails on Windows:
 *      QuotedIdent.java:81: error while writing QuotedIdent.*86: PATH\QuotedIdent$*86.class
 *      (The filename, directory name, or volume label syntax is incorrect)
 *
 * @library ..
 * @run main quid.QuotedIdent2
 */
/*
 * Standalone testing:
 * <code>
 * $ cd $MY_REPO_DIR/langtools
 * $ (cd make; make)
 * $ ./dist/bootstrap/bin/javac -d dist test/tools/javac/quid/QuotedIdent.java
 * $ ./dist/bootstrap/bin/javac -d dist -cp dist test/tools/javac/quid/QuotedIdent2.java
 * $ java -version  # should print 1.6 or later
 * $ java -cp dist QuotedIdent2
 * </code>
 */

package quid;

import quid.QuotedIdent.*;
import quid.QuotedIdent.#"*86";
import static quid.QuotedIdent.#"MAKE-*86";

public class QuotedIdent2 {
    static void check(int testid, String have, String expect)
                throws RuntimeException {
        QuotedIdent.check(testid, have, expect);
    }

    public static void main(String[] args) throws Exception {
        String s;

        s = #"int".valueOf(123).toString();
        check(22, s, "123");

        s = #"MAKE-*86"().#"555-1212"();
        check(23, s, "[*86.555-1212]");

        s = #"Yog-Shoggoth".#"(nameless ululation)";
        check(25, s, "Tekeli-li!");

        s = QuotedIdent.#"int".class.getName();
        check(31, s, QuotedIdent.class.getName()+"$int");

        Class x86 = Class.forName(QuotedIdent.class.getName()+"$*86");
        if (x86 != #"*86".class)
            check(32, "reflected "+x86, "static "+#"*86".class);

        s = (String) x86.getDeclaredMethod("555-1212").invoke(QuotedIdent.#"MAKE-*86"());
        check(31, s, "[*86.555-1212]");

        System.out.println("OK");
    }
}
