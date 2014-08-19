/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jdi;

/**
 * Initial class that provides access to the default implementation
 * of JDI interfaces. A debugger application uses this class to access the
 * single instance of the {@link VirtualMachineManager} interface.
 *
 * @author Gordon Hirsch
 * @since  1.3
 */

@jdk.Exported
public class Bootstrap extends Object {

    /**
     * Returns the virtual machine manager.
     *
     * <p> May throw an unspecified error if initialization of the
     * {@link com.sun.jdi.VirtualMachineManager} fails or if
     * the virtual machine manager is unable to locate or create
     * any {@link com.sun.jdi.connect.Connector Connectors}. </p>
     * <p>
     * @throws java.lang.SecurityException if a security manager has been
     * installed and it denies {@link JDIPermission}
     * <tt>("virtualMachineManager")</tt> or other unspecified
     * permissions required by the implementation.
     * </p>
     */
    static public synchronized VirtualMachineManager virtualMachineManager() {
        return com.sun.tools.jdi.VirtualMachineManagerImpl.virtualMachineManager();
    }
}
