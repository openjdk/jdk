/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "jimage.hpp"
#include "classfile/classListParser.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/lambdaFormInvokers.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "interpreter/bytecode.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/linkResolver.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "memory/archiveUtils.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "oops/constantPool.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/hashtable.inline.hpp"
#include "utilities/macros.hpp"

ClassListParser* ClassListParser::_instance = NULL;

ClassListParser::ClassListParser(const char* file) {
  assert(_instance == NULL, "must be singleton");
  _instance = this;
  _classlist_file = file;
  _file = NULL;
  // Use os::open() because neither fopen() nor os::fopen()
  // can handle long path name on Windows.
  int fd = os::open(file, O_RDONLY, S_IREAD);
  if (fd != -1) {
    // Obtain a File* from the file descriptor so that fgets()
    // can be used in parse_one_line()
    _file = os::open(fd, "r");
  }
  if (_file == NULL) {
    char errmsg[JVM_MAXPATHLEN];
    os::lasterror(errmsg, JVM_MAXPATHLEN);
    vm_exit_during_initialization("Loading classlist failed", errmsg);
  }
  _line_no = 0;
  _interfaces = new (ResourceObj::C_HEAP, mtClass) GrowableArray<int>(10, mtClass);
  _indy_items = new (ResourceObj::C_HEAP, mtClass) GrowableArray<const char*>(9, mtClass);
}

ClassListParser::~ClassListParser() {
  if (_file) {
    fclose(_file);
  }
  _instance = NULL;
}

bool ClassListParser::parse_one_line() {
  for (;;) {
    if (fgets(_line, sizeof(_line), _file) == NULL) {
      return false;
    }
    ++ _line_no;
    _line_len = (int)strlen(_line);
    if (_line_len > _max_allowed_line_len) {
      error("input line too long (must be no longer than %d chars)", _max_allowed_line_len);
    }
    if (*_line == '#') { // comment
      continue;
    }

    {
      int len = (int)strlen(_line);
      int i;
      // Replace \t\r\n\f with ' '
      for (i=0; i<len; i++) {
        if (_line[i] == '\t' || _line[i] == '\r' || _line[i] == '\n' || _line[i] == '\f') {
          _line[i] = ' ';
        }
      }

      // Remove trailing newline/space
      while (len > 0) {
        if (_line[len-1] == ' ') {
          _line[len-1] = '\0';
          len --;
        } else {
          break;
        }
      }
      _line_len = len;
    }

    // valid line
    break;
  }

  _class_name = _line;
  _id = _unspecified;
  _super = _unspecified;
  _interfaces->clear();
  _source = NULL;
  _interfaces_specified = false;
  _indy_items->clear();
  _lambda_form_line = false;

  if (_line[0] == '@') {
    return parse_at_tags();
  }

  if ((_token = strchr(_line, ' ')) == NULL) {
    // No optional arguments are specified.
    return true;
  }

  // Mark the end of the name, and go to the next input char
  *_token++ = '\0';

  while (*_token) {
    skip_whitespaces();

    if (parse_uint_option("id:", &_id)) {
      continue;
    } else if (parse_uint_option("super:", &_super)) {
      check_already_loaded("Super class", _super);
      continue;
    } else if (skip_token("interfaces:")) {
      int i;
      while (try_parse_uint(&i)) {
        check_already_loaded("Interface", i);
        _interfaces->append(i);
      }
    } else if (skip_token("source:")) {
      skip_whitespaces();
      _source = _token;
      char* s = strchr(_token, ' ');
      if (s == NULL) {
        break; // end of input line
      } else {
        *s = '\0'; // mark the end of _source
        _token = s+1;
      }
    } else {
      error("Unknown input");
    }
  }

  // if src is specified
  //     id super interfaces must all be specified
  //     loader may be specified
  // else
  //     # the class is loaded from classpath
  //     id may be specified
  //     super, interfaces, loader must not be specified
  return true;
}

void ClassListParser::split_tokens_by_whitespace(int offset) {
  int start = offset;
  int end;
  bool done = false;
  while (!done) {
    while (_line[start] == ' ' || _line[start] == '\t') start++;
    end = start;
    while (_line[end] && _line[end] != ' ' && _line[end] != '\t') end++;
    if (_line[end] == '\0') {
      done = true;
    } else {
      _line[end] = '\0';
    }
    _indy_items->append(_line + start);
    start = ++end;
  }
}

