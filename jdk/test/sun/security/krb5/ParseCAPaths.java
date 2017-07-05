/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6789935
 * @summary cross-realm capath search error
 */

import java.util.Arrays;
import sun.security.krb5.Realm;

public class ParseCAPaths {
    static boolean failed = false;
    public static void main(String[] args) throws Exception {
        System.setProperty("java.security.krb5.conf", System.getProperty("test.src", ".") +"/krb5-capaths.conf");
        //System.setProperty("sun.security.krb5.debug", "true");

        // Standard example
        check("ANL.GOV", "TEST.ANL.GOV", "ANL.GOV");
        check("ANL.GOV", "ES.NET", "ANL.GOV");
        check("ANL.GOV", "PNL.GOV", "ANL.GOV", "ES.NET");
        check("ANL.GOV", "NERSC.GOV", "ANL.GOV", "ES.NET");
        // Hierachical
        check("N1.N.COM", "N2.N.COM", "N1.N.COM", "N.COM");     // 2 common
        check("N1.N.COM", "N2.N3.COM", "N1.N.COM", "N.COM",     // 1 common
                "COM", "N3.COM");
        check("N1.COM", "N2.COM", "N1.COM", "COM");             // 1 common
        check("N1", "N2", "N1");                                // 0 common
        // Extra garbages
        check("A1.COM", "A4.COM", "A1.COM", "A2.COM");
        check("B1.COM", "B3.COM", "B1.COM", "B2.COM");
        // Missing is "."
        check("C1.COM", "C3.COM", "C1.COM", "C2.COM");
        // Multiple path
        check("D1.COM", "D4.COM", "D1.COM", "D2.COM");
        check("E1.COM", "E4.COM", "E1.COM", "E2.COM");
        check("F1.COM", "F4.COM", "F1.COM", "F9.COM");
        // Infinite loop
        check("G1.COM", "G3.COM", "G1.COM", "COM");
        check("H1.COM", "H3.COM", "H1.COM");
        check("I1.COM", "I4.COM", "I1.COM", "I5.COM");

        if (failed) {
            throw new Exception("Failed somewhere.");
        }
    }

    static void check(String from, String to, String... paths) {
        try {
            check2(from, to, paths);
        } catch (Exception e) {
            failed = true;
            e.printStackTrace();
        }
    }
    static void check2(String from, String to, String... paths)
            throws Exception {
        System.out.println(from + " -> " + to);
        System.out.println("    expected: " + Arrays.toString(paths));
        String[] result = Realm.getRealmsList(from, to);
        System.out.println("    result:   " + Arrays.toString(result));
        if (result == null) {
            if (paths.length == 0) {
                // OK
            } else {
                throw new Exception("Shouldn't have a valid path.");
            }
        } else if(result.length != paths.length) {
            throw new Exception("Length of path not correct");
        } else {
            for (int i=0; i<result.length; i++) {
                if (!result[i].equals(paths[i])) {
                    throw new Exception("Path not same");
                }
            }
        }
    }
}
