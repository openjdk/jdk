/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.corba;

import com.sun.corba.se.impl.corba.AnyImpl;
import com.sun.corba.se.impl.encoding.BufferManagerWrite;
import com.sun.corba.se.impl.encoding.CDROutputObject;
import com.sun.corba.se.impl.encoding.EncapsOutputStream;
import com.sun.corba.se.impl.encoding.TypeCodeOutputStream;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;

import com.sun.corba.se.pept.protocol.MessageMediator;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.transport.CorbaConnection;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;

import java.security.AccessController;
import java.security.PrivilegedAction;

public final class OutputStreamFactory {

    private OutputStreamFactory() {
    }

    public static TypeCodeOutputStream newTypeCodeOutputStream(
            final ORB orb) {
        return AccessController.doPrivileged(
            new PrivilegedAction<TypeCodeOutputStream>() {
                @Override
                public TypeCodeOutputStream run() {
                    return new TypeCodeOutputStream(orb);
                }
        });
    }

    public static TypeCodeOutputStream newTypeCodeOutputStream(
            final ORB orb, final boolean littleEndian) {
        return AccessController.doPrivileged(
            new PrivilegedAction<TypeCodeOutputStream>() {
                @Override
                public TypeCodeOutputStream run() {
                    return new TypeCodeOutputStream(orb, littleEndian);
                }
        });
    }

    public static EncapsOutputStream newEncapsOutputStream(
            final ORB orb) {
        return AccessController.doPrivileged(
            new PrivilegedAction<EncapsOutputStream>() {
                @Override
                public EncapsOutputStream run() {
                    return new EncapsOutputStream(
                        (com.sun.corba.se.spi.orb.ORB)orb);
                }
        });
    }

    public static EncapsOutputStream newEncapsOutputStream(
            final ORB orb, final GIOPVersion giopVersion) {
        return AccessController.doPrivileged(
            new PrivilegedAction<EncapsOutputStream>() {
                @Override
                public EncapsOutputStream run() {
                    return new EncapsOutputStream(
                        (com.sun.corba.se.spi.orb.ORB)orb, giopVersion);
                }
        });
    }

    public static EncapsOutputStream newEncapsOutputStream(
            final ORB orb, final boolean isLittleEndian) {
        return AccessController.doPrivileged(
            new PrivilegedAction<EncapsOutputStream>() {
                @Override
                public EncapsOutputStream run() {
                    return new EncapsOutputStream(
                        (com.sun.corba.se.spi.orb.ORB)orb, isLittleEndian);
                }
        });
    }

    public static CDROutputObject newCDROutputObject(
            final ORB orb, final MessageMediator messageMediator,
            final Message header, final byte streamFormatVersion) {
        return AccessController.doPrivileged(
            new PrivilegedAction<CDROutputObject>() {
                @Override
                public CDROutputObject run() {
                    return new CDROutputObject(orb, messageMediator,
                        header, streamFormatVersion);
                }
        });
    }

    public static CDROutputObject newCDROutputObject(
            final ORB orb, final MessageMediator messageMediator,
            final Message header, final byte streamFormatVersion,
            final int strategy) {
        return AccessController.doPrivileged(
            new PrivilegedAction<CDROutputObject>() {
                @Override
                public CDROutputObject run() {
                    return new CDROutputObject(orb, messageMediator,
                        header, streamFormatVersion, strategy);
                }
        });
    }

    public static CDROutputObject newCDROutputObject(
            final ORB orb, final CorbaMessageMediator mediator,
            final GIOPVersion giopVersion, final CorbaConnection connection,
            final Message header, final byte streamFormatVersion) {
        return AccessController.doPrivileged(
            new PrivilegedAction<CDROutputObject>() {
                @Override
                public CDROutputObject run() {
                    return new CDROutputObject(orb, mediator,
                        giopVersion, connection, header, streamFormatVersion);
                }
        });
    }

}
