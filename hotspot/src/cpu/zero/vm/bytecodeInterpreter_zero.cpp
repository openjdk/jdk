/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * Copyright 2008 Red Hat, Inc.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_cppInterpreter_zero.cpp.incl"

#ifdef CC_INTERP

const char *BytecodeInterpreter::name_of_field_at_address(address addr) {
#define DO(member) {if (addr == (address) &(member)) return XSTR(member);}
  DO(_thread);
  DO(_bcp);
  DO(_locals);
  DO(_constants);
  DO(_method);
  DO(_mdx);
  DO(_stack);
  DO(_msg);
  DO(_result);
  DO(_prev_link);
  DO(_oop_temp);
  DO(_stack_base);
  DO(_stack_limit);
  DO(_monitor_base);
  DO(_self_link);
#undef DO
  if (addr > (address) &_result && addr < (address) (&_result + 1))
    return "_result)";
  return NULL;
}

#endif // CC_INTERP
