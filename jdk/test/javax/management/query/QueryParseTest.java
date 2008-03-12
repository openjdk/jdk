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
 * @test QueryParseTest
 * @bug 6602310 6604768
 * @summary Test Query.fromString and Query.toString.
 * @author Eamonn McManus
 */

import java.util.Collections;
import java.util.Set;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;

public class QueryParseTest {
    // In this table, each string constant corresponds to a test case.
    // The objects following the string up to the next string are MBeans.
    // Each MBean must implement ExpectedValue to return true or false
    // according as it should return that value for the query parsed
    // from the given string.  The test will parse the string into a
    // a query and verify that the MBeans return the expected value
    // for that query.  Then it will convert the query back into a string
    // and into a second query, and check that the MBeans return the
    // expected value for that query too.  The reason we need to do all
    // this is that the spec talks about "equivalent queries", and gives
    // the implementation wide scope to rearrange queries.  So we cannot
    // just compare string values.
    //
    // We could also write an implementation-dependent test that knew what
    // the strings look like, and that would have to be changed if the
    // implementation changed.  But the approach here is cleaner.
    //
    // To simplify the creation of MBeans, most use the expectTrue or
    // expectFalse methods.  The parameters of these methods end up in
    // attributes called "A", "B", "C", etc.
    private static final Object[] queryTests = {
        // RELATIONS

        "A < B",
        expectTrue(1, 2), expectTrue(1.0, 2.0), expectTrue("one", "two"),
        expectTrue(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY),
        expectFalse(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY),
        expectFalse(1, 1), expectFalse(1.0, 1.0), expectFalse("one", "one"),
        expectFalse(2, 1), expectFalse(2.0, 1.0), expectFalse("two", "one"),
        expectFalse(Double.NaN, Double.NaN),

        "One = two",
        expectTrueOneTwo(1, 1), expectTrueOneTwo(1.0, 1.0),
        expectFalseOneTwo(1, 2), expectFalseOneTwo(2, 1),

        "A <= B",
        expectTrue(1, 1), expectTrue(1, 2), expectTrue("one", "one"),
        expectTrue("one", "two"),
        expectFalse(2, 1), expectFalse("two", "one"),
        expectFalse(Double.NaN, Double.NaN),

        "A >= B",
        expectTrue(1, 1), expectTrue(2, 1), expectTrue("two", "one"),
        expectFalse(1, 2), expectFalse("one", "two"),

        "A > B",
        expectTrue(2, 1), expectTrue("two", "one"),
        expectFalse(2, 2), expectFalse(1, 2), expectFalse(1.0, 2.0),
        expectFalse("one", "two"),

        "A <> B",
        expectTrue(1, 2), expectTrue("foo", "bar"),
        expectFalse(1, 1), expectFalse("foo", "foo"),

        "A != B",
        expectTrue(1, 2), expectTrue("foo", "bar"),
        expectFalse(1, 1), expectFalse("foo", "foo"),

        // PARENTHESES

        "(((A))) = (B)",
        expectTrue(1, 1), expectFalse(1, 2),

        "(A = B)",
        expectTrue(1, 1), expectFalse(1, 2),

        "(((A = (B))))",
        expectTrue(1, 1), expectFalse(1, 2),

        // INTEGER LITERALS

        "A = 1234567890123456789",
        expectTrue(1234567890123456789L), expectFalse(123456789L),

        "A = +1234567890123456789",
        expectTrue(1234567890123456789L), expectFalse(123456789L),

        "A = -1234567890123456789",
        expectTrue(-1234567890123456789L), expectFalse(-123456789L),


        "A = + 1234567890123456789",
        expectTrue(1234567890123456789L), expectFalse(123456789L),

        "A = - 1234567890123456789",
        expectTrue(-1234567890123456789L), expectFalse(-123456789L),

        "A = " + Long.MAX_VALUE,
        expectTrue(Long.MAX_VALUE), expectFalse(Long.MIN_VALUE),

        "A = " + Long.MIN_VALUE,
        expectTrue(Long.MIN_VALUE), expectFalse(Long.MAX_VALUE),

        // DOUBLE LITERALS

        "A = 0.0",
        expectTrue(0.0), expectFalse(1.0),

        "A = 0.0e23",
        expectTrue(0.0), expectFalse(1.0),

        "A = 1.2e3",
        expectTrue(1.2e3), expectFalse(1.2),

        "A = +1.2",
        expectTrue(1.2), expectFalse(-1.2),

        "A = 1.2e+3",
        expectTrue(1.2e3), expectFalse(1.2),

        "A = 1.2e-3",
        expectTrue(1.2e-3), expectFalse(1.2),

        "A = 1.2E3",
        expectTrue(1.2e3), expectFalse(1.2),

        "A = -1.2e3",
        expectTrue(-1.2e3), expectFalse(1.2),

        "A = " + Double.MAX_VALUE,
        expectTrue(Double.MAX_VALUE), expectFalse(Double.MIN_VALUE),

        "A = " + -Double.MAX_VALUE,
        expectTrue(-Double.MAX_VALUE), expectFalse(-Double.MIN_VALUE),

        "A = " + Double.MIN_VALUE,
        expectTrue(Double.MIN_VALUE), expectFalse(Double.MAX_VALUE),

        "A = " + -Double.MIN_VALUE,
        expectTrue(-Double.MIN_VALUE), expectFalse(-Double.MAX_VALUE),

        Query.toString(  // A = Infinity   ->   A = (1.0/0.0)
                Query.eq(Query.attr("A"), Query.value(Double.POSITIVE_INFINITY))),
        expectTrue(Double.POSITIVE_INFINITY),
        expectFalse(0.0), expectFalse(Double.NEGATIVE_INFINITY),

        Query.toString(  // A = -Infinity   ->   A = (-1.0/0.0)
                Query.eq(Query.attr("A"), Query.value(Double.NEGATIVE_INFINITY))),
        expectTrue(Double.NEGATIVE_INFINITY),
        expectFalse(0.0), expectFalse(Double.POSITIVE_INFINITY),

        Query.toString(  // A < NaN   ->   A < (0.0/0.0)
                Query.lt(Query.attr("A"), Query.value(Double.NaN))),
        expectFalse(0.0), expectFalse(Double.NEGATIVE_INFINITY),
        expectFalse(Double.POSITIVE_INFINITY), expectFalse(Double.NaN),

        Query.toString(  // A >= NaN   ->   A < (0.0/0.0)
                Query.geq(Query.attr("A"), Query.value(Double.NaN))),
        expectFalse(0.0), expectFalse(Double.NEGATIVE_INFINITY),
        expectFalse(Double.POSITIVE_INFINITY), expectFalse(Double.NaN),

        // STRING LITERALS

        "A = 'blim'",
        expectTrue("blim"), expectFalse("blam"),

        "A = 'can''t'",
        expectTrue("can't"), expectFalse("cant"), expectFalse("can''t"),

        "A = '''blim'''",
        expectTrue("'blim'"), expectFalse("'blam'"),

        "A = ''",
        expectTrue(""), expectFalse((Object) null),

        // BOOLEAN LITERALS

        "A = true",
        expectTrue(true), expectFalse(false), expectFalse((Object) null),

        "A = TRUE",
        expectTrue(true), expectFalse(false),

        "A = TrUe",
        expectTrue(true), expectFalse(false),

        "A = false",
        expectTrue(false), expectFalse(true),

        "A = fAlSe",
        expectTrue(false), expectFalse(true),

        "A = \"true\"",   // An attribute called "true"
        expectFalse(true), expectFalse(false), expectFalse("\"true\""),
        newTester(new String[] {"A", "true"}, new Object[] {2.2, 2.2}, true),
        newTester(new String[] {"A", "true"}, new Object[] {2.2, 2.3}, false),

        "A = \"False\"",
        expectFalse(true), expectFalse(false), expectFalse("\"False\""),
        newTester(new String[] {"A", "False"}, new Object[] {2.2, 2.2}, true),
        newTester(new String[] {"A", "False"}, new Object[] {2.2, 2.3}, false),

        // ARITHMETIC

        "A + B = 10",
        expectTrue(4, 6), expectFalse(3, 8),

        "A + B = 'blim'",
        expectTrue("bl", "im"), expectFalse("bl", "am"),

        "A - B = 10",
        expectTrue(16, 6), expectFalse(16, 3),

        "A * B = 10",
        expectTrue(2, 5), expectFalse(3, 3),

        "A / B = 10",
        expectTrue(70, 7), expectTrue(70.0, 7), expectFalse(70.01, 7),

        "A + B + C = 10",
        expectTrue(2, 3, 5), expectFalse(2, 4, 8),

        "A+B+C=10",
        expectTrue(2, 3, 5), expectFalse(2, 4, 8),

        "A + B + C + D = 10",
        expectTrue(1, 2, 3, 4), expectFalse(2, 3, 4, 5),

        "A + (B + C) = 10",
        expectTrue(2, 3, 5), expectFalse(2, 4, 8),

        // It is not correct to rearrange A + (B + C) as A + B + C
        // (which means (A + B) + C), because of overflow.
        // In particular Query.toString must not do this.
        "A + (B + C) = " + Double.MAX_VALUE,  // ensure no false associativity
        expectTrue(Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE),
        expectFalse(-Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE),

        "A * (B * C) < " + Double.MAX_VALUE,  // same test for multiplication
        expectTrue(Double.MAX_VALUE, Double.MAX_VALUE, Double.MIN_VALUE),
        expectFalse(Double.MIN_VALUE, Double.MAX_VALUE, Double.MAX_VALUE),

        "A * B + C = 10",
        expectTrue(3, 3, 1), expectTrue(2, 4, 2), expectFalse(1, 2, 3),

        "A*B+C=10",
        expectTrue(3, 3, 1), expectTrue(2, 4, 2), expectFalse(1, 2, 3),

        "(A * B) + C = 10",
        expectTrue(3, 3, 1), expectTrue(2, 4, 2), expectFalse(1, 2, 3),

        "A + B * C = 10",
        expectTrue(1, 3, 3), expectTrue(2, 2, 4), expectFalse(1, 2, 3),

        "A - B * C = 10",
        expectTrue(16, 2, 3), expectFalse(15, 2, 2),

        "A + B / C = 10",
        expectTrue(5, 15, 3), expectFalse(5, 16, 4),

        "A - B / C = 10",
        expectTrue(16, 12, 2), expectFalse(15, 10, 3),

        "A * (B + C) = 10",
        expectTrue(2, 2, 3), expectFalse(1, 2, 3),

        "A / (B + C) = 10",
        expectTrue(70, 4, 3), expectFalse(70, 3, 5),

        "A * (B - C) = 10",
        expectTrue(2, 8, 3), expectFalse(2, 3, 8),

        "A / (B - C) = 10",
        expectTrue(70, 11, 4), expectFalse(70, 4, 11),

        "A / B / C = 10",
        expectTrue(140, 2, 7), expectFalse(100, 5, 5),

        "A / (B / C) = 10",
        expectTrue(70, 14, 2), expectFalse(70, 10, 7),

        // LOGIC

        "A = B or C = D",
        expectTrue(1, 1, 2, 3), expectTrue(1, 2, 3, 3), expectTrue(1, 1, 2, 2),
        expectFalse(1, 2, 3, 4), expectFalse("!", "!!", "?", "??"),

        "A = B and C = D",
        expectTrue(1, 1, 2, 2),
        expectFalse(1, 1, 2, 3), expectFalse(1, 2, 3, 3),

        "A = 1 and B = 2 and C = 3",
        expectTrue(1, 2, 3), expectFalse(1, 2, 4),

        "A = 1 or B = 2 or C = 3",
        expectTrue(1, 2, 3), expectTrue(1, 0, 0), expectTrue(0, 0, 3),
        expectFalse(2, 3, 4),

        // grouped as (a and b) or (c and d)
        "A = 1 AND B = 2 OR C = 3 AND D = 4",
        expectTrue(1, 2, 3, 4), expectTrue(1, 2, 1, 2), expectTrue(3, 4, 3, 4),
        expectFalse(3, 4, 1, 2), expectFalse(1, 1, 1, 1),

        "(A = 1 AND B = 2) OR (C = 3 AND D = 4)",
        expectTrue(1, 2, 3, 4), expectTrue(1, 2, 1, 2), expectTrue(3, 4, 3, 4),
        expectFalse(3, 4, 1, 2), expectFalse(1, 1, 1, 1),

        "(A = 1 or B = 2) AND (C = 3 or C = 4)",
        expectTrue(1, 1, 3, 3), expectTrue(2, 2, 4, 4), expectTrue(1, 2, 3, 4),
        expectFalse(1, 2, 1, 2), expectFalse(3, 4, 3, 4),

        // LIKE

        "A like 'b%m'",
        expectTrue("blim"), expectTrue("bm"),
        expectFalse(""), expectFalse("blimmo"), expectFalse("mmm"),

        "A not like 'b%m'",
        expectFalse("blim"), expectFalse("bm"),
        expectTrue(""), expectTrue("blimmo"), expectTrue("mmm"),

        "A like 'b_m'",
        expectTrue("bim"), expectFalse("blim"),

        "A like '%can''t%'",
        expectTrue("can't"),
        expectTrue("I'm sorry Dave, I'm afraid I can't do that"),
        expectFalse("cant"), expectFalse("can''t"),

        "A like '\\%%\\%'",
        expectTrue("%blim%"), expectTrue("%%"),
        expectFalse("blim"), expectFalse("%asdf"), expectFalse("asdf%"),

        "A LIKE '*%?_'",
        expectTrue("*blim?!"), expectTrue("*?_"),
        expectFalse("blim"), expectFalse("blim?"),
        expectFalse("?*"), expectFalse("??"), expectFalse(""), expectFalse("?"),

        Query.toString(
                Query.initialSubString(Query.attr("A"), Query.value("*?%_"))),
        expectTrue("*?%_tiddly"), expectTrue("*?%_"),
        expectFalse("?%_tiddly"), expectFalse("*!%_"), expectFalse("*??_"),
        expectFalse("*?%!"), expectFalse("*?%!tiddly"),

        Query.toString(
                Query.finalSubString(Query.attr("A"), Query.value("*?%_"))),
        expectTrue("tiddly*?%_"), expectTrue("*?%_"),
        expectFalse("tiddly?%_"), expectFalse("*!%_"), expectFalse("*??_"),
        expectFalse("*?%!"), expectFalse("tiddly*?%!"),

        // BETWEEN

        "A between B and C",
        expectTrue(1, 1, 2), expectTrue(2, 1, 2), expectTrue(2, 1, 3),
        expectFalse(3, 1, 2), expectFalse(0, 1, 2), expectFalse(2, 3, 1),
        expectTrue(1.0, 0.0, 2.0), expectFalse(2.0, 0.0, 1.0),
        expectTrue(0.0, 0.0, 0.0), expectTrue(1.0, 0.0, 1.0),
        expectTrue(1.0, 0.0, Double.POSITIVE_INFINITY),
        expectFalse(1.0, Double.NEGATIVE_INFINITY, 0.0),
        expectFalse(false, false, true), expectFalse(true, false, true),
        expectTrue("jim", "fred", "sheila"), expectFalse("fred", "jim", "sheila"),

        "A between B and C and 1+2=3",
        expectTrue(2, 1, 3), expectFalse(2, 3, 1),

        "A not between B and C",
        expectTrue(1, 2, 3), expectFalse(2, 1, 3),

        // IN

        "A in (1, 2, 3)",
        expectTrue(1), expectTrue(2), expectTrue(3),
        expectFalse(0), expectFalse(4),

        "A in (1)",
        expectTrue(1), expectFalse(0),

        "A in (1.2, 3.4)",
        expectTrue(1.2), expectTrue(3.4), expectFalse(0.0),

        "A in ('foo', 'bar')",
        expectTrue("foo"), expectTrue("bar"), expectFalse("baz"),

        "A in ('foo', 'bar') and 'bl'+'im'='blim'",
        expectTrue("foo"), expectTrue("bar"), expectFalse("baz"),

        "A in (B, C, D)",  // requires fix for CR 6604768
        expectTrue(1, 1, 2, 3), expectFalse(1, 2, 3, 4),

        "A not in (B, C, D)",
        expectTrue(1, 2, 3, 4), expectFalse(1, 1, 2, 3),

        // QUOTING

        "\"LIKE\" = 1 and \"NOT\" = 2 and \"INSTANCEOF\" = 3 and " +
                "\"TRUE\" = 4 and \"FALSE\" = 5",
        newTester(
                new String[] {"LIKE", "NOT", "INSTANCEOF", "TRUE", "FALSE"},
                new Object[] {1, 2, 3, 4, 5},
                true),
        newTester(
                new String[] {"LIKE", "NOT", "INSTANCEOF", "TRUE", "FALSE"},
                new Object[] {5, 4, 3, 2, 1},
                false),

        "\"\"\"woo\"\"\" = 5",
        newTester(new String[] {"\"woo\""}, new Object[] {5}, true),
        newTester(new String[] {"\"woo\""}, new Object[] {4}, false),
        expectFalse(),

        // INSTANCEOF

        "instanceof '" + Tester.class.getName() + "'",
        expectTrue(),

        "instanceof '" + String.class.getName() + "'",
        expectFalse(),

        // LIKE OBJECTNAME

        // The test MBean is registered as a:b=c
        "like 'a:b=c'", expectTrue(),
        "like 'a:*'", expectTrue(),
        "like '*:b=c'", expectTrue(),
        "like 'a:b=*'", expectTrue(),
        "like 'a:b=?'", expectTrue(),
        "like 'd:b=c'", expectFalse(),
        "like 'a:b=??*'", expectFalse(),
        "like 'a:b=\"can''t\"'", expectFalse(),

        // QUALIFIED ATTRIBUTE

        Tester.class.getName() + "#A = 5",
        expectTrue(5), expectFalse(4),

        Tester.class.getName() + " # A = 5",
        expectTrue(5), expectFalse(4),

        Tester.class.getSuperclass().getName() + "#A = 5",
        expectFalse(5),

        DynamicMBean.class.getName() + "#A = 5",
        expectFalse(5),

        Tester.class.getName() + "#A = 5",
        new Tester(new String[] {"A"}, new Object[] {5}, false) {},
        // note the little {} at the end which means this is a subclass
        // and therefore QualifiedAttributeValue should return false.

        MBeanServerDelegate.class.getName() + "#SpecificationName LIKE '%'",
        new Wrapped(new MBeanServerDelegate(), true),
        new Tester(new String[] {"SpecificationName"}, new Object[] {"JMX"}, false),

        // DOTTED ATTRIBUTE

        "A.canonicalName = '" +
                MBeanServerDelegate.DELEGATE_NAME.getCanonicalName() + "'",
        expectTrue(MBeanServerDelegate.DELEGATE_NAME),
        expectFalse(ObjectName.WILDCARD),

        "A.class.name = 'java.lang.String'",
        expectTrue("blim"), expectFalse(95), expectFalse((Object) null),

        "A.canonicalName like 'JMImpl%:%'",
        expectTrue(MBeanServerDelegate.DELEGATE_NAME),
        expectFalse(ObjectName.WILDCARD),

        "A.true = 'blim'",
        new Tester(new String[] {"A.true"}, new Object[] {"blim"}, true),
        new Tester(new String[] {"A.true"}, new Object[] {"blam"}, false),

        "\"A.true\" = 'blim'",
        new Tester(new String[] {"A.true"}, new Object[] {"blim"}, true),
        new Tester(new String[] {"A.true"}, new Object[] {"blam"}, false),

        MBeanServerDelegate.class.getName() +
                "#SpecificationName.class.name = 'java.lang.String'",
        new Wrapped(new MBeanServerDelegate(), true),
        new Tester(new String[] {"SpecificationName"}, new Object[] {"JMX"}, false),

        MBeanServerDelegate.class.getName() +
                " # SpecificationName.class.name = 'java.lang.String'",
        new Wrapped(new MBeanServerDelegate(), true),
        new Tester(new String[] {"SpecificationName"}, new Object[] {"JMX"}, false),

        // CLASS

        "class = '" + Tester.class.getName() + "'",
        expectTrue(),
        new Wrapped(new MBeanServerDelegate(), false),

        "Class = '" + Tester.class.getName() + "'",
        expectTrue(),
        new Wrapped(new MBeanServerDelegate(), false),
    };

