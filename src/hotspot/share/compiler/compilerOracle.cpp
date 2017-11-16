/*
 * Copyright (c) 1998, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "compiler/compilerOracle.hpp"
#include "compiler/methodMatcher.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/klass.hpp"
#include "oops/method.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/os.hpp"

enum OptionType {
  IntxType,
  UintxType,
  BoolType,
  CcstrType,
  DoubleType,
  UnknownType
};

/* Methods to map real type names to OptionType */
template<typename T>
static OptionType get_type_for() {
  return UnknownType;
};

template<> OptionType get_type_for<intx>() {
  return IntxType;
}

template<> OptionType get_type_for<uintx>() {
  return UintxType;
}

template<> OptionType get_type_for<bool>() {
  return BoolType;
}

template<> OptionType get_type_for<ccstr>() {
  return CcstrType;
}

template<> OptionType get_type_for<double>() {
  return DoubleType;
}

// this must parallel the command_names below
enum OracleCommand {
  UnknownCommand = -1,
  OracleFirstCommand = 0,
  BreakCommand = OracleFirstCommand,
  PrintCommand,
  ExcludeCommand,
  InlineCommand,
  DontInlineCommand,
  CompileOnlyCommand,
  LogCommand,
  OptionCommand,
  QuietCommand,
  HelpCommand,
  OracleCommandCount
};

// this must parallel the enum OracleCommand
static const char * command_names[] = {
  "break",
  "print",
  "exclude",
  "inline",
  "dontinline",
  "compileonly",
  "log",
  "option",
  "quiet",
  "help"
};

class MethodMatcher;
class TypedMethodOptionMatcher;

static BasicMatcher* lists[OracleCommandCount] = { 0, };
static TypedMethodOptionMatcher* option_list = NULL;
static bool any_set = false;

class TypedMethodOptionMatcher : public MethodMatcher {
 private:
  TypedMethodOptionMatcher* _next;
  const char*   _option;
  OptionType    _type;
 public:

  union {
    bool bool_value;
    intx intx_value;
    uintx uintx_value;
    double double_value;
    ccstr ccstr_value;
  } _u;

  TypedMethodOptionMatcher() : MethodMatcher(),
    _next(NULL),
    _type(UnknownType) {
      _option = NULL;
      memset(&_u, 0, sizeof(_u));
  }

  static TypedMethodOptionMatcher* parse_method_pattern(char*& line, const char*& error_msg);
  TypedMethodOptionMatcher* match(const methodHandle& method, const char* opt, OptionType type);

  void init(const char* opt, OptionType type, TypedMethodOptionMatcher* next) {
    _next = next;
    _type = type;
    _option = os::strdup_check_oom(opt);
  }

  void set_next(TypedMethodOptionMatcher* next) {_next = next; }
  TypedMethodOptionMatcher* next() { return _next; }
  OptionType type() { return _type; }
  template<typename T> T value();
  template<typename T> void set_value(T value);
  void print();
  void print_all();
  TypedMethodOptionMatcher* clone();
  ~TypedMethodOptionMatcher();
};

// A few templated accessors instead of a full template class.
template<> intx TypedMethodOptionMatcher::value<intx>() {
  return _u.intx_value;
}

template<> uintx TypedMethodOptionMatcher::value<uintx>() {
  return _u.uintx_value;
}

template<> bool TypedMethodOptionMatcher::value<bool>() {
  return _u.bool_value;
}

template<> double TypedMethodOptionMatcher::value<double>() {
  return _u.double_value;
}

template<> ccstr TypedMethodOptionMatcher::value<ccstr>() {
  return _u.ccstr_value;
}

template<> void TypedMethodOptionMatcher::set_value(intx value) {
  _u.intx_value = value;
}

template<> void TypedMethodOptionMatcher::set_value(uintx value) {
  _u.uintx_value = value;
}

template<> void TypedMethodOptionMatcher::set_value(double value) {
  _u.double_value = value;
}

template<> void TypedMethodOptionMatcher::set_value(bool value) {
  _u.bool_value = value;
}

