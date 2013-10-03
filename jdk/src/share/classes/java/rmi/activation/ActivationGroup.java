/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.rmi.MarshalledObject;
import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.UnknownGroupException;
import java.rmi.activation.UnknownObjectException;
import java.rmi.server.RMIClassLoader;
import java.rmi.server.UnicastRemoteObject;
import java.security.AccessController;
import sun.security.action.GetIntegerAction;

/**
 * An <code>ActivationGroup</code> is responsible for creating new
 * instances of "activatable" objects in its group, informing its
 * <code>ActivationMonitor</code> when either: its object's become
 * active or inactive, or the group as a whole becomes inactive. <p>
 *
 * An <code>ActivationGroup</code> is <i>initially</i> created in one
 * of several ways: <ul>
 * <li>as a side-effect of creating an <code>ActivationDesc</code>
 *     without an explicit <code>ActivationGroupID</code> for the
 *     first activatable object in the group, or
 * <li>via the <code>ActivationGroup.createGroup</code> method
 * <li>as a side-effect of activating the first object in a group
 *     whose <code>ActivationGroupDesc</code> was only registered.</ul><p>
 *
 * Only the activator can <i>recreate</i> an
 * <code>ActivationGroup</code>.  The activator spawns, as needed, a
 * separate VM (as a child process, for example) for each registered
 * activation group and directs activation requests to the appropriate
 * group. It is implementation specific how VMs are spawned. An
 * activation group is created via the
 * <code>ActivationGroup.createGroup</code> static method. The
 * <code>createGroup</code> method has two requirements on the group
 * to be created: 1) the group must be a concrete subclass of
 * <code>ActivationGroup</code>, and 2) the group must have a
 * constructor that takes two arguments:
 *
 * <ul>
 * <li> the group's <code>ActivationGroupID</code>, and
 * <li> the group's initialization data (in a
 *      <code>java.rmi.MarshalledObject</code>)</ul><p>
 *
 * When created, the default implementation of
 * <code>ActivationGroup</code> will override the system properties
 * with the properties requested when its
 * <code>ActivationGroupDesc</code> was created, and will set a
 * <code>java.rmi.RMISecurityManager</code> as the default system
 * security manager.  If your application requires specific properties
 * to be set when objects are activated in the group, the application
 * should create a special <code>Properties</code> object containing
 * these properties, then create an <code>ActivationGroupDesc</code>
 * with the <code>Properties</code> object, and use
 * <code>ActivationGroup.createGroup</code> before creating any
 * <code>ActivationDesc</code>s (before the default
 * <code>ActivationGroupDesc</code> is created).  If your application
 * requires the use of a security manager other than
 * <code>java.rmi.RMISecurityManager</code>, in the
 * ActivativationGroupDescriptor properties list you can set
 * <code>java.security.manager</code> property to the name of the security
 * manager you would like to install.
 *
 * @author      Ann Wollrath
 * @see         ActivationInstantiator
 * @see         ActivationGroupDesc
 * @see         ActivationGroupID
 * @since       1.2
 */
