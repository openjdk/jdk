/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "jvm.h"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "services/diagnosticArgument.hpp"
#include "services/diagnosticFramework.hpp"
#include "services/management.hpp"

CmdLine::CmdLine(const char* line, size_t len, bool no_command_name)
  : _cmd(line), _cmd_len(0), _args(NULL), _args_len(0)
{
  assert(line != NULL, "Command line string should not be NULL");
  const char* line_end;
  const char* cmd_end;

  _cmd = line;
  line_end = &line[len];

  // Skip whitespace in the beginning of the line.
  while (_cmd < line_end && isspace((int) _cmd[0])) {
    _cmd++;
  }
  cmd_end = _cmd;

  if (no_command_name) {
    _cmd = NULL;
    _cmd_len = 0;
  } else {
    // Look for end of the command name
    while (cmd_end < line_end && !isspace((int) cmd_end[0])) {
      cmd_end++;
    }
    _cmd_len = cmd_end - _cmd;
  }
  _args = cmd_end;
  _args_len = line_end - _args;
}

bool DCmdArgIter::next(TRAPS) {
  if (_len == 0) return false;
  // skipping delimiters
  while (_cursor < _len - 1 && _buffer[_cursor] == _delim) {
    _cursor++;
  }
  // handling end of command line
  if (_cursor == _len - 1 && _buffer[_cursor] == _delim) {
    _key_addr = &_buffer[_cursor];
    _key_len = 0;
    _value_addr = &_buffer[_cursor];
    _value_len = 0;
    return false;
  }
  // extracting first item, argument or option name
  _key_addr = &_buffer[_cursor];
  bool arg_had_quotes = false;
  while (_cursor <= _len - 1 && _buffer[_cursor] != '=' && _buffer[_cursor] != _delim) {
    // argument can be surrounded by single or double quotes
    if (_buffer[_cursor] == '\"' || _buffer[_cursor] == '\'') {
      _key_addr++;
      char quote = _buffer[_cursor];
      arg_had_quotes = true;
      while (_cursor < _len - 1) {
        _cursor++;
        if (_buffer[_cursor] == quote && _buffer[_cursor - 1] != '\\') {
          break;
        }
      }
      if (_buffer[_cursor] != quote) {
        THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
                "Format error in diagnostic command arguments", false);
      }
      break;
    }
    _cursor++;
  }
  _key_len = &_buffer[_cursor] - _key_addr;
  if (arg_had_quotes) {
    // if the argument was quoted, we need to step past the last quote here
    _cursor++;
  }
  // check if the argument has the <key>=<value> format
  if (_cursor <= _len -1 && _buffer[_cursor] == '=') {
    _cursor++;
    _value_addr = &_buffer[_cursor];
    bool value_had_quotes = false;
    // extract the value
    while (_cursor <= _len - 1 && _buffer[_cursor] != _delim) {
      // value can be surrounded by simple or double quotes
      if (_buffer[_cursor] == '\"' || _buffer[_cursor] == '\'') {
        _value_addr++;
        char quote = _buffer[_cursor];
        value_had_quotes = true;
        while (_cursor < _len - 1) {
          _cursor++;
          if (_buffer[_cursor] == quote && _buffer[_cursor - 1] != '\\') {
            break;
          }
        }
        if (_buffer[_cursor] != quote) {
          THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
                  "Format error in diagnostic command arguments", false);
        }
        break;
      }
      _cursor++;
    }
    _value_len = &_buffer[_cursor] - _value_addr;
    if (value_had_quotes) {
      // if the value was quoted, we need to step past the last quote here
      _cursor++;
    }
  } else {
    _value_addr = NULL;
    _value_len = 0;
  }
  return _key_len != 0;
}

bool DCmdInfo::by_name(void* cmd_name, DCmdInfo* info) {
  if (info == NULL) return false;
  return strcmp((const char*)cmd_name, info->name()) == 0;
}

