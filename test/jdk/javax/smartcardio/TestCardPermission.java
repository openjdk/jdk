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
 * @library /test/lib
 * @author Andreas Sterbenz
 */

import javax.smartcardio.*;
import java.security.Permission;

import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

public class TestCardPermission {

    public static void main(String[] args) throws Exception {
        testAction("*");
        testAction("connect");
        testAction("reset");
        testAction("exclusive");
        testAction("transmitControl");
        testAction("getBasicChannel");
        testAction("openLogicalChannel");

        testAction("connect,reset");
        testAction("Reset,coNnect", "connect,reset");
        testAction("exclusive,*,connect", "*");
        testAction("connect,reset,exclusive,transmitControl,getBasicChannel,openLogicalChannel", "*");
        testAction(null, null);

        invalidAction("");
        invalidAction("foo");
        invalidAction("connect, reset");
        invalidAction("connect,,reset");
        invalidAction("connect,");
        invalidAction(",connect");


        testImpliesNotCardPermission("connect");
        testImpliesNotSubsetCardPermission();
        testImpliesNameEqualsAll();
        testImpliesBothSameNameNotAll();
        testImpliesNameNotSameNotAll();
    }

    private static void invalidAction(String s) throws Exception {
        try {
            CardPermission c = new CardPermission("*", s);
            throw new Exception("Created invalid action: " + c);
        } catch (IllegalArgumentException e) {
            System.out.println("OK: " + e);
        }
    }

    private static void testAction(String actions) throws Exception {
        testAction(actions, actions);
    }

    private static void testAction(String actions, String canon) throws Exception {
        CardPermission p = new CardPermission("*", actions);
        System.out.println(p);
        String a = p.getActions();
        if (canon != null && canon.equals(a) == false) {
            throw new Exception("Canonical actions mismatch: " + canon + " != " + a);
        }
    }

    private static void testImpliesNotCardPermission(String actions) {
        CardPermission p1 = new CardPermission("*", actions);
        Permission p2 = new Permission(actions) {
            @Override public boolean implies(Permission permission) { return false; }
            @Override public boolean equals(Object obj) { return false; }
            @Override public int hashCode() { return 0; }
            @Override public String getActions() { return null; }
        };
        assertFalse(p1.implies(p2));
    }

    private static void testImpliesNotSubsetCardPermission() {
        CardPermission p1 = new CardPermission("*", "connect,reset");
        CardPermission p2 = new CardPermission("*", "transmitControl");
        assertFalse(p1.implies(p2));
    }

    private static void testImpliesNameEqualsAll() {
        CardPermission p1 = new CardPermission("*", "connect,reset");
        CardPermission p2 = new CardPermission("None", "reset");
        assertTrue(p1.implies(p2));
    }

    private static void testImpliesBothSameNameNotAll() {
        CardPermission p1 = new CardPermission("None", "connect,reset");
        CardPermission p2 = new CardPermission("None", "reset");
        assertTrue(p1.implies(p2));
    }

    private static void testImpliesNameNotSameNotAll() {
        CardPermission p1 = new CardPermission("None", "connect,reset");
        CardPermission p2 = new CardPermission("Other", "reset");
        assertFalse(p1.implies(p2));
    }


}
