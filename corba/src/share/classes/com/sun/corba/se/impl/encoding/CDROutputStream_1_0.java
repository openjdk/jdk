/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package com.sun.corba.se.impl.encoding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.rmi.Remote;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.util.Hashtable;
import java.util.Stack;

import javax.rmi.CORBA.Util;
import javax.rmi.CORBA.ValueHandler;
import javax.rmi.CORBA.ValueHandlerMultiFormat;

import org.omg.CORBA.CustomMarshal;
import org.omg.CORBA.DataOutputStream;
import org.omg.CORBA.TypeCodePackage.BadKind;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.Object;
import org.omg.CORBA.Principal;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.Any;
import org.omg.CORBA.VM_CUSTOM;
import org.omg.CORBA.VM_TRUNCATABLE;
import org.omg.CORBA.VM_NONE;
import org.omg.CORBA.portable.IDLEntity;
import org.omg.CORBA.portable.CustomValue;
import org.omg.CORBA.portable.StreamableValue;
import org.omg.CORBA.portable.BoxedValueHelper;
import org.omg.CORBA.portable.OutputStream;
import org.omg.CORBA.portable.ValueBase;

import com.sun.org.omg.CORBA.portable.ValueHelper;

import com.sun.corba.se.pept.protocol.MessageMediator;
import com.sun.corba.se.pept.transport.ByteBufferPool;

import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.IORFactories;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.orb.ORBVersionFactory;
import com.sun.corba.se.spi.orb.ORBVersion;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;
import com.sun.corba.se.spi.logging.CORBALogDomains;

import com.sun.corba.se.impl.encoding.ByteBufferWithInfo;
import com.sun.corba.se.impl.encoding.MarshalOutputStream;
import com.sun.corba.se.impl.encoding.CodeSetConversion;
import com.sun.corba.se.impl.corba.TypeCodeImpl;
import com.sun.corba.se.impl.orbutil.CacheTable;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.orbutil.RepositoryIdStrings;
import com.sun.corba.se.impl.orbutil.RepositoryIdUtility;
import com.sun.corba.se.impl.orbutil.RepositoryIdFactory;
import com.sun.corba.se.impl.util.Utility;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;

public class CDROutputStream_1_0 extends CDROutputStreamBase
{
    private static final int INDIRECTION_TAG = 0xffffffff;

    protected boolean littleEndian;
    protected BufferManagerWrite bufferManagerWrite;
    ByteBufferWithInfo bbwi;

    protected ORB orb;
    protected ORBUtilSystemException wrapper ;

    protected boolean debug = false;

    protected int blockSizeIndex = -1;
    protected int blockSizePosition = 0;

    protected byte streamFormatVersion;

    private static final int DEFAULT_BUFFER_SIZE = 1024;
    private static final String kWriteMethod = "write";

    // Codebase cache
    private CacheTable codebaseCache = null;

    // Value cache
    private CacheTable valueCache = null;

    // Repository ID cache
    private CacheTable repositoryIdCache = null;

    // Write end flag
    private int end_flag = 0;

    // Beginning with the resolution to interop issue 3526,
    // only enclosing chunked valuetypes are taken into account
    // when computing the nesting level.  However, we still need
    // the old computation around for interoperability with our
    // older ORBs.
    private int chunkedValueNestingLevel = 0;

    private boolean mustChunk = false;

    // In block marker
    protected boolean inBlock = false;

    // Last end tag position
    private int end_flag_position = 0;
    private int end_flag_index = 0;

    // ValueHandler
    private ValueHandler valueHandler = null;

    // Repository ID handlers
    private RepositoryIdUtility repIdUtil;
    private RepositoryIdStrings repIdStrs;

    // Code set converters (created when first needed)
    private CodeSetConversion.CTBConverter charConverter;
    private CodeSetConversion.CTBConverter wcharConverter;

    // REVISIT - This should be re-factored so that including whether
    // to use pool byte buffers or not doesn't need to be known.
    public void init(org.omg.CORBA.ORB orb,
                        boolean littleEndian,
                        BufferManagerWrite bufferManager,
                        byte streamFormatVersion,
                        boolean usePooledByteBuffers)
    {
        // ORB must not be null.  See CDROutputStream constructor.
        this.orb = (ORB)orb;
        this.wrapper = ORBUtilSystemException.get( this.orb,
            CORBALogDomains.RPC_ENCODING ) ;
        debug = this.orb.transportDebugFlag;

        this.littleEndian = littleEndian;
        this.bufferManagerWrite = bufferManager;
        this.bbwi = new ByteBufferWithInfo(orb, bufferManager, usePooledByteBuffers);
        this.streamFormatVersion = streamFormatVersion;

        createRepositoryIdHandlers();
    }

    public void init(org.omg.CORBA.ORB orb,
                        boolean littleEndian,
                        BufferManagerWrite bufferManager,
                        byte streamFormatVersion)
   {
       init(orb, littleEndian, bufferManager, streamFormatVersion, true);
   }

    private final void createRepositoryIdHandlers()
    {
        repIdUtil = RepositoryIdFactory.getRepIdUtility();
        repIdStrs = RepositoryIdFactory.getRepIdStringsFactory();
    }

    public BufferManagerWrite getBufferManager()
    {
        return bufferManagerWrite;
    }

    public byte[] toByteArray() {
        byte[] it;

        it = new byte[bbwi.position()];

        // Micro-benchmarks show ByteBuffer.get(int) out perform the bulk
        // ByteBuffer.get(byte[], offset, length).
        for (int i = 0; i < bbwi.position(); i++)
            it[i] = bbwi.byteBuffer.get(i);

        return it;
    }

    public GIOPVersion getGIOPVersion() {
        return GIOPVersion.V1_0;
    }

    // Called by Request and Reply message. Valid for GIOP versions >= 1.2 only.
    // Illegal for GIOP versions < 1.2.
    void setHeaderPadding(boolean headerPadding) {
        throw wrapper.giopVersionError();
    }

    protected void handleSpecialChunkBegin(int requiredSize)
    {
        // No-op for GIOP 1.0
    }

    protected void handleSpecialChunkEnd()
    {
        // No-op for GIOP 1.0
    }

    protected final int computeAlignment(int align) {
        if (align > 1) {
            int incr = bbwi.position() & (align - 1);
            if (incr != 0)
                return align - incr;
        }

        return 0;
    }

    protected void alignAndReserve(int align, int n) {

        bbwi.position(bbwi.position() + computeAlignment(align));

        if (bbwi.position() + n  > bbwi.buflen)
            grow(align, n);
    }

