/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * MessageDigest implementation class using native Ucrypto API.
 * This class currently supports: MD5, SHA-2 (224, 256, 384, 512)
 * and SHA-3 (224, 256, 384, 512) digests
 *
 * @since 9
 */
abstract class NativeDigest extends MessageDigestSpi {

    public static final class MD5 extends NativeDigest {
        public MD5() {
            super(UcryptoMech.CRYPTO_MD5, 16);
        }
    }
    public static final class SHA1 extends NativeDigest {
        public SHA1() {
            super(UcryptoMech.CRYPTO_SHA1, 20);
        }
    }
    public static final class SHA224 extends NativeDigest {
        public SHA224() {
            super(UcryptoMech.CRYPTO_SHA224, 28);
        }
    }
    public static final class SHA256 extends NativeDigest {
        public SHA256() {
            super(UcryptoMech.CRYPTO_SHA256, 32);
        }
    }
    public static final class SHA384 extends NativeDigest {
        public SHA384() {
            super(UcryptoMech.CRYPTO_SHA384, 48);
        }
    }
    public static final class SHA512 extends NativeDigest {
        public SHA512() {
            super(UcryptoMech.CRYPTO_SHA512, 64);
        }
    }
    public static final class SHA3_224 extends NativeDigest {
        public SHA3_224() {
            super(UcryptoMech.CRYPTO_SHA3_224, 28);
        }
    }
    public static final class SHA3_256 extends NativeDigest {
        public SHA3_256() {
            super(UcryptoMech.CRYPTO_SHA3_256, 32);
        }
    }
    public static final class SHA3_384 extends NativeDigest {
        public SHA3_384() {
            super(UcryptoMech.CRYPTO_SHA3_384, 48);
        }
    }
    public static final class SHA3_512 extends NativeDigest {
        public SHA3_512() {
            super(UcryptoMech.CRYPTO_SHA3_512, 64);
        }
    }

    private final int digestLen;
    private final UcryptoMech mech;

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

        private final long id;
        private final UcryptoMech mech;

        private static void drainRefQueueBounded() {
            while (true) {
                DigestContextRef next = (DigestContextRef) refQueue.poll();
                if (next == null) break;
                next.dispose(true);
            }
        }

        DigestContextRef(NativeDigest nc, long id, UcryptoMech mech) {
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
                    UcryptoProvider.debug("Resource: free Digest Ctxt " +
                        this.id);
                    NativeDigest.nativeFree(mech.value(), id);
                } else {
                    UcryptoProvider.debug("Resource: discard Digest Ctxt " +
                        this.id);
                }
            } finally {
                this.clear();
            }
        }
    }

    NativeDigest(UcryptoMech mech, int digestLen) {
        this.mech = mech;
        this.digestLen = digestLen;
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
                throw new UcryptoException("Digest length mismatch." +
                    " Len: " + len + ". digestLen: " + digestLen);
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
                          digestLen + " bytes long. Got: " + len);
        }
        if ((ofs < 0) || (len < 0) || (ofs > out.length - len)) {
            throw new DigestException("Buffer too short to store digest. " +
                "ofs: " + ofs + ". len: " + len + ". out.length: " + out.length);
        }

        if (pCtxt == null) {
            pCtxt = new DigestContextRef(this, nativeInit(mech.value()), mech);
        }
        try {
            int status = nativeDigest(mech.value(), pCtxt.id, out, ofs, digestLen);
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
            throw new ArrayIndexOutOfBoundsException("ofs: " + ofs + ". len: "
                + len + ". in.length: " + in.length);
        }
        if (pCtxt == null) {
            pCtxt = new DigestContextRef(this, nativeInit(mech.value()), mech);
        }
        nativeUpdate(mech.value(), pCtxt.id, in, ofs, len);
    }

    /**
     * Clone this digest.
     */
    public synchronized Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("Clone is not supported");
    }

    // return pointer to the context
    protected static final native long nativeInit(int mech);
    // return status code; always 0
    protected static final native int nativeUpdate(int mech, long pCtxt, byte[] in, int ofs, int inLen);
    // return status code; always 0
    protected static final native int nativeDigest(int mech, long pCtxt, byte[] out, int ofs, int digestLen);
    // free the specified context
    private static final native void nativeFree(int mech, long id);
}
