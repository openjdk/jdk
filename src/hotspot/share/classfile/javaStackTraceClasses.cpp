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

#include "cds/aotMetaspace.hpp"
#include "cds/aotReferenceObjSupport.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/javaClassesImpl.hpp"
#include "classfile/javaStackTraceClasses.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/debugInfo.hpp"
#include "code/dependencyContext.hpp"
#include "code/pcDesc.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopCast.inline.hpp"
#include "oops/symbol.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/vframe.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/preserveException.hpp"
#include "utilities/utf8.hpp"

// Inline helper functions

static inline int merge_bci_and_version(int bci, int version) {
  // only store u2 for version, checking for overflow.
  if (version > USHRT_MAX || version < 0) version = USHRT_MAX;
  assert((u2)bci == bci, "bci should be short");
  return build_int_from_shorts((u2)version, (u2)bci);
}

static inline int bci_at(unsigned int merged) {
  return extract_high_short_from_int(merged);
}

static inline int version_at(unsigned int merged) {
  return extract_low_short_from_int(merged);
}

static inline int get_line_number(Method* method, int bci) {
  int line_number = 0;
  if (method->is_native()) {
    // Negative value different from -1 below, enabling Java code in
    // class java.lang.StackTraceElement to distinguish "native" from
    // "no LineNumberTable".  JDK tests for -2.
    line_number = -2;
  } else {
    // Returns -1 if no LineNumberTable, and otherwise actual line number
    line_number = method->line_number_from_bci(bci);
  }
  return line_number;
}

static inline Symbol* get_source_file_name(InstanceKlass* holder, int version) {
  // RedefineClasses() currently permits redefine operations to
  // happen in parallel using a "last one wins" philosophy. That
  // spec laxness allows the constant pool entry associated with
  // the source_file_name_index for any older constant pool version
  // to be unstable so we shouldn't try to use it.
  if (holder->constants()->version() != version) {
    return nullptr;
  } else {
    return holder->source_file_name();
  }
}


// java_lang_Throwable

int java_lang_Throwable::_backtrace_offset;
int java_lang_Throwable::_detailMessage_offset;
int java_lang_Throwable::_stackTrace_offset;
int java_lang_Throwable::_depth_offset;
int java_lang_Throwable::_cause_offset;
int java_lang_Throwable::_static_unassigned_stacktrace_offset;

#define THROWABLE_FIELDS_DO(macro) \
  macro(_backtrace_offset,     k, "backtrace",     object_signature,                  false); \
  macro(_detailMessage_offset, k, "detailMessage", string_signature,                  false); \
  macro(_stackTrace_offset,    k, "stackTrace",    java_lang_StackTraceElement_array, false); \
  macro(_depth_offset,         k, "depth",         int_signature,                     false); \
  macro(_cause_offset,         k, "cause",         throwable_signature,               false); \
  macro(_static_unassigned_stacktrace_offset, k, "UNASSIGNED_STACK", java_lang_StackTraceElement_array, true)

void java_lang_Throwable::compute_offsets() {
  InstanceKlass* k = vmClasses::Throwable_klass();
  THROWABLE_FIELDS_DO(FIELD_COMPUTE_OFFSET);
}

#if INCLUDE_CDS
void java_lang_Throwable::serialize_offsets(SerializeClosure* f) {
  THROWABLE_FIELDS_DO(FIELD_SERIALIZE_OFFSET);
}
#endif

oop java_lang_Throwable::unassigned_stacktrace() {
  InstanceKlass* ik = vmClasses::Throwable_klass();
  oop base = ik->static_field_base_raw();
  return base->obj_field(_static_unassigned_stacktrace_offset);
}

oop java_lang_Throwable::backtrace(oop throwable) {
  return throwable->obj_field_acquire(_backtrace_offset);
}


void java_lang_Throwable::set_backtrace(oop throwable, oop value) {
  throwable->release_obj_field_put(_backtrace_offset, value);
}

int java_lang_Throwable::depth(oop throwable) {
  return throwable->int_field(_depth_offset);
}

void java_lang_Throwable::set_depth(oop throwable, int value) {
  throwable->int_field_put(_depth_offset, value);
}

oop java_lang_Throwable::message(oop throwable) {
  return throwable->obj_field(_detailMessage_offset);
}

const char* java_lang_Throwable::message_as_utf8(oop throwable) {
  oop msg = java_lang_Throwable::message(throwable);
  const char* msg_utf8 = nullptr;
  if (msg != nullptr) {
    msg_utf8 = java_lang_String::as_utf8_string(msg);
  }
  return msg_utf8;
}

oop java_lang_Throwable::cause(oop throwable) {
  return throwable->obj_field(_cause_offset);
}

void java_lang_Throwable::set_message(oop throwable, oop value) {
  throwable->obj_field_put(_detailMessage_offset, value);
}


void java_lang_Throwable::set_stacktrace(oop throwable, oop st_element_array) {
  throwable->obj_field_put(_stackTrace_offset, st_element_array);
}

void java_lang_Throwable::clear_stacktrace(oop throwable) {
  set_stacktrace(throwable, nullptr);
}


void java_lang_Throwable::print(oop throwable, outputStream* st) {
  ResourceMark rm;
  Klass* k = throwable->klass();
  assert(k != nullptr, "just checking");
  st->print("%s", k->external_name());
  oop msg = message(throwable);
  if (msg != nullptr) {
    st->print(": %s", java_lang_String::as_utf8_string(msg));
  }
}


static inline bool version_matches(Method* method, int version) {
  // After this many redefines, the stack trace is unreliable.
  assert(version < USHRT_MAX, "version is too big");
  return method != nullptr && (method->constants()->version() == version);
}

// This class provides a simple wrapper over the internal structure of
// exception backtrace to insulate users of the backtrace from needing
// to know what it looks like.
// The code of this class is not GC safe. Allocations can only happen
// in expand().
class BacktraceBuilder: public StackObj {
 friend class BacktraceIterator;
 private:
  Handle          _backtrace;
  objArrayOop     _head;
  typeArrayOop    _methods;
  typeArrayOop    _bcis;
  objArrayOop     _mirrors;
  typeArrayOop    _names; // Needed to insulate method name against redefinition.
  // True if the top frame of the backtrace is omitted because it shall be hidden.
  bool            _has_hidden_top_frame;
  int             _index;
  NoSafepointVerifier _nsv;

