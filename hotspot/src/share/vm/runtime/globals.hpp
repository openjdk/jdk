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

#ifndef SHARE_VM_RUNTIME_GLOBALS_HPP
#define SHARE_VM_RUNTIME_GLOBALS_HPP

#include "utilities/debug.hpp"

// use this for flags that are true per default in the tiered build
// but false in non-tiered builds, and vice versa
#ifdef TIERED
#define  trueInTiered true
#define falseInTiered false
#else
#define  trueInTiered false
#define falseInTiered true
#endif

#ifdef TARGET_ARCH_x86
# include "globals_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "globals_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "globals_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "globals_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "globals_ppc.hpp"
#endif
#ifdef TARGET_ARCH_aarch64
# include "globals_aarch64.hpp"
#endif
#ifdef TARGET_OS_FAMILY_linux
# include "globals_linux.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "globals_solaris.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "globals_windows.hpp"
#endif
#ifdef TARGET_OS_FAMILY_aix
# include "globals_aix.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "globals_bsd.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_x86
# include "globals_linux_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_sparc
# include "globals_linux_sparc.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_zero
# include "globals_linux_zero.hpp"
#endif
#ifdef TARGET_OS_ARCH_solaris_x86
# include "globals_solaris_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_solaris_sparc
# include "globals_solaris_sparc.hpp"
#endif
#ifdef TARGET_OS_ARCH_windows_x86
# include "globals_windows_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_arm
# include "globals_linux_arm.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_ppc
# include "globals_linux_ppc.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_aarch64
# include "globals_linux_aarch64.hpp"
#endif
#ifdef TARGET_OS_ARCH_aix_ppc
# include "globals_aix_ppc.hpp"
#endif
#ifdef TARGET_OS_ARCH_bsd_x86
# include "globals_bsd_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_bsd_zero
# include "globals_bsd_zero.hpp"
#endif
#ifdef COMPILER1
#ifdef TARGET_ARCH_x86
# include "c1_globals_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "c1_globals_sparc.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "c1_globals_arm.hpp"
#endif
#ifdef TARGET_ARCH_aarch64
# include "c1_globals_aarch64.hpp"
#endif
#ifdef TARGET_OS_FAMILY_linux
# include "c1_globals_linux.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "c1_globals_solaris.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "c1_globals_windows.hpp"
#endif
#ifdef TARGET_OS_FAMILY_aix
# include "c1_globals_aix.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "c1_globals_bsd.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "c1_globals_ppc.hpp"
#endif
#endif
#ifdef COMPILER2
#ifdef TARGET_ARCH_x86
# include "c2_globals_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "c2_globals_sparc.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "c2_globals_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "c2_globals_ppc.hpp"
#endif
#ifdef TARGET_ARCH_aarch64
# include "c2_globals_aarch64.hpp"
#endif
#ifdef TARGET_OS_FAMILY_linux
# include "c2_globals_linux.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "c2_globals_solaris.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "c2_globals_windows.hpp"
#endif
#ifdef TARGET_OS_FAMILY_aix
# include "c2_globals_aix.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "c2_globals_bsd.hpp"
#endif
#endif
#ifdef SHARK
#ifdef TARGET_ARCH_zero
# include "shark_globals_zero.hpp"
#endif
#endif

#if !defined(COMPILER1) && !defined(COMPILER2) && !defined(SHARK)
define_pd_global(bool, BackgroundCompilation,        false);
define_pd_global(bool, UseTLAB,                      false);
define_pd_global(bool, CICompileOSR,                 false);
define_pd_global(bool, UseTypeProfile,               false);
define_pd_global(bool, UseOnStackReplacement,        false);
define_pd_global(bool, InlineIntrinsics,             false);
define_pd_global(bool, PreferInterpreterNativeStubs, true);
define_pd_global(bool, ProfileInterpreter,           false);
define_pd_global(bool, ProfileTraps,                 false);
define_pd_global(bool, TieredCompilation,            false);

define_pd_global(intx, CompileThreshold,             0);

define_pd_global(intx, OnStackReplacePercentage,     0);
define_pd_global(bool, ResizeTLAB,                   false);
define_pd_global(intx, FreqInlineSize,               0);
define_pd_global(size_t, NewSizeThreadIncrease,      4*K);
define_pd_global(intx, InlineClassNatives,           true);
define_pd_global(intx, InlineUnsafeOps,              true);
define_pd_global(intx, InitialCodeCacheSize,         160*K);
define_pd_global(intx, ReservedCodeCacheSize,        32*M);
define_pd_global(intx, NonProfiledCodeHeapSize,      0);
define_pd_global(intx, ProfiledCodeHeapSize,         0);
define_pd_global(intx, NonNMethodCodeHeapSize,       32*M);

define_pd_global(intx, CodeCacheExpansionSize,       32*K);
define_pd_global(intx, CodeCacheMinBlockLength,      1);
define_pd_global(intx, CodeCacheMinimumUseSpace,     200*K);
define_pd_global(size_t, MetaspaceSize,              ScaleForWordSize(4*M));
define_pd_global(bool, NeverActAsServerClassMachine, true);
define_pd_global(uint64_t,MaxRAM,                    1ULL*G);
#define CI_COMPILER_COUNT 0
#else

#ifdef COMPILER2
#define CI_COMPILER_COUNT 2
#else
#define CI_COMPILER_COUNT 1
#endif // COMPILER2

#endif // no compilers

// string type aliases used only in this file
typedef const char* ccstr;
typedef const char* ccstrlist;   // represents string arguments which accumulate

struct Flag {
  enum Flags {
    // value origin
    DEFAULT          = 0,
    COMMAND_LINE     = 1,
    ENVIRON_VAR      = 2,
    CONFIG_FILE      = 3,
    MANAGEMENT       = 4,
    ERGONOMIC        = 5,
    ATTACH_ON_DEMAND = 6,
    INTERNAL         = 7,

    LAST_VALUE_ORIGIN = INTERNAL,
    VALUE_ORIGIN_BITS = 4,
    VALUE_ORIGIN_MASK = right_n_bits(VALUE_ORIGIN_BITS),

    // flag kind
    KIND_PRODUCT            = 1 << 4,
    KIND_MANAGEABLE         = 1 << 5,
    KIND_DIAGNOSTIC         = 1 << 6,
    KIND_EXPERIMENTAL       = 1 << 7,
    KIND_NOT_PRODUCT        = 1 << 8,
    KIND_DEVELOP            = 1 << 9,
    KIND_PLATFORM_DEPENDENT = 1 << 10,
    KIND_READ_WRITE         = 1 << 11,
    KIND_C1                 = 1 << 12,
    KIND_C2                 = 1 << 13,
    KIND_ARCH               = 1 << 14,
    KIND_SHARK              = 1 << 15,
    KIND_LP64_PRODUCT       = 1 << 16,
    KIND_COMMERCIAL         = 1 << 17,

    KIND_MASK = ~VALUE_ORIGIN_MASK
  };

  enum Error {
    // no error
    SUCCESS = 0,
    // flag name is missing
    MISSING_NAME,
    // flag value is missing
    MISSING_VALUE,
    // error parsing the textual form of the value
    WRONG_FORMAT,
    // flag is not writeable
    NON_WRITABLE,
    // flag value is outside of its bounds
    OUT_OF_BOUNDS,
    // flag value violates its constraint
    VIOLATES_CONSTRAINT,
    // there is no flag with the given name
    INVALID_FLAG,
    // other, unspecified error related to setting the flag
    ERR_OTHER
  };

  const char* _type;
  const char* _name;
  void* _addr;
  NOT_PRODUCT(const char* _doc;)
  Flags _flags;

  // points to all Flags static array
  static Flag* flags;

  // number of flags
  static size_t numFlags;

  static Flag* find_flag(const char* name) { return find_flag(name, strlen(name), true, true); };
  static Flag* find_flag(const char* name, size_t length, bool allow_locked = false, bool return_flag = false);
  static Flag* fuzzy_match(const char* name, size_t length, bool allow_locked = false);

  void check_writable();

  bool is_bool() const;
  bool get_bool() const;
  void set_bool(bool value);

  bool is_int() const;
  int get_int() const;
  void set_int(int value);

  bool is_uint() const;
  uint get_uint() const;
  void set_uint(uint value);

  bool is_intx() const;
  intx get_intx() const;
  void set_intx(intx value);

  bool is_uintx() const;
  uintx get_uintx() const;
  void set_uintx(uintx value);

  bool is_uint64_t() const;
  uint64_t get_uint64_t() const;
  void set_uint64_t(uint64_t value);

  bool is_size_t() const;
  size_t get_size_t() const;
  void set_size_t(size_t value);

  bool is_double() const;
  double get_double() const;
  void set_double(double value);

  bool is_ccstr() const;
  bool ccstr_accumulates() const;
  ccstr get_ccstr() const;
  void set_ccstr(ccstr value);

  Flags get_origin();
  void set_origin(Flags origin);

  bool is_default();
  bool is_ergonomic();
  bool is_command_line();

  bool is_product() const;
  bool is_manageable() const;
  bool is_diagnostic() const;
  bool is_experimental() const;
  bool is_notproduct() const;
  bool is_develop() const;
  bool is_read_write() const;
  bool is_commercial() const;

  bool is_constant_in_binary() const;

  bool is_unlocker() const;
  bool is_unlocked() const;
  bool is_writeable() const;
  bool is_external() const;

  bool is_unlocker_ext() const;
  bool is_unlocked_ext() const;
  bool is_writeable_ext() const;
  bool is_external_ext() const;

  void unlock_diagnostic();

  void get_locked_message(char*, int) const;
  void get_locked_message_ext(char*, int) const;

  // printRanges will print out flags type, name and range values as expected by -XX:+PrintFlagsRanges
  void print_on(outputStream* st, bool withComments = false, bool printRanges = false);
  void print_kind(outputStream* st);
  void print_as_flag(outputStream* st);

  static const char* flag_error_str(Flag::Error error);
};

// debug flags control various aspects of the VM and are global accessible

// use FlagSetting to temporarily change some debug flag
// e.g. FlagSetting fs(DebugThisAndThat, true);
// restored to previous value upon leaving scope
class FlagSetting {
  bool val;
  bool* flag;
 public:
  FlagSetting(bool& fl, bool newValue) { flag = &fl; val = fl; fl = newValue; }
  ~FlagSetting()                       { *flag = val; }
};


class CounterSetting {
  intx* counter;
 public:
  CounterSetting(intx* cnt) { counter = cnt; (*counter)++; }
  ~CounterSetting()         { (*counter)--; }
};

class IntFlagSetting {
  int val;
  int* flag;
 public:
  IntFlagSetting(int& fl, int newValue) { flag = &fl; val = fl; fl = newValue; }
  ~IntFlagSetting()                     { *flag = val; }
};

class UIntFlagSetting {
  uint val;
  uint* flag;
 public:
  UIntFlagSetting(uint& fl, uint newValue) { flag = &fl; val = fl; fl = newValue; }
  ~UIntFlagSetting()                       { *flag = val; }
};

class UIntXFlagSetting {
  uintx val;
  uintx* flag;
 public:
  UIntXFlagSetting(uintx& fl, uintx newValue) { flag = &fl; val = fl; fl = newValue; }
  ~UIntXFlagSetting()                         { *flag = val; }
};

class DoubleFlagSetting {
  double val;
  double* flag;
 public:
  DoubleFlagSetting(double& fl, double newValue) { flag = &fl; val = fl; fl = newValue; }
  ~DoubleFlagSetting()                           { *flag = val; }
};

class SizeTFlagSetting {
  size_t val;
  size_t* flag;
 public:
  SizeTFlagSetting(size_t& fl, size_t newValue) { flag = &fl; val = fl; fl = newValue; }
  ~SizeTFlagSetting()                           { *flag = val; }
};


class CommandLineFlags {
public:
  static Flag::Error boolAt(const char* name, size_t len, bool* value, bool allow_locked = false, bool return_flag = false);
  static Flag::Error boolAt(const char* name, bool* value, bool allow_locked = false, bool return_flag = false)      { return boolAt(name, strlen(name), value, allow_locked, return_flag); }
  static Flag::Error boolAtPut(const char* name, size_t len, bool* value, Flag::Flags origin);
  static Flag::Error boolAtPut(const char* name, bool* value, Flag::Flags origin)   { return boolAtPut(name, strlen(name), value, origin); }

  static Flag::Error intAt(const char* name, size_t len, int* value, bool allow_locked = false, bool return_flag = false);
  static Flag::Error intAt(const char* name, int* value, bool allow_locked = false, bool return_flag = false)      { return intAt(name, strlen(name), value, allow_locked, return_flag); }
  static Flag::Error intAtPut(const char* name, size_t len, int* value, Flag::Flags origin);
  static Flag::Error intAtPut(const char* name, int* value, Flag::Flags origin)   { return intAtPut(name, strlen(name), value, origin); }

  static Flag::Error uintAt(const char* name, size_t len, uint* value, bool allow_locked = false, bool return_flag = false);
  static Flag::Error uintAt(const char* name, uint* value, bool allow_locked = false, bool return_flag = false)      { return uintAt(name, strlen(name), value, allow_locked, return_flag); }
  static Flag::Error uintAtPut(const char* name, size_t len, uint* value, Flag::Flags origin);
  static Flag::Error uintAtPut(const char* name, uint* value, Flag::Flags origin)   { return uintAtPut(name, strlen(name), value, origin); }

  static Flag::Error intxAt(const char* name, size_t len, intx* value, bool allow_locked = false, bool return_flag = false);
  static Flag::Error intxAt(const char* name, intx* value, bool allow_locked = false, bool return_flag = false)      { return intxAt(name, strlen(name), value, allow_locked, return_flag); }
  static Flag::Error intxAtPut(const char* name, size_t len, intx* value, Flag::Flags origin);
  static Flag::Error intxAtPut(const char* name, intx* value, Flag::Flags origin)   { return intxAtPut(name, strlen(name), value, origin); }

  static Flag::Error uintxAt(const char* name, size_t len, uintx* value, bool allow_locked = false, bool return_flag = false);
  static Flag::Error uintxAt(const char* name, uintx* value, bool allow_locked = false, bool return_flag = false)    { return uintxAt(name, strlen(name), value, allow_locked, return_flag); }
  static Flag::Error uintxAtPut(const char* name, size_t len, uintx* value, Flag::Flags origin);
  static Flag::Error uintxAtPut(const char* name, uintx* value, Flag::Flags origin) { return uintxAtPut(name, strlen(name), value, origin); }

  static Flag::Error size_tAt(const char* name, size_t len, size_t* value, bool allow_locked = false, bool return_flag = false);
  static Flag::Error size_tAt(const char* name, size_t* value, bool allow_locked = false, bool return_flag = false)    { return size_tAt(name, strlen(name), value, allow_locked, return_flag); }
  static Flag::Error size_tAtPut(const char* name, size_t len, size_t* value, Flag::Flags origin);
  static Flag::Error size_tAtPut(const char* name, size_t* value, Flag::Flags origin) { return size_tAtPut(name, strlen(name), value, origin); }

  static Flag::Error uint64_tAt(const char* name, size_t len, uint64_t* value, bool allow_locked = false, bool return_flag = false);
  static Flag::Error uint64_tAt(const char* name, uint64_t* value, bool allow_locked = false, bool return_flag = false) { return uint64_tAt(name, strlen(name), value, allow_locked, return_flag); }
  static Flag::Error uint64_tAtPut(const char* name, size_t len, uint64_t* value, Flag::Flags origin);
  static Flag::Error uint64_tAtPut(const char* name, uint64_t* value, Flag::Flags origin) { return uint64_tAtPut(name, strlen(name), value, origin); }

  static Flag::Error doubleAt(const char* name, size_t len, double* value, bool allow_locked = false, bool return_flag = false);
  static Flag::Error doubleAt(const char* name, double* value, bool allow_locked = false, bool return_flag = false)    { return doubleAt(name, strlen(name), value, allow_locked, return_flag); }
  static Flag::Error doubleAtPut(const char* name, size_t len, double* value, Flag::Flags origin);
  static Flag::Error doubleAtPut(const char* name, double* value, Flag::Flags origin) { return doubleAtPut(name, strlen(name), value, origin); }

  static Flag::Error ccstrAt(const char* name, size_t len, ccstr* value, bool allow_locked = false, bool return_flag = false);
  static Flag::Error ccstrAt(const char* name, ccstr* value, bool allow_locked = false, bool return_flag = false)    { return ccstrAt(name, strlen(name), value, allow_locked, return_flag); }
  // Contract:  Flag will make private copy of the incoming value.
  // Outgoing value is always malloc-ed, and caller MUST call free.
  static Flag::Error ccstrAtPut(const char* name, size_t len, ccstr* value, Flag::Flags origin);
  static Flag::Error ccstrAtPut(const char* name, ccstr* value, Flag::Flags origin) { return ccstrAtPut(name, strlen(name), value, origin); }

  // Returns false if name is not a command line flag.
  static bool wasSetOnCmdline(const char* name, bool* value);
  static void printSetFlags(outputStream* out);

  // printRanges will print out flags type, name and range values as expected by -XX:+PrintFlagsRanges
  static void printFlags(outputStream* out, bool withComments, bool printRanges = false);

  static void verify() PRODUCT_RETURN;
};

// use this for flags that are true by default in the debug version but
// false in the optimized version, and vice versa
#ifdef ASSERT
#define trueInDebug  true
#define falseInDebug false
#else
#define trueInDebug  false
#define falseInDebug true
#endif

// use this for flags that are true per default in the product build
// but false in development builds, and vice versa
#ifdef PRODUCT
#define trueInProduct  true
#define falseInProduct false
#else
#define trueInProduct  false
#define falseInProduct true
#endif

#ifdef JAVASE_EMBEDDED
#define falseInEmbedded false
#else
#define falseInEmbedded true
#endif

// develop flags are settable / visible only during development and are constant in the PRODUCT version
// product flags are always settable / visible
// notproduct flags are settable / visible only during development and are not declared in the PRODUCT version

// A flag must be declared with one of the following types:
// bool, intx, uintx, size_t, ccstr, double, or uint64_t.
// The type "ccstr" is an alias for "const char*" and is used
// only in this file, because the macrology requires single-token type names.

// Note: Diagnostic options not meant for VM tuning or for product modes.
// They are to be used for VM quality assurance or field diagnosis
// of VM bugs.  They are hidden so that users will not be encouraged to
// try them as if they were VM ordinary execution options.  However, they
// are available in the product version of the VM.  Under instruction
// from support engineers, VM customers can turn them on to collect
// diagnostic information about VM problems.  To use a VM diagnostic
// option, you must first specify +UnlockDiagnosticVMOptions.
// (This master switch also affects the behavior of -Xprintflags.)
//
// experimental flags are in support of features that are not
//    part of the officially supported product, but are available
//    for experimenting with. They could, for example, be performance
//    features that may not have undergone full or rigorous QA, but which may
//    help performance in some cases and released for experimentation
//    by the community of users and developers. This flag also allows one to
//    be able to build a fully supported product that nonetheless also
//    ships with some unsupported, lightly tested, experimental features.
//    Like the UnlockDiagnosticVMOptions flag above, there is a corresponding
//    UnlockExperimentalVMOptions flag, which allows the control and
//    modification of the experimental flags.
//
// Nota bene: neither diagnostic nor experimental options should be used casually,
//    and they are not supported on production loads, except under explicit
//    direction from support engineers.
//
// manageable flags are writeable external product flags.
//    They are dynamically writeable through the JDK management interface
//    (com.sun.management.HotSpotDiagnosticMXBean API) and also through JConsole.
//    These flags are external exported interface (see CCC).  The list of
//    manageable flags can be queried programmatically through the management
//    interface.
//
//    A flag can be made as "manageable" only if
//    - the flag is defined in a CCC as an external exported interface.
//    - the VM implementation supports dynamic setting of the flag.
//      This implies that the VM must *always* query the flag variable
//      and not reuse state related to the flag state at any given time.
//    - you want the flag to be queried programmatically by the customers.
//
// product_rw flags are writeable internal product flags.
//    They are like "manageable" flags but for internal/private use.
//    The list of product_rw flags are internal/private flags which
//    may be changed/removed in a future release.  It can be set
//    through the management interface to get/set value
//    when the name of flag is supplied.
//
//    A flag can be made as "product_rw" only if
//    - the VM implementation supports dynamic setting of the flag.
//      This implies that the VM must *always* query the flag variable
//      and not reuse state related to the flag state at any given time.
//
// Note that when there is a need to support develop flags to be writeable,
// it can be done in the same way as product_rw.
//
// range is a macro that will expand to min and max arguments for range
//    checking code if provided - see commandLineFlagRangeList.hpp
//
// constraint is a macro that will expand to custom function call
//    for constraint checking if provided - see commandLineFlagConstraintList.hpp
//

