/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8279842 8282293
 * @modules java.base/sun.security.util
 *          java.security.jgss/sun.security.jgss
 *          java.security.jgss/sun.security.jgss.krb5
 *          java.security.jgss/sun.security.jgss.krb5.internal
 *          java.security.jgss/sun.security.krb5.internal:+open
 *          java.security.jgss/sun.security.krb5:+open
 *          java.security.jgss/sun.security.krb5.internal.ccache
 *          java.security.jgss/sun.security.krb5.internal.crypto
 *          java.security.jgss/sun.security.krb5.internal.ktab
 *          jdk.security.auth
 *          jdk.security.jgss
 *          jdk.httpserver
 * @summary HTTPS Channel Binding support for Java GSS/Kerberos
 * @library /test/lib
 * @run main jdk.test.lib.FileInstaller TestHosts TestHosts
 * @run main/othervm -Djdk.net.hosts.file=TestHosts
 *          -Djdk.https.negotiate.cbt=always HttpsCB true true
 * @run main/othervm -Djdk.net.hosts.file=TestHosts
 *          -Djdk.https.negotiate.cbt=never HttpsCB false true
 * @run main/othervm -Djdk.net.hosts.file=TestHosts
 *          -Djdk.https.negotiate.cbt=invalid HttpsCB false true
 * @run main/othervm -Djdk.net.hosts.file=TestHosts
 *          HttpsCB false true
 * @run main/othervm -Djdk.net.hosts.file=TestHosts
 *          -Djdk.https.negotiate.cbt=domain:other.com HttpsCB false true
 * @run main/othervm -Djdk.net.hosts.file=TestHosts
 *          -Djdk.https.negotiate.cbt=domain:host.web.domain HttpsCB true true
 * @run main/othervm -Djdk.net.hosts.file=TestHosts
 *          -Djdk.https.negotiate.cbt=domain:HOST.WEB.DOMAIN HttpsCB true true
 * @run main/othervm -Djdk.net.hosts.file=TestHosts
 *          -Djdk.https.negotiate.cbt=domain:*.web.domain HttpsCB true true
 * @run main/othervm -Djdk.net.hosts.file=TestHosts
 *          -Djdk.https.negotiate.cbt=domain:*.WEB.Domain HttpsCB true true
 * @run main/othervm -Djdk.net.hosts.file=TestHosts
 *          -Djdk.https.negotiate.cbt=domain:*.Invalid,*.WEB.Domain HttpsCB true true
 */

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsServer;
import com.sun.security.auth.module.Krb5LoginModule;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.security.auth.Subject;

import jdk.test.lib.Asserts;
import jdk.test.lib.net.SimpleSSLContext;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import sun.security.jgss.GSSUtil;
import sun.security.jgss.krb5.internal.TlsChannelBindingImpl;
import sun.security.krb5.Config;
import sun.security.util.TlsChannelBinding;

import java.util.Base64;
import java.util.concurrent.Callable;

public class HttpsCB {

    final static String REALM_WEB = "WEB.DOMAIN";
    final static String KRB5_CONF = "web.conf";
    final static String KRB5_TAB = "web.ktab";

    final static String WEB_USER = "web";
    final static char[] WEB_PASS = "webby".toCharArray();
    final static String WEB_HOST = "host.web.domain";
    final static String CONTENT = "Hello, World!";

    static int webPort;
    static URL cbtURL;
    static URL normalURL;

