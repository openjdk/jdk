/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.org.jvnet.fastinfoset.sax;

import org.xml.sax.SAXException;

/**
 * SAX2 extention handler to receive notification of character data as
 * primtive types.
 *
 * <p>This is an optional extension handler for SAX2. XML readers are not
 * required to recognize this handler, and it is not part of core-only
 * SAX2 distributions.</p>
 *
 * <p>This interface may be used with with a Fast Infoset
 * SAX parser to receive notification of data encoded using the
 * following built-in encoding algorithms specified in ITU-T Rec. X.891 | ISO/IEC 24824-1
 * (Fast Infoset), clause 10: "boolean", "base64", "short", "int", "long",
 * "float", "double" and "uuid" encoding algorithms.<p>
 *
 * <p>To set the PrimitiveTypeContentHandler for an XML reader, use the
 * {@link org.xml.sax.XMLReader#setProperty setProperty} method
 * with the property name
 * <code>URI TO BE DEFINED</code>
 * and an object implementing this interface (or null) as the value.
 * If the reader does not report primitive data types, it will throw a
 * {@link org.xml.sax.SAXNotRecognizedException SAXNotRecognizedException}</p>
 *
 * <p>To set the PrimitiveTypeContentHandler for an Fast Infoset reader, use
 * {@link com.sun.xml.internal.org.jvnet.fastinfoset.sax.FastInfosetReader#setPrimitiveTypeContentHandler
 *  setPrimitiveTypeContentHandler} method.<p>

 * <p>The Parser will call methods of this interface to report each
 * chunk of character data that has been converted to an array of primitive
 * types, for example an array of integer or an array of float. Parsers may
 * return all contiguous primtive types in a single chunk, or they may split
 * it into several chunks</p>
 *
 * <p>The application must not attempt to read from the array
 * outside of the specified range.</p>
 *
 * @see com.sun.xml.internal.org.jvnet.fastinfoset.sax.EncodingAlgorithmContentHandler
 * @see com.sun.xml.internal.org.jvnet.fastinfoset.sax.FastInfosetReader
 * @see org.xml.sax.XMLReader
 */
public interface PrimitiveTypeContentHandler {
    /**
     * Receive notification of character data as an array of boolean.
     *
     * <p>The application must not attempt to read from the array
     * outside of the specified range.</p>
     *
     * <p>Such notifications will occur for a Fast Infoset SAX parser
     * when processing data encoded using the "boolean" encoding
     * algorithm, see subclause 10.7<p>.
     *
     * @param b the array of boolean
     * @param start the start position in the array
     * @param length the number of boolean to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly
     *            wrapping another exception
     */
    public void booleans(boolean [] b, int start, int length) throws SAXException;

    /**
     * Receive notification of character data as an array of byte.
     *
     * <p>The application must not attempt to read from the array
     * outside of the specified range.</p>
     *
     * <p>Such notifications will occur for a Fast Infoset SAX parser
     * when processing data encoded using the "base64" encoding
     * algorithm, see subclause 10.3, or the "hexadecimal" encoding
     * algorithm, see subclause 10.2.
     *
     * <p>Such a notification may occur for binary data that would
     * normally require base 64 encoding and reported as character data
     * using the {@link org.xml.sax.ContentHandler#characters characters}
     * method <p>.
     *
     * @param b the array of byte
     * @param start the start position in the array
     * @param length the number of byte to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly
     *            wrapping another exception
     */
    public void bytes(byte[] b, int start, int length) throws SAXException;

    /**
     * Receive notification of character data as an array of short.
     *
     * <p>The application must not attempt to read from the array
     * outside of the specified range.</p>
     *
     * <p>Such notifications will occur for a Fast Infoset SAX parser
     * when processing data encoded using the "short" encoding
     * algorithm, see subclause 10.4<p>.
     *
     * @param s the array of short
     * @param start the start position in the array
     * @param length the number of short to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly
     *            wrapping another exception
     */
    public void shorts(short[] s, int start, int length) throws SAXException;

    /**
     * Receive notification of character data as an array of int.
     *
     * <p>The application must not attempt to read from the array
     * outside of the specified range.</p>
     *
     * <p>Such notifications will occur for a Fast Infoset SAX parser
     * when processing data encoded using the "int" encoding
     * algorithm, see subclause 10.5<p>.
     *
     * @param i the array of int
     * @param start the start position in the array
     * @param length the number of int to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly
     *            wrapping another exception
     */
    public void ints(int [] i, int start, int length) throws SAXException;

    /**
     * Receive notification of character data as an array of long.
     *
     * <p>The application must not attempt to read from the array
     * outside of the specified range.</p>
     *
     * <p>Such notifications will occur for a Fast Infoset SAX parser
     * when processing data encoded using the "long" encoding
     * algorithm, see subclause 10.6<p>.
     *
     * @param l the array of long
     * @param start the start position in the array
     * @param length the number of long to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly
     *            wrapping another exception
     */
    public void longs(long [] l, int start, int length) throws SAXException;

    /**
     * Receive notification of character data as an array of float.
     *
     * <p>The application must not attempt to read from the array
     * outside of the specified range.</p>
     *
     * <p>Such notifications will occur for a Fast Infoset SAX parser
     * when processing data encoded using the "float" encoding
     * algorithm, see subclause 10.8<p>.
     *
     * @param f the array of float
     * @param start the start position in the array
     * @param length the number of float to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly
     *            wrapping another exception
     */
    public void floats(float [] f, int start, int length) throws SAXException;

    /**
     * Receive notification of character data as an array of double.
     *
     * <p>The application must not attempt to read from the array
     * outside of the specified range.</p>
     *
     * <p>Such notifications will occur for a Fast Infoset SAX parser
     * when processing data encoded using the "double" encoding
     * algorithm, see subclause 10.9<p>.
     *
     * @param d the array of double
     * @param start the start position in the array
     * @param length the number of double to read from the array
     * @throws org.xml.sax.SAXException any SAX exception, possibly
     *            wrapping another exception
     */
    public void doubles(double [] d, int start, int length) throws SAXException;

    /**
     * Receive notification of character data as an two array of UUID.
     *
     * <p>The application must not attempt to read from the array
     * outside of the specified range.</p>
     *
     * <p>Such notifications will occur for a Fast Infoset SAX parser
     * when processing data encoded using the "uuid" encoding
     * algorithm, see subclause 10.10<p>.
     *
     * @param msblsb the array of long containing pairs of most signficant
     * bits and least significant bits of the UUIDs
     * @param start the start position in the array
     * @param length the number of long to read from the array. This will
     * be twice the number of UUIDs, which are pairs of two long values
     * @throws org.xml.sax.SAXException any SAX exception, possibly
     *            wrapping another exception
     */
    public void uuids(long[] msblsb, int start, int length) throws SAXException;
}
