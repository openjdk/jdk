/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SERVICES_DIAGNOSTICFRAMEWORK_HPP
#define SHARE_VM_SERVICES_DIAGNOSTICFRAMEWORK_HPP

#include "classfile/vmSymbols.hpp"
#include "memory/allocation.hpp"
#include "runtime/arguments.hpp"
#include "runtime/os.hpp"
#include "runtime/vm_version.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/ostream.hpp"


// CmdLine is the class used to handle a command line containing a single
// diagnostic command and its arguments. It provides methods to access the
// command name and the beginning of the arguments. The class is also
// able to identify commented command lines and the "stop" keyword
class CmdLine : public StackObj {
private:
  const char* _cmd;
  size_t      _cmd_len;
  const char* _args;
  size_t      _args_len;
public:
  CmdLine(const char* line, size_t len, bool no_command_name);
  const char* args_addr() const { return _args; }
  size_t args_len() const { return _args_len; }
  const char* cmd_addr() const { return _cmd; }
  size_t cmd_len() const { return _cmd_len; }
  bool is_empty() { return _cmd_len == 0; }
  bool is_executable() { return is_empty() || _cmd[0] != '#'; }
  bool is_stop() { return !is_empty() && strncmp("stop", _cmd, _cmd_len) == 0; }
};

// Iterator class taking a character string in input and returning a CmdLine
// instance for each command line. The argument delimiter has to be specified.
class DCmdIter : public StackObj {
  friend class DCmd;
private:
  const char* _str;
  char        _delim;
  size_t      _len;
  size_t      _cursor;
public:

  DCmdIter(const char* str, char delim) {
    _str = str;
    _delim = delim;
    _len = strlen(str);
    _cursor = 0;
  }
  bool has_next() { return _cursor < _len; }
  CmdLine next() {
    assert(_cursor <= _len, "Cannot iterate more");
    size_t n = _cursor;
    while (n < _len && _str[n] != _delim) n++;
    CmdLine line(&(_str[_cursor]), n - _cursor, false);
    _cursor = n + 1;
    // The default copy constructor of CmdLine is used to return a CmdLine
    // instance to the caller.
    return line;
  }
};

// Iterator class to iterate over diagnostic command arguments
class DCmdArgIter : public ResourceObj {
  const char* _buffer;
  size_t      _len;
  size_t      _cursor;
  const char* _key_addr;
  size_t      _key_len;
  const char* _value_addr;
  size_t      _value_len;
  char        _delim;
public:
  DCmdArgIter(const char* buf, size_t len, char delim) {
    _buffer = buf;
    _len = len;
    _delim = delim;
    _cursor = 0;
  }
  bool next(TRAPS);
  const char* key_addr() { return _key_addr; }
  size_t key_length() { return _key_len; }
  const char* value_addr() { return _value_addr; }
  size_t value_length() { return _value_len; }
};

// A DCmdInfo instance provides a description of a diagnostic command. It is
// used to export the description to the JMX interface of the framework.
class DCmdInfo : public ResourceObj {
protected:
  const char* _name;
  const char* _description;
  const char* _impact;
  int         _num_arguments;
  bool        _is_enabled;
public:
  DCmdInfo(const char* name,
          const char* description,
          const char* impact,
          int num_arguments,
          bool enabled) {
    this->_name = name;
    this->_description = description;
    this->_impact = impact;
    this->_num_arguments = num_arguments;
    this->_is_enabled = enabled;
  }
  const char* name() const { return _name; }
  const char* description() const { return _description; }
  const char* impact() const { return _impact; }
  int num_arguments() const { return _num_arguments; }
  bool is_enabled() const { return _is_enabled; }

  static bool by_name(void* name, DCmdInfo* info);
};

// A DCmdArgumentInfo instance provides a description of a diagnostic command
// argument. It is used to export the description to the JMX interface of the
// framework.
class DCmdArgumentInfo : public ResourceObj {
protected:
  const char* _name;
  const char* _description;
  const char* _type;
  const char* _default_string;
  bool        _mandatory;
  bool        _option;
  int         _position;
public:
  DCmdArgumentInfo(const char* name, const char* description, const char* type,
                   const char* default_string, bool mandatory, bool option) {
    this->_name = name;
    this->_description = description;
    this->_type = type;
    this->_default_string = default_string;
    this->_option = option;
    this->_mandatory = mandatory;
    this->_option = option;
    this->_position = -1;
  }
  DCmdArgumentInfo(const char* name, const char* description, const char* type,
                   const char* default_string, bool mandatory, bool option,
                   int position) {
    this->_name = name;
    this->_description = description;
    this->_type = type;
    this->_default_string = default_string;
    this->_option = option;
    this->_mandatory = mandatory;
    this->_option = option;
    this->_position = position;
  }
  const char* name() const { return _name; }
  const char* description() const { return _description; }
  const char* type() const { return _type; }
  const char* default_string() const { return _default_string; }
  bool is_mandatory() const { return _mandatory; }
  bool is_option() const { return _option; }
  int position() const { return _position; }
};

