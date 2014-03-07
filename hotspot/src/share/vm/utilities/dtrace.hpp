/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2009, 2012 Red Hat, Inc.
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

#ifndef SHARE_VM_UTILITIES_DTRACE_HPP
#define SHARE_VM_UTILITIES_DTRACE_HPP

#if defined(DTRACE_ENABLED)

#include <sys/sdt.h>

#define DTRACE_ONLY(x) x
#define NOT_DTRACE(x)

#if defined(SOLARIS)
// Work around dtrace tail call bug 6672627 until it is fixed in solaris 10.
#define HS_DTRACE_WORKAROUND_TAIL_CALL_BUG() \
  do { volatile size_t dtrace_workaround_tail_call_bug = 1; } while (0)
#elif defined(LINUX)
#define HS_DTRACE_WORKAROUND_TAIL_CALL_BUG()
#elif defined(__APPLE__)
#define HS_DTRACE_WORKAROUND_TAIL_CALL_BUG()
#include <sys/types.h>
#else
#error "dtrace enabled for unknown os"
#endif /* defined(SOLARIS) */

#include "dtracefiles/hotspot.h"
#include "dtracefiles/hotspot_jni.h"
#include "dtracefiles/hs_private.h"

#else /* defined(DTRACE_ENABLED) */

#define DTRACE_ONLY(x)
#define NOT_DTRACE(x) x

#define HS_DTRACE_WORKAROUND_TAIL_CALL_BUG()

#include "dtrace_disabled.hpp"

#endif /* defined(DTRACE_ENABLED) */

#endif // SHARE_VM_UTILITIES_DTRACE_HPP
