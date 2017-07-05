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

import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;


import com.sun.corba.se.impl.encoding.EncapsInputStream;
import com.sun.corba.se.impl.encoding.TypeCodeInputStream;
import com.sun.corba.se.impl.protocol.giopmsgheaders.Message;
import com.sun.corba.se.pept.protocol.MessageMediator;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.org.omg.SendingContext.CodeBase;

public class EncapsInputStreamFactory {

    public static EncapsInputStream newEncapsInputStream(
            final org.omg.CORBA.ORB orb, final byte[] buf, final int size,
            final boolean littleEndian, final GIOPVersion version) {
        return AccessController
                .doPrivileged(new PrivilegedAction<EncapsInputStream>() {
                    @Override
                    public EncapsInputStream run() {
                        return new EncapsInputStream(orb, buf, size,
                                littleEndian, version);
                    }
                });
    }

    public static EncapsInputStream newEncapsInputStream(
            final org.omg.CORBA.ORB orb, final ByteBuffer byteBuffer,
            final int size, final boolean littleEndian,
            final GIOPVersion version) {
        return AccessController
                .doPrivileged(new PrivilegedAction<EncapsInputStream>() {
                    @Override
                    public EncapsInputStream run() {
                        return new EncapsInputStream(orb, byteBuffer, size,
                                littleEndian, version);
                    }
                });
    }

    public static EncapsInputStream newEncapsInputStream(
            final org.omg.CORBA.ORB orb, final byte[] data, final int size) {
        return AccessController
                .doPrivileged(new PrivilegedAction<EncapsInputStream>() {
                    @Override
                    public EncapsInputStream run() {
                        return new EncapsInputStream(orb, data, size);
                    }
                });
    }

    public static EncapsInputStream newEncapsInputStream(
            final EncapsInputStream eis) {
        return AccessController
                .doPrivileged(new PrivilegedAction<EncapsInputStream>() {
                    @Override
                    public EncapsInputStream run() {
                        return new EncapsInputStream(eis);
                    }
                });
    }

    public static EncapsInputStream newEncapsInputStream(
            final org.omg.CORBA.ORB orb, final byte[] data, final int size,
            final GIOPVersion version) {
        return AccessController
                .doPrivileged(new PrivilegedAction<EncapsInputStream>() {
                    @Override
                    public EncapsInputStream run() {
                        return new EncapsInputStream(orb, data, size, version);
                    }
                });
    }

    public static EncapsInputStream newEncapsInputStream(
            final org.omg.CORBA.ORB orb, final byte[] data, final int size,
            final GIOPVersion version, final CodeBase codeBase) {
        return AccessController
                .doPrivileged(new PrivilegedAction<EncapsInputStream>() {
                    @Override
                    public EncapsInputStream run() {
                        return new EncapsInputStream(orb, data, size, version,
                                codeBase);
                    }
                });
    }

    public static TypeCodeInputStream newTypeCodeInputStream(
            final org.omg.CORBA.ORB orb, final byte[] buf, final int size,
            final boolean littleEndian, final GIOPVersion version) {
        return AccessController
                .doPrivileged(new PrivilegedAction<TypeCodeInputStream>() {
                    @Override
                    public TypeCodeInputStream run() {
                        return new TypeCodeInputStream(orb, buf, size,
                                littleEndian, version);
                    }
                });
    }

    public static TypeCodeInputStream newTypeCodeInputStream(
            final org.omg.CORBA.ORB orb, final ByteBuffer byteBuffer,
            final int size, final boolean littleEndian,
            final GIOPVersion version) {
        return AccessController
                .doPrivileged(new PrivilegedAction<TypeCodeInputStream>() {
                    @Override
                    public TypeCodeInputStream run() {
                        return new TypeCodeInputStream(orb, byteBuffer, size,
                                littleEndian, version);
                    }
                });
    }

    public static TypeCodeInputStream newTypeCodeInputStream(
            final org.omg.CORBA.ORB orb, final byte[] data, final int size) {
        return AccessController
                .doPrivileged(new PrivilegedAction<TypeCodeInputStream>() {
                    @Override
                    public TypeCodeInputStream run() {
                        return new TypeCodeInputStream(orb, data, size);
                    }
                });
    }
}
