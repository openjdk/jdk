/*
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6293767 6469513
 * @summary Test for the CardPermission class
 * @author Andreas Sterbenz
 */

import javax.smartcardio.*;

public class TestCardPermission {

    public static void main(String[] args) throws Exception {
        CardPermission perm;

        test("*");
        test("connect");
        test("reset");
        test("exclusive");
        test("transmitControl");
        test("getBasicChannel");
        test("openLogicalChannel");

        test("connect,reset");
        test("Reset,coNnect", "connect,reset");
        test("exclusive,*,connect", "*");
        test("connect,reset,exclusive,transmitControl,getBasicChannel,openLogicalChannel", "*");
        test(null, null);

        invalid("");
        invalid("foo");
        invalid("connect, reset");
        invalid("connect,,reset");
        invalid("connect,");
        invalid(",connect");
    }

    private static void invalid(String s) throws Exception {
        try {
            CardPermission c = new CardPermission("*", s);
            throw new Exception("Created invalid action: " + c);
        } catch (IllegalArgumentException e) {
            System.out.println("OK: " + e);
        }
    }

    private static void test(String actions) throws Exception {
        test(actions, actions);
    }

    private static void test(String actions, String canon) throws Exception {
        CardPermission p = new CardPermission("*", actions);
        System.out.println(p);
        String a = p.getActions();
        if (canon != null && canon.equals(a) == false) {
            throw new Exception("Canonical actions mismatch: " + canon + " != " + a);
        }
    }

}
