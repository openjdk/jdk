/*
 * Copyright (c) 2005, 2007, Oracle and/or its affiliates. All rights reserved.
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

#if defined(SOLARIS) && defined(DTRACE_ENABLED)

#include <sys/sdt.h>

#define DTRACE_ONLY(x) x
#define NOT_DTRACE(x)

// Work around dtrace tail call bug 6672627 until it is fixed in solaris 10.
#define HS_DTRACE_WORKAROUND_TAIL_CALL_BUG() \
  do { volatile size_t dtrace_workaround_tail_call_bug = 1; } while (0)

#else // ndef SOLARIS || ndef DTRACE_ENABLED

#define DTRACE_ONLY(x)
#define NOT_DTRACE(x) x

#define DTRACE_PROBE(a,b) {;}
#define DTRACE_PROBE1(a,b,c) {;}
#define DTRACE_PROBE2(a,b,c,d) {;}
#define DTRACE_PROBE3(a,b,c,d,e) {;}
#define DTRACE_PROBE4(a,b,c,d,e,f) {;}
#define DTRACE_PROBE5(a,b,c,d,e,f,g) {;}

#define HS_DTRACE_WORKAROUND_TAIL_CALL_BUG()

#endif

#define HS_DTRACE_PROBE_FN(provider,name)\
  __dtrace_##provider##___##name

#define HS_DTRACE_PROBE_DECL_N(provider,name,args) \
  DTRACE_ONLY(extern "C" void HS_DTRACE_PROBE_FN(provider,name) args)
#define HS_DTRACE_PROBE_CDECL_N(provider,name,args) \
  DTRACE_ONLY(extern void HS_DTRACE_PROBE_FN(provider,name) args)

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