    public static void main(String[] args)
            throws Exception {

        boolean expectCBT = Boolean.parseBoolean(args[0]);
        boolean expectNoCBT = Boolean.parseBoolean(args[1]);

        System.setProperty("sun.security.krb5.debug", "true");

        KDC kdcw = KDC.create(REALM_WEB);
        kdcw.addPrincipal(WEB_USER, WEB_PASS);
        kdcw.addPrincipalRandKey("krbtgt/" + REALM_WEB);
        kdcw.addPrincipalRandKey("HTTP/" + WEB_HOST);

        KDC.saveConfig(KRB5_CONF, kdcw,
                "default_keytab_name = " + KRB5_TAB,
                "[domain_realm]",
                "",
                ".web.domain="+REALM_WEB);

        System.setProperty("java.security.krb5.conf", KRB5_CONF);
        Config.refresh();
        KDC.writeMultiKtab(KRB5_TAB, kdcw);

        // Write a customized JAAS conf file, so that any kinit cache
        // will be ignored.
        System.setProperty("java.security.auth.login.config", OneKDC.JAAS_CONF);
        File f = new File(OneKDC.JAAS_CONF);
        FileOutputStream fos = new FileOutputStream(f);
        fos.write((
                "com.sun.security.jgss.krb5.initiate {\n" +
                "    com.sun.security.auth.module.Krb5LoginModule required;\n};\n"
                ).getBytes());
        fos.close();

        HttpServer h1 = httpd("Negotiate",
                "HTTP/" + WEB_HOST + "@" + REALM_WEB, KRB5_TAB);
        webPort = h1.getAddress().getPort();

        cbtURL = new URL("https://" + WEB_HOST +":" + webPort + "/cbt");
        normalURL = new URL("https://" + WEB_HOST +":" + webPort + "/normal");

        java.net.Authenticator.setDefault(new java.net.Authenticator() {
            public PasswordAuthentication getPasswordAuthentication () {
                return new PasswordAuthentication(
                        WEB_USER+"@"+REALM_WEB, WEB_PASS);
            }
        });

        // Client-side SSLContext needs to ignore hostname mismatch
        // and untrusted certificate.
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, new TrustManager[] {
                new X509ExtendedTrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(X509Certificate[] chain,
                            String authType, Socket socket) { }
                    public void checkServerTrusted(X509Certificate[] chain,
                            String authType, Socket socket) { }
                    public void checkClientTrusted(X509Certificate[] chain,
                            String authType, SSLEngine engine) { }
                    public void checkServerTrusted(X509Certificate[] chain,
                            String authType, SSLEngine engine) { }
                    public void checkClientTrusted(X509Certificate[] certs,
                            String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs,
                            String authType) { }
                }
        }, null);

        Asserts.assertEQ(visit(sc, cbtURL), expectCBT);
        Asserts.assertEQ(visit(sc, normalURL), expectNoCBT);
    }

    static boolean visit(SSLContext sc, URL url) {
        try {
            HttpsURLConnection conn = (HttpsURLConnection)
                    url.openConnection(Proxy.NO_PROXY);
            conn.setSSLSocketFactory(sc.getSocketFactory());
            BufferedReader reader;
            reader = new BufferedReader(new InputStreamReader(
                    conn.getInputStream()));
            return reader.readLine().equals(CONTENT);
        } catch (IOException e) {
            e.printStackTrace(System.out);
            return false;
        }
    }

    static HttpServer httpd(String scheme, String principal, String ktab)
            throws Exception {
        MyHttpHandler h = new MyHttpHandler();
        HttpsServer server = HttpsServer.create(new InetSocketAddress(0), 0);
        server.setHttpsConfigurator(
                new HttpsConfigurator(new SimpleSSLContext().get()));
        server.createContext("/", h).setAuthenticator(
                new MyServerAuthenticator(scheme, principal, ktab));
        server.start();
        return server;
    }

    static class MyHttpHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            t.sendResponseHeaders(200, 0);
            t.getResponseBody().write(CONTENT.getBytes());
            t.close();
        }
    }

    static class MyServerAuthenticator
            extends com.sun.net.httpserver.Authenticator {
        Subject s = new Subject();
        GSSManager m;
        GSSCredential cred;
        String scheme = null;
        String reqHdr = "WWW-Authenticate";
        String respHdr = "Authorization";
        int err = HttpURLConnection.HTTP_UNAUTHORIZED;

        public MyServerAuthenticator(String scheme,
                String principal, String ktab) throws Exception {

            this.scheme = scheme;
            Krb5LoginModule krb5 = new Krb5LoginModule();
            Map<String, String> map = new HashMap<>();
            Map<String, Object> shared = new HashMap<>();

            map.put("storeKey", "true");
            map.put("isInitiator", "false");
            map.put("useKeyTab", "true");
            map.put("keyTab", ktab);
            map.put("principal", principal);
            krb5.initialize(s, null, shared, map);
            krb5.login();
            krb5.commit();
            m = GSSManager.getInstance();
            cred = Subject.callAs(s, new Callable<GSSCredential>() {
                @Override
                public GSSCredential call() throws Exception {
                    System.err.println("Creating GSSCredential");
                    return m.createCredential(
                            null,
                            GSSCredential.INDEFINITE_LIFETIME,
                            MyServerAuthenticator.this.scheme
                                        .equalsIgnoreCase("Negotiate") ?
                                    GSSUtil.GSS_SPNEGO_MECH_OID :
                                    GSSUtil.GSS_KRB5_MECH_OID,
                            GSSCredential.ACCEPT_ONLY);
                }
            });
        }

        @Override
        public Result authenticate(HttpExchange exch) {
            // The GSContext is stored in an HttpContext attribute named
            // "GSSContext" and is created at the first request.
            GSSContext c = null;
            String auth = exch.getRequestHeaders().getFirst(respHdr);
            try {
                c = (GSSContext)exch.getHttpContext()
                        .getAttributes().get("GSSContext");
                if (auth == null) {                 // First request
                    Headers map = exch.getResponseHeaders();
                    map.set (reqHdr, scheme);        // Challenge!
                    c = Subject.callAs(s, () -> m.createContext(cred));
                    // CBT is required for cbtURL
                    if (exch instanceof HttpsExchange sexch
                            && exch.getRequestURI().toString().equals("/cbt")) {
                        TlsChannelBinding b = TlsChannelBinding.create(
                                (X509Certificate) sexch.getSSLSession()
                                        .getLocalCertificates()[0]);
                        c.setChannelBinding(
                                new TlsChannelBindingImpl(b.getData()));
                    }
                    exch.getHttpContext().getAttributes().put("GSSContext", c);
                    return new com.sun.net.httpserver.Authenticator.Retry(err);
                } else {                            // Later requests
                    byte[] token = Base64.getMimeDecoder()
                            .decode(auth.split(" ")[1]);
                    token = c.acceptSecContext(token, 0, token.length);
                    Headers map = exch.getResponseHeaders();
                    map.set (reqHdr, scheme + " " + Base64.getMimeEncoder()
                            .encodeToString(token).replaceAll("\\s", ""));
                    if (c.isEstablished()) {
                        return new com.sun.net.httpserver.Authenticator.Success(
                                new HttpPrincipal(c.getSrcName().toString(), ""));
                    } else {
                        return new com.sun.net.httpserver.Authenticator.Retry(err);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