#define RUNTIME_FLAGS(develop, develop_pd, product, product_pd, diagnostic, experimental, notproduct, manageable, product_rw, lp64_product, range, constraint) \
                                                                            \
  lp64_product(bool, UseCompressedOops, false,                              \
          "Use 32-bit object references in 64-bit VM. "                     \
          "lp64_product means flag is always constant in 32 bit VM")        \
                                                                            \
  lp64_product(bool, UseCompressedClassPointers, false,                     \
          "Use 32-bit class pointers in 64-bit VM. "                        \
          "lp64_product means flag is always constant in 32 bit VM")        \
                                                                            \
  notproduct(bool, CheckCompressedOops, true,                               \
          "Generate checks in encoding/decoding code in debug VM")          \
                                                                            \
  product_pd(size_t, HeapBaseMinAddress,                                    \
          "OS specific low limit for heap base address")                    \
                                                                            \
  product(uintx, HeapSearchSteps, 3 PPC64_ONLY(+17),                        \
          "Heap allocation steps through preferred address regions to find" \
          " where it can allocate the heap. Number of steps to take per "   \
          "region.")                                                        \
          range(1, max_uintx)                                               \
                                                                            \
  diagnostic(bool, PrintCompressedOopsMode, false,                          \
          "Print compressed oops base address and encoding mode")           \
                                                                            \
  lp64_product(intx, ObjectAlignmentInBytes, 8,                             \
          "Default object alignment in bytes, 8 is minimum")                \
          range(8, 256)                                                     \
          constraint(ObjectAlignmentInBytesConstraintFunc,AtParse)          \
                                                                            \
  product(bool, AssumeMP, false,                                            \
          "Instruct the VM to assume multiple processors are available")    \
                                                                            \
  /* UseMembar is theoretically a temp flag used for memory barrier      */ \
  /* removal testing.  It was supposed to be removed before FCS but has  */ \
  /* been re-added (see 6401008)                                         */ \
  product_pd(bool, UseMembar,                                               \
          "(Unstable) Issues membars on thread state transitions")          \
                                                                            \
  develop(bool, CleanChunkPoolAsync, falseInEmbedded,                       \
          "Clean the chunk pool asynchronously")                            \
                                                                            \
  experimental(bool, AlwaysSafeConstructors, false,                         \
          "Force safe construction, as if all fields are final.")           \
                                                                            \
  diagnostic(bool, UnlockDiagnosticVMOptions, trueInDebug,                  \
          "Enable normal processing of flags relating to field diagnostics")\
                                                                            \
  experimental(bool, UnlockExperimentalVMOptions, false,                    \
          "Enable normal processing of flags relating to experimental "     \
          "features")                                                       \
                                                                            \
  product(bool, JavaMonitorsInStackTrace, true,                             \
          "Print information about Java monitor locks when the stacks are"  \
          "dumped")                                                         \
                                                                            \
  product_pd(bool, UseLargePages,                                           \
          "Use large page memory")                                          \
                                                                            \
  product_pd(bool, UseLargePagesIndividualAllocation,                       \
          "Allocate large pages individually for better affinity")          \
                                                                            \
  develop(bool, LargePagesIndividualAllocationInjectError, false,           \
          "Fail large pages individual allocation")                         \
                                                                            \
  product(bool, UseLargePagesInMetaspace, false,                            \
          "Use large page memory in metaspace. "                            \
          "Only used if UseLargePages is enabled.")                         \
                                                                            \
  develop(bool, TracePageSizes, false,                                      \
          "Trace page size selection and usage")                            \
                                                                            \
  product(bool, UseNUMA, false,                                             \
          "Use NUMA if available")                                          \
                                                                            \
  product(bool, UseNUMAInterleaving, false,                                 \
          "Interleave memory across NUMA nodes if available")               \
                                                                            \
  product(size_t, NUMAInterleaveGranularity, 2*M,                           \
          "Granularity to use for NUMA interleaving on Windows OS")         \
                                                                            \
  product(bool, ForceNUMA, false,                                           \
          "Force NUMA optimizations on single-node/UMA systems")            \
                                                                            \
  product(uintx, NUMAChunkResizeWeight, 20,                                 \
          "Percentage (0-100) used to weight the current sample when "      \
          "computing exponentially decaying average for "                   \
          "AdaptiveNUMAChunkSizing")                                        \
          range(0, 100)                                                     \
                                                                            \
  product(size_t, NUMASpaceResizeRate, 1*G,                                 \
          "Do not reallocate more than this amount per collection")         \
                                                                            \
  product(bool, UseAdaptiveNUMAChunkSizing, true,                           \
          "Enable adaptive chunk sizing for NUMA")                          \
                                                                            \
  product(bool, NUMAStats, false,                                           \
          "Print NUMA stats in detailed heap information")                  \
                                                                            \
  product(uintx, NUMAPageScanRate, 256,                                     \
          "Maximum number of pages to include in the page scan procedure")  \
                                                                            \
  product_pd(bool, NeedsDeoptSuspend,                                       \
          "True for register window machines (sparc/ia64)")                 \
                                                                            \
  product(intx, UseSSE, 99,                                                 \
          "Highest supported SSE instructions set on x86/x64")              \
                                                                            \
  product(bool, UseAES, false,                                              \
          "Control whether AES instructions can be used on x86/x64")        \
                                                                            \
  product(bool, UseSHA, false,                                              \
          "Control whether SHA instructions can be used "                   \
          "on SPARC and on ARM")                                            \
                                                                            \
  product(bool, UseGHASHIntrinsics, false,                                  \
          "Use intrinsics for GHASH versions of crypto")                    \
                                                                            \
  product(size_t, LargePageSizeInBytes, 0,                                  \
          "Large page size (0 to let VM choose the page size)")             \
                                                                            \
  product(size_t, LargePageHeapSizeThreshold, 128*M,                        \
          "Use large pages if maximum heap is at least this big")           \
                                                                            \
  product(bool, ForceTimeHighResolution, false,                             \
          "Using high time resolution (for Win32 only)")                    \
                                                                            \
  develop(bool, TraceItables, false,                                        \
          "Trace initialization and use of itables")                        \
                                                                            \
  develop(bool, TracePcPatching, false,                                     \
          "Trace usage of frame::patch_pc")                                 \
                                                                            \
  develop(bool, TraceJumps, false,                                          \
          "Trace assembly jumps in thread ring buffer")                     \
                                                                            \
  develop(bool, TraceRelocator, false,                                      \
          "Trace the bytecode relocator")                                   \
                                                                            \
  develop(bool, TraceLongCompiles, false,                                   \
          "Print out every time compilation is longer than "                \
          "a given threshold")                                              \
                                                                            \
  develop(bool, SafepointALot, false,                                       \
          "Generate a lot of safepoints. This works with "                  \
          "GuaranteedSafepointInterval")                                    \
                                                                            \
  product_pd(bool, BackgroundCompilation,                                   \
          "A thread requesting compilation is not blocked during "          \
          "compilation")                                                    \
                                                                            \
  product(bool, PrintVMQWaitTime, false,                                    \
          "Print out the waiting time in VM operation queue")               \
                                                                            \
  develop(bool, TraceOopMapGeneration, false,                               \
          "Show OopMapGeneration")                                          \
                                                                            \
  product(bool, MethodFlushing, true,                                       \
          "Reclamation of zombie and not-entrant methods")                  \
                                                                            \
  develop(bool, VerifyStack, false,                                         \
          "Verify stack of each thread when it is entering a runtime call") \
                                                                            \
  diagnostic(bool, ForceUnreachable, false,                                 \
          "Make all non code cache addresses to be unreachable by "         \
          "forcing use of 64bit literal fixups")                            \
                                                                            \
  notproduct(bool, StressDerivedPointers, false,                            \
          "Force scavenge when a derived pointer is detected on stack "     \
          "after rtm call")                                                 \
                                                                            \
  develop(bool, TraceDerivedPointers, false,                                \
          "Trace traversal of derived pointers on stack")                   \
                                                                            \
  notproduct(bool, TraceCodeBlobStacks, false,                              \
          "Trace stack-walk of codeblobs")                                  \
                                                                            \
  product(bool, PrintJNIResolving, false,                                   \
          "Used to implement -v:jni")                                       \
                                                                            \
  notproduct(bool, PrintRewrites, false,                                    \
          "Print methods that are being rewritten")                         \
                                                                            \
  product(bool, UseInlineCaches, true,                                      \
          "Use Inline Caches for virtual calls ")                           \
                                                                            \
  develop(bool, InlineArrayCopy, true,                                      \
          "Inline arraycopy native that is known to be part of "            \
          "base library DLL")                                               \
                                                                            \
  develop(bool, InlineObjectHash, true,                                     \
          "Inline Object::hashCode() native that is known to be part "      \
          "of base library DLL")                                            \
                                                                            \
  develop(bool, InlineNatives, true,                                        \
          "Inline natives that are known to be part of base library DLL")   \
                                                                            \
  develop(bool, InlineMathNatives, true,                                    \
          "Inline SinD, CosD, etc.")                                        \
                                                                            \
  develop(bool, InlineClassNatives, true,                                   \
          "Inline Class.isInstance, etc")                                   \
                                                                            \
  develop(bool, InlineThreadNatives, true,                                  \
          "Inline Thread.currentThread, etc")                               \
                                                                            \
  develop(bool, InlineUnsafeOps, true,                                      \
          "Inline memory ops (native methods) from sun.misc.Unsafe")        \
                                                                            \
  product(bool, CriticalJNINatives, true,                                   \
          "Check for critical JNI entry points")                            \
                                                                            \
  notproduct(bool, StressCriticalJNINatives, false,                         \
          "Exercise register saving code in critical natives")              \
                                                                            \
  product(bool, UseSSE42Intrinsics, false,                                  \
          "SSE4.2 versions of intrinsics")                                  \
                                                                            \
  product(bool, UseAESIntrinsics, false,                                    \
          "Use intrinsics for AES versions of crypto")                      \
                                                                            \
  product(bool, UseSHA1Intrinsics, false,                                   \
          "Use intrinsics for SHA-1 crypto hash function. "                 \
          "Requires that UseSHA is enabled.")                               \
                                                                            \
  product(bool, UseSHA256Intrinsics, false,                                 \
          "Use intrinsics for SHA-224 and SHA-256 crypto hash functions. "  \
          "Requires that UseSHA is enabled.")                               \
                                                                            \
  product(bool, UseSHA512Intrinsics, false,                                 \
          "Use intrinsics for SHA-384 and SHA-512 crypto hash functions. "  \
          "Requires that UseSHA is enabled.")                               \
                                                                            \
  product(bool, UseCRC32Intrinsics, false,                                  \
          "use intrinsics for java.util.zip.CRC32")                         \
                                                                            \
  product(bool, UseCRC32CIntrinsics, false,                                 \
          "use intrinsics for java.util.zip.CRC32C")                        \
                                                                            \
  diagnostic(ccstrlist, DisableIntrinsic, "",                               \
         "do not expand intrinsics whose (internal) names appear here")     \
                                                                            \
  develop(bool, TraceCallFixup, false,                                      \
          "Trace all call fixups")                                          \
                                                                            \
  develop(bool, DeoptimizeALot, false,                                      \
          "Deoptimize at every exit from the runtime system")               \
                                                                            \
  notproduct(ccstrlist, DeoptimizeOnlyAt, "",                               \
          "A comma separated list of bcis to deoptimize at")                \
                                                                            \
  product(bool, DeoptimizeRandom, false,                                    \
          "Deoptimize random frames on random exit from the runtime system")\
                                                                            \
  notproduct(bool, ZombieALot, false,                                       \
          "Create zombies (non-entrant) at exit from the runtime system")   \
                                                                            \
  product(bool, UnlinkSymbolsALot, false,                                   \
          "Unlink unreferenced symbols from the symbol table at safepoints")\
                                                                            \
  notproduct(bool, WalkStackALot, false,                                    \
          "Trace stack (no print) at every exit from the runtime system")   \
                                                                            \
  product(bool, Debugging, false,                                           \
          "Set when executing debug methods in debug.cpp "                  \
          "(to prevent triggering assertions)")                             \
                                                                            \
  notproduct(bool, StrictSafepointChecks, trueInDebug,                      \
          "Enable strict checks that safepoints cannot happen for threads " \
          "that use No_Safepoint_Verifier")                                 \
                                                                            \
  notproduct(bool, VerifyLastFrame, false,                                  \
          "Verify oops on last frame on entry to VM")                       \
                                                                            \
  develop(bool, TraceHandleAllocation, false,                               \
          "Print out warnings when suspiciously many handles are allocated")\
                                                                            \
  product(bool, FailOverToOldVerifier, true,                                \
          "Fail over to old verifier when split verifier fails")            \
                                                                            \
  develop(bool, ShowSafepointMsgs, false,                                   \
          "Show message about safepoint synchronization")                   \
                                                                            \
  product(bool, SafepointTimeout, false,                                    \
          "Time out and warn or fail after SafepointTimeoutDelay "          \
          "milliseconds if failed to reach safepoint")                      \
                                                                            \
  develop(bool, DieOnSafepointTimeout, false,                               \
          "Die upon failure to reach safepoint (see SafepointTimeout)")     \
                                                                            \
  /* 50 retries * (5 * current_retry_count) millis = ~6.375 seconds */      \
  /* typically, at most a few retries are needed                    */      \
  product(intx, SuspendRetryCount, 50,                                      \
          "Maximum retry count for an external suspend request")            \
                                                                            \
  product(intx, SuspendRetryDelay, 5,                                       \
          "Milliseconds to delay per retry (* current_retry_count)")        \
                                                                            \
  product(bool, AssertOnSuspendWaitFailure, false,                          \
          "Assert/Guarantee on external suspend wait failure")              \
                                                                            \
  product(bool, TraceSuspendWaitFailures, false,                            \
          "Trace external suspend wait failures")                           \
                                                                            \
  product(bool, MaxFDLimit, true,                                           \
          "Bump the number of file descriptors to maximum in Solaris")      \
                                                                            \
  diagnostic(bool, LogEvents, true,                                         \
          "Enable the various ring buffer event logs")                      \
                                                                            \
  diagnostic(uintx, LogEventsBufferEntries, 10,                             \
          "Number of ring buffer event logs")                               \
          range(1, NOT_LP64(1*K) LP64_ONLY(1*M))                            \
                                                                            \
  product(bool, BytecodeVerificationRemote, true,                           \
          "Enable the Java bytecode verifier for remote classes")           \
                                                                            \
  product(bool, BytecodeVerificationLocal, false,                           \
          "Enable the Java bytecode verifier for local classes")            \
                                                                            \
  develop(bool, ForceFloatExceptions, trueInDebug,                          \
          "Force exceptions on FP stack under/overflow")                    \
                                                                            \
  develop(bool, VerifyStackAtCalls, false,                                  \
          "Verify that the stack pointer is unchanged after calls")         \
                                                                            \
  develop(bool, TraceJavaAssertions, false,                                 \
          "Trace java language assertions")                                 \
                                                                            \
  notproduct(bool, CheckAssertionStatusDirectives, false,                   \
          "Temporary - see javaClasses.cpp")                                \
                                                                            \
  notproduct(bool, PrintMallocFree, false,                                  \
          "Trace calls to C heap malloc/free allocation")                   \
                                                                            \
  product(bool, PrintOopAddress, false,                                     \
          "Always print the location of the oop")                           \
                                                                            \
  notproduct(bool, VerifyCodeCache, false,                                  \
          "Verify code cache on memory allocation/deallocation")            \
                                                                            \
  develop(bool, ZapDeadCompiledLocals, false,                               \
          "Zap dead locals in compiler frames")                             \
                                                                            \
  notproduct(bool, ZapDeadLocalsOld, false,                                 \
          "Zap dead locals (old version, zaps all frames when "             \
          "entering the VM")                                                \
                                                                            \
  notproduct(bool, CheckOopishValues, false,                                \
          "Warn if value contains oop (requires ZapDeadLocals)")            \
                                                                            \
  develop(bool, UseMallocOnly, false,                                       \
          "Use only malloc/free for allocation (no resource area/arena)")   \
                                                                            \
  develop(bool, PrintMalloc, false,                                         \
          "Print all malloc/free calls")                                    \
                                                                            \
  develop(bool, PrintMallocStatistics, false,                               \
          "Print malloc/free statistics")                                   \
                                                                            \
  develop(bool, ZapResourceArea, trueInDebug,                               \
          "Zap freed resource/arena space with 0xABABABAB")                 \
                                                                            \
  notproduct(bool, ZapVMHandleArea, trueInDebug,                            \
          "Zap freed VM handle space with 0xBCBCBCBC")                      \
                                                                            \
  develop(bool, ZapJNIHandleArea, trueInDebug,                              \
          "Zap freed JNI handle space with 0xFEFEFEFE")                     \
                                                                            \
  notproduct(bool, ZapStackSegments, trueInDebug,                           \
          "Zap allocated/freed stack segments with 0xFADFADED")             \
                                                                            \
  develop(bool, ZapUnusedHeapArea, trueInDebug,                             \
          "Zap unused heap space with 0xBAADBABE")                          \
                                                                            \
  develop(bool, TraceZapUnusedHeapArea, false,                              \
          "Trace zapping of unused heap space")                             \
                                                                            \
  develop(bool, CheckZapUnusedHeapArea, false,                              \
          "Check zapping of unused heap space")                             \
                                                                            \
  develop(bool, ZapFillerObjects, trueInDebug,                              \
          "Zap filler objects with 0xDEAFBABE")                             \
                                                                            \
  develop(bool, PrintVMMessages, true,                                      \
          "Print VM messages on console")                                   \
                                                                            \
  product(bool, PrintGCApplicationConcurrentTime, false,                    \
          "Print the time the application has been running")                \
                                                                            \
  product(bool, PrintGCApplicationStoppedTime, false,                       \
          "Print the time the application has been stopped")                \
                                                                            \
  diagnostic(bool, VerboseVerification, false,                              \
          "Display detailed verification details")                          \
                                                                            \
  notproduct(uintx, ErrorHandlerTest, 0,                                    \
          "If > 0, provokes an error after VM initialization; the value "   \
          "determines which error to provoke. See test_error_handler() "    \
          "in debug.cpp.")                                                  \
                                                                            \
  notproduct(uintx, TestCrashInErrorHandler, 0,                             \
          "If > 0, provokes an error inside VM error handler (a secondary " \
          "crash). see test_error_handler() in debug.cpp.")                 \
                                                                            \
  notproduct(bool, TestSafeFetchInErrorHandler, false,                      \
          "If true, tests SafeFetch inside error handler.")                 \
                                                                            \
  develop(bool, Verbose, false,                                             \
          "Print additional debugging information from other modes")        \
                                                                            \
  develop(bool, PrintMiscellaneous, false,                                  \
          "Print uncategorized debugging information (requires +Verbose)")  \
                                                                            \
  develop(bool, WizardMode, false,                                          \
          "Print much more debugging information")                          \
                                                                            \
  product(bool, ShowMessageBoxOnError, false,                               \
          "Keep process alive on VM fatal error")                           \
                                                                            \
  product(bool, CreateCoredumpOnCrash, true,                                \
          "Create core/mini dump on VM fatal error")                        \
                                                                            \
  product(uintx, ErrorLogTimeout, 2 * 60,                                   \
          "Timeout, in seconds, to limit the time spent on writing an "     \
          "error log in case of a crash.")                                  \
                                                                            \
  product_pd(bool, UseOSErrorReporting,                                     \
          "Let VM fatal error propagate to the OS (ie. WER on Windows)")    \
                                                                            \
  product(bool, SuppressFatalErrorMessage, false,                           \
          "Report NO fatal error message (avoid deadlock)")                 \
                                                                            \
  product(ccstrlist, OnError, "",                                           \
          "Run user-defined commands on fatal error; see VMError.cpp "      \
          "for examples")                                                   \
                                                                            \
  product(ccstrlist, OnOutOfMemoryError, "",                                \
          "Run user-defined commands on first java.lang.OutOfMemoryError")  \
                                                                            \
  manageable(bool, HeapDumpBeforeFullGC, false,                             \
          "Dump heap to file before any major stop-the-world GC")           \
                                                                            \
  manageable(bool, HeapDumpAfterFullGC, false,                              \
          "Dump heap to file after any major stop-the-world GC")            \
                                                                            \
  manageable(bool, HeapDumpOnOutOfMemoryError, false,                       \
          "Dump heap to file when java.lang.OutOfMemoryError is thrown")    \
                                                                            \
  manageable(ccstr, HeapDumpPath, NULL,                                     \
          "When HeapDumpOnOutOfMemoryError is on, the path (filename or "   \
          "directory) of the dump file (defaults to java_pid<pid>.hprof "   \
          "in the working directory)")                                      \
                                                                            \
  develop(size_t, SegmentedHeapDumpThreshold, 2*G,                          \
          "Generate a segmented heap dump (JAVA PROFILE 1.0.2 format) "     \
          "when the heap usage is larger than this")                        \
                                                                            \
  develop(size_t, HeapDumpSegmentSize, 1*G,                                 \
          "Approximate segment size when generating a segmented heap dump") \
                                                                            \
  develop(bool, BreakAtWarning, false,                                      \
          "Execute breakpoint upon encountering VM warning")                \
                                                                            \
  develop(bool, TraceVMOperation, false,                                    \
          "Trace VM operations")                                            \
                                                                            \
  develop(bool, UseFakeTimers, false,                                       \
          "Tell whether the VM should use system time or a fake timer")     \
                                                                            \
  product(ccstr, NativeMemoryTracking, "off",                               \
          "Native memory tracking options")                                 \
                                                                            \
  diagnostic(bool, PrintNMTStatistics, false,                               \
          "Print native memory tracking summary data if it is on")          \
                                                                            \
  diagnostic(bool, LogCompilation, false,                                   \
          "Log compilation activity in detail to LogFile")                  \
                                                                            \
  product(bool, PrintCompilation, false,                                    \
          "Print compilations")                                             \
                                                                            \
  diagnostic(bool, TraceNMethodInstalls, false,                             \
          "Trace nmethod installation")                                     \
                                                                            \
  diagnostic(intx, ScavengeRootsInCode, 2,                                  \
          "0: do not allow scavengable oops in the code cache; "            \
          "1: allow scavenging from the code cache; "                       \
          "2: emit as many constants as the compiler can see")              \
          range(0, 2)                                                       \
                                                                            \
  product(bool, AlwaysRestoreFPU, false,                                    \
          "Restore the FPU control word after every JNI call (expensive)")  \
                                                                            \
  diagnostic(bool, PrintCompilation2, false,                                \
          "Print additional statistics per compilation")                    \
                                                                            \
  diagnostic(bool, PrintAdapterHandlers, false,                             \
          "Print code generated for i2c/c2i adapters")                      \
                                                                            \
  diagnostic(bool, VerifyAdapterCalls, trueInDebug,                         \
          "Verify that i2c/c2i adapters are called properly")               \
                                                                            \
  develop(bool, VerifyAdapterSharing, false,                                \
          "Verify that the code for shared adapters is the equivalent")     \
                                                                            \
  diagnostic(bool, PrintAssembly, false,                                    \
          "Print assembly code (using external disassembler.so)")           \
                                                                            \
  diagnostic(ccstr, PrintAssemblyOptions, NULL,                             \
          "Print options string passed to disassembler.so")                 \
                                                                            \
  diagnostic(bool, PrintNMethods, false,                                    \
          "Print assembly code for nmethods when generated")                \
                                                                            \
  diagnostic(bool, PrintNativeNMethods, false,                              \
          "Print assembly code for native nmethods when generated")         \
                                                                            \
  develop(bool, PrintDebugInfo, false,                                      \
          "Print debug information for all nmethods when generated")        \
                                                                            \
  develop(bool, PrintRelocations, false,                                    \
          "Print relocation information for all nmethods when generated")   \
                                                                            \
  develop(bool, PrintDependencies, false,                                   \
          "Print dependency information for all nmethods when generated")   \
                                                                            \
  develop(bool, PrintExceptionHandlers, false,                              \
          "Print exception handler tables for all nmethods when generated") \
                                                                            \
  develop(bool, StressCompiledExceptionHandlers, false,                     \
          "Exercise compiled exception handlers")                           \
                                                                            \
  develop(bool, InterceptOSException, false,                                \
          "Start debugger when an implicit OS (e.g. NULL) "                 \
          "exception happens")                                              \
                                                                            \
  product(bool, PrintCodeCache, false,                                      \
          "Print the code cache memory usage when exiting")                 \
                                                                            \
  develop(bool, PrintCodeCache2, false,                                     \
          "Print detailed usage information on the code cache when exiting")\
                                                                            \
  product(bool, PrintCodeCacheOnCompilation, false,                         \
          "Print the code cache memory usage each time a method is "        \
          "compiled")                                                       \
                                                                            \
  diagnostic(bool, PrintStubCode, false,                                    \
          "Print generated stub code")                                      \
                                                                            \
  product(bool, StackTraceInThrowable, true,                                \
          "Collect backtrace in throwable when exception happens")          \
                                                                            \
  product(bool, OmitStackTraceInFastThrow, true,                            \
          "Omit backtraces for some 'hot' exceptions in optimized code")    \
                                                                            \
  product(bool, ProfilerPrintByteCodeStatistics, false,                     \
          "Print bytecode statistics when dumping profiler output")         \
                                                                            \
  product(bool, ProfilerRecordPC, false,                                    \
          "Collect ticks for each 16 byte interval of compiled code")       \
                                                                            \
  product(bool, ProfileVM, false,                                           \
          "Profile ticks that fall within VM (either in the VM Thread "     \
          "or VM code called through stubs)")                               \
                                                                            \
  product(bool, ProfileIntervals, false,                                    \
          "Print profiles for each interval (see ProfileIntervalsTicks)")   \
                                                                            \
  notproduct(bool, ProfilerCheckIntervals, false,                           \
          "Collect and print information on spacing of profiler ticks")     \
                                                                            \
  product(bool, PrintWarnings, true,                                        \
          "Print JVM warnings to output stream")                            \
                                                                            \
  notproduct(uintx, WarnOnStalledSpinLock, 0,                               \
          "Print warnings for stalled SpinLocks")                           \
                                                                            \
  product(bool, RegisterFinalizersAtInit, true,                             \
          "Register finalizable objects at end of Object.<init> or "        \
          "after allocation")                                               \
                                                                            \
  develop(bool, RegisterReferences, true,                                   \
          "Tell whether the VM should register soft/weak/final/phantom "    \
          "references")                                                     \
                                                                            \
  develop(bool, IgnoreRewrites, false,                                      \
          "Suppress rewrites of bytecodes in the oopmap generator. "        \
          "This is unsafe!")                                                \
                                                                            \
  develop(bool, PrintCodeCacheExtension, false,                             \
          "Print extension of code cache")                                  \
                                                                            \
  develop(bool, UsePrivilegedStack, true,                                   \
          "Enable the security JVM functions")                              \
                                                                            \
  develop(bool, ProtectionDomainVerification, true,                         \
          "Verify protection domain before resolution in system dictionary")\
                                                                            \
  product(bool, ClassUnloading, true,                                       \
          "Do unloading of classes")                                        \
                                                                            \
  product(bool, ClassUnloadingWithConcurrentMark, true,                     \
          "Do unloading of classes with a concurrent marking cycle")        \
                                                                            \
  develop(bool, DisableStartThread, false,                                  \
          "Disable starting of additional Java threads "                    \
          "(for debugging only)")                                           \
                                                                            \
  develop(bool, MemProfiling, false,                                        \
          "Write memory usage profiling to log file")                       \
                                                                            \
  notproduct(bool, PrintSystemDictionaryAtExit, false,                      \
          "Print the system dictionary at exit")                            \
                                                                            \
  experimental(intx, PredictedLoadedClassCount, 0,                          \
          "Experimental: Tune loaded class cache starting size")            \
                                                                            \
  diagnostic(bool, UnsyncloadClass, false,                                  \
          "Unstable: VM calls loadClass unsynchronized. Custom "            \
          "class loader must call VM synchronized for findClass "           \
          "and defineClass.")                                               \
                                                                            \
  product(bool, AlwaysLockClassLoader, false,                               \
          "Require the VM to acquire the class loader lock before calling " \
          "loadClass() even for class loaders registering "                 \
          "as parallel capable")                                            \
                                                                            \
  product(bool, AllowParallelDefineClass, false,                            \
          "Allow parallel defineClass requests for class loaders "          \
          "registering as parallel capable")                                \
                                                                            \
  product(bool, MustCallLoadClassInternal, false,                           \
          "Call loadClassInternal() rather than loadClass()")               \
                                                                            \
  product_pd(bool, DontYieldALot,                                           \
          "Throw away obvious excess yield calls")                          \
                                                                            \
  product_pd(bool, ConvertSleepToYield,                                     \
          "Convert sleep(0) to thread yield "                               \
          "(may be off for Solaris to improve GUI)")                        \
                                                                            \
  product(bool, ConvertYieldToSleep, false,                                 \
          "Convert yield to a sleep of MinSleepInterval to simulate Win32 " \
          "behavior")                                                       \
                                                                            \
  develop(bool, UseDetachedThreads, true,                                   \
          "Use detached threads that are recycled upon termination "        \
          "(for Solaris only)")                                             \
                                                                            \
  product(bool, UseLWPSynchronization, true,                                \
          "Use LWP-based instead of libthread-based synchronization "       \
          "(SPARC only)")                                                   \
                                                                            \
  experimental(ccstr, SyncKnobs, NULL,                                      \
               "(Unstable) Various monitor synchronization tunables")       \
                                                                            \
  experimental(intx, EmitSync, 0,                                           \
               "(Unsafe, Unstable) "                                        \
               "Control emission of inline sync fast-path code")            \
                                                                            \
  product(intx, MonitorBound, 0, "Bound Monitor population")                \
                                                                            \
  product(bool, MonitorInUseLists, false, "Track Monitors for Deflation")   \
                                                                            \
  experimental(intx, SyncFlags, 0, "(Unsafe, Unstable) "                    \
               "Experimental Sync flags")                                   \
                                                                            \
  experimental(intx, SyncVerbose, 0, "(Unstable)")                          \
                                                                            \
  diagnostic(bool, InlineNotify, true, "intrinsify subset of notify")       \
                                                                            \
  experimental(intx, ClearFPUAtPark, 0, "(Unsafe, Unstable)")               \
                                                                            \
  experimental(intx, hashCode, 5,                                           \
               "(Unstable) select hashCode generation algorithm")           \
                                                                            \
  experimental(intx, WorkAroundNPTLTimedWaitHang, 0,                        \
               "(Unstable, Linux-specific) "                                \
               "avoid NPTL-FUTEX hang pthread_cond_timedwait")              \
                                                                            \
  product(bool, FilterSpuriousWakeups, true,                                \
          "When true prevents OS-level spurious, or premature, wakeups "    \
          "from Object.wait (Ignored for Windows)")                         \
                                                                            \
  experimental(intx, NativeMonitorTimeout, -1, "(Unstable)")                \
                                                                            \
  experimental(intx, NativeMonitorFlags, 0, "(Unstable)")                   \
                                                                            \
  experimental(intx, NativeMonitorSpinLimit, 20, "(Unstable)")              \
                                                                            \
  develop(bool, UsePthreads, false,                                         \
          "Use pthread-based instead of libthread-based synchronization "   \
          "(SPARC only)")                                                   \
                                                                            \
  product(bool, ReduceSignalUsage, false,                                   \
          "Reduce the use of OS signals in Java and/or the VM")             \
                                                                            \
  develop_pd(bool, ShareVtableStubs,                                        \
          "Share vtable stubs (smaller code but worse branch prediction")   \
                                                                            \
  develop(bool, LoadLineNumberTables, true,                                 \
          "Tell whether the class file parser loads line number tables")    \
                                                                            \
  develop(bool, LoadLocalVariableTables, true,                              \
          "Tell whether the class file parser loads local variable tables") \
                                                                            \
  develop(bool, LoadLocalVariableTypeTables, true,                          \
          "Tell whether the class file parser loads local variable type"    \
          "tables")                                                         \
                                                                            \
  product(bool, AllowUserSignalHandlers, false,                             \
          "Do not complain if the application installs signal handlers "    \
          "(Solaris & Linux only)")                                         \
                                                                            \
  product(bool, UseSignalChaining, true,                                    \
          "Use signal-chaining to invoke signal handlers installed "        \
          "by the application (Solaris & Linux only)")                      \
                                                                            \
  product(bool, UseAltSigs, false,                                          \
          "Use alternate signals instead of SIGUSR1 & SIGUSR2 for VM "      \
          "internal signals (Solaris only)")                                \
                                                                            \
  product(bool, AllowJNIEnvProxy, false,                                    \
          "Allow JNIEnv proxies for jdbx")                                  \
                                                                            \
  product(bool, JNIDetachReleasesMonitors, true,                            \
          "JNI DetachCurrentThread releases monitors owned by thread")      \
                                                                            \
  product(bool, RestoreMXCSROnJNICalls, false,                              \
          "Restore MXCSR when returning from JNI calls")                    \
                                                                            \
  product(bool, CheckJNICalls, false,                                       \
          "Verify all arguments to JNI calls")                              \
                                                                            \
  product(bool, CheckEndorsedAndExtDirs, false,                             \
          "Verify the endorsed and extension directories are not used")     \
                                                                            \
  product(bool, UseFastJNIAccessors, true,                                  \
          "Use optimized versions of Get<Primitive>Field")                  \
                                                                            \
  product(intx, MaxJNILocalCapacity, 65536,                                 \
          "Maximum allowable local JNI handle capacity to "                 \
          "EnsureLocalCapacity() and PushLocalFrame(), "                    \
          "where <= 0 is unlimited, default: 65536")                        \
                                                                            \
  product(bool, EagerXrunInit, false,                                       \
          "Eagerly initialize -Xrun libraries; allows startup profiling, "  \
          "but not all -Xrun libraries may support the state of the VM "    \
          "at this time")                                                   \
                                                                            \
  product(bool, PreserveAllAnnotations, false,                              \
          "Preserve RuntimeInvisibleAnnotations as well "                   \
          "as RuntimeVisibleAnnotations")                                   \
                                                                            \
  develop(uintx, PreallocatedOutOfMemoryErrorCount, 4,                      \
          "Number of OutOfMemoryErrors preallocated with backtrace")        \
                                                                            \
  product(bool, UseXMMForArrayCopy, false,                                  \
          "Use SSE2 MOVQ instruction for Arraycopy")                        \
                                                                            \
  product(intx, FieldsAllocationStyle, 1,                                   \
          "0 - type based with oops first, "                                \
          "1 - with oops last, "                                            \
          "2 - oops in super and sub classes are together")                 \
          range(0, 2)                                                       \
                                                                            \
  product(bool, CompactFields, true,                                        \
          "Allocate nonstatic fields in gaps between previous fields")      \
                                                                            \
  notproduct(bool, PrintFieldLayout, false,                                 \
          "Print field layout for each class")                              \
                                                                            \
  /* Need to limit the extent of the padding to reasonable size.          */\
  /* 8K is well beyond the reasonable HW cache line size, even with       */\
  /* aggressive prefetching, while still leaving the room for segregating */\
  /* among the distinct pages.                                            */\
  product(intx, ContendedPaddingWidth, 128,                                 \
          "How many bytes to pad the fields/classes marked @Contended with")\
          range(0, 8192)                                                    \
          constraint(ContendedPaddingWidthConstraintFunc,AtParse)           \
                                                                            \
  product(bool, EnableContended, true,                                      \
          "Enable @Contended annotation support")                           \
                                                                            \
  product(bool, RestrictContended, true,                                    \
          "Restrict @Contended to trusted classes")                         \
                                                                            \
  product(bool, UseBiasedLocking, true,                                     \
          "Enable biased locking in JVM")                                   \
                                                                            \
  product(intx, BiasedLockingStartupDelay, 4000,                            \
          "Number of milliseconds to wait before enabling biased locking")  \
                                                                            \
  diagnostic(bool, PrintBiasedLockingStatistics, false,                     \
          "Print statistics of biased locking in JVM")                      \
                                                                            \
  product(intx, BiasedLockingBulkRebiasThreshold, 20,                       \
          "Threshold of number of revocations per type to try to "          \
          "rebias all objects in the heap of that type")                    \
                                                                            \
  product(intx, BiasedLockingBulkRevokeThreshold, 40,                       \
          "Threshold of number of revocations per type to permanently "     \
          "revoke biases of all objects in the heap of that type")          \
                                                                            \
  product(intx, BiasedLockingDecayTime, 25000,                              \
          "Decay time (in milliseconds) to re-enable bulk rebiasing of a "  \
          "type after previous bulk rebias")                                \
                                                                            \
  /* tracing */                                                             \
                                                                            \
  notproduct(bool, TraceRuntimeCalls, false,                                \
          "Trace run-time calls")                                           \
                                                                            \
  develop(bool, TraceJNICalls, false,                                       \
          "Trace JNI calls")                                                \
                                                                            \
  develop(bool, StressRewriter, false,                                      \
          "Stress linktime bytecode rewriting")                             \
                                                                            \
  notproduct(bool, TraceJVMCalls, false,                                    \
          "Trace JVM calls")                                                \
                                                                            \
  product(ccstr, TraceJVMTI, NULL,                                          \
          "Trace flags for JVMTI functions and events")                     \
                                                                            \
  /* This option can change an EMCP method into an obsolete method. */      \
  /* This can affect tests that except specific methods to be EMCP. */      \
  /* This option should be used with caution.                       */      \
  product(bool, StressLdcRewrite, false,                                    \
          "Force ldc -> ldc_w rewrite during RedefineClasses")              \
                                                                            \
  product(intx, TraceRedefineClasses, 0,                                    \
          "Trace level for JVMTI RedefineClasses")                          \
                                                                            \
  /* change to false by default sometime after Mustang */                   \
  product(bool, VerifyMergedCPBytecodes, true,                              \
          "Verify bytecodes after RedefineClasses constant pool merging")   \
                                                                            \
  develop(bool, TraceJNIHandleAllocation, false,                            \
          "Trace allocation/deallocation of JNI handle blocks")             \
                                                                            \
  develop(bool, TraceBytecodes, false,                                      \
          "Trace bytecode execution")                                       \
                                                                            \
  develop(bool, TraceClassInitialization, false,                            \
          "Trace class initialization")                                     \
                                                                            \
  product(bool, TraceExceptions, false,                                     \
          "Trace exceptions")                                               \
                                                                            \
  develop(bool, TraceICs, false,                                            \
          "Trace inline cache changes")                                     \
                                                                            \
  notproduct(bool, TraceInvocationCounterOverflow, false,                   \
          "Trace method invocation counter overflow")                       \
                                                                            \
  develop(bool, TraceInlineCacheClearing, false,                            \
          "Trace clearing of inline caches in nmethods")                    \
                                                                            \
  develop(bool, TraceDependencies, false,                                   \
          "Trace dependencies")                                             \
                                                                            \
  develop(bool, VerifyDependencies, trueInDebug,                            \
          "Exercise and verify the compilation dependency mechanism")       \
                                                                            \
  develop(bool, TraceNewOopMapGeneration, false,                            \
          "Trace OopMapGeneration")                                         \
                                                                            \
  develop(bool, TraceNewOopMapGenerationDetailed, false,                    \
          "Trace OopMapGeneration: print detailed cell states")             \
                                                                            \
  develop(bool, TimeOopMap, false,                                          \
          "Time calls to GenerateOopMap::compute_map() in sum")             \
                                                                            \
  develop(bool, TimeOopMap2, false,                                         \
          "Time calls to GenerateOopMap::compute_map() individually")       \
                                                                            \
  develop(bool, TraceMonitorMismatch, false,                                \
          "Trace monitor matching failures during OopMapGeneration")        \
                                                                            \
  develop(bool, TraceOopMapRewrites, false,                                 \
          "Trace rewriting of method oops during oop map generation")       \
                                                                            \
  develop(bool, TraceSafepoint, false,                                      \
          "Trace safepoint operations")                                     \
                                                                            \
  develop(bool, TraceICBuffer, false,                                       \
          "Trace usage of IC buffer")                                       \
                                                                            \
  develop(bool, TraceCompiledIC, false,                                     \
          "Trace changes of compiled IC")                                   \
                                                                            \
  notproduct(bool, TraceZapDeadLocals, false,                               \
          "Trace zapping dead locals")                                      \
                                                                            \
  develop(bool, TraceStartupTime, false,                                    \
          "Trace setup time")                                               \
                                                                            \
  develop(bool, TraceProtectionDomainVerification, false,                   \
          "Trace protection domain verification")                           \
                                                                            \
  develop(bool, TraceClearedExceptions, false,                              \
          "Print when an exception is forcibly cleared")                    \
                                                                            \
  product(bool, TraceClassResolution, false,                                \
          "Trace all constant pool resolutions (for debugging)")            \
                                                                            \
  product(bool, TraceBiasedLocking, false,                                  \
          "Trace biased locking in JVM")                                    \
                                                                            \
  product(bool, TraceMonitorInflation, false,                               \
          "Trace monitor inflation in JVM")                                 \
                                                                            \
  /* gc */                                                                  \
                                                                            \
  product(bool, UseSerialGC, false,                                         \
          "Use the Serial garbage collector")                               \
                                                                            \
  product(bool, UseG1GC, false,                                             \
          "Use the Garbage-First garbage collector")                        \
                                                                            \
  product(bool, UseParallelGC, false,                                       \
          "Use the Parallel Scavenge garbage collector")                    \
                                                                            \
  product(bool, UseParallelOldGC, false,                                    \
          "Use the Parallel Old garbage collector")                         \
                                                                            \
  product(uintx, HeapMaximumCompactionInterval, 20,                         \
          "How often should we maximally compact the heap (not allowing "   \
          "any dead space)")                                                \
                                                                            \
  product(uintx, HeapFirstMaximumCompactionCount, 3,                        \
          "The collection count for the first maximum compaction")          \
                                                                            \
  product(bool, UseMaximumCompactionOnSystemGC, true,                       \
          "Use maximum compaction in the Parallel Old garbage collector "   \
          "for a system GC")                                                \
                                                                            \
  product(uintx, ParallelOldDeadWoodLimiterMean, 50,                        \
          "The mean used by the parallel compact dead wood "                \
          "limiter (a number between 0-100)")                               \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, ParallelOldDeadWoodLimiterStdDev, 80,                      \
          "The standard deviation used by the parallel compact dead wood "  \
          "limiter (a number between 0-100)")                               \
          range(0, 100)                                                     \
                                                                            \
  product(uint, ParallelGCThreads, 0,                                       \
          "Number of parallel threads parallel gc will use")                \
                                                                            \
  diagnostic(bool, UseSemaphoreGCThreadsSynchronization, true,              \
            "Use semaphore synchronization for the GC Threads, "            \
            "instead of synchronization based on mutexes")                  \
                                                                            \
  product(bool, UseDynamicNumberOfGCThreads, false,                         \
          "Dynamically choose the number of parallel threads "              \
          "parallel gc will use")                                           \
                                                                            \
  diagnostic(bool, ForceDynamicNumberOfGCThreads, false,                    \
          "Force dynamic selection of the number of "                       \
          "parallel threads parallel gc will use to aid debugging")         \
                                                                            \
  product(size_t, HeapSizePerGCThread, ScaleForWordSize(64*M),              \
          "Size of heap (bytes) per GC thread used in calculating the "     \
          "number of GC threads")                                           \
          range((size_t)os::vm_page_size(), (size_t)max_uintx)              \
                                                                            \
  product(bool, TraceDynamicGCThreads, false,                               \
          "Trace the dynamic GC thread usage")                              \
                                                                            \
  develop(bool, ParallelOldGCSplitALot, false,                              \
          "Provoke splitting (copying data from a young gen space to "      \
          "multiple destination spaces)")                                   \
                                                                            \
  develop(uintx, ParallelOldGCSplitInterval, 3,                             \
          "How often to provoke splitting a young gen space")               \
          range(0, max_uintx)                                               \
                                                                            \
  product(uint, ConcGCThreads, 0,                                           \
          "Number of threads concurrent gc will use")                       \
                                                                            \
  product(size_t, YoungPLABSize, 4096,                                      \
          "Size of young gen promotion LAB's (in HeapWords)")               \
          constraint(YoungPLABSizeConstraintFunc,AfterMemoryInit)           \
                                                                            \
  product(size_t, OldPLABSize, 1024,                                        \
          "Size of old gen promotion LAB's (in HeapWords), or Number        \
          of blocks to attempt to claim when refilling CMS LAB's")          \
                                                                            \
  product(uintx, GCTaskTimeStampEntries, 200,                               \
          "Number of time stamp entries per gc worker thread")              \
          range(1, max_uintx)                                               \
                                                                            \
  product(bool, AlwaysTenure, false,                                        \
          "Always tenure objects in eden (ParallelGC only)")                \
                                                                            \
  product(bool, NeverTenure, false,                                         \
          "Never tenure objects in eden, may tenure on overflow "           \
          "(ParallelGC only)")                                              \
                                                                            \
  product(bool, ScavengeBeforeFullGC, true,                                 \
          "Scavenge young generation before each full GC.")                 \
                                                                            \
  develop(bool, ScavengeWithObjectsInToSpace, false,                        \
          "Allow scavenges to occur when to-space contains objects")        \
                                                                            \
  product(bool, UseConcMarkSweepGC, false,                                  \
          "Use Concurrent Mark-Sweep GC in the old generation")             \
                                                                            \
  product(bool, ExplicitGCInvokesConcurrent, false,                         \
          "A System.gc() request invokes a concurrent collection; "         \
          "(effective only when using concurrent collectors)")              \
                                                                            \
  product(bool, ExplicitGCInvokesConcurrentAndUnloadsClasses, false,        \
          "A System.gc() request invokes a concurrent collection and "      \
          "also unloads classes during such a concurrent gc cycle "         \
          "(effective only when UseConcMarkSweepGC)")                       \
                                                                            \
  product(bool, GCLockerInvokesConcurrent, false,                           \
          "The exit of a JNI critical section necessitating a scavenge, "   \
          "also kicks off a background concurrent collection")              \
                                                                            \
  product(uintx, GCLockerEdenExpansionPercent, 5,                           \
          "How much the GC can expand the eden by while the GC locker "     \
          "is active (as a percentage)")                                    \
          range(0, 100)                                                     \
                                                                            \
  diagnostic(uintx, GCLockerRetryAllocationCount, 2,                        \
          "Number of times to retry allocations when "                      \
          "blocked by the GC locker")                                       \
                                                                            \
  develop(bool, UseCMSAdaptiveFreeLists, true,                              \
          "Use adaptive free lists in the CMS generation")                  \
                                                                            \
  develop(bool, UseAsyncConcMarkSweepGC, true,                              \
          "Use Asynchronous Concurrent Mark-Sweep GC in the old generation")\
                                                                            \
  product(bool, UseCMSBestFit, true,                                        \
          "Use CMS best fit allocation strategy")                           \
                                                                            \
  product(bool, UseParNewGC, false,                                         \
          "Use parallel threads in the new generation")                     \
                                                                            \
  product(bool, PrintTaskqueue, false,                                      \
          "Print taskqueue statistics for parallel collectors")             \
                                                                            \
  product(bool, PrintTerminationStats, false,                               \
          "Print termination statistics for parallel collectors")           \
                                                                            \
  product(uintx, ParallelGCBufferWastePct, 10,                              \
          "Wasted fraction of parallel allocation buffer")                  \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, TargetPLABWastePct, 10,                                    \
          "Target wasted space in last buffer as percent of overall "       \
          "allocation")                                                     \
          range(1, 100)                                                     \
                                                                            \
  product(uintx, PLABWeight, 75,                                            \
          "Percentage (0-100) used to weight the current sample when "      \
          "computing exponentially decaying average for ResizePLAB")        \
          range(0, 100)                                                     \
                                                                            \
  product(bool, ResizePLAB, true,                                           \
          "Dynamically resize (survivor space) promotion LAB's")            \
                                                                            \
  product(bool, PrintPLAB, false,                                           \
          "Print (survivor space) promotion LAB's sizing decisions")        \
                                                                            \
  product(intx, ParGCArrayScanChunk, 50,                                    \
          "Scan a subset of object array and push remainder, if array is "  \
          "bigger than this")                                               \
          range(1, max_intx)                                                \
                                                                            \
  product(bool, ParGCUseLocalOverflow, false,                               \
          "Instead of a global overflow list, use local overflow stacks")   \
                                                                            \
  product(bool, ParGCTrimOverflow, true,                                    \
          "Eagerly trim the local overflow lists "                          \
          "(when ParGCUseLocalOverflow)")                                   \
                                                                            \
  notproduct(bool, ParGCWorkQueueOverflowALot, false,                       \
          "Simulate work queue overflow in ParNew")                         \
                                                                            \
  notproduct(uintx, ParGCWorkQueueOverflowInterval, 1000,                   \
          "An `interval' counter that determines how frequently "           \
          "we simulate overflow; a smaller number increases frequency")     \
                                                                            \
  product(uintx, ParGCDesiredObjsFromOverflowList, 20,                      \
          "The desired number of objects to claim from the overflow list")  \
                                                                            \
  diagnostic(uintx, ParGCStridesPerThread, 2,                               \
          "The number of strides per worker thread that we divide up the "  \
          "card table scanning work into")                                  \
          range(1, max_uintx)                                               \
                                                                            \
  diagnostic(intx, ParGCCardsPerStrideChunk, 256,                           \
          "The number of cards in each chunk of the parallel chunks used "  \
          "during card table scanning")                                     \
          range(1, max_intx)                                                \
                                                                            \
  product(uintx, OldPLABWeight, 50,                                         \
          "Percentage (0-100) used to weight the current sample when "      \
          "computing exponentially decaying average for resizing "          \
          "OldPLABSize")                                                    \
          range(0, 100)                                                     \
                                                                            \
  product(bool, ResizeOldPLAB, true,                                        \
          "Dynamically resize (old gen) promotion LAB's")                   \
                                                                            \
  product(bool, PrintOldPLAB, false,                                        \
          "Print (old gen) promotion LAB's sizing decisions")               \
                                                                            \
  product(size_t, CMSOldPLABMax, 1024,                                      \
          "Maximum size of CMS gen promotion LAB caches per worker "        \
          "per block size")                                                 \
          range(1, max_uintx)                                               \
                                                                            \
  product(size_t, CMSOldPLABMin, 16,                                        \
          "Minimum size of CMS gen promotion LAB caches per worker "        \
          "per block size")                                                 \
          range(1, max_uintx)                                               \
          constraint(CMSOldPLABMinConstraintFunc,AfterErgo)                 \
                                                                            \
  product(uintx, CMSOldPLABNumRefills, 4,                                   \
          "Nominal number of refills of CMS gen promotion LAB cache "       \
          "per worker per block size")                                      \
          range(1, max_uintx)                                               \
                                                                            \
  product(bool, CMSOldPLABResizeQuicker, false,                             \
          "React on-the-fly during a scavenge to a sudden "                 \
          "change in block demand rate")                                    \
                                                                            \
  product(uintx, CMSOldPLABToleranceFactor, 4,                              \
          "The tolerance of the phase-change detector for on-the-fly "      \
          "PLAB resizing during a scavenge")                                \
          range(1, max_uintx)                                               \
                                                                            \
  product(uintx, CMSOldPLABReactivityFactor, 2,                             \
          "The gain in the feedback loop for on-the-fly PLAB resizing "     \
          "during a scavenge")                                              \
                                                                            \
  product(bool, AlwaysPreTouch, false,                                      \
          "Force all freshly committed pages to be pre-touched")            \
                                                                            \
  product_pd(size_t, CMSYoungGenPerWorker,                                  \
          "The maximum size of young gen chosen by default per GC worker "  \
          "thread available")                                               \
          range(1, max_uintx)                                               \
                                                                            \
  product(uintx, CMSIncrementalSafetyFactor, 10,                            \
          "Percentage (0-100) used to add conservatism when computing the " \
          "duty cycle")                                                     \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, CMSExpAvgFactor, 50,                                       \
          "Percentage (0-100) used to weight the current sample when "      \
          "computing exponential averages for CMS statistics")              \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, CMS_FLSWeight, 75,                                         \
          "Percentage (0-100) used to weight the current sample when "      \
          "computing exponentially decaying averages for CMS FLS "          \
          "statistics")                                                     \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, CMS_FLSPadding, 1,                                         \
          "The multiple of deviation from mean to use for buffering "       \
          "against volatility in free list demand")                         \
                                                                            \
  product(uintx, FLSCoalescePolicy, 2,                                      \
          "CMS: aggressiveness level for coalescing, increasing "           \
          "from 0 to 4")                                                    \
          range(0, 4)                                                       \
                                                                            \
  product(bool, FLSAlwaysCoalesceLarge, false,                              \
          "CMS: larger free blocks are always available for coalescing")    \
                                                                            \
  product(double, FLSLargestBlockCoalesceProximity, 0.99,                   \
          "CMS: the smaller the percentage the greater the coalescing "     \
          "force")                                                          \
                                                                            \
  product(double, CMSSmallCoalSurplusPercent, 1.05,                         \
          "CMS: the factor by which to inflate estimated demand of small "  \
          "block sizes to prevent coalescing with an adjoining block")      \
                                                                            \
  product(double, CMSLargeCoalSurplusPercent, 0.95,                         \
          "CMS: the factor by which to inflate estimated demand of large "  \
          "block sizes to prevent coalescing with an adjoining block")      \
                                                                            \
  product(double, CMSSmallSplitSurplusPercent, 1.10,                        \
          "CMS: the factor by which to inflate estimated demand of small "  \
          "block sizes to prevent splitting to supply demand for smaller "  \
          "blocks")                                                         \
                                                                            \
  product(double, CMSLargeSplitSurplusPercent, 1.00,                        \
          "CMS: the factor by which to inflate estimated demand of large "  \
          "block sizes to prevent splitting to supply demand for smaller "  \
          "blocks")                                                         \
                                                                            \
  product(bool, CMSExtrapolateSweep, false,                                 \
          "CMS: cushion for block demand during sweep")                     \
                                                                            \
  product(uintx, CMS_SweepWeight, 75,                                       \
          "Percentage (0-100) used to weight the current sample when "      \
          "computing exponentially decaying average for inter-sweep "       \
          "duration")                                                       \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, CMS_SweepPadding, 1,                                       \
          "The multiple of deviation from mean to use for buffering "       \
          "against volatility in inter-sweep duration")                     \
                                                                            \
  product(uintx, CMS_SweepTimerThresholdMillis, 10,                         \
          "Skip block flux-rate sampling for an epoch unless inter-sweep "  \
          "duration exceeds this threshold in milliseconds")                \
                                                                            \
  product(bool, CMSClassUnloadingEnabled, true,                             \
          "Whether class unloading enabled when using CMS GC")              \
                                                                            \
  product(uintx, CMSClassUnloadingMaxInterval, 0,                           \
          "When CMS class unloading is enabled, the maximum CMS cycle "     \
          "count for which classes may not be unloaded")                    \
                                                                            \
  develop(intx, CMSDictionaryChoice, 0,                                     \
          "Use BinaryTreeDictionary as default in the CMS generation")      \
                                                                            \
  product(uintx, CMSIndexedFreeListReplenish, 4,                            \
          "Replenish an indexed free list with this number of chunks")      \
                                                                            \
  product(bool, CMSReplenishIntermediate, true,                             \
          "Replenish all intermediate free-list caches")                    \
                                                                            \
  product(bool, CMSSplitIndexedFreeListBlocks, true,                        \
          "When satisfying batched demand, split blocks from the "          \
          "IndexedFreeList whose size is a multiple of requested size")     \
                                                                            \
  product(bool, CMSLoopWarn, false,                                         \
          "Warn in case of excessive CMS looping")                          \
                                                                            \
  develop(bool, CMSOverflowEarlyRestoration, false,                         \
          "Restore preserved marks early")                                  \
                                                                            \
  product(size_t, MarkStackSize, NOT_LP64(32*K) LP64_ONLY(4*M),             \
          "Size of marking stack")                                          \
                                                                            \
  /* where does the range max value of (max_jint - 1) come from? */         \
  product(size_t, MarkStackSizeMax, NOT_LP64(4*M) LP64_ONLY(512*M),         \
          "Maximum size of marking stack")                                  \
          range(1, (max_jint - 1))                                          \
                                                                            \
  notproduct(bool, CMSMarkStackOverflowALot, false,                         \
          "Simulate frequent marking stack / work queue overflow")          \
                                                                            \
  notproduct(uintx, CMSMarkStackOverflowInterval, 1000,                     \
          "An \"interval\" counter that determines how frequently "         \
          "to simulate overflow; a smaller number increases frequency")     \
                                                                            \
  product(uintx, CMSMaxAbortablePrecleanLoops, 0,                           \
          "Maximum number of abortable preclean iterations, if > 0")        \
                                                                            \
  product(intx, CMSMaxAbortablePrecleanTime, 5000,                          \
          "Maximum time in abortable preclean (in milliseconds)")           \
                                                                            \
  product(uintx, CMSAbortablePrecleanMinWorkPerIteration, 100,              \
          "Nominal minimum work per abortable preclean iteration")          \
                                                                            \
  manageable(intx, CMSAbortablePrecleanWaitMillis, 100,                     \
          "Time that we sleep between iterations when not given "           \
          "enough work per iteration")                                      \
                                                                            \
  product(size_t, CMSRescanMultiple, 32,                                    \
          "Size (in cards) of CMS parallel rescan task")                    \
          range(1, max_uintx)                                               \
                                                                            \
  product(size_t, CMSConcMarkMultiple, 32,                                  \
          "Size (in cards) of CMS concurrent MT marking task")              \
          range(1, max_uintx)                                               \
                                                                            \
  product(bool, CMSAbortSemantics, false,                                   \
          "Whether abort-on-overflow semantics is implemented")             \
                                                                            \
  product(bool, CMSParallelInitialMarkEnabled, true,                        \
          "Use the parallel initial mark.")                                 \
                                                                            \
  product(bool, CMSParallelRemarkEnabled, true,                             \
          "Whether parallel remark enabled (only if ParNewGC)")             \
                                                                            \
  product(bool, CMSParallelSurvivorRemarkEnabled, true,                     \
          "Whether parallel remark of survivor space "                      \
          "enabled (effective only if CMSParallelRemarkEnabled)")           \
                                                                            \
  product(bool, CMSPLABRecordAlways, true,                                  \
          "Always record survivor space PLAB boundaries (effective only "   \
          "if CMSParallelSurvivorRemarkEnabled)")                           \
                                                                            \
  product(bool, CMSEdenChunksRecordAlways, true,                            \
          "Always record eden chunks used for the parallel initial mark "   \
          "or remark of eden")                                              \
                                                                            \
  product(bool, CMSPrintEdenSurvivorChunks, false,                          \
          "Print the eden and the survivor chunks used for the parallel "   \
          "initial mark or remark of the eden/survivor spaces")             \
                                                                            \
  product(bool, CMSConcurrentMTEnabled, true,                               \
          "Whether multi-threaded concurrent work enabled "                 \
          "(effective only if ParNewGC)")                                   \
                                                                            \
  product(bool, CMSPrecleaningEnabled, true,                                \
          "Whether concurrent precleaning enabled")                         \
                                                                            \
  product(uintx, CMSPrecleanIter, 3,                                        \
          "Maximum number of precleaning iteration passes")                 \
          range(0, 9)                                                       \
                                                                            \
  product(uintx, CMSPrecleanDenominator, 3,                                 \
          "CMSPrecleanNumerator:CMSPrecleanDenominator yields convergence " \
          "ratio")                                                          \
          range(1, max_uintx)                                               \
          constraint(CMSPrecleanDenominatorConstraintFunc,AfterErgo)        \
                                                                            \
  product(uintx, CMSPrecleanNumerator, 2,                                   \
          "CMSPrecleanNumerator:CMSPrecleanDenominator yields convergence " \
          "ratio")                                                          \
          range(0, max_uintx-1)                                             \
          constraint(CMSPrecleanNumeratorConstraintFunc,AfterErgo)          \
                                                                            \
  product(bool, CMSPrecleanRefLists1, true,                                 \
          "Preclean ref lists during (initial) preclean phase")             \
                                                                            \
  product(bool, CMSPrecleanRefLists2, false,                                \
          "Preclean ref lists during abortable preclean phase")             \
                                                                            \
  product(bool, CMSPrecleanSurvivors1, false,                               \
          "Preclean survivors during (initial) preclean phase")             \
                                                                            \
  product(bool, CMSPrecleanSurvivors2, true,                                \
          "Preclean survivors during abortable preclean phase")             \
                                                                            \
  product(uintx, CMSPrecleanThreshold, 1000,                                \
          "Do not iterate again if number of dirty cards is less than this")\
          range(100, max_uintx)                                             \
                                                                            \
  product(bool, CMSCleanOnEnter, true,                                      \
          "Clean-on-enter optimization for reducing number of dirty cards") \
                                                                            \
  product(uintx, CMSRemarkVerifyVariant, 1,                                 \
          "Choose variant (1,2) of verification following remark")          \
          range(1, 2)                                                       \
                                                                            \
  product(size_t, CMSScheduleRemarkEdenSizeThreshold, 2*M,                  \
          "If Eden size is below this, do not try to schedule remark")      \
                                                                            \
  product(uintx, CMSScheduleRemarkEdenPenetration, 50,                      \
          "The Eden occupancy percentage (0-100) at which "                 \
          "to try and schedule remark pause")                               \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, CMSScheduleRemarkSamplingRatio, 5,                         \
          "Start sampling eden top at least before young gen "              \
          "occupancy reaches 1/<ratio> of the size at which "               \
          "we plan to schedule remark")                                     \
          range(1, max_uintx)                                               \
                                                                            \
  product(uintx, CMSSamplingGrain, 16*K,                                    \
          "The minimum distance between eden samples for CMS (see above)")  \
          range(1, max_uintx)                                               \
                                                                            \
  product(bool, CMSScavengeBeforeRemark, false,                             \
          "Attempt scavenge before the CMS remark step")                    \
                                                                            \
  develop(bool, CMSTraceSweeper, false,                                     \
          "Trace some actions of the CMS sweeper")                          \
                                                                            \
  product(uintx, CMSWorkQueueDrainThreshold, 10,                            \
          "Don't drain below this size per parallel worker/thief")          \
                                                                            \
  manageable(intx, CMSWaitDuration, 2000,                                   \
          "Time in milliseconds that CMS thread waits for young GC")        \
                                                                            \
  develop(uintx, CMSCheckInterval, 1000,                                    \
          "Interval in milliseconds that CMS thread checks if it "          \
          "should start a collection cycle")                                \
                                                                            \
  product(bool, CMSYield, true,                                             \
          "Yield between steps of CMS")                                     \
                                                                            \
  product(size_t, CMSBitMapYieldQuantum, 10*M,                              \
          "Bitmap operations should process at most this many bits "        \
          "between yields")                                                 \
          range(1, max_uintx)                                               \
                                                                            \
  product(bool, CMSDumpAtPromotionFailure, false,                           \
          "Dump useful information about the state of the CMS old "         \
          "generation upon a promotion failure")                            \
                                                                            \
  product(bool, CMSPrintChunksInDump, false,                                \
          "In a dump enabled by CMSDumpAtPromotionFailure, include "        \
          "more detailed information about the free chunks")                \
                                                                            \
  product(bool, CMSPrintObjectsInDump, false,                               \
          "In a dump enabled by CMSDumpAtPromotionFailure, include "        \
          "more detailed information about the allocated objects")          \
                                                                            \
  diagnostic(bool, FLSVerifyAllHeapReferences, false,                       \
          "Verify that all references across the FLS boundary "             \
          "are to valid objects")                                           \
                                                                            \
  diagnostic(bool, FLSVerifyLists, false,                                   \
          "Do lots of (expensive) FreeListSpace verification")              \
                                                                            \
  diagnostic(bool, FLSVerifyIndexTable, false,                              \
          "Do lots of (expensive) FLS index table verification")            \
                                                                            \
  develop(bool, FLSVerifyDictionary, false,                                 \
          "Do lots of (expensive) FLS dictionary verification")             \
                                                                            \
  develop(bool, VerifyBlockOffsetArray, false,                              \
          "Do (expensive) block offset array verification")                 \
                                                                            \
  diagnostic(bool, BlockOffsetArrayUseUnallocatedBlock, false,              \
          "Maintain _unallocated_block in BlockOffsetArray "                \
          "(currently applicable only to CMS collector)")                   \
                                                                            \
  develop(bool, TraceCMSState, false,                                       \
          "Trace the state of the CMS collection")                          \
                                                                            \
  product(intx, RefDiscoveryPolicy, 0,                                      \
          "Select type of reference discovery policy: "                     \
          "reference-based(0) or referent-based(1)")                        \
          range(ReferenceProcessor::DiscoveryPolicyMin,                     \
                ReferenceProcessor::DiscoveryPolicyMax)                     \
                                                                            \
  product(bool, ParallelRefProcEnabled, false,                              \
          "Enable parallel reference processing whenever possible")         \
                                                                            \
  product(bool, ParallelRefProcBalancingEnabled, true,                      \
          "Enable balancing of reference processing queues")                \
                                                                            \
  product(uintx, CMSTriggerRatio, 80,                                       \
          "Percentage of MinHeapFreeRatio in CMS generation that is "       \
          "allocated before a CMS collection cycle commences")              \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, CMSBootstrapOccupancy, 50,                                 \
          "Percentage CMS generation occupancy at which to "                \
          "initiate CMS collection for bootstrapping collection stats")     \
          range(0, 100)                                                     \
                                                                            \
  product(intx, CMSInitiatingOccupancyFraction, -1,                         \
          "Percentage CMS generation occupancy to start a CMS collection "  \
          "cycle. A negative value means that CMSTriggerRatio is used")     \
          range(min_intx, 100)                                              \
                                                                            \
  product(uintx, InitiatingHeapOccupancyPercent, 45,                        \
          "Percentage of the (entire) heap occupancy to start a "           \
          "concurrent GC cycle. It is used by GCs that trigger a "          \
          "concurrent GC cycle based on the occupancy of the entire heap, " \
          "not just one of the generations (e.g., G1). A value of 0 "       \
          "denotes 'do constant GC cycles'.")                               \
          range(0, 100)                                                     \
                                                                            \
  manageable(intx, CMSTriggerInterval, -1,                                  \
          "Commence a CMS collection cycle (at least) every so many "       \
          "milliseconds (0 permanently, -1 disabled)")                      \
          range(-1, max_intx)                                               \
                                                                            \
  product(bool, UseCMSInitiatingOccupancyOnly, false,                       \
          "Only use occupancy as a criterion for starting a CMS collection")\
                                                                            \
  product(uintx, CMSIsTooFullPercentage, 98,                                \
          "An absolute ceiling above which CMS will always consider the "   \
          "unloading of classes when class unloading is enabled")           \
          range(0, 100)                                                     \
                                                                            \
  develop(bool, CMSTestInFreeList, false,                                   \
          "Check if the coalesced range is already in the "                 \
          "free lists as claimed")                                          \
                                                                            \
  notproduct(bool, CMSVerifyReturnedBytes, false,                           \
          "Check that all the garbage collected was returned to the "       \
          "free lists")                                                     \
                                                                            \
  notproduct(bool, ScavengeALot, false,                                     \
          "Force scavenge at every Nth exit from the runtime system "       \
          "(N=ScavengeALotInterval)")                                       \
                                                                            \
  develop(bool, FullGCALot, false,                                          \
          "Force full gc at every Nth exit from the runtime system "        \
          "(N=FullGCALotInterval)")                                         \
                                                                            \
  notproduct(bool, GCALotAtAllSafepoints, false,                            \
          "Enforce ScavengeALot/GCALot at all potential safepoints")        \
                                                                            \
  product(bool, PrintPromotionFailure, false,                               \
          "Print additional diagnostic information following "              \
          "promotion failure")                                              \
                                                                            \
  notproduct(bool, PromotionFailureALot, false,                             \
          "Use promotion failure handling on every young generation "       \
          "collection")                                                     \
                                                                            \
  develop(uintx, PromotionFailureALotCount, 1000,                           \
          "Number of promotion failures occurring at PLAB "                 \
          "refill attempts (ParNew) or promotion attempts "                 \
          "(other young collectors)")                                       \
                                                                            \
  develop(uintx, PromotionFailureALotInterval, 5,                           \
          "Total collections between promotion failures a lot")             \
                                                                            \
  experimental(uintx, WorkStealingSleepMillis, 1,                           \
          "Sleep time when sleep is used for yields")                       \
                                                                            \
  experimental(uintx, WorkStealingYieldsBeforeSleep, 5000,                  \
          "Number of yields before a sleep is done during work stealing")   \
                                                                            \
  experimental(uintx, WorkStealingHardSpins, 4096,                          \
          "Number of iterations in a spin loop between checks on "          \
          "time out of hard spin")                                          \
                                                                            \
  experimental(uintx, WorkStealingSpinToYieldRatio, 10,                     \
          "Ratio of hard spins to calls to yield")                          \
                                                                            \
  develop(uintx, ObjArrayMarkingStride, 512,                                \
          "Number of object array elements to push onto the marking stack " \
          "before pushing a continuation entry")                            \
                                                                            \
  develop(bool, MetadataAllocationFailALot, false,                          \
          "Fail metadata allocations at intervals controlled by "           \
          "MetadataAllocationFailALotInterval")                             \
                                                                            \
  develop(uintx, MetadataAllocationFailALotInterval, 1000,                  \
          "Metadata allocation failure a lot interval")                     \
                                                                            \
  develop(bool, TraceMetadataChunkAllocation, false,                        \
          "Trace chunk metadata allocations")                               \
                                                                            \
  product(bool, TraceMetadataHumongousAllocation, false,                    \
          "Trace humongous metadata allocations")                           \
                                                                            \
  develop(bool, TraceMetavirtualspaceAllocation, false,                     \
          "Trace virtual space metadata allocations")                       \
                                                                            \
  notproduct(bool, ExecuteInternalVMTests, false,                           \
          "Enable execution of internal VM tests")                          \
                                                                            \
  notproduct(bool, VerboseInternalVMTests, false,                           \
          "Turn on logging for internal VM tests.")                         \
                                                                            \
  product_pd(bool, UseTLAB, "Use thread-local object allocation")           \
                                                                            \
  product_pd(bool, ResizeTLAB,                                              \
          "Dynamically resize TLAB size for threads")                       \
                                                                            \
  product(bool, ZeroTLAB, false,                                            \
          "Zero out the newly created TLAB")                                \
                                                                            \
  product(bool, FastTLABRefill, true,                                       \
          "Use fast TLAB refill code")                                      \
                                                                            \
  product(bool, PrintTLAB, false,                                           \
          "Print various TLAB related information")                         \
                                                                            \
  product(bool, TLABStats, true,                                            \
          "Provide more detailed and expensive TLAB statistics "            \
          "(with PrintTLAB)")                                               \
                                                                            \
  product_pd(bool, NeverActAsServerClassMachine,                            \
          "Never act like a server-class machine")                          \
                                                                            \
  product(bool, AlwaysActAsServerClassMachine, false,                       \
          "Always act like a server-class machine")                         \
                                                                            \
  product_pd(uint64_t, MaxRAM,                                              \
          "Real memory size (in bytes) used to set maximum heap size")      \
                                                                            \
  product(size_t, ErgoHeapSizeLimit, 0,                                     \
          "Maximum ergonomically set heap size (in bytes); zero means use " \
          "MaxRAM / MaxRAMFraction")                                        \
                                                                            \
  product(uintx, MaxRAMFraction, 4,                                         \
          "Maximum fraction (1/n) of real memory used for maximum heap "    \
          "size")                                                           \
          range(1, max_uintx)                                               \
                                                                            \
  product(uintx, DefaultMaxRAMFraction, 4,                                  \
          "Maximum fraction (1/n) of real memory used for maximum heap "    \
          "size; deprecated: to be renamed to MaxRAMFraction")              \
          range(1, max_uintx)                                               \
                                                                            \
  product(uintx, MinRAMFraction, 2,                                         \
          "Minimum fraction (1/n) of real memory used for maximum heap "    \
          "size on systems with small physical memory size")                \
          range(1, max_uintx)                                               \
                                                                            \
  product(uintx, InitialRAMFraction, 64,                                    \
          "Fraction (1/n) of real memory used for initial heap size")       \
          range(1, max_uintx)                                               \
                                                                            \
  develop(uintx, MaxVirtMemFraction, 2,                                     \
          "Maximum fraction (1/n) of virtual memory used for ergonomically "\
          "determining maximum heap size")                                  \
                                                                            \
  product(bool, UseAutoGCSelectPolicy, false,                               \
          "Use automatic collection selection policy")                      \
                                                                            \
  product(uintx, AutoGCSelectPauseMillis, 5000,                             \
          "Automatic GC selection pause threshold in milliseconds")         \
                                                                            \
  product(bool, UseAdaptiveSizePolicy, true,                                \
          "Use adaptive generation sizing policies")                        \
                                                                            \
  product(bool, UsePSAdaptiveSurvivorSizePolicy, true,                      \
          "Use adaptive survivor sizing policies")                          \
                                                                            \
  product(bool, UseAdaptiveGenerationSizePolicyAtMinorCollection, true,     \
          "Use adaptive young-old sizing policies at minor collections")    \
                                                                            \
  product(bool, UseAdaptiveGenerationSizePolicyAtMajorCollection, true,     \
          "Use adaptive young-old sizing policies at major collections")    \
                                                                            \
  product(bool, UseAdaptiveSizePolicyWithSystemGC, false,                   \
          "Include statistics from System.gc() for adaptive size policy")   \
                                                                            \
  product(bool, UseAdaptiveGCBoundary, false,                               \
          "Allow young-old boundary to move")                               \
                                                                            \
  develop(bool, TraceAdaptiveGCBoundary, false,                             \
          "Trace young-old boundary moves")                                 \
                                                                            \
  develop(intx, PSAdaptiveSizePolicyResizeVirtualSpaceAlot, -1,             \
          "Resize the virtual spaces of the young or old generations")      \
                                                                            \
  product(uintx, AdaptiveSizeThroughPutPolicy, 0,                           \
          "Policy for changing generation size for throughput goals")       \
                                                                            \
  develop(bool, PSAdjustTenuredGenForMinorPause, false,                     \
          "Adjust tenured generation to achieve a minor pause goal")        \
                                                                            \
  develop(bool, PSAdjustYoungGenForMajorPause, false,                       \
          "Adjust young generation to achieve a major pause goal")          \
                                                                            \
  product(uintx, AdaptiveSizePolicyInitializingSteps, 20,                   \
          "Number of steps where heuristics is used before data is used")   \
                                                                            \
  develop(uintx, AdaptiveSizePolicyReadyThreshold, 5,                       \
          "Number of collections before the adaptive sizing is started")    \
                                                                            \
  product(uintx, AdaptiveSizePolicyOutputInterval, 0,                       \
          "Collection interval for printing information; zero means never") \
                                                                            \
  product(bool, UseAdaptiveSizePolicyFootprintGoal, true,                   \
          "Use adaptive minimum footprint as a goal")                       \
                                                                            \
  product(uintx, AdaptiveSizePolicyWeight, 10,                              \
          "Weight given to exponential resizing, between 0 and 100")        \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, AdaptiveTimeWeight,       25,                              \
          "Weight given to time in adaptive policy, between 0 and 100")     \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, PausePadding, 1,                                           \
          "How much buffer to keep for pause time")                         \
                                                                            \
  product(uintx, PromotedPadding, 3,                                        \
          "How much buffer to keep for promotion failure")                  \
                                                                            \
  product(uintx, SurvivorPadding, 3,                                        \
          "How much buffer to keep for survivor overflow")                  \
                                                                            \
  product(uintx, ThresholdTolerance, 10,                                    \
          "Allowed collection cost difference between generations")         \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, AdaptiveSizePolicyCollectionCostMargin, 50,                \
          "If collection costs are within margin, reduce both by full "     \
          "delta")                                                          \
                                                                            \
  product(uintx, YoungGenerationSizeIncrement, 20,                          \
          "Adaptive size percentage change in young generation")            \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, YoungGenerationSizeSupplement, 80,                         \
          "Supplement to YoungedGenerationSizeIncrement used at startup")   \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, YoungGenerationSizeSupplementDecay, 8,                     \
          "Decay factor to YoungedGenerationSizeSupplement")                \
          range(1, max_uintx)                                               \
                                                                            \
  product(uintx, TenuredGenerationSizeIncrement, 20,                        \
          "Adaptive size percentage change in tenured generation")          \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, TenuredGenerationSizeSupplement, 80,                       \
          "Supplement to TenuredGenerationSizeIncrement used at startup")   \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, TenuredGenerationSizeSupplementDecay, 2,                   \
          "Decay factor to TenuredGenerationSizeIncrement")                 \
          range(1, max_uintx)                                               \
                                                                            \
  product(uintx, MaxGCPauseMillis, max_uintx,                               \
          "Adaptive size policy maximum GC pause time goal in millisecond, "\
          "or (G1 Only) the maximum GC time per MMU time slice")            \
                                                                            \
  product(uintx, GCPauseIntervalMillis, 0,                                  \
          "Time slice for MMU specification")                               \
                                                                            \
  product(uintx, MaxGCMinorPauseMillis, max_uintx,                          \
          "Adaptive size policy maximum GC minor pause time goal "          \
          "in millisecond")                                                 \
                                                                            \
  product(uintx, GCTimeRatio, 99,                                           \
          "Adaptive size policy application time to GC time ratio")         \
                                                                            \
  product(uintx, AdaptiveSizeDecrementScaleFactor, 4,                       \
          "Adaptive size scale down factor for shrinking")                  \
          range(1, max_uintx)                                               \
                                                                            \
  product(bool, UseAdaptiveSizeDecayMajorGCCost, true,                      \
          "Adaptive size decays the major cost for long major intervals")   \
                                                                            \
  product(uintx, AdaptiveSizeMajorGCDecayTimeScale, 10,                     \
          "Time scale over which major costs decay")                        \
                                                                            \
  product(uintx, MinSurvivorRatio, 3,                                       \
          "Minimum ratio of young generation/survivor space size")          \
                                                                            \
  product(uintx, InitialSurvivorRatio, 8,                                   \
          "Initial ratio of young generation/survivor space size")          \
                                                                            \
  product(size_t, BaseFootPrintEstimate, 256*M,                             \
          "Estimate of footprint other than Java Heap")                     \
                                                                            \
  product(bool, UseGCOverheadLimit, true,                                   \
          "Use policy to limit of proportion of time spent in GC "          \
          "before an OutOfMemory error is thrown")                          \
                                                                            \
  product(uintx, GCTimeLimit, 98,                                           \
          "Limit of the proportion of time spent in GC before "             \
          "an OutOfMemoryError is thrown (used with GCHeapFreeLimit)")      \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, GCHeapFreeLimit, 2,                                        \
          "Minimum percentage of free space after a full GC before an "     \
          "OutOfMemoryError is thrown (used with GCTimeLimit)")             \
          range(0, 100)                                                     \
                                                                            \
  develop(uintx, AdaptiveSizePolicyGCTimeLimitThreshold, 5,                 \
          "Number of consecutive collections before gc time limit fires")   \
                                                                            \
  product(bool, PrintAdaptiveSizePolicy, false,                             \
          "Print information about AdaptiveSizePolicy")                     \
                                                                            \
  product(intx, PrefetchCopyIntervalInBytes, -1,                            \
          "How far ahead to prefetch destination area (<= 0 means off)")    \
                                                                            \
  product(intx, PrefetchScanIntervalInBytes, -1,                            \
          "How far ahead to prefetch scan area (<= 0 means off)")           \
                                                                            \
  product(intx, PrefetchFieldsAhead, -1,                                    \
          "How many fields ahead to prefetch in oop scan (<= 0 means off)") \
                                                                            \
  diagnostic(bool, VerifySilently, false,                                   \
          "Do not print the verification progress")                         \
                                                                            \
  diagnostic(bool, VerifyDuringStartup, false,                              \
          "Verify memory system before executing any Java code "            \
          "during VM initialization")                                       \
                                                                            \
  diagnostic(bool, VerifyBeforeExit, trueInDebug,                           \
          "Verify system before exiting")                                   \
                                                                            \
  diagnostic(bool, VerifyBeforeGC, false,                                   \
          "Verify memory system before GC")                                 \
                                                                            \
  diagnostic(bool, VerifyAfterGC, false,                                    \
          "Verify memory system after GC")                                  \
                                                                            \
  diagnostic(bool, VerifyDuringGC, false,                                   \
          "Verify memory system during GC (between phases)")                \
                                                                            \
  diagnostic(bool, GCParallelVerificationEnabled, true,                     \
          "Enable parallel memory system verification")                     \
                                                                            \
  diagnostic(bool, DeferInitialCardMark, false,                             \
          "When +ReduceInitialCardMarks, explicitly defer any that "        \
          "may arise from new_pre_store_barrier")                           \
                                                                            \
  product(bool, UseCondCardMark, false,                                     \
          "Check for already marked card before updating card table")       \
                                                                            \
  diagnostic(bool, VerifyRememberedSets, false,                             \
          "Verify GC remembered sets")                                      \
                                                                            \
  diagnostic(bool, VerifyObjectStartArray, true,                            \
          "Verify GC object start array if verify before/after")            \
                                                                            \
  product(bool, DisableExplicitGC, false,                                   \
          "Ignore calls to System.gc()")                                    \
                                                                            \
  notproduct(bool, CheckMemoryInitialization, false,                        \
          "Check memory initialization")                                    \
                                                                            \
  diagnostic(bool, BindCMSThreadToCPU, false,                               \
          "Bind CMS Thread to CPU if possible")                             \
                                                                            \
  diagnostic(uintx, CPUForCMSThread, 0,                                     \
          "When BindCMSThreadToCPU is true, the CPU to bind CMS thread to") \
                                                                            \
  product(bool, BindGCTaskThreadsToCPUs, false,                             \
          "Bind GCTaskThreads to CPUs if possible")                         \
                                                                            \
  product(bool, UseGCTaskAffinity, false,                                   \
          "Use worker affinity when asking for GCTasks")                    \
                                                                            \
  product(uintx, ProcessDistributionStride, 4,                              \
          "Stride through processors when distributing processes")          \
                                                                            \
  product(uintx, CMSCoordinatorYieldSleepCount, 10,                         \
          "Number of times the coordinator GC thread will sleep while "     \
          "yielding before giving up and resuming GC")                      \
                                                                            \
  product(uintx, CMSYieldSleepCount, 0,                                     \
          "Number of times a GC thread (minus the coordinator) "            \
          "will sleep while yielding before giving up and resuming GC")     \
                                                                            \
  /* gc tracing */                                                          \
  manageable(bool, PrintGC, false,                                          \
          "Print message at garbage collection")                            \
                                                                            \
  manageable(bool, PrintGCDetails, false,                                   \
          "Print more details at garbage collection")                       \
                                                                            \
  manageable(bool, PrintGCDateStamps, false,                                \
          "Print date stamps at garbage collection")                        \
                                                                            \
  manageable(bool, PrintGCTimeStamps, false,                                \
          "Print timestamps at garbage collection")                         \
                                                                            \
  manageable(bool, PrintGCID, true,                                         \
          "Print an identifier for each garbage collection")                \
                                                                            \
  product(bool, PrintGCTaskTimeStamps, false,                               \
          "Print timestamps for individual gc worker thread tasks")         \
                                                                            \
  develop(intx, ConcGCYieldTimeout, 0,                                      \
          "If non-zero, assert that GC threads yield within this "          \
          "number of milliseconds")                                         \
                                                                            \
  product(bool, PrintReferenceGC, false,                                    \
          "Print times spent handling reference objects during GC "         \
          "(enabled only when PrintGCDetails)")                             \
                                                                            \
  develop(bool, TraceReferenceGC, false,                                    \
          "Trace handling of soft/weak/final/phantom references")           \
                                                                            \
  develop(bool, TraceFinalizerRegistration, false,                          \
          "Trace registration of final references")                         \
                                                                            \
  notproduct(bool, TraceScavenge, false,                                    \
          "Trace scavenge")                                                 \
                                                                            \
  product(bool, IgnoreEmptyClassPaths, false,                               \
          "Ignore empty path elements in -classpath")                       \
                                                                            \
  product(bool, TraceClassPaths, false,                                     \
          "Trace processing of class paths")                                \
                                                                            \
  product_rw(bool, TraceClassLoading, false,                                \
          "Trace all classes loaded")                                       \
                                                                            \
  product(bool, TraceClassLoadingPreorder, false,                           \
          "Trace all classes loaded in order referenced (not loaded)")      \
                                                                            \
  product_rw(bool, TraceClassUnloading, false,                              \
          "Trace unloading of classes")                                     \
                                                                            \
  product_rw(bool, TraceLoaderConstraints, false,                           \
          "Trace loader constraints")                                       \
                                                                            \
  develop(bool, TraceClassLoaderData, false,                                \
          "Trace class loader loader_data lifetime")                        \
                                                                            \
  product(size_t, InitialBootClassLoaderMetaspaceSize,                      \
          NOT_LP64(2200*K) LP64_ONLY(4*M),                                  \
          "Initial size of the boot class loader data metaspace")           \
                                                                            \
  product(bool, TraceYoungGenTime, false,                                   \
          "Trace accumulated time for young collection")                    \
                                                                            \
  product(bool, TraceOldGenTime, false,                                     \
          "Trace accumulated time for old collection")                      \
                                                                            \
  product(bool, PrintTenuringDistribution, false,                           \
          "Print tenuring age information")                                 \
                                                                            \
  product_rw(bool, PrintHeapAtGC, false,                                    \
          "Print heap layout before and after each GC")                     \
                                                                            \
  product_rw(bool, PrintHeapAtGCExtended, false,                            \
          "Print extended information about the layout of the heap "        \
          "when -XX:+PrintHeapAtGC is set")                                 \
                                                                            \
  product(bool, PrintHeapAtSIGBREAK, true,                                  \
          "Print heap layout in response to SIGBREAK")                      \
                                                                            \
  manageable(bool, PrintClassHistogramBeforeFullGC, false,                  \
          "Print a class histogram before any major stop-world GC")         \
                                                                            \
  manageable(bool, PrintClassHistogramAfterFullGC, false,                   \
          "Print a class histogram after any major stop-world GC")          \
                                                                            \
  manageable(bool, PrintClassHistogram, false,                              \
          "Print a histogram of class instances")                           \
                                                                            \
  develop(bool, TraceWorkGang, false,                                       \
          "Trace activities of work gangs")                                 \
                                                                            \
  product(bool, TraceParallelOldGCTasks, false,                             \
          "Trace multithreaded GC activity")                                \
                                                                            \
  develop(bool, TraceBlockOffsetTable, false,                               \
          "Print BlockOffsetTable maps")                                    \
                                                                            \
  develop(bool, TraceCardTableModRefBS, false,                              \
          "Print CardTableModRefBS maps")                                   \
                                                                            \
  develop(bool, TraceGCTaskManager, false,                                  \
          "Trace actions of the GC task manager")                           \
                                                                            \
  develop(bool, TraceGCTaskQueue, false,                                    \
          "Trace actions of the GC task queues")                            \
                                                                            \
  diagnostic(bool, TraceGCTaskThread, false,                                \
          "Trace actions of the GC task threads")                           \
                                                                            \
  product(bool, PrintParallelOldGCPhaseTimes, false,                        \
          "Print the time taken by each phase in ParallelOldGC "            \
          "(PrintGCDetails must also be enabled)")                          \
                                                                            \
  develop(bool, TraceParallelOldGCMarkingPhase, false,                      \
          "Trace marking phase in ParallelOldGC")                           \
                                                                            \
  develop(bool, TraceParallelOldGCSummaryPhase, false,                      \
          "Trace summary phase in ParallelOldGC")                           \
                                                                            \
  develop(bool, TraceParallelOldGCCompactionPhase, false,                   \
          "Trace compaction phase in ParallelOldGC")                        \
                                                                            \
  develop(bool, TraceParallelOldGCDensePrefix, false,                       \
          "Trace dense prefix computation for ParallelOldGC")               \
                                                                            \
  develop(bool, IgnoreLibthreadGPFault, false,                              \
          "Suppress workaround for libthread GP fault")                     \
                                                                            \
  product(bool, PrintJNIGCStalls, false,                                    \
          "Print diagnostic message when GC is stalled "                    \
          "by JNI critical section")                                        \
                                                                            \
  experimental(double, ObjectCountCutOffPercent, 0.5,                       \
          "The percentage of the used heap that the instances of a class "  \
          "must occupy for the class to generate a trace event")            \
                                                                            \
  /* GC log rotation setting */                                             \
                                                                            \
  product(bool, UseGCLogFileRotation, false,                                \
          "Rotate gclog files (for long running applications). It requires "\
          "-Xloggc:<filename>")                                             \
                                                                            \
  product(uintx, NumberOfGCLogFiles, 0,                                     \
          "Number of gclog files in rotation "                              \
          "(default: 0, no rotation)")                                      \
                                                                            \
  product(size_t, GCLogFileSize, 8*K,                                       \
          "GC log file size, requires UseGCLogFileRotation. "               \
          "Set to 0 to only trigger rotation via jcmd")                     \
                                                                            \
  /* JVMTI heap profiling */                                                \
                                                                            \
  diagnostic(bool, TraceJVMTIObjectTagging, false,                          \
          "Trace JVMTI object tagging calls")                               \
                                                                            \
  diagnostic(bool, VerifyBeforeIteration, false,                            \
          "Verify memory system before JVMTI iteration")                    \
                                                                            \
  /* compiler interface */                                                  \
                                                                            \
  develop(bool, CIPrintCompilerName, false,                                 \
          "when CIPrint is active, print the name of the active compiler")  \
                                                                            \
  diagnostic(bool, CIPrintCompileQueue, false,                              \
          "display the contents of the compile queue whenever a "           \
          "compilation is enqueued")                                        \
                                                                            \
  develop(bool, CIPrintRequests, false,                                     \
          "display every request for compilation")                          \
                                                                            \
  product(bool, CITime, false,                                              \
          "collect timing information for compilation")                     \
                                                                            \
  develop(bool, CITimeVerbose, false,                                       \
          "be more verbose in compilation timings")                         \
                                                                            \
  develop(bool, CITimeEach, false,                                          \
          "display timing information after each successful compilation")   \
                                                                            \
  develop(bool, CICountOSR, false,                                          \
          "use a separate counter when assigning ids to osr compilations")  \
                                                                            \
  develop(bool, CICompileNatives, true,                                     \
          "compile native methods if supported by the compiler")            \
                                                                            \
  develop_pd(bool, CICompileOSR,                                            \
          "compile on stack replacement methods if supported by the "       \
          "compiler")                                                       \
                                                                            \
  develop(bool, CIPrintMethodCodes, false,                                  \
          "print method bytecodes of the compiled code")                    \
                                                                            \
  develop(bool, CIPrintTypeFlow, false,                                     \
          "print the results of ciTypeFlow analysis")                       \
                                                                            \
  develop(bool, CITraceTypeFlow, false,                                     \
          "detailed per-bytecode tracing of ciTypeFlow analysis")           \
                                                                            \
  develop(intx, OSROnlyBCI, -1,                                             \
          "OSR only at this bci.  Negative values mean exclude that bci")   \
                                                                            \
  /* compiler */                                                            \
                                                                            \
  /* notice: the max range value here is max_jint, not max_intx  */         \
  /* because of overflow issue                                   */         \
  product(intx, CICompilerCount, CI_COMPILER_COUNT,                         \
          "Number of compiler threads to run")                              \
          range(0, max_jint)                                                \
          constraint(CICompilerCountConstraintFunc, AtParse)                \
                                                                            \
  product(intx, CompilationPolicyChoice, 0,                                 \
          "which compilation policy (0-3)")                                 \
          range(0, 3)                                                       \
                                                                            \
  develop(bool, UseStackBanging, true,                                      \
          "use stack banging for stack overflow checks (required for "      \
          "proper StackOverflow handling; disable only to measure cost "    \
          "of stackbanging)")                                               \
                                                                            \
  develop(bool, UseStrictFP, true,                                          \
          "use strict fp if modifier strictfp is set")                      \
                                                                            \
  develop(bool, GenerateSynchronizationCode, true,                          \
          "generate locking/unlocking code for synchronized methods and "   \
          "monitors")                                                       \
                                                                            \
  develop(bool, GenerateCompilerNullChecks, true,                           \
          "Generate explicit null checks for loads/stores/calls")           \
                                                                            \
  develop(bool, GenerateRangeChecks, true,                                  \
          "Generate range checks for array accesses")                       \
                                                                            \
  develop_pd(bool, ImplicitNullChecks,                                      \
          "Generate code for implicit null checks")                         \
                                                                            \
  product_pd(bool, TrapBasedNullChecks,                                     \
          "Generate code for null checks that uses a cmp and trap "         \
          "instruction raising SIGTRAP.  This is only used if an access to" \
          "null (+offset) will not raise a SIGSEGV, i.e.,"                  \
          "ImplicitNullChecks don't work (PPC64).")                         \
                                                                            \
  product(bool, PrintSafepointStatistics, false,                            \
          "Print statistics about safepoint synchronization")               \
                                                                            \
  product(intx, PrintSafepointStatisticsCount, 300,                         \
          "Total number of safepoint statistics collected "                 \
          "before printing them out")                                       \
                                                                            \
  product(intx, PrintSafepointStatisticsTimeout,  -1,                       \
          "Print safepoint statistics only when safepoint takes "           \
          "more than PrintSafepointSatisticsTimeout in millis")             \
                                                                            \
  product(bool, TraceSafepointCleanupTime, false,                           \
          "Print the break down of clean up tasks performed during "        \
          "safepoint")                                                      \
                                                                            \
  product(bool, Inline, true,                                               \
          "Enable inlining")                                                \
                                                                            \
  product(bool, ClipInlining, true,                                         \
          "Clip inlining if aggregate method exceeds DesiredMethodLimit")   \
                                                                            \
  develop(bool, UseCHA, true,                                               \
          "Enable CHA")                                                     \
                                                                            \
  product(bool, UseTypeProfile, true,                                       \
          "Check interpreter profile for historically monomorphic calls")   \
                                                                            \
  diagnostic(bool, PrintInlining, false,                                    \
          "Print inlining optimizations")                                   \
                                                                            \
  product(bool, UsePopCountInstruction, false,                              \
          "Use population count instruction")                               \
                                                                            \
  develop(bool, EagerInitialization, false,                                 \
          "Eagerly initialize classes if possible")                         \
                                                                            \
  diagnostic(bool, LogTouchedMethods, false,                                \
          "Log methods which have been ever touched in runtime")            \
                                                                            \
  diagnostic(bool, PrintTouchedMethodsAtExit, false,                        \
          "Print all methods that have been ever touched in runtime")       \
                                                                            \
  develop(bool, TraceMethodReplacement, false,                              \
          "Print when methods are replaced do to recompilation")            \
                                                                            \
  develop(bool, PrintMethodFlushing, false,                                 \
          "Print the nmethods being flushed")                               \
                                                                            \
  diagnostic(bool, PrintMethodFlushingStatistics, false,                    \
          "print statistics about method flushing")                         \
                                                                            \
  diagnostic(intx, HotMethodDetectionLimit, 100000,                         \
          "Number of compiled code invocations after which "                \
          "the method is considered as hot by the flusher")                 \
                                                                            \
  diagnostic(intx, MinPassesBeforeFlush, 10,                                \
          "Minimum number of sweeper passes before an nmethod "             \
          "can be flushed")                                                 \
                                                                            \
  product(bool, UseCodeAging, true,                                         \
          "Insert counter to detect warm methods")                          \
                                                                            \
  diagnostic(bool, StressCodeAging, false,                                  \
          "Start with counters compiled in")                                \
                                                                            \
  develop(bool, UseRelocIndex, false,                                       \
          "Use an index to speed random access to relocations")             \
                                                                            \
  develop(bool, StressCodeBuffers, false,                                   \
          "Exercise code buffer expansion and other rare state changes")    \
                                                                            \
  diagnostic(bool, DebugNonSafepoints, trueInDebug,                         \
          "Generate extra debugging information for non-safepoints in "     \
          "nmethods")                                                       \
                                                                            \
  product(bool, PrintVMOptions, false,                                      \
          "Print flags that appeared on the command line")                  \
                                                                            \
  product(bool, IgnoreUnrecognizedVMOptions, false,                         \
          "Ignore unrecognized VM options")                                 \
                                                                            \
  product(bool, PrintCommandLineFlags, false,                               \
          "Print flags specified on command line or set by ergonomics")     \
                                                                            \
  product(bool, PrintFlagsInitial, false,                                   \
          "Print all VM flags before argument processing and exit VM")      \
                                                                            \
  product(bool, PrintFlagsFinal, false,                                     \
          "Print all VM flags after argument and ergonomic processing")     \
                                                                            \
  notproduct(bool, PrintFlagsWithComments, false,                           \
          "Print all VM flags with default values and descriptions and "    \
          "exit")                                                           \
                                                                            \
  product(bool, PrintFlagsRanges, false,                                    \
          "Print VM flags and their ranges and exit VM")                    \
                                                                            \
  diagnostic(bool, SerializeVMOutput, true,                                 \
          "Use a mutex to serialize output to tty and LogFile")             \
                                                                            \
  diagnostic(bool, DisplayVMOutput, true,                                   \
          "Display all VM output on the tty, independently of LogVMOutput") \
                                                                            \
  diagnostic(bool, LogVMOutput, false,                                      \
          "Save VM output to LogFile")                                      \
                                                                            \
  diagnostic(ccstr, LogFile, NULL,                                          \
          "If LogVMOutput or LogCompilation is on, save VM output to "      \
          "this file [default: ./hotspot_pid%p.log] (%p replaced with pid)")\
                                                                            \
  product(ccstr, ErrorFile, NULL,                                           \
          "If an error occurs, save the error data to this file "           \
          "[default: ./hs_err_pid%p.log] (%p replaced with pid)")           \
                                                                            \
  product(bool, DisplayVMOutputToStderr, false,                             \
          "If DisplayVMOutput is true, display all VM output to stderr")    \
                                                                            \
  product(bool, DisplayVMOutputToStdout, false,                             \
          "If DisplayVMOutput is true, display all VM output to stdout")    \
                                                                            \
  product(bool, UseHeavyMonitors, false,                                    \
          "use heavyweight instead of lightweight Java monitors")           \
                                                                            \
  product(bool, PrintStringTableStatistics, false,                          \
          "print statistics about the StringTable and SymbolTable")         \
                                                                            \
  diagnostic(bool, VerifyStringTableAtExit, false,                          \
          "verify StringTable contents at exit")                            \
                                                                            \
  notproduct(bool, PrintSymbolTableSizeHistogram, false,                    \
          "print histogram of the symbol table")                            \
                                                                            \
  notproduct(bool, ExitVMOnVerifyError, false,                              \
          "standard exit from VM if bytecode verify error "                 \
          "(only in debug mode)")                                           \
                                                                            \
  notproduct(ccstr, AbortVMOnException, NULL,                               \
          "Call fatal if this exception is thrown.  Example: "              \
          "java -XX:AbortVMOnException=java.lang.NullPointerException Foo") \
                                                                            \
  notproduct(ccstr, AbortVMOnExceptionMessage, NULL,                        \
          "Call fatal if the exception pointed by AbortVMOnException "      \
          "has this message")                                               \
                                                                            \
  develop(bool, DebugVtables, false,                                        \
          "add debugging code to vtable dispatch")                          \
                                                                            \
  develop(bool, PrintVtables, false,                                        \
          "print vtables when printing klass")                              \
                                                                            \
  notproduct(bool, PrintVtableStats, false,                                 \
          "print vtables stats at end of run")                              \
                                                                            \
  develop(bool, TraceCreateZombies, false,                                  \
          "trace creation of zombie nmethods")                              \
                                                                            \
  notproduct(bool, IgnoreLockingAssertions, false,                          \
          "disable locking assertions (for speed)")                         \
                                                                            \
  product(bool, RangeCheckElimination, true,                                \
          "Eliminate range checks")                                         \
                                                                            \
  develop_pd(bool, UncommonNullCast,                                        \
          "track occurrences of null in casts; adjust compiler tactics")    \
                                                                            \
  develop(bool, TypeProfileCasts,  true,                                    \
          "treat casts like calls for purposes of type profiling")          \
                                                                            \
  develop(bool, DelayCompilationDuringStartup, true,                        \
          "Delay invoking the compiler until main application class is "    \
          "loaded")                                                         \
                                                                            \
  develop(bool, CompileTheWorld, false,                                     \
          "Compile all methods in all classes in bootstrap class path "     \
          "(stress test)")                                                  \
                                                                            \
  develop(bool, CompileTheWorldPreloadClasses, true,                        \
          "Preload all classes used by a class before start loading")       \
                                                                            \
  notproduct(intx, CompileTheWorldSafepointInterval, 100,                   \
          "Force a safepoint every n compiles so sweeper can keep up")      \
                                                                            \
  develop(bool, FillDelaySlots, true,                                       \
          "Fill delay slots (on SPARC only)")                               \
                                                                            \
  develop(bool, TimeLivenessAnalysis, false,                                \
          "Time computation of bytecode liveness analysis")                 \
                                                                            \
  develop(bool, TraceLivenessGen, false,                                    \
          "Trace the generation of liveness analysis information")          \
                                                                            \
  notproduct(bool, TraceLivenessQuery, false,                               \
          "Trace queries of liveness analysis information")                 \
                                                                            \
  notproduct(bool, CollectIndexSetStatistics, false,                        \
          "Collect information about IndexSets")                            \
                                                                            \
  develop(bool, UseLoopSafepoints, true,                                    \
          "Generate Safepoint nodes in every loop")                         \
                                                                            \
  develop(intx, FastAllocateSizeLimit, 128*K,                               \
          /* Note:  This value is zero mod 1<<13 for a cheap sparc set. */  \
          "Inline allocations larger than this in doublewords must go slow")\
                                                                            \
  product(bool, AggressiveOpts, false,                                      \
          "Enable aggressive optimizations - see arguments.cpp")            \
                                                                            \
  product_pd(uintx, TypeProfileLevel,                                       \
          "=XYZ, with Z: Type profiling of arguments at call; "             \
                     "Y: Type profiling of return value at call; "          \
                     "X: Type profiling of parameters to methods; "         \
          "X, Y and Z in 0=off ; 1=jsr292 only; 2=all methods")             \
                                                                            \
  product(intx, TypeProfileArgsLimit,     2,                                \
          "max number of call arguments to consider for type profiling")    \
                                                                            \
  product(intx, TypeProfileParmsLimit,    2,                                \
          "max number of incoming parameters to consider for type profiling"\
          ", -1 for all")                                                   \
                                                                            \
  /* statistics */                                                          \
  develop(bool, CountCompiledCalls, false,                                  \
          "Count method invocations")                                       \
                                                                            \
  notproduct(bool, CountRuntimeCalls, false,                                \
          "Count VM runtime calls")                                         \
                                                                            \
  develop(bool, CountJNICalls, false,                                       \
          "Count jni method invocations")                                   \
                                                                            \
  notproduct(bool, CountJVMCalls, false,                                    \
          "Count jvm method invocations")                                   \
                                                                            \
  notproduct(bool, CountRemovableExceptions, false,                         \
          "Count exceptions that could be replaced by branches due to "     \
          "inlining")                                                       \
                                                                            \
  notproduct(bool, ICMissHistogram, false,                                  \
          "Produce histogram of IC misses")                                 \
                                                                            \
  /* interpreter */                                                         \
  develop(bool, ClearInterpreterLocals, false,                              \
          "Always clear local variables of interpreter activations upon "   \
          "entry")                                                          \
                                                                            \
  product_pd(bool, RewriteBytecodes,                                        \
          "Allow rewriting of bytecodes (bytecodes are not immutable)")     \
                                                                            \
  product_pd(bool, RewriteFrequentPairs,                                    \
          "Rewrite frequently used bytecode pairs into a single bytecode")  \
                                                                            \
  diagnostic(bool, PrintInterpreter, false,                                 \
          "Print the generated interpreter code")                           \
                                                                            \
  product(bool, UseInterpreter, true,                                       \
          "Use interpreter for non-compiled methods")                       \
                                                                            \
  develop(bool, UseFastSignatureHandlers, true,                             \
          "Use fast signature handlers for native calls")                   \
                                                                            \
  product(bool, UseLoopCounter, true,                                       \
          "Increment invocation counter on backward branch")                \
                                                                            \
  product_pd(bool, UseOnStackReplacement,                                   \
          "Use on stack replacement, calls runtime if invoc. counter "      \
          "overflows in loop")                                              \
                                                                            \
  notproduct(bool, TraceOnStackReplacement, false,                          \
          "Trace on stack replacement")                                     \
                                                                            \
  product_pd(bool, PreferInterpreterNativeStubs,                            \
          "Use always interpreter stubs for native methods invoked via "    \
          "interpreter")                                                    \
                                                                            \
  develop(bool, CountBytecodes, false,                                      \
          "Count number of bytecodes executed")                             \
                                                                            \
  develop(bool, PrintBytecodeHistogram, false,                              \
          "Print histogram of the executed bytecodes")                      \
                                                                            \
  develop(bool, PrintBytecodePairHistogram, false,                          \
          "Print histogram of the executed bytecode pairs")                 \
                                                                            \
  diagnostic(bool, PrintSignatureHandlers, false,                           \
          "Print code generated for native method signature handlers")      \
                                                                            \
  develop(bool, VerifyOops, false,                                          \
          "Do plausibility checks for oops")                                \
                                                                            \
  develop(bool, CheckUnhandledOops, false,                                  \
          "Check for unhandled oops in VM code")                            \
                                                                            \
  develop(bool, VerifyJNIFields, trueInDebug,                               \
          "Verify jfieldIDs for instance fields")                           \
                                                                            \
  notproduct(bool, VerifyJNIEnvThread, false,                               \
          "Verify JNIEnv.thread == Thread::current() when entering VM "     \
          "from JNI")                                                       \
                                                                            \
  develop(bool, VerifyFPU, false,                                           \
          "Verify FPU state (check for NaN's, etc.)")                       \
                                                                            \
  develop(bool, VerifyThread, false,                                        \
          "Watch the thread register for corruption (SPARC only)")          \
                                                                            \
  develop(bool, VerifyActivationFrameSize, false,                           \
          "Verify that activation frame didn't become smaller than its "    \
          "minimal size")                                                   \
                                                                            \
  develop(bool, TraceFrequencyInlining, false,                              \
          "Trace frequency based inlining")                                 \
                                                                            \
  develop_pd(bool, InlineIntrinsics,                                        \
          "Inline intrinsics that can be statically resolved")              \
                                                                            \
  product_pd(bool, ProfileInterpreter,                                      \
          "Profile at the bytecode level during interpretation")            \
                                                                            \
  develop(bool, TraceProfileInterpreter, false,                             \
          "Trace profiling at the bytecode level during interpretation. "   \
          "This outputs the profiling information collected to improve "    \
          "jit compilation.")                                               \
                                                                            \
  develop_pd(bool, ProfileTraps,                                            \
          "Profile deoptimization traps at the bytecode level")             \
                                                                            \
  product(intx, ProfileMaturityPercentage, 20,                              \
          "number of method invocations/branches (expressed as % of "       \
          "CompileThreshold) before using the method's profile")            \
          range(0, 100)                                                     \
                                                                            \
  diagnostic(bool, PrintMethodData, false,                                  \
          "Print the results of +ProfileInterpreter at end of run")         \
                                                                            \
  develop(bool, VerifyDataPointer, trueInDebug,                             \
          "Verify the method data pointer during interpreter profiling")    \
                                                                            \
  develop(bool, VerifyCompiledCode, false,                                  \
          "Include miscellaneous runtime verifications in nmethod code; "   \
          "default off because it disturbs nmethod size heuristics")        \
                                                                            \
  notproduct(bool, CrashGCForDumpingJavaThread, false,                      \
          "Manually make GC thread crash then dump java stack trace;  "     \
          "Test only")                                                      \
                                                                            \
  /* compilation */                                                         \
  product(bool, UseCompiler, true,                                          \
          "Use Just-In-Time compilation")                                   \
                                                                            \
  develop(bool, TraceCompilationPolicy, false,                              \
          "Trace compilation policy")                                       \
                                                                            \
  develop(bool, TimeCompilationPolicy, false,                               \
          "Time the compilation policy")                                    \
                                                                            \
  product(bool, UseCounterDecay, true,                                      \
          "Adjust recompilation counters")                                  \
                                                                            \
  develop(intx, CounterHalfLifeTime,    30,                                 \
          "Half-life time of invocation counters (in seconds)")             \
                                                                            \
  develop(intx, CounterDecayMinIntervalLength,   500,                       \
          "The minimum interval (in milliseconds) between invocation of "   \
          "CounterDecay")                                                   \
                                                                            \
  product(bool, AlwaysCompileLoopMethods, false,                            \
          "When using recompilation, never interpret methods "              \
          "containing loops")                                               \
                                                                            \
  product(bool, DontCompileHugeMethods, true,                               \
          "Do not compile methods > HugeMethodLimit")                       \
                                                                            \
  /* Bytecode escape analysis estimation. */                                \
  product(bool, EstimateArgEscape, true,                                    \
          "Analyze bytecodes to estimate escape state of arguments")        \
                                                                            \
  product(intx, BCEATraceLevel, 0,                                          \
          "How much tracing to do of bytecode escape analysis estimates")   \
                                                                            \
  product(intx, MaxBCEAEstimateLevel, 5,                                    \
          "Maximum number of nested calls that are analyzed by BC EA")      \
                                                                            \
  product(intx, MaxBCEAEstimateSize, 150,                                   \
          "Maximum bytecode size of a method to be analyzed by BC EA")      \
                                                                            \
  product(intx,  AllocatePrefetchStyle, 1,                                  \
          "0 = no prefetch, "                                               \
          "1 = prefetch instructions for each allocation, "                 \
          "2 = use TLAB watermark to gate allocation prefetch, "            \
          "3 = use BIS instruction on Sparc for allocation prefetch")       \
          range(0, 3)                                                       \
                                                                            \
  product(intx,  AllocatePrefetchDistance, -1,                              \
          "Distance to prefetch ahead of allocation pointer")               \
                                                                            \
  product(intx,  AllocatePrefetchLines, 3,                                  \
          "Number of lines to prefetch ahead of array allocation pointer")  \
                                                                            \
  product(intx,  AllocateInstancePrefetchLines, 1,                          \
          "Number of lines to prefetch ahead of instance allocation "       \
          "pointer")                                                        \
                                                                            \
  product(intx,  AllocatePrefetchStepSize, 16,                              \
          "Step size in bytes of sequential prefetch instructions")         \
                                                                            \
  product(intx,  AllocatePrefetchInstr, 0,                                  \
          "Prefetch instruction to prefetch ahead of allocation pointer")   \
                                                                            \
  /* deoptimization */                                                      \
  develop(bool, TraceDeoptimization, false,                                 \
          "Trace deoptimization")                                           \
                                                                            \
  develop(bool, DebugDeoptimization, false,                                 \
          "Tracing various information while debugging deoptimization")     \
                                                                            \
  product(intx, SelfDestructTimer, 0,                                       \
          "Will cause VM to terminate after a given time (in minutes) "     \
          "(0 means off)")                                                  \
                                                                            \
  product(intx, MaxJavaStackTraceDepth, 1024,                               \
          "The maximum number of lines in the stack trace for Java "        \
          "exceptions (0 means all)")                                       \
                                                                            \
  NOT_EMBEDDED(diagnostic(intx, GuaranteedSafepointInterval, 1000,          \
          "Guarantee a safepoint (at least) every so many milliseconds "    \
          "(0 means none)"))                                                \
                                                                            \
  EMBEDDED_ONLY(product(intx, GuaranteedSafepointInterval, 0,               \
          "Guarantee a safepoint (at least) every so many milliseconds "    \
          "(0 means none)"))                                                \
                                                                            \
  product(intx, SafepointTimeoutDelay, 10000,                               \
          "Delay in milliseconds for option SafepointTimeout")              \
                                                                            \
  product(intx, NmethodSweepActivity, 10,                                   \
          "Removes cold nmethods from code cache if > 0. Higher values "    \
          "result in more aggressive sweeping")                             \
          range(0, 2000)                                                    \
                                                                            \
  notproduct(bool, LogSweeper, false,                                       \
          "Keep a ring buffer of sweeper activity")                         \
                                                                            \
  notproduct(intx, SweeperLogEntries, 1024,                                 \
          "Number of records in the ring buffer of sweeper activity")       \
                                                                            \
  notproduct(intx, MemProfilingInterval, 500,                               \
          "Time between each invocation of the MemProfiler")                \
                                                                            \
  develop(intx, MallocCatchPtr, -1,                                         \
          "Hit breakpoint when mallocing/freeing this pointer")             \
                                                                            \
  notproduct(ccstrlist, SuppressErrorAt, "",                                \
          "List of assertions (file:line) to muzzle")                       \
                                                                            \
  notproduct(size_t, HandleAllocationLimit, 1024,                           \
          "Threshold for HandleMark allocation when +TraceHandleAllocation "\
          "is used")                                                        \
                                                                            \
  develop(size_t, TotalHandleAllocationLimit, 1024,                         \
          "Threshold for total handle allocation when "                     \
          "+TraceHandleAllocation is used")                                 \
                                                                            \
  develop(intx, StackPrintLimit, 100,                                       \
          "number of stack frames to print in VM-level stack dump")         \
                                                                            \
  notproduct(intx, MaxElementPrintSize, 256,                                \
          "maximum number of elements to print")                            \
                                                                            \
  notproduct(intx, MaxSubklassPrintSize, 4,                                 \
          "maximum number of subklasses to print when printing klass")      \
                                                                            \
  product(intx, MaxInlineLevel, 9,                                          \
          "maximum number of nested calls that are inlined")                \
                                                                            \
  product(intx, MaxRecursiveInlineLevel, 1,                                 \
          "maximum number of nested recursive calls that are inlined")      \
                                                                            \
  develop(intx, MaxForceInlineLevel, 100,                                   \
          "maximum number of nested calls that are forced for inlining "    \
          "(using CompilerOracle or marked w/ @ForceInline)")               \
                                                                            \
  product_pd(intx, InlineSmallCode,                                         \
          "Only inline already compiled methods if their code size is "     \
          "less than this")                                                 \
                                                                            \
  product(intx, MaxInlineSize, 35,                                          \
          "The maximum bytecode size of a method to be inlined")            \
                                                                            \
  product_pd(intx, FreqInlineSize,                                          \
          "The maximum bytecode size of a frequent method to be inlined")   \
                                                                            \
  product(intx, MaxTrivialSize, 6,                                          \
          "The maximum bytecode size of a trivial method to be inlined")    \
                                                                            \
  product(intx, MinInliningThreshold, 250,                                  \
          "The minimum invocation count a method needs to have to be "      \
          "inlined")                                                        \
                                                                            \
  develop(intx, MethodHistogramCutoff, 100,                                 \
          "The cutoff value for method invocation histogram (+CountCalls)") \
                                                                            \
  develop(intx, ProfilerNumberOfInterpretedMethods, 25,                     \
          "Number of interpreted methods to show in profile")               \
                                                                            \
  develop(intx, ProfilerNumberOfCompiledMethods, 25,                        \
          "Number of compiled methods to show in profile")                  \
                                                                            \
  develop(intx, ProfilerNumberOfStubMethods, 25,                            \
          "Number of stub methods to show in profile")                      \
                                                                            \
  develop(intx, ProfilerNumberOfRuntimeStubNodes, 25,                       \
          "Number of runtime stub nodes to show in profile")                \
                                                                            \
  product(intx, ProfileIntervalsTicks, 100,                                 \
          "Number of ticks between printing of interval profile "           \
          "(+ProfileIntervals)")                                            \
                                                                            \
  notproduct(intx, ScavengeALotInterval,     1,                             \
          "Interval between which scavenge will occur with +ScavengeALot")  \
                                                                            \
  notproduct(intx, FullGCALotInterval,     1,                               \
          "Interval between which full gc will occur with +FullGCALot")     \
                                                                            \
  notproduct(intx, FullGCALotStart,     0,                                  \
          "For which invocation to start FullGCAlot")                       \
                                                                            \
  notproduct(intx, FullGCALotDummies,  32*K,                                \
          "Dummy object allocated with +FullGCALot, forcing all objects "   \
          "to move")                                                        \
                                                                            \
  develop(intx, DontYieldALotInterval,    10,                               \
          "Interval between which yields will be dropped (milliseconds)")   \
                                                                            \
  develop(intx, MinSleepInterval,     1,                                    \
          "Minimum sleep() interval (milliseconds) when "                   \
          "ConvertSleepToYield is off (used for Solaris)")                  \
                                                                            \
  develop(intx, ProfilerPCTickThreshold,    15,                             \
          "Number of ticks in a PC buckets to be a hotspot")                \
                                                                            \
  notproduct(intx, DeoptimizeALotInterval,     5,                           \
          "Number of exits until DeoptimizeALot kicks in")                  \
                                                                            \
  notproduct(intx, ZombieALotInterval,     5,                               \
          "Number of exits until ZombieALot kicks in")                      \
                                                                            \
  diagnostic(intx, MallocVerifyInterval,     0,                             \
          "If non-zero, verify C heap after every N calls to "              \
          "malloc/realloc/free")                                            \
                                                                            \
  diagnostic(intx, MallocVerifyStart,     0,                                \
          "If non-zero, start verifying C heap after Nth call to "          \
          "malloc/realloc/free")                                            \
                                                                            \
  diagnostic(uintx, MallocMaxTestWords,     0,                              \
          "If non-zero, maximum number of words that malloc/realloc can "   \
          "allocate (for testing only)")                                    \
                                                                            \
  product(intx, TypeProfileWidth,     2,                                    \
          "Number of receiver types to record in call/cast profile")        \
                                                                            \
  develop(intx, BciProfileWidth,      2,                                    \
          "Number of return bci's to record in ret profile")                \
                                                                            \
  product(intx, PerMethodRecompilationCutoff, 400,                          \
          "After recompiling N times, stay in the interpreter (-1=>'Inf')") \
          range(-1, max_intx)                                               \
                                                                            \
  product(intx, PerBytecodeRecompilationCutoff, 200,                        \
          "Per-BCI limit on repeated recompilation (-1=>'Inf')")            \
          range(-1, max_intx)                                               \
                                                                            \
  product(intx, PerMethodTrapLimit,  100,                                   \
          "Limit on traps (of one kind) in a method (includes inlines)")    \
                                                                            \
  experimental(intx, PerMethodSpecTrapLimit,  5000,                         \
          "Limit on speculative traps (of one kind) in a method "           \
          "(includes inlines)")                                             \
                                                                            \
  product(intx, PerBytecodeTrapLimit,  4,                                   \
          "Limit on traps (of one kind) at a particular BCI")               \
                                                                            \
  experimental(intx, SpecTrapLimitExtraEntries,  3,                         \
          "Extra method data trap entries for speculation")                 \
                                                                            \
  develop(intx, InlineFrequencyRatio,    20,                                \
          "Ratio of call site execution to caller method invocation")       \
                                                                            \
  develop_pd(intx, InlineFrequencyCount,                                    \
          "Count of call site execution necessary to trigger frequent "     \
          "inlining")                                                       \
                                                                            \
  develop(intx, InlineThrowCount,    50,                                    \
          "Force inlining of interpreted methods that throw this often")    \
                                                                            \
  develop(intx, InlineThrowMaxSize,   200,                                  \
          "Force inlining of throwing methods smaller than this")           \
                                                                            \
  develop(intx, ProfilerNodeSize,  1024,                                    \
          "Size in K to allocate for the Profile Nodes of each thread")     \
                                                                            \
  /* gc parameters */                                                       \
  product(size_t, InitialHeapSize, 0,                                       \
          "Initial heap size (in bytes); zero means use ergonomics")        \
                                                                            \
  product(size_t, MaxHeapSize, ScaleForWordSize(96*M),                      \
          "Maximum heap size (in bytes)")                                   \
                                                                            \
  product(size_t, OldSize, ScaleForWordSize(4*M),                           \
          "Initial tenured generation size (in bytes)")                     \
                                                                            \
  product(size_t, NewSize, ScaleForWordSize(1*M),                           \
          "Initial new generation size (in bytes)")                         \
                                                                            \
  product(size_t, MaxNewSize, max_uintx,                                    \
          "Maximum new generation size (in bytes), max_uintx means set "    \
          "ergonomically")                                                  \
                                                                            \
  product(size_t, PretenureSizeThreshold, 0,                                \
          "Maximum size in bytes of objects allocated in DefNew "           \
          "generation; zero means no maximum")                              \
                                                                            \
  product(size_t, TLABSize, 0,                                              \
          "Starting TLAB size (in bytes); zero means set ergonomically")    \
                                                                            \
  product(size_t, MinTLABSize, 2*K,                                         \
          "Minimum allowed TLAB size (in bytes)")                           \
          range(1, max_uintx)                                               \
                                                                            \
  product(uintx, TLABAllocationWeight, 35,                                  \
          "Allocation averaging weight")                                    \
          range(0, 100)                                                     \
                                                                            \
  /* Limit the lower bound of this flag to 1 as it is used  */              \
  /* in a division expression.                              */              \
  product(uintx, TLABWasteTargetPercent, 1,                                 \
          "Percentage of Eden that can be wasted")                          \
          range(1, 100)                                                     \
                                                                            \
  product(uintx, TLABRefillWasteFraction,    64,                            \
          "Maximum TLAB waste at a refill (internal fragmentation)")        \
          range(1, max_uintx)                                               \
                                                                            \
  product(uintx, TLABWasteIncrement,    4,                                  \
          "Increment allowed waste at slow allocation")                     \
                                                                            \
  product(uintx, SurvivorRatio, 8,                                          \
          "Ratio of eden/survivor space size")                              \
                                                                            \
  product(uintx, NewRatio, 2,                                               \
          "Ratio of old/new generation sizes")                              \
                                                                            \
  product_pd(size_t, NewSizeThreadIncrease,                                 \
          "Additional size added to desired new generation size per "       \
          "non-daemon thread (in bytes)")                                   \
                                                                            \
  product_pd(size_t, MetaspaceSize,                                         \
          "Initial size of Metaspaces (in bytes)")                          \
                                                                            \
  product(size_t, MaxMetaspaceSize, max_uintx,                              \
          "Maximum size of Metaspaces (in bytes)")                          \
                                                                            \
  product(size_t, CompressedClassSpaceSize, 1*G,                            \
          "Maximum size of class area in Metaspace when compressed "        \
          "class pointers are used")                                        \
          range(1*M, 3*G)                                                   \
                                                                            \
  manageable(uintx, MinHeapFreeRatio, 40,                                   \
          "The minimum percentage of heap free after GC to avoid expansion."\
          " For most GCs this applies to the old generation. In G1 and"     \
          " ParallelGC it applies to the whole heap.")                      \
          range(0, 100)                                                     \
          constraint(MinHeapFreeRatioConstraintFunc,AfterErgo)              \
                                                                            \
  manageable(uintx, MaxHeapFreeRatio, 70,                                   \
          "The maximum percentage of heap free after GC to avoid shrinking."\
          " For most GCs this applies to the old generation. In G1 and"     \
          " ParallelGC it applies to the whole heap.")                      \
          range(0, 100)                                                     \
          constraint(MaxHeapFreeRatioConstraintFunc,AfterErgo)              \
                                                                            \
  product(intx, SoftRefLRUPolicyMSPerMB, 1000,                              \
          "Number of milliseconds per MB of free space in the heap")        \
                                                                            \
  product(size_t, MinHeapDeltaBytes, ScaleForWordSize(128*K),               \
          "The minimum change in heap space due to GC (in bytes)")          \
                                                                            \
  product(size_t, MinMetaspaceExpansion, ScaleForWordSize(256*K),           \
          "The minimum expansion of Metaspace (in bytes)")                  \
                                                                            \
  product(uintx, MaxMetaspaceFreeRatio,    70,                              \
          "The maximum percentage of Metaspace free after GC to avoid "     \
          "shrinking")                                                      \
          range(0, 100)                                                     \
          constraint(MaxMetaspaceFreeRatioConstraintFunc,AfterErgo)         \
                                                                            \
  product(uintx, MinMetaspaceFreeRatio,    40,                              \
          "The minimum percentage of Metaspace free after GC to avoid "     \
          "expansion")                                                      \
          range(0, 99)                                                      \
          constraint(MinMetaspaceFreeRatioConstraintFunc,AfterErgo)         \
                                                                            \
  product(size_t, MaxMetaspaceExpansion, ScaleForWordSize(4*M),             \
          "The maximum expansion of Metaspace without full GC (in bytes)")  \
                                                                            \
  product(uintx, QueuedAllocationWarningCount, 0,                           \
          "Number of times an allocation that queues behind a GC "          \
          "will retry before printing a warning")                           \
                                                                            \
  diagnostic(uintx, VerifyGCStartAt,   0,                                   \
          "GC invoke count where +VerifyBefore/AfterGC kicks in")           \
                                                                            \
  diagnostic(intx, VerifyGCLevel,     0,                                    \
          "Generation level at which to start +VerifyBefore/AfterGC")       \
                                                                            \
  product(uintx, MaxTenuringThreshold,    15,                               \
          "Maximum value for tenuring threshold")                           \
          range(0, markOopDesc::max_age + 1)                                \
          constraint(MaxTenuringThresholdConstraintFunc,AfterErgo)          \
                                                                            \
  product(uintx, InitialTenuringThreshold,    7,                            \
          "Initial value for tenuring threshold")                           \
          range(0, markOopDesc::max_age + 1)                                \
          constraint(InitialTenuringThresholdConstraintFunc,AfterErgo)      \
                                                                            \
  product(uintx, TargetSurvivorRatio,    50,                                \
          "Desired percentage of survivor space used after scavenge")       \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, MarkSweepDeadRatio,     5,                                 \
          "Percentage (0-100) of the old gen allowed as dead wood. "        \
          "Serial mark sweep treats this as both the minimum and maximum "  \
          "value. "                                                         \
          "CMS uses this value only if it falls back to mark sweep. "       \
          "Par compact uses a variable scale based on the density of the "  \
          "generation and treats this as the maximum value when the heap "  \
          "is either completely full or completely empty.  Par compact "    \
          "also has a smaller default value; see arguments.cpp.")           \
          range(0, 100)                                                     \
                                                                            \
  product(uintx, MarkSweepAlwaysCompactCount,     4,                        \
          "How often should we fully compact the heap (ignoring the dead "  \
          "space parameters)")                                              \
          range(1, max_uintx)                                               \
                                                                            \
  product(intx, PrintCMSStatistics, 0,                                      \
          "Statistics for CMS")                                             \
                                                                            \
  product(bool, PrintCMSInitiationStatistics, false,                        \
          "Statistics for initiating a CMS collection")                     \
                                                                            \
  product(intx, PrintFLSStatistics, 0,                                      \
          "Statistics for CMS' FreeListSpace")                              \
                                                                            \
  product(intx, PrintFLSCensus, 0,                                          \
          "Census for CMS' FreeListSpace")                                  \
                                                                            \
  develop(uintx, GCExpandToAllocateDelayMillis, 0,                          \
          "Delay between expansion and allocation (in milliseconds)")       \
                                                                            \
  develop(uintx, GCWorkerDelayMillis, 0,                                    \
          "Delay in scheduling GC workers (in milliseconds)")               \
                                                                            \
  product(intx, DeferThrSuspendLoopCount,     4000,                         \
          "(Unstable) Number of times to iterate in safepoint loop "        \
          "before blocking VM threads ")                                    \
                                                                            \
  product(intx, DeferPollingPageLoopCount,     -1,                          \
          "(Unsafe,Unstable) Number of iterations in safepoint loop "       \
          "before changing safepoint polling page to RO ")                  \
                                                                            \
  product(intx, SafepointSpinBeforeYield, 2000, "(Unstable)")               \
                                                                            \
  product(bool, PSChunkLargeArrays, true,                                   \
          "Process large arrays in chunks")                                 \
                                                                            \
  product(uintx, GCDrainStackTargetSize, 64,                                \
          "Number of entries we will try to leave on the stack "            \
          "during parallel gc")                                             \
                                                                            \
  /* stack parameters */                                                    \
  product_pd(intx, StackYellowPages,                                        \
          "Number of yellow zone (recoverable overflows) pages")            \
          range(1, max_intx)                                                \
                                                                            \
  product_pd(intx, StackRedPages,                                           \
          "Number of red zone (unrecoverable overflows) pages")             \
          range(1, max_intx)                                                \
                                                                            \
  /* greater stack shadow pages can't generate instruction to bang stack */ \
  product_pd(intx, StackShadowPages,                                        \
          "Number of shadow zone (for overflow checking) pages "            \
          "this should exceed the depth of the VM and native call stack")   \
          range(1, 50)                                                      \
                                                                            \
  product_pd(intx, ThreadStackSize,                                         \
          "Thread Stack Size (in Kbytes)")                                  \
                                                                            \
  product_pd(intx, VMThreadStackSize,                                       \
          "Non-Java Thread Stack Size (in Kbytes)")                         \
                                                                            \
  product_pd(intx, CompilerThreadStackSize,                                 \
          "Compiler Thread Stack Size (in Kbytes)")                         \
                                                                            \
  develop_pd(size_t, JVMInvokeMethodSlack,                                  \
          "Stack space (bytes) required for JVM_InvokeMethod to complete")  \
                                                                            \
  /* code cache parameters                                    */            \
  /* ppc64/tiered compilation has large code-entry alignment. */            \
  develop(uintx, CodeCacheSegmentSize, 64 PPC64_ONLY(+64) NOT_PPC64(TIERED_ONLY(+64)),\
          "Code cache segment size (in bytes) - smallest unit of "          \
          "allocation")                                                     \
          range(1, 1024)                                                    \
                                                                            \
  develop_pd(intx, CodeEntryAlignment,                                      \
          "Code entry alignment for generated code (in bytes)")             \
                                                                            \
  product_pd(intx, OptoLoopAlignment,                                       \
          "Align inner loops to zero relative to this modulus")             \
                                                                            \
  product_pd(uintx, InitialCodeCacheSize,                                   \
          "Initial code cache size (in bytes)")                             \
                                                                            \
  develop_pd(uintx, CodeCacheMinimumUseSpace,                               \
          "Minimum code cache size (in bytes) required to start VM.")       \
                                                                            \
  product(bool, SegmentedCodeCache, false,                                  \
          "Use a segmented code cache")                                     \
                                                                            \
  product_pd(uintx, ReservedCodeCacheSize,                                  \
          "Reserved code cache size (in bytes) - maximum code cache size")  \
                                                                            \
  product_pd(uintx, NonProfiledCodeHeapSize,                                \
          "Size of code heap with non-profiled methods (in bytes)")         \
                                                                            \
  product_pd(uintx, ProfiledCodeHeapSize,                                   \
          "Size of code heap with profiled methods (in bytes)")             \
                                                                            \
  product_pd(uintx, NonNMethodCodeHeapSize,                                 \
          "Size of code heap with non-nmethods (in bytes)")                 \
                                                                            \
  product_pd(uintx, CodeCacheExpansionSize,                                 \
          "Code cache expansion size (in bytes)")                           \
                                                                            \
  develop_pd(uintx, CodeCacheMinBlockLength,                                \
          "Minimum number of segments in a code cache block")               \
          range(1, 100)                                                     \
                                                                            \
  notproduct(bool, ExitOnFullCodeCache, false,                              \
          "Exit the VM if we fill the code cache")                          \
                                                                            \
  product(bool, UseCodeCacheFlushing, true,                                 \
          "Remove cold/old nmethods from the code cache")                   \
                                                                            \
  product(uintx, StartAggressiveSweepingAt, 10,                             \
          "Start aggressive sweeping if X[%] of the code cache is free."    \
          "Segmented code cache: X[%] of the non-profiled heap."            \
          "Non-segmented code cache: X[%] of the total code cache")         \
          range(0, 100)                                                     \
                                                                            \
  /* interpreter debugging */                                               \
  develop(intx, BinarySwitchThreshold, 5,                                   \
          "Minimal number of lookupswitch entries for rewriting to binary " \
          "switch")                                                         \
                                                                            \
  develop(intx, StopInterpreterAt, 0,                                       \
          "Stop interpreter execution at specified bytecode number")        \
                                                                            \
  develop(intx, TraceBytecodesAt, 0,                                        \
          "Trace bytecodes starting with specified bytecode number")        \
                                                                            \
  /* compiler interface */                                                  \
  develop(intx, CIStart, 0,                                                 \
          "The id of the first compilation to permit")                      \
                                                                            \
  develop(intx, CIStop, max_jint,                                           \
          "The id of the last compilation to permit")                       \
                                                                            \
  develop(intx, CIStartOSR, 0,                                              \
          "The id of the first osr compilation to permit "                  \
          "(CICountOSR must be on)")                                        \
                                                                            \
  develop(intx, CIStopOSR, max_jint,                                        \
          "The id of the last osr compilation to permit "                   \
          "(CICountOSR must be on)")                                        \
                                                                            \
  develop(intx, CIBreakAtOSR, -1,                                           \
          "The id of osr compilation to break at")                          \
                                                                            \
  develop(intx, CIBreakAt, -1,                                              \
          "The id of compilation to break at")                              \
                                                                            \
  product(ccstrlist, CompileOnly, "",                                       \
          "List of methods (pkg/class.name) to restrict compilation to")    \
                                                                            \
  product(ccstr, CompileCommandFile, NULL,                                  \
          "Read compiler commands from this file [.hotspot_compiler]")      \
                                                                            \
  product(ccstrlist, CompileCommand, "",                                    \
          "Prepend to .hotspot_compiler; e.g. log,java/lang/String.<init>") \
                                                                            \
  develop(bool, ReplayCompiles, false,                                      \
          "Enable replay of compilations from ReplayDataFile")              \
                                                                            \
  product(ccstr, ReplayDataFile, NULL,                                      \
          "File containing compilation replay information"                  \
          "[default: ./replay_pid%p.log] (%p replaced with pid)")           \
                                                                            \
   product(ccstr, InlineDataFile, NULL,                                     \
          "File containing inlining replay information"                     \
          "[default: ./inline_pid%p.log] (%p replaced with pid)")           \
                                                                            \
  develop(intx, ReplaySuppressInitializers, 2,                              \
          "Control handling of class initialization during replay: "        \
          "0 - don't do anything special; "                                 \
          "1 - treat all class initializers as empty; "                     \
          "2 - treat class initializers for application classes as empty; " \
          "3 - allow all class initializers to run during bootstrap but "   \
          "    pretend they are empty after starting replay")               \
          range(0, 3)                                                       \
                                                                            \
  develop(bool, ReplayIgnoreInitErrors, false,                              \
          "Ignore exceptions thrown during initialization for replay")      \
                                                                            \
  product(bool, DumpReplayDataOnError, true,                                \
          "Record replay data for crashing compiler threads")               \
                                                                            \
  product(bool, CICompilerCountPerCPU, false,                               \
          "1 compiler thread for log(N CPUs)")                              \
                                                                            \
  develop(intx, CIFireOOMAt,    -1,                                         \
          "Fire OutOfMemoryErrors throughout CI for testing the compiler "  \
          "(non-negative value throws OOM after this many CI accesses "     \
          "in each compile)")                                               \
  notproduct(intx, CICrashAt, -1,                                           \
          "id of compilation to trigger assert in compiler thread for "     \
          "the purpose of testing, e.g. generation of replay data")         \
  notproduct(bool, CIObjectFactoryVerify, false,                            \
          "enable potentially expensive verification in ciObjectFactory")   \
                                                                            \
  /* Priorities */                                                          \
  product_pd(bool, UseThreadPriorities,  "Use native thread priorities")    \
                                                                            \
  product(intx, ThreadPriorityPolicy, 0,                                    \
          "0 : Normal.                                                     "\
          "    VM chooses priorities that are appropriate for normal       "\
          "    applications. On Solaris NORM_PRIORITY and above are mapped "\
          "    to normal native priority. Java priorities below "           \
          "    NORM_PRIORITY map to lower native priority values. On       "\
          "    Windows applications are allowed to use higher native       "\
          "    priorities. However, with ThreadPriorityPolicy=0, VM will   "\
          "    not use the highest possible native priority,               "\
          "    THREAD_PRIORITY_TIME_CRITICAL, as it may interfere with     "\
          "    system threads. On Linux thread priorities are ignored      "\
          "    because the OS does not support static priority in          "\
          "    SCHED_OTHER scheduling class which is the only choice for   "\
          "    non-root, non-realtime applications.                        "\
          "1 : Aggressive.                                                 "\
          "    Java thread priorities map over to the entire range of      "\
          "    native thread priorities. Higher Java thread priorities map "\
          "    to higher native thread priorities. This policy should be   "\
          "    used with care, as sometimes it can cause performance       "\
          "    degradation in the application and/or the entire system. On "\
          "    Linux this policy requires root privilege.")                 \
          range(0, 1)                                                       \
                                                                            \
  product(bool, ThreadPriorityVerbose, false,                               \
          "Print priority changes")                                         \
                                                                            \
  product(intx, CompilerThreadPriority, -1,                                 \
          "The native priority at which compiler threads should run "       \
          "(-1 means no change)")                                           \
                                                                            \
  product(intx, VMThreadPriority, -1,                                       \
          "The native priority at which the VM thread should run "          \
          "(-1 means no change)")                                           \
                                                                            \
  product(bool, CompilerThreadHintNoPreempt, true,                          \
          "(Solaris only) Give compiler threads an extra quanta")           \
                                                                            \
  product(bool, VMThreadHintNoPreempt, false,                               \
          "(Solaris only) Give VM thread an extra quanta")                  \
                                                                            \
  product(intx, JavaPriority1_To_OSPriority, -1,                            \
          "Map Java priorities to OS priorities")                           \
                                                                            \
  product(intx, JavaPriority2_To_OSPriority, -1,                            \
          "Map Java priorities to OS priorities")                           \
                                                                            \
  product(intx, JavaPriority3_To_OSPriority, -1,                            \
          "Map Java priorities to OS priorities")                           \
                                                                            \
  product(intx, JavaPriority4_To_OSPriority, -1,                            \
          "Map Java priorities to OS priorities")                           \
                                                                            \
  product(intx, JavaPriority5_To_OSPriority, -1,                            \
          "Map Java priorities to OS priorities")                           \
                                                                            \
  product(intx, JavaPriority6_To_OSPriority, -1,                            \
          "Map Java priorities to OS priorities")                           \
                                                                            \
  product(intx, JavaPriority7_To_OSPriority, -1,                            \
          "Map Java priorities to OS priorities")                           \
                                                                            \
  product(intx, JavaPriority8_To_OSPriority, -1,                            \
          "Map Java priorities to OS priorities")                           \
                                                                            \
  product(intx, JavaPriority9_To_OSPriority, -1,                            \
          "Map Java priorities to OS priorities")                           \
                                                                            \
  product(intx, JavaPriority10_To_OSPriority,-1,                            \
          "Map Java priorities to OS priorities")                           \
                                                                            \
  experimental(bool, UseCriticalJavaThreadPriority, false,                  \
          "Java thread priority 10 maps to critical scheduling priority")   \
                                                                            \
  experimental(bool, UseCriticalCompilerThreadPriority, false,              \
          "Compiler thread(s) run at critical scheduling priority")         \
                                                                            \
  experimental(bool, UseCriticalCMSThreadPriority, false,                   \
          "ConcurrentMarkSweep thread runs at critical scheduling priority")\
                                                                            \
  /* compiler debugging */                                                  \
  notproduct(intx, CompileTheWorldStartAt,     1,                           \
          "First class to consider when using +CompileTheWorld")            \
                                                                            \
  notproduct(intx, CompileTheWorldStopAt, max_jint,                         \
          "Last class to consider when using +CompileTheWorld")             \
                                                                            \
  develop(intx, NewCodeParameter,      0,                                   \
          "Testing Only: Create a dedicated integer parameter before "      \
          "putback")                                                        \
                                                                            \
  /* new oopmap storage allocation */                                       \
  develop(intx, MinOopMapAllocation,     8,                                 \
          "Minimum number of OopMap entries in an OopMapSet")               \
                                                                            \
  /* Background Compilation */                                              \
  develop(intx, LongCompileThreshold,     50,                               \
          "Used with +TraceLongCompiles")                                   \
                                                                            \
  /* recompilation */                                                       \
  product_pd(intx, CompileThreshold,                                        \
          "number of interpreted method invocations before (re-)compiling") \
                                                                            \
  product(double, CompileThresholdScaling, 1.0,                             \
          "Factor to control when first compilation happens "               \
          "(both with and without tiered compilation): "                    \
          "values greater than 1.0 delay counter overflow, "                \
          "values between 0 and 1.0 rush counter overflow, "                \
          "value of 1.0 leaves compilation thresholds unchanged "           \
          "value of 0.0 is equivalent to -Xint. "                           \
          ""                                                                \
          "Flag can be set as per-method option. "                          \
          "If a value is specified for a method, compilation thresholds "   \
          "for that method are scaled by both the value of the global flag "\
          "and the value of the per-method flag.")                          \
                                                                            \
  product(intx, Tier0InvokeNotifyFreqLog, 7,                                \
          "Interpreter (tier 0) invocation notification frequency")         \
                                                                            \
  product(intx, Tier2InvokeNotifyFreqLog, 11,                               \
          "C1 without MDO (tier 2) invocation notification frequency")      \
                                                                            \
  product(intx, Tier3InvokeNotifyFreqLog, 10,                               \
          "C1 with MDO profiling (tier 3) invocation notification "         \
          "frequency")                                                      \
                                                                            \
  product(intx, Tier23InlineeNotifyFreqLog, 20,                             \
          "Inlinee invocation (tiers 2 and 3) notification frequency")      \
                                                                            \
  product(intx, Tier0BackedgeNotifyFreqLog, 10,                             \
          "Interpreter (tier 0) invocation notification frequency")         \
                                                                            \
  product(intx, Tier2BackedgeNotifyFreqLog, 14,                             \
          "C1 without MDO (tier 2) invocation notification frequency")      \
                                                                            \
  product(intx, Tier3BackedgeNotifyFreqLog, 13,                             \
          "C1 with MDO profiling (tier 3) invocation notification "         \
          "frequency")                                                      \
                                                                            \
  product(intx, Tier2CompileThreshold, 0,                                   \
          "threshold at which tier 2 compilation is invoked")               \
                                                                            \
  product(intx, Tier2BackEdgeThreshold, 0,                                  \
          "Back edge threshold at which tier 2 compilation is invoked")     \
                                                                            \
  product(intx, Tier3InvocationThreshold, 200,                              \
          "Compile if number of method invocations crosses this "           \
          "threshold")                                                      \
                                                                            \
  product(intx, Tier3MinInvocationThreshold, 100,                           \
          "Minimum invocation to compile at tier 3")                        \
                                                                            \
  product(intx, Tier3CompileThreshold, 2000,                                \
          "Threshold at which tier 3 compilation is invoked (invocation "   \
          "minimum must be satisfied")                                      \
                                                                            \
  product(intx, Tier3BackEdgeThreshold,  60000,                             \
          "Back edge threshold at which tier 3 OSR compilation is invoked") \
                                                                            \
  product(intx, Tier4InvocationThreshold, 5000,                             \
          "Compile if number of method invocations crosses this "           \
          "threshold")                                                      \
                                                                            \
  product(intx, Tier4MinInvocationThreshold, 600,                           \
          "Minimum invocation to compile at tier 4")                        \
                                                                            \
  product(intx, Tier4CompileThreshold, 15000,                               \
          "Threshold at which tier 4 compilation is invoked (invocation "   \
          "minimum must be satisfied")                                      \
                                                                            \
  product(intx, Tier4BackEdgeThreshold, 40000,                              \
          "Back edge threshold at which tier 4 OSR compilation is invoked") \
                                                                            \
  product(intx, Tier3DelayOn, 5,                                            \
          "If C2 queue size grows over this amount per compiler thread "    \
          "stop compiling at tier 3 and start compiling at tier 2")         \
                                                                            \
  product(intx, Tier3DelayOff, 2,                                           \
          "If C2 queue size is less than this amount per compiler thread "  \
          "allow methods compiled at tier 2 transition to tier 3")          \
                                                                            \
  product(intx, Tier3LoadFeedback, 5,                                       \
          "Tier 3 thresholds will increase twofold when C1 queue size "     \
          "reaches this amount per compiler thread")                        \
                                                                            \
  product(intx, Tier4LoadFeedback, 3,                                       \
          "Tier 4 thresholds will increase twofold when C2 queue size "     \
          "reaches this amount per compiler thread")                        \
                                                                            \
  product(intx, TieredCompileTaskTimeout, 50,                               \
          "Kill compile task if method was not used within "                \
          "given timeout in milliseconds")                                  \
                                                                            \
  product(intx, TieredStopAtLevel, 4,                                       \
          "Stop at given compilation level")                                \
                                                                            \
  product(intx, Tier0ProfilingStartPercentage, 200,                         \
          "Start profiling in interpreter if the counters exceed tier 3 "   \
          "thresholds by the specified percentage")                         \
                                                                            \
  product(uintx, IncreaseFirstTierCompileThresholdAt, 50,                   \
          "Increase the compile threshold for C1 compilation if the code "  \
          "cache is filled by the specified percentage")                    \
          range(0, 99)                                                      \
                                                                            \
  product(intx, TieredRateUpdateMinTime, 1,                                 \
          "Minimum rate sampling interval (in milliseconds)")               \
                                                                            \
  product(intx, TieredRateUpdateMaxTime, 25,                                \
          "Maximum rate sampling interval (in milliseconds)")               \
                                                                            \
  product_pd(bool, TieredCompilation,                                       \
          "Enable tiered compilation")                                      \
                                                                            \
  product(bool, PrintTieredEvents, false,                                   \
          "Print tiered events notifications")                              \
                                                                            \
  product_pd(intx, OnStackReplacePercentage,                                \
          "NON_TIERED number of method invocations/branches (expressed as " \
          "% of CompileThreshold) before (re-)compiling OSR code")          \
                                                                            \
  product(intx, InterpreterProfilePercentage, 33,                           \
          "NON_TIERED number of method invocations/branches (expressed as " \
          "% of CompileThreshold) before profiling in the interpreter")     \
          range(0, 100)                                                     \
                                                                            \
  develop(intx, MaxRecompilationSearchLength,    10,                        \
          "The maximum number of frames to inspect when searching for "     \
          "recompilee")                                                     \
                                                                            \
  develop(intx, MaxInterpretedSearchLength,     3,                          \
          "The maximum number of interpreted frames to skip when searching "\
          "for recompilee")                                                 \
                                                                            \
  develop(intx, DesiredMethodLimit,  8000,                                  \
          "The desired maximum method size (in bytecodes) after inlining")  \
                                                                            \
  develop(intx, HugeMethodLimit,  8000,                                     \
          "Don't compile methods larger than this if "                      \
          "+DontCompileHugeMethods")                                        \
                                                                            \
  /* New JDK 1.4 reflection implementation */                               \
                                                                            \
  develop(intx, FastSuperclassLimit, 8,                                     \
          "Depth of hardwired instanceof accelerator array")                \
                                                                            \
  /* Properties for Java libraries  */                                      \
                                                                            \
  product(size_t, MaxDirectMemorySize, 0,                                   \
          "Maximum total size of NIO direct-buffer allocations")            \
                                                                            \
  /* Flags used for temporary code during development  */                   \
                                                                            \
  diagnostic(bool, UseNewCode, false,                                       \
          "Testing Only: Use the new version while testing")                \
                                                                            \
  diagnostic(bool, UseNewCode2, false,                                      \
          "Testing Only: Use the new version while testing")                \
                                                                            \
  diagnostic(bool, UseNewCode3, false,                                      \
          "Testing Only: Use the new version while testing")                \
                                                                            \
  /* flags for performance data collection */                               \
                                                                            \
  product(bool, UsePerfData, falseInEmbedded,                               \
          "Flag to disable jvmstat instrumentation for performance testing "\
          "and problem isolation purposes")                                 \
                                                                            \
  product(bool, PerfDataSaveToFile, false,                                  \
          "Save PerfData memory to hsperfdata_<pid> file on exit")          \
                                                                            \
  product(ccstr, PerfDataSaveFile, NULL,                                    \
          "Save PerfData memory to the specified absolute pathname. "       \
          "The string %p in the file name (if present) "                    \
          "will be replaced by pid")                                        \
                                                                            \
  product(intx, PerfDataSamplingInterval, 50,                               \
          "Data sampling interval (in milliseconds)")                       \
                                                                            \
  develop(bool, PerfTraceDataCreation, false,                               \
          "Trace creation of Performance Data Entries")                     \
                                                                            \
  develop(bool, PerfTraceMemOps, false,                                     \
          "Trace PerfMemory create/attach/detach calls")                    \
                                                                            \
  product(bool, PerfDisableSharedMem, false,                                \
          "Store performance data in standard memory")                      \
                                                                            \
  product(intx, PerfDataMemorySize, 64*K,                                   \
          "Size of performance data memory region. Will be rounded "        \
          "up to a multiple of the native os page size.")                   \
                                                                            \
  product(intx, PerfMaxStringConstLength, 1024,                             \
          "Maximum PerfStringConstant string length before truncation")     \
                                                                            \
  product(bool, PerfAllowAtExitRegistration, false,                         \
          "Allow registration of atexit() methods")                         \
                                                                            \
  product(bool, PerfBypassFileSystemCheck, false,                           \
          "Bypass Win32 file system criteria checks (Windows Only)")        \
                                                                            \
  product(intx, UnguardOnExecutionViolation, 0,                             \
          "Unguard page and retry on no-execute fault (Win32 only) "        \
          "0=off, 1=conservative, 2=aggressive")                            \
          range(0, 2)                                                       \
                                                                            \
  /* Serviceability Support */                                              \
                                                                            \
  product(bool, ManagementServer, false,                                    \
          "Create JMX Management Server")                                   \
                                                                            \
  product(bool, DisableAttachMechanism, false,                              \
          "Disable mechanism that allows tools to attach to this VM")       \
                                                                            \
  product(bool, StartAttachListener, false,                                 \
          "Always start Attach Listener at VM startup")                     \
                                                                            \
  manageable(bool, PrintConcurrentLocks, false,                             \
          "Print java.util.concurrent locks in thread dump")                \
                                                                            \
  product(bool, TransmitErrorReport, false,                                 \
          "Enable error report transmission on erroneous termination")      \
                                                                            \
  product(ccstr, ErrorReportServer, NULL,                                   \
          "Override built-in error report server address")                  \
                                                                            \
  /* Shared spaces */                                                       \
                                                                            \
  product(bool, UseSharedSpaces, true,                                      \
          "Use shared spaces for metadata")                                 \
                                                                            \
  product(bool, VerifySharedSpaces, false,                                  \
          "Verify shared spaces (false for default archive, true for "      \
          "archive specified by -XX:SharedArchiveFile)")                    \
                                                                            \
  product(bool, RequireSharedSpaces, false,                                 \
          "Require shared spaces for metadata")                             \
                                                                            \
  product(bool, DumpSharedSpaces, false,                                    \
          "Special mode: JVM reads a class list, loads classes, builds "    \
          "shared spaces, and dumps the shared spaces to a file to be "     \
          "used in future JVM runs")                                        \
                                                                            \
  product(bool, PrintSharedSpaces, false,                                   \
          "Print usage of shared spaces")                                   \
                                                                            \
  product(bool, PrintSharedArchiveAndExit, false,                           \
          "Print shared archive file contents")                             \
                                                                            \
  product(bool, PrintSharedDictionary, false,                               \
          "If PrintSharedArchiveAndExit is true, also print the shared "    \
          "dictionary")                                                     \
                                                                            \
  product(size_t, SharedReadWriteSize,  NOT_LP64(12*M) LP64_ONLY(16*M),     \
          "Size of read-write space for metadata (in bytes)")               \
                                                                            \
  product(size_t, SharedReadOnlySize,  NOT_LP64(12*M) LP64_ONLY(16*M),      \
          "Size of read-only space for metadata (in bytes)")                \
                                                                            \
  product(uintx, SharedMiscDataSize,    NOT_LP64(2*M) LP64_ONLY(4*M),       \
          "Size of the shared miscellaneous data area (in bytes)")          \
                                                                            \
  product(uintx, SharedMiscCodeSize,    120*K,                              \
          "Size of the shared miscellaneous code area (in bytes)")          \
                                                                            \
  product(uintx, SharedBaseAddress, LP64_ONLY(32*G)                         \
          NOT_LP64(LINUX_ONLY(2*G) NOT_LINUX(0)),                           \
          "Address to allocate shared memory region for class data")        \
                                                                            \
  product(uintx, SharedSymbolTableBucketSize, 4,                            \
          "Average number of symbols per bucket in shared table")           \
                                                                            \
  diagnostic(bool, IgnoreUnverifiableClassesDuringDump, false,              \
          "Do not quit -Xshare:dump even if we encounter unverifiable "     \
          "classes. Just exclude them from the shared dictionary.")         \
                                                                            \
  diagnostic(bool, PrintMethodHandleStubs, false,                           \
          "Print generated stub code for method handles")                   \
                                                                            \
  develop(bool, TraceMethodHandles, false,                                  \
          "trace internal method handle operations")                        \
                                                                            \
  diagnostic(bool, VerifyMethodHandles, trueInDebug,                        \
          "perform extra checks when constructing method handles")          \
                                                                            \
  diagnostic(bool, ShowHiddenFrames, false,                                 \
          "show method handle implementation frames (usually hidden)")      \
                                                                            \
  experimental(bool, TrustFinalNonStaticFields, false,                      \
          "trust final non-static declarations for constant folding")       \
                                                                            \
  diagnostic(bool, FoldStableValues, true,                                  \
          "Optimize loads from stable fields (marked w/ @Stable)")          \
                                                                            \
  develop(bool, TraceInvokeDynamic, false,                                  \
          "trace internal invoke dynamic operations")                       \
                                                                            \
  diagnostic(bool, PauseAtStartup,      false,                              \
          "Causes the VM to pause at startup time and wait for the pause "  \
          "file to be removed (default: ./vm.paused.<pid>)")                \
                                                                            \
  diagnostic(ccstr, PauseAtStartupFile, NULL,                               \
          "The file to create and for whose removal to await when pausing " \
          "at startup. (default: ./vm.paused.<pid>)")                       \
                                                                            \
  diagnostic(bool, PauseAtExit, false,                                      \
          "Pause and wait for keypress on exit if a debugger is attached")  \
                                                                            \
  product(bool, ExtendedDTraceProbes,    false,                             \
          "Enable performance-impacting dtrace probes")                     \
                                                                            \
  product(bool, DTraceMethodProbes, false,                                  \
          "Enable dtrace probes for method-entry and method-exit")          \
                                                                            \
  product(bool, DTraceAllocProbes, false,                                   \
          "Enable dtrace probes for object allocation")                     \
                                                                            \
  product(bool, DTraceMonitorProbes, false,                                 \
          "Enable dtrace probes for monitor events")                        \
                                                                            \
  product(bool, RelaxAccessControlCheck, false,                             \
          "Relax the access control checks in the verifier")                \
                                                                            \
  product(uintx, StringTableSize, defaultStringTableSize,                   \
          "Number of buckets in the interned String table")                 \
          range(minimumStringTableSize, 111*defaultStringTableSize)         \
                                                                            \
  experimental(uintx, SymbolTableSize, defaultSymbolTableSize,              \
          "Number of buckets in the JVM internal Symbol table")             \
          range(minimumSymbolTableSize, 111*defaultSymbolTableSize)         \
                                                                            \
  product(bool, UseStringDeduplication, false,                              \
          "Use string deduplication")                                       \
                                                                            \
  product(bool, PrintStringDeduplicationStatistics, false,                  \
          "Print string deduplication statistics")                          \
                                                                            \
  product(uintx, StringDeduplicationAgeThreshold, 3,                        \
          "A string must reach this age (or be promoted to an old region) " \
          "to be considered for deduplication")                             \
          range(1, markOopDesc::max_age)                                    \
                                                                            \
  diagnostic(bool, StringDeduplicationResizeALot, false,                    \
          "Force table resize every time the table is scanned")             \
                                                                            \
  diagnostic(bool, StringDeduplicationRehashALot, false,                    \
          "Force table rehash every time the table is scanned")             \
                                                                            \
  develop(bool, TraceDefaultMethods, false,                                 \
          "Trace the default method processing steps")                      \
                                                                            \
  diagnostic(bool, WhiteBoxAPI, false,                                      \
          "Enable internal testing APIs")                                   \
                                                                            \
  product(bool, PrintGCCause, true,                                         \
          "Include GC cause in GC logging")                                 \
                                                                            \
  experimental(intx, SurvivorAlignmentInBytes, 0,                           \
           "Default survivor space alignment in bytes")                     \
           constraint(SurvivorAlignmentInBytesConstraintFunc,AfterErgo)     \
                                                                            \
  product(bool , AllowNonVirtualCalls, false,                               \
          "Obey the ACC_SUPER flag and allow invokenonvirtual calls")       \
                                                                            \
  product(ccstr, DumpLoadedClassList, NULL,                                 \
          "Dump the names all loaded classes, that could be stored into "   \
          "the CDS archive, in the specified file")                         \
                                                                            \
  product(ccstr, SharedClassListFile, NULL,                                 \
          "Override the default CDS class list")                            \
                                                                            \
  diagnostic(ccstr, SharedArchiveFile, NULL,                                \
          "Override the default location of the CDS archive file")          \
                                                                            \
  product(ccstr, ExtraSharedClassListFile, NULL,                            \
          "Extra classlist for building the CDS archive file")              \
                                                                            \
  experimental(size_t, ArrayAllocatorMallocLimit,                           \
          SOLARIS_ONLY(64*K) NOT_SOLARIS((size_t)-1),                       \
          "Allocation less than this value will be allocated "              \
          "using malloc. Larger allocations will use mmap.")                \
                                                                            \
  experimental(bool, AlwaysAtomicAccesses, false,                           \
          "Accesses to all variables should always be atomic")              \
                                                                            \
  product(bool, EnableTracing, false,                                       \
          "Enable event-based tracing")                                     \
                                                                            \
  product(bool, UseLockedTracing, false,                                    \
          "Use locked-tracing when doing event-based tracing")              \
                                                                            \
  diagnostic(bool, UseUnalignedAccesses, false,                             \
          "Use unaligned memory accesses in sun.misc.Unsafe")               \
                                                                            \
  product_pd(bool, PreserveFramePointer,                                    \
             "Use the FP register for holding the frame pointer "           \
             "and not as a general purpose register.")                      \
                                                                            \
  diagnostic(bool, CheckIntrinsics, true,                                   \
             "When a class C is loaded, check that "                        \
             "(1) all intrinsics defined by the VM for class C are present "\
             "in the loaded class file and are marked with the "            \
             "@HotSpotIntrinsicCandidate annotation, that "                 \
             "(2) there is an intrinsic registered for all loaded methods " \
             "that are annotated with the @HotSpotIntrinsicCandidate "      \
             "annotation, and that "                                        \
             "(3) no orphan methods exist for class C (i.e., methods for "  \
             "which the VM declares an intrinsic but that are not declared "\
             "in the loaded class C. "                                      \
             "Check (3) is available only in debug builds.")

