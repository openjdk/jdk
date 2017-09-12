/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind;

import java.security.BasicPermission;

/**
 * This class is for JAXB permissions. A {@code JAXBPermission}
 * contains a name (also referred to as a "target name") but
 * no actions list; you either have the named permission
 * or you don't.
 *
 * <P>
 * The target name is the name of the JAXB permission (see below).
 *
 * <P>
 * The following table lists all the possible {@code JAXBPermission} target names,
 * and for each provides a description of what the permission allows
 * and a discussion of the risks of granting code the permission.
 *
 * <table class="striped">
 * <caption style="display:none">Permission target name, what the permission allows, and associated risks"</caption>
 * <thead>
 * <tr>
 * <th scope="col">Permission Target Name</th>
 * <th scope="col">What the Permission Allows</th>
 * <th scope="col">Risks of Allowing this Permission</th>
 * </tr>
 * </thead>
 *
 * <tbody style="text-align:left">
 * <tr>
 *   <th scope="row">setDatatypeConverter</th>
 *   <td>
 *     Allows the code to set VM-wide {@link DatatypeConverterInterface}
 *     via {@link DatatypeConverter#setDatatypeConverter(DatatypeConverterInterface) the setDatatypeConverter method}
 *     that all the methods on {@link DatatypeConverter} uses.
 *   </td>
 *   <td>
 *     Malicious code can set {@link DatatypeConverterInterface}, which has
 *     VM-wide singleton semantics,  before a genuine JAXB implementation sets one.
 *     This allows malicious code to gain access to objects that it may otherwise
 *     not have access to, such as {@link java.awt.Frame#getFrames()} that belongs to
 *     another application running in the same JVM.
 *   </td>
 * </tr>
 * </tbody>
 * </table>
 *
 * @see java.security.BasicPermission
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see java.security.PermissionCollection
 * @see java.lang.SecurityManager
 *
 * @author Joe Fialli
 * @since 1.7, JAXB 2.2
 */

/* code was borrowed originally from java.lang.RuntimePermission. */
public final class JAXBPermission extends BasicPermission {
    /**
     * Creates a new JAXBPermission with the specified name.
     *
     * @param name
     * The name of the JAXBPermission. As of 2.2 only "setDatatypeConverter"
     * is defined.
     */
    public JAXBPermission(String name) {
        super(name);
    }

    private static final long serialVersionUID = 1L;
}
