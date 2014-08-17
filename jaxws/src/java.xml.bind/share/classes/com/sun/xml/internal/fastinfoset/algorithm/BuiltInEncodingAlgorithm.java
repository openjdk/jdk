/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */

package com.sun.xml.internal.fastinfoset.algorithm;

import java.nio.CharBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithm;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmException;

public abstract class BuiltInEncodingAlgorithm implements EncodingAlgorithm {
    protected final static Pattern SPACE_PATTERN = Pattern.compile("\\s");

    public abstract int getPrimtiveLengthFromOctetLength(int octetLength) throws EncodingAlgorithmException;

    public abstract int getOctetLengthFromPrimitiveLength(int primitiveLength);

    public abstract void encodeToBytes(Object array, int astart, int alength, byte[] b, int start);

    public interface WordListener {
        public void word(int start, int end);
    }

    public void matchWhiteSpaceDelimnatedWords(CharBuffer cb, WordListener wl) {
        Matcher m = SPACE_PATTERN.matcher(cb);
        int i = 0;
        int s = 0;
        while(m.find()) {
            s = m.start();
            if (s != i) {
                wl.word(i, s);
            }
            i = m.end();
        }
        if (i != cb.length())
            wl.word(i, cb.length());
    }

    public StringBuilder removeWhitespace(char[] ch, int start, int length) {
        StringBuilder buf = new StringBuilder();
        int firstNonWS = 0;
        int idx = 0;
        for (; idx < length; ++idx) {
            if (Character.isWhitespace(ch[idx + start])) {
                if (firstNonWS < idx) {
                    buf.append(ch, firstNonWS + start, idx - firstNonWS);
                }
                firstNonWS = idx + 1;
            }
        }
        if (firstNonWS < idx) {
            buf.append(ch, firstNonWS + start, idx - firstNonWS);
        }
        return buf;
    }

}
