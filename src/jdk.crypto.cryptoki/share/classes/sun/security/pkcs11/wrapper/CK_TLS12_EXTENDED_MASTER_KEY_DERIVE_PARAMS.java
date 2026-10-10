/*
 * Copyright (c) 2026, IBM Corporation. All rights reserved.
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

package sun.security.pkcs11.wrapper;

/**
 * CK_TLS12_EXTENDED_MASTER_KEY_DERIVE_PARAMS from PKCS#11 v3.20.
 */
public class CK_TLS12_EXTENDED_MASTER_KEY_DERIVE_PARAMS {

    /**
     * <B>PKCS#11:</B>
     *
     * <PRE>
     * CK_MECHANISM_TYPE prfHashMechanism;
     * </PRE>
     */
    public long prfHashMechanism;

    /**
     * <B>PKCS#11:</B>
     *
     * <PRE>
     * CK_BYTE_PTR pSessionHash;
     * CK_ULONG ulSessionHashLen;
     * </PRE>
     */
    public byte[] pSessionHash;

    /**
     * <B>PKCS#11:</B>
     *
     * <PRE>
     * CK_VERSION_PTR pVersion;
     * </PRE>
     */
    public CK_VERSION pVersion;

    public CK_TLS12_EXTENDED_MASTER_KEY_DERIVE_PARAMS(
            long prfHashMechanism,
            byte[] sessionHash,
            CK_VERSION version) {
        this.prfHashMechanism = prfHashMechanism;
        this.pSessionHash = sessionHash;
        this.pVersion = version;
    }
}
