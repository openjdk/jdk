/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciMethodData.hpp"
#include "ci/ciReplay.hpp"
#include "ci/ciSymbol.hpp"
#include "ci/ciKlass.hpp"
#include "ci/ciUtilities.inline.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "compiler/compilationPolicy.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compilerDefinitions.inline.hpp"
#include "interpreter/linkResolver.hpp"
#include "jvm.h"
#include "memory/allocation.inline.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/constantPool.inline.hpp"
#include "oops/cpCache.inline.hpp"
#include "oops/fieldStreams.inline.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/resolvedIndyEntry.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/threads.hpp"
#include "utilities/copy.hpp"
#include "utilities/macros.hpp"
#include "utilities/utf8.hpp"

// ciReplay

typedef struct _ciMethodDataRecord {
  const char* _klass_name;
  const char* _method_name;
  const char* _signature;

  int _state;
  int _invocation_counter;

  intptr_t* _data;
  char*     _orig_data;
  Klass**   _classes;
  Method**  _methods;
  int*      _classes_offsets;
  int*      _methods_offsets;
  int       _data_length;
  int       _orig_data_length;
  int       _classes_length;
  int       _methods_length;
} ciMethodDataRecord;

typedef struct _ciMethodRecord {
  const char* _klass_name;
  const char* _method_name;
  const char* _signature;

  int _instructions_size;
  int _interpreter_invocation_count;
  int _interpreter_throwout_count;
  int _invocation_counter;
  int _backedge_counter;
} ciMethodRecord;

typedef struct _ciInstanceKlassRecord {
  const InstanceKlass* _klass;
  jobject _java_mirror; // Global handle to java mirror to prevent unloading
} ciInstanceKlassRecord;

typedef struct _ciInlineRecord {
  const char* _klass_name;
  const char* _method_name;
  const char* _signature;

  int _inline_depth;
  int _inline_bci;
  bool _inline_late;
} ciInlineRecord;

class  CompileReplay;
static CompileReplay* replay_state;

class CompileReplay : public StackObj {
 private:
  FILE*   _stream;
  Thread* _thread;
  Handle  _protection_domain;
  bool    _protection_domain_initialized;
  Handle  _loader;
  int     _version;

  GrowableArray<ciMethodRecord*>     _ci_method_records;
  GrowableArray<ciMethodDataRecord*> _ci_method_data_records;
  GrowableArray<ciInstanceKlassRecord*> _ci_instance_klass_records;

  // Use pointer because we may need to return inline records
  // without destroying them.
  GrowableArray<ciInlineRecord*>*    _ci_inline_records;

  const char* _error_message;

  char* _bufptr;
  char* _buffer;
  int   _buffer_length;

  // "compile" data
  ciKlass* _iklass;
  Method*  _imethod;
  int      _entry_bci;
  int      _comp_level;

 public:
  CompileReplay(const char* filename, TRAPS) {
    _thread = THREAD;
    _loader = Handle(_thread, SystemDictionary::java_system_loader());
    _protection_domain = Handle();
    _protection_domain_initialized = false;

    _stream = os::fopen(filename, "rt");
    if (_stream == nullptr) {
      fprintf(stderr, "ERROR: Can't open replay file %s\n", filename);
    }

    _ci_inline_records = nullptr;
    _error_message = nullptr;

    _buffer_length = 32;
    _buffer = NEW_RESOURCE_ARRAY(char, _buffer_length);
    _bufptr = _buffer;

    _imethod = nullptr;
    _iklass  = nullptr;
    _entry_bci  = 0;
    _comp_level = 0;
    _version = 0;

    test();
  }

  ~CompileReplay() {
    if (_stream != nullptr) fclose(_stream);
  }

  void test() {
    strcpy(_buffer, "1 2 foo 4 bar 0x9 \"this is it\"");
    _bufptr = _buffer;
    assert(parse_int("test") == 1, "what");
    assert(parse_int("test") == 2, "what");
    assert(strcmp(parse_string(), "foo") == 0, "what");
    assert(parse_int("test") == 4, "what");
    assert(strcmp(parse_string(), "bar") == 0, "what");
    assert(parse_intptr_t("test") == 9, "what");
    assert(strcmp(parse_quoted_string(), "this is it") == 0, "what");
  }

  bool had_error() {
    return _error_message != nullptr || _thread->has_pending_exception();
  }

  bool can_replay() {
    return !(_stream == nullptr || had_error());
  }

  void report_error(const char* msg) {
    _error_message = msg;
  }

  int parse_int(const char* label) {
    if (had_error()) {
      return 0;
    }

    int v = 0;
    int read;
    if (sscanf(_bufptr, "%i%n", &v, &read) != 1) {
      report_error(label);
    } else {
      _bufptr += read;
    }
    return v;
  }

  intptr_t parse_intptr_t(const char* label) {
    if (had_error()) {
      return 0;
    }

    intptr_t v = 0;
    int read;
    if (sscanf(_bufptr, INTPTR_FORMAT "%n", &v, &read) != 1) {
      report_error(label);
    } else {
      _bufptr += read;
    }
    return v;
  }

  void skip_ws() {
    // Skip any leading whitespace
    while (*_bufptr == ' ' || *_bufptr == '\t') {
      _bufptr++;
    }
  }

  // Ignore the rest of the line
  void skip_remaining() {
    _bufptr = &_bufptr[strlen(_bufptr)]; // skip ahead to terminator
  }

  char* scan_and_terminate(char delim) {
    char* str = _bufptr;
    while (*_bufptr != delim && *_bufptr != '\0') {
      _bufptr++;
    }
    if (*_bufptr != '\0') {
      *_bufptr++ = '\0';
    }
    if (_bufptr == str) {
      // nothing here
      return nullptr;
    }
    return str;
  }

  char* parse_string() {
    if (had_error()) return nullptr;

    skip_ws();
    return scan_and_terminate(' ');
  }

  char* parse_quoted_string() {
    if (had_error()) return nullptr;

    skip_ws();

    if (*_bufptr == '"') {
      _bufptr++;
      return scan_and_terminate('"');
    } else {
      return scan_and_terminate(' ');
    }
  }

  char* parse_escaped_string() {
    char* result = parse_quoted_string();
    if (result != nullptr) {
      unescape_string(result);
    }
    return result;
  }

  // Look for the tag 'tag' followed by an
  bool parse_tag_and_count(const char* tag, int& length) {
    const char* t = parse_string();
    if (t == nullptr) {
      return false;
    }

    if (strcmp(tag, t) != 0) {
      report_error(tag);
      return false;
    }
    length = parse_int("parse_tag_and_count");
    return !had_error();
  }

