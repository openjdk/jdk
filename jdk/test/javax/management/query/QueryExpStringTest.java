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
 * @bug 4886011
 * @summary Test that QueryExp.toString() is reversible
 * @author Eamonn McManus
 * @run clean QueryExpStringTest
 * @run build QueryExpStringTest
 * @run main QueryExpStringTest
 */

import java.util.*;
import javax.management.*;

public class QueryExpStringTest {

    private static final ValueExp
        attr = Query.attr("attr"),
        qattr = Query.attr("className", "attr"),
        classattr = Query.classattr(),
        simpleString = Query.value("simpleString"),
        complexString = Query.value("a'b\\'\""),
        intValue = Query.value(12345678),
        integerValue = Query.value(new Integer(12345678)),
        longValue = Query.value(12345678L),
        floatValue = Query.value(2.5f),
        doubleValue = Query.value(2.5d),
        booleanValue = Query.value(true),
        plusValue = Query.plus(intValue, integerValue),
        timesValue = Query.times(doubleValue, floatValue),
        minusValue = Query.minus(floatValue, doubleValue),
        divValue = Query.div(doubleValue, floatValue);

    private static final QueryExp
        gt = Query.gt(intValue, floatValue),
        geq = Query.geq(intValue, floatValue),
        leq = Query.leq(intValue, floatValue),
        lt = Query.lt(intValue, floatValue),
        eq = Query.eq(intValue, floatValue),
        between = Query.between(intValue, floatValue, doubleValue),
        match = Query.match((AttributeValueExp) attr,
                            (StringValueExp) simpleString),
        initial = Query.initialSubString((AttributeValueExp) attr,
                                         (StringValueExp) simpleString),
        initialStar = Query.initialSubString((AttributeValueExp) attr,
                                             Query.value("*")),
        any = Query.anySubString((AttributeValueExp) attr,
                                 (StringValueExp) simpleString),
        anyStar = Query.anySubString((AttributeValueExp) attr,
                                     Query.value("*")),
        ffinal = Query.finalSubString((AttributeValueExp) attr,
                                      (StringValueExp) simpleString),
        finalMagic = Query.finalSubString((AttributeValueExp) attr,
                                          Query.value("?*[\\")),
        in = Query.in(intValue, new ValueExp[] {intValue, floatValue}),
        and = Query.and(gt, lt),
        or = Query.or(gt, lt),
        not = Query.not(gt);

    // Commented-out tests below require change to implementation

    private static final Object tests[] = {
        attr, "attr",
        qattr, "className.attr",
        classattr, "Class",
        simpleString, "'simpleString'",
//      complexString, "'a\\'b\\\\\\'\"'",
        intValue, "12345678",
        integerValue, "12345678",
        longValue, "12345678",
        floatValue, "2.5",
        doubleValue, "2.5",
        booleanValue, "true",
        plusValue, "12345678 + 12345678",
        timesValue, "2.5 * 2.5",
        minusValue, "2.5 - 2.5",
        divValue, "2.5 / 2.5",
        gt, "(12345678) > (2.5)",
        geq, "(12345678) >= (2.5)",
        leq, "(12345678) <= (2.5)",
        lt, "(12345678) < (2.5)",
        eq, "(12345678) = (2.5)",
        between, "(12345678) between (2.5) and (2.5)",
        match, "attr like 'simpleString'",
//      initial, "attr like 'simpleString*'",
//      initialStar, "attr like '\\\\**'",
//      any, "attr like '*simpleString*'",
//      anyStar, "attr like '*\\\\**'",
//      ffinal, "attr like '*simpleString'",
//      finalMagic, "attr like '*\\\\?\\\\*\\\\[\\\\\\\\'",
        in, "12345678 in (12345678, 2.5)",
        and, "((12345678) > (2.5)) and ((12345678) < (2.5))",
        or, "((12345678) > (2.5)) or ((12345678) < (2.5))",
        not, "not ((12345678) > (2.5))",
    };

