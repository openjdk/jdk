/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_arguments.cpp.incl"

#define DEFAULT_VENDOR_URL_BUG "http://java.sun.com/webapps/bugreport/crash.jsp"
#define DEFAULT_JAVA_LAUNCHER  "generic"

char**  Arguments::_jvm_flags_array             = NULL;
int     Arguments::_num_jvm_flags               = 0;
char**  Arguments::_jvm_args_array              = NULL;
int     Arguments::_num_jvm_args                = 0;
char*  Arguments::_java_command                 = NULL;
SystemProperty* Arguments::_system_properties   = NULL;
const char*  Arguments::_gc_log_filename        = NULL;
bool   Arguments::_has_profile                  = false;
bool   Arguments::_has_alloc_profile            = false;
uintx  Arguments::_min_heap_size                = 0;
Arguments::Mode Arguments::_mode                = _mixed;
bool   Arguments::_java_compiler                = false;
bool   Arguments::_xdebug_mode                  = false;
const char*  Arguments::_java_vendor_url_bug    = DEFAULT_VENDOR_URL_BUG;
const char*  Arguments::_sun_java_launcher      = DEFAULT_JAVA_LAUNCHER;
int    Arguments::_sun_java_launcher_pid        = -1;

// These parameters are reset in method parse_vm_init_args(JavaVMInitArgs*)
bool   Arguments::_AlwaysCompileLoopMethods     = AlwaysCompileLoopMethods;
bool   Arguments::_UseOnStackReplacement        = UseOnStackReplacement;
bool   Arguments::_BackgroundCompilation        = BackgroundCompilation;
bool   Arguments::_ClipInlining                 = ClipInlining;
intx   Arguments::_Tier2CompileThreshold        = Tier2CompileThreshold;

char*  Arguments::SharedArchivePath             = NULL;

AgentLibraryList Arguments::_libraryList;
AgentLibraryList Arguments::_agentList;

abort_hook_t     Arguments::_abort_hook         = NULL;
exit_hook_t      Arguments::_exit_hook          = NULL;
vfprintf_hook_t  Arguments::_vfprintf_hook      = NULL;


SystemProperty *Arguments::_java_ext_dirs = NULL;
SystemProperty *Arguments::_java_endorsed_dirs = NULL;
SystemProperty *Arguments::_sun_boot_library_path = NULL;
SystemProperty *Arguments::_java_library_path = NULL;
SystemProperty *Arguments::_java_home = NULL;
SystemProperty *Arguments::_java_class_path = NULL;
SystemProperty *Arguments::_sun_boot_class_path = NULL;

char* Arguments::_meta_index_path = NULL;
char* Arguments::_meta_index_dir = NULL;

static bool force_client_mode = false;

// Check if head of 'option' matches 'name', and sets 'tail' remaining part of option string

static bool match_option(const JavaVMOption *option, const char* name,
                         const char** tail) {
  int len = (int)strlen(name);
  if (strncmp(option->optionString, name, len) == 0) {
    *tail = option->optionString + len;
    return true;
  } else {
    return false;
  }
}

static void logOption(const char* opt) {
  if (PrintVMOptions) {
    jio_fprintf(defaultStream::output_stream(), "VM option '%s'\n", opt);
  }
}

// Process java launcher properties.
void Arguments::process_sun_java_launcher_properties(JavaVMInitArgs* args) {
  // See if sun.java.launcher or sun.java.launcher.pid is defined.
  // Must do this before setting up other system properties,
  // as some of them may depend on launcher type.
  for (int index = 0; index < args->nOptions; index++) {
    const JavaVMOption* option = args->options + index;
    const char* tail;

    if (match_option(option, "-Dsun.java.launcher=", &tail)) {
      process_java_launcher_argument(tail, option->extraInfo);
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

  PropertyList_add(&_system_properties, new SystemProperty("java.vm.specification.version", "1.0", false));
  PropertyList_add(&_system_properties, new SystemProperty("java.vm.specification.name",
                                                                 "Java Virtual Machine Specification",  false));
  PropertyList_add(&_system_properties, new SystemProperty("java.vm.specification.vendor",
                                                                 "Sun Microsystems Inc.",  false));
  PropertyList_add(&_system_properties, new SystemProperty("java.vm.version", VM_Version::vm_release(),  false));
  PropertyList_add(&_system_properties, new SystemProperty("java.vm.name", VM_Version::vm_name(),  false));
  PropertyList_add(&_system_properties, new SystemProperty("java.vm.vendor", VM_Version::vm_vendor(),  false));
  PropertyList_add(&_system_properties, new SystemProperty("java.vm.info", VM_Version::vm_info_string(),  true));

  // following are JVMTI agent writeable properties.
  // Properties values are set to NULL and they are
  // os specific they are initialized in os::init_system_properties_values().
  _java_ext_dirs = new SystemProperty("java.ext.dirs", NULL,  true);
  _java_endorsed_dirs = new SystemProperty("java.endorsed.dirs", NULL,  true);
  _sun_boot_library_path = new SystemProperty("sun.boot.library.path", NULL,  true);
  _java_library_path = new SystemProperty("java.library.path", NULL,  true);
  _java_home =  new SystemProperty("java.home", NULL,  true);
  _sun_boot_class_path = new SystemProperty("sun.boot.class.path", NULL,  true);

  _java_class_path = new SystemProperty("java.class.path", "",  true);

  // Add to System Property list.
  PropertyList_add(&_system_properties, _java_ext_dirs);
  PropertyList_add(&_system_properties, _java_endorsed_dirs);
  PropertyList_add(&_system_properties, _sun_boot_library_path);
  PropertyList_add(&_system_properties, _java_library_path);
  PropertyList_add(&_system_properties, _java_home);
  PropertyList_add(&_system_properties, _java_class_path);
  PropertyList_add(&_system_properties, _sun_boot_class_path);

  // Set OS specific system properties values
  os::init_system_properties_values();
}

/**
 * Provide a slightly more user-friendly way of eliminating -XX flags.
 * When a flag is eliminated, it can be added to this list in order to
 * continue accepting this flag on the command-line, while issuing a warning
 * and ignoring the value.  Once the JDK version reaches the 'accept_until'
 * limit, we flatly refuse to admit the existence of the flag.  This allows
 * a flag to die correctly over JDK releases using HSX.
 */
typedef struct {
  const char* name;
  JDK_Version obsoleted_in; // when the flag went away
  JDK_Version accept_until; // which version to start denying the existence
} ObsoleteFlag;

static ObsoleteFlag obsolete_jvm_flags[] = {
  { "UseTrainGC",                    JDK_Version::jdk(5), JDK_Version::jdk(7) },
  { "UseSpecialLargeObjectHandling", JDK_Version::jdk(5), JDK_Version::jdk(7) },
  { "UseOversizedCarHandling",       JDK_Version::jdk(5), JDK_Version::jdk(7) },
  { "TraceCarAllocation",            JDK_Version::jdk(5), JDK_Version::jdk(7) },
  { "PrintTrainGCProcessingStats",   JDK_Version::jdk(5), JDK_Version::jdk(7) },
  { "LogOfCarSpaceSize",             JDK_Version::jdk(5), JDK_Version::jdk(7) },
  { "OversizedCarThreshold",         JDK_Version::jdk(5), JDK_Version::jdk(7) },
  { "MinTickInterval",               JDK_Version::jdk(5), JDK_Version::jdk(7) },
  { "DefaultTickInterval",           JDK_Version::jdk(5), JDK_Version::jdk(7) },
  { "MaxTickInterval",               JDK_Version::jdk(5), JDK_Version::jdk(7) },
  { "DelayTickAdjustment",           JDK_Version::jdk(5), JDK_Version::jdk(7) },
  { "ProcessingToTenuringRatio",     JDK_Version::jdk(5), JDK_Version::jdk(7) },
  { "MinTrainLength",                JDK_Version::jdk(5), JDK_Version::jdk(7) },
  { "AppendRatio",         JDK_Version::jdk_update(6,10), JDK_Version::jdk(7) },
  { "DefaultMaxRAM",       JDK_Version::jdk_update(6,18), JDK_Version::jdk(7) },
  { "DefaultInitialRAMFraction",
                           JDK_Version::jdk_update(6,18), JDK_Version::jdk(7) },
  { NULL, JDK_Version(0), JDK_Version(0) }
};

// Returns true if the flag is obsolete and fits into the range specified
// for being ignored.  In the case that the flag is ignored, the 'version'
// value is filled in with the version number when the flag became
// obsolete so that that value can be displayed to the user.
bool Arguments::is_newly_obsolete(const char *s, JDK_Version* version) {
  int i = 0;
  assert(version != NULL, "Must provide a version buffer");
  while (obsolete_jvm_flags[i].name != NULL) {
    const ObsoleteFlag& flag_status = obsolete_jvm_flags[i];
    // <flag>=xxx form
    // [-|+]<flag> form
    if ((strncmp(flag_status.name, s, strlen(flag_status.name)) == 0) ||
        ((s[0] == '+' || s[0] == '-') &&
        (strncmp(flag_status.name, &s[1], strlen(flag_status.name)) == 0))) {
      if (JDK_Version::current().compare(flag_status.accept_until) == -1) {
          *version = flag_status.obsoleted_in;
          return true;
      }
    }
    i++;
  }
  return false;
}

// Constructs the system class path (aka boot class path) from the following
// components, in order:
//
//     prefix           // from -Xbootclasspath/p:...
//     endorsed         // the expansion of -Djava.endorsed.dirs=...
//     base             // from os::get_system_properties() or -Xbootclasspath=
//     suffix           // from -Xbootclasspath/a:...
//
// java.endorsed.dirs is a list of directories; any jar or zip files in the
// directories are added to the sysclasspath just before the base.
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

  // Expand the jar/zip files in each directory listed by the java.endorsed.dirs
  // property.  Must be called after all command-line arguments have been
  // processed (in particular, -Djava.endorsed.dirs=...) and before calling
  // combined_path().
  void expand_endorsed();

  inline const char* get_base()     const { return _items[_scp_base]; }
  inline const char* get_prefix()   const { return _items[_scp_prefix]; }
  inline const char* get_suffix()   const { return _items[_scp_suffix]; }
  inline const char* get_endorsed() const { return _items[_scp_endorsed]; }

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
    _scp_endorsed,      // the expansion of -Djava.endorsed.dirs=...
    _scp_base,          // the default sysclasspath
    _scp_suffix,        // from -Xbootclasspath/a:...
    _scp_nitems         // the number of items, must be last.
  };

  const char* _items[_scp_nitems];
  DEBUG_ONLY(bool _expansion_done;)
};

SysClassPath::SysClassPath(const char* base) {
  memset(_items, 0, sizeof(_items));
  _items[_scp_base] = base;
  DEBUG_ONLY(_expansion_done = false;)
}

