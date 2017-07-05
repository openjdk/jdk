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

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.math.BigDecimal;
import java.util.LinkedList;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.IORFactories;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.presentation.rmi.StubAdapter;
import com.sun.corba.se.spi.presentation.rmi.PresentationManager;
import com.sun.corba.se.spi.presentation.rmi.PresentationDefaults;

import com.sun.corba.se.impl.util.Utility;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.corba.TypeCodeImpl;
import com.sun.corba.se.impl.util.RepositoryId;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;

import org.omg.CORBA.Any;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.Principal;
import org.omg.CORBA.portable.IDLEntity;

/**
 * Implementation class that uses Java serialization for input streams.
 * This assumes a GIOP version 1.2 message format.
 *
 * This class uses a ByteArrayInputStream as the underlying buffer. The
 * first 16 bytes are directly read out of the underlying buffer. This allows
 * [GIOPHeader (12 bytes) + requestID (4 bytes)] to be read as bytes.
 * Subsequent write operations on this output stream object uses
 * ObjectInputStream class to read into the buffer. This allows unmarshaling
 * complex types and graphs using the ObjectInputStream implementation.
 *
 * Note, this class assumes a GIOP 1.2 style header. Further, the first
 * 12 bytes, that is, the GIOPHeader is read directly from the received
 * message, before this stream object is called. So, this class effectively
 * reads only the requestID (4 bytes) directly, and uses the
 * ObjectInputStream for further unmarshaling.
 *
 * @author Ram Jeyaraman
 */
public class IDLJavaSerializationInputStream extends CDRInputStreamBase {

    private ORB orb;
    private int bufSize;
    private ByteBuffer buffer;
    private byte encodingVersion;
    private ObjectInputStream is;
    private _ByteArrayInputStream bis;
    private BufferManagerRead bufferManager;

    // [GIOPHeader(12) + requestID(4)] bytes
    private final int directReadLength = Message.GIOPMessageHeaderLength + 4;

    // Used for mark / reset operations.
    private boolean markOn;
    private int peekIndex, peekCount;
    private LinkedList markedItemQ = new LinkedList();

    protected ORBUtilSystemException wrapper;

    class _ByteArrayInputStream extends ByteArrayInputStream {

        _ByteArrayInputStream(byte[] buf) {
            super(buf);
        }

        int getPosition() {
            return this.pos;
        }

        void setPosition(int value) {
            if (value < 0 || value > count) {
                throw new IndexOutOfBoundsException();
            }
            this.pos = value;
        }
    }

    class MarshalObjectInputStream extends ObjectInputStream {

        ORB orb;

        MarshalObjectInputStream(java.io.InputStream out, ORB orb)
                throws IOException {

            super(out);
            this.orb = orb;

            java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction() {
                    public Object run() {
                        // needs SerializablePermission("enableSubstitution")
                        enableResolveObject(true);
                        return null;
                    }
                }
            );
        }