template<> void TypedMethodOptionMatcher::set_value(ccstr value) {
  _u.ccstr_value = (const ccstr)os::strdup_check_oom(value);
}

void TypedMethodOptionMatcher::print() {
  ttyLocker ttyl;
  print_base(tty);
  switch (_type) {
  case IntxType:
    tty->print_cr(" intx %s = " INTX_FORMAT, _option, value<intx>());
    break;
  case UintxType:
    tty->print_cr(" uintx %s = " UINTX_FORMAT, _option, value<uintx>());
    break;
  case BoolType:
    tty->print_cr(" bool %s = %s", _option, value<bool>() ? "true" : "false");
    break;
  case DoubleType:
    tty->print_cr(" double %s = %f", _option, value<double>());
    break;
  case CcstrType:
    tty->print_cr(" const char* %s = '%s'", _option, value<ccstr>());
    break;
  default:
    ShouldNotReachHere();
  }
}

void TypedMethodOptionMatcher::print_all() {
   print();
   if (_next != NULL) {
     tty->print(" ");
     _next->print_all();
   }
 }

TypedMethodOptionMatcher* TypedMethodOptionMatcher::clone() {
  TypedMethodOptionMatcher* m = new TypedMethodOptionMatcher();
  m->_class_mode = _class_mode;
  m->_class_name = _class_name;
  m->_method_mode = _method_mode;
  m->_method_name = _method_name;
  m->_signature = _signature;
  // Need to ref count the symbols
  if (_class_name != NULL) {
    _class_name->increment_refcount();
  }
  if (_method_name != NULL) {
    _method_name->increment_refcount();
  }
  if (_signature != NULL) {
    _signature->increment_refcount();
  }
  return m;
}

TypedMethodOptionMatcher::~TypedMethodOptionMatcher() {
  if (_option != NULL) {
    os::free((void*)_option);
  }
}

TypedMethodOptionMatcher* TypedMethodOptionMatcher::parse_method_pattern(char*& line, const char*& error_msg) {
  assert(error_msg == NULL, "Dont call here with error_msg already set");
  TypedMethodOptionMatcher* tom = new TypedMethodOptionMatcher();
  MethodMatcher::parse_method_pattern(line, error_msg, tom);
  if (error_msg != NULL) {
    delete tom;
    return NULL;
  }
  return tom;
}

TypedMethodOptionMatcher* TypedMethodOptionMatcher::match(const methodHandle& method, const char* opt, OptionType type) {
  TypedMethodOptionMatcher* current = this;
  while (current != NULL) {
    // Fastest compare first.
    if (current->type() == type) {
      if (strcmp(current->_option, opt) == 0) {
        if (current->matches(method)) {
          return current;
        }
      }
    }
    current = current->next();
  }
  return NULL;
}

template<typename T>
static void add_option_string(TypedMethodOptionMatcher* matcher,
                                        const char* option,
                                        T value) {
  assert(matcher != option_list, "No circular lists please");
  matcher->init(option, get_type_for<T>(), option_list);
  matcher->set_value<T>(value);
  option_list = matcher;
  any_set = true;
  return;
}

static bool check_predicate(OracleCommand command, const methodHandle& method) {
  return ((lists[command] != NULL) &&
          !method.is_null() &&
          lists[command]->match(method));
}

static void add_predicate(OracleCommand command, BasicMatcher* bm) {
  assert(command != OptionCommand, "must use add_option_string");
  if (command == LogCommand && !LogCompilation && lists[LogCommand] == NULL) {
    tty->print_cr("Warning:  +LogCompilation must be enabled in order for individual methods to be logged.");
  }
  bm->set_next(lists[command]);
  lists[command] = bm;
  if ((command != DontInlineCommand) && (command != InlineCommand)) {
    any_set = true;
  }
  return;
}

template<typename T>
bool CompilerOracle::has_option_value(const methodHandle& method, const char* option, T& value) {
  if (option_list != NULL) {
    TypedMethodOptionMatcher* m = option_list->match(method, option, get_type_for<T>());
    if (m != NULL) {
      value = m->value<T>();
      return true;
    }
  }
  return false;
}

