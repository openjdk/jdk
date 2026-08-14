/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/barrierSetRuntime.hpp"
#include "oops/access.inline.hpp"
#include "oops/valuePayload.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "utilities/macros.hpp"

JRT_LEAF(void, BarrierSetRuntime::value_copy(address src, address dst, InlineLayoutInfo* li))
  ValuePayload src_payload = ValuePayload::construct_from_parts(src, li->klass(), li->kind());
  ValuePayload dst_payload = ValuePayload::construct_from_parts(dst, li->klass(), li->kind());
  HeapAccess<>::value_copy(src_payload, dst_payload);
JRT_END

JRT_LEAF(void, BarrierSetRuntime::value_copy_is_dest_uninitialized(address src, address dst, InlineLayoutInfo* li))
  ValuePayload src_payload = ValuePayload::construct_from_parts(src, li->klass(), li->kind());
  ValuePayload dst_payload = ValuePayload::construct_from_parts(dst, li->klass(), li->kind());
  HeapAccess<IS_DEST_UNINITIALIZED>::value_copy(src_payload, dst_payload);
JRT_END
