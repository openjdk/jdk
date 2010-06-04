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

/* @test
 * @bug 4313887
 * @summary Unit test for java.nio.file.Path
 */

import java.nio.file.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.PrintStream;

public class UriImportExport {

    static final PrintStream log = System.out;
    static int failures = 0;

    static void test(String fn, String expected) {
        log.println();
        Path p = Paths.get(fn);
        log.println(p);
        URI u = p.toUri();
        log.println("  --> " + u);
        if (expected != null && !(u.toString().equals(expected))) {
            log.println("FAIL: Expected " + expected);
            failures++;
            return;
        }
        Path q = Paths.get(u);
        log.println("  --> " + q);
        if (!p.toAbsolutePath().equals(q)) {
            log.println("FAIL: Expected " + p + ", got " + q);
            failures++;
            return;
        }
    }

    static void test(String fn) {
        test(fn, null);
    }

    public static void main(String[] args) throws Exception {
        test("foo");
        test("/foo");
        test("/foo bar");

        String osname = System.getProperty("os.name");
        if (osname.startsWith("Windows")) {
            test("C:\\foo");
            test("C:foo");
            test("\\\\rialto.dublin.com\\share\\");
            test("\\\\fe80--203-baff-fe5a-749ds1.ipv6-literal.net\\share\\missing",
                "file://[fe80::203:baff:fe5a:749d%1]/share/missing");
        }

        if (failures > 0)
            throw new RuntimeException(failures + " test(s) failed");
    }
}
