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

/*
 * @test
 * @bug 6797535
 * @summary Basic tests for methods in java.util.Objects
 * @author  Joseph D. Darcy
 */

import java.util.*;

public class BasicObjectsTest {
    public static void main(String... args) {
        int errors = 0;
        errors += testEquals();
        errors += testHashCode();
        errors += testToString();
        errors += testCompare();
        errors += testNonNull();
        if (errors > 0 )
            throw new RuntimeException();
    }

    private static int testEquals() {
        int errors = 0;
        Object[] values = {null, "42", 42};
        for(int i = 0; i < values.length; i++)
            for(int j = 0; j < values.length; j++) {
                boolean expected = (i == j);
                Object a = values[i];
                Object b = values[j];
                boolean result = Objects.equals(a, b);
                if (result != expected) {
                    errors++;
                    System.err.printf("When equating %s to %s, got %b instead of %b%n.",
                                      a, b, result, expected);
                }
            }
        return errors;
    }

    private static int testHashCode() {
        int errors = 0;
        errors += (Objects.hashCode(null) == 0 ) ? 0 : 1;
        String s = "42";
        errors += (Objects.hashCode(s) == s.hashCode() ) ? 0 : 1;
        return errors;
    }

    private static int testToString() {
        int errors = 0;
        errors += ("null".equals(Objects.toString(null)) ) ? 0 : 1;
        String s = "Some string";
        errors += (s.equals(Objects.toString(s)) ) ? 0 : 1;
        return errors;
    }

    private static int testCompare() {
        int errors = 0;
        String[] values = {"e. e. cummings", "zzz"};
        String[] VALUES = {"E. E. Cummings", "ZZZ"};
        errors += compareTest(null, null, 0);
        for(int i = 0; i < values.length; i++) {
            String a = values[i];
            errors += compareTest(a, a, 0);
            for(int j = 0; j < VALUES.length; j++) {
                int expected = Integer.compare(i, j);
                String b = VALUES[j];
                errors += compareTest(a, b, expected);
            }
        }
        return errors;
    }

    private static int compareTest(String a, String b, int expected) {
        int errors = 0;
        int result = Objects.compare(a, b, String.CASE_INSENSITIVE_ORDER);
        if (Integer.signum(result) != Integer.signum(expected)) {
            errors++;
            System.err.printf("When comparing %s to %s, got %d instead of %d%n.",
                              a, b, result, expected);
        }
        return errors;
    }

    private static int testNonNull() {
        int errors = 0;
        String s;

        // Test 1-arg variant
        try {
            s = Objects.nonNull("pants");
            if (s != "pants") {
                System.err.printf("1-arg non-null failed to return its arg");
                errors++;
            }
        } catch (NullPointerException e) {
            System.err.printf("1-arg nonNull threw unexpected NPE");
            errors++;
        }

        try {
            s = Objects.nonNull(null);
            System.err.printf("1-arg nonNull failed to throw NPE");
            errors++;
        } catch (NullPointerException e) {
            // Expected
        }

        // Test 2-arg variant
        try {
            s = Objects.nonNull("pants", "trousers");
            if (s != "pants") {
                System.err.printf("2-arg nonNull failed to return its arg");
                errors++;
            }
        } catch (NullPointerException e) {
            System.err.printf("2-arg nonNull threw unexpected NPE");
            errors++;
        }

        try {
            s = Objects.nonNull(null, "pantaloons");
            System.err.printf("2-arg nonNull failed to throw NPE");
            errors++;
        } catch (NullPointerException e) {
            if (e.getMessage() != "pantaloons") {
                System.err.printf("2-arg nonNull threw NPE w/ bad detail msg");
                errors++;
            }
        }
        return errors;
    }
}
