/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package sun.rmi.rmic;

import sun.tools.java.Identifier;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public interface RMIConstants extends sun.rmi.rmic.Constants {

    /*
     * identifiers for RMI classes referenced by rmic
     */
    public static final Identifier idRemoteObject =
        Identifier.lookup("java.rmi.server.RemoteObject");
    public static final Identifier idRemoteStub =
        Identifier.lookup("java.rmi.server.RemoteStub");
    public static final Identifier idRemoteRef =
        Identifier.lookup("java.rmi.server.RemoteRef");
    public static final Identifier idOperation =
        Identifier.lookup("java.rmi.server.Operation");
    public static final Identifier idSkeleton =
        Identifier.lookup("java.rmi.server.Skeleton");
    public static final Identifier idSkeletonMismatchException =
        Identifier.lookup("java.rmi.server.SkeletonMismatchException");
    public static final Identifier idRemoteCall =
        Identifier.lookup("java.rmi.server.RemoteCall");
    public static final Identifier idMarshalException =
        Identifier.lookup("java.rmi.MarshalException");
    public static final Identifier idUnmarshalException =
        Identifier.lookup("java.rmi.UnmarshalException");
    public static final Identifier idUnexpectedException =
        Identifier.lookup("java.rmi.UnexpectedException");

    /*
     * stub protocol versions
     */
    public static final int STUB_VERSION_1_1  = 1;
    public static final int STUB_VERSION_FAT  = 2;
    public static final int STUB_VERSION_1_2  = 3;

    /** serialVersionUID for all stubs that can use 1.2 protocol */
    public static final long STUB_SERIAL_VERSION_UID = 2;

    /** version number used to seed interface hash computation */
    public static final int INTERFACE_HASH_STUB_VERSION = 1;
}
