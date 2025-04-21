/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr;

import java.util.Objects;

/**
 * Permission for controlling access to Flight Recorder.
 *
 * @deprecated
 * This permission cannot be used for controlling access to resources
 * as the Security Manager is no longer supported.
 *
 * @since 9
 *
 * @see java.security.BasicPermission
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see java.security.PermissionCollection
 * @see java.lang.SecurityManager
 *
 */
@SuppressWarnings("serial")
@Deprecated(since="25", forRemoval=true)
public final class FlightRecorderPermission extends java.security.BasicPermission {
    /**
     * Constructs a {@code FlightRecorderPermission} with the specified name.
     *
     * @param name the permission name, must be either
     *        {@code "accessFlightRecorder"} or {@code "registerEvent"}, not
     *        {@code null}
     *
     * @throws IllegalArgumentException if {@code name} is empty or not valid
     */
    public FlightRecorderPermission(String name) {
        super(Objects.requireNonNull(name, "name"));
        if (!name.equals("accessFlightRecorder") && !name.equals("registerEvent")) {
            throw new IllegalArgumentException("name: " + name);
        }
    }
}
