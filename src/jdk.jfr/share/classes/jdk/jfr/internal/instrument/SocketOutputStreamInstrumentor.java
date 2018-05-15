/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jfr.events.SocketWriteEvent;

/**
 * See {@link JITracer} for an explanation of this code.
 */
@JIInstrumentationTarget("java.net.SocketOutputStream")
@JITypeMapping(from = "jdk.jfr.internal.instrument.SocketOutputStreamInstrumentor$AbstractPlainSocketImpl",
            to = "java.net.AbstractPlainSocketImpl")
final class SocketOutputStreamInstrumentor {

    private SocketOutputStreamInstrumentor() {
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    private void socketWrite(byte b[], int off, int len) throws IOException {
        SocketWriteEvent event = SocketWriteEvent.EVENT.get();
        if (!event.isEnabled()) {
            socketWrite(b, off, len);
            return;
        }
        int bytesWritten = 0;
        try {
            event.begin();
            socketWrite(b, off, len);
            bytesWritten = len;
        } finally {
            event.end() ;
            if (event.shouldCommit()) {
                String hostString  = impl.address.toString();
                int delimiterIndex = hostString.lastIndexOf('/');

                event.host         = hostString.substring(0, delimiterIndex);
                event.address      = hostString.substring(delimiterIndex + 1);
                event.port         = impl.port;
                event.bytesWritten = bytesWritten < 0 ? 0 : bytesWritten;

                event.commit();
                event.reset();
            }
        }
    }

    private AbstractPlainSocketImpl impl = null;

    void silenceFindBugsUnwrittenField(InetAddress dummy) {
        impl.address = dummy;
    }

    static class AbstractPlainSocketImpl {
        InetAddress address;
        int port;
    }
}
