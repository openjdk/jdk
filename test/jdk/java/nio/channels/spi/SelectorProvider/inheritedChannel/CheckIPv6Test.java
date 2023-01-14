/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;

/**
 * This test verifies that a service launched with IPv4 inherited channel
 * can use IPv6 networking; this used to be impossible, see JDK-6914801
 */
public class CheckIPv6Test {

    private static int failures = 0;

    private static final String SERVICE = "CheckIPv6Service";

    public static void main(String args[]) throws IOException {

        if (!CheckIPv6Service.isIPv6Available()) {
            System.out.println("IPv6 not available. Test skipped.");
            return;
        }

        try {
            EchoTest.TCPEchoTest(SERVICE);
            System.out.println("IPv6 test passed.");
        } catch (Exception x) {
            System.err.println(x);
            failures++;
        }

        if (failures > 0) {
            throw new RuntimeException("Test failed - see log for details");
        }
    }

}
