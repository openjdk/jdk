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

#include "precompiled.hpp"
#include "services/diagnosticArgument.hpp"
#include "services/diagnosticCommand.hpp"
#include "services/diagnosticFramework.hpp"

HelpDCmd::HelpDCmd(outputStream* output, bool heap) : DCmd(output, heap),
  _all("-all", "Show help for all commands", "BOOLEAN", false, "false"),
  _cmd("command name", "The name of the command for which we want help",
        "STRING", false) {
  _dcmdparser.add_dcmd_option(&_all);
  _dcmdparser.add_dcmd_argument(&_cmd);
};

void HelpDCmd::parse(CmdLine* line, char delim, TRAPS) {
  _dcmdparser.parse(line, delim, CHECK);
}

void HelpDCmd::print_help(outputStream* out) {
  _dcmdparser.print_help(out, name());
}

void HelpDCmd::execute(TRAPS) {
  if (_all.value()) {
    GrowableArray<const char*>* cmd_list = DCmdFactory::DCmd_list();
    for (int i = 0; i < cmd_list->length(); i++) {
      DCmdFactory* factory = DCmdFactory::factory(cmd_list->at(i),
                                                  strlen(cmd_list->at(i)));
      if (!factory->is_hidden()) {
        output()->print_cr("%s%s", factory->name(),
                           factory->is_enabled() ? "" : " [disabled]");
        output()->print_cr("\t%s", factory->description());
        output()->cr();
      }
      factory = factory->next();
    }
  } else if (_cmd.has_value()) {
    DCmd* cmd = NULL;
    DCmdFactory* factory = DCmdFactory::factory(_cmd.value(),
                                                strlen(_cmd.value()));
    if (factory != NULL) {
      output()->print_cr("%s%s", factory->name(),
                         factory->is_enabled() ? "" : " [disabled]");
      output()->print_cr(factory->description());
      output()->print_cr("\nImpact: %s", factory->impact());
      cmd = factory->create_resource_instance(output());
      if (cmd != NULL) {
        DCmdMark mark(cmd);
        cmd->print_help(output());
      }
    } else {
      output()->print_cr("Help unavailable : '%s' : No such command", _cmd.value());
    }
  } else {
    output()->print_cr("The following commands are available:");
    GrowableArray<const char *>* cmd_list = DCmdFactory::DCmd_list();
    for (int i = 0; i < cmd_list->length(); i++) {
      DCmdFactory* factory = DCmdFactory::factory(cmd_list->at(i),
                                                  strlen(cmd_list->at(i)));
      if (!factory->is_hidden()) {
        output()->print_cr("%s%s", factory->name(),
                           factory->is_enabled() ? "" : " [disabled]");
      }
      factory = factory->_next;
    }
    output()->print_cr("\nFor more information about a specific command use 'help <command>'.");
  }
}

void HelpDCmd::reset(TRAPS) {
  _dcmdparser.reset(CHECK);
}

void HelpDCmd::cleanup() {
  _dcmdparser.cleanup();
}

int HelpDCmd::num_arguments() {
  ResourceMark rm;
  HelpDCmd* dcmd = new HelpDCmd(NULL, false);
  if (dcmd != NULL) {
    DCmdMark mark(dcmd);
    return dcmd->_dcmdparser.num_arguments();
  } else {
    return 0;
  }
}

GrowableArray<const char*>* HelpDCmd::argument_name_array() {
  return _dcmdparser.argument_name_array();
}

GrowableArray<DCmdArgumentInfo*>* HelpDCmd::argument_info_array() {
  return _dcmdparser.argument_info_array();
}

void VersionDCmd::execute(TRAPS) {
  output()->print_cr("%s version %s", Abstract_VM_Version::vm_name(),
          Abstract_VM_Version::vm_release());
  JDK_Version jdk_version = JDK_Version::current();
  if (jdk_version.update_version() > 0) {
    output()->print_cr("JDK %d.%d_%02d", jdk_version.major_version(),
            jdk_version.minor_version(), jdk_version.update_version());
  } else {
    output()->print_cr("JDK %d.%d", jdk_version.major_version(),
            jdk_version.minor_version());
  }
}
