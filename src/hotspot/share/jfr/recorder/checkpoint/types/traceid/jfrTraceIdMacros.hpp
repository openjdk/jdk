/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JFR_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDMACROS_HPP
#define SHARE_VM_JFR_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDMACROS_HPP

#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdBits.inline.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/support/jfrKlassExtension.hpp"
#include "utilities/globalDefinitions.hpp"

/**
 *
 * If a traceid is used, depending on epoch, either the first or the second bit is tagged.
 * If a class member (method) is used, either the third or fourth bit is tagged.
 * Which bit to set is a function of the epoch. This allows for concurrent tagging.
 *
 * LeakProfiler subsystem gets its own byte and uses the same tagging scheme but is shifted up 8.
 *
 * We also tag the individual method by using the TraceFlag field,
 * (see jfr/support/jfrTraceIdExtension.hpp for details)
 *
 */

// these are defined in jfr/support/jfrKlassExtension.hpp
//
// #define JDK_JFR_EVENT_SUBKLASS  16
// #define JDK_JFR_EVENT_KLASS     32
// #define EVENT_HOST_KLASS        64

#define IS_JDK_JFR_EVENT_SUBKLASS(ptr) (((ptr)->trace_id() & (JDK_JFR_EVENT_SUBKLASS)) != 0)

#define ANY_USED_BITS (USED_EPOCH_2_BIT         | \
                       USED_EPOCH_1_BIT         | \
                       METHOD_USED_EPOCH_2_BIT  | \
                       METHOD_USED_EPOCH_1_BIT  | \
                       LEAKP_USED_EPOCH_2_BIT   | \
                       LEAKP_USED_EPOCH_1_BIT)

#define TRACE_ID_META_BITS (EVENT_HOST_KLASS | JDK_JFR_EVENT_KLASS | JDK_JFR_EVENT_SUBKLASS | ANY_USED_BITS)

#define ANY_EVENT                       (EVENT_HOST_KLASS | JDK_JFR_EVENT_KLASS | JDK_JFR_EVENT_SUBKLASS)
#define IS_JDK_JFR_EVENT_KLASS(ptr)     (((ptr)->trace_id() & JDK_JFR_EVENT_KLASS) != 0)
#define IS_EVENT_HOST_KLASS(ptr)        (((ptr)->trace_id() & EVENT_HOST_KLASS) != 0)
#define IS_NOT_AN_EVENT_KLASS(ptr)      (!IS_EVENT_KLASS(ptr))
#define IS_NOT_AN_EVENT_SUB_KLASS(ptr)  (!IS_JDK_JFR_EVENT_SUBKLASS(ptr))
#define IS_NOT_JDK_JFR_EVENT_KLASS(ptr) (!IS_JDK_JFR_EVENT_KLASS(ptr))
#define EVENT_FLAGS_MASK(ptr)           (((ptr)->trace_id() & ANY_EVENT) != 0)
#define UNEVENT(ptr)                    ((ptr)->set_trace_id(((ptr)->trace_id()) & ~ANY_EVENT))

#define TRACE_ID_SHIFT 16

#define TRACE_ID_MASKED(id)             (id & ~TRACE_ID_META_BITS)
#define TRACE_ID_VALUE(id)              (TRACE_ID_MASKED(id) >> TRACE_ID_SHIFT)
#define TRACE_ID_MASKED_PTR(ptr)        (TRACE_ID_MASKED((ptr)->trace_id()))
#define TRACE_ID_RAW(ptr)               ((ptr)->trace_id())
#define TRACE_ID(ptr)                   (TRACE_ID_MASKED_PTR(ptr) >> TRACE_ID_SHIFT)
#define METHOD_ID(kls, meth)            (TRACE_ID_MASKED_PTR(kls) | (meth)->method_idnum())
#define SET_TAG(ptr, tag)               (set_traceid_bits(tag, (ptr)->trace_id_addr()))
#define SET_LEAKP_TAG(ptr, tag)         (set_leakp_traceid_bits(tag, (ptr)->trace_id_addr()))
#define SET_TAG_CAS(ptr, tag)           (set_traceid_bits_cas(tag, (ptr)->trace_id_addr()))
#define SET_LEAKP_TAG_CAS(ptr, tag)     (set_leakp_traceid_bits_cas(tag, (ptr)->trace_id_addr()))

