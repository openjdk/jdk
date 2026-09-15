/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package javax.crypto.spec;

import java.nio.charset.Charset;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import javax.security.auth.Destroyable;
import sun.security.util.Debug;
import sun.security.util.PBEUtil;

/**
 * Parameters for the Argon2 Memory-Hard Function for Password Hashing
 * and Proof-of-Work Applications as specified in
 * <a href="https://www.rfc-editor.org/rfc/rfc9106.html">RFC 9106</a>.
 *
 * <p>An {@code Argon2ParameterSpec} object contains the inputs used by an
 * Argon2 key derivation function: the password, salt, memory cost, number of
 * iterations, degree of parallelism, output tag length, version, and optional
 * secret and associated data.
 *
 * <p>This class can be used to initialize a {@link javax.crypto.KDF} object
 * for one of the {@code Argon2} algorithm: {@code Argon2i}, {@code Argon2d},
 * or {@code Argon2id}.
 *
 * @spec https://www.rfc-editor.org/info/rfc9106
 *      RFC 9106: Argon2 Memory-Hard Function for Password Hashing and Proof-of-Work Applications
 * @spec security/standard-names.html
 *      Java Security Standard Algorithm Names
 * @see javax.crypto.KDF
 * @since 28
 */
public final class Argon2ParameterSpec implements AlgorithmParameterSpec,
        Destroyable {

    /**
     * Version number of the Argon2 algorithm.
     */
    public enum Version {
        /**
         * version 1.0 ({@code 0x10}).
         */
        V10(0x10),
        /**
         * version 1.3 ({@code 0x13}), as specified in RFC 9106.
         */
        V13(0x13);

        int value;

        Version(int value) {
            this.value = value;
        }

        /**
         * {@return 16 for V10, 19 for V13}.
         */
        public int value() {
            return value;
        }

        /**
         * Returns the {@code Version} for the specified encoded value.
         *
         * @param value the encoded value
         * @return the {@code Version} for {@code value}
         * @throws IllegalArgumentException if {@code value} does not
         *         correspond to a supported Argon2 version
         */
        public static Version of(int value) {
            switch (value) {
                case 16 ->{ return V10; }
                case 19 ->{ return V13; }
                default ->{
                    throw new IllegalArgumentException
                            ("Invalid version value " + value);
                }
            }
        }
    };

    /**
     * This {@code Builder} constructs {@code Argon2ParameterSpec} objects.
     *
     * <p>Obtain a {@code Builder} by calling
     * {@code Argon2ParameterSpec.newBuilder}. Supply the required parameters
     * with the builder methods, then call {@code build} to create the
     * {@code Argon2ParameterSpec} object.
     *
     * <p>Note that the {@code Builder} is not thread-safe.
     */
    public static final class Builder {

        private static int NONCE_LEN_MIN = 8;
        private static int P_MAX = 16777215; // 2^24 - 1
        private static int M_MIN = 8; // since p >= 1
        private static int MP_MAX = 30; // java integer max 2^31 - 1
        private static int TAGLEN_MIN = 4;
        // defaults to the version in RFC 9106
        private Version ver = Version.V13;
        private int p;
        private int tagLen;
        private int memory;
        private int t;
        private byte[] k = B0; // optional
        private byte[] x = B0; // optional

        private static <T> T checkNonNull(T o, String name) {
            if (o == null) {
                throw new IllegalArgumentException("Argon2 " + name +
                        " can't be null");
            }
            return o;
        }

        private static byte[] checkBytes(byte[] b, int minLen, String name) {
            checkNonNull(b, name);
            if (b.length < minLen) {
                throw new IllegalArgumentException("Argon2 " + name +
                        " must be at least " + minLen + " bytes");
            }
            return b;
        }

        private static int checkInteger(int i, int min, int max, String name) {
            if (min != -1 && i < min) {
                throw new IllegalArgumentException("Argon2 " + name +
                        " must be at least " + min);
            }
            if (max != -1 && i > max) {
                throw new IllegalArgumentException("Argon2 " + name +
                        " must be no more than " + max);
            }
            return i;
        }

        // return the ceiling of log2 value
        private static int ceilingOfLog2(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("Input must be positive");
            }
            return 32 - Integer.numberOfLeadingZeros(n - 1);
        }

        private Builder() {
        }

        /**
         * Sets the memory cost, in KiB.
         *
         * @param m the memory cost, in KiB
         * @return this builder
         * @throws IllegalArgumentException
         *         if {@code m} is less than 8; or if {@code parallelism(p)}
         *         has already been called and {@code m} is less than
         *         {@code 8 * p}
         */
        public Builder memoryKiB(int m) {
            this.memory = checkInteger(m, (p > 0 ? p << 3 : M_MIN),
                    -1, "memory cost in KiB");
            return this;
        }

        /**
         * Sets the memory cost to {@code 2}<sup>{@code mPower}</sup> KiB.
         *
         * @param mPower the base-2 exponent used to derive the memory cost
         * @return this builder
         * @throws IllegalArgumentException
         *         if {@code mPower} is less than 3 or greater than 30; or if
         *         {@code parallelism(p)} has already been called and
         *         {@code mPower} is less than {@code 3 + ceil(log2(p))}
         */
        public Builder memoryPowerOfTwo(int mPower) {
            checkInteger(mPower, ((p > 0 ? ceilingOfLog2(p) : 0) + 3),
                    MP_MAX, "memory cost in power of two");
            this.memory = 1 << mPower;
            return this;
        }

        /**
         * Sets the number of iterations.
         *
         * @param t the number of iterations
         * @return this builder
         * @throws IllegalArgumentException if {@code t} is not positive
         */
        public Builder iterations(int t) {
            this.t = checkInteger(t, 1, -1, "iterations");
            return this;
        }

        /**
         * Sets the degree of parallelism.
         *
         * @param p the degree of parallelism
         * @return this builder
         * @throws IllegalArgumentException if {@code p} is not positive,
         *         greater than {@code 16777215}, or greater than {@code m / 8}
         *         if the memory cost has already been set
         */
        public Builder parallelism(int p) {
            int max = (memory > 0 ? Math.min(P_MAX, memory >>> 3) : P_MAX);
            this.p = checkInteger(p, 1, max, "parallellism");
            return this;
        }

        /**
         * Sets the output tag length, in bytes.
         *
         * @param tagLen the output tag length, in bytes
         * @return this builder
         * @throws IllegalArgumentException if {@code tagLen} is less than 4
         */
        public Builder tagLen(int tagLen) {
            this.tagLen = checkInteger(tagLen, TAGLEN_MIN, -1, "tag length");
            return this;
        }

        /**
         * Sets the Argon2 version.
         *
         * @param ver the Argon2 Version
         * @return this builder
         * @throws IllegalArgumentException if {@code ver} is {@code null}
         */
        public Builder version(Version ver) {
            checkNonNull(ver, "version");
            this.ver = ver;
            return this;
        }

        /**
         * Sets the optional secret value.
         *
         * @param k the secret value
         * @return this builder
         * @throws IllegalArgumentException if {@code k} is {@code null}
         */
        public Builder secret(byte[] k) {
            checkNonNull(k, "secret");
            if (this.k != B0) {
                Arrays.fill(this.k, (byte)0);
            }
            this.k = (k.length > 0 ? k.clone() : B0);
            return this;
        }

        /**
         * Sets the optional associated data.
         *
         * @param x the associated data
         * @return this builder
         * @throws IllegalArgumentException if {@code x} is {@code null}
         */
        public Builder associatedData(byte[] x) {
            checkNonNull(x, "associated data");
            this.x = (x.length > 0 ? x.clone() : B0);
            return this;
        }

        /**
         * Builds an {@code Argon2ParameterSpec} object from the specified
         * {@code salt}, {@code password}, and builder parameters.
         *
         * @param salt the salt
         * @param password the password bytes
         * @return a new {@code Argon2ParameterSpec} object
         * @throws IllegalArgumentException if {@code salt} is {@code null} or
         *         has fewer than 8 bytes; if {@code password} is {@code null};
         *         or if any required builder parameter has not been set
         */
        public Argon2ParameterSpec build(byte[] salt, byte[] password) {
            checkBytes(salt, NONCE_LEN_MIN, "salt");
            checkNonNull(password, "password");
            // validate the other parameters to make sure they are all set
            checkInteger(this.tagLen, TAGLEN_MIN, -1, "tag length");
            checkInteger(this.p, 1, P_MAX, "parallelism");
            checkInteger(this.memory, p << 3, -1, "memory");
            checkInteger(this.t, 1, -1, "iterations");
            return new Argon2ParameterSpec(this, salt, password);
        }

        /**
         * Encodes the specified {@code passwdChar} with the specified
         * charset {@code cs}, then builds an {@code Argon2ParameterSpec}
         * object from the resulting password bytes, {@code salt}, and
         * builder parameters.
         *
         * @param salt the salt
         * @param passwdChar the password characters
         * @param cs the charset used to encode {@code passwdChar}
         * @return an {@code Argon2ParameterSpec} object
         * @throws IllegalArgumentException if {@code salt} is {@code null}
         *         or has fewer than 8 bytes; if {@code passwdChar} or
         *         {@code cs} is {@code null}; or if any required builder
         *         parameter has not been set.
         */
        public Argon2ParameterSpec build(byte[] salt, char[] passwdChar,
                Charset cs) {
            checkNonNull(passwdChar, "password char[]");
            checkNonNull(cs, "charset");
            byte[] password = PBEUtil.encodePassword(passwdChar, cs);
            try {
                return build(salt, password);
            } finally {
                Arrays.fill(password, (byte)0);
            }
        }
    };

    /**
     * {@return a new {@code Builder} for {@code Argon2ParameterSpec}}
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    private static final byte[] B0 = new byte[0];

    /*
     * Argon2 inputs parameters; see section 3.1 of the Argon2, i.e.
     * https://raw.githubusercontent.com/P-H-C/phc-winner-argon2/master/argon2-specs.pdf
     */
    // password bytes, len >= 0; clearable thus non-final
    private byte[] passwd;
    // salt bytes, len >= 8; 16 bytes is RECOMMENDED for password hashing
    private final byte[] salt;
    // memory in KiB
    private final int memory;
    // number of iterations, min = 1
    private final int t;
    // degree of parallelism, 1...2^24-1
    private final int p;
    // output length in bytes, min = 4
    private final int tagLen;
    // version, i.e. V10 or V13 (official)
    private final Version ver;
    // optional secret value, used for keyed hashing; clearable thus non-final
    private byte[] k;
    // optional associated data, used to fold any additional data into the
    // output hash
    private final byte[] x;

    /**
     * Constructs a parameter set for Argon2 from the given values.
     *
     * @param builder the builder object containing the given values
     * @param passwd the password bytes
     */
    private Argon2ParameterSpec(Builder builder, byte[] salt, byte[] passwd) {
        // values are already validated by Builder
        this.passwd = passwd.clone();
        this.salt = salt.clone();
        this.ver = builder.ver;
        this.p = builder.p;
        this.tagLen = builder.tagLen;
        this.memory = builder.memory;
        this.t = builder.t;
        this.k = builder.k.clone();
        this.x = builder.x.clone();
    }

    /**
     * {@return a copy of the password bytes}
     * @throws IllegalStateException if {@code destroy()} has been called
     */
    public byte[] password() {
        if (passwd == null) {
            throw new IllegalStateException("password has been cleared");
        }
        return passwd.clone();
    }

    /**
     * {@return a copy of the salt}
     */
    public byte[] salt() {
        return salt.clone();
    }

    /**
     * {@return the memory cost, in KiB}
     */
    public int memoryKiB() {
        return memory;
    }

    /**
     * {@return the number of iterations}
     */
    public int iterations() {
        return t;
    }

    /**
     * {@return the degree of parallelism}
     */
    public int parallelism() {
        return p;
    }

    /**
     * {@return the output tag length, in bytes}
     */
    public int tagLen() {
        return tagLen;
    }

    /**
     * {@return the Argon2 version}
     */
    public Version version() {
        return ver;
    }

    /**
     * {@return a copy of the optional secret value, or an empty array
     * if not set}
     * @throws IllegalStateException if {@code destroy()} has been called
     */
    public byte[] secret() {
        if (k == null) {
            throw new IllegalStateException("secret has been cleared");
        }
        return (k.length == 0 ? B0 : k.clone());
    }

    /**
     * {@return a copy of the optional associated data, or an empty array
     * if not set}
     */
    public byte[] associatedData() {
        return (x.length == 0 ? B0 : x.clone());
    }

    /**
     * Returns a string representation of this parameter set.
     *
     * <p>The password and secret values are not included.
     *
     * @return a string representation of this parameter set
     */
    public String toString() {
        // skip password and secret due to their sensitivity
        return String.format("%s, memoryKiB=%d, iterations=%d, parallelism=%d, tagLen=%d, associatedData=%s, salt=%s",
                ver.name(), memory, t, p, tagLen, Debug.toString(x),
                Debug.toString(salt));
    }

    /**
     * Clears the password and the secret value held by this object.
     */
    @Override
    public void destroy() {
        if (!isDestroyed()) {
            Arrays.fill(passwd, (byte)0);
            Arrays.fill(k, (byte)0);
            passwd = null;
            k = null;
        }
    }

    /**
     * {@return {@code true} if this object has been destroyed; {@code false}
     * otherwise}
     */
    @Override
    public boolean isDestroyed() {
        return (passwd == null && k == null);
    }
}