bool CompilerOracle::has_any_option() {
  return any_set;
}

// Explicit instantiation for all OptionTypes supported.
template bool CompilerOracle::has_option_value<intx>(const methodHandle& method, const char* option, intx& value);
template bool CompilerOracle::has_option_value<uintx>(const methodHandle& method, const char* option, uintx& value);
template bool CompilerOracle::has_option_value<bool>(const methodHandle& method, const char* option, bool& value);
template bool CompilerOracle::has_option_value<ccstr>(const methodHandle& method, const char* option, ccstr& value);
template bool CompilerOracle::has_option_value<double>(const methodHandle& method, const char* option, double& value);

bool CompilerOracle::has_option_string(const methodHandle& method, const char* option) {
  bool value = false;
  has_option_value(method, option, value);
  return value;
}

bool CompilerOracle::should_exclude(const methodHandle& method) {
  if (check_predicate(ExcludeCommand, method)) {
    return true;
  }
  if (lists[CompileOnlyCommand] != NULL) {
    return !lists[CompileOnlyCommand]->match(method);
  }
  return false;
}

bool CompilerOracle::should_inline(const methodHandle& method) {
  return (check_predicate(InlineCommand, method));
}

bool CompilerOracle::should_not_inline(const methodHandle& method) {
  return check_predicate(DontInlineCommand, method) || check_predicate(ExcludeCommand, method);
}

bool CompilerOracle::should_print(const methodHandle& method) {
  return check_predicate(PrintCommand, method);
}

bool CompilerOracle::should_print_methods() {
  return lists[PrintCommand] != NULL;
}

bool CompilerOracle::should_log(const methodHandle& method) {
  if (!LogCompilation)            return false;
  if (lists[LogCommand] == NULL)  return true;  // by default, log all
  return (check_predicate(LogCommand, method));
}

bool CompilerOracle::should_break_at(const methodHandle& method) {
  return check_predicate(BreakCommand, method);
}

static OracleCommand parse_command_name(const char * line, int* bytes_read) {
  assert(ARRAY_SIZE(command_names) == OracleCommandCount,
         "command_names size mismatch");

  *bytes_read = 0;
  char command[33];
  int result = sscanf(line, "%32[a-z]%n", command, bytes_read);
  for (uint i = 0; i < ARRAY_SIZE(command_names); i++) {
    if (strcmp(command, command_names[i]) == 0) {
      return (OracleCommand)i;
    }
  }
  return UnknownCommand;
}

static void usage() {
  tty->cr();
  tty->print_cr("The CompileCommand option enables the user of the JVM to control specific");
  tty->print_cr("behavior of the dynamic compilers. Many commands require a pattern that defines");
  tty->print_cr("the set of methods the command shall be applied to. The CompileCommand");
  tty->print_cr("option provides the following commands:");
  tty->cr();
  tty->print_cr("  break,<pattern>       - debug breakpoint in compiler and in generated code");
  tty->print_cr("  print,<pattern>       - print assembly");
  tty->print_cr("  exclude,<pattern>     - don't compile or inline");
  tty->print_cr("  inline,<pattern>      - always inline");
  tty->print_cr("  dontinline,<pattern>  - don't inline");
  tty->print_cr("  compileonly,<pattern> - compile only");
  tty->print_cr("  log,<pattern>         - log compilation");
  tty->print_cr("  option,<pattern>,<option type>,<option name>,<value>");
  tty->print_cr("                        - set value of custom option");
  tty->print_cr("  option,<pattern>,<bool option name>");
  tty->print_cr("                        - shorthand for setting boolean flag");
  tty->print_cr("  quiet                 - silence the compile command output");
  tty->print_cr("  help                  - print this text");
  tty->cr();
  tty->print_cr("The preferred format for the method matching pattern is:");
  tty->print_cr("  package/Class.method()");
  tty->cr();
  tty->print_cr("For backward compatibility this form is also allowed:");
  tty->print_cr("  package.Class::method()");
  tty->cr();
  tty->print_cr("The signature can be separated by an optional whitespace or comma:");
  tty->print_cr("  package/Class.method ()");
  tty->cr();
  tty->print_cr("The class and method identifier can be used together with leading or");
  tty->print_cr("trailing *'s for a small amount of wildcarding:");
  tty->print_cr("  *ackage/Clas*.*etho*()");
  tty->cr();
  tty->print_cr("It is possible to use more than one CompileCommand on the command line:");
  tty->print_cr("  -XX:CompileCommand=exclude,java/*.* -XX:CompileCommand=log,java*.*");
  tty->cr();
  tty->print_cr("The CompileCommands can be loaded from a file with the flag");
  tty->print_cr("-XX:CompileCommandFile=<file> or be added to the file '.hotspot_compiler'");
  tty->print_cr("Use the same format in the file as the argument to the CompileCommand flag.");
  tty->print_cr("Add one command on each line.");
  tty->print_cr("  exclude java/*.*");
  tty->print_cr("  option java/*.* ReplayInline");
  tty->cr();
  tty->print_cr("The following commands have conflicting behavior: 'exclude', 'inline', 'dontinline',");
  tty->print_cr("and 'compileonly'. There is no priority of commands. Applying (a subset of) these");
  tty->print_cr("commands to the same method results in undefined behavior.");
  tty->cr();
};

