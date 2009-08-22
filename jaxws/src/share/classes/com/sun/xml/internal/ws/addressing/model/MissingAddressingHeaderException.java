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

package com.sun.xml.internal.ws.addressing.model;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.resources.AddressingMessages;

import javax.xml.ws.WebServiceException;
import javax.xml.namespace.QName;

/**
 * This exception signals that a particular WS-Addressing header is missing in a SOAP message.
 *
 * @author Rama Pulavarthi
 */
public class MissingAddressingHeaderException extends WebServiceException {
    private final QName name;
    private final Packet packet;

    /**
     *
     * @param name QName of the missing WS-Addressing Header
     */
    public MissingAddressingHeaderException(@NotNull QName name) {
        this(name,null);
    }

    public MissingAddressingHeaderException(@NotNull QName name, @Nullable Packet p) {
        super(AddressingMessages.MISSING_HEADER_EXCEPTION(name));
        this.name = name;
        this.packet = p;
    }

    /**
     * Gets the QName of the missing WS-Addressing Header.
     *
     * @return
     *      never null.
     */
    public QName getMissingHeaderQName() {
        return name;
    }

    /**
     * The {@link Packet} in which a header was missing.
     *
     * <p>
     * This object can be used to deep-inspect the problematic SOAP message.
     */
    public Packet getPacket() {
        return packet;
    }
}