  enum {
    trace_methods_offset = java_lang_Throwable::trace_methods_offset,
    trace_bcis_offset    = java_lang_Throwable::trace_bcis_offset,
    trace_mirrors_offset = java_lang_Throwable::trace_mirrors_offset,
    trace_names_offset   = java_lang_Throwable::trace_names_offset,
    trace_conts_offset   = java_lang_Throwable::trace_conts_offset,
    trace_next_offset    = java_lang_Throwable::trace_next_offset,
    trace_hidden_offset  = java_lang_Throwable::trace_hidden_offset,
    trace_size           = java_lang_Throwable::trace_size,
    trace_chunk_size     = java_lang_Throwable::trace_chunk_size
  };

  // get info out of chunks
  static typeArrayOop get_methods(objArrayHandle chunk) {
    typeArrayOop methods = typeArrayOop(chunk->obj_at(trace_methods_offset));
    assert(methods != nullptr, "method array should be initialized in backtrace");
    return methods;
  }
  static typeArrayOop get_bcis(objArrayHandle chunk) {
    typeArrayOop bcis = typeArrayOop(chunk->obj_at(trace_bcis_offset));
    assert(bcis != nullptr, "bci array should be initialized in backtrace");
    return bcis;
  }
  static objArrayOop get_mirrors(objArrayHandle chunk) {
    objArrayOop mirrors = objArrayOop(chunk->obj_at(trace_mirrors_offset));
    assert(mirrors != nullptr, "mirror array should be initialized in backtrace");
    return mirrors;
  }
  static typeArrayOop get_names(objArrayHandle chunk) {
    typeArrayOop names = typeArrayOop(chunk->obj_at(trace_names_offset));
    assert(names != nullptr, "names array should be initialized in backtrace");
    return names;
  }
  static bool has_hidden_top_frame(objArrayHandle chunk) {
    oop hidden = chunk->obj_at(trace_hidden_offset);
    return hidden != nullptr;
  }

 public:

  // constructor for new backtrace
  BacktraceBuilder(TRAPS): _head(nullptr), _methods(nullptr), _bcis(nullptr), _mirrors(nullptr), _names(nullptr), _has_hidden_top_frame(false) {
    expand(CHECK);
    _backtrace = Handle(THREAD, _head);
    _index = 0;
  }

  BacktraceBuilder(Thread* thread, objArrayHandle backtrace) {
    _methods = get_methods(backtrace);
    _bcis = get_bcis(backtrace);
    _mirrors = get_mirrors(backtrace);
    _names = get_names(backtrace);
    _has_hidden_top_frame = has_hidden_top_frame(backtrace);
    assert(_methods->length() == _bcis->length() &&
           _methods->length() == _mirrors->length() &&
           _mirrors->length() == _names->length(),
           "method and source information arrays should match");

    // head is the preallocated backtrace
    _head = backtrace();
    _backtrace = Handle(thread, _head);
    _index = 0;
  }

  void expand(TRAPS) {
    objArrayHandle old_head(THREAD, _head);
    PauseNoSafepointVerifier pnsv(&_nsv);

    objArrayOop head = oopFactory::new_objectArray(trace_size, CHECK);
    objArrayHandle new_head(THREAD, head);

    typeArrayOop methods = oopFactory::new_shortArray(trace_chunk_size, CHECK);
    typeArrayHandle new_methods(THREAD, methods);

    typeArrayOop bcis = oopFactory::new_intArray(trace_chunk_size, CHECK);
    typeArrayHandle new_bcis(THREAD, bcis);

    objArrayOop mirrors = oopFactory::new_objectArray(trace_chunk_size, CHECK);
    objArrayHandle new_mirrors(THREAD, mirrors);

    typeArrayOop names = oopFactory::new_symbolArray(trace_chunk_size, CHECK);
    typeArrayHandle new_names(THREAD, names);

    if (!old_head.is_null()) {
      old_head->obj_at_put(trace_next_offset, new_head());
    }
    new_head->obj_at_put(trace_methods_offset, new_methods());
    new_head->obj_at_put(trace_bcis_offset, new_bcis());
    new_head->obj_at_put(trace_mirrors_offset, new_mirrors());
    new_head->obj_at_put(trace_names_offset, new_names());
    new_head->obj_at_put(trace_hidden_offset, nullptr);

    _head    = new_head();
    _methods = new_methods();
    _bcis = new_bcis();
    _mirrors = new_mirrors();
    _names  = new_names();
    _index = 0;
  }

  oop backtrace() {
    return _backtrace();
  }

  inline void push(Method* method, int bci, TRAPS) {
    // Smear the -1 bci to 0 since the array only holds unsigned
    // shorts.  The later line number lookup would just smear the -1
    // to a 0 even if it could be recorded.
    if (bci == SynchronizationEntryBCI) bci = 0;

    if (_index >= trace_chunk_size) {
      methodHandle mhandle(THREAD, method);
      expand(CHECK);
      method = mhandle();
    }

    _methods->ushort_at_put(_index, method->orig_method_idnum());
    _bcis->int_at_put(_index, merge_bci_and_version(bci, method->constants()->version()));

    // Note:this doesn't leak symbols because the mirror in the backtrace keeps the
    // klass owning the symbols alive so their refcounts aren't decremented.
    Symbol* name = method->name();
    _names->symbol_at_put(_index, name);

    // We need to save the mirrors in the backtrace to keep the class
    // from being unloaded while we still have this stack trace.
    assert(method->method_holder()->java_mirror() != nullptr, "never push null for mirror");
    _mirrors->obj_at_put(_index, method->method_holder()->java_mirror());

    _index++;
  }

