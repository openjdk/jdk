/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_JAVASTACKTRACECLASSES_HPP
#define SHARE_CLASSFILE_JAVASTACKTRACECLASSES_HPP

#include "classfile/vmClasses.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oop.hpp"
#include "oops/oopsHierarchy.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.hpp"
#include "utilities/macros.hpp"
#include "utilities/vmEnums.hpp"

#define CHECK_INIT(offset)  assert(offset != 0, "should be initialized"); return offset;

class SerializeClosure;

// Interface to java.lang.Throwable objects

class java_lang_Throwable: AllStatic {
  friend class BacktraceBuilder;
  friend class BacktraceIterator;

 private:
  // Trace constants
  enum {
    trace_methods_offset = 0,
    trace_bcis_offset    = 1,
    trace_mirrors_offset = 2,
    trace_names_offset   = 3,
    trace_conts_offset   = 4,
    trace_next_offset    = 5,
    trace_hidden_offset  = 6,
    trace_size           = 7,
    trace_chunk_size     = 32
  };

  static int _backtrace_offset;
  static int _detailMessage_offset;
  static int _stackTrace_offset;
  static int _depth_offset;
  static int _cause_offset;
  static int _static_unassigned_stacktrace_offset;

  // StackTrace (programmatic access, new since 1.4)
  static void clear_stacktrace(oop throwable);
  // Stacktrace (post JDK 1.7.0 to allow immutability protocol to be followed)
  static void set_stacktrace(oop throwable, oop st_element_array);
  static oop unassigned_stacktrace();

 public:
  // Backtrace
  static oop backtrace(oop throwable);
  static void set_backtrace(oop throwable, oop value);
  static int depth(oop throwable);
  static void set_depth(oop throwable, int value);
  // Message
  static int get_detailMessage_offset() { CHECK_INIT(_detailMessage_offset); }
  static oop message(oop throwable);
  static const char* message_as_utf8(oop throwable);
  static void set_message(oop throwable, oop value);

  static oop cause(oop throwable);

  static void print_stack_element(outputStream *st, Method* method, int bci);

  static void compute_offsets();
  static void serialize_offsets(SerializeClosure* f) NOT_CDS_RETURN;

  // Allocate space for backtrace (created but stack trace not filled in)
  static void allocate_backtrace(Handle throwable, TRAPS);
  // Fill in current stack trace for throwable with preallocated backtrace (no GC)
  static void fill_in_stack_trace_of_preallocated_backtrace(Handle throwable);
  // Fill in current stack trace, can cause GC
  static void fill_in_stack_trace(Handle throwable, const methodHandle& method, TRAPS);
  static void fill_in_stack_trace(Handle throwable, const methodHandle& method = methodHandle());

  // Programmatic access to stack trace
  static void get_stack_trace_elements(int depth, Handle backtrace, objArrayHandle stack_trace, TRAPS);

  // For recreating class initialization error exceptions.
  static Handle create_initialization_error(JavaThread* current, Handle throwable);

  // Printing
  static void print(oop throwable, outputStream* st);
  static void print_stack_trace(Handle throwable, outputStream* st);
  static void java_printStackTrace(Handle throwable, TRAPS);
  // Debugging
  friend class JavaClasses;
  // Gets the method and bci of the top frame (TOS). Returns false if this failed.
  static bool get_top_method_and_bci(oop throwable, Method** method, int* bci);
};

// Interface to java.lang.StackTraceElement objects

class java_lang_StackTraceElement: AllStatic {
 private:
  static int _declaringClassObject_offset;
  static int _classLoaderName_offset;
  static int _moduleName_offset;
  static int _moduleVersion_offset;
  static int _declaringClass_offset;
  static int _methodName_offset;
  static int _fileName_offset;
  static int _lineNumber_offset;

  // Setters
  static void set_classLoaderName(oop element, oop value);
  static void set_moduleName(oop element, oop value);
  static void set_moduleVersion(oop element, oop value);
  static void set_declaringClass(oop element, oop value);
  static void set_methodName(oop element, oop value);
  static void set_fileName(oop element, oop value);
  static void set_lineNumber(oop element, int value);
  static void set_declaringClassObject(oop element, oop value);

  static void decode_file_and_line(Handle java_mirror, InstanceKlass* holder, int version,
                                   const methodHandle& method, int bci,
                                   Symbol*& source, oop& source_file, int& line_number, TRAPS);

 public:
  // Create an instance of StackTraceElement
  static oop create(const methodHandle& method, int bci, TRAPS);

  static void fill_in(Handle element, InstanceKlass* holder, const methodHandle& method,
                      int version, int bci, Symbol* name, TRAPS);

  static void compute_offsets();
  static void serialize_offsets(SerializeClosure* f) NOT_CDS_RETURN;

#if INCLUDE_JVMCI
  static void decode(const methodHandle& method, int bci, Symbol*& fileName, int& lineNumber, TRAPS);
#endif

  // Debugging
  friend class JavaClasses;
};

class java_lang_ClassFrameInfo: AllStatic {
private:
  static int _classOrMemberName_offset;
  static int _flags_offset;

public:
  static oop  classOrMemberName(oop info);
  static int  flags(oop info);

  // Setters
  static void init_class(Handle stackFrame, const methodHandle& m);
  static void init_method(Handle stackFrame, const methodHandle& m, TRAPS);

  static void compute_offsets();
  static void serialize_offsets(SerializeClosure* f) NOT_CDS_RETURN;

  // Debugging
  friend class JavaClasses;
};

// Interface to java.lang.StackFrameInfo objects

#define STACKFRAMEINFO_INJECTED_FIELDS(macro)                      \
  macro(java_lang_StackFrameInfo, version, short_signature, false)

class java_lang_StackFrameInfo: AllStatic {
private:
  static int _type_offset;
  static int _name_offset;
  static int _bci_offset;
  static int _version_offset;
  static int _contScope_offset;

public:
  // Getters
  static oop name(oop info);
  static oop type(oop info);
  static Method* get_method(oop info);

  // Setters
  static void set_method_and_bci(Handle stackFrame, const methodHandle& method, int bci, oop cont, TRAPS);
  static void set_name(oop info, oop value);
  static void set_type(oop info, oop value);
  static void set_bci(oop info, int value);

  static void set_version(oop info, short value);
  static void set_contScope(oop info, oop value);

  static void compute_offsets();
  static void serialize_offsets(SerializeClosure* f) NOT_CDS_RETURN;

  static void to_stack_trace_element(Handle stackFrame, Handle stack_trace_element, TRAPS);

  // Debugging
  friend class JavaClasses;
};

class java_lang_LiveStackFrameInfo: AllStatic {
 private:
  static int _monitors_offset;
  static int _locals_offset;
  static int _operands_offset;
  static int _mode_offset;

 public:
  static void set_monitors(oop info, oop value);
  static void set_locals(oop info, oop value);
  static void set_operands(oop info, oop value);
  static void set_mode(oop info, int value);

  static void compute_offsets();
  static void serialize_offsets(SerializeClosure* f) NOT_CDS_RETURN;

  // Debugging
  friend class JavaClasses;
};

#undef CHECK_INIT

#endif // SHARE_CLASSFILE_JAVASTACKTRACECLASSES_HPP