SysClassPath::~SysClassPath() {
  // Free everything except the base.
  for (int i = 0; i < _scp_nitems; ++i) {
    if (i != _scp_base) reset_item_at(i);
  }
  DEBUG_ONLY(_expansion_done = false;)
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

void SysClassPath::expand_endorsed() {
  assert(_items[_scp_endorsed] == NULL, "can only be called once.");

  const char* path = Arguments::get_property("java.endorsed.dirs");
  if (path == NULL) {
    path = Arguments::get_endorsed_dir();
    assert(path != NULL, "no default for java.endorsed.dirs");
  }

  char* expanded_path = NULL;
  const char separator = *os::path_separator();
  const char* const end = path + strlen(path);
  while (path < end) {
    const char* tmp_end = strchr(path, separator);
    if (tmp_end == NULL) {
      expanded_path = add_jars_to_path(expanded_path, path);
      path = end;
    } else {
      char* dirpath = NEW_C_HEAP_ARRAY(char, tmp_end - path + 1);
      memcpy(dirpath, path, tmp_end - path);
      dirpath[tmp_end - path] = '\0';
      expanded_path = add_jars_to_path(expanded_path, dirpath);
      FREE_C_HEAP_ARRAY(char, dirpath);
      path = tmp_end + 1;
    }
  }
  _items[_scp_endorsed] = expanded_path;
  DEBUG_ONLY(_expansion_done = true;)
}

// Combine the bootclasspath elements, some of which may be null, into a single
// c-heap-allocated string.
char* SysClassPath::combined_path() {
  assert(_items[_scp_base] != NULL, "empty default sysclasspath");
  assert(_expansion_done, "must call expand_endorsed() first.");

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
  char* cp = NEW_C_HEAP_ARRAY(char, total_len);
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
    cp = NEW_C_HEAP_ARRAY(char, len);
    memcpy(cp, str, len);                       // copy the trailing null
  } else {
    const char separator = *os::path_separator();
    size_t old_len = strlen(path);
    size_t str_len = strlen(str);
    size_t len = old_len + str_len + 2;

    if (prepend) {
      cp = NEW_C_HEAP_ARRAY(char, len);
      char* cp_tmp = cp;
      memcpy(cp_tmp, str, str_len);
      cp_tmp += str_len;
      *cp_tmp = separator;
      memcpy(++cp_tmp, path, old_len + 1);      // copy the trailing null
      FREE_C_HEAP_ARRAY(char, path);
    } else {
      cp = REALLOC_C_HEAP_ARRAY(char, path, len);
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
  char *dbuf = NEW_C_HEAP_ARRAY(char, os::readdir_buf_size(directory));
  while ((entry = os::readdir(dir, (dirent *) dbuf)) != NULL) {
    const char* name = entry->d_name;
    const char* ext = name + strlen(name) - 4;
    bool isJarOrZip = ext > name &&
      (os::file_name_strcmp(ext, ".jar") == 0 ||
       os::file_name_strcmp(ext, ".zip") == 0);
    if (isJarOrZip) {
      char* jarpath = NEW_C_HEAP_ARRAY(char, directory_len + 2 + strlen(name));
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
  int args_read = sscanf(s, os::julong_format_specifier(), &n);
  if (args_read != 1) {
    return false;
  }
  while (*s != '\0' && isdigit(*s)) {
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

static bool set_bool_flag(char* name, bool value, FlagValueOrigin origin) {
  return CommandLineFlags::boolAtPut(name, &value, origin);
}

static bool set_fp_numeric_flag(char* name, char* value, FlagValueOrigin origin) {
  double v;
  if (sscanf(value, "%lf", &v) != 1) {
    return false;
  }

  if (CommandLineFlags::doubleAtPut(name, &v, origin)) {
    return true;
  }
  return false;
}

static bool set_numeric_flag(char* name, char* value, FlagValueOrigin origin) {
  julong v;
  intx intx_v;
  bool is_neg = false;
  // Check the sign first since atomull() parses only unsigned values.
  if (*value == '-') {
    if (!CommandLineFlags::intxAt(name, &intx_v)) {
      return false;
    }
    value++;
    is_neg = true;
  }
  if (!atomull(value, &v)) {
    return false;
  }
  intx_v = (intx) v;
  if (is_neg) {
    intx_v = -intx_v;
  }
  if (CommandLineFlags::intxAtPut(name, &intx_v, origin)) {
    return true;
  }
  uintx uintx_v = (uintx) v;
  if (!is_neg && CommandLineFlags::uintxAtPut(name, &uintx_v, origin)) {
    return true;
  }
  uint64_t uint64_t_v = (uint64_t) v;
  if (!is_neg && CommandLineFlags::uint64_tAtPut(name, &uint64_t_v, origin)) {
    return true;
  }
  return false;
}

static bool set_string_flag(char* name, const char* value, FlagValueOrigin origin) {
  if (!CommandLineFlags::ccstrAtPut(name, &value, origin))  return false;
  // Contract:  CommandLineFlags always returns a pointer that needs freeing.
  FREE_C_HEAP_ARRAY(char, value);
  return true;
}

static bool append_to_string_flag(char* name, const char* new_value, FlagValueOrigin origin) {
  const char* old_value = "";
  if (!CommandLineFlags::ccstrAt(name, &old_value))  return false;
  size_t old_len = old_value != NULL ? strlen(old_value) : 0;
  size_t new_len = strlen(new_value);
  const char* value;
  char* free_this_too = NULL;
  if (old_len == 0) {
    value = new_value;
  } else if (new_len == 0) {
    value = old_value;
  } else {
    char* buf = NEW_C_HEAP_ARRAY(char, old_len + 1 + new_len + 1);
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

bool Arguments::parse_argument(const char* arg, FlagValueOrigin origin) {

  // range of acceptable characters spelled out for portability reasons
#define NAME_RANGE  "[abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_]"
#define BUFLEN 255
  char name[BUFLEN+1];
  char dummy;

  if (sscanf(arg, "-%" XSTR(BUFLEN) NAME_RANGE "%c", name, &dummy) == 1) {
    return set_bool_flag(name, false, origin);
  }
  if (sscanf(arg, "+%" XSTR(BUFLEN) NAME_RANGE "%c", name, &dummy) == 1) {
    return set_bool_flag(name, true, origin);
  }

  char punct;
  if (sscanf(arg, "%" XSTR(BUFLEN) NAME_RANGE "%c", name, &punct) == 2 && punct == '=') {
    const char* value = strchr(arg, '=') + 1;
    Flag* flag = Flag::find_flag(name, strlen(name));
    if (flag != NULL && flag->is_ccstr()) {
      if (flag->ccstr_accumulates()) {
        return append_to_string_flag(name, value, origin);
      } else {
        if (value[0] == '\0') {
          value = NULL;
        }
        return set_string_flag(name, value, origin);
      }
    }
  }

  if (sscanf(arg, "%" XSTR(BUFLEN) NAME_RANGE ":%c", name, &punct) == 2 && punct == '=') {
    const char* value = strchr(arg, '=') + 1;
    // -XX:Foo:=xxx will reset the string flag to the given value.
    if (value[0] == '\0') {
      value = NULL;
    }
    return set_string_flag(name, value, origin);
  }

#define SIGNED_FP_NUMBER_RANGE "[-0123456789.]"
#define SIGNED_NUMBER_RANGE    "[-0123456789]"
#define        NUMBER_RANGE    "[0123456789]"
  char value[BUFLEN + 1];
  char value2[BUFLEN + 1];
  if (sscanf(arg, "%" XSTR(BUFLEN) NAME_RANGE "=" "%" XSTR(BUFLEN) SIGNED_NUMBER_RANGE "." "%" XSTR(BUFLEN) NUMBER_RANGE "%c", name, value, value2, &dummy) == 3) {
    // Looks like a floating-point number -- try again with more lenient format string
    if (sscanf(arg, "%" XSTR(BUFLEN) NAME_RANGE "=" "%" XSTR(BUFLEN) SIGNED_FP_NUMBER_RANGE "%c", name, value, &dummy) == 2) {
      return set_fp_numeric_flag(name, value, origin);
    }
  }

#define VALUE_RANGE "[-kmgtKMGT0123456789]"
  if (sscanf(arg, "%" XSTR(BUFLEN) NAME_RANGE "=" "%" XSTR(BUFLEN) VALUE_RANGE "%c", name, value, &dummy) == 2) {
    return set_numeric_flag(name, value, origin);
  }

  return false;
}

void Arguments::add_string(char*** bldarray, int* count, const char* arg) {
  assert(bldarray != NULL, "illegal argument");

  if (arg == NULL) {
    return;
  }

  int index = *count;

  // expand the array and add arg to the last element
  (*count)++;
  if (*bldarray == NULL) {
    *bldarray = NEW_C_HEAP_ARRAY(char*, *count);
  } else {
    *bldarray = REALLOC_C_HEAP_ARRAY(char*, *bldarray, *count);
  }
  (*bldarray)[index] = strdup(arg);
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
  }
  if (num_jvm_args() > 0) {
    st->print("jvm_args: "); print_jvm_args_on(st);
  }
  st->print_cr("java_command: %s", java_command() ? java_command() : "<unknown>");
  st->print_cr("Launcher Type: %s", _sun_java_launcher);
}

void Arguments::print_jvm_flags_on(outputStream* st) {
  if (_num_jvm_flags > 0) {
    for (int i=0; i < _num_jvm_flags; i++) {
      st->print("%s ", _jvm_flags_array[i]);
    }
    st->print_cr("");
  }
}

void Arguments::print_jvm_args_on(outputStream* st) {
  if (_num_jvm_args > 0) {
    for (int i=0; i < _num_jvm_args; i++) {
      st->print("%s ", _jvm_args_array[i]);
    }
    st->print_cr("");
  }
}

bool Arguments::process_argument(const char* arg,
    jboolean ignore_unrecognized, FlagValueOrigin origin) {

  JDK_Version since = JDK_Version();

  if (parse_argument(arg, origin)) {
    // do nothing
  } else if (is_newly_obsolete(arg, &since)) {
    enum { bufsize = 256 };
    char buffer[bufsize];
    since.to_string(buffer, bufsize);
    jio_fprintf(defaultStream::error_stream(),
      "Warning: The flag %s has been EOL'd as of %s and will"
      " be ignored\n", arg, buffer);
  } else {
    if (!ignore_unrecognized) {
      jio_fprintf(defaultStream::error_stream(),
                  "Unrecognized VM option '%s'\n", arg);
      // allow for commandline "commenting out" options like -XX:#+Verbose
      if (strlen(arg) == 0 || arg[0] != '#') {
        return false;
      }
    }
  }
  return true;
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
  while(c != EOF) {
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
        result &= process_argument(token, ignore_unrecognized, CONFIG_FILE);
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
    result &= process_argument(token, ignore_unrecognized, CONFIG_FILE);
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
  char* key;
  // ns must be static--its address may be stored in a SystemProperty object.
  const static char ns[1] = {0};
  char* value = (char *)ns;

  size_t key_len = (eq == NULL) ? strlen(prop) : (eq - prop);
  key = AllocateHeap(key_len + 1, "add_property");
  strncpy(key, prop, key_len);
  key[key_len] = '\0';

  if (eq != NULL) {
    size_t value_len = strlen(prop) - key_len - 1;
    value = AllocateHeap(value_len + 1, "add_property");
    strncpy(value, &prop[key_len + 1], value_len + 1);
  }

  if (strcmp(key, "java.compiler") == 0) {
    process_java_compiler_argument(value);
    FreeHeap(key);
    if (eq != NULL) {
      FreeHeap(value);
    }
    return true;
  } else if (strcmp(key, "sun.java.command") == 0) {
    _java_command = value;

    // don't add this property to the properties exposed to the java application
    FreeHeap(key);
    return true;
  } else if (strcmp(key, "sun.java.launcher.pid") == 0) {
    // launcher.pid property is private and is processed
    // in process_sun_java_launcher_properties();
    // the sun.java.launcher property is passed on to the java application
    FreeHeap(key);
    if (eq != NULL) {
      FreeHeap(value);
    }
    return true;
  } else if (strcmp(key, "java.vendor.url.bug") == 0) {
    // save it in _java_vendor_url_bug, so JVM fatal error handler can access
    // its value without going through the property list or making a Java call.
    _java_vendor_url_bug = value;
  } else if (strcmp(key, "sun.boot.library.path") == 0) {
    PropertyList_unique_add(&_system_properties, key, value, true);
    return true;
  }
  // Create new property and add at the end of the list
  PropertyList_unique_add(&_system_properties, key, value);
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
                          (char*)Abstract_VM_Version::vm_info_string(), false);

  UseInterpreter             = true;
  UseCompiler                = true;
  UseLoopCounter             = true;

  // Default values may be platform/compiler dependent -
  // use the saved values
  ClipInlining               = Arguments::_ClipInlining;
  AlwaysCompileLoopMethods   = Arguments::_AlwaysCompileLoopMethods;
  UseOnStackReplacement      = Arguments::_UseOnStackReplacement;
  BackgroundCompilation      = Arguments::_BackgroundCompilation;
  Tier2CompileThreshold      = Arguments::_Tier2CompileThreshold;

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
    break;
  }
}

// Conflict: required to use shared spaces (-Xshare:on), but
// incompatible command line options were chosen.

static void no_shared_spaces() {
  if (RequireSharedSpaces) {
    jio_fprintf(defaultStream::error_stream(),
      "Class data sharing is inconsistent with other specified options.\n");
    vm_exit_during_initialization("Unable to use shared archive.", NULL);
  } else {
    FLAG_SET_DEFAULT(UseSharedSpaces, false);
  }
}

#ifndef KERNEL
// If the user has chosen ParallelGCThreads > 0, we set UseParNewGC
// if it's not explictly set or unset. If the user has chosen
// UseParNewGC and not explicitly set ParallelGCThreads we
// set it, unless this is a single cpu machine.
void Arguments::set_parnew_gc_flags() {
  assert(!UseSerialGC && !UseParallelOldGC && !UseParallelGC && !UseG1GC,
         "control point invariant");
  assert(UseParNewGC, "Error");

  // Turn off AdaptiveSizePolicy by default for parnew until it is
  // complete.
  if (FLAG_IS_DEFAULT(UseAdaptiveSizePolicy)) {
    FLAG_SET_DEFAULT(UseAdaptiveSizePolicy, false);
  }

  if (ParallelGCThreads == 0) {
    FLAG_SET_DEFAULT(ParallelGCThreads,
                     Abstract_VM_Version::parallel_worker_threads());
    if (ParallelGCThreads == 1) {
      FLAG_SET_DEFAULT(UseParNewGC, false);
      FLAG_SET_DEFAULT(ParallelGCThreads, 0);
    }
  }
  if (UseParNewGC) {
    // CDS doesn't work with ParNew yet
    no_shared_spaces();

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

    // AlwaysTenure flag should make ParNew promote all at first collection.
    // See CR 6362902.
    if (AlwaysTenure) {
      FLAG_SET_CMDLINE(intx, MaxTenuringThreshold, 0);
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
}

// Adjust some sizes to suit CMS and/or ParNew needs; these work well on
// sparc/solaris for certain applications, but would gain from
// further optimization and tuning efforts, and would almost
// certainly gain from analysis of platform and environment.
void Arguments::set_cms_and_parnew_gc_flags() {
  assert(!UseSerialGC && !UseParallelOldGC && !UseParallelGC, "Error");
  assert(UseConcMarkSweepGC, "CMS is expected to be on here");

  // If we are using CMS, we prefer to UseParNewGC,
  // unless explicitly forbidden.
  if (FLAG_IS_DEFAULT(UseParNewGC)) {
    FLAG_SET_ERGO(bool, UseParNewGC, true);
  }

  // Turn off AdaptiveSizePolicy by default for cms until it is
  // complete.
  if (FLAG_IS_DEFAULT(UseAdaptiveSizePolicy)) {
    FLAG_SET_DEFAULT(UseAdaptiveSizePolicy, false);
  }

  // In either case, adjust ParallelGCThreads and/or UseParNewGC
  // as needed.
  if (UseParNewGC) {
    set_parnew_gc_flags();
  }

  // Now make adjustments for CMS
  size_t young_gen_per_worker;
  intx new_ratio;
  size_t min_new_default;
  intx tenuring_default;
  if (CMSUseOldDefaults) {  // old defaults: "old" as of 6.0
    if FLAG_IS_DEFAULT(CMSYoungGenPerWorker) {
      FLAG_SET_ERGO(intx, CMSYoungGenPerWorker, 4*M);
    }
    young_gen_per_worker = 4*M;
    new_ratio = (intx)15;
    min_new_default = 4*M;
    tenuring_default = (intx)0;
  } else { // new defaults: "new" as of 6.0
    young_gen_per_worker = CMSYoungGenPerWorker;
    new_ratio = (intx)7;
    min_new_default = 16*M;
    tenuring_default = (intx)4;
  }

  // Preferred young gen size for "short" pauses
  const uintx parallel_gc_threads =
    (ParallelGCThreads == 0 ? 1 : ParallelGCThreads);
  const size_t preferred_max_new_size_unaligned =
    ScaleForWordSize(young_gen_per_worker * parallel_gc_threads);
  const size_t preferred_max_new_size =
    align_size_up(preferred_max_new_size_unaligned, os::vm_page_size());

  // Unless explicitly requested otherwise, size young gen
  // for "short" pauses ~ 4M*ParallelGCThreads

  // If either MaxNewSize or NewRatio is set on the command line,
  // assume the user is trying to set the size of the young gen.

  if (FLAG_IS_DEFAULT(MaxNewSize) && FLAG_IS_DEFAULT(NewRatio)) {

    // Set MaxNewSize to our calculated preferred_max_new_size unless
    // NewSize was set on the command line and it is larger than
    // preferred_max_new_size.
    if (!FLAG_IS_DEFAULT(NewSize)) {   // NewSize explicitly set at command-line
      FLAG_SET_ERGO(uintx, MaxNewSize, MAX2(NewSize, preferred_max_new_size));
    } else {
      FLAG_SET_ERGO(uintx, MaxNewSize, preferred_max_new_size);
    }
    if (PrintGCDetails && Verbose) {
      // Too early to use gclog_or_tty
      tty->print_cr("Ergo set MaxNewSize: " SIZE_FORMAT, MaxNewSize);
    }

    // Unless explicitly requested otherwise, prefer a large
    // Old to Young gen size so as to shift the collection load
    // to the old generation concurrent collector

    // If this is only guarded by FLAG_IS_DEFAULT(NewRatio)
    // then NewSize and OldSize may be calculated.  That would
    // generally lead to some differences with ParNewGC for which
    // there was no obvious reason.  Also limit to the case where
    // MaxNewSize has not been set.

    FLAG_SET_ERGO(intx, NewRatio, MAX2(NewRatio, new_ratio));

    // Code along this path potentially sets NewSize and OldSize

    // Calculate the desired minimum size of the young gen but if
    // NewSize has been set on the command line, use it here since
    // it should be the final value.
    size_t min_new;
    if (FLAG_IS_DEFAULT(NewSize)) {
      min_new = align_size_up(ScaleForWordSize(min_new_default),
                              os::vm_page_size());
    } else {
      min_new = NewSize;
    }
    size_t prev_initial_size = InitialHeapSize;
    if (prev_initial_size != 0 && prev_initial_size < min_new + OldSize) {
      FLAG_SET_ERGO(uintx, InitialHeapSize, min_new + OldSize);
      // Currently minimum size and the initial heap sizes are the same.
      set_min_heap_size(InitialHeapSize);
      if (PrintGCDetails && Verbose) {
        warning("Initial heap size increased to " SIZE_FORMAT " M from "
                SIZE_FORMAT " M; use -XX:NewSize=... for finer control.",
                InitialHeapSize/M, prev_initial_size/M);
      }
    }

    // MaxHeapSize is aligned down in collectorPolicy
    size_t max_heap =
      align_size_down(MaxHeapSize,
                      CardTableRS::ct_max_alignment_constraint());

    if (PrintGCDetails && Verbose) {
      // Too early to use gclog_or_tty
      tty->print_cr("CMS set min_heap_size: " SIZE_FORMAT
           " initial_heap_size:  " SIZE_FORMAT
           " max_heap: " SIZE_FORMAT,
           min_heap_size(), InitialHeapSize, max_heap);
    }
    if (max_heap > min_new) {
      // Unless explicitly requested otherwise, make young gen
      // at least min_new, and at most preferred_max_new_size.
      if (FLAG_IS_DEFAULT(NewSize)) {
        FLAG_SET_ERGO(uintx, NewSize, MAX2(NewSize, min_new));
        FLAG_SET_ERGO(uintx, NewSize, MIN2(preferred_max_new_size, NewSize));
        if (PrintGCDetails && Verbose) {
          // Too early to use gclog_or_tty
          tty->print_cr("Ergo set NewSize: " SIZE_FORMAT, NewSize);
        }
      }
      // Unless explicitly requested otherwise, size old gen
      // so that it's at least 3X of NewSize to begin with;
      // later NewRatio will decide how it grows; see above.
      if (FLAG_IS_DEFAULT(OldSize)) {
        if (max_heap > NewSize) {
          FLAG_SET_ERGO(uintx, OldSize, MIN2(3*NewSize, max_heap - NewSize));
          if (PrintGCDetails && Verbose) {
            // Too early to use gclog_or_tty
            tty->print_cr("Ergo set OldSize: " SIZE_FORMAT, OldSize);
          }
        }
      }
    }
  }
  // Unless explicitly requested otherwise, definitely
  // promote all objects surviving "tenuring_default" scavenges.
  if (FLAG_IS_DEFAULT(MaxTenuringThreshold) &&
      FLAG_IS_DEFAULT(SurvivorRatio)) {
    FLAG_SET_ERGO(intx, MaxTenuringThreshold, tenuring_default);
  }
  // If we decided above (or user explicitly requested)
  // `promote all' (via MaxTenuringThreshold := 0),
  // prefer minuscule survivor spaces so as not to waste
  // space for (non-existent) survivors
  if (FLAG_IS_DEFAULT(SurvivorRatio) && MaxTenuringThreshold == 0) {
    FLAG_SET_ERGO(intx, SurvivorRatio, MAX2((intx)1024, SurvivorRatio));
  }
  // If OldPLABSize is set and CMSParPromoteBlocksToClaim is not,
  // set CMSParPromoteBlocksToClaim equal to OldPLABSize.
  // This is done in order to make ParNew+CMS configuration to work
  // with YoungPLABSize and OldPLABSize options.
  // See CR 6362902.
  if (!FLAG_IS_DEFAULT(OldPLABSize)) {
    if (FLAG_IS_DEFAULT(CMSParPromoteBlocksToClaim)) {
      // OldPLABSize is not the default value but CMSParPromoteBlocksToClaim
      // is.  In this situtation let CMSParPromoteBlocksToClaim follow
      // the value (either from the command line or ergonomics) of
      // OldPLABSize.  Following OldPLABSize is an ergonomics decision.
      FLAG_SET_ERGO(uintx, CMSParPromoteBlocksToClaim, OldPLABSize);
    } else {
      // OldPLABSize and CMSParPromoteBlocksToClaim are both set.
      // CMSParPromoteBlocksToClaim is a collector-specific flag, so
      // we'll let it to take precedence.
      jio_fprintf(defaultStream::error_stream(),
                  "Both OldPLABSize and CMSParPromoteBlocksToClaim"
                  " options are specified for the CMS collector."
                  " CMSParPromoteBlocksToClaim will take precedence.\n");
    }
  }
  if (!FLAG_IS_DEFAULT(ResizeOldPLAB) && !ResizeOldPLAB) {
    // OldPLAB sizing manually turned off: Use a larger default setting,
    // unless it was manually specified. This is because a too-low value
    // will slow down scavenges.
    if (FLAG_IS_DEFAULT(CMSParPromoteBlocksToClaim)) {
      FLAG_SET_ERGO(uintx, CMSParPromoteBlocksToClaim, 50); // default value before 6631166
    }
  }
  // Overwrite OldPLABSize which is the variable we will internally use everywhere.
  FLAG_SET_ERGO(uintx, OldPLABSize, CMSParPromoteBlocksToClaim);
  // If either of the static initialization defaults have changed, note this
  // modification.
  if (!FLAG_IS_DEFAULT(CMSParPromoteBlocksToClaim) || !FLAG_IS_DEFAULT(OldPLABWeight)) {
    CFLS_LAB::modify_initialization(OldPLABSize, OldPLABWeight);
  }
}
#endif // KERNEL

inline uintx max_heap_for_compressed_oops() {
  LP64_ONLY(return oopDesc::OopEncodingHeapMax - MaxPermSize - os::vm_page_size());
  NOT_LP64(ShouldNotReachHere(); return 0);
}

bool Arguments::should_auto_select_low_pause_collector() {
  if (UseAutoGCSelectPolicy &&
      !FLAG_IS_DEFAULT(MaxGCPauseMillis) &&
      (MaxGCPauseMillis <= AutoGCSelectPauseMillis)) {
    if (PrintGCDetails) {
      // Cannot use gclog_or_tty yet.
      tty->print_cr("Automatic selection of the low pause collector"
       " based on pause goal of %d (ms)", MaxGCPauseMillis);
    }
    return true;
  }
  return false;
}

void Arguments::set_ergonomics_flags() {
  // Parallel GC is not compatible with sharing. If one specifies
  // that they want sharing explicitly, do not set ergonomics flags.
  if (DumpSharedSpaces || ForceSharedSpaces) {
    return;
  }

  if (os::is_server_class_machine() && !force_client_mode ) {
    // If no other collector is requested explicitly,
    // let the VM select the collector based on
    // machine class and automatic selection policy.
    if (!UseSerialGC &&
        !UseConcMarkSweepGC &&
        !UseG1GC &&
        !UseParNewGC &&
        !DumpSharedSpaces &&
        FLAG_IS_DEFAULT(UseParallelGC)) {
      if (should_auto_select_low_pause_collector()) {
        FLAG_SET_ERGO(bool, UseConcMarkSweepGC, true);
      } else {
        FLAG_SET_ERGO(bool, UseParallelGC, true);
      }
      no_shared_spaces();
    }
  }

#ifndef ZERO
#ifdef _LP64
  // Check that UseCompressedOops can be set with the max heap size allocated
  // by ergonomics.
  if (MaxHeapSize <= max_heap_for_compressed_oops()) {
#ifndef COMPILER1
    if (FLAG_IS_DEFAULT(UseCompressedOops) && !UseG1GC) {
      FLAG_SET_ERGO(bool, UseCompressedOops, true);
    }
#endif
#ifdef _WIN64
    if (UseLargePages && UseCompressedOops) {
      // Cannot allocate guard pages for implicit checks in indexed addressing
      // mode, when large pages are specified on windows.
      // This flag could be switched ON if narrow oop base address is set to 0,
      // see code in Universe::initialize_heap().
      Universe::set_narrow_oop_use_implicit_null_checks(false);
    }
#endif //  _WIN64
  } else {
    if (UseCompressedOops && !FLAG_IS_DEFAULT(UseCompressedOops)) {
      warning("Max heap size too large for Compressed Oops");
      FLAG_SET_DEFAULT(UseCompressedOops, false);
    }
  }
  // Also checks that certain machines are slower with compressed oops
  // in vm_version initialization code.
#endif // _LP64
#endif // !ZERO
}

void Arguments::set_parallel_gc_flags() {
  assert(UseParallelGC || UseParallelOldGC, "Error");
  // If parallel old was requested, automatically enable parallel scavenge.
  if (UseParallelOldGC && !UseParallelGC && FLAG_IS_DEFAULT(UseParallelGC)) {
    FLAG_SET_DEFAULT(UseParallelGC, true);
  }

  // If no heap maximum was requested explicitly, use some reasonable fraction
  // of the physical memory, up to a maximum of 1GB.
  if (UseParallelGC) {
    FLAG_SET_ERGO(uintx, ParallelGCThreads,
                  Abstract_VM_Version::parallel_worker_threads());

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
      if (FLAG_IS_DEFAULT(PermMarkSweepDeadRatio)) {
        FLAG_SET_DEFAULT(PermMarkSweepDeadRatio, 5);
      }
    }
  }
}

void Arguments::set_g1_gc_flags() {
  assert(UseG1GC, "Error");
#ifdef COMPILER1
  FastTLABRefill = false;
#endif
  FLAG_SET_DEFAULT(ParallelGCThreads,
                     Abstract_VM_Version::parallel_worker_threads());
  if (ParallelGCThreads == 0) {
    FLAG_SET_DEFAULT(ParallelGCThreads,
                     Abstract_VM_Version::parallel_worker_threads());
  }
  no_shared_spaces();

  // Set the maximum pause time goal to be a reasonable default.
  if (FLAG_IS_DEFAULT(MaxGCPauseMillis)) {
    FLAG_SET_DEFAULT(MaxGCPauseMillis, 200);
  }
}

void Arguments::set_heap_size() {
  if (!FLAG_IS_DEFAULT(DefaultMaxRAMFraction)) {
    // Deprecated flag
    FLAG_SET_CMDLINE(uintx, MaxRAMFraction, DefaultMaxRAMFraction);
  }

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
      reasonable_max = MIN2(reasonable_max, (julong)max_heap_for_compressed_oops());
    }
    reasonable_max = os::allocatable_physical_memory(reasonable_max);

    if (!FLAG_IS_DEFAULT(InitialHeapSize)) {
      // An initial heap size was specified on the command line,
      // so be sure that the maximum size is consistent.  Done
      // after call to allocatable_physical_memory because that
      // method might reduce the allocation size.
      reasonable_max = MAX2(reasonable_max, (julong)InitialHeapSize);
    }

    if (PrintGCDetails && Verbose) {
      // Cannot use gclog_or_tty yet.
      tty->print_cr("  Maximum heap size " SIZE_FORMAT, reasonable_max);
    }
    FLAG_SET_ERGO(uintx, MaxHeapSize, (uintx)reasonable_max);
  }

  // If the initial_heap_size has not been set with InitialHeapSize
  // or -Xms, then set it as fraction of the size of physical memory,
  // respecting the maximum and minimum sizes of the heap.
  if (FLAG_IS_DEFAULT(InitialHeapSize)) {
    julong reasonable_minimum = (julong)(OldSize + NewSize);

    reasonable_minimum = MIN2(reasonable_minimum, (julong)MaxHeapSize);

    reasonable_minimum = os::allocatable_physical_memory(reasonable_minimum);

    julong reasonable_initial = phys_mem / InitialRAMFraction;

    reasonable_initial = MAX2(reasonable_initial, reasonable_minimum);
    reasonable_initial = MIN2(reasonable_initial, (julong)MaxHeapSize);

    reasonable_initial = os::allocatable_physical_memory(reasonable_initial);

    if (PrintGCDetails && Verbose) {
      // Cannot use gclog_or_tty yet.
      tty->print_cr("  Initial heap size " SIZE_FORMAT, (uintx)reasonable_initial);
      tty->print_cr("  Minimum heap size " SIZE_FORMAT, (uintx)reasonable_minimum);
    }
    FLAG_SET_ERGO(uintx, InitialHeapSize, (uintx)reasonable_initial);
    set_min_heap_size((uintx)reasonable_minimum);
  }
}

// This must be called after ergonomics because we want bytecode rewriting
// if the server compiler is used, or if UseSharedSpaces is disabled.
void Arguments::set_bytecode_flags() {
  // Better not attempt to store into a read-only space.
  if (UseSharedSpaces) {
    FLAG_SET_DEFAULT(RewriteBytecodes, false);
    FLAG_SET_DEFAULT(RewriteFrequentPairs, false);
  }

  if (!RewriteBytecodes) {
    FLAG_SET_DEFAULT(RewriteFrequentPairs, false);
  }
}

// Aggressive optimization flags  -XX:+AggressiveOpts
void Arguments::set_aggressive_opts_flags() {
#ifdef COMPILER2
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
    add_property(buffer);
  }
  if (AggressiveOpts && FLAG_IS_DEFAULT(DoEscapeAnalysis)) {
    FLAG_SET_DEFAULT(DoEscapeAnalysis, true);
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
}

//===========================================================================================================
// Parsing of java.compiler property

void Arguments::process_java_compiler_argument(char* arg) {
  // For backwards compatibility, Djava.compiler=NONE or ""
  // causes us to switch to -Xint mode UNLESS -Xdebug
  // is also specified.
  if (strlen(arg) == 0 || strcasecmp(arg, "NONE") == 0) {
    set_java_compiler(true);    // "-Djava.compiler[=...]" most recently seen.
  }
}

void Arguments::process_java_launcher_argument(const char* launcher, void* extra_info) {
  _sun_java_launcher = strdup(launcher);
}

bool Arguments::created_by_java_launcher() {
  assert(_sun_java_launcher != NULL, "property must have value");
  return strcmp(DEFAULT_JAVA_LAUNCHER, _sun_java_launcher) != 0;
}

//===========================================================================================================
// Parsing of main arguments

bool Arguments::verify_percentage(uintx value, const char* name) {
  if (value <= 100) {
    return true;
  }
  jio_fprintf(defaultStream::error_stream(),
              "%s of " UINTX_FORMAT " is invalid; must be between 0 and 100\n",
              name, value);
  return false;
}

static void force_serial_gc() {
  FLAG_SET_DEFAULT(UseSerialGC, true);
  FLAG_SET_DEFAULT(UseParNewGC, false);
  FLAG_SET_DEFAULT(UseConcMarkSweepGC, false);
  FLAG_SET_DEFAULT(CMSIncrementalMode, false);  // special CMS suboption
  FLAG_SET_DEFAULT(UseParallelGC, false);
  FLAG_SET_DEFAULT(UseParallelOldGC, false);
  FLAG_SET_DEFAULT(UseG1GC, false);
}

static bool verify_serial_gc_flags() {
  return (UseSerialGC &&
        !(UseParNewGC || (UseConcMarkSweepGC || CMSIncrementalMode) || UseG1GC ||
          UseParallelGC || UseParallelOldGC));
}

// Check consistency of GC selection
bool Arguments::check_gc_consistency() {
  bool status = true;
  // Ensure that the user has not selected conflicting sets
  // of collectors. [Note: this check is merely a user convenience;
  // collectors over-ride each other so that only a non-conflicting
  // set is selected; however what the user gets is not what they
  // may have expected from the combination they asked for. It's
  // better to reduce user confusion by not allowing them to
  // select conflicting combinations.
  uint i = 0;
  if (UseSerialGC)                       i++;
  if (UseConcMarkSweepGC || UseParNewGC) i++;
  if (UseParallelGC || UseParallelOldGC) i++;
  if (UseG1GC)                           i++;
  if (i > 1) {
    jio_fprintf(defaultStream::error_stream(),
                "Conflicting collector combinations in option list; "
                "please refer to the release notes for the combinations "
                "allowed\n");
    status = false;
  }

  return status;
}

// Check the consistency of vm_init_args
bool Arguments::check_vm_args_consistency() {
  // Method for adding checks for flag consistency.
  // The intent is to warn the user of all possible conflicts,
  // before returning an error.
  // Note: Needs platform-dependent factoring.
  bool status = true;

#if ( (defined(COMPILER2) && defined(SPARC)))
  // NOTE: The call to VM_Version_init depends on the fact that VM_Version_init
  // on sparc doesn't require generation of a stub as is the case on, e.g.,
  // x86.  Normally, VM_Version_init must be called from init_globals in
  // init.cpp, which is called by the initial java thread *after* arguments
  // have been parsed.  VM_Version_init gets called twice on sparc.
  extern void VM_Version_init();
  VM_Version_init();
  if (!VM_Version::has_v9()) {
    jio_fprintf(defaultStream::error_stream(),
                "V8 Machine detected, Server requires V9\n");
    status = false;
  }
#endif /* COMPILER2 && SPARC */

  // Allow both -XX:-UseStackBanging and -XX:-UseBoundThreads in non-product
  // builds so the cost of stack banging can be measured.
#if (defined(PRODUCT) && defined(SOLARIS))
  if (!UseBoundThreads && !UseStackBanging) {
    jio_fprintf(defaultStream::error_stream(),
                "-UseStackBanging conflicts with -UseBoundThreads\n");

     status = false;
  }
#endif

  if (TLABRefillWasteFraction == 0) {
    jio_fprintf(defaultStream::error_stream(),
                "TLABRefillWasteFraction should be a denominator, "
                "not " SIZE_FORMAT "\n",
                TLABRefillWasteFraction);
    status = false;
  }

  status = status && verify_percentage(MaxLiveObjectEvacuationRatio,
                              "MaxLiveObjectEvacuationRatio");
  status = status && verify_percentage(AdaptiveSizePolicyWeight,
                              "AdaptiveSizePolicyWeight");
  status = status && verify_percentage(AdaptivePermSizeWeight, "AdaptivePermSizeWeight");
  status = status && verify_percentage(ThresholdTolerance, "ThresholdTolerance");
  status = status && verify_percentage(MinHeapFreeRatio, "MinHeapFreeRatio");
  status = status && verify_percentage(MaxHeapFreeRatio, "MaxHeapFreeRatio");

  if (MinHeapFreeRatio > MaxHeapFreeRatio) {
    jio_fprintf(defaultStream::error_stream(),
                "MinHeapFreeRatio (" UINTX_FORMAT ") must be less than or "
                "equal to MaxHeapFreeRatio (" UINTX_FORMAT ")\n",
                MinHeapFreeRatio, MaxHeapFreeRatio);
    status = false;
  }
  // Keeping the heap 100% free is hard ;-) so limit it to 99%.
  MinHeapFreeRatio = MIN2(MinHeapFreeRatio, (uintx) 99);

  if (FullGCALot && FLAG_IS_DEFAULT(MarkSweepAlwaysCompactCount)) {
    MarkSweepAlwaysCompactCount = 1;  // Move objects every gc.
  }

  if (UseParallelOldGC && ParallelOldGCSplitALot) {
    // Settings to encourage splitting.
    if (!FLAG_IS_CMDLINE(NewRatio)) {
      FLAG_SET_CMDLINE(intx, NewRatio, 2);
    }
    if (!FLAG_IS_CMDLINE(ScavengeBeforeFullGC)) {
      FLAG_SET_CMDLINE(bool, ScavengeBeforeFullGC, false);
    }
  }

  status = status && verify_percentage(GCHeapFreeLimit, "GCHeapFreeLimit");
  status = status && verify_percentage(GCTimeLimit, "GCTimeLimit");
  if (GCTimeLimit == 100) {
    // Turn off gc-overhead-limit-exceeded checks
    FLAG_SET_DEFAULT(UseGCOverheadLimit, false);
  }

  status = status && verify_percentage(GCHeapFreeLimit, "GCHeapFreeLimit");

  // Check user specified sharing option conflict with Parallel GC
  bool cannot_share = ((UseConcMarkSweepGC || CMSIncrementalMode) || UseG1GC || UseParNewGC ||
                       UseParallelGC || UseParallelOldGC ||
                       SOLARIS_ONLY(UseISM) NOT_SOLARIS(UseLargePages));

  if (cannot_share) {
    // Either force sharing on by forcing the other options off, or
    // force sharing off.
    if (DumpSharedSpaces || ForceSharedSpaces) {
      jio_fprintf(defaultStream::error_stream(),
                  "Reverting to Serial GC because of %s\n",
                  ForceSharedSpaces ? " -Xshare:on" : "-Xshare:dump");
      force_serial_gc();
      FLAG_SET_DEFAULT(SOLARIS_ONLY(UseISM) NOT_SOLARIS(UseLargePages), false);
    } else {
      if (UseSharedSpaces && Verbose) {
        jio_fprintf(defaultStream::error_stream(),
                    "Turning off use of shared archive because of "
                    "choice of garbage collector or large pages\n");
      }
      no_shared_spaces();
    }
  }

  status = status && check_gc_consistency();

  if (_has_alloc_profile) {
    if (UseParallelGC || UseParallelOldGC) {
      jio_fprintf(defaultStream::error_stream(),
                  "error:  invalid argument combination.\n"
                  "Allocation profiling (-Xaprof) cannot be used together with "
                  "Parallel GC (-XX:+UseParallelGC or -XX:+UseParallelOldGC).\n");
      status = false;
    }
    if (UseConcMarkSweepGC) {
      jio_fprintf(defaultStream::error_stream(),
                  "error:  invalid argument combination.\n"
                  "Allocation profiling (-Xaprof) cannot be used together with "
                  "the CMS collector (-XX:+UseConcMarkSweepGC).\n");
      status = false;
    }
  }

  if (CMSIncrementalMode) {
    if (!UseConcMarkSweepGC) {
      jio_fprintf(defaultStream::error_stream(),
                  "error:  invalid argument combination.\n"
                  "The CMS collector (-XX:+UseConcMarkSweepGC) must be "
                  "selected in order\nto use CMSIncrementalMode.\n");
      status = false;
    } else {
      status = status && verify_percentage(CMSIncrementalDutyCycle,
                                  "CMSIncrementalDutyCycle");
      status = status && verify_percentage(CMSIncrementalDutyCycleMin,
                                  "CMSIncrementalDutyCycleMin");
      status = status && verify_percentage(CMSIncrementalSafetyFactor,
                                  "CMSIncrementalSafetyFactor");
      status = status && verify_percentage(CMSIncrementalOffset,
                                  "CMSIncrementalOffset");
      status = status && verify_percentage(CMSExpAvgFactor,
                                  "CMSExpAvgFactor");
      // If it was not set on the command line, set
      // CMSInitiatingOccupancyFraction to 1 so icms can initiate cycles early.
      if (CMSInitiatingOccupancyFraction < 0) {
        FLAG_SET_DEFAULT(CMSInitiatingOccupancyFraction, 1);
      }
    }
  }

  // CMS space iteration, which FLSVerifyAllHeapreferences entails,
  // insists that we hold the requisite locks so that the iteration is
  // MT-safe. For the verification at start-up and shut-down, we don't
  // yet have a good way of acquiring and releasing these locks,
  // which are not visible at the CollectedHeap level. We want to
  // be able to acquire these locks and then do the iteration rather
  // than just disable the lock verification. This will be fixed under
  // bug 4788986.
  if (UseConcMarkSweepGC && FLSVerifyAllHeapReferences) {
    if (VerifyGCStartAt == 0) {
      warning("Heap verification at start-up disabled "
              "(due to current incompatibility with FLSVerifyAllHeapReferences)");
      VerifyGCStartAt = 1;      // Disable verification at start-up
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
                "error: +ExplictGCInvokesConcurrent[AndUnloadsClasses] conflicts"
                " with -UseAsyncConcMarkSweepGC");
    status = false;
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

Arguments::ArgsRange Arguments::parse_memory_size(const char* s,
                                                  julong* long_arg,
                                                  julong min_size) {
  if (!atomull(s, long_arg)) return arg_unreadable;
  return check_memory_size(*long_arg, min_size);
}

// Parse JavaVMInitArgs structure

jint Arguments::parse_vm_init_args(const JavaVMInitArgs* args) {
  // For components of the system classpath.
  SysClassPath scp(Arguments::get_sysclasspath());
  bool scp_assembly_required = false;

  // Save default settings for some mode flags
  Arguments::_AlwaysCompileLoopMethods = AlwaysCompileLoopMethods;
  Arguments::_UseOnStackReplacement    = UseOnStackReplacement;
  Arguments::_ClipInlining             = ClipInlining;
  Arguments::_BackgroundCompilation    = BackgroundCompilation;
  Arguments::_Tier2CompileThreshold    = Tier2CompileThreshold;

  // Parse JAVA_TOOL_OPTIONS environment variable (if present)
  jint result = parse_java_tool_options_environment_variable(&scp, &scp_assembly_required);
  if (result != JNI_OK) {
    return result;
  }

  // Parse JavaVMInitArgs structure passed in
  result = parse_each_vm_init_arg(args, &scp, &scp_assembly_required, COMMAND_LINE);
  if (result != JNI_OK) {
    return result;
  }

  if (AggressiveOpts) {
    // Insert alt-rt.jar between user-specified bootclasspath
    // prefix and the default bootclasspath.  os::set_boot_path()
    // uses meta_index_dir as the default bootclasspath directory.
    const char* altclasses_jar = "alt-rt.jar";
    size_t altclasses_path_len = strlen(get_meta_index_dir()) + 1 +
                                 strlen(altclasses_jar);
    char* altclasses_path = NEW_C_HEAP_ARRAY(char, altclasses_path_len);
    strcpy(altclasses_path, get_meta_index_dir());
    strcat(altclasses_path, altclasses_jar);
    scp.add_suffix_to_prefix(altclasses_path);
    scp_assembly_required = true;
    FREE_C_HEAP_ARRAY(char, altclasses_path);
  }

  // Parse _JAVA_OPTIONS environment variable (if present) (mimics classic VM)
  result = parse_java_options_environment_variable(&scp, &scp_assembly_required);
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

jint Arguments::parse_each_vm_init_arg(const JavaVMInitArgs* args,
                                       SysClassPath* scp_p,
                                       bool* scp_assembly_required_p,
                                       FlagValueOrigin origin) {
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
        FLAG_SET_CMDLINE(bool, TraceClassLoading, true);
        FLAG_SET_CMDLINE(bool, TraceClassUnloading, true);
      } else if (!strcmp(tail, ":gc")) {
        FLAG_SET_CMDLINE(bool, PrintGC, true);
        FLAG_SET_CMDLINE(bool, TraceClassUnloading, true);
      } else if (!strcmp(tail, ":jni")) {
        FLAG_SET_CMDLINE(bool, PrintJNIResolving, true);
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
        char* name = (char*)memcpy(NEW_C_HEAP_ARRAY(char, len + 1), tail, len);
        name[len] = '\0';

        char *options = NULL;
        if(pos != NULL) {
          size_t len2 = strlen(pos+1) + 1; // options start after ':'.  Final zero must be copied.
          options = (char*)memcpy(NEW_C_HEAP_ARRAY(char, len2), pos+1, len2);
        }
#ifdef JVMTI_KERNEL
        if ((strcmp(name, "hprof") == 0) || (strcmp(name, "jdwp") == 0)) {
          warning("profiling and debugging agents are not supported with Kernel VM");
        } else
#endif // JVMTI_KERNEL
        add_init_library(name, options);
      }
    // -agentlib and -agentpath
    } else if (match_option(option, "-agentlib:", &tail) ||
          (is_absolute_path = match_option(option, "-agentpath:", &tail))) {
      if(tail != NULL) {
        const char* pos = strchr(tail, '=');
        size_t len = (pos == NULL) ? strlen(tail) : pos - tail;
        char* name = strncpy(NEW_C_HEAP_ARRAY(char, len + 1), tail, len);
        name[len] = '\0';

        char *options = NULL;
        if(pos != NULL) {
          options = strcpy(NEW_C_HEAP_ARRAY(char, strlen(pos + 1) + 1), pos + 1);
        }
#ifdef JVMTI_KERNEL
        if ((strcmp(name, "hprof") == 0) || (strcmp(name, "jdwp") == 0)) {
          warning("profiling and debugging agents are not supported with Kernel VM");
        } else
#endif // JVMTI_KERNEL
        add_init_agent(name, options, is_absolute_path);

      }
    // -javaagent
    } else if (match_option(option, "-javaagent:", &tail)) {
      if(tail != NULL) {
        char *options = strcpy(NEW_C_HEAP_ARRAY(char, strlen(tail) + 1), tail);
        add_init_agent("instrument", options, false);
      }
    // -Xnoclassgc
    } else if (match_option(option, "-Xnoclassgc", &tail)) {
      FLAG_SET_CMDLINE(bool, ClassUnloading, false);
    // -Xincgc: i-CMS
    } else if (match_option(option, "-Xincgc", &tail)) {
      FLAG_SET_CMDLINE(bool, UseConcMarkSweepGC, true);
      FLAG_SET_CMDLINE(bool, CMSIncrementalMode, true);
    // -Xnoincgc: no i-CMS
    } else if (match_option(option, "-Xnoincgc", &tail)) {
      FLAG_SET_CMDLINE(bool, UseConcMarkSweepGC, false);
      FLAG_SET_CMDLINE(bool, CMSIncrementalMode, false);
    // -Xconcgc
    } else if (match_option(option, "-Xconcgc", &tail)) {
      FLAG_SET_CMDLINE(bool, UseConcMarkSweepGC, true);
    // -Xnoconcgc
    } else if (match_option(option, "-Xnoconcgc", &tail)) {
      FLAG_SET_CMDLINE(bool, UseConcMarkSweepGC, false);
    // -Xbatch
    } else if (match_option(option, "-Xbatch", &tail)) {
      FLAG_SET_CMDLINE(bool, BackgroundCompilation, false);
    // -Xmn for compatibility with other JVM vendors
    } else if (match_option(option, "-Xmn", &tail)) {
      julong long_initial_eden_size = 0;
      ArgsRange errcode = parse_memory_size(tail, &long_initial_eden_size, 1);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid initial eden size: %s\n", option->optionString);
        describe_range_error(errcode);
        return JNI_EINVAL;
      }
      FLAG_SET_CMDLINE(uintx, MaxNewSize, (uintx)long_initial_eden_size);
      FLAG_SET_CMDLINE(uintx, NewSize, (uintx)long_initial_eden_size);
    // -Xms
    } else if (match_option(option, "-Xms", &tail)) {
      julong long_initial_heap_size = 0;
      ArgsRange errcode = parse_memory_size(tail, &long_initial_heap_size, 1);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid initial heap size: %s\n", option->optionString);
        describe_range_error(errcode);
        return JNI_EINVAL;
      }
      FLAG_SET_CMDLINE(uintx, InitialHeapSize, (uintx)long_initial_heap_size);
      // Currently the minimum size and the initial heap sizes are the same.
      set_min_heap_size(InitialHeapSize);
    // -Xmx
    } else if (match_option(option, "-Xmx", &tail)) {
      julong long_max_heap_size = 0;
      ArgsRange errcode = parse_memory_size(tail, &long_max_heap_size, 1);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid maximum heap size: %s\n", option->optionString);
        describe_range_error(errcode);
        return JNI_EINVAL;
      }
      FLAG_SET_CMDLINE(uintx, MaxHeapSize, (uintx)long_max_heap_size);
    // Xmaxf
    } else if (match_option(option, "-Xmaxf", &tail)) {
      int maxf = (int)(atof(tail) * 100);
      if (maxf < 0 || maxf > 100) {
        jio_fprintf(defaultStream::error_stream(),
                    "Bad max heap free percentage size: %s\n",
                    option->optionString);
        return JNI_EINVAL;
      } else {
        FLAG_SET_CMDLINE(uintx, MaxHeapFreeRatio, maxf);
      }
    // Xminf
    } else if (match_option(option, "-Xminf", &tail)) {
      int minf = (int)(atof(tail) * 100);
      if (minf < 0 || minf > 100) {
        jio_fprintf(defaultStream::error_stream(),
                    "Bad min heap free percentage size: %s\n",
                    option->optionString);
        return JNI_EINVAL;
      } else {
        FLAG_SET_CMDLINE(uintx, MinHeapFreeRatio, minf);
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
      FLAG_SET_CMDLINE(intx, ThreadStackSize,
                              round_to((int)long_ThreadStackSize, K) / K);
    // -Xoss
    } else if (match_option(option, "-Xoss", &tail)) {
          // HotSpot does not have separate native and Java stacks, ignore silently for compatibility
    // -Xmaxjitcodesize
    } else if (match_option(option, "-Xmaxjitcodesize", &tail)) {
      julong long_ReservedCodeCacheSize = 0;
      ArgsRange errcode = parse_memory_size(tail, &long_ReservedCodeCacheSize,
                                            (size_t)InitialCodeCacheSize);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid maximum code cache size: %s\n",
                    option->optionString);
        describe_range_error(errcode);
        return JNI_EINVAL;
      }
      FLAG_SET_CMDLINE(uintx, ReservedCodeCacheSize, (uintx)long_ReservedCodeCacheSize);
    // -green
    } else if (match_option(option, "-green", &tail)) {
      jio_fprintf(defaultStream::error_stream(),
                  "Green threads support not available\n");
          return JNI_EINVAL;
    // -native
    } else if (match_option(option, "-native", &tail)) {
          // HotSpot always uses native threads, ignore silently for compatibility
    // -Xsqnopause
    } else if (match_option(option, "-Xsqnopause", &tail)) {
          // EVM option, ignore silently for compatibility
    // -Xrs
    } else if (match_option(option, "-Xrs", &tail)) {
          // Classic/EVM option, new functionality
      FLAG_SET_CMDLINE(bool, ReduceSignalUsage, true);
    } else if (match_option(option, "-Xusealtsigs", &tail)) {
          // change default internal VM signals used - lower case for back compat
      FLAG_SET_CMDLINE(bool, UseAltSigs, true);
    // -Xoptimize
    } else if (match_option(option, "-Xoptimize", &tail)) {
          // EVM option, ignore silently for compatibility
    // -Xprof
    } else if (match_option(option, "-Xprof", &tail)) {
#ifndef FPROF_KERNEL
      _has_profile = true;
#else // FPROF_KERNEL
      // do we have to exit?
      warning("Kernel VM does not support flat profiling.");
#endif // FPROF_KERNEL
    // -Xaprof
    } else if (match_option(option, "-Xaprof", &tail)) {
      _has_alloc_profile = true;
    // -Xconcurrentio
    } else if (match_option(option, "-Xconcurrentio", &tail)) {
      FLAG_SET_CMDLINE(bool, UseLWPSynchronization, true);
      FLAG_SET_CMDLINE(bool, BackgroundCompilation, false);
      FLAG_SET_CMDLINE(intx, DeferThrSuspendLoopCount, 1);
      FLAG_SET_CMDLINE(bool, UseTLAB, false);
      FLAG_SET_CMDLINE(uintx, NewSizeThreadIncrease, 16 * K);  // 20Kb per thread added to new generation

      // -Xinternalversion
    } else if (match_option(option, "-Xinternalversion", &tail)) {
      jio_fprintf(defaultStream::output_stream(), "%s\n",
                  VM_Version::internal_vm_info_string());
      vm_exit(0);
#ifndef PRODUCT
    // -Xprintflags
    } else if (match_option(option, "-Xprintflags", &tail)) {
      CommandLineFlags::printFlags();
      vm_exit(0);
#endif
    // -D
    } else if (match_option(option, "-D", &tail)) {
      if (!add_property(tail)) {
        return JNI_ENOMEM;
      }
      // Out of the box management support
      if (match_option(option, "-Dcom.sun.management", &tail)) {
        FLAG_SET_CMDLINE(bool, ManagementServer, true);
      }
    // -Xint
    } else if (match_option(option, "-Xint", &tail)) {
          set_mode_flags(_int);
    // -Xmixed
    } else if (match_option(option, "-Xmixed", &tail)) {
          set_mode_flags(_mixed);
    // -Xcomp
    } else if (match_option(option, "-Xcomp", &tail)) {
      // for testing the compiler; turn off all flags that inhibit compilation
          set_mode_flags(_comp);

    // -Xshare:dump
    } else if (match_option(option, "-Xshare:dump", &tail)) {
#ifdef TIERED
      FLAG_SET_CMDLINE(bool, DumpSharedSpaces, true);
      set_mode_flags(_int);     // Prevent compilation, which creates objects
#elif defined(COMPILER2)
      vm_exit_during_initialization(
          "Dumping a shared archive is not supported on the Server JVM.", NULL);
#elif defined(KERNEL)
      vm_exit_during_initialization(
          "Dumping a shared archive is not supported on the Kernel JVM.", NULL);
#else
      FLAG_SET_CMDLINE(bool, DumpSharedSpaces, true);
      set_mode_flags(_int);     // Prevent compilation, which creates objects
#endif
    // -Xshare:on
    } else if (match_option(option, "-Xshare:on", &tail)) {
      FLAG_SET_CMDLINE(bool, UseSharedSpaces, true);
      FLAG_SET_CMDLINE(bool, RequireSharedSpaces, true);
#ifdef TIERED
      FLAG_SET_CMDLINE(bool, ForceSharedSpaces, true);
#endif // TIERED
    // -Xshare:auto
    } else if (match_option(option, "-Xshare:auto", &tail)) {
      FLAG_SET_CMDLINE(bool, UseSharedSpaces, true);
      FLAG_SET_CMDLINE(bool, RequireSharedSpaces, false);
    // -Xshare:off
    } else if (match_option(option, "-Xshare:off", &tail)) {
      FLAG_SET_CMDLINE(bool, UseSharedSpaces, false);
      FLAG_SET_CMDLINE(bool, RequireSharedSpaces, false);

    // -Xverify
    } else if (match_option(option, "-Xverify", &tail)) {
      if (strcmp(tail, ":all") == 0 || strcmp(tail, "") == 0) {
        FLAG_SET_CMDLINE(bool, BytecodeVerificationLocal, true);
        FLAG_SET_CMDLINE(bool, BytecodeVerificationRemote, true);
      } else if (strcmp(tail, ":remote") == 0) {
        FLAG_SET_CMDLINE(bool, BytecodeVerificationLocal, false);
        FLAG_SET_CMDLINE(bool, BytecodeVerificationRemote, true);
      } else if (strcmp(tail, ":none") == 0) {
        FLAG_SET_CMDLINE(bool, BytecodeVerificationLocal, false);
        FLAG_SET_CMDLINE(bool, BytecodeVerificationRemote, false);
      } else if (is_bad_option(option, args->ignoreUnrecognized, "verification")) {
        return JNI_EINVAL;
      }
    // -Xdebug
    } else if (match_option(option, "-Xdebug", &tail)) {
      // note this flag has been used, then ignore
      set_xdebug_mode(true);
    // -Xnoagent
    } else if (match_option(option, "-Xnoagent", &tail)) {
      // For compatibility with classic. HotSpot refuses to load the old style agent.dll.
    } else if (match_option(option, "-Xboundthreads", &tail)) {
      // Bind user level threads to kernel threads (Solaris only)
      FLAG_SET_CMDLINE(bool, UseBoundThreads, true);
    } else if (match_option(option, "-Xloggc:", &tail)) {
      // Redirect GC output to the file. -Xloggc:<filename>
      // ostream_init_log(), when called will use this filename
      // to initialize a fileStream.
      _gc_log_filename = strdup(tail);
      FLAG_SET_CMDLINE(bool, PrintGC, true);
      FLAG_SET_CMDLINE(bool, PrintGCTimeStamps, true);
      FLAG_SET_CMDLINE(bool, TraceClassUnloading, true);

    // JNI hooks
    } else if (match_option(option, "-Xcheck", &tail)) {
      if (!strcmp(tail, ":jni")) {
        CheckJNICalls = true;
      } else if (is_bad_option(option, args->ignoreUnrecognized,
                                     "check")) {
        return JNI_EINVAL;
      }
    } else if (match_option(option, "vfprintf", &tail)) {
      _vfprintf_hook = CAST_TO_FN_PTR(vfprintf_hook_t, option->extraInfo);
    } else if (match_option(option, "exit", &tail)) {
      _exit_hook = CAST_TO_FN_PTR(exit_hook_t, option->extraInfo);
    } else if (match_option(option, "abort", &tail)) {
      _abort_hook = CAST_TO_FN_PTR(abort_hook_t, option->extraInfo);
    // -XX:+AggressiveHeap
    } else if (match_option(option, "-XX:+AggressiveHeap", &tail)) {

      // This option inspects the machine and attempts to set various
      // parameters to be optimal for long-running, memory allocation
      // intensive jobs.  It is intended for machines with large
      // amounts of cpu and memory.

      // initHeapSize is needed since _initial_heap_size is 4 bytes on a 32 bit
      // VM, but we may not be able to represent the total physical memory
      // available (like having 8gb of memory on a box but using a 32bit VM).
      // Thus, we need to make sure we're using a julong for intermediate
      // calculations.
      julong initHeapSize;
      julong total_memory = os::physical_memory();

      if (total_memory < (julong)256*M) {
        jio_fprintf(defaultStream::error_stream(),
                    "You need at least 256mb of memory to use -XX:+AggressiveHeap\n");
        vm_exit(1);
      }

      // The heap size is half of available memory, or (at most)
      // all of possible memory less 160mb (leaving room for the OS
      // when using ISM).  This is the maximum; because adaptive sizing
      // is turned on below, the actual space used may be smaller.

      initHeapSize = MIN2(total_memory / (julong)2,
                          total_memory - (julong)160*M);

      // Make sure that if we have a lot of memory we cap the 32 bit
      // process space.  The 64bit VM version of this function is a nop.
      initHeapSize = os::allocatable_physical_memory(initHeapSize);

      // The perm gen is separate but contiguous with the
      // object heap (and is reserved with it) so subtract it
      // from the heap size.
      if (initHeapSize > MaxPermSize) {
        initHeapSize = initHeapSize - MaxPermSize;
      } else {
        warning("AggressiveHeap and MaxPermSize values may conflict");
      }

      if (FLAG_IS_DEFAULT(MaxHeapSize)) {
         FLAG_SET_CMDLINE(uintx, MaxHeapSize, initHeapSize);
         FLAG_SET_CMDLINE(uintx, InitialHeapSize, initHeapSize);
         // Currently the minimum size and the initial heap sizes are the same.
         set_min_heap_size(initHeapSize);
      }
      if (FLAG_IS_DEFAULT(NewSize)) {
         // Make the young generation 3/8ths of the total heap.
         FLAG_SET_CMDLINE(uintx, NewSize,
                                ((julong)MaxHeapSize / (julong)8) * (julong)3);
         FLAG_SET_CMDLINE(uintx, MaxNewSize, NewSize);
      }

      FLAG_SET_DEFAULT(UseLargePages, true);

      // Increase some data structure sizes for efficiency
      FLAG_SET_CMDLINE(uintx, BaseFootPrintEstimate, MaxHeapSize);
      FLAG_SET_CMDLINE(bool, ResizeTLAB, false);
      FLAG_SET_CMDLINE(uintx, TLABSize, 256*K);

      // See the OldPLABSize comment below, but replace 'after promotion'
      // with 'after copying'.  YoungPLABSize is the size of the survivor
      // space per-gc-thread buffers.  The default is 4kw.
      FLAG_SET_CMDLINE(uintx, YoungPLABSize, 256*K);      // Note: this is in words

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
      FLAG_SET_CMDLINE(uintx, OldPLABSize, 8*K);  // Note: this is in words

      // CompilationPolicyChoice=0 causes the server compiler to adopt
      // a more conservative which-method-do-I-compile policy when one
      // of the counters maintained by the interpreter trips.  The
      // result is reduced startup time and improved specjbb and
      // alacrity performance.  Zero is the default, but we set it
      // explicitly here in case the default changes.
      // See runtime/compilationPolicy.*.
      FLAG_SET_CMDLINE(intx, CompilationPolicyChoice, 0);

      // Enable parallel GC and adaptive generation sizing
      FLAG_SET_CMDLINE(bool, UseParallelGC, true);
      FLAG_SET_DEFAULT(ParallelGCThreads,
                       Abstract_VM_Version::parallel_worker_threads());

      // Encourage steady state memory management
      FLAG_SET_CMDLINE(uintx, ThresholdTolerance, 100);

      // This appears to improve mutator locality
      FLAG_SET_CMDLINE(bool, ScavengeBeforeFullGC, false);

      // Get around early Solaris scheduling bug
      // (affinity vs other jobs on system)
      // but disallow DR and offlining (5008695).
      FLAG_SET_CMDLINE(bool, BindGCTaskThreadsToCPUs, true);

    } else if (match_option(option, "-XX:+NeverTenure", &tail)) {
      // The last option must always win.
      FLAG_SET_CMDLINE(bool, AlwaysTenure, false);
      FLAG_SET_CMDLINE(bool, NeverTenure, true);
    } else if (match_option(option, "-XX:+AlwaysTenure", &tail)) {
      // The last option must always win.
      FLAG_SET_CMDLINE(bool, NeverTenure, false);
      FLAG_SET_CMDLINE(bool, AlwaysTenure, true);
    } else if (match_option(option, "-XX:+CMSPermGenSweepingEnabled", &tail) ||
               match_option(option, "-XX:-CMSPermGenSweepingEnabled", &tail)) {
      jio_fprintf(defaultStream::error_stream(),
        "Please use CMSClassUnloadingEnabled in place of "
        "CMSPermGenSweepingEnabled in the future\n");
    } else if (match_option(option, "-XX:+UseGCTimeLimit", &tail)) {
      FLAG_SET_CMDLINE(bool, UseGCOverheadLimit, true);
      jio_fprintf(defaultStream::error_stream(),
        "Please use -XX:+UseGCOverheadLimit in place of "
        "-XX:+UseGCTimeLimit in the future\n");
    } else if (match_option(option, "-XX:-UseGCTimeLimit", &tail)) {
      FLAG_SET_CMDLINE(bool, UseGCOverheadLimit, false);
      jio_fprintf(defaultStream::error_stream(),
        "Please use -XX:-UseGCOverheadLimit in place of "
        "-XX:-UseGCTimeLimit in the future\n");
    // The TLE options are for compatibility with 1.3 and will be
    // removed without notice in a future release.  These options
    // are not to be documented.
    } else if (match_option(option, "-XX:MaxTLERatio=", &tail)) {
      // No longer used.
    } else if (match_option(option, "-XX:+ResizeTLE", &tail)) {
      FLAG_SET_CMDLINE(bool, ResizeTLAB, true);
    } else if (match_option(option, "-XX:-ResizeTLE", &tail)) {
      FLAG_SET_CMDLINE(bool, ResizeTLAB, false);
    } else if (match_option(option, "-XX:+PrintTLE", &tail)) {
      FLAG_SET_CMDLINE(bool, PrintTLAB, true);
    } else if (match_option(option, "-XX:-PrintTLE", &tail)) {
      FLAG_SET_CMDLINE(bool, PrintTLAB, false);
    } else if (match_option(option, "-XX:TLEFragmentationRatio=", &tail)) {
      // No longer used.
    } else if (match_option(option, "-XX:TLESize=", &tail)) {
      julong long_tlab_size = 0;
      ArgsRange errcode = parse_memory_size(tail, &long_tlab_size, 1);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid TLAB size: %s\n", option->optionString);
        describe_range_error(errcode);
        return JNI_EINVAL;
      }
      FLAG_SET_CMDLINE(uintx, TLABSize, long_tlab_size);
    } else if (match_option(option, "-XX:TLEThreadRatio=", &tail)) {
      // No longer used.
    } else if (match_option(option, "-XX:+UseTLE", &tail)) {
      FLAG_SET_CMDLINE(bool, UseTLAB, true);
    } else if (match_option(option, "-XX:-UseTLE", &tail)) {
      FLAG_SET_CMDLINE(bool, UseTLAB, false);
SOLARIS_ONLY(
    } else if (match_option(option, "-XX:+UsePermISM", &tail)) {
      warning("-XX:+UsePermISM is obsolete.");
      FLAG_SET_CMDLINE(bool, UseISM, true);
    } else if (match_option(option, "-XX:-UsePermISM", &tail)) {
      FLAG_SET_CMDLINE(bool, UseISM, false);
)
    } else if (match_option(option, "-XX:+DisplayVMOutputToStderr", &tail)) {
      FLAG_SET_CMDLINE(bool, DisplayVMOutputToStdout, false);
      FLAG_SET_CMDLINE(bool, DisplayVMOutputToStderr, true);
    } else if (match_option(option, "-XX:+DisplayVMOutputToStdout", &tail)) {
      FLAG_SET_CMDLINE(bool, DisplayVMOutputToStderr, false);
      FLAG_SET_CMDLINE(bool, DisplayVMOutputToStdout, true);
    } else if (match_option(option, "-XX:+ExtendedDTraceProbes", &tail)) {
#ifdef SOLARIS
      FLAG_SET_CMDLINE(bool, ExtendedDTraceProbes, true);
      FLAG_SET_CMDLINE(bool, DTraceMethodProbes, true);
      FLAG_SET_CMDLINE(bool, DTraceAllocProbes, true);
      FLAG_SET_CMDLINE(bool, DTraceMonitorProbes, true);
#else // ndef SOLARIS
      jio_fprintf(defaultStream::error_stream(),
                  "ExtendedDTraceProbes flag is only applicable on Solaris\n");
      return JNI_EINVAL;
#endif // ndef SOLARIS
#ifdef ASSERT
    } else if (match_option(option, "-XX:+FullGCALot", &tail)) {
      FLAG_SET_CMDLINE(bool, FullGCALot, true);
      // disable scavenge before parallel mark-compact
      FLAG_SET_CMDLINE(bool, ScavengeBeforeFullGC, false);
#endif
    } else if (match_option(option, "-XX:CMSParPromoteBlocksToClaim=", &tail)) {
      julong cms_blocks_to_claim = (julong)atol(tail);
      FLAG_SET_CMDLINE(uintx, CMSParPromoteBlocksToClaim, cms_blocks_to_claim);
      jio_fprintf(defaultStream::error_stream(),
        "Please use -XX:OldPLABSize in place of "
        "-XX:CMSParPromoteBlocksToClaim in the future\n");
    } else if (match_option(option, "-XX:ParCMSPromoteBlocksToClaim=", &tail)) {
      julong cms_blocks_to_claim = (julong)atol(tail);
      FLAG_SET_CMDLINE(uintx, CMSParPromoteBlocksToClaim, cms_blocks_to_claim);
      jio_fprintf(defaultStream::error_stream(),
        "Please use -XX:OldPLABSize in place of "
        "-XX:ParCMSPromoteBlocksToClaim in the future\n");
    } else if (match_option(option, "-XX:ParallelGCOldGenAllocBufferSize=", &tail)) {
      julong old_plab_size = 0;
      ArgsRange errcode = parse_memory_size(tail, &old_plab_size, 1);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid old PLAB size: %s\n", option->optionString);
        describe_range_error(errcode);
        return JNI_EINVAL;
      }
      FLAG_SET_CMDLINE(uintx, OldPLABSize, old_plab_size);
      jio_fprintf(defaultStream::error_stream(),
                  "Please use -XX:OldPLABSize in place of "
                  "-XX:ParallelGCOldGenAllocBufferSize in the future\n");
    } else if (match_option(option, "-XX:ParallelGCToSpaceAllocBufferSize=", &tail)) {
      julong young_plab_size = 0;
      ArgsRange errcode = parse_memory_size(tail, &young_plab_size, 1);
      if (errcode != arg_in_range) {
        jio_fprintf(defaultStream::error_stream(),
                    "Invalid young PLAB size: %s\n", option->optionString);
        describe_range_error(errcode);
        return JNI_EINVAL;
      }
      FLAG_SET_CMDLINE(uintx, YoungPLABSize, young_plab_size);
      jio_fprintf(defaultStream::error_stream(),
                  "Please use -XX:YoungPLABSize in place of "
                  "-XX:ParallelGCToSpaceAllocBufferSize in the future\n");
    } else if (match_option(option, "-XX:", &tail)) { // -XX:xxxx
      // Skip -XX:Flags= since that case has already been handled
      if (strncmp(tail, "Flags=", strlen("Flags=")) != 0) {
        if (!process_argument(tail, args->ignoreUnrecognized, origin)) {
          return JNI_EINVAL;
        }
      }
    // Unknown option
    } else if (is_bad_option(option, args->ignoreUnrecognized)) {
      return JNI_ERR;
    }
  }
  // Change the default value for flags  which have different default values
  // when working with older JDKs.
  if (JDK_Version::current().compare_major(6) <= 0 &&
      FLAG_IS_DEFAULT(UseVMInterruptibleIO)) {
    FLAG_SET_DEFAULT(UseVMInterruptibleIO, true);
  }
  return JNI_OK;
}