int ClassListParser::split_at_tag_from_line() {
  _token = _line;
  char* ptr;
  if ((ptr = strchr(_line, ' ')) == NULL) {
    error("Too few items following the @ tag \"%s\" line #%d", _line, _line_no);
    return 0;
  }
  *ptr++ = '\0';
  while (*ptr == ' ' || *ptr == '\t') ptr++;
  return (int)(ptr - _line);
}

bool ClassListParser::parse_at_tags() {
  assert(_line[0] == '@', "must be");
  int offset;
  if ((offset = split_at_tag_from_line()) == 0) {
    return false;
  }

  if (strcmp(_token, LAMBDA_PROXY_TAG) == 0) {
    split_tokens_by_whitespace(offset);
    if (_indy_items->length() < 2) {
      error("Line with @ tag has too few items \"%s\" line #%d", _token, _line_no);
      return false;
    }
    // set the class name
    _class_name = _indy_items->at(0);
    return true;
  } else if (strcmp(_token, LAMBDA_FORM_TAG) == 0) {
    LambdaFormInvokers::append(os::strdup((const char*)(_line + offset), mtInternal));
    _lambda_form_line = true;
    return true;
  } else {
    error("Invalid @ tag at the beginning of line \"%s\" line #%d", _token, _line_no);
    return false;
  }
}

void ClassListParser::skip_whitespaces() {
  while (*_token == ' ' || *_token == '\t') {
    _token ++;
  }
}

void ClassListParser::skip_non_whitespaces() {
  while (*_token && *_token != ' ' && *_token != '\t') {
    _token ++;
  }
}

void ClassListParser::parse_int(int* value) {
  skip_whitespaces();
  if (sscanf(_token, "%i", value) == 1) {
    skip_non_whitespaces();
  } else {
    error("Error: expected integer");
  }
}

void ClassListParser::parse_uint(int* value) {
  parse_int(value);
  if (*value < 0) {
    error("Error: negative integers not allowed (%d)", *value);
  }
}

bool ClassListParser::try_parse_uint(int* value) {
  skip_whitespaces();
  if (sscanf(_token, "%i", value) == 1) {
    skip_non_whitespaces();
    return true;
  }
  return false;
}

bool ClassListParser::skip_token(const char* option_name) {
  size_t len = strlen(option_name);
  if (strncmp(_token, option_name, len) == 0) {
    _token += len;
    return true;
  } else {
    return false;
  }
}

bool ClassListParser::parse_int_option(const char* option_name, int* value) {
  if (skip_token(option_name)) {
    if (*value != _unspecified) {
      error("%s specified twice", option_name);
    } else {
      parse_int(value);
      return true;
    }
  }
  return false;
}

bool ClassListParser::parse_uint_option(const char* option_name, int* value) {
  if (skip_token(option_name)) {
    if (*value != _unspecified) {
      error("%s specified twice", option_name);
    } else {
      parse_uint(value);
      return true;
    }
  }
  return false;
}

void ClassListParser::print_specified_interfaces() {
  const int n = _interfaces->length();
  jio_fprintf(defaultStream::error_stream(), "Currently specified interfaces[%d] = {\n", n);
  for (int i=0; i<n; i++) {
    InstanceKlass* k = lookup_class_by_id(_interfaces->at(i));
    jio_fprintf(defaultStream::error_stream(), "  %4d = %s\n", _interfaces->at(i), k->name()->as_klass_external_name());
  }
  jio_fprintf(defaultStream::error_stream(), "}\n");
}

void ClassListParser::print_actual_interfaces(InstanceKlass* ik) {
  int n = ik->local_interfaces()->length();
  jio_fprintf(defaultStream::error_stream(), "Actual interfaces[%d] = {\n", n);
  for (int i = 0; i < n; i++) {
    InstanceKlass* e = ik->local_interfaces()->at(i);
    jio_fprintf(defaultStream::error_stream(), "  %s\n", e->name()->as_klass_external_name());
  }
  jio_fprintf(defaultStream::error_stream(), "}\n");
}

