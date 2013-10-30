/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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
package java.rmi.server;

import java.rmi.*;
import sun.rmi.server.UnicastServerRef;
import sun.rmi.server.UnicastServerRef2;

/**
 * Used for exporting a remote object with JRMP and obtaining a stub
 * that communicates to the remote object.
 *
 * <p>For the constructors and static <code>exportObject</code> methods
 * below, the stub for a remote object being exported is obtained as
 * follows:
 *
 * <ul>
 *
 * <li>If the remote object is exported using the {@link
 * #exportObject(Remote) UnicastRemoteObject.exportObject(Remote)} method,
 * a stub class (typically pregenerated from the remote object's class
 * using the <code>rmic</code> tool) is loaded and an instance of that stub
 * class is constructed as follows.
 * <ul>
 *
 * <li>A "root class" is determined as follows:  if the remote object's
 * class directly implements an interface that extends {@link Remote}, then
 * the remote object's class is the root class; otherwise, the root class is
 * the most derived superclass of the remote object's class that directly
 * implements an interface that extends <code>Remote</code>.
 *
 * <li>The name of the stub class to load is determined by concatenating
 * the binary name of the root class with the suffix <code>"_Stub"</code>.
 *
 * <li>The stub class is loaded by name using the class loader of the root
 * class.  The stub class must extend {@link RemoteStub} and must have a
 * public constructor that has one parameter, of type {@link RemoteRef}.
 *
 * <li>Finally, an instance of the stub class is constructed with a
 * {@link RemoteRef}.
 * </ul>
 *
 * <li>If the appropriate stub class could not be found, or the stub class
 * could not be loaded, or a problem occurs creating the stub instance, a
 * {@link StubNotFoundException} is thrown.
 *
 * <li>For all other means of exporting:
 * <ul>
 *
 * <li>If the remote object's stub class (as defined above) could not be
 * loaded or the system property
 * <code>java.rmi.server.ignoreStubClasses</code> is set to
 * <code>"true"</code> (case insensitive), a {@link
 * java.lang.reflect.Proxy} instance is constructed with the following
 * properties:
 *
 * <ul>
 *
 * <li>The proxy's class is defined by the class loader of the remote
 * object's class.
 *
 * <li>The proxy implements all the remote interfaces implemented by the
 * remote object's class.
 *
 * <li>The proxy's invocation handler is a {@link
 * RemoteObjectInvocationHandler} instance constructed with a
 * {@link RemoteRef}.
 *
 * <li>If the proxy could not be created, a {@link StubNotFoundException}
 * will be thrown.
 * </ul>
 *
 * <li>Otherwise, an instance of the remote object's stub class (as
 * described above) is used as the stub.
 *
 * </ul>
 * </ul>
 *
 * <p>If an object is exported with the
 * {@link #exportObject(Remote) exportObject(Remote)}
 * or
 * {@link #exportObject(Remote, int) exportObject(Remote, port)}
 * methods, or if a subclass constructor invokes one of the
 * {@link #UnicastRemoteObject()}
 * or
 * {@link #UnicastRemoteObject(int) UnicastRemoteObject(port)}
 * constructors, the object is exported with a server socket created using the
 * {@link RMISocketFactory}
 * class.
 *
 * @implNote
 * <p>By default, server sockets created by the {@link RMISocketFactory} class
 * listen on all network interfaces. See the
 * {@link RMISocketFactory} class and the section
 * <a href="{@docRoot}/../platform/rmi/spec/rmi-server29.html">RMI Socket Factories</a>
 * in the
 * <a href="{@docRoot}/../platform/rmi/spec/rmiTOC.html">Java RMI Specification</a>.
 *
 * @author  Ann Wollrath
 * @author  Peter Jones
 * @since   JDK1.1
 **/
public class UnicastRemoteObject extends RemoteServer {

    /**
     * @serial port number on which to export object
     */
    private int port = 0;

    /**
     * @serial client-side socket factory (if any)
     */
    private RMIClientSocketFactory csf = null;

    /**
     * @serial server-side socket factory (if any) to use when
     * exporting object
     */
    private RMIServerSocketFactory ssf = null;

    /* indicate compatibility with JDK 1.1.x version of class */
    private static final long serialVersionUID = 4974527148936298033L;

    /**
     * Creates and exports a new UnicastRemoteObject object using an
     * anonymous port.
     * @throws RemoteException if failed to export object
     * @since JDK1.1
     */
    protected UnicastRemoteObject() throws RemoteException
    {
        this(0);
    }

    /**
     * Creates and exports a new UnicastRemoteObject object using the
     * particular supplied port.
     * @param port the port number on which the remote object receives calls
     * (if <code>port</code> is zero, an anonymous port is chosen)
     * @throws RemoteException if failed to export object
     * @since 1.2
     */
    protected UnicastRemoteObject(int port) throws RemoteException
    {
        this.port = port;
        exportObject((Remote) this, port);
    }

