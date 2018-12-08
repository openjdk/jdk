/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.serviceprovider;

import java.util.List;

/* This class lives in the "base" project even if it is only useful for JDK9
 * because javac only links against classes from the "base".
 */

/**
 * Access to thread specific information made available via Java Management Extensions (JMX). Using
 * this abstraction enables avoiding a dependency to the {@code java.management} and
 * {@code jdk.management} modules on JDK 9 and later.
 */
public abstract class JMXService {
    protected abstract long getThreadAllocatedBytes(long id);

    protected abstract long getCurrentThreadCpuTime();

    protected abstract boolean isThreadAllocatedMemorySupported();

    protected abstract boolean isCurrentThreadCpuTimeSupported();

    protected abstract List<String> getInputArguments();

    // Placing this static field in JMXService (instead of GraalServices)
    // allows for lazy initialization.
    static final JMXService instance = GraalServices.loadSingle(JMXService.class, false);
}
