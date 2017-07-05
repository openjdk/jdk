/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_ARGUMENTS_HPP
#define SHARE_VM_RUNTIME_ARGUMENTS_HPP

#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "runtime/perfData.hpp"
#include "utilities/debug.hpp"
#include "utilities/top.hpp"

// Arguments parses the command line and recognizes options

// Invocation API hook typedefs (these should really be defined in jni.hpp)
extern "C" {
  typedef void (JNICALL *abort_hook_t)(void);
  typedef void (JNICALL *exit_hook_t)(jint code);
  typedef jint (JNICALL *vfprintf_hook_t)(FILE *fp, const char *format, va_list args)  ATTRIBUTE_PRINTF(2, 0);
}

// Forward declarations

class SysClassPath;

// Element describing System and User (-Dkey=value flags) defined property.

class SystemProperty: public CHeapObj<mtInternal> {
 private:
  char*           _key;
  char*           _value;
  SystemProperty* _next;
  bool            _writeable;
  bool writeable()   { return _writeable; }

 public:
  // Accessors
  const char* key() const                   { return _key; }
  char* value() const                       { return _value; }
  SystemProperty* next() const              { return _next; }
  void set_next(SystemProperty* next)       { _next = next; }
  bool set_value(char *value) {
    if (writeable()) {
      if (_value != NULL) {
        FreeHeap(_value);
      }
      _value = AllocateHeap(strlen(value)+1, mtInternal);
      if (_value != NULL) {
        strcpy(_value, value);
      }
      return true;
    }
    return false;
  }

  void append_value(const char *value) {
    char *sp;
    size_t len = 0;
    if (value != NULL) {
      len = strlen(value);
      if (_value != NULL) {
        len += strlen(_value);
      }
      sp = AllocateHeap(len+2, mtInternal);
      if (sp != NULL) {
        if (_value != NULL) {
          strcpy(sp, _value);
          strcat(sp, os::path_separator());
          strcat(sp, value);
          FreeHeap(_value);
        } else {
          strcpy(sp, value);
        }
        _value = sp;
      }
    }
  }

  // Constructor
  SystemProperty(const char* key, const char* value, bool writeable) {
    if (key == NULL) {
      _key = NULL;
    } else {
      _key = AllocateHeap(strlen(key)+1, mtInternal);
      strcpy(_key, key);
    }
    if (value == NULL) {
      _value = NULL;
    } else {
      _value = AllocateHeap(strlen(value)+1, mtInternal);
      strcpy(_value, value);
    }
    _next = NULL;
    _writeable = writeable;
  }
};


// For use by -agentlib, -agentpath and -Xrun
class AgentLibrary : public CHeapObj<mtInternal> {
  friend class AgentLibraryList;
public:
  // Is this library valid or not. Don't rely on os_lib == NULL as statically
  // linked lib could have handle of RTLD_DEFAULT which == 0 on some platforms
  enum AgentState {
    agent_invalid = 0,
    agent_valid   = 1
  };

 private:
  char*           _name;
  char*           _options;
  void*           _os_lib;
  bool            _is_absolute_path;
  bool            _is_static_lib;
  AgentState      _state;
  AgentLibrary*   _next;

 public:
  // Accessors
  const char* name() const                  { return _name; }
  char* options() const                     { return _options; }
  bool is_absolute_path() const             { return _is_absolute_path; }
  void* os_lib() const                      { return _os_lib; }
  void set_os_lib(void* os_lib)             { _os_lib = os_lib; }
  AgentLibrary* next() const                { return _next; }
  bool is_static_lib() const                { return _is_static_lib; }
  void set_static_lib(bool is_static_lib)   { _is_static_lib = is_static_lib; }
  bool valid()                              { return (_state == agent_valid); }
  void set_valid()                          { _state = agent_valid; }
  void set_invalid()                        { _state = agent_invalid; }

  // Constructor
  AgentLibrary(const char* name, const char* options, bool is_absolute_path, void* os_lib) {
    _name = AllocateHeap(strlen(name)+1, mtInternal);
    strcpy(_name, name);
    if (options == NULL) {
      _options = NULL;
    } else {
      _options = AllocateHeap(strlen(options)+1, mtInternal);
      strcpy(_options, options);
    }
    _is_absolute_path = is_absolute_path;
    _os_lib = os_lib;
    _next = NULL;
    _state = agent_invalid;
    _is_static_lib = false;
  }
};

