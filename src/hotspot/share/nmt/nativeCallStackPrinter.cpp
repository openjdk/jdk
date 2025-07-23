/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/log.hpp"
#include "nmt/nativeCallStackPrinter.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/ostream.hpp"

NativeCallStackPrinter::NativeCallStackPrinter(outputStream* out) :
    _text_storage(mtNMT, Arena::Tag::tag_other, 128 * K), _out(out)
{}

void NativeCallStackPrinter::print_stack(const NativeCallStack* stack) const {
  for (int i = 0; i < NMT_TrackingStackDepth; i++) {
    const address pc = stack->get_frame(i);
    if (pc == nullptr) {
      break;
    }
    bool created = false;
    const char** cached_frame_text = _cache.put_if_absent(pc, &created);
    if (created) {
      stringStream ss(4 * K);
      stack->print_frame(&ss, pc);
      (*cached_frame_text) = ss.as_string(&_text_storage);
    }
    _out->print_raw_cr(*cached_frame_text);
  }
}
