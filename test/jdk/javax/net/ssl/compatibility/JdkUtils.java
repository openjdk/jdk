/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLParameters;

/*
 * This class is used for returning some specific JDK information.
 */
public class JdkUtils {

    public static final String JAVA_RUNTIME_VERSION = "javaRuntimeVersion";
    public static final String SUPPORTS_EC_KEY = "supportsECKey";
    public static final String SUPPORTS_SNI = "supportsSNI";
    public static final String SUPPORTS_ALPN = "supportsALPN";

    // Returns the JDK build version.
    public static String javaRuntimeVersion() {
        return System.getProperty("java.runtime.version");
    }

    // Checks if EC key algorithm is supported by the JDK build.
    private static boolean supportsECKey() {
        boolean isSupported = true;
        try {
            KeyFactory.getInstance("EC");
        } catch (NoSuchAlgorithmException e) {
            isSupported = false;
        }
        return isSupported;
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

    public static void main(String[] args) {
        System.out.print(Utils.join(Utils.PARAM_DELIMITER,
                attr(JAVA_RUNTIME_VERSION, javaRuntimeVersion()),
                attr(SUPPORTS_EC_KEY, supportsECKey()),
                attr(SUPPORTS_SNI, supportsSNI()),
                attr(SUPPORTS_ALPN, supportsALPN())));
    }

    private static String attr(String name, Object value) {
        return name + "=" + String.valueOf(value);
    }
}
