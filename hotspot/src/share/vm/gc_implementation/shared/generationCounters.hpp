/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
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

// A GenerationCounter is a holder class for performance counters
// that track a generation

class GenerationCounters: public CHeapObj {
  friend class VMStructs;

 protected:
  PerfVariable*      _current_size;
  VirtualSpace*      _virtual_space;

  // Constant PerfData types don't need to retain a reference.
  // However, it's a good idea to document them here.
  // PerfStringConstant*     _name;
  // PerfConstant*           _min_size;
  // PerfConstant*           _max_size;
  // PerfConstant*           _spaces;

  char*              _name_space;

  // This constructor is only meant for use with the PSGenerationCounters
  // constructor.  The need for such an constructor should be eliminated
  // when VirtualSpace and PSVirtualSpace are unified.
  GenerationCounters() : _name_space(NULL), _current_size(NULL), _virtual_space(NULL) {}
 public:

  GenerationCounters(const char* name, int ordinal, int spaces,
                     VirtualSpace* v);

  ~GenerationCounters() {
    if (_name_space != NULL) FREE_C_HEAP_ARRAY(char, _name_space);
  }

  virtual void update_all() {
    _current_size->set_value(_virtual_space->committed_size());
  }

  const char* name_space() const        { return _name_space; }
};
