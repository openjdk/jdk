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

package sun.security.pkcs11;

import javax.crypto.KDFParameters;
import javax.crypto.KDFSpi;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.*;
import java.util.Arrays;
import java.util.List;

import static sun.security.pkcs11.TemplateManager.*;
import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;
import static sun.security.pkcs11.wrapper.PKCS11Exception.RV.CKR_KEY_SIZE_RANGE;

final class P11HKDF extends KDFSpi {
    private final Token token;
    private final P11SecretKeyFactory.HKDFKeyInfo svcKi;
    private static final SecretKey EMPTY_KEY = new SecretKey() {
        @Override
        public String getAlgorithm() {
            return "Generic";
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            return new byte[0];
        }
    };

    private static KDFParameters requireNull(KDFParameters kdfParameters,
            String message) throws InvalidAlgorithmParameterException {
        if (kdfParameters != null) {
            throw new InvalidAlgorithmParameterException(message);
        }
        return null;
    }

    private void checkMechanismEnabled(long mechanism) {
        if (!token.provider.config.isEnabled(mechanism)) {
            throw new ProviderException("Mechanism " +
                    Functions.getMechanismName(mechanism) +
                    " is disabled through 'enabledMechanisms' or " +
                    "'disabledMechanisms' in " + token.provider.getName() +
                    " configuration.");
        }
    }

    P11HKDF(Token token, String algorithm, KDFParameters kdfParameters)
            throws InvalidAlgorithmParameterException {
        super(requireNull(kdfParameters,
                algorithm + " does not support parameters"));
        this.token = token;
        this.svcKi = P11SecretKeyFactory.getHKDFKeyInfo(algorithm);
        assert this.svcKi != null : "Unsupported HKDF algorithm " + algorithm;
    }

    @Override
    protected KDFParameters engineGetParameters() {
        return null;
    }

    @Override
    protected SecretKey engineDeriveKey(String alg,
            AlgorithmParameterSpec derivationSpec)
            throws InvalidAlgorithmParameterException,
            NoSuchAlgorithmException {
        if (alg == null) {
            throw new NullPointerException("the algorithm for the " +
                    "SecretKey return value must not be null");
        }
        if (alg.isEmpty()) {
            throw new NoSuchAlgorithmException("the algorithm for the " +
                    "SecretKey return value must not be empty");
        }
        return derive(alg, derivationSpec, SecretKey.class);
    }

    @Override
    protected byte[] engineDeriveData(AlgorithmParameterSpec derivationSpec)
            throws InvalidAlgorithmParameterException {
        return derive("Generic", derivationSpec, byte[].class);
    }

