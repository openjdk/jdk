/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSETS_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSETS_HPP

#include "gc_implementation/g1/heapRegionSet.inline.hpp"

//////////////////// FreeRegionList ////////////////////

class FreeRegionList : public HeapRegionLinkedList {
protected:
  virtual const char* verify_region_extra(HeapRegion* hr);

  virtual bool regions_humongous() { return false; }
  virtual bool regions_empty()     { return true;  }

public:
  FreeRegionList(const char* name) : HeapRegionLinkedList(name) { }
};

//////////////////// MasterFreeRegionList ////////////////////

class MasterFreeRegionList : public FreeRegionList {
protected:
  virtual const char* verify_region_extra(HeapRegion* hr);
  virtual bool check_mt_safety();

public:
  MasterFreeRegionList(const char* name) : FreeRegionList(name) { }
};

//////////////////// SecondaryFreeRegionList ////////////////////

class SecondaryFreeRegionList : public FreeRegionList {
protected:
  virtual bool check_mt_safety();

public:
  SecondaryFreeRegionList(const char* name) : FreeRegionList(name) { }
};

//////////////////// OldRegionSet ////////////////////

class OldRegionSet : public HeapRegionSet {
protected:
  virtual const char* verify_region_extra(HeapRegion* hr);

  virtual bool regions_humongous() { return false; }
  virtual bool regions_empty()     { return false; }

public:
  OldRegionSet(const char* name) : HeapRegionSet(name) { }
};

//////////////////// MasterOldRegionSet ////////////////////

class MasterOldRegionSet : public OldRegionSet {
private:
protected:
  virtual bool check_mt_safety();

public:
  MasterOldRegionSet(const char* name) : OldRegionSet(name) { }
};

//////////////////// HumongousRegionSet ////////////////////

class HumongousRegionSet : public HeapRegionSet {
protected:
  virtual const char* verify_region_extra(HeapRegion* hr);

  virtual bool regions_humongous() { return true;  }
  virtual bool regions_empty()     { return false; }

public:
  HumongousRegionSet(const char* name) : HeapRegionSet(name) { }
};

//////////////////// MasterHumongousRegionSet ////////////////////

class MasterHumongousRegionSet : public HumongousRegionSet {
protected:
  virtual bool check_mt_safety();

public:
  MasterHumongousRegionSet(const char* name) : HumongousRegionSet(name) { }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSETS_HPP
