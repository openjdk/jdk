/*
 * Copyright (c) 2025, Red Hat, Inc.
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
 * class CK_KEY_DERIVATION_STRING_DATA provides the parameters to the
 * CKM_DES_ECB_ENCRYPT_DATA, CKM_DES3_ECB_ENCRYPT_DATA,
 * CKM_AES_ECB_ENCRYPT_DATA, CKM_CONCATENATE_BASE_AND_DATA,
 * CKM_CONCATENATE_DATA_AND_BASE, CKM_XOR_BASE_AND_DATA,
 * CKM_CAMELLIA_ECB_ENCRYPT_DATA, CKM_ARIA_ECB_ENCRYPT_DATA and
 * CKM_SEED_ECB_ENCRYPT_DATA mechanisms.<p>
 * <b>PKCS#11 structure:</b>
 * <pre>
 * typedef struct CK_KEY_DERIVATION_STRING_DATA {
 *     CK_BYTE_PTR pData;
 *     CK_ULONG ulLen;
 * } CK_KEY_DERIVATION_STRING_DATA;
 * </pre>
 *
 */
public class CK_KEY_DERIVATION_STRING_DATA {

    /**
     * <b>PKCS#11:</b>
     * <pre>
     *   CK_BYTE_PTR pData;
     *   CK_ULONG ulLen;
     * </pre>
     */
    public final byte[] pData;

    public CK_KEY_DERIVATION_STRING_DATA(byte[] pData) {
        this.pData = pData;
    }

    /**
     * Returns the string representation of CK_KEY_DERIVATION_STRING_DATA.
     *
     * @return the string representation of CK_KEY_DERIVATION_STRING_DATA
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(Constants.INDENT);
        sb.append("pData: ");
        sb.append(Functions.toHexString(pData));
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("ulLen: ");
        sb.append(Functions.getLength(pData));
        sb.append(Constants.NEWLINE);

        return sb.toString();
    }
}
