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

package com.sun.xml.internal.ws;

import javax.xml.ws.WebServiceException;
import javax.xml.ws.Dispatch;
import java.io.IOException;

/**
 * Closeable JAX-WS proxy object.
 *
 * @author Kohsuke Kawaguchi
 * @since JAX-WS 2.0.2
 */
// this interface is exposed to applications.
public interface Closeable extends java.io.Closeable {
    /**
     * Closes this object and cleans up any resources
     * it holds, such as network connections.
     *
     * <p>
     * This interface is implemented by a port proxy
     * or {@link Dispatch}. In particular, this signals
     * the implementation of certain specs (like WS-ReliableMessaging
     * and WS-SecureConversation) to terminate sessions that they
     * create during the life time of a proxy object.
     *
     * <p>
     * This is not a mandatory operation, so the application
     * does not have to call this method.
     *
     *
     * @throws WebServiceException
     *      If clean up fails unexpectedly, this exception
     *      will be thrown (instead of {@link IOException}.
     */
    public void close() throws WebServiceException;
}
