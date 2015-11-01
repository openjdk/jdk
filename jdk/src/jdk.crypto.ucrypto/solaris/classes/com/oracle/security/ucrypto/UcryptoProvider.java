/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.util.*;
import java.security.*;

/**
 * OracleUcrypto provider main class.
 *
 * @since 1.9
 */
public final class UcryptoProvider extends Provider {

    private static final long serialVersionUID = 351251234302833L;

    private static boolean DEBUG = false;
    private static HashMap<String, ServiceDesc> provProp = null;
    private static String defConfigName = "";

    static {
        try {
            // cannot use LoadLibraryAction because that would make the native
            // library available to the bootclassloader, but we run in the
            // extension classloader.
            String osname = System.getProperty("os.name");
            if (osname.startsWith("SunOS")) {
                provProp = AccessController.doPrivileged
                    (new PrivilegedAction<HashMap<String, ServiceDesc>>() {
                        public HashMap<String, ServiceDesc> run() {
                            try {
                                DEBUG = Boolean.parseBoolean(System.getProperty("com.oracle.security.ucrypto.debug"));
                                String javaHome = System.getProperty("java.home");
                                String sep = System.getProperty("file.separator");
                                defConfigName = javaHome + sep + "conf" + sep + "security" + sep +
                                    "ucrypto-solaris.cfg";
                                System.loadLibrary("j2ucrypto");
                                return new HashMap<>();
                            } catch (Error err) {
                                if (DEBUG) err.printStackTrace();
                                return null;
                            } catch (SecurityException se) {
                                if (DEBUG) se.printStackTrace();
                                return null;
                            }
                        }
                    });
            }
            if (provProp != null) {
                boolean[] result = loadLibraries();
                if (result.length == 2) {
                    if (result[0]) { // successfully loaded libmd
                        provProp.put("MessageDigest.MD5",
                            sd("MessageDigest", "MD5",
                               "com.oracle.security.ucrypto.NativeDigest$MD5"));
                        provProp.put("MessageDigest.SHA",
                            sd("MessageDigest", "SHA",
                               "com.oracle.security.ucrypto.NativeDigest$SHA1",
                               "SHA-1", "SHA1"));
                        provProp.put("MessageDigest.SHA-256",
                            sd("MessageDigest", "SHA-256",
                               "com.oracle.security.ucrypto.NativeDigest$SHA256",
                               "2.16.840.1.101.3.4.2.1", "OID.2.16.840.1.101.3.4.2.1"));

                        provProp.put("MessageDigest.SHA-384",
                            sd("MessageDigest", "SHA-384",
                               "com.oracle.security.ucrypto.NativeDigest$SHA384",
                               "2.16.840.1.101.3.4.2.2", "OID.2.16.840.1.101.3.4.2.2"));

                        provProp.put("MessageDigest.SHA-512",
                            sd("MessageDigest", "SHA-512",
                               "com.oracle.security.ucrypto.NativeDigest$SHA512",
                               "2.16.840.1.101.3.4.2.3", "OID.2.16.840.1.101.3.4.2.3"));
                    };
                    if (result[1]) { // successfully loaded libsoftcrypto
                        String supportedMechs = getMechList();
                        debug("Prov: supported mechs = " + supportedMechs);
                        for (UcryptoMech m : UcryptoMech.values()) {
                            if (supportedMechs.indexOf(m.name() + ",") != -1) {
                                ServiceDesc[] services = m.getServiceDescriptions();
                                // skip unsupported UcryptoMech
                                if (services == null || services.length == 0) continue;
                                for (int p = 0; p < services.length; p++) {
                                    ServiceDesc entry = services[p];
                                    provProp.put(entry.getType() + "." + entry.getAlgorithm(),
                                                 entry);
                                }
                            }
                        }
                        // NOTE: GCM support is only available since jdk 7
                        provProp.put("AlgorithmParameters.GCM",
                                     sd("AlgorithmParameters", "GCM", "com.oracle.security.ucrypto.GCMParameters"));
                    }
                } else {
                    debug("Prov: unexpected ucrypto library loading error, got " + result.length);
                }
            }
        } catch (AccessControlException ace) {
            if (DEBUG) ace.printStackTrace();
            // disable Ucrypto provider
            provProp = null;
        }
    }

    private static ServiceDesc sd(String type, String algo, String cn,
        String... aliases) {
        return new ServiceDesc(type, algo, cn, aliases);
    }

    private static final class ProviderService extends Provider.Service {
        ProviderService(Provider p, ServiceDesc sd) {
            super(p, sd.getType(), sd.getAlgorithm(), sd.getClassName(),
                  sd.getAliases(), null);
        }

