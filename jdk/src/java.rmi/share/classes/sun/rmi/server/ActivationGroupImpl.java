/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.Activatable;
import java.rmi.activation.ActivationDesc;
import java.rmi.activation.ActivationException;
import java.rmi.activation.ActivationGroup;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationID;
import java.rmi.activation.UnknownObjectException;
import java.rmi.server.RMIClassLoader;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import sun.rmi.registry.RegistryImpl;

/**
 * The default activation group implementation.
 *
 * @author      Ann Wollrath
 * @since       1.2
 * @see         java.rmi.activation.ActivationGroup
 */
public class ActivationGroupImpl extends ActivationGroup {

    // use serialVersionUID from JDK 1.2.2 for interoperability
    private static final long serialVersionUID = 5758693559430427303L;

    /** maps persistent IDs to activated remote objects */
    private final Hashtable<ActivationID,ActiveEntry> active =
        new Hashtable<>();
    private boolean groupInactive = false;
    private final ActivationGroupID groupID;
    private final List<ActivationID> lockedIDs = new ArrayList<>();

    /**
     * Creates a default activation group implementation.
     *
     * @param id the group's identifier
     * @param data ignored
     */
    public ActivationGroupImpl(ActivationGroupID id, MarshalledObject<?> data)
        throws RemoteException
    {
        super(id);
        groupID = id;

        /*
         * Unexport activation group impl and attempt to export it on
         * an unshared anonymous port.  See 4692286.
         */
        unexportObject(this, true);
        RMIServerSocketFactory ssf = new ServerSocketFactoryImpl();
        UnicastRemoteObject.exportObject(this, 0, null, ssf);

        if (System.getSecurityManager() == null) {
            try {
                // Provide a default security manager.
                System.setSecurityManager(new SecurityManager());

            } catch (Exception e) {
                throw new RemoteException("unable to set security manager", e);
            }
        }
    }

    /**
     * Trivial server socket factory used to export the activation group
     * impl on an unshared port.
     */
    private static class ServerSocketFactoryImpl
        implements RMIServerSocketFactory
    {
        public ServerSocket createServerSocket(int port) throws IOException
        {
            RMISocketFactory sf = RMISocketFactory.getSocketFactory();
            if (sf == null) {
                sf = RMISocketFactory.getDefaultSocketFactory();
            }
            return sf.createServerSocket(port);
        }
    }

    /*
     * Obtains a lock on the ActivationID id before returning. Allows only one
     * thread at a time to hold a lock on a particular id.  If the lock for id
     * is in use, all requests for an equivalent (in the Object.equals sense)
     * id will wait for the id to be notified and use the supplied id as the
     * next lock. The caller of "acquireLock" must execute the "releaseLock"
     * method" to release the lock and "notifyAll" waiters for the id lock
     * obtained from this method.  The typical usage pattern is as follows:
     *
     * try {
     *    acquireLock(id);
     *    // do stuff pertaining to id...
     * } finally {
     *    releaseLock(id);
     *    checkInactiveGroup();
     * }
     */
    private void acquireLock(ActivationID id) {

        ActivationID waitForID;

        for (;;) {

            synchronized (lockedIDs) {
                int index = lockedIDs.indexOf(id);
                if (index < 0) {
                    lockedIDs.add(id);
                    return;
                } else {
                    waitForID = lockedIDs.get(index);
                }
            }

            synchronized (waitForID) {
                synchronized (lockedIDs) {
                    int index = lockedIDs.indexOf(waitForID);
                    if (index < 0) continue;
                    ActivationID actualID = lockedIDs.get(index);
                    if (actualID != waitForID)
                        /*
                         * don't wait on an id that won't be notified.
                         */
                        continue;
                }

                try {
                    waitForID.wait();
                } catch (InterruptedException ignore) {
                }
            }
        }

    }

    /*
     * Releases the id lock obtained via the "acquireLock" method and then
     * notifies all threads waiting on the lock.
     */
    private void releaseLock(ActivationID id) {
        synchronized (lockedIDs) {
            id = lockedIDs.remove(lockedIDs.indexOf(id));
        }

        synchronized (id) {
            id.notifyAll();
        }
    }

