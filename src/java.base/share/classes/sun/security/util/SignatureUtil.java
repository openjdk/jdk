/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.io.IOException;
import java.security.*;
import java.security.spec.*;
import sun.security.rsa.RSAUtil;

/**
 * Utility class for Signature related operations. Currently used by various
 * internal PKI classes such as sun.security.x509.X509CertImpl,
 * sun.security.pkcs.SignerInfo, for setting signature parameters.
 *
 * @since   11
 */
public class SignatureUtil {

    // Utility method of creating an AlgorithmParameters object with
    // the specified algorithm name and encoding
    private static AlgorithmParameters createAlgorithmParameters(String algName,
            byte[] paramBytes) throws ProviderException {

        try {
            AlgorithmParameters result =
                AlgorithmParameters.getInstance(algName);
            result.init(paramBytes);
            return result;
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new ProviderException(e);
        }
    }

    private static AlgorithmParameterSpec getParamSpec(String sigName,
            AlgorithmParameters params)
            throws InvalidAlgorithmParameterException, ProviderException {

        if (params == null) return null;

        if (sigName.toUpperCase().indexOf("RSA") == -1) {
            throw new ProviderException
                 ("Unrecognized algorithm for signature parameters " +
                  sigName);
        }
        // AlgorithmParameters.getAlgorithm() may returns oid if it's
        // created during DER decoding. Convert to use the standard name
        // before passing it to RSAUtil
        String alg = params.getAlgorithm();
        if (alg.equalsIgnoreCase(sigName) || alg.indexOf(".") != -1) {
            try {
                params = createAlgorithmParameters(sigName,
                    params.getEncoded());
            } catch (IOException e) {
                throw new ProviderException(e);
            }
        }
        return RSAUtil.getParamSpec(params);
    }

    // Special method for setting the specified parameter bytes into the
    // specified Signature object as signature parameters.
    public static void specialSetParameter(Signature sig, byte[] paramBytes)
            throws InvalidAlgorithmParameterException, ProviderException {
        if (paramBytes != null) {
            String sigName = sig.getAlgorithm();
            AlgorithmParameters params =
                createAlgorithmParameters(sigName, paramBytes);
            specialSetParameter(sig, params);
        }
    }

    // Special method for setting the specified AlgorithmParameter object
    // into the specified Signature object as signature parameters.
    public static void specialSetParameter(Signature sig,
            AlgorithmParameters params)
            throws InvalidAlgorithmParameterException, ProviderException {
        if (params != null) {
            String sigName = sig.getAlgorithm();
            sig.setParameter(getParamSpec(sigName, params));
        }
    }
}
