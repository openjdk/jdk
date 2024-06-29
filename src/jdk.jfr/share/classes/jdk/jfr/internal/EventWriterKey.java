/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal;

import java.io.InputStream;

// Purpose of this class is NOT to create a cryptographically
// strong random number. but to quickly generate a value hard to guess
// without the need to load classes or have an impact on security
// related events, like SecureRandom::getAlgorithm("NativePRNGNonBlocking") does
public final class EventWriterKey {
    private static final long KEY = createKey();
    private static boolean loaded;
    private static boolean logged;

    public static long getKey() {
        return KEY;
    }

    private static long createKey() {
        long r = mixMurmur64(System.identityHashCode(new Object()));
        r = 31 * r + mixMurmur64(JVM.getPid().hashCode());
        r = 31 * r + mixMurmur64(System.nanoTime());
        r = 31 * r + mixMurmur64(Thread.currentThread().threadId());
        r = 31 * r + mixMurmur64(System.currentTimeMillis());
        r = 31 * r + mixMurmur64(JVM.getTypeId(JVM.class));
        r = 31 * r + mixMurmur64(JVM.counterTime());
        return mixMurmur64(r);
    }

    // Copied from jdk.internal.util.random.RandomSupport.mixMurmur64(long)
    private static long mixMurmur64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    public static void ensureEventWriterFactory() {
        if (loaded) {
            return;
        }
        String name = "/jdk/jfr/internal/EventWriterFactoryRecipe.class";
        try (InputStream is = EventWriterKey.class.getResourceAsStream(name)) {
            byte[] bytes = is.readAllBytes();
            bytes = replace(bytes,
                    "jdk/jfr/internal/EventWriterFactoryRecipe",
                    "jdk/jfr/internal/event/EventWriterFactory");
            Class<?> c = Class.forName("jdk.jfr.internal.event.EventWriter");
            SecuritySupport.defineClass(c, bytes);
            loaded = true;
        } catch (Throwable e) {
           throw new InternalError("Could not read bytecode for " + name, e);
        }
        Logger.log(LogTag.JFR_SYSTEM, LogLevel.DEBUG, "EventWriterFactory created");
    }

    // Starve the system of resources to prevent further attempts.
    // Note, code that have the capability to invoke this method
    // could spin in a loop anyway. Alternatives, such as System.exit(1),
    // may provide caller with additional capabilities.
    public static void block() {
        // Making this field variable a local variable leads to CTW failure
        logged = false;
        while (true) {
            try {
                if (!logged) {
                    // Only log once to prevent flooding of log.
                    logged = true;
                    // Purposely don't call Thread::getName() since it can be overridden
                    Logger.log(LogTag.JFR, LogLevel.ERROR, "Malicious attempt to access JFR buffers. Stopping thread from further execution.");
                }
            } catch (Throwable t) {
                // Ensure code can't break out and retry
            }
        }
    }

    private static byte[] replace(byte[] bytes, String match, String replacement) {
        if (match.length() != replacement.length()) {
            throw new IllegalArgumentException("Match must be same size as replacement");
        }
        for (int i = 0; i < bytes.length - match.length(); i++) {
            if (match(bytes, i, match)) {
                for (int j = 0; j < replacement.length(); j++) {
                    bytes[i + j] = (byte) replacement.charAt(j);
                }
            }
        }
        return bytes;
    }

    private static boolean match(byte[] bytes, int offset, String text) {
        for (int i = 0; i < text.length(); i++) {
            if (bytes[offset + i] != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
