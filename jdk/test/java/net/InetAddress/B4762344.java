/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4762344
 * @summary 2nd nameservice provider is non functional
 * @build B4762344 SimpleNameService Simple1NameServiceDescriptor Simple2NameServiceDescriptor
 * @run main/othervm -Dsun.net.spi.nameservice.provider.1=simple1,sun -Dsun.net.spi.nameservice.provider.2=simple2,sun B4762344
 */

import java.net.*;
import java.util.*;


public class B4762344 {
    private static String[][] hostnames = new String[][] {
            // both providers know this host, but with different address
            new String[] {"blade", "10.0.0.1"},
            // provider1 knwos this host
            new String[] {"blade.domain1", "10.0.0.2"},
            // provider2 knows this host
            new String[] {"blade.domain2", "20.0.0.2"}
        };
    private static String[][] hostaddrs = new String[][] {
            new String[] {"10.0.0.1", "blade"},
            new String[] {"10.0.0.2", "blade.domain1"},
            new String[] {"20.0.0.2", "blade.domain2"}
        };

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < hostnames.length; i++) {
            doLookup(hostnames[i][0], hostnames[i][1]);
        }
        for (int i = 0; i < hostaddrs.length; i++) {
            doReverseLookup(hostaddrs[i][0], hostaddrs[i][1]);
        }
    }

    private static void doLookup(String host, String addr) throws Exception {
        String res = InetAddress.getByName(host).getHostAddress();
        if (!res.equals(addr)) {
            throw new RuntimeException("Test failed: wrong address for host " + host);
        }
    }

    private static void doReverseLookup(String addr, String host) throws Exception {
        StringTokenizer tokenizer = new StringTokenizer(addr, ".");
        byte addrs[] = new byte[4];
        for (int i = 0; i < 4; i++) {
            addrs[i] = (byte)Integer.parseInt(tokenizer.nextToken());
        }
        String res = InetAddress.getByAddress(addrs).getHostName();
        if (!res.equals(host)) {
            throw new RuntimeException("Test failed: wrong host name for address " + addr);
        }
    }
}
