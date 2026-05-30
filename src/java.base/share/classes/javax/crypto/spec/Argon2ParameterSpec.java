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
 * <a href="http://tools.ietf.org/html/rfc9106">RFC 9106</a>.
 *
 * <p> The parameters consist of {@code password}, {@code salt},
 * {@code memoryKiB}, {@code iterations}, {@code parallelism}, {@code tagLen},
 * {@code version}, and the optional {@code secret} and {@code associatedData}
 * bytes.
 *
 * <p> This class can be used to initialize a {@code KDF} object that
 * implements the <i>Argon2</i> family of algorithms, i.e. <code>Argon2i</code>,
 * <code>Argon2d</code>, and <code>Argon2id</code>.
 *
 * @since 27
 */
public final class Argon2ParameterSpec implements AlgorithmParameterSpec,
        Destroyable {

    /**
     * Version of Argon2 implementations
     */
    public enum Version {
        /**
         * version 1.0
         */
        V10(0x10),
        /**
         * version 1.3, the official Argon2 version
         */
        V13(0x13);    // Official ARGON2_VERSION_NUMBER

        int value;

        Version(int value) {
            this.value = value;
        }

        /**
         * {@return 16 for V10, 19 for V13}
         */
        public int value() {
            return value;
        }

        /**
         * {@return V10 for 16, and V13 for 19}
         * @param value the integer value as a {@code String}
         */
        public static Version get(String value)
                throws IllegalArgumentException {
            switch (value) {
                case "16" ->{ return V10; }
                case "19" ->{ return V13; }
                default ->{
                    throw new IllegalArgumentException
                            ("Invalid version value " + value);
                }
            }
        }

    };

    /**
     * This {@code Builder} builds {@code Argon2ParameterSpec} objects.
     * <p>
     * The {@code Builder} is initialized via the {@code newBuilder} method of
     * {@code Argon2ParameterSpec}. As stated in the class description,
     * required parameters must be supplied by calling various methods. Finally,
     * an object is "built" by calling {@code build}.
     * Note that the {@code Builder} is not thread-safe.
     */
    public static final class Builder {

        private static int NONCE_LEN_MIN = 8;
        private static int P_MAX = 16777215; // 2^24 - 1
        private static int M_MIN = 8; // since p >= 1
        private static int MP_MAX = 30; // java integer max 2^31 - 1
        private static int TAGLEN_MIN = 4;

        private Version ver = Version.V13; // defaults to the official version
        private int p;
        private int tagLen;
        private int memory;
        private int t;
        private byte[] k = B0; // optional
        private byte[] x = B0; // optional

        private static <T> T checkNonNull(T o, String name)
                throws IllegalArgumentException {
            if (o == null) {
                throw new IllegalArgumentException("Argon2 " + name +
                        " can't be null");
            }
            return o;
        }

        private static byte[] checkBytes(byte[] b, int minLen, String name)
                throws IllegalArgumentException {
            checkNonNull(b, name);
            if (b.length < minLen) {
                throw new IllegalArgumentException("Argon2 " + name +
                        " must be at least " + minLen + " bytes");
            }
            return b;
        }

        private static int checkInteger(int i, int min, int max, String name)
                throws IllegalArgumentException {
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
         * Set the memory value to the builder.
         *
         * @param m the memory value in Kibibytes
         * @return this builder
         * @throws IllegalArgumentException
         *         if {@code m} is less than 8 or less than 8 * {@code p} if
         *         {@code parallelism(p)} is already called.
         */
        public Builder memoryKiB(int m) throws IllegalArgumentException {
            this.memory = checkInteger(m, (p > 0 ? p << 3 : M_MIN),
                    -1, "memory cost in KiB");
            return this;
        }

        /**
         * Set the memory value to the builder in power of two.
         *
         * @param mPower set memory value to 2^{@code mPower} Kibibytes
         * @return this builder
         * @throws IllegalArgumentException
         *         if {@code mPower} is less than 3 or larger than 30. Or if
         *         {@code parallelism(p)} is already called,
         *         {@code mPower} is less than 3 + log({@code p})
         */
        public Builder memoryPowerOfTwo(int mPower)
                throws IllegalArgumentException {
            checkInteger(mPower, ((p > 0 ? ceilingOfLog2(p) : 0) + 3),
                    MP_MAX, "memory cost in power of two");
            this.memory = 1 << mPower;
            return this;
        }

        /**
         * Set the number of iterations to the builder.
         *
         * @param t number of iterations
         * @return this builder
         * @throws IllegalArgumentException
         *         if {@code t} is not a positive integer
         */
        public Builder iterations(int t) throws IllegalArgumentException {
            this.t = checkInteger(t, 1, -1, "iterations");
            return this;
        }

        /**
         * Set the parallelism value to the builder.
         *
         * @param p the parallelism value
         * @return this builder
         * @throws IllegalArgumentException if {@code p} is not a positive
         *         integer, or larger than ({@code m} / 8) or
         *         (2^({@code mPower} - 3)) if {@code memoryKiB(m)} or
         *         {@code memoryPowerOfTwo(mPower)} is already called
         */
        public Builder parallelism(int p) throws IllegalArgumentException {
            this.p = checkInteger(p, 1, (memory > 0 ? (memory >>> 3) : P_MAX),
                    "parallellism");
            return this;
        }

        /**
         * Set tagLen value to the builder.
         *
         * @param tagLen length of the output tag in bytes
         * @return this builder
         * @throws IllegalArgumentException if {@code tagLen} is less than 4
         */
        public Builder tagLen(int tagLen) throws IllegalArgumentException {
            this.tagLen = checkInteger(tagLen, TAGLEN_MIN, -1, "tag length");
            return this;
        }

        /**
         * Set version value to the builder. If not supplied, defaults to
         * {@code V13}.
         *
         * @param ver the Version value
         * @return this builder
         * @throws IllegalArgumentException if the {@code ver} argument is
         *     {@code null}.
         */
        public Builder version(Version ver) throws IllegalArgumentException {
            checkNonNull(ver, "version");
            this.ver = ver;
            return this;
        }

        /**
         * Set the optional secret value to the builder.
         *
         * @param k secret value
         * @return this builder
         * @throws IllegalArgumentException if {@code k} is {@code null}
         */
        public Builder secret(byte[] k) throws IllegalArgumentException {
            checkNonNull(k, "secret");
            if (this.k != B0) {
                Arrays.fill(this.k, (byte)0);
            }
            this.k = (k.length > 0 ? k.clone() : B0);
            return this;
        }

        /**
         * Set the optional associated data value to the builder.
         *
         * @param x associated data
         * @return this builder
         * @throws IllegalArgumentException if {@code x} is {@code null}
         */
        public Builder associatedData(byte[] x)
                throws IllegalArgumentException {
            checkNonNull(x, "associated data");
            this.x = (x.length > 0 ? x.clone() : B0);
            return this;
        }

        /**
         * Use the specified {@code salt}, {@code password} and the supplied
         * parameters to create an Argon2ParameterSpec object.
         *
         * @param salt the salt value
         * @param password the password bytes
         * @return an {@code Argon2ParameterSpec} object
         * @throws IllegalArgumentException if {@code salt} is invalid, e.g.
         *         {@code null}, or less than 8-byte, the {@code password}
         *         is {@code null}, or missing the required parameters.
         */
        public Argon2ParameterSpec build(byte[] salt, byte[] password) {
            checkBytes(salt, NONCE_LEN_MIN, "salt");
            checkNonNull(password, "password");
            // validate the other parameters to make sure they are all set
            checkInteger(this.tagLen, TAGLEN_MIN, -1, "tag length");
            checkInteger(this.p, 1, -1, "parallelism");
            checkInteger(this.memory, p << 3, -1, "memory");
            checkInteger(this.t, 1, -1, "iterations");
            return new Argon2ParameterSpec(this, salt, password);
        }

        /**
         * Convert {@code passwdChar} to {@code byte[]} based on the
         * {@code cs}. Then use it with the specified {@code salt} to
         * create an {@code Argon2ParameterSpec} object.
         *
         * @param salt the salt value
         * @param passwdChar the password characters
         * @param cs the charset to encode the {@code passwdChar} into bytes
         * @return an {@code Argon2ParameterSpec} object
         * @throws IllegalArgumentException if the {@code salt} is invalid,
         *         e.g. {@code null}, or less than 8-byte, {@code passwdChar} is
         *         null, {@code cs} is null, or missing the required parameters.
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
     * {@return a new builder for {@code Argon2ParameterSpec}}
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    private static final byte[] B0 = new byte[0];

    /*
     * Argon2 inputs parameters
     */
    // password bytes, len >= 0; clearable thus non-final
    private byte[] passwd;
    // salt, min len = 8 per argon2-specs.pdf; should be unique for each
    // password; 16 bytes is RECOMMENDED for password hashing.
    private final byte[] salt;
    // memory in kibibytes
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
     * {@return the password bytes}
     */
    public byte[] password() {
        if (passwd == null) {
            throw new IllegalStateException("password has been cleared");
        }
        return passwd.clone();
    }

    /**
     * {@return the salt}
     */
    public byte[] salt() {
        return salt.clone();
    }

    /**
     * {@return the memory, i.e. in Kibibytes, must be at least 8*p}
     */
    public int memoryKiB() {
        return memory;
    }

    /**
     * {@return the number of iterations, must be a positive integer}
     */
    public int iterations() {
        return t;
    }

    /**
     * {@return the parallelism value, 1...2^24-1}
     */
    public int parallelism() {
        return p;
    }

    /**
     * {@return the tag length in bytes}
     */
    public int tagLen() {
        return tagLen;
    }

    /**
     * {@return the version of Argon2 implementation}
     */
    public Version version() {
        return ver;
    }

    /**
     * {@return the optional secret value, byte[0] if not used}
     */
    public byte[] secret() {
        if (k == null) {
            throw new IllegalStateException("secret has been cleared");
        }
        return (k.length == 0 ? B0 : k.clone());
    }

    /**
     * {@return the optional associated data, byte[0] if not used}
     */
    public byte[] associatedData() {
        return (x.length == 0 ? B0 : x.clone());
    }

    /**
     * {@return a String representation of the parameter set}
     */
    public String toString() {
        // skip password and secret due to their sensitivity
        return String.format("%s, memoryKiB=%d, iterations=%d, parallelism=%d, tagLen=%d, associatedData=%s, salt=%s",
                ver.name(), memory, t, p, tagLen, Debug.toString(x),
                Debug.toString(salt));
    }

    /**
     * Destroy this object by clearing out the {@code password} and
     * {@code secret} fields.
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
     * @return {@code true} if this {@code Object} has been destroyed,
     * {@code false} otherwise.
     */
    @Override
    public boolean isDestroyed() {
        return (passwd == null && k == null);
    }
}
