/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_TRACE_TRACEDATATYPES_HPP
#define SHARE_VM_TRACE_TRACEDATATYPES_HPP

#include <stddef.h>

#include "utilities/globalDefinitions.hpp"

enum {
  CONTENT_TYPE_NONE             = 0,
  CONTENT_TYPE_CLASS            = 20,
  CONTENT_TYPE_UTF8             = 21,
  CONTENT_TYPE_THREAD           = 22,
  CONTENT_TYPE_STACKTRACE       = 23,
  CONTENT_TYPE_BYTES            = 24,
  CONTENT_TYPE_EPOCHMILLIS      = 25,
  CONTENT_TYPE_MILLIS           = 26,
  CONTENT_TYPE_NANOS            = 27,
  CONTENT_TYPE_TICKS            = 28,
  CONTENT_TYPE_ADDRESS          = 29,
  CONTENT_TYPE_PERCENTAGE       = 30,

  JVM_CONTENT_TYPES_START       = 33,
  JVM_CONTENT_TYPES_END         = 255
};

enum ReservedEvent {
  EVENT_METADATA,
  EVENT_CHECKPOINT,
  EVENT_BUFFERLOST,

  NUM_RESERVED_EVENTS = JVM_CONTENT_TYPES_END
};

typedef enum ReservedEvent ReservedEvent;

class Symbol;

#endif // SHARE_VM_TRACE_TRACEDATATYPES_HPP
