/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDMACROS_HPP
#define SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDMACROS_HPP

/**
 *
 * If a traceid is used, depending on epoch, either the first or the second bit is tagged.
 * If a class member (method) is used, either the third or fourth bit is tagged.
 * Which bit to set is a function of the epoch. This allows for concurrent tagging.
 *
 * We also tag individual methods by using the _trace_flags field,
 * (see jfr/support/jfrTraceIdExtension.hpp for details)
 *
 */

// the following are defined in jfr/support/jfrKlassExtension.hpp
//
// #define JDK_JFR_EVENT_SUBKLASS                 16
// #define JDK_JFR_EVENT_KLASS                    32
// #define EVENT_HOST_KLASS                       64

// static bits
#define META_SHIFT                                8
#define EPOCH_1_CLEARED_META_BIT                  USED_BIT
#define EPOCH_1_CLEARED_BIT                       (EPOCH_1_CLEARED_META_BIT << META_SHIFT)
#define EPOCH_2_CLEARED_META_BIT                  (USED_BIT << 1)
#define EPOCH_2_CLEARED_BIT                       (EPOCH_2_CLEARED_META_BIT << META_SHIFT)
#define LEAKP_META_BIT                            (USED_BIT << 2)
#define LEAKP_BIT                                 (LEAKP_META_BIT << META_SHIFT)
#define TRANSIENT_META_BIT                        (USED_BIT << 3)
#define TRANSIENT_BIT                             (TRANSIENT_META_BIT << META_SHIFT)
#define SERIALIZED_META_BIT                       (USED_BIT << 4)
#define SERIALIZED_BIT                            (SERIALIZED_META_BIT << META_SHIFT)
#define TRACE_ID_SHIFT                            16
#define METHOD_ID_NUM_MASK                        ((1 << TRACE_ID_SHIFT) - 1)
#define META_BITS                                 (SERIALIZED_BIT | TRANSIENT_BIT | LEAKP_BIT | EPOCH_2_CLEARED_BIT | EPOCH_1_CLEARED_BIT)
#define EVENT_BITS                                (EVENT_HOST_KLASS | JDK_JFR_EVENT_KLASS | JDK_JFR_EVENT_SUBKLASS)
#define USED_BITS                                 (METHOD_USED_EPOCH_2_BIT | METHOD_USED_EPOCH_1_BIT | USED_EPOCH_2_BIT | USED_EPOCH_1_BIT)
#define ALL_BITS                                  (META_BITS | EVENT_BITS | USED_BITS)
#define ALL_BITS_MASK                             (~(ALL_BITS))

// epoch relative bits
#define IN_USE_THIS_EPOCH_BIT                     (JfrTraceIdEpoch::in_use_this_epoch_bit())
#define IN_USE_PREV_EPOCH_BIT                     (JfrTraceIdEpoch::in_use_prev_epoch_bit())
#define METHOD_IN_USE_THIS_EPOCH_BIT              (JfrTraceIdEpoch::method_in_use_this_epoch_bit())
#define METHOD_IN_USE_PREV_EPOCH_BIT              (JfrTraceIdEpoch::method_in_use_prev_epoch_bit())
#define METHOD_AND_CLASS_IN_USE_THIS_EPOCH_BITS   (JfrTraceIdEpoch::method_and_class_in_use_this_epoch_bits())
#define METHOD_AND_CLASS_IN_USE_PREV_EPOCH_BITS   (JfrTraceIdEpoch::method_and_class_in_use_prev_epoch_bits())
#define METHOD_FLAG_IN_USE_THIS_EPOCH_BIT         ((jbyte)IN_USE_THIS_EPOCH_BIT)
#define METHOD_FLAG_IN_USE_PREV_EPOCH_BIT         ((jbyte)IN_USE_PREV_EPOCH_BIT)

