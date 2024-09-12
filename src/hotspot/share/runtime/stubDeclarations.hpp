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

// C1 stubs are always generated in a generic CodeBlob

#ifdef COMPILER1
// do_blob(name)
#define C1_STUBS_DO(do_blob)                                           \
  do_blob(dtrace_object_alloc)                                         \
  do_blob(unwind_exception)                                            \
  do_blob(forward_exception)                                           \
  do_blob(throw_range_check_failed)       /* throws ArrayIndexOutOfBoundsException */ \
  do_blob(throw_index_exception)          /* throws IndexOutOfBoundsException */ \
  do_blob(throw_div0_exception)                                        \
  do_blob(throw_null_pointer_exception)                                \
  do_blob(register_finalizer)                                          \
  do_blob(new_instance)                                                \
  do_blob(fast_new_instance)                                           \
  do_blob(fast_new_instance_init_check)                                \
  do_blob(new_type_array)                                              \
  do_blob(new_object_array)                                            \
  do_blob(new_multi_array)                                             \
  do_blob(handle_exception_nofpu)         /* optimized version that does not preserve fpu registers */ \
  do_blob(handle_exception)                                            \
  do_blob(handle_exception_from_callee)                                \
  do_blob(throw_array_store_exception)                                 \
  do_blob(throw_class_cast_exception)                                  \
  do_blob(throw_incompatible_class_change_error)                       \
  do_blob(slow_subtype_check)                                          \
  do_blob(monitorenter)                                                \
  do_blob(monitorenter_nofpu)             /* optimized version that does not preserve fpu registers */ \
  do_blob(monitorexit)                                                 \
  do_blob(monitorexit_nofpu)              /* optimized version that does not preserve fpu registers */ \
  do_blob(deoptimize)                                                  \
  do_blob(access_field_patching)                                       \
  do_blob(load_klass_patching)                                         \
  do_blob(load_mirror_patching)                                        \
  do_blob(load_appendix_patching)                                      \
  do_blob(fpu2long_stub)                                               \
  do_blob(counter_overflow)                                            \
  do_blob(predicate_failed_trap)                                       \

#else
#define C1_STUBS_DO(do_blob)
#endif

// Opto stubs can be stored as entries with just an address or as
// blobs of different types. The former may include some JVMTI stubs.
//
// n.b. blobs and stub defines are generated in the order defined by
// C2_STUBS_DO, allowing dependencies from any givem stub on its
// predecessors to be guaranteed. That explains the initial placement
// of the blob declarations and intermediate placement of the jvmti
// stubs.

#ifdef COMPILER2
// do_jvmti_stub(name)
#if INCLUDE_JVMTI
#define C2_JVMTI_STUBS_DO(do_jvmti_stub)                               \
  do_jvmti_stub(notify_jvmti_vthread_start)                            \
  do_jvmti_stub(notify_jvmti_vthread_end)                              \
  do_jvmti_stub(notify_jvmti_vthread_mount)                            \
  do_jvmti_stub(notify_jvmti_vthread_unmount)                          \

#else
#define C2_JVMTI_STUBS_DO(do_jvmti_stub)
#endif // INCLUDE_JVMTI

// do_blob(name, type)
// do_stub(name, fancy_jump, pass_tls, return_pc)
// do_jvmti_stub(name)
//
// n.b. non-jvmti stubs may employ a special type of jump (0, 1 or 2)
// and require access to TLS and the return pc. jvmti stubs always
// employ jump 0, and require no special access
#define C2_STUBS_DO(do_blob, do_stub, do_jvmti_stub)                   \
  do_blob(uncommon_trap, UncommonTrapBlob*)                            \
  do_blob(exception, ExceptionBlob*)                                   \
  do_stub(new_instance, 0, true, false)                                \
  do_stub(new_array, 0, true, false)                                   \
  do_stub(new_array_nozero, 0, true, false)                            \
  do_stub(multianewarray2, 0, true, false)                             \
  do_stub(multianewarray3, 0, true, false)                             \
  do_stub(multianewarray4, 0, true, false)                             \
  do_stub(multianewarray5, 0, true, false)                             \
  do_stub(multianewarrayN, 0, true, false)                             \
  C2_JVMTI_STUBS_DO(do_jvmti_stub)                                     \
  do_stub(complete_monitor_locking, 0, false, false)                   \
  do_stub(monitor_notify, 0, false, false)                             \
  do_stub(monitor_notifyAll, 0, false, false)                          \
  do_stub(rethrow, 2, true, true)                                      \
  do_stub(slow_arraycopy, 0, false, false)                             \
  do_stub(register_finalizer, 0, false, false)                         \

#else
#define C2_STUBS_DO(do_blob, do_stub, do_jvmti_stub)
#endif

// generate a stub or blob id enum tag from a name

#define STUB_ID_NAME(base) base##_id

// generate a stub field name

#define STUB_FIELD_NAME(base) _##base

// generate a blob field name

#define BLOB_FIELD_NAME(base) _##base##_blob

#endif // SHARE_RUNTIME_STUBDECLARATIONS_HPP

