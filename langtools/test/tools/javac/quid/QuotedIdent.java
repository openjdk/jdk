/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6746458
 * @summary Verify correct lexing of quoted identifiers.
 * @author jrose
 * @ignore 6877225 test fails on Windows:
 *      QuotedIdent.java:81: error while writing QuotedIdent.*86: PATH\QuotedIdent$*86.class
 *      (The filename, directory name, or volume label syntax is incorrect)
 *
 * @library ..
 * @run main quid.QuotedIdent
 */

/*
 * Standalone testing:
 * <code>
 * $ cd $MY_REPO_DIR/langtools
 * $ (cd make; make)
 * $ ./dist/bootstrap/bin/javac -d dist test/tools/javac/quid/QuotedIdent.java
 * $ java -version  # should print 1.6 or later
 * $ java -cp dist quid.QuotedIdent
 * </code>
 */

package quid;

public class QuotedIdent {
    static void check(int testid, String have, String expect)
                throws RuntimeException {
        if ((have == null && have != expect) ||
                (have != null && !have.equals(expect))) {
            String msg =
                "TEST " + testid + ": HAVE \"" +
                have + "\" EXPECT \"" + expect + "\"";
            System.out.println("StringConversion: " + msg);
            throw new RuntimeException(msg);
        }
    }

    // negative tests:
    //static class #"" { } //BAD empty ident name
    //static class #"<foo>" { } //BAD bad char in ident name
    /*static class /*(//BAD ident name interrupted by newline) #"jump:
    " { } /* uncomment previous line to attempt class w/ bad name */

    static class #"int" extends Number {
        final int #"int";
        #"int"(int #"int") {
            this.#"int" = #"int";
        }
        static #"int" valueOf(int #"int") {
            return new #"int"(#"int");
        }
        public int intValue() { return #"int"; }
        public long longValue() { return #"int"; }
        public float floatValue() { return #"int"; }
        public double doubleValue() { return #"int"; }
        public String toString() { return String.valueOf(#"int"); }
    }

    class #"*86" {
        String #"555-1212"() { return "[*86.555-1212]"; }
    }
    static#"*86"#"MAKE-*86"() {   // note close spacing
        return new QuotedIdent().new#"*86"();
    }

    static String bar() { return "[bar]"; }

    public static void main(String[] args) throws Exception {
        String s;

        String #"sticky \' wicket" = "wicked ' stick";
        s = #"sticky ' wicket";
        check(11, s, "wicked \' stick");
        check(12, #"s", s);
        check(13, #"\163", s);

        s = #"QuotedIdent".bar();
        check(21, s, "[bar]");

        s = #"int".valueOf(123).toString();
        check(22, s, "123");

        s = #"MAKE-*86"().#"555-1212"();
        check(23, s, "[*86.555-1212]");

        class#"{{{inmost}}}" { }
        s = new#"{{{inmost}}}"().getClass().getName();
        if (!s.endsWith("{{{inmost}}}"))
            check(24, s, "should end with \"{{{inmost}}}\"");

        s = #"Yog-Shoggoth".#"(nameless ululation)";
        check(25, s, "Tekeli-li!");

        s = #"int".class.getName();
        check(31, s, QuotedIdent.class.getName()+"$int");

        Class x86 = Class.forName(QuotedIdent.class.getName()+"$*86");
        if (x86 != #"*86".class)
            check(32, "reflected "+x86, "static "+#"*86".class);

        s = (String) x86.getDeclaredMethod("555-1212").invoke(#"MAKE-*86"());
        check(31, s, "[*86.555-1212]");

        System.out.println("OK");
    }
}

interface #"Yog-Shoggoth" {
    final String #"(nameless ululation)" = "Tekeli-li!";
}
