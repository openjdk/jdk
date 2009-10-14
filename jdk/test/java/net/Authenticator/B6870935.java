/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * @test
 * @bug 6870935
 * @run main/othervm -Dhttp.nonProxyHosts="" -Dhttp.auth.digest.validateProxy=true B6870935
 */

import java.io.*;
import java.util.*;
import java.net.*;
import java.security.*;
import sun.net.www.*;

/* This is one simple test of the RFC2617 digest authentication behavior
 * It specifically tests that the client correctly checks the returned
 * Authentication-Info header field from the server and throws an exception
 * if the password is wrong
 */

public class B6870935 {

    static char[] passwd = "password".toCharArray();
    static String username = "user";
    static String nonce = "abcdefghijklmnopqrstuvwxyz";
    static String realm = "wallyworld";
    static String uri = "http://www.ibm.com";
    static volatile boolean error = false;

    static class DigestServer extends Thread {

        ServerSocket s;
        InputStream  is;
        OutputStream os;
        int port;

        String reply1 = "HTTP/1.1 407 Proxy Authentication Required\r\n"+
            "Proxy-Authenticate: Digest realm=\""+realm+"\" domain=/ "+
            "nonce=\""+nonce+"\" qop=\"auth\"\r\n\r\n";

        String reply2 = "HTTP/1.1 200 OK\r\n" +
            "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
            "Server: Apache/1.3.14 (Unix)\r\n" +
            "Content-Type: text/html; charset=iso-8859-1\r\n" +
            "Transfer-encoding: chunked\r\n\r\n"+
            "B\r\nHelloWorld1\r\n"+
            "B\r\nHelloWorld2\r\n"+
            "B\r\nHelloWorld3\r\n"+
            "B\r\nHelloWorld4\r\n"+
            "B\r\nHelloWorld5\r\n"+
            "0\r\n"+
            "Proxy-Authentication-Info: ";

        DigestServer (ServerSocket y) {
            s = y;
            port = s.getLocalPort();
        }

        public void run () {
            try {
                Socket s1 = s.accept ();
                is = s1.getInputStream ();
                os = s1.getOutputStream ();
                is.read ();
                os.write (reply1.getBytes());
                Thread.sleep (2000);
                s1.close ();

                s1 = s.accept ();
                is = s1.getInputStream ();
                os = s1.getOutputStream ();
                is.read ();
                // need to get the cnonce out of the response
                MessageHeader header = new MessageHeader (is);
                String raw = header.findValue ("Proxy-Authorization");
                HeaderParser parser = new HeaderParser (raw);
                String cnonce = parser.findValue ("cnonce");
                String cnstring = parser.findValue ("nc");
                String clientrsp = parser.findValue ("response");
                String expected = computeDigest(
                        true, username,passwd,realm,
                        "GET", uri, nonce, cnonce, cnstring
                );
                if (!expected.equals(clientrsp)) {
                    s1.close ();
                    s.close ();
                    error = true;
                    return;
                }

                String reply = reply2 + getAuthorization (
                        realm, false, uri, "GET", cnonce,
                        cnstring, passwd, username
                ) +"\r\n";
                os.write (reply.getBytes());
                Thread.sleep (2000);
                s1.close ();
            }
            catch (Exception e) {
                System.out.println (e);
                e.printStackTrace();
            }
        }

        private String getAuthorization (String realm, boolean isRequest, String uri, String method, String cnonce, String cnstring, char[] password, String username) {
            String response;

            try {
                response = computeDigest(isRequest, username,passwd,realm,
                                            method, uri, nonce, cnonce, cnstring);
            } catch (NoSuchAlgorithmException ex) {
                return null;
            }

            String value = "Digest"
                            + " qop=\"auth"
                            + "\", cnonce=\"" + cnonce
                            + "\", rspauth=\"" + response
                            + "\", nc=\"" + cnstring + "\"";
            return (value+ "\r\n");
        }

        private String computeDigest(
                            boolean isRequest, String userName, char[] password,
                            String realm, String connMethod,
                            String requestURI, String nonceString,
                            String cnonce, String ncValue
                        ) throws NoSuchAlgorithmException
        {

            String A1, HashA1;

            MessageDigest md = MessageDigest.getInstance("MD5");

            {
                A1 = userName + ":" + realm + ":";
                HashA1 = encode(A1, password, md);
            }

            String A2;
            if (isRequest) {
                A2 = connMethod + ":" + requestURI;
            } else {
                A2 = ":" + requestURI;
            }
            String HashA2 = encode(A2, null, md);
            String combo, finalHash;

            { /* RRC2617 when qop=auth */
                combo = HashA1+ ":" + nonceString + ":" + ncValue + ":" +
                            cnonce + ":auth:" +HashA2;

            }
            finalHash = encode(combo, null, md);
            return finalHash;
        }

        private final static char charArray[] = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
        };

        private String encode(String src, char[] passwd, MessageDigest md) {
            md.update(src.getBytes());
            if (passwd != null) {
                byte[] passwdBytes = new byte[passwd.length];
                for (int i=0; i<passwd.length; i++)
                    passwdBytes[i] = (byte)passwd[i];
                md.update(passwdBytes);
                Arrays.fill(passwdBytes, (byte)0x00);
            }
            byte[] digest = md.digest();

            StringBuffer res = new StringBuffer(digest.length * 2);
            for (int i = 0; i < digest.length; i++) {
                int hashchar = ((digest[i] >>> 4) & 0xf);
                res.append(charArray[hashchar]);
                hashchar = (digest[i] & 0xf);
                res.append(charArray[hashchar]);
            }
            return res.toString();
        }
    }


    static class MyAuthenticator extends Authenticator {
        public MyAuthenticator () {
            super ();
        }

        public PasswordAuthentication getPasswordAuthentication ()
        {
            return (new PasswordAuthentication (username, passwd));
        }
    }


    public static void main(String[] args) throws Exception {
        int nLoops = 1;
        int nSize = 10;
        int port, n =0;
        byte b[] = new byte[nSize];
        DigestServer server;
        ServerSocket sock;

        try {
            sock = new ServerSocket (0);
            port = sock.getLocalPort ();
        }
        catch (Exception e) {
            System.out.println ("Exception: " + e);
            return;
        }

        server = new DigestServer(sock);
        server.start ();

        try  {

            Authenticator.setDefault (new MyAuthenticator ());
            SocketAddress addr = new InetSocketAddress ("127.0.0.1", port);
            Proxy proxy = new Proxy (Proxy.Type.HTTP, addr);
            String s = "http://www.ibm.com";
            URL url = new URL(s);
            java.net.URLConnection conURL =  url.openConnection(proxy);

            InputStream in = conURL.getInputStream();
            int c;
            while ((c = in.read ()) != -1) {
            }
            in.close ();
        }
        catch(IOException e) {
            e.printStackTrace();
            error = true;
        }
        if (error) {
            throw new RuntimeException ("Error in test");
        }
    }
}
