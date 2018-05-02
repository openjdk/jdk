/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_JVMFLAGWRITEABLE_HPP
#define SHARE_VM_RUNTIME_JVMFLAGWRITEABLE_HPP

#include "utilities/growableArray.hpp"

class JVMFlagWriteable : public CHeapObj<mtArguments> {
public:
  enum WriteableType {
    // can be set without any limits
    Always           = 0,
    // can only be set once, either via command lines or during runtime
    Once             = 1,
    // can only be set on command line (multiple times allowed)
    CommandLineOnly  = 2
  };
private:
  const char* _name;
  WriteableType _type;
  bool _writeable;
  bool _startup_done;
public:
  // the "name" argument must be a string literal
  JVMFlagWriteable(const char* name, WriteableType type) { _name=name; _type=type; _writeable=true; _startup_done=false; }
  ~JVMFlagWriteable() {}
  const char* name() { return _name; }
  const WriteableType type() { return _type; }
  bool is_writeable(void);
  void mark_once(void);
  void mark_startup(void);
};

class JVMFlagWriteableList : public AllStatic {
  static GrowableArray<JVMFlagWriteable*>* _controls;
public:
  static void init();
  static int length() { return (_controls != NULL) ? _controls->length() : 0; }
  static JVMFlagWriteable* at(int i) { return (_controls != NULL) ? _controls->at(i) : NULL; }
  static JVMFlagWriteable* find(const char* name);
  static void add(JVMFlagWriteable* range) { _controls->append(range); }
  static void mark_startup(void);
};

#endif // SHARE_VM_RUNTIME_JVMFLAGWRITEABLE_HPP