    //
    // Default implementation of grow.  Subclassers may override this.
    // Always grow the single buffer. This needs to delegate
    // fragmentation policy for IIOP 1.1.
    //
    protected void grow(int align, int n)
    {
        bbwi.needed = n;

        bufferManagerWrite.overflow(bbwi);
    }

    public final void putEndian() throws SystemException {
        write_boolean(littleEndian);
    }

    public final boolean littleEndian() {
        return littleEndian;
    }

    void freeInternalCaches() {
        if (codebaseCache != null)
            codebaseCache.done();

        if (valueCache != null)
            valueCache.done();

        if (repositoryIdCache != null)
            repositoryIdCache.done();
    }

    // No such type in java
    public final void write_longdouble(double x)
    {
        throw wrapper.longDoubleNotImplemented(
            CompletionStatus.COMPLETED_MAYBE ) ;
    }

    public void write_octet(byte x)
    {
        // The 'if' stmt is commented out since we need the alignAndReserve to
        // be called, particularly when the first body byte is written,
        // to induce header padding to align the body on a 8-octet boundary,
        // for GIOP versions 1.2 and above. Refer to internalWriteOctetArray()
        // method that also has a similar change.
        //if (bbwi.position() + 1 > bbwi.buflen)
            alignAndReserve(1, 1);

//      REVISIT - Should just use ByteBuffer.put(byte) and let it
//                increment the ByteBuffer position. This is true
//                for all write operations in this file.

        bbwi.byteBuffer.put(bbwi.position(), x);
        bbwi.position(bbwi.position() + 1);

    }

    public final void write_boolean(boolean x)
    {
        write_octet(x? (byte)1:(byte)0);
    }

    public void write_char(char x)
    {
        CodeSetConversion.CTBConverter converter = getCharConverter();

        converter.convert(x);

        // CORBA formal 99-10-07 15.3.1.6: "In the case of multi-byte encodings
        // of characters, a single instance of the char type may only
        // hold one octet of any multi-byte character encoding."
        if (converter.getNumBytes() > 1)
            throw wrapper.invalidSingleCharCtb(CompletionStatus.COMPLETED_MAYBE);

        write_octet(converter.getBytes()[0]);
    }

