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
import java.rmi.activation.UnknownGroupException;
import java.rmi.activation.UnknownObjectException;

/**
 * An <code>ActivationMonitor</code> is specific to an
 * <code>ActivationGroup</code> and is obtained when a group is
 * reported active via a call to
 * <code>ActivationSystem.activeGroup</code> (this is done
 * internally). An activation group is responsible for informing its
 * <code>ActivationMonitor</code> when either: its objects become active or
 * inactive, or the group as a whole becomes inactive.
 *
 * @author      Ann Wollrath
 * @see         Activator
 * @see         ActivationSystem
 * @see         ActivationGroup
 * @since       1.2
 * @deprecated
 * See the <a href="{@docRoot}/java.rmi/java/rmi/activation/package-summary.html">
 * {@code java.rmi.activation}</a> package specification for further information.
 */
@Deprecated(forRemoval=true, since="15")
@SuppressWarnings("removal")
public interface ActivationMonitor extends Remote {

   /**
     * An activation group calls its monitor's
     * <code>inactiveObject</code> method when an object in its group
     * becomes inactive (deactivates).  An activation group discovers
     * that an object (that it participated in activating) in its VM
     * is no longer active, via calls to the activation group's
     * <code>inactiveObject</code> method. <p>
     *
     * The <code>inactiveObject</code> call informs the
     * <code>ActivationMonitor</code> that the remote object reference
     * it holds for the object with the activation identifier,
     * <code>id</code>, is no longer valid. The monitor considers the
     * reference associated with <code>id</code> as a stale reference.
     * Since the reference is considered stale, a subsequent
     * <code>activate</code> call for the same activation identifier
     * results in re-activating the remote object.
     *
     * @param id the object's activation identifier
     * @exception UnknownObjectException if object is unknown
     * @exception RemoteException if remote call fails
     * @since 1.2
     */
    public void inactiveObject(ActivationID id)
        throws UnknownObjectException, RemoteException;

    /**
     * Informs that an object is now active. An <code>ActivationGroup</code>
     * informs its monitor if an object in its group becomes active by
     * other means than being activated directly (i.e., the object
     * is registered and "activated" itself).
     *
     * @param id the active object's id
     * @param obj the marshalled form of the object's stub
     * @exception UnknownObjectException if object is unknown
     * @exception RemoteException if remote call fails
     * @since 1.2
     */
    public void activeObject(ActivationID id,
                             MarshalledObject<? extends Remote> obj)
        throws UnknownObjectException, RemoteException;

    /**
     * Informs that the group is now inactive. The group will be
     * recreated upon a subsequent request to activate an object
     * within the group. A group becomes inactive when all objects
     * in the group report that they are inactive.
     *
     * @param id the group's id
     * @param incarnation the group's incarnation number
     * @exception UnknownGroupException if group is unknown
     * @exception RemoteException if remote call fails
     * @since 1.2
     */
    public void inactiveGroup(ActivationGroupID id,
                              long incarnation)
        throws UnknownGroupException, RemoteException;

}
