/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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


package org.omg.CORBA.portable;

/**
 This class is part of the local stub API, the purpose of which is to provide
 high performance calls for collocated clients and servers
 (i.e. clients and servers residing in the same Java VM).
 The local stub API is supported via three additional methods on
 <code>ObjectImpl</code> and <code>Delegate</code>.
 ORB vendors may subclass this class to return additional
 request state that may be required by their implementations.
 @see ObjectImpl
 @see Delegate
*/

public class ServantObject
{
    /** The real servant. The local stub may cast this field to the expected type, and then
     * invoke the operation directly. Note, the object may or may not be the actual servant
     * instance.
     */
    public java.lang.Object servant;
}