void DCmdParser::add_dcmd_option(GenDCmdArgument* arg) {
  assert(arg != NULL, "Sanity");
  if (_options == NULL) {
    _options = arg;
  } else {
    GenDCmdArgument* o = _options;
    while (o->next() != NULL) {
      o = o->next();
    }
    o->set_next(arg);
  }
  arg->set_next(NULL);
  JavaThread* THREAD = JavaThread::current(); // For exception macros.
  arg->init_value(THREAD);
  if (HAS_PENDING_EXCEPTION) {
    fatal("Initialization must be successful");
  }
}

void DCmdParser::add_dcmd_argument(GenDCmdArgument* arg) {
  assert(arg != NULL, "Sanity");
  if (_arguments_list == NULL) {
    _arguments_list = arg;
  } else {
    GenDCmdArgument* a = _arguments_list;
    while (a->next() != NULL) {
      a = a->next();
    }
    a->set_next(arg);
  }
  arg->set_next(NULL);
  JavaThread* THREAD = JavaThread::current(); // For exception macros.
  arg->init_value(THREAD);
  if (HAS_PENDING_EXCEPTION) {
    fatal("Initialization must be successful");
  }
}

void DCmdParser::parse(CmdLine* line, char delim, TRAPS) {
  GenDCmdArgument* next_argument = _arguments_list;
  DCmdArgIter iter(line->args_addr(), line->args_len(), delim);
  bool cont = iter.next(CHECK);
  while (cont) {
    GenDCmdArgument* arg = lookup_dcmd_option(iter.key_addr(),
            iter.key_length());
    if (arg != NULL) {
      arg->read_value(iter.value_addr(), iter.value_length(), CHECK);
    } else {
      if (next_argument != NULL) {
        arg = next_argument;
        arg->read_value(iter.key_addr(), iter.key_length(), CHECK);
        next_argument = next_argument->next();
      } else {
        const size_t buflen    = 120;
        const size_t argbuflen = 30;
        char buf[buflen];
        char argbuf[argbuflen];
        size_t len = MIN2<size_t>(iter.key_length(), argbuflen - 1);

        strncpy(argbuf, iter.key_addr(), len);
        argbuf[len] = '\0';
        jio_snprintf(buf, buflen - 1, "Unknown argument '%s' in diagnostic command.", argbuf);

        THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), buf);
      }
    }
    cont = iter.next(CHECK);
  }
  check(CHECK);
}

GenDCmdArgument* DCmdParser::lookup_dcmd_option(const char* name, size_t len) {
  GenDCmdArgument* arg = _options;
  while (arg != NULL) {
    if (strlen(arg->name()) == len &&
      strncmp(name, arg->name(), len) == 0) {
      return arg;
    }
    arg = arg->next();
  }
  return NULL;
}

void DCmdParser::check(TRAPS) {
  const size_t buflen = 256;
  char buf[buflen];
  GenDCmdArgument* arg = _arguments_list;
  while (arg != NULL) {
    if (arg->is_mandatory() && !arg->has_value()) {
      jio_snprintf(buf, buflen - 1, "The argument '%s' is mandatory.", arg->name());
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), buf);
    }
    arg = arg->next();
  }
  arg = _options;
  while (arg != NULL) {
    if (arg->is_mandatory() && !arg->has_value()) {
      jio_snprintf(buf, buflen - 1, "The option '%s' is mandatory.", arg->name());
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), buf);
    }
    arg = arg->next();
  }
}

