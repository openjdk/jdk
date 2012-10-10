/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/shared/vmGCOperations.hpp"
#include "runtime/javaCalls.hpp"
#include "services/diagnosticArgument.hpp"
#include "services/diagnosticCommand.hpp"
#include "services/diagnosticFramework.hpp"
#include "services/heapDumper.hpp"
#include "services/management.hpp"

void DCmdRegistrant::register_dcmds(){
  // Registration of the diagnostic commands
  // First boolean argument specifies if the command is enabled
  // Second boolean argument specifies if the command is hidden
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<HelpDCmd>(true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<VersionDCmd>(true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<CommandLineDCmd>(true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<PrintSystemPropertiesDCmd>(true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<PrintVMFlagsDCmd>(true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<VMUptimeDCmd>(true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<SystemGCDCmd>(true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<RunFinalizationDCmd>(true, false));
#if INCLUDE_SERVICES // Heap dumping supported
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<HeapDumpDCmd>(true, false));
#endif // INCLUDE_SERVICES
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<ClassHistogramDCmd>(true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<ThreadDumpDCmd>(true, false));

  //Enhanced JMX Agent Support
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JMXStartRemoteDCmd>(true,false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JMXStartLocalDCmd>(true,false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JMXStopRemoteDCmd>(true,false));

}

#ifndef HAVE_EXTRA_DCMD
void DCmdRegistrant::register_dcmds_ext(){
   // Do nothing here
}
#endif


HelpDCmd::HelpDCmd(outputStream* output, bool heap) : DCmdWithParser(output, heap),
  _all("-all", "Show help for all commands", "BOOLEAN", false, "false"),
  _cmd("command name", "The name of the command for which we want help",
        "STRING", false) {
  _dcmdparser.add_dcmd_option(&_all);
  _dcmdparser.add_dcmd_argument(&_cmd);
};

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
      output()->cr();
      cmd = factory->create_resource_instance(output());
      if (cmd != NULL) {
        DCmdMark mark(cmd);
        cmd->print_help(factory->name());
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

PrintVMFlagsDCmd::PrintVMFlagsDCmd(outputStream* output, bool heap) :
                                   DCmdWithParser(output, heap),
  _all("-all", "Print all flags supported by the VM", "BOOLEAN", false, "false") {
  _dcmdparser.add_dcmd_option(&_all);
}

void PrintVMFlagsDCmd::execute(TRAPS) {
  if (_all.value()) {
    CommandLineFlags::printFlags(output(), true);
  } else {
    CommandLineFlags::printSetFlags(output());
  }
}

int PrintVMFlagsDCmd::num_arguments() {
    ResourceMark rm;
    PrintVMFlagsDCmd* dcmd = new PrintVMFlagsDCmd(NULL, false);
    if (dcmd != NULL) {
      DCmdMark mark(dcmd);
      return dcmd->_dcmdparser.num_arguments();
    } else {
      return 0;
    }
}

void PrintSystemPropertiesDCmd::execute(TRAPS) {
  // load sun.misc.VMSupport
  Symbol* klass = vmSymbols::sun_misc_VMSupport();
  Klass* k = SystemDictionary::resolve_or_fail(klass, true, CHECK);
  instanceKlassHandle ik (THREAD, k);
  if (ik->should_be_initialized()) {
    ik->initialize(THREAD);
  }
  if (HAS_PENDING_EXCEPTION) {
    java_lang_Throwable::print(PENDING_EXCEPTION, output());
    output()->cr();
    CLEAR_PENDING_EXCEPTION;
    return;
  }

  // invoke the serializePropertiesToByteArray method
  JavaValue result(T_OBJECT);
  JavaCallArguments args;

  Symbol* signature = vmSymbols::serializePropertiesToByteArray_signature();
  JavaCalls::call_static(&result,
                         ik,
                         vmSymbols::serializePropertiesToByteArray_name(),
                         signature,
                         &args,
                         THREAD);
  if (HAS_PENDING_EXCEPTION) {
    java_lang_Throwable::print(PENDING_EXCEPTION, output());
    output()->cr();
    CLEAR_PENDING_EXCEPTION;
    return;
  }

  // The result should be a [B
  oop res = (oop)result.get_jobject();
  assert(res->is_typeArray(), "just checking");
  assert(typeArrayKlass::cast(res->klass())->element_type() == T_BYTE, "just checking");

  // copy the bytes to the output stream
  typeArrayOop ba = typeArrayOop(res);
  jbyte* addr = typeArrayOop(res)->byte_at_addr(0);
  output()->print_raw((const char*)addr, ba->length());
}

VMUptimeDCmd::VMUptimeDCmd(outputStream* output, bool heap) :
                           DCmdWithParser(output, heap),
  _date("-date", "Add a prefix with current date", "BOOLEAN", false, "false") {
  _dcmdparser.add_dcmd_option(&_date);
}

void VMUptimeDCmd::execute(TRAPS) {
  if (_date.value()) {
    output()->date_stamp(true, "", ": ");
  }
  output()->time_stamp().update_to(tty->time_stamp().ticks());
  output()->stamp();
  output()->print_cr(" s");
}

int VMUptimeDCmd::num_arguments() {
  ResourceMark rm;
  VMUptimeDCmd* dcmd = new VMUptimeDCmd(NULL, false);
  if (dcmd != NULL) {
    DCmdMark mark(dcmd);
    return dcmd->_dcmdparser.num_arguments();
  } else {
    return 0;
  }
}

void SystemGCDCmd::execute(TRAPS) {
  Universe::heap()->collect(GCCause::_java_lang_system_gc);
}

void RunFinalizationDCmd::execute(TRAPS) {
  Klass* k = SystemDictionary::resolve_or_fail(vmSymbols::java_lang_System(),
                                                 true, CHECK);
  instanceKlassHandle klass(THREAD, k);
  JavaValue result(T_VOID);
  JavaCalls::call_static(&result, klass,
                         vmSymbols::run_finalization_name(),
                         vmSymbols::void_method_signature(), CHECK);
}

#if INCLUDE_SERVICES // Heap dumping supported
HeapDumpDCmd::HeapDumpDCmd(outputStream* output, bool heap) :
                           DCmdWithParser(output, heap),
  _filename("filename","Name of the dump file", "STRING",true),
  _all("-all", "Dump all objects, including unreachable objects",
       "BOOLEAN", false, "false") {
  _dcmdparser.add_dcmd_option(&_all);
  _dcmdparser.add_dcmd_argument(&_filename);
}

void HeapDumpDCmd::execute(TRAPS) {
  // Request a full GC before heap dump if _all is false
  // This helps reduces the amount of unreachable objects in the dump
  // and makes it easier to browse.
  HeapDumper dumper(!_all.value() /* request GC if _all is false*/);
  int res = dumper.dump(_filename.value());
  if (res == 0) {
    output()->print_cr("Heap dump file created");
  } else {
    // heap dump failed
    ResourceMark rm;
    char* error = dumper.error_as_C_string();
    if (error == NULL) {
      output()->print_cr("Dump failed - reason unknown");
    } else {
      output()->print_cr("%s", error);
    }
  }
}

int HeapDumpDCmd::num_arguments() {
  ResourceMark rm;
  HeapDumpDCmd* dcmd = new HeapDumpDCmd(NULL, false);
  if (dcmd != NULL) {
    DCmdMark mark(dcmd);
    return dcmd->_dcmdparser.num_arguments();
  } else {
    return 0;
  }
}
#endif // INCLUDE_SERVICES

ClassHistogramDCmd::ClassHistogramDCmd(outputStream* output, bool heap) :
                                       DCmdWithParser(output, heap),
  _all("-all", "Inspect all objects, including unreachable objects",
       "BOOLEAN", false, "false") {
  _dcmdparser.add_dcmd_option(&_all);
}

void ClassHistogramDCmd::execute(TRAPS) {
  VM_GC_HeapInspection heapop(output(),
                              !_all.value() /* request full gc if false */,
                              true /* need_prologue */);
  VMThread::execute(&heapop);
}

int ClassHistogramDCmd::num_arguments() {
  ResourceMark rm;
  ClassHistogramDCmd* dcmd = new ClassHistogramDCmd(NULL, false);
  if (dcmd != NULL) {
    DCmdMark mark(dcmd);
    return dcmd->_dcmdparser.num_arguments();
  } else {
    return 0;
  }
}

ThreadDumpDCmd::ThreadDumpDCmd(outputStream* output, bool heap) :
                               DCmdWithParser(output, heap),
  _locks("-l", "print java.util.concurrent locks", "BOOLEAN", false, "false") {
  _dcmdparser.add_dcmd_option(&_locks);
}

void ThreadDumpDCmd::execute(TRAPS) {
  // thread stacks
  VM_PrintThreads op1(output(), _locks.value());
  VMThread::execute(&op1);

  // JNI global handles
  VM_PrintJNI op2(output());
  VMThread::execute(&op2);

  // Deadlock detection
  VM_FindDeadlocks op3(output());
  VMThread::execute(&op3);
}

int ThreadDumpDCmd::num_arguments() {
  ResourceMark rm;
  ThreadDumpDCmd* dcmd = new ThreadDumpDCmd(NULL, false);
  if (dcmd != NULL) {
    DCmdMark mark(dcmd);
    return dcmd->_dcmdparser.num_arguments();
  } else {
    return 0;
  }
}

// Enhanced JMX Agent support

JMXStartRemoteDCmd::JMXStartRemoteDCmd(outputStream *output, bool heap_allocated) :

  DCmdWithParser(output, heap_allocated),

  _config_file
  ("config.file",
   "set com.sun.management.config.file", "STRING", false),

  _jmxremote_port
  ("jmxremote.port",
   "set com.sun.management.jmxremote.port", "STRING", false),

  _jmxremote_rmi_port
  ("jmxremote.rmi.port",
   "set com.sun.management.jmxremote.rmi.port", "STRING", false),

  _jmxremote_ssl
  ("jmxremote.ssl",
   "set com.sun.management.jmxremote.ssl", "STRING", false),

  _jmxremote_registry_ssl
  ("jmxremote.registry.ssl",
   "set com.sun.management.jmxremote.registry.ssl", "STRING", false),

  _jmxremote_authenticate
  ("jmxremote.authenticate",
   "set com.sun.management.jmxremote.authenticate", "STRING", false),

  _jmxremote_password_file
  ("jmxremote.password.file",
   "set com.sun.management.jmxremote.password.file", "STRING", false),

  _jmxremote_access_file
  ("jmxremote.access.file",
   "set com.sun.management.jmxremote.access.file", "STRING", false),

  _jmxremote_login_config
  ("jmxremote.login.config",
   "set com.sun.management.jmxremote.login.config", "STRING", false),

  _jmxremote_ssl_enabled_cipher_suites
  ("jmxremote.ssl.enabled.cipher.suites",
   "set com.sun.management.jmxremote.ssl.enabled.cipher.suite", "STRING", false),

  _jmxremote_ssl_enabled_protocols
  ("jmxremote.ssl.enabled.protocols",
   "set com.sun.management.jmxremote.ssl.enabled.protocols", "STRING", false),

  _jmxremote_ssl_need_client_auth
  ("jmxremote.ssl.need.client.auth",
   "set com.sun.management.jmxremote.need.client.auth", "STRING", false),

  _jmxremote_ssl_config_file
  ("jmxremote.ssl.config.file",
   "set com.sun.management.jmxremote.ssl_config_file", "STRING", false)

  {
    _dcmdparser.add_dcmd_option(&_config_file);
    _dcmdparser.add_dcmd_option(&_jmxremote_port);
    _dcmdparser.add_dcmd_option(&_jmxremote_rmi_port);
    _dcmdparser.add_dcmd_option(&_jmxremote_ssl);
    _dcmdparser.add_dcmd_option(&_jmxremote_registry_ssl);
    _dcmdparser.add_dcmd_option(&_jmxremote_authenticate);
    _dcmdparser.add_dcmd_option(&_jmxremote_password_file);
    _dcmdparser.add_dcmd_option(&_jmxremote_access_file);
    _dcmdparser.add_dcmd_option(&_jmxremote_login_config);
    _dcmdparser.add_dcmd_option(&_jmxremote_ssl_enabled_cipher_suites);
    _dcmdparser.add_dcmd_option(&_jmxremote_ssl_enabled_protocols);
    _dcmdparser.add_dcmd_option(&_jmxremote_ssl_need_client_auth);
    _dcmdparser.add_dcmd_option(&_jmxremote_ssl_config_file);
}


int JMXStartRemoteDCmd::num_arguments() {
  ResourceMark rm;
  JMXStartRemoteDCmd* dcmd = new JMXStartRemoteDCmd(NULL, false);
  if (dcmd != NULL) {
    DCmdMark mark(dcmd);
    return dcmd->_dcmdparser.num_arguments();
  } else {
    return 0;
  }
}


void JMXStartRemoteDCmd::execute(TRAPS) {
    ResourceMark rm(THREAD);
    HandleMark hm(THREAD);

    // Load and initialize the sun.management.Agent class
    // invoke startRemoteManagementAgent(string) method to start
    // the remote management server.
    // throw java.lang.NoSuchMethodError if the method doesn't exist

    Handle loader = Handle(THREAD, SystemDictionary::java_system_loader());
    Klass* k = SystemDictionary::resolve_or_fail(vmSymbols::sun_management_Agent(), loader, Handle(), true, CHECK);
    instanceKlassHandle ik (THREAD, k);

    JavaValue result(T_VOID);

    // Pass all command line arguments to java as key=value,...
    // All checks are done on java side

    int len = 0;
    stringStream options;
    char comma[2] = {0,0};

    // Leave default values on Agent.class side and pass only
    // agruments explicitly set by user. All arguments passed
    // to jcmd override properties with the same name set by
    // command line with -D or by managmenent.properties
    // file.
#define PUT_OPTION(a) \
    if ( (a).is_set() ){ \
        options.print("%scom.sun.management.%s=%s", comma, (a).name(), (a).value()); \
        comma[0] = ','; \
    }

    PUT_OPTION(_config_file);
    PUT_OPTION(_jmxremote_port);
    PUT_OPTION(_jmxremote_rmi_port);
    PUT_OPTION(_jmxremote_ssl);
    PUT_OPTION(_jmxremote_registry_ssl);
    PUT_OPTION(_jmxremote_authenticate);
    PUT_OPTION(_jmxremote_password_file);
    PUT_OPTION(_jmxremote_access_file);
    PUT_OPTION(_jmxremote_login_config);
    PUT_OPTION(_jmxremote_ssl_enabled_cipher_suites);
    PUT_OPTION(_jmxremote_ssl_enabled_protocols);
    PUT_OPTION(_jmxremote_ssl_need_client_auth);
    PUT_OPTION(_jmxremote_ssl_config_file);

#undef PUT_OPTION

    Handle str = java_lang_String::create_from_str(options.as_string(), CHECK);
    JavaCalls::call_static(&result, ik, vmSymbols::startRemoteAgent_name(), vmSymbols::string_void_signature(), str, CHECK);
}

JMXStartLocalDCmd::JMXStartLocalDCmd(outputStream *output, bool heap_allocated) :
  DCmd(output, heap_allocated)
{
  // do nothing
}

void JMXStartLocalDCmd::execute(TRAPS) {
    ResourceMark rm(THREAD);
    HandleMark hm(THREAD);

    // Load and initialize the sun.management.Agent class
    // invoke startLocalManagementAgent(void) method to start
    // the local management server
    // throw java.lang.NoSuchMethodError if method doesn't exist

    Handle loader = Handle(THREAD, SystemDictionary::java_system_loader());
    Klass* k = SystemDictionary::resolve_or_fail(vmSymbols::sun_management_Agent(), loader, Handle(), true, CHECK);
    instanceKlassHandle ik (THREAD, k);

    JavaValue result(T_VOID);
    JavaCalls::call_static(&result, ik, vmSymbols::startLocalAgent_name(), vmSymbols::void_method_signature(), CHECK);
}


void JMXStopRemoteDCmd::execute(TRAPS) {
    ResourceMark rm(THREAD);
    HandleMark hm(THREAD);

    // Load and initialize the sun.management.Agent class
    // invoke stopRemoteManagementAgent method to stop the
    // management server
    // throw java.lang.NoSuchMethodError if method doesn't exist

    Handle loader = Handle(THREAD, SystemDictionary::java_system_loader());
    Klass* k = SystemDictionary::resolve_or_fail(vmSymbols::sun_management_Agent(), loader, Handle(), true, CHECK);
    instanceKlassHandle ik (THREAD, k);

    JavaValue result(T_VOID);
    JavaCalls::call_static(&result, ik, vmSymbols::stopRemoteAgent_name(), vmSymbols::void_method_signature(), CHECK);
}