  // Parse a sequence of raw data encoded as bytes and return the
  // resulting data.
  char* parse_data(const char* tag, int& length) {
    int read_size = 0;
    if (!parse_tag_and_count(tag, read_size)) {
      return nullptr;
    }

    int actual_size = sizeof(MethodData::CompilerCounters);
    char *result = NEW_RESOURCE_ARRAY(char, actual_size);
    int i = 0;
    if (read_size != actual_size) {
      tty->print_cr("Warning: ciMethodData parsing sees MethodData size %i in file, current is %i", read_size,
                    actual_size);
      // Replay serializes the entire MethodData, but the data is at the end.
      // If the MethodData instance size has changed, we can pad or truncate in the beginning
      int padding = actual_size - read_size;
      if (padding > 0) {
        // pad missing data with zeros
        tty->print_cr("- Padding MethodData");
        for (; i < padding; i++) {
          result[i] = 0;
        }
      } else if (padding < 0) {
        // drop some data
        tty->print_cr("- Truncating MethodData");
        for (int j = 0; j < -padding; j++) {
          int val = parse_int("data");
          // discard val
        }
      }
    }

    assert(i < actual_size, "At least some data must remain to be copied");
    for (; i < actual_size; i++) {
      int val = parse_int("data");
      result[i] = val;
    }
    length = actual_size;
    return result;
  }

  // Parse a standard chunk of data emitted as:
  //   'tag' <length> # # ...
  // Where each # is an intptr_t item
  intptr_t* parse_intptr_data(const char* tag, int& length) {
    if (!parse_tag_and_count(tag, length)) {
      return nullptr;
    }

    intptr_t* result = NEW_RESOURCE_ARRAY(intptr_t, length);
    for (int i = 0; i < length; i++) {
      skip_ws();
      intptr_t val = parse_intptr_t("data");
      result[i] = val;
    }
    return result;
  }

  // Parse a possibly quoted version of a symbol into a symbolOop
  Symbol* parse_symbol() {
    const char* str = parse_escaped_string();
    if (str != nullptr) {
      Symbol* sym = SymbolTable::new_symbol(str);
      return sym;
    }
    return nullptr;
  }

  bool parse_terminator() {
    char* terminator = parse_string();
    if (terminator != nullptr && strcmp(terminator, ";") == 0) {
      return true;
    }
    return false;
  }

  // Parse a special hidden klass location syntax
  // syntax: @bci <klass> <name> <signature> <bci> <location>* ;
  // syntax: @cpi <klass> <cpi> <location>* ;
  Klass* parse_cp_ref(TRAPS) {
    JavaThread* thread = THREAD;
    oop obj = nullptr;
    char* ref = parse_string();
    if (strcmp(ref, "bci") == 0) {
      Method* m = parse_method(CHECK_NULL);
      if (m == nullptr) {
        return nullptr;
      }

      InstanceKlass* ik = m->method_holder();
      const constantPoolHandle cp(Thread::current(), ik->constants());

      // invokedynamic or invokehandle

      methodHandle caller(Thread::current(), m);
      int bci = parse_int("bci");
      if (m->validate_bci(bci) != bci) {
        report_error("bad bci");
        return nullptr;
      }

      ik->link_class(CHECK_NULL);

      Bytecode_invoke bytecode = Bytecode_invoke_check(caller, bci);
      if (!Bytecodes::is_defined(bytecode.code()) || !bytecode.is_valid()) {
        report_error("no invoke found at bci");
        return nullptr;
      }
      bytecode.verify();
      int index = bytecode.index();

      ConstantPoolCacheEntry* cp_cache_entry = nullptr;
      CallInfo callInfo;
      Bytecodes::Code bc = bytecode.invoke_code();
      LinkResolver::resolve_invoke(callInfo, Handle(), cp, index, bc, CHECK_NULL);

      // ResolvedIndyEntry and ConstantPoolCacheEntry must currently coexist.
      // To address this, the variables below contain the values that *might*
      // be used to avoid multiple blocks of similar code. When CPCE is obsoleted
      // these can be removed
      oop appendix = nullptr;
      Method* adapter_method = nullptr;
      int pool_index = 0;

      if (bytecode.is_invokedynamic()) {
        index = cp->decode_invokedynamic_index(index);
        cp->cache()->set_dynamic_call(callInfo, index);

        appendix = cp->resolved_reference_from_indy(index);
        adapter_method = cp->resolved_indy_entry_at(index)->method();
        pool_index = cp->resolved_indy_entry_at(index)->constant_pool_index();
      } else if (bytecode.is_invokehandle()) {
#ifdef ASSERT
        Klass* holder = cp->klass_ref_at(index, bytecode.code(), CHECK_NULL);
        Symbol* name = cp->name_ref_at(index, bytecode.code());
        assert(MethodHandles::is_signature_polymorphic_name(holder, name), "");
#endif
        cp_cache_entry = cp->cache()->entry_at(cp->decode_cpcache_index(index));
        cp_cache_entry->set_method_handle(cp, callInfo);

        appendix = cp_cache_entry->appendix_if_resolved(cp);
        adapter_method = cp_cache_entry->f1_as_method();
        pool_index = cp_cache_entry->constant_pool_index();
      } else {
        report_error("no dynamic invoke found");
        return nullptr;
      }
      char* dyno_ref = parse_string();
      if (strcmp(dyno_ref, "<appendix>") == 0) {
        obj = appendix;
      } else if (strcmp(dyno_ref, "<adapter>") == 0) {
        if (!parse_terminator()) {
          report_error("no dynamic invoke found");
          return nullptr;
        }
        Method* adapter = adapter_method;
        if (adapter == nullptr) {
          report_error("no adapter found");
          return nullptr;
        }
        return adapter->method_holder();
      } else if (strcmp(dyno_ref, "<bsm>") == 0) {
        BootstrapInfo bootstrap_specifier(cp, pool_index, index);
        obj = cp->resolve_possibly_cached_constant_at(bootstrap_specifier.bsm_index(), CHECK_NULL);
      } else {
        report_error("unrecognized token");
        return nullptr;
      }
    } else {
      // constant pool ref (MethodHandle)
      if (strcmp(ref, "cpi") != 0) {
        report_error("unexpected token");
        return nullptr;
      }

      Klass* k = parse_klass(CHECK_NULL);
      if (k == nullptr) {
        return nullptr;
      }
      InstanceKlass* ik = InstanceKlass::cast(k);
      const constantPoolHandle cp(Thread::current(), ik->constants());

      int cpi = parse_int("cpi");

      if (cpi >= cp->length()) {
        report_error("bad cpi");
        return nullptr;
      }
      if (!cp->tag_at(cpi).is_method_handle()) {
        report_error("no method handle found at cpi");
        return nullptr;
      }
      ik->link_class(CHECK_NULL);
      obj = cp->resolve_possibly_cached_constant_at(cpi, CHECK_NULL);
    }
    if (obj == nullptr) {
      report_error("null cp object found");
      return nullptr;
    }
    Klass* k = nullptr;
    skip_ws();
    // loop: read fields
    char* field = nullptr;
    do {
      field = parse_string();
      if (field == nullptr) {
        report_error("no field found");
        return nullptr;
      }
      if (strcmp(field, ";") == 0) {
        break;
      }
      // raw Method*
      if (strcmp(field, "<vmtarget>") == 0) {
        Method* vmtarget = java_lang_invoke_MemberName::vmtarget(obj);
        k = (vmtarget == nullptr) ? nullptr : vmtarget->method_holder();
        if (k == nullptr) {
          report_error("null vmtarget found");
          return nullptr;
        }
        if (!parse_terminator()) {
          report_error("missing terminator");
          return nullptr;
        }
        return k;
      }
      obj = ciReplay::obj_field(obj, field);
      // array
      if (obj != nullptr && obj->is_objArray()) {
        objArrayOop arr = (objArrayOop)obj;
        int index = parse_int("index");
        if (index >= arr->length()) {
          report_error("bad array index");
          return nullptr;
        }
        obj = arr->obj_at(index);
      }
    } while (obj != nullptr);
    if (obj == nullptr) {
      report_error("null field found");
      return nullptr;
    }
    k = obj->klass();
    return k;
  }

