/*
 * Copyright (c) 1998, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jdi.event;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.AccessWatchpointRequest;

/**
 * Notification of a field access in the target VM.
 * <p>
 * This notification is not generated for an instance field while the object
 * containing the field is being constructed by the event thread if the
 * object's class or any of its superclasses has
 * {@linkplain java.lang.reflect.Field#isStrictInit() strictly-initialized}
 * instance fields. It is also not generated for a static field when the class
 * declaring the field is being initialized by the event thread and the class
 * declares strictly-initialized static fields.
 * <p>
 * Field modifications are not considered field accesses.
 *
 * @apiNote The instance fields of {@linkplain Class#isValue() value classes} are
 * strictly-initialized. Consequently, events for watched instance fields of a value
 * class are not generated during construction of an instance of the value class.
 *
 * @see AccessWatchpointRequest
 * @see EventQueue
 * @see VirtualMachine
 *
 * @author Robert Field
 * @since  1.3
 */
public interface AccessWatchpointEvent extends WatchpointEvent {
}
