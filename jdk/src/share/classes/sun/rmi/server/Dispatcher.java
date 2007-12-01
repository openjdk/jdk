/*
 * Copyright 1996 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package sun.rmi.server;

import java.rmi.Remote;
import java.rmi.server.RemoteCall;

/**
 * The Dispatcher interface allows the transport to make
 * the upcall to the server side remote reference.
 */
public interface Dispatcher {

    /**
     * Call to dispatch to the remote object (on the server side).
     * The up-call to the server and the marshaling of return result
     * (or exception) should be handled before returning from this
     * method.
     * @param obj the target remote object for the call
     * @param call the "remote call" from which operation and
     * method arguments can be obtained.
     * @exception RemoteException unable to marshal
     * return result
     */
    void dispatch(Remote obj, RemoteCall call)
        throws java.io.IOException;
}
