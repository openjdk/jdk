/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

import org.omg.CORBA.CompletionStatus;

import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.spi.ior.iiop.GIOPVersion;

import com.sun.corba.se.impl.encoding.CodeSetConversion;
import com.sun.corba.se.impl.encoding.OSFCodeSetRegistry;
import com.sun.corba.se.impl.encoding.CDROutputStream;
import com.sun.corba.se.impl.encoding.BufferManagerWrite;
import com.sun.corba.se.impl.encoding.BufferManagerFactory;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;
import com.sun.corba.se.impl.orbutil.ORBConstants;

import sun.corba.EncapsInputStreamFactory;

/**
 * Encapsulations are supposed to explicitly define their
 * code sets and GIOP version.  The original resolution to issue 2784
 * said that the defaults were UTF-8 and UTF-16, but that was not
 * agreed upon.
 *
 * These streams currently use CDR 1.2 with ISO8859-1 for char/string and
 * UTF16 for wchar/wstring.  If no byte order marker is available,
 * the endianness of the encapsulation is used.
 *
 * When more encapsulations arise that have their own special code
 * sets defined, we can make all constructors take such parameters.
 */
public class EncapsOutputStream extends CDROutputStream
{

    // REVISIT - Right now, EncapsOutputStream's do not use
    // pooled byte buffers. This is controlled by the following
    // static constant. This should be re-factored such that
    // the EncapsOutputStream doesn't know it's using pooled
    // byte buffers.
    final static boolean usePooledByteBuffers = false;

    // REVISIT - Right now, valuetypes in encapsulations will
    // only use stream format version 1, which may create problems
    // for service contexts or codecs (?).

    // corba/ORB
    // corba/ORBSingleton
    // iiop/ORB
    // iiop/GIOPImpl
    // corba/AnyImpl
    public EncapsOutputStream(ORB orb) {
        // GIOP version 1.2 with no fragmentation, big endian,
        // UTF8 for char data and UTF-16 for wide char data;
        this(orb, GIOPVersion.V1_2);
    }

    // CDREncapsCodec
    //
    // REVISIT.  A UTF-16 encoding with GIOP 1.1 will not work
    // with byte order markers.
    public EncapsOutputStream(ORB orb, GIOPVersion version) {
        this(orb, version, false);
    }

    // Used by IIOPProfileTemplate
    //
    public EncapsOutputStream(ORB orb, boolean isLittleEndian) {
        this(orb, GIOPVersion.V1_2, isLittleEndian);
    }

    public EncapsOutputStream(ORB orb,
                              GIOPVersion version,
                              boolean isLittleEndian)
    {
        super(orb, version, Message.CDR_ENC_VERSION, isLittleEndian,
              BufferManagerFactory.newBufferManagerWrite(
                                        BufferManagerFactory.GROW,
                                        Message.CDR_ENC_VERSION,
                                        orb),
              ORBConstants.STREAM_FORMAT_VERSION_1,
              usePooledByteBuffers);
    }

    public org.omg.CORBA.portable.InputStream create_input_stream() {
        freeInternalCaches();

        return  EncapsInputStreamFactory.newEncapsInputStream(orb(),
                getByteBuffer(),
                getSize(),
                isLittleEndian(),
                getGIOPVersion());
    }

    protected CodeSetConversion.CTBConverter createCharCTBConverter() {
        return CodeSetConversion.impl().getCTBConverter(OSFCodeSetRegistry.ISO_8859_1);
    }

    protected CodeSetConversion.CTBConverter createWCharCTBConverter() {
        if (getGIOPVersion().equals(GIOPVersion.V1_0))
            throw wrapper.wcharDataInGiop10(CompletionStatus.COMPLETED_MAYBE);

        // In the case of GIOP 1.1, we take the byte order of the stream and don't
        // use byte order markers since we're limited to a 2 byte fixed width encoding.
        if (getGIOPVersion().equals(GIOPVersion.V1_1))
            return CodeSetConversion.impl().getCTBConverter(OSFCodeSetRegistry.UTF_16,
                                                            isLittleEndian(),
                                                            false);

        // Assume anything else meets GIOP 1.2 requirements
        //
        // Use byte order markers?  If not, use big endian in GIOP 1.2.
        // (formal 00-11-03 15.3.16)

        boolean useBOM = ((ORB)orb()).getORBData().useByteOrderMarkersInEncapsulations();

        return CodeSetConversion.impl().getCTBConverter(OSFCodeSetRegistry.UTF_16,
                                                        false,
                                                        useBOM);
    }
}