public abstract class ActivationGroup
        extends UnicastRemoteObject
        implements ActivationInstantiator
{
    /**
     * @serial the group's identifier
     */
    private ActivationGroupID groupID;

    /**
     * @serial the group's monitor
     */
    private ActivationMonitor monitor;

    /**
     * @serial the group's incarnation number
     */
    private long incarnation;

    /** the current activation group for this VM */
    private static ActivationGroup currGroup;
    /** the current group's identifier */
    private static ActivationGroupID currGroupID;
    /** the current group's activation system */
    private static ActivationSystem currSystem;
    /** used to control a group being created only once */
    private static boolean canCreate = true;

    /** indicate compatibility with the Java 2 SDK v1.2 version of class */
    private static final long serialVersionUID = -7696947875314805420L;

    /**
     * Constructs an activation group with the given activation group
     * identifier.  The group is exported as a
     * <code>java.rmi.server.UnicastRemoteObject</code>.
     *
     * @param   groupID the group's identifier
     * @throws  RemoteException if this group could not be exported
     * @throws  UnsupportedOperationException if and only if activation is
     *          not supported by this implementation
     * @since   1.2
     */
    protected ActivationGroup(ActivationGroupID groupID)
        throws RemoteException
    {
        // call super constructor to export the object
        super();
        this.groupID = groupID;
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
     * <p>The group's <code>inactiveObject</code> method unexports the
     * remote object from the RMI runtime so that the object can no
     * longer receive incoming RMI calls. An object will only be unexported
     * if the object has no pending or executing calls.
     * The subclass of <code>ActivationGroup</code> must override this
     * method and unexport the object. <p>
     *
     * <p>After removing the object from the RMI runtime, the group
     * must inform its <code>ActivationMonitor</code> (via the monitor's
     * <code>inactiveObject</code> method) that the remote object is
     * not currently active so that the remote object will be
     * re-activated by the activator upon a subsequent activation
     * request.<p>
     *
     * <p>This method simply informs the group's monitor that the object
     * is inactive.  It is up to the concrete subclass of ActivationGroup
     * to fulfill the additional requirement of unexporting the object. <p>
     *
     * @param id the object's activation identifier
     * @return true if the object was successfully deactivated; otherwise
     *         returns false.
     * @exception UnknownObjectException if object is unknown (may already
     * be inactive)
     * @exception RemoteException if call informing monitor fails
     * @exception ActivationException if group is inactive
     * @since 1.2
     */
    public boolean inactiveObject(ActivationID id)
        throws ActivationException, UnknownObjectException, RemoteException
    {
        getMonitor().inactiveObject(id);
        return true;
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
     * @param obj the remote object implementation
     * @exception UnknownObjectException if object is not registered
     * @exception RemoteException if call informing monitor fails
     * @exception ActivationException if group is inactive
     * @since 1.2
     */
    public abstract void activeObject(ActivationID id, Remote obj)
        throws ActivationException, UnknownObjectException, RemoteException;

    /**
     * Create and set the activation group for the current VM.  The
     * activation group can only be set if it is not currently set.
     * An activation group is set using the <code>createGroup</code>
     * method when the <code>Activator</code> initiates the
     * re-creation of an activation group in order to carry out
     * incoming <code>activate</code> requests. A group must first be
     * registered with the <code>ActivationSystem</code> before it can
     * be created via this method.
     *
     * <p>The group class specified by the
     * <code>ActivationGroupDesc</code> must be a concrete subclass of
     * <code>ActivationGroup</code> and have a public constructor that
     * takes two arguments: the <code>ActivationGroupID</code> for the
     * group and the <code>MarshalledObject</code> containing the
     * group's initialization data (obtained from the
     * <code>ActivationGroupDesc</code>.
     *
     * <p>If the group class name specified in the
     * <code>ActivationGroupDesc</code> is <code>null</code>, then
     * this method will behave as if the group descriptor contained
     * the name of the default activation group implementation class.
     *
     * <p>Note that if your application creates its own custom
     * activation group, a security manager must be set for that
     * group.  Otherwise objects cannot be activated in the group.
     * <code>java.rmi.RMISecurityManager</code> is set by default.
     *
     * <p>If a security manager is already set in the group VM, this
     * method first calls the security manager's
     * <code>checkSetFactory</code> method.  This could result in a
     * <code>SecurityException</code>. If your application needs to
     * set a different security manager, you must ensure that the
     * policy file specified by the group's
     * <code>ActivationGroupDesc</code> grants the group the necessary
     * permissions to set a new security manager.  (Note: This will be
     * necessary if your group downloads and sets a security manager).
     *
     * <p>After the group is created, the
     * <code>ActivationSystem</code> is informed that the group is
     * active by calling the <code>activeGroup</code> method which
     * returns the <code>ActivationMonitor</code> for the group. The
     * application need not call <code>activeGroup</code>
     * independently since it is taken care of by this method.
     *
     * <p>Once a group is created, subsequent calls to the
     * <code>currentGroupID</code> method will return the identifier
     * for this group until the group becomes inactive.
     *
     * @param id the activation group's identifier
     * @param desc the activation group's descriptor
     * @param incarnation the group's incarnation number (zero on group's
     * initial creation)
     * @return the activation group for the VM
     * @exception ActivationException if group already exists or if error
     * occurs during group creation
     * @exception SecurityException if permission to create group is denied.
     * (Note: The default implementation of the security manager
     * <code>checkSetFactory</code>
     * method requires the RuntimePermission "setFactory")
     * @exception UnsupportedOperationException if and only if activation is
     * not supported by this implementation
     * @see SecurityManager#checkSetFactory
     * @since 1.2
     */
    public static synchronized
        ActivationGroup createGroup(ActivationGroupID id,
                                    final ActivationGroupDesc desc,
                                    long incarnation)
        throws ActivationException
    {
        SecurityManager security = System.getSecurityManager();
        if (security != null)
            security.checkSetFactory();

        if (currGroup != null)
            throw new ActivationException("group already exists");

        if (canCreate == false)
            throw new ActivationException("group deactivated and " +
                                          "cannot be recreated");

        try {
            // load group's class
            String groupClassName = desc.getClassName();
            Class<? extends ActivationGroup> cl;
            Class<? extends ActivationGroup> defaultGroupClass =
                sun.rmi.server.ActivationGroupImpl.class;
            if (groupClassName == null ||       // see 4252236
                groupClassName.equals(defaultGroupClass.getName()))
            {
                cl = defaultGroupClass;
            } else {
                Class<?> cl0;
                try {
                    cl0 = RMIClassLoader.loadClass(desc.getLocation(),
                                                   groupClassName);
                } catch (Exception ex) {
                    throw new ActivationException(
                        "Could not load group implementation class", ex);
                }
                if (ActivationGroup.class.isAssignableFrom(cl0)) {
                    cl = cl0.asSubclass(ActivationGroup.class);
                } else {
                    throw new ActivationException("group not correct class: " +
                                                  cl0.getName());
                }
            }

            // create group
            Constructor<? extends ActivationGroup> constructor =
                cl.getConstructor(ActivationGroupID.class,
                                  MarshalledObject.class);
            ActivationGroup newGroup =
                constructor.newInstance(id, desc.getData());
            currSystem = id.getSystem();
            newGroup.incarnation = incarnation;
            newGroup.monitor =
                currSystem.activeGroup(id, newGroup, incarnation);
            currGroup = newGroup;
            currGroupID = id;
            canCreate = false;
        } catch (InvocationTargetException e) {
                e.getTargetException().printStackTrace();
                throw new ActivationException("exception in group constructor",
                                              e.getTargetException());

        } catch (ActivationException e) {
            throw e;

        } catch (Exception e) {
            throw new ActivationException("exception creating group", e);
        }

        return currGroup;
    }

    /**
     * Returns the current activation group's identifier.  Returns null
     * if no group is currently active for this VM.
     * @exception UnsupportedOperationException if and only if activation is
     * not supported by this implementation
     * @return the activation group's identifier
     * @since 1.2
     */
    public static synchronized ActivationGroupID currentGroupID() {
        return currGroupID;
    }

    /**
     * Returns the activation group identifier for the VM.  If an
     * activation group does not exist for this VM, a default
     * activation group is created. A group can be created only once,
     * so if a group has already become active and deactivated.
     *
     * @return the activation group identifier
     * @exception ActivationException if error occurs during group
     * creation, if security manager is not set, or if the group
     * has already been created and deactivated.
     */
    static synchronized ActivationGroupID internalCurrentGroupID()
        throws ActivationException
    {
        if (currGroupID == null)
            throw new ActivationException("nonexistent group");

        return currGroupID;
    }

    /**
     * Set the activation system for the VM.  The activation system can
     * only be set it if no group is currently active. If the activation
     * system is not set via this call, then the <code>getSystem</code>
     * method attempts to obtain a reference to the
     * <code>ActivationSystem</code> by looking up the name
     * "java.rmi.activation.ActivationSystem" in the Activator's
     * registry. By default, the port number used to look up the
     * activation system is defined by
     * <code>ActivationSystem.SYSTEM_PORT</code>. This port can be overridden
     * by setting the property <code>java.rmi.activation.port</code>.
     *
     * <p>If there is a security manager, this method first
     * calls the security manager's <code>checkSetFactory</code> method.
     * This could result in a SecurityException.
     *
     * @param system remote reference to the <code>ActivationSystem</code>
     * @exception ActivationException if activation system is already set
     * @exception SecurityException if permission to set the activation system is denied.
     * (Note: The default implementation of the security manager
     * <code>checkSetFactory</code>
     * method requires the RuntimePermission "setFactory")
     * @exception UnsupportedOperationException if and only if activation is
     * not supported by this implementation
     * @see #getSystem
     * @see SecurityManager#checkSetFactory
     * @since 1.2
     */
    public static synchronized void setSystem(ActivationSystem system)
        throws ActivationException
    {
        SecurityManager security = System.getSecurityManager();
        if (security != null)
            security.checkSetFactory();

        if (currSystem != null)
            throw new ActivationException("activation system already set");

        currSystem = system;
    }

    /**
     * Returns the activation system for the VM. The activation system
     * may be set by the <code>setSystem</code> method. If the
     * activation system is not set via the <code>setSystem</code>
     * method, then the <code>getSystem</code> method attempts to
     * obtain a reference to the <code>ActivationSystem</code> by
     * looking up the name "java.rmi.activation.ActivationSystem" in
     * the Activator's registry. By default, the port number used to
     * look up the activation system is defined by
     * <code>ActivationSystem.SYSTEM_PORT</code>. This port can be
     * overridden by setting the property
     * <code>java.rmi.activation.port</code>.
     *
     * @return the activation system for the VM/group
     * @exception ActivationException if activation system cannot be
     *  obtained or is not bound
     * (means that it is not running)
     * @exception UnsupportedOperationException if and only if activation is
     * not supported by this implementation
     * @see #setSystem
     * @since 1.2
     */
    public static synchronized ActivationSystem getSystem()
        throws ActivationException
    {
        if (currSystem == null) {
            try {
                int port = AccessController.doPrivileged(
                    new GetIntegerAction("java.rmi.activation.port",
                                         ActivationSystem.SYSTEM_PORT));
                currSystem = (ActivationSystem)
                    Naming.lookup("//:" + port +
                                  "/java.rmi.activation.ActivationSystem");
            } catch (Exception e) {
                throw new ActivationException(
                    "unable to obtain ActivationSystem", e);
            }
        }
        return currSystem;
    }

    /**
     * This protected method is necessary for subclasses to
     * make the <code>activeObject</code> callback to the group's
     * monitor. The call is simply forwarded to the group's
     * <code>ActivationMonitor</code>.
     *
     * @param id the object's identifier
     * @param mobj a marshalled object containing the remote object's stub
     * @exception UnknownObjectException if object is not registered
     * @exception RemoteException if call informing monitor fails
     * @exception ActivationException if an activation error occurs
     * @since 1.2
     */
    protected void activeObject(ActivationID id,
                                MarshalledObject<? extends Remote> mobj)
        throws ActivationException, UnknownObjectException, RemoteException
    {
        getMonitor().activeObject(id, mobj);
    }

    /**
     * This protected method is necessary for subclasses to
     * make the <code>inactiveGroup</code> callback to the group's
     * monitor. The call is simply forwarded to the group's
     * <code>ActivationMonitor</code>. Also, the current group
     * for the VM is set to null.
     *
     * @exception UnknownGroupException if group is not registered
     * @exception RemoteException if call informing monitor fails
     * @since 1.2
     */
    protected void inactiveGroup()
        throws UnknownGroupException, RemoteException
    {
        try {
            getMonitor().inactiveGroup(groupID, incarnation);
        } finally {
            destroyGroup();
        }
    }

    /**
     * Returns the monitor for the activation group.
     */
    private ActivationMonitor getMonitor() throws RemoteException {
        synchronized (ActivationGroup.class) {
            if (monitor != null) {
                return monitor;
            }
        }
        throw new RemoteException("monitor not received");
    }

    /**
     * Destroys the current group.
     */
    private static synchronized void destroyGroup() {
        currGroup = null;
        currGroupID = null;
        // NOTE: don't set currSystem to null since it may be needed
    }

    /**
     * Returns the current group for the VM.
     * @exception ActivationException if current group is null (not active)
     */
    static synchronized ActivationGroup currentGroup()
        throws ActivationException
    {
        if (currGroup == null) {
            throw new ActivationException("group is not active");
        }
        return currGroup;
    }

}
