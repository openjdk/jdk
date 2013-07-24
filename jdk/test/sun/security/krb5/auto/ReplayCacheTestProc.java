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
 * @bug 7152176
 * @summary More krb5 tests
 * @library ../../../../java/security/testlibrary/
 * @compile -XDignore.symbol.file ReplayCacheTestProc.java
 * @run main/othervm/timeout=100 ReplayCacheTestProc
 */

import java.io.*;
import java.nio.BufferUnderflowException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.*;
import sun.security.jgss.GSSUtil;
import sun.security.krb5.internal.APReq;
import sun.security.krb5.internal.rcache.AuthTime;

// This test runs multiple acceptor Procs to mimin AP-REQ replays.
public class ReplayCacheTestProc {

    private static Proc[] ps;
    private static Proc pc;
    private static List<Req> reqs = new ArrayList<>();
    private static String HOST = "localhost";

    // Where should the rcache be saved. It seems KRB5RCACHEDIR is not
    // recognized on Solaris. Maybe version too low? I see 1.6.
    private static String cwd =
            System.getProperty("os.name").startsWith("SunOS") ?
                "/var/krb5/rcache/" :
                System.getProperty("user.dir");


    private static int uid;

    public static void main0(String[] args) throws Exception {
        System.setProperty("java.security.krb5.conf", OneKDC.KRB5_CONF);
        if (args.length == 0) { // The controller
            int ns = 5;     // number of servers
            int nu = 5;     // number of users
            int nx = 50;    // number of experiments
            int np = 5;     // number of peers (services)
            int mode = 0;   // native(1), random(0), java(-1)
            boolean random = true;      // random experiments choreograph

            // Do not test interop with native GSS on some platforms
            String os = System.getProperty("os.name", "???");
            if (!os.startsWith("SunOS") && !os.startsWith("Linux")) {
                mode = -1;
            }

            try {
                Class<?> clazz = Class.forName(
                        "com.sun.security.auth.module.UnixSystem");
                uid = (int)(long)(Long)
                        clazz.getMethod("getUid").invoke(clazz.newInstance());
            } catch (Exception e) {
                uid = -1;
            }

            KDC kdc = KDC.create(OneKDC.REALM, HOST, 0, true);
            for (int i=0; i<nu; i++) {
                kdc.addPrincipal(user(i), OneKDC.PASS);
            }
            kdc.addPrincipalRandKey("krbtgt/" + OneKDC.REALM);
            for (int i=0; i<np; i++) {
                kdc.addPrincipalRandKey(peer(i));
            }

            kdc.writeKtab(OneKDC.KTAB);
            KDC.saveConfig(OneKDC.KRB5_CONF, kdc);

            pc = Proc.create("ReplayCacheTestProc").debug("C")
                    .args("client")
                    .start();
            ps = new Proc[ns];
            Ex[] result = new Ex[nx];

            if (!random) {
                // 2 experiments, 2 server, 1 peer, 1 user
                nx = 2; ns = 2; np = 1; nu = 1;

                // Creates reqs from user# to peer#
                req(0, 0);

                // Creates server#
                ps[0] = ns(0);
                ps[1] = js(1);

                // Runs ex# using req# to server# with expected result
                result[0] = round(0, 0, 0, true);
                result[1] = round(1, 0, 1, false);
            } else {
                Random r = new Random();
                for (int i=0; i<ns; i++) {
                    boolean useNative = (mode == 1) ? true
                            : (mode == -1 ? false : r.nextBoolean());
                    ps[i] = useNative?ns(i):js(i);
                }
                for (int i=0; i<nx; i++) {
                    result[i] = new Ex();
                    int old;    // which req to send
                    boolean expected;
                    if (reqs.isEmpty() || r.nextBoolean()) {
                        Proc.d("Console get new AP-REQ");
                        old = req(r.nextInt(nu), r.nextInt(np));
                        expected = true;
                    } else {
                        Proc.d("Console resue old");
                        old = r.nextInt(reqs.size());
                        expected = false;
                    }
                    int s = r.nextInt(ns);
                    Proc.d("Console send to " + s);
                    result[i] = round(i, old, s, expected);
                    Proc.d("Console sees " + result[i].actual);
                }
            }

            pc.println("END");
            for (int i=0; i<ns; i++) {
                ps[i].println("END");
            }
            System.out.println("Result\n======");
            boolean finalOut = true;
            for (int i=0; i<nx; i++) {
                boolean out = result[i].expected==result[i].actual;
                finalOut &= out;
                System.out.printf("%3d: %s (%2d): u%d h%d %s %s   %s %2d\n",
                        i,
                        result[i].expected?"----":"    ",
                        result[i].old,
                        result[i].user, result[i].peer, result[i].server,
                        result[i].actual?"Good":"Bad ",
                        out?"   ":"xxx",
                        result[i].csize);
            }
            if (!finalOut) throw new Exception();
        } else if (args[0].equals("client")) {
            while (true) {
                String title = Proc.textIn();
                Proc.d("Client see " + title);
                if (title.equals("END")) break;
                String[] cas = title.split(" ");
                Context c = Context.fromUserPass(cas[0], OneKDC.PASS, false);
                c.startAsClient(cas[1], GSSUtil.GSS_KRB5_MECH_OID);
                c.x().requestCredDeleg(true);
                byte[] token = c.take(new byte[0]);
                Proc.d("Client AP-REQ generated");
                Proc.binOut(token);
            }
        } else {
            Proc.d("Server start");
            Context s = Context.fromUserKtab("*", OneKDC.KTAB, true);
            Proc.d("Server login");
            while (true) {
                String title = Proc.textIn();
                Proc.d("Server " + args[0] + " sees " + title);
                if (title.equals("END")) break;
                s.startAsServer(GSSUtil.GSS_KRB5_MECH_OID);
                byte[] token = Proc.binIn();
                try {
                    s.take(token);
                    Proc.textOut("true");
                    Proc.d(args[0] + " Good");
                } catch (Exception e) {
                    Proc.textOut("false");
                    Proc.d(args[0] + " Bad");
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            main0(args);
        } catch (Exception e) {
            Proc.d(e);
            throw e;
        }
    }

    // returns the user name
    private static String user(int p) {
        return "USER" + p;
    }
    // returns the peer name
    private static String peer(int p) {
        return "host" + p + "/" + HOST;
    }
    // returns the dfl name for a host
    private static String dfl(int p) {
        return cwd + "host" + p + (uid == -1 ? "" : ("_"+uid));
    }
    // generates an ap-req and save into reqs, returns the index
    private static int req(int user, int peer) throws Exception {
        pc.println(user(user) + " " + peer(peer));
        Req req = new Req(user, peer, pc.readData());
        reqs.add(req);
        return reqs.size() - 1;
    }
    // carries out a round of experiment
    // i: ex#, old: which req, server: which server, expected: result?
    private static Ex round(int i, int old, int server, boolean expected)
            throws Exception {
        ps[server].println("TEST");
        ps[server].println(reqs.get(old).msg);
        String reply = ps[server].readData();
        Ex result = new Ex();
        result.i = i;
        result.expected = expected;
        result.server = ps[server].debug();
        result.actual = Boolean.valueOf(reply);
        result.user = reqs.get(old).user;
        result.peer = reqs.get(old).peer;
        result.old = old;
        result.csize = csize(result.peer);
        result.hash = hash(reqs.get(old).msg);
        if (new File(dfl(result.peer)).exists()) {
            Files.copy(Paths.get(dfl(result.peer)), Paths.get(
                String.format("%03d-USER%d-host%d-%s-%s",
                    i, result.user, result.peer, result.server,
                    result.actual)
                + "-" + result.hash),
                StandardCopyOption.COPY_ATTRIBUTES);
        }
        return result;
    }
    // create a native server
    private static Proc ns(int i) throws Exception {
        return Proc.create("ReplayCacheTestProc")
                .args("N"+i)
                .env("KRB5_CONFIG", OneKDC.KRB5_CONF)
                .env("KRB5_KTNAME", OneKDC.KTAB)
                .env("KRB5RCACHEDIR", cwd)
                .prop("sun.security.jgss.native", "true")
                .prop("javax.security.auth.useSubjectCredsOnly", "false")
                .prop("sun.security.nativegss.debug", "true")
                .debug("N"+i)
                .start();
    }
    // creates a java server
    private static Proc js(int i) throws Exception {
        return Proc.create("ReplayCacheTestProc")
                .debug("S"+i)
                .args("S"+i)
                .prop("sun.security.krb5.rcache", "dfl")
                .prop("java.io.tmpdir", cwd)
                .start();
    }
    // generates hash of authenticator inside ap-req inside initsectoken
    private static String hash(String req) throws Exception {
        byte[] data = Base64.getDecoder().decode(req);
        data = Arrays.copyOfRange(data, 17, data.length);
        byte[] hash = MessageDigest.getInstance("MD5").digest(new APReq(data).authenticator.getBytes());
        char[] h = new char[hash.length * 2];
        char[] hexConst = "0123456789ABCDEF".toCharArray();
        for (int i=0; i<hash.length; i++) {
            h[2*i] = hexConst[(hash[i]&0xff)>>4];
            h[2*i+1] = hexConst[hash[i]&0xf];
        }
        return new String(h);
    }
    // return size of dfl file, excluding the null hash ones
    private static int csize(int p) throws Exception {
        try (SeekableByteChannel chan = Files.newByteChannel(
                Paths.get(dfl(p)), StandardOpenOption.READ)) {
            chan.position(6);
            int cc = 0;
            while (true) {
                try {
                    if (AuthTime.readFrom(chan) != null) cc++;
                } catch (BufferUnderflowException e) {
                    break;
                }
            }
            return cc;
        } catch (IOException ioe) {
            return 0;
        }
    }
    // models an experiement
    private static class Ex {
        int i;              // #
        boolean expected;   // expected result
        boolean actual;     // actual output
        int old;            // which ap-req to send
        String server;      // which server to send to
        String hash;        // the hash of req
        int user;           // which initiator
        int peer;           // which acceptor
        int csize;          // size of rcache after test
    }
    // models a saved ap-req msg
    private static class Req {
        String msg;         // based64-ed req
        int user;           // which initiator
        int peer;           // which accceptor
        Req(int user, int peer, String msg) {
            this.msg = msg;
            this.user= user;
            this.peer = peer;
        }
    }
}
