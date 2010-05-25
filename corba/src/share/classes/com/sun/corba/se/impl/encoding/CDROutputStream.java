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

import com.sun.corba.se.pept.protocol.MessageMediator;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;

import com.sun.corba.se.impl.encoding.CodeSetConversion;
import com.sun.corba.se.impl.encoding.OSFCodeSetRegistry;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;

/**
 * This is delegates to the real implementation.
 */
public abstract class CDROutputStream
    extends org.omg.CORBA_2_3.portable.OutputStream
    implements com.sun.corba.se.impl.encoding.MarshalOutputStream,
               org.omg.CORBA.DataOutputStream, org.omg.CORBA.portable.ValueOutputStream
{
    private CDROutputStreamBase impl;
    protected ORB orb ;
    protected ORBUtilSystemException wrapper ;
    protected CorbaMessageMediator corbaMessageMediator;


    // We can move this out somewhere later.  For now, it serves its purpose
    // to create a concrete CDR delegate based on the GIOP version.
    private static class OutputStreamFactory {

        public static CDROutputStreamBase newOutputStream(
                ORB orb, GIOPVersion version, byte encodingVersion) {
            switch(version.intValue()) {
                case GIOPVersion.VERSION_1_0:
                    return new CDROutputStream_1_0();
                case GIOPVersion.VERSION_1_1:
                    return new CDROutputStream_1_1();
            case GIOPVersion.VERSION_1_2:
                if (encodingVersion != Message.CDR_ENC_VERSION) {
                    return
                        new IDLJavaSerializationOutputStream(encodingVersion);
                }
                return new CDROutputStream_1_2();
            default:
                    ORBUtilSystemException wrapper = ORBUtilSystemException.get( orb,
                        CORBALogDomains.RPC_ENCODING ) ;
                    // REVISIT - what is appropriate?  INTERNAL exceptions
                    // are really hard to track later.
                    throw wrapper.unsupportedGiopVersion( version ) ;
            }
        }
    }

    // REVISIT - These two constructors should be re-factored to better hide
    // the fact that someone extending this class 'can' construct a CDROutputStream
    // that does not use pooled ByteBuffers. Right now, only EncapsOutputStream
    // does _not_ use pooled ByteBuffers, see EncapsOutputStream.

    // NOTE: When a stream is constructed for non-channel-backed sockets
    // it notifies the constructor not to use pooled (i.e, direct)
    // ByteBuffers.

    public CDROutputStream(ORB orb,
                           GIOPVersion version,
                           byte encodingVersion,
                           boolean littleEndian,
                           BufferManagerWrite bufferManager,
                           byte streamFormatVersion,
                           boolean usePooledByteBuffers)
    {
        impl = OutputStreamFactory.newOutputStream(orb, version,
                                                   encodingVersion);
        impl.init(orb, littleEndian, bufferManager,
                  streamFormatVersion, usePooledByteBuffers);

        impl.setParent(this);
        this.orb = orb ;
        this.wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_ENCODING ) ;
    }

    public CDROutputStream(ORB orb,
                           GIOPVersion version,
                           byte encodingVersion,
                           boolean littleEndian,
                           BufferManagerWrite bufferManager,
                           byte streamFormatVersion)
    {
        this(orb, version, encodingVersion, littleEndian,
             bufferManager, streamFormatVersion, true);
    }

    // org.omg.CORBA.portable.OutputStream

    // Provided by IIOPOutputStream and EncapsOutputStream
    public abstract org.omg.CORBA.portable.InputStream create_input_stream();

    public final void write_boolean(boolean value) {
        impl.write_boolean(value);
    }
    public final void write_char(char value) {
        impl.write_char(value);
    }
    public final void write_wchar(char value) {
        impl.write_wchar(value);
    }
    public final void write_octet(byte value) {
        impl.write_octet(value);
    }
    public final void write_short(short value) {
        impl.write_short(value);
    }
    public final void write_ushort(short value) {
        impl.write_ushort(value);
    }
    public final void write_long(int value) {
        impl.write_long(value);
    }
    public final void write_ulong(int value) {
        impl.write_ulong(value);
    }
    public final void write_longlong(long value) {
        impl.write_longlong(value);
    }
    public final void write_ulonglong(long value) {
        impl.write_ulonglong(value);
    }
    public final void write_float(float value) {
        impl.write_float(value);
    }
    public final void write_double(double value) {
        impl.write_double(value);
    }
    public final void write_string(String value) {
        impl.write_string(value);
    }
    public final void write_wstring(String value) {
        impl.write_wstring(value);
    }

    public final void write_boolean_array(boolean[] value, int offset, int length) {
        impl.write_boolean_array(value, offset, length);
    }
    public final void write_char_array(char[] value, int offset, int length) {
        impl.write_char_array(value, offset, length);
    }
    public final void write_wchar_array(char[] value, int offset, int length) {
        impl.write_wchar_array(value, offset, length);
    }
    public final void write_octet_array(byte[] value, int offset, int length) {
        impl.write_octet_array(value, offset, length);
    }
    public final void write_short_array(short[] value, int offset, int length) {
        impl.write_short_array(value, offset, length);
    }
    public final void write_ushort_array(short[] value, int offset, int length){
        impl.write_ushort_array(value, offset, length);
    }
    public final void write_long_array(int[] value, int offset, int length) {
        impl.write_long_array(value, offset, length);
    }
    public final void write_ulong_array(int[] value, int offset, int length) {
        impl.write_ulong_array(value, offset, length);
    }
    public final void write_longlong_array(long[] value, int offset, int length) {
        impl.write_longlong_array(value, offset, length);
    }
    public final void write_ulonglong_array(long[] value, int offset,int length) {
        impl.write_ulonglong_array(value, offset, length);
    }
    public final void write_float_array(float[] value, int offset, int length) {
        impl.write_float_array(value, offset, length);
    }
    public final void write_double_array(double[] value, int offset, int length) {
        impl.write_double_array(value, offset, length);
    }
    public final void write_Object(org.omg.CORBA.Object value) {
        impl.write_Object(value);
    }
    public final void write_TypeCode(TypeCode value) {
        impl.write_TypeCode(value);
    }
    public final void write_any(Any value) {
        impl.write_any(value);
    }

    public final void write_Principal(Principal value) {
        impl.write_Principal(value);
    }

    public final void write(int b) throws java.io.IOException {
        impl.write(b);
    }

    public final void write_fixed(java.math.BigDecimal value) {
        impl.write_fixed(value);
    }

    public final void write_Context(org.omg.CORBA.Context ctx,
                              org.omg.CORBA.ContextList contexts) {
        impl.write_Context(ctx, contexts);
    }

    public final org.omg.CORBA.ORB orb() {
        return impl.orb();
    }

    // org.omg.CORBA_2_3.portable.OutputStream
    public final void write_value(java.io.Serializable value) {
        impl.write_value(value);
    }

    public final void write_value(java.io.Serializable value, java.lang.Class clz) {
        impl.write_value(value, clz);
    }

    public final void write_value(java.io.Serializable value, String repository_id) {
        impl.write_value(value, repository_id);
    }

    public final void write_value(java.io.Serializable value,
                            org.omg.CORBA.portable.BoxedValueHelper factory) {
        impl.write_value(value, factory);
    }

    public final void write_abstract_interface(java.lang.Object obj) {
        impl.write_abstract_interface(obj);
    }

    // java.io.OutputStream
    public final void write(byte b[]) throws IOException {
        impl.write(b);
    }

    public final void write(byte b[], int off, int len) throws IOException {
        impl.write(b, off, len);
    }

    public final void flush() throws IOException {
        impl.flush();
    }

    public final void close() throws IOException {
        impl.close();
    }

    // com.sun.corba.se.impl.encoding.MarshalOutputStream
    public final void start_block() {
        impl.start_block();
    }

    public final void end_block() {
        impl.end_block();
    }

    public final void putEndian() {
        impl.putEndian();
    }

    public void writeTo(java.io.OutputStream s)
        throws IOException
    {
        impl.writeTo(s);
    }

    public final byte[] toByteArray() {
        return impl.toByteArray();
    }

    // org.omg.CORBA.DataOutputStream
    public final void write_Abstract (java.lang.Object value) {
        impl.write_Abstract(value);
    }

    public final void write_Value (java.io.Serializable value) {
        impl.write_Value(value);
    }

    public final void write_any_array(org.omg.CORBA.Any[] seq, int offset, int length) {
        impl.write_any_array(seq, offset, length);
    }

    public void setMessageMediator(MessageMediator messageMediator)
    {
        this.corbaMessageMediator = (CorbaMessageMediator) messageMediator;
    }

    public MessageMediator getMessageMediator()
    {
        return corbaMessageMediator;
    }

    // org.omg.CORBA.portable.ValueBase
    public final String[] _truncatable_ids() {
        return impl._truncatable_ids();
    }

    // Other
    protected final int getSize() {
        return impl.getSize();
    }

    protected final int getIndex() {
        return impl.getIndex();
    }

    protected int getRealIndex(int index) {
        // Used in indirections. Overridden by TypeCodeOutputStream.
        return index;
    }

    protected final void setIndex(int value) {
        impl.setIndex(value);
    }

    protected final ByteBuffer getByteBuffer() {
        return impl.getByteBuffer();
    }

    protected final void setByteBuffer(ByteBuffer byteBuffer) {
        impl.setByteBuffer(byteBuffer);
    }

    public final boolean isLittleEndian() {
        return impl.isLittleEndian();
    }

    // XREVISIT - return to final if possible
    // REVISIT - was protected - need access from msgtypes test.
    public ByteBufferWithInfo getByteBufferWithInfo() {
        return impl.getByteBufferWithInfo();
    }

    protected void setByteBufferWithInfo(ByteBufferWithInfo bbwi) {
        impl.setByteBufferWithInfo(bbwi);
    }

    // REVISIT: was protected - but need to access from xgiop.
    public final BufferManagerWrite getBufferManager() {
        return impl.getBufferManager();
    }

    public final void write_fixed(java.math.BigDecimal bigDecimal, short digits, short scale) {
        impl.write_fixed(bigDecimal, digits, scale);
    }

    public final void writeOctetSequenceTo(org.omg.CORBA.portable.OutputStream s) {
        impl.writeOctetSequenceTo(s);
    }

    public final GIOPVersion getGIOPVersion() {
        return impl.getGIOPVersion();
    }

    public final void writeIndirection(int tag, int posIndirectedTo) {
        impl.writeIndirection(tag, posIndirectedTo);
    }

    // Use Latin-1 for GIOP 1.0 or when code set negotiation was not
    // performed.
    protected CodeSetConversion.CTBConverter createCharCTBConverter() {
        return CodeSetConversion.impl().getCTBConverter(OSFCodeSetRegistry.ISO_8859_1);
    }

    // Subclasses must decide what to do here.  It's inconvenient to
    // make the class and this method abstract because of dup().
    protected abstract CodeSetConversion.CTBConverter createWCharCTBConverter();

    protected final void freeInternalCaches() {
        impl.freeInternalCaches();
    }

    void printBuffer() {
        impl.printBuffer();
    }

    public void alignOnBoundary(int octetBoundary) {
        impl.alignOnBoundary(octetBoundary);
    }

    // Needed by request and reply messages for GIOP versions >= 1.2 only.
    public void setHeaderPadding(boolean headerPadding) {
        impl.setHeaderPadding(headerPadding);
    }

    // ValueOutputStream -----------------------------

    public void start_value(String rep_id) {
        impl.start_value(rep_id);
    }

    public void end_value() {
        impl.end_value();
    }
}
