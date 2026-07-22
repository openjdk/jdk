/*
 * Copyright (c) 2026, IBM Corporation. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import sun.security.internal.spec.TlsMasterSecretParameterSpec;
import sun.security.pkcs11.wrapper.*;

import javax.crypto.KeyGeneratorSpi;
import javax.crypto.SecretKey;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

import static sun.security.pkcs11.TemplateManager.O_GENERATE;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;

/**
 * KeyGenerator for the TLS extended master secret.
 * See RFC 7627 for further details.
 *
 * @author Andreas Chmielewski
 * @since x.x
 */
public final class P11TlsExtendedMasterSecretGenerator extends KeyGeneratorSpi {

    // See RFC 5246, section 8.1 Computing the Master Secret
    private static final int TLS_MASTER_SECRET_LEN = 48;

    private static final String MSG = "TlsExtendedMasterSecretGenerator must be "
            + "initialized using a TlsMasterSecretParameterSpec";

    // token instance
    private final Token token;

    // algorithm name
    private final String algorithm;
    CK_VERSION ckVersion;
    // mechanism id
    private long mechanism;
    @SuppressWarnings("deprecation")
    private TlsMasterSecretParameterSpec spec;
    private P11Key p11Key;

    P11TlsExtendedMasterSecretGenerator(Token token, String algorithm, long mechanism)
            throws PKCS11Exception {
        super();
        this.token = token;
        this.algorithm = algorithm;
        this.mechanism = mechanism;
    }

    protected void engineInit(SecureRandom random) {
        throw new InvalidParameterException(MSG);
    }

    @SuppressWarnings("deprecation")
    protected void engineInit(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        if (!(params instanceof TlsMasterSecretParameterSpec spec)) {
            throw new InvalidAlgorithmParameterException(MSG);
        }

        int tlsVersion = (spec.getMajorVersion() << 8) | spec.getMinorVersion();
        if (tlsVersion != 0x0303) {
            // RFC 7627 defines EMS for earlier TLS versions as well, but
            // this implementation only exposes PKCS#11 TLS 1.2 EMS mechanisms.
            throw new InvalidAlgorithmParameterException(
                    "Extended Master Secret is only supported for TLS 1.2");
        }

        try {
            p11Key = P11SecretKeyFactory.convertKey(token,
                    spec.getPremasterSecret(), null);
        } catch (InvalidKeyException e) {
            throw new InvalidAlgorithmParameterException("init() failed", e);
        }

        this.spec = spec;

        String alg = p11Key.getAlgorithm();
        boolean isTlsRsaPremasterSecret = "TlsRsaPremasterSecret".equals(alg);
        if (!isTlsRsaPremasterSecret && !"TlsPremasterSecret".equals(alg)) {
            throw new InvalidAlgorithmParameterException(
                    "Unsupported premaster secret algorithm: " + alg);
        }

        mechanism = isTlsRsaPremasterSecret
                ? CKM_TLS12_EXTENDED_MASTER_KEY_DERIVE
                : CKM_TLS12_EXTENDED_MASTER_KEY_DERIVE_DH;

        if (isTlsRsaPremasterSecret) {
            ckVersion = new CK_VERSION(0, 0);
        } else {
            // PKCS#11 defines separate EMS derivation mechanisms for RSA
            // premaster secrets and all other premaster secret types.
            // The non-RSA mechanism is used for DH, ECDH, ECDHE, Kerberos,
            // and other key-exchange methods whose premaster secret does
            // not contain the embedded protocol version present in RSA
            // premaster secrets.
            ckVersion = null;
        }
    }

    protected void engineInit(int keysize, SecureRandom random) {
        throw new InvalidParameterException(MSG);
    }

    protected SecretKey engineGenerateKey() {
        if (spec == null) {
            throw new IllegalStateException(
                    "TlsExtendedMasterSecretGenerator must be initialized");
        }

        byte[] sessionHash = spec.getExtendedMasterSecretSessionHash();
        if (sessionHash.length == 0) {
            throw new ProviderException(
                    "Extended Master Secret session hash missing");
        }

        CK_MECHANISM ckMechanism = new CK_MECHANISM(
                mechanism, new CK_TLS12_EXTENDED_MASTER_KEY_DERIVE_PARAMS(
                Functions.getHashMechId(spec.getPRFHashAlg()),
                sessionHash, ckVersion));

        Session session = null;
        long p11KeyID = p11Key.getKeyID();

        try {
            session = token.getObjSession();

            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[]{
                    new CK_ATTRIBUTE(CKA_CLASS, CKO_SECRET_KEY),
                    new CK_ATTRIBUTE(CKA_KEY_TYPE, CKK_GENERIC_SECRET),
                    new CK_ATTRIBUTE(CKA_VALUE_LEN, TLS_MASTER_SECRET_LEN)
            };

            attributes = token.getAttributes(
                    O_GENERATE,
                    CKO_SECRET_KEY,
                    CKK_GENERIC_SECRET,
                    attributes);

            long keyID = token.p11.C_DeriveKey(session.id(), ckMechanism,
                    p11KeyID, attributes);

            int major = (ckVersion == null) ? -1 : ckVersion.major;
            int minor = (ckVersion == null) ? -1 : ckVersion.minor;

            return P11Key.masterSecretKey(session, keyID, "TlsMasterSecret",
                    TLS_MASTER_SECRET_LEN << 3, attributes, major, minor);
        } catch (PKCS11Exception e) {
            throw new ProviderException("Could not generate extended master secret", e);
        } finally {
            p11Key.releaseKeyID();
            token.releaseSession(session);
        }
    }
}