/*
 *  Macros for factoring of globals
 */

// Interface macros
#define DECLARE_PRODUCT_FLAG(type, name, value, doc)      extern "C" type name;
#define DECLARE_PD_PRODUCT_FLAG(type, name, doc)          extern "C" type name;
#define DECLARE_DIAGNOSTIC_FLAG(type, name, value, doc)   extern "C" type name;
#define DECLARE_EXPERIMENTAL_FLAG(type, name, value, doc) extern "C" type name;
#define DECLARE_MANAGEABLE_FLAG(type, name, value, doc)   extern "C" type name;
#define DECLARE_PRODUCT_RW_FLAG(type, name, value, doc)   extern "C" type name;
#ifdef PRODUCT
#define DECLARE_DEVELOPER_FLAG(type, name, value, doc)    extern "C" type CONST_##name; const type name = value;
#define DECLARE_PD_DEVELOPER_FLAG(type, name, doc)        extern "C" type CONST_##name; const type name = pd_##name;
#define DECLARE_NOTPRODUCT_FLAG(type, name, value, doc)   extern "C" type CONST_##name;
#else
#define DECLARE_DEVELOPER_FLAG(type, name, value, doc)    extern "C" type name;
#define DECLARE_PD_DEVELOPER_FLAG(type, name, doc)        extern "C" type name;
#define DECLARE_NOTPRODUCT_FLAG(type, name, value, doc)   extern "C" type name;
#endif // PRODUCT
// Special LP64 flags, product only needed for now.
#ifdef _LP64
#define DECLARE_LP64_PRODUCT_FLAG(type, name, value, doc) extern "C" type name;
#else
#define DECLARE_LP64_PRODUCT_FLAG(type, name, value, doc) const type name = value;
#endif // _LP64

