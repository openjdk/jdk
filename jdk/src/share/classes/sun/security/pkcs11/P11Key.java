/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.lang.ref.*;
import java.math.BigInteger;
import java.util.*;

import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;

import javax.crypto.*;
import javax.crypto.interfaces.*;
import javax.crypto.spec.*;

import sun.security.rsa.RSAPublicKeyImpl;

import sun.security.internal.interfaces.TlsMasterSecret;

import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;

import sun.security.util.DerValue;

/**
 * Key implementation classes.
 *
 * In PKCS#11, the components of private and secret keys may or may not
 * be accessible. If they are, we use the algorithm specific key classes
 * (e.g. DSAPrivateKey) for compatibility with existing applications.
 * If the components are not accessible, we use a generic class that
 * only implements PrivateKey (or SecretKey). Whether the components of a
 * key are extractable is automatically determined when the key object is
 * created.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
abstract class P11Key implements Key {

    private final static String PUBLIC = "public";
    private final static String PRIVATE = "private";
    private final static String SECRET = "secret";

    // type of key, one of (PUBLIC, PRIVATE, SECRET)
    final String type;

    // token instance
    final Token token;

    // algorithm name, returned by getAlgorithm(), etc.
    final String algorithm;

    // key id
    final long keyID;

    // effective key length of the key, e.g. 56 for a DES key
    final int keyLength;

    // flags indicating whether the key is a token object, sensitive, extractable
    final boolean tokenObject, sensitive, extractable;

    // phantom reference notification clean up for session keys
    private final SessionKeyRef sessionKeyRef;

    P11Key(String type, Session session, long keyID, String algorithm,
            int keyLength, CK_ATTRIBUTE[] attributes) {
        this.type = type;
        this.token = session.token;
        this.keyID = keyID;
        this.algorithm = algorithm;
        this.keyLength = keyLength;
        boolean tokenObject = false;
        boolean sensitive = false;
        boolean extractable = true;
        int n = (attributes == null) ? 0 : attributes.length;
        for (int i = 0; i < n; i++) {
            CK_ATTRIBUTE attr = attributes[i];
            if (attr.type == CKA_TOKEN) {
                tokenObject = attr.getBoolean();
            } else if (attr.type == CKA_SENSITIVE) {
                sensitive = attr.getBoolean();
            } else if (attr.type == CKA_EXTRACTABLE) {
                extractable = attr.getBoolean();
            }
        }
        this.tokenObject = tokenObject;
        this.sensitive = sensitive;
        this.extractable = extractable;
        if (tokenObject == false) {
            sessionKeyRef = new SessionKeyRef(this, keyID, session);
        } else {
            sessionKeyRef = null;
        }
    }

    // see JCA spec
    public final String getAlgorithm() {
        token.ensureValid();
        return algorithm;
    }

    // see JCA spec
    public final byte[] getEncoded() {
        byte[] b = getEncodedInternal();
        return (b == null) ? null : (byte[])b.clone();
    }

    abstract byte[] getEncodedInternal();

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        // equals() should never throw exceptions
        if (token.isValid() == false) {
            return false;
        }
        if (obj instanceof Key == false) {
            return false;
        }
        String thisFormat = getFormat();
        if (thisFormat == null) {
            // no encoding, key only equal to itself
            // XXX getEncoded() for unextractable keys will change that
            return false;
        }
        Key other = (Key)obj;
        if (thisFormat.equals(other.getFormat()) == false) {
            return false;
        }
        byte[] thisEnc = this.getEncodedInternal();
        byte[] otherEnc;
        if (obj instanceof P11Key) {
            otherEnc = ((P11Key)other).getEncodedInternal();
        } else {
            otherEnc = other.getEncoded();
        }
        return Arrays.equals(thisEnc, otherEnc);
    }

    public int hashCode() {
        // hashCode() should never throw exceptions
        if (token.isValid() == false) {
            return 0;
        }
        byte[] b1 = getEncodedInternal();
        if (b1 == null) {
            return 0;
        }
        int r = b1.length;
        for (int i = 0; i < b1.length; i++) {
            r += (b1[i] & 0xff) * 37;
        }
        return r;
    }

    protected Object writeReplace() throws ObjectStreamException {
        KeyRep.Type type;
        String format = getFormat();
        if (isPrivate() && "PKCS#8".equals(format)) {
            type = KeyRep.Type.PRIVATE;
        } else if (isPublic() && "X.509".equals(format)) {
            type = KeyRep.Type.PUBLIC;
        } else if (isSecret() && "RAW".equals(format)) {
            type = KeyRep.Type.SECRET;
        } else {
            // XXX short term serialization for unextractable keys
            throw new NotSerializableException
                ("Cannot serialize sensitive and unextractable keys");
        }
        return new KeyRep(type, getAlgorithm(), format, getEncoded());
    }

    public String toString() {
        token.ensureValid();
        String s1 = token.provider.getName() + " " + algorithm + " " + type
                + " key, " + keyLength + " bits";
        s1 += " (id " + keyID + ", "
                + (tokenObject ? "token" : "session") + " object";
        if (isPublic()) {
            s1 += ")";
        } else {
            s1 += ", " + (sensitive ? "" : "not ") + "sensitive";
            s1 += ", " + (extractable ? "" : "un") + "extractable)";
        }
        return s1;
    }

    int keyLength() {
        return keyLength;
    }

    boolean isPublic() {
        return type == PUBLIC;
    }

    boolean isPrivate() {
        return type == PRIVATE;
    }

    boolean isSecret() {
        return type == SECRET;
    }

    void fetchAttributes(CK_ATTRIBUTE[] attributes) {
        Session tempSession = null;
        try {
            tempSession = token.getOpSession();
            token.p11.C_GetAttributeValue(tempSession.id(), keyID, attributes);
        } catch (PKCS11Exception e) {
            throw new ProviderException(e);
        } finally {
            token.releaseSession(tempSession);
        }
    }

    private final static CK_ATTRIBUTE[] A0 = new CK_ATTRIBUTE[0];

    private static CK_ATTRIBUTE[] getAttributes(Session session, long keyID,
            CK_ATTRIBUTE[] knownAttributes, CK_ATTRIBUTE[] desiredAttributes) {
        if (knownAttributes == null) {
            knownAttributes = A0;
        }
        for (int i = 0; i < desiredAttributes.length; i++) {
            // For each desired attribute, check to see if we have the value
            // available already. If everything is here, we save a native call.
            CK_ATTRIBUTE attr = desiredAttributes[i];
            for (CK_ATTRIBUTE known : knownAttributes) {
                if ((attr.type == known.type) && (known.pValue != null)) {
                    attr.pValue = known.pValue;
                    break; // break inner for loop
                }
            }
            if (attr.pValue == null) {
                // nothing found, need to call C_GetAttributeValue()
                for (int j = 0; j < i; j++) {
                    // clear values copied from knownAttributes
                    desiredAttributes[j].pValue = null;
                }
                try {
                    session.token.p11.C_GetAttributeValue
                            (session.id(), keyID, desiredAttributes);
                } catch (PKCS11Exception e) {
                    throw new ProviderException(e);
                }
                break; // break loop, goto return
            }
        }
        return desiredAttributes;
    }

    static SecretKey secretKey(Session session, long keyID, String algorithm,
            int keyLength, CK_ATTRIBUTE[] attributes) {
        attributes = getAttributes(session, keyID, attributes, new CK_ATTRIBUTE[] {
            new CK_ATTRIBUTE(CKA_TOKEN),
            new CK_ATTRIBUTE(CKA_SENSITIVE),
            new CK_ATTRIBUTE(CKA_EXTRACTABLE),
        });
        return new P11SecretKey(session, keyID, algorithm, keyLength, attributes);
    }

    static SecretKey masterSecretKey(Session session, long keyID, String algorithm,
            int keyLength, CK_ATTRIBUTE[] attributes, int major, int minor) {
        attributes = getAttributes(session, keyID, attributes, new CK_ATTRIBUTE[] {
            new CK_ATTRIBUTE(CKA_TOKEN),
            new CK_ATTRIBUTE(CKA_SENSITIVE),
            new CK_ATTRIBUTE(CKA_EXTRACTABLE),
        });
        return new P11TlsMasterSecretKey
                (session, keyID, algorithm, keyLength, attributes, major, minor);
    }

    // we assume that all components of public keys are always accessible
    static PublicKey publicKey(Session session, long keyID, String algorithm,
            int keyLength, CK_ATTRIBUTE[] attributes) {
        if (algorithm.equals("RSA")) {
            return new P11RSAPublicKey
                (session, keyID, algorithm, keyLength, attributes);
        } else if (algorithm.equals("DSA")) {
            return new P11DSAPublicKey
                (session, keyID, algorithm, keyLength, attributes);
        } else if (algorithm.equals("DH")) {
            return new P11DHPublicKey
                (session, keyID, algorithm, keyLength, attributes);
        } else if (algorithm.equals("EC")) {
            return new P11ECPublicKey
                (session, keyID, algorithm, keyLength, attributes);
        } else {
            throw new ProviderException
                ("Unknown public key algorithm " + algorithm);
        }
    }

    static PrivateKey privateKey(Session session, long keyID, String algorithm,
            int keyLength, CK_ATTRIBUTE[] attributes) {
        attributes = getAttributes(session, keyID, attributes, new CK_ATTRIBUTE[] {
            new CK_ATTRIBUTE(CKA_TOKEN),
            new CK_ATTRIBUTE(CKA_SENSITIVE),
            new CK_ATTRIBUTE(CKA_EXTRACTABLE),
        });
        if (attributes[1].getBoolean() || (attributes[2].getBoolean() == false)) {
            return new P11PrivateKey
                (session, keyID, algorithm, keyLength, attributes);
        } else {
            if (algorithm.equals("RSA")) {
                // XXX better test for RSA CRT keys (single getAttributes() call)
                // we need to determine whether this is a CRT key
                // see if we can obtain the public exponent
                // this should also be readable for sensitive/extractable keys
                CK_ATTRIBUTE[] attrs2 = new CK_ATTRIBUTE[] {
                    new CK_ATTRIBUTE(CKA_PUBLIC_EXPONENT),
                };
                boolean crtKey;
                try {
                    session.token.p11.C_GetAttributeValue
                        (session.id(), keyID, attrs2);
                    crtKey = (attrs2[0].pValue instanceof byte[]);
                } catch (PKCS11Exception e) {
                    // ignore, assume not available
                    crtKey = false;
                }
                if (crtKey) {
                    return new P11RSAPrivateKey
                            (session, keyID, algorithm, keyLength, attributes);
                } else {
                    return new P11RSAPrivateNonCRTKey
                            (session, keyID, algorithm, keyLength, attributes);
                }
            } else if (algorithm.equals("DSA")) {
                return new P11DSAPrivateKey
                        (session, keyID, algorithm, keyLength, attributes);
            } else if (algorithm.equals("DH")) {
                return new P11DHPrivateKey
                        (session, keyID, algorithm, keyLength, attributes);
            } else if (algorithm.equals("EC")) {
                return new P11ECPrivateKey
                        (session, keyID, algorithm, keyLength, attributes);
            } else {
                throw new ProviderException
                        ("Unknown private key algorithm " + algorithm);
            }
        }
    }

    // class for sensitive and unextractable private keys
    private static final class P11PrivateKey extends P11Key
                                                implements PrivateKey {
        P11PrivateKey(Session session, long keyID, String algorithm,
                int keyLength, CK_ATTRIBUTE[] attributes) {
            super(PRIVATE, session, keyID, algorithm, keyLength, attributes);
        }
        // XXX temporary encoding for serialization purposes
        public String getFormat() {
            token.ensureValid();
            return null;
        }
        byte[] getEncodedInternal() {
            token.ensureValid();
            return null;
        }
    }

    private static class P11SecretKey extends P11Key implements SecretKey {
        private volatile byte[] encoded;
        P11SecretKey(Session session, long keyID, String algorithm,
                int keyLength, CK_ATTRIBUTE[] attributes) {
            super(SECRET, session, keyID, algorithm, keyLength, attributes);
        }
        public String getFormat() {
            token.ensureValid();
            if (sensitive || (extractable == false)) {
                return null;
            } else {
                return "RAW";
            }
        }
        byte[] getEncodedInternal() {
            token.ensureValid();
            if (getFormat() == null) {
                return null;
            }
            byte[] b = encoded;
            if (b == null) {
                synchronized (this) {
                    b = encoded;
                    if (b == null) {
                        Session tempSession = null;
                        try {
                            tempSession = token.getOpSession();
                            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
                                new CK_ATTRIBUTE(CKA_VALUE),
                            };
                            token.p11.C_GetAttributeValue
                                (tempSession.id(), keyID, attributes);
                            b = attributes[0].getByteArray();
                        } catch (PKCS11Exception e) {
                            throw new ProviderException(e);
                        } finally {
                            token.releaseSession(tempSession);
                        }
                        encoded = b;
                    }
                }
            }
            return b;
        }
    }

    private static class P11TlsMasterSecretKey extends P11SecretKey
            implements TlsMasterSecret {
        private final int majorVersion, minorVersion;
        P11TlsMasterSecretKey(Session session, long keyID, String algorithm,
                int keyLength, CK_ATTRIBUTE[] attributes, int major, int minor) {
            super(session, keyID, algorithm, keyLength, attributes);
            this.majorVersion = major;
            this.minorVersion = minor;
        }
        public int getMajorVersion() {
            return majorVersion;
        }

        public int getMinorVersion() {
            return minorVersion;
        }
    }

    // RSA CRT private key
    private static final class P11RSAPrivateKey extends P11Key
                implements RSAPrivateCrtKey {
        private BigInteger n, e, d, p, q, pe, qe, coeff;
        private byte[] encoded;
        P11RSAPrivateKey(Session session, long keyID, String algorithm,
                int keyLength, CK_ATTRIBUTE[] attributes) {
            super(PRIVATE, session, keyID, algorithm, keyLength, attributes);
        }
        private synchronized void fetchValues() {
            token.ensureValid();
            if (n != null) {
                return;
            }
            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
                new CK_ATTRIBUTE(CKA_MODULUS),
                new CK_ATTRIBUTE(CKA_PUBLIC_EXPONENT),
                new CK_ATTRIBUTE(CKA_PRIVATE_EXPONENT),
                new CK_ATTRIBUTE(CKA_PRIME_1),
                new CK_ATTRIBUTE(CKA_PRIME_2),
                new CK_ATTRIBUTE(CKA_EXPONENT_1),
                new CK_ATTRIBUTE(CKA_EXPONENT_2),
                new CK_ATTRIBUTE(CKA_COEFFICIENT),
            };
            fetchAttributes(attributes);
            n = attributes[0].getBigInteger();
            e = attributes[1].getBigInteger();
            d = attributes[2].getBigInteger();
            p = attributes[3].getBigInteger();
            q = attributes[4].getBigInteger();
            pe = attributes[5].getBigInteger();
            qe = attributes[6].getBigInteger();
            coeff = attributes[7].getBigInteger();
        }
        public String getFormat() {
            token.ensureValid();
            return "PKCS#8";
        }
        synchronized byte[] getEncodedInternal() {
            token.ensureValid();
            if (encoded == null) {
                fetchValues();
                try {
                    // XXX make constructor in SunRsaSign provider public
                    // and call it directly
                    KeyFactory factory = KeyFactory.getInstance
                        ("RSA", P11Util.getSunRsaSignProvider());
                    Key newKey = factory.translateKey(this);
                    encoded = newKey.getEncoded();
                } catch (GeneralSecurityException e) {
                    throw new ProviderException(e);
                }
            }
            return encoded;
        }
        public BigInteger getModulus() {
            fetchValues();
            return n;
        }
        public BigInteger getPublicExponent() {
            fetchValues();
            return e;
        }
        public BigInteger getPrivateExponent() {
            fetchValues();
            return d;
        }
        public BigInteger getPrimeP() {
            fetchValues();
            return p;
        }
        public BigInteger getPrimeQ() {
            fetchValues();
            return q;
        }
        public BigInteger getPrimeExponentP() {
            fetchValues();
            return pe;
        }
        public BigInteger getPrimeExponentQ() {
            fetchValues();
            return qe;
        }
        public BigInteger getCrtCoefficient() {
            fetchValues();
            return coeff;
        }
        public String toString() {
            fetchValues();
            StringBuilder sb = new StringBuilder(super.toString());
            sb.append("\n  modulus:          ");
            sb.append(n);
            sb.append("\n  public exponent:  ");
            sb.append(e);
            sb.append("\n  private exponent: ");
            sb.append(d);
            sb.append("\n  prime p:          ");
            sb.append(p);
            sb.append("\n  prime q:          ");
            sb.append(q);
            sb.append("\n  prime exponent p: ");
            sb.append(pe);
            sb.append("\n  prime exponent q: ");
            sb.append(qe);
            sb.append("\n  crt coefficient:  ");
            sb.append(coeff);
            return sb.toString();
        }
    }

    // RSA non-CRT private key
    private static final class P11RSAPrivateNonCRTKey extends P11Key
                implements RSAPrivateKey {
        private BigInteger n, d;
        private byte[] encoded;
        P11RSAPrivateNonCRTKey(Session session, long keyID, String algorithm,
                int keyLength, CK_ATTRIBUTE[] attributes) {
            super(PRIVATE, session, keyID, algorithm, keyLength, attributes);
        }
        private synchronized void fetchValues() {
            token.ensureValid();
            if (n != null) {
                return;
            }
            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
                new CK_ATTRIBUTE(CKA_MODULUS),
                new CK_ATTRIBUTE(CKA_PRIVATE_EXPONENT),
            };
            fetchAttributes(attributes);
            n = attributes[0].getBigInteger();
            d = attributes[1].getBigInteger();
        }
        public String getFormat() {
            token.ensureValid();
            return "PKCS#8";
        }
        synchronized byte[] getEncodedInternal() {
            token.ensureValid();
            if (encoded == null) {
                fetchValues();
                try {
                    // XXX make constructor in SunRsaSign provider public
                    // and call it directly
                    KeyFactory factory = KeyFactory.getInstance
                        ("RSA", P11Util.getSunRsaSignProvider());
                    Key newKey = factory.translateKey(this);
                    encoded = newKey.getEncoded();
                } catch (GeneralSecurityException e) {
                    throw new ProviderException(e);
                }
            }
            return encoded;
        }
        public BigInteger getModulus() {
            fetchValues();
            return n;
        }
        public BigInteger getPrivateExponent() {
            fetchValues();
            return d;
        }
        public String toString() {
            fetchValues();
            StringBuilder sb = new StringBuilder(super.toString());
            sb.append("\n  modulus:          ");
            sb.append(n);
            sb.append("\n  private exponent: ");
            sb.append(d);
            return sb.toString();
        }
    }

    private static final class P11RSAPublicKey extends P11Key
                                                implements RSAPublicKey {
        private BigInteger n, e;
        private byte[] encoded;
        P11RSAPublicKey(Session session, long keyID, String algorithm,
                int keyLength, CK_ATTRIBUTE[] attributes) {
            super(PUBLIC, session, keyID, algorithm, keyLength, attributes);
        }
        private synchronized void fetchValues() {
            token.ensureValid();
            if (n != null) {
                return;
            }
            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
                new CK_ATTRIBUTE(CKA_MODULUS),
                new CK_ATTRIBUTE(CKA_PUBLIC_EXPONENT),
            };
            fetchAttributes(attributes);
            n = attributes[0].getBigInteger();
            e = attributes[1].getBigInteger();
        }
        public String getFormat() {
            token.ensureValid();
            return "X.509";
        }
        synchronized byte[] getEncodedInternal() {
            token.ensureValid();
            if (encoded == null) {
                fetchValues();
                try {
                    encoded = new RSAPublicKeyImpl(n, e).getEncoded();
                } catch (InvalidKeyException e) {
                    throw new ProviderException(e);
                }
            }
            return encoded;
        }
        public BigInteger getModulus() {
            fetchValues();
            return n;
        }
        public BigInteger getPublicExponent() {
            fetchValues();
            return e;
        }
        public String toString() {
            fetchValues();
            return super.toString() +  "\n  modulus: " + n
                + "\n  public exponent: " + e;
        }
    }

    private static final class P11DSAPublicKey extends P11Key
                                                implements DSAPublicKey {
        private BigInteger y;
        private DSAParams params;
        private byte[] encoded;
        P11DSAPublicKey(Session session, long keyID, String algorithm,
                int keyLength, CK_ATTRIBUTE[] attributes) {
            super(PUBLIC, session, keyID, algorithm, keyLength, attributes);
        }
        private synchronized void fetchValues() {
            token.ensureValid();
            if (y != null) {
                return;
            }
            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
                new CK_ATTRIBUTE(CKA_VALUE),
                new CK_ATTRIBUTE(CKA_PRIME),
                new CK_ATTRIBUTE(CKA_SUBPRIME),
                new CK_ATTRIBUTE(CKA_BASE),
            };
            fetchAttributes(attributes);
            y = attributes[0].getBigInteger();
            params = new DSAParameterSpec(
                attributes[1].getBigInteger(),
                attributes[2].getBigInteger(),
                attributes[3].getBigInteger()
            );
        }
        public String getFormat() {
            token.ensureValid();
            return "X.509";
        }
        synchronized byte[] getEncodedInternal() {
            token.ensureValid();
            if (encoded == null) {
                fetchValues();
                try {
                    Key key = new sun.security.provider.DSAPublicKey
                            (y, params.getP(), params.getQ(), params.getG());
                    encoded = key.getEncoded();
                } catch (InvalidKeyException e) {
                    throw new ProviderException(e);
                }
            }
            return encoded;
        }
        public BigInteger getY() {
            fetchValues();
            return y;
        }
        public DSAParams getParams() {
            fetchValues();
            return params;
        }
        public String toString() {
            fetchValues();
            return super.toString() +  "\n  y: " + y + "\n  p: " + params.getP()
                + "\n  q: " + params.getQ() + "\n  g: " + params.getG();
        }
    }

    private static final class P11DSAPrivateKey extends P11Key
                                                implements DSAPrivateKey {
        private BigInteger x;
        private DSAParams params;
        private byte[] encoded;
        P11DSAPrivateKey(Session session, long keyID, String algorithm,
                int keyLength, CK_ATTRIBUTE[] attributes) {
            super(PRIVATE, session, keyID, algorithm, keyLength, attributes);
        }
        private synchronized void fetchValues() {
            token.ensureValid();
            if (x != null) {
                return;
            }
            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
                new CK_ATTRIBUTE(CKA_VALUE),
                new CK_ATTRIBUTE(CKA_PRIME),
                new CK_ATTRIBUTE(CKA_SUBPRIME),
                new CK_ATTRIBUTE(CKA_BASE),
            };
            fetchAttributes(attributes);
            x = attributes[0].getBigInteger();
            params = new DSAParameterSpec(
                attributes[1].getBigInteger(),
                attributes[2].getBigInteger(),
                attributes[3].getBigInteger()
            );
        }
        public String getFormat() {
            token.ensureValid();
            return "PKCS#8";
        }
        synchronized byte[] getEncodedInternal() {
            token.ensureValid();
            if (encoded == null) {
                fetchValues();
                try {
                    Key key = new sun.security.provider.DSAPrivateKey
                            (x, params.getP(), params.getQ(), params.getG());
                    encoded = key.getEncoded();
                } catch (InvalidKeyException e) {
                    throw new ProviderException(e);
                }
            }
            return encoded;
        }
        public BigInteger getX() {
            fetchValues();
            return x;
        }
        public DSAParams getParams() {
            fetchValues();
            return params;
        }
        public String toString() {
            fetchValues();
            return super.toString() +  "\n  x: " + x + "\n  p: " + params.getP()
                + "\n  q: " + params.getQ() + "\n  g: " + params.getG();
        }
    }

    private static final class P11DHPrivateKey extends P11Key
                                                implements DHPrivateKey {
        private BigInteger x;
        private DHParameterSpec params;
        private byte[] encoded;
        P11DHPrivateKey(Session session, long keyID, String algorithm,
                int keyLength, CK_ATTRIBUTE[] attributes) {
            super(PRIVATE, session, keyID, algorithm, keyLength, attributes);
        }
        private synchronized void fetchValues() {
            token.ensureValid();
            if (x != null) {
                return;
            }
            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
                new CK_ATTRIBUTE(CKA_VALUE),
                new CK_ATTRIBUTE(CKA_PRIME),
                new CK_ATTRIBUTE(CKA_BASE),
            };
            fetchAttributes(attributes);
            x = attributes[0].getBigInteger();
            params = new DHParameterSpec(
                attributes[1].getBigInteger(),
                attributes[2].getBigInteger()
            );
        }
        public String getFormat() {
            token.ensureValid();
            return "PKCS#8";
        }
        synchronized byte[] getEncodedInternal() {
            token.ensureValid();
            if (encoded == null) {
                fetchValues();
                try {
                    DHPrivateKeySpec spec = new DHPrivateKeySpec
                        (x, params.getP(), params.getG());
                    KeyFactory kf = KeyFactory.getInstance
                        ("DH", P11Util.getSunJceProvider());
                    Key key = kf.generatePrivate(spec);
                    encoded = key.getEncoded();
                } catch (GeneralSecurityException e) {
                    throw new ProviderException(e);
                }
            }
            return encoded;
        }
        public BigInteger getX() {
            fetchValues();
            return x;
        }
        public DHParameterSpec getParams() {
            fetchValues();
            return params;
        }
        public String toString() {
            fetchValues();
            return super.toString() +  "\n  x: " + x + "\n  p: " + params.getP()
                + "\n  g: " + params.getG();
        }
    }

    private static final class P11DHPublicKey extends P11Key
                                                implements DHPublicKey {
        private BigInteger y;
        private DHParameterSpec params;
        private byte[] encoded;
        P11DHPublicKey(Session session, long keyID, String algorithm,
                int keyLength, CK_ATTRIBUTE[] attributes) {
            super(PUBLIC, session, keyID, algorithm, keyLength, attributes);
        }
        private synchronized void fetchValues() {
            token.ensureValid();
            if (y != null) {
                return;
            }
            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
                new CK_ATTRIBUTE(CKA_VALUE),
                new CK_ATTRIBUTE(CKA_PRIME),
                new CK_ATTRIBUTE(CKA_BASE),
            };
            fetchAttributes(attributes);
            y = attributes[0].getBigInteger();
            params = new DHParameterSpec(
                attributes[1].getBigInteger(),
                attributes[2].getBigInteger()
            );
        }
        public String getFormat() {
            token.ensureValid();
            return "X.509";
        }
        synchronized byte[] getEncodedInternal() {
            token.ensureValid();
            if (encoded == null) {
                fetchValues();
                try {
                    DHPublicKeySpec spec = new DHPublicKeySpec
                        (y, params.getP(), params.getG());
                    KeyFactory kf = KeyFactory.getInstance
                        ("DH", P11Util.getSunJceProvider());
                    Key key = kf.generatePublic(spec);
                    encoded = key.getEncoded();
                } catch (GeneralSecurityException e) {
                    throw new ProviderException(e);
                }
            }
            return encoded;
        }
        public BigInteger getY() {
            fetchValues();
            return y;
        }
        public DHParameterSpec getParams() {
            fetchValues();
            return params;
        }
        public String toString() {
            fetchValues();
            return super.toString() +  "\n  y: " + y + "\n  p: " + params.getP()
                + "\n  g: " + params.getG();
        }
    }

    private static final class P11ECPrivateKey extends P11Key
                                                implements ECPrivateKey {
        private BigInteger s;
        private ECParameterSpec params;
        private byte[] encoded;
        P11ECPrivateKey(Session session, long keyID, String algorithm,
                int keyLength, CK_ATTRIBUTE[] attributes) {
            super(PRIVATE, session, keyID, algorithm, keyLength, attributes);
        }
        private synchronized void fetchValues() {
            token.ensureValid();
            if (s != null) {
                return;
            }
            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
                new CK_ATTRIBUTE(CKA_VALUE),
                new CK_ATTRIBUTE(CKA_EC_PARAMS, params),
            };
            fetchAttributes(attributes);
            s = attributes[0].getBigInteger();
            try {
                params = P11ECKeyFactory.decodeParameters
                            (attributes[1].getByteArray());
            } catch (Exception e) {
                throw new RuntimeException("Could not parse key values", e);
            }
        }
        public String getFormat() {
            token.ensureValid();
            return "PKCS#8";
        }
        synchronized byte[] getEncodedInternal() {
            token.ensureValid();
            if (encoded == null) {
                fetchValues();
                try {
                    Key key = new sun.security.ec.ECPrivateKeyImpl(s, params);
                    encoded = key.getEncoded();
                } catch (InvalidKeyException e) {
                    throw new ProviderException(e);
                }
            }
            return encoded;
        }
        public BigInteger getS() {
            fetchValues();
            return s;
        }
        public ECParameterSpec getParams() {
            fetchValues();
            return params;
        }
        public String toString() {
            fetchValues();
        return super.toString()
            + "\n  private value:  " + s
            + "\n  parameters: " + params;
        }
    }

    private static final class P11ECPublicKey extends P11Key
                                                implements ECPublicKey {
        private ECPoint w;
        private ECParameterSpec params;
        private byte[] encoded;
        P11ECPublicKey(Session session, long keyID, String algorithm,
                int keyLength, CK_ATTRIBUTE[] attributes) {
            super(PUBLIC, session, keyID, algorithm, keyLength, attributes);
        }
        private synchronized void fetchValues() {
            token.ensureValid();
            if (w != null) {
                return;
            }
            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
                new CK_ATTRIBUTE(CKA_EC_POINT),
                new CK_ATTRIBUTE(CKA_EC_PARAMS),
            };
            fetchAttributes(attributes);

            try {
                params = P11ECKeyFactory.decodeParameters
                            (attributes[1].getByteArray());

                /*
                 * An uncompressed EC point may be in either of two formats.
                 * First try the OCTET STRING encoding:
                 *   04 <length> 04 <X-coordinate> <Y-coordinate>
                 *
                 * Otherwise try the raw encoding:
                 *   04 <X-coordinate> <Y-coordinate>
                 */
                byte[] ecKey = attributes[0].getByteArray();

                try {
                    DerValue wECPoint = new DerValue(ecKey);
                    if (wECPoint.getTag() != DerValue.tag_OctetString)
                        throw new IOException("Unexpected tag: " +
                            wECPoint.getTag());

                    w = P11ECKeyFactory.decodePoint
                        (wECPoint.getDataBytes(), params.getCurve());

                } catch (IOException e) {
                    // Failover
                    w = P11ECKeyFactory.decodePoint(ecKey, params.getCurve());
                }

            } catch (Exception e) {
                throw new RuntimeException("Could not parse key values", e);
            }
        }
        public String getFormat() {
            token.ensureValid();
            return "X.509";
        }
        synchronized byte[] getEncodedInternal() {
            token.ensureValid();
            if (encoded == null) {
                fetchValues();
                try {
                    Key key = new sun.security.ec.ECPublicKeyImpl(w, params);
                    encoded = key.getEncoded();
                } catch (InvalidKeyException e) {
                    throw new ProviderException(e);
                }
            }
            return encoded;
        }
        public ECPoint getW() {
            fetchValues();
            return w;
        }
        public ECParameterSpec getParams() {
            fetchValues();
            return params;
        }
        public String toString() {
            fetchValues();
            return super.toString()
                + "\n  public x coord: " + w.getAffineX()
                + "\n  public y coord: " + w.getAffineY()
                + "\n  parameters: " + params;
        }
    }
}

