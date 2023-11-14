/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;

import java.io.IOException;
import java.security.AlgorithmParametersSpi;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.IntegerParameterSpec;
import java.security.spec.InvalidParameterSpecException;

/**
 * This class implements the parameter set used by the SHAKE256-LEN
 * message digest that has a various digest output length. This parameters
 * can be initialized with an {@link IntegerParameterSpec}. The integer
 * is the length of the digest output length in bits.
 */
public class IntegerParameters extends AlgorithmParametersSpi {
    private int n;
    @Override
    protected void engineInit(AlgorithmParameterSpec paramSpec)
            throws InvalidParameterSpecException {
        if (paramSpec instanceof IntegerParameterSpec ip) {
            n = ip.n();
        } else {
            throw new InvalidParameterSpecException("Unknown spec: " + paramSpec);
        }
    }

    @Override
    protected void engineInit(byte[] params) throws IOException {
        n = new DerValue(params).getInteger();
    }

    @Override
    protected void engineInit(byte[] params, String format) throws IOException {
        engineInit(params);
    }

    @Override
    protected <T extends AlgorithmParameterSpec> T engineGetParameterSpec(
            Class<T> paramSpec) throws InvalidParameterSpecException {
        if (paramSpec.isAssignableFrom(IntegerParameterSpec.class)) {
            return paramSpec.cast(
                    new IntegerParameterSpec(n));
        } else {
            throw new InvalidParameterSpecException
                    ("Inappropriate parameter Specification");
        }
    }

    @Override
    protected byte[] engineGetEncoded() throws IOException {
        return new DerOutputStream().putInteger(n).toByteArray();
    }

    @Override
    protected byte[] engineGetEncoded(String format) throws IOException {
        return engineGetEncoded();
    }

    @Override
    protected String engineToString() {
        return "len:" + n + " bits";
    }
}