    private <T> T derive(String alg, AlgorithmParameterSpec derivationSpec,
            Class<T> retType) throws InvalidAlgorithmParameterException {
        SecretKey baseKey;
        SecretKey salt = EMPTY_KEY;
        byte[] info = null;
        int outLen;
        boolean isExtract = false, isExpand = false;
        boolean isData = retType == byte[].class;
        assert isData || retType == SecretKey.class : "Invalid return type.";
        assert alg != null : "The algorithm cannot be null.";

        long mechanism = isData ? CKM_HKDF_DATA : CKM_HKDF_DERIVE;
        checkMechanismEnabled(mechanism);

        switch (derivationSpec) {
            case HKDFParameterSpec.Extract anExtract -> {
                isExtract = true;
                baseKey = consolidateKeyMaterial(anExtract.ikms());
                salt = consolidateKeyMaterial(anExtract.salts());
                outLen = svcKi.prkLen / 8;
                assert outLen * 8 == svcKi.prkLen : "Invalid PRK length.";
            }
            case HKDFParameterSpec.Expand anExpand -> {
                isExpand = true;
                baseKey = anExpand.prk();
                outLen = anExpand.length();
                info = anExpand.info();
            }
            case HKDFParameterSpec.ExtractThenExpand anExtractExpand -> {
                isExtract = true;
                isExpand = true;
                baseKey = consolidateKeyMaterial(anExtractExpand.ikms());
                salt = consolidateKeyMaterial(anExtractExpand.salts());
                outLen = anExtractExpand.length();
                info = anExtractExpand.info();
            }
            case null -> throw new NullPointerException(
                    "derivationSpec must be a " + HKDFParameterSpec.class +
                    " instance, instead of null.");
            default -> throw new InvalidAlgorithmParameterException(
                    "derivationSpec must be a " + HKDFParameterSpec.class +
                    " instance, instead of " + derivationSpec.getClass());
        }

        P11SecretKeyFactory.KeyInfo ki = P11SecretKeyFactory.getKeyInfo(alg);
        if (ki == null) {
            throw new InvalidAlgorithmParameterException("A PKCS #11 key " +
                    "type (CKK_*) was not found for a key of the algorithm '" +
                    alg + "'.");
        }
        checkDerivedKeyType(ki, alg);
        P11KeyGenerator.checkKeySize(ki.keyGenMech, outLen * 8, token);

        P11Key p11BaseKey = convertKey(baseKey, (isExtract ? "IKM" : "PRK") +
                " could not be converted to a token key for HKDF derivation.");

        long saltType = CKF_HKDF_SALT_NULL;
        byte[] saltBytes = null;
        P11Key p11SaltKey = null;
        if (salt instanceof SecretKeySpec) {
            saltType = CKF_HKDF_SALT_DATA;
            saltBytes = salt.getEncoded();
        } else if (salt != EMPTY_KEY) {
            // consolidateKeyMaterial returns a salt from the token.
            saltType = CKF_HKDF_SALT_KEY;
            p11SaltKey = (P11Key.P11SecretKey) salt;
            assert p11SaltKey.token == token : "salt must be from the same " +
                    "token as service.";
        }

        long derivedKeyClass = isData ? CKO_DATA : CKO_SECRET_KEY;
        CK_ATTRIBUTE[] attrs = new CK_ATTRIBUTE[] {
                new CK_ATTRIBUTE(CKA_CLASS, derivedKeyClass),
                new CK_ATTRIBUTE(CKA_KEY_TYPE, ki.keyType),
                new CK_ATTRIBUTE(CKA_VALUE_LEN, outLen)
        };
        Session session = null;
        long baseKeyID = p11BaseKey.getKeyID();
        try {
            session = token.getOpSession();
            CK_HKDF_PARAMS params = new CK_HKDF_PARAMS(isExtract, isExpand,
                    svcKi.hmacMech, saltType, saltBytes, p11SaltKey != null ?
                    p11SaltKey.getKeyID() : 0L, info);
            attrs = token.getAttributes(O_GENERATE, derivedKeyClass,
                    ki.keyType, attrs);
            long derivedObjectID = token.p11.C_DeriveKey(session.id(),
                    new CK_MECHANISM(mechanism, params), baseKeyID, attrs);
            Object ret;
            if (isData) {
                try {
                    CK_ATTRIBUTE[] dataAttr = new CK_ATTRIBUTE[] {
                            new CK_ATTRIBUTE(CKA_VALUE)
                    };
                    token.p11.C_GetAttributeValue(session.id(), derivedObjectID,
                            dataAttr);
                    ret = dataAttr[0].getByteArray();
                } finally {
                    token.p11.C_DestroyObject(session.id(), derivedObjectID);
                }
            } else {
                ret = P11Key.secretKey(session, derivedObjectID, alg,
                        outLen * 8, null);
            }
            return retType.cast(ret);
        } catch (PKCS11Exception e) {
            if (e.match(CKR_KEY_SIZE_RANGE)) {
                throw new InvalidAlgorithmParameterException("Invalid key " +
                        "size (" + outLen + " bytes) for algorithm '" + alg +
                        "'.", e);
            }
            throw new ProviderException("HKDF derivation for algorithm '" +
                    alg + "' failed.", e);
        } finally {
            if (p11SaltKey != null) {
                p11SaltKey.releaseKeyID();
            }
            p11BaseKey.releaseKeyID();
            token.releaseSession(session);
        }
    }

    private static boolean canDeriveKeyInfoType(long t) {
        return (t == CKK_DES || t == CKK_DES3 || t == CKK_AES ||
                t == CKK_RC4 || t == CKK_BLOWFISH || t == CKK_CHACHA20 ||
                t == CKK_GENERIC_SECRET);
    }

    private void checkDerivedKeyType(P11SecretKeyFactory.KeyInfo ki, String alg)
            throws InvalidAlgorithmParameterException {
        Class<?> kiClass = ki.getClass();
        if (!kiClass.equals(P11SecretKeyFactory.TLSKeyInfo.class) &&
                !(kiClass.equals(P11SecretKeyFactory.KeyInfo.class) &&
                        canDeriveKeyInfoType(ki.keyType))) {
            throw new InvalidAlgorithmParameterException("A key of algorithm " +
                    "'" + alg + "' is not valid for derivation.");
        }
    }

    private P11Key.P11SecretKey convertKey(SecretKey key, String errorMessage) {
        try {
            return (P11Key.P11SecretKey) P11SecretKeyFactory.convertKey(token,
                    key, null);
        } catch (InvalidKeyException ike) {
            throw new ProviderException(errorMessage, ike);
        }
    }

