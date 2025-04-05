/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.nio.file;

import java.security.BasicPermission;

/**
 * The {@code Permission} class for link creation operations.
 *
 * @deprecated
 * This permission cannot be used for controlling access to resources
 * as the Security Manager is no longer supported.
 *
 * @since 1.7
 */
@Deprecated(since="25", forRemoval=true)
public final class LinkPermission extends BasicPermission {
    @java.io.Serial
    static final long serialVersionUID = -1441492453772213220L;

    private void checkName(String name) {
        if (!name.equals("hard") && !name.equals("symbolic")) {
            throw new IllegalArgumentException("name: " + name);
        }
    }

    /**
     * Constructs a {@code LinkPermission} with the specified name.
     *
     * @param   name
     *          the name of the permission. It must be "hard" or "symbolic".
     *
     * @throws  IllegalArgumentException
     *          if name is empty or invalid
     */
    public LinkPermission(String name) {
        super(name);
        checkName(name);
    }

    /**
     * Constructs a {@code LinkPermission} with the specified name.
     *
     * @param   name
     *          the name of the permission; must be "hard" or "symbolic".
     * @param   actions
     *          the actions for the permission; must be the empty string or
     *          {@code null}
     *
     * @throws  IllegalArgumentException
     *          if name is empty or invalid, or actions is a non-empty string
     */
    public LinkPermission(String name, String actions) {
        super(name);
        checkName(name);
        if (actions != null && !actions.isEmpty()) {
            throw new IllegalArgumentException("actions: " + actions);
        }
    }
}
