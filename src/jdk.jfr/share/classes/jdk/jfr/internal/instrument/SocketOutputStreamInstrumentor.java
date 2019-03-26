/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.instrument;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import jdk.jfr.events.SocketWriteEvent;

/**
 * See {@link JITracer} for an explanation of this code.
 */
@JIInstrumentationTarget("java.net.Socket$SocketOutputStream")
final class SocketOutputStreamInstrumentor {

    private SocketOutputStreamInstrumentor() {
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public void write(byte b[], int off, int len) throws IOException {
        SocketWriteEvent event = SocketWriteEvent.EVENT.get();
        if (!event.isEnabled()) {
            write(b, off, len);
            return;
        }
        int bytesWritten = 0;
        try {
            event.begin();
            write(b, off, len);
            bytesWritten = len;
        } finally {
            event.end() ;
            if (event.shouldCommit()) {
                InetAddress remote = parent.getInetAddress();
                event.host = remote.getHostName();
                event.address = remote.getHostAddress();
                event.port = parent.getPort();
                event.bytesWritten = bytesWritten;

                event.commit();
                event.reset();
            }
        }
    }

    // private field in java.net.Socket$SocketOutputStream
    private Socket parent;
}
