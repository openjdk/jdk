/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.hotspot;

/**
 * Communicates with an MBean providing a JMX interface to a {@link HotSpotGraalRuntime} instance.
 * The MBean will be dynamically created when a JMX client attaches or some other event causes the
 * platform MBean server to be started.
 */
public interface HotSpotGraalManagementRegistration {

    /**
     * Completes the initialization of this registration by recording the
     * {@link HotSpotGraalRuntime} the MBean will provide an JMX interface to.
     */
    void initialize(HotSpotGraalRuntime runtime);

    /**
     * Polls this registration to see if the MBean is registered in a MBean server.
     *
     * @param sync synchronize with other threads that may be processing this registration. This is
     *            useful when the caller knows the server is active (e.g., it has a reference to
     *            server) and expects this poll to therefore return a non-null value.
     * @return an {@link javax.management.ObjectName} that can be used to access the MBean or
     *         {@code null} if the MBean has not been registered with an MBean server (e.g., no JMX
     *         client has attached to the VM)
     */
    Object poll(boolean sync);
}
