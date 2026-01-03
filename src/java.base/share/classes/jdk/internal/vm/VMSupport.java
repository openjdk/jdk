/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.Unsafe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;

/*
 * Support class used by JVMCI, JVMTI and VM attach mechanism.
 */
public class VMSupport {

    private static final Unsafe U = Unsafe.getUnsafe();
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

    /**
     * Decodes the exception described by {@code format} and {@code buffer} and throws it.
     *
     * @param format specifies how to interpret {@code buffer}:
     *            <pre>
     *             0: {@code buffer} was created by {@link #encodeThrowable}
     *             1: native memory for {@code buffer} could not be allocated
     *             2: an OutOfMemoryError was thrown while encoding the exception
     *             3: some other problem occured while encoding the exception. If {@code buffer != 0},
     *                it contains a {@code struct { u4 len; char[len] desc}} where {@code desc} describes the problem
     *             4: an OutOfMemoryError thrown from within VM code on a
     *                thread that cannot call Java (OOME has no stack trace)
     *            </pre>
     * @param buffer encoded info about the exception to throw (depends on {@code format})
     * @param inJVMHeap [@code true} if executing in the JVM heap, {@code false} otherwise
     * @param debug specifies whether debug stack traces should be enabled in case of translation failure
     */
    public static void decodeAndThrowThrowable(int format, long buffer, boolean inJVMHeap, boolean debug) throws Throwable {
        if (format != 0) {
            if (format == 4) {
                throw new TranslatedException(new OutOfMemoryError("in VM code and current thread cannot call Java"));
            }
            String context = String.format("while encoding an exception to translate it %s the JVM heap",
                    inJVMHeap ? "to" : "from");
            if (format == 1) {
                throw new InternalError("native buffer could not be allocated " + context);
            }
            if (format == 2) {
                throw new OutOfMemoryError(context);
            }
            if (format == 3 && buffer != 0L) {
                byte[] bytes = bufferToBytes(buffer);
                throw new InternalError("unexpected problem occurred " + context + ": " + new String(bytes, StandardCharsets.UTF_8));
            }
            throw new InternalError("unexpected problem occurred " + context);
        }
        throw TranslatedException.decodeThrowable(bufferToBytes(buffer), debug);
    }

    private static byte[] bufferToBytes(long buffer) {
        if (buffer == 0) {
            return null;
        }
        int len = U.getInt(buffer);
        byte[] bytes = new byte[len];
        U.copyMemory(null, buffer + 4, bytes, Unsafe.ARRAY_BYTE_BASE_OFFSET, len);
        return bytes;
    }

    /**
     * If {@code bufferSize} is large enough, encodes {@code throwable} into a byte array and writes
     * it to {@code buffer}. The encoding in {@code buffer} can be decoded by
     * {@link #decodeAndThrowThrowable}.
     *
     * @param throwable the exception to encode
     * @param buffer a native byte buffer
     * @param bufferSize the size of {@code buffer} in bytes
     * @return the number of bytes written into {@code buffer} if {@code bufferSize} is large
     *         enough, otherwise {@code -N} where {@code N} is the value {@code bufferSize} needs to
     *         be to fit the encoding
     */
    public static int encodeThrowable(Throwable throwable, long buffer, int bufferSize) {
        byte[] encoding = TranslatedException.encodeThrowable(throwable);
        int requiredSize = 4 + encoding.length;
        if (bufferSize < requiredSize) {
            return -requiredSize;
        }
        U.putInt(buffer, encoding.length);
        U.copyMemory(encoding, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, buffer + 4, encoding.length);
        return requiredSize;
    }
}