void ClassListParser::error(const char* msg, ...) {
  va_list ap;
  va_start(ap, msg);
  int error_index = _token - _line;
  if (error_index >= _line_len) {
    error_index = _line_len - 1;
  }
  if (error_index < 0) {
    error_index = 0;
  }

  jio_fprintf(defaultStream::error_stream(),
              "An error has occurred while processing class list file %s %d:%d.\n",
              _classlist_file, _line_no, (error_index + 1));
  jio_vfprintf(defaultStream::error_stream(), msg, ap);

  if (_line_len <= 0) {
    jio_fprintf(defaultStream::error_stream(), "\n");
  } else {
    jio_fprintf(defaultStream::error_stream(), ":\n");
    for (int i=0; i<_line_len; i++) {
      char c = _line[i];
      if (c == '\0') {
        jio_fprintf(defaultStream::error_stream(), "%s", " ");
      } else {
        jio_fprintf(defaultStream::error_stream(), "%c", c);
      }
    }
    jio_fprintf(defaultStream::error_stream(), "\n");
    for (int i=0; i<error_index; i++) {
      jio_fprintf(defaultStream::error_stream(), "%s", " ");
    }
    jio_fprintf(defaultStream::error_stream(), "^\n");
  }

  vm_exit_during_initialization("class list format error.", NULL);
  va_end(ap);
}

// This function is used for loading classes for customized class loaders
// during archive dumping.
InstanceKlass* ClassListParser::load_class_from_source(Symbol* class_name, TRAPS) {
#if !(defined(_LP64) && (defined(LINUX) || defined(__APPLE__)))
  // The only supported platforms are: (1) Linux/64-bit and (2) Solaris/64-bit and
  // (3) MacOSX/64-bit
  // This #if condition should be in sync with the areCustomLoadersSupportedForCDS
  // method in test/lib/jdk/test/lib/Platform.java.
  error("AppCDS custom class loaders not supported on this platform");
#endif

  if (!is_super_specified()) {
    error("If source location is specified, super class must be also specified");
  }
  if (!is_id_specified()) {
    error("If source location is specified, id must be also specified");
  }
  if (strncmp(_class_name, "java/", 5) == 0) {
    log_info(cds)("Prohibited package for non-bootstrap classes: %s.class from %s",
          _class_name, _source);
    return NULL;
  }

  InstanceKlass* k = ClassLoaderExt::load_class(class_name, _source, CHECK_NULL);

  if (k != NULL) {
    if (k->local_interfaces()->length() != _interfaces->length()) {
      print_specified_interfaces();
      print_actual_interfaces(k);
      error("The number of interfaces (%d) specified in class list does not match the class file (%d)",
            _interfaces->length(), k->local_interfaces()->length());
    }

    bool added = SystemDictionaryShared::add_unregistered_class(k, CHECK_NULL);
    if (!added) {
      // We allow only a single unregistered class for each unique name.
      error("Duplicated class %s", _class_name);
    }

    // This tells JVM_FindLoadedClass to not find this class.
    k->set_shared_classpath_index(UNREGISTERED_INDEX);
    k->clear_shared_class_loader_type();
  }

  return k;
}

void ClassListParser::populate_cds_indy_info(const constantPoolHandle &pool, int cp_index, CDSIndyInfo* cii, TRAPS) {
  // Caller needs to allocate ResourceMark.
  int type_index = pool->bootstrap_name_and_type_ref_index_at(cp_index);
  int name_index = pool->name_ref_index_at(type_index);
  cii->add_item(pool->symbol_at(name_index)->as_C_string());
  int sig_index = pool->signature_ref_index_at(type_index);
  cii->add_item(pool->symbol_at(sig_index)->as_C_string());
  int argc = pool->bootstrap_argument_count_at(cp_index);
  if (argc > 0) {
    for (int arg_i = 0; arg_i < argc; arg_i++) {
      int arg = pool->bootstrap_argument_index_at(cp_index, arg_i);
      jbyte tag = pool->tag_at(arg).value();
      if (tag == JVM_CONSTANT_MethodType) {
        cii->add_item(pool->method_type_signature_at(arg)->as_C_string());
      } else if (tag == JVM_CONSTANT_MethodHandle) {
        cii->add_ref_kind(pool->method_handle_ref_kind_at(arg));
        int callee_index = pool->method_handle_klass_index_at(arg);
        Klass* callee = pool->klass_at(callee_index, THREAD);
        if (callee != NULL) {
          cii->add_item(callee->name()->as_C_string());
        }
        cii->add_item(pool->method_handle_name_ref_at(arg)->as_C_string());
        cii->add_item(pool->method_handle_signature_ref_at(arg)->as_C_string());
      } else {
        ShouldNotReachHere();
      }
    }
  }
}

