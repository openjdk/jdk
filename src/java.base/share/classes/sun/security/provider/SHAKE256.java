/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package sun.security.provider;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.IntegerParameterSpec;
import java.security.spec.InvalidParameterSpecException;

/*
 * The SHAKE256 extendable output function.
 */
public final class SHAKE256 extends SHA3 {

    public static class WithoutLen extends SHA3 {
        public WithoutLen() {
            super("SHAKE256-LEN", 64, (byte) 0x1F, 64);
        }
    }

    public static class WithLen extends SHA3 {
        public WithLen(AlgorithmParameterSpec p)
                throws InvalidAlgorithmParameterException {
            super("SHAKE256-LEN", n(p), (byte) 0x1F, 64);
        }

        private static int n(AlgorithmParameterSpec p)
                throws InvalidAlgorithmParameterException {
            if (p instanceof IntegerParameterSpec is) {
                int bitsLen = is.n();
                if (bitsLen <= 0 || (bitsLen & 0x07) != 0) {
                    throw new InvalidAlgorithmParameterException("Invalid length: " + bitsLen);
                }
                return bitsLen / 8;
            } else {
                throw new InvalidAlgorithmParameterException("Unknown spec: " + p);
            }
        }


        @Override
        protected AlgorithmParameters engineGetParameters() {
            try {
                AlgorithmParameters p = AlgorithmParameters.getInstance("SHAKE256-LEN");
                p.init(new IntegerParameterSpec(engineGetDigestLength() * 8));
                return p;
            } catch (NoSuchAlgorithmException | InvalidParameterSpecException e) {
                throw new ProviderException(e);
            }
        }
    }
    public SHAKE256(int d) {
        super("SHAKE256", d, (byte) 0x1F, 64);
    }

    public void update(byte in) {
        engineUpdate(in);
    }
    public void update(byte[] in, int off, int len) {
        engineUpdate(in, off, len);
    }

    public byte[] digest() {
        return engineDigest();
    }

    public void reset() {
        engineReset();
    }
}