#define IN_USE_THIS_EPOCH_BIT           (JfrTraceIdEpoch::in_use_this_epoch_bit())
#define IN_USE_PREV_EPOCH_BIT           (JfrTraceIdEpoch::in_use_prev_epoch_bit())
#define LEAKP_IN_USE_THIS_EPOCH_BIT     (JfrTraceIdEpoch::leakp_in_use_this_epoch_bit())
#define LEAKP_IN_USE_PREV_EPOCH_BIT     (JfrTraceIdEpoch::leakp_in_use_prev_epoch_bit())

#define METHOD_IN_USE_THIS_EPOCH_BIT    (JfrTraceIdEpoch::method_in_use_this_epoch_bit())
#define METHOD_IN_USE_PREV_EPOCH_BIT    (JfrTraceIdEpoch::method_in_use_prev_epoch_bit())
#define METHOD_AND_CLASS_IN_USE_THIS_EPOCH_BITS (JfrTraceIdEpoch::method_and_class_in_use_this_epoch_bits())
#define METHOD_AND_CLASS_IN_USE_PREV_EPOCH_BITS (JfrTraceIdEpoch::method_and_class_in_use_prev_epoch_bits())

#define UNUSE_THIS_EPOCH_MASK           (~(IN_USE_THIS_EPOCH_BIT))
#define UNUSE_PREV_EPOCH_MASK           (~(IN_USE_PREV_EPOCH_BIT))
#define LEAKP_UNUSE_THIS_EPOCH_MASK     UNUSE_THIS_EPOCH_MASK
#define LEAKP_UNUSE_PREV_EPOCH_MASK     UNUSE_PREV_EPOCH_MASK

#define UNUSE_METHOD_THIS_EPOCH_MASK    (~(METHOD_IN_USE_THIS_EPOCH_BIT))
#define UNUSE_METHOD_PREV_EPOCH_MASK    (~(METHOD_IN_USE_PREV_EPOCH_BIT))
#define LEAKP_UNUSE_METHOD_THIS_EPOCH_MASK (~(UNUSE_METHOD_THIS_EPOCH_MASK))
#define LEAKP_UNUSE_METHOD_PREV_EPOCH_MASK (~UNUSE_METHOD_PREV_EPOCH_MASK))

#define UNUSE_METHOD_AND_CLASS_THIS_EPOCH_MASK (~(METHOD_IN_USE_THIS_EPOCH_BIT | IN_USE_THIS_EPOCH_BIT))
#define UNUSE_METHOD_AND_CLASS_PREV_EPOCH_MASK (~(METHOD_IN_USE_PREV_EPOCH_BIT | IN_USE_PREV_EPOCH_BIT))

#define SET_USED_THIS_EPOCH(ptr)        (SET_TAG(ptr, IN_USE_THIS_EPOCH_BIT))
#define SET_USED_PREV_EPOCH(ptr)        (SET_TAG_CAS(ptr, IN_USE_PREV_EPOCH_BIT))
#define SET_LEAKP_USED_THIS_EPOCH(ptr)  (SET_LEAKP_TAG(ptr, IN_USE_THIS_EPOCH_BIT))
#define SET_LEAKP_USED_PREV_EPOCH(ptr)  (SET_LEAKP_TAG(ptr, IN_USE_PREV_EPOCH_BIT))
#define SET_METHOD_AND_CLASS_USED_THIS_EPOCH(kls) (SET_TAG(kls, METHOD_AND_CLASS_IN_USE_THIS_EPOCH_BITS))

#define USED_THIS_EPOCH(ptr)            (((ptr)->trace_id() & IN_USE_THIS_EPOCH_BIT) != 0)
#define NOT_USED_THIS_EPOCH(ptr)        (!USED_THIS_EPOCH(ptr))
#define USED_PREV_EPOCH(ptr)            (((ptr)->trace_id() & IN_USE_PREV_EPOCH_BIT) != 0)
#define NOT_USED_PREV_EPOCH(ptr)        (!USED_PREV_EPOCH(ptr))
#define USED_ANY_EPOCH(ptr)             (((ptr)->trace_id() & (USED_EPOCH_2_BIT | USED_EPOCH_1_BIT)) != 0)
#define NOT_USED_ANY_EPOCH(ptr)         (!USED_ANY_EPOCH(ptr))

