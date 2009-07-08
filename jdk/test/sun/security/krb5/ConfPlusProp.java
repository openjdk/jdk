/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
/*
 * @test
 * @bug 6857795
 * @summary krb5.conf ignored if system properties on realm and kdc are provided
 */

import sun.security.krb5.Config;
import sun.security.krb5.KrbException;

public class ConfPlusProp {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.security.krb5.realm", "R2");
        System.setProperty("java.security.krb5.kdc", "k2");

        // Point to a file with existing default_realm
        System.setProperty("java.security.krb5.conf",
                System.getProperty("test.src", ".") +"/confplusprop.conf");
        Config config = Config.getInstance();

        if (!config.getDefaultRealm().equals("R2")) {
            throw new Exception("Default realm error");
        }
        if (!config.getKDCList("R1").equals("k1")) {
            throw new Exception("R1 kdc error");
        }
        if (!config.getKDCList("R2").equals("k2")) {
            throw new Exception("R2 kdc error");
        }
        if (!config.getDefault("forwardable", "libdefaults").equals("well")) {
            throw new Exception("Extra config error");
        }

        // Point to a file with no libdefaults
        System.setProperty("java.security.krb5.conf",
                System.getProperty("test.src", ".") +"/confplusprop2.conf");
        Config.refresh();

        config = Config.getInstance();

        if (!config.getDefaultRealm().equals("R2")) {
            throw new Exception("Default realm error again");
        }
        if (!config.getKDCList("R1").equals("k12")) {
            throw new Exception("R1 kdc error");
        }
        if (!config.getKDCList("R2").equals("k2")) {
            throw new Exception("R2 kdc error");
        }

        // Point to a non-existing file
        System.setProperty("java.security.krb5.conf", "i-am-not-a file");
        Config.refresh();

        config = Config.getInstance();

        if (!config.getDefaultRealm().equals("R2")) {
            throw new Exception("Default realm error");
        }
        try {
            config.getKDCList("R1");
            throw new Exception("R1 is nowhere");
        } catch (KrbException ke) {
            // OK
        }
        if (!config.getKDCList("R2").equals("k2")) {
            throw new Exception("R2 kdc error");
        }
        if (config.getDefault("forwardable", "libdefaults") != null) {
            throw new Exception("Extra config error");
        }
    }
}
