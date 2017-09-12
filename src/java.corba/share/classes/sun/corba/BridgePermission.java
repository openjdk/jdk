/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

package sun.corba ;

import java.security.BasicPermission ;

/** Permission class used to protect access to the sun.corba.Bridge
 * object.  The only name valid here is "getBridge".  The
 * BridgePermission("getBridge") permission must be held by the
 * caller of sun.corba.Bridge.get().
 */
public final class BridgePermission extends BasicPermission
{
    /**
     * Creates a new BridgePermission with the specified name.
     * The name is the symbolic name of the BridgePermission.
     * The only valid name here is "getBridge".
     *
     * @param name the name of the BridgePermission.
     */
    public BridgePermission(String name)
    {
        super(name);
    }

    /**
     * Creates a new BridgePermission object with the specified name.
     * The name is the symbolic name of the BridgePermission, and the
     * actions String is currently unused and should be null.
     * The only valid name here is "getBridge".
     *
     * @param name the name of the BridgePermission.
     * @param actions should be null.
     */

    public BridgePermission(String name, String actions)
    {
        super(name, actions);
    }
}
