/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7158329
 * @bug 8001208
 * @summary NPE in sun.security.krb5.Credentials.acquireDefaultCreds()
 * @compile -XDignore.symbol.file EmptyCC.java
 * @run main EmptyCC tmpcc
 * @run main EmptyCC FILE:tmpcc
 */
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import sun.security.krb5.Credentials;
import sun.security.krb5.PrincipalName;
import sun.security.krb5.internal.ccache.CredentialsCache;

public class EmptyCC {
    public static void main(String[] args) throws Exception {
        final PrincipalName pn = new PrincipalName("dummy@FOO.COM");
        final String ccache = args[0];

        if (args.length == 1) {
            // Main process, write the ccache and launch sub process
            CredentialsCache cache = CredentialsCache.create(pn, ccache);
            cache.save();

            // java -cp $test.classes EmptyCC readcc
            ProcessBuilder pb = new ProcessBuilder(
                    new File(new File(System.getProperty("java.home"), "bin"),
                        "java").getPath(),
                    "-cp",
                    System.getProperty("test.classes"),
                    "EmptyCC",
                    ccache,
                    "readcc"
                    );

            pb.environment().put("KRB5CCNAME", ccache);
            pb.redirectErrorStream(true);

            Process p = pb.start();
            try (InputStream ins = p.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = ins.read(buf)) > 0) {
                    System.out.write(buf, 0, n);
                }
            }
            if (p.waitFor() != 0) {
                throw new Exception("Test failed");
            }
        } else {
            // Sub process, read the ccache
            String cc = System.getenv("KRB5CCNAME");
            if (!cc.equals(ccache)) {
                throw new Exception("env not set correctly");
            }
            // 8001208: Fix for KRB5CCNAME not complete
            // Make sure the ccache is created with bare file name
            if (CredentialsCache.getInstance() == null) {
                throw new Exception("Cache not instantiated");
            }
            if (!new File("tmpcc").exists()) {
                throw new Exception("File not found");
            }
            Credentials.acquireTGTFromCache(pn, null);
        }
    }
}
