/*
 * Copyright (c) 2005, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes;

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
     * Write the given properties list to a byte array and return it. Properties with
     * a key or value that is not a String is filtered out. The stream written to the byte
     * array is ISO 8859-1 encoded.
     */
    private static byte[] serializePropertiesToByteArray(Properties p) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        PrintWriter bw = new PrintWriter(new OutputStreamWriter(out, "8859_1"));

        bw.println("#" + new Date().toString());

        try {
            for (String key : p.stringPropertyNames()) {
                String val = p.getProperty(key);
                key = toISO88591(toEscapeSpecialChar(toEscapeSpace(key)));
                val = toISO88591(toEscapeSpecialChar(val));
                bw.println(key + "=" + val);
            }
        } catch (CharacterCodingException e) {
            throw new RuntimeException(e);
        }
        bw.flush();

        return out.toByteArray();
    }

    private static String toEscapeSpecialChar(String source) {
        return source.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r").replace("\f", "\\f");
    }

    private static String toEscapeSpace(String source) {
        return source.replace(" ", "\\ ");
    }

    private static String toISO88591(String source) throws CharacterCodingException {
        var charBuf = CharBuffer.wrap(source);
        // 6 is 2 bytes for '\\u' as String and 4 bytes for code point.
        var byteBuf = ByteBuffer.allocate(charBuf.length() * 6);
        var encoder = StandardCharsets.ISO_8859_1
                .newEncoder()
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        CoderResult result;
        do {
            result = encoder.encode(charBuf, byteBuf, false);
            if (result.isUnmappable()) {
                byteBuf.put(String.format("\\u%04X", (int)charBuf.get()).getBytes());
            } else if (result.isError()) {
                result.throwException();
            }
        } while (result.isError());

        return new String(byteBuf.array(), 0, byteBuf.position());
    }

    public static byte[] serializePropertiesToByteArray() throws IOException {
        return serializePropertiesToByteArray(System.getProperties());
    }

    public static byte[] serializeAgentPropertiesToByteArray() throws IOException {
        return serializePropertiesToByteArray(getAgentProperties());
    }

    /*
     * Returns true if the given JAR file has the Class-Path attribute in the
     * main section of the JAR manifest. Throws RuntimeException if the given
     * path is not a JAR file or some other error occurs.
     */
    public static boolean isClassPathAttributePresent(String path) {
        try {
            Manifest man = (new JarFile(path)).getManifest();
            if (man != null) {
                if (man.getMainAttributes().getValue(Attributes.Name.CLASS_PATH) != null) {
                    return true;
                }
            }
            return false;
        } catch (IOException ioe) {
            throw new RuntimeException(ioe.getMessage());
        }
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
