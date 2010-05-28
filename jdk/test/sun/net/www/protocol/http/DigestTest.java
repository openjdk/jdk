/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4432213
 * @run main/othervm -Dhttp.auth.digest.validateServer=true DigestTest
 * @summary  Need to support Digest Authentication for Proxies
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

class DigestServer extends Thread {

    ServerSocket s;
    Socket   s1;
    InputStream  is;
    OutputStream os;
    int port;

    String reply1 = "HTTP/1.1 401 Unauthorized\r\n"+
        "WWW-Authenticate: Digest realm=\""+realm+"\" domain=/ "+
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
        "Authentication-Info: ";

    DigestServer (ServerSocket y) {
        s = y;
        port = s.getLocalPort();
    }

    public void run () {
        try {
                s1 = s.accept ();
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
                String raw = header.findValue ("Authorization");
                HeaderParser parser = new HeaderParser (raw);
                String cnonce = parser.findValue ("cnonce");
                String cnstring = parser.findValue ("nc");

                String reply = reply2 + getAuthorization (uri, "GET", cnonce, cnstring) +"\r\n";
                os.write (reply.getBytes());
                Thread.sleep (2000);
                s1.close ();
        }
        catch (Exception e) {
            System.out.println (e);
            e.printStackTrace();
        }
    }

    static char[] passwd = "password".toCharArray();
    static String username = "user";
    static String nonce = "abcdefghijklmnopqrstuvwxyz";
    static String realm = "wallyworld";
    static String uri = "/foo.html";

    private String getAuthorization (String uri, String method, String cnonce, String cnstring) {
        String response;

        try {
            response = computeDigest(false, username,passwd,realm,
                                        method, uri, nonce, cnonce, cnstring);
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }

        String value = "Digest"
                        + " qop=auth\""
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

public class DigestTest {

    static class MyAuthenticator extends Authenticator {
        public MyAuthenticator () {
            super ();
        }

        public PasswordAuthentication getPasswordAuthentication ()
        {
            return (new PasswordAuthentication ("user", "Wrongpassword".toCharArray()));
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
            sock = new ServerSocket (5000);
            port = sock.getLocalPort ();
        }
        catch (Exception e) {
            System.out.println ("Exception: " + e);
            return;
        }

        server = new DigestServer(sock);
        server.start ();
        boolean passed = false;

        try  {

            Authenticator.setDefault (new MyAuthenticator ());
            String s = "http://localhost:" + port + DigestServer.uri;
            URL url = new URL(s);
            java.net.URLConnection conURL =  url.openConnection();

            InputStream in = conURL.getInputStream();
            int c;
            while ((c = in.read ()) != -1) {
            }
            in.close ();
        }
        catch(ProtocolException e) {
            passed = true;
        }
        if (!passed) {
            throw new RuntimeException ("Expected a ProtocolException from wrong password");
        }
    }
}