    /**
     * Creates and exports a new UnicastRemoteObject object using the
     * particular supplied port and socket factories.
     * @param port the port number on which the remote object receives calls
     * (if <code>port</code> is zero, an anonymous port is chosen)
     * @param csf the client-side socket factory for making calls to the
     * remote object
     * @param ssf the server-side socket factory for receiving remote calls
     * @throws RemoteException if failed to export object
     * @since 1.2
     */
    protected UnicastRemoteObject(int port,
                                  RMIClientSocketFactory csf,
                                  RMIServerSocketFactory ssf)
        throws RemoteException
    {
        this.port = port;
        this.csf = csf;
        this.ssf = ssf;
        exportObject((Remote) this, port, csf, ssf);
    }

    /**
     * Re-export the remote object when it is deserialized.
     */
    private void readObject(java.io.ObjectInputStream in)
        throws java.io.IOException, java.lang.ClassNotFoundException
    {
        in.defaultReadObject();
        reexport();
    }

    /**
     * Returns a clone of the remote object that is distinct from
     * the original.
     *
     * @exception CloneNotSupportedException if clone failed due to
     * a RemoteException.
     * @return the new remote object
     * @since JDK1.1
     */
    public Object clone() throws CloneNotSupportedException
    {
        try {
            UnicastRemoteObject cloned = (UnicastRemoteObject) super.clone();
            cloned.reexport();
            return cloned;
        } catch (RemoteException e) {
            throw new ServerCloneException("Clone failed", e);
        }
    }

    /*
     * Exports this UnicastRemoteObject using its initialized fields because
     * its creation bypassed running its constructors (via deserialization
     * or cloning, for example).
     */
    private void reexport() throws RemoteException
    {
        if (csf == null && ssf == null) {
            exportObject((Remote) this, port);
        } else {
            exportObject((Remote) this, port, csf, ssf);
        }
    }

    /**
     * Exports the remote object to make it available to receive incoming
     * calls using an anonymous port.
     * @param obj the remote object to be exported
     * @return remote object stub
     * @exception RemoteException if export fails
     * @since JDK1.1
     */
    public static RemoteStub exportObject(Remote obj)
        throws RemoteException
    {
        /*
         * Use UnicastServerRef constructor passing the boolean value true
         * to indicate that only a generated stub class should be used.  A
         * generated stub class must be used instead of a dynamic proxy
         * because the return value of this method is RemoteStub which a
         * dynamic proxy class cannot extend.
         */
        return (RemoteStub) exportObject(obj, new UnicastServerRef(true));
    }

    /**
     * Exports the remote object to make it available to receive incoming
     * calls, using the particular supplied port.
     * @param obj the remote object to be exported
     * @param port the port to export the object on
     * @return remote object stub
     * @exception RemoteException if export fails
     * @since 1.2
     */
    public static Remote exportObject(Remote obj, int port)
        throws RemoteException
    {
        return exportObject(obj, new UnicastServerRef(port));
    }

    /**
     * Exports the remote object to make it available to receive incoming
     * calls, using a transport specified by the given socket factory.
     * @param obj the remote object to be exported
     * @param port the port to export the object on
     * @param csf the client-side socket factory for making calls to the
     * remote object
     * @param ssf the server-side socket factory for receiving remote calls
     * @return remote object stub
     * @exception RemoteException if export fails
     * @since 1.2
     */
    public static Remote exportObject(Remote obj, int port,
                                      RMIClientSocketFactory csf,
                                      RMIServerSocketFactory ssf)
        throws RemoteException
    {

        return exportObject(obj, new UnicastServerRef2(port, csf, ssf));
    }

    /**
     * Removes the remote object, obj, from the RMI runtime. If
     * successful, the object can no longer accept incoming RMI calls.
     * If the force parameter is true, the object is forcibly unexported
     * even if there are pending calls to the remote object or the
     * remote object still has calls in progress.  If the force
     * parameter is false, the object is only unexported if there are
     * no pending or in progress calls to the object.
     *
     * @param obj the remote object to be unexported
     * @param force if true, unexports the object even if there are
     * pending or in-progress calls; if false, only unexports the object
     * if there are no pending or in-progress calls
     * @return true if operation is successful, false otherwise
     * @exception NoSuchObjectException if the remote object is not
     * currently exported
     * @since 1.2
     */
    public static boolean unexportObject(Remote obj, boolean force)
        throws java.rmi.NoSuchObjectException
    {
        return sun.rmi.transport.ObjectTable.unexportObject(obj, force);
    }

    /**
     * Exports the specified object using the specified server ref.
     */
    private static Remote exportObject(Remote obj, UnicastServerRef sref)
        throws RemoteException
    {
        // if obj extends UnicastRemoteObject, set its ref.
        if (obj instanceof UnicastRemoteObject) {
            ((UnicastRemoteObject) obj).ref = sref;
        }
        return sref.exportObject(obj, null, false);
    }
}
