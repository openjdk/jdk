/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.activation.UnknownObjectException;

/**
 * The <code>Activator</code> facilitates remote object activation. A
 * "faulting" remote reference calls the activator's
 * <code>activate</code> method to obtain a "live" reference to a
 * "activatable" remote object. Upon receiving a request for activation,
 * the activator looks up the activation descriptor for the activation
 * identifier, <code>id</code>, determines the group in which the
 * object should be activated initiates object re-creation via the
 * group's <code>ActivationInstantiator</code> (via a call to the
 * <code>newInstance</code> method). The activator initiates the
 * execution of activation groups as necessary. For example, if an
 * activation group for a specific group identifier is not already
 * executing, the activator initiates the execution of a VM for the
 * group. <p>
 *
 * The <code>Activator</code> works closely with
 * <code>ActivationSystem</code>, which provides a means for registering
 * groups and objects within those groups, and <code>ActivationMonitor</code>,
 * which receives information about active and inactive objects and inactive
 * groups. <p>
 *
 * The activator is responsible for monitoring and detecting when
 * activation groups fail so that it can remove stale remote references
 * to groups and active object's within those groups.
 *
 * @author      Ann Wollrath
 * @see         ActivationInstantiator
 * @see         ActivationGroupDesc
 * @see         ActivationGroupID
 * @since       1.2
 * @deprecated
 * See the <a href="{@docRoot}/java.rmi/java/rmi/activation/package-summary.html">
 * {@code java.rmi.activation}</a> package specification for further information.
 */
@Deprecated(forRemoval=true, since="15")
@SuppressWarnings("removal")
public interface Activator extends Remote {
    /**
     * Activate the object associated with the activation identifier,
     * <code>id</code>. If the activator knows the object to be active
     * already, and <code>force</code> is false , the stub with a
     * "live" reference is returned immediately to the caller;
     * otherwise, if the activator does not know that corresponding
     * the remote object is active, the activator uses the activation
     * descriptor information (previously registered) to determine the
     * group (VM) in which the object should be activated. If an
     * <code>ActivationInstantiator</code> corresponding to the
     * object's group descriptor already exists, the activator invokes
     * the activation group's <code>newInstance</code> method passing
     * it the object's id and descriptor. <p>
     *
     * If the activation group for the object's group descriptor does
     * not yet exist, the activator starts an
     * <code>ActivationInstantiator</code> executing (by spawning a
     * child process, for example). When the activator receives the
     * activation group's call back (via the
     * <code>ActivationSystem</code>'s <code>activeGroup</code>
     * method) specifying the activation group's reference, the
     * activator can then invoke that activation instantiator's
     * <code>newInstance</code> method to forward each pending
     * activation request to the activation group and return the
     * result (a marshalled remote object reference, a stub) to the
     * caller.<p>
     *
     * Note that the activator receives a "marshalled" object instead of a
     * Remote object so that the activator does not need to load the
     * code for that object, or participate in distributed garbage
     * collection for that object. If the activator kept a strong
     * reference to the remote object, the activator would then
     * prevent the object from being garbage collected under the
     * normal distributed garbage collection mechanism.
     *
     * @param id the activation identifier for the object being activated
     * @param force if true, the activator contacts the group to obtain
     * the remote object's reference; if false, returning the cached value
     * is allowed.
     * @return the remote object (a stub) in a marshalled form
     * @exception ActivationException if object activation fails
     * @exception UnknownObjectException if object is unknown (not registered)
     * @exception RemoteException if remote call fails
     * @since 1.2
     */
    public MarshalledObject<? extends Remote> activate(ActivationID id,
                                                       boolean force)
        throws ActivationException, UnknownObjectException, RemoteException;

}
