/*
 * Copyright (c) 2001, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.pept.protocol;

import com.sun.corba.se.pept.broker.Broker;
import com.sun.corba.se.pept.encoding.InputObject;
import com.sun.corba.se.pept.encoding.OutputObject;
import com.sun.corba.se.pept.transport.Connection;
import com.sun.corba.se.pept.transport.ContactInfo;

import java.io.IOException;

/**
 * <code>MessageMediator</code> is a central repository for artifacts
 * associated with an individual message.
 *
 * @author Harold Carr
 */
public interface MessageMediator
{
    /**
     * The {@link com.sun.corba.se.pept.broker.Broker Broker} associated
     * with an invocation.
     *
     * @return {@link com.sun.corba.se.pept.broker.Broker Broker}
     */
    public Broker getBroker();

    /**
     * Get the
     * {@link com.sun.corba.se.pept.transport.ContactInfo ContactInfo}
     * which created this <code>MessageMediator</code>.
     *
     * @return
     * {@link com.sun.corba.se.pept.transport.ContactInfo ContactInfo}
     */
    public ContactInfo getContactInfo();

    /**
     * Get the
     * {@link com.sun.corba.se.pept.transport.Connection Connection}
     * on which this message is sent or received.
     */
    public Connection getConnection();

    /**
     * Used to initialize message headers.
     *
     * Note: this should be moved to a <code>RequestDispatcher</code>.
     */
    public void initializeMessage();

    /**
     * Used to send the message (or its last fragment).
     *
     * Note: this should be moved to a <code>RequestDispatcher</code>.
     */
    public void finishSendingRequest();

    /**
     * Used to wait for a response for synchronous messages.
     *
     * @deprecated
     */
    @Deprecated
    public InputObject waitForResponse();

    /**
     * Used to set the
     * {@link com.sun.corba.se.pept.encoding.OutputObject OutputObject}
     * used for the message.
     *
     * @param outputObject
     */
    public void setOutputObject(OutputObject outputObject);

    /**
     * Used to get the
     * {@link com.sun.corba.se.pept.encoding.OutputObject OutputObject}
     * used for the message.
     *
     * @return
     * {@link com.sun.corba.se.pept.encoding.OutputObject OutputObject}
     */
    public OutputObject getOutputObject();

    /**
     * Used to set the
     * {@link com.sun.corba.se.pept.encoding.InputObject InputObject}
     * used for the message.
     *
     * @param inputObject
     */
    public void setInputObject(InputObject inputObject);

    /**
     * Used to get the
     * {@link com.sun.corba.se.pept.encoding.InputObject InputObject}
     * used for the message.
     *
     * @return
     * {@link com.sun.corba.se.pept.encoding.InputObject InputObject}
     */
    public InputObject getInputObject();
}

// End of file.
