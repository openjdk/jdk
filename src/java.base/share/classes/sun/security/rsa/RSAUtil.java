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

package sun.security.rsa;

import java.io.IOException;
import java.security.*;
import java.security.spec.*;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.AlgorithmId;

/**
 * Utility class for SunRsaSign provider.
 * Currently used by RSAKeyPairGenerator and RSAKeyFactory.
 *
 * @since   11
 */
public class RSAUtil {

    public enum KeyType {
        RSA ("RSA"),
        PSS ("RSASSA-PSS")
        ;

        private final String algo;

        KeyType(String keyAlgo) {
            this.algo = keyAlgo;
        }
        public String keyAlgo() {
            return algo;
        }
        public static KeyType lookup(String name)
                throws InvalidKeyException, ProviderException {
            if (name == null) {
                throw new InvalidKeyException("Null key algorithm");
            }
            for (KeyType kt : KeyType.values()) {
                if (kt.keyAlgo().equalsIgnoreCase(name)) {
                    return kt;
                }
            }
            // no match
            throw new ProviderException("Unsupported algorithm " + name);
        }
    }

    public static void checkParamsAgainstType(KeyType type,
            AlgorithmParameterSpec paramSpec) throws ProviderException {
        switch (type) {
            case RSA:
                if (paramSpec != null) {
                    throw new ProviderException("null params expected for " +
                        type.keyAlgo());
                }
                break;
            case PSS:
                if ((paramSpec != null) &&
                    !(paramSpec instanceof PSSParameterSpec)) {
                    throw new ProviderException
                        ("PSSParmeterSpec expected for " + type.keyAlgo());
                }
                break;
            default:
                throw new ProviderException
                    ("Unsupported RSA algorithm " + type);
        }
    }

    public static AlgorithmId createAlgorithmId(KeyType type,
            AlgorithmParameterSpec paramSpec) throws ProviderException {

        checkParamsAgainstType(type, paramSpec);

        ObjectIdentifier oid = null;
        AlgorithmParameters params = null;
        try {
            switch (type) {
                case RSA:
                    oid = AlgorithmId.RSAEncryption_oid;
                    break;
                case PSS:
                    if (paramSpec != null) {
                        params = AlgorithmParameters.getInstance(type.keyAlgo());
                        params.init(paramSpec);
                    }
                    oid = AlgorithmId.RSASSA_PSS_oid;
                    break;
                default:
                    throw new ProviderException
                        ("Unsupported RSA algorithm "  + type);
            }
            AlgorithmId result;
            if (params == null) {
                result = new AlgorithmId(oid);
            } else {
                result = new AlgorithmId(oid, params);
            }
            return result;
        } catch (NoSuchAlgorithmException | InvalidParameterSpecException e) {
            // should not happen
            throw new ProviderException(e);
        }
    }

    public static AlgorithmParameterSpec getParamSpec(AlgorithmId algid)
            throws ProviderException {
        if (algid == null) {
            throw new ProviderException("AlgorithmId should not be null");
        }
        return getParamSpec(algid.getParameters());
    }

    public static AlgorithmParameterSpec getParamSpec(AlgorithmParameters params)
            throws ProviderException {
        if (params == null) return null;

        try {
            String algName = params.getAlgorithm();
            KeyType type = KeyType.lookup(algName);
            Class<? extends AlgorithmParameterSpec> specCls;
            switch (type) {
                case RSA:
                    throw new ProviderException("No params accepted for " +
                        type.keyAlgo());
                case PSS:
                    specCls = PSSParameterSpec.class;
                    break;
                default:
                    throw new ProviderException("Unsupported RSA algorithm: " + algName);
            }
            return params.getParameterSpec(specCls);
        } catch (ProviderException pe) {
            // pass it up
            throw pe;
        } catch (Exception e) {
            throw new ProviderException(e);
        }
    }
}
