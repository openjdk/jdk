/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.security.ucrypto;

import java.io.IOException;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.security.*;
import sun.security.action.PutAllAction;
import sun.security.action.GetPropertyAction;

/**
 * OracleUcrypto provider main class.
 *
 * @since 1.9
 */
public final class UcryptoProvider extends Provider {

    private static final long serialVersionUID = 351251234302833L;

    private static boolean DEBUG;
    private static HashMap<String, String> provProp;

    static {
        try {
            DEBUG = Boolean.parseBoolean(AccessController.doPrivileged
                (new GetPropertyAction("com.oracle.security.ucrypto.debug")));

            // cannot use LoadLibraryAction because that would make the native
            // library available to the bootclassloader, but we run in the
            // extension classloader.
            provProp = AccessController.doPrivileged
                (new PrivilegedAction<HashMap<String, String>>() {
                    public HashMap<String, String> run() {
                        try {
                            System.loadLibrary("j2ucrypto");
                            String osname = System.getProperty("os.name");
                            if (osname.startsWith("SunOS")) {
                                return new HashMap<String, String>();
                            } else return null;
                        } catch (Error err) {
                            return null;
                        } catch (SecurityException se) {
                            return null;
                        }
                    }
                });
            if (provProp != null) {
                boolean[] result = loadLibraries();
                if (result.length == 2) {
                    if (result[0]) { // successfully loaded libmd
                        provProp.put("MessageDigest.MD5",
                                     "com.oracle.security.ucrypto.NativeDigest$MD5");
                        provProp.put("MessageDigest.SHA",
                                     "com.oracle.security.ucrypto.NativeDigest$SHA1");
                        provProp.put("Alg.Alias.MessageDigest.SHA-1", "SHA");
                        provProp.put("Alg.Alias.MessageDigest.SHA1", "SHA");
                        provProp.put("MessageDigest.SHA-256",
                                     "com.oracle.security.ucrypto.NativeDigest$SHA256");
                        provProp.put("Alg.Alias.MessageDigest.2.16.840.1.101.3.4.2.1", "SHA-256");
                        provProp.put("Alg.Alias.MessageDigest.OID.2.16.840.1.101.3.4.2.1", "SHA-256");

                        provProp.put("MessageDigest.SHA-384",
                                     "com.oracle.security.ucrypto.NativeDigest$SHA384");
                        provProp.put("Alg.Alias.MessageDigest.2.16.840.1.101.3.4.2.2", "SHA-384");
                        provProp.put("Alg.Alias.MessageDigest.OID.2.16.840.1.101.3.4.2.2", "SHA-384");

                        provProp.put("MessageDigest.SHA-512",
                                     "com.oracle.security.ucrypto.NativeDigest$SHA512");
                        provProp.put("Alg.Alias.MessageDigest.2.16.840.1.101.3.4.2.3", "SHA-512");
                        provProp.put("Alg.Alias.MessageDigest.OID.2.16.840.1.101.3.4.2.3", "SHA-512");

                    }
                    if (result[1]) { // successfully loaded libsoftcrypto
                        String supportedMechs = getMechList();
                        debug("Prov: supported mechs = " + supportedMechs);
                        for (UcryptoMech m : UcryptoMech.values()) {
                            if (supportedMechs.indexOf(m.name() + ",") != -1) {
                                String[] jceProps = m.jceProperties();
                                // skip unsupported UcryptoMech
                                if (jceProps == null) continue;
                                for (int p = 0; p < jceProps.length; p++) {
                                    StringTokenizer st =
                                        new StringTokenizer(jceProps[p], ";");
                                    if (st.countTokens() != 2) {
                                        throw new RuntimeException("Wrong format: " + jceProps[p]);
                                    }
                                    provProp.put(st.nextToken(), st.nextToken());
                                }
                            }
                        }
                        // NOTE: GCM support is only available since jdk 7
                        provProp.put("AlgorithmParameters.GCM",
                                     "com.oracle.security.ucrypto.GCMParameters");
                    }
                } else {
                    debug("Prov: unexpected ucrypto library loading error, got " + result.length);
                }
            }
        } catch (AccessControlException ace) {
            // disable Ucrypto provider
            DEBUG = false;
            provProp = null;
        }
    }

    static Provider provider = null;
    private static native boolean[] loadLibraries();
    private static native String getMechList();

    static void debug(String msg) {
        if (DEBUG) {
            System.out.println("UCrypto/" + msg);
        }
    }

    public UcryptoProvider() {
        super("OracleUcrypto", 1.9d, "Provider using Oracle Ucrypto API");
        if (provProp != null) {
            AccessController.doPrivileged(new PutAllAction(this, provProp));
        }
        if (provider == null) provider = this;
    }

    public UcryptoProvider(String configName) {
        super("OracleUcrypto", 1.9d, "Provider using Oracle Ucrypto API");
        try {
            if (provProp != null) {
                HashMap<String, String> customProvProp =
                    new HashMap<String, String>(provProp);
                Config c = new Config(configName);
                String[] disabledServices = c.getDisabledServices();
                for (int i = 0; i < disabledServices.length; i++) {
                    if (customProvProp.remove(disabledServices[i]) != null) {
                        debug("Prov: remove config-disabled service " + disabledServices[i]);
                    } else {
                        debug("Prov: ignore unsupported config-disabled service " +
                              disabledServices[i]);
                    }
                }
                AccessController.doPrivileged(new PutAllAction(this, customProvProp));
            }
        } catch (IOException ioe) { // thrown by Config
            throw new UcryptoException("Error parsing Config", ioe);
        }
        if (provider == null) provider = this;
    }

    public boolean equals(Object obj) {
        return this == obj;
    }

    public int hashCode() {
        return System.identityHashCode(this);
    }
}
