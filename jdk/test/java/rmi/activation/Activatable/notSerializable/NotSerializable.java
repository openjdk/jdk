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

/* @test
 * @bug 4460983
 * @summary This test verifies that an instance of Activatable cannot
 * be serialized (without implicit impl-to-stub replacement), because
 * it cannot be meaningfully deserialized anyway.
 * See also test/java/rmi/server/RemoteObject/unrecognizedRefType.
 * @author Peter Jones
 *
 * @run main/othervm NotSerializable
 */

import java.io.ByteArrayOutputStream;
import java.io.NotSerializableException;
import java.io.ObjectInput;
import java.io.ObjectOutputStream;
import java.io.ObjectOutput;
import java.lang.reflect.Method;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.Operation;
import java.rmi.server.RemoteCall;
import java.rmi.server.RemoteObject;
import java.rmi.server.RemoteRef;
import java.rmi.server.RemoteStub;
import java.rmi.activation.Activatable;
import java.rmi.activation.ActivationID;
import java.rmi.activation.Activator;

public class NotSerializable {

    public static void main(String[] args) throws Exception {
        System.err.println("\nRegression test for bug 4460983\n");

        Activatable act = new FakeActivatable();
        try {
            ObjectOutputStream out =
                new ObjectOutputStream(new ByteArrayOutputStream());
            try {
                out.writeObject(act);
                throw new RuntimeException("TEST FAILED: " +
                    "Activatable instance successfully serialized");
            } catch (NotSerializableException e) {
                System.err.println("NotSerializableException as expected:");
                e.printStackTrace();
            } // other exceptions cause test failure

            System.err.println("TEST PASSED");
        } finally {
            try {
                Activatable.unexportObject(act, true);
            } catch (NoSuchObjectException e) {
            }
        }
    }

    private static class FakeActivatable extends Activatable {
        FakeActivatable() throws RemoteException {
            super(new ActivationID(new FakeActivator()), 0);
        }
    }

    private static class FakeActivator
        extends RemoteStub implements Activator
    {
        FakeActivator() {
            super(new FakeRemoteRef("FakeRef"));
        }

        public MarshalledObject activate(ActivationID id, boolean force) {
            return null;
        }
    }

    private static class FakeRemoteRef implements RemoteRef {
        private final String refType;

        FakeRemoteRef(String refType) {
            this.refType = refType;
        }

        public Object invoke(Remote obj,
                             Method method,
                             Object[] params,
                             long opnum)
        {
            throw new UnsupportedOperationException();
        }

        public RemoteCall newCall(RemoteObject obj,
                                  Operation[] op,
                                  int opnum,
                                  long hash)
        {
            throw new UnsupportedOperationException();
        }

        public void invoke(RemoteCall call) {
            throw new UnsupportedOperationException();
        }

        public void done(RemoteCall call) {
            throw new UnsupportedOperationException();
        }

        public String getRefClass(java.io.ObjectOutput out) {
            return refType;
        }

        public int remoteHashCode() { return hashCode(); }
        public boolean remoteEquals(RemoteRef obj) { return equals(obj); }
        public String remoteToString() { return toString(); }

        public void readExternal(ObjectInput in) {
            throw new UnsupportedOperationException();
        }

        public void writeExternal(ObjectOutput out) {
            // no data to write
        }
    }
}
