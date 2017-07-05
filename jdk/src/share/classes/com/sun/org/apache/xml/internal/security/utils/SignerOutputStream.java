/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2008 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.sun.org.apache.xml.internal.security.utils;

import java.io.ByteArrayOutputStream;

import com.sun.org.apache.xml.internal.security.algorithms.SignatureAlgorithm;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureException;

/**
 * @author raul
 *
 */
public class SignerOutputStream extends ByteArrayOutputStream {
    final SignatureAlgorithm sa;
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger
        (SignerOutputStream.class.getName());

    /**
     * @param sa
     */
    public SignerOutputStream(SignatureAlgorithm sa) {
        this.sa=sa;
    }

    /** @inheritDoc */
    public void write(byte[] arg0)  {
        super.write(arg0, 0, arg0.length);
        try {
            sa.update(arg0);
        } catch (XMLSignatureException e) {
            throw new RuntimeException(""+e);
        }
    }

    /** @inheritDoc */
    public void write(int arg0) {
        super.write(arg0);
        try {
            sa.update((byte)arg0);
        } catch (XMLSignatureException e) {
            throw new RuntimeException(""+e);
        }
    }

    /** @inheritDoc */
    public void write(byte[] arg0, int arg1, int arg2) {
        super.write(arg0, arg1, arg2);
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Canonicalized SignedInfo:");
            StringBuffer sb = new StringBuffer(arg2);
            for (int i=arg1; i<(arg1+arg2); i++) {
                sb.append((char) arg0[i]);
            }
            log.log(java.util.logging.Level.FINE, sb.toString());
        }
        try {
            sa.update(arg0,arg1,arg2);
        } catch (XMLSignatureException e) {
            throw new RuntimeException(""+e);
        }
    }
}
