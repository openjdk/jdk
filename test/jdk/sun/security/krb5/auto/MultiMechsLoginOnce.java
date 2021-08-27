/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6859027
 * @summary Duplicate communications to KDC in GSSManager.createCredential(usage)
 * @library /test/lib
 * @compile -XDignore.symbol.file MultiMechsLoginOnce.java
 * @run main jdk.test.lib.FileInstaller TestHosts TestHosts
 * @run main/othervm -Djdk.net.hosts.file=TestHosts MultiMechsLoginOnce me
 * @run main/othervm -Djdk.net.hosts.file=TestHosts MultiMechsLoginOnce null
 */

import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

public class MultiMechsLoginOnce {

    static int count = 0;

    public static void main(String[] args) throws Exception {

        new OneKDC(null) {
            @Override
            protected byte[] processAsReq(byte[] in) throws Exception {
                count++;
                return super.processAsReq(in);
            }
        }.writeJAASConf()
                .setOption(KDC.Option.PREAUTH_REQUIRED, false);

        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
        GSSManager man = GSSManager.getInstance();

        // Test both with name and without name
        GSSName me = args[0].equals("me")
                ? man.createName(OneKDC.USER, GSSName.NT_USER_NAME)
                : null;
        GSSCredential cred = man.createCredential(
                me,
                GSSCredential.DEFAULT_LIFETIME,
                (Oid[])null,
                GSSCredential.INITIATE_ONLY);
        if (cred.getMechs().length < 2) {
            throw new RuntimeException("Not multi mech: " + cred);
        }
        if (count != 1) {
            throw new RuntimeException("Request not once: " + count);
        }
    }
}
