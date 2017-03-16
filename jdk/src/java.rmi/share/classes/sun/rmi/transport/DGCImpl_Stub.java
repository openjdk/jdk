/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.transport;

import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.rmi.dgc.Lease;
import java.rmi.dgc.VMID;
import java.rmi.server.UID;
import java.security.AccessController;
import java.security.PrivilegedAction;

import sun.rmi.server.UnicastRef;
import sun.rmi.transport.tcp.TCPConnection;

/**
 * Stubs to invoke DGC remote methods.
 * Originally generated from RMIC but frozen to insert serialFilter.
 */
@SuppressWarnings({"deprecation", "serial"})
public final class DGCImpl_Stub
        extends java.rmi.server.RemoteStub
        implements java.rmi.dgc.DGC {
    private static final java.rmi.server.Operation[] operations = {
            new java.rmi.server.Operation("void clean(java.rmi.server.ObjID[], long, java.rmi.dgc.VMID, boolean)"),
            new java.rmi.server.Operation("java.rmi.dgc.Lease dirty(java.rmi.server.ObjID[], long, java.rmi.dgc.Lease)")
    };

    private static final long interfaceHash = -669196253586618813L;

    /** Registry max depth of remote invocations. **/
    private static int DGCCLIENT_MAX_DEPTH = 6;

    /** Registry maximum array size in remote invocations. **/
    private static int DGCCLIENT_MAX_ARRAY_SIZE = 10000;

    // constructors
    public DGCImpl_Stub() {
        super();
    }

    public DGCImpl_Stub(java.rmi.server.RemoteRef ref) {
        super(ref);
    }

    // methods from remote interfaces

    // implementation of clean(ObjID[], long, VMID, boolean)
    public void clean(java.rmi.server.ObjID[] $param_arrayOf_ObjID_1, long $param_long_2, java.rmi.dgc.VMID $param_VMID_3, boolean $param_boolean_4)
            throws java.rmi.RemoteException {
        try {
            java.rmi.server.RemoteCall call = ref.newCall((java.rmi.server.RemoteObject) this, operations, 0, interfaceHash);
            try {
                java.io.ObjectOutput out = call.getOutputStream();
                out.writeObject($param_arrayOf_ObjID_1);
                out.writeLong($param_long_2);
                out.writeObject($param_VMID_3);
                out.writeBoolean($param_boolean_4);
            } catch (java.io.IOException e) {
                throw new java.rmi.MarshalException("error marshalling arguments", e);
            }
            ref.invoke(call);
            ref.done(call);
        } catch (java.lang.RuntimeException e) {
            throw e;
        } catch (java.rmi.RemoteException e) {
            throw e;
        } catch (java.lang.Exception e) {
            throw new java.rmi.UnexpectedException("undeclared checked exception", e);
        }
    }

    // implementation of dirty(ObjID[], long, Lease)
    public java.rmi.dgc.Lease dirty(java.rmi.server.ObjID[] $param_arrayOf_ObjID_1, long $param_long_2, java.rmi.dgc.Lease $param_Lease_3)
            throws java.rmi.RemoteException {
        try {
            java.rmi.server.RemoteCall call = ref.newCall((java.rmi.server.RemoteObject) this, operations, 1, interfaceHash);
            try {
                java.io.ObjectOutput out = call.getOutputStream();
                out.writeObject($param_arrayOf_ObjID_1);
                out.writeLong($param_long_2);
                out.writeObject($param_Lease_3);
            } catch (java.io.IOException e) {
                throw new java.rmi.MarshalException("error marshalling arguments", e);
            }
            ref.invoke(call);
            java.rmi.dgc.Lease $result;
            Connection connection = ((StreamRemoteCall) call).getConnection();
            try {
                java.io.ObjectInput in = call.getInputStream();

                if (in instanceof ObjectInputStream) {
                    /**
                     * Set a filter on the stream for the return value.
                     */
                    ObjectInputStream ois = (ObjectInputStream) in;
                    AccessController.doPrivileged((PrivilegedAction<Void>)() -> {
                        ois.setObjectInputFilter(DGCImpl_Stub::leaseFilter);
                        return null;
                    });
                }
                $result = (java.rmi.dgc.Lease) in.readObject();
            } catch (java.io.IOException | java.lang.ClassNotFoundException e) {
                if (connection instanceof TCPConnection) {
                    // Modified to prevent re-use of the connection after an exception
                    ((TCPConnection) connection).getChannel().free(connection, false);
                }
                throw new java.rmi.UnmarshalException("error unmarshalling return", e);
            } finally {
                ref.done(call);
            }
            return $result;
        } catch (java.lang.RuntimeException e) {
            throw e;
        } catch (java.rmi.RemoteException e) {
            throw e;
        } catch (java.lang.Exception e) {
            throw new java.rmi.UnexpectedException("undeclared checked exception", e);
        }
    }

    /**
     * ObjectInputFilter to filter DGCClient return value (a Lease).
     * The list of acceptable classes is very short and explicit.
     * The depth and array sizes are limited.
     *
     * @param filterInfo access to class, arrayLength, etc.
     * @return  {@link ObjectInputFilter.Status#ALLOWED} if allowed,
     *          {@link ObjectInputFilter.Status#REJECTED} if rejected,
     *          otherwise {@link ObjectInputFilter.Status#UNDECIDED}
     */
    private static ObjectInputFilter.Status leaseFilter(ObjectInputFilter.FilterInfo filterInfo) {

        if (filterInfo.depth() > DGCCLIENT_MAX_DEPTH) {
            return ObjectInputFilter.Status.REJECTED;
        }
        Class<?> clazz = filterInfo.serialClass();
        if (clazz != null) {
            while (clazz.isArray()) {
                if (filterInfo.arrayLength() >= 0 && filterInfo.arrayLength() > DGCCLIENT_MAX_ARRAY_SIZE) {
                    return ObjectInputFilter.Status.REJECTED;
                }
                // Arrays are allowed depending on the component type
                clazz = clazz.getComponentType();
            }
            if (clazz.isPrimitive()) {
                // Arrays of primitives are allowed
                return ObjectInputFilter.Status.ALLOWED;
            }
            return (clazz == UID.class ||
                    clazz == VMID.class ||
                    clazz == Lease.class)
                    ? ObjectInputFilter.Status.ALLOWED
                    : ObjectInputFilter.Status.REJECTED;
        }
        // Not a class, not size limited
        return ObjectInputFilter.Status.UNDECIDED;
    }

}
