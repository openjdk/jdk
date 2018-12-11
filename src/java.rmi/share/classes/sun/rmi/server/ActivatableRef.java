/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.server;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.*;
import java.rmi.activation.*;
import java.rmi.server.Operation;
import java.rmi.server.RMIClassLoader;
import java.rmi.server.RemoteCall;
import java.rmi.server.RemoteObject;
import java.rmi.server.RemoteObjectInvocationHandler;
import java.rmi.server.RemoteRef;
import java.rmi.server.RemoteStub;

@SuppressWarnings("deprecation")
public class ActivatableRef implements RemoteRef {

    private static final long serialVersionUID = 7579060052569229166L;

    protected ActivationID id;
    protected RemoteRef ref;
    transient boolean force = false;

    private static final int MAX_RETRIES = 3;
    private static final String versionComplaint =
        "activation requires 1.2 stubs";

    /**
     * Create a new (empty) ActivatableRef
     */
    public ActivatableRef()
    {}

    /**
     * Create a ActivatableRef with the specified id
     */
    public ActivatableRef(ActivationID id, RemoteRef ref)
    {
        this.id = id;
        this.ref = ref;
    }

    /**
     * Returns the stub for the remote object whose class is
     * specified in the activation descriptor. The ActivatableRef
     * in the resulting stub has its activation id set to the
     * activation id supplied as the second argument.
     */
    public static Remote getStub(ActivationDesc desc, ActivationID id)
        throws StubNotFoundException
    {
        String className = desc.getClassName();

        try {
            Class<?> cl =
                RMIClassLoader.loadClass(desc.getLocation(), className);
            RemoteRef clientRef = new ActivatableRef(id, null);
            return Util.createProxy(cl, clientRef, false);

        } catch (IllegalArgumentException e) {
            throw new StubNotFoundException(
                "class implements an illegal remote interface", e);

        } catch (ClassNotFoundException e) {
            throw new StubNotFoundException("unable to load class: " +
                                            className, e);
        } catch (MalformedURLException e) {
            throw new StubNotFoundException("malformed URL", e);
        }
    }

    /**
     * Invoke method on remote object. This method delegates remote
     * method invocation to the underlying ref type.  If the
     * underlying reference is not known (is null), then the object
     * must be activated first.  If an attempt at method invocation
     * fails, the object should force reactivation.  Method invocation
     * must preserve "at most once" call semantics.  In RMI, "at most
     * once" applies to parameter deserialization at the remote site
     * and the remote object's method execution.  "At most once" does
     * not apply to parameter serialization at the client so the
     * parameters of a call don't need to be buffered in anticipation
     * of call retry. Thus, a method call is only be retried if the
     * initial method invocation does not execute at all at the server
     * (including parameter deserialization).
     */
    public Object invoke(Remote obj,
                         java.lang.reflect.Method method,
                         Object[] params,
                         long opnum)
        throws Exception
    {

        boolean force = false;
        RemoteRef localRef;
        Exception exception = null;

        /*
         * Attempt object activation if active ref is unknown.
         * Throws a RemoteException if object can't be activated.
         */
        synchronized (this) {
            if (ref == null) {
                localRef = activate(force);
                force = true;
            } else {
                localRef = ref;
            }
        }

        for (int retries = MAX_RETRIES; retries > 0; retries--) {

            try {
                return localRef.invoke(obj, method, params, opnum);
            } catch (NoSuchObjectException e) {
                /*
                 * Object is not active in VM; retry call
                 */
                exception = e;
            } catch (ConnectException e) {
                /*
                 * Failure during connection setup; retry call
                 */
                exception = e;
            } catch (UnknownHostException e) {
                /*
                 * Failure during connection setup; retry call.
                 */
                exception = e;
            } catch (ConnectIOException e) {
                /*
                 * Failure reusing cached connection; retry call
                 */
                exception = e;
            } catch (MarshalException e) {
                /*
                 * Failure during parameter serialization; call may
                 * have reached server, so call retry not possible.
                 */
                throw e;
            } catch (ServerError e) {
                /*
                 * Call reached server; propagate remote exception.
                 */
                throw e;
            } catch (ServerException e) {
                /*
                 * Call reached server; propagate remote exception
                 */
                throw e;
            } catch (RemoteException e) {
                /*
                 * This is a catch-all for other RemoteExceptions.
                 * UnmarshalException being the only one relevant.
                 *
                 * StubNotFoundException should never show up because
                 * it is generally thrown when attempting to locate
                 * a stub.
                 *
                 * UnexpectedException should never show up because
                 * it is only thrown by a stub and would be wrapped
                 * in a ServerException if it was propagated by a
                 * remote call.
                 */
                synchronized (this) {
                    if (localRef == ref) {
                        ref = null;     // this may be overly conservative
                    }
                }

                throw e;
            }

            if (retries > 1) {
                /*
                 * Activate object, since object could not be reached.
                 */
                synchronized (this) {
                    if (localRef.remoteEquals(ref) || ref == null) {
                        RemoteRef newRef = activate(force);

                        if (newRef.remoteEquals(localRef) &&
                            exception instanceof NoSuchObjectException &&
                            force == false) {
                            /*
                             * If last exception was NoSuchObjectException,
                             * then old value of ref is definitely wrong,
                             * so make sure that it is different.
                             */
                            newRef = activate(true);
                        }

                        localRef = newRef;
                        force = true;
                    } else {
                        localRef = ref;
                        force = false;
                    }
                }
            }
        }

        /*
         * Retries unsuccessful, so throw last exception
         */
        throw exception;
    }

