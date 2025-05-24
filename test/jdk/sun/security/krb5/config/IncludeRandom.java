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

/*
 * @test
 * @bug 8356997
 * @summary Support "include" anywhere
 * @modules java.security.jgss/sun.security.krb5
 * @library /test/lib
 * @run main/othervm IncludeRandom
 */
import jdk.test.lib.security.SeededSecureRandom;
import sun.security.krb5.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// A randomized class to prove that wherever the "include" line is inside
// a krb5.conf file, it can always be parsed correctly.
public class IncludeRandom {

    static SecureRandom sr = SeededSecureRandom.one();

    // Must be global. Counting in recursive methods
    static int nInc = 0; // number of included files
    static int nAssign = 0; // number of assignments to the same setting

    public static void main(String[] args) throws Exception {
        System.setProperty("java.security.krb5.conf", "f");
        for (var i = 0; i < 10_000; i++) {
            test();
        }
    }

    static void test() throws Exception {
        nInc = 0;
        nAssign = 0;
        write("f");
        if (nAssign != 0) {
            Config.refresh();
            var j = Config.getInstance().getAll("section", "x");
            var r = read("f", new ArrayList<String>())
                    .stream()
                    .collect(Collectors.joining(" "));
            if (!j.equals(r)) {
                System.out.println(j);
                System.out.println(r);
                throw new RuntimeException("Different");
            }
        }
        try (var dir = Files.newDirectoryStream(Path.of("."))) {
            for (var f : dir) {
                Files.delete(f);
            }
        }
    }

    // read settings as raw files
    static List<String> read(String f, List<String> list) throws IOException {
        for (var s : Files.readAllLines(Path.of(f))) {
            if (s.startsWith("include ")) {
                read(s.substring(8), list);
            }
            if (s.startsWith("x = ")) {
                list.add(s.substring(4));
            }
        }
        return list;
    }

    // write krb5.conf with random include
    static void write(String f) throws IOException {
        var p = Path.of(f);
        if (Files.exists(p)) return;
        var content = new ArrayList<String>();
        content.add("[section]");
        for (var i = 0; i < sr.nextInt(5); i++) {
            if (sr.nextBoolean()) content.add("[section]");
            content.add("x = " + sr.nextInt(99999999));
            nAssign++;
        }
        for (var i = 0; i < sr.nextInt(3); i++) {
            if (nInc < 98) {
                // include file name is random, so there could be dup
                // but name length always grows, so no recursive
                var inc = f + sr.nextInt(20);
                content.add(sr.nextInt(content.size() + 1),
                        "include " + Path.of(inc).toAbsolutePath());
                nInc++;
                write(inc);
            }
        }
        Files.write(p, content);
    }
}
