/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
import java.rmi.activation.*;

public class ExtLoadedImpl implements CheckLoader {

    public ExtLoadedImpl(ActivationID id, MarshalledObject obj)
        throws ActivationException, RemoteException
    {
        Activatable.exportObject(this, id, 0);
    }

    public boolean isCorrectContextLoader() {
        ClassLoader contextLoader =
            Thread.currentThread().getContextClassLoader();
        ClassLoader implLoader = this.getClass().getClassLoader();
        if (contextLoader == implLoader) {
            System.err.println("contextLoader same as implLoader");
            return false;
        } else if (contextLoader.getParent() == implLoader) {
            System.err.println("contextLoader is child of implLoader");
            return true;
        } else {
            System.err.println("unknown loader relationship");
            return false;
        }
    }
}
