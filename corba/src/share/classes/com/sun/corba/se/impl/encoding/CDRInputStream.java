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

import com.sun.org.omg.SendingContext.CodeBase;

import com.sun.corba.se.pept.protocol.MessageMediator;

import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;

import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.encoding.CodeSetConversion;
import com.sun.corba.se.impl.encoding.OSFCodeSetRegistry;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;

/**
 * This is delegates to the real implementation.
 *
 * NOTE:
 *
 * Before using the stream for valuetype unmarshaling, one must call
 * performORBVersionSpecificInit().
 */
public abstract class CDRInputStream
    extends org.omg.CORBA_2_3.portable.InputStream
    implements com.sun.corba.se.impl.encoding.MarshalInputStream,
               org.omg.CORBA.DataInputStream, org.omg.CORBA.portable.ValueInputStream
{
    protected CorbaMessageMediator messageMediator;
    private CDRInputStreamBase impl;

    // We can move this out somewhere later.  For now, it serves its purpose
    // to create a concrete CDR delegate based on the GIOP version.
    private static class InputStreamFactory {

        public static CDRInputStreamBase newInputStream(
                ORB orb, GIOPVersion version, byte encodingVersion) {
            switch(version.intValue()) {
                case GIOPVersion.VERSION_1_0:
                    return new CDRInputStream_1_0();
                case GIOPVersion.VERSION_1_1:
                    return new CDRInputStream_1_1();
                case GIOPVersion.VERSION_1_2:
                    if (encodingVersion != Message.CDR_ENC_VERSION) {
                        return
                          new IDLJavaSerializationInputStream(encodingVersion);
                    }
                    return new CDRInputStream_1_2();
                    // else fall through and report exception.
                default:
                    ORBUtilSystemException wrapper = ORBUtilSystemException.get( orb,
                        CORBALogDomains.RPC_ENCODING ) ;
                    throw wrapper.unsupportedGiopVersion( version ) ;
            }
        }
    }

    // Required for the case when a ClientResponseImpl is
    // created with a SystemException due to a dead server/closed
    // connection with no warning.  Note that the stream will
    // not be initialized in this case.
    //
    // Probably also required by ServerRequestImpl.
    //
    // REVISIT.
    public CDRInputStream() {
    }

    public CDRInputStream(CDRInputStream is) {
        impl = is.impl.dup();
        impl.setParent(this);
    }

    public CDRInputStream(org.omg.CORBA.ORB orb,
                          ByteBuffer byteBuffer,
                          int size,
                          boolean littleEndian,
                          GIOPVersion version,
                          byte encodingVersion,
                          BufferManagerRead bufMgr)
    {
        impl = InputStreamFactory.newInputStream((ORB)orb, version,
                                                 encodingVersion);

        impl.init(orb, byteBuffer, size, littleEndian, bufMgr);

        impl.setParent(this);
    }

    // org.omg.CORBA.portable.InputStream
    public final boolean read_boolean() {
        return impl.read_boolean();
    }

    public final char read_char() {
        return impl.read_char();
    }

    public final char read_wchar() {
        return impl.read_wchar();
    }

    public final byte read_octet() {
        return impl.read_octet();
    }

    public final short read_short() {
        return impl.read_short();
    }

    public final short read_ushort() {
        return impl.read_ushort();
    }

    public final int read_long() {
        return impl.read_long();
    }

    public final int read_ulong() {
        return impl.read_ulong();
    }

    public final long read_longlong() {
        return impl.read_longlong();
    }

    public final long read_ulonglong() {
        return impl.read_ulonglong();
    }

    public final float read_float() {
        return impl.read_float();
    }

    public final double read_double() {
        return impl.read_double();
    }

    public final String read_string() {
        return impl.read_string();
    }

    public final String read_wstring() {
        return impl.read_wstring();
    }

    public final void read_boolean_array(boolean[] value, int offset, int length) {
        impl.read_boolean_array(value, offset, length);
    }

    public final void read_char_array(char[] value, int offset, int length) {
        impl.read_char_array(value, offset, length);
    }

    public final void read_wchar_array(char[] value, int offset, int length) {
        impl.read_wchar_array(value, offset, length);
    }

    public final void read_octet_array(byte[] value, int offset, int length) {
        impl.read_octet_array(value, offset, length);
    }

    public final void read_short_array(short[] value, int offset, int length) {
        impl.read_short_array(value, offset, length);
    }

    public final void read_ushort_array(short[] value, int offset, int length) {
        impl.read_ushort_array(value, offset, length);
    }

    public final void read_long_array(int[] value, int offset, int length) {
        impl.read_long_array(value, offset, length);
    }

    public final void read_ulong_array(int[] value, int offset, int length) {
        impl.read_ulong_array(value, offset, length);
    }

    public final void read_longlong_array(long[] value, int offset, int length) {
        impl.read_longlong_array(value, offset, length);
    }

    public final void read_ulonglong_array(long[] value, int offset, int length) {
        impl.read_ulonglong_array(value, offset, length);
    }

    public final void read_float_array(float[] value, int offset, int length) {
        impl.read_float_array(value, offset, length);
    }

    public final void read_double_array(double[] value, int offset, int length) {
        impl.read_double_array(value, offset, length);
    }

    public final org.omg.CORBA.Object read_Object() {
        return impl.read_Object();
    }

    public final TypeCode read_TypeCode() {
        return impl.read_TypeCode();
    }
    public final Any read_any() {
        return impl.read_any();
    }

    public final Principal read_Principal() {
        return impl.read_Principal();
    }

    public final int read() throws java.io.IOException {
        return impl.read();
    }

    public final java.math.BigDecimal read_fixed() {
        return impl.read_fixed();
    }

    public final org.omg.CORBA.Context read_Context() {
        return impl.read_Context();
    }

    public final org.omg.CORBA.Object read_Object(java.lang.Class clz) {
        return impl.read_Object(clz);
    }

    public final org.omg.CORBA.ORB orb() {
        return impl.orb();
    }

    // org.omg.CORBA_2_3.portable.InputStream
    public final java.io.Serializable read_value() {
        return impl.read_value();
    }

    public final java.io.Serializable read_value(java.lang.Class clz) {
        return impl.read_value(clz);
    }

    public final java.io.Serializable read_value(org.omg.CORBA.portable.BoxedValueHelper factory) {
        return impl.read_value(factory);
    }

    public final java.io.Serializable read_value(java.lang.String rep_id) {
        return impl.read_value(rep_id);
    }

    public final java.io.Serializable read_value(java.io.Serializable value) {
        return impl.read_value(value);
    }

    public final java.lang.Object read_abstract_interface() {
        return impl.read_abstract_interface();
    }

    public final java.lang.Object read_abstract_interface(java.lang.Class clz) {
        return impl.read_abstract_interface(clz);
    }
    // com.sun.corba.se.impl.encoding.MarshalInputStream

    public final void consumeEndian() {
        impl.consumeEndian();
    }

    public final int getPosition() {
        return impl.getPosition();
    }

    // org.omg.CORBA.DataInputStream

    public final java.lang.Object read_Abstract () {
        return impl.read_Abstract();
    }

    public final java.io.Serializable read_Value () {
        return impl.read_Value();
    }

    public final void read_any_array (org.omg.CORBA.AnySeqHolder seq, int offset, int length) {
        impl.read_any_array(seq, offset, length);
    }

    public final void read_boolean_array (org.omg.CORBA.BooleanSeqHolder seq, int offset, int length) {
        impl.read_boolean_array(seq, offset, length);
    }

    public final void read_char_array (org.omg.CORBA.CharSeqHolder seq, int offset, int length) {
        impl.read_char_array(seq, offset, length);
    }

    public final void read_wchar_array (org.omg.CORBA.WCharSeqHolder seq, int offset, int length) {
        impl.read_wchar_array(seq, offset, length);
    }

    public final void read_octet_array (org.omg.CORBA.OctetSeqHolder seq, int offset, int length) {
        impl.read_octet_array(seq, offset, length);
    }

    public final void read_short_array (org.omg.CORBA.ShortSeqHolder seq, int offset, int length) {
        impl.read_short_array(seq, offset, length);
    }

    public final void read_ushort_array (org.omg.CORBA.UShortSeqHolder seq, int offset, int length) {
        impl.read_ushort_array(seq, offset, length);
    }

    public final void read_long_array (org.omg.CORBA.LongSeqHolder seq, int offset, int length) {
        impl.read_long_array(seq, offset, length);
    }

    public final void read_ulong_array (org.omg.CORBA.ULongSeqHolder seq, int offset, int length) {
        impl.read_ulong_array(seq, offset, length);
    }

    public final void read_ulonglong_array (org.omg.CORBA.ULongLongSeqHolder seq, int offset, int length) {
        impl.read_ulonglong_array(seq, offset, length);
    }

    public final void read_longlong_array (org.omg.CORBA.LongLongSeqHolder seq, int offset, int length) {
        impl.read_longlong_array(seq, offset, length);
    }

    public final void read_float_array (org.omg.CORBA.FloatSeqHolder seq, int offset, int length) {
        impl.read_float_array(seq, offset, length);
    }

    public final void read_double_array (org.omg.CORBA.DoubleSeqHolder seq, int offset, int length) {
        impl.read_double_array(seq, offset, length);
    }

    // org.omg.CORBA.portable.ValueBase
    public final String[] _truncatable_ids() {
        return impl._truncatable_ids();
    }

    // java.io.InputStream
    public final int read(byte b[]) throws IOException {
        return impl.read(b);
    }

    public final int read(byte b[], int off, int len) throws IOException {
        return impl.read(b, off, len);
    }

    public final long skip(long n) throws IOException {
        return impl.skip(n);
    }

    public final int available() throws IOException {
        return impl.available();
    }

    public final void close() throws IOException {
        impl.close();
    }

    public final void mark(int readlimit) {
        impl.mark(readlimit);
    }

    public final void reset() {
        impl.reset();
    }

    public final boolean markSupported() {
        return impl.markSupported();
    }

    public abstract CDRInputStream dup();

    // Needed by TCUtility
    public final java.math.BigDecimal read_fixed(short digits, short scale) {
        return impl.read_fixed(digits, scale);
    }

    public final boolean isLittleEndian() {
        return impl.isLittleEndian();
    }

    protected final ByteBuffer getByteBuffer() {
        return impl.getByteBuffer();
    }

    protected final void setByteBuffer(ByteBuffer byteBuffer) {
        impl.setByteBuffer(byteBuffer);
    }

    protected final void setByteBufferWithInfo(ByteBufferWithInfo bbwi) {
        impl.setByteBufferWithInfo(bbwi);
    }

    public final int getBufferLength() {
        return impl.getBufferLength();
    }

    protected final void setBufferLength(int value) {
        impl.setBufferLength(value);
    }

    protected final int getIndex() {
        return impl.getIndex();
    }

    protected final void setIndex(int value) {
        impl.setIndex(value);
    }

    public final void orb(org.omg.CORBA.ORB orb) {
        impl.orb(orb);
    }

    public final GIOPVersion getGIOPVersion() {
        return impl.getGIOPVersion();
    }

    public final BufferManagerRead getBufferManager() {
        return impl.getBufferManager();
    }

    // This should be overridden by any stream (ex: IIOPInputStream)
    // which wants to read values.  Thus, TypeCodeInputStream doesn't
    // have to do this.
    public CodeBase getCodeBase() {
        return null;
    }

    // Use Latin-1 for GIOP 1.0 or when code set negotiation was not
    // performed.
    protected CodeSetConversion.BTCConverter createCharBTCConverter() {
        return CodeSetConversion.impl().getBTCConverter(OSFCodeSetRegistry.ISO_8859_1,
                                                        impl.isLittleEndian());
    }

    // Subclasses must decide what to do here.  It's inconvenient to
    // make the class and this method abstract because of dup().
    protected abstract CodeSetConversion.BTCConverter createWCharBTCConverter();

    // Prints the current buffer in a human readable form
    void printBuffer() {
        impl.printBuffer();
    }

    /**
     * Aligns the current position on the given octet boundary
     * if there are enough bytes available to do so.  Otherwise,
     * it just returns.  This is used for some (but not all)
     * GIOP 1.2 message headers.
     */
    public void alignOnBoundary(int octetBoundary) {
        impl.alignOnBoundary(octetBoundary);
    }

    // Needed by request and reply messages for GIOP versions >= 1.2 only.
    public void setHeaderPadding(boolean headerPadding) {
        impl.setHeaderPadding(headerPadding);
    }

    /**
     * This must be called after determining the proper ORB version,
     * and setting it on the stream's ORB instance.  It can be called
     * after reading the service contexts, since that is the only place
     * we can get the ORB version info.
     *
     * Trying to unmarshal things requiring repository IDs before calling
     * this will result in NullPtrExceptions.
     */
    public void performORBVersionSpecificInit() {
        // In the case of SystemExceptions, a stream is created
        // with its default constructor (and thus no impl is set).
        if (impl != null)
            impl.performORBVersionSpecificInit();
    }

    /**
     * Resets any internal references to code set converters.
     * This is useful for forcing the CDR stream to reacquire
     * converters (probably from its subclasses) when state
     * has changed.
     */
    public void resetCodeSetConverters() {
        impl.resetCodeSetConverters();
    }

    public void setMessageMediator(MessageMediator messageMediator)
    {
        this.messageMediator = (CorbaMessageMediator) messageMediator;
    }

    public MessageMediator getMessageMediator()
    {
        return messageMediator;
    }

    // ValueInputStream -----------------------------

    public void start_value() {
        impl.start_value();
    }

    public void end_value() {
        impl.end_value();
    }
}