jint Arguments::finalize_vm_init_args(SysClassPath* scp_p, bool scp_assembly_required) {
  // This must be done after all -D arguments have been processed.
  scp_p->expand_endorsed();

  if (scp_assembly_required || scp_p->get_endorsed() != NULL) {
    // Assemble the bootclasspath elements into the final path.
    Arguments::set_sysclasspath(scp_p->combined_path());
  }

  // This must be done after all arguments have been processed.
  // java_compiler() true means set to "NONE" or empty.
  if (java_compiler() && !xdebug_mode()) {
    // For backwards compatibility, we switch to interpreted mode if
    // -Djava.compiler="NONE" or "" is specified AND "-Xdebug" was
    // not specified.
    set_mode_flags(_int);
  }
  if (CompileThreshold == 0) {
    set_mode_flags(_int);
  }

#ifdef TIERED
  // If we are using tiered compilation in the tiered vm then c1 will
  // do the profiling and we don't want to waste that time in the
  // interpreter.
  if (TieredCompilation) {
    ProfileInterpreter = false;
  } else {
    // Since we are running vanilla server we must adjust the compile threshold
    // unless the user has already adjusted it because the default threshold assumes
    // we will run tiered.

    if (FLAG_IS_DEFAULT(CompileThreshold)) {
      CompileThreshold = Tier2CompileThreshold;
    }
  }
#endif // TIERED

#ifndef COMPILER2
  // Don't degrade server performance for footprint
  if (FLAG_IS_DEFAULT(UseLargePages) &&
      MaxHeapSize < LargePageHeapSizeThreshold) {
    // No need for large granularity pages w/small heaps.
    // Note that large pages are enabled/disabled for both the
    // Java heap and the code cache.
    FLAG_SET_DEFAULT(UseLargePages, false);
    SOLARIS_ONLY(FLAG_SET_DEFAULT(UseMPSS, false));
    SOLARIS_ONLY(FLAG_SET_DEFAULT(UseISM, false));
  }

#else
  if (!FLAG_IS_DEFAULT(OptoLoopAlignment) && FLAG_IS_DEFAULT(MaxLoopPad)) {
    FLAG_SET_DEFAULT(MaxLoopPad, OptoLoopAlignment-1);
  }
  // Temporary disable bulk zeroing reduction with G1. See CR 6627983.
  if (UseG1GC) {
    FLAG_SET_DEFAULT(ReduceBulkZeroing, false);
  }
#endif

  if (!check_vm_args_consistency()) {
    return JNI_ERR;
  }

  return JNI_OK;
}

