/*
 * Copyright (c) 2023, Red Hat, Inc.
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

import static sun.security.pkcs11.wrapper.PKCS11Constants.*;

/**
 * class CK_PKCS5_PBKD2_PARAMS2 provides the parameters to the CKM_PKCS5_PBKD2
 * mechanism.<p>
 * <b>PKCS#11 structure:</b>
 * <pre>
 * typedef struct CK_PKCS5_PBKD2_PARAMS2 {
 *   CK_PKCS5_PBKDF2_SALT_SOURCE_TYPE saltSource;
 *   CK_VOID_PTR pSaltSourceData;
 *   CK_ULONG ulSaltSourceDataLen;
 *   CK_ULONG iterations;
 *   CK_PKCS5_PBKD2_PSEUDO_RANDOM_FUNCTION_TYPE prf;
 *   CK_VOID_PTR pPrfData;
 *   CK_ULONG ulPrfDataLen;
 *   CK_UTF8CHAR_PTR pPassword;
 *   CK_ULONG ulPasswordLen;
 * } CK_PKCS5_PBKD2_PARAMS2;
 * </pre>
 *
 */
public class CK_PKCS5_PBKD2_PARAMS2 {

    /**
     * <b>PKCS#11:</b>
     * <pre>
     *   CK_PKCS5_PBKDF2_SALT_SOURCE_TYPE saltSource;
     * </pre>
     */
    public long saltSource;

    /**
     * <b>PKCS#11:</b>
     * <pre>
     *   CK_VOID_PTR pSaltSourceData;
     *   CK_ULONG ulSaltSourceDataLen;
     * </pre>
     */
    public byte[] pSaltSourceData;

    /**
     * <b>PKCS#11:</b>
     * <pre>
     *   CK_ULONG iterations;
     * </pre>
     */
    public long iterations;

    /**
     * <b>PKCS#11:</b>
     * <pre>
     *   CK_PKCS5_PBKD2_PSEUDO_RANDOM_FUNCTION_TYPE prf;
     * </pre>
     */
    public long prf;

    /**
     * <b>PKCS#11:</b>
     * <pre>
     *   CK_VOID_PTR pPrfData;
     *   CK_ULONG ulPrfDataLen;
     * </pre>
     */
    public byte[] pPrfData;

    /**
     * <b>PKCS#11:</b>
     * <pre>
     *   CK_UTF8CHAR_PTR pPassword
     *   CK_ULONG ulPasswordLen;
     * </pre>
     */
    public char[] pPassword;

    public CK_PKCS5_PBKD2_PARAMS2(char[] pPassword, byte[] pSalt,
            long iterations, long prf) {
        this.pPassword = pPassword;
        this.pSaltSourceData = pSalt;
        this.iterations = iterations;
        this.prf = prf;
        this.saltSource = CKZ_SALT_SPECIFIED;
    }

    /**
     * Returns the string representation of CK_PKCS5_PBKD2_PARAMS2.
     *
     * @return the string representation of CK_PKCS5_PBKD2_PARAMS2
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(Constants.INDENT);
        sb.append("saltSource: ");
        sb.append(Functions.getParamSourcesName(saltSource));
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("pSaltSourceData: ");
        sb.append(Functions.toHexString(pSaltSourceData));
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("ulSaltSourceDataLen: ");
        sb.append(Functions.getLength(pSaltSourceData));
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("iterations: ");
        sb.append(iterations);
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("prf: ");
        sb.append(Functions.getPrfName(prf));
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("pPrfData: ");
        sb.append(Functions.toHexString(pPrfData));
        sb.append(Constants.NEWLINE);

        sb.append(Constants.INDENT);
        sb.append("ulPrfDataLen: ");
        sb.append(Functions.getLength(pPrfData));

        return sb.toString();
    }

}
