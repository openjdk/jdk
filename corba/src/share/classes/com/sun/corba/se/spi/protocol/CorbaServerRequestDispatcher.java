/*
 * Copyright (c) 1998, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.protocol;

import com.sun.corba.se.pept.protocol.ServerRequestDispatcher;

import com.sun.corba.se.spi.ior.ObjectKey;

// XXX These must all be replaced by Sun private APIs.
import com.sun.corba.se.spi.ior.IOR ;

/**
 * Server delegate adds behavior on the server-side -- specifically
 * on the dispatch path. A single server delegate instance serves
 * many server objects.  This is the second level of the dispatch
 * on the server side: Acceptor to ServerSubcontract to ServerRequestDispatcher to
 * ObjectAdapter to Servant, although this may be short-circuited.
 * Instances of this class are registered in the subcontract Registry.
 */
public interface CorbaServerRequestDispatcher
    extends ServerRequestDispatcher
{
    /**
     * Handle a locate request.
     */
    public IOR locate(ObjectKey key);
}

// End of file.
