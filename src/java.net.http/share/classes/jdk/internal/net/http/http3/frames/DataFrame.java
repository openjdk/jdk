/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http.http3.frames;


/**
 * This class models an HTTP/3 DATA frame.
 * @apiNote
 * An instance of {@code DataFrame} is used to read or writes
 * the frame's type and length. The payload is supposed to be
 * read or written directly to the stream on its own, after having
 * read or written the frame type and length.
 * @see PartialFrame
 */
public final class DataFrame extends PartialFrame {

    /**
     * The DATA frame type, as defined by HTTP/3
     */
    public static final int TYPE = Http3FrameType.TYPE.DATA_FRAME;

    private final long length;

    /**
     * Creates a new HTTP/3 HEADERS frame
     */
    public DataFrame(long length) {
        super(TYPE, length);
        this.length = length;
    }

    @Override
    public long length() {
        return length;
    }

}
