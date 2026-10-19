/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8370615
 * @summary Improve Kerberos credentialing
 * @library /test/lib
 * @compile -XDignore.symbol.file UserIterCount.java
 * @run main jdk.test.lib.FileInstaller TestHosts TestHosts
 * @run main/othervm -Djdk.net.hosts.file=TestHosts UserIterCount
 */

import java.util.HashMap;

import sun.security.krb5.EncryptionKey;
import sun.security.krb5.KrbException;
import sun.security.krb5.PrincipalName;

public class UserIterCount {

    static class MyKDC extends OneKDC {
        static final HashMap<String, EncryptionKey> CACHE
                = new HashMap<>();

        public MyKDC() throws Exception {
            super(null);
        }

        @Override
        protected byte[] getParams(PrincipalName p, int etype) {
            if (etype == 18) {
                if (p.toString().startsWith(OneKDC.USER)) {
                    return new byte[]{0, 0, 16, 01};
                } else {
                    return new byte[]{0, 79, (byte)255, (byte)255};
                }
            } else {
                return super.getParams(p, etype);
            }
        }

        @Override
        EncryptionKey keyForUser(PrincipalName p, int etype, boolean server)
                throws KrbException {
            var key = p.toString() + etype + server;
            var v = CACHE.get(key);
            if (v == null) {
                v = super.keyForUser(p, etype, server);
                CACHE.put(key, v);
            }
            return v;
        }
    }

    public static void main(String[] args) throws Exception {
        new MyKDC().writeJAASConf();
        Context.fromUserPass(OneKDC.USER, OneKDC.PASS, false);
        Context.fromUserPass(OneKDC.USER2, OneKDC.PASS2, false);
    }
}
