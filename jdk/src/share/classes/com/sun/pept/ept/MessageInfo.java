/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

/** Java interface "MessageInfo.java" generated from Poseidon for UML.
 *  Poseidon for UML is developed by <A HREF="http://www.gentleware.com">Gentleware</A>.
 *  Generated with <A HREF="http://jakarta.apache.org/velocity/">velocity</A> template engine.
 */
package com.sun.pept.ept;

import com.sun.pept.encoding.Decoder;
import com.sun.pept.encoding.Encoder;
import com.sun.pept.presentation.MessageStruct;
import com.sun.pept.protocol.MessageDispatcher;
import com.sun.pept.transport.Connection;
import java.util.*;

/**
 * <p>
 *
 * @author Dr. Harold Carr
 * </p>
 */
public interface MessageInfo extends MessageStruct {

  ///////////////////////////////////////
  // operations

/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a EPTFactory with ...
 * </p>
 */
    public EPTFactory getEPTFactory();
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a MessageDispatcher with ...
 * </p>
 */
    public MessageDispatcher getMessageDispatcher();
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a Encoder with ...
 * </p>
 */
    public Encoder getEncoder();
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a Decoder with ...
 * </p>
 */
    public Decoder getDecoder();
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * @return a Connection with ...
 * </p>
 */
    public Connection getConnection();
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * </p><p>
 *
 * @param eptFactory ...
 * </p>
 */
    public void setEPTFactory(EPTFactory eptFactory);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * </p><p>
 *
 * @param messageDispatcher ...
 * </p>
 */
    public void setMessageDispatcher(MessageDispatcher messageDispatcher);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * </p><p>
 *
 * @param encoder ...
 * </p>
 */
    public void setEncoder(Encoder encoder);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * </p><p>
 *
 * @param decoder ...
 * </p>
 */
    public void setDecoder(Decoder decoder);
/**
 * <p>
 * Does ...
 * </p><p>
 *
 * </p><p>
 *
 * @param connection ...
 * </p>
 */
    public void setConnection(Connection connection);

} // end MessageInfo
