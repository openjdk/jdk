/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8009977
 * @summary A test library to launch multiple Java processes
 * @library ../../../../java/security/testlibrary/
 * @compile -XDignore.symbol.file BasicProc.java
 * @run main/othervm BasicProc
 */

import java.io.File;
import org.ietf.jgss.Oid;

import javax.security.auth.PrivateCredentialPermission;

public class BasicProc {

    static String CONF = "krb5.conf";
    static String KTAB = "ktab";
    public static void main(String[] args) throws Exception {
        String HOST = "localhost";
        String SERVER = "server/" + HOST;
        String BACKEND = "backend/" + HOST;
        String USER = "user";
        char[] PASS = "password".toCharArray();
        String REALM = "REALM";

        Oid oid = new Oid("1.2.840.113554.1.2.2");

        if (args.length == 0) {
            System.setProperty("java.security.krb5.conf", CONF);
            KDC kdc = KDC.create(REALM, HOST, 0, true);
            kdc.addPrincipal(USER, PASS);
            kdc.addPrincipalRandKey("krbtgt/" + REALM);
            kdc.addPrincipalRandKey(SERVER);
            kdc.addPrincipalRandKey(BACKEND);

            String cwd = System.getProperty("user.dir");
            kdc.writeKtab(KTAB);
            KDC.saveConfig(CONF, kdc, "forwardable = true");

            Proc pc = Proc.create("BasicProc")
                    .args("client")
                    .prop("java.security.krb5.conf", CONF)
                    .prop("java.security.manager", "")
                    .perm(new java.util.PropertyPermission(
                            "sun.security.krb5.principal", "read"))
                    .perm(new javax.security.auth.AuthPermission(
                            "modifyPrincipals"))
                    .perm(new javax.security.auth.AuthPermission(
                            "modifyPrivateCredentials"))
                    .perm(new javax.security.auth.AuthPermission("doAs"))
                    .perm(new javax.security.auth.kerberos.ServicePermission(
                            "krbtgt/" + REALM + "@" + REALM, "initiate"))
                    .perm(new javax.security.auth.kerberos.ServicePermission(
                            "server/localhost@" + REALM, "initiate"))
                    .perm(new javax.security.auth.kerberos.DelegationPermission(
                            "\"server/localhost@" + REALM + "\" " +
                                    "\"krbtgt/" + REALM + "@" + REALM + "\""))
                    .debug("C")
                    .start();
            Proc ps = Proc.create("BasicProc")
                    .args("server")
                    .prop("java.security.krb5.conf", CONF)
                    .prop("java.security.manager", "")
                    .perm(new java.util.PropertyPermission(
                            "sun.security.krb5.principal", "read"))
                    .perm(new javax.security.auth.AuthPermission(
                            "modifyPrincipals"))
                    .perm(new javax.security.auth.AuthPermission(
                            "modifyPrivateCredentials"))
                    .perm(new javax.security.auth.AuthPermission("doAs"))
                    .perm(new PrivateCredentialPermission(
                            "javax.security.auth.kerberos.KeyTab * \"*\"",
                            "read"))
                    .perm(new javax.security.auth.kerberos.ServicePermission(
                            "server/localhost@" + REALM, "accept"))
                    .perm(new java.io.FilePermission(
                            cwd + File.separator + KTAB, "read"))
                    .perm(new javax.security.auth.kerberos.ServicePermission(
                            "backend/localhost@" + REALM, "initiate"))
                    .debug("S")
                    .start();
            Proc pb = Proc.create("BasicProc")
                    .args("backend")
                    .prop("java.security.krb5.conf", CONF)
                    .prop("java.security.manager", "")
                    .perm(new java.util.PropertyPermission(
                            "sun.security.krb5.principal", "read"))
                    .perm(new javax.security.auth.AuthPermission(
                            "modifyPrincipals"))
                    .perm(new javax.security.auth.AuthPermission(
                            "modifyPrivateCredentials"))
                    .perm(new javax.security.auth.AuthPermission("doAs"))
                    .perm(new PrivateCredentialPermission(
                            "javax.security.auth.kerberos.KeyTab * \"*\"",
                            "read"))
                    .perm(new javax.security.auth.kerberos.ServicePermission(
                            "backend/localhost@" + REALM, "accept"))
                    .perm(new java.io.FilePermission(
                            cwd + File.separator + KTAB, "read"))
                    .debug("B")
                    .start();

            // Client and server handshake
            String token = pc.readData();
            ps.println(token);
            token = ps.readData();
            pc.println(token);
            // Server and backend handshake
            token = ps.readData();
            pb.println(token);
            token = pb.readData();
            ps.println(token);
            // wrap/unwrap/getMic/verifyMic and plain text
            token = ps.readData();
            pb.println(token);
            token = pb.readData();
            ps.println(token);
            token = pb.readData();
            ps.println(token);

            if ((pc.waitFor() | ps.waitFor() | pb.waitFor()) != 0) {
                throw new Exception();
            }
        } else if (args[0].equals("client")) {
            Context c = Context.fromUserPass(USER, PASS, false);
            c.startAsClient(SERVER, oid);
            c.x().requestCredDeleg(true);
            Proc.binOut(c.take(new byte[0]));
            byte[] token = Proc.binIn();
            c.take(token);
        } else if (args[0].equals("server")) {
            Context s = Context.fromUserKtab(SERVER, KTAB, true);
            s.startAsServer(oid);
            byte[] token = Proc.binIn();
            token = s.take(token);
            Proc.binOut(token);
            Context s2 = s.delegated();
            s2.startAsClient(BACKEND, oid);
            Proc.binOut(s2.take(new byte[0]));
            token = Proc.binIn();
            s2.take(token);
            byte[] msg = "Hello".getBytes();
            Proc.binOut(s2.wrap(msg, true));
            s2.verifyMic(Proc.binIn(), msg);
            String in = Proc.textIn();
            if (!in.equals("Hello")) {
                throw new Exception();
            }
        } else if (args[0].equals("backend")) {
            Context b = Context.fromUserKtab(BACKEND, KTAB, true);
            b.startAsServer(oid);
            byte[] token = Proc.binIn();
            Proc.binOut(b.take(token));
            byte[] msg = b.unwrap(Proc.binIn(), true);
            Proc.binOut(b.getMic(msg));
            Proc.textOut(new String(msg));
        }
    }
    // create a native server
    private static Proc ns(Proc p) throws Exception {
        return p
            .env("KRB5_CONFIG", CONF)
            .env("KRB5_KTNAME", KTAB)
            .prop("sun.security.jgss.native", "true")
            .prop("javax.security.auth.useSubjectCredsOnly", "false")
            .prop("sun.security.nativegss.debug", "true");
    }
}