    private static final String[] incorrectQueries = {
        "", " ", "25", "()", "(a = b", "a = b)", "a.3 = 5",
        "a = " + Long.MAX_VALUE + "0",
        "a = " + Double.MAX_VALUE + "0",
        "a = " + Double.MIN_VALUE + "0",
        "a = 12a5", "a = 12e5e5", "a = 12.23.34",
        "a = 'can't'", "a = 'unterminated", "a = 'asdf''",
        "a = \"oops", "a = \"oops\"\"",
        "a like 5", "true or false",
        "a ! b", "? = 3", "a = @", "a##b",
        "a between b , c", "a between and c",
        "a in b, c", "a in 23", "a in (2, 3", "a in (2, 3x)",
        "a like \"foo\"", "a like b", "a like 23",
        "like \"foo\"", "like b", "like 23", "like 'a:b'",
        "5 like 'a'", "'a' like '%'",
        "a not= b", "a not = b", "a not b", "a not b c",
        "a = +b", "a = +'b'", "a = +true", "a = -b", "a = -'b'",
        "a#5 = b", "a#'b' = c",
        "a instanceof b", "a instanceof 17", "a instanceof",
        "a like 'oops\\'", "a like '[oops'",

        // Check that -Long.MIN_VALUE is an illegal constant.  This is one more
        // than Long.MAX_VALUE and, like the Java language, we only allow it
        // if it is the operand of unary minus.
        "a = " + Long.toString(Long.MIN_VALUE).substring(1),
    };

