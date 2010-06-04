/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_compilerOracle.cpp.incl"

class MethodMatcher : public CHeapObj {
 public:
  enum Mode {
    Exact,
    Prefix = 1,
    Suffix = 2,
    Substring = Prefix | Suffix,
    Any,
    Unknown = -1
  };

 protected:
  jobject        _class_name;
  Mode           _class_mode;
  jobject        _method_name;
  Mode           _method_mode;
  jobject        _signature;
  MethodMatcher* _next;

  static bool match(symbolHandle candidate, symbolHandle match, Mode match_mode);

  symbolHandle class_name() const { return (symbolOop)JNIHandles::resolve_non_null(_class_name); }
  symbolHandle method_name() const { return (symbolOop)JNIHandles::resolve_non_null(_method_name); }
  symbolHandle signature() const { return (symbolOop)JNIHandles::resolve(_signature); }

 public:
  MethodMatcher(symbolHandle class_name, Mode class_mode,
                symbolHandle method_name, Mode method_mode,
                symbolHandle signature, MethodMatcher* next);
  MethodMatcher(symbolHandle class_name, symbolHandle method_name, MethodMatcher* next);

  // utility method
  MethodMatcher* find(methodHandle method) {
    symbolHandle class_name  = Klass::cast(method->method_holder())->name();
    symbolHandle method_name = method->name();
    for (MethodMatcher* current = this; current != NULL; current = current->_next) {
      if (match(class_name, current->class_name(), current->_class_mode) &&
          match(method_name, current->method_name(), current->_method_mode) &&
          (current->signature().is_null() || current->signature()() == method->signature())) {
        return current;
      }
    }
    return NULL;
  }

  bool match(methodHandle method) {
    return find(method) != NULL;
  }

  MethodMatcher* next() const { return _next; }

  static void print_symbol(symbolHandle h, Mode mode) {
    ResourceMark rm;

    if (mode == Suffix || mode == Substring || mode == Any) {
      tty->print("*");
    }
    if (mode != Any) {
      h()->print_symbol_on(tty);
    }
    if (mode == Prefix || mode == Substring) {
      tty->print("*");
    }
  }

  void print_base() {
    print_symbol(class_name(), _class_mode);
    tty->print(".");
    print_symbol(method_name(), _method_mode);
    if (!signature().is_null()) {
      tty->print(" ");
      signature()->print_symbol_on(tty);
    }
  }

  virtual void print() {
    print_base();
    tty->cr();
  }
};

MethodMatcher::MethodMatcher(symbolHandle class_name, symbolHandle method_name, MethodMatcher* next) {
  _class_name  = JNIHandles::make_global(class_name);
  _method_name = JNIHandles::make_global(method_name);
  _next        = next;
  _class_mode  = MethodMatcher::Exact;
  _method_mode = MethodMatcher::Exact;
  _signature   = NULL;
}


MethodMatcher::MethodMatcher(symbolHandle class_name, Mode class_mode,
                             symbolHandle method_name, Mode method_mode,
                             symbolHandle signature, MethodMatcher* next):
    _class_mode(class_mode)
  , _method_mode(method_mode)
  , _next(next)
  , _class_name(JNIHandles::make_global(class_name()))
  , _method_name(JNIHandles::make_global(method_name()))
  , _signature(JNIHandles::make_global(signature())) {
}

bool MethodMatcher::match(symbolHandle candidate, symbolHandle match, Mode match_mode) {
  if (match_mode == Any) {
    return true;
  }

  if (match_mode == Exact) {
    return candidate() == match();
  }

  ResourceMark rm;
  const char * candidate_string = candidate->as_C_string();
  const char * match_string = match->as_C_string();

  switch (match_mode) {
  case Prefix:
    return strstr(candidate_string, match_string) == candidate_string;

  case Suffix: {
    size_t clen = strlen(candidate_string);
    size_t mlen = strlen(match_string);
    return clen >= mlen && strcmp(candidate_string + clen - mlen, match_string) == 0;
  }

  case Substring:
    return strstr(candidate_string, match_string) != NULL;

  default:
    return false;
  }
}


