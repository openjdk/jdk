/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_SHAREDRUNTIME_ID_HPP
#define SHARE_RUNTIME_SHAREDRUNTIME_ID_HPP

#include "utilities/macros.hpp"

// macros for generating definitions and declarations for shared, c1
// and opto blob fields and associated stub ids

// Different shared stubs can have different blob types and may
// include some JFR stubs
//
// n.b resolve, handler and throw stubs must remain grouped in the
// same order to allow id values to be range checked

#if INCLUDE_JFR
// template(name, type)
#define SHARED_JFR_STUBS_DO(template)                                  \
  template(jfr_write_checkpoint, RuntimeStub*)                         \
  template(jfr_return_lease, RuntimeStub*)                             \

#else
#define SHARED_JFR_STUBS_DO(TEMPLATE)
#endif

// template(name, type)
#define SHARED_STUBS_DO(template)                                      \
  template(deopt, DeoptimizationBlob*)                                 \
  /* resolve stubs */                                                  \
  template(wrong_method, RuntimeStub*)                                 \
  template(wrong_method_abstract, RuntimeStub*)                        \
  template(ic_miss, RuntimeStub*)                                      \
  template(resolve_opt_virtual_call, RuntimeStub*)                     \
  template(resolve_virtual_call, RuntimeStub*)                         \
  template(resolve_static_call, RuntimeStub*)                          \
  /* handler stubs */                                                  \
  template(polling_page_vectors_safepoint_handler, SafepointBlob*)     \
  template(polling_page_safepoint_handler, SafepointBlob*)             \
  template(polling_page_return_handler, SafepointBlob*)                \
  /* throw stubs */                                                    \
  template(throw_AbstractMethodError, RuntimeStub*)                    \
  template(throw_IncompatibleClassChangeError, RuntimeStub*)           \
  template(throw_NullPointerException_at_call, RuntimeStub*)           \
  template(throw_StackOverflowError, RuntimeStub*)                     \
  template(throw_delayed_StackOverflowError, RuntimeStub*)             \
  /* other stubs */                                                    \
  SHARED_JFR_STUBS_DO(template)                                        \

// C1 stubs are always generated in a generic CodeBlob

#ifdef COMPILER1
// template(name)
#define C1_STUBS_DO(template)                                          \
  template(dtrace_object_alloc)                                        \
  template(unwind_exception)                                           \
  template(forward_exception)                                          \
  template(throw_range_check_failed)       /* throws ArrayIndexOutOfBoundsException */ \
  template(throw_index_exception)          /* throws IndexOutOfBoundsException */ \
  template(throw_div0_exception)                                       \
  template(throw_null_pointer_exception)                               \
  template(register_finalizer)                                         \
  template(new_instance)                                               \
  template(fast_new_instance)                                          \
  template(fast_new_instance_init_check)                               \
  template(new_type_array)                                             \
  template(new_object_array)                                           \
  template(new_multi_array)                                            \
  template(handle_exception_nofpu)         /* optimized version that does not preserve fpu registers */ \
  template(handle_exception)                                           \
  template(handle_exception_from_callee)                               \
  template(throw_array_store_exception)                                \
  template(throw_class_cast_exception)                                 \
  template(throw_incompatible_class_change_error)                      \
  template(slow_subtype_check)                                         \
  template(monitorenter)                                               \
  template(monitorenter_nofpu)             /* optimized version that does not preserve fpu registers */ \
  template(monitorexit)                                                \
  template(monitorexit_nofpu)              /* optimized version that does not preserve fpu registers */ \
  template(deoptimize)                                                 \
  template(access_field_patching)                                      \
  template(load_klass_patching)                                        \
  template(load_mirror_patching)                                       \
  template(load_appendix_patching)                                     \
  template(fpu2long_stub)                                              \
  template(counter_overflow)                                           \
  template(predicate_failed_trap)                                      \

#else
#define C1_STUBS_DO(template)
#endif

// Opto stubs can have different blob types and may include some JVMTI
// stubs

#ifdef COMPILER2
// template(name, type)
#if INCLUDE_JVMTI
#define OPTO_JVMTI_STUBS_DO(template)                                  \
  template(notify_jvmti_vthread_start, address)                        \
  template(notify_jvmti_vthread_end, address)                          \
  template(notify_jvmti_vthread_mount, address)                        \
  template(notify_jvmti_vthread_unmount, address)                      \

#else
#define OPTO_JVMTI_STUBS_DO(template)
#endif // INCLUDE_JVMTI

#define OPTO_STUBS_DO(template)                                        \
  template(uncommon_trap, UncommonTrapBlob*)                           \
  template(exception, ExceptionBlob*)                                  \
  template(new_instance_Java, address)                                 \
  template(new_array_Java, address)                                    \
  template(new_array_nozero_Java, address)                             \
  template(multianewarray2_Java, address)                              \
  template(multianewarray3_Java, address)                              \
  template(multianewarray4_Java, address)                              \
  template(multianewarray5_Java, address)                              \
  template(multianewarrayN_Java, address)                              \
  template(complete_monitor_locking_Java, address)                     \
  template(complete_monitor_locking_C , address)                       \
  template(monitor_notify_Java, address)                               \
  template(monitor_notifyAll_Java, address)                            \
  template(rethrow_Java, address)                                      \
  template(slow_arraycopy_Java, address)                               \
  template(register_finalizer_Java, address)                           \
  template(class_init_barrier_Java, address)                           \
  OPTO_JVMTI_STUBS_DO(template)                                        \

#else
#define OPTO_STUBS_DO(template)
#endif

// generate a stub id enum tag from a name

#define STUB_ID_NAME(base) base##_id

// generate a blob id enum tag from a name

#define BLOB_ID_NAME(base) base##_id

// generate a blob field name

#define BLOB_FIELD_NAME(base) _##base##_blob

#endif // SHARE_RUNTIME_SHAREDRUNTIME_ID_HPP

