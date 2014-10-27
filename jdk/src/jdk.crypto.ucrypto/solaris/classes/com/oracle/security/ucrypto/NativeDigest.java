/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.security.ucrypto;

import java.lang.ref.*;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.security.*;

/**
 * MessageDigest implementation class. This class currently supports
 * MD5, SHA1, SHA256, SHA384, and SHA512
 *
 * @since 1.9
 */
public abstract class NativeDigest extends MessageDigestSpi
        implements Cloneable {

    private static final int MECH_MD5 = 1;
    private static final int MECH_SHA1 = 2;
    private static final int MECH_SHA256 = 3;
    private static final int MECH_SHA224 = 4;
    private static final int MECH_SHA384 = 5;
    private static final int MECH_SHA512 = 6;

    private final int digestLen;
    private final int mech;

    // field for ensuring native memory is freed
    private DigestContextRef pCtxt = null;

    private static class DigestContextRef extends PhantomReference<NativeDigest>
        implements Comparable<DigestContextRef> {

        private static ReferenceQueue<NativeDigest> refQueue =
            new ReferenceQueue<NativeDigest>();

        // Needed to keep these references from being GC'ed until when their
        // referents are GC'ed so we can do post-mortem processing
        private static Set<DigestContextRef> refList =
            new ConcurrentSkipListSet<DigestContextRef>();
            //            Collections.synchronizedSortedSet(new TreeSet<DigestContextRef>());

        private final long id;
        private final int mech;

        private static void drainRefQueueBounded() {
            while (true) {
                DigestContextRef next = (DigestContextRef) refQueue.poll();
                if (next == null) break;
                next.dispose(true);
            }
        }

        DigestContextRef(NativeDigest nc, long id, int mech) {
            super(nc, refQueue);
            this.id = id;
            this.mech = mech;
            refList.add(this);
            UcryptoProvider.debug("Resource: track Digest Ctxt " + this.id);
            drainRefQueueBounded();
        }

        public int compareTo(DigestContextRef other) {
            if (this.id == other.id) {
                return 0;
            } else {
                return (this.id < other.id) ? -1 : 1;
            }
        }

        void dispose(boolean needFree) {
            refList.remove(this);
            try {
                if (needFree) {
                    UcryptoProvider.debug("Resource: free Digest Ctxt " + this.id);
                    NativeDigest.nativeFree(mech, id);
                } else UcryptoProvider.debug("Resource: stop tracking Digest Ctxt " + this.id);
            } finally {
                this.clear();
            }
        }
    }

    NativeDigest(int mech, int digestLen) {
        this.digestLen = digestLen;
        this.mech = mech;
    }

    // see JCA spec
    protected int engineGetDigestLength() {
        return digestLen;
    }

    // see JCA spec
    protected synchronized void engineReset() {
        if (pCtxt != null) {
            pCtxt.dispose(true);
            pCtxt = null;
        }
    }

    // see JCA spec
    protected synchronized byte[] engineDigest() {
        byte[] digest = new byte[digestLen];
        try {
            int len = engineDigest(digest, 0, digestLen);
            if (len != digestLen) {
                throw new UcryptoException("Digest length mismatch");
            }
            return digest;
        } catch (DigestException de) {
            throw new UcryptoException("Internal error", de);
        }
    }

    // see JCA spec
    protected synchronized int engineDigest(byte[] out, int ofs, int len)
            throws DigestException {
        if (len < digestLen) {
            throw new DigestException("Output buffer must be at least " +
                                      digestLen + " bytes long");
        }
        if ((ofs < 0) || (len < 0) || (ofs > out.length - len)) {
            throw new DigestException("Buffer too short to store digest");
        }

        if (pCtxt == null) {
            pCtxt = new DigestContextRef(this, nativeInit(mech), mech);
        }
        try {
            int status = nativeDigest(mech, pCtxt.id, out, ofs, digestLen);
            if (status != 0) {
                throw new DigestException("Internal error: " + status);
            }
        } finally {
            pCtxt.dispose(false);
            pCtxt = null;
        }
        return digestLen;
    }

    // see JCA spec
    protected synchronized void engineUpdate(byte in) {
        byte[] temp = { in };
        engineUpdate(temp, 0, 1);
    }

    // see JCA spec
    protected synchronized void engineUpdate(byte[] in, int ofs, int len) {
        if (len == 0) {
            return;
        }
        if ((ofs < 0) || (len < 0) || (ofs > in.length - len)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (pCtxt == null) {
            pCtxt = new DigestContextRef(this, nativeInit(mech), mech);
        }
        nativeUpdate(mech, pCtxt.id, in, ofs, len);
    }

    /**
     * Clone this digest.
     */
    public synchronized Object clone() throws CloneNotSupportedException {
        NativeDigest copy = (NativeDigest) super.clone();
        // re-work the fields that cannot be copied over
        if (pCtxt != null) {
            copy.pCtxt = new DigestContextRef(this, nativeClone(mech, pCtxt.id), mech);
        }
        return copy;
    }

    // return pointer to the context
    protected static native long nativeInit(int mech);
    // return status code; always 0
    protected static native int nativeUpdate(int mech, long pCtxt, byte[] in, int ofs, int inLen);
    // return status code; always 0
    protected static native int nativeDigest(int mech, long pCtxt, byte[] out, int ofs, int digestLen);
    // return pointer to the duplicated context
    protected static native long nativeClone(int mech, long pCtxt);
    // free the specified context
    private native static void nativeFree(int mech, long id);


    public static final class MD5 extends NativeDigest {
        public MD5() {
            super(MECH_MD5, 16);
        }
    }

    public static final class SHA1 extends NativeDigest {
        public SHA1() {
            super(MECH_SHA1, 20);
        }
    }

    public static final class SHA256 extends NativeDigest {
        public SHA256() {
            super(MECH_SHA256, 32);
        }
    }


    public static final class SHA384 extends NativeDigest {
        public SHA384() {
            super(MECH_SHA384, 48);
        }
    }


    public static final class SHA512 extends NativeDigest {
        public SHA512() {
            super(MECH_SHA512, 64);
        }
    }
}
