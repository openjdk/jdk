/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Factory methods for input/output streams based on channels.
 */
public class Streams {
    private Streams() { }

    /**
     * Return an input stream that reads bytes from the given channel.
     */
    public static InputStream of(ReadableByteChannel ch) {
        if (ch instanceof SocketChannelImpl sc) {
            return new SocketInputStream(sc);
        } else {
            return new ChannelInputStream(ch);
        }
    }

    /**
     * Return an output stream that writes bytes to the given channel.
     */
    public static OutputStream of(WritableByteChannel ch) {
        if (ch instanceof SocketChannelImpl sc) {
            return new SocketOutputStream(sc);
        } else {
            return new ChannelOutputStream(ch);
        }
    }
}
