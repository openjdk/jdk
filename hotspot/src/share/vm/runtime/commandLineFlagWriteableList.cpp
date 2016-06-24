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

#include "precompiled.hpp"
#include "runtime/commandLineFlagWriteableList.hpp"
#include "runtime/os.hpp"
#if INCLUDE_ALL_GCS
#include "gc/cms/concurrentMarkSweepGeneration.inline.hpp"
#include "gc/g1/g1_globals.hpp"
#include "gc/g1/heapRegionBounds.inline.hpp"
#include "gc/shared/plab.hpp"
#endif // INCLUDE_ALL_GCS
#ifdef COMPILER1
#include "c1/c1_globals.hpp"
#endif // COMPILER1
#ifdef COMPILER2
#include "opto/c2_globals.hpp"
#endif // COMPILER2
#if INCLUDE_JVMCI
#include "jvmci/jvmci_globals.hpp"
#endif

bool CommandLineFlagWriteable::is_writeable(void) {
  return _writeable;
}

void CommandLineFlagWriteable::mark_once(void) {
  if (_type == Once) {
    _writeable = false;
  }
}

void CommandLineFlagWriteable::mark_startup(void) {
  if (_type == CommandLineFlagWriteable::CommandLineOnly) {
    _writeable = false;
  }
}

// No control emitting
void emit_writeable_no(...)                         { /* NOP */ }

// No control emitting if type argument is NOT provided
void emit_writeable_bool(const char* /*name*/)      { /* NOP */ }
void emit_writeable_ccstr(const char* /*name*/)     { /* NOP */ }
void emit_writeable_ccstrlist(const char* /*name*/) { /* NOP */ }
void emit_writeable_int(const char* /*name*/)       { /* NOP */ }
void emit_writeable_intx(const char* /*name*/)      { /* NOP */ }
void emit_writeable_uint(const char* /*name*/)      { /* NOP */ }
void emit_writeable_uintx(const char* /*name*/)     { /* NOP */ }
void emit_writeable_uint64_t(const char* /*name*/)  { /* NOP */ }
void emit_writeable_size_t(const char* /*name*/)    { /* NOP */ }
void emit_writeable_double(const char* /*name*/)    { /* NOP */ }

// CommandLineFlagWriteable emitting code functions if range arguments are provided
void emit_writeable_bool(const char* name, CommandLineFlagWriteable::WriteableType type) {
  CommandLineFlagWriteableList::add(new CommandLineFlagWriteable(name, type));
}
void emit_writeable_int(const char* name, CommandLineFlagWriteable::WriteableType type) {
  CommandLineFlagWriteableList::add(new CommandLineFlagWriteable(name, type));
}
void emit_writeable_intx(const char* name, CommandLineFlagWriteable::WriteableType type) {
  CommandLineFlagWriteableList::add(new CommandLineFlagWriteable(name, type));
}
void emit_writeable_uint(const char* name, CommandLineFlagWriteable::WriteableType type) {
  CommandLineFlagWriteableList::add(new CommandLineFlagWriteable(name, type));
}
void emit_writeable_uintx(const char* name, CommandLineFlagWriteable::WriteableType type) {
  CommandLineFlagWriteableList::add(new CommandLineFlagWriteable(name, type));
}
void emit_writeable_uint64_t(const char* name, CommandLineFlagWriteable::WriteableType type) {
  CommandLineFlagWriteableList::add(new CommandLineFlagWriteable(name, type));
}
void emit_writeable_size_t(const char* name, CommandLineFlagWriteable::WriteableType type) {
  CommandLineFlagWriteableList::add(new CommandLineFlagWriteable(name, type));
}
void emit_writeable_double(const char* name, CommandLineFlagWriteable::WriteableType type) {
  CommandLineFlagWriteableList::add(new CommandLineFlagWriteable(name, type));
}

