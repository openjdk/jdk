/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/vmSymbols.hpp"
#include "runtime/arguments.hpp"
#include "runtime/os.hpp"
#include "runtime/vmThread.hpp"
#include "services/diagnosticArgument.hpp"
#include "services/diagnosticCommand.hpp"
#include "services/diagnosticCommand_ext.hpp"
#include "services/diagnosticFramework.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "oops/method.hpp"

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

class SetVMFlagDCmd : public DCmdWithParser {
protected:
  DCmdArgument<char*> _flag;
  DCmdArgument<char*> _value;

public:
  SetVMFlagDCmd(outputStream* output, bool heap);
  static const char* name() { return "VM.set_flag"; }
  static const char* description() {
    return "Sets VM flag option using the provided value.";
  }
  static const char* impact() {
    return "Low";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "control", NULL};
    return p;
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

class JVMTIDataDumpDCmd : public DCmd {
public:
  JVMTIDataDumpDCmd(outputStream* output, bool heap) : DCmd(output, heap) { }
  static const char* name() { return "JVMTI.data_dump"; }
  static const char* description() {
    return "Signal the JVM to do a data-dump request for JVMTI.";
  }
  static const char* impact() {
    return "High";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments() { return 0; }
  virtual void execute(DCmdSource source, TRAPS);
};

class VMDynamicLibrariesDCmd : public DCmd {
public:
  VMDynamicLibrariesDCmd(outputStream* output, bool heap);
  static const char* name() {
    return "VM.dynlibs";
  }
  static const char* description() {
    return "Print loaded dynamic libraries.";
  }
  static const char* impact() {
    return "Low";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments() {
    return 0;
  };
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

class VMInfoDCmd : public DCmd {
public:
  VMInfoDCmd(outputStream* output, bool heap) : DCmd(output, heap) { }
  static const char* name() { return "VM.info"; }
  static const char* description() {
    return "Print information about JVM environment and status.";
  }
  static const char* impact() { return "Low"; }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments() { return 0; }
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

class HeapInfoDCmd : public DCmd {
public:
  HeapInfoDCmd(outputStream* output, bool heap) : DCmd(output, heap) { }
  static const char* name() { return "GC.heap_info"; }
  static const char* description() {
    return "Provide generic Java heap information.";
  }
  static const char* impact() {
    return "Medium";
  }
  static int num_arguments() { return 0; }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
      "monitor", NULL};
      return p;
  }

  virtual void execute(DCmdSource source, TRAPS);
};

class FinalizerInfoDCmd : public DCmd {
public:
  FinalizerInfoDCmd(outputStream* output, bool heap) : DCmd(output, heap) { }
  static const char* name() { return "GC.finalizer_info"; }
  static const char* description() {
    return "Provide information about Java finalization queue.";
  }
  static const char* impact() {
    return "Medium";
  }
  static int num_arguments() { return 0; }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
      "monitor", NULL};
      return p;
  }

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


class ClassHierarchyDCmd : public DCmdWithParser {
protected:
  DCmdArgument<bool> _print_interfaces; // true if inherited interfaces should be printed.
  DCmdArgument<bool> _print_subclasses; // true if subclasses of the specified classname should be printed.
  DCmdArgument<char*> _classname; // Optional single class name whose hierarchy should be printed.
public:
  ClassHierarchyDCmd(outputStream* output, bool heap);
  static const char* name() {
    return "VM.class_hierarchy";
  }
  static const char* description() {
    return "Print a list of all loaded classes, indented to show the class hiearchy. "
           "The name of each class is followed by the ClassLoaderData* of its ClassLoader, "
           "or \"null\" if loaded by the bootstrap class loader.";
  }
  static const char* impact() {
      return "Medium: Depends on number of loaded classes.";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments();
  virtual void execute(DCmdSource source, TRAPS);
};

class TouchedMethodsDCmd : public DCmdWithParser {
public:
  TouchedMethodsDCmd(outputStream* output, bool heap);
  static const char* name() {
    return "VM.print_touched_methods";
  }
  static const char* description() {
    return "Print all methods that have ever been touched during the lifetime of this JVM.";
  }
  static const char* impact() {
    return "Medium: Depends on Java content.";
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

// Print the JMX system status
class JMXStatusDCmd : public DCmd {
public:
  JMXStatusDCmd(outputStream *output, bool heap_allocated);

  static const char *name() {
    return "ManagementAgent.status";
  }

  static const char *description() {
    return "Print the management agent status.";
  }

  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }

  virtual void execute(DCmdSource source, TRAPS);

};

class RotateGCLogDCmd : public DCmd {
public:
  RotateGCLogDCmd(outputStream* output, bool heap) : DCmd(output, heap) {}
  static const char* name() { return "GC.rotate_log"; }
  static const char* description() {
    return "Force the GC log file to be rotated.";
  }
  static const char* impact() { return "Low"; }
  virtual void execute(DCmdSource source, TRAPS);
  static int num_arguments() { return 0; }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "control", NULL};
    return p;
  }
};

class CompileQueueDCmd : public DCmd {
public:
  CompileQueueDCmd(outputStream* output, bool heap) : DCmd(output, heap) {}
  static const char* name() {
    return "Compiler.queue";
  }
  static const char* description() {
    return "Print methods queued for compilation.";
  }
  static const char* impact() {
    return "Low";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments() { return 0; }
  virtual void execute(DCmdSource source, TRAPS);
};

class CodeListDCmd : public DCmd {
public:
  CodeListDCmd(outputStream* output, bool heap) : DCmd(output, heap) {}
  static const char* name() {
    return "Compiler.codelist";
  }
  static const char* description() {
    return "Print all compiled methods in code cache that are alive";
  }
  static const char* impact() {
    return "Medium";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments() { return 0; }
  virtual void execute(DCmdSource source, TRAPS);
};


class CodeCacheDCmd : public DCmd {
public:
  CodeCacheDCmd(outputStream* output, bool heap) : DCmd(output, heap) {}
  static const char* name() {
    return "Compiler.codecache";
  }
  static const char* description() {
    return "Print code cache layout and bounds.";
  }
  static const char* impact() {
    return "Low";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments() { return 0; }
  virtual void execute(DCmdSource source, TRAPS);
};

class CompilerDirectivesPrintDCmd : public DCmd {
public:
  CompilerDirectivesPrintDCmd(outputStream* output, bool heap) : DCmd(output, heap) {}
  static const char* name() {
    return "Compiler.directives_print";
  }
  static const char* description() {
    return "Print all active compiler directives.";
  }
  static const char* impact() {
    return "Low";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments() { return 0; }
  virtual void execute(DCmdSource source, TRAPS);
};

class CompilerDirectivesRemoveDCmd : public DCmd {
public:
  CompilerDirectivesRemoveDCmd(outputStream* output, bool heap) : DCmd(output, heap) {}
  static const char* name() {
    return "Compiler.directives_remove";
  }
  static const char* description() {
    return "Remove latest added compiler directive.";
  }
  static const char* impact() {
    return "Low";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments() { return 0; }
  virtual void execute(DCmdSource source, TRAPS);
};

class CompilerDirectivesAddDCmd : public DCmdWithParser {
protected:
  DCmdArgument<char*> _filename;
public:
  CompilerDirectivesAddDCmd(outputStream* output, bool heap);
  static const char* name() {
    return "Compiler.directives_add";
  }
  static const char* description() {
    return "Add compiler directives from file.";
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

class CompilerDirectivesClearDCmd : public DCmd {
public:
  CompilerDirectivesClearDCmd(outputStream* output, bool heap) : DCmd(output, heap) {}
  static const char* name() {
    return "Compiler.directives_clear";
  }
  static const char* description() {
    return "Remove all compiler directives.";
  }
  static const char* impact() {
    return "Low";
  }
  static const JavaPermission permission() {
    JavaPermission p = {"java.lang.management.ManagementPermission",
                        "monitor", NULL};
    return p;
  }
  static int num_arguments() { return 0; }
  virtual void execute(DCmdSource source, TRAPS);
};

#endif // SHARE_VM_SERVICES_DIAGNOSTICCOMMAND_HPP
