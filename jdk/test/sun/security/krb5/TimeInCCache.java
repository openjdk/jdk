/*
 * Copyright (c) 2007, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6590930
 * @summary read/write does not match for ccache
 */

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import sun.security.krb5.internal.ccache.CCacheInputStream;
import sun.security.krb5.internal.ccache.Credentials;

public class TimeInCCache {
    public static void main(String[] args) throws Exception {
        // A trivial cache file, with startdate and renewTill being zero.
        // The endtime is set to sometime in year 2022, so that isValid()
        // will always check starttime.
        byte[] ccache = new byte[]{
            5, 4, 0, 12, 0, 1, 0, 8, -1, -1, -1, 19, -1, -2, 89, 51,
            0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 10, 77, 65, 88, 73,
            46, 76, 79, 67, 65, 76, 0, 0, 0, 5, 100, 117, 109, 109, 121, 0,
            0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 10, 77, 65, 88, 73, 46,
            76, 79, 67, 65, 76, 0, 0, 0, 5, 100, 117, 109, 109, 121, 0, 0,
            0, 0, 0, 0, 0, 2, 0, 0, 0, 10, 77, 65, 88, 73, 46, 76,
            79, 67, 65, 76, 0, 0, 0, 6, 107, 114, 98, 116, 103, 116, 0, 0,
            0, 10, 77, 65, 88, 73, 46, 76, 79, 67, 65, 76, 0, 17, 0, 0,
            0, 16, -78, -85, -90, -50, -68, 115, 68, 8, -39, -109, 91, 61, -17, -27,
            -122, -120, 71, 69, 16, -121, 0, 0, 0, 0, 98, 69, 16, -121, 0, 0,
            0, 0, 0, 64, -32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 1, 0, 97, -127, -3, 48, -127, -6, -96, 3, 2, 1, 5, -95, 12,
            27, 10, 77, 65, 88, 73, 46, 76, 79, 67, 65, 76, -94, 31, 48, 29,
            -96, 3, 2, 1, 0, -95, 22, 48, 20, 27, 6, 107, 114, 98, 116, 103,
            116, 27, 10, 77, 65, 88, 73, 46, 76, 79, 67, 65, 76, -93, -127, -61,
            48, -127, -64, -96, 3, 2, 1, 17, -95, 3, 2, 1, 1, -94, -127, -77,
            4, -127, -80, 43, 65, -66, 34, 21, -34, 37, 35, 32, 50, -14, 122, 77,
            -3, -29, 37, 99, 50, 125, -43, -96, -78, 85, 23, 41, -80, 68, 2, -109,
            -27, 38, -41, -72, -32, 127, 63, -76, -22, 81, 33, -114, -30, 104, 125, -81,
            -29, 70, -25, 23, 100, -75, -25, 62, -120, -78, -61, -100, -74, 50, -117, -127,
            -16, 79, -106, 62, -39, 91, 100, -10, 23, -88, -18, -47, 51, -19, 113, 18,
            98, -101, 31, 98, 22, -81, 11, -41, -42, 67, 87, 92, -2, 42, -54, 79,
            49, -90, 43, -37, 90, -102, 125, 62, -88, -77, 100, 102, 23, -57, -51, 38,
            68, -44, -57, -102, 103, -6, 85, -58, 74, -117, -87, 67, -103, -36, 110, -122,
            115, 12, 118, -106, -114, -51, 79, 68, 32, -91, -53, -5, -51, 89, 72, 70,
            123, -12, -95, 9, 40, -30, -117, 74, 77, 38, 91, 126, -82, 17, 98, 98,
            -49, 78, 36, 36, 103, -76, -100, -23, 118, -92, -8, 80, 103, -23, -98, 56,
            21, 65, -77, 0, 0, 0, 0
        };
        System.setProperty("sun.security.krb5.debug", "true");  // test code changes in DEBUG
        CCacheInputStream cis = new CCacheInputStream(new ByteArrayInputStream(ccache));
        cis.readVersion();
        cis.readTag();
        cis.readPrincipal(0x504);
        Method m = CCacheInputStream.class.getDeclaredMethod("readCred", Integer.TYPE);
        m.setAccessible(true);
        Credentials c = (Credentials) m.invoke(cis, new Integer(0x504));
        sun.security.krb5.Credentials cc = c.setKrbCreds();

        // 1. Make sure starttime is still null
        if (cc.getStartTime() != null) {
            throw new Exception("Fail, starttime should be zero here");
        }

        // 2. Make sure renewTill is still null
        if (cc.getRenewTill() != null) {
            throw new Exception("Fail, renewTill should be zero here");
        }

        // 3. Make sure isValid works
        c.isValid();
    }
}
