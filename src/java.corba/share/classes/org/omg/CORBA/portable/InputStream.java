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
 * InputStream is the Java API for reading IDL types
 * from CDR marshal streams. These methods are used by the ORB to
 * unmarshal IDL types as well as to extract IDL types out of Anys.
 * The <code>_array</code> versions of the methods can be directly
 * used to read sequences and arrays of IDL types.
 *
 * @since   JDK1.2
 */

public abstract class InputStream extends java.io.InputStream
{
    /**
     * Reads a boolean value from this input stream.
     *
     * @return the <code>boolean</code> value read from this input stream
     */
    public abstract boolean     read_boolean();
    /**
     * Reads a char value from this input stream.
     *
     * @return the <code>char</code> value read from this input stream
     */
    public abstract char        read_char();
    /**
     * Reads a wide char value from this input stream.
     *
     * @return the <code>char</code> value read from this input stream
     */
    public abstract char        read_wchar();
    /**
     * Reads an octet (that is, a byte) value from this input stream.
     *
     * @return the <code>byte</code> value read from this input stream
     */
    public abstract byte        read_octet();
    /**
     * Reads a short value from this input stream.
     *
     * @return the <code>short</code> value read from this input stream
     */
    public abstract short       read_short();
    /**
     * Reads a unsigned short value from this input stream.
     *
     * @return the <code>short</code> value read from this input stream
     */
    public abstract short       read_ushort();
    /**
     * Reads a CORBA long (that is, Java int) value from this input stream.
     *
     * @return the <code>int</code> value read from this input stream
     */
    public abstract int         read_long();
    /**
     * Reads an unsigned CORBA long (that is, Java int) value from this input
stream.
     *
     * @return the <code>int</code> value read from this input stream
     */
    public abstract int         read_ulong();
    /**
     * Reads a CORBA longlong (that is, Java long) value from this input stream.
     *
     * @return the <code>long</code> value read from this input stream
     */
    public abstract long        read_longlong();
    /**
     * Reads a CORBA unsigned longlong (that is, Java long) value from this input
stream.
     *
     * @return the <code>long</code> value read from this input stream
     */
    public abstract long        read_ulonglong();
    /**
     * Reads a float value from this input stream.
     *
     * @return the <code>float</code> value read from this input stream
     */
    public abstract float       read_float();
    /**
     * Reads a double value from this input stream.
     *
     * @return the <code>double</code> value read from this input stream
     */
    public abstract double      read_double();
    /**
     * Reads a string value from this input stream.
     *
     * @return the <code>String</code> value read from this input stream
     */
    public abstract String      read_string();
    /**
     * Reads a wide string value from this input stream.
     *
     * @return the <code>String</code> value read from this input stream
     */
    public abstract String      read_wstring();

