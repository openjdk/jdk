/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4966382 8039132
 * @run main/othervm UdpTcp UDP
 * @run main/othervm UdpTcp TCP
 * @summary udp or tcp
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import sun.security.krb5.Config;

public class UdpTcp {

    public static void main(String[] args)
            throws Exception {

        System.setProperty("sun.security.krb5.debug", "true");

        OneKDC kdc = new OneKDC(null);
        kdc.writeJAASConf();

        // Two styles of kdc_timeout setting. One global, one realm-specific.
        if (args[0].equals("UDP")) {
            KDC.saveConfig(OneKDC.KRB5_CONF, kdc,
                    "kdc_timeout = 10s");
        } else {
            kdc.addConf("kdc_timeout = 10s");
            KDC.saveConfig(OneKDC.KRB5_CONF, kdc,
                    "udp_preference_limit = 1");
        }
        Config.refresh();

        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream oldout = System.out;
        System.setOut(new PrintStream(bo));
        Context.fromUserPass(OneKDC.USER, OneKDC.PASS, false);
        System.setOut(oldout);

        for (String line: new String(bo.toByteArray()).split("\n")) {
            if (line.contains(">>> KDCCommunication")) {
                if (!line.contains(args[0]) || !line.contains("timeout=10000")) {
                    throw new Exception("No " + args[0] + " in: " + line);
                }
            }
        }
    }
}