class MethodOptionMatcher: public MethodMatcher {
  const char * option;
 public:
  MethodOptionMatcher(symbolHandle class_name, Mode class_mode,
                             symbolHandle method_name, Mode method_mode,
                             symbolHandle signature, const char * opt, MethodMatcher* next):
    MethodMatcher(class_name, class_mode, method_name, method_mode, signature, next) {
    option = opt;
  }

  bool match(methodHandle method, const char* opt) {
    MethodOptionMatcher* current = this;
    while (current != NULL) {
      current = (MethodOptionMatcher*)current->find(method);
      if (current == NULL) {
        return false;
      }
      if (strcmp(current->option, opt) == 0) {
        return true;
      }
      current = current->next();
    }
    return false;
  }

  MethodOptionMatcher* next() {
    return (MethodOptionMatcher*)_next;
  }

  virtual void print() {
    print_base();
    tty->print(" %s", option);
    tty->cr();
  }
};



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

static const char * command_name(OracleCommand command) {
  if (command < OracleFirstCommand || command >= OracleCommandCount) {
    return "unknown command";
  }
  return command_names[command];
}

class MethodMatcher;
static MethodMatcher* lists[OracleCommandCount] = { 0, };


static bool check_predicate(OracleCommand command, methodHandle method) {
  return ((lists[command] != NULL) &&
          !method.is_null() &&
          lists[command]->match(method));
}


static MethodMatcher* add_predicate(OracleCommand command,
                                    symbolHandle class_name, MethodMatcher::Mode c_mode,
                                    symbolHandle method_name, MethodMatcher::Mode m_mode,
                                    symbolHandle signature) {
  assert(command != OptionCommand, "must use add_option_string");
  if (command == LogCommand && !LogCompilation && lists[LogCommand] == NULL)
    tty->print_cr("Warning:  +LogCompilation must be enabled in order for individual methods to be logged.");
  lists[command] = new MethodMatcher(class_name, c_mode, method_name, m_mode, signature, lists[command]);
  return lists[command];
}



static MethodMatcher* add_option_string(symbolHandle class_name, MethodMatcher::Mode c_mode,
                                        symbolHandle method_name, MethodMatcher::Mode m_mode,
                                        symbolHandle signature,
                                        const char* option) {
  lists[OptionCommand] = new MethodOptionMatcher(class_name, c_mode, method_name, m_mode,
                                                 signature, option, lists[OptionCommand]);
  return lists[OptionCommand];
}


bool CompilerOracle::has_option_string(methodHandle method, const char* option) {
  return lists[OptionCommand] != NULL &&
    ((MethodOptionMatcher*)lists[OptionCommand])->match(method, option);
}


bool CompilerOracle::should_exclude(methodHandle method, bool& quietly) {
  quietly = true;
  if (lists[ExcludeCommand] != NULL) {
    if (lists[ExcludeCommand]->match(method)) {
      quietly = _quiet;
      return true;
    }
  }

  if (lists[CompileOnlyCommand] != NULL) {
    return !lists[CompileOnlyCommand]->match(method);
  }
  return false;
}


bool CompilerOracle::should_inline(methodHandle method) {
  return (check_predicate(InlineCommand, method));
}


bool CompilerOracle::should_not_inline(methodHandle method) {
  return (check_predicate(DontInlineCommand, method));
}


bool CompilerOracle::should_print(methodHandle method) {
  return (check_predicate(PrintCommand, method));
}


bool CompilerOracle::should_log(methodHandle method) {
  if (!LogCompilation)            return false;
  if (lists[LogCommand] == NULL)  return true;  // by default, log all
  return (check_predicate(LogCommand, method));
}


bool CompilerOracle::should_break_at(methodHandle method) {
  return check_predicate(BreakCommand, method);
}


