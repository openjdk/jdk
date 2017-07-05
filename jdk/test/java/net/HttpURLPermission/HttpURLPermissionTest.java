/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.net.HttpURLPermission;
import java.io.*;

/**
 * @test
 * @bug 8010464
 */

public class HttpURLPermissionTest {

    // super class for all test types
    abstract static class Test {
        boolean expected;
        abstract boolean execute();
    };

    // Tests URL part of implies() method. This is the main test.
    static class URLImpliesTest extends Test {
        String arg1, arg2;

        URLImpliesTest(String arg1, String arg2, boolean expected) {
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.expected = expected;
        }

          boolean execute() {
            HttpURLPermission p1 = new HttpURLPermission (arg1, "GET:*");
            HttpURLPermission p2 = new HttpURLPermission (arg2, "GET:*");
            boolean result = p1.implies(p2);
            return result == expected;
        }
    };

    static URLImpliesTest imtest(String arg1, String arg2, boolean expected) {
        return new URLImpliesTest(arg1, arg2, expected);
    }

    static class ActionImpliesTest extends Test {
        String arg1, arg2;

        ActionImpliesTest(String arg1, String arg2, boolean expected) {
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.expected = expected;
        }

          boolean execute() {
            String url1 = "http://www.foo.com/-";
            String url2 = "http://www.foo.com/a/b";
            HttpURLPermission p1 = new HttpURLPermission(url1, arg1);
            HttpURLPermission p2 = new HttpURLPermission(url2, arg2);
            boolean result = p1.implies(p2);
            return result == expected;
        }
    }

    static ActionImpliesTest actest(String arg1, String arg2, boolean expected) {
        return new ActionImpliesTest(arg1, arg2, expected);
    }

    static Test[] pathImplies = {
        // single
        imtest("http://www.foo.com/", "http://www.foo.com/", true),
        imtest("http://www.bar.com/", "http://www.foo.com/", false),
        imtest("http://www.foo.com/a/b", "http://www.foo.com/", false),
        imtest("http://www.foo.com/a/b", "http://www.foo.com/a/b/c", false),
        // wildcard
        imtest("http://www.foo.com/a/b/*", "http://www.foo.com/a/b/c", true),
        imtest("http://www.foo.com/a/b/*", "http://www.foo.com/a/b/*", true),
        imtest("http://www.foo.com/a/b/*", "http://www.foo.com/a/b/c#frag", true),
        imtest("http://www.foo.com/a/b/*", "http://www.foo.com/a/b/c#frag?foo=foo", true),
        imtest("http://www.foo.com/a/b/*", "http://www.foo.com/b/b/c", false),
        imtest("http://www.foo.com/a/b/*", "http://www.foo.com/a/b/c.html", true),
        imtest("http://www.foo.com/a/b/*", "http://www.foo.com/a/b/c.html", true),
        imtest("http://www.foo.com/a/b/*", "https://www.foo.com/a/b/c", false),
        // recursive
        imtest("http://www.foo.com/a/b/-", "http://www.foo.com/a/b/-", true),
        imtest("http://www.foo.com/a/b/-", "http://www.foo.com/a/b/c", true),
        imtest("http://www.foo.com/a/b/-", "http://www.foo.com/a/b/c#frag", true),
        imtest("http://www.foo.com/a/b/-", "http://www.foo.com/a/b/c#frag?foo=foo", true),
        imtest("http://www.foo.com/a/b/-", "http://www.foo.com/b/b/c", false),
        imtest("http://www.foo.com/a/b/-", "http://www.foo.com/a/b/c.html", true),
        imtest("http://www.foo.com/a/b/-", "http://www.foo.com/a/b/c.html", true),
        imtest("http://www.foo.com/a/b/-", "http://www.foo.com/a/b/c/d/e.html", true),
        imtest("https://www.foo.com/a/b/-", "http://www.foo.com/a/b/c/d/e.html", false),
        imtest("http://www.foo.com/a/b/-", "http://www.foo.com/a/b/c/d/e#frag", true),
        imtest("http://www.foo.com/a/b/-", "https://www.foo.com/a/b/c", false),
        // special cases
        imtest("http:*", "https://www.foo.com/a/b/c", false),
        imtest("http:*", "http://www.foo.com/a/b/c", true),
        imtest("http:*", "http://foo/bar", true),
        imtest("http://foo/bar", "https://foo/bar", false)
    };

    static Test[] actionImplies = {
        actest("GET", "GET", true),
        actest("GET", "POST", false),
        actest("GET:", "PUT", false),
        actest("GET:", "GET", true),
        actest("GET,POST", "GET", true),
        actest("GET,POST:", "GET", true),
        actest("GET:X-Foo", "GET:x-foo", true),
        actest("GET:X-Foo,X-bar", "GET:x-foo", true),
        actest("GET:X-Foo", "GET:x-boo", false),
        actest("GET:X-Foo,X-Bar", "GET:x-bar,x-foo", true),
        actest("GET:X-Bar,X-Foo,X-Bar,Y-Foo", "GET:x-bar,x-foo", true),
        actest("GET:*", "GET:x-bar,x-foo", true),
        actest("*:*", "GET:x-bar,x-foo", true)
    };

    static boolean failed = false;

    public static void main(String args[]) throws Exception {
        for (int i=0; i<pathImplies.length ; i++) {
            URLImpliesTest test = (URLImpliesTest)pathImplies[i];
            Exception caught = null;
            boolean result = false;
            try {
                result = test.execute();
            } catch (Exception e) {
                caught = e;
                e.printStackTrace();
            }
            if (!result) {
                failed = true;
                System.out.println ("test failed: " + test.arg1 + ": " +
                        test.arg2 + " Exception: " + caught);
            }
            System.out.println ("path test " + i + " OK");

        }
        for (int i=0; i<actionImplies.length ; i++) {
            ActionImpliesTest test = (ActionImpliesTest)actionImplies[i];
            Exception caught = null;
            boolean result = false;
            try {
                result = test.execute();
            } catch (Exception e) {
                caught = e;
                e.printStackTrace();
            }
            if (!result) {
                failed = true;
                System.out.println ("test failed: " + test.arg1 + ": " +
                        test.arg2 + " Exception: " + caught);
            }
            System.out.println ("action test " + i + " OK");
        }

        serializationTest("http://www.foo.com/-", "GET,DELETE:*");
        serializationTest("https://www.foo.com/-", "POST:X-Foo");
        serializationTest("https:*", "*:*");
        serializationTest("http://www.foo.com/a/b/s/", "POST:X-Foo");
        serializationTest("http://www.foo.com/a/b/s/*", "POST:X-Foo");

        if (failed) {
            throw new RuntimeException("some tests failed");
        }

    }

    static void serializationTest(String name, String actions)
        throws Exception {

        HttpURLPermission out = new HttpURLPermission(name, actions);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream o = new ObjectOutputStream(baos);
        o.writeObject(out);
        ByteArrayInputStream bain = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream i = new ObjectInputStream(bain);
        HttpURLPermission in = (HttpURLPermission)i.readObject();
        if (!in.equals(out)) {
            System.out.println ("FAIL");
            System.out.println ("in = " + in);
            System.out.println ("out = " + out);
            failed = true;
        }
    }
}