  // Parse a valid klass name and look it up
  // syntax: <name>
  // syntax: <constant pool ref>
  Klass* parse_klass(TRAPS) {
    skip_ws();
    // check for constant pool object reference (for a dynamic/hidden class)
    bool cp_ref = (*_bufptr == '@');
    if (cp_ref) {
      ++_bufptr;
      Klass* k = parse_cp_ref(CHECK_NULL);
      if (k != nullptr && !k->is_hidden()) {
        report_error("expected hidden class");
        return nullptr;
      }
      return k;
    }
    char* str = parse_escaped_string();
    Symbol* klass_name = SymbolTable::new_symbol(str);
    if (klass_name != nullptr) {
      Klass* k = nullptr;
      if (_iklass != nullptr) {
        k = (Klass*)_iklass->find_klass(ciSymbol::make(klass_name->as_C_string()))->constant_encoding();
      } else {
        k = SystemDictionary::resolve_or_fail(klass_name, _loader, _protection_domain, true, THREAD);
      }
      if (HAS_PENDING_EXCEPTION) {
        oop throwable = PENDING_EXCEPTION;
        java_lang_Throwable::print(throwable, tty);
        tty->cr();
        report_error(str);
        if (ReplayIgnoreInitErrors) {
          CLEAR_PENDING_EXCEPTION;
          _error_message = nullptr;
        }
        return nullptr;
      }
      return k;
    }
    return nullptr;
  }

  // Lookup a klass
  Klass* resolve_klass(const char* klass, TRAPS) {
    Symbol* klass_name = SymbolTable::new_symbol(klass);
    return SystemDictionary::resolve_or_fail(klass_name, _loader, _protection_domain, true, THREAD);
  }

  // Parse the standard tuple of <klass> <name> <signature>
  Method* parse_method(TRAPS) {
    InstanceKlass* k = (InstanceKlass*)parse_klass(CHECK_NULL);
    if (k == nullptr) {
      report_error("Can't find holder klass");
      return nullptr;
    }
    Symbol* method_name = parse_symbol();
    Symbol* method_signature = parse_symbol();
    Method* m = k->find_method(method_name, method_signature);
    if (m == nullptr) {
      report_error("Can't find method");
    }
    return m;
  }

  int get_line(int c) {
    int buffer_pos = 0;
    while(c != EOF) {
      if (buffer_pos + 1 >= _buffer_length) {
        int new_length = _buffer_length * 2;
        // Next call will throw error in case of OOM.
        _buffer = REALLOC_RESOURCE_ARRAY(char, _buffer, _buffer_length, new_length);
        _buffer_length = new_length;
      }
      if (c == '\n') {
        c = getc(_stream); // get next char
        break;
      } else if (c == '\r') {
        // skip LF
      } else {
        _buffer[buffer_pos++] = c;
      }
      c = getc(_stream);
    }
    // null terminate it, reset the pointer
    _buffer[buffer_pos] = '\0'; // NL or EOF
    _bufptr = _buffer;
    return c;
  }

  // Process each line of the replay file executing each command until
  // the file ends.
  void process(TRAPS) {
    int line_no = 1;
    int c = getc(_stream);
    while(c != EOF) {
      c = get_line(c);
      process_command(false, THREAD);
      if (had_error()) {
        int pos = _bufptr - _buffer + 1;
        tty->print_cr("Error while parsing line %d at position %d: %s\n", line_no, pos, _error_message);
        if (ReplayIgnoreInitErrors) {
          CLEAR_PENDING_EXCEPTION;
          _error_message = nullptr;
        } else {
          return;
        }
      }
      line_no++;
    }
    reset();
  }

  void process_command(bool is_replay_inline, TRAPS) {
    char* cmd = parse_string();
    if (cmd == nullptr) {
      return;
    }
    if (strcmp("#", cmd) == 0) {
      // comment line, print or ignore
      if (Verbose) {
        tty->print_cr("# %s", _bufptr);
      }
      skip_remaining();
    } else if (strcmp("version", cmd) == 0) {
      _version = parse_int("version");
      if (_version < 0 || _version > REPLAY_VERSION) {
        tty->print_cr("# unrecognized version %d, expected 0 <= version <= %d", _version, REPLAY_VERSION);
      }
    } else if (strcmp("compile", cmd) == 0) {
      process_compile(CHECK);
    } else if (!is_replay_inline) {
      if (strcmp("ciMethod", cmd) == 0) {
        process_ciMethod(CHECK);
      } else if (strcmp("ciMethodData", cmd) == 0) {
        process_ciMethodData(CHECK);
      } else if (strcmp("staticfield", cmd) == 0) {
        process_staticfield(CHECK);
      } else if (strcmp("ciInstanceKlass", cmd) == 0) {
        process_ciInstanceKlass(CHECK);
      } else if (strcmp("instanceKlass", cmd) == 0) {
        process_instanceKlass(CHECK);
#if INCLUDE_JVMTI
      } else if (strcmp("JvmtiExport", cmd) == 0) {
        process_JvmtiExport(CHECK);
#endif // INCLUDE_JVMTI
      } else {
        report_error("unknown command");
      }
    } else {
      report_error("unknown command");
    }
    if (!had_error() && *_bufptr != '\0') {
      report_error("line not properly terminated");
    }
  }

  // validation of comp_level
  bool is_valid_comp_level(int comp_level) {
    const int msg_len = 256;
    char* msg = nullptr;
    if (!is_compile(comp_level)) {
      msg = NEW_RESOURCE_ARRAY(char, msg_len);
      jio_snprintf(msg, msg_len, "%d isn't compilation level", comp_level);
    } else if (is_c1_compile(comp_level) && !CompilerConfig::is_c1_enabled()) {
      msg = NEW_RESOURCE_ARRAY(char, msg_len);
      jio_snprintf(msg, msg_len, "compilation level %d requires C1", comp_level);
    } else if (is_c2_compile(comp_level) && !CompilerConfig::is_c2_enabled()) {
      msg = NEW_RESOURCE_ARRAY(char, msg_len);
      jio_snprintf(msg, msg_len, "compilation level %d requires C2", comp_level);
    }
    if (msg != nullptr) {
      report_error(msg);
      return false;
    }
    return true;
  }

