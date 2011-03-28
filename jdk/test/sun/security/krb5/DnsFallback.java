/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6673164
 * @bug 6552334
 * @run main/othervm DnsFallback
 * @summary fix dns_fallback parse error, and use dns by default
 */

import sun.security.krb5.*;
import java.io.*;

public class DnsFallback {
    public static void main(String[] args) throws Exception {

        // for 6673164
        check("true", "true", true);
        check("false", "true", false);
        check("true", "false", true);
        check("false", "false", false);
        check("true", null, true);
        check("false", null, false);
        check(null, "true", true);
        check(null, "false", false);

        // for 6552334
        check(null, null, true);
    }

    static void check(String realm, String fallback, boolean output) throws Exception {
        FileOutputStream fo = new FileOutputStream("dnsfallback.conf");
        StringBuffer sb = new StringBuffer();
        sb.append("[libdefaults]\n");
        if (realm != null) {
            sb.append("dns_lookup_realm=" + realm + "\n");
        }
        if (fallback != null) {
            sb.append("dns_fallback=" + fallback + "\n");
        }
        fo.write(sb.toString().getBytes());
        fo.close();
        System.setProperty("java.security.krb5.conf", "dnsfallback.conf");
        Config.refresh();
        System.out.println("Testing " + realm + ", " + fallback + ", " + output);
        if (Config.getInstance().useDNS_Realm() != output) {
            throw new Exception("Fail");
        }
    }
}

