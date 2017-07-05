/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.security.ucrypto;

import java.io.IOException;
import java.util.Arrays;
import java.security.AlgorithmParametersSpi;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import javax.crypto.spec.GCMParameterSpec;
import sun.security.util.*;

/**
 * This class implements the parameter set used with GCM mode
 * which is defined in RFC5084 as follows:
 *
 * <pre>
 * GCMParameters ::= SEQUENCE {
 *   aes-nonce        OCTET STRING, -- recommended size is 12 octets
 *   aes-ICVlen       AES-GCM-ICVlen DEFAULT 12 }
 *
 * where
 * AES-GCM-ICVlen ::= INTEGER (12 | 13 | 14 | 15 | 16)
 * NOTE: however, NIST 800-38D also lists 4 (32bit) and 8 (64bit)
 * as possible AES-GCM-ICVlen values, so we allow all 6 values.
 * </pre>
 *
 * @since 1.9
 */
public final class GCMParameters extends AlgorithmParametersSpi {

    private byte[] iv; // i.e. aes-nonce
    private int tLen; // i.e. aes-ICVlen, in bytes

    public GCMParameters() {}

    private void setValues(byte[] iv, int tLen) throws IOException {
        if (iv == null) {
            throw new IOException("IV cannot be null");
        }
        if (tLen != 4 && tLen != 8 && (tLen < 12 || tLen > 16)) {
            throw new IOException("Unsupported tag length: " + tLen);
        }
        this.iv = iv;
        this.tLen = tLen;
    }

    protected byte[] engineGetEncoded() throws IOException {
        DerOutputStream out = new DerOutputStream();
        DerOutputStream bytes = new DerOutputStream();

        bytes.putOctetString(iv);
        bytes.putInteger(tLen);
        out.write(DerValue.tag_Sequence, bytes);
        return out.toByteArray();
    }

    protected byte[] engineGetEncoded(String format) throws IOException {
        // ignore format for now
        return engineGetEncoded();
    }

    protected <T extends AlgorithmParameterSpec>
            T engineGetParameterSpec(Class<T> paramSpec)
        throws InvalidParameterSpecException {
        if (GCMParameterSpec.class.isAssignableFrom(paramSpec)) {
            return paramSpec.cast(new GCMParameterSpec(tLen*8, iv.clone()));
        } else {
            throw new InvalidParameterSpecException
                ("Inappropriate parameter specification");
        }
    }

    protected void engineInit(AlgorithmParameterSpec paramSpec)
        throws InvalidParameterSpecException {
        if (!(paramSpec instanceof GCMParameterSpec)) {
            throw new InvalidParameterSpecException
                ("Inappropriate parameter specification");
        }
        GCMParameterSpec gcmSpec = (GCMParameterSpec) paramSpec;
        try {
            setValues(gcmSpec.getIV(), gcmSpec.getTLen()/8);
        } catch (IOException ioe) {
            throw new InvalidParameterSpecException(ioe.getMessage());
        }
    }

    protected void engineInit(byte[] encoded) throws IOException {
        DerValue val = new DerValue(encoded);
        if (val.tag == DerValue.tag_Sequence) {
            val.data.reset();
            setValues(val.data.getOctetString(), val.data.getInteger());
        } else {
            throw new IOException("GCM parameter parsing error: SEQ tag expected");
        }
    }

    protected void engineInit(byte[] encoded, String format)
        throws IOException {
        // ignore format for now
        engineInit(encoded);
    }

    protected String engineToString() {
        return ("IV=" + Arrays.toString(iv) + ", tLen=" + tLen * 8);
    }
}
