/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2014 SAP SE. All rights reserved.
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

// This file defines a set of macros which are used by the c++-interpreter
// for updating a method's methodData object.


#ifndef SHARE_VM_INTERPRETER_BYTECODEINTERPRETERPROFILING_HPP
#define SHARE_VM_INTERPRETER_BYTECODEINTERPRETERPROFILING_HPP

#ifdef CC_INTERP

// Empty dummy implementations if profiling code is switched off. //////////////

#define SET_MDX(mdx)

#define BI_PROFILE_GET_OR_CREATE_METHOD_DATA(exception_handler)                \
  if (ProfileInterpreter) {                                                    \
    ShouldNotReachHere();                                                      \
  }

#define BI_PROFILE_ALIGN_TO_CURRENT_BCI()

#define BI_PROFILE_UPDATE_JUMP()
#define BI_PROFILE_UPDATE_BRANCH(is_taken)
#define BI_PROFILE_UPDATE_RET(bci)
#define BI_PROFILE_SUBTYPECHECK_FAILED(receiver)
#define BI_PROFILE_UPDATE_CHECKCAST(null_seen, receiver)
#define BI_PROFILE_UPDATE_INSTANCEOF(null_seen, receiver)
#define BI_PROFILE_UPDATE_CALL()
#define BI_PROFILE_UPDATE_FINALCALL()
#define BI_PROFILE_UPDATE_VIRTUALCALL(receiver)
#define BI_PROFILE_UPDATE_SWITCH(switch_index)

#endif // CC_INTERP

#endif // SHARE_VM_INTERPRETER_BYTECODECINTERPRETERPROFILING_HPP
