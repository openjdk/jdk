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

package java.rmi.activation;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;
import java.rmi.server.RemoteObject;
import java.rmi.server.RemoteObjectInvocationHandler;
import java.rmi.server.RemoteRef;
import java.rmi.server.UID;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permissions;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;

/**
 * Activation makes use of special identifiers to denote remote
 * objects that can be activated over time. An activation identifier
 * (an instance of the class <code>ActivationID</code>) contains several
 * pieces of information needed for activating an object:
 * <ul>
 * <li> a remote reference to the object's activator (a {@link
 * java.rmi.server.RemoteRef RemoteRef}
 * instance), and
 * <li> a unique identifier (a {@link java.rmi.server.UID UID}
 * instance) for the object. </ul> <p>
 *
 * An activation identifier for an object can be obtained by registering
 * an object with the activation system. Registration is accomplished
 * in a few ways: <ul>
 * <li>via the <code>Activatable.register</code> method
 * <li>via the first <code>Activatable</code> constructor (that takes
 * three arguments and both registers and exports the object, and
 * <li>via the first <code>Activatable.exportObject</code> method
 * that takes the activation descriptor, object and port as arguments;
 * this method both registers and exports the object. </ul>
 *
 * @author      Ann Wollrath
 * @see         Activatable
 * @since       1.2
 */
public class ActivationID implements Serializable {
    /**
     * the object's activator
     */
    private transient Activator activator;

    /**
     * the object's unique id
     */
    private transient UID uid = new UID();

    /** indicate compatibility with the Java 2 SDK v1.2 version of class */
    private static final long serialVersionUID = -4608673054848209235L;

    /** an AccessControlContext with no permissions */
    private static final AccessControlContext NOPERMS_ACC;
    static {
        Permissions perms = new Permissions();
        ProtectionDomain[] pd = { new ProtectionDomain(null, perms) };
        NOPERMS_ACC = new AccessControlContext(pd);
    }

    /**
     * The constructor for <code>ActivationID</code> takes a single
     * argument, activator, that specifies a remote reference to the
     * activator responsible for activating the object associated with
     * this identifier. An instance of <code>ActivationID</code> is globally
     * unique.
     *
     * @param activator reference to the activator responsible for
     * activating the object
     * @throws UnsupportedOperationException if and only if activation is
     *         not supported by this implementation
     * @since 1.2
     */
    public ActivationID(Activator activator) {
        this.activator = activator;
    }

    /**
     * Activate the object for this id.
     *
     * @param force if true, forces the activator to contact the group
     * when activating the object (instead of returning a cached reference);
     * if false, returning a cached value is acceptable.
     * @return the reference to the active remote object
     * @exception ActivationException if activation fails
     * @exception UnknownObjectException if the object is unknown
     * @exception RemoteException if remote call fails
     * @since 1.2
     */
    public Remote activate(boolean force)
        throws ActivationException, UnknownObjectException, RemoteException
    {
        try {
            MarshalledObject<? extends Remote> mobj =
                activator.activate(this, force);
            return AccessController.doPrivileged(
                new PrivilegedExceptionAction<Remote>() {
                    public Remote run() throws IOException, ClassNotFoundException {
                        return mobj.get();
                    }
                }, NOPERMS_ACC);
        } catch (PrivilegedActionException pae) {
            Exception ex = pae.getException();
            if (ex instanceof RemoteException) {
                throw (RemoteException) ex;
            } else {
                throw new UnmarshalException("activation failed", ex);
            }
        }

    }

    /**
     * Returns a hashcode for the activation id.  Two identifiers that
     * refer to the same remote object will have the same hash code.
     *
     * @see java.util.Hashtable
     * @since 1.2
     */
    public int hashCode() {
        return uid.hashCode();
    }

    /**
     * Compares two activation ids for content equality.
     * Returns true if both of the following conditions are true:
     * 1) the unique identifiers equivalent (by content), and
     * 2) the activator specified in each identifier
     *    refers to the same remote object.
     *
     * @param   obj     the Object to compare with
     * @return  true if these Objects are equal; false otherwise.
     * @see             java.util.Hashtable
     * @since 1.2
     */
    public boolean equals(Object obj) {
        if (obj instanceof ActivationID) {
            ActivationID id = (ActivationID) obj;
            return (uid.equals(id.uid) && activator.equals(id.activator));
        } else {
            return false;
        }
    }