// maintain an order of entry list of AgentLibrary
class AgentLibraryList VALUE_OBJ_CLASS_SPEC {
 private:
  AgentLibrary*   _first;
  AgentLibrary*   _last;
 public:
  bool is_empty() const                     { return _first == NULL; }
  AgentLibrary* first() const               { return _first; }

  // add to the end of the list
  void add(AgentLibrary* lib) {
    if (is_empty()) {
      _first = _last = lib;
    } else {
      _last->_next = lib;
      _last = lib;
    }
    lib->_next = NULL;
  }

  // search for and remove a library known to be in the list
  void remove(AgentLibrary* lib) {
    AgentLibrary* curr;
    AgentLibrary* prev = NULL;
    for (curr = first(); curr != NULL; prev = curr, curr = curr->next()) {
      if (curr == lib) {
        break;
      }
    }
    assert(curr != NULL, "always should be found");

    if (curr != NULL) {
      // it was found, by-pass this library
      if (prev == NULL) {
        _first = curr->_next;
      } else {
        prev->_next = curr->_next;
      }
      if (curr == _last) {
        _last = prev;
      }
      curr->_next = NULL;
    }
  }

  AgentLibraryList() {
    _first = NULL;
    _last = NULL;
  }
};

// Helper class for controlling the lifetime of JavaVMInitArgs objects.
class ScopedVMInitArgs;

class Arguments : AllStatic {
  friend class VMStructs;
  friend class JvmtiExport;
  friend class CodeCacheExtensions;
 public:
  // Operation modi
  enum Mode {
    _int,       // corresponds to -Xint
    _mixed,     // corresponds to -Xmixed
    _comp       // corresponds to -Xcomp
  };

  enum ArgsRange {
    arg_unreadable = -3,
    arg_too_small  = -2,
    arg_too_big    = -1,
    arg_in_range   = 0
  };

 private:

  // an array containing all flags specified in the .hotspotrc file
  static char** _jvm_flags_array;
  static int    _num_jvm_flags;
  // an array containing all jvm arguments specified in the command line
  static char** _jvm_args_array;
  static int    _num_jvm_args;
  // string containing all java command (class/jarfile name and app args)
  static char* _java_command;

  // Property list
  static SystemProperty* _system_properties;

  // Quick accessor to System properties in the list:
  static SystemProperty *_sun_boot_library_path;
  static SystemProperty *_java_library_path;
  static SystemProperty *_java_home;
  static SystemProperty *_java_class_path;
  static SystemProperty *_sun_boot_class_path;

  // temporary: to emit warning if the default ext dirs are not empty.
  // remove this variable when the warning is no longer needed.
  static char* _ext_dirs;

  // java.vendor.url.bug, bug reporting URL for fatal errors.
  static const char* _java_vendor_url_bug;

  // sun.java.launcher, private property to provide information about
  // java launcher
  static const char* _sun_java_launcher;

  // sun.java.launcher.pid, private property
  static int    _sun_java_launcher_pid;

  // was this VM created via the -XXaltjvm=<path> option
  static bool   _sun_java_launcher_is_altjvm;

  // Option flags
  static bool   _has_profile;
  static const char*  _gc_log_filename;
  // Value of the conservative maximum heap alignment needed
  static size_t  _conservative_max_heap_alignment;

  static uintx _min_heap_size;

  // -Xrun arguments
  static AgentLibraryList _libraryList;
  static void add_init_library(const char* name, char* options)
    { _libraryList.add(new AgentLibrary(name, options, false, NULL)); }

  // -agentlib and -agentpath arguments
  static AgentLibraryList _agentList;
  static void add_init_agent(const char* name, char* options, bool absolute_path)
    { _agentList.add(new AgentLibrary(name, options, absolute_path, NULL)); }

  // Late-binding agents not started via arguments
  static void add_loaded_agent(AgentLibrary *agentLib)
    { _agentList.add(agentLib); }
  static void add_loaded_agent(const char* name, char* options, bool absolute_path, void* os_lib)
    { _agentList.add(new AgentLibrary(name, options, absolute_path, os_lib)); }

  // Operation modi
  static Mode _mode;
  static void set_mode_flags(Mode mode);
  static bool _java_compiler;
  static void set_java_compiler(bool arg) { _java_compiler = arg; }
  static bool java_compiler()   { return _java_compiler; }