// Scan next flag and value in line, return MethodMatcher object on success, NULL on failure.
// On failure, error_msg contains description for the first error.
// For future extensions: set error_msg on first error.
static void scan_flag_and_value(const char* type, const char* line, int& total_bytes_read,
                                            TypedMethodOptionMatcher* matcher,
                                            char* errorbuf, const int buf_size) {
  total_bytes_read = 0;
  int bytes_read = 0;
  char flag[256];

  // Read flag name.
  if (sscanf(line, "%*[ \t]%255[a-zA-Z0-9]%n", flag, &bytes_read) == 1) {
    line += bytes_read;
    total_bytes_read += bytes_read;

    // Read value.
    if (strcmp(type, "intx") == 0) {
      intx value;
      if (sscanf(line, "%*[ \t]" INTX_FORMAT "%n", &value, &bytes_read) == 1) {
        total_bytes_read += bytes_read;
        add_option_string(matcher, flag, value);
        return;
      } else {
        jio_snprintf(errorbuf, buf_size, "  Value cannot be read for flag %s of type %s ", flag, type);
      }
    } else if (strcmp(type, "uintx") == 0) {
      uintx value;
      if (sscanf(line, "%*[ \t]" UINTX_FORMAT "%n", &value, &bytes_read) == 1) {
        total_bytes_read += bytes_read;
        add_option_string(matcher, flag, value);
        return;
      } else {
        jio_snprintf(errorbuf, buf_size, "  Value cannot be read for flag %s of type %s", flag, type);
      }
    } else if (strcmp(type, "ccstr") == 0) {
      ResourceMark rm;
      char* value = NEW_RESOURCE_ARRAY(char, strlen(line) + 1);
      if (sscanf(line, "%*[ \t]%255[_a-zA-Z0-9]%n", value, &bytes_read) == 1) {
        total_bytes_read += bytes_read;
        add_option_string(matcher, flag, (ccstr)value);
        return;
      } else {
        jio_snprintf(errorbuf, buf_size, "  Value cannot be read for flag %s of type %s", flag, type);
      }
    } else if (strcmp(type, "ccstrlist") == 0) {
      // Accumulates several strings into one. The internal type is ccstr.
      ResourceMark rm;
      char* value = NEW_RESOURCE_ARRAY(char, strlen(line) + 1);
      char* next_value = value;
      if (sscanf(line, "%*[ \t]%255[_a-zA-Z0-9]%n", next_value, &bytes_read) == 1) {
        total_bytes_read += bytes_read;
        line += bytes_read;
        next_value += bytes_read;
        char* end_value = next_value-1;
        while (sscanf(line, "%*[ \t]%255[_a-zA-Z0-9]%n", next_value, &bytes_read) == 1) {
          total_bytes_read += bytes_read;
          line += bytes_read;
          *end_value = ' '; // override '\0'
          next_value += bytes_read;
          end_value = next_value-1;
        }
        add_option_string(matcher, flag, (ccstr)value);
        return;
      } else {
        jio_snprintf(errorbuf, buf_size, "  Value cannot be read for flag %s of type %s", flag, type);
      }
    } else if (strcmp(type, "bool") == 0) {
      char value[256];
      if (sscanf(line, "%*[ \t]%255[a-zA-Z]%n", value, &bytes_read) == 1) {
        if (strcmp(value, "true") == 0) {
          total_bytes_read += bytes_read;
          add_option_string(matcher, flag, true);
          return;
        } else if (strcmp(value, "false") == 0) {
          total_bytes_read += bytes_read;
          add_option_string(matcher, flag, false);
          return;
        } else {
          jio_snprintf(errorbuf, buf_size, "  Value cannot be read for flag %s of type %s", flag, type);
        }
      } else {
        jio_snprintf(errorbuf, buf_size, "  Value cannot be read for flag %s of type %s", flag, type);
      }
    } else if (strcmp(type, "double") == 0) {
      char buffer[2][256];
      // Decimal separator '.' has been replaced with ' ' or '/' earlier,
      // so read integer and fraction part of double value separately.
      if (sscanf(line, "%*[ \t]%255[0-9]%*[ /\t]%255[0-9]%n", buffer[0], buffer[1], &bytes_read) == 2) {
        char value[512] = "";
        jio_snprintf(value, sizeof(value), "%s.%s", buffer[0], buffer[1]);
        total_bytes_read += bytes_read;
        add_option_string(matcher, flag, atof(value));
        return;
      } else {
        jio_snprintf(errorbuf, buf_size, "  Value cannot be read for flag %s of type %s", flag, type);
      }
    } else {
      jio_snprintf(errorbuf, buf_size, "  Type %s not supported ", type);
    }
  } else {
    jio_snprintf(errorbuf, buf_size, "  Flag name for type %s should be alphanumeric ", type);
  }
  return;
}