  // compile <klass> <name> <signature> <entry_bci> <comp_level> inline <count> (<depth> <bci> <klass> <name> <signature>)*
  void* process_inline(ciMethod* imethod, Method* m, int entry_bci, int comp_level, TRAPS) {
    _imethod    = m;
    _iklass     = imethod->holder();
    _entry_bci  = entry_bci;
    _comp_level = comp_level;
    int line_no = 1;
    int c = getc(_stream);
    while(c != EOF) {
      c = get_line(c);
      process_command(true, CHECK_NULL);
      if (had_error()) {
        tty->print_cr("Error while parsing line %d: %s\n", line_no, _error_message);
        tty->print_cr("%s", _buffer);
        return nullptr;
      }
      if (_ci_inline_records != nullptr && _ci_inline_records->length() > 0) {
        // Found inlining record for the requested method.
        return _ci_inline_records;
      }
      line_no++;
    }
    return nullptr;
  }

  // compile <klass> <name> <signature> <entry_bci> <comp_level> inline <count> (<depth> <bci> <inline_late> <klass> <name> <signature>)*
  void process_compile(TRAPS) {
    Method* method = parse_method(CHECK);
    if (had_error()) return;
    int entry_bci = parse_int("entry_bci");
    int comp_level = parse_int("comp_level");
    if (!is_valid_comp_level(comp_level)) {
      return;
    }
    if (_imethod != nullptr) {
      // Replay Inlining
      if (entry_bci != _entry_bci || comp_level != _comp_level) {
        return;
      }
      const char* iklass_name  = _imethod->method_holder()->name()->as_utf8();
      const char* imethod_name = _imethod->name()->as_utf8();
      const char* isignature   = _imethod->signature()->as_utf8();
      const char* klass_name   = method->method_holder()->name()->as_utf8();
      const char* method_name  = method->name()->as_utf8();
      const char* signature    = method->signature()->as_utf8();
      if (strcmp(iklass_name,  klass_name)  != 0 ||
          strcmp(imethod_name, method_name) != 0 ||
          strcmp(isignature,   signature)   != 0) {
        return;
      }
    }
    int inline_count = 0;
    if (parse_tag_and_count("inline", inline_count)) {
      // Record inlining data
      _ci_inline_records = new GrowableArray<ciInlineRecord*>();
      for (int i = 0; i < inline_count; i++) {
        int depth = parse_int("inline_depth");
        int bci = parse_int("inline_bci");
        if (had_error()) {
          break;
        }
        int inline_late = 0;
        if (_version >= 2) {
          inline_late = parse_int("inline_late");
          if (had_error()) {
              break;
          }
        }

        Method* inl_method = parse_method(CHECK);
        if (had_error()) {
          break;
        }
        new_ciInlineRecord(inl_method, bci, depth, inline_late);
      }
    }
    if (_imethod != nullptr) {
      return; // Replay Inlining
    }
    InstanceKlass* ik = method->method_holder();
    ik->initialize(THREAD);
    if (HAS_PENDING_EXCEPTION) {
      oop throwable = PENDING_EXCEPTION;
      java_lang_Throwable::print(throwable, tty);
      tty->cr();
      if (ReplayIgnoreInitErrors) {
        CLEAR_PENDING_EXCEPTION;
        ik->set_init_state(InstanceKlass::fully_initialized);
      } else {
        return;
      }
    }
    // Make sure the existence of a prior compile doesn't stop this one
    CompiledMethod* nm = (entry_bci != InvocationEntryBci) ? method->lookup_osr_nmethod_for(entry_bci, comp_level, true) : method->code();
    if (nm != nullptr) {
      nm->make_not_entrant();
    }
    replay_state = this;
    CompileBroker::compile_method(methodHandle(THREAD, method), entry_bci, comp_level,
                                  methodHandle(), 0, CompileTask::Reason_Replay, THREAD);
    replay_state = nullptr;
  }

  // ciMethod <klass> <name> <signature> <invocation_counter> <backedge_counter> <interpreter_invocation_count> <interpreter_throwout_count> <instructions_size>
  void process_ciMethod(TRAPS) {
    Method* method = parse_method(CHECK);
    if (had_error()) return;
    ciMethodRecord* rec = new_ciMethod(method);
    rec->_invocation_counter = parse_int("invocation_counter");
    rec->_backedge_counter = parse_int("backedge_counter");
    rec->_interpreter_invocation_count = parse_int("interpreter_invocation_count");
    rec->_interpreter_throwout_count = parse_int("interpreter_throwout_count");
    rec->_instructions_size = parse_int("instructions_size");
  }

  // ciMethodData <klass> <name> <signature> <state> <invocation_counter> orig <length> <byte>* data <length> <ptr>* oops <length> (<offset> <klass>)* methods <length> (<offset> <klass> <name> <signature>)*
  void process_ciMethodData(TRAPS) {
    Method* method = parse_method(CHECK);
    if (had_error()) return;
    /* just copied from Method, to build interpret data*/

    // To be properly initialized, some profiling in the MDO needs the
    // method to be rewritten (number of arguments at a call for instance)
    method->method_holder()->link_class(CHECK);
    assert(method->method_data() == nullptr, "Should only be initialized once");
    method->build_profiling_method_data(methodHandle(THREAD, method), CHECK);

    // collect and record all the needed information for later
    ciMethodDataRecord* rec = new_ciMethodData(method);
    rec->_state = parse_int("state");
    if (_version < 1) {
      parse_int("current_mileage");
    } else {
      rec->_invocation_counter = parse_int("invocation_counter");
    }

    rec->_orig_data = parse_data("orig", rec->_orig_data_length);
    if (rec->_orig_data == nullptr) {
      return;
    }
    rec->_data = parse_intptr_data("data", rec->_data_length);
    if (rec->_data == nullptr) {
      return;
    }
    if (!parse_tag_and_count("oops", rec->_classes_length)) {
      return;
    }
    rec->_classes = NEW_RESOURCE_ARRAY(Klass*, rec->_classes_length);
    rec->_classes_offsets = NEW_RESOURCE_ARRAY(int, rec->_classes_length);
    for (int i = 0; i < rec->_classes_length; i++) {
      int offset = parse_int("offset");
      if (had_error()) {
        return;
      }
      Klass* k = parse_klass(CHECK);
      rec->_classes_offsets[i] = offset;
      rec->_classes[i] = k;
    }

    if (!parse_tag_and_count("methods", rec->_methods_length)) {
      return;
    }
    rec->_methods = NEW_RESOURCE_ARRAY(Method*, rec->_methods_length);
    rec->_methods_offsets = NEW_RESOURCE_ARRAY(int, rec->_methods_length);
    for (int i = 0; i < rec->_methods_length; i++) {
      int offset = parse_int("offset");
      if (had_error()) {
        return;
      }
      Method* m = parse_method(CHECK);
      rec->_methods_offsets[i] = offset;
      rec->_methods[i] = m;
    }
  }