  // -Xdebug flag
  static bool _xdebug_mode;
  static void set_xdebug_mode(bool arg) { _xdebug_mode = arg; }
  static bool xdebug_mode()             { return _xdebug_mode; }

  // Used to save default settings
  static bool _AlwaysCompileLoopMethods;
  static bool _UseOnStackReplacement;
  static bool _BackgroundCompilation;
  static bool _ClipInlining;
  static bool _CIDynamicCompilePriority;
  static intx _Tier3InvokeNotifyFreqLog;
  static intx _Tier4InvocationThreshold;

  // Tiered
  static void set_tiered_flags();
  // CMS/ParNew garbage collectors
  static void set_parnew_gc_flags();
  static void set_cms_and_parnew_gc_flags();
  // UseParallel[Old]GC
  static void set_parallel_gc_flags();
  // Garbage-First (UseG1GC)
  static void set_g1_gc_flags();
  // GC ergonomics
  static void set_conservative_max_heap_alignment();
  static void set_use_compressed_oops();
  static void set_use_compressed_klass_ptrs();
  static void select_gc();
  static void set_ergonomics_flags();
  static void set_shared_spaces_flags();
  // limits the given memory size by the maximum amount of memory this process is
  // currently allowed to allocate or reserve.
  static julong limit_by_allocatable_memory(julong size);
  // Setup heap size
  static void set_heap_size();
  // Set up runtime image flags
  static void set_runtime_image_flags();
  // Based on automatic selection criteria, should the
  // low pause collector be used.
  static bool should_auto_select_low_pause_collector();

  // Bytecode rewriting
  static void set_bytecode_flags();

  // Invocation API hooks
  static abort_hook_t     _abort_hook;
  static exit_hook_t      _exit_hook;
  static vfprintf_hook_t  _vfprintf_hook;

  // System properties
  static bool add_property(const char* prop);

  // Aggressive optimization flags.
  static void set_aggressive_opts_flags();

  // Argument parsing
  static void do_pd_flag_adjustments();
  static bool parse_argument(const char* arg, Flag::Flags origin);
  static bool process_argument(const char* arg, jboolean ignore_unrecognized, Flag::Flags origin);
  static void process_java_launcher_argument(const char*, void*);
  static void process_java_compiler_argument(char* arg);
  static jint parse_options_environment_variable(const char* name, ScopedVMInitArgs* vm_args);
  static jint parse_java_tool_options_environment_variable(ScopedVMInitArgs* vm_args);
  static jint parse_java_options_environment_variable(ScopedVMInitArgs* vm_args);
  static jint parse_vm_init_args(const JavaVMInitArgs *java_tool_options_args,
                                 const JavaVMInitArgs *java_options_args,
                                 const JavaVMInitArgs *cmd_line_args);
  static jint parse_each_vm_init_arg(const JavaVMInitArgs* args, SysClassPath* scp_p, bool* scp_assembly_required_p, Flag::Flags origin);
  static jint finalize_vm_init_args(SysClassPath* scp_p, bool scp_assembly_required);
  static bool is_bad_option(const JavaVMOption* option, jboolean ignore, const char* option_type);

  static bool is_bad_option(const JavaVMOption* option, jboolean ignore) {
    return is_bad_option(option, ignore, NULL);
  }

  static void describe_range_error(ArgsRange errcode);
  static ArgsRange check_memory_size(julong size, julong min_size);
  static ArgsRange parse_memory_size(const char* s, julong* long_arg,
                                     julong min_size);
  // Parse a string for a unsigned integer.  Returns true if value
  // is an unsigned integer greater than or equal to the minimum
  // parameter passed and returns the value in uintx_arg.  Returns
  // false otherwise, with uintx_arg undefined.
  static bool parse_uintx(const char* value, uintx* uintx_arg,
                          uintx min_size);

  // methods to build strings from individual args
  static void build_jvm_args(const char* arg);
  static void build_jvm_flags(const char* arg);
  static void add_string(char*** bldarray, int* count, const char* arg);
  static const char* build_resource_string(char** args, int count);

  static bool methodExists(
    char* className, char* methodName,
    int classesNum, char** classes, bool* allMethods,
    int methodsNum, char** methods, bool* allClasses
  );