int skip_whitespace(char* line) {
  // Skip any leading spaces
  int whitespace_read = 0;
  sscanf(line, "%*[ \t]%n", &whitespace_read);
  return whitespace_read;
}

void CompilerOracle::print_parse_error(const char*&  error_msg, char* original_line) {
  assert(error_msg != NULL, "Must have error_message");

  ttyLocker ttyl;
  tty->print_cr("CompileCommand: An error occurred during parsing");
  tty->print_cr("Line: %s", original_line);
  tty->print_cr("Error: %s", error_msg);
  CompilerOracle::print_tip();
}

void CompilerOracle::parse_from_line(char* line) {
  if (line[0] == '\0') return;
  if (line[0] == '#')  return;

  char* original_line = line;
  int bytes_read;
  OracleCommand command = parse_command_name(line, &bytes_read);
  line += bytes_read;
  ResourceMark rm;

  if (command == UnknownCommand) {
    ttyLocker ttyl;
    tty->print_cr("CompileCommand: unrecognized command");
    tty->print_cr("  \"%s\"", original_line);
    CompilerOracle::print_tip();
    return;
  }

  if (command == QuietCommand) {
    _quiet = true;
    return;
  }

  if (command == HelpCommand) {
    usage();
    return;
  }

  const char* error_msg = NULL;
  if (command == OptionCommand) {
    // Look for trailing options.
    //
    // Two types of trailing options are
    // supported:
    //
    // (1) CompileCommand=option,Klass::method,flag
    // (2) CompileCommand=option,Klass::method,type,flag,value
    //
    // Type (1) is used to enable a boolean flag for a method.
    //
    // Type (2) is used to support options with a value. Values can have the
    // the following types: intx, uintx, bool, ccstr, ccstrlist, and double.
    //
    // For future extensions: extend scan_flag_and_value()

    char option[256]; // stores flag for Type (1) and type of Type (2)
    line++; // skip the ','
    TypedMethodOptionMatcher* archetype = TypedMethodOptionMatcher::parse_method_pattern(line, error_msg);
    if (archetype == NULL) {
      assert(error_msg != NULL, "Must have error_message");
      print_parse_error(error_msg, original_line);
      return;
    }

    line += skip_whitespace(line);

    // This is unnecessarily complex. Should retire multi-option lines and skip while loop
    while (sscanf(line, "%255[a-zA-Z0-9]%n", option, &bytes_read) == 1) {
      line += bytes_read;

      // typed_matcher is used as a blueprint for each option, deleted at the end
      TypedMethodOptionMatcher* typed_matcher = archetype->clone();
      if (strcmp(option, "intx") == 0
          || strcmp(option, "uintx") == 0
          || strcmp(option, "bool") == 0
          || strcmp(option, "ccstr") == 0
          || strcmp(option, "ccstrlist") == 0
          || strcmp(option, "double") == 0
          ) {
        char errorbuf[1024] = {0};
        // Type (2) option: parse flag name and value.
        scan_flag_and_value(option, line, bytes_read, typed_matcher, errorbuf, sizeof(errorbuf));
        if (*errorbuf != '\0') {
          error_msg = errorbuf;
          print_parse_error(error_msg, original_line);
          return;
        }
        line += bytes_read;
      } else {
        // Type (1) option
        add_option_string(typed_matcher, option, true);
      }
      if (typed_matcher != NULL && !_quiet) {
        // Print out the last match added
        assert(error_msg == NULL, "No error here");
        ttyLocker ttyl;
        tty->print("CompileCommand: %s ", command_names[command]);
        typed_matcher->print();
      }
      line += skip_whitespace(line);
    } // while(
    delete archetype;
  } else {  // not an OptionCommand)
    assert(error_msg == NULL, "Don't call here with error_msg already set");

    BasicMatcher* matcher = BasicMatcher::parse_method_pattern(line, error_msg);
    if (error_msg != NULL) {
      assert(matcher == NULL, "consistency");
      print_parse_error(error_msg, original_line);
      return;
    }

    add_predicate(command, matcher);
    if (!_quiet) {
      ttyLocker ttyl;
      tty->print("CompileCommand: %s ", command_names[command]);
      matcher->print(tty);
      tty->cr();
    }
  }
}

