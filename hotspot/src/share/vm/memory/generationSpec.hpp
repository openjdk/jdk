/*
 * Copyright (c) 2001, 2004, Oracle and/or its affiliates. All rights reserved.
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

// The specification of a generation.  This class also encapsulates
// some generation-specific behavior.  This is done here rather than as a
// virtual function of Generation because these methods are needed in
// initialization of the Generations.
class GenerationSpec : public CHeapObj {
  friend class VMStructs;
private:
  Generation::Name _name;
  size_t           _init_size;
  size_t           _max_size;

public:
  GenerationSpec(Generation::Name name, size_t init_size, size_t max_size) {
    _name = name;
    _init_size = init_size;
    _max_size = max_size;
  }

  Generation* init(ReservedSpace rs, int level, GenRemSet* remset);

  // Accessors
  Generation::Name name()        const { return _name; }
  size_t init_size()             const { return _init_size; }
  void set_init_size(size_t size)      { _init_size = size; }
  size_t max_size()              const { return _max_size; }
  void set_max_size(size_t size)       { _max_size = size; }

  // Alignment
  void align(size_t alignment) {
    set_init_size(align_size_up(init_size(), alignment));
    set_max_size(align_size_up(max_size(), alignment));
  }

  // Return the number of regions contained in the generation which
  // might need to be independently covered by a remembered set.
  virtual int n_covered_regions() const { return 1; }
};

typedef GenerationSpec* GenerationSpecPtr;

// The specification of a permanent generation. This class is very
// similar to GenerationSpec in use. Due to PermGen's not being a
// true Generation, we cannot combine the spec classes either.
class PermanentGenerationSpec : public CHeapObj {
  friend class VMStructs;
private:
  PermGen::Name    _name;
  size_t           _init_size;
  size_t           _max_size;
  size_t           _read_only_size;
  size_t           _read_write_size;
  size_t           _misc_data_size;
  size_t           _misc_code_size;
  bool             _enable_shared_spaces;

  enum {
    _n_spaces = 2
  };

public:
  PermanentGenerationSpec(PermGen::Name name, size_t init_size,
                          size_t max_size, size_t read_only_size,
                          size_t read_write_size, size_t misc_data_size,
                          size_t misc_code_size);

  PermGen* init(ReservedSpace rs, size_t init_size, GenRemSet* remset);

  void disable_sharing() {
    _enable_shared_spaces = false;
    _read_only_size = 0;
    _read_write_size = 0;
    _misc_data_size = 0;
    _misc_code_size = 0;
  }

  // Accessors
  PermGen::Name name()           const { return _name; }
  size_t init_size()             const { return _init_size; }
  void set_init_size(size_t size)      { _init_size = size; }

  // Max size for user DOES NOT include shared spaces.
  // Max size for space allocation DOES include shared spaces.
  size_t max_size() const {
    return _max_size + _read_only_size + _read_write_size;
  }

  // Need one covered region for the main space, and one for the shared
  // spaces (together).
  int n_covered_regions() const { return 2; }

  void align(size_t alignment);

  size_t read_only_size() const { return _read_only_size; }
  size_t read_write_size() const { return _read_write_size; }
  size_t misc_data_size() const { return _misc_data_size; }
  size_t misc_code_size() const { return _misc_code_size; }
  bool enable_shared_spaces()    const { return _enable_shared_spaces; }
};
