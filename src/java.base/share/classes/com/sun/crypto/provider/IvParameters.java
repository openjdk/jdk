/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import java.io.*;
import sun.security.util.*;
import sun.security.util.HexDumpEncoder;
import java.security.AlgorithmParametersSpi;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import javax.crypto.spec.IvParameterSpec;

/**
 * This class implements the parameter (IV) used with Block Ciphers.
 * Different algorithms may have different length requirement for IV
 * length.
 *
 * <pre>
 * IV ::= OCTET STRING  -- length depends on the block size of the
 * block ciphers
 * </pre>
 *
 * @author Valerie Peng
 *
 */
abstract class IvParameters extends AlgorithmParametersSpi {

    // 4-byte IVs, used by AES w/ KWP mode
    public static final class Four extends IvParameters {
        public Four() {
            super(4);
        }
    }

    // 8-byte IVs, used by DES, DESede, Blowfish, and AES w/ KW mode
    public static final class Eight extends IvParameters {
        public Eight() {
            super(8);
        }
    }

    // 16-byte IVs, used by AES with most of its modes, e.g. CBC
    public static final class Sixteen extends IvParameters {
        public Sixteen() {
            super(16);
        }
    }

    private final int validLen;
    private byte[] iv = null;

    private IvParameters(int validLen) {
        this.validLen = validLen;
    }

    protected void engineInit(AlgorithmParameterSpec paramSpec)
        throws InvalidParameterSpecException {
        if (!(paramSpec instanceof IvParameterSpec)) {
            throw new InvalidParameterSpecException
                ("Inappropriate parameter specification");
        }
        byte[] tmpIv = ((IvParameterSpec)paramSpec).getIV();
        if (tmpIv.length != validLen) {
            throw new InvalidParameterSpecException("IV not " +
                    validLen + " bytes long");
        }
        iv = tmpIv.clone();
    }

    protected void engineInit(byte[] encoded) throws IOException {
        DerInputStream der = new DerInputStream(encoded);

        byte[] tmpIv = der.getOctetString();
        if (der.available() != 0) {
            throw new IOException("IV parsing error: extra data");
        }
        if (tmpIv.length != validLen) {
            throw new IOException("IV not " + validLen +
                " bytes long");
        }
        iv = tmpIv;
    }

    protected void engineInit(byte[] encoded, String decodingMethod)
        throws IOException {
        if ((decodingMethod != null) &&
            (!decodingMethod.equalsIgnoreCase("ASN.1"))) {
            throw new IllegalArgumentException("Only support ASN.1 format");
        }
        engineInit(encoded);
    }

    protected <T extends AlgorithmParameterSpec>
        T engineGetParameterSpec(Class<T> paramSpec)
        throws InvalidParameterSpecException {
        if (IvParameterSpec.class.isAssignableFrom(paramSpec)) {
            return paramSpec.cast(new IvParameterSpec(this.iv));
        } else {
            throw new InvalidParameterSpecException
                ("Inappropriate parameter specification");
        }
    }

    protected byte[] engineGetEncoded() throws IOException {
        DerOutputStream out = new DerOutputStream();
        out.putOctetString(this.iv);
        return out.toByteArray();
    }

    protected byte[] engineGetEncoded(String encodingMethod)
        throws IOException {
        return engineGetEncoded();
    }

    protected String engineToString() {
        String LINE_SEP = System.lineSeparator();

        String ivString = LINE_SEP + "    iv:" + LINE_SEP + "[";
        HexDumpEncoder encoder = new HexDumpEncoder();
        ivString += encoder.encodeBuffer(this.iv);
        ivString += "]" + LINE_SEP;
        return ivString;
    }
}
