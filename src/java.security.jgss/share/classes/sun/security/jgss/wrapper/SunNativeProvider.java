/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.jgss.wrapper;

import java.io.Serial;
import java.util.HashMap;
import java.security.Provider;
import java.security.AccessController;
import java.security.PrivilegedAction;

import jdk.internal.util.OperatingSystem;
import jdk.internal.util.StaticProperty;
import org.ietf.jgss.Oid;
import sun.security.action.GetBooleanAction;
import sun.security.action.PutAllAction;
import static sun.security.util.SecurityConstants.PROVIDER_VER;

/**
 * Defines the Sun NativeGSS provider for plugging in the
 * native GSS mechanisms to Java GSS.
 *
 * List of supported mechanisms depends on the local
 * machine configuration.
 *
 * @author Yu-Ching Valerie Peng
 */

public final class SunNativeProvider extends Provider {

    @Serial
    private static final long serialVersionUID = -238911724858694204L;

    private static final String NAME = "SunNativeGSS";
    private static final String INFO = "Sun Native GSS provider";
    private static final String MF_CLASS =
        "sun.security.jgss.wrapper.NativeGSSFactory";

    static final boolean DEBUG =
        GetBooleanAction.privilegedGetProperty("sun.security.nativegss.debug");

    static void debug(String message) {
        if (message == null) {
            throw new NullPointerException();
        }
        System.err.println(NAME + ": " + message);
    }

    @SuppressWarnings("removal")
    private static final HashMap<String, String> MECH_MAP =
            AccessController.doPrivileged(
                new PrivilegedAction<>() {
                    public HashMap<String, String> run() {
                        try {
                            // Ensure the InetAddress class is loaded before
                            // loading j2gss. The library will access this class
                            // and a deadlock might happen. See JDK-8210373.
                            Class.forName("java.net.InetAddress");
                            System.loadLibrary("j2gss");
                        } catch (ClassNotFoundException | Error err) {
                            if (DEBUG) {
                                debug("No j2gss library found!");
                                err.printStackTrace();
                            }
                            return null;
                        }
                        String[] gssLibs;
                        String defaultLib
                                = System.getProperty("sun.security.jgss.lib");
                        if (defaultLib == null || defaultLib.trim().equals("")) {
                            gssLibs = switch (OperatingSystem.current()) {
                                case LINUX -> new String[]{
                                        "libgssapi.so",
                                        "libgssapi_krb5.so",
                                        "libgssapi_krb5.so.2",
                                };
                                case MACOS -> new String[]{
                                        "libgssapi_krb5.dylib",
                                        "/usr/lib/sasl2/libgssapiv2.2.so",
                                };
                                case WINDOWS -> new String[]{
                                        // Full path needed, DLL is in jre/bin
                                        StaticProperty.javaHome() + "\\bin\\sspi_bridge.dll",
                                };
                                case AIX -> new String[]{
                                        "/opt/freeware/lib64/libgssapi_krb5.so",
                                };
                                default -> new String[0];
                            };
                        } else {
                            gssLibs = new String[]{ defaultLib };
                        }
                        for (String libName: gssLibs) {
                            if (GSSLibStub.init(libName, DEBUG)) {
                                if (DEBUG) {
                                    debug("Loaded GSS library: " + libName);
                                }
                                Oid[] mechs = GSSLibStub.indicateMechs();
                                HashMap<String,String> map = new HashMap<>();
                                for (int i = 0; i < mechs.length; i++) {
                                    if (DEBUG) {
                                        debug("Native MF for " + mechs[i]);
                                    }
                                    map.put("GssApiMechanism." + mechs[i],
                                            MF_CLASS);
                                }
                                return map;
                            }
                        }
                        return null;
                    }
                });

    // initialize INSTANCE after MECH_MAP is constructed
    static final Provider INSTANCE = new SunNativeProvider();

    @SuppressWarnings("removal")
    public SunNativeProvider() {
        /* We are the Sun NativeGSS provider */
        super(NAME, PROVIDER_VER, INFO);

        if (MECH_MAP != null) {
            AccessController.doPrivileged(new PutAllAction(this, MECH_MAP));
        }
    }
}