// Generate code to call emit_writeable_xxx function
#define EMIT_WRITEABLE_PRODUCT_FLAG(type, name, value, doc)      ); emit_writeable_##type(#name
#define EMIT_WRITEABLE_COMMERCIAL_FLAG(type, name, value, doc)   ); emit_writeable_##type(#name
#define EMIT_WRITEABLE_DIAGNOSTIC_FLAG(type, name, value, doc)   ); emit_writeable_##type(#name
#define EMIT_WRITEABLE_EXPERIMENTAL_FLAG(type, name, value, doc) ); emit_writeable_##type(#name
#define EMIT_WRITEABLE_MANAGEABLE_FLAG(type, name, value, doc)   ); emit_writeable_##type(#name
#define EMIT_WRITEABLE_PRODUCT_RW_FLAG(type, name, value, doc)   ); emit_writeable_##type(#name
#define EMIT_WRITEABLE_PD_PRODUCT_FLAG(type, name, doc)          ); emit_writeable_##type(#name
#define EMIT_WRITEABLE_DEVELOPER_FLAG(type, name, value, doc)    ); emit_writeable_##type(#name
#define EMIT_WRITEABLE_PD_DEVELOPER_FLAG(type, name, doc)        ); emit_writeable_##type(#name
#define EMIT_WRITEABLE_PD_DIAGNOSTIC_FLAG(type, name, doc)       ); emit_writeable_##type(#name
#define EMIT_WRITEABLE_NOTPRODUCT_FLAG(type, name, value, doc)   ); emit_writeable_##type(#name
#define EMIT_WRITEABLE_LP64_PRODUCT_FLAG(type, name, value, doc) ); emit_writeable_##type(#name

// Generate type argument to pass into emit_writeable_xxx functions
#define EMIT_WRITEABLE(a)                                      , CommandLineFlagWriteable::a

#define INITIAL_WRITEABLES_SIZE 2
GrowableArray<CommandLineFlagWriteable*>* CommandLineFlagWriteableList::_controls = NULL;

