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

#define USDT1 1
#elif defined(LINUX)
#define HS_DTRACE_WORKAROUND_TAIL_CALL_BUG()
#define USDT1 1
#elif defined(__APPLE__)
#define HS_DTRACE_WORKAROUND_TAIL_CALL_BUG()
#define USDT2 1
#include <sys/types.h>
#include "dtracefiles/hotspot.h"
#include "dtracefiles/hotspot_jni.h"
#include "dtracefiles/hs_private.h"
#else
#error "dtrace enabled for unknown os"
#endif /* defined(SOLARIS) */

#else /* defined(DTRACE_ENABLED) */

#define DTRACE_ONLY(x)
#define NOT_DTRACE(x) x

#define HS_DTRACE_WORKAROUND_TAIL_CALL_BUG()

#ifndef USDT2

#define DTRACE_PROBE(a,b) {;}
#define DTRACE_PROBE1(a,b,c) {;}
#define DTRACE_PROBE2(a,b,c,d) {;}
#define DTRACE_PROBE3(a,b,c,d,e) {;}
#define DTRACE_PROBE4(a,b,c,d,e,f) {;}
#define DTRACE_PROBE5(a,b,c,d,e,f,g) {;}
#define DTRACE_PROBE6(a,b,c,d,e,f,g,h) {;}
#define DTRACE_PROBE7(a,b,c,d,e,f,g,h,i) {;}
#define DTRACE_PROBE8(a,b,c,d,e,f,g,h,i,j) {;}
#define DTRACE_PROBE9(a,b,c,d,e,f,g,h,i,j,k) {;}
#define DTRACE_PROBE10(a,b,c,d,e,f,g,h,i,j,k,l) {;}

#else /* USDT2 */

#include "dtrace_usdt2_disabled.hpp"
#endif /* USDT2 */

#endif /* defined(DTRACE_ENABLED) */

#ifndef USDT2

#define HS_DTRACE_PROBE_FN(provider,name)\
  __dtrace_##provider##___##name

#ifdef SOLARIS
// Solaris dtrace needs actual extern function decls.
#define HS_DTRACE_PROBE_DECL_N(provider,name,args) \
  DTRACE_ONLY(extern "C" void HS_DTRACE_PROBE_FN(provider,name) args)
#define HS_DTRACE_PROBE_CDECL_N(provider,name,args) \
  DTRACE_ONLY(extern void HS_DTRACE_PROBE_FN(provider,name) args)
#else
// Systemtap dtrace compatible probes on GNU/Linux don't.
// If dtrace is disabled this macro becomes NULL
#define HS_DTRACE_PROBE_DECL_N(provider,name,args)
#define HS_DTRACE_PROBE_CDECL_N(provider,name,args)
#endif

/* Dtrace probe declarations */
#define HS_DTRACE_PROBE_DECL(provider,name) \
  HS_DTRACE_PROBE_DECL0(provider,name)
#define HS_DTRACE_PROBE_DECL0(provider,name)\
  HS_DTRACE_PROBE_DECL_N(provider,name,(void))
#define HS_DTRACE_PROBE_DECL1(provider,name,t0)\
  HS_DTRACE_PROBE_DECL_N(provider,name,(uintptr_t))
#define HS_DTRACE_PROBE_DECL2(provider,name,t0,t1)\
  HS_DTRACE_PROBE_DECL_N(provider,name,(uintptr_t,uintptr_t))
#define HS_DTRACE_PROBE_DECL3(provider,name,t0,t1,t2)\
  HS_DTRACE_PROBE_DECL_N(provider,name,(uintptr_t,uintptr_t,uintptr_t))
#define HS_DTRACE_PROBE_DECL4(provider,name,t0,t1,t2,t3)\
  HS_DTRACE_PROBE_DECL_N(provider,name,(uintptr_t,uintptr_t,uintptr_t,\
    uintptr_t))
#define HS_DTRACE_PROBE_DECL5(provider,name,t0,t1,t2,t3,t4)\
  HS_DTRACE_PROBE_DECL_N(provider,name,(\
    uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t))
#define HS_DTRACE_PROBE_DECL6(provider,name,t0,t1,t2,t3,t4,t5)\
  HS_DTRACE_PROBE_DECL_N(provider,name,(\
    uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t))
#define HS_DTRACE_PROBE_DECL7(provider,name,t0,t1,t2,t3,t4,t5,t6)\
  HS_DTRACE_PROBE_DECL_N(provider,name,(\
    uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t))
#define HS_DTRACE_PROBE_DECL8(provider,name,t0,t1,t2,t3,t4,t5,t6,t7)\
  HS_DTRACE_PROBE_DECL_N(provider,name,(\
    uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t,\
    uintptr_t))
#define HS_DTRACE_PROBE_DECL9(provider,name,t0,t1,t2,t3,t4,t5,t6,t7,t8)\
  HS_DTRACE_PROBE_DECL_N(provider,name,(\
    uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t,\
    uintptr_t,uintptr_t))
#define HS_DTRACE_PROBE_DECL10(provider,name,t0,t1,t2,t3,t4,t5,t6,t7,t8,t9)\
  HS_DTRACE_PROBE_DECL_N(provider,name,(\
    uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t,uintptr_t,\
    uintptr_t,uintptr_t,uintptr_t))

/* Dtrace probe definitions */
#if defined(SOLARIS)
// Solaris dtrace uses actual function calls.
#define HS_DTRACE_PROBE_N(provider,name, args) \
  DTRACE_ONLY(HS_DTRACE_PROBE_FN(provider,name) args)

