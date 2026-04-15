/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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


package java.sql;

import java.security.*;

/**
 * This class was only useful in conjunction with the {@link java.lang.SecurityManager},
 * which is no longer supported. There is no replacement for this class.
 *
 * @since 1.3
 *
 * @deprecated There is no replacement for this class.
 */
@Deprecated(since="26", forRemoval=true)
public final class SQLPermission extends BasicPermission {

    /**
     * Creates a new {@code SQLPermission} object with the specified name.
     * The name is the symbolic name of the {@code SQLPermission}.
     *
     * @param name the name of this {@code SQLPermission} object, which must
     * be either {@code  setLog}, {@code callAbort}, {@code setSyncFactory},
     *  {@code deregisterDriver}, or {@code setNetworkTimeout}
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */

    public SQLPermission(String name) {
        super(name);
    }

    /**
     * Creates a new {@code SQLPermission} object with the specified name.
     * The name is the symbolic name of the {@code SQLPermission}; the
     * actions {@code String} is currently unused and should be
     * {@code null}.
     *
     * @param name the name of this {@code SQLPermission} object, which must
     * be either {@code  setLog}, {@code callAbort}, {@code setSyncFactory},
     *  {@code deregisterDriver}, or {@code setNetworkTimeout}
     * @param actions should be {@code null}
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */

    public SQLPermission(String name, String actions) {
        super(name, actions);
    }

    /**
     * Private serial version unique ID to ensure serialization
     * compatibility.
     */
    static final long serialVersionUID = -1439323187199563495L;

}
