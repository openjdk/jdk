/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7184246
 * @summary Simplify Config.get() of krb5
 * @modules java.security.jgss/sun.security.krb5:+open
 */
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Vector;
import sun.security.krb5.Config;
import sun.security.krb5.SCDynamicStoreConfig;

public class SCDynamicConfigTest {

    static Vector<Hashtable<String,String>>hosts() {
        Vector <Hashtable<String,String>> result = new Vector<>();
        Hashtable<String,String> pair = new Hashtable<>();
        pair.put("host", "127.0.0.1");
        result.add(pair);
        pair = new Hashtable<>();
        pair.put("host", "127.0.0.2");
        result.add(pair);
        return result;
    }

    public static void main(String[] args) throws Exception {
        // Reconstruct a typical SCDynamicConfig.getKerberosConfig() output
        Hashtable<String, Object> conf = new Hashtable<>();

        Hashtable<String, Object> libdefaults = new Hashtable<>();
        libdefaults.put("default_realm", "REALM.COM");
        conf.put("libdefaults", libdefaults);

        Hashtable<String, Object> realms = new Hashtable<>();
        Hashtable<String, Object> thisRealm = new Hashtable<>();
        realms.put("REALM.COM", thisRealm);
        thisRealm.put("kpasswd", hosts());
        thisRealm.put("kadmin", hosts());
        thisRealm.put("kdc", hosts());
        conf.put("realms", realms);

        Hashtable<String, Object> domain_realm = new Hashtable<>();
        domain_realm.put(".realm.com", "REALM.COM");
        domain_realm.put("realm.com", "REALM.COM");
        conf.put("domain_realm", domain_realm);

        System.out.println("SCDynamicConfig:\n");
        System.out.println(conf);

        // Simulate SCDynamicConfig.getConfig() output
        Method m = SCDynamicStoreConfig.class.getDeclaredMethod(
                "convertNativeConfig", Hashtable.class);
        m.setAccessible(true);
        conf = (Hashtable)m.invoke(null, conf);

        System.out.println("\nkrb5.conf:\n");
        System.out.println(conf);

        // Feed it into a Config object
        System.setProperty("java.security.krb5.conf", "not-a-file");
        Config cf = Config.getInstance();
        Field f = Config.class.getDeclaredField("stanzaTable");
        f.setAccessible(true);
        f.set(cf, conf);

        System.out.println("\nConfig:\n");
        System.out.println(cf);

        if (!cf.getDefaultRealm().equals("REALM.COM")) {
            throw new Exception();
        }
        if (!cf.getKDCList("REALM.COM").equals("127.0.0.1 127.0.0.2")) {
            throw new Exception();
        }
        if (!cf.get("domain_realm", ".realm.com").equals("REALM.COM")) {
            throw new Exception();
        }
    }
}