#define HS_DTRACE_PROBE(provider,name) HS_DTRACE_PROBE0(provider,name)
#define HS_DTRACE_PROBE0(provider,name)\
  HS_DTRACE_PROBE_N(provider,name,())
#define HS_DTRACE_PROBE1(provider,name,a0)\
  HS_DTRACE_PROBE_N(provider,name,((uintptr_t)a0))
#define HS_DTRACE_PROBE2(provider,name,a0,a1)\
  HS_DTRACE_PROBE_N(provider,name,((uintptr_t)a0,(uintptr_t)a1))
#define HS_DTRACE_PROBE3(provider,name,a0,a1,a2)\
  HS_DTRACE_PROBE_N(provider,name,((uintptr_t)a0,(uintptr_t)a1,(uintptr_t)a2))
#define HS_DTRACE_PROBE4(provider,name,a0,a1,a2,a3)\
  HS_DTRACE_PROBE_N(provider,name,((uintptr_t)a0,(uintptr_t)a1,(uintptr_t)a2,\
    (uintptr_t)a3))
#define HS_DTRACE_PROBE5(provider,name,a0,a1,a2,a3,a4)\
  HS_DTRACE_PROBE_N(provider,name,((uintptr_t)a0,(uintptr_t)a1,(uintptr_t)a2,\
    (uintptr_t)a3,(uintptr_t)a4))
#define HS_DTRACE_PROBE6(provider,name,a0,a1,a2,a3,a4,a5)\
  HS_DTRACE_PROBE_N(provider,name,((uintptr_t)a0,(uintptr_t)a1,(uintptr_t)a2,\
    (uintptr_t)a3,(uintptr_t)a4,(uintptr_t)a5))
#define HS_DTRACE_PROBE7(provider,name,a0,a1,a2,a3,a4,a5,a6)\
  HS_DTRACE_PROBE_N(provider,name,((uintptr_t)a0,(uintptr_t)a1,(uintptr_t)a2,\
    (uintptr_t)a3,(uintptr_t)a4,(uintptr_t)a5,(uintptr_t)a6))
#define HS_DTRACE_PROBE8(provider,name,a0,a1,a2,a3,a4,a5,a6,a7)\
  HS_DTRACE_PROBE_N(provider,name,((uintptr_t)a0,(uintptr_t)a1,(uintptr_t)a2,\
    (uintptr_t)a3,(uintptr_t)a4,(uintptr_t)a5,(uintptr_t)a6,(uintptr_t)a7))
#define HS_DTRACE_PROBE9(provider,name,a0,a1,a2,a3,a4,a5,a6,a7,a8)\
  HS_DTRACE_PROBE_N(provider,name,((uintptr_t)a0,(uintptr_t)a1,(uintptr_t)a2,\
    (uintptr_t)a3,(uintptr_t)a4,(uintptr_t)a5,(uintptr_t)a6,(uintptr_t)a7,\
    (uintptr_t)a8))
#define HS_DTRACE_PROBE10(provider,name,a0,a1,a2,a3,a4,a5,a6,a7,a8,a9)\
  HS_DTRACE_PROBE_N(provider,name,((uintptr_t)a0,(uintptr_t)a1,(uintptr_t)a2,\
    (uintptr_t)a3,(uintptr_t)a4,(uintptr_t)a5,(uintptr_t)a6,(uintptr_t)a7,\
    (uintptr_t)a8,(uintptr_t)a9))
#else
// Systemtap dtrace compatible probes on GNU/Linux use direct macros.
// If dtrace is disabled this macro becomes NULL
#define HS_DTRACE_PROBE(provider,name) HS_DTRACE_PROBE0(provider,name)
#define HS_DTRACE_PROBE0(provider,name)\
  DTRACE_PROBE(provider,name)
#define HS_DTRACE_PROBE1(provider,name,a0)\
  DTRACE_PROBE1(provider,name,a0)
#define HS_DTRACE_PROBE2(provider,name,a0,a1)\
  DTRACE_PROBE2(provider,name,a0,a1)
#define HS_DTRACE_PROBE3(provider,name,a0,a1,a2)\
  DTRACE_PROBE3(provider,name,a0,a1,a2)
#define HS_DTRACE_PROBE4(provider,name,a0,a1,a2,a3)\
  DTRACE_PROBE4(provider,name,a0,a1,a2,a3)
#define HS_DTRACE_PROBE5(provider,name,a0,a1,a2,a3,a4)\
  DTRACE_PROBE5(provider,name,a0,a1,a2,a3,a4)
#define HS_DTRACE_PROBE6(provider,name,a0,a1,a2,a3,a4,a5)\
  DTRACE_PROBE6(provider,name,a0,a1,a2,a3,a4,a5)
#define HS_DTRACE_PROBE7(provider,name,a0,a1,a2,a3,a4,a5,a6)\
  DTRACE_PROBE7(provider,name,a0,a1,a2,a3,a4,a5,a6)
#define HS_DTRACE_PROBE8(provider,name,a0,a1,a2,a3,a4,a5,a6,a7)\
  DTRACE_PROBE8(provider,name,a0,a1,a2,a3,a4,a5,a6,a7)
#define HS_DTRACE_PROBE9(provider,name,a0,a1,a2,a3,a4,a5,a6,a7,a8)\
  DTRACE_PROBE9(provider,name,a0,a1,a2,a3,a4,a5,a6,a7,a8)
#define HS_DTRACE_PROBE10(provider,name,a0,a1,a2,a3,a4,a5,a6,a7,a8,a9)\
  DTRACE_PROBE10(provider,name,a0,a1,a2,a3,a4,a5,a6,a7,a8,a9)
#endif

#endif /* !USDT2 */

#endif // SHARE_VM_UTILITIES_DTRACE_HPP