  void set_has_hidden_top_frame() {
    if (!_has_hidden_top_frame) {
      // It would be nice to add java/lang/Boolean::TRUE here
      // to indicate that this backtrace has a hidden top frame.
      // But this code is used before TRUE is allocated.
      // Therefore let's just use an arbitrary legal oop
      // available right here. _methods is a short[].
      assert(_methods != nullptr, "we need a legal oop");
      _has_hidden_top_frame = true;
      _head->obj_at_put(trace_hidden_offset, _methods);
    }
  }
};

struct BacktraceElement : public StackObj {
  int _method_id;
  int _bci;
  int _version;
  Symbol* _name;
  Handle _mirror;
  BacktraceElement(Handle mirror, int mid, int version, int bci, Symbol* name) :
                   _method_id(mid), _bci(bci), _version(version), _name(name), _mirror(mirror) {}
};

class BacktraceIterator : public StackObj {
  int _index;
  objArrayHandle  _result;
  objArrayHandle  _mirrors;
  typeArrayHandle _methods;
  typeArrayHandle _bcis;
  typeArrayHandle _names;

  void init(objArrayHandle result, Thread* thread) {
    // Get method id, bci, version and mirror from chunk
    _result = result;
    if (_result.not_null()) {
      _methods = typeArrayHandle(thread, BacktraceBuilder::get_methods(_result));
      _bcis = typeArrayHandle(thread, BacktraceBuilder::get_bcis(_result));
      _mirrors = objArrayHandle(thread, BacktraceBuilder::get_mirrors(_result));
      _names = typeArrayHandle(thread, BacktraceBuilder::get_names(_result));
      _index = 0;
    }
  }
 public:
  BacktraceIterator(objArrayHandle result, Thread* thread) {
    init(result, thread);
    assert(_methods.is_null() || _methods->length() == java_lang_Throwable::trace_chunk_size, "lengths don't match");
  }

  BacktraceElement next(Thread* thread) {
    BacktraceElement e (Handle(thread, _mirrors->obj_at(_index)),
                        _methods->ushort_at(_index),
                        version_at(_bcis->int_at(_index)),
                        bci_at(_bcis->int_at(_index)),
                        _names->symbol_at(_index));
    _index++;

    if (_index >= java_lang_Throwable::trace_chunk_size) {
      int next_offset = java_lang_Throwable::trace_next_offset;
      // Get next chunk
      objArrayHandle result (thread, objArrayOop(_result->obj_at(next_offset)));
      init(result, thread);
    }
    return e;
  }

  bool repeat() {
    return _result.not_null() && _mirrors->obj_at(_index) != nullptr;
  }
};


// Print stack trace element to the specified output stream.
// The output is formatted into a stringStream and written to the outputStream in one step.
static void print_stack_element_to_stream(outputStream* st, Handle mirror, int method_id,
                                          int version, int bci, Symbol* name) {
  ResourceMark rm;
  stringStream ss;

  InstanceKlass* holder = java_lang_Class::as_InstanceKlass(mirror());
  const char* klass_name  = holder->external_name();
  char* method_name = name->as_C_string();
  ss.print("\tat %s.%s(", klass_name, method_name);

  // Print module information
  ModuleEntry* module = holder->module();
  if (module->is_named()) {
    char* module_name = module->name()->as_C_string();
    if (module->version() != nullptr) {
      char* module_version = module->version()->as_C_string();
      ss.print("%s@%s/", module_name, module_version);
    } else {
      ss.print("%s/", module_name);
    }
  }

  char* source_file_name = nullptr;
  Symbol* source = get_source_file_name(holder, version);
  if (source != nullptr) {
    source_file_name = source->as_C_string();
  }

  // The method can be null if the requested class version is gone
  Method* method = holder->method_with_orig_idnum(method_id, version);
  if (!version_matches(method, version)) {
    ss.print("Redefined)");
  } else {
    int line_number = get_line_number(method, bci);
    if (line_number == -2) {
      ss.print("Native Method)");
    } else {
      if (source_file_name != nullptr && (line_number != -1)) {
        // Sourcename and linenumber
        ss.print("%s:%d)", source_file_name, line_number);
      } else if (source_file_name != nullptr) {
        // Just sourcename
        ss.print("%s)", source_file_name);
      } else {
        // Neither sourcename nor linenumber
        ss.print("Unknown Source)");
      }
      nmethod* nm = method->code();
      if (WizardMode && nm != nullptr) {
        ss.print("(nmethod " INTPTR_FORMAT ")", p2i(nm));
      }
    }
  }

  ss.cr();
  st->print_raw(ss.freeze(), ss.size());
}

void java_lang_Throwable::print_stack_element(outputStream *st, Method* method, int bci) {
  Handle mirror (Thread::current(),  method->method_holder()->java_mirror());
  int method_id = method->orig_method_idnum();
  int version = method->constants()->version();
  print_stack_element_to_stream(st, mirror, method_id, version, bci, method->name());
}

/**
 * Print the throwable message and its stack trace plus all causes by walking the
 * cause chain.  The output looks the same as of Throwable.printStackTrace().
 */
void java_lang_Throwable::print_stack_trace(Handle throwable, outputStream* st) {
  // First, print the message.
  print(throwable(), st);
  st->cr();

  // Now print the stack trace.
  JavaThread* THREAD = JavaThread::current(); // For exception macros.
  while (throwable.not_null()) {
    objArrayHandle result (THREAD, objArrayOop(backtrace(throwable())));
    if (result.is_null()) {
      st->print_raw_cr("\t<<no stack trace available>>");
      return;
    }
    BacktraceIterator iter(result, THREAD);

    while (iter.repeat()) {
      BacktraceElement bte = iter.next(THREAD);
      print_stack_element_to_stream(st, bte._mirror, bte._method_id, bte._version, bte._bci, bte._name);
    }
    if (THREAD->can_call_java()) {
      // Call getCause() which doesn't necessarily return the _cause field.
      ExceptionMark em(THREAD);
      JavaValue cause(T_OBJECT);
      JavaCalls::call_virtual(&cause,
                              throwable,
                              throwable->klass(),
                              vmSymbols::getCause_name(),
                              vmSymbols::void_throwable_signature(),
                              THREAD);
      // Ignore any exceptions. we are in the middle of exception handling. Same as classic VM.
      if (HAS_PENDING_EXCEPTION) {
        CLEAR_PENDING_EXCEPTION;
        throwable = Handle();
      } else {
        throwable = Handle(THREAD, cause.get_oop());
        if (throwable.not_null()) {
          st->print("Caused by: ");
          print(throwable(), st);
          st->cr();
        }
      }
    } else {
      st->print_raw_cr("<<cannot call Java to get cause>>");
      return;
    }
  }
}