static OracleCommand parse_command_name(const char * line, int* bytes_read) {
  assert(ARRAY_SIZE(command_names) == OracleCommandCount,
         "command_names size mismatch");

  *bytes_read = 0;
  char command[32];
  int result = sscanf(line, "%32[a-z]%n", command, bytes_read);
  for (uint i = 0; i < ARRAY_SIZE(command_names); i++) {
    if (strcmp(command, command_names[i]) == 0) {
      return (OracleCommand)i;
    }
  }
  return UnknownCommand;
}


static void usage() {
  tty->print_cr("  CompileCommand and the CompilerOracle allows simple control over");
  tty->print_cr("  what's allowed to be compiled.  The standard supported directives");
  tty->print_cr("  are exclude and compileonly.  The exclude directive stops a method");
  tty->print_cr("  from being compiled and compileonly excludes all methods except for");
  tty->print_cr("  the ones mentioned by compileonly directives.  The basic form of");
  tty->print_cr("  all commands is a command name followed by the name of the method");
  tty->print_cr("  in one of two forms: the standard class file format as in");
  tty->print_cr("  class/name.methodName or the PrintCompilation format");
  tty->print_cr("  class.name::methodName.  The method name can optionally be followed");
  tty->print_cr("  by a space then the signature of the method in the class file");
  tty->print_cr("  format.  Otherwise the directive applies to all methods with the");
  tty->print_cr("  same name and class regardless of signature.  Leading and trailing");
  tty->print_cr("  *'s in the class and/or method name allows a small amount of");
  tty->print_cr("  wildcarding.  ");
  tty->cr();
  tty->print_cr("  Examples:");
  tty->cr();
  tty->print_cr("  exclude java/lang/StringBuffer.append");
  tty->print_cr("  compileonly java/lang/StringBuffer.toString ()Ljava/lang/String;");
  tty->print_cr("  exclude java/lang/String*.*");
  tty->print_cr("  exclude *.toString");
}


// The characters allowed in a class or method name.  All characters > 0x7f
// are allowed in order to handle obfuscated class files (e.g. Volano)
#define RANGEBASE "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789$_<>" \
        "\x80\x81\x82\x83\x84\x85\x86\x87\x88\x89\x8a\x8b\x8c\x8d\x8e\x8f" \
        "\x90\x91\x92\x93\x94\x95\x96\x97\x98\x99\x9a\x9b\x9c\x9d\x9e\x9f" \
        "\xa0\xa1\xa2\xa3\xa4\xa5\xa6\xa7\xa8\xa9\xaa\xab\xac\xad\xae\xaf" \
        "\xb0\xb1\xb2\xb3\xb4\xb5\xb6\xb7\xb8\xb9\xba\xbb\xbc\xbd\xbe\xbf" \
        "\xc0\xc1\xc2\xc3\xc4\xc5\xc6\xc7\xc8\xc9\xca\xcb\xcc\xcd\xce\xcf" \
        "\xd0\xd1\xd2\xd3\xd4\xd5\xd6\xd7\xd8\xd9\xda\xdb\xdc\xdd\xde\xdf" \
        "\xe0\xe1\xe2\xe3\xe4\xe5\xe6\xe7\xe8\xe9\xea\xeb\xec\xed\xee\xef" \
        "\xf0\xf1\xf2\xf3\xf4\xf5\xf6\xf7\xf8\xf9\xfa\xfb\xfc\xfd\xfe\xff"

#define RANGE0 "[*" RANGEBASE "]"
#define RANGEDOT "[*" RANGEBASE ".]"
#define RANGESLASH "[*" RANGEBASE "/]"


// Accept several syntaxes for these patterns
//  original syntax
//   cmd  java.lang.String foo
//  PrintCompilation syntax
//   cmd  java.lang.String::foo
//  VM syntax
//   cmd  java/lang/String[. ]foo
//

static const char* patterns[] = {
  "%*[ \t]%255" RANGEDOT    " "     "%255"  RANGE0 "%n",
  "%*[ \t]%255" RANGEDOT   "::"     "%255"  RANGE0 "%n",
  "%*[ \t]%255" RANGESLASH "%*[ .]" "%255"  RANGE0 "%n",
};

