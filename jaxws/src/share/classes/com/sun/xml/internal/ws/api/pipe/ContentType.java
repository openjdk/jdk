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

package com.sun.xml.internal.ws.api.pipe;

/**
 * A Content-Type transport header that will be returned by {@link Codec#encode(com.sun.xml.internal.ws.api.message.Packet, java.io.OutputStream)}.
 * It will provide the Content-Type header and also take care of SOAP 1.1 SOAPAction header.
 *
 * TODO: rename to ContentMetadata?
 *
 * @author Vivek Pandey
 */
public interface ContentType {
    /**
     * Gives non-null Content-Type header value.
     */
    public String getContentType();

    /**
     * Gives SOAPAction transport header value. It will be non-null only for SOAP 1.1 messages. In other cases
     * it MUST be null. The SOAPAction transport header should be written out only when its non-null.
     *
     * @return It can be null, in that case SOAPAction header should be written.
     */
    public String getSOAPActionHeader();

    /**
     * Controls the Accept transport header, if the transport supports it.
     * Returning null means the transport need not add any new header.
     *
     * <p>
     * We realize that this is not an elegant abstraction, but
     * this would do for now. If another person comes and asks for
     * a similar functionality, we'll define a real abstraction.
     */
    public String getAcceptHeader();
}
