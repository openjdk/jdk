/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
    private static boolean DEBUG = sun.security.krb5.internal.Krb5.DEBUG;

    static {
        boolean isMac = java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Boolean>() {
                public Boolean run() {
                    String osname = System.getProperty("os.name");
                    if (osname.contains("OS X")) {
                        System.loadLibrary("osxkrb5");
                        return true;
                    }
                    return false;
                }
            });
        if (isMac) installNotificationCallback();
    }

    private static Vector<String> unwrapHost(
            Collection<Hashtable<String, String>> c) {
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
    private static Hashtable<String, Object>
            convertRealmConfigs(Hashtable<String, ?> configs) {
        Hashtable<String, Object> realmsTable = new Hashtable<String, Object>();

        for (String realm : configs.keySet()) {
            // get the kdc
            Hashtable<String, Collection<?>> map =
                    (Hashtable<String, Collection<?>>) configs.get(realm);
            Hashtable<String, Vector<String>> realmMap =
                    new Hashtable<String, Vector<String>>();

            // put the kdc into the realmMap
            Collection<Hashtable<String, String>> kdc =
                    (Collection<Hashtable<String, String>>) map.get("kdc");
            if (kdc != null) realmMap.put("kdc", unwrapHost(kdc));

            // put the admin server into the realmMap
            Collection<Hashtable<String, String>> kadmin =
                    (Collection<Hashtable<String, String>>) map.get("kadmin");
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
    public static Hashtable<String, Object> getConfig() throws IOException {
        Hashtable<String, Object> stanzaTable = getKerberosConfig();
        if (stanzaTable == null) {
            throw new IOException(
                    "Could not load configuration from SCDynamicStore");
        }
        if (DEBUG) System.out.println("Raw map from JNI: " + stanzaTable);
        return convertNativeConfig(stanzaTable);
    }

    @SuppressWarnings("unchecked")
    private static Hashtable<String, Object> convertNativeConfig(
            Hashtable<String, Object> stanzaTable) {
        // convert SCDynamicStore realm structure to Java realm structure
        Hashtable<String, ?> realms =
                (Hashtable<String, ?>) stanzaTable.get("realms");
        if (realms != null) {
            stanzaTable.remove("realms");
            Hashtable<String, Object> realmsTable = convertRealmConfigs(realms);
            stanzaTable.put("realms", realmsTable);
        }
        WrapAllStringInVector(stanzaTable);
        if (DEBUG) System.out.println("stanzaTable : " + stanzaTable);
        return stanzaTable;
    }

    @SuppressWarnings("unchecked")
    private static void WrapAllStringInVector(
            Hashtable<String, Object> stanzaTable) {
        for (String s: stanzaTable.keySet()) {
            Object v = stanzaTable.get(s);
            if (v instanceof Hashtable) {
                WrapAllStringInVector((Hashtable<String,Object>)v);
            } else if (v instanceof String) {
                Vector<String> vec = new Vector<>();
                vec.add((String)v);
                stanzaTable.put(s, vec);
            }
        }
    }
}
