/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_RUNTIME_STUBDECLARATIONS_HPP
#define SHARE_RUNTIME_STUBDECLARATIONS_HPP

#include "utilities/macros.hpp"

// macros for generating definitions and declarations for shared, c1
// and opto blob fields and associated stub ids

// Different shared stubs can have different blob types and may
// include some JFR stubs
//
// n.b resolve, handler and throw stubs must remain grouped in the
// same order to allow id values to be range checked

#if INCLUDE_JFR
// do_blob(name, type)
#define SHARED_JFR_STUBS_DO(do_blob)                                   \
  do_blob(jfr_write_checkpoint, RuntimeStub*)                          \
  do_blob(jfr_return_lease, RuntimeStub*)                              \

#else
#define SHARED_JFR_STUBS_DO(do_blob)
#endif

// do_blob(name, type)
#define SHARED_STUBS_DO(do_blob)                                       \
  do_blob(deopt, DeoptimizationBlob*)                                  \
  /* resolve stubs */                                                  \
  do_blob(wrong_method, RuntimeStub*)                                  \
  do_blob(wrong_method_abstract, RuntimeStub*)                         \
  do_blob(ic_miss, RuntimeStub*)                                       \
  do_blob(resolve_opt_virtual_call, RuntimeStub*)                      \
  do_blob(resolve_virtual_call, RuntimeStub*)                          \
  do_blob(resolve_static_call, RuntimeStub*)                           \
  /* handler stubs */                                                  \
  do_blob(polling_page_vectors_safepoint_handler, SafepointBlob*)      \
  do_blob(polling_page_safepoint_handler, SafepointBlob*)              \
  do_blob(polling_page_return_handler, SafepointBlob*)                 \
  /* throw stubs */                                                    \
  do_blob(throw_AbstractMethodError, RuntimeStub*)                     \
  do_blob(throw_IncompatibleClassChangeError, RuntimeStub*)            \
  do_blob(throw_NullPointerException_at_call, RuntimeStub*)            \
  do_blob(throw_StackOverflowError, RuntimeStub*)                      \
  do_blob(throw_delayed_StackOverflowError, RuntimeStub*)              \
  /* other stubs */                                                    \
  SHARED_JFR_STUBS_DO(do_blob)                                         \

// generate a stub id enum tag from a name

#define STUB_ID_NAME(base) base##_id

// generate a blob id enum tag from a name

#define BLOB_ID_NAME(base) base##_id

// generate a blob field name

#define BLOB_FIELD_NAME(base) _##base##_blob

#endif // SHARE_RUNTIME_STUBDECLARATIONS_HPP