        @Override
        public Object newInstance(Object ctrParamObj)
            throws NoSuchAlgorithmException {
            String type = getType();
            if (ctrParamObj != null) {
                throw new InvalidParameterException
                    ("constructorParameter not used with " + type + " engines");
            }
            String algo = getAlgorithm();
            try {
                if (type.equals("Cipher")) {
                    int keySize = -1;
                    if (algo.charAt(3) == '_') {
                        keySize = Integer.parseInt(algo.substring(4, 7))/8;
                        algo = algo.substring(0, 3) + algo.substring(7);
                    }
                    if (algo.equals("AES/ECB/NoPadding")) {
                        return new NativeCipher.AesEcbNoPadding(keySize);
                    } else if (algo.equals("AES/ECB/PKCS5Padding")) {
                        return new NativeCipherWithJavaPadding.AesEcbPKCS5();
                    } else if (algo.equals("AES/CBC/NoPadding")) {
                        return new NativeCipher.AesCbcNoPadding(keySize);
                    } else if (algo.equals("AES/CBC/PKCS5Padding")) {
                        return new NativeCipherWithJavaPadding.AesCbcPKCS5();
                    } else if (algo.equals("AES/CTR/NoPadding")) {
                        return new NativeCipher.AesCtrNoPadding();
                    } else if (algo.equals("AES/GCM/NoPadding")) {
                        return new NativeGCMCipher.AesGcmNoPadding(keySize);
                    } else if (algo.equals("AES/CFB128/NoPadding")) {
                        return new NativeCipher.AesCfb128NoPadding();
                    } else if (algo.equals("AES/CFB128/PKCS5Padding")) {
                        return new NativeCipherWithJavaPadding.AesCfb128PKCS5();
                    } else if (algo.equals("RSA/ECB/NoPadding")) {
                        return new NativeRSACipher.NoPadding();
                    } else if (algo.equals("RSA/ECB/PKCS1Padding")) {
                        return new NativeRSACipher.PKCS1Padding();
                    }
                } else if (type.equals("Signature")) {
                    if (algo.equals("SHA1withRSA")) {
                        return new NativeRSASignature.SHA1();
                    } else if (algo.equals("SHA256withRSA")) {
                        return new NativeRSASignature.SHA256();
                    } else if (algo.equals("SHA384withRSA")) {
                        return new NativeRSASignature.SHA384();
                    } else if (algo.equals("SHA512withRSA")) {
                        return new NativeRSASignature.SHA512();
                    } else if (algo.equals("MD5withRSA")) {
                        return new NativeRSASignature.MD5();
                    }
                } else if (type.equals("MessageDigest")) {
                    if (algo.equals("SHA")) {
                        return new NativeDigest.SHA1();
                    } else if (algo.equals("SHA-256")) {
                        return new NativeDigest.SHA256();
                    } else if (algo.equals("SHA-384")) {
                        return new NativeDigest.SHA384();
                    } else if (algo.equals("SHA-512")) {
                        return new NativeDigest.SHA512();
                    } else if (algo.equals("MD5")) {
                        return new NativeDigest.MD5();
                    }
                } else if (type.equals("AlgorithmParameters")) {
                    if (algo.equals("GCM")) {
                        return new GCMParameters();
                    }
                }
            } catch (Exception ex) {
                throw new NoSuchAlgorithmException("Error constructing " +
                    type + " for " + algo + " using OracleUcrypto", ex);
            }
            throw new ProviderException("No impl for " + algo +
                " " + type);
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
        super("OracleUcrypto", 9.0d, "Provider using Oracle Ucrypto API");

        AccessController.doPrivileged(new PrivilegedAction<>() {
            public Void run() {
                init(defConfigName);
                return null;
            }
        });
        if (provider == null) provider = this;
    }

    private void init(final String configName) {
        if (provProp != null) {
            debug("Prov: configuration file " + configName);
            Config c;
            try {
                c = new Config(configName);
            } catch (Exception ex) {
                throw new UcryptoException("Error parsing Config", ex);
            }

            String[] disabledServices = c.getDisabledServices();
            for (String ds : disabledServices) {
                if (provProp.remove(ds) != null) {
                    debug("Prov: remove config-disabled service " + ds);
                } else {
                    debug("Prov: ignore unsupported service " + ds);
                }
            }

            for (ServiceDesc s: provProp.values()) {
                debug("Prov: add service for " + s);
                putService(new ProviderService(this, s));
            }
        }
    }

    @Override
    public Provider configure(String configArg) throws InvalidParameterException {
        // default policy entry only grants read access to default config
        if (!defConfigName.equals(configArg)) {
            throw new InvalidParameterException("Ucrypto provider can only be " +
                "configured with default configuration file");
        }
        // re-read the config
        init(defConfigName);
        return this;
    }

    public boolean equals(Object obj) {
        return this == obj;
    }

    public int hashCode() {
        return System.identityHashCode(this);
    }
}