bool ClassListParser::is_matching_cp_entry(constantPoolHandle &pool, int cp_index, TRAPS) {
  ResourceMark rm(THREAD);
  CDSIndyInfo cii;
  populate_cds_indy_info(pool, cp_index, &cii, THREAD);
  GrowableArray<const char*>* items = cii.items();
  int indy_info_offset = 1;
  if (_indy_items->length() - indy_info_offset != items->length()) {
    return false;
  }
  for (int i = 0; i < items->length(); i++) {
    if (strcmp(_indy_items->at(i + indy_info_offset), items->at(i)) != 0) {
      return false;
    }
  }
  return true;
}
void ClassListParser::resolve_indy(Symbol* class_name_symbol, TRAPS) {
  ClassListParser::resolve_indy_impl(class_name_symbol, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    ResourceMark rm(THREAD);
    char* ex_msg = (char*)"";
    oop message = java_lang_Throwable::message(PENDING_EXCEPTION);
    if (message != NULL) {
      ex_msg = java_lang_String::as_utf8_string(message);
    }
    log_warning(cds)("resolve_indy for class %s has encountered exception: %s %s",
                     class_name_symbol->as_C_string(),
                     PENDING_EXCEPTION->klass()->external_name(),
                     ex_msg);
    CLEAR_PENDING_EXCEPTION;
  }
}

void ClassListParser::resolve_indy_impl(Symbol* class_name_symbol, TRAPS) {
  Handle class_loader(THREAD, SystemDictionary::java_system_loader());
  Handle protection_domain;
  Klass* klass = SystemDictionary::resolve_or_fail(class_name_symbol, class_loader, protection_domain, true, CHECK); // FIXME should really be just a lookup
  if (klass != NULL && klass->is_instance_klass()) {
    InstanceKlass* ik = InstanceKlass::cast(klass);
    if (SystemDictionaryShared::has_class_failed_verification(ik)) {
      // don't attempt to resolve indy on classes that has previously failed verification
      return;
    }
    MetaspaceShared::try_link_class(ik, CHECK);

    ConstantPool* cp = ik->constants();
    ConstantPoolCache* cpcache = cp->cache();
    bool found = false;
    for (int cpcindex = 0; cpcindex < cpcache->length(); cpcindex ++) {
      int indy_index = ConstantPool::encode_invokedynamic_index(cpcindex);
      ConstantPoolCacheEntry* cpce = cpcache->entry_at(cpcindex);
      int pool_index = cpce->constant_pool_index();
      constantPoolHandle pool(THREAD, cp);
      if (pool->tag_at(pool_index).is_invoke_dynamic()) {
        BootstrapInfo bootstrap_specifier(pool, pool_index, indy_index);
        Handle bsm = bootstrap_specifier.resolve_bsm(CHECK);
        if (!SystemDictionaryShared::is_supported_invokedynamic(&bootstrap_specifier)) {
          log_debug(cds, lambda)("is_supported_invokedynamic check failed for cp_index %d", pool_index);
          continue;
        }
        bool matched = is_matching_cp_entry(pool, pool_index, CHECK);
        if (matched) {
          found = true;
          CallInfo info;
          bool is_done = bootstrap_specifier.resolve_previously_linked_invokedynamic(info, CHECK);
          if (!is_done) {
            // resolve it
            Handle recv;
            LinkResolver::resolve_invoke(info, recv, pool, indy_index, Bytecodes::_invokedynamic, CHECK);
            break;
          }
          cpce->set_dynamic_call(pool, info);
        }
      }
    }
    if (!found) {
      ResourceMark rm(THREAD);
      log_warning(cds)("No invoke dynamic constant pool entry can be found for class %s. The classlist is probably out-of-date.",
                     class_name_symbol->as_C_string());
    }
  }
}

