/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.corba.se.impl.protocol.giopmsgheaders;

import java.io.IOException;

/**
 * Interface which allows an implementation to use
 * double dispatch when processing the various
 * concrete message types found in this package.
 */
public interface MessageHandler
{
    //
    // REVISIT - These should not throw IOException.
    //           Should be handled internally.

    /**
     * Used for message types for which we don't have concrete classes, yet,
     * such as CloseConnection and MessageError, as well as unknown types.
     */
    void handleInput(Message header) throws IOException;

    // Request
    void handleInput(RequestMessage_1_0 header) throws IOException;
    void handleInput(RequestMessage_1_1 header) throws IOException;
    void handleInput(RequestMessage_1_2 header) throws IOException;

    // Reply
    void handleInput(ReplyMessage_1_0 header) throws IOException;
    void handleInput(ReplyMessage_1_1 header) throws IOException;
    void handleInput(ReplyMessage_1_2 header) throws IOException;

    // LocateRequest
    void handleInput(LocateRequestMessage_1_0 header) throws IOException;
    void handleInput(LocateRequestMessage_1_1 header) throws IOException;
    void handleInput(LocateRequestMessage_1_2 header) throws IOException;

    // LocateReply
    void handleInput(LocateReplyMessage_1_0 header) throws IOException;
    void handleInput(LocateReplyMessage_1_1 header) throws IOException;
    void handleInput(LocateReplyMessage_1_2 header) throws IOException;

    // Fragment
    void handleInput(FragmentMessage_1_1 header) throws IOException;
    void handleInput(FragmentMessage_1_2 header) throws IOException;

    // CancelRequest
    void handleInput(CancelRequestMessage header) throws IOException;
}
