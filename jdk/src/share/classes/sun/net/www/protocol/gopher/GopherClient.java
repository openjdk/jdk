/*
 * Copyright (c) 1996, 2004, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package sun.net.www.protocol.gopher;

import java.io.*;
import java.util.*;
import java.net.*;
import sun.net.www.*;
import sun.net.NetworkClient;
import java.net.URL;
import java.net.URLStreamHandler;

import sun.security.action.GetBooleanAction;

/** Class to maintain the state of a gopher fetch and handle the protocol */
public class GopherClient extends NetworkClient implements Runnable {

    /* The following three data members are left in for binary
     * backwards-compatibility.  Unfortunately, HotJava sets them directly
     * when it wants to change the settings.  The new design has us not
     * cache these, so this is unnecessary, but eliminating the data members
     * would break HJB 1.1 under JDK 1.2.
     *
     * These data members are not used, and their values are meaningless.
     * REMIND:  Take them out for JDK 2.0!
     */

    /**
     * @deprecated
     */
    @Deprecated
    public static boolean       useGopherProxy;

    /**
     * @deprecated
     */
    @Deprecated
    public static String        gopherProxyHost;

    /**
     * @deprecated
     */
    @Deprecated
    public static int           gopherProxyPort;


    static {
        useGopherProxy = java.security.AccessController.doPrivileged(
            new sun.security.action.GetBooleanAction("gopherProxySet"))
            .booleanValue();

        gopherProxyHost = java.security.AccessController.doPrivileged(
            new sun.security.action.GetPropertyAction("gopherProxyHost"));

        gopherProxyPort = java.security.AccessController.doPrivileged(
            new sun.security.action.GetIntegerAction("gopherProxyPort", 80))
            .intValue();
    }

    PipedOutputStream os;
    URL u;
    int gtype;
    String gkey;
    sun.net.www.URLConnection connection;

    GopherClient(sun.net.www.URLConnection connection) {
        this.connection = connection;
    }

    /**
     * @return true if gopher connections should go through a proxy, according
     *          to system properties.
     */
    public static boolean getUseGopherProxy() {
        return java.security.AccessController.doPrivileged(
            new GetBooleanAction("gopherProxySet")).booleanValue();
    }

