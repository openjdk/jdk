/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_TRACE_TRACEMACROS_HPP
#define SHARE_VM_TRACE_TRACEMACROS_HPP

typedef u8 traceid;

#define EVENT_THREAD_EXIT(thread)
#define EVENT_THREAD_DESTRUCT(thread)
#define TRACE_KLASS_CREATION(k, p, t)

#define TRACE_INIT_KLASS_ID(k)
#define TRACE_INIT_MODULE_ID(m)
#define TRACE_INIT_PACKAGE_ID(p)
#define TRACE_INIT_THREAD_ID(td)
#define TRACE_DATA TraceThreadData

#define THREAD_TRACE_ID(thread) ((traceid)thread->osthread()->thread_id())
extern "C" void JNICALL trace_register_natives(JNIEnv*, jclass);
#define TRACE_REGISTER_NATIVES ((void*)((address_word)(&trace_register_natives)))
#define TRACE_START() JNI_OK
#define TRACE_INITIALIZE() JNI_OK

#define TRACE_DEFINE_TRACE_ID_METHODS typedef int ___IGNORED_hs_trace_type1
#define TRACE_DEFINE_TRACE_ID_FIELD typedef int ___IGNORED_hs_trace_type2
#define TRACE_DEFINE_KLASS_TRACE_ID_OFFSET typedef int ___IGNORED_hs_trace_type3
#define TRACE_KLASS_TRACE_ID_OFFSET in_ByteSize(0); ShouldNotReachHere()
#define TRACE_DEFINE_THREAD_TRACE_DATA_OFFSET typedef int ___IGNORED_hs_trace_type4
#define TRACE_THREAD_TRACE_DATA_OFFSET in_ByteSize(0); ShouldNotReachHere()
#define TRACE_DEFINE_THREAD_TRACE_ID_OFFSET typedef int ___IGNORED_hs_trace_type5
#define TRACE_THREAD_TRACE_ID_OFFSET in_ByteSize(0); ShouldNotReachHere()
#define TRACE_DEFINE_THREAD_ID_SIZE typedef int ___IGNORED_hs_trace_type6
#define TRACE_TEMPLATES(template)
#define TRACE_INTRINSICS(do_intrinsic, do_class, do_name, do_signature, do_alias)

#endif // SHARE_VM_TRACE_TRACEMACROS_HPP