void DCmdParser::print_help(outputStream* out, const char* cmd_name) const {
  out->print("Syntax : %s %s", cmd_name, _options == NULL ? "" : "[options]");
  GenDCmdArgument* arg = _arguments_list;
  while (arg != NULL) {
    if (arg->is_mandatory()) {
      out->print(" <%s>", arg->name());
    } else {
      out->print(" [<%s>]", arg->name());
    }
    arg = arg->next();
  }
  out->cr();
  if (_arguments_list != NULL) {
    out->print_cr("\nArguments:");
    arg = _arguments_list;
    while (arg != NULL) {
      out->print("\t%s : %s %s (%s, ", arg->name(),
                 arg->is_mandatory() ? "" : "[optional]",
                 arg->description(), arg->type());
      if (arg->has_default()) {
        out->print("%s", arg->default_string());
      } else {
        out->print("no default value");
      }
      out->print_cr(")");
      arg = arg->next();
    }
  }
  if (_options != NULL) {
    out->print_cr("\nOptions: (options must be specified using the <key> or <key>=<value> syntax)");
    arg = _options;
    while (arg != NULL) {
      out->print("\t%s : %s %s (%s, ", arg->name(),
                 arg->is_mandatory() ? "" : "[optional]",
                 arg->description(), arg->type());
      if (arg->has_default()) {
        out->print("%s", arg->default_string());
      } else {
        out->print("no default value");
      }
      out->print_cr(")");
      arg = arg->next();
    }
  }
}

void DCmdParser::reset(TRAPS) {
  GenDCmdArgument* arg = _arguments_list;
  while (arg != NULL) {
    arg->reset(CHECK);
    arg = arg->next();
  }
  arg = _options;
  while (arg != NULL) {
    arg->reset(CHECK);
    arg = arg->next();
  }
}

void DCmdParser::cleanup() {
  GenDCmdArgument* arg = _arguments_list;
  while (arg != NULL) {
    arg->cleanup();
    arg = arg->next();
  }
  arg = _options;
  while (arg != NULL) {
    arg->cleanup();
    arg = arg->next();
  }
}

int DCmdParser::num_arguments() const {
  GenDCmdArgument* arg = _arguments_list;
  int count = 0;
  while (arg != NULL) {
    count++;
    arg = arg->next();
  }
  arg = _options;
  while (arg != NULL) {
    count++;
    arg = arg->next();
  }
  return count;
}

GrowableArray<const char *>* DCmdParser::argument_name_array() const {
  int count = num_arguments();
  GrowableArray<const char *>* array = new GrowableArray<const char *>(count);
  GenDCmdArgument* arg = _arguments_list;
  while (arg != NULL) {
    array->append(arg->name());
    arg = arg->next();
  }
  arg = _options;
  while (arg != NULL) {
    array->append(arg->name());
    arg = arg->next();
  }
  return array;
}

GrowableArray<DCmdArgumentInfo*>* DCmdParser::argument_info_array() const {
  int count = num_arguments();
  GrowableArray<DCmdArgumentInfo*>* array = new GrowableArray<DCmdArgumentInfo *>(count);
  int idx = 0;
  GenDCmdArgument* arg = _arguments_list;
  while (arg != NULL) {
    array->append(new DCmdArgumentInfo(arg->name(), arg->description(),
                  arg->type(), arg->default_string(), arg->is_mandatory(),
                  false, arg->allow_multiple(), idx));
    idx++;
    arg = arg->next();
  }
  arg = _options;
  while (arg != NULL) {
    array->append(new DCmdArgumentInfo(arg->name(), arg->description(),
                  arg->type(), arg->default_string(), arg->is_mandatory(),
                  true, arg->allow_multiple()));
    arg = arg->next();
  }
  return array;
}

DCmdFactory* DCmdFactory::_DCmdFactoryList = NULL;
bool DCmdFactory::_has_pending_jmx_notification = false;

void DCmd::parse_and_execute(DCmdSource source, outputStream* out,
                             const char* cmdline, char delim, TRAPS) {

  if (cmdline == NULL) return; // Nothing to do!
  DCmdIter iter(cmdline, '\n');

  int count = 0;
  while (iter.has_next()) {
    if(source == DCmd_Source_MBean && count > 0) {
      // When diagnostic commands are invoked via JMX, each command line
      // must contains one and only one command because of the Permission
      // checks performed by the DiagnosticCommandMBean
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Invalid syntax");
    }
    CmdLine line = iter.next();
    if (line.is_stop()) {
      break;
    }
    if (line.is_executable()) {
      ResourceMark rm;
      DCmd* command = DCmdFactory::create_local_DCmd(source, line, out, CHECK);
      assert(command != NULL, "command error must be handled before this line");
      DCmdMark mark(command);
      command->parse(&line, delim, CHECK);
      command->execute(source, CHECK);
    }
    count++;
  }
}

