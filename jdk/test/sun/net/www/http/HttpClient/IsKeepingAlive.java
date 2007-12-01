/*
 * Copyright 1999-2001 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * @test 1.5 99/07/29
 * @bug 4277155
 * @summary Make sure HttpClient.getHttpKeepAliveSet() has
 *    doPrivileged() call at appropriate places.
 * @run main/othervm/policy=IsKeepingAlive.policy IsKeepingAlive
 */

import java.net.URL;
import java.net.ServerSocket;
import sun.net.www.http.HttpClient;
import java.security.*;

public class IsKeepingAlive {

    public static void main(String[] args) throws Exception {

        ServerSocket ss = new ServerSocket(0);

        SecurityManager security = System.getSecurityManager();
        if (security == null) {
            security = new SecurityManager();
            System.setSecurityManager(security);
        }

        URL url1 = new URL("http://localhost:" + ss.getLocalPort());

        HttpClient c1 = HttpClient.New(url1);

        boolean keepAlive = c1.isKeepingAlive();

        ss.close();
    }
}
