/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZGCIDPRINTER_HPP
#define SHARE_GC_Z_ZGCIDPRINTER_HPP

#include "gc/shared/gcId.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

class ZGCIdPrinter : public GCIdPrinter {
  friend class ZGCIdMinor;
  friend class ZGCIdMajor;

private:
  static ZGCIdPrinter* _instance;

  uint _minor_gc_id;
  uint _major_gc_id;
  char _major_tag;

  ZGCIdPrinter();

  void set_minor_gc_id(uint id);
  void set_major_gc_id(uint id);
  void set_major_tag(char tag);

  int print_gc_id_unchecked(uint gc_id, char *buf, size_t len);
  size_t print_gc_id(uint gc_id, char *buf, size_t len) override;

public:
  static void initialize();
};

class ZGCIdMinor : public StackObj {
public:
  ZGCIdMinor(uint gc_id);
  ~ZGCIdMinor();
};

class ZGCIdMajor : public StackObj {
public:
  ZGCIdMajor(uint gc_id, char tag);
  ~ZGCIdMajor();
};

#endif // SHARE_GC_Z_ZGCIDPRINTER_HPP
