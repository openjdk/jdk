/* Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciUtilities.hpp"
#include "compiler/compileBroker.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/copy.hpp"
#include "utilities/macros.hpp"

#ifndef PRODUCT

// ciReplay

typedef struct _ciMethodDataRecord {
  const char* klass;
  const char* method;
  const char* signature;
  int state;
  int current_mileage;
  intptr_t* data;
  int data_length;
  char* orig_data;
  int orig_data_length;
  int oops_length;
  jobject* oops_handles;
  int* oops_offsets;
} ciMethodDataRecord;

typedef struct _ciMethodRecord {
  const char* klass;
  const char* method;
  const char* signature;
  int instructions_size;
  int interpreter_invocation_count;
  int interpreter_throwout_count;
  int invocation_counter;
  int backedge_counter;
} ciMethodRecord;

class CompileReplay;
static CompileReplay* replay_state;

class CompileReplay : public StackObj {
 private:
  FILE*   stream;
  Thread* thread;
  Handle  protection_domain;
  Handle  loader;

  GrowableArray<ciMethodRecord*>     ci_method_records;
  GrowableArray<ciMethodDataRecord*> ci_method_data_records;

  const char* _error_message;

  char* bufptr;
  char* buffer;
  int   buffer_length;
  int   buffer_end;
  int   line_no;

 public:
  CompileReplay(const char* filename, TRAPS) {
    thread = THREAD;
    loader = Handle(thread, SystemDictionary::java_system_loader());
    stream = fopen(filename, "rt");
    if (stream == NULL) {
      fprintf(stderr, "ERROR: Can't open replay file %s\n", filename);
    }
    buffer_length = 32;
    buffer = NEW_RESOURCE_ARRAY(char, buffer_length);
    _error_message = NULL;

    test();
  }

  ~CompileReplay() {
    if (stream != NULL) fclose(stream);
  }

  void test() {
    strcpy(buffer, "1 2 foo 4 bar 0x9 \"this is it\"");
    bufptr = buffer;
    assert(parse_int("test") == 1, "what");
    assert(parse_int("test") == 2, "what");
    assert(strcmp(parse_string(), "foo") == 0, "what");
    assert(parse_int("test") == 4, "what");
    assert(strcmp(parse_string(), "bar") == 0, "what");
    assert(parse_intptr_t("test") == 9, "what");
    assert(strcmp(parse_quoted_string(), "this is it") == 0, "what");
  }

  bool had_error() {
    return _error_message != NULL || thread->has_pending_exception();
  }

  bool can_replay() {
    return !(stream == NULL || had_error());
  }

  void report_error(const char* msg) {
    _error_message = msg;
    // Restore the buffer contents for error reporting
    for (int i = 0; i < buffer_end; i++) {
      if (buffer[i] == '\0') buffer[i] = ' ';
    }
  }

  int parse_int(const char* label) {
    if (had_error()) {
      return 0;
    }

    int v = 0;
    int read;
    if (sscanf(bufptr, "%i%n", &v, &read) != 1) {
      report_error(label);
    } else {
      bufptr += read;
    }
    return v;
  }

  intptr_t parse_intptr_t(const char* label) {
    if (had_error()) {
      return 0;
    }

    intptr_t v = 0;
    int read;
    if (sscanf(bufptr, INTPTR_FORMAT "%n", &v, &read) != 1) {
      report_error(label);
    } else {
      bufptr += read;
    }
    return v;
  }

  void skip_ws() {
    // Skip any leading whitespace
    while (*bufptr == ' ' || *bufptr == '\t') {
      bufptr++;
    }
  }


  char* scan_and_terminate(char delim) {
    char* str = bufptr;
    while (*bufptr != delim && *bufptr != '\0') {
      bufptr++;
    }
    if (*bufptr != '\0') {
      *bufptr++ = '\0';
    }
    if (bufptr == str) {
      // nothing here
      return NULL;
    }
    return str;
  }

  char* parse_string() {
    if (had_error()) return NULL;

    skip_ws();
    return scan_and_terminate(' ');
  }

  char* parse_quoted_string() {
    if (had_error()) return NULL;

    skip_ws();

    if (*bufptr == '"') {
      bufptr++;
      return scan_and_terminate('"');
    } else {
      return scan_and_terminate(' ');
    }
  }

  const char* parse_escaped_string() {
    char* result = parse_quoted_string();
    if (result != NULL) {
      unescape_string(result);
    }
    return result;
  }

  // Look for the tag 'tag' followed by an
  bool parse_tag_and_count(const char* tag, int& length) {
    const char* t = parse_string();
    if (t == NULL) {
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
    if (!parse_tag_and_count(tag, length)) {
      return NULL;
    }

    char * result = NEW_RESOURCE_ARRAY(char, length);
    for (int i = 0; i < length; i++) {
      int val = parse_int("data");
      result[i] = val;
    }
    return result;
  }

  // Parse a standard chunk of data emitted as:
  //   'tag' <length> # # ...
  // Where each # is an intptr_t item
  intptr_t* parse_intptr_data(const char* tag, int& length) {
    if (!parse_tag_and_count(tag, length)) {
      return NULL;
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
  Symbol* parse_symbol(TRAPS) {
    const char* str = parse_escaped_string();
    if (str != NULL) {
      Symbol* sym = SymbolTable::lookup(str, (int)strlen(str), CHECK_NULL);
      return sym;
    }
    return NULL;
  }

  // Parse a valid klass name and look it up
  Klass* parse_klass(TRAPS) {
    const char* str = parse_escaped_string();
    Symbol* klass_name = SymbolTable::lookup(str, (int)strlen(str), CHECK_NULL);
    if (klass_name != NULL) {
      Klass* k = SystemDictionary::resolve_or_fail(klass_name, loader, protection_domain, true, THREAD);
      if (HAS_PENDING_EXCEPTION) {
        oop throwable = PENDING_EXCEPTION;
        java_lang_Throwable::print(throwable, tty);
        tty->cr();
        report_error(str);
        return NULL;
      }
      return k;
    }
    return NULL;
  }

  // Lookup a klass
  Klass* resolve_klass(const char* klass, TRAPS) {
    Symbol* klass_name = SymbolTable::lookup(klass, (int)strlen(klass), CHECK_NULL);
    return SystemDictionary::resolve_or_fail(klass_name, loader, protection_domain, true, CHECK_NULL);
  }

  // Parse the standard tuple of <klass> <name> <signature>
  Method* parse_method(TRAPS) {
    InstanceKlass* k = (InstanceKlass*)parse_klass(CHECK_NULL);
    Symbol* method_name = parse_symbol(CHECK_NULL);
    Symbol* method_signature = parse_symbol(CHECK_NULL);
    Method* m = k->find_method(method_name, method_signature);
    if (m == NULL) {
      report_error("Can't find method");
    }
    return m;
  }

  // Process each line of the replay file executing each command until
  // the file ends.
  void process(TRAPS) {
    line_no = 1;
    int pos = 0;
    int c = getc(stream);
    while(c != EOF) {
      if (pos + 1 >= buffer_length) {
        int newl = buffer_length * 2;
        char* newb = NEW_RESOURCE_ARRAY(char, newl);
        memcpy(newb, buffer, pos);
        buffer = newb;
        buffer_length = newl;
      }
      if (c == '\n') {
        // null terminate it, reset the pointer and process the line
        buffer[pos] = '\0';
        buffer_end = pos++;
        bufptr = buffer;
        process_command(CHECK);
        if (had_error()) {
          tty->print_cr("Error while parsing line %d: %s\n", line_no, _error_message);
          tty->print_cr("%s", buffer);
          return;
        }
        pos = 0;
        buffer_end = 0;
        line_no++;
      } else if (c == '\r') {
        // skip LF
      } else {
        buffer[pos++] = c;
      }
      c = getc(stream);
    }
  }

  void process_command(TRAPS) {
    char* cmd = parse_string();
    if (cmd == NULL) {
      return;
    }
    if (strcmp("#", cmd) == 0) {
      // ignore
    } else if (strcmp("compile", cmd) == 0) {
      process_compile(CHECK);
    } else if (strcmp("ciMethod", cmd) == 0) {
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
  }

  // validation of comp_level
  bool is_valid_comp_level(int comp_level) {
    const int msg_len = 256;
    char* msg = NULL;
    if (!is_compile(comp_level)) {
      msg = NEW_RESOURCE_ARRAY(char, msg_len);
      jio_snprintf(msg, msg_len, "%d isn't compilation level", comp_level);
    } else if (!TieredCompilation && (comp_level != CompLevel_highest_tier)) {
      msg = NEW_RESOURCE_ARRAY(char, msg_len);
      switch (comp_level) {
        case CompLevel_simple:
          jio_snprintf(msg, msg_len, "compilation level %d requires Client VM or TieredCompilation", comp_level);
          break;
        case CompLevel_full_optimization:
          jio_snprintf(msg, msg_len, "compilation level %d requires Server VM", comp_level);
          break;
        default:
          jio_snprintf(msg, msg_len, "compilation level %d requires TieredCompilation", comp_level);
      }
    }
    if (msg != NULL) {
      report_error(msg);
      return false;
    }
    return true;
  }

  // compile <klass> <name> <signature> <entry_bci> <comp_level>
  void process_compile(TRAPS) {
    Method* method = parse_method(CHECK);
    if (had_error()) return;
    int entry_bci = parse_int("entry_bci");
    const char* comp_level_label = "comp_level";
    int comp_level = parse_int(comp_level_label);
    // old version w/o comp_level
    if (had_error() && (error_message() == comp_level_label)) {
      comp_level = CompLevel_full_optimization;
    }
    if (!is_valid_comp_level(comp_level)) {
      return;
    }
    Klass* k = method->method_holder();
    ((InstanceKlass*)k)->initialize(THREAD);
    if (HAS_PENDING_EXCEPTION) {
      oop throwable = PENDING_EXCEPTION;
      java_lang_Throwable::print(throwable, tty);
      tty->cr();
      if (ReplayIgnoreInitErrors) {
        CLEAR_PENDING_EXCEPTION;
        ((InstanceKlass*)k)->set_init_state(InstanceKlass::fully_initialized);
      } else {
        return;
      }
    }
    // Make sure the existence of a prior compile doesn't stop this one
    nmethod* nm = (entry_bci != InvocationEntryBci) ? method->lookup_osr_nmethod_for(entry_bci, comp_level, true) : method->code();
    if (nm != NULL) {
      nm->make_not_entrant();
    }
    replay_state = this;
    CompileBroker::compile_method(method, entry_bci, comp_level,
                                  methodHandle(), 0, "replay", THREAD);
    replay_state = NULL;
    reset();
  }

  // ciMethod <klass> <name> <signature> <invocation_counter> <backedge_counter> <interpreter_invocation_count> <interpreter_throwout_count> <instructions_size>
  //
  //
  void process_ciMethod(TRAPS) {
    Method* method = parse_method(CHECK);
    if (had_error()) return;
    ciMethodRecord* rec = new_ciMethod(method);
    rec->invocation_counter = parse_int("invocation_counter");
    rec->backedge_counter = parse_int("backedge_counter");
    rec->interpreter_invocation_count = parse_int("interpreter_invocation_count");
    rec->interpreter_throwout_count = parse_int("interpreter_throwout_count");
    rec->instructions_size = parse_int("instructions_size");
  }

  // ciMethodData <klass> <name> <signature> <state> <current mileage> orig <length> # # ... data <length> # # ... oops <length>
  void process_ciMethodData(TRAPS) {
    Method* method = parse_method(CHECK);
    if (had_error()) return;
    /* jsut copied from Method, to build interpret data*/
    if (InstanceRefKlass::owns_pending_list_lock((JavaThread*)THREAD)) {
      return;
    }
    // methodOopDesc::build_interpreter_method_data(method, CHECK);
    {
      // Grab a lock here to prevent multiple
      // MethodData*s from being created.
      MutexLocker ml(MethodData_lock, THREAD);
      if (method->method_data() == NULL) {
        ClassLoaderData* loader_data = method->method_holder()->class_loader_data();
        MethodData* method_data = MethodData::allocate(loader_data, method, CHECK);
        method->set_method_data(method_data);
      }
    }

    // collect and record all the needed information for later
    ciMethodDataRecord* rec = new_ciMethodData(method);
    rec->state = parse_int("state");
    rec->current_mileage = parse_int("current_mileage");

    rec->orig_data = parse_data("orig", rec->orig_data_length);
    if (rec->orig_data == NULL) {
      return;
    }
    rec->data = parse_intptr_data("data", rec->data_length);
    if (rec->data == NULL) {
      return;
    }
    if (!parse_tag_and_count("oops", rec->oops_length)) {
      return;
    }
    rec->oops_handles = NEW_RESOURCE_ARRAY(jobject, rec->oops_length);
    rec->oops_offsets = NEW_RESOURCE_ARRAY(int, rec->oops_length);
    for (int i = 0; i < rec->oops_length; i++) {
      int offset = parse_int("offset");
      if (had_error()) {
        return;
      }
      Klass* k = parse_klass(CHECK);
      rec->oops_offsets[i] = offset;
      KlassHandle *kh = NEW_C_HEAP_OBJ(KlassHandle, mtCompiler);
      ::new ((void*)kh) KlassHandle(THREAD, k);
      rec->oops_handles[i] = (jobject)kh;
    }
  }

  // instanceKlass <name>
  //
  // Loads and initializes the klass 'name'.  This can be used to
  // create particular class loading environments
  void process_instanceKlass(TRAPS) {
    // just load the referenced class
    Klass* k = parse_klass(CHECK);
  }

  // ciInstanceKlass <name> <is_linked> <is_initialized> <length> tag # # # ...
  //
  // Load the klass 'name' and link or initialize it.  Verify that the
  // constant pool is the same length as 'length' and make sure the
  // constant pool tags are in the same state.
  void process_ciInstanceKlass(TRAPS) {
    InstanceKlass* k = (InstanceKlass *)parse_klass(CHECK);
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
            tty->print_cr("Resolving klass %s at %d", cp->unresolved_klass_at(i)->as_utf8(), i);
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
          if (tag != cp->tag_at(i).value()) {
            report_error("tag mismatch: wrong class files?");
            return;
          }
          break;

        case JVM_CONSTANT_Class:
          if (tag == JVM_CONSTANT_Class) {
          } else if (tag == JVM_CONSTANT_UnresolvedClass) {
            tty->print_cr("Warning: entry was unresolved in the replay data");
          } else {
            report_error("Unexpected tag");
            return;
          }
          break;

        case 0:
          if (parsed_two_word == i) continue;

        default:
          fatal(err_msg_res("Unexpected tag: %d", cp->tag_at(i).value()));
          break;
      }

    }
  }

  // Initialize a class and fill in the value for a static field.
  // This is useful when the compile was dependent on the value of
  // static fields but it's impossible to properly rerun the static
  // initiailizer.
  void process_staticfield(TRAPS) {
    InstanceKlass* k = (InstanceKlass *)parse_klass(CHECK);

    if (ReplaySuppressInitializers == 0 ||
        ReplaySuppressInitializers == 2 && k->class_loader() == NULL) {
      return;
    }

    assert(k->is_initialized(), "must be");

    const char* field_name = parse_escaped_string();;
    const char* field_signature = parse_string();
    fieldDescriptor fd;
    Symbol* name = SymbolTable::lookup(field_name, (int)strlen(field_name), CHECK);
    Symbol* sig = SymbolTable::lookup(field_signature, (int)strlen(field_signature), CHECK);
    if (!k->find_local_field(name, sig, &fd) ||
        !fd.is_static() ||
        fd.has_initial_value()) {
      report_error(field_name);
      return;
    }

    oop java_mirror = k->java_mirror();
    if (field_signature[0] == '[') {
      int length = parse_int("array length");
      oop value = NULL;

      if (field_signature[1] == '[') {
        // multi dimensional array
        ArrayKlass* kelem = (ArrayKlass *)parse_klass(CHECK);
        int rank = 0;
        while (field_signature[rank] == '[') {
          rank++;
        }
        int* dims = NEW_RESOURCE_ARRAY(int, rank);
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
          value = oopFactory::new_singleArray(length, CHECK);
        } else if (strcmp(field_signature, "[D") == 0) {
          value = oopFactory::new_doubleArray(length, CHECK);
        } else if (strcmp(field_signature, "[I") == 0) {
          value = oopFactory::new_intArray(length, CHECK);
        } else if (strcmp(field_signature, "[J") == 0) {
          value = oopFactory::new_longArray(length, CHECK);
        } else if (field_signature[0] == '[' && field_signature[1] == 'L') {
          KlassHandle kelem = resolve_klass(field_signature + 1, CHECK);
          value = oopFactory::new_objArray(kelem(), length, CHECK);
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
        int value = atol(string_value);
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
      } else if (field_signature[0] == 'L') {
        Symbol* klass_name = SymbolTable::lookup(field_signature, (int)strlen(field_signature), CHECK);
        KlassHandle kelem = resolve_klass(field_signature, CHECK);
        oop value = ((InstanceKlass*)kelem())->allocate_instance(CHECK);
        java_mirror->obj_field_put(fd.offset(), value);
      } else {
        report_error("unhandled staticfield");
      }
    }
  }