void CompilerOracle::print_tip() {
  tty->cr();
  tty->print_cr("Usage: '-XX:CompileCommand=command,\"package/Class.method()\"'");
  tty->print_cr("Use:   '-XX:CompileCommand=help' for more information.");
  tty->cr();
}

static const char* default_cc_file = ".hotspot_compiler";

static const char* cc_file() {
#ifdef ASSERT
  if (CompileCommandFile == NULL)
    return default_cc_file;
#endif
  return CompileCommandFile;
}

bool CompilerOracle::has_command_file() {
  return cc_file() != NULL;
}

bool CompilerOracle::_quiet = false;

void CompilerOracle::parse_from_file() {
  assert(has_command_file(), "command file must be specified");
  FILE* stream = fopen(cc_file(), "rt");
  if (stream == NULL) return;

  char token[1024];
  int  pos = 0;
  int  c = getc(stream);
  while(c != EOF && pos < (int)(sizeof(token)-1)) {
    if (c == '\n') {
      token[pos++] = '\0';
      parse_from_line(token);
      pos = 0;
    } else {
      token[pos++] = c;
    }
    c = getc(stream);
  }
  token[pos++] = '\0';
  parse_from_line(token);

  fclose(stream);
}

void CompilerOracle::parse_from_string(const char* str, void (*parse_line)(char*)) {
  char token[1024];
  int  pos = 0;
  const char* sp = str;
  int  c = *sp++;
  while (c != '\0' && pos < (int)(sizeof(token)-1)) {
    if (c == '\n') {
      token[pos++] = '\0';
      parse_line(token);
      pos = 0;
    } else {
      token[pos++] = c;
    }
    c = *sp++;
  }
  token[pos++] = '\0';
  parse_line(token);
}

void CompilerOracle::append_comment_to_file(const char* message) {
  assert(has_command_file(), "command file must be specified");
  fileStream stream(fopen(cc_file(), "at"));
  stream.print("# ");
  for (int index = 0; message[index] != '\0'; index++) {
    stream.put(message[index]);
    if (message[index] == '\n') stream.print("# ");
  }
  stream.cr();
}

