/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.vm;

import jdk.internal.access.SharedSecrets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;

/*
 * Support class used by JVMTI and VM attach mechanism.
 */
public class VMSupport {

    private static Properties agentProps = null;

    /**
     * Returns the agent properties.
     */
    public static synchronized Properties getAgentProperties() {
        if (agentProps == null) {
            agentProps = new Properties();
            initAgentProperties(agentProps);
        }
        return agentProps;
    }
    private static native Properties initAgentProperties(Properties props);

    /**
     * Writes the given properties list to a byte array and return it. The stream written
     * to the byte array is ISO 8859-1 encoded.
     */
    private static byte[] serializePropertiesToByteArray(Properties p) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        p.store(out, null);
        return out.toByteArray();
    }

    /**
     * @return a Properties object containing only the entries in {@code p}
     *          whose key and value are both Strings
     */
    private static Properties onlyStrings(Properties p) {
        Properties props = new Properties();

        // stringPropertyNames() returns a snapshot of the property keys
        Set<String> keyset = p.stringPropertyNames();
        for (String key : keyset) {
            String value = p.getProperty(key);
            props.put(key, value);
        }
        return props;
    }

    public static byte[] serializePropertiesToByteArray() throws IOException {
        return serializePropertiesToByteArray(onlyStrings(System.getProperties()));
    }

    public static byte[] serializeSecurityPropertiesToByteArray() throws IOException {
        Properties p = SharedSecrets.getJavaSecurityPropertiesAccess().getCurrentProperties();
        return serializePropertiesToByteArray(onlyStrings(p));
    }

    public static byte[] serializeAgentPropertiesToByteArray() throws IOException {
        return serializePropertiesToByteArray(onlyStrings(getAgentProperties()));
    }

    /*
     * Return the temporary directory that the VM uses for the attach
     * and perf data files.
     *
     * It is important that this directory is well-known and the
     * same for all VM instances. It cannot be affected by configuration
     * variables such as java.io.tmpdir.
     */
    public static native String getVMTemporaryDirectory();
}
