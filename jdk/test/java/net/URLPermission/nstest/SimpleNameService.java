/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * A simple name service based on an in-memory HashMap.
 */
import java.net.UnknownHostException;
import java.net.InetAddress;
import sun.net.spi.nameservice.*;
import java.util.*;

public final class SimpleNameService implements NameService {

    private static LinkedHashMap hosts = new LinkedHashMap();

    private static String addrToString(byte addr[]) {
        return Byte.toString(addr[0]) + "." +
               Byte.toString(addr[1]) + "." +
               Byte.toString(addr[2]) + "." +
               Byte.toString(addr[3]);
    }

    // ------------

    public static void put(String host, String addr) {
        hosts.put(host, addr);
    }

    public static void put(String host, byte addr[]) {
        hosts.put(host, addrToString(addr));
    }

    public static void remove(String host) {
        hosts.remove(host);
    }

    public static int entries () {
        return hosts.size();
    }

    public static int lookupCalls() {
        return lookupCalls;
    }

    static int lookupCalls = 0;

    // ------------

    public SimpleNameService() throws Exception {
    }

    public InetAddress[] lookupAllHostAddr(String host) throws UnknownHostException {

        lookupCalls ++;

        String value = (String)hosts.get(host);
        if (value == null) {
            throw new UnknownHostException(host);
        }
        StringTokenizer st = new StringTokenizer(value, ".");
        byte addr[] = new byte[4];
        for (int i=0; i<4; i++) {
            addr[i] = (byte)Integer.parseInt(st.nextToken());
        }
        InetAddress[] res = new InetAddress[1];
        res[0] = InetAddress.getByAddress(host, addr);
        return res;
    }

    public String getHostByAddr(byte[] addr) throws UnknownHostException {
        String addrString = addrToString(addr);
        Iterator i = hosts.keySet().iterator();
        while (i.hasNext()) {
            String host = (String)i.next();
            String value = (String)hosts.get(host);
            if (value.equals(addrString)) {
                return host;
            }
        }
        throw new UnknownHostException();
    }
}
