/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6296310
 * @library ../../httptest/
 * @build HttpCallback TestHttpServer HttpTransaction
 * @run main/othervm B6296310
 * @summary  REGRESSION: AppletClassLoader.getResourceAsStream() behaviour is wrong in some cases
 */

import java.net.*;
import java.io.*;
import java.util.*;

/*
 * http server returns 200 and content-length=0
 * Test will throw NPE if bug still exists
 */

public class B6296310
{
   static SimpleHttpTransaction httpTrans;
   static TestHttpServer server;

   public static void main(String[] args)
   {
      ResponseCache.setDefault(new MyCacheHandler());
      startHttpServer();

      makeHttpCall();
   }

   public static void startHttpServer() {
      try {
         httpTrans = new SimpleHttpTransaction();
         server = new TestHttpServer(httpTrans, 1, 10, 0);
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public static void makeHttpCall() {
      try {
         System.out.println("http server listen on: " + server.getLocalPort());
         URL url = new URL("http" , InetAddress.getLocalHost().getHostAddress(),
                            server.getLocalPort(), "/");
         HttpURLConnection uc = (HttpURLConnection)url.openConnection();
         System.out.println(uc.getResponseCode());
      } catch (IOException e) {
         e.printStackTrace();
      } finally {
         server.terminate();
      }
   }
}

class SimpleHttpTransaction implements HttpCallback
{
   /*
    * Our http server which simply retruns a file with no content
    */
   public void request(HttpTransaction trans) {
      try {
         trans.setResponseEntityBody("");
         trans.sendResponse(200, "OK");
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
}

class MyCacheHandler extends ResponseCache
{
   public CacheResponse get(URI uri, String rqstMethod, Map rqstHeaders)
   {
      return null;
   }

   public CacheRequest put(URI uri, URLConnection conn)
   {
      return new MyCacheRequest();
   }
}

class MyCacheRequest extends CacheRequest
{
   public void abort() {}

   public OutputStream getBody() throws IOException {
       return null;
   }
}
