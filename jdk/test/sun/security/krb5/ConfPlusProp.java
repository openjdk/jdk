/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6857795
 * @bug 6858589
 * @bug 6972005
 * @summary krb5.conf ignored if system properties on realm and kdc are provided
 */

import sun.security.krb5.Config;

public class ConfPlusProp {
    Config config;
    public static void main(String[] args) throws Exception {
        new ConfPlusProp().run();
    }

    void refresh() throws Exception {
        Config.refresh();
        config = Config.getInstance();
    }

    void checkDefaultRealm(String r) throws Exception {
        try {
            if (!config.getDefaultRealm().equals(r)) {
                throw new AssertionError("Default realm error");
            }
        } catch (Exception e) {
            if (r != null) throw e;
        }
    }

    void check(String r, String k) throws Exception {
        try {
            if (!config.getKDCList(r).equals(k)) {
                throw new AssertionError(r + " kdc not " + k);
            }
        } catch (Exception e) {
            if (k != null) throw e;
        }
    }

    void run() throws Exception {

        // No prop, only conf

        // Point to a file with existing default_realm
        System.setProperty("java.security.krb5.conf",
                System.getProperty("test.src", ".") +"/confplusprop.conf");
        refresh();

        checkDefaultRealm("R1");
        check("R1", "k1");
        check("R2", "old");
        check("R3", null);
        if (!config.getDefault("forwardable", "libdefaults").equals("well")) {
            throw new Exception("Extra config error");
        }

        // Point to a file with no libdefaults
        System.setProperty("java.security.krb5.conf",
                System.getProperty("test.src", ".") +"/confplusprop2.conf");
        refresh();

        checkDefaultRealm(null);
        check("R1", "k12");
        check("R2", "old");
        check("R3", null);

        int version = System.getProperty("java.version").charAt(2) - '0';
        System.out.println("JDK version is " + version);

        // Zero-config is supported since 1.7
        if (version >= 7) {
            // Point to a non-existing file
            System.setProperty("java.security.krb5.conf", "i-am-not-a file");
            refresh();

            // Default realm might come from DNS
            //checkDefaultRealm(null);
            check("R1", null);
            check("R2", null);
            check("R3", null);
            if (config.getDefault("forwardable", "libdefaults") != null) {
                throw new Exception("Extra config error");
            }
        }

        // Add prop
        System.setProperty("java.security.krb5.realm", "R2");
        System.setProperty("java.security.krb5.kdc", "k2");

        // Point to a file with existing default_realm
        System.setProperty("java.security.krb5.conf",
                System.getProperty("test.src", ".") +"/confplusprop.conf");
        refresh();

        checkDefaultRealm("R2");
        check("R1", "k1");
        check("R2", "k2");
        check("R3", "k2");
        if (!config.getDefault("forwardable", "libdefaults").equals("well")) {
            throw new Exception("Extra config error");
        }

        // Point to a file with no libdefaults
        System.setProperty("java.security.krb5.conf",
                System.getProperty("test.src", ".") +"/confplusprop2.conf");
        refresh();

        checkDefaultRealm("R2");
        check("R1", "k12");
        check("R2", "k2");
        check("R3", "k2");

        // Point to a non-existing file
        System.setProperty("java.security.krb5.conf", "i-am-not-a file");
        refresh();

        checkDefaultRealm("R2");
        check("R1", "k2");
        check("R2", "k2");
        check("R3", "k2");
        if (config.getDefault("forwardable", "libdefaults") != null) {
            throw new Exception("Extra config error");
        }
    }
}
