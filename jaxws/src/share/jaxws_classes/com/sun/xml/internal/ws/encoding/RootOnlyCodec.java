/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.encoding;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Codec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;

/**
 * {@link Codec} that works only on the root part of the MIME/multipart.
 * It doesn't work on the attachment parts, so it takes {@link AttachmentSet}
 * as an argument and creates a corresponding {@link Message}. This enables
 * attachments to be parsed lazily by wrapping the mimepull parser into an
 * {@link AttachmentSet}
 *
 * @author Jitendra Kotamraju
 */
public interface RootOnlyCodec extends Codec {

    /**
     * Reads root part bytes from {@link InputStream} and constructs a {@link Message}
     * along with the given attachments.
     *
     * @param in root part's data
     *
     * @param contentType root part's MIME content type (like "application/xml")
     *
     * @param packet the new created {@link Message} is set in this packet
     *
     * @param att attachments
     *
     * @throws IOException
     *      if {@link InputStream} throws an exception.
     */
    void decode(@NotNull InputStream in, @NotNull String contentType, @NotNull Packet packet, @NotNull AttachmentSet att)
            throws IOException;

    /**
     *
     * @see #decode(InputStream, String, Packet, AttachmentSet)
     */
    void decode(@NotNull ReadableByteChannel in, @NotNull String contentType, @NotNull Packet packet, @NotNull AttachmentSet att);
}
