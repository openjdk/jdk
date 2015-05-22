/*
 * Copyright (c) 1997, 2002, Oracle and/or its affiliates. All rights reserved.
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

import java.rmi.activation.ActivationGroupDesc;
import java.rmi.activation.ActivationGroupID;
import java.rmi.activation.ActivationGroup;

/**
 * This is the bootstrap code to start a VM executing an activation
 * group.
 *
 * The activator spawns (as a child process) an activation group as needed
 * and directs activation requests to the appropriate activation
 * group. After spawning the VM, the activator passes some
 * information to the bootstrap code via its stdin:
 * <ul>
 * <li> the activation group's id,
 * <li> the activation group's descriptor (an instance of the class
 *    java.rmi.activation.ActivationGroupDesc) for the group, adn
 * <li> the group's incarnation number.
 * </ul><p>
 *
 * When the bootstrap VM starts executing, it reads group id and
 * descriptor from its stdin so that it can create the activation
 * group for the VM.
 *
 * @author Ann Wollrath
 */
public abstract class ActivationGroupInit
{
    /**
     * Main program to start a VM for an activation group.
     */
    public static void main(String args[])
    {
        try {
            if (System.getSecurityManager() == null) {
                System.setSecurityManager(new SecurityManager());
            }
            // read group id, descriptor, and incarnation number from stdin
            MarshalInputStream in = new MarshalInputStream(System.in);
            ActivationGroupID id  = (ActivationGroupID)in.readObject();
            ActivationGroupDesc desc = (ActivationGroupDesc)in.readObject();
            long incarnation = in.readLong();

            // create and set group for the VM
            ActivationGroup.createGroup(id, desc, incarnation);
        } catch (Exception e) {
            System.err.println("Exception in starting ActivationGroupInit:");
            e.printStackTrace();
        } finally {
            try {
                System.in.close();
                // note: system out/err shouldn't be closed
                // since the parent may want to read them.
            } catch (Exception ex) {
                // ignore exceptions
            }
        }
    }
}
