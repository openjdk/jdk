/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package sun.nio.ch;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

/**
 * An InputStream that reads bytes from a file channel.
 */
class FileChannelInputStream extends ChannelInputStream {
    /**
     * Initialize a FileChannelInputStream that reads from the given file channel.
     */
    FileChannelInputStream(FileChannel fc) {
        super(fc);
    }

    @Override
    FileChannel channel() {
        return (FileChannel) super.channel();
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        // transferTo(SocketChannel)
        if (out instanceof SocketOutputStream sos) {
            SocketChannel sc = sos.channel();
            synchronized (sc.blockingLock()) {
                if (!sc.isBlocking())
                    throw new IllegalBlockingModeException();
                return transferTo(sc);
            }
        }

        // transferTo(WritableByteChannel)
        if (out instanceof ChannelOutputStream cos) {
            WritableByteChannel target = cos.channel();
            if (target instanceof SelectableChannel sc) {
                synchronized (sc.blockingLock()) {
                    if (!sc.isBlocking())
                        throw new IllegalBlockingModeException();
                    return transferTo(target);
                }
            }
            return transferTo(target);
        }

        // transferTo(FileChannel)
        if (out instanceof FileOutputStream fos) {
            return transferTo(fos.getChannel());
        }

        return super.transferTo(out);
    }

    /**
     * Transfers all bytes to the given target channel.
     */
    private long transferTo(WritableByteChannel target) throws IOException {
        FileChannel fc = channel();
        long initialPos = fc.position();
        long pos = initialPos;
        try {
            while (pos < fc.size()) {
                pos += fc.transferTo(pos, Long.MAX_VALUE, target);
            }
        } finally {
            fc.position(pos);
        }
        return pos - initialPos;
    }
}
