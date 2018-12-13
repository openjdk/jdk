/*
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.mscapi;

import sun.security.util.Length;

import java.security.Key;

/**
 * The handle for a key using the Microsoft Crypto API.
 *
 * @see CPrivateKey
 * @see CPublicKey
 *
 * @since 1.6
 * @author  Stanley Man-Kit Ho
 */
abstract class CKey implements Key, Length {
    private static final long serialVersionUID = -1088859394025049194L;

    static class NativeHandles {

        long hCryptProv = 0;
        long hCryptKey = 0;

        public NativeHandles(long hCryptProv, long hCryptKey) {
            this.hCryptProv = hCryptProv;
            this.hCryptKey = hCryptKey;
        }

        @SuppressWarnings("deprecation")
        protected void finalize() throws Throwable {
            try {
                synchronized(this) {
                    cleanUp(hCryptProv, hCryptKey);
                    hCryptProv = 0;
                    hCryptKey = 0;
                }
            } finally {
                super.finalize();
            }
        }
    }

    protected final NativeHandles handles;

    protected final int keyLength;

    protected final String algorithm;

    protected CKey(String algorithm, long hCryptProv, long hCryptKey, int keyLength) {
        this.algorithm = algorithm;
        this.handles = new NativeHandles(hCryptProv, hCryptKey);
        this.keyLength = keyLength;
    }

    // Native method to cleanup the key handle.
    private native static void cleanUp(long hCryptProv, long hCryptKey);

    @Override
    public int length() {
        return keyLength;
    }

    public long getHCryptKey() {
        return handles.hCryptKey;
    }

    public long getHCryptProvider() {
        return handles.hCryptProv;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    protected native static String getContainerName(long hCryptProv);

    protected native static String getKeyType(long hCryptKey);
}
