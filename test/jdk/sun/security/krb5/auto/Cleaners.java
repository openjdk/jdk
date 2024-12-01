/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8284490
 * @summary Remove finalizer method in java.security.jgss
 * @key intermittent
 * @requires os.family != "windows"
 * @library /test/lib
 * @compile -XDignore.symbol.file Cleaners.java
 * @run main/othervm Cleaners launcher
 */

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Set;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.Proc;
import org.ietf.jgss.Oid;
import sun.security.krb5.Config;

public class Cleaners {

    private static final String CONF = "krb5.conf";
    private static final String KTAB_S = "server.ktab";
    private static final String KTAB_B = "backend.ktab";

    private static final String HOST = "localhost";
    private static final String SERVER = "server/" + HOST;
    private static final String BACKEND = "backend/" + HOST;
    private static final String USER = "user";
    private static final char[] PASS = "password".toCharArray();
    private static final String REALM = "REALM";

    private static final byte[] MSG = "12345678".repeat(128)
            .getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {

        Oid oid = new Oid("1.2.840.113554.1.2.2");
        byte[] token, msg;

        switch (args[0]) {
            case "launcher" -> {
                KDC kdc = KDC.create(REALM, HOST, 0, true);
                kdc.addPrincipal(USER, PASS);
                kdc.addPrincipalRandKey("krbtgt/" + REALM);
                kdc.addPrincipalRandKey(SERVER);
                kdc.addPrincipalRandKey(BACKEND);

                // Native lib might do some name lookup
                KDC.saveConfig(CONF, kdc,
                        "dns_lookup_kdc = no",
                        "ticket_lifetime = 1h",
                        "dns_lookup_realm = no",
                        "dns_canonicalize_hostname = false",
                        "forwardable = true");
                System.setProperty("java.security.krb5.conf", CONF);
                Config.refresh();

                // Create kaytab and ccache files for native clients
                kdc.writeKtab(KTAB_S, false, SERVER);
                kdc.writeKtab(KTAB_B, false, BACKEND);
                kdc.kinit(USER, "ccache");
                Files.setPosixFilePermissions(Paths.get("ccache"),
                        Set.of(PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE));

                Proc pc = proc("client")
                        .env("KRB5CCNAME", "FILE:ccache")
                        .env("KRB5_KTNAME", "none") // Do not try system ktab if ccache fails
                        .start();
                Proc ps = proc("server")
                        .env("KRB5_KTNAME", KTAB_S)
                        .start();
                Proc pb = proc("backend")
                        .env("KRB5_KTNAME", KTAB_B)
                        .start();

                // Client and server
                ps.println(pc.readData()); // AP-REQ
                pc.println(ps.readData()); // AP-REP, mutual auth
                ps.println(pc.readData()); // wrap msg
                ps.println(pc.readData()); // mic msg

                // Server and backend
                pb.println(ps.readData()); // AP-REQ
                ps.println(pb.readData()); // wrap msg
                ps.println(pb.readData()); // mic msg

                ensureCleanersCalled(pc);
                ensureCleanersCalled(ps);
                ensureCleanersCalled(pb);
            }
            case "client" -> {
                Context c = Context.fromThinAir();
                c.startAsClient(SERVER, oid);
                c.x().requestCredDeleg(true);
                c.x().requestMutualAuth(true);
                Proc.binOut(c.take(new byte[0])); // AP-REQ
                c.take(Proc.binIn()); // AP-REP
                Proc.binOut(c.wrap(MSG, true));
                Proc.binOut(c.getMic(MSG));
            }
            case "server" -> {
                Context s = Context.fromThinAir();
                s.startAsServer(oid);
                token = Proc.binIn(); // AP-REQ
                Proc.binOut(s.take(token)); // AP-REP
                msg = s.unwrap(Proc.binIn(), true);
                Asserts.assertTrue(Arrays.equals(msg, MSG));
                s.verifyMic(Proc.binIn(), msg);
                Context s2 = s.delegated();
                s2.startAsClient(BACKEND, oid);
                s2.x().requestMutualAuth(false);
                Proc.binOut(s2.take(new byte[0])); // AP-REQ
                msg = s2.unwrap(Proc.binIn(), true);
                Asserts.assertTrue(Arrays.equals(msg, MSG));
                s2.verifyMic(Proc.binIn(), msg);
            }
            case "backend" -> {
                Context b = Context.fromThinAir();
                b.startAsServer(oid);
                token = b.take(Proc.binIn()); // AP-REQ
                Asserts.assertTrue(token == null);
                Proc.binOut(b.wrap(MSG, true));
                Proc.binOut(b.getMic(MSG));
            }
        }
        System.err.println("Prepare for GC");
        for (int i = 0; i < 10; i++) {
            System.gc();
            Thread.sleep(100);
        }
    }

    private static void ensureCleanersCalled(Proc p) throws Exception {
        p.output()
                .shouldHaveExitValue(0)
                .stderrShouldMatch("Prepare for GC(.|\\n)*GSSLibStub_deleteContext")
                .stderrShouldMatch("Prepare for GC(.|\\n)*GSSLibStub_releaseName")
                .stderrShouldMatch("Prepare for GC(.|\\n)*GSSLibStub_releaseCred");
    }

    private static Proc proc(String type) throws Exception {
        return Proc.create("Cleaners")
                .args(type)
                .debug(type)
                .env("KRB5_CONFIG", CONF)
                .env("KRB5_TRACE", "/dev/stderr")
                .prop("sun.security.jgss.native", "true")
                .prop("javax.security.auth.useSubjectCredsOnly", "false")
                .prop("sun.security.nativegss.debug", "true");
    }
}
