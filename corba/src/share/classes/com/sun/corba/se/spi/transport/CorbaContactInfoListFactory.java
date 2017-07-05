/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.transport ;

import com.sun.corba.se.spi.transport.CorbaContactInfoList ;

import com.sun.corba.se.spi.ior.IOR ;
import com.sun.corba.se.spi.orb.ORB;

/** Interface used to create a ContactInfoList from an IOR, as required
 * for supporting CORBA semantics using the DCS framework.  This is a
 * natural correspondence since an IOR contains the information for
 * contacting one or more communication endpoints that can be used to
 * invoke a method on an object, along with the necessary information
 * on particular transports, encodings, and protocols to use.
 * Note that the actual implementation may support more than one
 * IOR in the case of GIOP with Location Forward messages.
 */
public interface CorbaContactInfoListFactory {
    /**
     * This will be called after the no-arg constructor before
     * create is called.
     */
    public void setORB(ORB orb);

    public CorbaContactInfoList create( IOR ior ) ;
}
