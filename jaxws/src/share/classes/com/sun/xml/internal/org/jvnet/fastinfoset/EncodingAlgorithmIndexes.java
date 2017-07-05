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


package com.sun.xml.internal.org.jvnet.fastinfoset;

/**
 * The indexes of built-in encoding algorithms.
 *
 * <p>The indexes of the built-in encoding algorithms are specified
 * in ITU-T Rec. X.891 | ISO/IEC 24824-1 (Fast Infoset), clause
 * 10. The indexes start from 0 instead of 1 as specified.<p>
 *
 * @see com.sun.xml.internal.org.jvnet.fastinfoset.sax.EncodingAlgorithmContentHandler
 * @see com.sun.xml.internal.org.jvnet.fastinfoset.sax.EncodingAlgorithmAttributes
 */
public final class EncodingAlgorithmIndexes {
    public static final int HEXADECIMAL = 0;
    public static final int BASE64      = 1;
    public static final int SHORT       = 2;
    public static final int INT         = 3;
    public static final int LONG        = 4;
    public static final int BOOLEAN     = 5;
    public static final int FLOAT       = 6;
    public static final int DOUBLE      = 7;
    public static final int UUID        = 8;
    public static final int CDATA       = 9;
}