static MethodMatcher::Mode check_mode(char name[], const char*& error_msg) {
  int match = MethodMatcher::Exact;
  while (name[0] == '*') {
    match |= MethodMatcher::Suffix;
    strcpy(name, name + 1);
  }

  if (strcmp(name, "*") == 0) return MethodMatcher::Any;

  size_t len = strlen(name);
  while (len > 0 && name[len - 1] == '*') {
    match |= MethodMatcher::Prefix;
    name[--len] = '\0';
  }

  if (strstr(name, "*") != NULL) {
    error_msg = "  Embedded * not allowed";
    return MethodMatcher::Unknown;
  }
  return (MethodMatcher::Mode)match;
}

static bool scan_line(const char * line,
                      char class_name[],  MethodMatcher::Mode* c_mode,
                      char method_name[], MethodMatcher::Mode* m_mode,
                      int* bytes_read, const char*& error_msg) {
  *bytes_read = 0;
  error_msg = NULL;
  for (uint i = 0; i < ARRAY_SIZE(patterns); i++) {
    if (2 == sscanf(line, patterns[i], class_name, method_name, bytes_read)) {
      *c_mode = check_mode(class_name, error_msg);
      *m_mode = check_mode(method_name, error_msg);
      return *c_mode != MethodMatcher::Unknown && *m_mode != MethodMatcher::Unknown;
    }
  }
  return false;
}



void CompilerOracle::parse_from_line(char* line) {
  if (line[0] == '\0') return;
  if (line[0] == '#')  return;

  bool have_colon = (strstr(line, "::") != NULL);
  for (char* lp = line; *lp != '\0'; lp++) {
    // Allow '.' to separate the class name from the method name.
    // This is the preferred spelling of methods:
    //      exclude java/lang/String.indexOf(I)I
    // Allow ',' for spaces (eases command line quoting).
    //      exclude,java/lang/String.indexOf
    // For backward compatibility, allow space as separator also.
    //      exclude java/lang/String indexOf
    //      exclude,java/lang/String,indexOf
    // For easy cut-and-paste of method names, allow VM output format
    // as produced by methodOopDesc::print_short_name:
    //      exclude java.lang.String::indexOf
    // For simple implementation convenience here, convert them all to space.
    if (have_colon) {
      if (*lp == '.')  *lp = '/';   // dots build the package prefix
      if (*lp == ':')  *lp = ' ';
    }
    if (*lp == ',' || *lp == '.')  *lp = ' ';
  }

  char* original_line = line;
  int bytes_read;
  OracleCommand command = parse_command_name(line, &bytes_read);
  line += bytes_read;

  if (command == QuietCommand) {
    _quiet = true;
    return;
  }

  if (command == HelpCommand) {
    usage();
    return;
  }

  MethodMatcher::Mode c_match = MethodMatcher::Exact;
  MethodMatcher::Mode m_match = MethodMatcher::Exact;
  char class_name[256];
  char method_name[256];
  char sig[1024];
  char errorbuf[1024];
  const char* error_msg = NULL;
  MethodMatcher* match = NULL;

  if (scan_line(line, class_name, &c_match, method_name, &m_match, &bytes_read, error_msg)) {
    EXCEPTION_MARK;
    symbolHandle c_name = oopFactory::new_symbol_handle(class_name, CHECK);
    symbolHandle m_name = oopFactory::new_symbol_handle(method_name, CHECK);
    symbolHandle signature;

    line += bytes_read;
    // there might be a signature following the method.
    // signatures always begin with ( so match that by hand
    if (1 == sscanf(line, "%*[ \t](%254[);/" RANGEBASE "]%n", sig + 1, &bytes_read)) {
      sig[0] = '(';
      line += bytes_read;
      signature = oopFactory::new_symbol_handle(sig, CHECK);
    }

    if (command == OptionCommand) {
      // Look for trailing options to support
      // ciMethod::has_option("string") to control features in the
      // compiler.  Multiple options may follow the method name.
      char option[256];
      while (sscanf(line, "%*[ \t]%255[a-zA-Z0-9]%n", option, &bytes_read) == 1) {
        if (match != NULL && !_quiet) {
          // Print out the last match added
          tty->print("CompilerOracle: %s ", command_names[command]);
          match->print();
        }
        match = add_option_string(c_name, c_match, m_name, m_match, signature, strdup(option));
        line += bytes_read;
      }
    } else {
      bytes_read = 0;
      sscanf(line, "%*[ \t]%n", &bytes_read);
      if (line[bytes_read] != '\0') {
        jio_snprintf(errorbuf, sizeof(errorbuf), "  Unrecognized text after command: %s", line);
        error_msg = errorbuf;
      } else {
        match = add_predicate(command, c_name, c_match, m_name, m_match, signature);
      }
    }
  }

  if (match != NULL) {
    if (!_quiet) {
      tty->print("CompilerOracle: %s ", command_names[command]);
      match->print();
    }
  } else {
    tty->print_cr("CompilerOracle: unrecognized line");
    tty->print_cr("  \"%s\"", original_line);
    if (error_msg != NULL) {
      tty->print_cr(error_msg);
    }
  }
}