#define LEAKP_USED_THIS_EPOCH(ptr)      (((ptr)->trace_id() & LEAKP_IN_USE_THIS_EPOCH_BIT) != 0)
#define LEAKP_NOT_USED_THIS_EPOCH(ptr)  (!LEAKP_USED_THIS_EPOCH(ptr))
#define LEAKP_USED_PREV_EPOCH(ptr)      (((ptr)->trace_id() & LEAKP_IN_USE_PREV_EPOCH_BIT) != 0)
#define LEAKP_NOT_USED_PREV_EPOCH(ptr)  (!LEAKP_USED_PREV_EPOCH(ptr))
#define LEAKP_USED_ANY_EPOCH(ptr)       (((ptr)->trace_id() & (LEAKP_USED_EPOCH_2_BIT | LEAKP_USED_EPOCH_1_BIT)) != 0)
#define LEAKP_NOT_USED_ANY_EPOCH(ptr)   (!LEAKP_USED_ANY_EPOCH(ptr))

#define ANY_USED_THIS_EPOCH(ptr)        (((ptr)->trace_id() & (LEAKP_IN_USE_THIS_EPOCH_BIT | IN_USE_THIS_EPOCH_BIT)) != 0)
#define ANY_NOT_USED_THIS_EPOCH(ptr)    (!ANY_USED_THIS_EPOCH(ptr))
#define ANY_USED_PREV_EPOCH(ptr)        (((ptr)->trace_id() & (LEAKP_IN_USE_PREV_EPOCH_BIT | IN_USE_PREV_EPOCH_BIT)) != 0)
#define ANY_NOT_USED_PREV_EPOCH(ptr)    (!ANY_USED_PREV_EPOCH(ptr))

#define METHOD_USED_THIS_EPOCH(kls)     (((kls)->trace_id() & METHOD_IN_USE_THIS_EPOCH_BIT) != 0)
#define METHOD_NOT_USED_THIS_EPOCH(kls) (!METHOD_USED_THIS_EPOCH(kls))
#define METHOD_USED_PREV_EPOCH(kls)     (((kls)->trace_id() & METHOD_IN_USE_PREV_EPOCH_BIT) != 0)
#define METHOD_NOT_USED_PREV_EPOCH(kls) (!METHOD_USED_PREV_EPOCH(kls))
#define METHOD_USED_ANY_EPOCH(kls)      (((kls)->trace_id() & (METHOD_IN_USE_PREV_EPOCH_BIT | METHOD_IN_USE_THIS_EPOCH_BIT)) != 0)

#define METHOD_NOT_USED_ANY_EPOCH(kls)  (!METHOD_USED_ANY_EPOCH(kls))

#define METHOD_AND_CLASS_USED_THIS_EPOCH(kls) ((((kls)->trace_id() & METHOD_AND_CLASS_IN_USE_THIS_EPOCH_BITS) == \
                                                                     METHOD_AND_CLASS_IN_USE_THIS_EPOCH_BITS) != 0)

#define METHOD_AND_CLASS_USED_PREV_EPOCH(kls) ((((kls)->trace_id() & METHOD_AND_CLASS_IN_USE_PREV_EPOCH_BITS) == \
                                                                     METHOD_AND_CLASS_IN_USE_PREV_EPOCH_BITS) != 0)

#define METHOD_AND_CLASS_USED_ANY_EPOCH(kls)     ((METHOD_USED_ANY_EPOCH(kls) && USED_ANY_EPOCH(kls)) != 0)
#define METHOD_AND_CLASS_NOT_USED_ANY_EPOCH(kls) (!METHOD_AND_CLASS_USED_ANY_EPOCH(kls))

#define LEAKP_METHOD_IN_USE_THIS_EPOCH  (LEAKP_IN_USE_THIS_EPOCH_BIT | METHOD_IN_USE_THIS_EPOCH_BIT)
#define LEAKP_METHOD_IN_USE_PREV_EPOCH  (LEAKP_IN_USE_PREV_EPOCH_BIT | METHOD_IN_USE_PREV_EPOCH_BIT)
#define LEAKP_METHOD_USED_THIS_EPOCH(ptr)  ((((ptr)->trace_id() & LEAKP_METHOD_IN_USE_THIS_EPOCH) == \
                                                                  LEAKP_METHOD_IN_USE_THIS_EPOCH) != 0)
#define LEAKP_METHOD_NOT_USED_THIS_EPOCH(kls) (!LEAKP_METHOD_USED_THIS_EPOCH(kls))
#define LEAKP_METHOD_USED_PREV_EPOCH(ptr)  ((((ptr)->trace_id() & LEAKP_METHOD_IN_USE_PREV_EPOCH) == \
                                                                  LEAKP_METHOD_IN_USE_PREV_EPOCH) != 0)
#define LEAKP_METHOD_NOT_USED_PREV_EPOCH(kls) (!LEAKP_METHOD_USED_PREV_EPOCH(kls))

