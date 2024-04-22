/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.crypto.provider;

import java.security.*;
import java.security.spec.*;
import java.util.Arrays;

// The alternate DHKEM implementation used by the Compliance.java test.
public class EvenKEMImpl extends DHKEM {

    public static boolean isEven(Key k) {
        return Arrays.hashCode(k.getEncoded()) % 2 == 0;
    }

    @Override
    public EncapsulatorSpi engineNewEncapsulator(
            PublicKey pk, AlgorithmParameterSpec spec, SecureRandom secureRandom)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        if (!isEven(pk)) throw new InvalidKeyException("Only accept even keys");
        return super.engineNewEncapsulator(pk, spec, secureRandom);
    }

    @Override
    public DecapsulatorSpi engineNewDecapsulator(
            PrivateKey sk, AlgorithmParameterSpec spec)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        if (!isEven(sk)) throw new InvalidKeyException("Only accept even keys");
        return super.engineNewDecapsulator(sk, spec);
    }
}