    public static void main(String[] args) throws Exception {
        int nexti;
        String failed = null;

        System.out.println("TESTING CORRECT QUERY STRINGS");
        for (int i = 0; i < queryTests.length; i = nexti) {
            for (nexti = i + 1; nexti < queryTests.length; nexti++) {
                if (queryTests[nexti] instanceof String)
                    break;
            }
            if (!(queryTests[i] instanceof String))
                throw new Exception("Test bug: should be string: " + queryTests[i]);

            String qs = (String) queryTests[i];
            System.out.println("Test: " + qs);

            QueryExp qe = Query.fromString(qs);
            String qes = Query.toString(qe);
            System.out.println("...parses to: " + qes);
            final QueryExp[] queries;
            if (qes.equals(qs))
                queries = new QueryExp[] {qe};
            else {
                QueryExp qe2 = Query.fromString(qes);
                String qes2 = Query.toString(qe2);
                System.out.println("...which parses to: " + qes2);
                if (qes.equals(qes2))
                    queries = new QueryExp[] {qe};
                else
                    queries = new QueryExp[] {qe, qe2};
            }

            for (int j = i + 1; j < nexti; j++) {
                Object mbean;
                if (queryTests[j] instanceof Wrapped)
                    mbean = ((Wrapped) queryTests[j]).mbean();
                else
                    mbean = queryTests[j];
                boolean expect = ((ExpectedValue) queryTests[j]).expectedValue();
                for (QueryExp qet : queries) {
                    boolean actual = runQuery(qet, mbean);
                    boolean ok = (expect == actual);
                    System.out.println(
                            "..." + mbean + " -> " + actual +
                            (ok ? " (OK)" : " ####INCORRECT####"));
                    if (!ok)
                        failed = qs;
                }
            }
        }

        System.out.println();
        System.out.println("TESTING INCORRECT QUERY STRINGS");
        for (String s : incorrectQueries) {
            try {
                QueryExp qe = Query.fromString(s);
                System.out.println("###DID NOT GET ERROR:### \"" + s + "\"");
                failed = s;
            } catch (IllegalArgumentException e) {
                String es = (e.getClass() == IllegalArgumentException.class) ?
                    e.getMessage() : e.toString();
                System.out.println("OK: exception for \"" + s + "\": " + es);
            }
        }

        if (failed == null)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: Last failure: " + failed);
    }