// operators
#define TRACE_ID_RAW(ptr)                         ((ptr)->trace_id())
#define TRACE_ID(ptr)                             (TRACE_ID_RAW(ptr) >> TRACE_ID_SHIFT)
#define TRACE_ID_MASKED(ptr)                      (TRACE_ID_RAW(ptr) & ALL_BITS_MASK)
#define TRACE_ID_PREDICATE(ptr, bits)             ((TRACE_ID_RAW(ptr) & bits) != 0)
#define TRACE_ID_TAG(ptr, bits)                   (set_traceid_bits(bits, (ptr)->trace_id_addr()))
#define TRACE_ID_TAG_CAS(ptr, bits)               (set_traceid_bits_cas(bits, (ptr)->trace_id_addr()))
#define TRACE_ID_CLEAR(ptr, bits)                 (set_traceid_mask(bits, (ptr)->trace_id_addr()))
#define TRACE_ID_META_TAG(ptr, bits)              (set_traceid_meta_bits(bits, (ptr)->trace_id_addr()))
#define TRACE_ID_META_CLEAR(ptr, bits)            (set_traceid_meta_mask(bits, (ptr)->trace_id_addr()))
#define METHOD_ID(kls, method)                    (TRACE_ID_MASKED(kls) | (method)->orig_method_idnum())
#define METHOD_FLAG_PREDICATE(method, bits)       ((method)->is_trace_flag_set(bits))
#define METHOD_FLAG_TAG(method, bits)             (set_bits(bits, (method)->trace_flags_addr()))
#define METHOD_META_TAG(method, bits)             (set_meta_bits(bits, (method)->trace_meta_addr()))
#define METHOD_FLAG_CLEAR(method, bits)           (clear_bits_cas(bits, (method)->trace_flags_addr()))
#define METHOD_META_CLEAR(method, bits)           (set_meta_mask(bits, (method)->trace_meta_addr()))

// predicates
#define USED_THIS_EPOCH(ptr)                      (TRACE_ID_PREDICATE(ptr, (TRANSIENT_BIT | IN_USE_THIS_EPOCH_BIT)))
#define NOT_USED_THIS_EPOCH(ptr)                  (!(USED_THIS_EPOCH(ptr)))
#define USED_PREV_EPOCH(ptr)                      (TRACE_ID_PREDICATE(ptr, (TRANSIENT_BIT | IN_USE_PREV_EPOCH_BIT)))
#define USED_ANY_EPOCH(ptr)                       (TRACE_ID_PREDICATE(ptr, (TRANSIENT_BIT | USED_EPOCH_2_BIT | USED_EPOCH_1_BIT)))
#define METHOD_USED_THIS_EPOCH(kls)               (TRACE_ID_PREDICATE(kls, (METHOD_IN_USE_THIS_EPOCH_BIT)))
#define METHOD_NOT_USED_THIS_EPOCH(kls)           (!(METHOD_USED_THIS_EPOCH(kls)))
#define METHOD_USED_PREV_EPOCH(kls)               (TRACE_ID_PREDICATE(kls, (METHOD_IN_USE_PREV_EPOCH_BIT)))
#define METHOD_USED_ANY_EPOCH(kls)                (TRACE_ID_PREDICATE(kls, (METHOD_IN_USE_PREV_EPOCH_BIT | METHOD_IN_USE_THIS_EPOCH_BIT)))
#define METHOD_AND_CLASS_USED_THIS_EPOCH(kls)     (TRACE_ID_PREDICATE(kls, (METHOD_AND_CLASS_IN_USE_THIS_EPOCH_BITS)))
#define METHOD_AND_CLASS_USED_PREV_EPOCH(kls)     (TRACE_ID_PREDICATE(kls, (METHOD_AND_CLASS_IN_USE_PREV_EPOCH_BITS)))
#define METHOD_AND_CLASS_USED_ANY_EPOCH(kls)      (METHOD_USED_ANY_EPOCH(kls) && USED_ANY_EPOCH(kls))
#define METHOD_FLAG_USED_THIS_EPOCH(method)       (METHOD_FLAG_PREDICATE(method, (METHOD_FLAG_IN_USE_THIS_EPOCH_BIT)))
#define METHOD_FLAG_NOT_USED_THIS_EPOCH(method)   (!(METHOD_FLAG_USED_THIS_EPOCH(method)))
#define METHOD_FLAG_USED_PREV_EPOCH(method)       (METHOD_FLAG_PREDICATE(method, (METHOD_FLAG_IN_USE_PREV_EPOCH_BIT)))

// setters
#define SET_USED_THIS_EPOCH(ptr)                  (TRACE_ID_TAG(ptr, IN_USE_THIS_EPOCH_BIT))
#define SET_METHOD_AND_CLASS_USED_THIS_EPOCH(kls) (TRACE_ID_TAG(kls, METHOD_AND_CLASS_IN_USE_THIS_EPOCH_BITS))
#define SET_METHOD_FLAG_USED_THIS_EPOCH(method)   (METHOD_FLAG_TAG(method, METHOD_FLAG_IN_USE_THIS_EPOCH_BIT))
#define CLEAR_METHOD_AND_CLASS_PREV_EPOCH_MASK    (~(METHOD_IN_USE_PREV_EPOCH_BIT | IN_USE_PREV_EPOCH_BIT))
#define CLEAR_METHOD_AND_CLASS_PREV_EPOCH(kls)    (TRACE_ID_CLEAR(kls, CLEAR_METHOD_AND_CLASS_PREV_EPOCH_MASK))
#define CLEAR_METHOD_FLAG_USED_PREV_EPOCH(method) (METHOD_FLAG_CLEAR(method, METHOD_FLAG_IN_USE_PREV_EPOCH_BIT))

