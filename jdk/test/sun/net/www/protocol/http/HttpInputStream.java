/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4937598
 * @summary http://www.clipstream.com vedio does not play; read() problem
 */
import java.util.*;
import java.io.*;
import java.net.*;
import java.text.*;

public class HttpInputStream implements Runnable {

  ServerSocket serverSock;

  public void run() {
      try {
          Socket s = serverSock.accept();
          InputStream in = s.getInputStream();
          byte b[] = new byte[4096];

          // assume we read the entire http request
          // (bad assumption but okay for test case)
          int nread = in.read(b);

          OutputStream o = s.getOutputStream();

          o.write( "HTTP/1.1 200 OK".getBytes() );
          o.write( "Content-Length: 20".getBytes() );
          o.write( (byte)'\r' );
          o.write( (byte)'\n' );
          o.write( (byte)'\r' );
          o.write( (byte)'\n' );

          for (int i = 0; i < 20; i++) {
              o.write((byte)0xff);
          }

          o.flush();
          o.close();

      } catch (Exception e) { }
  }


  public HttpInputStream() throws Exception {

     serverSock = new ServerSocket(0);
     int port = serverSock.getLocalPort();

     Thread thr = new Thread(this);
     thr.start();

     Date date = new Date(new Date().getTime()-1440000); // this time yesterday
     URL url;
     HttpURLConnection con;

     url = new URL("http://localhost:" + String.valueOf(port) +
                   "/anything");
     con = (HttpURLConnection)url.openConnection();

     int ret = con.getResponseCode();
     byte[] b = new byte[20];
     InputStream is = con.getInputStream();
     int i = 0, count = 0;
     while ((i = is.read()) != -1) {
         System.out.println("i = "+i);
         count++;
     }
     if (count != 20) {
         throw new RuntimeException("HttpInputStream.read() failed with 0xff");
     }
  }

  public static void main(String args[]) throws Exception {
      new HttpInputStream();
  }
}