#if INCLUDE_JVMTI
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
    rec->klass =  method->method_holder()->name()->as_utf8();
    rec->method = method->name()->as_utf8();
    rec->signature = method->signature()->as_utf8();
    ci_method_records.append(rec);
    return rec;
  }

  // Lookup data for a ciMethod
  ciMethodRecord* find_ciMethodRecord(Method* method) {
    const char* klass_name =  method->method_holder()->name()->as_utf8();
    const char* method_name = method->name()->as_utf8();
    const char* signature = method->signature()->as_utf8();
    for (int i = 0; i < ci_method_records.length(); i++) {
      ciMethodRecord* rec = ci_method_records.at(i);
      if (strcmp(rec->klass, klass_name) == 0 &&
          strcmp(rec->method, method_name) == 0 &&
          strcmp(rec->signature, signature) == 0) {
        return rec;
      }
    }
    return NULL;
  }

  // Create and initialize a record for a ciMethodData
  ciMethodDataRecord* new_ciMethodData(Method* method) {
    ciMethodDataRecord* rec = NEW_RESOURCE_OBJ(ciMethodDataRecord);
    rec->klass =  method->method_holder()->name()->as_utf8();
    rec->method = method->name()->as_utf8();
    rec->signature = method->signature()->as_utf8();
    ci_method_data_records.append(rec);
    return rec;
  }

  // Lookup data for a ciMethodData
  ciMethodDataRecord* find_ciMethodDataRecord(Method* method) {
    const char* klass_name =  method->method_holder()->name()->as_utf8();
    const char* method_name = method->name()->as_utf8();
    const char* signature = method->signature()->as_utf8();
    for (int i = 0; i < ci_method_data_records.length(); i++) {
      ciMethodDataRecord* rec = ci_method_data_records.at(i);
      if (strcmp(rec->klass, klass_name) == 0 &&
          strcmp(rec->method, method_name) == 0 &&
          strcmp(rec->signature, signature) == 0) {
        return rec;
      }
    }
    return NULL;
  }

  const char* error_message() {
    return _error_message;
  }

  void reset() {
    _error_message = NULL;
    ci_method_records.clear();
    ci_method_data_records.clear();
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

int ciReplay::replay_impl(TRAPS) {
  HandleMark hm;
  ResourceMark rm;
  // Make sure we don't run with background compilation
  BackgroundCompilation = false;

  if (ReplaySuppressInitializers > 2) {
    // ReplaySuppressInitializers > 2 means that we want to allow
    // normal VM bootstrap but once we get into the replay itself
    // don't allow any intializers to be run.
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
    oop throwable = PENDING_EXCEPTION;
    CLEAR_PENDING_EXCEPTION;
    java_lang_Throwable::print(throwable, tty);
    tty->cr();
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
  if (replay_state == NULL) {
    return;
  }

  ASSERT_IN_VM;
  ResourceMark rm;

  Method* method = m->get_MethodData()->method();
  ciMethodDataRecord* rec = replay_state->find_ciMethodDataRecord(method);
  if (rec == NULL) {
    // This indicates some mismatch with the original environment and
    // the replay environment though it's not always enough to
    // interfere with reproducing a bug
    tty->print_cr("Warning: requesting ciMethodData record for method with no data: ");
    method->print_name(tty);
    tty->cr();
  } else {
    m->_state = rec->state;
    m->_current_mileage = rec->current_mileage;
    if (rec->data_length != 0) {
      assert(m->_data_size == rec->data_length * (int)sizeof(rec->data[0]), "must agree");

      // Write the correct ciObjects back into the profile data
      ciEnv* env = ciEnv::current();
      for (int i = 0; i < rec->oops_length; i++) {
        KlassHandle *h = (KlassHandle *)rec->oops_handles[i];
        *(ciMetadata**)(rec->data + rec->oops_offsets[i]) =
          env->get_metadata((*h)());
      }
      // Copy the updated profile data into place as intptr_ts
#ifdef _LP64
      Copy::conjoint_jlongs_atomic((jlong *)rec->data, (jlong *)m->_data, rec->data_length);
#else
      Copy::conjoint_jints_atomic((jint *)rec->data, (jint *)m->_data, rec->data_length);
#endif
    }

    // copy in the original header
    Copy::conjoint_jbytes(rec->orig_data, (char*)&m->_orig, rec->orig_data_length);
  }
}


bool ciReplay::should_not_inline(ciMethod* method) {
  if (replay_state == NULL) {
    return false;
  }

  VM_ENTRY_MARK;
  // ciMethod without a record shouldn't be inlined.
  return replay_state->find_ciMethodRecord(method->get_Method()) == NULL;
}


void ciReplay::initialize(ciMethod* m) {
  if (replay_state == NULL) {
    return;
  }

  ASSERT_IN_VM;
  ResourceMark rm;

  Method* method = m->get_Method();
  ciMethodRecord* rec = replay_state->find_ciMethodRecord(method);
  if (rec == NULL) {
    // This indicates some mismatch with the original environment and
    // the replay environment though it's not always enough to
    // interfere with reproducing a bug
    tty->print_cr("Warning: requesting ciMethod record for method with no data: ");
    method->print_name(tty);
    tty->cr();
  } else {
    EXCEPTION_CONTEXT;
    MethodCounters* mcs = method->method_counters();
    // m->_instructions_size = rec->instructions_size;
    m->_instructions_size = -1;
    m->_interpreter_invocation_count = rec->interpreter_invocation_count;
    m->_interpreter_throwout_count = rec->interpreter_throwout_count;
    if (mcs == NULL) {
      mcs = Method::build_method_counters(method, CHECK_AND_CLEAR);
    }
    mcs->invocation_counter()->_counter = rec->invocation_counter;
    mcs->backedge_counter()->_counter = rec->backedge_counter;
  }
}

bool ciReplay::is_loaded(Method* method) {
  if (replay_state == NULL) {
    return true;
  }

  ASSERT_IN_VM;
  ResourceMark rm;

  ciMethodRecord* rec = replay_state->find_ciMethodRecord(method);
  return rec != NULL;
}
#endif // PRODUCT
