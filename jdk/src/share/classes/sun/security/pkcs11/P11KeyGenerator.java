/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.security.pkcs11;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.*;

import static sun.security.pkcs11.TemplateManager.*;
import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;

/**
 * KeyGenerator implementation class. This class currently supports
 * DES, DESede, AES, ARCFOUR, and Blowfish.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class P11KeyGenerator extends KeyGeneratorSpi {

    // token instance
    private final Token token;

    // algorithm name
    private final String algorithm;

    // mechanism id
    private long mechanism;

    // raw key size in bits, e.g. 64 for DES. Always valid.
    private int keySize;

    // bits of entropy in the key, e.g. 56 for DES. Always valid.
    private int significantKeySize;

    // keyType (CKK_*), needed for TemplateManager call only.
    private long keyType;

    // for determining if both 112 and 168 bits of DESede key lengths
    // are supported.
    private boolean supportBothKeySizes;

    // min and max key sizes (in bits) for variable-key-length
    // algorithms, e.g. RC4 and Blowfish
    private int minKeySize;
    private int maxKeySize;

    P11KeyGenerator(Token token, String algorithm, long mechanism)
            throws PKCS11Exception {
        super();
        this.token = token;
        this.algorithm = algorithm;
        this.mechanism = mechanism;

        if (this.mechanism == CKM_DES3_KEY_GEN) {
            /* Given the current lookup order specified in SunPKCS11.java,
               if CKM_DES2_KEY_GEN is used to construct this object, it
               means that CKM_DES3_KEY_GEN is disabled or unsupported.
            */
            supportBothKeySizes =
                (token.provider.config.isEnabled(CKM_DES2_KEY_GEN) &&
                 (token.getMechanismInfo(CKM_DES2_KEY_GEN) != null));
        } else if (this.mechanism == CKM_RC4_KEY_GEN) {
            CK_MECHANISM_INFO info = token.getMechanismInfo(mechanism);
            // Although PKCS#11 spec documented that these are in bits,
            // NSS, for one, uses bytes. Multiple by 8 if the number seems
            // unreasonably small.
            if (info.ulMinKeySize < 8) {
                minKeySize = (int)info.ulMinKeySize << 3;
                maxKeySize = (int)info.ulMaxKeySize << 3;
            } else {
                minKeySize = (int)info.ulMinKeySize;
                maxKeySize = (int)info.ulMaxKeySize;
            }
            // Explicitly disallow keys shorter than 40-bits for security
            if (minKeySize < 40) minKeySize = 40;
        } else if (this.mechanism == CKM_BLOWFISH_KEY_GEN) {
            CK_MECHANISM_INFO info = token.getMechanismInfo(mechanism);
            maxKeySize = (int)info.ulMaxKeySize << 3;
            minKeySize = (int)info.ulMinKeySize << 3;
            // Explicitly disallow keys shorter than 40-bits for security
            if (minKeySize < 40) minKeySize = 40;
        }

        setDefaultKeySize();
    }

    // set default keysize and also initialize keyType
    private void setDefaultKeySize() {
        // whether to check default key size against the min and max value
        boolean validateKeySize = false;
        switch ((int)mechanism) {
        case (int)CKM_DES_KEY_GEN:
            keySize = 64;
            significantKeySize = 56;
            keyType = CKK_DES;
            break;
        case (int)CKM_DES2_KEY_GEN:
            keySize = 128;
            significantKeySize = 112;
            keyType = CKK_DES2;
            break;
        case (int)CKM_DES3_KEY_GEN:
            keySize = 192;
            significantKeySize = 168;
            keyType = CKK_DES3;
            break;
        case (int)CKM_AES_KEY_GEN:
            keyType = CKK_AES;
            keySize = 128;
            significantKeySize = 128;
            break;
        case (int)CKM_RC4_KEY_GEN:
            keyType = CKK_RC4;
            keySize = 128;
            validateKeySize = true;
            break;
        case (int)CKM_BLOWFISH_KEY_GEN:
            keyType = CKK_BLOWFISH;
            keySize = 128;
            validateKeySize = true;
            break;
        default:
            throw new ProviderException("Unknown mechanism " + mechanism);
        }
        if (validateKeySize &&
            ((keySize > maxKeySize) || (keySize < minKeySize))) {
            throw new ProviderException("Unsupported key size");
        }
    }

    // see JCE spec
    protected void engineInit(SecureRandom random) {
        token.ensureValid();
        setDefaultKeySize();
    }

    // see JCE spec
    protected void engineInit(AlgorithmParameterSpec params,
            SecureRandom random) throws InvalidAlgorithmParameterException {
        throw new InvalidAlgorithmParameterException
                ("AlgorithmParameterSpec not supported");
    }

    // see JCE spec
    protected void engineInit(int keySize, SecureRandom random) {
        token.ensureValid();
        switch ((int)mechanism) {
        case (int)CKM_DES_KEY_GEN:
            if ((keySize != this.keySize) &&
                (keySize != this.significantKeySize)) {
                throw new InvalidParameterException
                        ("DES key length must be 56 bits");
            }
            break;
        case (int)CKM_DES2_KEY_GEN:
        case (int)CKM_DES3_KEY_GEN:
            long newMechanism;
            if ((keySize == 112) || (keySize == 128)) {
                newMechanism = CKM_DES2_KEY_GEN;
            } else if ((keySize == 168) || (keySize == 192)) {
                newMechanism = CKM_DES3_KEY_GEN;
            } else {
                throw new InvalidParameterException
                    ("DESede key length must be 112, or 168 bits");
            }
            if (mechanism != newMechanism) {
                if (supportBothKeySizes) {
                    mechanism = newMechanism;
                    setDefaultKeySize();
                } else {
                    throw new InvalidParameterException
                        ("Only " + significantKeySize +
                         "-bit DESede key length is supported");
                }
            }
            break;
        case (int)CKM_AES_KEY_GEN:
            if ((keySize != 128) && (keySize != 192) && (keySize != 256)) {
                throw new InvalidParameterException
                        ("AES key length must be 128, 192, or 256 bits");
            }
            this.keySize = keySize;
            significantKeySize = keySize;
            break;
        case (int)CKM_RC4_KEY_GEN:
        case (int)CKM_BLOWFISH_KEY_GEN:
            if ((keySize < minKeySize) || (keySize > maxKeySize)) {
                throw new InvalidParameterException
                    (algorithm + " key length must be between " +
                     minKeySize + " and " + maxKeySize + " bits");
            }
            this.keySize = keySize;
            this.significantKeySize = keySize;
            break;
        default:
            throw new ProviderException("Unknown mechanism " + mechanism);
        }
    }

    // see JCE spec
    protected SecretKey engineGenerateKey() {
        Session session = null;
        try {
            session = token.getObjSession();
            CK_ATTRIBUTE[] attributes;
            switch ((int)keyType) {
            case (int)CKK_DES:
            case (int)CKK_DES2:
            case (int)CKK_DES3:
                // fixed length, do not specify CKA_VALUE_LEN
                attributes = new CK_ATTRIBUTE[] {
                    new CK_ATTRIBUTE(CKA_CLASS, CKO_SECRET_KEY),
                };
                break;
            default:
                attributes = new CK_ATTRIBUTE[] {
                    new CK_ATTRIBUTE(CKA_CLASS, CKO_SECRET_KEY),
                    new CK_ATTRIBUTE(CKA_VALUE_LEN, keySize >> 3),
                };
                break;
            }
            attributes = token.getAttributes
                (O_GENERATE, CKO_SECRET_KEY, keyType, attributes);
            long keyID = token.p11.C_GenerateKey
                (session.id(), new CK_MECHANISM(mechanism), attributes);
            return P11Key.secretKey
                (session, keyID, algorithm, significantKeySize, attributes);
        } catch (PKCS11Exception e) {
            throw new ProviderException("Could not generate key", e);
        } finally {
            token.releaseSession(session);
        }
    }

}
