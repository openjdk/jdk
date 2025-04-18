/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_OOPS_OBJLAYOUT_HPP
#define SHARE_OOPS_OBJLAYOUT_HPP

class HeaderMode {
public:
  enum Mode {
    // +UseCompactObjectHeaders (implies +UseCompressedClassPointers)
    Compact = 0,
    // +UseCompressedClassPointers (-UseCompactObjectHeaders)
    Compressed,
    // -UseCompressedClassPointers (-UseCompactObjectHeaders)
    Uncompressed
  };
private:
  const Mode _mode;
public:
  HeaderMode(Mode mode) : _mode(mode) {}

  inline bool has_klass_gap() const;

  // Size of markword, or markword+klassword; offset of length for arrays
  inline int base_offset_in_bytes() const;

  // Size of markword, or markword+klassword; offset of length for arrays
  template<typename T>
  inline int array_first_element_offset_in_bytes() const;
};

/*
 * This class helps to avoid loading more than one flag in some
 * operations that require checking UseCompressedClassPointers,
 * UseCompactObjectHeaders and possibly more.
 *
 * This is important on some performance critical paths, e.g. where
 * the Klass* is accessed frequently, especially by GC oop iterators
 * and stack-trace builders.
 */
class ObjLayout : public AllStatic {

  static HeaderMode::Mode _mode;
  static int  _oop_base_offset_in_bytes;
  static bool _oop_has_klass_gap;

  static bool is_initialized() {
    return _oop_base_offset_in_bytes > 0;
  }

public:
  static void initialize();
  static inline HeaderMode::Mode klass_mode() {
    return _mode;
  }
  static inline int oop_base_offset_in_bytes() {
    return _oop_base_offset_in_bytes;
  }
  static inline bool oop_has_klass_gap() {
    return _oop_has_klass_gap;
  }
};

#endif // SHARE_OOPS_OBJLAYOUT_HPP
