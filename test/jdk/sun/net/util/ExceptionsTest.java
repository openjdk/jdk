/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import jdk.internal.util.Exceptions;
import jdk.internal.util.Exceptions.SensitiveInfo;
import static jdk.internal.util.Exceptions.formatMsg;
import static jdk.internal.util.Exceptions.filterNonSocketInfo;
import static jdk.internal.util.Exceptions.enhancedNonSocketExceptions;

/*
 * @test
 * @bug 8348986
 * @summary Improve coverage of enhanced exception messages
 * @modules java.base/jdk.internal.util
 * @run main/othervm -Djdk.includeInExceptions=hostInfo ExceptionsTest
 * @run main/othervm ExceptionsTest
 * @run main/othervm -Djdk.includeInExceptions=userInfo ExceptionsTest
 * @run main/othervm -Djdk.net.hosts.file=does.not.exist -Djdk.includeInExceptions=userInfo ExceptionsTest
 * @run main/othervm -Djdk.net.hosts.file=does.not.exist -Djdk.includeInExceptions=hostInfo ExceptionsTest
 */
public class ExceptionsTest {

    static boolean netEnabled() {
        System.out.printf("netEnabled = %b\n", enhancedNonSocketExceptions());
        return enhancedNonSocketExceptions();
    }

    static boolean dnsEnabled() {
        System.out.printf("dnsEnabled = %b\n", enhancedNonSocketExceptions());
        return enhancedNonSocketExceptions();
    }

    static boolean hostFileEnabled() {
        return System.getProperty("jdk.net.hosts.file", "").length() > 0;
    }

    static String[][][] tests = {
    //
    // If a format argument is of the form ".pre(xxx)" or ".suf(yyy)", then that is
    // interpreted as a .prefixWith("xxx") or .suffixWith("yyy") call to the preceding
    // argument. .rep() signifies .replaceWith()
    //
    //                  Number of elements in array
    //                  ---------------------------
    //                1              N                 2
    //
    //       Format string         args to format                  Enhanced o/p     non-enhanced o/p
    //
    /*  1 */ {{"foo: %s bar"},     {"abc"},                        {"foo: abc bar", "foo: bar"}},
    /*  2 */ {{"foo: %s bar"},     {"a", "b"},                     {"foo: a bar", "foo: bar"}},
    /*  3 */ {{"foo: %s bar"},     {null},                         {"foo: null bar", "foo: bar"}},
    /*  4 */ {{"foo: %s bar"},     {""},                           {"foo: bar", "foo: bar"}},
    /*  5 */ {{"%s foo: %s bar"},  {"a", "b"},                     {"a foo: b bar", "foo: bar"}},
    /*  6 */ {{"foo: %s bar %s"},  {"a", "b"},                     {"foo: a bar b", "foo: bar"}},
    /*  7 */ {{"foo: %s bar %s"},  {"abc", "def"},                 {"foo: abc bar def", "foo: bar"}},
    /*  8 */ {{"%s bar %s"},       {"abc", ".pre(foo: )", "def"},  {"foo: abc bar def", "bar"}},
    /*  9 */ {{"%s baz"},          {"abc", ".suf(: bar)"},         {"abc: bar baz", "baz"}},
    /* 10 */ {{"%s baz"},          {"abc", ".suf(: bar)"
                                         , ".rep(bob)"},           {"abc: bar baz", "bob baz"}}
    };


    static void dnsTest() {
        String host = "fub.z.a.bar.foo";
        try {
            var addr = InetAddress.getByName(host);
        } catch (IOException e) {
            if (!dnsEnabled() && e.toString().contains(host))
                throw new RuntimeException("Name lookup failed");
        }
    }

    static void hostFileTest() {
        String result1 = "Unable to resolve host www.rte.ie as hosts file does.not.exist not found";
        String result2 = "Unable to resolve host as hosts file " +
                         "from ${jdk.net.hosts.file} system property not found";

        try {
            var a = InetAddress.getByName("www.rte.ie");
        } catch (IOException e) {
            if (dnsEnabled() && !e.toString().contains(result1)) {
                System.out.println("Lookup failed: " + e.toString());
                throw new RuntimeException("Name lookup failed");
            }
            if (!dnsEnabled() && !e.toString().contains(result2)) {
                System.out.println("Lookup failed: " + e.toString());
                throw new RuntimeException("Name lookup failed");
            }
        }
    }


    final static String PRE = ".pre(";
    final static String SUF = ".suf(";
    final static String REP = ".rep(";

    static SensitiveInfo[] getArgs(String[] args) {
        SensitiveInfo[] sa = new SensitiveInfo[args.length];

        int index = 0;
        for (String s : args) {
            if (s != null && s.startsWith(PRE)) {
                var preArg = s.substring(PRE.length(), s.indexOf(')'));
                sa[index-1] = sa[index-1].prefixWith(preArg);
            } else if (s != null && s.startsWith(SUF)) {
                var sufArg = s.substring(SUF.length(), s.indexOf(')'));
                sa[index-1] = sa[index-1].suffixWith(sufArg);
            } else if (s != null && s.startsWith(REP)) {
                var repArg = s.substring(REP.length(), s.indexOf(')'));
                sa[index-1] = sa[index-1].replaceWith(repArg);
            } else {
                sa[index++] = filterNonSocketInfo(s);
            }
        }
        return Arrays.copyOf(sa, index);
    }

    public static void main(String[] a) {
        if (!hostFileEnabled()) {
            dnsTest();
        } else {
            hostFileTest();
            return;
        }

        int count = 1;
        for (String[][] test : tests) {
            String format = test[0][0];
            String expectedEnhanced = test[2][0];
            String expectedNormal = test[2][1];
            SensitiveInfo[] args = getArgs(test[1]);

            String output = formatMsg(format, args);
            if (netEnabled()) {
                if (!output.equals(expectedEnhanced)) {
                    var msg = String.format("FAIL %d: got: \"%s\" Expected: \"%s\"", count,
                                output, expectedEnhanced);
                    throw new RuntimeException(msg);
                }
            } else {
                if (!output.equals(expectedNormal)) {
                    var msg = String.format("FAIL %d: got: \"%s\" Expected: \"%s\"", count,
                                output, expectedNormal);
                    throw new RuntimeException(msg);
                }
            }
            count++;
        }
    }
}
