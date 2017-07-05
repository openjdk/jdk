/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.presentation.rmi;

import java.security.*;

/**
 * This class controls the use of dynamic proxies.
 * A DynamicAccessPermission contains a name (also referred to as a "target name") but
 * no actions list; you either have the named permission
 * or you don't.
 *
 */

public final class DynamicAccessPermission extends BasicPermission {
    //private static final long serialVersionUID = -8343910153355041693L;

    /**
     * Creates a new DynamicAccessPermission with the specified name.
     * @param name the name of the DynamicAccessPermission.
     */
    public DynamicAccessPermission(String name)
    {
        super(name);
    }

    /**
     * Creates a new DynamicAccessPermission object with the specified name.
     * The name is the symbolic name of the DynamicAccessPermission, and the
     * actions String is currently unused and should be null.
     *
     * @param name the name of the DynamicAccessPermission.
     * @param actions should be null.
     */
    public DynamicAccessPermission(String name, String actions)
    {
        super(name, actions);
    }
}