jint Arguments::parse_java_options_environment_variable(SysClassPath* scp_p, bool* scp_assembly_required_p) {
  return parse_options_environment_variable("_JAVA_OPTIONS", scp_p,
                                            scp_assembly_required_p);
}

jint Arguments::parse_java_tool_options_environment_variable(SysClassPath* scp_p, bool* scp_assembly_required_p) {
  return parse_options_environment_variable("JAVA_TOOL_OPTIONS", scp_p,
                                            scp_assembly_required_p);
}

jint Arguments::parse_options_environment_variable(const char* name, SysClassPath* scp_p, bool* scp_assembly_required_p) {
  const int N_MAX_OPTIONS = 64;
  const int OPTION_BUFFER_SIZE = 1024;
  char buffer[OPTION_BUFFER_SIZE];

  // The variable will be ignored if it exceeds the length of the buffer.
  // Don't check this variable if user has special privileges
  // (e.g. unix su command).
  if (os::getenv(name, buffer, sizeof(buffer)) &&
      !os::have_special_privileges()) {
    JavaVMOption options[N_MAX_OPTIONS];      // Construct option array
    jio_fprintf(defaultStream::error_stream(),
                "Picked up %s: %s\n", name, buffer);
    char* rd = buffer;                        // pointer to the input string (rd)
    int i;
    for (i = 0; i < N_MAX_OPTIONS;) {         // repeat for all options in the input string
      while (isspace(*rd)) rd++;              // skip whitespace
      if (*rd == 0) break;                    // we re done when the input string is read completely

      // The output, option string, overwrites the input string.
      // Because of quoting, the pointer to the option string (wrt) may lag the pointer to
      // input string (rd).
      char* wrt = rd;

      options[i++].optionString = wrt;        // Fill in option
      while (*rd != 0 && !isspace(*rd)) {     // unquoted strings terminate with a space or NULL
        if (*rd == '\'' || *rd == '"') {      // handle a quoted string
          int quote = *rd;                    // matching quote to look for
          rd++;                               // don't copy open quote
          while (*rd != quote) {              // include everything (even spaces) up until quote
            if (*rd == 0) {                   // string termination means unmatched string
              jio_fprintf(defaultStream::error_stream(),
                          "Unmatched quote in %s\n", name);
              return JNI_ERR;
            }
            *wrt++ = *rd++;                   // copy to option string
          }
          rd++;                               // don't copy close quote
        } else {
          *wrt++ = *rd++;                     // copy to option string
        }
      }
      // Need to check if we're done before writing a NULL,
      // because the write could be to the byte that rd is pointing to.
      if (*rd++ == 0) {
        *wrt = 0;
        break;
      }
      *wrt = 0;                               // Zero terminate option
    }
    // Construct JavaVMInitArgs structure and parse as if it was part of the command line
    JavaVMInitArgs vm_args;
    vm_args.version = JNI_VERSION_1_2;
    vm_args.options = options;
    vm_args.nOptions = i;
    vm_args.ignoreUnrecognized = IgnoreUnrecognizedVMOptions;

    if (PrintVMOptions) {
      const char* tail;
      for (int i = 0; i < vm_args.nOptions; i++) {
        const JavaVMOption *option = vm_args.options + i;
        if (match_option(option, "-XX:", &tail)) {
          logOption(tail);
        }
      }
    }

    return(parse_each_vm_init_arg(&vm_args, scp_p, scp_assembly_required_p, ENVIRON_VAR));
  }
  return JNI_OK;
}

