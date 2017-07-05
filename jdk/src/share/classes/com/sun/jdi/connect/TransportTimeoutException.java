/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * This exception may be thrown as a result of a timeout
 * when attaching to a target VM, or waiting to accept a
 * connection from a target VM.
 *
 * <p> When attaching to a target VM, using {@link
 * AttachingConnector#attach attach} this
 * exception may be thrown if the connector supports a timeout
 * {@link Connector.Argument connector argument}. Similiarly,
 * when waiting to accept a connection from a target VM,
 * using {@link ListeningConnector#accept accept} this
 * exception may be thrown if the connector supports a
 * timeout connector argument when accepting.
 *
 * <p> In addition, for developers creating {@link
 * com.sun.jdi.connect.spi.TransportService TransportService}
 * implementations this exception is thrown when
 * {@link com.sun.jdi.connect.spi.TransportService#attach attach}
 * times out when establishing a connection to a target VM,
 * or {@link com.sun.jdi.connect.spi.TransportService#accept
 * accept} times out while waiting for a target VM to connect. </p>
 *
 * @see AttachingConnector#attach
 * @see ListeningConnector#accept
 * @see com.sun.jdi.connect.spi.TransportService#attach
 * @see com.sun.jdi.connect.spi.TransportService#accept
 *
 * @since 1.5
 */
public class TransportTimeoutException extends java.io.IOException {

    /**
     * Constructs a <tt>TransportTimeoutException</tt> with no detail
     * message.
     */
    public TransportTimeoutException() {
    }


    /**
     * Constructs a <tt>TransportTimeoutException</tt> with the
     * specified detail message.
     *
     * @param message the detail message pertaining to this exception.
     */
    public TransportTimeoutException(String message) {
        super(message);
    }

}