/**
 * Print the throwable stack trace by calling the Java method java.lang.Throwable.printStackTrace().
 */
void java_lang_Throwable::java_printStackTrace(Handle throwable, TRAPS) {
  assert(throwable->is_a(vmClasses::Throwable_klass()), "Throwable instance expected");
  JavaValue result(T_VOID);
  JavaCalls::call_virtual(&result,
                          throwable,
                          vmClasses::Throwable_klass(),
                          vmSymbols::printStackTrace_name(),
                          vmSymbols::void_method_signature(),
                          THREAD);
}

void java_lang_Throwable::fill_in_stack_trace(Handle throwable, const methodHandle& method, TRAPS) {
  if (!StackTraceInThrowable) return;
  ResourceMark rm(THREAD);

  // Start out by clearing the backtrace for this object, in case the VM
  // runs out of memory while allocating the stack trace
  set_backtrace(throwable(), nullptr);
  // Clear lazily constructed Java level stacktrace if refilling occurs
  // This is unnecessary in 1.7+ but harmless
  clear_stacktrace(throwable());

  int max_depth = MaxJavaStackTraceDepth;
  JavaThread* thread = THREAD;

  BacktraceBuilder bt(CHECK);

  // If there is no Java frame just return the method that was being called
  // with bci 0
  if (!thread->has_last_Java_frame()) {
    if (max_depth >= 1 && method() != nullptr) {
      bt.push(method(), 0, CHECK);
      log_info(stacktrace)("%s, %d", throwable->klass()->external_name(), 1);
      set_depth(throwable(), 1);
      set_backtrace(throwable(), bt.backtrace());
    }
    return;
  }

  // Instead of using vframe directly, this version of fill_in_stack_trace
  // basically handles everything by hand. This significantly improved the
  // speed of this method call up to 28.5% on Solaris sparc. 27.1% on Windows.
  // See bug 6333838 for  more details.
  // The "ASSERT" here is to verify this method generates the exactly same stack
  // trace as utilizing vframe.
#ifdef ASSERT
  vframeStream st(thread, false /* stop_at_java_call_stub */, false /* process_frames */);
#endif
  int total_count = 0;
  RegisterMap map(thread,
                  RegisterMap::UpdateMap::skip,
                  RegisterMap::ProcessFrames::skip,
                  RegisterMap::WalkContinuation::include);
  int decode_offset = 0;
  nmethod* nm = nullptr;
  bool skip_fillInStackTrace_check = false;
  bool skip_throwableInit_check = false;
  bool skip_hidden = !ShowHiddenFrames;
  bool show_carrier = ShowCarrierFrames;
  ContinuationEntry* cont_entry = thread->last_continuation();
  for (frame fr = thread->last_frame(); max_depth == 0 || max_depth != total_count;) {
    Method* method = nullptr;
    int bci = 0;

    // Compiled java method case.
    if (decode_offset != 0) {
      DebugInfoReadStream stream(nm, decode_offset);
      decode_offset = stream.read_int();
      method = (Method*)nm->metadata_at(stream.read_int());
      bci = stream.read_bci();
    } else {
      if (fr.is_first_frame()) break;

      if (Continuation::is_continuation_enterSpecial(fr)) {
        assert(cont_entry == Continuation::get_continuation_entry_for_entry_frame(thread, fr), "");
        if (!show_carrier && cont_entry->is_virtual_thread()) {
          break;
        }
        cont_entry = cont_entry->parent();
      }

      address pc = fr.pc();
      if (fr.is_interpreted_frame()) {
        address bcp;
        if (!map.in_cont()) {
          bcp = fr.interpreter_frame_bcp();
          method = fr.interpreter_frame_method();
        } else {
          bcp = map.stack_chunk()->interpreter_frame_bcp(fr);
          method = map.stack_chunk()->interpreter_frame_method(fr);
        }
        bci =  method->bci_from(bcp);
        fr = fr.sender(&map);
      } else {
        CodeBlob* cb = fr.cb();
        // HMMM QQQ might be nice to have frame return nm as null if cb is non-null
        // but non nmethod
        fr = fr.sender(&map);
        if (cb == nullptr || !cb->is_nmethod()) {
          continue;
        }
        nm = cb->as_nmethod();
        assert(nm->method() != nullptr, "must be");
        if (nm->method()->is_native()) {
          method = nm->method();
          bci = 0;
        } else {
          PcDesc* pd = nm->pc_desc_at(pc);
          decode_offset = pd->scope_decode_offset();
          // if decode_offset is not equal to 0, it will execute the
          // "compiled java method case" at the beginning of the loop.
          continue;
        }
      }
    }
#ifdef ASSERT
    if (!st.at_end()) { // TODO LOOM remove once we show only vthread trace
      assert(st.method() == method && st.bci() == bci, "Wrong stack trace");
      st.next();
    }
#endif

    // the format of the stacktrace will be:
    // - 1 or more fillInStackTrace frames for the exception class (skipped)
    // - 0 or more <init> methods for the exception class (skipped)
    // - rest of the stack

    if (!skip_fillInStackTrace_check) {
      if (method->name() == vmSymbols::fillInStackTrace_name() &&
          throwable->is_a(method->method_holder())) {
        continue;
      }
      else {
        skip_fillInStackTrace_check = true; // gone past them all
      }
    }
    if (!skip_throwableInit_check) {
      assert(skip_fillInStackTrace_check, "logic error in backtrace filtering");

      // skip <init> methods of the exception class and superclasses
      // This is similar to classic VM.
      if (method->name() == vmSymbols::object_initializer_name() &&
          throwable->is_a(method->method_holder())) {
        continue;
      } else {
        // there are none or we've seen them all - either way stop checking
        skip_throwableInit_check = true;
      }
    }
    if (method->is_hidden() || method->is_continuation_enter_intrinsic()) {
      if (skip_hidden) {
        if (total_count == 0) {
          // The top frame will be hidden from the stack trace.
          bt.set_has_hidden_top_frame();
        }
        continue;
      }
    }

    bt.push(method, bci, CHECK);
    total_count++;
  }

  log_info(stacktrace)("%s, %d", throwable->klass()->external_name(), total_count);

  // Put completed stack trace into throwable object
  set_backtrace(throwable(), bt.backtrace());
  set_depth(throwable(), total_count);
}