  // instanceKlass <name>
  // instanceKlass <constant pool ref> # <original hidden class name>
  //
  // Loads and initializes the klass 'name'.  This can be used to
  // create particular class loading environments
  void process_instanceKlass(TRAPS) {
    // just load the referenced class
    Klass* k = parse_klass(CHECK);

    if (_version >= 1) {
      if (!_protection_domain_initialized && k != nullptr) {
        assert(_protection_domain() == nullptr, "must be uninitialized");
        // The first entry is the holder class of the method for which a replay compilation is requested.
        // Use the same protection domain to load all subsequent classes in order to resolve all classes
        // in signatures of inlinees. This ensures that inlining can be done as stated in the replay file.
        _protection_domain = Handle(_thread, k->protection_domain());
      }

      _protection_domain_initialized = true;
    }

    if (k == nullptr) {
      return;
    }
    const char* comment = parse_string();
    bool is_comment = comment != nullptr && strcmp(comment, "#") == 0;
    if (k->is_hidden() != is_comment) {
      report_error("hidden class with comment expected");
      return;
    }
    // comment, print or ignore
    if (is_comment) {
      if (Verbose) {
        const char* hidden = parse_string();
        tty->print_cr("Found %s for %s", k->name()->as_quoted_ascii(), hidden);
      }
      skip_remaining();
    }
  }

  // ciInstanceKlass <name> <is_linked> <is_initialized> <length> tag*
  //
  // Load the klass 'name' and link or initialize it.  Verify that the
  // constant pool is the same length as 'length' and make sure the
  // constant pool tags are in the same state.
  void process_ciInstanceKlass(TRAPS) {
    InstanceKlass* k = (InstanceKlass*)parse_klass(CHECK);
    if (k == nullptr) {
      skip_remaining();
      return;
    }
    int is_linked = parse_int("is_linked");
    int is_initialized = parse_int("is_initialized");
    int length = parse_int("length");
    if (is_initialized) {
      k->initialize(THREAD);
      if (HAS_PENDING_EXCEPTION) {
        oop throwable = PENDING_EXCEPTION;
        java_lang_Throwable::print(throwable, tty);
        tty->cr();
        if (ReplayIgnoreInitErrors) {
          CLEAR_PENDING_EXCEPTION;
          k->set_init_state(InstanceKlass::fully_initialized);
        } else {
          return;
        }
      }
    } else if (is_linked) {
      k->link_class(CHECK);
    }
    new_ciInstanceKlass(k);
    ConstantPool* cp = k->constants();
    if (length != cp->length()) {
      report_error("constant pool length mismatch: wrong class files?");
      return;
    }

    int parsed_two_word = 0;
    for (int i = 1; i < length; i++) {
      int tag = parse_int("tag");
      if (had_error()) {
        return;
      }
      switch (cp->tag_at(i).value()) {
        case JVM_CONSTANT_UnresolvedClass: {
          if (tag == JVM_CONSTANT_Class) {
            tty->print_cr("Resolving klass %s at %d", cp->klass_name_at(i)->as_utf8(), i);
            Klass* k = cp->klass_at(i, CHECK);
          }
          break;
        }
        case JVM_CONSTANT_Long:
        case JVM_CONSTANT_Double:
          parsed_two_word = i + 1;

        case JVM_CONSTANT_ClassIndex:
        case JVM_CONSTANT_StringIndex:
        case JVM_CONSTANT_String:
        case JVM_CONSTANT_UnresolvedClassInError:
        case JVM_CONSTANT_Fieldref:
        case JVM_CONSTANT_Methodref:
        case JVM_CONSTANT_InterfaceMethodref:
        case JVM_CONSTANT_NameAndType:
        case JVM_CONSTANT_Utf8:
        case JVM_CONSTANT_Integer:
        case JVM_CONSTANT_Float:
        case JVM_CONSTANT_MethodHandle:
        case JVM_CONSTANT_MethodType:
        case JVM_CONSTANT_Dynamic:
        case JVM_CONSTANT_InvokeDynamic:
          if (tag != cp->tag_at(i).value()) {
            report_error("tag mismatch: wrong class files?");
            return;
          }
          break;

        case JVM_CONSTANT_Class:
          if (tag == JVM_CONSTANT_UnresolvedClass) {
            Klass* k = cp->klass_at(i, CHECK);
            tty->print_cr("Warning: entry was unresolved in the replay data: %s", k->name()->as_utf8());
          } else if (tag != JVM_CONSTANT_Class) {
            report_error("Unexpected tag");
            return;
          }
          break;

        case 0:
          if (parsed_two_word == i) continue;

        default:
          fatal("Unexpected tag: %d", cp->tag_at(i).value());
          break;
      }

    }
  }