    /**
     * @return the proxy host to use, or null if nothing is set.
     */
    public static String getGopherProxyHost() {
        String host = java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction("gopherProxyHost"));
        if ("".equals(host)) {
            host = null;
        }
        return host;
    }

    /**
     * @return the proxy port to use.  Will default reasonably.
     */
    public static int getGopherProxyPort() {
        return java.security.AccessController.doPrivileged(
            new sun.security.action.GetIntegerAction("gopherProxyPort", 80))
            .intValue();
    }

    /** Given a url, setup to fetch the gopher document it refers to */
    InputStream openStream(URL u) throws IOException {
        this.u = u;
        this.os = os;
        int i = 0;
        String s = u.getFile();
        int limit = s.length();
        int c = '1';
        while (i < limit && (c = s.charAt(i)) == '/')
            i++;
        gtype = c == '/' ? '1' : c;
        if (i < limit)
            i++;
        gkey = s.substring(i);

        openServer(u.getHost(), u.getPort() <= 0 ? 70 : u.getPort());

        MessageHeader msgh = new MessageHeader();

        switch (gtype) {
          case '0':
          case '7':
            msgh.add("content-type", "text/plain");
            break;
          case '1':
            msgh.add("content-type", "text/html");
            break;
          case 'g':
          case 'I':
            msgh.add("content-type", "image/gif");
            break;
          default:
            msgh.add("content-type", "content/unknown");
            break;
        }
        if (gtype != '7') {
            serverOutput.print(decodePercent(gkey) + "\r\n");
            serverOutput.flush();
        } else if ((i = gkey.indexOf('?')) >= 0) {
            serverOutput.print(decodePercent(gkey.substring(0, i) + "\t" +
                                           gkey.substring(i + 1) + "\r\n"));
            serverOutput.flush();
            msgh.add("content-type", "text/html");
        } else {
            msgh.add("content-type", "text/html");
        }
        connection.setProperties(msgh);
        if (msgh.findValue("content-type") == "text/html") {
            os = new PipedOutputStream();
            PipedInputStream ret = new PipedInputStream();
            ret.connect(os);
            new Thread(this).start();
            return ret;
        }
        return new GopherInputStream(this, serverInput);
    }

    /** Translate all the instances of %NN into the character they represent */
    private String decodePercent(String s) {
        if (s == null || s.indexOf('%') < 0)
            return s;
        int limit = s.length();
        char d[] = new char[limit];
        int dp = 0;
        for (int sp = 0; sp < limit; sp++) {
            int c = s.charAt(sp);
            if (c == '%' && sp + 2 < limit) {
                int s1 = s.charAt(sp + 1);
                int s2 = s.charAt(sp + 2);
                if ('0' <= s1 && s1 <= '9')
                    s1 = s1 - '0';
                else if ('a' <= s1 && s1 <= 'f')
                    s1 = s1 - 'a' + 10;
                else if ('A' <= s1 && s1 <= 'F')
                    s1 = s1 - 'A' + 10;
                else
                    s1 = -1;
                if ('0' <= s2 && s2 <= '9')
                    s2 = s2 - '0';
                else if ('a' <= s2 && s2 <= 'f')
                    s2 = s2 - 'a' + 10;
                else if ('A' <= s2 && s2 <= 'F')
                    s2 = s2 - 'A' + 10;
                else
                    s2 = -1;
                if (s1 >= 0 && s2 >= 0) {
                    c = (s1 << 4) | s2;
                    sp += 2;
                }
            }
            d[dp++] = (char) c;
        }
        return new String(d, 0, dp);
    }

    /** Turn special characters into the %NN form */
    private String encodePercent(String s) {
        if (s == null)
            return s;
        int limit = s.length();
        char d[] = null;
        int dp = 0;
        for (int sp = 0; sp < limit; sp++) {
            int c = s.charAt(sp);
            if (c <= ' ' || c == '"' || c == '%') {
                if (d == null)
                    d = s.toCharArray();
                if (dp + 3 >= d.length) {
                    char nd[] = new char[dp + 10];
                    System.arraycopy(d, 0, nd, 0, dp);
                    d = nd;
                }
                d[dp] = '%';
                int dig = (c >> 4) & 0xF;
                d[dp + 1] = (char) (dig < 10 ? '0' + dig : 'A' - 10 + dig);
                dig = c & 0xF;
                d[dp + 2] = (char) (dig < 10 ? '0' + dig : 'A' - 10 + dig);
                dp += 3;
            } else {
                if (d != null) {
                    if (dp >= d.length) {
                        char nd[] = new char[dp + 10];
                        System.arraycopy(d, 0, nd, 0, dp);
                        d = nd;
                    }
                    d[dp] = (char) c;
                }
                dp++;
            }
        }
        return d == null ? s : new String(d, 0, dp);
    }

    /** This method is run as a seperate thread when an incoming gopher
        document requires translation to html */
    public void run() {
        int qpos = -1;
        try {
            if (gtype == '7' && (qpos = gkey.indexOf('?')) < 0) {
                PrintStream ps = new PrintStream(os, false, encoding);
                ps.print("<html><head><title>Searchable Gopher Index</title></head>\n<body><h1>Searchable Gopher Index</h1><isindex>\n</body></html>\n");
            } else if (gtype != '1' && gtype != '7') {
                byte buf[] = new byte[2048];
                try {
                    int n;
                    while ((n = serverInput.read(buf)) >= 0)
                            os.write(buf, 0, n);
                } catch(Exception e) {
                }
            } else {
                PrintStream ps = new PrintStream(os, false, encoding);
                String title = null;
                if (gtype == '7')
                    title = "Results of searching for \"" + gkey.substring(qpos + 1)
                        + "\" on " + u.getHost();
                else
                    title = "Gopher directory " + gkey + " from " + u.getHost();
                ps.print("<html><head><title>");
                ps.print(title);
                ps.print("</title></head>\n<body>\n<H1>");
                ps.print(title);
                ps.print("</h1><dl compact>\n");
                DataInputStream ds = new DataInputStream(serverInput);
                String s;
                while ((s = ds.readLine()) != null) {
                    int len = s.length();
                    while (len > 0 && s.charAt(len - 1) <= ' ')
                        len--;
                    if (len <= 0)
                        continue;
                    int key = s.charAt(0);
                    int t1 = s.indexOf('\t');
                    int t2 = t1 > 0 ? s.indexOf('\t', t1 + 1) : -1;
                    int t3 = t2 > 0 ? s.indexOf('\t', t2 + 1) : -1;
                    if (t3 < 0) {
                        // ps.print("<br><i>"+s+"</i>\n");
                        continue;
                    }
                    String port = t3 + 1 < len ? ":" + s.substring(t3 + 1, len) : "";
                    String host = t2 + 1 < t3 ? s.substring(t2 + 1, t3) : u.getHost();
                    ps.print("<dt><a href=\"gopher://" + host + port + "/"
                             + s.substring(0, 1) + encodePercent(s.substring(t1 + 1, t2)) + "\">\n");
                    ps.print("<img align=middle border=0 width=25 height=32 src=");
                    switch (key) {
                      default:
                        ps.print(System.getProperty("java.net.ftp.imagepath.file"));
                        break;
                      case '0':
                        ps.print(System.getProperty("java.net.ftp.imagepath.text"));
                        break;
                      case '1':
                        ps.print(System.getProperty("java.net.ftp.imagepath.directory"));
                        break;
                      case 'g':
                        ps.print(System.getProperty("java.net.ftp.imagepath.gif"));
                        break;
                    }
                    ps.print(".gif align=middle><dd>\n");
                    ps.print(s.substring(1, t1) + "</a>\n");
                }
                ps.print("</dl></body>\n");
                ps.close();
           }

       } catch (UnsupportedEncodingException e) {
            throw new InternalError(encoding+ " encoding not found");
       } catch (IOException e) {
       } finally {
           try {
               closeServer();
               os.close();
           } catch (IOException e2) {
           }
        }
    }
}

/** An input stream that does nothing more than hold on to the NetworkClient
    that created it.  This is used when only the input stream is needed, and
    the network client needs to be closed when the input stream is closed. */
class GopherInputStream extends FilterInputStream {
    NetworkClient parent;

    GopherInputStream(NetworkClient o, InputStream fd) {
        super(fd);
        parent = o;
    }

    public void close() {
        try {
            parent.closeServer();
            super.close();
        } catch (IOException e) {
        }
    }
}
