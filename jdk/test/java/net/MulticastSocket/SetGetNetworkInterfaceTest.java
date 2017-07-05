/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6458027
 * @summary Disabling IPv6 on a specific network interface causes problems.
 *
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;


public class SetGetNetworkInterfaceTest  {

    public static void main(String[] args) throws Exception {

        boolean passed = true;
        try {
            MulticastSocket ms = new MulticastSocket();
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface netIf = networkInterfaces.nextElement();
                if (isNetworkInterfaceTestable(netIf)) {
                    printNetIfDetails(netIf);
                    ms.setNetworkInterface(netIf);
                    NetworkInterface msNetIf = ms.getNetworkInterface();
                    if (netIf.equals(msNetIf)) {
                        System.out.println(" OK");
                    } else {
                        System.out.println("FAILED!!!");
                        printNetIfDetails(msNetIf);
                        passed = false;
                    }
                    System.out.println("------------------");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            passed = false;
        }
        if (!passed) {
            throw new RuntimeException("Test Fail");
        }
        System.out.println("Test passed ");
    }

    private static boolean isNetworkInterfaceTestable(NetworkInterface netIf) throws Exception {
        System.out.println("checking netif == " + netIf.getName());
        return  (netIf.isUp() && netIf.supportsMulticast() && isIpAddrAvailable(netIf));
    }

    private static boolean isIpAddrAvailable (NetworkInterface netIf) {
        boolean ipAddrAvailable = false;
        byte[] nullIpAddr = {'0', '0', '0', '0'};
        byte[] testIpAddr = null;

        Enumeration<InetAddress> ipAddresses = netIf.getInetAddresses();
        while (ipAddresses.hasMoreElements()) {
            InetAddress testAddr = ipAddresses.nextElement();
            testIpAddr = testAddr.getAddress();
            if ((testIpAddr != null) && (!Arrays.equals(testIpAddr, nullIpAddr))) {
                ipAddrAvailable = true;
                break;
            } else {
                System.out.println("ignore netif " + netIf.getName());
            }
        }
        return ipAddrAvailable;
    }

    private static void printNetIfDetails(NetworkInterface ni)
            throws SocketException {
        System.out.println("Name " + ni.getName() + " index " + ni.getIndex());
        Enumeration<InetAddress> en = ni.getInetAddresses();
        while (en.hasMoreElements()) {
            System.out.println(" InetAdress: " + en.nextElement());
        }
        System.out.println("HardwareAddress: " + createMacAddrString(ni));
        System.out.println("loopback: " + ni.isLoopback() + "; pointToPoint: "
                + ni.isPointToPoint() + "; virtual: " + ni.isVirtual()
                + "; MTU: " + ni.getMTU());
    }

    private static String createMacAddrString(NetworkInterface netIf)
            throws SocketException {
        byte[] macAddr = netIf.getHardwareAddress();
        StringBuilder sb = new StringBuilder();
        if (macAddr != null) {
            for (int i = 0; i < macAddr.length; i++) {
                sb.append(String.format("%02X%s", macAddr[i],
                        (i < macAddr.length - 1) ? "-" : ""));
            }
        }
        return sb.toString();
    }
}
