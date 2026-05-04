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
 * class CK_HKDF_PARAMS provides the parameters to the CKM_HKDF_DERIVE and
 * CKM_HKDF_DATA mechanisms.<p>
 * <b>PKCS#11 structure:</b>
 * <pre>
 * typedef struct CK_HKDF_PARAMS {
 *   CK_BBOOL          bExtract;
 *   CK_BBOOL          bExpand;
 *   CK_MECHANISM_TYPE prfHashMechanism;
 *   CK_ULONG          ulSaltType;
 *   CK_BYTE_PTR       pSalt;
 *   CK_ULONG          ulSaltLen;
 *   CK_OBJECT_HANDLE  hSaltKey;
 *   CK_BYTE_PTR       pInfo;
 *   CK_ULONG          ulInfoLen;
 * } CK_HKDF_PARAMS;
 * </pre>
 *
 */
public class CK_HKDF_PARAMS {

    /**
     * <b>PKCS#11:</b>
     * <pre>
     *   CK_BBOOL bExtract;
     * </pre>
     */
    public final boolean bExtract;

    /**
     * <b>PKCS#11:</b>
     * <pre>
     *   CK_BBOOL bExpand;
     * </pre>
     */
    public final boolean bExpand;

    /**
     * <b>PKCS#11:</b>
     * <pre>
     *   CK_MECHANISM_TYPE prfHashMechanism;
     * </pre>
     */
    public final long prfHashMechanism;

    /**
     * <b>PKCS#11:</b>
     * <pre>
     *   CK_ULONG ulSaltType;
     * </pre>
     */
    public final long ulSaltType;

    /**
     * <b>PKCS#11:</b>
     * <pre>
     *   CK_BYTE_PTR pSalt;
     *   CK_ULONG ulSaltLen;
     * </pre>
     */
    public final byte[] pSalt;

    /**
     * <b>PKCS#11:</b>
     * <pre>
     *   CK_OBJECT_HANDLE hSaltKey;
     * </pre>
     */
    public final long hSaltKey;

    /**
     * <b>PKCS#11:</b>
     * <pre>
     *   CK_BYTE_PTR pInfo;
     *   CK_ULONG ulInfoLen;
     * </pre>
     */
    public final byte[] pInfo;

    public CK_HKDF_PARAMS(boolean bExtract, boolean bExpand,
            long prfHashMechanism, long ulSaltType, byte[] pSalt, long hSaltKey,
            byte[] pInfo) {
        this.bExtract = bExtract;
        this.bExpand = bExpand;
        this.prfHashMechanism = prfHashMechanism;
        this.ulSaltType = ulSaltType;
        this.pSalt = pSalt;
        this.hSaltKey = hSaltKey;
        this.pInfo = pInfo;
    }

    /**
     * Returns the string representation of CK_HKDF_PARAMS.
     *
     * @return the string representation of CK_HKDF_PARAMS
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(Constants.INDENT);
        sb.append("bExtract: ");
        sb.append(bExtract);
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("bExpand: ");
        sb.append(bExpand);
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("prfHashMechanism: ");
        sb.append(Functions.getMechanismName(prfHashMechanism));
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("ulSaltType: ");
        sb.append(Functions.saltTypeToString(ulSaltType));
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("pSalt: ");
        sb.append(Functions.toHexString(pSalt));
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("ulSaltLen: ");
        sb.append(Functions.getLength(pSalt));
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("hSaltKey: ");
        sb.append(hSaltKey);
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("pInfo: ");
        sb.append(Functions.toHexString(pInfo));
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("ulInfoLen: ");
        sb.append(Functions.getLength(pInfo));
        sb.append(Constants.NEWLINE);

        return sb.toString();
    }
}
