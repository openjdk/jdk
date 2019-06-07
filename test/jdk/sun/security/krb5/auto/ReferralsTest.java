/*
 * Copyright (c) 2019, Red Hat, Inc.
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
 * @bug 8215032
 * @library /test/lib
 * @run main/othervm/timeout=120 -Dsun.security.krb5.debug=true ReferralsTest
 * @summary Test Kerberos cross-realm referrals (RFC 6806)
 */

import java.io.File;
import sun.security.krb5.Credentials;
import sun.security.krb5.internal.CredentialsUtil;
import sun.security.krb5.KrbAsReqBuilder;
import sun.security.krb5.PrincipalName;

public class ReferralsTest {
    private static final boolean DEBUG = true;
    private static final String krbConfigName = "krb5-localkdc.conf";
    private static final String realmKDC1 = "RABBIT.HOLE";
    private static final String realmKDC2 = "DEV.RABBIT.HOLE";
    private static final char[] password = "123qwe@Z".toCharArray();
    private static final String clientName = "test";

    private static final String clientAlias = clientName +
            PrincipalName.NAME_REALM_SEPARATOR_STR + realmKDC1;

    private static final String clientKDC1QueryName = clientAlias.replaceAll(
            PrincipalName.NAME_REALM_SEPARATOR_STR, "\\\\" +
            PrincipalName.NAME_REALM_SEPARATOR_STR) +
            PrincipalName.NAME_REALM_SEPARATOR_STR + realmKDC1;
    private static PrincipalName clientKDC1QueryPrincipal = null;
    static {
        try {
            clientKDC1QueryPrincipal = new PrincipalName(
                    clientKDC1QueryName, PrincipalName.KRB_NT_ENTERPRISE,
                    null);
        } catch (Throwable t) {}
    }

    private static final String clientKDC2Name = clientName +
            PrincipalName.NAME_REALM_SEPARATOR_STR + realmKDC2;

    private static final String serviceName = "http" +
            PrincipalName.NAME_COMPONENT_SEPARATOR_STR +
            "server.dev.rabbit.hole";

    private static Credentials tgt;
    private static Credentials tgs;

    public static void main(String[] args) throws Exception {
        try {
            initializeKDCs();
            getTGT();
            getTGS();
        } finally {
            cleanup();
        }
    }

    private static void initializeKDCs() throws Exception {
        KDC kdc1 = KDC.create(realmKDC1, "localhost", 0, true);
        kdc1.addPrincipalRandKey(PrincipalName.TGS_DEFAULT_SRV_NAME +
                PrincipalName.NAME_COMPONENT_SEPARATOR_STR + realmKDC1);
        kdc1.addPrincipal(PrincipalName.TGS_DEFAULT_SRV_NAME +
                PrincipalName.NAME_COMPONENT_SEPARATOR_STR + realmKDC1 +
                PrincipalName.NAME_REALM_SEPARATOR_STR + realmKDC2,
                password);
        kdc1.addPrincipal(PrincipalName.TGS_DEFAULT_SRV_NAME +
                PrincipalName.NAME_COMPONENT_SEPARATOR_STR + realmKDC2,
                password);

        KDC kdc2 = KDC.create(realmKDC2, "localhost", 0, true);
        kdc2.addPrincipalRandKey(PrincipalName.TGS_DEFAULT_SRV_NAME +
                PrincipalName.NAME_COMPONENT_SEPARATOR_STR + realmKDC2);
        kdc2.addPrincipal(clientKDC2Name, password);
        kdc2.addPrincipal(serviceName, password);
        kdc2.addPrincipal(PrincipalName.TGS_DEFAULT_SRV_NAME +
                PrincipalName.NAME_COMPONENT_SEPARATOR_STR + realmKDC1,
                password);
        kdc2.addPrincipal(PrincipalName.TGS_DEFAULT_SRV_NAME +
                PrincipalName.NAME_COMPONENT_SEPARATOR_STR + realmKDC2 +
                PrincipalName.NAME_REALM_SEPARATOR_STR + realmKDC1,
                password);

        kdc1.registerAlias(clientAlias, kdc2);
        kdc1.registerAlias(serviceName, kdc2);
        kdc2.registerAlias(clientAlias, clientKDC2Name);

        KDC.saveConfig(krbConfigName, kdc1, kdc2,
                    "forwardable=true");
        System.setProperty("java.security.krb5.conf", krbConfigName);
    }

    private static void cleanup() {
        File f = new File(krbConfigName);
        if (f.exists()) {
            f.delete();
        }
    }

    private static void getTGT() throws Exception {
        KrbAsReqBuilder builder = new KrbAsReqBuilder(clientKDC1QueryPrincipal,
                password);
        tgt = builder.action().getCreds();
        builder.destroy();
        if (DEBUG) {
            System.out.println("TGT");
            System.out.println("----------------------");
            System.out.println(tgt);
            System.out.println("----------------------");
        }
        if (tgt == null) {
            throw new Exception("TGT is null");
        }
        if (!tgt.getClient().getName().equals(clientKDC2Name)) {
            throw new Exception("Unexpected TGT client");
        }
        String[] tgtServerNames = tgt.getServer().getNameStrings();
        if (tgtServerNames.length != 2 || !tgtServerNames[0].equals(
                PrincipalName.TGS_DEFAULT_SRV_NAME) ||
                !tgtServerNames[1].equals(realmKDC2) ||
                !tgt.getServer().getRealmString().equals(realmKDC2)) {
            throw new Exception("Unexpected TGT server");
        }
    }

    private static void getTGS() throws Exception {
        tgs = CredentialsUtil.acquireServiceCreds(serviceName +
                PrincipalName.NAME_REALM_SEPARATOR_STR + realmKDC1, tgt);
        if (DEBUG) {
            System.out.println("TGS");
            System.out.println("----------------------");
            System.out.println(tgs);
            System.out.println("----------------------");
        }
        if (tgs == null) {
            throw new Exception("TGS is null");
        }
        if (!tgs.getClient().getName().equals(clientKDC2Name)) {
            throw new Exception("Unexpected TGS client");
        }
        if (!tgs.getServer().getNameString().equals(serviceName) ||
                !tgs.getServer().getRealmString().equals(realmKDC2)) {
            throw new Exception("Unexpected TGS server");
        }
    }
}