Klass* ClassListParser::load_current_class(TRAPS) {
  TempNewSymbol class_name_symbol = SymbolTable::new_symbol(_class_name);

  if (_indy_items->length() > 0) {
    resolve_indy(class_name_symbol, CHECK_NULL);
    return NULL;
  }

  Klass* klass = NULL;
  if (!is_loading_from_source()) {
    // Load classes for the boot/platform/app loaders only.
    if (is_super_specified()) {
      error("If source location is not specified, super class must not be specified");
    }
    if (are_interfaces_specified()) {
      error("If source location is not specified, interface(s) must not be specified");
    }

    bool non_array = !Signature::is_array(class_name_symbol);

    JavaValue result(T_OBJECT);
    if (non_array) {
      // At this point, we are executing in the context of the boot loader. We
      // cannot call Class.forName because that is context dependent and
      // would load only classes for the boot loader.
      //
      // Instead, let's call java_system_loader().loadClass() directly, which will
      // delegate to the correct loader (boot, platform or app) depending on
      // the class name.

      Handle s = java_lang_String::create_from_symbol(class_name_symbol, CHECK_NULL);
      // ClassLoader.loadClass() wants external class name format, i.e., convert '/' chars to '.'
      Handle ext_class_name = java_lang_String::externalize_classname(s, CHECK_NULL);
      Handle loader = Handle(THREAD, SystemDictionary::java_system_loader());

      JavaCalls::call_virtual(&result,
                              loader, //SystemDictionary::java_system_loader(),
                              vmClasses::ClassLoader_klass(),
                              vmSymbols::loadClass_name(),
                              vmSymbols::string_class_signature(),
                              ext_class_name,
                              THREAD); // <-- failure is handled below
    } else {
      // array classes are not supported in class list.
      THROW_NULL(vmSymbols::java_lang_ClassNotFoundException());
    }
    assert(result.get_type() == T_OBJECT, "just checking");
    oop obj = (oop) result.get_jobject();
    if (!HAS_PENDING_EXCEPTION && (obj != NULL)) {
      klass = java_lang_Class::as_Klass(obj);
    } else { // load classes in bootclasspath/a
      if (HAS_PENDING_EXCEPTION) {
        ArchiveUtils::check_for_oom(PENDING_EXCEPTION); // exit on OOM
        CLEAR_PENDING_EXCEPTION;
      }

      if (non_array) {
        Klass* k = SystemDictionary::resolve_or_null(class_name_symbol, CHECK_NULL);
        if (k != NULL) {
          klass = k;
        } else {
          if (!HAS_PENDING_EXCEPTION) {
            THROW_NULL(vmSymbols::java_lang_ClassNotFoundException());
          } else {
            ArchiveUtils::check_for_oom(PENDING_EXCEPTION); // exit on OOM
          }
        }
      }
    }
  } else {
    // If "source:" tag is specified, all super class and super interfaces must be specified in the
    // class list file.
    klass = load_class_from_source(class_name_symbol, CHECK_NULL);
    if (HAS_PENDING_EXCEPTION) {
      ArchiveUtils::check_for_oom(PENDING_EXCEPTION); // exit on OOM
    }
  }

  if (klass != NULL && klass->is_instance_klass() && is_id_specified()) {
    InstanceKlass* ik = InstanceKlass::cast(klass);
    int id = this->id();
    SystemDictionaryShared::update_shared_entry(ik, id);
    InstanceKlass** old_ptr = table()->lookup(id);
    if (old_ptr != NULL) {
      error("Duplicated ID %d for class %s", id, _class_name);
    }
    table()->add(id, ik);
  }

  return klass;
}

bool ClassListParser::is_loading_from_source() {
  return (_source != NULL);
}

InstanceKlass* ClassListParser::lookup_class_by_id(int id) {
  InstanceKlass** klass_ptr = table()->lookup(id);
  if (klass_ptr == NULL) {
    error("Class ID %d has not been defined", id);
  }
  assert(*klass_ptr != NULL, "must be");
  return *klass_ptr;
}


InstanceKlass* ClassListParser::lookup_super_for_current_class(Symbol* super_name) {
  if (!is_loading_from_source()) {
    return NULL;
  }

  InstanceKlass* k = lookup_class_by_id(super());
  if (super_name != k->name()) {
    error("The specified super class %s (id %d) does not match actual super class %s",
          k->name()->as_klass_external_name(), super(),
          super_name->as_klass_external_name());
  }
  return k;
}

InstanceKlass* ClassListParser::lookup_interface_for_current_class(Symbol* interface_name) {
  if (!is_loading_from_source()) {
    return NULL;
  }

  const int n = _interfaces->length();
  if (n == 0) {
    error("Class %s implements the interface %s, but no interface has been specified in the input line",
          _class_name, interface_name->as_klass_external_name());
    ShouldNotReachHere();
  }

  int i;
  for (i=0; i<n; i++) {
    InstanceKlass* k = lookup_class_by_id(_interfaces->at(i));
    if (interface_name == k->name()) {
      return k;
    }
  }

  // interface_name is not specified by the "interfaces:" keyword.
  print_specified_interfaces();
  error("The interface %s implemented by class %s does not match any of the specified interface IDs",
        interface_name->as_klass_external_name(), _class_name);
  ShouldNotReachHere();
  return NULL;
}