void java_lang_Throwable::fill_in_stack_trace(Handle throwable, const methodHandle& method) {
  // No-op if stack trace is disabled
  if (!StackTraceInThrowable) {
    return;
  }

  // Disable stack traces for some preallocated out of memory errors
  if (!Universe::should_fill_in_stack_trace(throwable)) {
    return;
  }

  JavaThread* THREAD = JavaThread::current(); // For exception macros.
  PreserveExceptionMark pm(THREAD);

  fill_in_stack_trace(throwable, method, THREAD);
  // Ignore exceptions thrown during stack trace filling (OOM) and reinstall the
  // original exception via the PreserveExceptionMark destructor.
  CLEAR_PENDING_EXCEPTION;
}

void java_lang_Throwable::allocate_backtrace(Handle throwable, TRAPS) {
  // Allocate stack trace - backtrace is created but not filled in

  // No-op if stack trace is disabled
  if (!StackTraceInThrowable) return;
  BacktraceBuilder bt(CHECK);   // creates a backtrace
  set_backtrace(throwable(), bt.backtrace());
}

void java_lang_Throwable::fill_in_stack_trace_of_preallocated_backtrace(Handle throwable) {
  // Fill in stack trace into preallocated backtrace (no GC)

  // No-op if stack trace is disabled
  if (!StackTraceInThrowable) return;

  assert(throwable->is_a(vmClasses::Throwable_klass()), "sanity check");

  JavaThread* THREAD = JavaThread::current(); // For exception macros.

  objArrayHandle backtrace (THREAD, (objArrayOop)java_lang_Throwable::backtrace(throwable()));
  assert(backtrace.not_null(), "backtrace should have been preallocated");

  ResourceMark rm(THREAD);
  vframeStream st(THREAD, false /* stop_at_java_call_stub */, false /* process_frames */);

  BacktraceBuilder bt(THREAD, backtrace);

  // Unlike fill_in_stack_trace we do not skip fillInStackTrace or throwable init
  // methods as preallocated errors aren't created by "java" code.

  // fill in as much stack trace as possible
  int chunk_count = 0;
  for (;!st.at_end(); st.next()) {
    bt.push(st.method(), st.bci(), CHECK);
    chunk_count++;

    // Bail-out for deep stacks
    if (chunk_count >= trace_chunk_size) break;
  }
  set_depth(throwable(), chunk_count);
  log_info(stacktrace)("%s, %d", throwable->klass()->external_name(), chunk_count);

  // We support the Throwable immutability protocol defined for Java 7.
  java_lang_Throwable::set_stacktrace(throwable(), java_lang_Throwable::unassigned_stacktrace());
  assert(java_lang_Throwable::unassigned_stacktrace() != nullptr, "not initialized");
}

void java_lang_Throwable::get_stack_trace_elements(int depth, Handle backtrace,
                                                   objArrayHandle stack_trace_array_h, TRAPS) {

  if (backtrace.is_null() || stack_trace_array_h.is_null()) {
    THROW(vmSymbols::java_lang_NullPointerException());
  }

  assert(stack_trace_array_h->is_objArray(), "Stack trace array should be an array of StackTraceElenent");

  if (stack_trace_array_h->length() != depth) {
    THROW(vmSymbols::java_lang_IndexOutOfBoundsException());
  }

  objArrayHandle result(THREAD, objArrayOop(backtrace()));
  BacktraceIterator iter(result, THREAD);

  int index = 0;
  while (iter.repeat()) {
    BacktraceElement bte = iter.next(THREAD);

    Handle stack_trace_element(THREAD, stack_trace_array_h->obj_at(index++));

    if (stack_trace_element.is_null()) {
      THROW(vmSymbols::java_lang_NullPointerException());
    }

    InstanceKlass* holder = java_lang_Class::as_InstanceKlass(bte._mirror());
    methodHandle method (THREAD, holder->method_with_orig_idnum(bte._method_id, bte._version));

    java_lang_StackTraceElement::fill_in(stack_trace_element, holder,
                                         method,
                                         bte._version,
                                         bte._bci,
                                         bte._name,
                                         CHECK);
  }
}