  // staticfield <klass> <name> <signature> <value>
  //
  // Initialize a class and fill in the value for a static field.
  // This is useful when the compile was dependent on the value of
  // static fields but it's impossible to properly rerun the static
  // initializer.
  void process_staticfield(TRAPS) {
    InstanceKlass* k = (InstanceKlass *)parse_klass(CHECK);

    if (k == nullptr || ReplaySuppressInitializers == 0 ||
        (ReplaySuppressInitializers == 2 && k->class_loader() == nullptr)) {
      skip_remaining();
      return;
    }

    assert(k->is_initialized(), "must be");

    const char* field_name = parse_escaped_string();
    const char* field_signature = parse_string();
    fieldDescriptor fd;
    Symbol* name = SymbolTable::new_symbol(field_name);
    Symbol* sig = SymbolTable::new_symbol(field_signature);
    if (!k->find_local_field(name, sig, &fd) ||
        !fd.is_static() ||
        fd.has_initial_value()) {
      report_error(field_name);
      return;
    }

    oop java_mirror = k->java_mirror();
    if (field_signature[0] == JVM_SIGNATURE_ARRAY) {
      int length = parse_int("array length");
      oop value = nullptr;

      if (field_signature[1] == JVM_SIGNATURE_ARRAY) {
        // multi dimensional array
        ArrayKlass* kelem = (ArrayKlass *)parse_klass(CHECK);
        if (kelem == nullptr) {
          return;
        }
        int rank = 0;
        while (field_signature[rank] == JVM_SIGNATURE_ARRAY) {
          rank++;
        }
        jint* dims = NEW_RESOURCE_ARRAY(jint, rank);
        dims[0] = length;
        for (int i = 1; i < rank; i++) {
          dims[i] = 1; // These aren't relevant to the compiler
        }
        value = kelem->multi_allocate(rank, dims, CHECK);
      } else {
        if (strcmp(field_signature, "[B") == 0) {
          value = oopFactory::new_byteArray(length, CHECK);
        } else if (strcmp(field_signature, "[Z") == 0) {
          value = oopFactory::new_boolArray(length, CHECK);
        } else if (strcmp(field_signature, "[C") == 0) {
          value = oopFactory::new_charArray(length, CHECK);
        } else if (strcmp(field_signature, "[S") == 0) {
          value = oopFactory::new_shortArray(length, CHECK);
        } else if (strcmp(field_signature, "[F") == 0) {
          value = oopFactory::new_floatArray(length, CHECK);
        } else if (strcmp(field_signature, "[D") == 0) {
          value = oopFactory::new_doubleArray(length, CHECK);
        } else if (strcmp(field_signature, "[I") == 0) {
          value = oopFactory::new_intArray(length, CHECK);
        } else if (strcmp(field_signature, "[J") == 0) {
          value = oopFactory::new_longArray(length, CHECK);
        } else if (field_signature[0] == JVM_SIGNATURE_ARRAY &&
                   field_signature[1] == JVM_SIGNATURE_CLASS) {
          parse_klass(CHECK); // eat up the array class name
          Klass* kelem = resolve_klass(field_signature + 1, CHECK);
          value = oopFactory::new_objArray(kelem, length, CHECK);
        } else {
          report_error("unhandled array staticfield");
        }
      }
      java_mirror->obj_field_put(fd.offset(), value);
    } else {
      const char* string_value = parse_escaped_string();
      if (strcmp(field_signature, "I") == 0) {
        int value = atoi(string_value);
        java_mirror->int_field_put(fd.offset(), value);
      } else if (strcmp(field_signature, "B") == 0) {
        int value = atoi(string_value);
        java_mirror->byte_field_put(fd.offset(), value);
      } else if (strcmp(field_signature, "C") == 0) {
        int value = atoi(string_value);
        java_mirror->char_field_put(fd.offset(), value);
      } else if (strcmp(field_signature, "S") == 0) {
        int value = atoi(string_value);
        java_mirror->short_field_put(fd.offset(), value);
      } else if (strcmp(field_signature, "Z") == 0) {
        int value = atoi(string_value);
        java_mirror->bool_field_put(fd.offset(), value);
      } else if (strcmp(field_signature, "J") == 0) {
        jlong value;
        if (sscanf(string_value, JLONG_FORMAT, &value) != 1) {
          fprintf(stderr, "Error parsing long: %s\n", string_value);
          return;
        }
        java_mirror->long_field_put(fd.offset(), value);
      } else if (strcmp(field_signature, "F") == 0) {
        float value = atof(string_value);
        java_mirror->float_field_put(fd.offset(), value);
      } else if (strcmp(field_signature, "D") == 0) {
        double value = atof(string_value);
        java_mirror->double_field_put(fd.offset(), value);
      } else if (strcmp(field_signature, "Ljava/lang/String;") == 0) {
        Handle value = java_lang_String::create_from_str(string_value, CHECK);
        java_mirror->obj_field_put(fd.offset(), value());
      } else if (field_signature[0] == JVM_SIGNATURE_CLASS) {
        Klass* k = resolve_klass(string_value, CHECK);
        oop value = InstanceKlass::cast(k)->allocate_instance(CHECK);
        java_mirror->obj_field_put(fd.offset(), value);
      } else {
        report_error("unhandled staticfield");
      }
    }
  }

#if INCLUDE_JVMTI
  // JvmtiExport <field> <value>
  void process_JvmtiExport(TRAPS) {
    const char* field = parse_string();
    bool value = parse_int("JvmtiExport flag") != 0;
    if (strcmp(field, "can_access_local_variables") == 0) {
      JvmtiExport::set_can_access_local_variables(value);
    } else if (strcmp(field, "can_hotswap_or_post_breakpoint") == 0) {
      JvmtiExport::set_can_hotswap_or_post_breakpoint(value);
    } else if (strcmp(field, "can_post_on_exceptions") == 0) {
      JvmtiExport::set_can_post_on_exceptions(value);
    } else {
      report_error("Unrecognized JvmtiExport directive");
    }
  }
#endif // INCLUDE_JVMTI

  // Create and initialize a record for a ciMethod
  ciMethodRecord* new_ciMethod(Method* method) {
    ciMethodRecord* rec = NEW_RESOURCE_OBJ(ciMethodRecord);
    rec->_klass_name =  method->method_holder()->name()->as_utf8();
    rec->_method_name = method->name()->as_utf8();
    rec->_signature = method->signature()->as_utf8();
    _ci_method_records.append(rec);
    return rec;
  }

  // Lookup data for a ciMethod
  ciMethodRecord* find_ciMethodRecord(Method* method) {
    const char* klass_name =  method->method_holder()->name()->as_utf8();
    const char* method_name = method->name()->as_utf8();
    const char* signature = method->signature()->as_utf8();
    for (int i = 0; i < _ci_method_records.length(); i++) {
      ciMethodRecord* rec = _ci_method_records.at(i);
      if (strcmp(rec->_klass_name, klass_name) == 0 &&
          strcmp(rec->_method_name, method_name) == 0 &&
          strcmp(rec->_signature, signature) == 0) {
        return rec;
      }
    }
    return nullptr;
  }

  // Create and initialize a record for a ciInstanceKlass which was present at replay dump time.
  void new_ciInstanceKlass(const InstanceKlass* klass) {
    ciInstanceKlassRecord* rec = NEW_RESOURCE_OBJ(ciInstanceKlassRecord);
    rec->_klass = klass;
    oop java_mirror = klass->java_mirror();
    Handle h_java_mirror(_thread, java_mirror);
    rec->_java_mirror = JNIHandles::make_global(h_java_mirror);
    _ci_instance_klass_records.append(rec);
  }

  // Check if a ciInstanceKlass was present at replay dump time for a klass.
  ciInstanceKlassRecord* find_ciInstanceKlass(const InstanceKlass* klass) {
    for (int i = 0; i < _ci_instance_klass_records.length(); i++) {
      ciInstanceKlassRecord* rec = _ci_instance_klass_records.at(i);
      if (klass == rec->_klass) {
        // ciInstanceKlass for this klass was resolved.
        return rec;
      }
    }
    return nullptr;
  }

  // Create and initialize a record for a ciMethodData
  ciMethodDataRecord* new_ciMethodData(Method* method) {
    ciMethodDataRecord* rec = NEW_RESOURCE_OBJ(ciMethodDataRecord);
    rec->_klass_name =  method->method_holder()->name()->as_utf8();
    rec->_method_name = method->name()->as_utf8();
    rec->_signature = method->signature()->as_utf8();
    _ci_method_data_records.append(rec);
    return rec;
  }