void DCmdWithParser::parse(CmdLine* line, char delim, TRAPS) {
  _dcmdparser.parse(line, delim, CHECK);
}

void DCmdWithParser::print_help(const char* name) const {
  _dcmdparser.print_help(output(), name);
}

void DCmdWithParser::reset(TRAPS) {
  _dcmdparser.reset(CHECK);
}

void DCmdWithParser::cleanup() {
  _dcmdparser.cleanup();
}

GrowableArray<const char*>* DCmdWithParser::argument_name_array() const {
  return _dcmdparser.argument_name_array();
}

GrowableArray<DCmdArgumentInfo*>* DCmdWithParser::argument_info_array() const {
  return _dcmdparser.argument_info_array();
}

void DCmdFactory::push_jmx_notification_request() {
  MutexLocker ml(Notification_lock, Mutex::_no_safepoint_check_flag);
  _has_pending_jmx_notification = true;
  Notification_lock->notify_all();
}

void DCmdFactory::send_notification(TRAPS) {
  DCmdFactory::send_notification_internal(THREAD);
  // Clearing pending exception to avoid premature termination of
  // the service thread
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
  }
}
void DCmdFactory::send_notification_internal(TRAPS) {
  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);
  bool notif = false;
  {
    MutexLocker ml(THREAD, Notification_lock, Mutex::_no_safepoint_check_flag);
    notif = _has_pending_jmx_notification;
    _has_pending_jmx_notification = false;
  }
  if (notif) {

    Klass* k = Management::com_sun_management_internal_DiagnosticCommandImpl_klass(CHECK);
    InstanceKlass* dcmd_mbean_klass = InstanceKlass::cast(k);

    JavaValue result(T_OBJECT);
    JavaCalls::call_static(&result,
            dcmd_mbean_klass,
            vmSymbols::getDiagnosticCommandMBean_name(),
            vmSymbols::getDiagnosticCommandMBean_signature(),
            CHECK);

    instanceOop m = (instanceOop) result.get_oop();
    instanceHandle dcmd_mbean_h(THREAD, m);

    if (!dcmd_mbean_h->is_a(k)) {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "DiagnosticCommandImpl.getDiagnosticCommandMBean didn't return a DiagnosticCommandMBean instance");
    }

    JavaValue result2(T_VOID);
    JavaCallArguments args2(dcmd_mbean_h);

    JavaCalls::call_virtual(&result2,
            dcmd_mbean_klass,
            vmSymbols::createDiagnosticFrameworkNotification_name(),
            vmSymbols::void_method_signature(),
            &args2,
            CHECK);
  }
}

bool DCmdFactory::_send_jmx_notification = false;

DCmdFactory* DCmdFactory::factory(DCmdSource source, const char* name, size_t len) {
  MutexLocker ml(DCmdFactory_lock, Mutex::_no_safepoint_check_flag);
  DCmdFactory* factory = _DCmdFactoryList;
  while (factory != NULL) {
    if (strlen(factory->name()) == len &&
        strncmp(name, factory->name(), len) == 0) {
      if(factory->export_flags() & source) {
        return factory;
      } else {
        return NULL;
      }
    }
    factory = factory->_next;
  }
  return NULL;
}

int DCmdFactory::register_DCmdFactory(DCmdFactory* factory) {
  MutexLocker ml(DCmdFactory_lock, Mutex::_no_safepoint_check_flag);
  factory->_next = _DCmdFactoryList;
  _DCmdFactoryList = factory;
  if (_send_jmx_notification && !factory->_hidden
      && (factory->_export_flags & DCmd_Source_MBean)) {
    DCmdFactory::push_jmx_notification_request();
  }
  return 0; // Actually, there's no checks for duplicates
}

