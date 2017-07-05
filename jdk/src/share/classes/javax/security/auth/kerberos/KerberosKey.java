/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.security.auth.kerberos;

import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.security.auth.Destroyable;
import javax.security.auth.DestroyFailedException;

/**
 * This class encapsulates a long term secret key for a Kerberos
 * principal.<p>
 *
 * All Kerberos JAAS login modules that obtain a principal's password and
 * generate the secret key from it should use this class.
 * Sometimes, such as when authenticating a server in
 * the absence of user-to-user authentication, the login module will store
 * an instance of this class in the private credential set of a
 * {@link javax.security.auth.Subject Subject} during the commit phase of the
 * authentication process.<p>
 *
 * A Kerberos service using a keytab to read secret keys should use
 * the {@link KeyTab} class, where latest keys can be read when needed.<p>
 *
 * It might be necessary for the application to be granted a
 * {@link javax.security.auth.PrivateCredentialPermission
 * PrivateCredentialPermission} if it needs to access the KerberosKey
 * instance from a Subject. This permission is not needed when the
 * application depends on the default JGSS Kerberos mechanism to access the
 * KerberosKey. In that case, however, the application will need an
 * appropriate
 * {@link javax.security.auth.kerberos.ServicePermission ServicePermission}.
 *
 * @author Mayank Upadhyay
 * @since 1.4
 */
public class KerberosKey implements SecretKey, Destroyable {

    private static final long serialVersionUID = -4625402278148246993L;

   /**
     * The principal that this secret key belongs to.
     *
     * @serial
     */
    private KerberosPrincipal principal;

   /**
     * the version number of this secret key
     *
     * @serial
     */
    private int versionNum;

   /**
    * {@code KeyImpl} is serialized by writing out the ASN1 Encoded bytes
    * of the encryption key.
    * The ASN1 encoding is defined in RFC4120 and as  follows:
    * <pre>
    * EncryptionKey   ::= SEQUENCE {
    *           keytype   [0] Int32 -- actually encryption type --,
    *           keyvalue  [1] OCTET STRING
    * }
    * </pre>
    *
    * @serial
    */

    private KeyImpl key;
    private transient boolean destroyed = false;

    /**
     * Constructs a KerberosKey from the given bytes when the key type and
     * key version number are known. This can be used when reading the secret
     * key information from a Kerberos "keytab".
     *
     * @param principal the principal that this secret key belongs to
     * @param keyBytes the raw bytes for the secret key
     * @param keyType the key type for the secret key as defined by the
     * Kerberos protocol specification.
     * @param versionNum the version number of this secret key
     */
    public KerberosKey(KerberosPrincipal principal,
                       byte[] keyBytes,
                       int keyType,
                       int versionNum) {
        this.principal = principal;
        this.versionNum = versionNum;
        key = new KeyImpl(keyBytes, keyType);
    }

    /**
     * Constructs a KerberosKey from a principal's password.
     *
     * @param principal the principal that this password belongs to
     * @param password the password that should be used to compute the key
     * @param algorithm the name for the algorithm that this key will be
     * used for. This parameter may be null in which case the default
     * algorithm "DES" will be assumed.
     * @throws IllegalArgumentException if the name of the
     * algorithm passed is unsupported.
     */
    public KerberosKey(KerberosPrincipal principal,
                       char[] password,
                       String algorithm) {

        this.principal = principal;
        // Pass principal in for salt
        key = new KeyImpl(principal, password, algorithm);
    }

    /**
     * Returns the principal that this key belongs to.
     *
     * @return the principal this key belongs to.
     */
    public final KerberosPrincipal getPrincipal() {
        if (destroyed)
            throw new IllegalStateException("This key is no longer valid");
        return principal;
    }

    /**
     * Returns the key version number.
     *
     * @return the key version number.
     */
    public final int getVersionNumber() {
        if (destroyed)
            throw new IllegalStateException("This key is no longer valid");
        return versionNum;
    }

    /**
     * Returns the key type for this long-term key.
     *
     * @return the key type.
     */
    public final int getKeyType() {
        if (destroyed)
            throw new IllegalStateException("This key is no longer valid");
        return key.getKeyType();
    }

    /*
     * Methods from java.security.Key
     */

    /**
     * Returns the standard algorithm name for this key. For
     * example, "DES" would indicate that this key is a DES key.
     * See Appendix A in the <a href=
     * "../../../../../technotes/guides/security/crypto/CryptoSpec.html#AppA">
     * Java Cryptography Architecture API Specification &amp; Reference
     * </a>
     * for information about standard algorithm names.
     *
     * @return the name of the algorithm associated with this key.
     */
    public final String getAlgorithm() {
        if (destroyed)
            throw new IllegalStateException("This key is no longer valid");
        return key.getAlgorithm();
    }

    /**
     * Returns the name of the encoding format for this secret key.
     *
     * @return the String "RAW"
     */
    public final String getFormat() {
        if (destroyed)
            throw new IllegalStateException("This key is no longer valid");
        return key.getFormat();
    }

    /**
     * Returns the key material of this secret key.
     *
     * @return the key material
     */
    public final byte[] getEncoded() {
        if (destroyed)
            throw new IllegalStateException("This key is no longer valid");
        return key.getEncoded();
    }

    /**
     * Destroys this key. A call to any of its other methods after this
     * will cause an  IllegalStateException to be thrown.
     *
     * @throws DestroyFailedException if some error occurs while destorying
     * this key.
     */
    public void destroy() throws DestroyFailedException {
        if (!destroyed) {
            key.destroy();
            principal = null;
            destroyed = true;
        }
    }


    /** Determines if this key has been destroyed.*/
    public boolean isDestroyed() {
        return destroyed;
    }

    public String toString() {
        if (destroyed) {
            return "Destroyed Principal";
        }
        return "Kerberos Principal " + principal.toString() +
                "Key Version " + versionNum +
                "key "  + key.toString();
    }

    /**
     * Returns a hashcode for this KerberosKey.
     *
     * @return a hashCode() for the {@code KerberosKey}
     * @since 1.6
     */
    public int hashCode() {
        int result = 17;
        if (isDestroyed()) {
            return result;
        }
        result = 37 * result + Arrays.hashCode(getEncoded());
        result = 37 * result + getKeyType();
        if (principal != null) {
            result = 37 * result + principal.hashCode();
        }
        return result * 37 + versionNum;
    }

    /**
     * Compares the specified Object with this KerberosKey for equality.
     * Returns true if the given object is also a
     * {@code KerberosKey} and the two
     * {@code KerberosKey} instances are equivalent.
     *
     * @param other the Object to compare to
     * @return true if the specified object is equal to this KerberosKey,
     * false otherwise. NOTE: Returns false if either of the KerberosKey
     * objects has been destroyed.
     * @since 1.6
     */
    public boolean equals(Object other) {

        if (other == this)
            return true;

        if (! (other instanceof KerberosKey)) {
            return false;
        }

        KerberosKey otherKey = ((KerberosKey) other);
        if (isDestroyed() || otherKey.isDestroyed()) {
            return false;
        }

        if (versionNum != otherKey.getVersionNumber() ||
                getKeyType() != otherKey.getKeyType() ||
                !Arrays.equals(getEncoded(), otherKey.getEncoded())) {
            return false;
        }

        if (principal == null) {
            if (otherKey.getPrincipal() != null) {
                return false;
            }
        } else {
            if (!principal.equals(otherKey.getPrincipal())) {
                return false;
            }
        }

        return true;
    }
}
