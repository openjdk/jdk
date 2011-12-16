/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SERVICES_DIAGNOSTICCOMMAND_HPP
#define SHARE_VM_SERVICES_DIAGNOSTICCOMMAND_HPP

#include "runtime/arguments.hpp"
#include "classfile/vmSymbols.hpp"
#include "utilities/ostream.hpp"
#include "runtime/vm_version.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/os.hpp"
#include "services/diagnosticArgument.hpp"
#include "services/diagnosticCommand.hpp"
#include "services/diagnosticFramework.hpp"

class HelpDCmd : public DCmd {
protected:
  DCmdParser _dcmdparser;
  DCmdArgument<bool> _all;
  DCmdArgument<char*> _cmd;
public:
  HelpDCmd(outputStream* output, bool heap);
  static const char* name() { return "help"; }
  static const char* description() {
    return "For more information about a specific command use 'help <command>'. "
           "With no argument this will show a list of available commands. "
           "'help all' will show help for all commands.";
  }
  static const char* impact() { return "Low: "; }
  static int num_arguments();
  virtual void parse(CmdLine* line, char delim, TRAPS);
  virtual void execute(TRAPS);
  virtual void reset(TRAPS);
  virtual void cleanup();
  virtual void print_help(outputStream* out);
  virtual GrowableArray<const char*>* argument_name_array();
  virtual GrowableArray<DCmdArgumentInfo*>* argument_info_array();
};

class VersionDCmd : public DCmd {
public:
  VersionDCmd(outputStream* output, bool heap) : DCmd(output,heap) { }
  static const char* name() { return "VM.version"; }
  static const char* description() {
    return "Print JVM version information.";
  }
  static const char* impact() { return "Low: "; }
  static int num_arguments() { return 0; }
  virtual void parse(CmdLine* line, char delim, TRAPS) { }
  virtual void execute(TRAPS);
  virtual void print_help(outputStream* out) { }
};

#endif // SHARE_VM_SERVICES_DIAGNOSTICCOMMAND_HPP