Handle java_lang_Throwable::create_initialization_error(JavaThread* current, Handle throwable) {
  // Creates an ExceptionInInitializerError to be recorded as the initialization error when class initialization
  // failed due to the passed in 'throwable'. We cannot save 'throwable' directly due to issues with keeping alive
  // all objects referenced via its stacktrace. So instead we save a new EIIE instance, with the same message and
  // symbolic stacktrace of 'throwable'.
  assert(throwable.not_null(), "shouldn't be");

  // Now create the message from the original exception and thread name.
  ResourceMark rm(current);
  const char *message = nullptr;
  oop detailed_message = java_lang_Throwable::message(throwable());
  if (detailed_message != nullptr) {
    message = java_lang_String::as_utf8_string(detailed_message);
  }
  stringStream st;
  st.print("Exception %s%s ", throwable()->klass()->name()->as_klass_external_name(),
             message == nullptr ? "" : ":");
  if (message == nullptr) {
    st.print("[in thread \"%s\"]", current->name());
  } else {
    st.print("%s [in thread \"%s\"]", message, current->name());
  }

  Symbol* exception_name = vmSymbols::java_lang_ExceptionInInitializerError();
  Handle init_error = Exceptions::new_exception(current, exception_name, st.as_string());
  // If new_exception returns a different exception while creating the exception,
  // abandon the attempt to save the initialization error and return null.
  if (init_error->klass()->name() != exception_name) {
    log_info(class, init)("Exception %s thrown while saving initialization exception",
                        init_error->klass()->external_name());
    return Handle();
  }

  // Call to java to fill in the stack trace and clear declaringClassObject to
  // not keep classes alive in the stack trace.
  // call this:  public StackTraceElement[] getStackTrace()
  JavaValue result(T_ARRAY);
  JavaCalls::call_virtual(&result, throwable,
                          vmClasses::Throwable_klass(),
                          vmSymbols::getStackTrace_name(),
                          vmSymbols::getStackTrace_signature(),
                          current);
  if (!current->has_pending_exception()){
    Handle stack_trace(current, result.get_oop());
    assert(stack_trace->is_objArray(), "Should be an array");
    java_lang_Throwable::set_stacktrace(init_error(), stack_trace());
    // Clear backtrace because the stacktrace should be used instead.
    set_backtrace(init_error(), nullptr);
  } else {
    log_info(class, init)("Exception thrown while getting stack trace for initialization exception %s",
                        init_error->klass()->external_name());
    current->clear_pending_exception();
  }

  return init_error;
}

bool java_lang_Throwable::get_top_method_and_bci(oop throwable, Method** method, int* bci) {
  JavaThread* current = JavaThread::current();
  objArrayHandle result(current, objArrayOop(backtrace(throwable)));
  BacktraceIterator iter(result, current);
  // No backtrace available.
  if (!iter.repeat()) return false;

  // If the exception happened in a frame that has been hidden, i.e.,
  // omitted from the back trace, we can not compute the message.
  oop hidden = ((objArrayOop)backtrace(throwable))->obj_at(trace_hidden_offset);
  if (hidden != nullptr) {
    return false;
  }

  // Get first backtrace element.
  BacktraceElement bte = iter.next(current);

  InstanceKlass* holder = java_lang_Class::as_InstanceKlass(bte._mirror());
  assert(holder != nullptr, "first element should be non-null");
  Method* m = holder->method_with_orig_idnum(bte._method_id, bte._version);

  // Original version is no longer available.
  if (m == nullptr || !version_matches(m, bte._version)) {
    return false;
  }

  *method = m;
  *bci = bte._bci;
  return true;
}

oop java_lang_StackTraceElement::create(const methodHandle& method, int bci, TRAPS) {
  // Allocate java.lang.StackTraceElement instance
  InstanceKlass* k = vmClasses::StackTraceElement_klass();
  assert(k != nullptr, "must be loaded in 1.4+");
  if (k->should_be_initialized()) {
    k->initialize(CHECK_NULL);
  }

  Handle element = k->allocate_instance_handle(CHECK_NULL);

  int version = method->constants()->version();
  fill_in(element, method->method_holder(), method, version, bci, method->name(), CHECK_NULL);
  return element();
}

void java_lang_StackTraceElement::fill_in(Handle element,
                                          InstanceKlass* holder, const methodHandle& method,
                                          int version, int bci, Symbol* name, TRAPS) {
  assert(element->is_a(vmClasses::StackTraceElement_klass()), "sanity check");

  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);

  // Fill in class name
  Handle java_class(THREAD, holder->java_mirror());
  oop classname = java_lang_Class::name(java_class, CHECK);
  java_lang_StackTraceElement::set_declaringClass(element(), classname);
  java_lang_StackTraceElement::set_declaringClassObject(element(), java_class());

  oop loader = holder->class_loader();
  if (loader != nullptr) {
    oop loader_name = java_lang_ClassLoader::name(loader);
    if (loader_name != nullptr)
      java_lang_StackTraceElement::set_classLoaderName(element(), loader_name);
  }

  // Fill in method name
  oop methodname = StringTable::intern(name, CHECK);
  java_lang_StackTraceElement::set_methodName(element(), methodname);

  // Fill in module name and version
  ModuleEntry* module = holder->module();
  if (module->is_named()) {
    oop module_name = StringTable::intern(module->name(), CHECK);
    java_lang_StackTraceElement::set_moduleName(element(), module_name);
    oop module_version;
    if (module->version() != nullptr) {
      module_version = StringTable::intern(module->version(), CHECK);
    } else {
      module_version = nullptr;
    }
    java_lang_StackTraceElement::set_moduleVersion(element(), module_version);
  }

  if (method() == nullptr || !version_matches(method(), version)) {
    // The method was redefined, accurate line number information isn't available
    java_lang_StackTraceElement::set_fileName(element(), nullptr);
    java_lang_StackTraceElement::set_lineNumber(element(), -1);
  } else {
    Symbol* source;
    oop source_file;
    int line_number;
    decode_file_and_line(java_class, holder, version, method, bci, source, source_file, line_number, CHECK);

    java_lang_StackTraceElement::set_fileName(element(), source_file);
    java_lang_StackTraceElement::set_lineNumber(element(), line_number);
  }
}

void java_lang_StackTraceElement::decode_file_and_line(Handle java_class,
                                                       InstanceKlass* holder,
                                                       int version,
                                                       const methodHandle& method,
                                                       int bci,
                                                       Symbol*& source,
                                                       oop& source_file,
                                                       int& line_number, TRAPS) {
  // Fill in source file name and line number.
  source = get_source_file_name(holder, version);
  source_file = java_lang_Class::source_file(java_class());
  if (source != nullptr) {
    // Class was not redefined. We can trust its cache if set,
    // else we have to initialize it.
    if (source_file == nullptr) {
      source_file = StringTable::intern(source, CHECK);
      java_lang_Class::set_source_file(java_class(), source_file);
    }
  } else {
    // Class was redefined. Dump the cache if it was set.
    if (source_file != nullptr) {
      source_file = nullptr;
      java_lang_Class::set_source_file(java_class(), source_file);
    }
  }
  line_number = get_line_number(method(), bci);
}

