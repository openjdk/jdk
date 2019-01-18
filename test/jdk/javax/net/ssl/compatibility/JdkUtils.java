/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

/*
 * This class is used for returning some specific JDK information.
 */
public class JdkUtils {

    public static final String JAVA_RUNTIME_VERSION = "javaRuntimeVersion";
    public static final String SUPPORTED_PROTOCOLS = "supportedProtocols";
    public static final String SUPPORTED_CIPHER_SUITES = "supportedCipherSuites";
    public static final String SUPPORTS_SNI = "supportsSNI";
    public static final String SUPPORTS_ALPN = "supportsALPN";

    // Returns the JDK build version.
    public static String javaRuntimeVersion() {
        return System.getProperty("java.runtime.version");
    }

    private static String supportedProtocols() {
        StringBuilder protocols = new StringBuilder();
        for (String protocol : new String[] {
                "TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3" }) {
            if (supportsProtocol(protocol)) {
                protocols.append(protocol).append(Utils.VALUE_DELIMITER);
            }
        }
        return protocols.toString().substring(
                0, protocols.toString().length() - 1);
    }

    private static boolean supportsProtocol(String protocol) {
        boolean supported = true;
        try {
            SSLContext.getInstance(protocol);
        } catch (NoSuchAlgorithmException e) {
            supported = false;
        }
        return supported;
    }

    private static String supportedCipherSuites() {
        StringBuilder cipherSuites = new StringBuilder();
        String[] supportedCipherSuites = ((SSLSocketFactory) SSLSocketFactory
                .getDefault()).getSupportedCipherSuites();
        for (int i = 0; i < supportedCipherSuites.length - 1; i++) {
            cipherSuites.append(supportedCipherSuites[i])
                    .append(Utils.VALUE_DELIMITER);
        }
        cipherSuites.append(
                supportedCipherSuites[supportedCipherSuites.length - 1]);
        return cipherSuites.toString();
    }

    // Checks if SNI is supported by the JDK build.
    private static boolean supportsSNI() {
        boolean isSupported = true;
        try {
            SSLParameters.class.getMethod("getServerNames");
        } catch (NoSuchMethodException e) {
            isSupported = false;
        }
        return isSupported;
    }

    // Checks if ALPN is supported by the JDK build.
    private static boolean supportsALPN() {
        boolean isSupported = true;
        try {
            SSLParameters.class.getMethod("getApplicationProtocols");
        } catch (NoSuchMethodException e) {
            isSupported = false;
        }
        return isSupported;
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        System.out.print(Utils.join(Utils.PARAM_DELIMITER,
                attr(JAVA_RUNTIME_VERSION, javaRuntimeVersion()),
                attr(SUPPORTED_PROTOCOLS, supportedProtocols()),
                attr(SUPPORTED_CIPHER_SUITES, supportedCipherSuites()),
                attr(SUPPORTS_SNI, supportsSNI()),
                attr(SUPPORTS_ALPN, supportsALPN())));
    }

    private static String attr(String name, Object value) {
        return name + "=" + String.valueOf(value);
    }
}
