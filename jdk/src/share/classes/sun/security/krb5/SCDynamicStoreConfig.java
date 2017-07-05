/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.security.krb5;

import java.io.IOException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Vector;


public class SCDynamicStoreConfig {
    private static native void installNotificationCallback();
    private static native Hashtable<String, Object> getKerberosConfig();

    static {
        java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Void>() {
                public Void run() {
                    System.loadLibrary("osx");
                    return null;
                }
            });
        installNotificationCallback();
    }

    private static Vector<String> unwrapHost(Collection<Hashtable<String, String>> c) {
        Vector<String> vector = new Vector<String>();
        for (Hashtable<String, String> m : c) {
            vector.add(m.get("host"));
        }
        return vector;
    }

    /**
     * convertRealmConfigs: Maps the Object graph that we get from JNI to the
     * object graph that Config expects. Also the items inside the kdc array
     * are wrapped inside Hashtables
     */
    @SuppressWarnings("unchecked")
    private static Hashtable<String, Object> convertRealmConfigs(Hashtable<String, ?> configs) {
        Hashtable<String, Object> realmsTable = new Hashtable<String, Object>();

        for (String realm : configs.keySet()) {
            // get the kdc
            Hashtable<String, Collection<?>> map = (Hashtable<String, Collection<?>>) configs.get(realm);
            Collection<Hashtable<String, String>> kdc = (Collection<Hashtable<String, String>>) map.get("kdc");

            // put the kdc into the realmMap
            Hashtable<String, Vector<String>> realmMap = new Hashtable<String, Vector<String>>();
            if (kdc != null) realmMap.put("kdc", unwrapHost(kdc));

            // put the admin server into the realmMap
            Collection<Hashtable<String, String>> kadmin = (Collection<Hashtable<String, String>>) map.get("kadmin");
            if (kadmin != null) realmMap.put("admin_server", unwrapHost(kadmin));

            // add the full entry to the realmTable
            realmsTable.put(realm, realmMap);
        }

        return realmsTable;
    }

    /**
     * Calls down to JNI to get the raw Kerberos Config and maps the object
     * graph to the one that Kerberos Config in Java expects
     *
     * @return
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public static Hashtable<String, Object> getConfig() throws IOException {
        Hashtable<String, Object> stanzaTable = getKerberosConfig();
        if (stanzaTable == null) {
            throw new IOException("Could not load configuration from SCDynamicStore");
        }
        //System.out.println("Raw map from JNI: " + stanzaTable);

        // convert SCDynamicStore realm structure to Java realm structure
        Hashtable<String, ?> realms = (Hashtable<String, ?>) stanzaTable.get("realms");
        if (realms != null) {
            stanzaTable.remove("realms");
            Hashtable<String, Object> realmsTable = convertRealmConfigs(realms);
            stanzaTable.put("realms", realmsTable);
        }

       // System.out.println("stanzaTable : " + stanzaTable);
        return stanzaTable;
    }
}
