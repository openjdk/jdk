/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

import java.rmi.activation.Activatable;
import java.rmi.RemoteException;
import java.rmi.activation.ActivationID;
import java.rmi.MarshalledObject;
import java.net.URL;

public class ActivatableImpl extends Activatable implements MyRMI {

    private boolean classLoaderOk = false;

    public ActivatableImpl(ActivationID id, MarshalledObject mobj)
        throws RemoteException
    {
        super(id, 0);

        ClassLoader thisLoader = ActivatableImpl.class.getClassLoader();
        ClassLoader ccl = Thread.currentThread().getContextClassLoader();

        System.err.println("implLoader: " + thisLoader);
        System.err.println("ccl: " + ccl);

        /*
         * the context class loader is the ccl from when this object
         * was exported.  If the bug has been fixed, the ccl will be
         * the same as the class loader of this class.
         */
        classLoaderOk = (thisLoader == ccl);
    }

    public boolean classLoaderOk() throws RemoteException {
        return classLoaderOk;
    }

    public void shutdown() throws Exception {
        ActivationLibrary.deactivate(this, getID());
    }
}