    private static boolean runQuery(QueryExp qe, Object mbean)
    throws Exception {
        MBeanServer mbs = MBeanServerFactory.newMBeanServer();
        ObjectName name = new ObjectName("a:b=c");
        mbs.registerMBean(mbean, name);
        Set<ObjectName> names = mbs.queryNames(new ObjectName("a:*"), qe);
        if (names.isEmpty())
            return false;
        if (names.equals(Collections.singleton(name)))
            return true;
        throw new Exception("Unexpected query result set: " + names);
    }

    private static interface ExpectedValue {
        public boolean expectedValue();
    }

    private static class Wrapped implements ExpectedValue {
        private final Object mbean;
        private final boolean expect;

        Wrapped(Object mbean, boolean expect) {
            this.mbean = mbean;
            this.expect = expect;
        }

        Object mbean() {
            return mbean;
        }

        public boolean expectedValue() {
            return expect;
        }
    }

    private static class Tester implements DynamicMBean, ExpectedValue {
        private final AttributeList attributes;
        private final boolean expectedValue;

        Tester(AttributeList attributes, boolean expectedValue) {
            this.attributes = attributes;
            this.expectedValue = expectedValue;
        }

        Tester(String[] names, Object[] values, boolean expectedValue) {
            this(makeAttributeList(names, values), expectedValue);
        }

