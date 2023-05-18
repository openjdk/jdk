/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.management;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.vm.ThreadContainer;
import jdk.internal.vm.ThreadContainers;

/**
 * This class consists exclusively of static methods that operate on threads for the
 * purpoes of monitoring and management.
 *
 * @since 21
 */
@PreviewFeature(feature = PreviewFeature.Feature.STRUCTURED_CONCURRENCY)
public final class Threads {
    private Threads() { }

    /**
     * Returns a string that describes the current thread's enclosing
     * {@linkplain java.util.concurrent.StructuredTaskScope##TreeStructure scopes}
     * or an empty string if there no enclosing scopes.
     *
     * <p> If there is a security manager set, and describing an enclosing scope
     * requires getting the stack trace of its owner thread, then the security
     * manager's {@code checkPermission} method is called to check the permission
     * {@code RuntimePermission("getStackTrace")}.
     *
     * @implSpec The default implementation includes the name, owner, and owner
     * stack trace of each enclosing scope.
     *
     * @apiNote There is no equivalent at this time to describe the enclosing
     * scopes of other threads.
     *
     * @throws SecurityException if denied by the security manager
     * @see java.util.concurrent.StructuredTaskScope
     */
    public static String currentThreadEnclosingScopes() {
        StringBuilder sb = new StringBuilder();
        ThreadContainer container = ThreadContainers.container(Thread.currentThread());
        Thread owner;
        while ((owner = container.owner()) != null) {
            sb.append(container)
                    .append(", owner=")
                    .append(owner)
                    .append(System.lineSeparator());
            for (StackTraceElement e : owner.getStackTrace()) {
                sb.append("    ")
                        .append(e.toString())
                        .append(System.lineSeparator());
            }
            container = container.parent();
        }
        return sb.toString();
    }
}
