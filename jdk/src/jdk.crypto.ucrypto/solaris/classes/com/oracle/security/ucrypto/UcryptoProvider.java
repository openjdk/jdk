/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Constructor;
import java.util.*;
import java.security.*;
import static sun.security.util.SecurityConstants.PROVIDER_VER;


/**
 * OracleUcrypto provider main class.
 *
 * @since 9
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
            // platform classloader.
            provProp = AccessController.doPrivileged
                (new PrivilegedAction<>() {
                    @Override
                    public HashMap<String, ServiceDesc> run() {
                        String osname = System.getProperty("os.name");
                        if (osname.startsWith("SunOS")) {
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
                            } catch (SecurityException se) {
                                if (DEBUG) se.printStackTrace();
                            }
                        }
                        return null;
                    }
                });
            if (provProp != null) {
                boolean[] result = loadLibraries();
                if (result.length == 2) {
                    // true when libsoftcrypto or libucrypto(S12) has been successfully loaded
                    if (result[1]) {
                        String supportedMechs = getMechList();
                        debug("Prov: supported mechs = " + supportedMechs);
                        StringTokenizer st = new StringTokenizer(supportedMechs, ":,;");
                        // format: numOfSupportedMechs:[mechName,mechValue;]+
                        // skip the first one which is numberOfSupportedMechs
                        st.nextToken();
                        while (st.hasMoreTokens()) {
                            String mechName = st.nextToken();
                            int nativeMechVal = Integer.parseInt(st.nextToken());
                            try {
                                UcryptoMech m = Enum.valueOf(UcryptoMech.class, mechName);
                                m.setValue(nativeMechVal);
                                ServiceDesc[] services = m.getServiceDescriptions();
                                // defined in UcryptoMech as unsupported
                                if (services == null || services.length == 0) {
                                    debug("Skip Unsupported Algorithm: " + mechName);
                                    continue;
                                }
                                for (int p = 0; p < services.length; p++) {
                                    ServiceDesc entry = services[p];
                                    provProp.put(entry.getType() + "." + entry.getAlgorithm(),
                                                 entry);
                                }
                            } catch (IllegalArgumentException iae) {
                                // not defined in UcryptoMech
                                debug("Skip Unrecognized Algorithm: " + mechName);
                            }
                        }
                        // NOTE: GCM support is only available since jdk 7
                        provProp.put("AlgorithmParameters.GCM",
                                     sd("AlgorithmParameters", "GCM",
                                        "com.oracle.security.ucrypto.GCMParameters"));
                    }
                    // true when libmd is needed and has been successfully loaded
                    if (result[0]) {
                        for (LibMDMech m : LibMDMech.values()) {
                            ServiceDesc[] services = m.getServiceDescriptions();
                            for (ServiceDesc entry : services) {
                                String sKey = entry.getType() + "." + entry.getAlgorithm();
                                //  only register if none has been registered
                                provProp.putIfAbsent(sKey, entry);
                            }
                        }
                    };
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

        @SuppressWarnings("deprecation")
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
                    }
                    String implClass = getClassName();
                    Class<?> clz = Class.forName(implClass);
                    if (keySize != -1) {
                        Constructor<?> ctr = clz.getConstructor(int.class);
                        return ctr.newInstance(keySize);
                    } else {
                        return clz.newInstance();
                    }
                } else if (type.equals("Signature") || type.equals("MessageDigest")) {
                    String implClass = getClassName();
                    Class<?> clz = Class.forName(implClass);
                    return clz.newInstance();
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
        super("OracleUcrypto", PROVIDER_VER, "Provider using Oracle Ucrypto API");

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
        try {
            init(configArg);
        } catch (UcryptoException ue) {
            InvalidParameterException ipe =
                    new InvalidParameterException("Error using " + configArg);
            ipe.initCause(ue.getCause());
            throw ipe;
        }
        return this;
    }

    public boolean equals(Object obj) {
        return this == obj;
    }

    public int hashCode() {
        return System.identityHashCode(this);
    }
}