    /**
     * Reads an array of booleans from this input stream.
     * @param value returned array of booleans.
     * @param offset offset on the stream.
     * @param length length of buffer to read.
     */
    public abstract void        read_boolean_array(boolean[] value, int offset, int
length);
    /**
     * Reads an array of chars from this input stream.
     * @param value returned array of chars.
     * @param offset offset on the stream.
     * @param length length of buffer to read.
     */
    public abstract void        read_char_array(char[] value, int offset, int
length);
    /**
     * Reads an array of wide chars from this input stream.
     * @param value returned array of wide chars.
     * @param offset offset on the stream.
     * @param length length of buffer to read.
     */
    public abstract void        read_wchar_array(char[] value, int offset, int
length);
    /**
     * Reads an array of octets (that is, bytes) from this input stream.
     * @param value returned array of octets (that is, bytes).
     * @param offset offset on the stream.
     * @param length length of buffer to read.
     */
    public abstract void        read_octet_array(byte[] value, int offset, int
length);
    /**
     * Reads an array of shorts from this input stream.
     * @param value returned array of shorts.
     * @param offset offset on the stream.
     * @param length length of buffer to read.
     */
    public abstract void        read_short_array(short[] value, int offset, int
length);
    /**
     * Reads an array of unsigned shorts from this input stream.
     * @param value returned array of shorts.
     * @param offset offset on the stream.
     * @param length length of buffer to read.
     */
    public abstract void        read_ushort_array(short[] value, int offset, int
length);
    /**
     * Reads an array of CORBA longs (that is, Java ints) from this input stream.
     * @param value returned array of CORBA longs (that is, Java ints).
     * @param offset offset on the stream.
     * @param length length of buffer to read.
     */
    public abstract void        read_long_array(int[] value, int offset, int
length);
    /**
     * Reads an array of unsigned CORBA longs (that is, Java ints) from this input
stream.
     * @param value returned array of CORBA longs (that is, Java ints).
     * @param offset offset on the stream.
     * @param length length of buffer to read.
     */
    public abstract void        read_ulong_array(int[] value, int offset, int
length);
    /**
     * Reads an array of CORBA longlongs (that is, Java longs) from this input
stream.
     * @param value returned array of CORBA longs (that is, Java longs).
     * @param offset offset on the stream.
     * @param length length of buffer to read.
     */
    public abstract void        read_longlong_array(long[] value, int offset, int
length);
    /**
     * Reads an array of unsigned CORBA longlongs (that is, Java longs) from this
input stream.
     * @param value returned array of CORBA longs (that is, Java longs).
     * @param offset offset on the stream.
     * @param length length of buffer to read.
     */
    public abstract void        read_ulonglong_array(long[] value, int offset, int
length);
    /**
     * Reads an array of floats from this input stream.
     * @param value returned array of floats.
     * @param offset offset on the stream.
     * @param length length of buffer to read.
     */
    public abstract void        read_float_array(float[] value, int offset, int
length);
    /**
     * Reads an array of doubles from this input stream.
     * @param value returned array of doubles.
     * @param offset offset on the stream.
     * @param length length of buffer to read.
     */
    public abstract void        read_double_array(double[] value, int offset, int
length);

    /**
     * Reads a CORBA object from this input stream.
     *
     * @return the <code>Object</code> instance read from this input stream.
     */
    public abstract org.omg.CORBA.Object read_Object();
    /**
     * Reads a <code>TypeCode</code> from this input stream.
     *
     * @return the <code>TypeCode</code> instance read from this input stream.
     */
    public abstract TypeCode    read_TypeCode();
    /**
     * Reads an Any from this input stream.
     *
     * @return the <code>Any</code> instance read from this input stream.
     */
    public abstract Any         read_any();

    /**
     * Returns principal for invocation.
     * @return Principal for invocation.
     * @deprecated Deprecated by CORBA 2.2.
     */
    @Deprecated
    public  Principal   read_Principal() {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }


    /**
     * @see <a href="package-summary.html#unimpl"><code>portable</code>
     * package comments for unimplemented features</a>
     */
    public int read() throws java.io.IOException {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Reads a BigDecimal number.
     * @return a java.math.BigDecimal number
     */
    public java.math.BigDecimal read_fixed() {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Reads a CORBA context from the stream.
     * @return a CORBA context
     * @see <a href="package-summary.html#unimpl"><code>portable</code>
     * package comments for unimplemented features</a>
     */
    public org.omg.CORBA.Context read_Context() {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }
    /*
     * The following methods were added by orbos/98-04-03: Java to IDL
     * Mapping. These are used by RMI over IIOP.
     */

    /**
     * Unmarshals an object and returns a CORBA Object,
     * which is an instance of the class passed as its argument.
     * This class is the stub class of the expected type.
     *
     * @param clz  The Class object for the stub class which
     * corresponds to the type that is statistically expected, or
     * the Class object for the RMI/IDL interface type that
     * is statistically expected.
     * @return an Object instance of clz read from this stream
     *
     * @see <a href="package-summary.html#unimpl"><code>portable</code>
     * package comments for unimplemented features</a>
     */
    public  org.omg.CORBA.Object read_Object(java.lang.Class
                                             clz) {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }

    /**
     * Returns the ORB that created this InputStream.
     *
     * @return the <code>ORB</code> object that created this stream
     *
     * @see <a href="package-summary.html#unimpl"><code>portable</code>
     * package comments for unimplemented features</a>
     */
    public org.omg.CORBA.ORB orb() {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }
}
