/*
 * Copyright 1998-1999 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jdi.event;

import com.sun.jdi.*;

import java.util.List;

/**
 * Notification of a breakpoint in the target VM.
 * The breakpoint event
 * is generated before the code at its location is executed.
 * When a location
 * is reached which satisfies a currently enabled
 * {@link com.sun.jdi.request.BreakpointRequest breakpoint request},
 * an {@link EventSet event set}
 * containing an instance of this class will be added
 * to the VM's event queue.
 *
 * @see EventQueue
 * @see VirtualMachine
 * @see com.sun.jdi.request.BreakpointRequest
 *
 * @author Robert Field
 * @since  1.3
 */
public interface BreakpointEvent extends LocatableEvent {

}
