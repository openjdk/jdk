/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef SHARE_VM_TRACE_TRACE_MACRO_HPP
#define SHARE_VM_TRACE_TRACE_MACRO_HPP

#define EVENT_BEGIN(type, name)
#define EVENT_SET(name, field, value)
#define EVENT_COMMIT(name, ...)
#define EVENT_STARTED(name, time)
#define EVENT_ENDED(name, time)
#define EVENT_THREAD_EXIT(thread)

#define TRACE_ENABLED 0

#define TRACE_INIT_ID(k)
#define TRACE_BUFFER void*

#define TRACE_START() true
#define TRACE_INITIALIZE() 0

#define TRACE_SET_KLASS_TRACE_ID(x1, x2) do { } while (0)
#define TRACE_DEFINE_KLASS_METHODS typedef int ___IGNORED_hs_trace_type1
#define TRACE_DEFINE_KLASS_TRACE_ID typedef int ___IGNORED_hs_trace_type2

#endif
