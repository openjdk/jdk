/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.net;

import java.security.BasicPermission;

/**
 * Represents permission to access the extended networking capabilities
 * defined in the jdk.net package. These permissions contain a target
 * name, but no actions list. Callers either possess the permission or not.
 * <p>
 * The following targets are defined:
 *
 * <table class="striped"><caption style="display:none">permission target name,
 *  what the target allows,and associated risks</caption>
 * <thead>
 * <tr>
 *   <th scope="col">Permission Target Name</th>
 *   <th scope="col">What the Permission Allows</th>
 *   <th scope="col">Risks of Allowing this Permission</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 *   <th scope="row">setOption.SO_FLOW_SLA</th>
 *   <td>set the {@link ExtendedSocketOptions#SO_FLOW_SLA SO_FLOW_SLA} option
 *       on any socket that supports it</td>
 *   <td>allows caller to set a higher priority or bandwidth allocation
 *       to sockets it creates, than they might otherwise be allowed.
 *       This permission is deprecated.</td>
 * </tr>
 * <tr>
 *   <th scope="row">getOption.SO_FLOW_SLA</th>
 *   <td>retrieve the {@link ExtendedSocketOptions#SO_FLOW_SLA SO_FLOW_SLA}
 *       setting from any socket that supports the option</td>
 *   <td>allows caller access to SLA information that it might not
 *       otherwise have. This permission is deprecated.</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * @see jdk.net.ExtendedSocketOptions
 *
 * @since 1.8
 */

public final class NetworkPermission extends BasicPermission {

    private static final long serialVersionUID = -2012939586906722291L;

    /**
     * Creates a NetworkPermission with the given target name.
     *
     * @param name the permission target name
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    public NetworkPermission(String name)
    {
        super(name);
    }

    /**
     * Creates a NetworkPermission with the given target name.
     *
     * @param name the permission target name
     * @param actions should be {@code null}. Is ignored if not.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    public NetworkPermission(String name, String actions)
    {
        super(name, actions);
    }
}
