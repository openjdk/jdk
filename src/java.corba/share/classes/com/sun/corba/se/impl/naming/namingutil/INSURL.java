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

package com.sun.corba.se.impl.naming.namingutil;

/**
 * INS URL is a generic interface for two different types of URL's specified
 * in INS spec.
 *
 * @author Hemanth
 */
public interface INSURL {
    public boolean getRIRFlag( );

    // There can be one or more Endpoint's in the URL, so the return value is
    // a List
    public java.util.List getEndpointInfo( );

    public String getKeyString( );

    public String getStringifiedName( );

    // This method will return true only in CorbanameURL, It is provided because
    // corbaname: URL needs special handling.
    public boolean isCorbanameURL( );

    // A debug method, which is not required for normal operation
    public void dPrint( );
}
