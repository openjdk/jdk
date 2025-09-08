/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_SOFTREFPOLICY_HPP
#define SHARE_GC_SHARED_SOFTREFPOLICY_HPP

class SoftRefPolicy {
 private:
  // Set to true when policy wants soft refs cleared.
  // Reset to false by gc after it clears all soft refs.
  bool _should_clear_all_soft_refs;

 public:
  SoftRefPolicy() :
    _should_clear_all_soft_refs(false) {}

  bool should_clear_all_soft_refs() { return _should_clear_all_soft_refs; }
  void set_should_clear_all_soft_refs(bool v) { _should_clear_all_soft_refs = v; }
};

#endif // SHARE_GC_SHARED_SOFTREFPOLICY_HPP
