/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import jdk.internal.vm.Continuation;

public final class StringPool {
    public static final int MIN_LIMIT = 16;
    public static final int MAX_LIMIT = 131072; /* 0 MAX means disabled */
    private static final int PRECACHE_THRESHOLD = 128;
    private static final long DO_NOT_POOL = -1;
    /* max size */
    private static final int MAX_SIZE = 32 * 1024;
    /* max size bytes */
    private static final long MAX_SIZE_UTF16 = 16 * 1024 * 1024;
    /* mask for constructing generation relative string id. */
    private static final long SID_MASK = -65536;
    /* string id index */
    private static final AtomicLong sidIdx = new AtomicLong(1);
    /* looking at a biased data set 4 is a good value */
    private static final String[] preCache = new String[] { "", "", "", "" };
    /* the cache */
    private static final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<>(MAX_SIZE, 0.75f);
    /* loop mask */
    private static final int preCacheMask = 0x03;
    /* index of oldest */
    private static int preCacheOld = 0;
    /* max size bytes */
    private static long currentSizeUTF16;
    /* The string pool epoch generation is the range [1-32767] set by the JVM on epoch shift. Not private to avoid being optimized away. */
    static short generation = 0;

    /* internalSid is a composite id [48-bit externalSid][16-bit generation]. */
    private static boolean isCurrentGeneration(long internalSid) {
        return generation == (short)internalSid;
    }

    private static long updateInternalSid(long internalSid) {
        return (internalSid & SID_MASK) | generation;
    }

    private static long nextInternalSid() {
        return sidIdx.getAndIncrement() << 16  | generation;
    }

    /* externalSid is the most significant 48-bits of the internalSid. */
    private static long externalSid(long internalSid) {
        return internalSid >> 16;
    }

    /* Explicitly pin a virtual thread before acquiring the string pool monitor
     * because migrating the EventWriter onto another carrier thread is impossible.
     */
    private static long storeString(String s, boolean pinVirtualThread) {
        if (pinVirtualThread) {
            assert(Thread.currentThread().isVirtual());
            Continuation.pin();
        }
        try {
            /* synchronized because of writing the string to the JVM. */
            synchronized (StringPool.class) {
                Long lsid = cache.get(s);
                long internalSid;
                if (lsid != null) {
                    internalSid = lsid.longValue();
                    if (isCurrentGeneration(internalSid)) {
                        // Someone already updated the cache.
                        return externalSid(internalSid);
                    }
                    internalSid = updateInternalSid(internalSid);
                } else {
                    // Not yet added or the cache was cleared.
                    internalSid = nextInternalSid();
                    currentSizeUTF16 += s.length();
                }
                long extSid = externalSid(internalSid);
                // Write the string to the JVM before publishing to the cache.
                JVM.addStringConstant(extSid, s);
                cache.put(s, internalSid);
                return extSid;
            }
        } finally {
            if (pinVirtualThread) {
                assert(Thread.currentThread().isVirtual());
                Continuation.unpin();
            }
        }
    }

    /* a string fetched from the string pool must be of the current generation */
    private static long ensureCurrentGeneration(String s, Long lsid, boolean pinVirtualThread) {
        long internalSid = lsid.longValue();
        return isCurrentGeneration(internalSid) ? externalSid(internalSid) : storeString(s, pinVirtualThread);
    }

    /*
     * The string pool uses a generational id scheme to sync the JVM and Java sides.
     * The string pool relies on the EventWriter and its implementation, especially
     * its ability to restart event write attempts on interleaving epoch shifts.
     * Even though a string id is generationally valid during StringPool lookup,
     * the JVM can evolve the generation before the event is committed,
     * effectively invalidating the fetched string id. The event restart mechanism
     * of the EventWriter ensures that committed strings are in the correct generation.
     */
    public static long addString(String s, boolean pinVirtualThread) {
        Long lsid = cache.get(s);
        if (lsid != null) {
            return ensureCurrentGeneration(s, lsid, pinVirtualThread);
        }
        if (s.length() <= PRECACHE_THRESHOLD && !preCache(s)) {
            /* we should not pool this string */
            return DO_NOT_POOL;
        }
        if (cache.size() > MAX_SIZE || currentSizeUTF16 > MAX_SIZE_UTF16) {
            /* pool was full */
            reset(pinVirtualThread);
        }
        return storeString(s, pinVirtualThread);
    }

    private static boolean preCache(String s) {
        if (preCache[0].equals(s)) {
            return true;
        }
        if (preCache[1].equals(s)) {
            return true;
        }
        if (preCache[2].equals(s)) {
            return true;
        }
        if (preCache[3].equals(s)) {
            return true;
        }
        preCacheOld = (preCacheOld - 1) & preCacheMask;
        preCache[preCacheOld] = s;
        return false;
    }

    private static void reset(boolean pinVirtualThread) {
        if (pinVirtualThread) {
            assert(Thread.currentThread().isVirtual());
            Continuation.pin();
        }
        try {
            synchronized (StringPool.class) {
                cache.clear();
                currentSizeUTF16 = 0;
            }
        } finally {
            if (pinVirtualThread) {
                assert(Thread.currentThread().isVirtual());
                Continuation.unpin();
            }
        }
    }
}