    public static void main(String[] args) throws Exception {
        System.out.println("Testing QueryExp.toString()");

        boolean ok = true;

        for (int i = 0; i < tests.length; i += 2) {
            String testString = tests[i].toString();
            String expected = (String) tests[i + 1];
            if (expected.equals(testString))
                System.out.println("OK: " + expected);
            else {
                System.err.println("Expected: {" + expected + "}; got: {" +
                                   testString + "}");
                ok = false;
            }

            try {
                Object parsed;
                String[] expectedref = new String[] {expected};
                if (tests[i] instanceof ValueExp)
                    parsed = parseExp(expectedref);
                else
                    parsed = parseQuery(expectedref);
                if (expectedref[0].length() > 0)
                    throw new Exception("Junk after parse: " + expectedref[0]);
                String parsedString = parsed.toString();
                if (parsedString.equals(expected))
                    System.out.println("OK: parsed " + parsedString);
                else {
                    System.err.println("Parse differs: expected: {" +
                                       expected + "}; got: {" +
                                       parsedString + "}");
                    ok = false;
                }
            } catch (Exception e) {
                System.err.println("Parse got exception: {" + expected +
                                   "}: " + e);
                ok = false;
            }
        }

        if (ok)
            System.out.println("Test passed");
        else {
            System.out.println("TEST FAILED");
            System.exit(1);
        }
    }

    private static QueryExp parseQuery(String[] ss) throws Exception {
        if (skip(ss, "("))
            return parseQueryAfterParen(ss);

        if (skip(ss, "not (")) {
            QueryExp not = parseQuery(ss);
            if (!skip(ss, ")"))
                throw new Exception("Expected ) after not (...");
            return Query.not(not);
        }

        ValueExp exp = parseExp(ss);

        if (skip(ss, " like ")) {
            ValueExp pat = parseExp(ss);
            if (!(exp instanceof AttributeValueExp &&
                  pat instanceof StringValueExp)) {
                throw new Exception("Expected types `attr like string': " +
                                    exp + " like " + pat);
            }
            return Query.match((AttributeValueExp) exp, (StringValueExp) pat);
        }

        if (skip(ss, " in (")) {
            List values = new ArrayList();
            if (!skip(ss, ")")) {
                do {
                    values.add(parseExp(ss));
                } while (skip(ss, ", "));
                if (!skip(ss, ")"))
                    throw new Exception("Expected ) after in (...");
            }
            return Query.in(exp, (ValueExp[]) values.toArray(new ValueExp[0]));
        }

        throw new Exception("Expected in or like after expression");
    }

    private static QueryExp parseQueryAfterParen(String[] ss)
            throws Exception {
        /* This is very ugly.  We might have "(q1) and (q2)" here, or
           we might have "(e1) < (e2)".  Since the syntax for a query
           (q1) is not the same as for an expression (e1), but can
           begin with one, we try to parse the query, and if we get an
           exception we then try to parse an expression.  It's a hacky
           kind of look-ahead.  */
        String start = ss[0];
        try {
            QueryExp lhs = parseQuery(ss);
            QueryExp result;

            if (skip(ss, ") and ("))
                result = Query.and(lhs, parseQuery(ss));
            else if (skip(ss, ") or ("))
                result = Query.or(lhs, parseQuery(ss));
            else
                throw new Exception("Expected `) and/or ('");
            if (!skip(ss, ")"))
                throw new Exception("Expected `)' after subquery");
            return result;
        } catch (Exception e) {
            ss[0] = start;
            ValueExp lhs = parseExp(ss);
            if (!skip(ss, ") "))
                throw new Exception("Expected `) ' after subexpression");
            String op = scanWord(ss);
            if (!skip(ss, " ("))
                throw new Exception("Expected ` (' after `" + op + "'");
            ValueExp rhs = parseExp(ss);
            if (!skip(ss, ")"))
                throw new Exception("Expected `)' after subexpression");
            if (op.equals("="))
                return Query.eq(lhs, rhs);
            if (op.equals("<"))
                return Query.lt(lhs, rhs);
            if (op.equals(">"))
                return Query.gt(lhs, rhs);
            if (op.equals("<="))
                return Query.leq(lhs, rhs);
            if (op.equals(">="))
                return Query.geq(lhs, rhs);
            if (!op.equals("between"))
                throw new Exception("Unknown operator `" + op + "'");
            if (!skip(ss, " and ("))
                throw new Exception("Expected ` and (' after between");
            ValueExp high = parseExp(ss);
            if (!skip(ss, ")"))
                throw new Exception("Expected `)' after subexpression");
            return Query.between(lhs, rhs, high);
        }
    }