#if INCLUDE_JVMCI
void java_lang_StackTraceElement::decode(const methodHandle& method, int bci,
                                         Symbol*& filename, int& line_number, TRAPS) {
  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);

  filename = nullptr;
  line_number = -1;

  oop source_file;
  int version = method->constants()->version();
  InstanceKlass* holder = method->method_holder();
  Handle java_class(THREAD, holder->java_mirror());
  decode_file_and_line(java_class, holder, version, method, bci, filename, source_file, line_number, CHECK);
}
#endif // INCLUDE_JVMCI

// java_lang_ClassFrameInfo

int java_lang_ClassFrameInfo::_classOrMemberName_offset;
int java_lang_ClassFrameInfo::_flags_offset;

#define CLASSFRAMEINFO_FIELDS_DO(macro) \
  macro(_classOrMemberName_offset, k, "classOrMemberName", object_signature,  false); \
  macro(_flags_offset,             k, vmSymbols::flags_name(), int_signature, false)

void java_lang_ClassFrameInfo::compute_offsets() {
  InstanceKlass* k = vmClasses::ClassFrameInfo_klass();
  CLASSFRAMEINFO_FIELDS_DO(FIELD_COMPUTE_OFFSET);
}

#if INCLUDE_CDS
void java_lang_ClassFrameInfo::serialize_offsets(SerializeClosure* f) {
  CLASSFRAMEINFO_FIELDS_DO(FIELD_SERIALIZE_OFFSET);
}
#endif

static int get_flags(const methodHandle& m) {
  int flags = m->access_flags().as_method_flags();
  if (m->is_object_initializer()) {
    flags |= java_lang_invoke_MemberName::MN_IS_CONSTRUCTOR;
  } else {
    // Note: Static initializers can be here. Record them as plain methods.
    flags |= java_lang_invoke_MemberName::MN_IS_METHOD;
  }
  if (m->caller_sensitive()) {
    flags |= java_lang_invoke_MemberName::MN_CALLER_SENSITIVE;
  }
  if (m->is_hidden()) {
    flags |= java_lang_invoke_MemberName::MN_HIDDEN_MEMBER;
  }
  assert((flags & 0xFF000000) == 0, "unexpected flags");
  return flags;
}

oop java_lang_ClassFrameInfo::classOrMemberName(oop obj) {
  return obj->obj_field(_classOrMemberName_offset);
}

int java_lang_ClassFrameInfo::flags(oop obj) {
  return obj->int_field(_flags_offset);
}

void java_lang_ClassFrameInfo::init_class(Handle stackFrame, const methodHandle& m) {
  stackFrame->obj_field_put(_classOrMemberName_offset, m->method_holder()->java_mirror());
  // flags is initialized when ClassFrameInfo object is constructed and retain the value
  int flags = java_lang_ClassFrameInfo::flags(stackFrame()) | get_flags(m);
  stackFrame->int_field_put(_flags_offset, flags);
}

void java_lang_ClassFrameInfo::init_method(Handle stackFrame, const methodHandle& m, TRAPS) {
  oop rmethod_name = java_lang_invoke_ResolvedMethodName::find_resolved_method(m, CHECK);
  stackFrame->obj_field_put(_classOrMemberName_offset, rmethod_name);
  // flags is initialized when ClassFrameInfo object is constructed and retain the value
  int flags = java_lang_ClassFrameInfo::flags(stackFrame()) | get_flags(m);
  stackFrame->int_field_put(_flags_offset, flags);
}


// java_lang_StackFrameInfo

int java_lang_StackFrameInfo::_type_offset;
int java_lang_StackFrameInfo::_name_offset;
int java_lang_StackFrameInfo::_bci_offset;
int java_lang_StackFrameInfo::_version_offset;
int java_lang_StackFrameInfo::_contScope_offset;

#define STACKFRAMEINFO_FIELDS_DO(macro) \
  macro(_type_offset,       k, "type",       object_signature,            false); \
  macro(_name_offset,       k, "name",       string_signature,            false); \
  macro(_bci_offset,        k, "bci",        int_signature,               false); \
  macro(_contScope_offset,  k, "contScope",  continuationscope_signature, false)

void java_lang_StackFrameInfo::compute_offsets() {
  InstanceKlass* k = vmClasses::StackFrameInfo_klass();
  STACKFRAMEINFO_FIELDS_DO(FIELD_COMPUTE_OFFSET);
  STACKFRAMEINFO_INJECTED_FIELDS(INJECTED_FIELD_COMPUTE_OFFSET);
}

#if INCLUDE_CDS
void java_lang_StackFrameInfo::serialize_offsets(SerializeClosure* f) {
  STACKFRAMEINFO_FIELDS_DO(FIELD_SERIALIZE_OFFSET);
  STACKFRAMEINFO_INJECTED_FIELDS(INJECTED_FIELD_SERIALIZE_OFFSET);
}
#endif

Method* java_lang_StackFrameInfo::get_method(oop obj) {
  oop m = java_lang_ClassFrameInfo::classOrMemberName(obj);
  Method* method = java_lang_invoke_ResolvedMethodName::vmtarget(m);
  return method;
}

void java_lang_StackFrameInfo::set_method_and_bci(Handle stackFrame, const methodHandle& method, int bci, oop cont, TRAPS) {
  // set Method* or mid/cpref
  HandleMark hm(THREAD);
  Handle cont_h(THREAD, cont);
  java_lang_ClassFrameInfo::init_method(stackFrame, method, CHECK);

  // set bci
  java_lang_StackFrameInfo::set_bci(stackFrame(), bci);
  // method may be redefined; store the version
  int version = method->constants()->version();
  assert((jushort)version == version, "version should be short");
  java_lang_StackFrameInfo::set_version(stackFrame(), (short)version);

  oop contScope = cont_h() != nullptr ? jdk_internal_vm_Continuation::scope(cont_h()) : (oop)nullptr;
  java_lang_StackFrameInfo::set_contScope(stackFrame(), contScope);
}