void CommandLineFlagWriteableList::init(void) {

  _controls = new (ResourceObj::C_HEAP, mtArguments) GrowableArray<CommandLineFlagWriteable*>(INITIAL_WRITEABLES_SIZE, true);

  emit_writeable_no(NULL RUNTIME_FLAGS(EMIT_WRITEABLE_DEVELOPER_FLAG,
                                   EMIT_WRITEABLE_PD_DEVELOPER_FLAG,
                                   EMIT_WRITEABLE_PRODUCT_FLAG,
                                   EMIT_WRITEABLE_PD_PRODUCT_FLAG,
                                   EMIT_WRITEABLE_DIAGNOSTIC_FLAG,
                                   EMIT_WRITEABLE_PD_DIAGNOSTIC_FLAG,
                                   EMIT_WRITEABLE_EXPERIMENTAL_FLAG,
                                   EMIT_WRITEABLE_NOTPRODUCT_FLAG,
                                   EMIT_WRITEABLE_MANAGEABLE_FLAG,
                                   EMIT_WRITEABLE_PRODUCT_RW_FLAG,
                                   EMIT_WRITEABLE_LP64_PRODUCT_FLAG,
                                   IGNORE_RANGE,
                                   IGNORE_CONSTRAINT,
                                   EMIT_WRITEABLE));

  EMIT_WRITEABLES_FOR_GLOBALS_EXT

  emit_writeable_no(NULL ARCH_FLAGS(EMIT_WRITEABLE_DEVELOPER_FLAG,
                                EMIT_WRITEABLE_PRODUCT_FLAG,
                                EMIT_WRITEABLE_DIAGNOSTIC_FLAG,
                                EMIT_WRITEABLE_EXPERIMENTAL_FLAG,
                                EMIT_WRITEABLE_NOTPRODUCT_FLAG,
                                IGNORE_RANGE,
                                IGNORE_CONSTRAINT,
                                EMIT_WRITEABLE));

#if INCLUDE_JVMCI
  emit_writeable_no(NULL JVMCI_FLAGS(EMIT_WRITEABLE_DEVELOPER_FLAG,
                                 EMIT_WRITEABLE_PD_DEVELOPER_FLAG,
                                 EMIT_WRITEABLE_PRODUCT_FLAG,
                                 EMIT_WRITEABLE_PD_PRODUCT_FLAG,
                                 EMIT_WRITEABLE_DIAGNOSTIC_FLAG,
                                 EMIT_WRITEABLE_PD_DIAGNOSTIC_FLAG,
                                 EMIT_WRITEABLE_EXPERIMENTAL_FLAG,
                                 EMIT_WRITEABLE_NOTPRODUCT_FLAG,
                                 IGNORE_RANGE,
                                 IGNORE_CONSTRAINT,
                                 EMIT_WRITEABLE));
#endif // INCLUDE_JVMCI

#ifdef COMPILER1
  emit_writeable_no(NULL C1_FLAGS(EMIT_WRITEABLE_DEVELOPER_FLAG,
                              EMIT_WRITEABLE_PD_DEVELOPER_FLAG,
                              EMIT_WRITEABLE_PRODUCT_FLAG,
                              EMIT_WRITEABLE_PD_PRODUCT_FLAG,
                              EMIT_WRITEABLE_DIAGNOSTIC_FLAG,
                              EMIT_WRITEABLE_PD_DIAGNOSTIC_FLAG,
                              EMIT_WRITEABLE_NOTPRODUCT_FLAG,
                              IGNORE_RANGE,
                              IGNORE_CONSTRAINT,
                              EMIT_WRITEABLE));
#endif // COMPILER1

#ifdef COMPILER2
  emit_writeable_no(NULL C2_FLAGS(EMIT_WRITEABLE_DEVELOPER_FLAG,
                              EMIT_WRITEABLE_PD_DEVELOPER_FLAG,
                              EMIT_WRITEABLE_PRODUCT_FLAG,
                              EMIT_WRITEABLE_PD_PRODUCT_FLAG,
                              EMIT_WRITEABLE_DIAGNOSTIC_FLAG,
                              EMIT_WRITEABLE_PD_DIAGNOSTIC_FLAG,
                              EMIT_WRITEABLE_EXPERIMENTAL_FLAG,
                              EMIT_WRITEABLE_NOTPRODUCT_FLAG,
                              IGNORE_RANGE,
                              IGNORE_CONSTRAINT,
                              EMIT_WRITEABLE));
#endif // COMPILER2

#if INCLUDE_ALL_GCS
  emit_writeable_no(NULL G1_FLAGS(EMIT_WRITEABLE_DEVELOPER_FLAG,
                              EMIT_WRITEABLE_PD_DEVELOPER_FLAG,
                              EMIT_WRITEABLE_PRODUCT_FLAG,
                              EMIT_WRITEABLE_PD_PRODUCT_FLAG,
                              EMIT_WRITEABLE_DIAGNOSTIC_FLAG,
                              EMIT_WRITEABLE_PD_DIAGNOSTIC_FLAG,
                              EMIT_WRITEABLE_EXPERIMENTAL_FLAG,
                              EMIT_WRITEABLE_NOTPRODUCT_FLAG,
                              EMIT_WRITEABLE_MANAGEABLE_FLAG,
                              EMIT_WRITEABLE_PRODUCT_RW_FLAG,
                              IGNORE_RANGE,
                              IGNORE_CONSTRAINT,
                              EMIT_WRITEABLE));
#endif // INCLUDE_ALL_GCS
}

CommandLineFlagWriteable* CommandLineFlagWriteableList::find(const char* name) {
  CommandLineFlagWriteable* found = NULL;
  for (int i=0; i<length(); i++) {
    CommandLineFlagWriteable* writeable = at(i);
    if (strcmp(writeable->name(), name) == 0) {
      found = writeable;
      break;
    }
  }
  return found;
}

void CommandLineFlagWriteableList::mark_startup(void) {
  for (int i=0; i<length(); i++) {
    CommandLineFlagWriteable* writeable = at(i);
    writeable->mark_startup();
  }
}