    /**
     * Creates a new instance of an activatable remote object. The
     * <code>Activator</code> calls this method to create an activatable
     * object in this group. This method should be idempotent; a call to
     * activate an already active object should return the previously
     * activated object.
     *
     * Note: this method assumes that the Activator will only invoke
     * newInstance for the same object in a serial fashion (i.e.,
     * the activator will not allow the group to see concurrent requests
     * to activate the same object.
     *
     * @param id the object's activation identifier
     * @param desc the object's activation descriptor
     * @return a marshalled object containing the activated object's stub
     */
    public MarshalledObject<? extends Remote>
                                      newInstance(final ActivationID id,
                                                  final ActivationDesc desc)
        throws ActivationException, RemoteException
    {
        RegistryImpl.checkAccess("ActivationInstantiator.newInstance");

        if (!groupID.equals(desc.getGroupID()))
            throw new ActivationException("newInstance in wrong group");

        try {
            acquireLock(id);
            synchronized (this) {
                if (groupInactive == true)
                    throw new InactiveGroupException("group is inactive");
            }

            ActiveEntry entry = active.get(id);
            if (entry != null)
                return entry.mobj;

            String className = desc.getClassName();

            final Class<? extends Remote> cl =
                RMIClassLoader.loadClass(desc.getLocation(), className)
                .asSubclass(Remote.class);
            Remote impl = null;

            final Thread t = Thread.currentThread();
            final ClassLoader savedCcl = t.getContextClassLoader();
            ClassLoader objcl = cl.getClassLoader();
            final ClassLoader ccl = covers(objcl, savedCcl) ? objcl : savedCcl;

            /*
             * Fix for 4164971: allow non-public activatable class
             * and/or constructor, create the activatable object in a
             * privileged block
             */
            try {
                /*
                 * The code below is in a doPrivileged block to
                 * protect against user code which code might have set
                 * a global socket factory (in which case application
                 * code would be on the stack).
                 */
                impl = AccessController.doPrivileged(
                      new PrivilegedExceptionAction<Remote>() {
                      public Remote run() throws InstantiationException,
                          NoSuchMethodException, IllegalAccessException,
                          InvocationTargetException
                      {
                          Constructor<? extends Remote> constructor =
                              cl.getDeclaredConstructor(
                                  ActivationID.class, MarshalledObject.class);
                          constructor.setAccessible(true);
                          try {
                              /*
                               * Fix for 4289544: make sure to set the
                               * context class loader to be the class
                               * loader of the impl class before
                               * constructing that class.
                               */
                              t.setContextClassLoader(ccl);
                              return constructor.newInstance(id,
                                                             desc.getData());
                          } finally {
                              t.setContextClassLoader(savedCcl);
                          }
                      }
                  });
            } catch (PrivilegedActionException pae) {
                Throwable e = pae.getException();

                // narrow the exception's type and rethrow it
                if (e instanceof InstantiationException) {
                    throw (InstantiationException) e;
                } else if (e instanceof NoSuchMethodException) {
                    throw (NoSuchMethodException) e;
                } else if (e instanceof IllegalAccessException) {
                    throw (IllegalAccessException) e;
                } else if (e instanceof InvocationTargetException) {
                    throw (InvocationTargetException) e;
                } else if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else if (e instanceof Error) {
                    throw (Error) e;
                }
            }

            entry = new ActiveEntry(impl);
            active.put(id, entry);
            return entry.mobj;

        } catch (NoSuchMethodException | NoSuchMethodError e) {
            /* user forgot to provide activatable constructor?
             * or code recompiled and user forgot to provide
             *  activatable constructor?
             */
            throw new ActivationException
                ("Activatable object must provide an activation"+
                 " constructor", e );

        } catch (InvocationTargetException e) {
            throw new ActivationException("exception in object constructor",
                                          e.getTargetException());

        } catch (Exception e) {
            throw new ActivationException("unable to activate object", e);
        } finally {
            releaseLock(id);
            checkInactiveGroup();
        }
    }


