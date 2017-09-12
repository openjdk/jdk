/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

import java.rmi.*;
import java.rmi.server.*;

public class MyObjectImpl extends UnicastRemoteObject implements MyObject {
    private int clientNum = -1;
    private byte[] data = null;
    //private MyObjectFactory mof = null;
    private boolean AliveMyObjectsCounterWasIncremented = false;

    public MyObjectImpl() throws RemoteException {
        super();
    }

    public MyObjectImpl(int c, int size) //MyObjectFactory mof, int c, int size)
                        throws RemoteException {
        super();
        //this.mof = mof;
        this.clientNum = c;
        this.data = new byte[size];
        //mof.incAliveMyObjects(1);
        AliveMyObjectsCounterWasIncremented = true;
    }

    public void method1(MyObject obj) throws RemoteException {
    }

    public void method2(MyObject[] objs) throws RemoteException {
    }

    public void method3() throws RemoteException {
    }

    protected void finalize() throws Throwable {
        if(AliveMyObjectsCounterWasIncremented)
            ; //mof.decAliveMyObjects(1);
        super.finalize();
    }
}
