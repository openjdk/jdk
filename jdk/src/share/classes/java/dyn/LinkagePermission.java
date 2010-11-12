/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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

package java.dyn;

import java.security.*;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;

/**
 * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
 * This class is for managing runtime permission checking for
 * operations performed by methods in the {@link Linkage} class.
 * Like a {@link RuntimePermission}, on which it is modeled,
 * a {@code LinkagePermission} contains a target name but
 * no actions list; you either have the named permission
 * or you don't.
 * <p>
 * The following table lists all the possible {@code LinkagePermission} target names,
 * and for each provides a description of what the permission allows
 * and a discussion of the risks of granting code the permission.
 * <p>
 *
 * <table border=1 cellpadding=5 summary="permission target name,
 *  what the target allows,and associated risks">
 * <tr>
 * <th>Permission Target Name</th>
 * <th>What the Permission Allows</th>
 * <th>Risks of Allowing this Permission</th>
 * </tr>
 *
 * <tr>
 *   <td>invalidateAll</td>
 *   <td>Force the relinking of invokedynamic call sites everywhere.</td>
 *   <td>This could allow an attacker to slow down the system,
 *       or perhaps expose timing bugs in a dynamic language implementations,
 *       by forcing redundant relinking operations.</td>
 * </tr>
 *
 *
 * <tr>
 *   <td>invalidateCallerClass.{class name}</td>
 *   <td>Force the relinking of invokedynamic call sites in the given class.</td>
 *   <td>See {@code invalidateAll}.</td>
 * </tr>
 * </table>
 * <p>ISSUE: Is this still needed?
 *
 * @see java.lang.RuntimePermission
 * @see java.lang.SecurityManager
 *
 * @author John Rose, JSR 292 EG
 */

public final class LinkagePermission extends BasicPermission {
    private static final long serialVersionUID = 292L;

    /**
     * Create a new LinkagePermission with the given name.
     * The name is the symbolic name of the LinkagePermission, such as
     * "invalidateCallerClass.*", etc. An asterisk
     * may appear at the end of the name, following a ".", or by itself, to
     * signify a wildcard match.
     *
     * @param name the name of the LinkagePermission
     */
    public LinkagePermission(String name) {
        super(name);
    }

    /**
     * Create a new LinkagePermission with the given name on the given class.
     * Equivalent to {@code LinkagePermission(name+"."+clazz.getName())}.
     *
     * @param name the name of the LinkagePermission
     * @param clazz the class affected by the permission
     */
    public LinkagePermission(String name, Class<?> clazz) {
        super(name + "." + clazz.getName());
    }
}
