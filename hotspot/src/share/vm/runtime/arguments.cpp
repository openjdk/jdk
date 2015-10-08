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

#include "precompiled.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/javaAssertions.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "code/codeCacheExtensions.hpp"
#include "gc/shared/cardTableRS.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/taskqueue.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/universe.inline.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/arguments.hpp"
#include "runtime/arguments_ext.hpp"
#include "runtime/commandLineFlagConstraintList.hpp"
#include "runtime/commandLineFlagRangeList.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "runtime/vm_version.hpp"
#include "services/management.hpp"
#include "services/memTracker.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/macros.hpp"
#include "utilities/stringUtils.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmciRuntime.hpp"
#endif
#if INCLUDE_ALL_GCS
#include "gc/cms/compactibleFreeListSpace.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#endif // INCLUDE_ALL_GCS

// Note: This is a special bug reporting site for the JVM
#define DEFAULT_VENDOR_URL_BUG "http://bugreport.java.com/bugreport/crash.jsp"
#define DEFAULT_JAVA_LAUNCHER  "generic"

#define UNSUPPORTED_GC_OPTION(gc)                                     \
do {                                                                  \
  if (gc) {                                                           \
    if (FLAG_IS_CMDLINE(gc)) {                                        \
      warning(#gc " is not supported in this VM.  Using Serial GC."); \
    }                                                                 \
    FLAG_SET_DEFAULT(gc, false);                                      \
  }                                                                   \
} while(0)

char** Arguments::_jvm_flags_array              = NULL;
int    Arguments::_num_jvm_flags                = 0;
char** Arguments::_jvm_args_array               = NULL;
int    Arguments::_num_jvm_args                 = 0;
char*  Arguments::_java_command                 = NULL;
SystemProperty* Arguments::_system_properties   = NULL;
const char*  Arguments::_gc_log_filename        = NULL;
bool   Arguments::_has_profile                  = false;
size_t Arguments::_conservative_max_heap_alignment = 0;
size_t Arguments::_min_heap_size                = 0;
Arguments::Mode Arguments::_mode                = _mixed;
bool   Arguments::_java_compiler                = false;
bool   Arguments::_xdebug_mode                  = false;
const char*  Arguments::_java_vendor_url_bug    = DEFAULT_VENDOR_URL_BUG;
const char*  Arguments::_sun_java_launcher      = DEFAULT_JAVA_LAUNCHER;
int    Arguments::_sun_java_launcher_pid        = -1;
bool   Arguments::_sun_java_launcher_is_altjvm  = false;

// These parameters are reset in method parse_vm_init_args()
bool   Arguments::_AlwaysCompileLoopMethods     = AlwaysCompileLoopMethods;
bool   Arguments::_UseOnStackReplacement        = UseOnStackReplacement;
bool   Arguments::_BackgroundCompilation        = BackgroundCompilation;
bool   Arguments::_ClipInlining                 = ClipInlining;
intx   Arguments::_Tier3InvokeNotifyFreqLog     = Tier3InvokeNotifyFreqLog;
intx   Arguments::_Tier4InvocationThreshold     = Tier4InvocationThreshold;

char*  Arguments::SharedArchivePath             = NULL;

AgentLibraryList Arguments::_libraryList;
AgentLibraryList Arguments::_agentList;

abort_hook_t     Arguments::_abort_hook         = NULL;
exit_hook_t      Arguments::_exit_hook          = NULL;
vfprintf_hook_t  Arguments::_vfprintf_hook      = NULL;


SystemProperty *Arguments::_sun_boot_library_path = NULL;
SystemProperty *Arguments::_java_library_path = NULL;
SystemProperty *Arguments::_java_home = NULL;
SystemProperty *Arguments::_java_class_path = NULL;
SystemProperty *Arguments::_sun_boot_class_path = NULL;

char* Arguments::_ext_dirs = NULL;

// Check if head of 'option' matches 'name', and sets 'tail' to the remaining
// part of the option string.
static bool match_option(const JavaVMOption *option, const char* name,
                         const char** tail) {
  size_t len = strlen(name);
  if (strncmp(option->optionString, name, len) == 0) {
    *tail = option->optionString + len;
    return true;
  } else {
    return false;
  }
}

// Check if 'option' matches 'name'. No "tail" is allowed.
static bool match_option(const JavaVMOption *option, const char* name) {
  const char* tail = NULL;
  bool result = match_option(option, name, &tail);
  if (tail != NULL && *tail == '\0') {
    return result;
  } else {
    return false;
  }
}

// Return true if any of the strings in null-terminated array 'names' matches.
// If tail_allowed is true, then the tail must begin with a colon; otherwise,
// the option must match exactly.
static bool match_option(const JavaVMOption* option, const char** names, const char** tail,
  bool tail_allowed) {
  for (/* empty */; *names != NULL; ++names) {
    if (match_option(option, *names, tail)) {
      if (**tail == '\0' || tail_allowed && **tail == ':') {
        return true;
      }
    }
  }
  return false;
}

static void logOption(const char* opt) {
  if (PrintVMOptions) {
    jio_fprintf(defaultStream::output_stream(), "VM option '%s'\n", opt);
  }
}

// Process java launcher properties.
void Arguments::process_sun_java_launcher_properties(JavaVMInitArgs* args) {
  // See if sun.java.launcher, sun.java.launcher.is_altjvm or
  // sun.java.launcher.pid is defined.
  // Must do this before setting up other system properties,
  // as some of them may depend on launcher type.
  for (int index = 0; index < args->nOptions; index++) {
    const JavaVMOption* option = args->options + index;
    const char* tail;

    if (match_option(option, "-Dsun.java.launcher=", &tail)) {
      process_java_launcher_argument(tail, option->extraInfo);
      continue;
    }
    if (match_option(option, "-Dsun.java.launcher.is_altjvm=", &tail)) {
      if (strcmp(tail, "true") == 0) {
        _sun_java_launcher_is_altjvm = true;
      }
      continue;
    }
    if (match_option(option, "-Dsun.java.launcher.pid=", &tail)) {
      _sun_java_launcher_pid = atoi(tail);
      continue;
    }
  }
}

// Initialize system properties key and value.
void Arguments::init_system_properties() {
  PropertyList_add(&_system_properties, new SystemProperty("java.vm.specification.name",
                                                                 "Java Virtual Machine Specification",  false));
  PropertyList_add(&_system_properties, new SystemProperty("java.vm.version", VM_Version::vm_release(),  false));
  PropertyList_add(&_system_properties, new SystemProperty("java.vm.name", VM_Version::vm_name(),  false));
  PropertyList_add(&_system_properties, new SystemProperty("java.vm.info", VM_Version::vm_info_string(),  true));

  // Following are JVMTI agent writable properties.
  // Properties values are set to NULL and they are
  // os specific they are initialized in os::init_system_properties_values().
  _sun_boot_library_path = new SystemProperty("sun.boot.library.path", NULL,  true);
  _java_library_path = new SystemProperty("java.library.path", NULL,  true);
  _java_home =  new SystemProperty("java.home", NULL,  true);
  _sun_boot_class_path = new SystemProperty("sun.boot.class.path", NULL,  true);

  _java_class_path = new SystemProperty("java.class.path", "",  true);

  // Add to System Property list.
  PropertyList_add(&_system_properties, _sun_boot_library_path);
  PropertyList_add(&_system_properties, _java_library_path);
  PropertyList_add(&_system_properties, _java_home);
  PropertyList_add(&_system_properties, _java_class_path);
  PropertyList_add(&_system_properties, _sun_boot_class_path);

  // Set OS specific system properties values
  os::init_system_properties_values();

  JVMCI_ONLY(JVMCIRuntime::init_system_properties(&_system_properties);)
}

// Update/Initialize System properties after JDK version number is known
void Arguments::init_version_specific_system_properties() {
  enum { bufsz = 16 };
  char buffer[bufsz];
  const char* spec_vendor = "Oracle Corporation";
  uint32_t spec_version = JDK_Version::current().major_version();

  jio_snprintf(buffer, bufsz, "1." UINT32_FORMAT, spec_version);

  PropertyList_add(&_system_properties,
      new SystemProperty("java.vm.specification.vendor",  spec_vendor, false));
  PropertyList_add(&_system_properties,
      new SystemProperty("java.vm.specification.version", buffer, false));
  PropertyList_add(&_system_properties,
      new SystemProperty("java.vm.vendor", VM_Version::vm_vendor(),  false));
}

/*
 *  -XX argument processing:
 *
 *  -XX arguments are defined in several places, such as:
 *      globals.hpp, globals_<cpu>.hpp, globals_<os>.hpp, <compiler>_globals.hpp, or <gc>_globals.hpp.
 *  -XX arguments are parsed in parse_argument().
 *  -XX argument bounds checking is done in check_vm_args_consistency().
 *
 * Over time -XX arguments may change. There are mechanisms to handle common cases:
 *
 *      ALIASED: An option that is simply another name for another option. This is often
 *               part of the process of deprecating a flag, but not all aliases need
 *               to be deprecated.
 *
 *               Create an alias for an option by adding the old and new option names to the
 *               "aliased_jvm_flags" table. Delete the old variable from globals.hpp (etc).
 *
 *   DEPRECATED: An option that is supported, but a warning is printed to let the user know that
 *               support may be removed in the future. Both regular and aliased options may be
 *               deprecated.
 *
 *               Add a deprecation warning for an option (or alias) by adding an entry in the
 *               "special_jvm_flags" table and setting the "deprecated_in" field.
 *               Often an option "deprecated" in one major release will
 *               be made "obsolete" in the next. In this case the entry should also have it's
 *               "obsolete_in" field set.
 *
 *     OBSOLETE: An option that has been removed (and deleted from globals.hpp), but is still accepted
 *               on the command line. A warning is printed to let the user know that option might not
 *               be accepted in the future.
 *
 *               Add an obsolete warning for an option by adding an entry in the "special_jvm_flags"
 *               table and setting the "obsolete_in" field.
 *
 *      EXPIRED: A deprecated or obsolete option that has an "accept_until" version less than or equal
 *               to the current JDK version. The system will flatly refuse to admit the existence of
 *               the flag. This allows a flag to die automatically over JDK releases.
 *
 *               Note that manual cleanup of expired options should be done at major JDK version upgrades:
 *                  - Newly expired options should be removed from the special_jvm_flags and aliased_jvm_flags tables.
 *                  - Newly obsolete or expired deprecated options should have their global variable
 *                    definitions removed (from globals.hpp, etc) and related implementations removed.
 *
 * Recommended approach for removing options:
 *
 * To remove options commonly used by customers (e.g. product, commercial -XX options), use
 * the 3-step model adding major release numbers to the deprecate, obsolete and expire columns.
 *
 * To remove internal options (e.g. diagnostic, experimental, develop options), use
 * a 2-step model adding major release numbers to the obsolete and expire columns.
 *
 * To change the name of an option, use the alias table as well as a 2-step
 * model adding major release numbers to the deprecate and expire columns.
 * Think twice about aliasing commonly used customer options.
 *
 * There are times when it is appropriate to leave a future release number as undefined.
 *
 * Tests:  Aliases should be tested in VMAliasOptions.java.
 *         Deprecated options should be tested in VMDeprecatedOptions.java.
 */

// Obsolete or deprecated -XX flag.
typedef struct {
  const char* name;
  JDK_Version deprecated_in; // When the deprecation warning started (or "undefined").
  JDK_Version obsolete_in;   // When the obsolete warning started (or "undefined").
  JDK_Version expired_in;    // When the option expires (or "undefined").
} SpecialFlag;

// The special_jvm_flags table declares options that are being deprecated and/or obsoleted. The
// "deprecated_in" or "obsolete_in" fields may be set to "undefined", but not both.
// When the JDK version reaches 'deprecated_in' limit, the JVM will process this flag on
// the command-line as usual, but will issue a warning.
// When the JDK version reaches 'obsolete_in' limit, the JVM will continue accepting this flag on
// the command-line, while issuing a warning and ignoring the flag value.
// Once the JDK version reaches 'expired_in' limit, the JVM will flatly refuse to admit the
// existence of the flag.
//
// MANUAL CLEANUP ON JDK VERSION UPDATES:
// This table ensures that the handling of options will update automatically when the JDK
// version is incremented, but the source code needs to be cleanup up manually:
// - As "deprecated" options age into "obsolete" or "expired" options, the associated "globals"
//   variable should be removed, as well as users of the variable.
// - As "deprecated" options age into "obsolete" options, move the entry into the
//   "Obsolete Flags" section of the table.
// - All expired options should be removed from the table.
static SpecialFlag const special_jvm_flags[] = {
  // -------------- Deprecated Flags --------------
  // --- Non-alias flags - sorted by obsolete_in then expired_in:
  { "MaxGCMinorPauseMillis",        JDK_Version::jdk(8), JDK_Version::undefined(), JDK_Version::undefined() },
  { "UseParNewGC",                  JDK_Version::jdk(9), JDK_Version::undefined(), JDK_Version::jdk(10) },

  // --- Deprecated alias flags (see also aliased_jvm_flags) - sorted by obsolete_in then expired_in:
  { "DefaultMaxRAMFraction",        JDK_Version::jdk(8), JDK_Version::undefined(), JDK_Version::undefined() },
  { "CreateMinidumpOnCrash",        JDK_Version::jdk(9), JDK_Version::undefined(), JDK_Version::undefined() },
  { "CMSMarkStackSizeMax",          JDK_Version::jdk(9), JDK_Version::undefined(), JDK_Version::jdk(10) },
  { "CMSMarkStackSize",             JDK_Version::jdk(9), JDK_Version::undefined(), JDK_Version::jdk(10) },
  { "G1MarkStackSize",              JDK_Version::jdk(9), JDK_Version::undefined(), JDK_Version::jdk(10) },
  { "ParallelMarkingThreads",       JDK_Version::jdk(9), JDK_Version::undefined(), JDK_Version::jdk(10) },
  { "ParallelCMSThreads",           JDK_Version::jdk(9), JDK_Version::undefined(), JDK_Version::jdk(10) },

  // -------------- Obsolete Flags - sorted by expired_in --------------
  { "UseOldInlining",                JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "SafepointPollOffset",           JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "UseBoundThreads",               JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "DefaultThreadPriority",         JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "NoYieldsInMicrolock",           JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "BackEdgeThreshold",             JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "UseNewReflection",              JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "ReflectionWrapResolutionErrors",JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "VerifyReflectionBytecodes",     JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "AutoShutdownNMT",               JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "NmethodSweepFraction",          JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "NmethodSweepCheckInterval",     JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "CodeCacheMinimumFreeSpace",     JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
#ifndef ZERO
  { "UseFastAccessorMethods",        JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "UseFastEmptyMethods",           JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
#endif // ZERO
  { "UseCompilerSafepoints",         JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "AdaptiveSizePausePolicy",       JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "ParallelGCRetainPLAB",          JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "ThreadSafetyMargin",            JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "LazyBootClassLoader",           JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "StarvationMonitorInterval",     JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },
  { "PreInflateSpin",                JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(10) },

#ifdef TEST_VERIFY_SPECIAL_JVM_FLAGS
  { "dep > obs",                    JDK_Version::jdk(9), JDK_Version::jdk(8), JDK_Version::undefined() },
  { "dep > exp ",                   JDK_Version::jdk(9), JDK_Version::undefined(), JDK_Version::jdk(8) },
  { "obs > exp ",                   JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::jdk(8) },
  { "not deprecated or obsolete",   JDK_Version::undefined(), JDK_Version::undefined(), JDK_Version::jdk(9) },
  { "dup option",                   JDK_Version::jdk(9), JDK_Version::undefined(), JDK_Version::undefined() },
  { "dup option",                   JDK_Version::jdk(9), JDK_Version::undefined(), JDK_Version::undefined() },
  { "BytecodeVerificationRemote",   JDK_Version::undefined(), JDK_Version::jdk(9), JDK_Version::undefined() },
#endif

  { NULL, JDK_Version(0), JDK_Version(0) }
};

// Flags that are aliases for other flags.
typedef struct {
  const char* alias_name;
  const char* real_name;
} AliasedFlag;

static AliasedFlag const aliased_jvm_flags[] = {
  { "DefaultMaxRAMFraction",    "MaxRAMFraction"    },
  { "CMSMarkStackSizeMax",      "MarkStackSizeMax"  },
  { "CMSMarkStackSize",         "MarkStackSize"     },
  { "G1MarkStackSize",          "MarkStackSize"     },
  { "ParallelMarkingThreads",   "ConcGCThreads"     },
  { "ParallelCMSThreads",       "ConcGCThreads"     },
  { "CreateMinidumpOnCrash",    "CreateCoredumpOnCrash" },
  { NULL, NULL}
};

// Return true if "v" is less than "other", where "other" may be "undefined".
static bool version_less_than(JDK_Version v, JDK_Version other) {
  assert(!v.is_undefined(), "must be defined");
  if (!other.is_undefined() && v.compare(other) >= 0) {
    return false;
  } else {
    return true;
  }
}

static bool lookup_special_flag(const char *flag_name, SpecialFlag& flag) {
  for (size_t i = 0; special_jvm_flags[i].name != NULL; i++) {
    if ((strcmp(special_jvm_flags[i].name, flag_name) == 0)) {
      flag = special_jvm_flags[i];
      return true;
    }
  }
  return false;
}

bool Arguments::is_obsolete_flag(const char *flag_name, JDK_Version* version) {
  assert(version != NULL, "Must provide a version buffer");
  SpecialFlag flag;
  if (lookup_special_flag(flag_name, flag)) {
    if (!flag.obsolete_in.is_undefined()) {
      if (version_less_than(JDK_Version::current(), flag.expired_in)) {
        *version = flag.obsolete_in;
        return true;
      }
    }
  }
  return false;
}

int Arguments::is_deprecated_flag(const char *flag_name, JDK_Version* version) {
  assert(version != NULL, "Must provide a version buffer");
  SpecialFlag flag;
  if (lookup_special_flag(flag_name, flag)) {
    if (!flag.deprecated_in.is_undefined()) {
      if (version_less_than(JDK_Version::current(), flag.obsolete_in) &&
          version_less_than(JDK_Version::current(), flag.expired_in)) {
        *version = flag.deprecated_in;
        return 1;
      } else {
        return -1;
      }
    }
  }
  return 0;
}

const char* Arguments::real_flag_name(const char *flag_name) {
  for (size_t i = 0; aliased_jvm_flags[i].alias_name != NULL; i++) {
    const AliasedFlag& flag_status = aliased_jvm_flags[i];
    if (strcmp(flag_status.alias_name, flag_name) == 0) {
        return flag_status.real_name;
    }
  }
  return flag_name;
}

#ifndef PRODUCT
static bool lookup_special_flag(const char *flag_name, size_t skip_index) {
  for (size_t i = 0; special_jvm_flags[i].name != NULL; i++) {
    if ((i != skip_index) && (strcmp(special_jvm_flags[i].name, flag_name) == 0)) {
      return true;
    }
  }
  return false;
}

static bool verify_special_jvm_flags() {
  bool success = true;
  for (size_t i = 0; special_jvm_flags[i].name != NULL; i++) {
    const SpecialFlag& flag = special_jvm_flags[i];
    if (lookup_special_flag(flag.name, i)) {
      warning("Duplicate special flag declaration \"%s\"", flag.name);
      success = false;
    }
    if (flag.deprecated_in.is_undefined() &&
        flag.obsolete_in.is_undefined()) {
      warning("Special flag entry \"%s\" must declare version deprecated and/or obsoleted in.", flag.name);
      success = false;
    }

    if (!flag.deprecated_in.is_undefined()) {
      if (!version_less_than(flag.deprecated_in, flag.obsolete_in)) {
        warning("Special flag entry \"%s\" must be deprecated before obsoleted.", flag.name);
        success = false;
      }

      if (!version_less_than(flag.deprecated_in, flag.expired_in)) {
        warning("Special flag entry \"%s\" must be deprecated before expired.", flag.name);
        success = false;
      }
    }

    if (!flag.obsolete_in.is_undefined()) {
      if (!version_less_than(flag.obsolete_in, flag.expired_in)) {
        warning("Special flag entry \"%s\" must be obsoleted before expired.", flag.name);
        success = false;
      }

      // if flag has become obsolete it should not have a "globals" flag defined anymore.
      if (!version_less_than(JDK_Version::current(), flag.obsolete_in)) {
        if (Flag::find_flag(flag.name) != NULL) {
          warning("Global variable for obsolete special flag entry \"%s\" should be removed", flag.name);
          success = false;
        }
      }
    }

    if (!flag.expired_in.is_undefined()) {
      // if flag has become expired it should not have a "globals" flag defined anymore.
      if (!version_less_than(JDK_Version::current(), flag.expired_in)) {
        if (Flag::find_flag(flag.name) != NULL) {
          warning("Global variable for expired flag entry \"%s\" should be removed", flag.name);
          success = false;
        }
      }
    }

  }
  return success;
}
#endif

// Constructs the system class path (aka boot class path) from the following
// components, in order:
//
//     prefix           // from -Xbootclasspath/p:...
//     base             // from os::get_system_properties() or -Xbootclasspath=
//     suffix           // from -Xbootclasspath/a:...
//
// This could be AllStatic, but it isn't needed after argument processing is
// complete.
class SysClassPath: public StackObj {
public:
  SysClassPath(const char* base);
  ~SysClassPath();

  inline void set_base(const char* base);
  inline void add_prefix(const char* prefix);
  inline void add_suffix_to_prefix(const char* suffix);
  inline void add_suffix(const char* suffix);
  inline void reset_path(const char* base);

  inline const char* get_base()     const { return _items[_scp_base]; }
  inline const char* get_prefix()   const { return _items[_scp_prefix]; }
  inline const char* get_suffix()   const { return _items[_scp_suffix]; }

  // Combine all the components into a single c-heap-allocated string; caller
  // must free the string if/when no longer needed.
  char* combined_path();

private:
  // Utility routines.
  static char* add_to_path(const char* path, const char* str, bool prepend);
  static char* add_jars_to_path(char* path, const char* directory);

  inline void reset_item_at(int index);

  // Array indices for the items that make up the sysclasspath.  All except the
  // base are allocated in the C heap and freed by this class.
  enum {
    _scp_prefix,        // from -Xbootclasspath/p:...
    _scp_base,          // the default sysclasspath
    _scp_suffix,        // from -Xbootclasspath/a:...
    _scp_nitems         // the number of items, must be last.
  };

  const char* _items[_scp_nitems];
};

SysClassPath::SysClassPath(const char* base) {
  memset(_items, 0, sizeof(_items));
  _items[_scp_base] = base;
}

SysClassPath::~SysClassPath() {
  // Free everything except the base.
  for (int i = 0; i < _scp_nitems; ++i) {
    if (i != _scp_base) reset_item_at(i);
  }
}

inline void SysClassPath::set_base(const char* base) {
  _items[_scp_base] = base;
}

inline void SysClassPath::add_prefix(const char* prefix) {
  _items[_scp_prefix] = add_to_path(_items[_scp_prefix], prefix, true);
}

inline void SysClassPath::add_suffix_to_prefix(const char* suffix) {
  _items[_scp_prefix] = add_to_path(_items[_scp_prefix], suffix, false);
}

inline void SysClassPath::add_suffix(const char* suffix) {
  _items[_scp_suffix] = add_to_path(_items[_scp_suffix], suffix, false);
}

inline void SysClassPath::reset_item_at(int index) {
  assert(index < _scp_nitems && index != _scp_base, "just checking");
  if (_items[index] != NULL) {
    FREE_C_HEAP_ARRAY(char, _items[index]);
    _items[index] = NULL;
  }
}

inline void SysClassPath::reset_path(const char* base) {
  // Clear the prefix and suffix.
  reset_item_at(_scp_prefix);
  reset_item_at(_scp_suffix);
  set_base(base);
}

//------------------------------------------------------------------------------


// Combine the bootclasspath elements, some of which may be null, into a single
// c-heap-allocated string.
char* SysClassPath::combined_path() {
  assert(_items[_scp_base] != NULL, "empty default sysclasspath");

  size_t lengths[_scp_nitems];
  size_t total_len = 0;

  const char separator = *os::path_separator();

  // Get the lengths.
  int i;
  for (i = 0; i < _scp_nitems; ++i) {
    if (_items[i] != NULL) {
      lengths[i] = strlen(_items[i]);
      // Include space for the separator char (or a NULL for the last item).
      total_len += lengths[i] + 1;
    }
  }
  assert(total_len > 0, "empty sysclasspath not allowed");

  // Copy the _items to a single string.
  char* cp = NEW_C_HEAP_ARRAY(char, total_len, mtInternal);
  char* cp_tmp = cp;
  for (i = 0; i < _scp_nitems; ++i) {
    if (_items[i] != NULL) {
      memcpy(cp_tmp, _items[i], lengths[i]);
      cp_tmp += lengths[i];
      *cp_tmp++ = separator;
    }
  }
  *--cp_tmp = '\0';     // Replace the extra separator.
  return cp;
}

// Note:  path must be c-heap-allocated (or NULL); it is freed if non-null.
char*
SysClassPath::add_to_path(const char* path, const char* str, bool prepend) {
  char *cp;

  assert(str != NULL, "just checking");
  if (path == NULL) {
    size_t len = strlen(str) + 1;
    cp = NEW_C_HEAP_ARRAY(char, len, mtInternal);
    memcpy(cp, str, len);                       // copy the trailing null
  } else {
    const char separator = *os::path_separator();
    size_t old_len = strlen(path);
    size_t str_len = strlen(str);
    size_t len = old_len + str_len + 2;

    if (prepend) {
      cp = NEW_C_HEAP_ARRAY(char, len, mtInternal);
      char* cp_tmp = cp;
      memcpy(cp_tmp, str, str_len);
      cp_tmp += str_len;
      *cp_tmp = separator;
      memcpy(++cp_tmp, path, old_len + 1);      // copy the trailing null
      FREE_C_HEAP_ARRAY(char, path);
    } else {
      cp = REALLOC_C_HEAP_ARRAY(char, path, len, mtInternal);
      char* cp_tmp = cp + old_len;
      *cp_tmp = separator;
      memcpy(++cp_tmp, str, str_len + 1);       // copy the trailing null
    }
  }
  return cp;
}

// Scan the directory and append any jar or zip files found to path.
// Note:  path must be c-heap-allocated (or NULL); it is freed if non-null.
char* SysClassPath::add_jars_to_path(char* path, const char* directory) {
  DIR* dir = os::opendir(directory);
  if (dir == NULL) return path;

  char dir_sep[2] = { '\0', '\0' };
  size_t directory_len = strlen(directory);
  const char fileSep = *os::file_separator();
  if (directory[directory_len - 1] != fileSep) dir_sep[0] = fileSep;

  /* Scan the directory for jars/zips, appending them to path. */
  struct dirent *entry;
  char *dbuf = NEW_C_HEAP_ARRAY(char, os::readdir_buf_size(directory), mtInternal);
  while ((entry = os::readdir(dir, (dirent *) dbuf)) != NULL) {
    const char* name = entry->d_name;
    const char* ext = name + strlen(name) - 4;
    bool isJarOrZip = ext > name &&
      (os::file_name_strcmp(ext, ".jar") == 0 ||
       os::file_name_strcmp(ext, ".zip") == 0);
    if (isJarOrZip) {
      char* jarpath = NEW_C_HEAP_ARRAY(char, directory_len + 2 + strlen(name), mtInternal);
      sprintf(jarpath, "%s%s%s", directory, dir_sep, name);
      path = add_to_path(path, jarpath, false);
      FREE_C_HEAP_ARRAY(char, jarpath);
    }
  }
  FREE_C_HEAP_ARRAY(char, dbuf);
  os::closedir(dir);
  return path;
}

// Parses a memory size specification string.
static bool atomull(const char *s, julong* result) {
  julong n = 0;
  int args_read = 0;
  bool is_hex = false;
  // Skip leading 0[xX] for hexadecimal
  if (*s =='0' && (*(s+1) == 'x' || *(s+1) == 'X')) {
    s += 2;
    is_hex = true;
    args_read = sscanf(s, JULONG_FORMAT_X, &n);
  } else {
    args_read = sscanf(s, JULONG_FORMAT, &n);
  }
  if (args_read != 1) {
    return false;
  }
  while (*s != '\0' && (isdigit(*s) || (is_hex && isxdigit(*s)))) {
    s++;
  }
  // 4705540: illegal if more characters are found after the first non-digit
  if (strlen(s) > 1) {
    return false;
  }
  switch (*s) {
    case 'T': case 't':
      *result = n * G * K;
      // Check for overflow.
      if (*result/((julong)G * K) != n) return false;
      return true;
    case 'G': case 'g':
      *result = n * G;
      if (*result/G != n) return false;
      return true;
    case 'M': case 'm':
      *result = n * M;
      if (*result/M != n) return false;
      return true;
    case 'K': case 'k':
      *result = n * K;
      if (*result/K != n) return false;
      return true;
    case '\0':
      *result = n;
      return true;
    default:
      return false;
  }
}

Arguments::ArgsRange Arguments::check_memory_size(julong size, julong min_size) {
  if (size < min_size) return arg_too_small;
  // Check that size will fit in a size_t (only relevant on 32-bit)
  if (size > max_uintx) return arg_too_big;
  return arg_in_range;
}

// Describe an argument out of range error
void Arguments::describe_range_error(ArgsRange errcode) {
  switch(errcode) {
  case arg_too_big:
    jio_fprintf(defaultStream::error_stream(),
                "The specified size exceeds the maximum "
                "representable size.\n");
    break;
  case arg_too_small:
  case arg_unreadable:
  case arg_in_range:
    // do nothing for now
    break;
  default:
    ShouldNotReachHere();
  }
}

static bool set_bool_flag(const char* name, bool value, Flag::Flags origin) {
  if (CommandLineFlags::boolAtPut(name, &value, origin) == Flag::SUCCESS) {
    return true;
  } else {
    return false;
  }
}

static bool set_fp_numeric_flag(const char* name, char* value, Flag::Flags origin) {
  double v;
  if (sscanf(value, "%lf", &v) != 1) {
    return false;
  }

  if (CommandLineFlags::doubleAtPut(name, &v, origin) == Flag::SUCCESS) {
    return true;
  }
  return false;
}

static bool set_numeric_flag(const char* name, char* value, Flag::Flags origin) {
  julong v;
  int int_v;
  intx intx_v;
  bool is_neg = false;
  // Check the sign first since atomull() parses only unsigned values.
  if (*value == '-') {
    if ((CommandLineFlags::intxAt(name, &intx_v) != Flag::SUCCESS) && (CommandLineFlags::intAt(name, &int_v) != Flag::SUCCESS)) {
      return false;
    }
    value++;
    is_neg = true;
  }
  if (!atomull(value, &v)) {
    return false;
  }
  int_v = (int) v;
  if (is_neg) {
    int_v = -int_v;
  }
  if (CommandLineFlags::intAtPut(name, &int_v, origin) == Flag::SUCCESS) {
    return true;
  }
  uint uint_v = (uint) v;
  if (!is_neg && CommandLineFlags::uintAtPut(name, &uint_v, origin) == Flag::SUCCESS) {
    return true;
  }
  intx_v = (intx) v;
  if (is_neg) {
    intx_v = -intx_v;
  }
  if (CommandLineFlags::intxAtPut(name, &intx_v, origin) == Flag::SUCCESS) {
    return true;
  }
  uintx uintx_v = (uintx) v;
  if (!is_neg && (CommandLineFlags::uintxAtPut(name, &uintx_v, origin) == Flag::SUCCESS)) {
    return true;
  }
  uint64_t uint64_t_v = (uint64_t) v;
  if (!is_neg && (CommandLineFlags::uint64_tAtPut(name, &uint64_t_v, origin) == Flag::SUCCESS)) {
    return true;
  }
  size_t size_t_v = (size_t) v;
  if (!is_neg && (CommandLineFlags::size_tAtPut(name, &size_t_v, origin) == Flag::SUCCESS)) {
    return true;
  }
  return false;
}

static bool set_string_flag(const char* name, const char* value, Flag::Flags origin) {
  if (CommandLineFlags::ccstrAtPut(name, &value, origin) != Flag::SUCCESS) return false;
  // Contract:  CommandLineFlags always returns a pointer that needs freeing.
  FREE_C_HEAP_ARRAY(char, value);
  return true;
}

static bool append_to_string_flag(const char* name, const char* new_value, Flag::Flags origin) {
  const char* old_value = "";
  if (CommandLineFlags::ccstrAt(name, &old_value) != Flag::SUCCESS) return false;
  size_t old_len = old_value != NULL ? strlen(old_value) : 0;
  size_t new_len = strlen(new_value);
  const char* value;
  char* free_this_too = NULL;
  if (old_len == 0) {
    value = new_value;
  } else if (new_len == 0) {
    value = old_value;
  } else {
    char* buf = NEW_C_HEAP_ARRAY(char, old_len + 1 + new_len + 1, mtInternal);
    // each new setting adds another LINE to the switch:
    sprintf(buf, "%s\n%s", old_value, new_value);
    value = buf;
    free_this_too = buf;
  }
  (void) CommandLineFlags::ccstrAtPut(name, &value, origin);
  // CommandLineFlags always returns a pointer that needs freeing.
  FREE_C_HEAP_ARRAY(char, value);
  if (free_this_too != NULL) {
    // CommandLineFlags made its own copy, so I must delete my own temp. buffer.
    FREE_C_HEAP_ARRAY(char, free_this_too);
  }
  return true;
}

const char* Arguments::handle_aliases_and_deprecation(const char* arg, bool warn) {
  const char* real_name = real_flag_name(arg);
  JDK_Version since = JDK_Version();
  switch (is_deprecated_flag(arg, &since)) {
    case -1:
      return NULL; // obsolete or expired, don't process normally
    case 0:
      return real_name;
    case 1: {
      if (warn) {
        char version[256];
        since.to_string(version, sizeof(version));
        if (real_name != arg) {
          warning("Option %s was deprecated in version %s and will likely be removed in a future release. Use option %s instead.",
                  arg, version, real_name);
        } else {
          warning("Option %s was deprecated in version %s and will likely be removed in a future release.",
                  arg, version);
        }
      }
      return real_name;
    }
  }
  ShouldNotReachHere();
  return NULL;
}

bool Arguments::parse_argument(const char* arg, Flag::Flags origin) {

  // range of acceptable characters spelled out for portability reasons
#define NAME_RANGE  "[abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_]"
#define BUFLEN 255
  char name[BUFLEN+1];
  char dummy;
  const char* real_name;
  bool warn_if_deprecated = true;

  if (sscanf(arg, "-%" XSTR(BUFLEN) NAME_RANGE "%c", name, &dummy) == 1) {
    real_name = handle_aliases_and_deprecation(name, warn_if_deprecated);
    if (real_name == NULL) {
      return false;
    }
    return set_bool_flag(real_name, false, origin);
  }
  if (sscanf(arg, "+%" XSTR(BUFLEN) NAME_RANGE "%c", name, &dummy) == 1) {
    real_name = handle_aliases_and_deprecation(name, warn_if_deprecated);
    if (real_name == NULL) {
      return false;
    }
    return set_bool_flag(real_name, true, origin);
  }

  char punct;
  if (sscanf(arg, "%" XSTR(BUFLEN) NAME_RANGE "%c", name, &punct) == 2 && punct == '=') {
    const char* value = strchr(arg, '=') + 1;
    Flag* flag;

    // this scanf pattern matches both strings (handled here) and numbers (handled later))
    real_name = handle_aliases_and_deprecation(name, warn_if_deprecated);
    if (real_name == NULL) {
      return false;
    }
    flag = Flag::find_flag(real_name);
    if (flag != NULL && flag->is_ccstr()) {
      if (flag->ccstr_accumulates()) {
        return append_to_string_flag(real_name, value, origin);
      } else {
        if (value[0] == '\0') {
          value = NULL;
        }
        return set_string_flag(real_name, value, origin);
      }
    } else {
      warn_if_deprecated = false; // if arg is deprecated, we've already done warning...
    }
  }

  if (sscanf(arg, "%" XSTR(BUFLEN) NAME_RANGE ":%c", name, &punct) == 2 && punct == '=') {
    const char* value = strchr(arg, '=') + 1;
    // -XX:Foo:=xxx will reset the string flag to the given value.
    if (value[0] == '\0') {
      value = NULL;
    }
    real_name = handle_aliases_and_deprecation(name, warn_if_deprecated);
    if (real_name == NULL) {
      return false;
    }
    return set_string_flag(real_name, value, origin);
  }

#define SIGNED_FP_NUMBER_RANGE "[-0123456789.]"
#define SIGNED_NUMBER_RANGE    "[-0123456789]"
#define        NUMBER_RANGE    "[0123456789]"
  char value[BUFLEN + 1];
  char value2[BUFLEN + 1];
  if (sscanf(arg, "%" XSTR(BUFLEN) NAME_RANGE "=" "%" XSTR(BUFLEN) SIGNED_NUMBER_RANGE "." "%" XSTR(BUFLEN) NUMBER_RANGE "%c", name, value, value2, &dummy) == 3) {
    // Looks like a floating-point number -- try again with more lenient format string
    if (sscanf(arg, "%" XSTR(BUFLEN) NAME_RANGE "=" "%" XSTR(BUFLEN) SIGNED_FP_NUMBER_RANGE "%c", name, value, &dummy) == 2) {
      real_name = handle_aliases_and_deprecation(name, warn_if_deprecated);
      if (real_name == NULL) {
        return false;
      }
      return set_fp_numeric_flag(real_name, value, origin);
    }
  }

#define VALUE_RANGE "[-kmgtxKMGTX0123456789abcdefABCDEF]"
  if (sscanf(arg, "%" XSTR(BUFLEN) NAME_RANGE "=" "%" XSTR(BUFLEN) VALUE_RANGE "%c", name, value, &dummy) == 2) {
    real_name = handle_aliases_and_deprecation(name, warn_if_deprecated);
    if (real_name == NULL) {
      return false;
    }
    return set_numeric_flag(real_name, value, origin);
  }

  return false;
}

void Arguments::add_string(char*** bldarray, int* count, const char* arg) {
  assert(bldarray != NULL, "illegal argument");

  if (arg == NULL) {
    return;
  }

  int new_count = *count + 1;

  // expand the array and add arg to the last element
  if (*bldarray == NULL) {
    *bldarray = NEW_C_HEAP_ARRAY(char*, new_count, mtInternal);
  } else {
    *bldarray = REALLOC_C_HEAP_ARRAY(char*, *bldarray, new_count, mtInternal);
  }
  (*bldarray)[*count] = os::strdup_check_oom(arg);
  *count = new_count;
}

void Arguments::build_jvm_args(const char* arg) {
  add_string(&_jvm_args_array, &_num_jvm_args, arg);
}

void Arguments::build_jvm_flags(const char* arg) {
  add_string(&_jvm_flags_array, &_num_jvm_flags, arg);
}

// utility function to return a string that concatenates all
// strings in a given char** array
const char* Arguments::build_resource_string(char** args, int count) {
  if (args == NULL || count == 0) {
    return NULL;
  }
  size_t length = strlen(args[0]) + 1; // add 1 for the null terminator
  for (int i = 1; i < count; i++) {
    length += strlen(args[i]) + 1; // add 1 for a space
  }
  char* s = NEW_RESOURCE_ARRAY(char, length);
  strcpy(s, args[0]);
  for (int j = 1; j < count; j++) {
    strcat(s, " ");
    strcat(s, args[j]);
  }
  return (const char*) s;
}

void Arguments::print_on(outputStream* st) {
  st->print_cr("VM Arguments:");
  if (num_jvm_flags() > 0) {
    st->print("jvm_flags: "); print_jvm_flags_on(st);
    st->cr();
  }
  if (num_jvm_args() > 0) {
    st->print("jvm_args: "); print_jvm_args_on(st);
    st->cr();
  }
  st->print_cr("java_command: %s", java_command() ? java_command() : "<unknown>");
  if (_java_class_path != NULL) {
    char* path = _java_class_path->value();
    st->print_cr("java_class_path (initial): %s", strlen(path) == 0 ? "<not set>" : path );
  }
  st->print_cr("Launcher Type: %s", _sun_java_launcher);
}

void Arguments::print_summary_on(outputStream* st) {
  // Print the command line.  Environment variables that are helpful for
  // reproducing the problem are written later in the hs_err file.
  // flags are from setting file
  if (num_jvm_flags() > 0) {
    st->print_raw("Settings File: ");
    print_jvm_flags_on(st);
    st->cr();
  }
  // args are the command line and environment variable arguments.
  st->print_raw("Command Line: ");
  if (num_jvm_args() > 0) {
    print_jvm_args_on(st);
  }
  // this is the classfile and any arguments to the java program
  if (java_command() != NULL) {
    st->print("%s", java_command());
  }
  st->cr();
}

void Arguments::print_jvm_flags_on(outputStream* st) {
  if (_num_jvm_flags > 0) {
    for (int i=0; i < _num_jvm_flags; i++) {
      st->print("%s ", _jvm_flags_array[i]);
    }
  }
}

void Arguments::print_jvm_args_on(outputStream* st) {
  if (_num_jvm_args > 0) {
    for (int i=0; i < _num_jvm_args; i++) {
      st->print("%s ", _jvm_args_array[i]);
    }
  }
}

bool Arguments::process_argument(const char* arg,
                                 jboolean ignore_unrecognized,
                                 Flag::Flags origin) {
  JDK_Version since = JDK_Version();

  if (parse_argument(arg, origin) || ignore_unrecognized) {
    return true;
  }

  // Determine if the flag has '+', '-', or '=' characters.
  bool has_plus_minus = (*arg == '+' || *arg == '-');
  const char* const argname = has_plus_minus ? arg + 1 : arg;

  size_t arg_len;
  const char* equal_sign = strchr(argname, '=');
  if (equal_sign == NULL) {
    arg_len = strlen(argname);
  } else {
    arg_len = equal_sign - argname;
  }

  // Only make the obsolete check for valid arguments.
  if (arg_len <= BUFLEN) {
    // Construct a string which consists only of the argument name without '+', '-', or '='.
    char stripped_argname[BUFLEN+1];
    strncpy(stripped_argname, argname, arg_len);
    stripped_argname[arg_len] = '\0';  // strncpy may not null terminate.

    if (is_obsolete_flag(stripped_argname, &since)) {
      char version[256];
      since.to_string(version, sizeof(version));
      warning("Ignoring option %s; support was removed in %s", stripped_argname, version);
      return true;
    }
  }

  // For locked flags, report a custom error message if available.
  // Otherwise, report the standard unrecognized VM option.
  Flag* found_flag = Flag::find_flag((const char*)argname, arg_len, true, true);
  if (found_flag != NULL) {
    char locked_message_buf[BUFLEN];
    found_flag->get_locked_message(locked_message_buf, BUFLEN);
    if (strlen(locked_message_buf) == 0) {
      if (found_flag->is_bool() && !has_plus_minus) {
        jio_fprintf(defaultStream::error_stream(),
          "Missing +/- setting for VM option '%s'\n", argname);
      } else if (!found_flag->is_bool() && has_plus_minus) {
        jio_fprintf(defaultStream::error_stream(),
          "Unexpected +/- setting in VM option '%s'\n", argname);
      } else {
        jio_fprintf(defaultStream::error_stream(),
          "Improperly specified VM option '%s'\n", argname);
      }
    } else {
      jio_fprintf(defaultStream::error_stream(), "%s", locked_message_buf);
    }
  } else {
    jio_fprintf(defaultStream::error_stream(),
                "Unrecognized VM option '%s'\n", argname);
    Flag* fuzzy_matched = Flag::fuzzy_match((const char*)argname, arg_len, true);
    if (fuzzy_matched != NULL) {
      jio_fprintf(defaultStream::error_stream(),
                  "Did you mean '%s%s%s'? ",
                  (fuzzy_matched->is_bool()) ? "(+/-)" : "",
                  fuzzy_matched->_name,
                  (fuzzy_matched->is_bool()) ? "" : "=<value>");
    }
  }

  // allow for commandline "commenting out" options like -XX:#+Verbose
  return arg[0] == '#';
}

bool Arguments::process_settings_file(const char* file_name, bool should_exist, jboolean ignore_unrecognized) {
  FILE* stream = fopen(file_name, "rb");
  if (stream == NULL) {
    if (should_exist) {
      jio_fprintf(defaultStream::error_stream(),
                  "Could not open settings file %s\n", file_name);
      return false;
    } else {
      return true;
    }
  }

  char token[1024];
  int  pos = 0;

  bool in_white_space = true;
  bool in_comment     = false;
  bool in_quote       = false;
  char quote_c        = 0;
  bool result         = true;

  int c = getc(stream);
  while(c != EOF && pos < (int)(sizeof(token)-1)) {
    if (in_white_space) {
      if (in_comment) {
        if (c == '\n') in_comment = false;
      } else {
        if (c == '#') in_comment = true;
        else if (!isspace(c)) {
          in_white_space = false;
          token[pos++] = c;
        }
      }
    } else {
      if (c == '\n' || (!in_quote && isspace(c))) {
        // token ends at newline, or at unquoted whitespace
        // this allows a way to include spaces in string-valued options
        token[pos] = '\0';
        logOption(token);
        result &= process_argument(token, ignore_unrecognized, Flag::CONFIG_FILE);
        build_jvm_flags(token);
        pos = 0;
        in_white_space = true;
        in_quote = false;
      } else if (!in_quote && (c == '\'' || c == '"')) {
        in_quote = true;
        quote_c = c;
      } else if (in_quote && (c == quote_c)) {
        in_quote = false;
      } else {
        token[pos++] = c;
      }
    }
    c = getc(stream);
  }
  if (pos > 0) {
    token[pos] = '\0';
    result &= process_argument(token, ignore_unrecognized, Flag::CONFIG_FILE);
    build_jvm_flags(token);
  }
  fclose(stream);
  return result;
}

//=============================================================================================================
// Parsing of properties (-D)

const char* Arguments::get_property(const char* key) {
  return PropertyList_get_value(system_properties(), key);
}

bool Arguments::add_property(const char* prop) {
  const char* eq = strchr(prop, '=');
  const char* key;
  const char* value = "";

  if (eq == NULL) {
    // property doesn't have a value, thus use passed string
    key = prop;
  } else {
    // property have a value, thus extract it and save to the
    // allocated string
    size_t key_len = eq - prop;
    char* tmp_key = AllocateHeap(key_len + 1, mtInternal);

    strncpy(tmp_key, prop, key_len);
    tmp_key[key_len] = '\0';
    key = tmp_key;

    value = &prop[key_len + 1];
  }

  if (strcmp(key, "java.compiler") == 0) {
    process_java_compiler_argument(value);
    // Record value in Arguments, but let it get passed to Java.
  } else if (strcmp(key, "sun.java.launcher.is_altjvm") == 0 ||
             strcmp(key, "sun.java.launcher.pid") == 0) {
    // sun.java.launcher.is_altjvm and sun.java.launcher.pid property are
    // private and are processed in process_sun_java_launcher_properties();
    // the sun.java.launcher property is passed on to the java application
  } else if (strcmp(key, "sun.boot.library.path") == 0) {
    PropertyList_unique_add(&_system_properties, key, value, true);
  } else {
    if (strcmp(key, "sun.java.command") == 0) {
      if (_java_command != NULL) {
        os::free(_java_command);
      }
      _java_command = os::strdup_check_oom(value, mtInternal);
    } else if (strcmp(key, "java.vendor.url.bug") == 0) {
      if (_java_vendor_url_bug != DEFAULT_VENDOR_URL_BUG) {
        assert(_java_vendor_url_bug != NULL, "_java_vendor_url_bug is NULL");
        os::free((void *)_java_vendor_url_bug);
      }
      // save it in _java_vendor_url_bug, so JVM fatal error handler can access
      // its value without going through the property list or making a Java call.
      _java_vendor_url_bug = os::strdup_check_oom(value, mtInternal);
    }

    // Create new property and add at the end of the list
    PropertyList_unique_add(&_system_properties, key, value);
  }

  if (key != prop) {
    // SystemProperty copy passed value, thus free previously allocated
    // memory
    FreeHeap((void *)key);
  }

  return true;
}

//===========================================================================================================
// Setting int/mixed/comp mode flags

void Arguments::set_mode_flags(Mode mode) {
  // Set up default values for all flags.
  // If you add a flag to any of the branches below,
  // add a default value for it here.
  set_java_compiler(false);
  _mode                      = mode;

  // Ensure Agent_OnLoad has the correct initial values.
  // This may not be the final mode; mode may change later in onload phase.
  PropertyList_unique_add(&_system_properties, "java.vm.info",
                          VM_Version::vm_info_string(), false);

  UseInterpreter             = true;
  UseCompiler                = true;
  UseLoopCounter             = true;

  // Default values may be platform/compiler dependent -
  // use the saved values
  ClipInlining               = Arguments::_ClipInlining;
  AlwaysCompileLoopMethods   = Arguments::_AlwaysCompileLoopMethods;
  UseOnStackReplacement      = Arguments::_UseOnStackReplacement;
  BackgroundCompilation      = Arguments::_BackgroundCompilation;
  if (TieredCompilation) {
    if (FLAG_IS_DEFAULT(Tier3InvokeNotifyFreqLog)) {
      Tier3InvokeNotifyFreqLog = Arguments::_Tier3InvokeNotifyFreqLog;
    }
    if (FLAG_IS_DEFAULT(Tier4InvocationThreshold)) {
      Tier4InvocationThreshold = Arguments::_Tier4InvocationThreshold;
    }
  }

  // Change from defaults based on mode
  switch (mode) {
  default:
    ShouldNotReachHere();
    break;
  case _int:
    UseCompiler              = false;
    UseLoopCounter           = false;
    AlwaysCompileLoopMethods = false;
    UseOnStackReplacement    = false;
    break;
  case _mixed:
    // same as default
    break;
  case _comp:
    UseInterpreter           = false;
    BackgroundCompilation    = false;
    ClipInlining             = false;
    // Be much more aggressive in tiered mode with -Xcomp and exercise C2 more.
    // We will first compile a level 3 version (C1 with full profiling), then do one invocation of it and
    // compile a level 4 (C2) and then continue executing it.
    if (TieredCompilation) {
      Tier3InvokeNotifyFreqLog = 0;
      Tier4InvocationThreshold = 0;
    }
    break;
  }
}

#if defined(COMPILER2) || INCLUDE_JVMCI || defined(_LP64) || !INCLUDE_CDS
// Conflict: required to use shared spaces (-Xshare:on), but
// incompatible command line options were chosen.

static void no_shared_spaces(const char* message) {
  if (RequireSharedSpaces) {
    jio_fprintf(defaultStream::error_stream(),
      "Class data sharing is inconsistent with other specified options.\n");
    vm_exit_during_initialization("Unable to use shared archive.", message);
  } else {
    FLAG_SET_DEFAULT(UseSharedSpaces, false);
  }
}
#endif

// Returns threshold scaled with the value of scale.
// If scale < 0.0, threshold is returned without scaling.
intx Arguments::scaled_compile_threshold(intx threshold, double scale) {
  if (scale == 1.0 || scale < 0.0) {
    return threshold;
  } else {
    return (intx)(threshold * scale);
  }
}

// Returns freq_log scaled with the value of scale.
// Returned values are in the range of [0, InvocationCounter::number_of_count_bits + 1].
// If scale < 0.0, freq_log is returned without scaling.
intx Arguments::scaled_freq_log(intx freq_log, double scale) {
  // Check if scaling is necessary or if negative value was specified.
  if (scale == 1.0 || scale < 0.0) {
    return freq_log;
  }
  // Check values to avoid calculating log2 of 0.
  if (scale == 0.0 || freq_log == 0) {
    return 0;
  }
  // Determine the maximum notification frequency value currently supported.
  // The largest mask value that the interpreter/C1 can handle is
  // of length InvocationCounter::number_of_count_bits. Mask values are always
  // one bit shorter then the value of the notification frequency. Set
  // max_freq_bits accordingly.
  intx max_freq_bits = InvocationCounter::number_of_count_bits + 1;
  intx scaled_freq = scaled_compile_threshold((intx)1 << freq_log, scale);
  if (scaled_freq == 0) {
    // Return 0 right away to avoid calculating log2 of 0.
    return 0;
  } else if (scaled_freq > nth_bit(max_freq_bits)) {
    return max_freq_bits;
  } else {
    return log2_intptr(scaled_freq);
  }
}

void Arguments::set_tiered_flags() {
  // With tiered, set default policy to AdvancedThresholdPolicy, which is 3.
  if (FLAG_IS_DEFAULT(CompilationPolicyChoice)) {
    FLAG_SET_DEFAULT(CompilationPolicyChoice, 3);
  }
  if (CompilationPolicyChoice < 2) {
    vm_exit_during_initialization(
      "Incompatible compilation policy selected", NULL);
  }
  // Increase the code cache size - tiered compiles a lot more.
  if (FLAG_IS_DEFAULT(ReservedCodeCacheSize)) {
    FLAG_SET_ERGO(uintx, ReservedCodeCacheSize,
                  MIN2(CODE_CACHE_DEFAULT_LIMIT, ReservedCodeCacheSize * 5));
  }
  // Enable SegmentedCodeCache if TieredCompilation is enabled and ReservedCodeCacheSize >= 240M
  if (FLAG_IS_DEFAULT(SegmentedCodeCache) && ReservedCodeCacheSize >= 240*M) {
    FLAG_SET_ERGO(bool, SegmentedCodeCache, true);

    if (FLAG_IS_DEFAULT(ReservedCodeCacheSize)) {
      // Multiply sizes by 5 but fix NonNMethodCodeHeapSize (distribute among non-profiled and profiled code heap)
      if (FLAG_IS_DEFAULT(ProfiledCodeHeapSize)) {
        FLAG_SET_ERGO(uintx, ProfiledCodeHeapSize, ProfiledCodeHeapSize * 5 + NonNMethodCodeHeapSize * 2);
      }
      if (FLAG_IS_DEFAULT(NonProfiledCodeHeapSize)) {
        FLAG_SET_ERGO(uintx, NonProfiledCodeHeapSize, NonProfiledCodeHeapSize * 5 + NonNMethodCodeHeapSize * 2);
      }
      // Check consistency of code heap sizes
      if ((NonNMethodCodeHeapSize + NonProfiledCodeHeapSize + ProfiledCodeHeapSize) != ReservedCodeCacheSize) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid code heap sizes: NonNMethodCodeHeapSize(%dK) + ProfiledCodeHeapSize(%dK) + NonProfiledCodeHeapSize(%dK) = %dK. Must be equal to ReservedCodeCacheSize = %uK.\n",
                    NonNMethodCodeHeapSize/K, ProfiledCodeHeapSize/K, NonProfiledCodeHeapSize/K,
                    (NonNMethodCodeHeapSize + ProfiledCodeHeapSize + NonProfiledCodeHeapSize)/K, ReservedCodeCacheSize/K);
        vm_exit(1);
      }
    }
  }
  if (!UseInterpreter) { // -Xcomp
    Tier3InvokeNotifyFreqLog = 0;
    Tier4InvocationThreshold = 0;
  }

  if (CompileThresholdScaling < 0) {
    vm_exit_during_initialization("Negative value specified for CompileThresholdScaling", NULL);
  }

  // Scale tiered compilation thresholds.
  // CompileThresholdScaling == 0.0 is equivalent to -Xint and leaves compilation thresholds unchanged.
  if (!FLAG_IS_DEFAULT(CompileThresholdScaling) && CompileThresholdScaling > 0.0) {
    FLAG_SET_ERGO(intx, Tier0InvokeNotifyFreqLog, scaled_freq_log(Tier0InvokeNotifyFreqLog));
    FLAG_SET_ERGO(intx, Tier0BackedgeNotifyFreqLog, scaled_freq_log(Tier0BackedgeNotifyFreqLog));

    FLAG_SET_ERGO(intx, Tier3InvocationThreshold, scaled_compile_threshold(Tier3InvocationThreshold));
    FLAG_SET_ERGO(intx, Tier3MinInvocationThreshold, scaled_compile_threshold(Tier3MinInvocationThreshold));
    FLAG_SET_ERGO(intx, Tier3CompileThreshold, scaled_compile_threshold(Tier3CompileThreshold));
    FLAG_SET_ERGO(intx, Tier3BackEdgeThreshold, scaled_compile_threshold(Tier3BackEdgeThreshold));

    // Tier2{Invocation,MinInvocation,Compile,Backedge}Threshold should be scaled here
    // once these thresholds become supported.

    FLAG_SET_ERGO(intx, Tier2InvokeNotifyFreqLog, scaled_freq_log(Tier2InvokeNotifyFreqLog));
    FLAG_SET_ERGO(intx, Tier2BackedgeNotifyFreqLog, scaled_freq_log(Tier2BackedgeNotifyFreqLog));

    FLAG_SET_ERGO(intx, Tier3InvokeNotifyFreqLog, scaled_freq_log(Tier3InvokeNotifyFreqLog));
    FLAG_SET_ERGO(intx, Tier3BackedgeNotifyFreqLog, scaled_freq_log(Tier3BackedgeNotifyFreqLog));

    FLAG_SET_ERGO(intx, Tier23InlineeNotifyFreqLog, scaled_freq_log(Tier23InlineeNotifyFreqLog));

    FLAG_SET_ERGO(intx, Tier4InvocationThreshold, scaled_compile_threshold(Tier4InvocationThreshold));
    FLAG_SET_ERGO(intx, Tier4MinInvocationThreshold, scaled_compile_threshold(Tier4MinInvocationThreshold));
    FLAG_SET_ERGO(intx, Tier4CompileThreshold, scaled_compile_threshold(Tier4CompileThreshold));
    FLAG_SET_ERGO(intx, Tier4BackEdgeThreshold, scaled_compile_threshold(Tier4BackEdgeThreshold));
  }
}

#if INCLUDE_ALL_GCS
static void disable_adaptive_size_policy(const char* collector_name) {
  if (UseAdaptiveSizePolicy) {
    if (FLAG_IS_CMDLINE(UseAdaptiveSizePolicy)) {
      warning("Disabling UseAdaptiveSizePolicy; it is incompatible with %s.",
              collector_name);
    }
    FLAG_SET_DEFAULT(UseAdaptiveSizePolicy, false);
  }
}

void Arguments::set_parnew_gc_flags() {
  assert(!UseSerialGC && !UseParallelOldGC && !UseParallelGC && !UseG1GC,
         "control point invariant");
  assert(UseConcMarkSweepGC, "CMS is expected to be on here");
  assert(UseParNewGC, "ParNew should always be used with CMS");

  if (FLAG_IS_DEFAULT(ParallelGCThreads)) {
    FLAG_SET_DEFAULT(ParallelGCThreads, Abstract_VM_Version::parallel_worker_threads());
    assert(ParallelGCThreads > 0, "We should always have at least one thread by default");
  } else if (ParallelGCThreads == 0) {
    jio_fprintf(defaultStream::error_stream(),
        "The ParNew GC can not be combined with -XX:ParallelGCThreads=0\n");
    vm_exit(1);
  }

  // By default YoungPLABSize and OldPLABSize are set to 4096 and 1024 respectively,
  // these settings are default for Parallel Scavenger. For ParNew+Tenured configuration
  // we set them to 1024 and 1024.
  // See CR 6362902.
  if (FLAG_IS_DEFAULT(YoungPLABSize)) {
    FLAG_SET_DEFAULT(YoungPLABSize, (intx)1024);
  }
  if (FLAG_IS_DEFAULT(OldPLABSize)) {
    FLAG_SET_DEFAULT(OldPLABSize, (intx)1024);
  }

  // When using compressed oops, we use local overflow stacks,
  // rather than using a global overflow list chained through
  // the klass word of the object's pre-image.
  if (UseCompressedOops && !ParGCUseLocalOverflow) {
    if (!FLAG_IS_DEFAULT(ParGCUseLocalOverflow)) {
      warning("Forcing +ParGCUseLocalOverflow: needed if using compressed references");
    }
    FLAG_SET_DEFAULT(ParGCUseLocalOverflow, true);
  }
  assert(ParGCUseLocalOverflow || !UseCompressedOops, "Error");
}

// Adjust some sizes to suit CMS and/or ParNew needs; these work well on
// sparc/solaris for certain applications, but would gain from
// further optimization and tuning efforts, and would almost
// certainly gain from analysis of platform and environment.
void Arguments::set_cms_and_parnew_gc_flags() {
  assert(!UseSerialGC && !UseParallelOldGC && !UseParallelGC, "Error");
  assert(UseConcMarkSweepGC, "CMS is expected to be on here");
  assert(UseParNewGC, "ParNew should always be used with CMS");

  // Turn off AdaptiveSizePolicy by default for cms until it is complete.
  disable_adaptive_size_policy("UseConcMarkSweepGC");

  set_parnew_gc_flags();

  size_t max_heap = align_size_down(MaxHeapSize,
                                    CardTableRS::ct_max_alignment_constraint());

  // Now make adjustments for CMS
  intx   tenuring_default = (intx)6;
  size_t young_gen_per_worker = CMSYoungGenPerWorker;

  // Preferred young gen size for "short" pauses:
  // upper bound depends on # of threads and NewRatio.
  const size_t preferred_max_new_size_unaligned =
    MIN2(max_heap/(NewRatio+1), ScaleForWordSize(young_gen_per_worker * ParallelGCThreads));
  size_t preferred_max_new_size =
    align_size_up(preferred_max_new_size_unaligned, os::vm_page_size());

  // Unless explicitly requested otherwise, size young gen
  // for "short" pauses ~ CMSYoungGenPerWorker*ParallelGCThreads

  // If either MaxNewSize or NewRatio is set on the command line,
  // assume the user is trying to set the size of the young gen.
  if (FLAG_IS_DEFAULT(MaxNewSize) && FLAG_IS_DEFAULT(NewRatio)) {

    // Set MaxNewSize to our calculated preferred_max_new_size unless
    // NewSize was set on the command line and it is larger than
    // preferred_max_new_size.
    if (!FLAG_IS_DEFAULT(NewSize)) {   // NewSize explicitly set at command-line
      FLAG_SET_ERGO(size_t, MaxNewSize, MAX2(NewSize, preferred_max_new_size));
    } else {
      FLAG_SET_ERGO(size_t, MaxNewSize, preferred_max_new_size);
    }
    if (PrintGCDetails && Verbose) {
      // Too early to use gclog_or_tty
      tty->print_cr("CMS ergo set MaxNewSize: " SIZE_FORMAT, MaxNewSize);
    }

    // Code along this path potentially sets NewSize and OldSize
    if (PrintGCDetails && Verbose) {
      // Too early to use gclog_or_tty
      tty->print_cr("CMS set min_heap_size: " SIZE_FORMAT
           " initial_heap_size:  " SIZE_FORMAT
           " max_heap: " SIZE_FORMAT,
           min_heap_size(), InitialHeapSize, max_heap);
    }
    size_t min_new = preferred_max_new_size;
    if (FLAG_IS_CMDLINE(NewSize)) {
      min_new = NewSize;
    }
    if (max_heap > min_new && min_heap_size() > min_new) {
      // Unless explicitly requested otherwise, make young gen
      // at least min_new, and at most preferred_max_new_size.
      if (FLAG_IS_DEFAULT(NewSize)) {
        FLAG_SET_ERGO(size_t, NewSize, MAX2(NewSize, min_new));
        FLAG_SET_ERGO(size_t, NewSize, MIN2(preferred_max_new_size, NewSize));
        if (PrintGCDetails && Verbose) {
          // Too early to use gclog_or_tty
          tty->print_cr("CMS ergo set NewSize: " SIZE_FORMAT, NewSize);
        }
      }
      // Unless explicitly requested otherwise, size old gen
      // so it's NewRatio x of NewSize.
      if (FLAG_IS_DEFAULT(OldSize)) {
        if (max_heap > NewSize) {
          FLAG_SET_ERGO(size_t, OldSize, MIN2(NewRatio*NewSize, max_heap - NewSize));
          if (PrintGCDetails && Verbose) {
            // Too early to use gclog_or_tty
            tty->print_cr("CMS ergo set OldSize: " SIZE_FORMAT, OldSize);
          }
        }
      }
    }
  }
  // Unless explicitly requested otherwise, definitely
  // promote all objects surviving "tenuring_default" scavenges.
  if (FLAG_IS_DEFAULT(MaxTenuringThreshold) &&
      FLAG_IS_DEFAULT(SurvivorRatio)) {
    FLAG_SET_ERGO(uintx, MaxTenuringThreshold, tenuring_default);
  }
  // If we decided above (or user explicitly requested)
  // `promote all' (via MaxTenuringThreshold := 0),
  // prefer minuscule survivor spaces so as not to waste
  // space for (non-existent) survivors
  if (FLAG_IS_DEFAULT(SurvivorRatio) && MaxTenuringThreshold == 0) {
    FLAG_SET_ERGO(uintx, SurvivorRatio, MAX2((uintx)1024, SurvivorRatio));
  }

  // OldPLABSize is interpreted in CMS as not the size of the PLAB in words,
  // but rather the number of free blocks of a given size that are used when
  // replenishing the local per-worker free list caches.
  if (FLAG_IS_DEFAULT(OldPLABSize)) {
    if (!FLAG_IS_DEFAULT(ResizeOldPLAB) && !ResizeOldPLAB) {
      // OldPLAB sizing manually turned off: Use a larger default setting,
      // unless it was manually specified. This is because a too-low value
      // will slow down scavenges.
      FLAG_SET_ERGO(size_t, OldPLABSize, CFLS_LAB::_default_static_old_plab_size); // default value before 6631166
    } else {
      FLAG_SET_DEFAULT(OldPLABSize, CFLS_LAB::_default_dynamic_old_plab_size); // old CMSParPromoteBlocksToClaim default
    }
  }

  // If either of the static initialization defaults have changed, note this
  // modification.
  if (!FLAG_IS_DEFAULT(OldPLABSize) || !FLAG_IS_DEFAULT(OldPLABWeight)) {
    CFLS_LAB::modify_initialization(OldPLABSize, OldPLABWeight);
  }

  if (!ClassUnloading) {
    FLAG_SET_CMDLINE(bool, CMSClassUnloadingEnabled, false);
    FLAG_SET_CMDLINE(bool, ExplicitGCInvokesConcurrentAndUnloadsClasses, false);
  }

  if (PrintGCDetails && Verbose) {
    tty->print_cr("MarkStackSize: %uk  MarkStackSizeMax: %uk",
      (unsigned int) (MarkStackSize / K), (uint) (MarkStackSizeMax / K));
    tty->print_cr("ConcGCThreads: %u", ConcGCThreads);
  }
}
#endif // INCLUDE_ALL_GCS

void set_object_alignment() {
  // Object alignment.
  assert(is_power_of_2(ObjectAlignmentInBytes), "ObjectAlignmentInBytes must be power of 2");
  MinObjAlignmentInBytes     = ObjectAlignmentInBytes;
  assert(MinObjAlignmentInBytes >= HeapWordsPerLong * HeapWordSize, "ObjectAlignmentInBytes value is too small");
  MinObjAlignment            = MinObjAlignmentInBytes / HeapWordSize;
  assert(MinObjAlignmentInBytes == MinObjAlignment * HeapWordSize, "ObjectAlignmentInBytes value is incorrect");
  MinObjAlignmentInBytesMask = MinObjAlignmentInBytes - 1;

  LogMinObjAlignmentInBytes  = exact_log2(ObjectAlignmentInBytes);
  LogMinObjAlignment         = LogMinObjAlignmentInBytes - LogHeapWordSize;

  // Oop encoding heap max
  OopEncodingHeapMax = (uint64_t(max_juint) + 1) << LogMinObjAlignmentInBytes;

  if (SurvivorAlignmentInBytes == 0) {
    SurvivorAlignmentInBytes = ObjectAlignmentInBytes;
  }

#if INCLUDE_ALL_GCS
  // Set CMS global values
  CompactibleFreeListSpace::set_cms_values();
#endif // INCLUDE_ALL_GCS
}

size_t Arguments::max_heap_for_compressed_oops() {
  // Avoid sign flip.
  assert(OopEncodingHeapMax > (uint64_t)os::vm_page_size(), "Unusual page size");
  // We need to fit both the NULL page and the heap into the memory budget, while
  // keeping alignment constraints of the heap. To guarantee the latter, as the
  // NULL page is located before the heap, we pad the NULL page to the conservative
  // maximum alignment that the GC may ever impose upon the heap.
  size_t displacement_due_to_null_page = align_size_up_(os::vm_page_size(),
                                                        _conservative_max_heap_alignment);

  LP64_ONLY(return OopEncodingHeapMax - displacement_due_to_null_page);
  NOT_LP64(ShouldNotReachHere(); return 0);
}

bool Arguments::should_auto_select_low_pause_collector() {
  if (UseAutoGCSelectPolicy &&
      !FLAG_IS_DEFAULT(MaxGCPauseMillis) &&
      (MaxGCPauseMillis <= AutoGCSelectPauseMillis)) {
    if (PrintGCDetails) {
      // Cannot use gclog_or_tty yet.
      tty->print_cr("Automatic selection of the low pause collector"
       " based on pause goal of %d (ms)", (int) MaxGCPauseMillis);
    }
    return true;
  }
  return false;
}

void Arguments::set_use_compressed_oops() {
#ifndef ZERO
#ifdef _LP64
  // MaxHeapSize is not set up properly at this point, but
  // the only value that can override MaxHeapSize if we are
  // to use UseCompressedOops is InitialHeapSize.
  size_t max_heap_size = MAX2(MaxHeapSize, InitialHeapSize);

  if (max_heap_size <= max_heap_for_compressed_oops()) {
#if !defined(COMPILER1) || defined(TIERED)
    if (FLAG_IS_DEFAULT(UseCompressedOops)) {
      FLAG_SET_ERGO(bool, UseCompressedOops, true);
    }
#endif
  } else {
    if (UseCompressedOops && !FLAG_IS_DEFAULT(UseCompressedOops)) {
      warning("Max heap size too large for Compressed Oops");
      FLAG_SET_DEFAULT(UseCompressedOops, false);
      FLAG_SET_DEFAULT(UseCompressedClassPointers, false);
    }
  }
#endif // _LP64
#endif // ZERO
}


// NOTE: set_use_compressed_klass_ptrs() must be called after calling
// set_use_compressed_oops().
void Arguments::set_use_compressed_klass_ptrs() {
#ifndef ZERO
#ifdef _LP64
  // UseCompressedOops must be on for UseCompressedClassPointers to be on.
  if (!UseCompressedOops) {
    if (UseCompressedClassPointers) {
      warning("UseCompressedClassPointers requires UseCompressedOops");
    }
    FLAG_SET_DEFAULT(UseCompressedClassPointers, false);
  } else {
    // Turn on UseCompressedClassPointers too
    if (FLAG_IS_DEFAULT(UseCompressedClassPointers)) {
      FLAG_SET_ERGO(bool, UseCompressedClassPointers, true);
    }
    // Check the CompressedClassSpaceSize to make sure we use compressed klass ptrs.
    if (UseCompressedClassPointers) {
      if (CompressedClassSpaceSize > KlassEncodingMetaspaceMax) {
        warning("CompressedClassSpaceSize is too large for UseCompressedClassPointers");
        FLAG_SET_DEFAULT(UseCompressedClassPointers, false);
      }
    }
  }
#endif // _LP64
#endif // !ZERO
}

void Arguments::set_conservative_max_heap_alignment() {
  // The conservative maximum required alignment for the heap is the maximum of
  // the alignments imposed by several sources: any requirements from the heap
  // itself, the collector policy and the maximum page size we may run the VM
  // with.
  size_t heap_alignment = GenCollectedHeap::conservative_max_heap_alignment();
#if INCLUDE_ALL_GCS
  if (UseParallelGC) {
    heap_alignment = ParallelScavengeHeap::conservative_max_heap_alignment();
  } else if (UseG1GC) {
    heap_alignment = G1CollectedHeap::conservative_max_heap_alignment();
  }
#endif // INCLUDE_ALL_GCS
  _conservative_max_heap_alignment = MAX4(heap_alignment,
                                          (size_t)os::vm_allocation_granularity(),
                                          os::max_page_size(),
                                          CollectorPolicy::compute_heap_alignment());
}

void Arguments::select_gc_ergonomically() {
  if (os::is_server_class_machine()) {
    if (should_auto_select_low_pause_collector()) {
      FLAG_SET_ERGO(bool, UseConcMarkSweepGC, true);
    } else {
#if defined(JAVASE_EMBEDDED)
      FLAG_SET_ERGO(bool, UseParallelGC, true);
#else
      FLAG_SET_ERGO(bool, UseG1GC, true);
#endif
    }
  } else {
    FLAG_SET_ERGO(bool, UseSerialGC, true);
  }
}

void Arguments::select_gc() {
  if (!gc_selected()) {
    select_gc_ergonomically();
    guarantee(gc_selected(), "No GC selected");
  }
}

void Arguments::set_ergonomics_flags() {
  select_gc();

#if defined(COMPILER2) || INCLUDE_JVMCI
  // Shared spaces work fine with other GCs but causes bytecode rewriting
  // to be disabled, which hurts interpreter performance and decreases
  // server performance.  When -server is specified, keep the default off
  // unless it is asked for.  Future work: either add bytecode rewriting
  // at link time, or rewrite bytecodes in non-shared methods.
  if (!DumpSharedSpaces && !RequireSharedSpaces &&
      (FLAG_IS_DEFAULT(UseSharedSpaces) || !UseSharedSpaces)) {
    no_shared_spaces("COMPILER2 default: -Xshare:auto | off, have to manually setup to on.");
  }
#endif

  set_conservative_max_heap_alignment();

#ifndef ZERO
#ifdef _LP64
  set_use_compressed_oops();

  // set_use_compressed_klass_ptrs() must be called after calling
  // set_use_compressed_oops().
  set_use_compressed_klass_ptrs();

  // Also checks that certain machines are slower with compressed oops
  // in vm_version initialization code.
#endif // _LP64
#endif // !ZERO

  CodeCacheExtensions::set_ergonomics_flags();
}

void Arguments::set_parallel_gc_flags() {
  assert(UseParallelGC || UseParallelOldGC, "Error");
  // Enable ParallelOld unless it was explicitly disabled (cmd line or rc file).
  if (FLAG_IS_DEFAULT(UseParallelOldGC)) {
    FLAG_SET_DEFAULT(UseParallelOldGC, true);
  }
  FLAG_SET_DEFAULT(UseParallelGC, true);

  // If no heap maximum was requested explicitly, use some reasonable fraction
  // of the physical memory, up to a maximum of 1GB.
  FLAG_SET_DEFAULT(ParallelGCThreads,
                   Abstract_VM_Version::parallel_worker_threads());
  if (ParallelGCThreads == 0) {
    jio_fprintf(defaultStream::error_stream(),
        "The Parallel GC can not be combined with -XX:ParallelGCThreads=0\n");
    vm_exit(1);
  }

  if (UseAdaptiveSizePolicy) {
    // We don't want to limit adaptive heap sizing's freedom to adjust the heap
    // unless the user actually sets these flags.
    if (FLAG_IS_DEFAULT(MinHeapFreeRatio)) {
      FLAG_SET_DEFAULT(MinHeapFreeRatio, 0);
    }
    if (FLAG_IS_DEFAULT(MaxHeapFreeRatio)) {
      FLAG_SET_DEFAULT(MaxHeapFreeRatio, 100);
    }
  }

  // If InitialSurvivorRatio or MinSurvivorRatio were not specified, but the
  // SurvivorRatio has been set, reset their default values to SurvivorRatio +
  // 2.  By doing this we make SurvivorRatio also work for Parallel Scavenger.
  // See CR 6362902 for details.
  if (!FLAG_IS_DEFAULT(SurvivorRatio)) {
    if (FLAG_IS_DEFAULT(InitialSurvivorRatio)) {
       FLAG_SET_DEFAULT(InitialSurvivorRatio, SurvivorRatio + 2);
    }
    if (FLAG_IS_DEFAULT(MinSurvivorRatio)) {
      FLAG_SET_DEFAULT(MinSurvivorRatio, SurvivorRatio + 2);
    }
  }

  if (UseParallelOldGC) {
    // Par compact uses lower default values since they are treated as
    // minimums.  These are different defaults because of the different
    // interpretation and are not ergonomically set.
    if (FLAG_IS_DEFAULT(MarkSweepDeadRatio)) {
      FLAG_SET_DEFAULT(MarkSweepDeadRatio, 1);
    }
  }
}

void Arguments::set_g1_gc_flags() {
  assert(UseG1GC, "Error");
#if defined(COMPILER1) || INCLUDE_JVMCI
  FastTLABRefill = false;
#endif
  FLAG_SET_DEFAULT(ParallelGCThreads, Abstract_VM_Version::parallel_worker_threads());
  if (ParallelGCThreads == 0) {
    assert(!FLAG_IS_DEFAULT(ParallelGCThreads), "The default value for ParallelGCThreads should not be 0.");
    vm_exit_during_initialization("The flag -XX:+UseG1GC can not be combined with -XX:ParallelGCThreads=0", NULL);
  }

#if INCLUDE_ALL_GCS
  if (G1ConcRefinementThreads == 0) {
    FLAG_SET_DEFAULT(G1ConcRefinementThreads, ParallelGCThreads);
  }
#endif

  // MarkStackSize will be set (if it hasn't been set by the user)
  // when concurrent marking is initialized.
  // Its value will be based upon the number of parallel marking threads.
  // But we do set the maximum mark stack size here.
  if (FLAG_IS_DEFAULT(MarkStackSizeMax)) {
    FLAG_SET_DEFAULT(MarkStackSizeMax, 128 * TASKQUEUE_SIZE);
  }

  if (FLAG_IS_DEFAULT(GCTimeRatio) || GCTimeRatio == 0) {
    // In G1, we want the default GC overhead goal to be higher than
    // say in PS. So we set it here to 10%. Otherwise the heap might
    // be expanded more aggressively than we would like it to. In
    // fact, even 10% seems to not be high enough in some cases
    // (especially small GC stress tests that the main thing they do
    // is allocation). We might consider increase it further.
    FLAG_SET_DEFAULT(GCTimeRatio, 9);
  }

  if (PrintGCDetails && Verbose) {
    tty->print_cr("MarkStackSize: %uk  MarkStackSizeMax: %uk",
      (unsigned int) (MarkStackSize / K), (uint) (MarkStackSizeMax / K));
    tty->print_cr("ConcGCThreads: %u", ConcGCThreads);
  }
}

#if !INCLUDE_ALL_GCS
#ifdef ASSERT
static bool verify_serial_gc_flags() {
  return (UseSerialGC &&
        !(UseParNewGC || (UseConcMarkSweepGC) || UseG1GC ||
          UseParallelGC || UseParallelOldGC));
}
#endif // ASSERT
#endif // INCLUDE_ALL_GCS

void Arguments::set_gc_specific_flags() {
#if INCLUDE_ALL_GCS
  // Set per-collector flags
  if (UseParallelGC || UseParallelOldGC) {
    set_parallel_gc_flags();
  } else if (UseConcMarkSweepGC) {
    set_cms_and_parnew_gc_flags();
  } else if (UseG1GC) {
    set_g1_gc_flags();
  }
  if (AssumeMP && !UseSerialGC) {
    if (FLAG_IS_DEFAULT(ParallelGCThreads) && ParallelGCThreads == 1) {
      warning("If the number of processors is expected to increase from one, then"
              " you should configure the number of parallel GC threads appropriately"
              " using -XX:ParallelGCThreads=N");
    }
  }
  if (MinHeapFreeRatio == 100) {
    // Keeping the heap 100% free is hard ;-) so limit it to 99%.
    FLAG_SET_ERGO(uintx, MinHeapFreeRatio, 99);
  }
#else // INCLUDE_ALL_GCS
  assert(verify_serial_gc_flags(), "SerialGC unset");
#endif // INCLUDE_ALL_GCS
}

julong Arguments::limit_by_allocatable_memory(julong limit) {
  julong max_allocatable;
  julong result = limit;
  if (os::has_allocatable_memory_limit(&max_allocatable)) {
    result = MIN2(result, max_allocatable / MaxVirtMemFraction);
  }
  return result;
}

// Use static initialization to get the default before parsing
static const size_t DefaultHeapBaseMinAddress = HeapBaseMinAddress;

void Arguments::set_heap_size() {
  const julong phys_mem =
    FLAG_IS_DEFAULT(MaxRAM) ? MIN2(os::physical_memory(), (julong)MaxRAM)
                            : (julong)MaxRAM;

  // If the maximum heap size has not been set with -Xmx,
  // then set it as fraction of the size of physical memory,
  // respecting the maximum and minimum sizes of the heap.
  if (FLAG_IS_DEFAULT(MaxHeapSize)) {
    julong reasonable_max = phys_mem / MaxRAMFraction;

    if (phys_mem <= MaxHeapSize * MinRAMFraction) {
      // Small physical memory, so use a minimum fraction of it for the heap
      reasonable_max = phys_mem / MinRAMFraction;
    } else {
      // Not-small physical memory, so require a heap at least
      // as large as MaxHeapSize
      reasonable_max = MAX2(reasonable_max, (julong)MaxHeapSize);
    }
    if (!FLAG_IS_DEFAULT(ErgoHeapSizeLimit) && ErgoHeapSizeLimit != 0) {
      // Limit the heap size to ErgoHeapSizeLimit
      reasonable_max = MIN2(reasonable_max, (julong)ErgoHeapSizeLimit);
    }
    if (UseCompressedOops) {
      // Limit the heap size to the maximum possible when using compressed oops
      julong max_coop_heap = (julong)max_heap_for_compressed_oops();

      // HeapBaseMinAddress can be greater than default but not less than.
      if (!FLAG_IS_DEFAULT(HeapBaseMinAddress)) {
        if (HeapBaseMinAddress < DefaultHeapBaseMinAddress) {
          // matches compressed oops printing flags
          if (PrintCompressedOopsMode || (PrintMiscellaneous && Verbose)) {
            jio_fprintf(defaultStream::error_stream(),
                        "HeapBaseMinAddress must be at least " SIZE_FORMAT
                        " (" SIZE_FORMAT "G) which is greater than value given "
                        SIZE_FORMAT "\n",
                        DefaultHeapBaseMinAddress,
                        DefaultHeapBaseMinAddress/G,
                        HeapBaseMinAddress);
          }
          FLAG_SET_ERGO(size_t, HeapBaseMinAddress, DefaultHeapBaseMinAddress);
        }
      }

      if (HeapBaseMinAddress + MaxHeapSize < max_coop_heap) {
        // Heap should be above HeapBaseMinAddress to get zero based compressed oops
        // but it should be not less than default MaxHeapSize.
        max_coop_heap -= HeapBaseMinAddress;
      }
      reasonable_max = MIN2(reasonable_max, max_coop_heap);
    }
    reasonable_max = limit_by_allocatable_memory(reasonable_max);

    if (!FLAG_IS_DEFAULT(InitialHeapSize)) {
      // An initial heap size was specified on the command line,
      // so be sure that the maximum size is consistent.  Done
      // after call to limit_by_allocatable_memory because that
      // method might reduce the allocation size.
      reasonable_max = MAX2(reasonable_max, (julong)InitialHeapSize);
    }

    if (PrintGCDetails && Verbose) {
      // Cannot use gclog_or_tty yet.
      tty->print_cr("  Maximum heap size " SIZE_FORMAT, (size_t) reasonable_max);
    }
    FLAG_SET_ERGO(size_t, MaxHeapSize, (size_t)reasonable_max);
  }

  // If the minimum or initial heap_size have not been set or requested to be set
  // ergonomically, set them accordingly.
  if (InitialHeapSize == 0 || min_heap_size() == 0) {
    julong reasonable_minimum = (julong)(OldSize + NewSize);

    reasonable_minimum = MIN2(reasonable_minimum, (julong)MaxHeapSize);

    reasonable_minimum = limit_by_allocatable_memory(reasonable_minimum);

    if (InitialHeapSize == 0) {
      julong reasonable_initial = phys_mem / InitialRAMFraction;

      reasonable_initial = MAX3(reasonable_initial, reasonable_minimum, (julong)min_heap_size());
      reasonable_initial = MIN2(reasonable_initial, (julong)MaxHeapSize);

      reasonable_initial = limit_by_allocatable_memory(reasonable_initial);

      if (PrintGCDetails && Verbose) {
        // Cannot use gclog_or_tty yet.
        tty->print_cr("  Initial heap size " SIZE_FORMAT, (size_t)reasonable_initial);
      }
      FLAG_SET_ERGO(size_t, InitialHeapSize, (size_t)reasonable_initial);
    }
    // If the minimum heap size has not been set (via -Xms),
    // synchronize with InitialHeapSize to avoid errors with the default value.
    if (min_heap_size() == 0) {
      set_min_heap_size(MIN2((size_t)reasonable_minimum, InitialHeapSize));
      if (PrintGCDetails && Verbose) {
        // Cannot use gclog_or_tty yet.
        tty->print_cr("  Minimum heap size " SIZE_FORMAT, min_heap_size());
      }
    }
  }
}

// This option inspects the machine and attempts to set various
// parameters to be optimal for long-running, memory allocation
// intensive jobs.  It is intended for machines with large
// amounts of cpu and memory.
jint Arguments::set_aggressive_heap_flags() {
  // initHeapSize is needed since _initial_heap_size is 4 bytes on a 32 bit
  // VM, but we may not be able to represent the total physical memory
  // available (like having 8gb of memory on a box but using a 32bit VM).
  // Thus, we need to make sure we're using a julong for intermediate
  // calculations.
  julong initHeapSize;
  julong total_memory = os::physical_memory();

  if (total_memory < (julong) 256 * M) {
    jio_fprintf(defaultStream::error_stream(),
            "You need at least 256mb of memory to use -XX:+AggressiveHeap\n");
    vm_exit(1);
  }

  // The heap size is half of available memory, or (at most)
  // all of possible memory less 160mb (leaving room for the OS
  // when using ISM).  This is the maximum; because adaptive sizing
  // is turned on below, the actual space used may be smaller.

  initHeapSize = MIN2(total_memory / (julong) 2,
          total_memory - (julong) 160 * M);

  initHeapSize = limit_by_allocatable_memory(initHeapSize);

  if (FLAG_IS_DEFAULT(MaxHeapSize)) {
    if (FLAG_SET_CMDLINE(size_t, MaxHeapSize, initHeapSize) != Flag::SUCCESS) {
      return JNI_EINVAL;
    }
    if (FLAG_SET_CMDLINE(size_t, InitialHeapSize, initHeapSize) != Flag::SUCCESS) {
      return JNI_EINVAL;
    }
    // Currently the minimum size and the initial heap sizes are the same.
    set_min_heap_size(initHeapSize);
  }
  if (FLAG_IS_DEFAULT(NewSize)) {
    // Make the young generation 3/8ths of the total heap.
    if (FLAG_SET_CMDLINE(size_t, NewSize,
            ((julong) MaxHeapSize / (julong) 8) * (julong) 3) != Flag::SUCCESS) {
      return JNI_EINVAL;
    }
    if (FLAG_SET_CMDLINE(size_t, MaxNewSize, NewSize) != Flag::SUCCESS) {
      return JNI_EINVAL;
    }
  }

#if !defined(_ALLBSD_SOURCE) && !defined(AIX)  // UseLargePages is not yet supported on BSD and AIX.
  FLAG_SET_DEFAULT(UseLargePages, true);
#endif

  // Increase some data structure sizes for efficiency
  if (FLAG_SET_CMDLINE(size_t, BaseFootPrintEstimate, MaxHeapSize) != Flag::SUCCESS) {
    return JNI_EINVAL;
  }
  if (FLAG_SET_CMDLINE(bool, ResizeTLAB, false) != Flag::SUCCESS) {
    return JNI_EINVAL;
  }
  if (FLAG_SET_CMDLINE(size_t, TLABSize, 256 * K) != Flag::SUCCESS) {
    return JNI_EINVAL;
  }

  // See the OldPLABSize comment below, but replace 'after promotion'
  // with 'after copying'.  YoungPLABSize is the size of the survivor
  // space per-gc-thread buffers.  The default is 4kw.
  if (FLAG_SET_CMDLINE(size_t, YoungPLABSize, 256 * K) != Flag::SUCCESS) { // Note: this is in words
    return JNI_EINVAL;
  }

  // OldPLABSize is the size of the buffers in the old gen that
  // UseParallelGC uses to promote live data that doesn't fit in the
  // survivor spaces.  At any given time, there's one for each gc thread.
  // The default size is 1kw. These buffers are rarely used, since the
  // survivor spaces are usually big enough.  For specjbb, however, there
  // are occasions when there's lots of live data in the young gen
  // and we end up promoting some of it.  We don't have a definite
  // explanation for why bumping OldPLABSize helps, but the theory
  // is that a bigger PLAB results in retaining something like the
  // original allocation order after promotion, which improves mutator
  // locality.  A minor effect may be that larger PLABs reduce the
  // number of PLAB allocation events during gc.  The value of 8kw
  // was arrived at by experimenting with specjbb.
  if (FLAG_SET_CMDLINE(size_t, OldPLABSize, 8 * K) != Flag::SUCCESS) { // Note: this is in words
    return JNI_EINVAL;
  }

  // Enable parallel GC and adaptive generation sizing
  if (FLAG_SET_CMDLINE(bool, UseParallelGC, true) != Flag::SUCCESS) {
    return JNI_EINVAL;
  }
  FLAG_SET_DEFAULT(ParallelGCThreads,
          Abstract_VM_Version::parallel_worker_threads());

  // Encourage steady state memory management
  if (FLAG_SET_CMDLINE(uintx, ThresholdTolerance, 100) != Flag::SUCCESS) {
    return JNI_EINVAL;
  }

  // This appears to improve mutator locality
  if (FLAG_SET_CMDLINE(bool, ScavengeBeforeFullGC, false) != Flag::SUCCESS) {
    return JNI_EINVAL;
  }

  // Get around early Solaris scheduling bug
  // (affinity vs other jobs on system)
  // but disallow DR and offlining (5008695).
  if (FLAG_SET_CMDLINE(bool, BindGCTaskThreadsToCPUs, true) != Flag::SUCCESS) {
    return JNI_EINVAL;
  }

  return JNI_OK;
}

// This must be called after ergonomics.
void Arguments::set_bytecode_flags() {
  if (!RewriteBytecodes) {
    FLAG_SET_DEFAULT(RewriteFrequentPairs, false);
  }
}

// Aggressive optimization flags  -XX:+AggressiveOpts
jint Arguments::set_aggressive_opts_flags() {
#ifdef COMPILER2
  if (AggressiveUnboxing) {
    if (FLAG_IS_DEFAULT(EliminateAutoBox)) {
      FLAG_SET_DEFAULT(EliminateAutoBox, true);
    } else if (!EliminateAutoBox) {
      // warning("AggressiveUnboxing is disabled because EliminateAutoBox is disabled");
      AggressiveUnboxing = false;
    }
    if (FLAG_IS_DEFAULT(DoEscapeAnalysis)) {
      FLAG_SET_DEFAULT(DoEscapeAnalysis, true);
    } else if (!DoEscapeAnalysis) {
      // warning("AggressiveUnboxing is disabled because DoEscapeAnalysis is disabled");
      AggressiveUnboxing = false;
    }
  }
  if (AggressiveOpts || !FLAG_IS_DEFAULT(AutoBoxCacheMax)) {
    if (FLAG_IS_DEFAULT(EliminateAutoBox)) {
      FLAG_SET_DEFAULT(EliminateAutoBox, true);
    }
    if (FLAG_IS_DEFAULT(AutoBoxCacheMax)) {
      FLAG_SET_DEFAULT(AutoBoxCacheMax, 20000);
    }

    // Feed the cache size setting into the JDK
    char buffer[1024];
    sprintf(buffer, "java.lang.Integer.IntegerCache.high=" INTX_FORMAT, AutoBoxCacheMax);
    if (!add_property(buffer)) {
      return JNI_ENOMEM;
    }
  }
  if (AggressiveOpts && FLAG_IS_DEFAULT(BiasedLockingStartupDelay)) {
    FLAG_SET_DEFAULT(BiasedLockingStartupDelay, 500);
  }
#endif

  if (AggressiveOpts) {
// Sample flag setting code
//    if (FLAG_IS_DEFAULT(EliminateZeroing)) {
//      FLAG_SET_DEFAULT(EliminateZeroing, true);
//    }
  }

  return JNI_OK;
}

//===========================================================================================================
// Parsing of java.compiler property

void Arguments::process_java_compiler_argument(const char* arg) {
  // For backwards compatibility, Djava.compiler=NONE or ""
  // causes us to switch to -Xint mode UNLESS -Xdebug
  // is also specified.
  if (strlen(arg) == 0 || strcasecmp(arg, "NONE") == 0) {
    set_java_compiler(true);    // "-Djava.compiler[=...]" most recently seen.
  }
}

void Arguments::process_java_launcher_argument(const char* launcher, void* extra_info) {
  _sun_java_launcher = os::strdup_check_oom(launcher);
}

bool Arguments::created_by_java_launcher() {
  assert(_sun_java_launcher != NULL, "property must have value");
  return strcmp(DEFAULT_JAVA_LAUNCHER, _sun_java_launcher) != 0;
}

bool Arguments::sun_java_launcher_is_altjvm() {
  return _sun_java_launcher_is_altjvm;
}

//===========================================================================================================
// Parsing of main arguments

// check if do gclog rotation
// +UseGCLogFileRotation is a must,
// no gc log rotation when log file not supplied or
// NumberOfGCLogFiles is 0
void check_gclog_consistency() {
  if (UseGCLogFileRotation) {
    if ((Arguments::gc_log_filename() == NULL) || (NumberOfGCLogFiles == 0)) {
      jio_fprintf(defaultStream::output_stream(),
                  "To enable GC log rotation, use -Xloggc:<filename> -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=<num_of_files>\n"
                  "where num_of_file > 0\n"
                  "GC log rotation is turned off\n");
      UseGCLogFileRotation = false;
    }
  }

  if (UseGCLogFileRotation && (GCLogFileSize != 0) && (GCLogFileSize < 8*K)) {
    if (FLAG_SET_CMDLINE(size_t, GCLogFileSize, 8*K) == Flag::SUCCESS) {
      jio_fprintf(defaultStream::output_stream(),
                "GCLogFileSize changed to minimum 8K\n");
    }
  }
}

// This function is called for -Xloggc:<filename>, it can be used
// to check if a given file name(or string) conforms to the following
// specification:
// A valid string only contains "[A-Z][a-z][0-9].-_%[p|t]"
// %p and %t only allowed once. We only limit usage of filename not path
bool is_filename_valid(const char *file_name) {
  const char* p = file_name;
  char file_sep = os::file_separator()[0];
  const char* cp;
  // skip prefix path
  for (cp = file_name; *cp != '\0'; cp++) {
    if (*cp == '/' || *cp == file_sep) {
      p = cp + 1;
    }
  }

  int count_p = 0;
  int count_t = 0;
  while (*p != '\0') {
    if ((*p >= '0' && *p <= '9') ||
        (*p >= 'A' && *p <= 'Z') ||
        (*p >= 'a' && *p <= 'z') ||
         *p == '-'               ||
         *p == '_'               ||
         *p == '.') {
       p++;
       continue;
    }
    if (*p == '%') {
      if(*(p + 1) == 'p') {
        p += 2;
        count_p ++;
        continue;
      }
      if (*(p + 1) == 't') {
        p += 2;
        count_t ++;
        continue;
      }
    }
    return false;
  }
  return count_p < 2 && count_t < 2;
}

// Check consistency of GC selection
bool Arguments::check_gc_consistency() {
  check_gclog_consistency();
  // Ensure that the user has not selected conflicting sets
  // of collectors.
  uint i = 0;
  if (UseSerialGC)                       i++;
  if (UseConcMarkSweepGC)                i++;
  if (UseParallelGC || UseParallelOldGC) i++;
  if (UseG1GC)                           i++;
  if (i > 1) {
    jio_fprintf(defaultStream::error_stream(),
                "Conflicting collector combinations in option list; "
                "please refer to the release notes for the combinations "
                "allowed\n");
    return false;
  }

  if (UseConcMarkSweepGC && !UseParNewGC) {
    jio_fprintf(defaultStream::error_stream(),
        "It is not possible to combine the DefNew young collector with the CMS collector.\n");
    return false;
  }

  if (UseParNewGC && !UseConcMarkSweepGC) {
    jio_fprintf(defaultStream::error_stream(),
        "It is not possible to combine the ParNew young collector with any collector other than CMS.\n");
    return false;
  }

  return true;
}

// Check the consistency of vm_init_args
bool Arguments::check_vm_args_consistency() {
  // Method for adding checks for flag consistency.
  // The intent is to warn the user of all possible conflicts,
  // before returning an error.
  // Note: Needs platform-dependent factoring.
  bool status = true;

  if (TLABRefillWasteFraction == 0) {
    jio_fprintf(defaultStream::error_stream(),
                "TLABRefillWasteFraction should be a denominator, "
                "not " SIZE_FORMAT "\n",
                TLABRefillWasteFraction);
    status = false;
  }

  if (FullGCALot && FLAG_IS_DEFAULT(MarkSweepAlwaysCompactCount)) {
    MarkSweepAlwaysCompactCount = 1;  // Move objects every gc.
  }

  if (UseParallelOldGC && ParallelOldGCSplitALot) {
    // Settings to encourage splitting.
    if (!FLAG_IS_CMDLINE(NewRatio)) {
      if (FLAG_SET_CMDLINE(uintx, NewRatio, 2) != Flag::SUCCESS) {
        status = false;
      }
    }
    if (!FLAG_IS_CMDLINE(ScavengeBeforeFullGC)) {
      if (FLAG_SET_CMDLINE(bool, ScavengeBeforeFullGC, false) != Flag::SUCCESS) {
        status = false;
      }
    }
  }

  if (!(UseParallelGC || UseParallelOldGC) && FLAG_IS_DEFAULT(ScavengeBeforeFullGC)) {
    FLAG_SET_DEFAULT(ScavengeBeforeFullGC, false);
  }

  if (GCTimeLimit == 100) {
    // Turn off gc-overhead-limit-exceeded checks
    FLAG_SET_DEFAULT(UseGCOverheadLimit, false);
  }

  status = status && check_gc_consistency();

  // CMS space iteration, which FLSVerifyAllHeapreferences entails,
  // insists that we hold the requisite locks so that the iteration is
  // MT-safe. For the verification at start-up and shut-down, we don't
  // yet have a good way of acquiring and releasing these locks,
  // which are not visible at the CollectedHeap level. We want to
  // be able to acquire these locks and then do the iteration rather
  // than just disable the lock verification. This will be fixed under
  // bug 4788986.
  if (UseConcMarkSweepGC && FLSVerifyAllHeapReferences) {
    if (VerifyDuringStartup) {
      warning("Heap verification at start-up disabled "
              "(due to current incompatibility with FLSVerifyAllHeapReferences)");
      VerifyDuringStartup = false; // Disable verification at start-up
    }

    if (VerifyBeforeExit) {
      warning("Heap verification at shutdown disabled "
              "(due to current incompatibility with FLSVerifyAllHeapReferences)");
      VerifyBeforeExit = false; // Disable verification at shutdown
    }
  }

  // Note: only executed in non-PRODUCT mode
  if (!UseAsyncConcMarkSweepGC &&
      (ExplicitGCInvokesConcurrent ||
       ExplicitGCInvokesConcurrentAndUnloadsClasses)) {
    jio_fprintf(defaultStream::error_stream(),
                "error: +ExplicitGCInvokesConcurrent[AndUnloadsClasses] conflicts"
                " with -UseAsyncConcMarkSweepGC");
    status = false;
  }

  if (PrintNMTStatistics) {
#if INCLUDE_NMT
    if (MemTracker::tracking_level() == NMT_off) {
#endif // INCLUDE_NMT
      warning("PrintNMTStatistics is disabled, because native memory tracking is not enabled");
      PrintNMTStatistics = false;
#if INCLUDE_NMT
    }
#endif
  }
#if INCLUDE_JVMCI
  if (EnableJVMCI) {
    if (!ScavengeRootsInCode) {
      warning("forcing ScavengeRootsInCode non-zero because JVMCI is enabled");
      ScavengeRootsInCode = 1;
    }
    if (FLAG_IS_DEFAULT(TypeProfileLevel)) {
      TypeProfileLevel = 0;
    }
    if (UseJVMCICompiler) {
      if (FLAG_IS_DEFAULT(TypeProfileWidth)) {
        TypeProfileWidth = 8;
      }
    }
  }
#endif

  // Check lower bounds of the code cache
  // Template Interpreter code is approximately 3X larger in debug builds.
  uint min_code_cache_size = CodeCacheMinimumUseSpace DEBUG_ONLY(* 3);
  if (InitialCodeCacheSize < (uintx)os::vm_page_size()) {
    jio_fprintf(defaultStream::error_stream(),
                "Invalid InitialCodeCacheSize=%dK. Must be at least %dK.\n", InitialCodeCacheSize/K,
                os::vm_page_size()/K);
    status = false;
  } else if (ReservedCodeCacheSize < InitialCodeCacheSize) {
    jio_fprintf(defaultStream::error_stream(),
                "Invalid ReservedCodeCacheSize: %dK. Must be at least InitialCodeCacheSize=%dK.\n",
                ReservedCodeCacheSize/K, InitialCodeCacheSize/K);
    status = false;
  } else if (ReservedCodeCacheSize < min_code_cache_size) {
    jio_fprintf(defaultStream::error_stream(),
                "Invalid ReservedCodeCacheSize=%dK. Must be at least %uK.\n", ReservedCodeCacheSize/K,
                min_code_cache_size/K);
    status = false;
  } else if (ReservedCodeCacheSize > CODE_CACHE_SIZE_LIMIT) {
    // Code cache size larger than CODE_CACHE_SIZE_LIMIT is not supported.
    jio_fprintf(defaultStream::error_stream(),
                "Invalid ReservedCodeCacheSize=%dM. Must be at most %uM.\n", ReservedCodeCacheSize/M,
                CODE_CACHE_SIZE_LIMIT/M);
    status = false;
  } else if (NonNMethodCodeHeapSize < min_code_cache_size){
    jio_fprintf(defaultStream::error_stream(),
                "Invalid NonNMethodCodeHeapSize=%dK. Must be at least %uK.\n", NonNMethodCodeHeapSize/K,
                min_code_cache_size/K);
    status = false;
  } else if ((!FLAG_IS_DEFAULT(NonNMethodCodeHeapSize) || !FLAG_IS_DEFAULT(ProfiledCodeHeapSize) || !FLAG_IS_DEFAULT(NonProfiledCodeHeapSize))
             && (NonNMethodCodeHeapSize + NonProfiledCodeHeapSize + ProfiledCodeHeapSize) != ReservedCodeCacheSize) {
    jio_fprintf(defaultStream::error_stream(),
                "Invalid code heap sizes: NonNMethodCodeHeapSize(%dK) + ProfiledCodeHeapSize(%dK) + NonProfiledCodeHeapSize(%dK) = %dK. Must be equal to ReservedCodeCacheSize = %uK.\n",
                NonNMethodCodeHeapSize/K, ProfiledCodeHeapSize/K, NonProfiledCodeHeapSize/K,
                (NonNMethodCodeHeapSize + ProfiledCodeHeapSize + NonProfiledCodeHeapSize)/K, ReservedCodeCacheSize/K);
    status = false;
  }

  if (!FLAG_IS_DEFAULT(CICompilerCount) && !FLAG_IS_DEFAULT(CICompilerCountPerCPU) && CICompilerCountPerCPU) {
    warning("The VM option CICompilerCountPerCPU overrides CICompilerCount.");
  }

  return status;
}

bool Arguments::is_bad_option(const JavaVMOption* option, jboolean ignore,
  const char* option_type) {
  if (ignore) return false;

  const char* spacer = " ";
  if (option_type == NULL) {
    option_type = ++spacer; // Set both to the empty string.
  }

  if (os::obsolete_option(option)) {
    jio_fprintf(defaultStream::error_stream(),
                "Obsolete %s%soption: %s\n", option_type, spacer,
      option->optionString);
    return false;
  } else {
    jio_fprintf(defaultStream::error_stream(),
                "Unrecognized %s%soption: %s\n", option_type, spacer,
      option->optionString);
    return true;
  }
}

static const char* user_assertion_options[] = {
  "-da", "-ea", "-disableassertions", "-enableassertions", 0
};

static const char* system_assertion_options[] = {
  "-dsa", "-esa", "-disablesystemassertions", "-enablesystemassertions", 0
};

bool Arguments::parse_uintx(const char* value,
                            uintx* uintx_arg,
                            uintx min_size) {

  // Check the sign first since atomull() parses only unsigned values.
  bool value_is_positive = !(*value == '-');

  if (value_is_positive) {
    julong n;
    bool good_return = atomull(value, &n);
    if (good_return) {
      bool above_minimum = n >= min_size;
      bool value_is_too_large = n > max_uintx;

      if (above_minimum && !value_is_too_large) {
        *uintx_arg = n;
        return true;
      }
    }
  }
  return false;
}

Arguments::ArgsRange Arguments::parse_memory_size(const char* s,
                                                  julong* long_arg,
                                                  julong min_size) {
  if (!atomull(s, long_arg)) return arg_unreadable;
  return check_memory_size(*long_arg, min_size);
}

// Parse JavaVMInitArgs structure

jint Arguments::parse_vm_init_args(const JavaVMInitArgs *java_tool_options_args,
                                   const JavaVMInitArgs *java_options_args,
                                   const JavaVMInitArgs *cmd_line_args) {
  // For components of the system classpath.
  SysClassPath scp(Arguments::get_sysclasspath());
  bool scp_assembly_required = false;

  // Save default settings for some mode flags
  Arguments::_AlwaysCompileLoopMethods = AlwaysCompileLoopMethods;
  Arguments::_UseOnStackReplacement    = UseOnStackReplacement;
  Arguments::_ClipInlining             = ClipInlining;
  Arguments::_BackgroundCompilation    = BackgroundCompilation;
  if (TieredCompilation) {
    Arguments::_Tier3InvokeNotifyFreqLog = Tier3InvokeNotifyFreqLog;
    Arguments::_Tier4InvocationThreshold = Tier4InvocationThreshold;
  }

  // Setup flags for mixed which is the default
  set_mode_flags(_mixed);

  // Parse args structure generated from JAVA_TOOL_OPTIONS environment
  // variable (if present).
  jint result = parse_each_vm_init_arg(
      java_tool_options_args, &scp, &scp_assembly_required, Flag::ENVIRON_VAR);
  if (result != JNI_OK) {
    return result;
  }

  // Parse args structure generated from the command line flags.
  result = parse_each_vm_init_arg(cmd_line_args, &scp, &scp_assembly_required,
                                  Flag::COMMAND_LINE);
  if (result != JNI_OK) {
    return result;
  }

  // Parse args structure generated from the _JAVA_OPTIONS environment
  // variable (if present) (mimics classic VM)
  result = parse_each_vm_init_arg(
      java_options_args, &scp, &scp_assembly_required, Flag::ENVIRON_VAR);
  if (result != JNI_OK) {
    return result;
  }

  // Do final processing now that all arguments have been parsed
  result = finalize_vm_init_args(&scp, scp_assembly_required);
  if (result != JNI_OK) {
    return result;
  }

  return JNI_OK;
}

// Checks if name in command-line argument -agent{lib,path}:name[=options]
// represents a valid JDWP agent.  is_path==true denotes that we
// are dealing with -agentpath (case where name is a path), otherwise with
// -agentlib
bool valid_jdwp_agent(char *name, bool is_path) {
  char *_name;
  const char *_jdwp = "jdwp";
  size_t _len_jdwp, _len_prefix;

  if (is_path) {
    if ((_name = strrchr(name, (int) *os::file_separator())) == NULL) {
      return false;
    }

    _name++;  // skip past last path separator
    _len_prefix = strlen(JNI_LIB_PREFIX);

    if (strncmp(_name, JNI_LIB_PREFIX, _len_prefix) != 0) {
      return false;
    }

    _name += _len_prefix;
    _len_jdwp = strlen(_jdwp);

    if (strncmp(_name, _jdwp, _len_jdwp) == 0) {
      _name += _len_jdwp;
    }
    else {
      return false;
    }

    if (strcmp(_name, JNI_LIB_SUFFIX) != 0) {
      return false;
    }

    return true;
  }

  if (strcmp(name, _jdwp) == 0) {
    return true;
  }

  return false;
}

jint Arguments::parse_each_vm_init_arg(const JavaVMInitArgs* args,
                                       SysClassPath* scp_p,
                                       bool* scp_assembly_required_p,
                                       Flag::Flags origin) {
  // Remaining part of option string
  const char* tail;

  // iterate over arguments
  for (int index = 0; index < args->nOptions; index++) {
    bool is_absolute_path = false;  // for -agentpath vs -agentlib

    const JavaVMOption* option = args->options + index;

    if (!match_option(option, "-Djava.class.path", &tail) &&
        !match_option(option, "-Dsun.java.command", &tail) &&
        !match_option(option, "-Dsun.java.launcher", &tail)) {

        // add all jvm options to the jvm_args string. This string
        // is used later to set the java.vm.args PerfData string constant.
        // the -Djava.class.path and the -Dsun.java.command options are
        // omitted from jvm_args string as each have their own PerfData
        // string constant object.
        build_jvm_args(option->optionString);
    }

    // -verbose:[class/gc/jni]
    if (match_option(option, "-verbose", &tail)) {
      if (!strcmp(tail, ":class") || !strcmp(tail, "")) {
        if (FLAG_SET_CMDLINE(bool, TraceClassLoading, true) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
        if (FLAG_SET_CMDLINE(bool, TraceClassUnloading, true) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
      } else if (!strcmp(tail, ":gc")) {
        if (FLAG_SET_CMDLINE(bool, PrintGC, true) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
      } else if (!strcmp(tail, ":jni")) {
        if (FLAG_SET_CMDLINE(bool, PrintJNIResolving, true) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
      }
    // -da / -ea / -disableassertions / -enableassertions
    // These accept an optional class/package name separated by a colon, e.g.,
    // -da:java.lang.Thread.
    } else if (match_option(option, user_assertion_options, &tail, true)) {
      bool enable = option->optionString[1] == 'e';     // char after '-' is 'e'
      if (*tail == '\0') {
        JavaAssertions::setUserClassDefault(enable);
      } else {
        assert(*tail == ':', "bogus match by match_option()");
        JavaAssertions::addOption(tail + 1, enable);
      }
    // -dsa / -esa / -disablesystemassertions / -enablesystemassertions
    } else if (match_option(option, system_assertion_options, &tail, false)) {
      bool enable = option->optionString[1] == 'e';     // char after '-' is 'e'
      JavaAssertions::setSystemClassDefault(enable);
    // -bootclasspath:
    } else if (match_option(option, "-Xbootclasspath:", &tail)) {
      scp_p->reset_path(tail);
      *scp_assembly_required_p = true;
    // -bootclasspath/a:
    } else if (match_option(option, "-Xbootclasspath/a:", &tail)) {
      scp_p->add_suffix(tail);
      *scp_assembly_required_p = true;
    // -bootclasspath/p:
    } else if (match_option(option, "-Xbootclasspath/p:", &tail)) {
      scp_p->add_prefix(tail);
      *scp_assembly_required_p = true;
    // -Xrun
    } else if (match_option(option, "-Xrun", &tail)) {
      if (tail != NULL) {
        const char* pos = strchr(tail, ':');
        size_t len = (pos == NULL) ? strlen(tail) : pos - tail;
        char* name = (char*)memcpy(NEW_C_HEAP_ARRAY(char, len + 1, mtInternal), tail, len);
        name[len] = '\0';

        char *options = NULL;
        if(pos != NULL) {
          size_t len2 = strlen(pos+1) + 1; // options start after ':'.  Final zero must be copied.
          options = (char*)memcpy(NEW_C_HEAP_ARRAY(char, len2, mtInternal), pos+1, len2);
        }
#if !INCLUDE_JVMTI
        if (strcmp(name, "jdwp") == 0) {
          jio_fprintf(defaultStream::error_stream(),
            "Debugging agents are not supported in this VM\n");
          return JNI_ERR;
        }
#endif // !INCLUDE_JVMTI
        add_init_library(name, options);
      }
    // -agentlib and -agentpath
    } else if (match_option(option, "-agentlib:", &tail) ||
          (is_absolute_path = match_option(option, "-agentpath:", &tail))) {
      if(tail != NULL) {
        const char* pos = strchr(tail, '=');
        size_t len = (pos == NULL) ? strlen(tail) : pos - tail;
        char* name = strncpy(NEW_C_HEAP_ARRAY(char, len + 1, mtInternal), tail, len);
        name[len] = '\0';

        char *options = NULL;
        if(pos != NULL) {
          options = os::strdup_check_oom(pos + 1, mtInternal);
        }
#if !INCLUDE_JVMTI
        if (valid_jdwp_agent(name, is_absolute_path)) {
          jio_fprintf(defaultStream::error_stream(),
            "Debugging agents are not supported in this VM\n");
          return JNI_ERR;
        }
#endif // !INCLUDE_JVMTI
        add_init_agent(name, options, is_absolute_path);
      }
    // -javaagent
    } else if (match_option(option, "-javaagent:", &tail)) {
#if !INCLUDE_JVMTI
      jio_fprintf(defaultStream::error_stream(),
        "Instrumentation agents are not supported in this VM\n");
      return JNI_ERR;
#else
      if(tail != NULL) {
        char *options = strcpy(NEW_C_HEAP_ARRAY(char, strlen(tail) + 1, mtInternal), tail);
        add_init_agent("instrument", options, false);
      }
#endif // !INCLUDE_JVMTI
    // -Xnoclassgc
    } else if (match_option(option, "-Xnoclassgc")) {
      if (FLAG_SET_CMDLINE(bool, ClassUnloading, false) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    // -Xconcgc
    } else if (match_option(option, "-Xconcgc")) {
      if (FLAG_SET_CMDLINE(bool, UseConcMarkSweepGC, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    // -Xnoconcgc
    } else if (match_option(option, "-Xnoconcgc")) {
      if (FLAG_SET_CMDLINE(bool, UseConcMarkSweepGC, false) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    // -Xbatch
    } else if (match_option(option, "-Xbatch")) {
      if (FLAG_SET_CMDLINE(bool, BackgroundCompilation, false) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    // -Xmn for compatibility with other JVM vendors
    } else if (match_option(option, "-Xmn", &tail)) {
      julong long_initial_young_size = 0;
      ArgsRange errcode = parse_memory_size(tail, &long_initial_young_size, 1);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid initial young generation size: %s\n", option->optionString);
        describe_range_error(errcode);
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(size_t, MaxNewSize, (size_t)long_initial_young_size) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(size_t, NewSize, (size_t)long_initial_young_size) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    // -Xms
    } else if (match_option(option, "-Xms", &tail)) {
      julong long_initial_heap_size = 0;
      // an initial heap size of 0 means automatically determine
      ArgsRange errcode = parse_memory_size(tail, &long_initial_heap_size, 0);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid initial heap size: %s\n", option->optionString);
        describe_range_error(errcode);
        return JNI_EINVAL;
      }
      set_min_heap_size((size_t)long_initial_heap_size);
      // Currently the minimum size and the initial heap sizes are the same.
      // Can be overridden with -XX:InitialHeapSize.
      if (FLAG_SET_CMDLINE(size_t, InitialHeapSize, (size_t)long_initial_heap_size) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    // -Xmx
    } else if (match_option(option, "-Xmx", &tail) || match_option(option, "-XX:MaxHeapSize=", &tail)) {
      julong long_max_heap_size = 0;
      ArgsRange errcode = parse_memory_size(tail, &long_max_heap_size, 1);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid maximum heap size: %s\n", option->optionString);
        describe_range_error(errcode);
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(size_t, MaxHeapSize, (size_t)long_max_heap_size) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    // Xmaxf
    } else if (match_option(option, "-Xmaxf", &tail)) {
      char* err;
      int maxf = (int)(strtod(tail, &err) * 100);
      if (*err != '\0' || *tail == '\0') {
        jio_fprintf(defaultStream::error_stream(),
                    "Bad max heap free percentage size: %s\n",
                    option->optionString);
        return JNI_EINVAL;
      } else {
        if (FLAG_SET_CMDLINE(uintx, MaxHeapFreeRatio, maxf) != Flag::SUCCESS) {
            return JNI_EINVAL;
        }
      }
    // Xminf
    } else if (match_option(option, "-Xminf", &tail)) {
      char* err;
      int minf = (int)(strtod(tail, &err) * 100);
      if (*err != '\0' || *tail == '\0') {
        jio_fprintf(defaultStream::error_stream(),
                    "Bad min heap free percentage size: %s\n",
                    option->optionString);
        return JNI_EINVAL;
      } else {
        if (FLAG_SET_CMDLINE(uintx, MinHeapFreeRatio, minf) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
      }
    // -Xss
    } else if (match_option(option, "-Xss", &tail)) {
      julong long_ThreadStackSize = 0;
      ArgsRange errcode = parse_memory_size(tail, &long_ThreadStackSize, 1000);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid thread stack size: %s\n", option->optionString);
        describe_range_error(errcode);
        return JNI_EINVAL;
      }
      // Internally track ThreadStackSize in units of 1024 bytes.
      if (FLAG_SET_CMDLINE(intx, ThreadStackSize,
                       round_to((int)long_ThreadStackSize, K) / K) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    // -Xoss, -Xsqnopause, -Xoptimize, -Xboundthreads
    } else if (match_option(option, "-Xoss", &tail) ||
               match_option(option, "-Xsqnopause") ||
               match_option(option, "-Xoptimize") ||
               match_option(option, "-Xboundthreads")) {
      // All these options are deprecated in JDK 9 and will be removed in a future release
      char version[256];
      JDK_Version::jdk(9).to_string(version, sizeof(version));
      warning("Ignoring option %s; support was removed in %s", option->optionString, version);
    } else if (match_option(option, "-XX:CodeCacheExpansionSize=", &tail)) {
      julong long_CodeCacheExpansionSize = 0;
      ArgsRange errcode = parse_memory_size(tail, &long_CodeCacheExpansionSize, os::vm_page_size());
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                   "Invalid argument: %s. Must be at least %luK.\n", option->optionString,
                   os::vm_page_size()/K);
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(uintx, CodeCacheExpansionSize, (uintx)long_CodeCacheExpansionSize) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    } else if (match_option(option, "-Xmaxjitcodesize", &tail) ||
               match_option(option, "-XX:ReservedCodeCacheSize=", &tail)) {
      julong long_ReservedCodeCacheSize = 0;

      ArgsRange errcode = parse_memory_size(tail, &long_ReservedCodeCacheSize, 1);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid maximum code cache size: %s.\n", option->optionString);
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(uintx, ReservedCodeCacheSize, (uintx)long_ReservedCodeCacheSize) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      // -XX:NonNMethodCodeHeapSize=
    } else if (match_option(option, "-XX:NonNMethodCodeHeapSize=", &tail)) {
      julong long_NonNMethodCodeHeapSize = 0;

      ArgsRange errcode = parse_memory_size(tail, &long_NonNMethodCodeHeapSize, 1);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid maximum non-nmethod code heap size: %s.\n", option->optionString);
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(uintx, NonNMethodCodeHeapSize, (uintx)long_NonNMethodCodeHeapSize) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      // -XX:ProfiledCodeHeapSize=
    } else if (match_option(option, "-XX:ProfiledCodeHeapSize=", &tail)) {
      julong long_ProfiledCodeHeapSize = 0;

      ArgsRange errcode = parse_memory_size(tail, &long_ProfiledCodeHeapSize, 1);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid maximum profiled code heap size: %s.\n", option->optionString);
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(uintx, ProfiledCodeHeapSize, (uintx)long_ProfiledCodeHeapSize) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      // -XX:NonProfiledCodeHeapSizee=
    } else if (match_option(option, "-XX:NonProfiledCodeHeapSize=", &tail)) {
      julong long_NonProfiledCodeHeapSize = 0;

      ArgsRange errcode = parse_memory_size(tail, &long_NonProfiledCodeHeapSize, 1);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid maximum non-profiled code heap size: %s.\n", option->optionString);
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(uintx, NonProfiledCodeHeapSize, (uintx)long_NonProfiledCodeHeapSize) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    // -green
    } else if (match_option(option, "-green")) {
      jio_fprintf(defaultStream::error_stream(),
                  "Green threads support not available\n");
          return JNI_EINVAL;
    // -native
    } else if (match_option(option, "-native")) {
          // HotSpot always uses native threads, ignore silently for compatibility
    // -Xrs
    } else if (match_option(option, "-Xrs")) {
          // Classic/EVM option, new functionality
      if (FLAG_SET_CMDLINE(bool, ReduceSignalUsage, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    } else if (match_option(option, "-Xusealtsigs")) {
          // change default internal VM signals used - lower case for back compat
      if (FLAG_SET_CMDLINE(bool, UseAltSigs, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    // -Xprof
    } else if (match_option(option, "-Xprof")) {
#if INCLUDE_FPROF
      _has_profile = true;
#else // INCLUDE_FPROF
      jio_fprintf(defaultStream::error_stream(),
        "Flat profiling is not supported in this VM.\n");
      return JNI_ERR;
#endif // INCLUDE_FPROF
    // -Xconcurrentio
    } else if (match_option(option, "-Xconcurrentio")) {
      if (FLAG_SET_CMDLINE(bool, UseLWPSynchronization, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(bool, BackgroundCompilation, false) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(intx, DeferThrSuspendLoopCount, 1) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(bool, UseTLAB, false) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(size_t, NewSizeThreadIncrease, 16 * K) != Flag::SUCCESS) {  // 20Kb per thread added to new generation
        return JNI_EINVAL;
      }

      // -Xinternalversion
    } else if (match_option(option, "-Xinternalversion")) {
      jio_fprintf(defaultStream::output_stream(), "%s\n",
                  VM_Version::internal_vm_info_string());
      vm_exit(0);
#ifndef PRODUCT
    // -Xprintflags
    } else if (match_option(option, "-Xprintflags")) {
      CommandLineFlags::printFlags(tty, false);
      vm_exit(0);
#endif
    // -D
    } else if (match_option(option, "-D", &tail)) {
      const char* value;
      if (match_option(option, "-Djava.endorsed.dirs=", &value) &&
            *value!= '\0' && strcmp(value, "\"\"") != 0) {
        // abort if -Djava.endorsed.dirs is set
        jio_fprintf(defaultStream::output_stream(),
          "-Djava.endorsed.dirs=%s is not supported. Endorsed standards and standalone APIs\n"
          "in modular form will be supported via the concept of upgradeable modules.\n", value);
        return JNI_EINVAL;
      }
      if (match_option(option, "-Djava.ext.dirs=", &value) &&
            *value != '\0' && strcmp(value, "\"\"") != 0) {
        // abort if -Djava.ext.dirs is set
        jio_fprintf(defaultStream::output_stream(),
          "-Djava.ext.dirs=%s is not supported.  Use -classpath instead.\n", value);
        return JNI_EINVAL;
      }

      if (!add_property(tail)) {
        return JNI_ENOMEM;
      }
      // Out of the box management support
      if (match_option(option, "-Dcom.sun.management", &tail)) {
#if INCLUDE_MANAGEMENT
        if (FLAG_SET_CMDLINE(bool, ManagementServer, true) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
#else
        jio_fprintf(defaultStream::output_stream(),
          "-Dcom.sun.management is not supported in this VM.\n");
        return JNI_ERR;
#endif
      }
    // -Xint
    } else if (match_option(option, "-Xint")) {
          set_mode_flags(_int);
    // -Xmixed
    } else if (match_option(option, "-Xmixed")) {
          set_mode_flags(_mixed);
    // -Xcomp
    } else if (match_option(option, "-Xcomp")) {
      // for testing the compiler; turn off all flags that inhibit compilation
          set_mode_flags(_comp);
    // -Xshare:dump
    } else if (match_option(option, "-Xshare:dump")) {
      if (FLAG_SET_CMDLINE(bool, DumpSharedSpaces, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      set_mode_flags(_int);     // Prevent compilation, which creates objects
    // -Xshare:on
    } else if (match_option(option, "-Xshare:on")) {
      if (FLAG_SET_CMDLINE(bool, UseSharedSpaces, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(bool, RequireSharedSpaces, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    // -Xshare:auto
    } else if (match_option(option, "-Xshare:auto")) {
      if (FLAG_SET_CMDLINE(bool, UseSharedSpaces, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(bool, RequireSharedSpaces, false) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    // -Xshare:off
    } else if (match_option(option, "-Xshare:off")) {
      if (FLAG_SET_CMDLINE(bool, UseSharedSpaces, false) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(bool, RequireSharedSpaces, false) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    // -Xverify
    } else if (match_option(option, "-Xverify", &tail)) {
      if (strcmp(tail, ":all") == 0 || strcmp(tail, "") == 0) {
        if (FLAG_SET_CMDLINE(bool, BytecodeVerificationLocal, true) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
        if (FLAG_SET_CMDLINE(bool, BytecodeVerificationRemote, true) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
      } else if (strcmp(tail, ":remote") == 0) {
        if (FLAG_SET_CMDLINE(bool, BytecodeVerificationLocal, false) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
        if (FLAG_SET_CMDLINE(bool, BytecodeVerificationRemote, true) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
      } else if (strcmp(tail, ":none") == 0) {
        if (FLAG_SET_CMDLINE(bool, BytecodeVerificationLocal, false) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
        if (FLAG_SET_CMDLINE(bool, BytecodeVerificationRemote, false) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
      } else if (is_bad_option(option, args->ignoreUnrecognized, "verification")) {
        return JNI_EINVAL;
      }
    // -Xdebug
    } else if (match_option(option, "-Xdebug")) {
      // note this flag has been used, then ignore
      set_xdebug_mode(true);
    // -Xnoagent
    } else if (match_option(option, "-Xnoagent")) {
      // For compatibility with classic. HotSpot refuses to load the old style agent.dll.
    } else if (match_option(option, "-Xloggc:", &tail)) {
      // Redirect GC output to the file. -Xloggc:<filename>
      // ostream_init_log(), when called will use this filename
      // to initialize a fileStream.
      _gc_log_filename = os::strdup_check_oom(tail);
     if (!is_filename_valid(_gc_log_filename)) {
       jio_fprintf(defaultStream::output_stream(),
                  "Invalid file name for use with -Xloggc: Filename can only contain the "
                  "characters [A-Z][a-z][0-9]-_.%%[p|t] but it has been %s\n"
                  "Note %%p or %%t can only be used once\n", _gc_log_filename);
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(bool, PrintGC, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(bool, PrintGCTimeStamps, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    // JNI hooks
    } else if (match_option(option, "-Xcheck", &tail)) {
      if (!strcmp(tail, ":jni")) {
#if !INCLUDE_JNI_CHECK
        warning("JNI CHECKING is not supported in this VM");
#else
        CheckJNICalls = true;
#endif // INCLUDE_JNI_CHECK
      } else if (is_bad_option(option, args->ignoreUnrecognized,
                                     "check")) {
        return JNI_EINVAL;
      }
    } else if (match_option(option, "vfprintf")) {
      _vfprintf_hook = CAST_TO_FN_PTR(vfprintf_hook_t, option->extraInfo);
    } else if (match_option(option, "exit")) {
      _exit_hook = CAST_TO_FN_PTR(exit_hook_t, option->extraInfo);
    } else if (match_option(option, "abort")) {
      _abort_hook = CAST_TO_FN_PTR(abort_hook_t, option->extraInfo);
    // -XX:+AggressiveHeap
    } else if (match_option(option, "-XX:+AggressiveHeap")) {
      jint result = set_aggressive_heap_flags();
      if (result != JNI_OK) {
          return result;
      }
    // Need to keep consistency of MaxTenuringThreshold and AlwaysTenure/NeverTenure;
    // and the last option wins.
    } else if (match_option(option, "-XX:+NeverTenure")) {
      if (FLAG_SET_CMDLINE(bool, NeverTenure, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(bool, AlwaysTenure, false) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(uintx, MaxTenuringThreshold, markOopDesc::max_age + 1) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    } else if (match_option(option, "-XX:+AlwaysTenure")) {
      if (FLAG_SET_CMDLINE(bool, NeverTenure, false) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(bool, AlwaysTenure, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(uintx, MaxTenuringThreshold, 0) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    } else if (match_option(option, "-XX:MaxTenuringThreshold=", &tail)) {
      uintx max_tenuring_thresh = 0;
      if (!parse_uintx(tail, &max_tenuring_thresh, 0)) {
        jio_fprintf(defaultStream::error_stream(),
                    "Improperly specified VM option \'MaxTenuringThreshold=%s\'\n", tail);
        return JNI_EINVAL;
      }

      if (FLAG_SET_CMDLINE(uintx, MaxTenuringThreshold, max_tenuring_thresh) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }

      if (MaxTenuringThreshold == 0) {
        if (FLAG_SET_CMDLINE(bool, NeverTenure, false) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
        if (FLAG_SET_CMDLINE(bool, AlwaysTenure, true) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
      } else {
        if (FLAG_SET_CMDLINE(bool, NeverTenure, false) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
        if (FLAG_SET_CMDLINE(bool, AlwaysTenure, false) != Flag::SUCCESS) {
          return JNI_EINVAL;
        }
      }
    } else if (match_option(option, "-XX:+DisplayVMOutputToStderr")) {
      if (FLAG_SET_CMDLINE(bool, DisplayVMOutputToStdout, false) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(bool, DisplayVMOutputToStderr, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    } else if (match_option(option, "-XX:+DisplayVMOutputToStdout")) {
      if (FLAG_SET_CMDLINE(bool, DisplayVMOutputToStderr, false) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(bool, DisplayVMOutputToStdout, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
    } else if (match_option(option, "-XX:+ExtendedDTraceProbes")) {
#if defined(DTRACE_ENABLED)
      if (FLAG_SET_CMDLINE(bool, ExtendedDTraceProbes, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(bool, DTraceMethodProbes, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(bool, DTraceAllocProbes, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(bool, DTraceMonitorProbes, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
#else // defined(DTRACE_ENABLED)
      jio_fprintf(defaultStream::error_stream(),
                  "ExtendedDTraceProbes flag is not applicable for this configuration\n");
      return JNI_EINVAL;
#endif // defined(DTRACE_ENABLED)
#ifdef ASSERT
    } else if (match_option(option, "-XX:+FullGCALot")) {
      if (FLAG_SET_CMDLINE(bool, FullGCALot, true) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
      // disable scavenge before parallel mark-compact
      if (FLAG_SET_CMDLINE(bool, ScavengeBeforeFullGC, false) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
#endif
    } else if (match_option(option, "-XX:MaxDirectMemorySize=", &tail)) {
      julong max_direct_memory_size = 0;
      ArgsRange errcode = parse_memory_size(tail, &max_direct_memory_size, 0);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid maximum direct memory size: %s\n",
                    option->optionString);
        describe_range_error(errcode);
        return JNI_EINVAL;
      }
      if (FLAG_SET_CMDLINE(size_t, MaxDirectMemorySize, max_direct_memory_size) != Flag::SUCCESS) {
        return JNI_EINVAL;
      }
#if !INCLUDE_MANAGEMENT
    } else if (match_option(option, "-XX:+ManagementServer")) {
        jio_fprintf(defaultStream::error_stream(),
          "ManagementServer is not supported in this VM.\n");
        return JNI_ERR;
#endif // INCLUDE_MANAGEMENT
    } else if (match_option(option, "-XX:", &tail)) { // -XX:xxxx
      // Skip -XX:Flags= and -XX:VMOptionsFile= since those cases have
      // already been handled
      if ((strncmp(tail, "Flags=", strlen("Flags=")) != 0) &&
          (strncmp(tail, "VMOptionsFile=", strlen("VMOptionsFile=")) != 0)) {
        if (!process_argument(tail, args->ignoreUnrecognized, origin)) {
          return JNI_EINVAL;
        }
      }
    // Unknown option
    } else if (is_bad_option(option, args->ignoreUnrecognized)) {
      return JNI_ERR;
    }
  }

  // PrintSharedArchiveAndExit will turn on
  //   -Xshare:on
  //   -XX:+TraceClassPaths
  if (PrintSharedArchiveAndExit) {
    if (FLAG_SET_CMDLINE(bool, UseSharedSpaces, true) != Flag::SUCCESS) {
      return JNI_EINVAL;
    }
    if (FLAG_SET_CMDLINE(bool, RequireSharedSpaces, true) != Flag::SUCCESS) {
      return JNI_EINVAL;
    }
    if (FLAG_SET_CMDLINE(bool, TraceClassPaths, true) != Flag::SUCCESS) {
      return JNI_EINVAL;
    }
  }

  // Change the default value for flags  which have different default values
  // when working with older JDKs.
#ifdef LINUX
 if (JDK_Version::current().compare_major(6) <= 0 &&
      FLAG_IS_DEFAULT(UseLinuxPosixThreadCPUClocks)) {
    FLAG_SET_DEFAULT(UseLinuxPosixThreadCPUClocks, false);
  }
#endif // LINUX
  fix_appclasspath();
  return JNI_OK;
}

// Remove all empty paths from the app classpath (if IgnoreEmptyClassPaths is enabled)
//
// This is necessary because some apps like to specify classpath like -cp foo.jar:${XYZ}:bar.jar
// in their start-up scripts. If XYZ is empty, the classpath will look like "-cp foo.jar::bar.jar".
// Java treats such empty paths as if the user specified "-cp foo.jar:.:bar.jar". I.e., an empty
// path is treated as the current directory.
//
// This causes problems with CDS, which requires that all directories specified in the classpath
// must be empty. In most cases, applications do NOT want to load classes from the current
// directory anyway. Adding -XX:+IgnoreEmptyClassPaths will make these applications' start-up
// scripts compatible with CDS.
void Arguments::fix_appclasspath() {
  if (IgnoreEmptyClassPaths) {
    const char separator = *os::path_separator();
    const char* src = _java_class_path->value();

    // skip over all the leading empty paths
    while (*src == separator) {
      src ++;
    }

    char* copy = os::strdup_check_oom(src, mtInternal);

    // trim all trailing empty paths
    for (char* tail = copy + strlen(copy) - 1; tail >= copy && *tail == separator; tail--) {
      *tail = '\0';
    }

    char from[3] = {separator, separator, '\0'};
    char to  [2] = {separator, '\0'};
    while (StringUtils::replace_no_expand(copy, from, to) > 0) {
      // Keep replacing "::" -> ":" until we have no more "::" (non-windows)
      // Keep replacing ";;" -> ";" until we have no more ";;" (windows)
    }

    _java_class_path->set_value(copy);
    FreeHeap(copy); // a copy was made by set_value, so don't need this anymore
  }

  if (!PrintSharedArchiveAndExit) {
    ClassLoader::trace_class_path("[classpath: ", _java_class_path->value());
  }
}

static bool has_jar_files(const char* directory) {
  DIR* dir = os::opendir(directory);
  if (dir == NULL) return false;

  struct dirent *entry;
  char *dbuf = NEW_C_HEAP_ARRAY(char, os::readdir_buf_size(directory), mtInternal);
  bool hasJarFile = false;
  while (!hasJarFile && (entry = os::readdir(dir, (dirent *) dbuf)) != NULL) {
    const char* name = entry->d_name;
    const char* ext = name + strlen(name) - 4;
    hasJarFile = ext > name && (os::file_name_strcmp(ext, ".jar") == 0);
  }
  FREE_C_HEAP_ARRAY(char, dbuf);
  os::closedir(dir);
  return hasJarFile ;
}

static int check_non_empty_dirs(const char* path) {
  const char separator = *os::path_separator();
  const char* const end = path + strlen(path);
  int nonEmptyDirs = 0;
  while (path < end) {
    const char* tmp_end = strchr(path, separator);
    if (tmp_end == NULL) {
      if (has_jar_files(path)) {
        nonEmptyDirs++;
        jio_fprintf(defaultStream::output_stream(),
          "Non-empty directory: %s\n", path);
      }
      path = end;
    } else {
      char* dirpath = NEW_C_HEAP_ARRAY(char, tmp_end - path + 1, mtInternal);
      memcpy(dirpath, path, tmp_end - path);
      dirpath[tmp_end - path] = '\0';
      if (has_jar_files(dirpath)) {
        nonEmptyDirs++;
        jio_fprintf(defaultStream::output_stream(),
          "Non-empty directory: %s\n", dirpath);
      }
      FREE_C_HEAP_ARRAY(char, dirpath);
      path = tmp_end + 1;
    }
  }
  return nonEmptyDirs;
}

jint Arguments::finalize_vm_init_args(SysClassPath* scp_p, bool scp_assembly_required) {
  // check if the default lib/endorsed directory exists; if so, error
  char path[JVM_MAXPATHLEN];
  const char* fileSep = os::file_separator();
  sprintf(path, "%s%slib%sendorsed", Arguments::get_java_home(), fileSep, fileSep);

#if INCLUDE_JVMCI
  jint res = JVMCIRuntime::save_options(_system_properties);
  if (res != JNI_OK) {
    return res;
  }

  if (EnableJVMCI) {
    // Append lib/jvmci/*.jar to boot class path
    char jvmciDir[JVM_MAXPATHLEN];
    const char* fileSep = os::file_separator();
    jio_snprintf(jvmciDir, sizeof(jvmciDir), "%s%slib%sjvmci", Arguments::get_java_home(), fileSep, fileSep);
    DIR* dir = os::opendir(jvmciDir);
    if (dir != NULL) {
      struct dirent *entry;
      char *dbuf = NEW_C_HEAP_ARRAY(char, os::readdir_buf_size(jvmciDir), mtInternal);
      while ((entry = os::readdir(dir, (dirent *) dbuf)) != NULL) {
        const char* name = entry->d_name;
        const char* ext = name + strlen(name) - 4;
        if (ext > name && strcmp(ext, ".jar") == 0) {
          char fileName[JVM_MAXPATHLEN];
          jio_snprintf(fileName, sizeof(fileName), "%s%s%s", jvmciDir, fileSep, name);
          scp_p->add_suffix(fileName);
          scp_assembly_required = true;
        }
      }
      FREE_C_HEAP_ARRAY(char, dbuf);
      os::closedir(dir);
    }
  }
#endif // INCLUDE_JVMCI

  if (CheckEndorsedAndExtDirs) {
    int nonEmptyDirs = 0;
    // check endorsed directory
    nonEmptyDirs += check_non_empty_dirs(path);
    // check the extension directories
    nonEmptyDirs += check_non_empty_dirs(Arguments::get_ext_dirs());
    if (nonEmptyDirs > 0) {
      return JNI_ERR;
    }
  }

  DIR* dir = os::opendir(path);
  if (dir != NULL) {
    jio_fprintf(defaultStream::output_stream(),
      "<JAVA_HOME>/lib/endorsed is not supported. Endorsed standards and standalone APIs\n"
      "in modular form will be supported via the concept of upgradeable modules.\n");
    os::closedir(dir);
    return JNI_ERR;
  }

  sprintf(path, "%s%slib%sext", Arguments::get_java_home(), fileSep, fileSep);
  dir = os::opendir(path);
  if (dir != NULL) {
    jio_fprintf(defaultStream::output_stream(),
      "<JAVA_HOME>/lib/ext exists, extensions mechanism no longer supported; "
      "Use -classpath instead.\n.");
    os::closedir(dir);
    return JNI_ERR;
  }

  if (scp_assembly_required) {
    // Assemble the bootclasspath elements into the final path.
    char *combined_path = scp_p->combined_path();
    Arguments::set_sysclasspath(combined_path);
    FREE_C_HEAP_ARRAY(char, combined_path);
  }

  // This must be done after all arguments have been processed.
  // java_compiler() true means set to "NONE" or empty.
  if (java_compiler() && !xdebug_mode()) {
    // For backwards compatibility, we switch to interpreted mode if
    // -Djava.compiler="NONE" or "" is specified AND "-Xdebug" was
    // not specified.
    set_mode_flags(_int);
  }

  // CompileThresholdScaling == 0.0 is same as -Xint: Disable compilation (enable interpreter-only mode),
  // but like -Xint, leave compilation thresholds unaffected.
  // With tiered compilation disabled, setting CompileThreshold to 0 disables compilation as well.
  if ((CompileThresholdScaling == 0.0) || (!TieredCompilation && CompileThreshold == 0)) {
    set_mode_flags(_int);
  }

  // eventually fix up InitialTenuringThreshold if only MaxTenuringThreshold is set
  if (FLAG_IS_DEFAULT(InitialTenuringThreshold) && (InitialTenuringThreshold > MaxTenuringThreshold)) {
    FLAG_SET_ERGO(uintx, InitialTenuringThreshold, MaxTenuringThreshold);
  }

#if !defined(COMPILER2) && !INCLUDE_JVMCI
  // Don't degrade server performance for footprint
  if (FLAG_IS_DEFAULT(UseLargePages) &&
      MaxHeapSize < LargePageHeapSizeThreshold) {
    // No need for large granularity pages w/small heaps.
    // Note that large pages are enabled/disabled for both the
    // Java heap and the code cache.
    FLAG_SET_DEFAULT(UseLargePages, false);
  }

#elif defined(COMPILER2)
  if (!FLAG_IS_DEFAULT(OptoLoopAlignment) && FLAG_IS_DEFAULT(MaxLoopPad)) {
    FLAG_SET_DEFAULT(MaxLoopPad, OptoLoopAlignment-1);
  }
#endif

#ifndef TIERED
  // Tiered compilation is undefined.
  UNSUPPORTED_OPTION(TieredCompilation, "TieredCompilation");
#endif

  // If we are running in a headless jre, force java.awt.headless property
  // to be true unless the property has already been set.
  // Also allow the OS environment variable JAVA_AWT_HEADLESS to set headless state.
  if (os::is_headless_jre()) {
    const char* headless = Arguments::get_property("java.awt.headless");
    if (headless == NULL) {
      const char *headless_env = ::getenv("JAVA_AWT_HEADLESS");
      if (headless_env == NULL) {
        if (!add_property("java.awt.headless=true")) {
          return JNI_ENOMEM;
        }
      } else {
        char buffer[256];
        jio_snprintf(buffer, sizeof(buffer), "java.awt.headless=%s", headless_env);
        if (!add_property(buffer)) {
          return JNI_ENOMEM;
        }
      }
    }
  }

  if (UseConcMarkSweepGC && FLAG_IS_DEFAULT(UseParNewGC) && !UseParNewGC) {
    // CMS can only be used with ParNew
    FLAG_SET_ERGO(bool, UseParNewGC, true);
  }

  if (!check_vm_args_consistency()) {
    return JNI_ERR;
  }

  return JNI_OK;
}

// Helper class for controlling the lifetime of JavaVMInitArgs
// objects.  The contents of the JavaVMInitArgs are guaranteed to be
// deleted on the destruction of the ScopedVMInitArgs object.
class ScopedVMInitArgs : public StackObj {
 private:
  JavaVMInitArgs _args;
  bool           _is_set;

 public:
  ScopedVMInitArgs() {
    _args.version = JNI_VERSION_1_2;
    _args.nOptions = 0;
    _args.options = NULL;
    _args.ignoreUnrecognized = false;
    _is_set = false;
  }

  // Populates the JavaVMInitArgs object represented by this
  // ScopedVMInitArgs object with the arguments in options.  The
  // allocated memory is deleted by the destructor.  If this method
  // returns anything other than JNI_OK, then this object is in a
  // partially constructed state, and should be abandoned.
  jint set_args(GrowableArray<JavaVMOption>* options) {
    _is_set = true;
    JavaVMOption* options_arr = NEW_C_HEAP_ARRAY_RETURN_NULL(
        JavaVMOption, options->length(), mtInternal);
    if (options_arr == NULL) {
      return JNI_ENOMEM;
    }
    _args.options = options_arr;

    for (int i = 0; i < options->length(); i++) {
      options_arr[i] = options->at(i);
      options_arr[i].optionString = os::strdup(options_arr[i].optionString);
      if (options_arr[i].optionString == NULL) {
        // Rely on the destructor to do cleanup.
        _args.nOptions = i;
        return JNI_ENOMEM;
      }
    }

    _args.nOptions = options->length();
    _args.ignoreUnrecognized = IgnoreUnrecognizedVMOptions;
    return JNI_OK;
  }

  JavaVMInitArgs* get() { return &_args; }
  bool is_set()         { return _is_set; }

  ~ScopedVMInitArgs() {
    if (_args.options == NULL) return;
    for (int i = 0; i < _args.nOptions; i++) {
      os::free(_args.options[i].optionString);
    }
    FREE_C_HEAP_ARRAY(JavaVMOption, _args.options);
  }

  // Insert options into this option list, to replace option at
  // vm_options_file_pos (-XX:VMOptionsFile)
  jint insert(const JavaVMInitArgs* args,
              const JavaVMInitArgs* args_to_insert,
              const int vm_options_file_pos) {
    assert(_args.options == NULL, "shouldn't be set yet");
    assert(args_to_insert->nOptions != 0, "there should be args to insert");
    assert(vm_options_file_pos != -1, "vm_options_file_pos should be set");

    int length = args->nOptions + args_to_insert->nOptions - 1;
    GrowableArray<JavaVMOption> *options = new (ResourceObj::C_HEAP, mtInternal)
              GrowableArray<JavaVMOption>(length, true);    // Construct new option array
    for (int i = 0; i < args->nOptions; i++) {
      if (i == vm_options_file_pos) {
        // insert the new options starting at the same place as the
        // -XX:VMOptionsFile option
        for (int j = 0; j < args_to_insert->nOptions; j++) {
          options->push(args_to_insert->options[j]);
        }
      } else {
        options->push(args->options[i]);
      }
    }
    // make into options array
    jint result = set_args(options);
    delete options;
    return result;
  }
};

jint Arguments::parse_java_options_environment_variable(ScopedVMInitArgs* args) {
  return parse_options_environment_variable("_JAVA_OPTIONS", args);
}

jint Arguments::parse_java_tool_options_environment_variable(ScopedVMInitArgs* args) {
  return parse_options_environment_variable("JAVA_TOOL_OPTIONS", args);
}

jint Arguments::parse_options_environment_variable(const char* name,
                                                   ScopedVMInitArgs* vm_args) {
  char *buffer = ::getenv(name);

  // Don't check this environment variable if user has special privileges
  // (e.g. unix su command).
  if (buffer == NULL || os::have_special_privileges()) {
    return JNI_OK;
  }

  if ((buffer = os::strdup(buffer)) == NULL) {
    return JNI_ENOMEM;
  }

  int retcode = parse_options_buffer(name, buffer, strlen(buffer), vm_args);

  os::free(buffer);
  return retcode;
}

const int OPTION_BUFFER_SIZE = 1024;

jint Arguments::parse_vm_options_file(const char* file_name, ScopedVMInitArgs* vm_args) {
  // read file into buffer
  int fd = ::open(file_name, O_RDONLY);
  if (fd < 0) {
    jio_fprintf(defaultStream::error_stream(),
                "Could not open options file '%s'\n",
                file_name);
    return JNI_ERR;
  }

  // '+ 1' for NULL termination even with max bytes
  int bytes_alloc = OPTION_BUFFER_SIZE + 1;

  char *buf = NEW_C_HEAP_ARRAY_RETURN_NULL(char, bytes_alloc, mtInternal);
  if (NULL == buf) {
    jio_fprintf(defaultStream::error_stream(),
                "Could not allocate read buffer for options file parse\n");
    os::close(fd);
    return JNI_ENOMEM;
  }

  memset(buf, 0, (unsigned)bytes_alloc);

  // Fill buffer
  // Use ::read() instead of os::read because os::read()
  // might do a thread state transition
  // and it is too early for that here

  int bytes_read = ::read(fd, (void *)buf, (unsigned)bytes_alloc);
  os::close(fd);
  if (bytes_read < 0) {
    FREE_C_HEAP_ARRAY(char, buf);
    jio_fprintf(defaultStream::error_stream(),
                "Could not read options file '%s'\n", file_name);
    return JNI_ERR;
  }

  if (bytes_read == 0) {
    // tell caller there is no option data and that is ok
    FREE_C_HEAP_ARRAY(char, buf);
    return JNI_OK;
  }

  // file is larger than OPTION_BUFFER_SIZE
  if (bytes_read > bytes_alloc - 1) {
    FREE_C_HEAP_ARRAY(char, buf);
    jio_fprintf(defaultStream::error_stream(),
                "Options file '%s' is larger than %d bytes.\n",
                file_name, bytes_alloc - 1);
    return JNI_EINVAL;
  }

  int retcode = parse_options_buffer(file_name, buf, bytes_read, vm_args);

  FREE_C_HEAP_ARRAY(char, buf);
  return retcode;
}

jint Arguments::parse_options_buffer(const char* name, char* buffer, const size_t buf_len, ScopedVMInitArgs* vm_args) {
  GrowableArray<JavaVMOption> *options = new (ResourceObj::C_HEAP, mtInternal) GrowableArray<JavaVMOption>(2, true);    // Construct option array

  // some pointers to help with parsing
  char *buffer_end = buffer + buf_len;
  char *opt_hd = buffer;
  char *wrt = buffer;
  char *rd = buffer;

  // parse all options
  while (rd < buffer_end) {
    // skip leading white space from the input string
    while (rd < buffer_end && isspace(*rd)) {
      rd++;
    }

    if (rd >= buffer_end) {
      break;
    }

    // Remember this is where we found the head of the token.
    opt_hd = wrt;

    // Tokens are strings of non white space characters separated
    // by one or more white spaces.
    while (rd < buffer_end && !isspace(*rd)) {
      if (*rd == '\'' || *rd == '"') {      // handle a quoted string
        int quote = *rd;                    // matching quote to look for
        rd++;                               // don't copy open quote
        while (rd < buffer_end && *rd != quote) {
                                            // include everything (even spaces)
                                            // up until the close quote
          *wrt++ = *rd++;                   // copy to option string
        }

        if (rd < buffer_end) {
          rd++;                             // don't copy close quote
        } else {
                                            // did not see closing quote
          jio_fprintf(defaultStream::error_stream(),
                      "Unmatched quote in %s\n", name);
          delete options;
          return JNI_ERR;
        }
      } else {
        *wrt++ = *rd++;                     // copy to option string
      }
    }

    // steal a white space character and set it to NULL
    *wrt++ = '\0';
    // We now have a complete token

    JavaVMOption option;
    option.optionString = opt_hd;

    options->append(option);                // Fill in option

    rd++;  // Advance to next character
  }

  // Fill out JavaVMInitArgs structure.
  jint status = vm_args->set_args(options);

  delete options;
  return status;
}

void Arguments::set_shared_spaces_flags() {
  if (DumpSharedSpaces) {
    if (RequireSharedSpaces) {
      warning("Cannot dump shared archive while using shared archive");
    }
    UseSharedSpaces = false;
#ifdef _LP64
    if (!UseCompressedOops || !UseCompressedClassPointers) {
      vm_exit_during_initialization(
        "Cannot dump shared archive when UseCompressedOops or UseCompressedClassPointers is off.", NULL);
    }
  } else {
    if (!UseCompressedOops || !UseCompressedClassPointers) {
      no_shared_spaces("UseCompressedOops and UseCompressedClassPointers must be on for UseSharedSpaces.");
    }
#endif
  }
}

#if !INCLUDE_ALL_GCS
static void force_serial_gc() {
  FLAG_SET_DEFAULT(UseSerialGC, true);
  UNSUPPORTED_GC_OPTION(UseG1GC);
  UNSUPPORTED_GC_OPTION(UseParallelGC);
  UNSUPPORTED_GC_OPTION(UseParallelOldGC);
  UNSUPPORTED_GC_OPTION(UseConcMarkSweepGC);
  UNSUPPORTED_GC_OPTION(UseParNewGC);
}
#endif // INCLUDE_ALL_GCS

// Sharing support
// Construct the path to the archive
static char* get_shared_archive_path() {
  char *shared_archive_path;
  if (SharedArchiveFile == NULL) {
    char jvm_path[JVM_MAXPATHLEN];
    os::jvm_path(jvm_path, sizeof(jvm_path));
    char *end = strrchr(jvm_path, *os::file_separator());
    if (end != NULL) *end = '\0';
    size_t jvm_path_len = strlen(jvm_path);
    size_t file_sep_len = strlen(os::file_separator());
    const size_t len = jvm_path_len + file_sep_len + 20;
    shared_archive_path = NEW_C_HEAP_ARRAY(char, len, mtInternal);
    if (shared_archive_path != NULL) {
      jio_snprintf(shared_archive_path, len, "%s%sclasses.jsa",
        jvm_path, os::file_separator());
    }
  } else {
    shared_archive_path = os::strdup_check_oom(SharedArchiveFile, mtInternal);
  }
  return shared_archive_path;
}

#ifndef PRODUCT
// Determine whether LogVMOutput should be implicitly turned on.
static bool use_vm_log() {
  if (LogCompilation || !FLAG_IS_DEFAULT(LogFile) ||
      PrintCompilation || PrintInlining || PrintDependencies || PrintNativeNMethods ||
      PrintDebugInfo || PrintRelocations || PrintNMethods || PrintExceptionHandlers ||
      PrintAssembly || TraceDeoptimization || TraceDependencies ||
      (VerifyDependencies && FLAG_IS_CMDLINE(VerifyDependencies))) {
    return true;
  }

#ifdef COMPILER1
  if (PrintC1Statistics) {
    return true;
  }
#endif // COMPILER1

#ifdef COMPILER2
  if (PrintOptoAssembly || PrintOptoStatistics) {
    return true;
  }
#endif // COMPILER2

  return false;
}

#endif // PRODUCT

jint Arguments::insert_vm_options_file(const JavaVMInitArgs* args,
                                       char** flags_file,
                                       char** vm_options_file,
                                       const int vm_options_file_pos,
                                       ScopedVMInitArgs *vm_options_file_args,
                                       ScopedVMInitArgs* args_out) {
  jint code = parse_vm_options_file(*vm_options_file, vm_options_file_args);
  if (code != JNI_OK) {
    return code;
  }

  // Now set global settings from the vm_option file, giving an error if
  // it has VMOptionsFile in it
  code = match_special_option_and_act(vm_options_file_args->get(), flags_file,
                                      NULL, NULL, NULL);
  if (code != JNI_OK) {
    return code;
  }

  if (vm_options_file_args->get()->nOptions < 1) {
    return 0;
  }

  return args_out->insert(args, vm_options_file_args->get(),
                          vm_options_file_pos);
}

jint Arguments::match_special_option_and_act(const JavaVMInitArgs* args,
                                             char ** flags_file,
                                             char ** vm_options_file,
                                             ScopedVMInitArgs* vm_options_file_args,
                                             ScopedVMInitArgs* args_out) {
  // Remaining part of option string
  const char* tail;
  int   vm_options_file_pos = -1;

  for (int index = 0; index < args->nOptions; index++) {
    const JavaVMOption* option = args->options + index;
    if (ArgumentsExt::process_options(option)) {
      continue;
    }
    if (match_option(option, "-XX:Flags=", &tail)) {
      *flags_file = (char *) tail;
      if (*flags_file == NULL) {
        jio_fprintf(defaultStream::error_stream(),
                    "Cannot copy flags_file name.\n");
        return JNI_ENOMEM;
      }
      continue;
    }
    if (match_option(option, "-XX:VMOptionsFile=", &tail)) {
      if (vm_options_file != NULL) {
        // The caller accepts -XX:VMOptionsFile
        if (*vm_options_file != NULL) {
          jio_fprintf(defaultStream::error_stream(),
                      "Only one VM Options file is supported "
                      "on the command line\n");
          return JNI_EINVAL;
        }

        *vm_options_file = (char *) tail;
        vm_options_file_pos = index;  // save position of -XX:VMOptionsFile
        if (*vm_options_file == NULL) {
          jio_fprintf(defaultStream::error_stream(),
                      "Cannot copy vm_options_file name.\n");
          return JNI_ENOMEM;
        }
      } else {
        jio_fprintf(defaultStream::error_stream(),
                    "VM options file is only supported on the command line\n");
        return JNI_EINVAL;
      }
      continue;
    }
    if (match_option(option, "-XX:+PrintVMOptions")) {
      PrintVMOptions = true;
      continue;
    }
    if (match_option(option, "-XX:-PrintVMOptions")) {
      PrintVMOptions = false;
      continue;
    }
    if (match_option(option, "-XX:+IgnoreUnrecognizedVMOptions")) {
      IgnoreUnrecognizedVMOptions = true;
      continue;
    }
    if (match_option(option, "-XX:-IgnoreUnrecognizedVMOptions")) {
      IgnoreUnrecognizedVMOptions = false;
      continue;
    }
    if (match_option(option, "-XX:+PrintFlagsInitial")) {
      CommandLineFlags::printFlags(tty, false);
      vm_exit(0);
    }
    if (match_option(option, "-XX:NativeMemoryTracking", &tail)) {
#if INCLUDE_NMT
      // The launcher did not setup nmt environment variable properly.
      if (!MemTracker::check_launcher_nmt_support(tail)) {
        warning("Native Memory Tracking did not setup properly, using wrong launcher?");
      }

      // Verify if nmt option is valid.
      if (MemTracker::verify_nmt_option()) {
        // Late initialization, still in single-threaded mode.
        if (MemTracker::tracking_level() >= NMT_summary) {
          MemTracker::init();
        }
      } else {
        vm_exit_during_initialization("Syntax error, expecting -XX:NativeMemoryTracking=[off|summary|detail]", NULL);
      }
      continue;
#else
      jio_fprintf(defaultStream::error_stream(),
        "Native Memory Tracking is not supported in this VM\n");
      return JNI_ERR;
#endif
    }

#ifndef PRODUCT
    if (match_option(option, "-XX:+PrintFlagsWithComments")) {
      CommandLineFlags::printFlags(tty, true);
      vm_exit(0);
    }
#endif
  }

  // If there's a VMOptionsFile, parse that (also can set flags_file)
  if ((vm_options_file != NULL) && (*vm_options_file != NULL)) {
    return insert_vm_options_file(args, flags_file, vm_options_file,
                                  vm_options_file_pos, vm_options_file_args, args_out);
  }
  return JNI_OK;
}

static void print_options(const JavaVMInitArgs *args) {
  const char* tail;
  for (int index = 0; index < args->nOptions; index++) {
    const JavaVMOption *option = args->options + index;
    if (match_option(option, "-XX:", &tail)) {
      logOption(tail);
    }
  }
}

// Parse entry point called from JNI_CreateJavaVM

jint Arguments::parse(const JavaVMInitArgs* args) {
  assert(verify_special_jvm_flags(), "deprecated and obsolete flag table inconsistent");

  // Initialize ranges and constraints
  CommandLineFlagRangeList::init();
  CommandLineFlagConstraintList::init();

  // If flag "-XX:Flags=flags-file" is used it will be the first option to be processed.
  const char* hotspotrc = ".hotspotrc";
  char* flags_file = NULL;
  char* vm_options_file = NULL;
  bool settings_file_specified = false;
  bool needs_hotspotrc_warning = false;
  ScopedVMInitArgs java_tool_options_args;
  ScopedVMInitArgs java_options_args;
  ScopedVMInitArgs modified_cmd_line_args;
  // Pass in vm_options_file_args to keep memory for flags_file from being
  // deallocated if found in the vm options file.
  ScopedVMInitArgs vm_options_file_args;

  jint code =
      parse_java_tool_options_environment_variable(&java_tool_options_args);
  if (code != JNI_OK) {
    return code;
  }

  code = parse_java_options_environment_variable(&java_options_args);
  if (code != JNI_OK) {
    return code;
  }

  code = match_special_option_and_act(java_tool_options_args.get(),
                                      &flags_file, NULL, NULL, NULL);
  if (code != JNI_OK) {
    return code;
  }

  code = match_special_option_and_act(args, &flags_file, &vm_options_file,
                                      &vm_options_file_args,
                                      &modified_cmd_line_args);
  if (code != JNI_OK) {
    return code;
  }


  // The command line arguments have been modified to include VMOptionsFile arguments.
  if (modified_cmd_line_args.is_set()) {
    args = modified_cmd_line_args.get();
  }

  code = match_special_option_and_act(java_options_args.get(), &flags_file,
                                      NULL, NULL, NULL);
  if (code != JNI_OK) {
    return code;
  }

  settings_file_specified = (flags_file != NULL);

  if (IgnoreUnrecognizedVMOptions) {
    // uncast const to modify the flag args->ignoreUnrecognized
    *(jboolean*)(&args->ignoreUnrecognized) = true;
    java_tool_options_args.get()->ignoreUnrecognized = true;
    java_options_args.get()->ignoreUnrecognized = true;
  }

  // Parse specified settings file
  if (settings_file_specified) {
    if (!process_settings_file(flags_file, true, args->ignoreUnrecognized)) {
      return JNI_EINVAL;
    }
  } else {
#ifdef ASSERT
    // Parse default .hotspotrc settings file
    if (!process_settings_file(".hotspotrc", false, args->ignoreUnrecognized)) {
      return JNI_EINVAL;
    }
#else
    struct stat buf;
    if (os::stat(hotspotrc, &buf) == 0) {
      needs_hotspotrc_warning = true;
    }
#endif
  }

  if (PrintVMOptions) {
    print_options(java_tool_options_args.get());
    print_options(args);
    print_options(java_options_args.get());
  }

  // Parse JavaVMInitArgs structure passed in, as well as JAVA_TOOL_OPTIONS and _JAVA_OPTIONS
  jint result = parse_vm_init_args(java_tool_options_args.get(),
                                   java_options_args.get(),
                                   args);   // command line arguments

  if (result != JNI_OK) {
    return result;
  }

  // Call get_shared_archive_path() here, after possible SharedArchiveFile option got parsed.
  SharedArchivePath = get_shared_archive_path();
  if (SharedArchivePath == NULL) {
    return JNI_ENOMEM;
  }

  // Set up VerifySharedSpaces
  if (FLAG_IS_DEFAULT(VerifySharedSpaces) && SharedArchiveFile != NULL) {
    VerifySharedSpaces = true;
  }

  // Delay warning until here so that we've had a chance to process
  // the -XX:-PrintWarnings flag
  if (needs_hotspotrc_warning) {
    warning("%s file is present but has been ignored.  "
            "Run with -XX:Flags=%s to load the file.",
            hotspotrc, hotspotrc);
  }

#if defined(_ALLBSD_SOURCE) || defined(AIX)  // UseLargePages is not yet supported on BSD and AIX.
  UNSUPPORTED_OPTION(UseLargePages, "-XX:+UseLargePages");
#endif

  ArgumentsExt::report_unsupported_options();

#ifndef PRODUCT
  if (TraceBytecodesAt != 0) {
    TraceBytecodes = true;
  }
  if (CountCompiledCalls) {
    if (UseCounterDecay) {
      warning("UseCounterDecay disabled because CountCalls is set");
      UseCounterDecay = false;
    }
  }
#endif // PRODUCT

  if (ScavengeRootsInCode == 0) {
    if (!FLAG_IS_DEFAULT(ScavengeRootsInCode)) {
      warning("Forcing ScavengeRootsInCode non-zero");
    }
    ScavengeRootsInCode = 1;
  }

  if (PrintGCDetails) {
    // Turn on -verbose:gc options as well
    PrintGC = true;
  }

  // Set object alignment values.
  set_object_alignment();

#if !INCLUDE_ALL_GCS
  force_serial_gc();
#endif // INCLUDE_ALL_GCS
#if !INCLUDE_CDS
  if (DumpSharedSpaces || RequireSharedSpaces) {
    jio_fprintf(defaultStream::error_stream(),
      "Shared spaces are not supported in this VM\n");
    return JNI_ERR;
  }
  if ((UseSharedSpaces && FLAG_IS_CMDLINE(UseSharedSpaces)) || PrintSharedSpaces) {
    warning("Shared spaces are not supported in this VM");
    FLAG_SET_DEFAULT(UseSharedSpaces, false);
    FLAG_SET_DEFAULT(PrintSharedSpaces, false);
  }
  no_shared_spaces("CDS Disabled");
#endif // INCLUDE_CDS

  return JNI_OK;
}

jint Arguments::apply_ergo() {

  // Set flags based on ergonomics.
  set_ergonomics_flags();

  set_shared_spaces_flags();

  // Check the GC selections again.
  if (!check_gc_consistency()) {
    return JNI_EINVAL;
  }

  if (TieredCompilation) {
    set_tiered_flags();
  } else {
    int max_compilation_policy_choice = 1;
#ifdef COMPILER2
    max_compilation_policy_choice = 2;
#endif
    // Check if the policy is valid.
    if (CompilationPolicyChoice >= max_compilation_policy_choice) {
      vm_exit_during_initialization(
        "Incompatible compilation policy selected", NULL);
    }
    // Scale CompileThreshold
    // CompileThresholdScaling == 0.0 is equivalent to -Xint and leaves CompileThreshold unchanged.
    if (!FLAG_IS_DEFAULT(CompileThresholdScaling) && CompileThresholdScaling > 0.0) {
      FLAG_SET_ERGO(intx, CompileThreshold, scaled_compile_threshold(CompileThreshold));
    }
  }

#ifdef COMPILER2
#ifndef PRODUCT
  if (PrintIdealGraphLevel > 0) {
    FLAG_SET_ERGO(bool, PrintIdealGraph, true);
  }
#endif
#endif

  // Set heap size based on available physical memory
  set_heap_size();

  ArgumentsExt::set_gc_specific_flags();

  // Initialize Metaspace flags and alignments
  Metaspace::ergo_initialize();

  // Set bytecode rewriting flags
  set_bytecode_flags();

  // Set flags if Aggressive optimization flags (-XX:+AggressiveOpts) enabled
  jint code = set_aggressive_opts_flags();
  if (code != JNI_OK) {
    return code;
  }

  // Turn off biased locking for locking debug mode flags,
  // which are subtly different from each other but neither works with
  // biased locking
  if (UseHeavyMonitors
#ifdef COMPILER1
      || !UseFastLocking
#endif // COMPILER1
#if INCLUDE_JVMCI
      || !JVMCIUseFastLocking
#endif
    ) {
    if (!FLAG_IS_DEFAULT(UseBiasedLocking) && UseBiasedLocking) {
      // flag set to true on command line; warn the user that they
      // can't enable biased locking here
      warning("Biased Locking is not supported with locking debug flags"
              "; ignoring UseBiasedLocking flag." );
    }
    UseBiasedLocking = false;
  }

#ifdef ZERO
  // Clear flags not supported on zero.
  FLAG_SET_DEFAULT(ProfileInterpreter, false);
  FLAG_SET_DEFAULT(UseBiasedLocking, false);
  LP64_ONLY(FLAG_SET_DEFAULT(UseCompressedOops, false));
  LP64_ONLY(FLAG_SET_DEFAULT(UseCompressedClassPointers, false));
#endif // CC_INTERP

#ifdef COMPILER2
  if (!EliminateLocks) {
    EliminateNestedLocks = false;
  }
  if (!Inline) {
    IncrementalInline = false;
  }
#ifndef PRODUCT
  if (!IncrementalInline) {
    AlwaysIncrementalInline = false;
  }
#endif
  if (!UseTypeSpeculation && FLAG_IS_DEFAULT(TypeProfileLevel)) {
    // nothing to use the profiling, turn if off
    FLAG_SET_DEFAULT(TypeProfileLevel, 0);
  }
#endif

  if (PrintAssembly && FLAG_IS_DEFAULT(DebugNonSafepoints)) {
    warning("PrintAssembly is enabled; turning on DebugNonSafepoints to gain additional output");
    DebugNonSafepoints = true;
  }

  if (FLAG_IS_CMDLINE(CompressedClassSpaceSize) && !UseCompressedClassPointers) {
    warning("Setting CompressedClassSpaceSize has no effect when compressed class pointers are not used");
  }

#ifndef PRODUCT
  if (!LogVMOutput && FLAG_IS_DEFAULT(LogVMOutput)) {
    if (use_vm_log()) {
      LogVMOutput = true;
    }
  }
#endif // PRODUCT

  if (PrintCommandLineFlags) {
    CommandLineFlags::printSetFlags(tty);
  }

  // Apply CPU specific policy for the BiasedLocking
  if (UseBiasedLocking) {
    if (!VM_Version::use_biased_locking() &&
        !(FLAG_IS_CMDLINE(UseBiasedLocking))) {
      UseBiasedLocking = false;
    }
  }
#ifdef COMPILER2
  if (!UseBiasedLocking || EmitSync != 0) {
    UseOptoBiasInlining = false;
  }
#endif

  return JNI_OK;
}

jint Arguments::adjust_after_os() {
  if (UseNUMA) {
    if (UseParallelGC || UseParallelOldGC) {
      if (FLAG_IS_DEFAULT(MinHeapDeltaBytes)) {
         FLAG_SET_DEFAULT(MinHeapDeltaBytes, 64*M);
      }
    }
    // UseNUMAInterleaving is set to ON for all collectors and
    // platforms when UseNUMA is set to ON. NUMA-aware collectors
    // such as the parallel collector for Linux and Solaris will
    // interleave old gen and survivor spaces on top of NUMA
    // allocation policy for the eden space.
    // Non NUMA-aware collectors such as CMS, G1 and Serial-GC on
    // all platforms and ParallelGC on Windows will interleave all
    // of the heap spaces across NUMA nodes.
    if (FLAG_IS_DEFAULT(UseNUMAInterleaving)) {
      FLAG_SET_ERGO(bool, UseNUMAInterleaving, true);
    }
  }
  return JNI_OK;
}

int Arguments::PropertyList_count(SystemProperty* pl) {
  int count = 0;
  while(pl != NULL) {
    count++;
    pl = pl->next();
  }
  return count;
}

const char* Arguments::PropertyList_get_value(SystemProperty *pl, const char* key) {
  assert(key != NULL, "just checking");
  SystemProperty* prop;
  for (prop = pl; prop != NULL; prop = prop->next()) {
    if (strcmp(key, prop->key()) == 0) return prop->value();
  }
  return NULL;
}

const char* Arguments::PropertyList_get_key_at(SystemProperty *pl, int index) {
  int count = 0;
  const char* ret_val = NULL;

  while(pl != NULL) {
    if(count >= index) {
      ret_val = pl->key();
      break;
    }
    count++;
    pl = pl->next();
  }

  return ret_val;
}

char* Arguments::PropertyList_get_value_at(SystemProperty* pl, int index) {
  int count = 0;
  char* ret_val = NULL;

  while(pl != NULL) {
    if(count >= index) {
      ret_val = pl->value();
      break;
    }
    count++;
    pl = pl->next();
  }

  return ret_val;
}

void Arguments::PropertyList_add(SystemProperty** plist, SystemProperty *new_p) {
  SystemProperty* p = *plist;
  if (p == NULL) {
    *plist = new_p;
  } else {
    while (p->next() != NULL) {
      p = p->next();
    }
    p->set_next(new_p);
  }
}

void Arguments::PropertyList_add(SystemProperty** plist, const char* k, const char* v) {
  if (plist == NULL)
    return;

  SystemProperty* new_p = new SystemProperty(k, v, true);
  PropertyList_add(plist, new_p);
}

void Arguments::PropertyList_add(SystemProperty *element) {
  PropertyList_add(&_system_properties, element);
}

// This add maintains unique property key in the list.
void Arguments::PropertyList_unique_add(SystemProperty** plist, const char* k, const char* v, jboolean append) {
  if (plist == NULL)
    return;

  // If property key exist then update with new value.
  SystemProperty* prop;
  for (prop = *plist; prop != NULL; prop = prop->next()) {
    if (strcmp(k, prop->key()) == 0) {
      if (append) {
        prop->append_value(v);
      } else {
        prop->set_value(v);
      }
      return;
    }
  }

  PropertyList_add(plist, k, v);
}

// Copies src into buf, replacing "%%" with "%" and "%p" with pid
// Returns true if all of the source pointed by src has been copied over to
// the destination buffer pointed by buf. Otherwise, returns false.
// Notes:
// 1. If the length (buflen) of the destination buffer excluding the
// NULL terminator character is not long enough for holding the expanded
// pid characters, it also returns false instead of returning the partially
// expanded one.
// 2. The passed in "buflen" should be large enough to hold the null terminator.
bool Arguments::copy_expand_pid(const char* src, size_t srclen,
                                char* buf, size_t buflen) {
  const char* p = src;
  char* b = buf;
  const char* src_end = &src[srclen];
  char* buf_end = &buf[buflen - 1];

  while (p < src_end && b < buf_end) {
    if (*p == '%') {
      switch (*(++p)) {
      case '%':         // "%%" ==> "%"
        *b++ = *p++;
        break;
      case 'p':  {       //  "%p" ==> current process id
        // buf_end points to the character before the last character so
        // that we could write '\0' to the end of the buffer.
        size_t buf_sz = buf_end - b + 1;
        int ret = jio_snprintf(b, buf_sz, "%d", os::current_process_id());

        // if jio_snprintf fails or the buffer is not long enough to hold
        // the expanded pid, returns false.
        if (ret < 0 || ret >= (int)buf_sz) {
          return false;
        } else {
          b += ret;
          assert(*b == '\0', "fail in copy_expand_pid");
          if (p == src_end && b == buf_end + 1) {
            // reach the end of the buffer.
            return true;
          }
        }
        p++;
        break;
      }
      default :
        *b++ = '%';
      }
    } else {
      *b++ = *p++;
    }
  }
  *b = '\0';
  return (p == src_end); // return false if not all of the source was copied
}
