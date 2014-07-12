/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8029994
 * @summary Support "include" and "includedir" in krb5.conf
 * @compile -XDignore.symbol.file Include.java
 * @run main/othervm Include
 */
import sun.security.krb5.Config;
import sun.security.krb5.KrbException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Include {
    public static void main(String[] args) throws Exception {

        String krb5Conf = "[section]\nkey=";        // Skeleton of a section

        Path conf = Paths.get("krb5.conf");         // base krb5.conf

        Path ifile = Paths.get("f");                // include f
        Path idir = Paths.get("x");                 // includedir fx
        Path idirdir = Paths.get("x/xx");           // sub dir, will be ignored
        Path idirdirfile = Paths.get("x/xx/ff");    // sub dir, will be ignored
        Path idirfile1 = Paths.get("x/f1");         // one file
        Path idirfile2 = Paths.get("x/f2");         // another file
        Path idirfile3 = Paths.get("x/f.3");        // third file bad name

        // OK: The base file can be missing
        System.setProperty("java.security.krb5.conf", "no-such-file");
        tryReload(true);

        System.setProperty("java.security.krb5.conf", conf.toString());

        // Write base file
        Files.write(conf,
                ("include " + ifile.toAbsolutePath() + "\n" +
                        "includedir " + idir.toAbsolutePath() + "\n" +
                        krb5Conf + "base").getBytes()
        );

        // Error: Neither include nor includedir exists
        tryReload(false);

        // Error: Only includedir exists
        Files.createDirectory(idir);
        tryReload(false);

        // Error: Both exists, but include is a cycle
        Files.write(ifile,
                ("include " + conf.toAbsolutePath() + "\n" +
                    krb5Conf + "incfile").getBytes());
        tryReload(false);

        // Error: A good include exists, but no includedir
        Files.delete(idir);
        Files.write(ifile, (krb5Conf + "incfile").getBytes());
        tryReload(false);

        // OK: Everything is set
        Files.createDirectory(idir);
        tryReload(true);   // Now OK

        // fx1 and fx2 will be loaded
        Files.write(idirfile1, (krb5Conf + "incdir1").getBytes());
        Files.write(idirfile2, (krb5Conf + "incdir2").getBytes());
        // fx3 and fxs (and file inside it) will be ignored
        Files.write(idirfile3, (krb5Conf + "incdir3").getBytes());
        Files.createDirectory(idirdir);
        Files.write(idirdirfile, (krb5Conf + "incdirdir").getBytes());

        // OK: All good files read
        tryReload(true);

        String v = Config.getInstance().getAll("section", "key");
        // The order of files in includedir could be either
        if (!v.equals("incfile incdir1 incdir2 base") &&
                !v.equals("incfile incdir2 incdir1 base")) {
            throw new Exception(v);
        }

        // Error: include file not absolute
        Files.write(conf,
                ("include " + ifile + "\n" +
                        "includedir " + idir.toAbsolutePath() + "\n" +
                        krb5Conf + "base").getBytes()
        );
        tryReload(false);

        // Error: includedir not absolute
        Files.write(conf,
                ("include " + ifile.toAbsolutePath() + "\n" +
                        "includedir " + idir + "\n" +
                        krb5Conf + "base").getBytes()
        );
        tryReload(false);

        // OK: unsupported directive
        Files.write(conf,
                ("module /lib/lib.so\n" +
                        krb5Conf + "base").getBytes()
        );
        tryReload(true);
    }

    private static void tryReload(boolean expected) throws Exception {
        if (expected) {
            Config.refresh();
        } else {
            try {
                Config.refresh();
                throw new Exception("Should be illegal");
            } catch (KrbException ke) {
                // OK
            }
        }
    }
}