    private abstract sealed class KeyMaterialMerger permits
            AnyKeyMaterialMerger, KeyKeyMaterialMerger, DataKeyMaterialMerger {

        final KeyMaterialMerger merge(SecretKey nextKeyMaterial) {
            if (nextKeyMaterial instanceof SecretKeySpec) {
                return merge(nextKeyMaterial.getEncoded());
            } else {
                return merge(convertKey(nextKeyMaterial,
                        "Failure when merging key material."));
            }
        }

        abstract SecretKey getKeyMaterial();

        protected abstract KeyMaterialMerger merge(byte[] nextKeyMaterial);

        protected abstract KeyMaterialMerger merge(
                P11Key.P11SecretKey nextKeyMaterial);

        protected final P11Key.P11SecretKey p11Merge(
                P11Key.P11SecretKey baseKey, CK_MECHANISM ckMech,
                int derivedKeyLen) {
            checkMechanismEnabled(ckMech.mechanism);
            Session session = null;
            long baseKeyID = baseKey.getKeyID();
            try {
                session = token.getOpSession();
                CK_ATTRIBUTE[] attrs = new CK_ATTRIBUTE[] {
                        new CK_ATTRIBUTE(CKA_CLASS, CKO_SECRET_KEY),
                        new CK_ATTRIBUTE(CKA_KEY_TYPE, CKK_GENERIC_SECRET),
                };
                long derivedKeyID = token.p11.C_DeriveKey(session.id(), ckMech,
                        baseKeyID, attrs);
                return (P11Key.P11SecretKey) P11Key.secretKey(session,
                        derivedKeyID, "Generic", derivedKeyLen * 8, null);
            } catch (PKCS11Exception e) {
                throw new ProviderException("Failure when merging key " +
                        "material.", e);
            } finally {
                baseKey.releaseKeyID();
                token.releaseSession(session);
            }
        }
    }

    private final class AnyKeyMaterialMerger extends KeyMaterialMerger {

        protected KeyMaterialMerger merge(byte[] nextKeyMaterial) {
            return P11HKDF.this.new DataKeyMaterialMerger(nextKeyMaterial);
        }

        protected KeyMaterialMerger merge(P11Key.P11SecretKey nextKeyMaterial) {
            return P11HKDF.this.new KeyKeyMaterialMerger(nextKeyMaterial);
        }

        SecretKey getKeyMaterial() {
            return EMPTY_KEY;
        }
    }

    private final class KeyKeyMaterialMerger extends KeyMaterialMerger {
        private P11Key.P11SecretKey keyMaterial;

        KeyKeyMaterialMerger(P11Key.P11SecretKey keyMaterial) {
            this.keyMaterial = keyMaterial;
        }

        protected KeyMaterialMerger merge(byte[] nextKeyMaterial) {
            keyMaterial = p11Merge(keyMaterial,
                    new CK_MECHANISM(CKM_CONCATENATE_BASE_AND_DATA,
                            new CK_KEY_DERIVATION_STRING_DATA(nextKeyMaterial)),
                    keyMaterial.keyLength + nextKeyMaterial.length);
            return this;
        }

        protected KeyMaterialMerger merge(P11Key.P11SecretKey nextKeyMaterial) {
            try {
                keyMaterial = p11Merge(keyMaterial,
                        new CK_MECHANISM(CKM_CONCATENATE_BASE_AND_KEY,
                                nextKeyMaterial.getKeyID()),
                        keyMaterial.keyLength + nextKeyMaterial.keyLength);
            } finally {
                nextKeyMaterial.releaseKeyID();
            }
            return this;
        }

        SecretKey getKeyMaterial() {
            return keyMaterial;
        }
    }

    private final class DataKeyMaterialMerger extends KeyMaterialMerger {
        private byte[] keyMaterial;

        DataKeyMaterialMerger(byte[] keyMaterial) {
            this.keyMaterial = keyMaterial;
        }

        protected KeyMaterialMerger merge(byte[] nextKeyMaterial) {
            keyMaterial = Arrays.copyOf(keyMaterial,
                    keyMaterial.length + nextKeyMaterial.length);
            System.arraycopy(nextKeyMaterial, 0, keyMaterial,
                    keyMaterial.length - nextKeyMaterial.length,
                    nextKeyMaterial.length);
            return this;
        }

        protected KeyMaterialMerger merge(P11Key.P11SecretKey nextKeyMaterial) {
            return P11HKDF.this.new KeyKeyMaterialMerger(p11Merge(
                    nextKeyMaterial, new CK_MECHANISM(
                            CKM_CONCATENATE_DATA_AND_BASE,
                            new CK_KEY_DERIVATION_STRING_DATA(keyMaterial)),
                    keyMaterial.length + nextKeyMaterial.keyLength));
        }

        SecretKey getKeyMaterial() {
            return new SecretKeySpec(keyMaterial, "Generic");
        }
    }

    private SecretKey consolidateKeyMaterial(List<SecretKey> keys) {
        KeyMaterialMerger keyMerger = P11HKDF.this.new AnyKeyMaterialMerger();
        for (SecretKey key : keys) {
            keyMerger = keyMerger.merge(key);
        }
        return keyMerger.getKeyMaterial();
    }
}