        private static AttributeList makeAttributeList(
                String[] names, Object[] values) {
            if (names.length != values.length)
                throw new Error("Test bug: names and values different length");
            AttributeList list = new AttributeList();
            for (int i = 0; i < names.length; i++)
                list.add(new Attribute(names[i], values[i]));
            return list;
        }

        public Object getAttribute(String attribute)
        throws AttributeNotFoundException {
            for (Attribute a : attributes.asList()) {
                if (a.getName().equals(attribute))
                    return a.getValue();
            }
            throw new AttributeNotFoundException(attribute);
        }

        public void setAttribute(Attribute attribute) {
            throw new UnsupportedOperationException();
        }

        public AttributeList getAttributes(String[] attributes) {
            AttributeList list = new AttributeList();
            for (String attribute : attributes) {
                try {
                    list.add(new Attribute(attribute, getAttribute(attribute)));
                } catch (AttributeNotFoundException e) {
                    // OK: ignore, per semantics of getAttributes
                }
            }
            return list;
        }

        public AttributeList setAttributes(AttributeList attributes) {
            throw new UnsupportedOperationException();
        }

        public Object invoke(String actionName, Object[] params, String[] signature) {
            throw new UnsupportedOperationException();
        }

        public MBeanInfo getMBeanInfo() {
            MBeanAttributeInfo mbais[] = new MBeanAttributeInfo[attributes.size()];
            for (int i = 0; i < mbais.length; i++) {
                Attribute attr = attributes.asList().get(i);
                String name = attr.getName();
                Object value = attr.getValue();
                String type =
                        ((value == null) ? new Object() : value).getClass().getName();
                mbais[i] = new MBeanAttributeInfo(
                        name, type, name, true, false, false);
            }
            return new MBeanInfo(
                    getClass().getName(), "descr", mbais, null, null, null);
        }

        public boolean expectedValue() {
            return expectedValue;
        }

        @Override
        public String toString() {
            return attributes.toString();
        }
    }

    // Method rather than field, to avoid circular init dependencies
    private static String[] abcd() {
        return new String[] {"A", "B", "C", "D"};
    }

    private static String[] onetwo() {
        return new String[] {"One", "two"};
    }

    private static Object expectTrue(Object... attrs) {
        return newTester(abcd(), attrs, true);
    }

    private static Object expectFalse(Object... attrs) {
        return newTester(abcd(), attrs, false);
    }

    private static Object expectTrueOneTwo(Object... attrs) {
        return newTester(onetwo(), attrs, true);
    }

    private static Object expectFalseOneTwo(Object... attrs) {
        return newTester(onetwo(), attrs, false);
    }

    private static Object newTester(String[] names, Object[] attrs, boolean expect) {
        AttributeList list = new AttributeList();
        for (int i = 0; i < attrs.length; i++)
            list.add(new Attribute(names[i], attrs[i]));
        return new Tester(list, expect);
    }
}