  static void parseOnlyLine(
    const char* line,
    short* classesNum, short* classesMax, char*** classes, bool** allMethods,
    short* methodsNum, short* methodsMax, char*** methods, bool** allClasses
  );

  // Returns true if the string s is in the list of flags that have recently
  // been made obsolete.  If we detect one of these flags on the command
  // line, instead of failing we print a warning message and ignore the
  // flag.  This gives the user a release or so to stop using the flag.
  static bool is_newly_obsolete(const char* s, JDK_Version* buffer);

  static short  CompileOnlyClassesNum;
  static short  CompileOnlyClassesMax;
  static char** CompileOnlyClasses;
  static bool*  CompileOnlyAllMethods;

  static short  CompileOnlyMethodsNum;
  static short  CompileOnlyMethodsMax;
  static char** CompileOnlyMethods;
  static bool*  CompileOnlyAllClasses;

  static short  InterpretOnlyClassesNum;
  static short  InterpretOnlyClassesMax;
  static char** InterpretOnlyClasses;
  static bool*  InterpretOnlyAllMethods;

  static bool   CheckCompileOnly;

  static char*  SharedArchivePath;

 public:
  // Scale compile thresholds
  // Returns threshold scaled with CompileThresholdScaling
  static intx scaled_compile_threshold(intx threshold, double scale);
  static intx scaled_compile_threshold(intx threshold) {
    return scaled_compile_threshold(threshold, CompileThresholdScaling);
  }
  // Returns freq_log scaled with CompileThresholdScaling
  static intx scaled_freq_log(intx freq_log, double scale);
  static intx scaled_freq_log(intx freq_log) {
    return scaled_freq_log(freq_log, CompileThresholdScaling);
  }

  // Parses the arguments, first phase
  static jint parse(const JavaVMInitArgs* args);
  // Apply ergonomics
  static jint apply_ergo();
  // Adjusts the arguments after the OS have adjusted the arguments
  static jint adjust_after_os();

  static void set_gc_specific_flags();
  static inline bool gc_selected(); // whether a gc has been selected
  static void select_gc_ergonomically();

  // Check for consistency in the selection of the garbage collector.
  static bool check_gc_consistency();        // Check user-selected gc
  static void check_deprecated_gc_flags();
  // Check consistency or otherwise of VM argument settings
  static bool check_vm_args_consistency();
  // Used by os_solaris
  static bool process_settings_file(const char* file_name, bool should_exist, jboolean ignore_unrecognized);

  static size_t conservative_max_heap_alignment() { return _conservative_max_heap_alignment; }
  // Return the maximum size a heap with compressed oops can take
  static size_t max_heap_for_compressed_oops();

  // return a char* array containing all options
  static char** jvm_flags_array()          { return _jvm_flags_array; }
  static char** jvm_args_array()           { return _jvm_args_array; }
  static int num_jvm_flags()               { return _num_jvm_flags; }
  static int num_jvm_args()                { return _num_jvm_args; }
  // return the arguments passed to the Java application
  static const char* java_command()        { return _java_command; }

  // print jvm_flags, jvm_args and java_command
  static void print_on(outputStream* st);
  static void print_summary_on(outputStream* st);

  // convenient methods to obtain / print jvm_flags and jvm_args
  static const char* jvm_flags()           { return build_resource_string(_jvm_flags_array, _num_jvm_flags); }
  static const char* jvm_args()            { return build_resource_string(_jvm_args_array, _num_jvm_args); }
  static void print_jvm_flags_on(outputStream* st);
  static void print_jvm_args_on(outputStream* st);

  // -Dkey=value flags
  static SystemProperty*  system_properties()   { return _system_properties; }
  static const char*    get_property(const char* key);

  // -Djava.vendor.url.bug
  static const char* java_vendor_url_bug()  { return _java_vendor_url_bug; }

  // -Dsun.java.launcher
  static const char* sun_java_launcher()    { return _sun_java_launcher; }
  // Was VM created by a Java launcher?
  static bool created_by_java_launcher();
  // -Dsun.java.launcher.is_altjvm
  static bool sun_java_launcher_is_altjvm();
  // -Dsun.java.launcher.pid
  static int sun_java_launcher_pid()        { return _sun_java_launcher_pid; }

  // -Xloggc:<file>, if not specified will be NULL
  static const char* gc_log_filename()      { return _gc_log_filename; }

  // -Xprof
  static bool has_profile()                 { return _has_profile; }

