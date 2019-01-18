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

/*
 * It represents a JDK with some specific attributes.
 * If two JdkInfo instances have the same version value, the instances are
 * regarded as equivalent.
 */
public class JdkInfo {

    public final String jdkPath;

    public final String version;
    public final String supportedProtocols;
    public final String supportedCipherSuites;
    public final boolean supportsSNI;
    public final boolean supportsALPN;

    public JdkInfo(String jdkPath) throws Throwable {
        this.jdkPath = jdkPath;

        String output = jdkAttributes(jdkPath);
        if (output == null || output.trim().isEmpty()) {
            throw new RuntimeException(
                    "Cannot determine the JDK attributes: " + jdkPath);
        }

        String[] attributes = Utils.split(output, Utils.PARAM_DELIMITER);
        version = attributes[0].replaceAll(".*=", "");
        supportedProtocols = attributes[1].replaceAll(".*=", "");
        supportedCipherSuites = attributes[2].replaceAll(".*=", "");
        supportsSNI = Boolean.valueOf(attributes[3].replaceAll(".*=", ""));
        supportsALPN = Boolean.valueOf(attributes[4].replaceAll(".*=", ""));
    }

    // Determines the specific attributes for the specified JDK.
    private static String jdkAttributes(String jdkPath) throws Throwable {
        return ProcessUtils.java(jdkPath, null, JdkUtils.class).getOutput();
    }

    @Override
    public int hashCode() {
        return version == null ? 0 : version.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        JdkInfo other = (JdkInfo) obj;
        if (version == null) {
            if (other.version != null) {
                return false;
            }
        } else if (!version.equals(other.version)) {
            return false;
        }
        return true;
    }

    public boolean supportsProtocol(Protocol protocol) {
        return supportedProtocols.contains(protocol.name);
    }

    public boolean supportsCipherSuite(CipherSuite cipherSuite) {
        return supportedCipherSuites.contains(cipherSuite.name());
    }
}
