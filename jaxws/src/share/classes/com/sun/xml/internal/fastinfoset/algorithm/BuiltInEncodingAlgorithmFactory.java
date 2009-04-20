/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */


package com.sun.xml.internal.fastinfoset.algorithm;

import com.sun.xml.internal.fastinfoset.EncodingConstants;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmIndexes;

public final class BuiltInEncodingAlgorithmFactory {

    public final static BuiltInEncodingAlgorithm[] table =
            new BuiltInEncodingAlgorithm[EncodingConstants.ENCODING_ALGORITHM_BUILTIN_END + 1];

    public final static HexadecimalEncodingAlgorithm hexadecimalEncodingAlgorithm = new HexadecimalEncodingAlgorithm();

    public final static BASE64EncodingAlgorithm base64EncodingAlgorithm = new BASE64EncodingAlgorithm();

    public final static BooleanEncodingAlgorithm booleanEncodingAlgorithm = new BooleanEncodingAlgorithm();

    public final static ShortEncodingAlgorithm shortEncodingAlgorithm = new ShortEncodingAlgorithm();

    public final static IntEncodingAlgorithm intEncodingAlgorithm = new IntEncodingAlgorithm();

    public final static LongEncodingAlgorithm longEncodingAlgorithm = new LongEncodingAlgorithm();

    public final static FloatEncodingAlgorithm floatEncodingAlgorithm = new FloatEncodingAlgorithm();

    public final static DoubleEncodingAlgorithm doubleEncodingAlgorithm = new DoubleEncodingAlgorithm();

    public final static UUIDEncodingAlgorithm uuidEncodingAlgorithm = new UUIDEncodingAlgorithm();

    static {
        table[EncodingAlgorithmIndexes.HEXADECIMAL] = hexadecimalEncodingAlgorithm;
        table[EncodingAlgorithmIndexes.BASE64] = base64EncodingAlgorithm;
        table[EncodingAlgorithmIndexes.SHORT] = shortEncodingAlgorithm;
        table[EncodingAlgorithmIndexes.INT] = intEncodingAlgorithm;
        table[EncodingAlgorithmIndexes.LONG] = longEncodingAlgorithm;
        table[EncodingAlgorithmIndexes.BOOLEAN] = booleanEncodingAlgorithm;
        table[EncodingAlgorithmIndexes.FLOAT] = floatEncodingAlgorithm;
        table[EncodingAlgorithmIndexes.DOUBLE] = doubleEncodingAlgorithm;
        table[EncodingAlgorithmIndexes.UUID] = uuidEncodingAlgorithm;
    }
}