#define UNUSE_THIS_EPOCH(ptr)           (set_traceid_mask(UNUSE_THIS_EPOCH_MASK, (ptr)->trace_id_addr()))
#define UNUSE_PREV_EPOCH(ptr)           (set_traceid_mask(UNUSE_PREV_EPOCH_MASK, (ptr)->trace_id_addr()))
#define UNUSE_METHOD_THIS_EPOCH(kls)    (set_traceid_mask(UNUSE_METHOD_THIS_EPOCH_MASK, (kls)->trace_id_addr()))
#define UNUSE_METHOD_PREV_EPOCH(kls)    (set_traceid_mask(UNUSE_METHOD_PREV_EPOCH_MASK, (kls)->trace_id_addr()))

#define LEAKP_UNUSE_THIS_EPOCH(ptr)     (set_leakp_traceid_mask(UNUSE_THIS_EPOCH_MASK, (ptr)->trace_id_addr()))
#define LEAKP_UNUSE_PREV_EPOCH(ptr)     (set_leakp_traceid_mask(UNUSE_PREV_EPOCH_MASK, (ptr)->trace_id_addr()))
#define LEAKP_UNUSE_METHOD_THIS_EPOCH(kls) (set_leakp_traceid_mask(UNUSE_METHOD_THIS_EPOCH_MASK, (kls)->trace_id_addr()))
#define LEAKP_UNUSE_METHOD_PREV_EPOCH(kls) (set_leakp_traceid_mask(UNUSE_METHOD_PREV_EPOCH_MASK, (kls)->trace_id_addr()))

#define ANY_USED(ptr)                   (((ptr)->trace_id() & ANY_USED_BITS) != 0)
#define ANY_NOT_USED(ptr)               (!ANY_USED(ptr))

#define UNUSE_METHOD_AND_CLASS_THIS_EPOCH(kls) (set_traceid_mask(UNUSE_METHOD_AND_CLASS_THIS_EPOCH_MASK, (kls)->trace_id_addr()))
#define LEAKP_UNUSE_METHOD_AND_CLASS_THIS_EPOCH(kls) (set_leakp_traceid_mask(UNUSE_METHOD_AND_CLASS_THIS_EPOCH_MASK, (kls)->trace_id_addr()))
#define UNUSE_METHOD_AND_CLASS_PREV_EPOCH(kls) (set_traceid_mask(UNUSE_METHOD_AND_CLASS_PREV_EPOCH_MASK, (kls)->trace_id_addr()))
#define LEAKP_UNUSE_METHODS_AND_CLASS_PREV_EPOCH(kls) (set_leakp_traceid_mask(UNUSE_METHOD_AND_CLASS_PREV_EPOCH_MASK, (kls)->trace_id_addr()))

#define METHOD_FLAG_USED_THIS_EPOCH(m)       ((m)->is_trace_flag_set((jbyte)JfrTraceIdEpoch::in_use_this_epoch_bit()))
#define METHOD_FLAG_NOT_USED_THIS_EPOCH(m)   (!METHOD_FLAG_USED_THIS_EPOCH(m))
#define SET_METHOD_FLAG_USED_THIS_EPOCH(m)   ((m)->set_trace_flag((jbyte)JfrTraceIdEpoch::in_use_this_epoch_bit()))
#define METHOD_FLAG_USED_PREV_EPOCH(m)       ((m)->is_trace_flag_set((jbyte)JfrTraceIdEpoch::in_use_prev_epoch_bit()))
#define METHOD_FLAG_NOT_USED_PREV_EPOCH(m)   (!METHOD_FLAG_USED_PREV_EPOCH(m))
#define METHOD_FLAG_USED_ANY_EPOCH(m)        ((METHOD_FLAG_USED_THIS_EPOCH(m) || METHOD_FLAG_USED_PREV_EPOCH(m)) != 0)
#define METHOD_FLAG_NOT_USED_ANY_EPOCH(m)    ((METHOD_FLAG_NOT_USED_THIS_EPOCH(m) && METHOD_FLAG_NOT_USED_PREV_EPOCH(m)) != 0)
#define CLEAR_METHOD_FLAG_USED_THIS_EPOCH(m) (clear_bits_cas((jbyte)JfrTraceIdEpoch::in_use_this_epoch_bit(), (m)->trace_flags_addr()))
#define CLEAR_METHOD_FLAG_USED_PREV_EPOCH(m) (clear_bits_cas((jbyte)JfrTraceIdEpoch::in_use_prev_epoch_bit(), (m)->trace_flags_addr()))

#endif // SHARE_VM_JFR_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDMACROS_HPP