DCmd* DCmdFactory::create_local_DCmd(DCmdSource source, CmdLine &line,
                                     outputStream* out, TRAPS) {
  DCmdFactory* f = factory(source, line.cmd_addr(), line.cmd_len());
  if (f != NULL) {
    if (!f->is_enabled()) {
      THROW_MSG_NULL(vmSymbols::java_lang_IllegalArgumentException(),
                     f->disabled_message());
    }
    return f->create_resource_instance(out);
  }
  THROW_MSG_NULL(vmSymbols::java_lang_IllegalArgumentException(),
             "Unknown diagnostic command");
}

GrowableArray<const char*>* DCmdFactory::DCmd_list(DCmdSource source) {
  MutexLocker ml(DCmdFactory_lock, Mutex::_no_safepoint_check_flag);
  GrowableArray<const char*>* array = new GrowableArray<const char*>();
  DCmdFactory* factory = _DCmdFactoryList;
  while (factory != NULL) {
    if (!factory->is_hidden() && (factory->export_flags() & source)) {
      array->append(factory->name());
    }
    factory = factory->next();
  }
  return array;
}

GrowableArray<DCmdInfo*>* DCmdFactory::DCmdInfo_list(DCmdSource source ) {
  MutexLocker ml(DCmdFactory_lock, Mutex::_no_safepoint_check_flag);
  GrowableArray<DCmdInfo*>* array = new GrowableArray<DCmdInfo*>();
  DCmdFactory* factory = _DCmdFactoryList;
  while (factory != NULL) {
    if (!factory->is_hidden() && (factory->export_flags() & source)) {
      array->append(new DCmdInfo(factory->name(),
                    factory->description(), factory->impact(),
                    factory->permission(), factory->num_arguments(),
                    factory->is_enabled()));
    }
    factory = factory->next();
  }
  return array;
}

static InstanceKlass* factory_klass(TRAPS) {
  Symbol* klass = vmSymbols::sum_management_cmd_Factory();
  Klass* k = SystemDictionary::resolve_or_fail(klass, true, CHECK_NULL);
  InstanceKlass* ik = InstanceKlass::cast(k);
  if (ik->should_be_initialized()) {
    ik->initialize(CHECK_NULL);
  }
  return ik;
}

static InstanceKlass* executor_klass(TRAPS) {
  Symbol* klass = vmSymbols::sum_management_cmd_internal_Executor();
  Klass* k = SystemDictionary::resolve_or_fail(klass, true, CHECK_NULL);
  InstanceKlass* ik = InstanceKlass::cast(k);
  if (ik->should_be_initialized()) {
    ik->initialize(CHECK_NULL);
  }
  return ik;
}

void JavaDCmd::parse(CmdLine *line, char delim, TRAPS) {
  HandleMark hm(THREAD);
  InstanceKlass* ik = factory_klass(CHECK);
  JavaValue result(T_OBJECT);
  JavaCallArguments args;
  args.set_receiver(Handle(THREAD, JNIHandles::resolve_non_null(_factory._factory)));

  char* cmd_args = NULL;
  if (line->args_len() > 0) {
    cmd_args = NEW_RESOURCE_ARRAY(char, line->args_len() + 1);
    strncpy(cmd_args, line->args_addr(), line->args_len());
    cmd_args[line->args_len()] = '\0';
  }

  Handle args_str = java_lang_String::create_from_str((cmd_args != NULL ? cmd_args : ""), CHECK);
  args.push_oop(args_str);
  args.push_int(delim);

  JavaCalls::call_virtual(&result,
                          ik,
                          vmSymbols::buildCommand_name(),
                          vmSymbols::buildCommand_signature(),
                          &args,
                          CHECK);
  Handle r = Handle(THREAD, result.get_oop());
  _cmd = JNIHandles::make_global(r);
}

