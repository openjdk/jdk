/*
 * Copyright (c) 1997, 2004, Oracle and/or its affiliates. All rights reserved.
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
package org.omg.CORBA.portable;

import org.omg.CORBA.TypeCode;
import org.omg.CORBA.Principal;
import org.omg.CORBA.Any;

/**
 * OuputStream is the Java API for writing IDL types
 * to CDR marshal streams. These methods are used by the ORB to
 * marshal IDL types as well as to insert IDL types into Anys.
 * The <code>_array</code> versions of the methods can be directly
 * used to write sequences and arrays of IDL types.
 *
 * @since   JDK1.2
 */


public abstract class OutputStream extends java.io.OutputStream
{
    /**
     * Returns an input stream with the same buffer.
     *@return an input stream with the same buffer.
     */
    public abstract InputStream create_input_stream();

    /**
     * Writes a boolean value to this stream.
     * @param value the value to be written.
     */
    public abstract void write_boolean(boolean value);
    /**
     * Writes a char value to this stream.
     * @param value the value to be written.
     */
    public abstract void write_char(char value);
    /**
     * Writes a wide char value to this stream.
     * @param value the value to be written.
     */
    public abstract void write_wchar(char value);
    /**
     * Writes a CORBA octet (i.e. byte) value to this stream.
     * @param value the value to be written.
     */
    public abstract void write_octet(byte value);
    /**
     * Writes a short value to this stream.
     * @param value the value to be written.
     */
    public abstract void write_short(short value);
    /**
     * Writes an unsigned short value to this stream.
     * @param value the value to be written.
     */
    public abstract void write_ushort(short value);
    /**
     * Writes a CORBA long (i.e. Java int) value to this stream.
     * @param value the value to be written.
     */
    public abstract void write_long(int value);
    /**
     * Writes an unsigned CORBA long (i.e. Java int) value to this stream.
     * @param value the value to be written.
     */
    public abstract void write_ulong(int value);
    /**
     * Writes a CORBA longlong (i.e. Java long) value to this stream.
     * @param value the value to be written.
     */
    public abstract void write_longlong(long value);
    /**
     * Writes an unsigned CORBA longlong (i.e. Java long) value to this stream.
     * @param value the value to be written.
     */
    public abstract void write_ulonglong(long value);
    /**
     * Writes a float value to this stream.
     * @param value the value to be written.
     */
    public abstract void write_float(float value);
    /**
     * Writes a double value to this stream.
     * @param value the value to be written.
     */
    public abstract void write_double(double value);
    /**
     * Writes a string value to this stream.
     * @param value the value to be written.
     */
    public abstract void write_string(String value);
    /**
     * Writes a wide string value to this stream.
     * @param value the value to be written.
     */
    public abstract void write_wstring(String value);

    /**
     * Writes an array of booleans on this output stream.
     * @param value the array to be written.
     * @param offset offset on the stream.
     * @param length length of buffer to write.
     */
    public abstract void write_boolean_array(boolean[] value, int offset,
                                             int length);
    /**
     * Writes an array of chars on this output stream.
     * @param value the array to be written.
     * @param offset offset on the stream.
     * @param length length of buffer to write.
     */
    public abstract void write_char_array(char[] value, int offset,
                                          int length);
    /**
     * Writes an array of wide chars on this output stream.
     * @param value the array to be written.
     * @param offset offset on the stream.
     * @param length length of buffer to write.
     */
    public abstract void write_wchar_array(char[] value, int offset,
                                           int length);
    /**
     * Writes an array of CORBA octets (bytes) on this output stream.
     * @param value the array to be written.
     * @param offset offset on the stream.
     * @param length length of buffer to write.
     */
    public abstract void write_octet_array(byte[] value, int offset,
                                           int length);
    /**
     * Writes an array of shorts on this output stream.
     * @param value the array to be written.
     * @param offset offset on the stream.
     * @param length length of buffer to write.
     */
    public abstract void write_short_array(short[] value, int offset,
                                           int length);
    /**
     * Writes an array of unsigned shorts on this output stream.
     * @param value the array to be written.
     * @param offset offset on the stream.
     * @param length length of buffer to write.
     */
    public abstract void write_ushort_array(short[] value, int offset,
                                            int length);
    /**
     * Writes an array of CORBA longs (i.e. Java ints) on this output stream.
     * @param value the array to be written.
     * @param offset offset on the stream.
     * @param length length of buffer to write.
     */
    public abstract void write_long_array(int[] value, int offset,
                                          int length);
    /**
     * Writes an array of unsigned CORBA longs (i.e. Java ints) on this output stream.
     * @param value the array to be written.
     * @param offset offset on the stream.
     * @param length length of buffer to write.
     */
    public abstract void write_ulong_array(int[] value, int offset,
                                           int length);
    /**
     * Writes an array of CORBA longlongs (i.e. Java longs) on this output stream.
     * @param value the array to be written.
     * @param offset offset on the stream.
     * @param length length of buffer to write.
     */
    public abstract void write_longlong_array(long[] value, int offset,
                                              int length);
    /**
     * Writes an array of unsigned CORBA longlongs (i.e. Java ints) on this output stream.
     * @param value the array to be written.
     * @param offset offset on the stream.
     * @param length length of buffer to write.
     */
    public abstract void write_ulonglong_array(long[] value, int offset,
                                               int length);
    /**
     * Writes an array of floats on this output stream.
     * @param value the array to be written.
     * @param offset offset on the stream.
     * @param length length of buffer to write.
     */
    public abstract void write_float_array(float[] value, int offset,
                                           int length);
    /**
     * Writes an array of doubles on this output stream.
     * @param value the array to be written.
     * @param offset offset on the stream.
     * @param length length of buffer to write.
     */
    public abstract void write_double_array(double[] value, int offset,
                                            int length);
    /**
     * Writes a CORBA Object on this output stream.
     * @param value the value to be written.
     */
    public abstract void write_Object(org.omg.CORBA.Object value);
    /**
     * Writes a TypeCode on this output stream.
     * @param value the value to be written.
     */
    public abstract void write_TypeCode(TypeCode value);
    /**
     * Writes an Any on this output stream.
     * @param value the value to be written.
     */
    public abstract void write_any(Any value);

    /**
     * Writes a Principle on this output stream.
     * @param value the value to be written.
     * @deprecated Deprecated by CORBA 2.2.
     */
    @Deprecated
    public void write_Principal(Principal value) {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Writes an integer (length of arrays) onto this stream.
     * @param b the value to be written.
     * @throws java.io.IOException if there is an input/output error
     * @see <a href="package-summary.html#unimpl"><code>portable</code>
     * package comments for unimplemented features</a>
     */
    public void write(int b) throws java.io.IOException {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Writes a BigDecimal number.
     * @param value a BidDecimal--value to be written.
     */
    public void write_fixed(java.math.BigDecimal value) {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Writes a CORBA context on this stream. The
     * Context is marshaled as a sequence of strings.
     * Only those Context values specified in the contexts
     * parameter are actually written.
     * @param ctx a CORBA context
     * @param contexts a <code>ContextList</code> object containing the list of contexts
     *        to be written
     * @see <a href="package-summary.html#unimpl"><code>portable</code>
     * package comments for unimplemented features</a>
     */
    public void write_Context(org.omg.CORBA.Context ctx,
                              org.omg.CORBA.ContextList contexts) {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Returns the ORB that created this OutputStream.
     * @return the ORB that created this OutputStream
     * @see <a href="package-summary.html#unimpl"><code>portable</code>
     * package comments for unimplemented features</a>
     */
    public org.omg.CORBA.ORB orb() {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }
}