// Implementation macros
#define MATERIALIZE_PRODUCT_FLAG(type, name, value, doc)      type name = value;
#define MATERIALIZE_PD_PRODUCT_FLAG(type, name, doc)          type name = pd_##name;
#define MATERIALIZE_DIAGNOSTIC_FLAG(type, name, value, doc)   type name = value;
#define MATERIALIZE_EXPERIMENTAL_FLAG(type, name, value, doc) type name = value;
#define MATERIALIZE_MANAGEABLE_FLAG(type, name, value, doc)   type name = value;
#define MATERIALIZE_PRODUCT_RW_FLAG(type, name, value, doc)   type name = value;
#ifdef PRODUCT
#define MATERIALIZE_DEVELOPER_FLAG(type, name, value, doc)    type CONST_##name = value;
#define MATERIALIZE_PD_DEVELOPER_FLAG(type, name, doc)        type CONST_##name = pd_##name;
#define MATERIALIZE_NOTPRODUCT_FLAG(type, name, value, doc)   type CONST_##name = value;
#else
#define MATERIALIZE_DEVELOPER_FLAG(type, name, value, doc)    type name = value;
#define MATERIALIZE_PD_DEVELOPER_FLAG(type, name, doc)        type name = pd_##name;
#define MATERIALIZE_NOTPRODUCT_FLAG(type, name, value, doc)   type name = value;
#endif // PRODUCT
#ifdef _LP64
#define MATERIALIZE_LP64_PRODUCT_FLAG(type, name, value, doc) type name = value;
#else
#define MATERIALIZE_LP64_PRODUCT_FLAG(type, name, value, doc) /* flag is constant */
#endif // _LP64

