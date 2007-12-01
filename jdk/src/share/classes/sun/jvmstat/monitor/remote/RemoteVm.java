/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.jvmstat.monitor.remote;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface for accessing the instrumentation exported by a
 * Java Virtual Machine running on a remote host.
 *
 * @author Brian Doherty
 * @since 1.5
 */
public interface RemoteVm extends Remote {

    /**
     * Interface to get the bytes associated with the instrumentation
     * for the remote Java Virtual Machine.
     *
     * @return byte[] - a byte array containing the current bytes
     *                  for the instrumentation exported by the
     *                  remote Java Virtual Machine.
     * @throws RemoteException Thrown on any communication error
     */
    byte[] getBytes() throws RemoteException;

    /**
     * Interface to get the the size of the instrumentation buffer
     * for the target Java Virtual Machine.
     *
     * @return int - the size of the instrumentation buffer for the
     *               remote Java Virtual Machine.
     * @throws RemoteException Thrown on any communication error
     */
    int getCapacity() throws RemoteException;

    /**
     * Interface to return the Local Virtual Machine Identifier for
     * the remote Java Virtual Machine. The Local Virtual Machine
     * Identifier is also know as the <em>lvmid</em>.
     *
     * @throws RemoteException Thrown on any communication error
     */
    int getLocalVmId() throws RemoteException;

    /**
     * Interface to detach from the remote Java Virtual Machine.
     *
     * @throws RemoteException Thrown on any communication error
     */
    void detach() throws RemoteException;
}
