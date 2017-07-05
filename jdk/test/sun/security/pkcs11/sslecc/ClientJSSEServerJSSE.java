/*
 * Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 6405536
 * @summary Verify that all ciphersuites work (incl. ECC using NSS crypto)
 * @author Andreas Sterbenz
 * @library ..
 * @library ../../../../java/security/testlibrary
 * @run main/othervm ClientJSSEServerJSSE
 */

import java.security.*;

public class ClientJSSEServerJSSE extends PKCS11Test {

    private static String[] cmdArgs;

    public static void main(String[] args) throws Exception {
        cmdArgs = args;
        main(new ClientJSSEServerJSSE());
    }

    public void main(Provider p) throws Exception {
        if (p.getService("KeyFactory", "EC") == null) {
            System.out.println("Provider does not support EC, skipping");
            return;
        }
        Providers.setAt(p, 1);
        CipherTest.main(new JSSEFactory(), cmdArgs);
        Security.removeProvider(p.getName());
    }

    private static class JSSEFactory extends CipherTest.PeerFactory {

        String getName() {
            return "Client JSSE - Server JSSE";
        }

        CipherTest.Client newClient(CipherTest cipherTest) throws Exception {
            return new JSSEClient(cipherTest);
        }

        CipherTest.Server newServer(CipherTest cipherTest) throws Exception {
            return new JSSEServer(cipherTest);
        }
    }
}
