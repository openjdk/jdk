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
 * A class to model an unknown or reserved frame.
 * @apiNote
 * From <a href="https://www.rfc-editor.org/rfc/rfc9114.html#name-reserved-frame-types">RFC 9114</a>:
 * <blockquote>
 * Frame types of the format 0x1f * N + 0x21 for non-negative integer
 * values of N are reserved to exercise the requirement that
 * unknown types be ignored (Section 9). These frames have no semantics,
 * and MAY be sent on any stream where frames are allowed to be sent.
 * This enables their use for application-layer padding. Endpoints MUST NOT
 * consider these frames to have any meaning upon receipt.
 * </blockquote>
 *
 * @apiNote
 * An instance of {@code UnknownFrame} is used to read or writes
 * the frame's type and length. The payload is supposed to be
 * read or written directly to the stream on its own, after having
 * read or written the frame type and length.
 * @see jdk.internal.net.http.http3.frames.PartialFrame
 * */
public final class UnknownFrame extends PartialFrame {
    final long length;
    UnknownFrame(long type, long length) {
        super(type, length);
        this.length = length;
    }

    @Override
    public long length() {
        return length;
    }

    /**
     * {@return true if this frame type is one of the reserved
     *  types}
     */
    public boolean isReserved() {
        return Http3FrameType.isReservedType(type);
    }

}
