/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4665824
 * @summary JSSE - ClassCastException with 1.4
 * @modules java.base/com.sun.net.ssl
 */

import com.sun.net.ssl.*;

public class TruncateArray {

    public static void main(String args[]) throws Exception {
        try {

            TrustManager tms [] = new TrustManager [] {
                new MyTM(), new MyTM(), new MyTM() };

            KeyManager kms [] = new KeyManager [] {
                new MyKM(), new MyKM(), new MyKM() };

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kms, tms, null);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SUNX509");
            KeyManager[] km = kmf.getKeyManagers();

            TrustManagerFactory tmf =
                TrustManagerFactory.getInstance("SUNX509");
            TrustManager[] tm = tmf.getTrustManagers();

        } catch (ClassCastException e) {
            throw e;
        } catch (Throwable e) {
            /*
             * swallow anything else, we only are interested
             * in class casting errors here.  For example, we soon
             * may be catching methods called on uninitialized factories.
             */
            System.out.println("Caught something else");
            e.printStackTrace();
        }
    }

    static class MyTM implements TrustManager {
    }

    static class MyKM implements KeyManager {
    }
}
