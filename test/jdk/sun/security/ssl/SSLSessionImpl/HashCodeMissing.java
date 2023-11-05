/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4910892
 * @summary 4518403 was not properly fixed.   hashcode should be hashCode.
 * @library /javax/net/ssl/templates
 * @run main/othervm HashCodeMissing
 *
 *     SunJSSE does not support dynamic system properties, no way to re-use
 *     system properties in samevm/agentvm mode.
 * @author Brad Wetmore
 */

import java.io.*;
import javax.net.ssl.*;
import java.lang.reflect.*;

public class HashCodeMissing extends SSLSocketTemplate {

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = false;


    @Override
    protected void runServerApplication(SSLSocket sslSocket) throws Exception {
        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslIS.read();
        sslOS.write(85);
        sslOS.flush();

    }

    @Override
    protected void runClientApplication(SSLSocket sslSocket) throws Exception {

        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslOS.write(280);
        sslOS.flush();
        sslIS.read();

        SSLSession sslSession = sslSocket.getSession();

        sslSocket.close();

        Class clazz = sslSession.getClass();

        /*
         * Real test is done here
         */
        System.out.println("Getting 'hashCode'");
        Method method = clazz.getDeclaredMethod("hashCode", new Class [0]);
        System.out.println("Method = " + method);
    }

    public static void main(String[] args) throws Exception {

        if (debug)
            System.setProperty("javax.net.debug", "all");

        /*
         * Start the tests.
         */
        new HashCodeMissing().run();
    }

}
