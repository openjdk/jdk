/*
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @run main/othervm B6296310
 * @run main/othervm -Djava.net.preferIPv6Addresses=true B6296310
 * @summary  REGRESSION: AppletClassLoader.getResourceAsStream() behaviour is wrong in some cases
 */

import java.io.IOException;
import java.io.OutputStream;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ResponseCache;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/*
 * http server returns 200 and content-length=0
 * Test will throw NPE if bug still exists
 */

public class B6296310
{
   static SimpleHttpTransaction httpTrans;
   static HttpServer server;

   public static void main(String[] args) throws Exception
   {
      ResponseCache.setDefault(new MyCacheHandler());
      startHttpServer();
      makeHttpCall();
   }

   public static void startHttpServer() throws IOException {
     httpTrans = new SimpleHttpTransaction();
     InetAddress loopback = InetAddress.getLoopbackAddress();
     server = HttpServer.create(new InetSocketAddress(loopback, 0), 10);
     server.createContext("/", httpTrans);
     server.setExecutor(Executors.newSingleThreadExecutor());
     server.start();
   }

   public static void makeHttpCall() throws IOException {
      try {
         System.out.println("http server listen on: " + server.getAddress().getPort());
         URL url = new URL("http" , InetAddress.getLoopbackAddress().getHostAddress(),
                            server.getAddress().getPort(), "/");
         HttpURLConnection uc = (HttpURLConnection)url.openConnection(Proxy.NO_PROXY);
         System.out.println(uc.getResponseCode());
      } finally {
         server.stop(1);
      }
   }
}

class SimpleHttpTransaction implements HttpHandler
{
   /*
    * Our http server which simply retruns a file with no content
    */
   @Override
   public void handle(HttpExchange trans) {
      try {
         trans.sendResponseHeaders(200, 0);
         trans.close();
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
