/*
 * Copyright (c) 2001, 2004, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;

import org.omg.CORBA.Any;
import org.omg.CORBA.Principal;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.portable.InputStream;

import com.sun.corba.se.pept.encoding.OutputObject;
import com.sun.corba.se.pept.protocol.MessageMediator;

import com.sun.corba.se.spi.encoding.CorbaOutputObject ;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;
import com.sun.corba.se.pept.transport.ByteBufferPool;
import com.sun.corba.se.spi.transport.CorbaConnection;
import com.sun.corba.se.spi.logging.CORBALogDomains;

import com.sun.corba.se.spi.servicecontext.ServiceContexts;
import com.sun.corba.se.impl.encoding.BufferManagerFactory;
import com.sun.corba.se.impl.encoding.ByteBufferWithInfo;
import com.sun.corba.se.impl.encoding.CDROutputStream;
import com.sun.corba.se.impl.encoding.CDROutputStream_1_0;
import com.sun.corba.se.impl.encoding.CodeSetConversion;
import com.sun.corba.se.impl.encoding.CodeSetComponentInfo;
import com.sun.corba.se.impl.encoding.OSFCodeSetRegistry;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;
import com.sun.corba.se.impl.protocol.giopmsgheaders.MessageBase;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.logging.OMGSystemException;

/**
 * @author Harold Carr
 */
public class CDROutputObject extends CorbaOutputObject
{
    private Message header;
    private ORB orb;
    private ORBUtilSystemException wrapper;
    private OMGSystemException omgWrapper;

    // REVISIT - only used on sendCancelRequest.
    private CorbaConnection connection;

    private CDROutputObject(
        ORB orb, GIOPVersion giopVersion, Message header,
        BufferManagerWrite manager, byte streamFormatVersion,
        CorbaMessageMediator mediator)
    {
        super(orb, giopVersion, header.getEncodingVersion(),
              false, manager, streamFormatVersion,
              ((mediator != null && mediator.getConnection() != null) ?
               ((CorbaConnection)mediator.getConnection()).
                     shouldUseDirectByteBuffers() : false));

        this.header = header;
        this.orb = orb;
        this.wrapper = ORBUtilSystemException.get( orb, CORBALogDomains.RPC_ENCODING ) ;
        this.omgWrapper = OMGSystemException.get( orb, CORBALogDomains.RPC_ENCODING ) ;

        getBufferManager().setOutputObject(this);
        this.corbaMessageMediator = mediator;
    }

    public CDROutputObject(ORB orb,
                           MessageMediator messageMediator,
                           Message header,
                           byte streamFormatVersion)
    {
        this(
            orb,
            ((CorbaMessageMediator)messageMediator).getGIOPVersion(),
            header,
            BufferManagerFactory.newBufferManagerWrite(
                ((CorbaMessageMediator)messageMediator).getGIOPVersion(),
                header.getEncodingVersion(),
                orb),
            streamFormatVersion,
            (CorbaMessageMediator)messageMediator);
    }

    // NOTE:
    // Used in SharedCDR (i.e., must be grow).
    // Used in msgtypes test.
    public CDROutputObject(ORB orb,
                           MessageMediator messageMediator,
                           Message header,
                           byte streamFormatVersion,
                           int strategy)
    {
        this(
            orb,
            ((CorbaMessageMediator)messageMediator).getGIOPVersion(),
            header,
            BufferManagerFactory.
                newBufferManagerWrite(strategy,
                                      header.getEncodingVersion(),
                                      orb),
            streamFormatVersion,
            (CorbaMessageMediator)messageMediator);
    }

    // REVISIT
    // Used on sendCancelRequest.
    // Used for needs addressing mode.
    public CDROutputObject(ORB orb, CorbaMessageMediator mediator,
                           GIOPVersion giopVersion,
                           CorbaConnection connection, Message header,
                           byte streamFormatVersion)
    {
        this(
            orb,
            giopVersion,
            header,
            BufferManagerFactory.
            newBufferManagerWrite(giopVersion,
                                  header.getEncodingVersion(),
                                  orb),
            streamFormatVersion,
            mediator);
        this.connection = connection ;
    }

    // XREVISIT
    // Header should only be in message mediator.
    // Another possibility: merge header and message mediator.
    // REVISIT - make protected once all encoding together
    public Message getMessageHeader() {
        return header;
    }

    public final void finishSendingMessage() {
        getBufferManager().sendMessage();
    }

