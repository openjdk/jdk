/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8146086
 * @summary Publishing two webservices on same port fails with "java.net.BindException: Address already in use"
 * @run main/othervm WSTest
 */
import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.xml.ws.Endpoint;
import java.net.ServerSocket;

public class WSTest {

    @WebService(targetNamespace = "test")
    public static class Method1 {
        @WebMethod
        public String getMethod1Value() {
            return "from Method1";
        }
    }

    @WebService(targetNamespace = "test")
    public static class Method2 {
        @WebMethod
        public String getMethod2Value() {
            return "from Method2";
        }
    }

    public static void main(String[] args) throws Exception {

        // find a free port
        ServerSocket ss = new ServerSocket(0);
        int port = ss.getLocalPort();
        ss.close();

        Endpoint endPoint1 = null;
        Endpoint endPoint2 = null;
        try {
            endPoint1 = Endpoint.publish("http://0.0.0.0:" + port + "/method1",
                    new Method1());
            endPoint2 = Endpoint.publish("http://0.0.0.0:" + port + "/method2",
                    new Method2());

            System.out.println("Sleep 3 secs...");

            Thread.sleep(3000);
        } finally {
            stop(endPoint2);
            stop(endPoint1);
        }
    }

    private static void stop(Endpoint endPoint) {
        if (endPoint == null) return;

        try {
            endPoint.stop();
        } catch (Throwable ignored) {
        }
    }

}
