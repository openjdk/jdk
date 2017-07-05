/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     6267846 6275009
 * @summary Test Collections.nCopies
 * @author  Martin Buchholz
 */

import java.util.*;
import java.util.concurrent.*;

public class NCopies {
    static volatile int passed = 0, failed = 0;

    static void fail(String msg) {
        failed++;
        new AssertionError(msg).printStackTrace();
    }

    static void pass() {
        passed++;
    }

    static void unexpected(Throwable t) {
        failed++;
        t.printStackTrace();
    }

    static void check(boolean condition, String msg) {
        if (condition)
            passed++;
        else
            fail(msg);
    }

    static void check(boolean condition) {
        check(condition, "Assertion failure");
    }

    private static void checkEmpty(List<String> x) {
            check(x.isEmpty());
            check(x.size() == 0);
            check(x.indexOf("foo") == -1);
            check(x.lastIndexOf("foo") == -1);
            check(x.toArray().length == 0);
            check(x.toArray().getClass() == Object[].class);
    }

    private static void checkFoos(List<String> x) {
            check(! x.isEmpty());
            check(x.indexOf(new String("foo")) == 0);
            check(x.lastIndexOf(new String("foo")) == x.size()-1);
            check(x.toArray().length == x.size());
            check(x.toArray().getClass() == Object[].class);
            String[] sa = x.toArray(new String[x.size()]);
            check(sa.getClass() == String[].class);
            check(sa[0].equals("foo"));
            check(sa[sa.length-1].equals("foo"));
            check(x.get(x.size()/2).equals("foo"));
            checkEmpty(x.subList(x.size()/2, x.size()/2));
    }

    public static void main(String[] args) {
        try {
            List<String> empty = Collections.nCopies(0, "foo");
            checkEmpty(empty);
            checkEmpty(empty.subList(0,0));

            List<String> foos = Collections.nCopies(42, "foo");
            check(foos.size() == 42);
            checkFoos(foos.subList(foos.size()/2, foos.size()-1));

        } catch (Throwable t) { unexpected(t); }

        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new Error("Some tests failed");
    }
}