  // Lookup data for a ciMethodData
  ciMethodDataRecord* find_ciMethodDataRecord(Method* method) {
    const char* klass_name =  method->method_holder()->name()->as_utf8();
    const char* method_name = method->name()->as_utf8();
    const char* signature = method->signature()->as_utf8();
    for (int i = 0; i < _ci_method_data_records.length(); i++) {
      ciMethodDataRecord* rec = _ci_method_data_records.at(i);
      if (strcmp(rec->_klass_name, klass_name) == 0 &&
          strcmp(rec->_method_name, method_name) == 0 &&
          strcmp(rec->_signature, signature) == 0) {
        return rec;
      }
    }
    return nullptr;
  }

  // Create and initialize a record for a ciInlineRecord
  ciInlineRecord* new_ciInlineRecord(Method* method, int bci, int depth, int inline_late) {
    ciInlineRecord* rec = NEW_RESOURCE_OBJ(ciInlineRecord);
    rec->_klass_name =  method->method_holder()->name()->as_utf8();
    rec->_method_name = method->name()->as_utf8();
    rec->_signature = method->signature()->as_utf8();
    rec->_inline_bci = bci;
    rec->_inline_depth = depth;
    rec->_inline_late = inline_late;
    _ci_inline_records->append(rec);
    return rec;
  }

  // Lookup inlining data for a ciMethod
  ciInlineRecord* find_ciInlineRecord(Method* method, int bci, int depth) {
    if (_ci_inline_records != nullptr) {
      return find_ciInlineRecord(_ci_inline_records, method, bci, depth);
    }
    return nullptr;
  }

  static ciInlineRecord* find_ciInlineRecord(GrowableArray<ciInlineRecord*>*  records,
                                      Method* method, int bci, int depth) {
    if (records != nullptr) {
      const char* klass_name  = method->method_holder()->name()->as_utf8();
      const char* method_name = method->name()->as_utf8();
      const char* signature   = method->signature()->as_utf8();
      for (int i = 0; i < records->length(); i++) {
        ciInlineRecord* rec = records->at(i);
        if ((rec->_inline_bci == bci) &&
            (rec->_inline_depth == depth) &&
            (strcmp(rec->_klass_name, klass_name) == 0) &&
            (strcmp(rec->_method_name, method_name) == 0) &&
            (strcmp(rec->_signature, signature) == 0)) {
          return rec;
        }
      }
    }
    return nullptr;
  }

  const char* error_message() {
    return _error_message;
  }

  void reset() {
    _error_message = nullptr;
    _ci_method_records.clear();
    _ci_method_data_records.clear();
  }

  // Take an ascii string contain \u#### escapes and convert it to utf8
  // in place.
  static void unescape_string(char* value) {
    char* from = value;
    char* to = value;
    while (*from != '\0') {
      if (*from != '\\') {
        *from++ = *to++;
      } else {
        switch (from[1]) {
          case 'u': {
            from += 2;
            jchar value=0;
            for (int i=0; i<4; i++) {
              char c = *from++;
              switch (c) {
                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                  value = (value << 4) + c - '0';
                  break;
                case 'a': case 'b': case 'c':
                case 'd': case 'e': case 'f':
                  value = (value << 4) + 10 + c - 'a';
                  break;
                case 'A': case 'B': case 'C':
                case 'D': case 'E': case 'F':
                  value = (value << 4) + 10 + c - 'A';
                  break;
                default:
                  ShouldNotReachHere();
              }
            }
            UNICODE::convert_to_utf8(&value, 1, to);
            to++;
            break;
          }
          case 't': *to++ = '\t'; from += 2; break;
          case 'n': *to++ = '\n'; from += 2; break;
          case 'r': *to++ = '\r'; from += 2; break;
          case 'f': *to++ = '\f'; from += 2; break;
          default:
            ShouldNotReachHere();
        }
      }
    }
    *from = *to;
  }
};

void ciReplay::replay(TRAPS) {
  int exit_code = replay_impl(THREAD);

  Threads::destroy_vm();

  vm_exit(exit_code);
}

bool ciReplay::no_replay_state() {
  return replay_state == nullptr;
}

void* ciReplay::load_inline_data(ciMethod* method, int entry_bci, int comp_level) {
  if (FLAG_IS_DEFAULT(InlineDataFile)) {
    tty->print_cr("ERROR: no inline replay data file specified (use -XX:InlineDataFile=inline_pid12345.txt).");
    return nullptr;
  }

  VM_ENTRY_MARK;
  // Load and parse the replay data
  CompileReplay rp(InlineDataFile, THREAD);
  if (!rp.can_replay()) {
    tty->print_cr("ciReplay: !rp.can_replay()");
    return nullptr;
  }
  void* data = rp.process_inline(method, method->get_Method(), entry_bci, comp_level, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    Handle throwable(THREAD, PENDING_EXCEPTION);
    CLEAR_PENDING_EXCEPTION;
    java_lang_Throwable::print_stack_trace(throwable, tty);
    tty->cr();
    return nullptr;
  }

  if (rp.had_error()) {
    tty->print_cr("ciReplay: Failed on %s", rp.error_message());
    return nullptr;
  }
  return data;
}

int ciReplay::replay_impl(TRAPS) {
  HandleMark hm(THREAD);
  ResourceMark rm(THREAD);

  if (ReplaySuppressInitializers > 2) {
    // ReplaySuppressInitializers > 2 means that we want to allow
    // normal VM bootstrap but once we get into the replay itself
    // don't allow any initializers to be run.
    ReplaySuppressInitializers = 1;
  }

  if (FLAG_IS_DEFAULT(ReplayDataFile)) {
    tty->print_cr("ERROR: no compiler replay data file specified (use -XX:ReplayDataFile=replay_pid12345.txt).");
    return 1;
  }

  // Load and parse the replay data
  CompileReplay rp(ReplayDataFile, THREAD);
  int exit_code = 0;
  if (rp.can_replay()) {
    rp.process(THREAD);
  } else {
    exit_code = 1;
    return exit_code;
  }

  if (HAS_PENDING_EXCEPTION) {
    Handle throwable(THREAD, PENDING_EXCEPTION);
    CLEAR_PENDING_EXCEPTION;
    java_lang_Throwable::print_stack_trace(throwable, tty);
    tty->cr();
    exit_code = 2;
  }

  if (rp.had_error()) {
    tty->print_cr("Failed on %s", rp.error_message());
    exit_code = 1;
  }
  return exit_code;
}