   /**
    * The group's <code>inactiveObject</code> method is called
    * indirectly via a call to the <code>Activatable.inactive</code>
    * method. A remote object implementation must call
    * <code>Activatable</code>'s <code>inactive</code> method when
    * that object deactivates (the object deems that it is no longer
    * active). If the object does not call
    * <code>Activatable.inactive</code> when it deactivates, the
    * object will never be garbage collected since the group keeps
    * strong references to the objects it creates. <p>
    *
    * The group's <code>inactiveObject</code> method
    * unexports the remote object from the RMI runtime so that the
    * object can no longer receive incoming RMI calls. This call will
    * only succeed if the object has no pending/executing calls. If
    * the object does have pending/executing RMI calls, then false
    * will be returned.
    *
    * If the object has no pending/executing calls, the object is
    * removed from the RMI runtime and the group informs its
    * <code>ActivationMonitor</code> (via the monitor's
    * <code>inactiveObject</code> method) that the remote object is
    * not currently active so that the remote object will be
    * re-activated by the activator upon a subsequent activation
    * request.
    *
    * @param id the object's activation identifier
    * @return true if the operation succeeds (the operation will
    * succeed if the object in currently known to be active and is
    * either already unexported or is currently exported and has no
    * pending/executing calls); false is returned if the object has
    * pending/executing calls in which case it cannot be deactivated
    * @exception UnknownObjectException if object is unknown (may already
    * be inactive)
    * @exception RemoteException if call informing monitor fails
    */
    public boolean inactiveObject(ActivationID id)
        throws ActivationException, UnknownObjectException, RemoteException
    {

        try {
            acquireLock(id);
            synchronized (this) {
                if (groupInactive == true)
                    throw new ActivationException("group is inactive");
            }

            ActiveEntry entry = active.get(id);
            if (entry == null) {
                // REMIND: should this be silent?
                throw new UnknownObjectException("object not active");
            }

            try {
                if (Activatable.unexportObject(entry.impl, false) == false)
                    return false;
            } catch (NoSuchObjectException allowUnexportedObjects) {
            }

            try {
                super.inactiveObject(id);
            } catch (UnknownObjectException allowUnregisteredObjects) {
            }

            active.remove(id);

        } finally {
            releaseLock(id);
            checkInactiveGroup();
        }

        return true;
    }

    /*
     * Determines if the group has become inactive and
     * marks it as such.
     */
    private void checkInactiveGroup() {
        boolean groupMarkedInactive = false;
        synchronized (this) {
            if (active.size() == 0 && lockedIDs.size() == 0 &&
                groupInactive == false)
            {
                groupInactive = true;
                groupMarkedInactive = true;
            }
        }

        if (groupMarkedInactive) {
            try {
                super.inactiveGroup();
            } catch (Exception ignoreDeactivateFailure) {
            }

            try {
                UnicastRemoteObject.unexportObject(this, true);
            } catch (NoSuchObjectException allowUnexportedGroup) {
            }
        }
    }

    /**
     * The group's <code>activeObject</code> method is called when an
     * object is exported (either by <code>Activatable</code> object
     * construction or an explicit call to
     * <code>Activatable.exportObject</code>. The group must inform its
     * <code>ActivationMonitor</code> that the object is active (via
     * the monitor's <code>activeObject</code> method) if the group
     * hasn't already done so.
     *
     * @param id the object's identifier
     * @param impl the remote object implementation
     * @exception UnknownObjectException if object is not registered
     * @exception RemoteException if call informing monitor fails
     */
    public void activeObject(ActivationID id, Remote impl)
        throws ActivationException, UnknownObjectException, RemoteException
    {

        try {
            acquireLock(id);
            synchronized (this) {
                if (groupInactive == true)
                    throw new ActivationException("group is inactive");
            }
            if (!active.contains(id)) {
                ActiveEntry entry = new ActiveEntry(impl);
                active.put(id, entry);
                // created new entry, so inform monitor of active object
                try {
                    super.activeObject(id, entry.mobj);
                } catch (RemoteException e) {
                    // daemon can still find it by calling newInstance
                }
            }
        } finally {
            releaseLock(id);
            checkInactiveGroup();
        }
    }

    /**
     * Entry in table for active object.
     */
    private static class ActiveEntry {
        Remote impl;
        MarshalledObject<Remote> mobj;

        ActiveEntry(Remote impl) throws ActivationException {
            this.impl =  impl;
            try {
                this.mobj = new MarshalledObject<Remote>(impl);
            } catch (IOException e) {
                throw new
                    ActivationException("failed to marshal remote object", e);
            }
        }
    }

    /**
     * Returns true if the first argument is either equal to, or is a
     * descendant of, the second argument.  Null is treated as the root of
     * the tree.
     */
    private static boolean covers(ClassLoader sub, ClassLoader sup) {
        if (sup == null) {
            return true;
        } else if (sub == null) {
            return false;
        }
        do {
            if (sub == sup) {
                return true;
            }
            sub = sub.getParent();
        } while (sub != null);
        return false;
    }
}
