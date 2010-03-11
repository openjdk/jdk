/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

/* @test
 * @summary Unit test for java.net.CookieHandler
 * @bug 4696506
 * @run main/othervm CookieHandlerTest
 * @author Yingxian Wang
 */

// Run in othervm since a default cookier handler is set and this
// can effect other HTTP related tests.

import java.net.*;
import java.util.*;
import java.io.*;

public class CookieHandlerTest implements Runnable {
    static Map<String,String> cookies;
    ServerSocket ss;

    /*
     * Our "http" server to return a 404
     */
    public void run() {
        try {
            Socket s = ss.accept();

            // check request contains "Cookie"
            InputStream is = s.getInputStream ();
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            boolean flag = false;
            String x;
            while ((x=r.readLine()) != null) {
                if (x.length() ==0) {
                    break;
                }
                String header = "Cookie: ";
                if (x.startsWith(header)) {
                    if (x.equals("Cookie: "+((String)cookies.get("Cookie")))) {
                        flag = true;
                    }
                }
            }
            if (!flag) {
                throw new RuntimeException("server should see cookie in request");
            }

            PrintStream out = new PrintStream(
                                 new BufferedOutputStream(
                                    s.getOutputStream() ));

            /* send the header */
            out.print("HTTP/1.1 200 OK\r\n");
            out.print("Set-Cookie2: "+((String)cookies.get("Set-Cookie2")+"\r\n"));
            out.print("Content-Type: text/html; charset=iso-8859-1\r\n");
            out.print("Connection: close\r\n");
            out.print("\r\n");
            out.print("<HTML>");
            out.print("<HEAD><TITLE>Testing cookie</TITLE></HEAD>");
            out.print("<BODY>OK.</BODY>");
            out.print("</HTML>");
            out.flush();

            s.close();
            ss.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    CookieHandlerTest() throws Exception {

        /* start the server */
        ss = new ServerSocket(0);
        (new Thread(this)).start();

        /* establish http connection to server */
        String uri = "http://localhost:" +
                     Integer.toString(ss.getLocalPort());
        URL url = new URL(uri);

        HttpURLConnection http = (HttpURLConnection)url.openConnection();

        int respCode = http.getResponseCode();
        http.disconnect();

    }
    public static void main(String args[]) throws Exception {
        cookies = new HashMap<String, String>();
        cookies.put("Cookie", "$Version=\"1\"; Customer=\"WILE_E_COYOTE\"; $Path=\"/acme\"");
        cookies.put("Set-Cookie2", "$Version=\"1\"; Part_Number=\"Riding_Rocket_0023\"; $Path=\"/acme/ammo\"; Part_Number=\"Rocket_Launcher_0001\"; $Path=\"/acme\"");
        CookieHandler.setDefault(new MyCookieHandler());
        new CookieHandlerTest();
    }

    static class MyCookieHandler extends CookieHandler {
        public Map<String,List<String>>
            get(URI uri, Map<String,List<String>> requestHeaders)
            throws IOException {
            // returns cookies[0]
            // they will be include in request
            Map<String,List<String>> map = new HashMap<String,List<String>>();
            List<String> l = new ArrayList<String>();
            l.add(cookies.get("Cookie"));
            map.put("Cookie",l);
            return Collections.unmodifiableMap(map);
        }

        public void
            put(URI uri, Map<String,List<String>> responseHeaders)
            throws IOException {
            // check response has cookies[1]
            List<String> l = responseHeaders.get("Set-Cookie2");
            String value = l.get(0);
            if (!value.equals(cookies.get("Set-Cookie2"))) {
                throw new RuntimeException("cookie should be available for handle to put into cache");
               }
        }
    }

}