void ciReplay::initialize(ciMethodData* m) {
  if (no_replay_state()) {
    return;
  }

  ASSERT_IN_VM;
  ResourceMark rm;

  Method* method = m->get_MethodData()->method();
  ciMethodDataRecord* rec = replay_state->find_ciMethodDataRecord(method);
  if (rec == nullptr) {
    // This indicates some mismatch with the original environment and
    // the replay environment though it's not always enough to
    // interfere with reproducing a bug
    tty->print_cr("Warning: requesting ciMethodData record for method with no data: ");
    method->print_name(tty);
    tty->cr();
  } else {
    m->_state = rec->_state;
    m->_invocation_counter = rec->_invocation_counter;
    if (rec->_data_length != 0) {
      assert(m->_data_size + m->_extra_data_size == rec->_data_length * (int)sizeof(rec->_data[0]) ||
             m->_data_size == rec->_data_length * (int)sizeof(rec->_data[0]), "must agree");

      // Write the correct ciObjects back into the profile data
      ciEnv* env = ciEnv::current();
      for (int i = 0; i < rec->_classes_length; i++) {
        Klass *k = rec->_classes[i];
        // In case this class pointer is is tagged, preserve the tag bits
        intptr_t status = 0;
        if (k != nullptr) {
          status = ciTypeEntries::with_status(env->get_metadata(k)->as_klass(), rec->_data[rec->_classes_offsets[i]]);
        }
        rec->_data[rec->_classes_offsets[i]] = status;
      }
      for (int i = 0; i < rec->_methods_length; i++) {
        Method *m = rec->_methods[i];
        *(ciMetadata**)(rec->_data + rec->_methods_offsets[i]) =
          env->get_metadata(m);
      }
      // Copy the updated profile data into place as intptr_ts
#ifdef _LP64
      Copy::conjoint_jlongs_atomic((jlong *)rec->_data, (jlong *)m->_data, rec->_data_length);
#else
      Copy::conjoint_jints_atomic((jint *)rec->_data, (jint *)m->_data, rec->_data_length);
#endif
    }

    // copy in the original header
    Copy::conjoint_jbytes(rec->_orig_data, (char*)&m->_orig, rec->_orig_data_length);
  }
}


bool ciReplay::should_not_inline(ciMethod* method) {
  if (no_replay_state()) {
    return false;
  }
  VM_ENTRY_MARK;
  // ciMethod without a record shouldn't be inlined.
  return replay_state->find_ciMethodRecord(method->get_Method()) == nullptr;
}

bool ciReplay::should_inline(void* data, ciMethod* method, int bci, int inline_depth, bool& should_delay) {
  if (data != nullptr) {
    GrowableArray<ciInlineRecord*>* records = (GrowableArray<ciInlineRecord*>*)data;
    VM_ENTRY_MARK;
    // Inline record are ordered by bci and depth.
    ciInlineRecord* record = CompileReplay::find_ciInlineRecord(records, method->get_Method(), bci, inline_depth);
    if (record == nullptr) {
      return false;
    }
    should_delay = record->_inline_late;
    return true;
  } else if (replay_state != nullptr) {
    VM_ENTRY_MARK;
    // Inline record are ordered by bci and depth.
    ciInlineRecord* record = replay_state->find_ciInlineRecord(method->get_Method(), bci, inline_depth);
    if (record == nullptr) {
      return false;
    }
    should_delay = record->_inline_late;
    return true;
  }
  return false;
}

bool ciReplay::should_not_inline(void* data, ciMethod* method, int bci, int inline_depth) {
  if (data != nullptr) {
    GrowableArray<ciInlineRecord*>* records = (GrowableArray<ciInlineRecord*>*)data;
    VM_ENTRY_MARK;
    // Inline record are ordered by bci and depth.
    return CompileReplay::find_ciInlineRecord(records, method->get_Method(), bci, inline_depth) == nullptr;
  } else if (replay_state != nullptr) {
    VM_ENTRY_MARK;
    // Inline record are ordered by bci and depth.
    return replay_state->find_ciInlineRecord(method->get_Method(), bci, inline_depth) == nullptr;
  }
  return false;
}

void ciReplay::initialize(ciMethod* m) {
  if (no_replay_state()) {
    return;
  }

  ASSERT_IN_VM;
  ResourceMark rm;

  Method* method = m->get_Method();
  ciMethodRecord* rec = replay_state->find_ciMethodRecord(method);
  if (rec == nullptr) {
    // This indicates some mismatch with the original environment and
    // the replay environment though it's not always enough to
    // interfere with reproducing a bug
    tty->print_cr("Warning: requesting ciMethod record for method with no data: ");
    method->print_name(tty);
    tty->cr();
  } else {
    EXCEPTION_CONTEXT;
    // m->_instructions_size = rec->_instructions_size;
    m->_inline_instructions_size = -1;
    m->_interpreter_invocation_count = rec->_interpreter_invocation_count;
    m->_interpreter_throwout_count = rec->_interpreter_throwout_count;
    MethodCounters* mcs = method->get_method_counters(CHECK_AND_CLEAR);
    guarantee(mcs != nullptr, "method counters allocation failed");
    mcs->invocation_counter()->_counter = rec->_invocation_counter;
    mcs->backedge_counter()->_counter = rec->_backedge_counter;
  }
}

void ciReplay::initialize(ciInstanceKlass* ci_ik, InstanceKlass* ik) {
  assert(!no_replay_state(), "must have replay state");

  ASSERT_IN_VM;
  ciInstanceKlassRecord* rec = replay_state->find_ciInstanceKlass(ik);
  assert(rec != nullptr, "ciInstanceKlass must be whitelisted");
  ci_ik->_java_mirror = CURRENT_ENV->get_instance(JNIHandles::resolve(rec->_java_mirror));
}

bool ciReplay::is_loaded(Method* method) {
  if (no_replay_state()) {
    return true;
  }

  ASSERT_IN_VM;
  ResourceMark rm;

  ciMethodRecord* rec = replay_state->find_ciMethodRecord(method);
  return rec != nullptr;
}

bool ciReplay::is_klass_unresolved(const InstanceKlass* klass) {
  if (no_replay_state()) {
    return false;
  }

  // Check if klass is found on whitelist.
  ciInstanceKlassRecord* rec = replay_state->find_ciInstanceKlass(klass);
  return rec == nullptr;
}

oop ciReplay::obj_field(oop obj, Symbol* name) {
  InstanceKlass* ik = InstanceKlass::cast(obj->klass());

  do {
    if (!ik->has_nonstatic_fields()) {
      ik = ik->java_super();
      continue;
    }

    for (JavaFieldStream fs(ik); !fs.done(); fs.next()) {
      if (fs.access_flags().is_static()) {
        continue;
      }
      if (fs.name() == name) {
        int offset = fs.offset();
#ifdef ASSERT
        fieldDescriptor fd = fs.field_descriptor();
        assert(fd.offset() == ik->field_offset(fd.index()), "!");
#endif
        oop f = obj->obj_field(offset);
        return f;
      }
    }

    ik = ik->java_super();
  } while (ik != nullptr);
  return nullptr;
}

oop ciReplay::obj_field(oop obj, const char *name) {
  Symbol* fname = SymbolTable::probe(name, (int)strlen(name));
  if (fname == nullptr) {
    return nullptr;
  }
  return obj_field(obj, fname);
}