        /**
         * Connect the Stub to the ORB.
         */
        protected final Object resolveObject(Object obj) throws IOException {
            try {
                if (StubAdapter.isStub(obj)) {
                    StubAdapter.connect(obj, orb);
                }
            } catch (java.rmi.RemoteException re) {
                IOException ie = new IOException("resolveObject failed");
                ie.initCause(re);
                throw ie;
            }
            return obj;
        }
    }

    public IDLJavaSerializationInputStream(byte encodingVersion) {
        super();
        this.encodingVersion = encodingVersion;
    }

    public void init(org.omg.CORBA.ORB orb,
                     ByteBuffer byteBuffer,
                     int bufSize,
                     boolean littleEndian,
                     BufferManagerRead bufferManager) {
        this.orb = (ORB) orb;
        this.bufSize = bufSize;
        this.bufferManager = bufferManager;
        buffer = byteBuffer;
        wrapper =
            ORBUtilSystemException.get((ORB)orb, CORBALogDomains.RPC_ENCODING);

        byte[] buf;
        if (buffer.hasArray()) {
            buf = buffer.array();
        } else {
            buf = new byte[bufSize];
            buffer.get(buf);
        }
        // Note: at this point, the buffer position is zero. The setIndex()
        // method call can be used to set a desired read index.
        bis = new _ByteArrayInputStream(buf);
    }

    // Called from read_octet or read_long or read_ulong method.
    private void initObjectInputStream() {
        //System.out.print(" is ");
        if (is != null) {
            throw wrapper.javaStreamInitFailed();
        }
        try {
            is = new MarshalObjectInputStream(bis, orb);
        } catch (Exception e) {
            throw wrapper.javaStreamInitFailed(e);
        }
    }

    // org.omg.CORBA.portable.InputStream

    // Primitive types.

    public boolean read_boolean() {
        if (!markOn && !(markedItemQ.isEmpty())) { // dequeue
            return ((Boolean)markedItemQ.removeFirst()).booleanValue();
        }
        if (markOn && !(markedItemQ.isEmpty()) &&
                (peekIndex < peekCount)) { // peek
            return ((Boolean)markedItemQ.get(peekIndex++)).booleanValue();
        }
        try {
            boolean value = is.readBoolean();
            if (markOn) { // enqueue
                markedItemQ.addLast(Boolean.valueOf(value));
            }
            return value;
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "read_boolean");
        }
    }

    public char read_char() {
        if (!markOn && !(markedItemQ.isEmpty())) { // dequeue
            return ((Character)markedItemQ.removeFirst()).charValue();
        }
        if (markOn && !(markedItemQ.isEmpty()) &&
                (peekIndex < peekCount)) { // peek
            return ((Character)markedItemQ.get(peekIndex++)).charValue();
        }
        try {
            char value = is.readChar();
            if (markOn) { // enqueue
                markedItemQ.addLast(new Character(value));
            }
            return value;
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "read_char");
        }
    }

    public char read_wchar() {
        return this.read_char();
    }

    public byte read_octet() {

        // check if size < [ GIOPHeader(12) + requestID(4)] bytes
        if (bis.getPosition() < directReadLength) {
            byte b = (byte) bis.read();
            if (bis.getPosition() == directReadLength) {
                initObjectInputStream();
            }
            return b;
        }

        if (!markOn && !(markedItemQ.isEmpty())) { // dequeue
            return ((Byte)markedItemQ.removeFirst()).byteValue();
        }

        if (markOn && !(markedItemQ.isEmpty()) &&
                (peekIndex < peekCount)) { // peek
            return ((Byte)markedItemQ.get(peekIndex++)).byteValue();
        }

        try {
            byte value = is.readByte();
            if (markOn) { // enqueue
                //markedItemQ.addLast(Byte.valueOf(value)); // only in JDK 1.5
                markedItemQ.addLast(new Byte(value));
            }
            return value;
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "read_octet");
        }
    }

    public short read_short() {
        if (!markOn && !(markedItemQ.isEmpty())) { // dequeue
            return ((Short)markedItemQ.removeFirst()).shortValue();
        }
        if (markOn && !(markedItemQ.isEmpty()) &&
                (peekIndex < peekCount)) { // peek
            return ((Short)markedItemQ.get(peekIndex++)).shortValue();
        }

        try {
            short value = is.readShort();
            if (markOn) { // enqueue
                markedItemQ.addLast(new Short(value));
            }
            return value;
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "read_short");
        }
    }

    public short read_ushort() {
        return this.read_short();
    }

    public int read_long() {

        // check if size < [ GIOPHeader(12) + requestID(4)] bytes
        if (bis.getPosition() < directReadLength) {

            // Use big endian (network byte order). This is fixed.
            // Both the writer and reader use the same byte order.
            int b1 = (bis.read() << 24) & 0xFF000000;
            int b2 = (bis.read() << 16) & 0x00FF0000;
            int b3 = (bis.read() << 8)  & 0x0000FF00;
            int b4 = (bis.read() << 0)  & 0x000000FF;

            if (bis.getPosition() == directReadLength) {
                initObjectInputStream();
            } else if (bis.getPosition() > directReadLength) {
                // Cannot happen. All direct reads are contained
                // within the first 16 bytes.
                wrapper.javaSerializationException("read_long");
            }

            return (b1 | b2 | b3 | b4);
        }

        if (!markOn && !(markedItemQ.isEmpty())) { // dequeue
            return ((Integer)markedItemQ.removeFirst()).intValue();
        }

        if (markOn && !(markedItemQ.isEmpty()) &&
                (peekIndex < peekCount)) { // peek
            return ((Integer)markedItemQ.get(peekIndex++)).intValue();
        }

        try {
            int value = is.readInt();
            if (markOn) { // enqueue
                markedItemQ.addLast(new Integer(value));
            }
            return value;
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "read_long");
        }
    }

    public int read_ulong() {
        return this.read_long();
    }

    public long read_longlong() {
        if (!markOn && !(markedItemQ.isEmpty())) { // dequeue
            return ((Long)markedItemQ.removeFirst()).longValue();
        }
        if (markOn && !(markedItemQ.isEmpty()) &&
                (peekIndex < peekCount)) { // peek
            return ((Long)markedItemQ.get(peekIndex++)).longValue();
        }

        try {
            long value = is.readLong();
            if (markOn) { // enqueue
                markedItemQ.addLast(new Long(value));
            }
            return value;
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "read_longlong");
        }
    }

    public long read_ulonglong() {
        return read_longlong();
    }

    public float read_float() {
        if (!markOn && !(markedItemQ.isEmpty())) { // dequeue
            return ((Float)markedItemQ.removeFirst()).floatValue();
        }
        if (markOn && !(markedItemQ.isEmpty()) &&
                (peekIndex < peekCount)) { // peek
            return ((Float)markedItemQ.get(peekIndex++)).floatValue();
        }

        try {
            float value = is.readFloat();
            if (markOn) { // enqueue
                markedItemQ.addLast(new Float(value));
            }
            return value;
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "read_float");
        }
    }

    public double read_double() {
        if (!markOn && !(markedItemQ.isEmpty())) { // dequeue
            return ((Double)markedItemQ.removeFirst()).doubleValue();
        }
        if (markOn && !(markedItemQ.isEmpty()) &&
                (peekIndex < peekCount)) { // peek
            return ((Double)markedItemQ.get(peekIndex++)).doubleValue();
        }

        try {
            double value = is.readDouble();
            if (markOn) { // enqueue
                markedItemQ.addLast(new Double(value));
            }
            return value;
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "read_double");
        }
    }

    // String types.

    public String read_string() {
        if (!markOn && !(markedItemQ.isEmpty())) { // dequeue
            return (String) markedItemQ.removeFirst();
        }
        if (markOn && !(markedItemQ.isEmpty()) &&
            (peekIndex < peekCount)) { // peek
            return (String) markedItemQ.get(peekIndex++);
        }
        try {
            String value = is.readUTF();
            if (markOn) { // enqueue
                markedItemQ.addLast(value);
            }
            return value;
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "read_string");
        }
    }

    public String read_wstring() {
        if (!markOn && !(markedItemQ.isEmpty())) { // dequeue
            return (String) markedItemQ.removeFirst();
        }
        if (markOn && !(markedItemQ.isEmpty()) &&
                (peekIndex < peekCount)) { // peek
            return (String) markedItemQ.get(peekIndex++);
        }
        try {
            String value = (String) is.readObject();
            if (markOn) { // enqueue
                markedItemQ.addLast(value);
            }
            return value;
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "read_wstring");
        }
    }

    // Array types.

    public void read_boolean_array(boolean[] value, int offset, int length){
        for(int i = 0; i < length; i++) {
            value[i+offset] = read_boolean();
        }
    }

    public void read_char_array(char[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_char();
        }
    }

    public void read_wchar_array(char[] value, int offset, int length) {
        read_char_array(value, offset, length);
    }

    public void read_octet_array(byte[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_octet();
        }
        /* // Cannot use this efficient read due to mark/reset support.
        try {
            while (length > 0) {
                int n = is.read(value, offset, length);
                offset += n;
                length -= n;
            }
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "read_octet_array");
        }
        */
    }

    public void read_short_array(short[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_short();
        }
    }

    public void read_ushort_array(short[] value, int offset, int length) {
        read_short_array(value, offset, length);
    }

    public void read_long_array(int[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_long();
        }
    }

    public void read_ulong_array(int[] value, int offset, int length) {
        read_long_array(value, offset, length);
    }

    public void read_longlong_array(long[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_longlong();
        }
    }

    public void read_ulonglong_array(long[] value, int offset, int length) {
        read_longlong_array(value, offset, length);
    }

    public void read_float_array(float[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_float();
        }
    }

    public void read_double_array(double[] value, int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_double();
        }
    }

    // Complex types.

    public org.omg.CORBA.Object read_Object() {
        return read_Object(null);
    }

    public TypeCode read_TypeCode() {
        TypeCodeImpl tc = new TypeCodeImpl(orb);
        tc.read_value(parent);
        return tc;
    }

    public Any read_any() {

        Any any = orb.create_any();
        TypeCodeImpl tc = new TypeCodeImpl(orb);

        // read off the typecode

        // REVISIT We could avoid this try-catch if we could peek the typecode
        // kind off this stream and see if it is a tk_value.
        // Looking at the code we know that for tk_value the Any.read_value()
        // below ignores the tc argument anyway (except for the kind field).
        // But still we would need to make sure that the whole typecode,
        // including encapsulations, is read off.
        try {
            tc.read_value(parent);
        } catch (org.omg.CORBA.MARSHAL ex) {
            if (tc.kind().value() != org.omg.CORBA.TCKind._tk_value) {
                throw ex;
            }
            // We can be sure that the whole typecode encapsulation has been
            // read off.
            ex.printStackTrace();
        }

        // read off the value of the any.
        any.read_value(parent, tc);

        return any;
    }

    public Principal read_Principal() {
        // We don't need an implementation for this method, since principal
        // is absent in GIOP version 1.2 or above.
        int len = read_long();
        byte[] pvalue = new byte[len];
        read_octet_array(pvalue,0,len);
        Principal p = new com.sun.corba.se.impl.corba.PrincipalImpl();
        p.name(pvalue);
        return p;
    }

    public BigDecimal read_fixed() {
        return new BigDecimal(read_fixed_buffer().toString());
    }

    // Each octet contains (up to) two decimal digits. If the fixed type has
    // an odd number of decimal digits, then the representation
    // begins with the first (most significant) digit.
    // Otherwise, this first half-octet is all zero, and the first digit
    // is in the second half-octet.
    // The sign configuration, in the last half-octet of the representation,
    // is 0xD for negative numbers and 0xC for positive and zero values.
    private StringBuffer read_fixed_buffer() {
        StringBuffer buffer = new StringBuffer(64);
        byte doubleDigit;
        int firstDigit;
        int secondDigit;
        boolean wroteFirstDigit = false;
        boolean more = true;
        while (more) {
            doubleDigit = read_octet();
            firstDigit = (int)((doubleDigit & 0xf0) >> 4);
            secondDigit = (int)(doubleDigit & 0x0f);
            if (wroteFirstDigit || firstDigit != 0) {
                buffer.append(Character.forDigit(firstDigit, 10));
                wroteFirstDigit = true;
            }
            if (secondDigit == 12) {
                // positive number or zero
                if ( ! wroteFirstDigit) {
                    // zero
                    return new StringBuffer("0.0");
                } else {
                    // positive number
                    // done
                }
                more = false;
            } else if (secondDigit == 13) {
                // negative number
                buffer.insert(0, '-');
                more = false;
            } else {
                buffer.append(Character.forDigit(secondDigit, 10));
                wroteFirstDigit = true;
            }
        }
        return buffer;
    }

    public org.omg.CORBA.Object read_Object(java.lang.Class clz) {

        // In any case, we must first read the IOR.
        IOR ior = IORFactories.makeIOR(parent) ;
        if (ior.isNil()) {
            return null;
        }

        PresentationManager.StubFactoryFactory sff =
            ORB.getStubFactoryFactory();
        String codeBase = ior.getProfile().getCodebase();
        PresentationManager.StubFactory stubFactory = null;

        if (clz == null) {
            RepositoryId rid = RepositoryId.cache.getId(ior.getTypeId() );
            String className = rid.getClassName();
            boolean isIDLInterface = rid.isIDLType();

            if (className == null || className.equals( "" )) {
                stubFactory = null;
            } else {
                try {
                    stubFactory = sff.createStubFactory(className,
                        isIDLInterface, codeBase, (Class) null,
                        (ClassLoader) null);
                } catch (Exception exc) {
                    // Could not create stubFactory, so use null.
                    // XXX stubFactory handling is still too complex:
                    // Can we resolve the stubFactory question once in
                    // a single place?
                    stubFactory = null ;
                }
            }
        } else if (StubAdapter.isStubClass(clz)) {
            stubFactory = PresentationDefaults.makeStaticStubFactory(clz);
        } else {
            // clz is an interface class
            boolean isIDL = IDLEntity.class.isAssignableFrom(clz);

            stubFactory = sff.createStubFactory(
                 clz.getName(), isIDL, codeBase, clz, clz.getClassLoader());
        }

        return CDRInputStream_1_0.internalIORToObject(ior, stubFactory, orb);
    }

    public org.omg.CORBA.ORB orb() {
        return this.orb;
    }

    // org.omg.CORBA_2_3.portable.InputStream

    public java.io.Serializable read_value() {
        if (!markOn && !(markedItemQ.isEmpty())) { // dequeue
            return (Serializable) markedItemQ.removeFirst();
        }
        if (markOn && !(markedItemQ.isEmpty()) &&
                (peekIndex < peekCount)) { // peek
            return (Serializable) markedItemQ.get(peekIndex++);
        }
        try {
            Serializable value = (java.io.Serializable) is.readObject();
            if (markOn) { // enqueue
                markedItemQ.addLast(value);
            }
            return value;
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "read_value");
        }
    }

    public java.io.Serializable read_value(java.lang.Class clz) {
        return read_value();
    }

    public java.io.Serializable read_value(
            org.omg.CORBA.portable.BoxedValueHelper factory) {
        return read_value();
    }

    public java.io.Serializable read_value(java.lang.String rep_id) {
        return read_value();
    }

    public java.io.Serializable read_value(java.io.Serializable value) {
        return read_value();
    }

    public java.lang.Object read_abstract_interface() {
        return read_abstract_interface(null);
    }

    public java.lang.Object read_abstract_interface(java.lang.Class clz) {
        boolean isObject = read_boolean();
        if (isObject) {
            return read_Object(clz);
        } else {
            return read_value();
        }
    }

    // com.sun.corba.se.impl.encoding.MarshalInputStream
    public void consumeEndian() {
        throw wrapper.giopVersionError();
    }

    public int getPosition() {
        try {
            return bis.getPosition();
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "getPosition");
        }
    }

    // org.omg.CORBA.DataInputStream
    public java.lang.Object read_Abstract() {
        return read_abstract_interface();
    }

    public java.io.Serializable read_Value() {
        return read_value();
    }

    public void read_any_array (org.omg.CORBA.AnySeqHolder seq,
                                int offset, int length) {
        read_any_array(seq.value, offset, length);
    }

    private final void read_any_array(org.omg.CORBA.Any[] value,
                                     int offset, int length) {
        for(int i=0; i < length; i++) {
            value[i+offset] = read_any();
        }
    }

    public void read_boolean_array (org.omg.CORBA.BooleanSeqHolder seq,
                                    int offset, int length){
        read_boolean_array(seq.value, offset, length);
    }

    public void read_char_array (org.omg.CORBA.CharSeqHolder seq,
                                 int offset, int length){
        read_char_array(seq.value, offset, length);
    }

    public void read_wchar_array (org.omg.CORBA.WCharSeqHolder seq,
                                  int offset, int length){
        read_wchar_array(seq.value, offset, length);
    }

    public void read_octet_array (org.omg.CORBA.OctetSeqHolder seq,
                                  int offset, int length){
        read_octet_array(seq.value, offset, length);
    }

    public void read_short_array (org.omg.CORBA.ShortSeqHolder seq,
                                  int offset, int length){
        read_short_array(seq.value, offset, length);
    }

    public void read_ushort_array (org.omg.CORBA.UShortSeqHolder seq,
                                   int offset, int length){
        read_ushort_array(seq.value, offset, length);
    }

    public void read_long_array (org.omg.CORBA.LongSeqHolder seq,
                                 int offset, int length){
        read_long_array(seq.value, offset, length);
    }

    public void read_ulong_array (org.omg.CORBA.ULongSeqHolder seq,
                                  int offset, int length){
        read_ulong_array(seq.value, offset, length);
    }

    public void read_ulonglong_array (org.omg.CORBA.ULongLongSeqHolder seq,
                                      int offset, int length){
        read_ulonglong_array(seq.value, offset, length);
    }

    public void read_longlong_array (org.omg.CORBA.LongLongSeqHolder seq,
                                     int offset, int length){
        read_longlong_array(seq.value, offset, length);
    }

    public void read_float_array (org.omg.CORBA.FloatSeqHolder seq,
                                  int offset, int length){
        read_float_array(seq.value, offset, length);
    }

    public void read_double_array (org.omg.CORBA.DoubleSeqHolder seq,
                                   int offset, int length){
        read_double_array(seq.value, offset, length);
    }

    // org.omg.CORBA.portable.ValueBase

    public String[] _truncatable_ids() {
        throw wrapper.giopVersionError();
    }

    // java.io.InputStream
    // REVISIT - should we make these throw UnsupportedOperationExceptions?
    // Right now, they'll go up to the java.io versions!

    //     public int read(byte b[]) throws IOException;
    //     public int read(byte b[], int off, int len) throws IOException
    //     public long skip(long n) throws IOException;
    //     public int available() throws IOException;
    //     public void close() throws IOException;

    public void mark(int readLimit) {
        // Nested mark disallowed.
        // Further, mark is not supported until first 16 bytes are read.
        if (markOn || is == null) {
            throw wrapper.javaSerializationException("mark");
        }
        markOn = true;
        if (!(markedItemQ.isEmpty())) {
            peekIndex = 0;
            peekCount = markedItemQ.size();
        }
        /*
        // Note: only ByteArrayInputStream supports mark/reset.
        if (is == null || is.markSupported() == false) {
            throw wrapper.javaSerializationException("mark");
        }
        is.mark(readLimit);
        */
    }

    public void reset() {
        markOn = false;
        peekIndex = 0;
        peekCount = 0;
        /*
        // Note: only ByteArrayInputStream supports mark/reset.
        if (is == null || is.markSupported() == false) {
            throw wrapper.javaSerializationException("mark");
        }
        try {
            is.reset();
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "reset");
        }
        */
    }

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
    public boolean markSupported() {
        return true;
    }

    // Needed by AnyImpl and ServiceContexts
    public CDRInputStreamBase dup() {

        CDRInputStreamBase result = null ;

        try {
            result = (CDRInputStreamBase) this.getClass().newInstance();
        } catch (Exception e) {
            throw wrapper.couldNotDuplicateCdrInputStream(e);
        }

        result.init(this.orb, this.buffer, this.bufSize, false, null);

        // Set the buffer position.
        ((IDLJavaSerializationInputStream)result).skipBytes(getPosition());

        // Set mark related data.
        ((IDLJavaSerializationInputStream)result).
            setMarkData(markOn, peekIndex, peekCount,
                        (LinkedList) markedItemQ.clone());

        return result;
    }

    // Used exclusively by the dup() method.
    void skipBytes(int len) {
        try {
            is.skipBytes(len);
        } catch (Exception e) {
            throw wrapper.javaSerializationException(e, "skipBytes");
        }
    }

    // Used exclusively by the dup() method.
    void setMarkData(boolean markOn, int peekIndex, int peekCount,
                     LinkedList markedItemQ) {
        this.markOn = markOn;
        this.peekIndex = peekIndex;
        this.peekCount = peekCount;
        this.markedItemQ = markedItemQ;
    }

    // Needed by TCUtility
    public java.math.BigDecimal read_fixed(short digits, short scale) {
        // digits isn't really needed here
        StringBuffer buffer = read_fixed_buffer();
        if (digits != buffer.length())
            throw wrapper.badFixed( new Integer(digits),
                new Integer(buffer.length()) ) ;
        buffer.insert(digits - scale, '.');
        return new BigDecimal(buffer.toString());
    }

    // Needed by TypeCodeImpl
    public boolean isLittleEndian() {
        throw wrapper.giopVersionError();
    }

    // Needed by request and reply messages for GIOP versions >= 1.2 only.
    void setHeaderPadding(boolean headerPadding) {
        // no-op. We don't care about body alignment while using
        // Java serialization. What the GIOP spec states does not apply here.
    }

    // Needed by IIOPInputStream and other subclasses

    public ByteBuffer getByteBuffer() {
        throw wrapper.giopVersionError();
    }

    public void setByteBuffer(ByteBuffer byteBuffer) {
        throw wrapper.giopVersionError();
    }

    public void setByteBufferWithInfo(ByteBufferWithInfo bbwi) {
        throw wrapper.giopVersionError();
    }

    public int getBufferLength() {
        return bufSize;
    }

    public void setBufferLength(int value) {
        // this is redundant, since buffer size was already specified
        // as part of the init call. So, ignore.
    }

    public int getIndex() {
        return bis.getPosition();
    }

    public void setIndex(int value) {
        try {
            bis.setPosition(value);
        } catch (IndexOutOfBoundsException e) {
            throw wrapper.javaSerializationException(e, "setIndex");
        }
    }

    public void orb(org.omg.CORBA.ORB orb) {
        this.orb = (ORB) orb;
    }

    public BufferManagerRead getBufferManager() {
        return bufferManager;
    }

    public GIOPVersion getGIOPVersion() {
        return GIOPVersion.V1_2;
    }

    com.sun.org.omg.SendingContext.CodeBase getCodeBase() {
        return parent.getCodeBase();
    }

    void printBuffer() {
        byte[] buf = this.buffer.array();

        System.out.println("+++++++ Input Buffer ++++++++");
        System.out.println();
        System.out.println("Current position: " + getPosition());
        System.out.println("Total length : " + this.bufSize);
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

    void alignOnBoundary(int octetBoundary) {
        throw wrapper.giopVersionError();
    }

    void performORBVersionSpecificInit() {
        // No-op.
    }

    public void resetCodeSetConverters() {
        // No-op.
    }

    // ValueInputStream -------------------------

    public void start_value() {
        throw wrapper.giopVersionError();
    }

    public void end_value() {
        throw wrapper.giopVersionError();
    }
}