/*
 * NOTE: Must use PhantomReference here and not WeakReference
 * otherwise the key maybe cleared before other objects which
 * still use these keys during finalization such as SSLSocket.
 */
final class SessionKeyRef extends PhantomReference<P11Key>
    implements Comparable<SessionKeyRef> {
    private static ReferenceQueue<P11Key> refQueue =
        new ReferenceQueue<P11Key>();
    private static Set<SessionKeyRef> refList =
        Collections.synchronizedSortedSet(new TreeSet<SessionKeyRef>());

    static ReferenceQueue<P11Key> referenceQueue() {
        return refQueue;
    }

    private static void drainRefQueueBounded() {
        while (true) {
            SessionKeyRef next = (SessionKeyRef) refQueue.poll();
            if (next == null) break;
            next.dispose();
        }
    }

    // handle to the native key
    private long keyID;
    private Session session;

    SessionKeyRef(P11Key key , long keyID, Session session) {
        super(key, refQueue);
        this.keyID = keyID;
        this.session = session;
        this.session.addObject();
        refList.add(this);
        // TBD: run at some interval and not every time?
        drainRefQueueBounded();
    }

    private void dispose() {
        refList.remove(this);
        if (session.token.isValid()) {
            Session newSession = null;
            try {
                newSession = session.token.getOpSession();
                session.token.p11.C_DestroyObject(newSession.id(), keyID);
            } catch (PKCS11Exception e) {
                // ignore
            } finally {
                this.clear();
                session.token.releaseSession(newSession);
                session.removeObject();
            }
        }
    }

    public int compareTo(SessionKeyRef other) {
        if (this.keyID == other.keyID) {
            return 0;
        } else {
            return (this.keyID < other.keyID) ? -1 : 1;
        }
    }
}