    // These wchar methods are only used when talking to
    // legacy ORBs, now.
    private final void writeLittleEndianWchar(char x) {
        bbwi.byteBuffer.put(bbwi.position(), (byte)(x & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 1, (byte)((x >>> 8) & 0xFF));
        bbwi.position(bbwi.position() + 2);
    }

    private final void writeBigEndianWchar(char x) {
        bbwi.byteBuffer.put(bbwi.position(), (byte)((x >>> 8) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 1, (byte)(x & 0xFF));
        bbwi.position(bbwi.position() + 2);
    }

    private final void writeLittleEndianShort(short x) {
        bbwi.byteBuffer.put(bbwi.position(), (byte)(x & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 1, (byte)((x >>> 8) & 0xFF));
        bbwi.position(bbwi.position() + 2);
    }

    private final void writeBigEndianShort(short x) {
        bbwi.byteBuffer.put(bbwi.position(), (byte)((x >>> 8) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 1, (byte)(x & 0xFF));
        bbwi.position(bbwi.position() + 2);
    }

    private final void writeLittleEndianLong(int x) {
        bbwi.byteBuffer.put(bbwi.position(), (byte)(x & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 1, (byte)((x >>> 8) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 2, (byte)((x >>> 16) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 3, (byte)((x >>> 24) & 0xFF));
        bbwi.position(bbwi.position() + 4);
    }

    private final void writeBigEndianLong(int x) {
        bbwi.byteBuffer.put(bbwi.position(), (byte)((x >>> 24) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 1, (byte)((x >>> 16) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 2, (byte)((x >>> 8) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 3, (byte)(x & 0xFF));
        bbwi.position(bbwi.position() + 4);
    }

    private final void writeLittleEndianLongLong(long x) {
        bbwi.byteBuffer.put(bbwi.position(), (byte)(x & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 1, (byte)((x >>> 8) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 2, (byte)((x >>> 16) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 3, (byte)((x >>> 24) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 4, (byte)((x >>> 32) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 5, (byte)((x >>> 40) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 6, (byte)((x >>> 48) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 7, (byte)((x >>> 56) & 0xFF));
        bbwi.position(bbwi.position() + 8);
    }

    private final void writeBigEndianLongLong(long x) {
        bbwi.byteBuffer.put(bbwi.position(), (byte)((x >>> 56) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 1, (byte)((x >>> 48) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 2, (byte)((x >>> 40) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 3, (byte)((x >>> 32) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 4, (byte)((x >>> 24) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 5, (byte)((x >>> 16) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 6, (byte)((x >>> 8) & 0xFF));
        bbwi.byteBuffer.put(bbwi.position() + 7, (byte)(x & 0xFF));
        bbwi.position(bbwi.position() + 8);
    }

    public void write_wchar(char x)
    {
        // Don't allow transmission of wchar/wstring data with
        // foreign ORBs since it's against the spec.
        if (ORBUtility.isForeignORB(orb)) {
            throw wrapper.wcharDataInGiop10(CompletionStatus.COMPLETED_MAYBE);
        }

        // If it's one of our legacy ORBs, do what they did:
        alignAndReserve(2, 2);

        if (littleEndian) {
            writeLittleEndianWchar(x);
        } else {
            writeBigEndianWchar(x);
        }
    }

    public void write_short(short x)
    {
        alignAndReserve(2, 2);

        if (littleEndian) {
            writeLittleEndianShort(x);
        } else {
            writeBigEndianShort(x);
        }
    }

    public final void write_ushort(short x)
    {
        write_short(x);
    }

    public void write_long(int x)
    {
        alignAndReserve(4, 4);

        if (littleEndian) {
            writeLittleEndianLong(x);
        } else {
            writeBigEndianLong(x);
        }
    }

    public final void write_ulong(int x)
    {
        write_long(x);
    }

    public void write_longlong(long x)
    {
        alignAndReserve(8, 8);

        if (littleEndian) {
            writeLittleEndianLongLong(x);
        } else {
            writeBigEndianLongLong(x);
        }
    }

    public final void write_ulonglong(long x)
    {
        write_longlong(x);
    }

    public final void write_float(float x)
    {
        write_long(Float.floatToIntBits(x));
    }

    public final void write_double(double x)
    {
        write_longlong(Double.doubleToLongBits(x));
    }

    public void write_string(String value)
    {
      writeString(value);
    }

    protected int writeString(String value)
    {
        if (value == null) {
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);
        }

        CodeSetConversion.CTBConverter converter = getCharConverter();

        converter.convert(value);

        // A string is encoded as an unsigned CORBA long for the
        // number of bytes to follow (including a terminating null).
        // There is only one octet per character in the string.
        int len = converter.getNumBytes() + 1;

        handleSpecialChunkBegin(computeAlignment(4) + 4 + len);

        write_long(len);
        int indirection = get_offset() - 4;

        internalWriteOctetArray(converter.getBytes(), 0, converter.getNumBytes());

        // Write the null ending
        write_octet((byte)0);

        handleSpecialChunkEnd();
        return indirection;
    }

    public void write_wstring(String value)
    {
        if (value == null)
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);

        // Don't allow transmission of wchar/wstring data with
        // foreign ORBs since it's against the spec.
        if (ORBUtility.isForeignORB(orb)) {
            throw wrapper.wcharDataInGiop10(CompletionStatus.COMPLETED_MAYBE);
        }

        // When talking to our legacy ORBs, do what they did:
        int len = value.length() + 1;

        // This will only have an effect if we're already chunking
        handleSpecialChunkBegin(4 + (len * 2) + computeAlignment(4));

        write_long(len);

        for (int i = 0; i < len - 1; i++)
            write_wchar(value.charAt(i));

        // Write the null ending
        write_short((short)0);

        // This will only have an effect if we're already chunking
        handleSpecialChunkEnd();
    }

    // Performs no checks and doesn't tamper with chunking
    void internalWriteOctetArray(byte[] value, int offset, int length)
    {
        int n = offset;

        // This flag forces the alignAndReserve method to be called the
        // first time an octet is written. This is necessary to ensure
        // that the body is aligned on an 8-octet boundary. Note the 'if'
        // condition inside the 'while' loop below. Also, refer to the
        // write_octet() method that has a similar change.
        boolean align = true;

        while (n < length+offset) {
            int avail;
            int bytes;
            int wanted;

            if ((bbwi.position() + 1 > bbwi.buflen) || align) {
                align = false;
                alignAndReserve(1, 1);
            }
            avail = bbwi.buflen - bbwi.position();
            wanted = (length + offset) - n;
            bytes = (wanted < avail) ? wanted : avail;
            for (int i = 0; i < bytes; i++)
                bbwi.byteBuffer.put(bbwi.position() + i, value[n+i]);
            bbwi.position(bbwi.position() + bytes);
            n += bytes;
        }
    }

    public final void write_octet_array(byte b[], int offset, int length)
    {
        if ( b == null )
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);

        // This will only have an effect if we're already chunking
        handleSpecialChunkBegin(length);

        internalWriteOctetArray(b, offset, length);

        // This will only have an effect if we're already chunking
        handleSpecialChunkEnd();
    }

    public void write_Principal(Principal p)
    {
        write_long(p.name().length);
        write_octet_array(p.name(), 0, p.name().length);
    }

    public void write_any(Any any)
    {
        if ( any == null )
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);

        write_TypeCode(any.type());
        any.write_value(parent);
    }

    public void write_TypeCode(TypeCode tc)
    {
        if ( tc == null ) {
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);
        }
        TypeCodeImpl tci;
        if (tc instanceof TypeCodeImpl) {
            tci = (TypeCodeImpl)tc;
        }
        else {
            tci = new TypeCodeImpl(orb, tc);
        }

        tci.write_value((org.omg.CORBA_2_3.portable.OutputStream)parent);
    }

    public void write_Object(org.omg.CORBA.Object ref)
    {
        if (ref == null) {
            IOR nullIOR = IORFactories.makeIOR( orb ) ;
            nullIOR.write(parent);
            return;
        }

        // IDL to Java formal 01-06-06 1.21.4.2
        if (ref instanceof org.omg.CORBA.LocalObject)
            throw wrapper.writeLocalObject(CompletionStatus.COMPLETED_MAYBE);

        IOR ior = ORBUtility.connectAndGetIOR( orb, ref ) ;
        ior.write(parent);
        return;
    }

    // ------------ RMI related methods --------------------------

    public void write_abstract_interface(java.lang.Object obj) {
        boolean corbaObject = false; // Assume value type.
        org.omg.CORBA.Object theObject = null;

        // Is it a CORBA.Object?

        if (obj != null && obj instanceof org.omg.CORBA.Object) {

            // Yes.

            theObject = (org.omg.CORBA.Object)obj;
            corbaObject = true;
        }

        // Write our flag...

        write_boolean(corbaObject);

        // Now write out the object...

        if (corbaObject) {
            write_Object(theObject);
        } else {
            try {
                write_value((java.io.Serializable)obj);
            } catch(ClassCastException cce) {
                if (obj instanceof java.io.Serializable)
                    throw cce;
                else
                    ORBUtility.throwNotSerializableForCorba(obj.getClass().getName());
            }
        }
    }

    public void write_value(Serializable object, Class clz) {

        write_value(object);
    }

    private void writeWStringValue(String string) {

        int indirection = writeValueTag(mustChunk, true, null);

        // Write WStringValue's repository ID
        write_repositoryId(repIdStrs.getWStringValueRepId());

        // Add indirection for object to indirection table
        updateIndirectionTable(indirection, string, string);

        // Write Value chunk
        if (mustChunk) {
            start_block();
            end_flag--;
            chunkedValueNestingLevel--;
        } else
            end_flag--;

        write_wstring(string);

        if (mustChunk)
            end_block();

        // Write end tag
        writeEndTag(mustChunk);
    }

    private void writeArray(Serializable array, Class clazz) {

        if (valueHandler == null)
            valueHandler = ORBUtility.createValueHandler(); //d11638

        // Write value_tag
        int indirection = writeValueTag(mustChunk, true,
                                        Util.getCodebase(clazz));

        // Write repository ID
        write_repositoryId(repIdStrs.createSequenceRepID(clazz));

        // Add indirection for object to indirection table
        updateIndirectionTable(indirection, array, array);

        // Write Value chunk
        if (mustChunk) {
            start_block();
            end_flag--;
            chunkedValueNestingLevel--;
        } else
            end_flag--;

        if (valueHandler instanceof ValueHandlerMultiFormat) {
            ValueHandlerMultiFormat vh = (ValueHandlerMultiFormat)valueHandler;
            vh.writeValue(parent, array, streamFormatVersion);
        } else
            valueHandler.writeValue(parent, array);

        if (mustChunk)
            end_block();

        // Write end tag
        writeEndTag(mustChunk);
    }

    private void writeValueBase(org.omg.CORBA.portable.ValueBase object,
                                Class clazz) {
        // _REVISIT_ could check to see whether chunking really needed
        mustChunk = true;

        // Write value_tag
        int indirection = writeValueTag(true, true, Util.getCodebase(clazz));

        // Get rep id
        String repId = ((ValueBase)object)._truncatable_ids()[0];

        // Write rep id
        write_repositoryId(repId);

        // Add indirection for object to indirection table
        updateIndirectionTable(indirection, object, object);

        // Write Value chunk
        start_block();
        end_flag--;
        chunkedValueNestingLevel--;
        writeIDLValue(object, repId);
        end_block();

        // Write end tag
        writeEndTag(true);
    }

    private void writeRMIIIOPValueType(Serializable object, Class clazz) {
        if (valueHandler == null)
            valueHandler = ORBUtility.createValueHandler(); //d11638

        Serializable key = object;

        // Allow the ValueHandler to call writeReplace on
        // the Serializable (if the method is present)
        object = valueHandler.writeReplace(key);

        if (object == null) {
            // Write null tag and return
            write_long(0);
            return;
        }

        if (object != key) {
            if (valueCache != null && valueCache.containsKey(object)) {
                writeIndirection(INDIRECTION_TAG, valueCache.getVal(object));
                return;
            }

            clazz = object.getClass();
        }

        if (mustChunk || valueHandler.isCustomMarshaled(clazz)) {
            mustChunk = true;
        }

        // Write value_tag
        int indirection = writeValueTag(mustChunk, true, Util.getCodebase(clazz));

        // Write rep. id
        write_repositoryId(repIdStrs.createForJavaType(clazz));

        // Add indirection for object to indirection table
        updateIndirectionTable(indirection, object, key);

        if (mustChunk) {
            // Write Value chunk
            end_flag--;
            chunkedValueNestingLevel--;
            start_block();
        } else
            end_flag--;

        if (valueHandler instanceof ValueHandlerMultiFormat) {
            ValueHandlerMultiFormat vh = (ValueHandlerMultiFormat)valueHandler;
            vh.writeValue(parent, object, streamFormatVersion);
        } else
            valueHandler.writeValue(parent, object);

        if (mustChunk)
            end_block();

        // Write end tag
        writeEndTag(mustChunk);
    }

    public void write_value(Serializable object, String repository_id) {

        // Handle null references
        if (object == null) {
            // Write null tag and return
            write_long(0);
            return;
        }

        // Handle shared references
        if (valueCache != null && valueCache.containsKey(object)) {
            writeIndirection(INDIRECTION_TAG, valueCache.getVal(object));
            return;
        }

        Class clazz = object.getClass();
        boolean oldMustChunk = mustChunk;

        if (mustChunk)
            mustChunk = true;

        if (inBlock)
            end_block();

        if (clazz.isArray()) {
            // Handle arrays
            writeArray(object, clazz);
        } else if (object instanceof org.omg.CORBA.portable.ValueBase) {
            // Handle IDL Value types
            writeValueBase((org.omg.CORBA.portable.ValueBase)object, clazz);
        } else if (shouldWriteAsIDLEntity(object)) {
            writeIDLEntity((IDLEntity)object);
        } else if (object instanceof java.lang.String) {
            writeWStringValue((String)object);
        } else if (object instanceof java.lang.Class) {
            writeClass(repository_id, (Class)object);
        } else {
            // RMI-IIOP value type
            writeRMIIIOPValueType(object, clazz);
        }

        mustChunk = oldMustChunk;

        // Check to see if we need to start another block for a
        // possible outer value
        if (mustChunk)
            start_block();

    }

    public void write_value(Serializable object)
    {
        write_value(object, (String)null);
    }

    public void write_value(Serializable object, org.omg.CORBA.portable.BoxedValueHelper factory)
    {
        // Handle null references
        if (object == null) {
            // Write null tag and return
            write_long(0);
            return;
        }

        // Handle shared references
        if ((valueCache != null) && valueCache.containsKey(object)) {
            writeIndirection(INDIRECTION_TAG, valueCache.getVal(object));
            return;
        }

        boolean oldMustChunk = mustChunk;

        boolean isCustom = false;
        if (factory instanceof ValueHelper) {
            short modifier;
            try {
                modifier = ((ValueHelper)factory).get_type().type_modifier();
            } catch(BadKind ex) {  // tk_value_box
                modifier = VM_NONE.value;
            }
            if (object instanceof CustomMarshal &&
                modifier == VM_CUSTOM.value) {
                isCustom = true;
                mustChunk = true;
            }
            if (modifier == VM_TRUNCATABLE.value)
                mustChunk = true;
        }

        if (mustChunk) {

            if (inBlock)
                end_block();

            // Write value_tag
            int indirection = writeValueTag(true,
                                            orb.getORBData().useRepId(),
                                            Util.getCodebase(object.getClass())
                                           );

            if (orb.getORBData().useRepId()) {
                write_repositoryId(factory.get_id());
            }

            // Add indirection for object to indirection table
            updateIndirectionTable(indirection, object, object);

            // Write Value chunk
            start_block();
            end_flag--;
            chunkedValueNestingLevel--;
            if (isCustom)
                ((CustomMarshal)object).marshal(parent);
            else
                factory.write_value(parent, object);
            end_block();

            // Write end tag
            writeEndTag(true);
        }
        else {
            // Write value_tag
            int indirection = writeValueTag(false,
                                            orb.getORBData().useRepId(),
                                            Util.getCodebase(object.getClass())
                                           );

            if (orb.getORBData().useRepId()) {
                write_repositoryId(factory.get_id());
            }

            // Add indirection for object to indirection table
            updateIndirectionTable(indirection, object, object);

            // Write Value chunk
            end_flag--;
            // no need to test for custom on the non-chunked path
            factory.write_value(parent, object);

            // Write end tag
            writeEndTag(false);
        }

        mustChunk = oldMustChunk;

        // Check to see if we need to start another block for a
        // possible outer value
        if (mustChunk)
            start_block();

    }

    public int get_offset() {
        return bbwi.position();
    }

    public void start_block() {
        if (debug) {
            dprint("CDROutputStream_1_0 start_block, position" + bbwi.position());
        }

        //Move inBlock=true to after write_long since write_long might
        //trigger grow which will lead to erroneous behavior with a
        //missing blockSizeIndex.
        //inBlock = true;

        // Save space in the buffer for block size
        write_long(0);

        //Has to happen after write_long since write_long could
        //trigger grow which is overridden by supper classes to
        //depend on inBlock.
        inBlock = true;

        blockSizePosition = get_offset();

        // Remember where to put the size of the endblock less 4
        blockSizeIndex = bbwi.position();

        if (debug) {
            dprint("CDROutputStream_1_0 start_block, blockSizeIndex "
                   + blockSizeIndex);
        }

    }

    // Utility method which will hopefully decrease chunking complexity
    // by allowing us to end_block and update chunk lengths without
    // calling alignAndReserve.  Otherwise, it's possible to get into
    // recursive scenarios which lose the chunking state.
    protected void writeLongWithoutAlign(int x) {
        if (littleEndian) {
            writeLittleEndianLong(x);
        } else {
            writeBigEndianLong(x);
        }
    }

    public void end_block() {
        if (debug) {
            dprint("CDROutputStream_1_0.java end_block");
        }

        if (!inBlock)
            return;

        if (debug) {
            dprint("CDROutputStream_1_0.java end_block, in a block");
        }

        inBlock = false;

        // Test to see if the block was of zero length
        // If so, remove the block instead of ending it
        // (This can happen if the last field written
        //  in a value was another value)
        if (get_offset() == blockSizePosition) {
            // Need to assert that blockSizeIndex == bbwi.position()?  REVISIT

            bbwi.position(bbwi.position() - 4);
            blockSizeIndex = -1;
            blockSizePosition = -1;
            return;
        }

        int oldSize = bbwi.position();
        bbwi.position(blockSizeIndex - 4);

        writeLongWithoutAlign(oldSize - blockSizeIndex);

        bbwi.position(oldSize);
        blockSizeIndex = -1;
        blockSizePosition = -1;

        // System.out.println("      post end_block: " + get_offset() + " " + bbwi.position());
    }

    public org.omg.CORBA.ORB orb()
    {
        return orb;
    }

    // ------------ End RMI related methods --------------------------

    public final void write_boolean_array(boolean[]value, int offset, int length) {
        if ( value == null )
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);

        // This will only have an effect if we're already chunking
        handleSpecialChunkBegin(length);

        for (int i = 0; i < length; i++)
            write_boolean(value[offset + i]);

        // This will only have an effect if we're already chunking
        handleSpecialChunkEnd();
    }

    public final void write_char_array(char[]value, int offset, int length) {
        if ( value == null )
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);

        // This will only have an effect if we're already chunking
        handleSpecialChunkBegin(length);

        for (int i = 0; i < length; i++)
            write_char(value[offset + i]);

        // This will only have an effect if we're already chunking
        handleSpecialChunkEnd();
    }

    public void write_wchar_array(char[]value, int offset, int length) {
        if ( value == null )
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);

        // This will only have an effect if we're already chunking
        handleSpecialChunkBegin(computeAlignment(2) + (length * 2));

        for (int i = 0; i < length; i++)
            write_wchar(value[offset + i]);

        // This will only have an effect if we're already chunking
        handleSpecialChunkEnd();
    }

    public final void write_short_array(short[]value, int offset, int length) {
        if ( value == null )
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);

        // This will only have an effect if we're already chunking
        handleSpecialChunkBegin(computeAlignment(2) + (length * 2));

        for (int i = 0; i < length; i++)
            write_short(value[offset + i]);

        // This will only have an effect if we're already chunking
        handleSpecialChunkEnd();
    }

    public final void write_ushort_array(short[]value, int offset, int length) {
        write_short_array(value, offset, length);
    }

    public final void write_long_array(int[]value, int offset, int length) {
        if ( value == null )
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);

        // This will only have an effect if we're already chunking
        handleSpecialChunkBegin(computeAlignment(4) + (length * 4));

        for (int i = 0; i < length; i++)
            write_long(value[offset + i]);

        // This will only have an effect if we're already chunking
        handleSpecialChunkEnd();
    }

    public final void write_ulong_array(int[]value, int offset, int length) {
        write_long_array(value, offset, length);
    }

    public final void write_longlong_array(long[]value, int offset, int length) {
        if ( value == null )
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);

        // This will only have an effect if we're already chunking
        handleSpecialChunkBegin(computeAlignment(8) + (length * 8));

        for (int i = 0; i < length; i++)
            write_longlong(value[offset + i]);

        // This will only have an effect if we're already chunking
        handleSpecialChunkEnd();
    }

    public final void write_ulonglong_array(long[]value, int offset, int length) {
        write_longlong_array(value, offset, length);
    }

    public final void write_float_array(float[]value, int offset, int length) {
        if ( value == null )
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);

        // This will only have an effect if we're already chunking
        handleSpecialChunkBegin(computeAlignment(4) + (length * 4));

        for (int i = 0; i < length; i++)
            write_float(value[offset + i]);

        // This will only have an effect if we're already chunking
        handleSpecialChunkEnd();
    }

    public final void write_double_array(double[]value, int offset, int length) {
        if ( value == null )
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);

        // This will only have an effect if we're already chunking
        handleSpecialChunkBegin(computeAlignment(8) + (length * 8));

        for (int i = 0; i < length; i++)
            write_double(value[offset + i]);

        // This will only have an effect if we're already chunking
        handleSpecialChunkEnd();
    }

    public void write_string_array(String[] value, int offset, int length) {
        if ( value == null )
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);

        for(int i = 0; i < length; i++)
            write_string(value[offset + i]);
    }

    public void write_wstring_array(String[] value, int offset, int length) {
        if ( value == null )
            throw wrapper.nullParam(CompletionStatus.COMPLETED_MAYBE);

        for(int i = 0; i < length; i++)
            write_wstring(value[offset + i]);
    }

    public final void write_any_array(org.omg.CORBA.Any value[], int offset, int length)
    {
        for(int i = 0; i < length; i++)
            write_any(value[offset + i]);
    }

    //--------------------------------------------------------------------//
    // CDROutputStream state management.
    //

    public void writeTo(java.io.OutputStream s)
        throws java.io.IOException
    {
        byte[] tmpBuf = null;

        if (bbwi.byteBuffer.hasArray())
        {
            tmpBuf = bbwi.byteBuffer.array();
        }
        else
        {
            int size = bbwi.position();
            tmpBuf = new byte[size];
            // Micro-benchmarks are showing a loop of ByteBuffer.get(int) is
            // faster than ByteBuffer.get(byte[], offset, length)
            for (int i = 0; i < size; i++)
                tmpBuf[i] = bbwi.byteBuffer.get(i);
        }

        s.write(tmpBuf, 0, bbwi.position());
    }

    public void writeOctetSequenceTo(org.omg.CORBA.portable.OutputStream s) {

        byte[] buf = null;

        if (bbwi.byteBuffer.hasArray())
        {
            buf = bbwi.byteBuffer.array();
        }
        else
        {
            int size = bbwi.position();
            buf = new byte[size];
            // Micro-benchmarks are showing a loop of ByteBuffer.get(int) is
            // faster than ByteBuffer.get(byte[], offset, length)
            for (int i = 0; i < size; i++)
                buf[i] = bbwi.byteBuffer.get(i);
        }

        s.write_long(bbwi.position());
        s.write_octet_array(buf, 0, bbwi.position());

    }

    public final int getSize() {
        return bbwi.position();
    }

    public int getIndex() {
        return bbwi.position();
    }

    public boolean isLittleEndian() {
        return littleEndian;
    }

    public void setIndex(int value) {
        bbwi.position(value);
    }

    public ByteBufferWithInfo getByteBufferWithInfo() {
        return bbwi;
    }

    public void setByteBufferWithInfo(ByteBufferWithInfo bbwi) {
        this.bbwi = bbwi;
    }

    public ByteBuffer getByteBuffer() {
        ByteBuffer result = null;;
        if (bbwi != null) {
            result = bbwi.byteBuffer;
        }
        return result;
    }

    public void setByteBuffer(ByteBuffer byteBuffer) {
        bbwi.byteBuffer = byteBuffer;
    }

    private final void updateIndirectionTable(int indirection, java.lang.Object object,
                                              java.lang.Object key) {
        // int indirection = get_offset();
        if (valueCache == null)
            valueCache = new CacheTable(orb,true);
        valueCache.put(object, indirection);
        if (key != object)
            valueCache.put(key, indirection);
    }

    private final void write_repositoryId(String id) {
        // Use an indirection if available
        if (repositoryIdCache != null && repositoryIdCache.containsKey(id)) {
            writeIndirection(INDIRECTION_TAG, repositoryIdCache.getVal(id));
            return;
        }

        // Write it as a string.  Note that we have already done the
        // special case conversion of non-Latin-1 characters to escaped
        // Latin-1 sequences in RepositoryId.

        // It's not a good idea to cache them now that we can have
        // multiple code sets.
        int indirection = writeString(id);

        // Add indirection for id to indirection table
        if (repositoryIdCache == null)
        repositoryIdCache = new CacheTable(orb,true);
        repositoryIdCache.put(id, indirection);
    }

    private void write_codebase(String str, int pos) {
        if (codebaseCache != null && codebaseCache.containsKey(str)) {
            writeIndirection(INDIRECTION_TAG, codebaseCache.getVal(str));
        }
        else {
            write_string(str);
            if (codebaseCache == null)
                codebaseCache = new CacheTable(orb,true);
            codebaseCache.put(str, pos);
        }
    }

    private final int writeValueTag(boolean chunkIt, boolean useRepId,
                                    String codebase) {
        int indirection = 0;
        if (chunkIt && !useRepId){
            if (codebase == null) {
                write_long(repIdUtil.getStandardRMIChunkedNoRepStrId());
                indirection = get_offset() - 4;
            } else {
                write_long(repIdUtil.getCodeBaseRMIChunkedNoRepStrId());
                indirection = get_offset() - 4;
                write_codebase(codebase, get_offset());
            }
        } else if (chunkIt && useRepId){
            if (codebase == null) {
                write_long(repIdUtil.getStandardRMIChunkedId());
                indirection = get_offset() - 4;
            } else {
                write_long(repIdUtil.getCodeBaseRMIChunkedId());
                indirection = get_offset() - 4;
                write_codebase(codebase, get_offset());
            }
        } else if (!chunkIt && !useRepId) {
            if (codebase == null) {
                write_long(repIdUtil.getStandardRMIUnchunkedNoRepStrId());
                indirection = get_offset() - 4;
            } else {
                write_long(repIdUtil.getCodeBaseRMIUnchunkedNoRepStrId());
                indirection = get_offset() - 4;
                write_codebase(codebase, get_offset());
            }
        } else if (!chunkIt && useRepId) {
            if (codebase == null) {
                write_long(repIdUtil.getStandardRMIUnchunkedId());
                indirection = get_offset() - 4;
            } else {
                write_long(repIdUtil.getCodeBaseRMIUnchunkedId());
                indirection = get_offset() - 4;
                write_codebase(codebase, get_offset());
            }
        }
        return indirection;
    }

    private void writeIDLValue(Serializable object, String repID)
    {
        if (object instanceof StreamableValue) {
            ((StreamableValue)object)._write(parent);

        } else if (object instanceof CustomValue) {
            ((CustomValue)object).marshal(parent);

        } else {
            BoxedValueHelper helper = Utility.getHelper(object.getClass(), null, repID);
            boolean isCustom = false;
            if (helper instanceof ValueHelper && object instanceof CustomMarshal) {
                try {
                    if (((ValueHelper)helper).get_type().type_modifier() == VM_CUSTOM.value)
                        isCustom = true;
                } catch(BadKind ex) {
                    throw wrapper.badTypecodeForCustomValue( CompletionStatus.COMPLETED_MAYBE,
                        ex ) ;
                }
            }
            if (isCustom)
                ((CustomMarshal)object).marshal(parent);
            else
                helper.write_value(parent, object);
        }
    }

    // Handles end tag compaction...
    private void writeEndTag(boolean chunked){

        if (chunked) {
            if (get_offset() == end_flag_position) {

                if (bbwi.position() == end_flag_index) {

                    // We are exactly at the same position and index as the
                    // end of the last end tag.  Thus, we can back up over it
                    // and compact the tags.
                    bbwi.position(bbwi.position() - 4);

                } else {

                    // Special case in which we're at the beginning of a new
                    // fragment, but the position is the same.  We can't back up,
                    // so we just write the new end tag without compaction.  This
                    // occurs when a value ends and calls start_block to open a
                    // continuation chunk, but it's called at the very end of
                    // a fragment.
                }
            }

            writeNestingLevel();

            // Remember the last index and position.  These are only used when chunking.
            end_flag_index = bbwi.position();
            end_flag_position = get_offset();

            chunkedValueNestingLevel++;
        }

        // Increment the nesting level
        end_flag++;
    }

    /**
     * Handles ORB versioning of the end tag.  Should only
     * be called if chunking.
     *
     * If talking to our older ORBs (Standard Extension,
     * Kestrel, and Ladybird), write the end flag that takes
     * into account all enclosing valuetypes.
     *
     * If talking a newer or foreign ORB, or if the orb
     * instance is null, write the end flag that only takes
     * into account the enclosing chunked valuetypes.
     */
    private void writeNestingLevel() {
        if (orb == null ||
            ORBVersionFactory.getFOREIGN().equals(orb.getORBVersion()) ||
            ORBVersionFactory.getNEWER().compareTo(orb.getORBVersion()) <= 0) {

            write_long(chunkedValueNestingLevel);

        } else {
            write_long(end_flag);
        }
    }

    private void writeClass(String repository_id, Class clz) {

        if (repository_id == null)
            repository_id = repIdStrs.getClassDescValueRepId();

        // Write value_tag
        int indirection = writeValueTag(mustChunk, true, null);
        updateIndirectionTable(indirection, clz, clz);

        write_repositoryId(repository_id);

        if (mustChunk) {
            // Write Value chunk
            start_block();
            end_flag--;
            chunkedValueNestingLevel--;
        } else
            end_flag--;

        writeClassBody(clz);

        if (mustChunk)
            end_block();

        // Write end tag
        writeEndTag(mustChunk);
    }

    // Pre-Merlin/J2EE 1.3 ORBs wrote the repository ID
    // and codebase strings in the wrong order.  This handles
    // backwards compatibility.
    private void writeClassBody(Class clz) {
        if (orb == null ||
            ORBVersionFactory.getFOREIGN().equals(orb.getORBVersion()) ||
            ORBVersionFactory.getNEWER().compareTo(orb.getORBVersion()) <= 0) {

            write_value(Util.getCodebase(clz));
            write_value(repIdStrs.createForAnyType(clz));
        } else {

            write_value(repIdStrs.createForAnyType(clz));
            write_value(Util.getCodebase(clz));
        }
    }

    // Casts and returns an Object as a Serializable
    // This is required for JDK 1.1 only to avoid VerifyErrors when
    // passing arrays as Serializable
    // private java.io.Serializable make_serializable(java.lang.Object object)
    // {
    //  return (java.io.Serializable)object;
    // }

    private boolean shouldWriteAsIDLEntity(Serializable object)
    {
        return ((object instanceof IDLEntity) && (!(object instanceof ValueBase)) &&
                (!(object instanceof org.omg.CORBA.Object)));

    }

    private void writeIDLEntity(IDLEntity object) {

        // _REVISIT_ could check to see whether chunking really needed
        mustChunk = true;

        String repository_id = repIdStrs.createForJavaType(object);
        Class clazz = object.getClass();
        String codebase = Util.getCodebase(clazz);

        // Write value_tag
        int indirection = writeValueTag(true, true, codebase);
        updateIndirectionTable(indirection, object, object);

        // Write rep. id
        write_repositoryId(repository_id);

        // Write Value chunk
        end_flag--;
        chunkedValueNestingLevel--;
        start_block();

        // Write the IDLEntity using reflection
        try {
            ClassLoader clazzLoader = (clazz == null ? null : clazz.getClassLoader());
            final Class helperClass = Utility.loadClassForClass(clazz.getName()+"Helper", codebase,
                                                   clazzLoader, clazz, clazzLoader);
            final Class argTypes[] = {org.omg.CORBA.portable.OutputStream.class, clazz};
            // getDeclaredMethod requires RuntimePermission accessDeclaredMembers
            // if a different class loader is used (even though the javadoc says otherwise)
            Method writeMethod = null;
            try {
                writeMethod = (Method)AccessController.doPrivileged(
                    new PrivilegedExceptionAction() {
                        public java.lang.Object run() throws NoSuchMethodException {
                            return helperClass.getDeclaredMethod(kWriteMethod, argTypes);
                        }
                    }
                );
            } catch (PrivilegedActionException pae) {
                // this gets caught below
                throw (NoSuchMethodException)pae.getException();
            }
            java.lang.Object args[] = {parent, object};
            writeMethod.invoke(null, args);
        } catch (ClassNotFoundException cnfe) {
            throw wrapper.errorInvokingHelperWrite( CompletionStatus.COMPLETED_MAYBE, cnfe ) ;
        } catch(NoSuchMethodException nsme) {
            throw wrapper.errorInvokingHelperWrite( CompletionStatus.COMPLETED_MAYBE, nsme ) ;
        } catch(IllegalAccessException iae) {
            throw wrapper.errorInvokingHelperWrite( CompletionStatus.COMPLETED_MAYBE, iae ) ;
        } catch(InvocationTargetException ite) {
            throw wrapper.errorInvokingHelperWrite( CompletionStatus.COMPLETED_MAYBE, ite ) ;
        }
        end_block();

        // Write end tag
        writeEndTag(true);
    }

    /* DataOutputStream methods */

    public void write_Abstract (java.lang.Object value) {
        write_abstract_interface(value);
    }

    public void write_Value (java.io.Serializable value) {
        write_value(value);
    }

    // This will stay a custom add-on until the java-rtf issue is resolved.
    // Then it should be declared in org.omg.CORBA.portable.OutputStream.
    //
    // Pads the string representation of bigDecimal with zeros to fit the given
    // digits and scale before it gets written to the stream.
    public void write_fixed(java.math.BigDecimal bigDecimal, short digits, short scale) {
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

    // This method should be remove by the java-rtf issue.
    // Right now the scale and digits information of the type code is lost.
    public void write_fixed(java.math.BigDecimal bigDecimal) {
        // This string might contain sign and/or dot
        this.write_fixed(bigDecimal.toString(), bigDecimal.signum());
    }

    // The string may contain a sign and dot
    public void write_fixed(String string, int signum) {
        int stringLength = string.length();
        // Each octet contains (up to) two decimal digits
        byte doubleDigit = 0;
        char ch;
        byte digit;

        // First calculate the length of the string without optional sign and dot
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
                throw wrapper.badDigitInFixed( CompletionStatus.COMPLETED_MAYBE ) ;
            }
            // If the fixed type has an odd number of decimal digits,
            // then the representation begins with the first (most significant) digit.
            // Otherwise, this first half-octet is all zero, and the first digit
            // is in the second half-octet.
            if (numDigits % 2 == 0) {
                doubleDigit |= digit;
                this.write_octet(doubleDigit);
                doubleDigit = 0;
            } else {
                doubleDigit |= (digit << 4);
            }
            numDigits--;
        }
        // The sign configuration, in the last half-octet of the representation,
        // is 0xD for negative numbers and 0xC for positive and zero values
        if (signum == -1) {
            doubleDigit |= 0xd;
        } else {
            doubleDigit |= 0xc;
        }
        this.write_octet(doubleDigit);
    }

    private final static String _id = "IDL:omg.org/CORBA/DataOutputStream:1.0";
    private final static String[] _ids = { _id };

    public String[] _truncatable_ids() {
        if (_ids == null)
            return null;

        return (String[])_ids.clone();
    }

    /* for debugging */

    public void printBuffer() {
        CDROutputStream_1_0.printBuffer(this.bbwi);
    }

    public static void printBuffer(ByteBufferWithInfo bbwi) {

        System.out.println("+++++++ Output Buffer ++++++++");
        System.out.println();
        System.out.println("Current position: " + bbwi.position());
        System.out.println("Total length : " + bbwi.buflen);
        System.out.println();

        char[] charBuf = new char[16];

        try {

            for (int i = 0; i < bbwi.position(); i += 16) {

                int j = 0;

                // For every 16 bytes, there is one line
                // of output.  First, the hex output of
                // the 16 bytes with each byte separated
                // by a space.
                while (j < 16 && j + i < bbwi.position()) {
                    int k = bbwi.byteBuffer.get(i + j);
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

                while (x < 16 && x + i < bbwi.position()) {
                    if (ORBUtility.isPrintable((char)bbwi.byteBuffer.get(i + x)))
                        charBuf[x] = (char)bbwi.byteBuffer.get(i + x);
                    else
                        charBuf[x] = '.';
                    x++;
                }
                System.out.println(new String(charBuf, 0, x));
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.out.println("++++++++++++++++++++++++++++++");
    }

    public void writeIndirection(int tag, int posIndirectedTo)
    {
        // Must ensure that there are no chunks between the tag
        // and the actual indirection value.  This isn't talked about
        // in the spec, but seems to cause headaches in our code.
        // At the very least, this method isolates the indirection code
        // that was duplicated so often.

        handleSpecialChunkBegin(computeAlignment(4) + 8);

        // write indirection tag
        write_long(tag);

        // write indirection
        // Use parent.getRealIndex() so that it can be overridden by TypeCodeOutputStreams
/*
        System.out.println("CDROutputStream_1_0 writing indirection pos " + posIndirectedTo +
                           " - real index " + parent.getRealIndex(get_offset()) + " = " +
                           (posIndirectedTo - parent.getRealIndex(get_offset())));
*/
        write_long(posIndirectedTo - parent.getRealIndex(get_offset()));

        handleSpecialChunkEnd();
    }

    protected CodeSetConversion.CTBConverter getCharConverter() {
        if (charConverter == null)
            charConverter = parent.createCharCTBConverter();

        return charConverter;
    }

    protected CodeSetConversion.CTBConverter getWCharConverter() {
        if (wcharConverter == null)
            wcharConverter = parent.createWCharCTBConverter();

        return wcharConverter;
    }

    protected void dprint(String msg) {
        if (debug)
            ORBUtility.dprint(this, msg);
    }

    void alignOnBoundary(int octetBoundary) {
        alignAndReserve(octetBoundary, 0);
    }

    public void start_value(String rep_id) {

        if (debug) {
            dprint("start_value w/ rep id "
                   + rep_id
                   + " called at pos "
                   + get_offset()
                   + " position "
                   + bbwi.position());
        }

        if (inBlock)
            end_block();

        // Write value_tag
        writeValueTag(true, true, null);

        // Write rep. id
        write_repositoryId(rep_id);

        // Write Value chunk
        end_flag--;
        chunkedValueNestingLevel--;

        // Make sure to chunk the custom data
        start_block();
    }

    public void end_value() {

        if (debug) {
            dprint("end_value called at pos "
                   + get_offset()
                   + " position "
                   + bbwi.position());
        }

        end_block();

        writeEndTag(true);

        // Check to see if we need to start another block for a
        // possible outer value.  Since we're in the stream
        // format 2 custom type contained by another custom
        // type, mustChunk should always be true.
        //
        // Here's why we need to open a continuation chunk:
        //
        // We need to enclose the default data of the
        // next subclass down in chunks.  There won't be
        // an end tag separating the superclass optional
        // data and the subclass's default data.

        if (debug) {
            dprint("mustChunk is " + mustChunk);
        }

        if (mustChunk) {
            start_block();
        }
    }

    public void close() throws IOException
    {
        // tell BufferManagerWrite to release any ByteBuffers
        getBufferManager().close();

        // It's possible bbwi.byteBuffer is shared between
        // this OutputStream and an InputStream. Thus, we check
        // if the Input/Output streams are using the same ByteBuffer.
        // If they sharing the same ByteBuffer we need to ensure only
        // one of those ByteBuffers are released to the ByteBufferPool.

        if (getByteBufferWithInfo() != null && getByteBuffer() != null)
        {
            MessageMediator messageMediator = parent.getMessageMediator();
            if (messageMediator != null)
            {
                CDRInputObject inputObj =
                               (CDRInputObject)messageMediator.getInputObject();
                if (inputObj != null)
                {
                    if (inputObj.isSharing(getByteBuffer()))
                    {
                        // Set InputStream's ByteBuffer and bbwi to null
                        // so its ByteBuffer cannot be released to the pool
                        inputObj.setByteBuffer(null);
                        inputObj.setByteBufferWithInfo(null);
                    }
                }
            }

            // release this stream's ByteBuffer to the pool
            ByteBufferPool byteBufferPool = orb.getByteBufferPool();
            if (debug)
            {
                // print address of ByteBuffer being released
                int bbAddress = System.identityHashCode(bbwi.byteBuffer);
                StringBuffer sb = new StringBuffer(80);
                sb.append(".close - releasing ByteBuffer id (");
                sb.append(bbAddress).append(") to ByteBufferPool.");
                String msg = sb.toString();
                dprint(msg);
             }
             byteBufferPool.releaseByteBuffer(getByteBuffer());
             bbwi.byteBuffer = null;
             bbwi = null;
        }
    }
}