    /**
     * <code>writeObject</code> for custom serialization.
     *
     * <p>This method writes this object's serialized form for
     * this class as follows:
     *
     * <p>The <code>writeObject</code> method is invoked on
     * <code>out</code> passing this object's unique identifier
     * (a {@link java.rmi.server.UID UID} instance) as the argument.
     *
     * <p>Next, the {@link
     * java.rmi.server.RemoteRef#getRefClass(java.io.ObjectOutput)
     * getRefClass} method is invoked on the activator's
     * <code>RemoteRef</code> instance to obtain its external ref
     * type name.  Next, the <code>writeUTF</code> method is
     * invoked on <code>out</code> with the value returned by
     * <code>getRefClass</code>, and then the
     * <code>writeExternal</code> method is invoked on the
     * <code>RemoteRef</code> instance passing <code>out</code>
     * as the argument.
     *
     * @serialData The serialized data for this class comprises a
     * <code>java.rmi.server.UID</code> (written with
     * <code>ObjectOutput.writeObject</code>) followed by the
     * external ref type name of the activator's
     * <code>RemoteRef</code> instance (a string written with
     * <code>ObjectOutput.writeUTF</code>), followed by the
     * external form of the <code>RemoteRef</code> instance as
     * written by its <code>writeExternal</code> method.
     *
     * <p>The external ref type name of the
     * <code>RemoteRef</Code> instance is
     * determined using the definitions of external ref type
     * names specified in the {@link java.rmi.server.RemoteObject
     * RemoteObject} <code>writeObject</code> method
     * <b>serialData</b> specification.  Similarly, the data
     * written by the <code>writeExternal</code> method and read
     * by the <code>readExternal</code> method of
     * <code>RemoteRef</code> implementation classes
     * corresponding to each of the defined external ref type
     * names is specified in the {@link
     * java.rmi.server.RemoteObject RemoteObject}
     * <code>writeObject</code> method <b>serialData</b>
     * specification.
     **/
    private void writeObject(ObjectOutputStream out)
        throws IOException, ClassNotFoundException
    {
        out.writeObject(uid);

        RemoteRef ref;
        if (activator instanceof RemoteObject) {
            ref = ((RemoteObject) activator).getRef();
        } else if (Proxy.isProxyClass(activator.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(activator);
            if (!(handler instanceof RemoteObjectInvocationHandler)) {
                throw new InvalidObjectException(
                    "unexpected invocation handler");
            }
            ref = ((RemoteObjectInvocationHandler) handler).getRef();

        } else {
            throw new InvalidObjectException("unexpected activator type");
        }
        out.writeUTF(ref.getRefClass(out));
        ref.writeExternal(out);
    }

    /**
     * <code>readObject</code> for custom serialization.
     *
     * <p>This method reads this object's serialized form for this
     * class as follows:
     *
     * <p>The <code>readObject</code> method is invoked on
     * <code>in</code> to read this object's unique identifier
     * (a {@link java.rmi.server.UID UID} instance).
     *
     * <p>Next, the <code>readUTF</code> method is invoked on
     * <code>in</code> to read the external ref type name of the
     * <code>RemoteRef</code> instance for this object's
     * activator.  Next, the <code>RemoteRef</code>
     * instance is created of an implementation-specific class
     * corresponding to the external ref type name (returned by
     * <code>readUTF</code>), and the <code>readExternal</code>
     * method is invoked on that <code>RemoteRef</code> instance
     * to read the external form corresponding to the external
     * ref type name.
     *
     * <p>Note: If the external ref type name is
     * <code>"UnicastRef"</code>, <code>"UnicastServerRef"</code>,
     * <code>"UnicastRef2"</code>, <code>"UnicastServerRef2"</code>,
     * or <code>"ActivatableRef"</code>, a corresponding
     * implementation-specific class must be found, and its
     * <code>readExternal</code> method must read the serial data
     * for that external ref type name as specified to be written
     * in the <b>serialData</b> documentation for this class.
     * If the external ref type name is any other string (of non-zero
     * length), a <code>ClassNotFoundException</code> will be thrown,
     * unless the implementation provides an implementation-specific
     * class corresponding to that external ref type name, in which
     * case the <code>RemoteRef</code> will be an instance of
     * that implementation-specific class.
     */
    private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException
    {
        uid = (UID)in.readObject();

        try {
            Class<? extends RemoteRef> refClass =
                Class.forName(RemoteRef.packagePrefix + "." + in.readUTF())
                .asSubclass(RemoteRef.class);
            @SuppressWarnings("deprecation")
            RemoteRef ref = refClass.newInstance();
            ref.readExternal(in);
            activator = (Activator)
                Proxy.newProxyInstance(Activator.class.getClassLoader(),
                                       new Class<?>[] { Activator.class },
                                       new RemoteObjectInvocationHandler(ref));
        } catch (InstantiationException e) {
            throw (IOException)
                new InvalidObjectException(
                    "Unable to create remote reference").initCause(e);
        } catch (IllegalAccessException e) {
            throw (IOException)
                new InvalidObjectException(
                    "Unable to create remote reference").initCause(e);
        }
    }
}
