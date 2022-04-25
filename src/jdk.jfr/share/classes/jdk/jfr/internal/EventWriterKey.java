/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

// Purpose of this class is NOT to create a cryptographically
// strong random number. but to quickly generate a value hard to guess
// without the need to load classes or have an impact on security
// related events, like SecureRandom::getAlgorithm("NativePRNGNonBlocking") does
public final class EventWriterKey {
    private final static long KEY = createKey();

    public static long getKey() {
        return KEY;
    }

    private static long createKey() {
        JVM jvm = JVM.getJVM();
        long r = mixMurmur64(System.identityHashCode(new Object()));
        r = 31 * r + mixMurmur64(jvm.getPid().hashCode());
        r = 31 * r + mixMurmur64(System.nanoTime());
        r = 31 * r + mixMurmur64(Thread.currentThread().getId());
        r = 31 * r + mixMurmur64(System.currentTimeMillis());
        r = 31 * r + mixMurmur64(jvm.getTypeId(JVM.class));
        r = 31 * r + mixMurmur64(JVM.counterTime());
        return mixMurmur64(r);
    }

    // Copied from jdk.internal.util.random.RandomSupport.mixMurmur64(long)
    private static long mixMurmur64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