  // -Xms
  static size_t min_heap_size()             { return _min_heap_size; }
  static void  set_min_heap_size(size_t v)  { _min_heap_size = v;  }

  // -Xrun
  static AgentLibrary* libraries()          { return _libraryList.first(); }
  static bool init_libraries_at_startup()   { return !_libraryList.is_empty(); }
  static void convert_library_to_agent(AgentLibrary* lib)
                                            { _libraryList.remove(lib);
                                              _agentList.add(lib); }

  // -agentlib -agentpath
  static AgentLibrary* agents()             { return _agentList.first(); }
  static bool init_agents_at_startup()      { return !_agentList.is_empty(); }

  // abort, exit, vfprintf hooks
  static abort_hook_t    abort_hook()       { return _abort_hook; }
  static exit_hook_t     exit_hook()        { return _exit_hook; }
  static vfprintf_hook_t vfprintf_hook()    { return _vfprintf_hook; }

  static bool GetCheckCompileOnly ()        { return CheckCompileOnly; }

  static const char* GetSharedArchivePath() { return SharedArchivePath; }

  static bool CompileMethod(char* className, char* methodName) {
    return
      methodExists(
        className, methodName,
        CompileOnlyClassesNum, CompileOnlyClasses, CompileOnlyAllMethods,
        CompileOnlyMethodsNum, CompileOnlyMethods, CompileOnlyAllClasses
      );
  }

  // Java launcher properties
  static void process_sun_java_launcher_properties(JavaVMInitArgs* args);

  // System properties
  static void init_system_properties();

  // Update/Initialize System properties after JDK version number is known
  static void init_version_specific_system_properties();

  // Property List manipulation
  static void PropertyList_add(SystemProperty *element);
  static void PropertyList_add(SystemProperty** plist, SystemProperty *element);
  static void PropertyList_add(SystemProperty** plist, const char* k, char* v);
  static void PropertyList_unique_add(SystemProperty** plist, const char* k, char* v) {
    PropertyList_unique_add(plist, k, v, false);
  }
  static void PropertyList_unique_add(SystemProperty** plist, const char* k, char* v, jboolean append);
  static const char* PropertyList_get_value(SystemProperty* plist, const char* key);
  static int  PropertyList_count(SystemProperty* pl);
  static const char* PropertyList_get_key_at(SystemProperty* pl,int index);
  static char* PropertyList_get_value_at(SystemProperty* pl,int index);

  // Miscellaneous System property value getter and setters.
  static void set_dll_dir(char *value) { _sun_boot_library_path->set_value(value); }
  static void set_java_home(char *value) { _java_home->set_value(value); }
  static void set_library_path(char *value) { _java_library_path->set_value(value); }
  static void set_ext_dirs(char *value)     { _ext_dirs = os::strdup_check_oom(value); }
  static void set_sysclasspath(char *value) { _sun_boot_class_path->set_value(value); }
  static void append_sysclasspath(const char *value) { _sun_boot_class_path->append_value(value); }

  static char* get_java_home() { return _java_home->value(); }
  static char* get_dll_dir() { return _sun_boot_library_path->value(); }
  static char* get_sysclasspath() { return _sun_boot_class_path->value(); }
  static char* get_ext_dirs()        { return _ext_dirs;  }
  static char* get_appclasspath() { return _java_class_path->value(); }
  static void  fix_appclasspath();


  // Operation modi
  static Mode mode()                { return _mode; }
  static bool is_interpreter_only() { return mode() == _int; }


  // Utility: copies src into buf, replacing "%%" with "%" and "%p" with pid.
  static bool copy_expand_pid(const char* src, size_t srclen, char* buf, size_t buflen);
};

bool Arguments::gc_selected() {
  return UseConcMarkSweepGC || UseG1GC || UseParallelGC || UseParallelOldGC || UseSerialGC;
}

// Disable options not supported in this release, with a warning if they
// were explicitly requested on the command-line
#define UNSUPPORTED_OPTION(opt, description)                    \
do {                                                            \
  if (opt) {                                                    \
    if (FLAG_IS_CMDLINE(opt)) {                                 \
      warning(description " is disabled in this release.");     \
    }                                                           \
    FLAG_SET_DEFAULT(opt, false);                               \
  }                                                             \
} while(0)

#endif // SHARE_VM_RUNTIME_ARGUMENTS_HPP
