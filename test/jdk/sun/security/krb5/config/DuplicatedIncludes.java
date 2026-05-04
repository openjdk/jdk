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

import jdk.test.lib.Asserts;
import sun.security.krb5.Config;
import sun.security.krb5.KrbException;

import java.nio.file.Files;
import java.nio.file.Path;

/*
 * @test
 * @bug 8356997
 * @summary Support "include" anywhere
 * @modules java.security.jgss/sun.security.krb5
 * @library /test/lib
 * @run main/othervm DuplicatedIncludes
 */
public class DuplicatedIncludes {
    public static void main(String[] args) throws Exception {

        var cwd = Path.of("").toAbsolutePath().toString();
        System.setProperty("java.security.krb5.conf", "krb5.conf");

        // It's OK to include a file multiple times
        Files.writeString(Path.of("krb5.conf"), String.format("""
                include %1$s/sub
                include %1$s/sub
                """, cwd));

        Files.writeString(Path.of("sub"), """
                [a]
                b = c
                """);
        Config.refresh();

        // But a file cannot include itself
        Files.writeString(Path.of("sub"), String.format("""
                include %1$s/sub
                """, cwd));
        Asserts.assertThrows(KrbException.class, () -> Config.refresh());

        // A file also cannot include a file that includes it
        Files.writeString(Path.of("sub"), String.format("""
                include %1$s/sub2
                """, cwd));
        Files.writeString(Path.of("sub2"), String.format("""
                include %1$s/sub
                """, cwd));
        Asserts.assertThrows(KrbException.class, () -> Config.refresh());

        // It's OK for a file to include another file that has already
        // been included multiple times, as long as it's not on the stack.
        // This proves it's necessary to place "dups.remove(file)" in a
        // finally block in Config::readConfigFileLines. This case is
        // not covered by IncludeRandom.java because of the structured
        // include pattern (included always longer than includee) there.
        Files.writeString(Path.of("krb5.conf"), String.format("""
                include %1$s/sub
                include %1$s/sub
                include %1$s/sub2
                """, cwd));
        Files.writeString(Path.of("sub"), "");
        Files.writeString(Path.of("sub2"), String.format("""
                include %1$s/sub
                """, cwd));
        Config.refresh();
    }
}