    /**
     * private method to obtain the ref for a call.
     */
    private synchronized RemoteRef getRef()
        throws RemoteException
    {
        if (ref == null) {
            ref = activate(false);
        }

        return ref;
    }

    /**
     * private method to activate the remote object.
     *
     * NOTE: the caller must be synchronized on "this" before
     * calling this method.
     */
    private RemoteRef activate(boolean force)
        throws RemoteException
    {
        assert Thread.holdsLock(this);

        ref = null;
        try {
            /*
             * Activate the object and retrieve the remote reference
             * from inside the stub returned as the result. Then
             * set this activatable ref's internal ref to be the
             * ref inside the ref of the stub. In more clear terms,
             * the stub returned from the activate call contains an
             * ActivatableRef. We need to set the ref in *this*
             * ActivatableRef to the ref inside the ActivatableRef
             * retrieved from the stub. The ref type embedded in the
             * ActivatableRef is typically a UnicastRef.
             */

            Remote proxy = id.activate(force);
            ActivatableRef newRef = null;

            if (proxy instanceof RemoteStub) {
                newRef = (ActivatableRef) ((RemoteStub) proxy).getRef();
            } else {
                /*
                 * Assume that proxy is an instance of a dynamic proxy
                 * class.  If that assumption is not correct, or either of
                 * the casts below fails, the resulting exception will be
                 * wrapped in an ActivateFailedException below.
                 */
                RemoteObjectInvocationHandler handler =
                    (RemoteObjectInvocationHandler)
                    Proxy.getInvocationHandler(proxy);
                newRef = (ActivatableRef) handler.getRef();
            }
            ref = newRef.ref;
            return ref;

        } catch (ConnectException e) {
            throw new ConnectException("activation failed", e);
        } catch (RemoteException e) {
            throw new ConnectIOException("activation failed", e);
        } catch (UnknownObjectException e) {
            throw new NoSuchObjectException("object not registered");
        } catch (ActivationException e) {
            throw new ActivateFailedException("activation failed", e);
        }
    }

    /**
     * This call is used by the old 1.1 stub protocol and is
     * unsupported since activation requires 1.2 stubs.
     */
    public synchronized RemoteCall newCall(RemoteObject obj,
                                           Operation[] ops,
                                           int opnum,
                                           long hash)
        throws RemoteException
    {
        throw new UnsupportedOperationException(versionComplaint);
    }

    /**
     * This call is used by the old 1.1 stub protocol and is
     * unsupported since activation requires 1.2 stubs.
     */
    public void invoke(RemoteCall call) throws Exception
    {
        throw new UnsupportedOperationException(versionComplaint);
    }

    /**
     * This call is used by the old 1.1 stub protocol and is
     * unsupported since activation requires 1.2 stubs.
     */
    public void done(RemoteCall call) throws RemoteException {
        throw new UnsupportedOperationException(versionComplaint);
    }

    /**
     * Returns the class of the ref type to be serialized
     */
    public String getRefClass(ObjectOutput out)
    {
        return "ActivatableRef";
    }

    /**
     * Write out external representation for remote ref.
     */
    public void writeExternal(ObjectOutput out) throws IOException
    {
        RemoteRef localRef = ref;

        out.writeObject(id);
        if (localRef == null) {
            out.writeUTF("");
        } else {
            out.writeUTF(localRef.getRefClass(out));
            localRef.writeExternal(out);
        }
    }

    /**
     * Read in external representation for remote ref.
     * @exception ClassNotFoundException If the class for an object
     * being restored cannot be found.
     */
    public void readExternal(ObjectInput in)
        throws IOException, ClassNotFoundException
    {
        id = (ActivationID)in.readObject();
        ref = null;
        String className = in.readUTF();

        if (className.isEmpty()) return;

        try {
            Class<?> refClass = Class.forName(RemoteRef.packagePrefix + "." +
                                              className);
            ref = (RemoteRef)refClass.newInstance();
            ref.readExternal(in);
        } catch (InstantiationException e) {
            throw new UnmarshalException("Unable to create remote reference",
                                         e);
        } catch (IllegalAccessException e) {
            throw new UnmarshalException("Illegal access creating remote reference");
        }
    }

    //----------------------------------------------------------------------;
    /**
     * Method from object, forward from RemoteObject
     */
    public String remoteToString() {
        return Util.getUnqualifiedName(getClass()) +
                " [remoteRef: " + ref + "]";
    }

    /**
     * default implementation of hashCode for remote objects
     */
    public int remoteHashCode() {
        return id.hashCode();
    }

    /** default implementation of equals for remote objects
     */
    public boolean remoteEquals(RemoteRef ref) {
        if (ref instanceof ActivatableRef)
            return id.equals(((ActivatableRef)ref).id);
        return false;
    }
}