void CompilerOracle::append_exclude_to_file(const methodHandle& method) {
  assert(has_command_file(), "command file must be specified");
  fileStream stream(fopen(cc_file(), "at"));
  stream.print("exclude ");
  method->method_holder()->name()->print_symbol_on(&stream);
  stream.print(".");
  method->name()->print_symbol_on(&stream);
  method->signature()->print_symbol_on(&stream);
  stream.cr();
  stream.cr();
}


void compilerOracle_init() {
  CompilerOracle::parse_from_string(CompileCommand, CompilerOracle::parse_from_line);
  CompilerOracle::parse_from_string(CompileOnly, CompilerOracle::parse_compile_only);
  if (CompilerOracle::has_command_file()) {
    CompilerOracle::parse_from_file();
  } else {
    struct stat buf;
    if (os::stat(default_cc_file, &buf) == 0) {
      warning("%s file is present but has been ignored.  "
              "Run with -XX:CompileCommandFile=%s to load the file.",
              default_cc_file, default_cc_file);
    }
  }
  if (lists[PrintCommand] != NULL) {
    if (PrintAssembly) {
      warning("CompileCommand and/or %s file contains 'print' commands, but PrintAssembly is also enabled", default_cc_file);
    } else if (FLAG_IS_DEFAULT(DebugNonSafepoints)) {
      warning("printing of assembly code is enabled; turning on DebugNonSafepoints to gain additional output");
      DebugNonSafepoints = true;
    }
  }
}


void CompilerOracle::parse_compile_only(char * line) {
  int i;
  char name[1024];
  const char* className = NULL;
  const char* methodName = NULL;

  bool have_colon = (strstr(line, "::") != NULL);
  char method_sep = have_colon ? ':' : '.';

  if (Verbose) {
    tty->print_cr("%s", line);
  }

  ResourceMark rm;
  while (*line != '\0') {
    MethodMatcher::Mode c_match = MethodMatcher::Exact;
    MethodMatcher::Mode m_match = MethodMatcher::Exact;

    for (i = 0;
         i < 1024 && *line != '\0' && *line != method_sep && *line != ',' && !isspace(*line);
         line++, i++) {
      name[i] = *line;
      if (name[i] == '.')  name[i] = '/';  // package prefix uses '/'
    }

    if (i > 0) {
      char* newName = NEW_RESOURCE_ARRAY( char, i + 1);
      if (newName == NULL)
        return;
      strncpy(newName, name, i);
      newName[i] = '\0';

      if (className == NULL) {
        className = newName;
      } else {
        methodName = newName;
      }
    }

    if (*line == method_sep) {
      if (className == NULL) {
        className = "";
        c_match = MethodMatcher::Any;
      }
    } else {
      // got foo or foo/bar
      if (className == NULL) {
        ShouldNotReachHere();
      } else {
        // missing class name handled as "Any" class match
        if (className[0] == '\0') {
          c_match = MethodMatcher::Any;
        }
      }
    }

    // each directive is terminated by , or NUL or . followed by NUL
    if (*line == ',' || *line == '\0' || (line[0] == '.' && line[1] == '\0')) {
      if (methodName == NULL) {
        methodName = "";
        if (*line != method_sep) {
          m_match = MethodMatcher::Any;
        }
      }

      EXCEPTION_MARK;
      Symbol* c_name = SymbolTable::new_symbol(className, CHECK);
      Symbol* m_name = SymbolTable::new_symbol(methodName, CHECK);
      Symbol* signature = NULL;

      BasicMatcher* bm = new BasicMatcher();
      bm->init(c_name, c_match, m_name, m_match, signature);
      add_predicate(CompileOnlyCommand, bm);
      if (PrintVMOptions) {
        tty->print("CompileOnly: compileonly ");
        lists[CompileOnlyCommand]->print_all(tty);
      }

      className = NULL;
      methodName = NULL;
    }

    line = *line == '\0' ? line : line + 1;
  }
}
