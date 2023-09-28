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
 * @bug 8309356
 * @summary Read files in includedir in alphanumeric order
 * @modules java.security.jgss/sun.security.krb5
 * @library /test/lib
 * @run main/othervm IncludeDirOrder
 */

import jdk.test.lib.Asserts;
import sun.security.krb5.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IncludeDirOrder {
    public static void main(String[] args) throws Exception {
        long seed = new Random().nextLong();
        System.out.println("Seed is " + seed);
        Random random = new Random(seed);
        Path xdir = Path.of("x");
        Files.writeString(Path.of("main.conf"), String.format("""
                includedir %s
                [libdefaults]
                default_realm = K
                """, xdir.toAbsolutePath()));
        Files.createDirectory(xdir);
        var list = IntStream.range(10, 20).boxed().collect(Collectors.toList());
        Collections.shuffle(list, random);
        for (var i : list) {
            write(xdir, i);
        }
        // K10 should always be read first
        System.setProperty("java.security.krb5.conf", "main.conf");
        Asserts.assertEQ(Config.getInstance().getDefaultRealm(), "K10");
    }

    static void write(Path xdir, int realm) throws IOException {
        Files.writeString(xdir.resolve("K" + realm), String.format("""
                [libdefaults]
                default_realm = K%d
                """, realm));
    }
}
