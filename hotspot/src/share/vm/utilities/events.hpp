/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_EVENTS_HPP
#define SHARE_VM_UTILITIES_EVENTS_HPP

#include "memory/allocation.hpp"
#include "utilities/top.hpp"

// Events and EventMark provide interfaces to log events taking place in the vm.
// This facility is extremly useful for post-mortem debugging. The eventlog
// often provides crucial information about events leading up to the crash.
//
// All arguments past the format string must be passed as an intptr_t.
//
// To log a single event use:
//    Events::log("New nmethod has been created " INTPTR_FORMAT, nm);
//
// To log a block of events use:
//    EventMark m("GarbageCollecting %d", (intptr_t)gc_number);
//
// The constructor to eventlog indents the eventlog until the
// destructor has been executed.
//
// IMPLEMENTATION RESTRICTION:
//   Max 3 arguments are saved for each logged event.
//

class Events : AllStatic {
 public:
  // Logs an event, format as printf
  static void log(const char* format, ...) PRODUCT_RETURN;

  // Prints all events in the buffer
  static void print_all(outputStream* st) PRODUCT_RETURN;

  // Prints last number events from the event buffer
  static void print_last(outputStream *st, int number) PRODUCT_RETURN;
};

class EventMark : public StackObj {
 public:
  // log a begin event, format as printf
  EventMark(const char* format, ...) PRODUCT_RETURN;
  // log an end event
  ~EventMark() PRODUCT_RETURN;
};

int print_all_events(outputStream *st);

#endif // SHARE_VM_UTILITIES_EVENTS_HPP
