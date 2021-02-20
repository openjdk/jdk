/*
 * Copyright (c) 2021, Huawei Technologies Co. Ltd. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Alibaba designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

#ifndef SHARE_VM_GC_G1_G1FULLGCMARKREGIONCACHE_HPP
#define SHARE_VM_GC_G1_G1FULLGCMARKREGIONCACHE_HPP

#include "memory/allocation.hpp"

class G1FullGCMarkRegionCache {
private:
  size_t* _cache;
public:
  G1FullGCMarkRegionCache();
  void inc_live(uint hr_index, size_t words);

  void* operator new(size_t size);
  void  operator delete(void* p);

  ~G1FullGCMarkRegionCache();
};

#endif