// The DCmdParser class can be used to create an argument parser for a
// diagnostic command. It is not mandatory to use it to parse arguments.
class DCmdParser {
private:
  GenDCmdArgument* _options;
  GenDCmdArgument* _arguments_list;
  char             _delim;
public:
  DCmdParser() {
    _options = NULL;
    _arguments_list = NULL;
    _delim = ' ';
  }
  void add_dcmd_option(GenDCmdArgument* arg);
  void add_dcmd_argument(GenDCmdArgument* arg);
  GenDCmdArgument* lookup_dcmd_option(const char* name, size_t len);
  GenDCmdArgument* arguments_list() { return _arguments_list; };
  void check(TRAPS);
  void parse(CmdLine* line, char delim, TRAPS);
  void print_help(outputStream* out, const char* cmd_name);
  void reset(TRAPS);
  void cleanup();
  int num_arguments();
  GrowableArray<const char*>* argument_name_array();
  GrowableArray<DCmdArgumentInfo*>* argument_info_array();
};

// The DCmd class is the parent class of all diagnostic commands
// Diagnostic command instances should not be instantiated directly but
// created using the associated factory. The factory can be retrieved with
// the DCmdFactory::getFactory() method.
// A diagnostic command instance can either be allocated in the resource Area
// or in the C-heap. Allocation in the resource area is recommended when the
// current thread is the only one which will access the diagnostic command
// instance. Allocation in the C-heap is required when the diagnostic command
// is accessed by several threads (for instance to perform asynchronous
// execution).
// To ensure a proper cleanup, it's highly recommended to use a DCmdMark for
// each diagnostic command instance. In case of a C-heap allocated diagnostic
// command instance, the DCmdMark must be created in the context of the last
// thread that will access the instance.
class DCmd : public ResourceObj {
protected:
  outputStream* _output;
  bool          _is_heap_allocated;
public:
  DCmd(outputStream* output, bool heap_allocated) {
    _output = output;
    _is_heap_allocated = heap_allocated;
  }

  static const char* name() { return "No Name";}
  static const char* description() { return "No Help";}
  static const char* disabled_message() { return "Diagnostic command currently disabled"; }
  static const char* impact() { return "Low: No impact"; }
  static int num_arguments() { return 0; }
  outputStream* output() { return _output; }
  bool is_heap_allocated()  { return _is_heap_allocated; }
  virtual void print_help(const char* name) {
    output()->print_cr("Syntax: %s", name);
  }
  virtual void parse(CmdLine* line, char delim, TRAPS) {
    DCmdArgIter iter(line->args_addr(), line->args_len(), delim);
    bool has_arg = iter.next(CHECK);
    if (has_arg) {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                "Unknown argument in diagnostic command");
    }
  }
  virtual void execute(TRAPS) { }
  virtual void reset(TRAPS) { }
  virtual void cleanup() { }

  // support for the JMX interface
  virtual GrowableArray<const char*>* argument_name_array() {
    GrowableArray<const char*>* array = new GrowableArray<const char*>(0);
    return array;
  }
  virtual GrowableArray<DCmdArgumentInfo*>* argument_info_array() {
    GrowableArray<DCmdArgumentInfo*>* array = new GrowableArray<DCmdArgumentInfo*>(0);
    return array;
  }

  // main method to invoke the framework
  static void parse_and_execute(outputStream* out, const char* cmdline,
                                char delim, TRAPS);
};

class DCmdWithParser : public DCmd {
protected:
  DCmdParser _dcmdparser;
public:
  DCmdWithParser (outputStream *output, bool heap=false) : DCmd(output, heap) { }
  static const char* name() { return "No Name";}
  static const char* description() { return "No Help";}
  static const char* disabled_message() { return "Diagnostic command currently disabled"; }
  static const char* impact() { return "Low: No impact"; }
  static int num_arguments() { return 0; }
  virtual void parse(CmdLine *line, char delim, TRAPS);
  virtual void execute(TRAPS) { }
  virtual void reset(TRAPS);
  virtual void cleanup();
  virtual void print_help(const char* name);
  virtual GrowableArray<const char*>* argument_name_array();
  virtual GrowableArray<DCmdArgumentInfo*>* argument_info_array();
};

