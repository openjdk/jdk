/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7162687
 * @key intermittent
 * @summary enhance KDC server availability detection
 * @compile -XDignore.symbol.file Unreachable.java
 * @run main/othervm/timeout=10 Unreachable
 */

import java.io.File;
import javax.security.auth.login.LoginException;
import sun.security.krb5.Config;

public class Unreachable {

    public static void main(String[] args) throws Exception {
        File f = new File(
                System.getProperty("test.src", "."), "unreachable.krb5.conf");
        System.setProperty("java.security.krb5.conf", f.getPath());
        Config.refresh();

        // If PortUnreachableException is not received, the login will consume
        // about 3*3*30 seconds and the test will timeout.
        try {
            Context.fromUserPass("name", "pass".toCharArray(), true);
        } catch (LoginException le) {
            // This is OK
        }
    }
}