// types
#define IS_JDK_JFR_EVENT_KLASS(kls)               (TRACE_ID_PREDICATE(kls, JDK_JFR_EVENT_KLASS))
#define IS_JDK_JFR_EVENT_SUBKLASS(kls)            (TRACE_ID_PREDICATE(kls, JDK_JFR_EVENT_SUBKLASS))
#define IS_NOT_AN_EVENT_SUB_KLASS(kls)            (!(IS_JDK_JFR_EVENT_SUBKLASS(kls)))
#define IS_EVENT_HOST_KLASS(kls)                  (TRACE_ID_PREDICATE(kls, EVENT_HOST_KLASS))
#define SET_JDK_JFR_EVENT_KLASS(kls)              (TRACE_ID_TAG(kls, JDK_JFR_EVENT_KLASS))
#define SET_JDK_JFR_EVENT_SUBKLASS(kls)           (TRACE_ID_TAG(kls, JDK_JFR_EVENT_SUBKLASS))
#define SET_EVENT_HOST_KLASS(kls)                 (TRACE_ID_TAG(kls, EVENT_HOST_KLASS))
#define EVENT_KLASS_MASK(kls)                     (TRACE_ID_RAW(kls) & EVENT_BITS)

// meta
#define META_MASK                                 (~(SERIALIZED_META_BIT | TRANSIENT_META_BIT | LEAKP_META_BIT))
#define SET_LEAKP(ptr)                            (TRACE_ID_META_TAG(ptr, LEAKP_META_BIT))
#define IS_LEAKP(ptr)                             (TRACE_ID_PREDICATE(ptr, LEAKP_BIT))
#define SET_TRANSIENT(ptr)                        (TRACE_ID_META_TAG(ptr, TRANSIENT_META_BIT))
#define IS_SERIALIZED(ptr)                        (TRACE_ID_PREDICATE(ptr, SERIALIZED_BIT))
#define IS_NOT_SERIALIZED(ptr)                    (!(IS_SERIALIZED(ptr)))
#define SHOULD_TAG(ptr)                           (NOT_USED_THIS_EPOCH(ptr))
#define SHOULD_TAG_KLASS_METHOD(ptr)              (METHOD_NOT_USED_THIS_EPOCH(ptr))
#define SET_SERIALIZED(ptr)                       (TRACE_ID_META_TAG(ptr, SERIALIZED_META_BIT))
#define CLEAR_SERIALIZED(ptr)                     (TRACE_ID_META_CLEAR(ptr, META_MASK))
#define SET_PREV_EPOCH_CLEARED_BIT(ptr)           (TRACE_ID_META_TAG(ptr, IN_USE_PREV_EPOCH_BIT))
#define IS_METHOD_SERIALIZED(method)              (METHOD_FLAG_PREDICATE(method, SERIALIZED_BIT))
#define IS_METHOD_LEAKP_USED(method)              (METHOD_FLAG_PREDICATE(method, LEAKP_BIT))
#define METHOD_NOT_SERIALIZED(method)             (!(IS_METHOD_SERIALIZED(method)))
#define SET_METHOD_LEAKP(method)                  (METHOD_META_TAG(method, LEAKP_META_BIT))
#define SET_METHOD_SERIALIZED(method)             (METHOD_META_TAG(method, SERIALIZED_META_BIT))
#define CLEAR_METHOD_SERIALIZED(method)           (METHOD_META_CLEAR(method, META_MASK))
#define SET_PREV_EPOCH_METHOD_CLEARED_BIT(ptr)    (METHOD_META_TAG(ptr, IN_USE_PREV_EPOCH_BIT))
#define CLEAR_LEAKP(ptr)                          (TRACE_ID_META_CLEAR(ptr, (~(LEAKP_META_BIT))))
#define CLEAR_THIS_EPOCH_CLEARED_BIT(ptr)         (TRACE_ID_META_CLEAR(ptr,(~(IN_USE_THIS_EPOCH_BIT))))
#define CLEAR_THIS_EPOCH_METHOD_CLEARED_BIT(ptr)  (METHOD_META_CLEAR(ptr,(~(IN_USE_THIS_EPOCH_BIT))))

#endif // SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDMACROS_HPP
