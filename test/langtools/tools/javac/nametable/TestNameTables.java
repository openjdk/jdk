/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8315248
 * @summary AssertionError in Name.compareTo
 * @modules jdk.compiler/com.sun.tools.javac.util
 *
 * @run main TestNameTables
 */

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;

import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

/**
 * Tests that CharSequence.compareTo works on CharSequence objects that may
 * come from different name tables.
 *
 * While the java.util.Name class generally specifies that operations involving
 * multiple Name objects should use names from the same table, that restriction
 * is harder to specify when the names are widened CharSequence objects.
 *
 * The test can be extended if necessary to cover other methods on Name,
 * if the restrictions on Name are relaxed to permit more mix-and-match usages.
 */
public class TestNameTables {
    public static void main(String... args) {
        new TestNameTables().run();
    }

    public static final String USE_SHARED_TABLE = "useSharedTable";
    public static final String USE_UNSHARED_TABLE = "useUnsharedTable";
    public static final String USE_STRING_TABLE = "useStringTable";

    public final List<String> ALL_TABLES = List.of(USE_SHARED_TABLE, USE_UNSHARED_TABLE, USE_STRING_TABLE);

    private final PrintStream out = System.err;

    void run() {
        for (var s : ALL_TABLES) {
            test(createNameTable(s));
        }

        for (var s1 : ALL_TABLES) {
            for (var s2 : ALL_TABLES) {
                test(createNameTable(s1), createNameTable(s2));
            }
        }
    }

    Name.Table createNameTable(String option) {
        Context c = new Context();
        Options o = Options.instance(c);
        o.put(option, "true");
        Names n = new Names(c);
        return n.table;
    }

    /**
     * Tests operations using a single name table.
     *
     * @param table the name table
     */
    void test(Name.Table table) {
        test(table, table);
    }

    /**
     * Tests operations using distinct name tables, of either the same
     * or different impl types.
     *
     * @param table1 the first name table
     * @param table2 the second name table
     */
    void test(Name.Table table1, Name.Table table2) {
        if (table1 == table2) {
            out.println("Testing " + table1);
        } else {
            out.println("Testing " + table1 + " : " + table2);
        }

        // tests are primarily that there are no issues manipulating names from
        // distinct name tables
        testCharSequenceCompare(table1, table2);
    }

    void testCharSequenceCompare(Name.Table table1, Name.Table table2) {
        Name n1 = table1.fromString("abc");
        Name n2 = table2.fromString("abc");
        checkEquals(CharSequence.compare(n1, n2), 0, "CharSequence.compare");
    }

    void checkEquals(Object found, Object expect, String op) {
        if (!Objects.equals(found, expect)) {
            out.println("Failed: " + op);
            out.println("     found: " + found);
            out.println("    expect: " + expect);
        }
    }
}