    private static ValueExp parseExp(String[] ss) throws Exception {
        final ValueExp prim = parsePrimary(ss);

        /* Look ahead to see if we have an arithmetic operator. */
        String back = ss[0];
        if (!skip(ss, " "))
            return prim;
        if (ss[0].equals("") || "+-*/".indexOf(ss[0].charAt(0)) < 0) {
            ss[0] = back;
            return prim;
        }

        final String op = scanWord(ss);
        if (op.length() != 1)
            throw new Exception("Expected arithmetic operator after space");
        if ("+-*/".indexOf(op) < 0)
            throw new Exception("Unknown arithmetic operator: " + op);
        if (!skip(ss, " "))
            throw new Exception("Expected space after arithmetic operator");
        ValueExp rhs = parseExp(ss);
        switch (op.charAt(0)) {
        case '+': return Query.plus(prim, rhs);
        case '-': return Query.minus(prim, rhs);
        case '*': return Query.times(prim, rhs);
        case '/': return Query.div(prim, rhs);
        default: throw new Exception("Can't happen: " + op.charAt(0));
        }
    }

    private static ValueExp parsePrimary(String[] ss) throws Exception {
        String s = ss[0];

        if (s.length() == 0)
            throw new Exception("Empty string found, expression expected");

        char first = s.charAt(0);

        if (first == ' ')
            throw new Exception("Space found, expression expected");

        if (first == '-' || Character.isDigit(first))
            return parseNumberExp(ss);

        if (first == '\'')
            return parseString(ss);

        if (matchWord(ss, "true"))
            return Query.value(true);

        if (matchWord(ss, "false"))
            return Query.value(false);

        if (matchWord(ss, "Class"))
            return Query.classattr();

        String word = scanWord(ss);
        int lastDot = word.lastIndexOf('.');
        if (lastDot < 0)
            return Query.attr(word);
        else
            return Query.attr(word.substring(0, lastDot),
                              word.substring(lastDot + 1));
    }

    private static String scanWord(String[] ss) throws Exception {
        String s = ss[0];
        int space = s.indexOf(' ');
        if (space < 0) {
            ss[0] = "";
            return s;
        } else {
            String word = s.substring(0, space);
            ss[0] = s.substring(space);
            return word;
        }
    }

    private static boolean matchWord(String[] ss, String word)
            throws Exception {
        String s = ss[0];
        if (s.startsWith(word)) {
            int len = word.length();
            if (s.length() == len || s.charAt(len) == ' '
                || s.charAt(len) == ')') {
                ss[0] = s.substring(len);
                return true;
            }
        }
        return false;
    }

    private static ValueExp parseNumberExp(String[] ss) throws Exception {
        String s = ss[0];
        int len = s.length();
        boolean isFloat = false;
        int i;
        for (i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c) || c == '-' || c == '+')
                continue;
            if (c == '.' || c == 'e' || c == 'E') {
                isFloat = true;
                continue;
            }
            break;
        }
        ss[0] = s.substring(i);
        s = s.substring(0, i);
        if (isFloat)
            return Query.value(Double.parseDouble(s));
        else
            return Query.value(Long.parseLong(s));
    }

    private static ValueExp parseString(String[] ss) throws Exception {
        if (!skip(ss, "'"))
            throw new Exception("Expected ' at start of string");
        String s = ss[0];
        int len = s.length();
        StringBuffer buf = new StringBuffer();
        int i;
        for (i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                ss[0] = s.substring(i + 1);
                return Query.value(buf.toString());
            }
            if (c == '\\') {
                if (++i == len)
                    throw new Exception("\\ at end of string");
                c = s.charAt(i);
            }
            buf.append(c);
        }
        throw new Exception("No closing ' at end of string");
    }

    private static boolean skip(String[] ss, String skip) {
        if (ss[0].startsWith(skip)) {
            ss[0] = ss[0].substring(skip.length());
            return true;
        } else
            return false;
    }
}