void JavaDCmd::execute(DCmdSource source, TRAPS) {
  HandleMark hm(THREAD);
  InstanceKlass* ik = executor_klass(CHECK);
  JavaValue result(T_OBJECT);
  JavaCallArguments args;

  args.push_oop(Handle(THREAD, JNIHandles::resolve_non_null(_cmd)));
  JavaCalls::call_static(&result,
                         ik,
                         vmSymbols::executeCommand_name(),
                         vmSymbols::executeCommand_signature(),
                         &args,
                         CHECK);
  Handle h = Handle(THREAD, result.get_oop());
  if (h.not_null()) {
    output()->print_raw(java_lang_String::as_utf8_string(h()));
  }
}

void JavaDCmd::cleanup() {
  JNIHandles::destroy_global(_cmd);
}

void JavaDCmd::print_help(const char* name) const {
  GrowableArray<DCmdArgumentInfo*>* argument_infos = _factory._argument_infos;

  outputStream* out = output();
  int option_count = _factory._option_count;
  out->print("Syntax : %s %s", name, option_count == 0 ? "" : "[options]");

  for (int i = option_count; i < argument_infos->length(); i++) {
     DCmdArgumentInfo* info = argument_infos->at(i);
     if (info->is_mandatory()) {
       out->print(" <%s>", info->name());
     } else {
       out->print(" [<%s>]", info->name());
     }
  }
  out->cr();

  if (argument_infos->length() > option_count) {
    out->print_cr("\nArguments:");

    for (int i = _factory._option_count; i < argument_infos->length(); i++) {
       DCmdArgumentInfo* info = argument_infos->at(i);
       out->print("\t%s : %s %s (%s, ", info->name(),
                  info->is_mandatory() ? "" : "[optional]",
                  info->description(), info->type());
       if (info->default_string() != NULL) {
          out->print("%s", info->default_string());
       } else {
          out->print("no default value");
       }
        out->print_cr(")");
    }
  }

  if (option_count > 0) {
    out->print_cr("\nOptions: (options must be specified using the <key> or <key>=<value> syntax)");
    for (int i = 0; i < option_count; i++) {
       DCmdArgumentInfo* info = argument_infos->at(i);
       out->print("\t%s : %s %s (%s, ", info->name(),
                  info->is_mandatory() ? "" : "[optional]",
                  info->description(), info->type());
       if (info->default_string() != NULL) {
          out->print("%s", info->default_string());
       } else {
          out->print("no default value");
       }
       out->print_cr(")");
    }
  }
}

GrowableArray<const char*>* JavaDCmd::argument_name_array() const {
  return _factory._argument_names;
}

GrowableArray<DCmdArgumentInfo*>* JavaDCmd::argument_info_array() const {
  return _factory._argument_infos;
}

static int get_offset_of(oop o, Symbol* name, Symbol* sig) {
  fieldDescriptor fd;
  InstanceKlass* ik = InstanceKlass::cast(o->klass());
  bool found = ik->find_local_field(name, sig, &fd);
  assert(found, "sanity check");
  return fd.offset();
}

static oop get_oop_field(oop o, Symbol* name, Symbol* sig) {
  return o->obj_field(get_offset_of(o, name, sig));
}

static oop get_string_field(oop o, Symbol* name) {
  return get_oop_field(o, name, vmSymbols::string_signature());
}

static jint get_int_field(oop o, Symbol* name) {
  return o->int_field(get_offset_of(o, name, vmSymbols::int_signature()));
}

static jboolean get_bool_field(oop o, Symbol* name) {
  return o->bool_field(get_offset_of(o, name, vmSymbols::bool_signature()));
}

static char* to_native_string(oop string, bool null_if_empty = false) {
  size_t length = java_lang_String::utf8_length(string);
  if (length == 0 && null_if_empty) {
    return NULL;
  }
  char* result = AllocateHeap(length + 1, mtInternal);
  java_lang_String::as_utf8_string(string, result, (int) length + 1);
  return result;
}

