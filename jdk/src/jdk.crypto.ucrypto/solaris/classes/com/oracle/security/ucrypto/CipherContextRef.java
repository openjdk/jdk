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

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.Arrays;
import java.util.concurrent.ConcurrentSkipListSet;
import java.lang.ref.*;

import java.security.*;
import java.security.spec.*;
import javax.crypto.*;

import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

/**
 * Internal class for context resource clean up.
 *
 * @since 1.9
 */
final class CipherContextRef extends PhantomReference<NativeCipher>
    implements Comparable<CipherContextRef> {

    private static ReferenceQueue<NativeCipher> refQueue =
        new ReferenceQueue<NativeCipher>();

    // Needed to keep these references from being GC'ed until when their
    // referents are GC'ed so we can do post-mortem processing
    private static Set<CipherContextRef> refList =
        new ConcurrentSkipListSet<CipherContextRef>();

    final long id;
    final boolean encrypt;

    private static void drainRefQueueBounded() {
        while (true) {
            CipherContextRef next = (CipherContextRef) refQueue.poll();
            if (next == null) break;
            next.dispose(true);
        }
    }

    CipherContextRef(NativeCipher nc, long id, boolean encrypt) {
        super(nc, refQueue);
        this.id = id;
        this.encrypt = encrypt;
        refList.add(this);
        UcryptoProvider.debug("Resource: trace CipherCtxt " + this.id);
        drainRefQueueBounded();
    }

    public int compareTo(CipherContextRef other) {
        if (this.id == other.id) {
            return 0;
        } else {
            return (this.id < other.id) ? -1 : 1;
        }
    }

    void dispose(boolean doCancel) {
        refList.remove(this);
        try {
            if (doCancel) {
                UcryptoProvider.debug("Resource: cancel CipherCtxt " + id);
                int k = NativeCipher.nativeFinal(id, encrypt, null, 0);
                if (k < 0) {
                    UcryptoProvider.debug
                        ("Resource: error cancelling CipherCtxt " + id +
                        " " + new UcryptoException(-k).getMessage());
                }
            } else {
                UcryptoProvider.debug("Resource: untrace CipherCtxt " + id);
            }
        } finally {
            this.clear();
        }
    }
}