// Only materialize src code for range checking when required, ignore otherwise
#define IGNORE_RANGE(a, b)
// Only materialize src code for contraint checking when required, ignore otherwise
#define IGNORE_CONSTRAINT(func,type)

RUNTIME_FLAGS(DECLARE_DEVELOPER_FLAG, \
              DECLARE_PD_DEVELOPER_FLAG, \
              DECLARE_PRODUCT_FLAG, \
              DECLARE_PD_PRODUCT_FLAG, \
              DECLARE_DIAGNOSTIC_FLAG, \
              DECLARE_EXPERIMENTAL_FLAG, \
              DECLARE_NOTPRODUCT_FLAG, \
              DECLARE_MANAGEABLE_FLAG, \
              DECLARE_PRODUCT_RW_FLAG, \
              DECLARE_LP64_PRODUCT_FLAG, \
              IGNORE_RANGE, \
              IGNORE_CONSTRAINT)

RUNTIME_OS_FLAGS(DECLARE_DEVELOPER_FLAG, \
                 DECLARE_PD_DEVELOPER_FLAG, \
                 DECLARE_PRODUCT_FLAG, \
                 DECLARE_PD_PRODUCT_FLAG, \
                 DECLARE_DIAGNOSTIC_FLAG, \
                 DECLARE_NOTPRODUCT_FLAG, \
                 IGNORE_RANGE, \
                 IGNORE_CONSTRAINT)

ARCH_FLAGS(DECLARE_DEVELOPER_FLAG, \
           DECLARE_PRODUCT_FLAG, \
           DECLARE_DIAGNOSTIC_FLAG, \
           DECLARE_EXPERIMENTAL_FLAG, \
           DECLARE_NOTPRODUCT_FLAG, \
           IGNORE_RANGE, \
           IGNORE_CONSTRAINT)

// Extensions

#include "runtime/globals_ext.hpp"

#endif // SHARE_VM_RUNTIME_GLOBALS_HPP
