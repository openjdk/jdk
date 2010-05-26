/*
 * Copyright (c) 2000, 2004, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.corba.se.impl.encoding;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.omg.CORBA.TypeCode;
import org.omg.CORBA.Principal;
import org.omg.CORBA.Any;

import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.org.omg.SendingContext.CodeBase;

/**
 * Describes CDRInputStream delegates and provides some
 * implementation.  Non-default constructors are avoided in
 * the delegation to separate instantiation from initialization,
 * so we use init methods.
 */
abstract class CDRInputStreamBase extends java.io.InputStream
{
    protected CDRInputStream parent;

    public void setParent(CDRInputStream parent) {
        this.parent = parent;
    }

    public abstract void init(org.omg.CORBA.ORB orb,
                              ByteBuffer byteBuffer,
                              int size,
                              boolean littleEndian,
                              BufferManagerRead bufferManager);

    // org.omg.CORBA.portable.InputStream
    public abstract boolean read_boolean();
    public abstract char read_char();
    public abstract char read_wchar();
    public abstract byte read_octet();
    public abstract short read_short();
    public abstract short read_ushort();
    public abstract int read_long();
    public abstract int read_ulong();
    public abstract long read_longlong();
    public abstract long read_ulonglong();
    public abstract float read_float();
    public abstract double read_double();
    public abstract String read_string();
    public abstract String read_wstring();
    public abstract void read_boolean_array(boolean[] value, int offset, int length);
    public abstract void read_char_array(char[] value, int offset, int length);
    public abstract void read_wchar_array(char[] value, int offset, int length);
    public abstract void read_octet_array(byte[] value, int offset, int length);
    public abstract void read_short_array(short[] value, int offset, int length);
    public abstract void read_ushort_array(short[] value, int offset, int length);
    public abstract void read_long_array(int[] value, int offset, int length);
    public abstract void read_ulong_array(int[] value, int offset, int length);
    public abstract void read_longlong_array(long[] value, int offset, int length);
    public abstract void read_ulonglong_array(long[] value, int offset, int length);
    public abstract void read_float_array(float[] value, int offset, int length);
    public abstract void read_double_array(double[] value, int offset, int length);
    public abstract org.omg.CORBA.Object read_Object();
    public abstract TypeCode read_TypeCode();
    public abstract Any read_any();
    public abstract Principal read_Principal();
    public int read() throws java.io.IOException {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }
    public abstract java.math.BigDecimal read_fixed();
    public org.omg.CORBA.Context read_Context() {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }
    public abstract org.omg.CORBA.Object read_Object(java.lang.Class clz);
    public abstract org.omg.CORBA.ORB orb();

    // org.omg.CORBA_2_3.portable.InputStream
    public abstract java.io.Serializable read_value();
    public abstract java.io.Serializable read_value(java.lang.Class clz);
    public abstract java.io.Serializable read_value(org.omg.CORBA.portable.BoxedValueHelper factory);
    public abstract java.io.Serializable read_value(java.lang.String rep_id);
    public abstract java.io.Serializable read_value(java.io.Serializable value);
    public abstract java.lang.Object read_abstract_interface();
    public abstract java.lang.Object read_abstract_interface(java.lang.Class clz);

    // com.sun.corba.se.impl.encoding.MarshalInputStream
    public abstract void consumeEndian();
    public abstract int getPosition();

    // org.omg.CORBA.DataInputStream
    public abstract java.lang.Object read_Abstract ();
    public abstract java.io.Serializable read_Value ();
    public abstract void read_any_array (org.omg.CORBA.AnySeqHolder seq, int offset, int length);
    public abstract void read_boolean_array (org.omg.CORBA.BooleanSeqHolder seq, int offset, int length);
    public abstract void read_char_array (org.omg.CORBA.CharSeqHolder seq, int offset, int length);
    public abstract void read_wchar_array (org.omg.CORBA.WCharSeqHolder seq, int offset, int length);
    public abstract void read_octet_array (org.omg.CORBA.OctetSeqHolder seq, int offset, int length);
    public abstract void read_short_array (org.omg.CORBA.ShortSeqHolder seq, int offset, int length);
    public abstract void read_ushort_array (org.omg.CORBA.UShortSeqHolder seq, int offset, int length);
    public abstract void read_long_array (org.omg.CORBA.LongSeqHolder seq, int offset, int length);
    public abstract void read_ulong_array (org.omg.CORBA.ULongSeqHolder seq, int offset, int length);
    public abstract void read_ulonglong_array (org.omg.CORBA.ULongLongSeqHolder seq, int offset, int length);
    public abstract void read_longlong_array (org.omg.CORBA.LongLongSeqHolder seq, int offset, int length);
    public abstract void read_float_array (org.omg.CORBA.FloatSeqHolder seq, int offset, int length);
    public abstract void read_double_array (org.omg.CORBA.DoubleSeqHolder seq, int offset, int length);

    // org.omg.CORBA.portable.ValueBase
    public abstract String[] _truncatable_ids();

    // java.io.InputStream
    // REVISIT - should we make these throw UnsupportedOperationExceptions?
    // Right now, they'll go up to the java.io versions!

//     public abstract int read(byte b[]) throws IOException;
//     public abstract int read(byte b[], int off, int len) throws IOException
//     public abstract long skip(long n) throws IOException;
//     public abstract int available() throws IOException;
//     public abstract void close() throws IOException;
    public abstract void mark(int readlimit);
    public abstract void reset();

    // This should return false so that outside users (people using the JDK)
    // don't have any guarantees that mark/reset will work in their
    // custom marshaling code.  This is necessary since they could do things
    // like expect obj1a == obj1b in the following code:
    //
    // is.mark(10000);
    // Object obj1a = is.readObject();
    // is.reset();
    // Object obj1b = is.readObject();
    //
    public boolean markSupported() { return false; }

    // Needed by AnyImpl and ServiceContexts
    public abstract CDRInputStreamBase dup();

    // Needed by TCUtility
    public abstract java.math.BigDecimal read_fixed(short digits, short scale);

    // Needed by TypeCodeImpl
    public abstract boolean isLittleEndian();

    // Needed by request and reply messages for GIOP versions >= 1.2 only.
    abstract void setHeaderPadding(boolean headerPadding);

    // Needed by IIOPInputStream and other subclasses

    public abstract ByteBuffer getByteBuffer();
    public abstract void setByteBuffer(ByteBuffer byteBuffer);

    public abstract void setByteBufferWithInfo(ByteBufferWithInfo bbwi);

    public abstract int getBufferLength();
    public abstract void setBufferLength(int value);

    public abstract int getIndex();
    public abstract void setIndex(int value);

    public abstract void orb(org.omg.CORBA.ORB orb);

    public abstract BufferManagerRead getBufferManager();
    public abstract GIOPVersion getGIOPVersion();

    abstract CodeBase getCodeBase();

    abstract void printBuffer();

    abstract void alignOnBoundary(int octetBoundary);

    abstract void performORBVersionSpecificInit();

    public abstract void resetCodeSetConverters();

    // ValueInputStream -------------------------
    public abstract void start_value();
    public abstract void end_value();
}