void java_lang_StackFrameInfo::to_stack_trace_element(Handle stackFrame, Handle stack_trace_element, TRAPS) {
  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);

  Method* method = java_lang_StackFrameInfo::get_method(stackFrame());
  InstanceKlass* holder = method->method_holder();
  short version = stackFrame->short_field(_version_offset);
  int bci = stackFrame->int_field(_bci_offset);
  Symbol* name = method->name();
  java_lang_StackTraceElement::fill_in(stack_trace_element, holder, methodHandle(THREAD, method), version, bci, name, CHECK);
}

oop java_lang_StackFrameInfo::type(oop obj) {
  return obj->obj_field(_type_offset);
}

void java_lang_StackFrameInfo::set_type(oop obj, oop value) {
  obj->obj_field_put(_type_offset, value);
}

oop java_lang_StackFrameInfo::name(oop obj) {
  return obj->obj_field(_name_offset);
}

void java_lang_StackFrameInfo::set_name(oop obj, oop value) {
  obj->obj_field_put(_name_offset, value);
}

void java_lang_StackFrameInfo::set_version(oop obj, short value) {
  obj->short_field_put(_version_offset, value);
}

void java_lang_StackFrameInfo::set_bci(oop obj, int value) {
  assert(value >= 0 && value < max_jushort, "must be a valid bci value");
  obj->int_field_put(_bci_offset, value);
}

void java_lang_StackFrameInfo::set_contScope(oop obj, oop value) {
  obj->obj_field_put(_contScope_offset, value);
}

int java_lang_LiveStackFrameInfo::_monitors_offset;
int java_lang_LiveStackFrameInfo::_locals_offset;
int java_lang_LiveStackFrameInfo::_operands_offset;
int java_lang_LiveStackFrameInfo::_mode_offset;

#define LIVESTACKFRAMEINFO_FIELDS_DO(macro) \
  macro(_monitors_offset,   k, "monitors",    object_array_signature, false); \
  macro(_locals_offset,     k, "locals",      object_array_signature, false); \
  macro(_operands_offset,   k, "operands",    object_array_signature, false); \
  macro(_mode_offset,       k, "mode",        int_signature,          false)

void java_lang_LiveStackFrameInfo::compute_offsets() {
  InstanceKlass* k = vmClasses::LiveStackFrameInfo_klass();
  LIVESTACKFRAMEINFO_FIELDS_DO(FIELD_COMPUTE_OFFSET);
}

#if INCLUDE_CDS
void java_lang_LiveStackFrameInfo::serialize_offsets(SerializeClosure* f) {
  LIVESTACKFRAMEINFO_FIELDS_DO(FIELD_SERIALIZE_OFFSET);
}
#endif

void java_lang_LiveStackFrameInfo::set_monitors(oop obj, oop value) {
  obj->obj_field_put(_monitors_offset, value);
}

void java_lang_LiveStackFrameInfo::set_locals(oop obj, oop value) {
  obj->obj_field_put(_locals_offset, value);
}

void java_lang_LiveStackFrameInfo::set_operands(oop obj, oop value) {
  obj->obj_field_put(_operands_offset, value);
}

void java_lang_LiveStackFrameInfo::set_mode(oop obj, int value) {
  obj->int_field_put(_mode_offset, value);
}

// java_lang_StackTraceElement

int java_lang_StackTraceElement::_methodName_offset;
int java_lang_StackTraceElement::_fileName_offset;
int java_lang_StackTraceElement::_lineNumber_offset;
int java_lang_StackTraceElement::_moduleName_offset;
int java_lang_StackTraceElement::_moduleVersion_offset;
int java_lang_StackTraceElement::_classLoaderName_offset;
int java_lang_StackTraceElement::_declaringClass_offset;
int java_lang_StackTraceElement::_declaringClassObject_offset;

#define STACKTRACEELEMENT_FIELDS_DO(macro) \
  macro(_declaringClassObject_offset,  k, "declaringClassObject", class_signature, false); \
  macro(_classLoaderName_offset, k, "classLoaderName", string_signature, false); \
  macro(_moduleName_offset,      k, "moduleName",      string_signature, false); \
  macro(_moduleVersion_offset,   k, "moduleVersion",   string_signature, false); \
  macro(_declaringClass_offset,  k, "declaringClass",  string_signature, false); \
  macro(_methodName_offset,      k, "methodName",      string_signature, false); \
  macro(_fileName_offset,        k, "fileName",        string_signature, false); \
  macro(_lineNumber_offset,      k, "lineNumber",      int_signature,    false)

// Support for java_lang_StackTraceElement
void java_lang_StackTraceElement::compute_offsets() {
  InstanceKlass* k = vmClasses::StackTraceElement_klass();
  STACKTRACEELEMENT_FIELDS_DO(FIELD_COMPUTE_OFFSET);
}

#if INCLUDE_CDS
void java_lang_StackTraceElement::serialize_offsets(SerializeClosure* f) {
  STACKTRACEELEMENT_FIELDS_DO(FIELD_SERIALIZE_OFFSET);
}
#endif

void java_lang_StackTraceElement::set_fileName(oop element, oop value) {
  element->obj_field_put(_fileName_offset, value);
}

void java_lang_StackTraceElement::set_declaringClass(oop element, oop value) {
  element->obj_field_put(_declaringClass_offset, value);
}

void java_lang_StackTraceElement::set_methodName(oop element, oop value) {
  element->obj_field_put(_methodName_offset, value);
}

void java_lang_StackTraceElement::set_lineNumber(oop element, int value) {
  element->int_field_put(_lineNumber_offset, value);
}

void java_lang_StackTraceElement::set_moduleName(oop element, oop value) {
  element->obj_field_put(_moduleName_offset, value);
}

void java_lang_StackTraceElement::set_moduleVersion(oop element, oop value) {
  element->obj_field_put(_moduleVersion_offset, value);
}

void java_lang_StackTraceElement::set_classLoaderName(oop element, oop value) {
  element->obj_field_put(_classLoaderName_offset, value);
}

void java_lang_StackTraceElement::set_declaringClassObject(oop element, oop value) {
  element->obj_field_put(_declaringClassObject_offset, value);
}


