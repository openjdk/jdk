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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/*
 * @test
 * @bug 8356997
 * @summary Support "include" anywhere
 * @modules java.security.jgss/sun.security.krb5
 * @library /test/lib
 * @run main/othervm IncludeSameKey
 */
public class IncludeSameKey {
    public static void main(String[] args) throws Exception {
        var cwd = Path.of("").toAbsolutePath().toString();
        Files.writeString(Path.of("krb5.conf"), String.format("""
                include %1$s/outside
                [a]
                include %1$s/beginsec
                b = {
                    c = 1
                }
                [a]
                b = {
                    c = 2
                }
                include %1$s/insec
                include %1$s/insec2
                b = {
                include %1$s/insubsec
                    c = 3
                include %1$s/endsubsec
                }
                include %1$s/endsec
                """, cwd));
        for (var inc : List.of("outside", "beginsec", "insec", "insec2",
                "insubsec", "endsubsec", "endsec")) {
            Files.writeString(Path.of(inc), String.format("""
                    [a]
                    b = {
                        c = %s
                    }
                    """, inc));
        }
        System.setProperty("java.security.krb5.conf", "krb5.conf");
        Asserts.assertEQ(Config.getInstance().getAll("a", "b", "c"),
                "outside beginsec 1 2 insec insec2 insubsec 3 endsubsec endsec");
    }
}
