/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.ws.api.message.stream;

import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.message.AttachmentSetImpl;

/**
 * Base representation an XML or SOAP message as stream.
 *
 */
abstract class StreamBasedMessage {
    /**
     * The properties of the message.
     */
    public final Packet properties;

    /**
     * The attachments of this message
     * (attachments live outside a message.)
     */
    public final AttachmentSet attachments;

    /**
     * Create a new message.
     *
     * @param properties
     *      the properties of the message.
     *
     */
    protected StreamBasedMessage(Packet properties) {
        this.properties = properties;
        this.attachments = new AttachmentSetImpl();
    }

    /**
     * Create a new message.
     *
     * @param properties
     *      the properties of the message.
     *
     * @param attachments
     *      the attachments of the message.
     */
    protected StreamBasedMessage(Packet properties, AttachmentSet attachments) {
        this.properties = properties;
        this.attachments = attachments;
    }
}