    /**
     * Write the contents of the CDROutputStream to the specified
     * output stream.  Has the side-effect of pushing any current
     * Message onto the Message list.
     * @param connection The output stream to write to.
     */
    public void writeTo(CorbaConnection connection)
        throws java.io.IOException
    {

        //
        // Update the GIOP MessageHeader size field.
        //

        ByteBufferWithInfo bbwi = getByteBufferWithInfo();

        getMessageHeader().setSize(bbwi.byteBuffer, bbwi.getSize());

        if (orb() != null) {
            if (((ORB)orb()).transportDebugFlag) {
                dprint(".writeTo: " + connection);
            }
            if (((ORB)orb()).giopDebugFlag) {
                CDROutputStream_1_0.printBuffer(bbwi);
            }
        }
        bbwi.byteBuffer.position(0).limit(bbwi.getSize());
        connection.write(bbwi.byteBuffer);
    }

    /** overrides create_input_stream from CDROutputStream */
    public org.omg.CORBA.portable.InputStream create_input_stream()
    {
        // XREVISIT
        return null;
        //return new XIIOPInputStream(orb(), getByteBuffer(), getIndex(),
            //isLittleEndian(), getMessageHeader(), conn);
    }

    public CorbaConnection getConnection()
    {
        // REVISIT - only set when doing sendCancelRequest.
        if (connection != null) {
            return connection;
        }
        return (CorbaConnection) corbaMessageMediator.getConnection();
    }

    // XREVISIT - If CDROutputObject doesn't live in the iiop
    // package, it will need this, here, to give package access
    // to xgiop.
    // REVISIT - make protected once all encoding together
    public final ByteBufferWithInfo getByteBufferWithInfo() {
        return super.getByteBufferWithInfo();
    }

    // REVISIT - make protected once all encoding together
    public final void setByteBufferWithInfo(ByteBufferWithInfo bbwi) {
        super.setByteBufferWithInfo(bbwi);
    }

    /**
     * Override the default CDR factory behavior to get the
     * negotiated code sets from the connection.
     *
     * These are only called once per message, the first time needed.
     *
     * In the local case, there is no Connection, so use the
     * local code sets.
     */
    protected CodeSetConversion.CTBConverter createCharCTBConverter() {
        CodeSetComponentInfo.CodeSetContext codesets = getCodeSets();

        // If the connection doesn't have its negotiated
        // code sets by now, fall back on the defaults defined
        // in CDRInputStream.
        if (codesets == null)
            return super.createCharCTBConverter();

        OSFCodeSetRegistry.Entry charSet
            = OSFCodeSetRegistry.lookupEntry(codesets.getCharCodeSet());

        if (charSet == null)
            throw wrapper.unknownCodeset( charSet ) ;

        return CodeSetConversion.impl().getCTBConverter(charSet,
                                                        isLittleEndian(),
                                                        false);
    }

    protected CodeSetConversion.CTBConverter createWCharCTBConverter() {

        CodeSetComponentInfo.CodeSetContext codesets = getCodeSets();

        // If the connection doesn't have its negotiated
        // code sets by now, we have to throw an exception.
        // See CORBA formal 00-11-03 13.9.2.6.
        if (codesets == null) {
            if (getConnection().isServer())
                throw omgWrapper.noClientWcharCodesetCtx() ;
            else
                throw omgWrapper.noServerWcharCodesetCmp() ;
        }

        OSFCodeSetRegistry.Entry wcharSet
            = OSFCodeSetRegistry.lookupEntry(codesets.getWCharCodeSet());

        if (wcharSet == null)
            throw wrapper.unknownCodeset( wcharSet ) ;

        boolean useByteOrderMarkers
            = ((ORB)orb()).getORBData().useByteOrderMarkers();

        // With UTF-16:
        //
        // For GIOP 1.2, we can put byte order markers if we want to, and
        // use the default of big endian otherwise.  (See issue 3405b)
        //
        // For GIOP 1.1, we don't use BOMs and use the endianness of
        // the stream.
        if (wcharSet == OSFCodeSetRegistry.UTF_16) {
            if (getGIOPVersion().equals(GIOPVersion.V1_2)) {
                return CodeSetConversion.impl().getCTBConverter(wcharSet,
                                                                false,
                                                                useByteOrderMarkers);
            }

            if (getGIOPVersion().equals(GIOPVersion.V1_1)) {
                return CodeSetConversion.impl().getCTBConverter(wcharSet,
                                                                isLittleEndian(),
                                                                false);
            }
        }

        // In the normal case, let the converter system handle it
        return CodeSetConversion.impl().getCTBConverter(wcharSet,
                                                        isLittleEndian(),
                                                        useByteOrderMarkers);
    }

    // If we're local and don't have a Connection, use the
    // local code sets, otherwise get them from the connection.
    // If the connection doesn't have negotiated code sets
    // yet, then we use ISO8859-1 for char/string and wchar/wstring
    // are illegal.
    private CodeSetComponentInfo.CodeSetContext getCodeSets() {
        if (getConnection() == null)
            return CodeSetComponentInfo.LOCAL_CODE_SETS;
        else
            return getConnection().getCodeSetContext();
    }

    protected void dprint(String msg)
    {
        ORBUtility.dprint("CDROutputObject", msg);
    }
}

// End of file.