static void fill_argument_info(GrowableArray<const char*>* argument_names,
                               GrowableArray<DCmdArgumentInfo*>* argument_infos,
                               oop meta) {
  oop name = get_string_field(meta, vmSymbols::name_name());
  oop description = get_string_field(meta, vmSymbols::description_name());
  int ordinal = get_int_field(meta, vmSymbols::ordinal_name());
  oop defaultValue = get_string_field(meta, vmSymbols::defaultValue_name());
  bool isMandatory = get_bool_field(meta, vmSymbols::isMandatory_name());
  oop type = get_string_field(meta, vmSymbols::type_name());

  argument_names->append(to_native_string(name));


  DCmdArgumentInfo* info = new(ResourceObj::C_HEAP, mtInternal)
                             DCmdArgumentInfo(to_native_string(name),
                                              to_native_string(description),
                                              to_native_string(type),
                                              to_native_string(defaultValue, true),
                                              isMandatory,
                                              ordinal != -1,
                                              false,
                                              ordinal);
  argument_infos->append(info);
}

void DCmdRegistrant::register_java_dcmd(jobject app_factory, TRAPS) {
  HandleMark hm(THREAD);
  oop o = JNIHandles::resolve_non_null(app_factory);
  int export_flags = get_int_field(o, vmSymbols::flags_name());
  bool enabled = get_bool_field(o, vmSymbols::factory_enabled_name());
  oop disabled_message = get_string_field(o, vmSymbols::factory_disabledMessage_name());
  oop cmd = get_oop_field(o, vmSymbols::factory_command_name(), vmSymbols::cmdMeta_signature());
  oop name = get_string_field(cmd, vmSymbols::name_name());
  oop description = get_string_field(cmd, vmSymbols::description_name());
  oop impact = get_string_field(cmd, vmSymbols::impact_name());
  oop permission_class = get_string_field(cmd, vmSymbols::permissionClass_name());
  oop permission_name = get_string_field(cmd, vmSymbols::permissionName_name());
  oop permission_action = get_string_field(cmd, vmSymbols::permissionAction_name());

  JavaPermission permission = {to_native_string(permission_class, true),
                               to_native_string(permission_name, true),
                               to_native_string(permission_action, true)};

  objArrayOop options = (objArrayOop)get_oop_field(o, vmSymbols::factory_options_name(),
                                                      vmSymbols::paramMeta_array_signature());
  objArrayOop arguments = (objArrayOop)get_oop_field(o, vmSymbols::factory_arguments_name(),
                                                        vmSymbols::paramMeta_array_signature());

  int num_of_arguments = options->length() + arguments->length();
  GrowableArray<const char*>* argument_names = new(ResourceObj::C_HEAP, mtInternal)
                                                   GrowableArray<const char*>(num_of_arguments, mtInternal);
  GrowableArray<DCmdArgumentInfo*>* argument_infos = new(ResourceObj::C_HEAP, mtInternal)
                                                   GrowableArray<DCmdArgumentInfo*>(num_of_arguments, mtInternal);

  for (int i = 0; i < options->length(); i++) {
    fill_argument_info(argument_names, argument_infos, options->obj_at(i));
  }

  for (int i = 0; i < arguments->length(); i++) {
    fill_argument_info(argument_names, argument_infos, arguments->obj_at(i));
  }

  Handle fh = Handle(THREAD, o);
  DCmdFactory::register_DCmdFactory(new JavaDCmdFactoryImpl(
                                      export_flags, enabled,
                                      num_of_arguments,
                                      to_native_string(name), to_native_string(description),
                                      to_native_string(impact), permission,
                                      to_native_string(disabled_message),
                                      argument_names,
                                      argument_infos,
                                      options->length(),
                                      JNIHandles::make_global(fh)));
}
