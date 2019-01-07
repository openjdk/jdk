/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_PARALLEL_PSFILEBACKEDVIRTUALSPACE_HPP
#define SHARE_VM_GC_PARALLEL_PSFILEBACKEDVIRTUALSPACE_HPP

#include "gc/parallel/psVirtualspace.hpp"

class PSFileBackedVirtualSpace : public PSVirtualSpace {
private:
  const char* _file_path;
  int _fd;
  bool _mapping_succeeded;
public:
  PSFileBackedVirtualSpace(ReservedSpace rs, size_t alignment, const char* file_path);
  PSFileBackedVirtualSpace(ReservedSpace rs, const char* file_path);

  bool   initialize();
  bool   expand_by(size_t bytes);
  bool   shrink_by(size_t bytes);
  size_t expand_into(PSVirtualSpace* space, size_t bytes);
  void   release();
};
#endif // SHARE_VM_GC_PARALLEL_PSFILEBACKEDVIRTUALSPACE_HPP