// Parse entry point called from JNI_CreateJavaVM

jint Arguments::parse(const JavaVMInitArgs* args) {

  // Sharing support
  // Construct the path to the archive
  char jvm_path[JVM_MAXPATHLEN];
  os::jvm_path(jvm_path, sizeof(jvm_path));
#ifdef TIERED
  if (strstr(jvm_path, "client") != NULL) {
    force_client_mode = true;
  }
#endif // TIERED
  char *end = strrchr(jvm_path, *os::file_separator());
  if (end != NULL) *end = '\0';
  char *shared_archive_path = NEW_C_HEAP_ARRAY(char, strlen(jvm_path) +
                                        strlen(os::file_separator()) + 20);
  if (shared_archive_path == NULL) return JNI_ENOMEM;
  strcpy(shared_archive_path, jvm_path);
  strcat(shared_archive_path, os::file_separator());
  strcat(shared_archive_path, "classes");
  DEBUG_ONLY(strcat(shared_archive_path, "_g");)
  strcat(shared_archive_path, ".jsa");
  SharedArchivePath = shared_archive_path;

  // Remaining part of option string
  const char* tail;

  // If flag "-XX:Flags=flags-file" is used it will be the first option to be processed.
  bool settings_file_specified = false;
  const char* flags_file;
  int index;
  for (index = 0; index < args->nOptions; index++) {
    const JavaVMOption *option = args->options + index;
    if (match_option(option, "-XX:Flags=", &tail)) {
      flags_file = tail;
      settings_file_specified = true;
    }
    if (match_option(option, "-XX:+PrintVMOptions", &tail)) {
      PrintVMOptions = true;
    }
    if (match_option(option, "-XX:-PrintVMOptions", &tail)) {
      PrintVMOptions = false;
    }
    if (match_option(option, "-XX:+IgnoreUnrecognizedVMOptions", &tail)) {
      IgnoreUnrecognizedVMOptions = true;
    }
    if (match_option(option, "-XX:-IgnoreUnrecognizedVMOptions", &tail)) {
      IgnoreUnrecognizedVMOptions = false;
    }
  }

  if (IgnoreUnrecognizedVMOptions) {
    // uncast const to modify the flag args->ignoreUnrecognized
    *(jboolean*)(&args->ignoreUnrecognized) = true;
  }

  // Parse specified settings file
  if (settings_file_specified) {
    if (!process_settings_file(flags_file, true, args->ignoreUnrecognized)) {
      return JNI_EINVAL;
    }
  }

  // Parse default .hotspotrc settings file
  if (!settings_file_specified) {
    if (!process_settings_file(".hotspotrc", false, args->ignoreUnrecognized)) {
      return JNI_EINVAL;
    }
  }

  if (PrintVMOptions) {
    for (index = 0; index < args->nOptions; index++) {
      const JavaVMOption *option = args->options + index;
      if (match_option(option, "-XX:", &tail)) {
        logOption(tail);
      }
    }
  }

  // Parse JavaVMInitArgs structure passed in, as well as JAVA_TOOL_OPTIONS and _JAVA_OPTIONS
  jint result = parse_vm_init_args(args);
  if (result != JNI_OK) {
    return result;
  }

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

  if (EnableInvokeDynamic && !EnableMethodHandles) {
    if (!FLAG_IS_DEFAULT(EnableMethodHandles)) {
      warning("forcing EnableMethodHandles true because EnableInvokeDynamic is true");
    }
    EnableMethodHandles = true;
  }
  if (EnableMethodHandles && !AnonymousClasses) {
    if (!FLAG_IS_DEFAULT(AnonymousClasses)) {
      warning("forcing AnonymousClasses true because EnableMethodHandles is true");
    }
    AnonymousClasses = true;
  }
  if ((EnableMethodHandles || AnonymousClasses) && ScavengeRootsInCode == 0) {
    if (!FLAG_IS_DEFAULT(ScavengeRootsInCode)) {
      warning("forcing ScavengeRootsInCode non-zero because EnableMethodHandles or AnonymousClasses is true");
    }
    ScavengeRootsInCode = 1;
  }

  if (PrintGCDetails) {
    // Turn on -verbose:gc options as well
    PrintGC = true;
    if (FLAG_IS_DEFAULT(TraceClassUnloading)) {
      TraceClassUnloading = true;
    }
  }

#if defined(_LP64) && defined(COMPILER1)
  UseCompressedOops = false;
#endif

#ifdef SERIALGC
  force_serial_gc();
#endif // SERIALGC
#ifdef KERNEL
  no_shared_spaces();
#endif // KERNEL

  // Set flags based on ergonomics.
  set_ergonomics_flags();

  // Check the GC selections again.
  if (!check_gc_consistency()) {
    return JNI_EINVAL;
  }

#ifndef KERNEL
  if (UseConcMarkSweepGC) {
    // Set flags for CMS and ParNew.  Check UseConcMarkSweep first
    // to ensure that when both UseConcMarkSweepGC and UseParNewGC
    // are true, we don't call set_parnew_gc_flags() as well.
    set_cms_and_parnew_gc_flags();
  } else {
    // Set heap size based on available physical memory
    set_heap_size();
    // Set per-collector flags
    if (UseParallelGC || UseParallelOldGC) {
      set_parallel_gc_flags();
    } else if (UseParNewGC) {
      set_parnew_gc_flags();
    } else if (UseG1GC) {
      set_g1_gc_flags();
    }
  }
#endif // KERNEL

#ifdef SERIALGC
  assert(verify_serial_gc_flags(), "SerialGC unset");
#endif // SERIALGC

  // Set bytecode rewriting flags
  set_bytecode_flags();

  // Set flags if Aggressive optimization flags (-XX:+AggressiveOpts) enabled.
  set_aggressive_opts_flags();

#ifdef CC_INTERP
  // Biased locking is not implemented with c++ interpreter
  FLAG_SET_DEFAULT(UseBiasedLocking, false);
#endif /* CC_INTERP */

#ifdef COMPILER2
  if (!UseBiasedLocking || EmitSync != 0) {
    UseOptoBiasInlining = false;
  }
#endif

  if (PrintCommandLineFlags) {
    CommandLineFlags::printSetFlags();
  }

#ifdef ASSERT
  if (PrintFlagsFinal) {
    CommandLineFlags::printFlags();
  }
#endif

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

void Arguments::PropertyList_add(SystemProperty** plist, const char* k, char* v) {
  if (plist == NULL)
    return;

  SystemProperty* new_p = new SystemProperty(k, v, true);
  PropertyList_add(plist, new_p);
}

// This add maintains unique property key in the list.
void Arguments::PropertyList_unique_add(SystemProperty** plist, const char* k, char* v, jboolean append) {
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

#ifdef KERNEL
char *Arguments::get_kernel_properties() {
  // Find properties starting with kernel and append them to string
  // We need to find out how long they are first because the URL's that they
  // might point to could get long.
  int length = 0;
  SystemProperty* prop;
  for (prop = _system_properties; prop != NULL; prop = prop->next()) {
    if (strncmp(prop->key(), "kernel.", 7 ) == 0) {
      length += (strlen(prop->key()) + strlen(prop->value()) + 5);  // "-D ="
    }
  }
  // Add one for null terminator.
  char *props = AllocateHeap(length + 1, "get_kernel_properties");
  if (length != 0) {
    int pos = 0;
    for (prop = _system_properties; prop != NULL; prop = prop->next()) {
      if (strncmp(prop->key(), "kernel.", 7 ) == 0) {
        jio_snprintf(&props[pos], length-pos,
                     "-D%s=%s ", prop->key(), prop->value());
        pos = strlen(props);
      }
    }
  }
  // null terminate props in case of null
  props[length] = '\0';
  return props;
}
#endif // KERNEL

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
