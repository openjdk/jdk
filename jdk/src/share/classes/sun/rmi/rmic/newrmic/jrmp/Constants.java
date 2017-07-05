/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.rmic.newrmic.jrmp;

/**
 * Constants specific to the JRMP rmic generator.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author Peter Jones
 **/
final class Constants {

    private Constants() { throw new AssertionError(); }

    /*
     * fully-qualified names of types used by rmic
     */
    static final String REMOTE_OBJECT = "java.rmi.server.RemoteObject";
    static final String REMOTE_STUB = "java.rmi.server.RemoteStub";
    static final String REMOTE_REF = "java.rmi.server.RemoteRef";
    static final String OPERATION = "java.rmi.server.Operation";
    static final String SKELETON = "java.rmi.server.Skeleton";
    static final String SKELETON_MISMATCH_EXCEPTION =
        "java.rmi.server.SkeletonMismatchException";
    static final String REMOTE_CALL = "java.rmi.server.RemoteCall";
    static final String MARSHAL_EXCEPTION = "java.rmi.MarshalException";
    static final String UNMARSHAL_EXCEPTION = "java.rmi.UnmarshalException";
    static final String UNEXPECTED_EXCEPTION = "java.rmi.UnexpectedException";

    /*
     * stub protocol versions
     */
    enum StubVersion { V1_1, VCOMPAT, V1_2 };

    /*
     * serialVersionUID for all stubs that can use 1.2 protocol
     */
    static final long STUB_SERIAL_VERSION_UID = 2;

    /*
     * version number used to seed interface hash computation
     */
    static final int INTERFACE_HASH_STUB_VERSION = 1;
}
