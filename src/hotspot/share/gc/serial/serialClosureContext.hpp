/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SERIAL_SERIALCLOSURECONTEXT_HPP
#define SHARE_GC_SERIAL_SERIALCLOSURECONTEXT_HPP

#include "gc/shared/referenceProcessor.hpp"

class SerialClosureContext : public AbstractClosureContext {
  BoolObjectClosure& _is_alive;
  OopClosure& _keep_alive;
  VoidClosure& _complete_gc;
public:
  SerialClosureContext(BoolObjectClosure& is_alive, OopClosure& keep_alive, VoidClosure& complete_gc)
    : _is_alive(is_alive), _keep_alive(keep_alive), _complete_gc(complete_gc) {};
  BoolObjectClosure* is_alive(uint worker_id)                                    { return &_is_alive; }
  OopClosure* keep_alive(uint worker_id)                                         { return &_keep_alive; }
  VoidClosure* complete_gc(uint worker_id)                                       { return &_complete_gc; }
  void prepare_run_task(uint queue_count, ThreadModel tm, bool marks_oops_alive) { log_debug(gc, ref)("SerialClosureContext: prepare_run_task"); };
};

#endif /* SHARE_GC_SERIAL_SERIALCLOSURECONTEXT_HPP */
