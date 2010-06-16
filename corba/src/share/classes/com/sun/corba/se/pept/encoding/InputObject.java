/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.pept.encoding;

import java.io.IOException;

import com.sun.corba.se.pept.protocol.MessageMediator;

/**
 * <p> An <code>InputObject</code> is the interface used by the
 * presentation block to get programming language typed data from data
 *  encoded in a message. </p>
 *
 * <p> The implementation of an <code>InputObject</code> contains the
 * encoded data.  When the presentation block asks for data the
 * implementation of <code>InputObject</code> is responsible for converting
 * the encoded representation of the data to the types expected by the
 * programming language.</p>
 *
 * <p>A particular <em>encoding</em> would subclass
 * <code>InputObject</code>.  The subclass would provide methods to get
 * the data types appropriate to the presentation block (e.g., simple
 * types such as int or boolean, all the way to any type derived from
 * <code>java.io.Serializable</code>.).</p>
 *
 * <p>Note: the protocol block may also use the <code>InputObject</code> to
 * obtain header metadata.</p>
 *
 * @author Harold Carr
*/
public interface InputObject
{
    public void setMessageMediator(MessageMediator messageMediator);

    public MessageMediator getMessageMediator();

    public void close() throws IOException;
}

// End of file.
