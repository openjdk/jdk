/*
 * Copyright 1998-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jdi.connect;

import com.sun.jdi.connect.spi.TransportService;        // for javadoc

/**
 * A method of communication between a debugger and a target VM.
 *
 * <p> A Transport represents the transport mechanism used by a
 * {@link com.sun.jdi.connect.Connector Connector} to establish a
 * connection with a target VM. It consists of a name which is obtained
 * by invoking the {@link #name} method. Furthermore, a Transport
 * encapsulates a {@link com.sun.jdi.connect.spi.TransportService
 * TransportService} which is the underlying service used
 * to establish connections and exchange Java Debug Wire Protocol
 * (JDWP) packets with a target VM.
 *
 * @author Gordon Hirsch
 * @since  1.3
 */
public interface Transport {
    /**
     * Returns a short identifier for the transport.
     *
     * @return the name of this transport.
     */
    String name();
}
