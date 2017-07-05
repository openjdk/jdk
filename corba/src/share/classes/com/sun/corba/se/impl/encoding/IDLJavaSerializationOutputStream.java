/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
import java.io.ObjectOutputStream;
import java.io.ByteArrayOutputStream;

import java.nio.ByteBuffer;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.IORFactories;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.presentation.rmi.StubAdapter;

import com.sun.corba.se.impl.util.Utility;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.corba.TypeCodeImpl;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;

import org.omg.CORBA.Any;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.Principal;
import org.omg.CORBA.CompletionStatus;

/**
 * Implementation class that uses Java serialization for output streams.
 * This assumes a GIOP version 1.2 message format.
 *
 * This class uses a ByteArrayOutputStream as the underlying buffer. The
 * first 16 bytes are direct writes into the underlying buffer. This allows
 * [GIOPHeader (12 bytes) + requestID (4 bytes)] to be written as bytes.
 * Subsequent write operations on this output stream object uses
 * ObjectOutputStream class to write into the buffer. This allows marshaling
 * complex types and graphs using the ObjectOutputStream implementation.
 *
 * Note, this class assumes a GIOP 1.2 style header. Note, we expect that the
 * first 16 bytes are written only using the write_octet, write_long or
 * write_ulong method calls.
 *
 * @author Ram Jeyaraman
 */
public class IDLJavaSerializationOutputStream extends CDROutputStreamBase {

    private ORB orb;
    private byte encodingVersion;
    private ObjectOutputStream os;
    private _ByteArrayOutputStream bos;
    private BufferManagerWrite bufferManager;

    // [GIOPHeader(12) + requestID(4)] bytes
    private final int directWriteLength = Message.GIOPMessageHeaderLength + 4;

    protected ORBUtilSystemException wrapper;

    class _ByteArrayOutputStream extends ByteArrayOutputStream {

        _ByteArrayOutputStream(int initialSize) {
            super(initialSize);
        }

        byte[] getByteArray() {
            return this.buf;
        }
    }

    class MarshalObjectOutputStream extends ObjectOutputStream {

        ORB orb;

        MarshalObjectOutputStream(java.io.OutputStream out, ORB orb)
                throws IOException {

            super(out);
            this.orb = orb;
            java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction() {
                    public Object run() {
                        // needs SerializablePermission("enableSubstitution")
                        enableReplaceObject(true);
                        return null;
                    }
                }
            );
        }

