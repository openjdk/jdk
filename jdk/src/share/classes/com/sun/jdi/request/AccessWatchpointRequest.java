/*
 * Copyright 1998-2001 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.jdi.request;

import com.sun.jdi.*;

/**
 * Request for notification when the contents of a field are accessed
 * in the target VM.
 * This event will be triggered when the specified field is accessed
 * by Java<SUP><FONT SIZE="-2">TM</FONT></SUP> programming
 * language code or by a
 * Java Native Interface (JNI) get function (<code>Get&lt;Type&gt;Field,
 * GetStatic&lt;Type&gt;Field</code>).
 * Access by JDI does not trigger this event.
 * When an enabled AccessWatchpointRequest is satisfied, an
 * {@link com.sun.jdi.event.EventSet event set} containing an
 * {@link com.sun.jdi.event.AccessWatchpointEvent AccessWatchpointEvent} will be placed
 * on the {@link com.sun.jdi.event.EventQueue EventQueue}.
 * The collection of existing ExceptionRequests is
 * managed by the {@link EventRequestManager}
 * The collection of existing
 * watchpoints is
 * managed by the {@link EventRequestManager}.
 * <p>
 * Note that the modification
 * of a Field is not considered an access.
 *
 * @see ModificationWatchpointRequest
 * @see com.sun.jdi.event.EventQueue
 * @see EventRequestManager
 *
 * @author Robert Field
 * @since  1.3
 */
public interface AccessWatchpointRequest extends WatchpointRequest {
}
