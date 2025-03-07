/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "gc/z/zGCIdPrinter.hpp"
#include "include/jvm.h"

ZGCIdPrinter* ZGCIdPrinter::_instance;

void ZGCIdPrinter::initialize() {
  _instance = new ZGCIdPrinter();
  GCId::set_printer(_instance);
}

int ZGCIdPrinter::print_gc_id_unchecked(uint gc_id, char* buf, size_t len) {
  if (gc_id == _minor_gc_id) {
    // Minor collections are always tagged with 'y'
    return jio_snprintf(buf, len, "GC(%u) y: ", gc_id);
  }

  if (gc_id == _major_gc_id) {
    // Major collections are either tagged with 'Y' or 'O',
    // this is controlled by _major_tag.
    return jio_snprintf(buf, len, "GC(%u) %c: ", gc_id, _major_tag);
  }

  // The initial log for each GC should be untagged this
  // is handled by not yet having set the current GC id
  // for that collection and thus falling through to here.
  return jio_snprintf(buf, len, "GC(%u) ", gc_id);
}

size_t ZGCIdPrinter::print_gc_id(uint gc_id, char* buf, size_t len) {
  const int ret = print_gc_id_unchecked(gc_id, buf, len);
  assert(ret > 0, "Failed to print prefix. Log buffer too small?");
  return (size_t)ret;
}

ZGCIdPrinter::ZGCIdPrinter()
  : _minor_gc_id(GCId::undefined()),
    _major_gc_id(GCId::undefined()),
    _major_tag('-') { }

void ZGCIdPrinter::set_minor_gc_id(uint id) {
  _minor_gc_id = id;
}

void ZGCIdPrinter::set_major_gc_id(uint id) {
 _major_gc_id = id;
}

void ZGCIdPrinter::set_major_tag(char tag) {
  _major_tag = tag;
}

ZGCIdMinor::ZGCIdMinor(uint gc_id) {
  ZGCIdPrinter::_instance->set_minor_gc_id(gc_id);
}

ZGCIdMinor::~ZGCIdMinor() {
  ZGCIdPrinter::_instance->set_minor_gc_id(GCId::undefined());
}

ZGCIdMajor::ZGCIdMajor(uint gc_id, char tag) {
  ZGCIdPrinter::_instance->set_major_gc_id(gc_id);
  ZGCIdPrinter::_instance->set_major_tag(tag);
}

ZGCIdMajor::~ZGCIdMajor() {
  ZGCIdPrinter::_instance->set_major_gc_id(GCId::undefined());
  ZGCIdPrinter::_instance->set_major_tag('-');
}