class DCmdMark : public StackObj {
  DCmd* _ref;
public:
  DCmdMark(DCmd* cmd) { _ref = cmd; }
  ~DCmdMark() {
    if (_ref != NULL) {
      _ref->cleanup();
      if (_ref->is_heap_allocated()) {
        delete _ref;
      }
    }
  }
};

// Diagnostic commands are not directly instantiated but created with a factory.
// Each diagnostic command class has its own factory. The DCmdFactory class also
// manages the status of the diagnostic command (hidden, enabled). A DCmdFactory
// has to be registered to make the diagnostic command available (see
// management.cpp)
class DCmdFactory: public CHeapObj<mtInternal> {
private:
  static Mutex*       _dcmdFactory_lock;
  // Pointer to the next factory in the singly-linked list of registered
  // diagnostic commands
  DCmdFactory*        _next;
  // When disabled, a diagnostic command cannot be executed. Any attempt to
  // execute it will result in the printing of the disabled message without
  // instantiating the command.
  bool                _enabled;
  // When hidden, a diagnostic command doesn't appear in the list of commands
  // provided by the 'help' command.
  bool                _hidden;
  int                 _num_arguments;
  static DCmdFactory* _DCmdFactoryList;
public:
  DCmdFactory(int num_arguments, bool enabled, bool hidden) {
    _next = NULL;
    _enabled = enabled;
    _hidden = hidden;
    _num_arguments = num_arguments;
  }
  bool is_enabled() const { return _enabled; }
  void set_enabled(bool b) { _enabled = b; }
  bool is_hidden() const { return _hidden; }
  void set_hidden(bool b) { _hidden = b; }
  int num_arguments() { return _num_arguments; }
  DCmdFactory* next() { return _next; }
  virtual DCmd* create_Cheap_instance(outputStream* output) = 0;
  virtual DCmd* create_resource_instance(outputStream* output) = 0;
  virtual const char* name() const = 0;
  virtual const char* description() const = 0;
  virtual const char* impact() const = 0;
  virtual const char* disabled_message() const = 0;
  // Register a DCmdFactory to make a diagnostic command available.
  // Once registered, a diagnostic command must not be unregistered.
  // To prevent a diagnostic command from being executed, just set the
  // enabled flag to false.
  static int register_DCmdFactory(DCmdFactory* factory);
  static DCmdFactory* factory(const char* cmd, size_t len);
  // Returns a C-heap allocated diagnostic command for the given command line
  static DCmd* create_global_DCmd(CmdLine &line, outputStream* out, TRAPS);
  // Returns a resourceArea allocated diagnostic command for the given command line
  static DCmd* create_local_DCmd(CmdLine &line, outputStream* out, TRAPS);
  static GrowableArray<const char*>* DCmd_list();
  static GrowableArray<DCmdInfo*>* DCmdInfo_list();

  friend class HelpDCmd;
};

// Template to easily create DCmdFactory instances. See management.cpp
// where this template is used to create and register factories.
template <class DCmdClass> class DCmdFactoryImpl : public DCmdFactory {
public:
  DCmdFactoryImpl(bool enabled, bool hidden) :
    DCmdFactory(DCmdClass::num_arguments(), enabled, hidden) { }
  // Returns a C-heap allocated instance
  virtual DCmd* create_Cheap_instance(outputStream* output) {
    return new (ResourceObj::C_HEAP, mtInternal) DCmdClass(output, true);
  }
  // Returns a resourceArea allocated instance
  virtual DCmd* create_resource_instance(outputStream* output) {
    return new DCmdClass(output, false);
  }
  virtual const char* name() const {
    return DCmdClass::name();
  }
  virtual const char* description() const {
    return DCmdClass::description();
  }
  virtual const char* impact() const {
    return DCmdClass::impact();
  }
  virtual const char* disabled_message() const {
     return DCmdClass::disabled_message();
  }
};

// This class provides a convenient way to register Dcmds, without a need to change
// management.cpp every time. Body of these two methods resides in
// diagnosticCommand.cpp

class DCmdRegistrant : public AllStatic {

private:
    static void register_dcmds();
    static void register_dcmds_ext();

    friend class Management;
};

#endif // SHARE_VM_SERVICES_DIAGNOSTICFRAMEWORK_HPP
