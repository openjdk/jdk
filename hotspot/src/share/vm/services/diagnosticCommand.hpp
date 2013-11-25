/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "services/diagnosticCommand_ext.hpp"
#include "utilities/macros.hpp"

class HelpDCmd : public DCmdWithParser {
protected:
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
  static const char* impact() { return "Low"; }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

class VersionDCmd : public DCmd {
public:
  VersionDCmd(outputStream* output, bool heap) : DCmd(output,heap) { }
  static const char* name() { return "VM.version"; }
  static const char* description() {
    return "Print JVM version information.";
  }
  static const char* impact() { return "Low"; }
  static const JavaPermission permission() {
    JavaPermission p = {"java.util.PropertyPermission",
                        "java.vm.version", "read"};
    return p;
  }
  static int num_arguments() { return 0; }
  virtual void execute(DCmdSource source, TRAPS);
};

class CommandLineDCmd : public DCmd {
public:
  CommandLineDCmd(outputStream* output, bool heap) : DCmd(output, heap) { }
  static const char* name() { return "VM.command_line"; }
  static const char* description() {
    return "Print the command line used to start this VM instance.";
  }
  static const char* impact() { return "Low"; }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments() { return 0; }
  virtual void execute(DCmdSource source, TRAPS) {
    Arguments::print_on(_output);
  }
};

// See also: get_system_properties in attachListener.cpp
class PrintSystemPropertiesDCmd : public DCmd {
public:
  PrintSystemPropertiesDCmd(outputStream* output, bool heap) : DCmd(output, heap) { }
    static const char* name() { return "VM.system_properties"; }
    static const char* description() {
      return "Print system properties.";
    }
    static const char* impact() {
      return "Low";
    }
    static const JavaPermission permission() {
      JavaPermission p = {"java.util.PropertyPermission",
                          "*", "read"};
      return p;
    }
    static int num_arguments() { return 0; }
    virtual void execute(DCmdSource source, TRAPS);
};

// See also: print_flag in attachListener.cpp
class PrintVMFlagsDCmd : public DCmdWithParser {
protected:
  DCmdArgument<bool> _all;
public:
  PrintVMFlagsDCmd(outputStream* output, bool heap);
  static const char* name() { return "VM.flags"; }
  static const char* description() {
    return "Print VM flag options and their current values.";
  }
  static const char* impact() {
    return "Low";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

class VMUptimeDCmd : public DCmdWithParser {
protected:
  DCmdArgument<bool> _date;
public:
  VMUptimeDCmd(outputStream* output, bool heap);
  static const char* name() { return "VM.uptime"; }
  static const char* description() {
    return "Print VM uptime.";
  }
  static const char* impact() {
    return "Low";
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

class SystemGCDCmd : public DCmd {
public:
  SystemGCDCmd(outputStream* output, bool heap) : DCmd(output, heap) { }
    static const char* name() { return "GC.run"; }
    static const char* description() {
      return "Call java.lang.System.gc().";
    }
    static const char* impact() {
      return "Medium: Depends on Java heap size and content.";
    }
    static int num_arguments() { return 0; }
    virtual void execute(DCmdSource source, TRAPS);
};

class RunFinalizationDCmd : public DCmd {
public:
  RunFinalizationDCmd(outputStream* output, bool heap) : DCmd(output, heap) { }
    static const char* name() { return "GC.run_finalization"; }
    static const char* description() {
      return "Call java.lang.System.runFinalization().";
    }
    static const char* impact() {
      return "Medium: Depends on Java content.";
    }
    static int num_arguments() { return 0; }
    virtual void execute(DCmdSource source, TRAPS);
};

#if INCLUDE_SERVICES   // Heap dumping supported
// See also: dump_heap in attachListener.cpp
class HeapDumpDCmd : public DCmdWithParser {
protected:
  DCmdArgument<char*> _filename;
  DCmdArgument<bool>  _all;
public:
  HeapDumpDCmd(outputStream* output, bool heap);
  static const char* name() {
    return "GC.heap_dump";
  }
  static const char* description() {
    return "Generate a HPROF format dump of the Java heap.";
  }
  static const char* impact() {
    return "High: Depends on Java heap size and content. "
           "Request a full GC unless the '-all' option is specified.";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};
#endif // INCLUDE_SERVICES

// See also: inspectheap in attachListener.cpp
class ClassHistogramDCmd : public DCmdWithParser {
protected:
  DCmdArgument<bool> _all;
public:
  ClassHistogramDCmd(outputStream* output, bool heap);
  static const char* name() {
    return "GC.class_histogram";
  }
  static const char* description() {
    return "Provide statistics about the Java heap usage.";
  }
  static const char* impact() {
    return "High: Depends on Java heap size and content.";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

class ClassStatsDCmd : public DCmdWithParser {
protected:
  DCmdArgument<bool> _all;
  DCmdArgument<bool> _csv;
  DCmdArgument<bool> _help;
  DCmdArgument<char*> _columns;
public:
  ClassStatsDCmd(outputStream* output, bool heap);
  static const char* name() {
    return "GC.class_stats";
  }
  static const char* description() {
    return "Provide statistics about Java class meta data. Requires -XX:+UnlockDiagnosticVMOptions.";
  }
  static const char* impact() {
    return "High: Depends on Java heap size and content.";
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

// See also: thread_dump in attachListener.cpp
class ThreadDumpDCmd : public DCmdWithParser {
protected:
  DCmdArgument<bool> _locks;
public:
  ThreadDumpDCmd(outputStream* output, bool heap);
  static const char* name() { return "Thread.print"; }
  static const char* description() {
    return "Print all threads with stacktraces.";
  }
  static const char* impact() {
    return "Medium: Depends on the number of threads.";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

// Enhanced JMX Agent support

class JMXStartRemoteDCmd : public DCmdWithParser {

  // Explicitly list all properties that could be
  // passed to Agent.startRemoteManagementAgent()
  // com.sun.management is omitted

  DCmdArgument<char *> _config_file;
  DCmdArgument<char *> _jmxremote_port;
  DCmdArgument<char *> _jmxremote_rmi_port;
  DCmdArgument<char *> _jmxremote_ssl;
  DCmdArgument<char *> _jmxremote_registry_ssl;
  DCmdArgument<char *> _jmxremote_authenticate;
  DCmdArgument<char *> _jmxremote_password_file;
  DCmdArgument<char *> _jmxremote_access_file;
  DCmdArgument<char *> _jmxremote_login_config;
  DCmdArgument<char *> _jmxremote_ssl_enabled_cipher_suites;
  DCmdArgument<char *> _jmxremote_ssl_enabled_protocols;
  DCmdArgument<char *> _jmxremote_ssl_need_client_auth;
  DCmdArgument<char *> _jmxremote_ssl_config_file;

  // JDP support
  // Keep autodiscovery char* not bool to pass true/false
  // as property value to java level.
  DCmdArgument<char *> _jmxremote_autodiscovery;
  DCmdArgument<jlong>  _jdp_port;
  DCmdArgument<char *> _jdp_address;
  DCmdArgument<char *> _jdp_source_addr;
  DCmdArgument<jlong>  _jdp_ttl;
  DCmdArgument<jlong>  _jdp_pause;
  DCmdArgument<char *> _jdp_name;

public:
  JMXStartRemoteDCmd(outputStream *output, bool heap_allocated);

  static const char *name() {
    return "ManagementAgent.start";
  }

  static const char *description() {
    return "Start remote management agent.";
  }

  static int num_arguments();

  virtual void execute(DCmdSource source, TRAPS);

};

class JMXStartLocalDCmd : public DCmd {

  // Explicitly request start of local agent,
  // it will not be started by start dcmd


public:
  JMXStartLocalDCmd(outputStream *output, bool heap_allocated);

  static const char *name() {
    return "ManagementAgent.start_local";
  }

  static const char *description() {
    return "Start local management agent.";
  }

  virtual void execute(DCmdSource source, TRAPS);

};

class JMXStopRemoteDCmd : public DCmd {
public:
  JMXStopRemoteDCmd(outputStream *output, bool heap_allocated) :
  DCmd(output, heap_allocated) {
    // Do Nothing
  }

  static const char *name() {
    return "ManagementAgent.stop";
  }

  static const char *description() {
    return "Stop remote management agent.";
  }

  virtual void execute(DCmdSource source, TRAPS);
};

#endif // SHARE_VM_SERVICES_DIAGNOSTICCOMMAND_HPP
