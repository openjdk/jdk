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

import java.nio.ByteBuffer;

import com.sun.org.omg.SendingContext.CodeBase;

import com.sun.corba.se.pept.encoding.InputObject;

import com.sun.corba.se.spi.logging.CORBALogDomains;

import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.spi.transport.CorbaConnection;

import com.sun.corba.se.spi.ior.iiop.GIOPVersion;

import com.sun.corba.se.impl.encoding.BufferManagerFactory;
import com.sun.corba.se.impl.encoding.CodeSetComponentInfo;
import com.sun.corba.se.impl.encoding.CodeSetConversion;
import com.sun.corba.se.impl.encoding.OSFCodeSetRegistry;
import com.sun.corba.se.impl.encoding.CDRInputStream;

import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;

import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.logging.OMGSystemException;

import com.sun.corba.se.impl.orbutil.ORBUtility;

/**
 * @author Harold Carr
 */
public class CDRInputObject extends CDRInputStream
    implements
        InputObject
{
    private CorbaConnection corbaConnection;
    private Message header;
    private boolean unmarshaledHeader;
    private ORB orb ;
    private ORBUtilSystemException wrapper ;
    private OMGSystemException omgWrapper ;

    public CDRInputObject(ORB orb,
                          CorbaConnection corbaConnection,
                          ByteBuffer byteBuffer,
                          Message header)
    {
        super(orb, byteBuffer, header.getSize(), header.isLittleEndian(),
              header.getGIOPVersion(), header.getEncodingVersion(),
              BufferManagerFactory.newBufferManagerRead(
                                          header.getGIOPVersion(),
                                          header.getEncodingVersion(),
                                          orb));

        this.corbaConnection = corbaConnection;
        this.orb = orb ;
        this.wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_ENCODING ) ;
        this.omgWrapper = OMGSystemException.get( orb,
            CORBALogDomains.RPC_ENCODING ) ;

        if (orb.transportDebugFlag) {
            dprint(".CDRInputObject constructor:");
        }

        getBufferManager().init(header);

        this.header = header;

        unmarshaledHeader = false;

        setIndex(Message.GIOPMessageHeaderLength);

        setBufferLength(header.getSize());
    }

    // REVISIT - think about this some more.
    // This connection normally is accessed from the message mediator.
    // However, giop input needs to get code set info from the connetion
    // *before* the message mediator is available.
    public final CorbaConnection getConnection()
    {
        return corbaConnection;
    }

    // XREVISIT - Should the header be kept in the stream or the
    // message mediator?  Or should we not have a header and
    // have the information stored in the message mediator
    // directly?
    public Message getMessageHeader()
    {
        return header;
    }

    /**
     * Unmarshal the extended GIOP header
     * NOTE: May be fragmented, so should not be called by the ReaderThread.
     * See CorbaResponseWaitingRoomImpl.waitForResponse.  It is done
     * there in the client thread.
     */
    public void unmarshalHeader()
    {
        // Unmarshal the extended GIOP message from the buffer.

        if (!unmarshaledHeader) {
            try {
                if (((ORB)orb()).transportDebugFlag) {
                    dprint(".unmarshalHeader->: " + getMessageHeader());
                }
                getMessageHeader().read(this);
                unmarshaledHeader= true;
            } catch (RuntimeException e) {
                if (((ORB)orb()).transportDebugFlag) {
                    dprint(".unmarshalHeader: !!ERROR!!: "
                           + getMessageHeader()
                           + ": " + e);
                }
                throw e;
            } finally {
                if (((ORB)orb()).transportDebugFlag) {
                    dprint(".unmarshalHeader<-: " + getMessageHeader());
                }
            }
        }
    }

    public final boolean unmarshaledHeader()
    {
        return unmarshaledHeader;
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
    protected CodeSetConversion.BTCConverter createCharBTCConverter() {
        CodeSetComponentInfo.CodeSetContext codesets = getCodeSets();

        // If the connection doesn't have its negotiated
        // code sets by now, fall back on the defaults defined
        // in CDRInputStream.
        if (codesets == null)
            return super.createCharBTCConverter();

        OSFCodeSetRegistry.Entry charSet
            = OSFCodeSetRegistry.lookupEntry(codesets.getCharCodeSet());

        if (charSet == null)
            throw wrapper.unknownCodeset( charSet ) ;

        return CodeSetConversion.impl().getBTCConverter(charSet, isLittleEndian());
    }

    protected CodeSetConversion.BTCConverter createWCharBTCConverter() {

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

        // For GIOP 1.2 and UTF-16, use big endian if there is no byte
        // order marker.  (See issue 3405b)
        //
        // For GIOP 1.1 and UTF-16, use the byte order the stream if
        // there isn't (and there shouldn't be) a byte order marker.
        //
        // GIOP 1.0 doesn't have wchars.  If we're talking to a legacy ORB,
        // we do what our old ORBs did.
        if (wcharSet == OSFCodeSetRegistry.UTF_16) {
            if (getGIOPVersion().equals(GIOPVersion.V1_2))
                return CodeSetConversion.impl().getBTCConverter(wcharSet, false);
        }

        return CodeSetConversion.impl().getBTCConverter(wcharSet, isLittleEndian());
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

    public final CodeBase getCodeBase() {
        if (getConnection() == null)
            return null;
        else
            return getConnection().getCodeBase();
    }

    // -----------------------------------------------------------
    // Below this point are commented out methods with features
    // from the old stream.  We must find ways to address
    // these issues in the future.
    // -----------------------------------------------------------

    // XREVISIT
//     private XIIOPInputStream(XIIOPInputStream stream) {
//         super(stream);

//         this.conn = stream.conn;
//         this.msg = stream.msg;
//         this.unmarshaledHeader = stream.unmarshaledHeader;
//     }

    public CDRInputStream dup() {
        // XREVISIT
        return null;
        // return new XIIOPInputStream(this);
    }

    protected void dprint(String msg)
    {
        ORBUtility.dprint("CDRInputObject", msg);
    }
}

// End of file.
