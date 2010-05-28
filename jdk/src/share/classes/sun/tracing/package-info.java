/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This package contains internal common code for implementing tracing
 * frameworks, and defined a number of existing frameworks.
 * <p>
 * There are four tracing frameworks currently defined.  The "Null" and
 * "Multiplex" frameworks are used internally as part of the implementation.
 * The "DTrace" framework is the prime consumer framework at the moment,
 * while the "PrintStream" framework is a functional, but hidden, framework
 * which can be used to track probe firings.  All but the "DTrace" framework
 * are defined in this package.  The "DTrace" framework is implemented in the
 * {@code sun.tracing.dtrace} package.
 * <p>
 * This package also contains the {@code ProviderSkeleton} class, which
 * holds most of the common code needed for implementing frameworks.
 * <p>
 * The "Null" framework is used when there are no other active frameworks.
 * It accomplishes absolutely nothing and is merely a placeholder so that
 * the application can call the tracing routines without error.
 * <p>
 * The "Multiplex" framework is used when there are multiple active frameworks.
 * It is initialized with the framework factories and create providers and
 * probes that dispatch to each active framework in turn.
 * <p>
 * The "PrintStream" framework is currently a debugging framework which
 * dispatches trace calls to a user-defined PrintStream class, defined by
 * a property.  It may some day be opened up to general use.
 * <p>
 * See the {@code sun.tracing.dtrace} and {@code com.sun.tracing.dtrace}
 * packages for information on the "DTrace" framework.
 */

package sun.tracing;
