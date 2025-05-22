/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal;

import jdk.jfr.Enabled;
import jdk.jfr.Registered;
import jdk.jfr.StackTrace;

/**
 * A mirror event is a fictitious event class that contains metadata about an
 * event, but not the implementation to write the event data to buffers.
 * <p>
 * A mirror event should be used when an event class is in a module where a
 * dependency on the jdk.jfr module is not possible, for example, due to a
 * circular dependency.
 * <p>
 * Subclass the MirrorEvent class and add the exact same fields as the actual
 * event, but with labels, descriptions etc.
 * <p>
 * For the mirror mechanism to work, the mirror class must be registered in the
 * jdk.jfr.internal.MirrorEvents class.
 */
@Registered(false)
@Enabled(false)
@StackTrace(false)
public abstract class MirrorEvent {
}