        /**
         * Checks for objects that are instances of java.rmi.Remote
         * that need to be serialized as proxy (Stub) objects.
         */
        protected final Object replaceObject(Object obj) throws IOException {
            try {
                if ((obj instanceof java.rmi.Remote) &&
                        !(StubAdapter.isStub(obj))) {
                    return Utility.autoConnect(obj, orb, true);
                }
            } catch (Exception e) {
                IOException ie = new IOException("replaceObject failed");
                ie.initCause(e);
                throw ie;
            }
            return obj;
        }
    }

    public IDLJavaSerializationOutputStream(byte encodingVersion) {
        super();
        this.encodingVersion = encodingVersion;
    }

    public void init(org.omg.CORBA.ORB orb, boolean littleEndian,
                     BufferManagerWrite bufferManager,
                     byte streamFormatVersion,
                     boolean usePooledByteBuffers) {
        this.orb = (ORB) orb;
        this.bufferManager = bufferManager;
        wrapper = ORBUtilSystemException.get((ORB) orb,
                                             CORBALogDomains.RPC_ENCODING);
        bos =
            new _ByteArrayOutputStream(ORBConstants.GIOP_DEFAULT_BUFFER_SIZE);
    }

    // Called from read_octet or read_long or read_ulong method.
    private void initObjectOutputStream() {
        //System.out.print(" os ");
        if (os != null) {
            throw wrapper.javaStreamInitFailed();
        }
        try {
            os = new MarshalObjectOutputStream(bos, orb);
        } catch (Exception e) {
            throw wrapper.javaStreamInitFailed(e);
        }
    }

    // org.omg.CORBA.portable.OutputStream

    // Primitive types.

    public final void write_boolean(boolean value) {
        try {
            os.writeBoolean(value);
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "write_boolean");
        }
    }

    public final void write_char(char value) {
        try {
            os.writeChar(value);
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "write_char");
        }
    }

    public final void write_wchar(char value) {
        this.write_char(value);
    }

    public final void write_octet(byte value) {

        // check if size < [ GIOPHeader(12) + requestID(4)] bytes
        if (bos.size() < directWriteLength) {
            bos.write(value); // direct write.
            if (bos.size() == directWriteLength) {
                initObjectOutputStream();
            }
            return;
        }

        try {
            os.writeByte(value);
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "write_octet");
        }
    }

    public final void write_short(short value) {
        try {
            os.writeShort(value);
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "write_short");
        }
    }

    public final void write_ushort(short value) {
        this.write_short(value);
    }

    public final void write_long(int value) {

        // check if size < [ GIOPHeader(12) + requestID(4)] bytes
        if (bos.size() < directWriteLength) {

            // Use big endian (network byte order). This is fixed.
            // Both the writer and reader use the same byte order.
            bos.write((byte)((value >>> 24) & 0xFF));
            bos.write((byte)((value >>> 16) & 0xFF));
            bos.write((byte)((value >>> 8) & 0xFF));
            bos.write((byte)((value >>> 0) & 0xFF));

            if (bos.size() == directWriteLength) {
                initObjectOutputStream();
            } else if (bos.size() > directWriteLength) {
                // Cannot happen. All direct writes are contained
                // within the first 16 bytes.
                wrapper.javaSerializationException("write_long");
            }
            return;
        }

        try {
            os.writeInt(value);
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "write_long");
        }
    }

    public final void write_ulong(int value) {
        this.write_long(value);
    }

    public final void write_longlong(long value) {
        try {
            os.writeLong(value);
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "write_longlong");
        }
    }

    public final void write_ulonglong(long value) {
        this.write_longlong(value);
    }

    public final void write_float(float value) {
        try {
            os.writeFloat(value);
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "write_float");
        }
    }

    public final void write_double(double value) {
        try {
            os.writeDouble(value);
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "write_double");
        }
    }

    // String types.

    public final void write_string(String value) {
        try {
            os.writeUTF(value);
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "write_string");
        }
    }

    public final void write_wstring(String value) {
        try {
            os.writeObject(value);
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "write_wstring");
        }
    }

    // Array types.

    public final void write_boolean_array(boolean[] value,
                                          int offset, int length) {
        for (int i = 0; i < length; i++) {
            write_boolean(value[offset + i]);
        }
    }

    public final void write_char_array(char[] value, int offset, int length) {
        for (int i = 0; i < length; i++) {
            write_char(value[offset + i]);
        }
    }

    public final void write_wchar_array(char[] value, int offset, int length) {
        write_char_array(value, offset, length);
    }

    public final void write_octet_array(byte[] value, int offset, int length) {
        try {
            os.write(value, offset, length);
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "write_octet_array");
        }
    }

    public final void write_short_array(short[] value,
                                        int offset, int length) {
        for (int i = 0; i < length; i++) {
            write_short(value[offset + i]);
        }
    }

    public final void write_ushort_array(short[] value,
                                         int offset, int length){
        write_short_array(value, offset, length);
    }

    public final void write_long_array(int[] value, int offset, int length) {
        for (int i = 0; i < length; i++) {
            write_long(value[offset + i]);
        }
    }

    public final void write_ulong_array(int[] value, int offset, int length) {
        write_long_array(value, offset, length);
    }

    public final void write_longlong_array(long[] value,
                                           int offset, int length) {
        for (int i = 0; i < length; i++) {
            write_longlong(value[offset + i]);
        }
    }

    public final void write_ulonglong_array(long[] value,
                                            int offset,int length) {
        write_longlong_array(value, offset, length);
    }

    public final void write_float_array(float[] value,
                                        int offset, int length) {
        for (int i = 0; i < length; i++) {
            write_float(value[offset + i]);
        }
    }

    public final void write_double_array(double[] value,
                                         int offset, int length) {
        for (int i = 0; i < length; i++) {
            write_double(value[offset + i]);
        }
    }

    // Complex types (objects and graphs).

    public final void write_Object(org.omg.CORBA.Object value) {
        if (value == null) {
            IOR nullIOR = IORFactories.makeIOR(orb);
            nullIOR.write(parent);
            return;
        }
        // IDL to Java formal 01-06-06 1.21.4.2
        if (value instanceof org.omg.CORBA.LocalObject) {
            throw wrapper.writeLocalObject(CompletionStatus.COMPLETED_MAYBE);
        }
        IOR ior = ORBUtility.connectAndGetIOR(orb, value);
        ior.write(parent);
        return;
    }

    public final void write_TypeCode(TypeCode tc) {
        if (tc == null) {
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);
        }
        TypeCodeImpl tci;
        if (tc instanceof TypeCodeImpl) {
            tci = (TypeCodeImpl) tc;
        } else {
            tci = new TypeCodeImpl(orb, tc);
        }
        tci.write_value((org.omg.CORBA_2_3.portable.OutputStream) parent);
    }

    public final void write_any(Any any) {
        if (any == null) {
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);
        }
        write_TypeCode(any.type());
        any.write_value(parent);
    }

    public final void write_Principal(Principal p) {
        // We don't need an implementation for this method, since principal
        // is absent in GIOP version 1.2 or above.
        write_long(p.name().length);
        write_octet_array(p.name(), 0, p.name().length);
    }

    public final void write_fixed(java.math.BigDecimal bigDecimal) {
        // This string might contain sign and/or dot
        this.write_fixed(bigDecimal.toString(), bigDecimal.signum());
    }

    // The string may contain a sign and dot
    private void write_fixed(String string, int signum) {

        int stringLength = string.length();

        // Each octet contains (up to) two decimal digits.
        byte doubleDigit = 0;
        char ch;
        byte digit;

        // First calculate the string length without optional sign and dot.
        int numDigits = 0;
        for (int i=0; i<stringLength; i++) {
            ch = string.charAt(i);
            if (ch == '-' || ch == '+' || ch == '.')
                continue;
            numDigits++;
        }

        for (int i=0; i<stringLength; i++) {
            ch = string.charAt(i);
            if (ch == '-' || ch == '+' || ch == '.')
                continue;
            digit = (byte)Character.digit(ch, 10);
            if (digit == -1) {
                throw wrapper.badDigitInFixed(
                                            CompletionStatus.COMPLETED_MAYBE);
            }
            // If the fixed type has an odd number of decimal digits, then the
            // representation begins with the first (most significant) digit.
            // Otherwise, this first half-octet is all zero, and the first
            // digit is in the second half-octet.
            if (numDigits % 2 == 0) {
                doubleDigit |= digit;
                this.write_octet(doubleDigit);
                doubleDigit = 0;
            } else {
                doubleDigit |= (digit << 4);
            }
            numDigits--;
        }

        // The sign configuration in the last half-octet of the representation,
        // is 0xD for negative numbers and 0xC for positive and zero values.
        if (signum == -1) {
            doubleDigit |= 0xd;
        } else {
            doubleDigit |= 0xc;
        }
        this.write_octet(doubleDigit);
    }

    public final org.omg.CORBA.ORB orb() {
        return this.orb;
    }

    // org.omg.CORBA_2_3.portable.OutputStream

    public final void write_value(java.io.Serializable value) {
        write_value(value, (String) null);
    }

    public final void write_value(java.io.Serializable value,
                                  java.lang.Class clz) {
        write_value(value);
    }

    public final void write_value(java.io.Serializable value,
                                  String repository_id) {
        try {
            os.writeObject(value);
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "write_value");
        }
    }

    public final void write_value(java.io.Serializable value,
                             org.omg.CORBA.portable.BoxedValueHelper factory) {
        this.write_value(value, (String) null);
    }

    public final void write_abstract_interface(java.lang.Object obj) {

        boolean isCorbaObject = false; // Assume value type.
        org.omg.CORBA.Object theCorbaObject = null;

        // Is it a CORBA.Object?
        if (obj != null && obj instanceof org.omg.CORBA.Object) {
            theCorbaObject = (org.omg.CORBA.Object)obj;
            isCorbaObject = true;
        }

        // Write the boolean flag.
        this.write_boolean(isCorbaObject);

        // Now write out the object.
        if (isCorbaObject) {
            write_Object(theCorbaObject);
        } else {
            try {
                write_value((java.io.Serializable)obj);
            } catch(ClassCastException cce) {
                if (obj instanceof java.io.Serializable) {
                    throw cce;
                } else {
                    ORBUtility.throwNotSerializableForCorba(
                                                    obj.getClass().getName());
                }
            }
        }
    }

    // com.sun.corba.se.os.encoding.MarshalOutputStream

    public final void start_block() {
        throw wrapper.giopVersionError();
    }

    public final void end_block() {
        throw wrapper.giopVersionError();
    }

    public final void putEndian() {
        throw wrapper.giopVersionError();
    }

    public void writeTo(java.io.OutputStream s) throws IOException {
        try {
            os.flush();
            bos.writeTo(s);
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "writeTo");
        }
    }

    public final byte[] toByteArray() {
        try {
            os.flush();
            return bos.toByteArray(); // new copy.
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "toByteArray");
        }
    }

    // org.omg.CORBA.DataOutputStream

    public final void write_Abstract (java.lang.Object value) {
        write_abstract_interface(value);
    }

    public final void write_Value(java.io.Serializable value) {
        write_value(value);
    }

    public final void write_any_array(org.omg.CORBA.Any[] value,
                                      int offset, int length) {
        for(int i = 0; i < length; i++) {
            write_any(value[offset + i]);
        }
    }

    // org.omg.CORBA.portable.ValueBase

    public final String[] _truncatable_ids() {
        throw wrapper.giopVersionError();
    }

    // Other.

    public final int getSize() {
        try {
            os.flush();
            return bos.size();
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "write_boolean");
        }
    }

    public final int getIndex() {
        return getSize();
    }

    protected int getRealIndex(int index) {
        return getSize();
    }

    public final void setIndex(int value) {
        throw wrapper.giopVersionError();
    }

    public final ByteBuffer getByteBuffer() {
        throw wrapper.giopVersionError();
    }

    public final void setByteBuffer(ByteBuffer byteBuffer) {
        throw wrapper.giopVersionError();
    }

    public final boolean isLittleEndian() {
        // Java serialization uses network byte order, that is, big-endian.
        return false;
    }

    public ByteBufferWithInfo getByteBufferWithInfo() {
        try {
            os.flush();
        } catch (Exception e) {
            throw wrapper.javaSerializationException(
                                            e, "getByteBufferWithInfo");
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(bos.getByteArray());
        byteBuffer.limit(bos.size());
        return new ByteBufferWithInfo(this.orb, byteBuffer, bos.size());
    }

    public void setByteBufferWithInfo(ByteBufferWithInfo bbwi) {
        throw wrapper.giopVersionError();
    }

    public final BufferManagerWrite getBufferManager() {
        return bufferManager;
    }

    // This will stay a custom add-on until the java-rtf issue is resolved.
    // Then it should be declared in org.omg.CORBA.portable.OutputStream.
    //
    // Pads the string representation of bigDecimal with zeros to fit the given
    // digits and scale before it gets written to the stream.
    public final void write_fixed(java.math.BigDecimal bigDecimal,
                                  short digits, short scale) {
        String string = bigDecimal.toString();
        String integerPart;
        String fractionPart;
        StringBuffer stringBuffer;

        // Get rid of the sign
        if (string.charAt(0) == '-' || string.charAt(0) == '+') {
            string = string.substring(1);
        }

        // Determine integer and fraction parts
        int dotIndex = string.indexOf('.');
        if (dotIndex == -1) {
            integerPart = string;
            fractionPart = null;
        } else if (dotIndex == 0 ) {
            integerPart = null;
            fractionPart = string;
        } else {
            integerPart = string.substring(0, dotIndex);
            fractionPart = string.substring(dotIndex + 1);
        }

        // Pad both parts with zeros as necessary
        stringBuffer = new StringBuffer(digits);
        if (fractionPart != null) {
            stringBuffer.append(fractionPart);
        }
        while (stringBuffer.length() < scale) {
            stringBuffer.append('0');
        }
        if (integerPart != null) {
            stringBuffer.insert(0, integerPart);
        }
        while (stringBuffer.length() < digits) {
            stringBuffer.insert(0, '0');
        }

        // This string contains no sign or dot
        this.write_fixed(stringBuffer.toString(), bigDecimal.signum());
    }

    public final void writeOctetSequenceTo(
            org.omg.CORBA.portable.OutputStream s) {
        byte[] buf = this.toByteArray(); // new copy.
        s.write_long(buf.length);
        s.write_octet_array(buf, 0, buf.length);
    }

    public final GIOPVersion getGIOPVersion() {
        return GIOPVersion.V1_2;
    }

    public final void writeIndirection(int tag, int posIndirectedTo) {
        throw wrapper.giopVersionError();
    }

    void freeInternalCaches() {}

    void printBuffer() {
        byte[] buf = this.toByteArray();

        System.out.println("+++++++ Output Buffer ++++++++");
        System.out.println();
        System.out.println("Current position: " + buf.length);
        //System.out.println("Total length : " + buf.length);
        System.out.println();

        char[] charBuf = new char[16];

        try {

            for (int i = 0; i < buf.length; i += 16) {

                int j = 0;

                // For every 16 bytes, there is one line
                // of output.  First, the hex output of
                // the 16 bytes with each byte separated
                // by a space.
                while (j < 16 && j + i < buf.length) {
                    int k = buf[i + j];
                    if (k < 0)
                        k = 256 + k;
                    String hex = Integer.toHexString(k);
                    if (hex.length() == 1)
                        hex = "0" + hex;
                    System.out.print(hex + " ");
                    j++;
                }

                // Add any extra spaces to align the
                // text column in case we didn't end
                // at 16
                while (j < 16) {
                    System.out.print("   ");
                    j++;
                }

                // Now output the ASCII equivalents.  Non-ASCII
                // characters are shown as periods.
                int x = 0;

                while (x < 16 && x + i < buf.length) {
                    if (ORBUtility.isPrintable((char)buf[i + x])) {
                        charBuf[x] = (char) buf[i + x];
                    } else {
                        charBuf[x] = '.';
                    }
                    x++;
                }
                System.out.println(new String(charBuf, 0, x));
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.out.println("++++++++++++++++++++++++++++++");
    }

    public void alignOnBoundary(int octetBoundary) {
        throw wrapper.giopVersionError();
    }

    // Needed by request and reply messages for GIOP versions >= 1.2 only.
    public void setHeaderPadding(boolean headerPadding) {
        // no-op. We don't care about body alignment while using
        // Java serialization. What the GIOP spec states does not apply here.
    }

    // ValueOutputStream -----------------------------

    public void start_value(String rep_id) {
        throw wrapper.giopVersionError();
    }

    public void end_value() {
        throw wrapper.giopVersionError();
    }

    // java.io.OutputStream

    // Note: These methods are defined in the super class and accessible.

    //public abstract void write(byte b[]) throws IOException;
    //public abstract void write(byte b[], int off, int len)
    //    throws IOException;
    //public abstract void flush() throws IOException;
    //public abstract void close() throws IOException;
}