static const char* cc_file() {
  if (CompileCommandFile == NULL)
    return ".hotspot_compiler";
  return CompileCommandFile;
}
bool CompilerOracle::_quiet = false;

void CompilerOracle::parse_from_file() {
  FILE* stream = fopen(cc_file(), "rt");
  if (stream == NULL) return;

  char token[1024];
  int  pos = 0;
  int  c = getc(stream);
  while(c != EOF) {
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
  while (c != '\0') {
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
  fileStream stream(fopen(cc_file(), "at"));
  stream.print("# ");
  for (int index = 0; message[index] != '\0'; index++) {
    stream.put(message[index]);
    if (message[index] == '\n') stream.print("# ");
  }
  stream.cr();
}

void CompilerOracle::append_exclude_to_file(methodHandle method) {
  fileStream stream(fopen(cc_file(), "at"));
  stream.print("exclude ");
  Klass::cast(method->method_holder())->name()->print_symbol_on(&stream);
  stream.print(".");
  method->name()->print_symbol_on(&stream);
  method->signature()->print_symbol_on(&stream);
  stream.cr();
  stream.cr();
}


void compilerOracle_init() {
  CompilerOracle::parse_from_string(CompileCommand, CompilerOracle::parse_from_line);
  CompilerOracle::parse_from_string(CompileOnly, CompilerOracle::parse_compile_only);
  CompilerOracle::parse_from_file();
  if (lists[PrintCommand] != NULL) {
    if (PrintAssembly) {
      warning("CompileCommand and/or .hotspot_compiler file contains 'print' commands, but PrintAssembly is also enabled");
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
    tty->print_cr(line);
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
        c_match = MethodMatcher::Prefix;
      } else {
        methodName = newName;
      }
    }

    if (*line == method_sep) {
      if (className == NULL) {
        className = "";
        c_match = MethodMatcher::Any;
      } else {
        // foo/bar.blah is an exact match on foo/bar, bar.blah is a suffix match on bar
        if (strchr(className, '/') != NULL) {
          c_match = MethodMatcher::Exact;
        } else {
          c_match = MethodMatcher::Suffix;
        }
      }
    } else {
      // got foo or foo/bar
      if (className == NULL) {
        ShouldNotReachHere();
      } else {
        // got foo or foo/bar
        if (strchr(className, '/') != NULL) {
          c_match = MethodMatcher::Prefix;
        } else if (className[0] == '\0') {
          c_match = MethodMatcher::Any;
        } else {
          c_match = MethodMatcher::Substring;
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
      symbolHandle c_name = oopFactory::new_symbol_handle(className, CHECK);
      symbolHandle m_name = oopFactory::new_symbol_handle(methodName, CHECK);
      symbolHandle signature;

      add_predicate(CompileOnlyCommand, c_name, c_match, m_name, m_match, signature);
      if (PrintVMOptions) {
        tty->print("CompileOnly: compileonly ");
        lists[CompileOnlyCommand]->print();
      }

      className = NULL;
      methodName = NULL;
    }

    line = *line == '\0' ? line : line + 1;
  }
}
