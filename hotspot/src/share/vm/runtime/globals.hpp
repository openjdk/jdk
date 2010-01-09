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

#if !defined(COMPILER1) && !defined(COMPILER2)
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
define_pd_global(intx, Tier2CompileThreshold,        0);
define_pd_global(intx, Tier3CompileThreshold,        0);
define_pd_global(intx, Tier4CompileThreshold,        0);

define_pd_global(intx, BackEdgeThreshold,            0);
define_pd_global(intx, Tier2BackEdgeThreshold,       0);
define_pd_global(intx, Tier3BackEdgeThreshold,       0);
define_pd_global(intx, Tier4BackEdgeThreshold,       0);

define_pd_global(intx, OnStackReplacePercentage,     0);
define_pd_global(bool, ResizeTLAB,                   false);
define_pd_global(intx, FreqInlineSize,               0);
define_pd_global(intx, InlineSmallCode,              0);
define_pd_global(intx, NewSizeThreadIncrease,        4*K);
define_pd_global(intx, InlineClassNatives,           true);
define_pd_global(intx, InlineUnsafeOps,              true);
define_pd_global(intx, InitialCodeCacheSize,         160*K);
define_pd_global(intx, ReservedCodeCacheSize,        32*M);
define_pd_global(intx, CodeCacheExpansionSize,       32*K);
define_pd_global(intx, CodeCacheMinBlockLength,      1);
define_pd_global(uintx,PermSize,    ScaleForWordSize(4*M));
define_pd_global(uintx,MaxPermSize, ScaleForWordSize(64*M));
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

enum FlagValueOrigin {
  DEFAULT          = 0,
  COMMAND_LINE     = 1,
  ENVIRON_VAR      = 2,
  CONFIG_FILE      = 3,
  MANAGEMENT       = 4,
  ERGONOMIC        = 5,
  ATTACH_ON_DEMAND = 6,
  INTERNAL         = 99
};

struct Flag {
  const char *type;
  const char *name;
  void*       addr;
  const char *kind;
  FlagValueOrigin origin;

  // points to all Flags static array
  static Flag *flags;

  // number of flags
  static size_t numFlags;

  static Flag* find_flag(char* name, size_t length);

  bool is_bool() const        { return strcmp(type, "bool") == 0; }
  bool get_bool() const       { return *((bool*) addr); }
  void set_bool(bool value)   { *((bool*) addr) = value; }

  bool is_intx()  const       { return strcmp(type, "intx")  == 0; }
  intx get_intx() const       { return *((intx*) addr); }
  void set_intx(intx value)   { *((intx*) addr) = value; }

  bool is_uintx() const       { return strcmp(type, "uintx") == 0; }
  uintx get_uintx() const     { return *((uintx*) addr); }
  void set_uintx(uintx value) { *((uintx*) addr) = value; }

  bool is_uint64_t() const          { return strcmp(type, "uint64_t") == 0; }
  uint64_t get_uint64_t() const     { return *((uint64_t*) addr); }
  void set_uint64_t(uint64_t value) { *((uint64_t*) addr) = value; }

  bool is_double() const        { return strcmp(type, "double") == 0; }
  double get_double() const     { return *((double*) addr); }
  void set_double(double value) { *((double*) addr) = value; }

  bool is_ccstr() const          { return strcmp(type, "ccstr") == 0 || strcmp(type, "ccstrlist") == 0; }
  bool ccstr_accumulates() const { return strcmp(type, "ccstrlist") == 0; }
  ccstr get_ccstr() const     { return *((ccstr*) addr); }
  void set_ccstr(ccstr value) { *((ccstr*) addr) = value; }

  bool is_unlocker() const;
  bool is_unlocked() const;
  bool is_writeable() const;
  bool is_external() const;

  void print_on(outputStream* st);
  void print_as_flag(outputStream* st);
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
  intx val;
  intx* flag;
 public:
  IntFlagSetting(intx& fl, intx newValue) { flag = &fl; val = fl; fl = newValue; }
  ~IntFlagSetting()                       { *flag = val; }
};


class DoubleFlagSetting {
  double val;
  double* flag;
 public:
  DoubleFlagSetting(double& fl, double newValue) { flag = &fl; val = fl; fl = newValue; }
  ~DoubleFlagSetting()                           { *flag = val; }
};


class CommandLineFlags {
 public:
  static bool boolAt(char* name, size_t len, bool* value);
  static bool boolAt(char* name, bool* value)      { return boolAt(name, strlen(name), value); }
  static bool boolAtPut(char* name, size_t len, bool* value, FlagValueOrigin origin);
  static bool boolAtPut(char* name, bool* value, FlagValueOrigin origin)   { return boolAtPut(name, strlen(name), value, origin); }

  static bool intxAt(char* name, size_t len, intx* value);
  static bool intxAt(char* name, intx* value)      { return intxAt(name, strlen(name), value); }
  static bool intxAtPut(char* name, size_t len, intx* value, FlagValueOrigin origin);
  static bool intxAtPut(char* name, intx* value, FlagValueOrigin origin)   { return intxAtPut(name, strlen(name), value, origin); }

  static bool uintxAt(char* name, size_t len, uintx* value);
  static bool uintxAt(char* name, uintx* value)    { return uintxAt(name, strlen(name), value); }
  static bool uintxAtPut(char* name, size_t len, uintx* value, FlagValueOrigin origin);
  static bool uintxAtPut(char* name, uintx* value, FlagValueOrigin origin) { return uintxAtPut(name, strlen(name), value, origin); }

  static bool uint64_tAt(char* name, size_t len, uint64_t* value);
  static bool uint64_tAt(char* name, uint64_t* value) { return uint64_tAt(name, strlen(name), value); }
  static bool uint64_tAtPut(char* name, size_t len, uint64_t* value, FlagValueOrigin origin);
  static bool uint64_tAtPut(char* name, uint64_t* value, FlagValueOrigin origin) { return uint64_tAtPut(name, strlen(name), value, origin); }

  static bool doubleAt(char* name, size_t len, double* value);
  static bool doubleAt(char* name, double* value)    { return doubleAt(name, strlen(name), value); }
  static bool doubleAtPut(char* name, size_t len, double* value, FlagValueOrigin origin);
  static bool doubleAtPut(char* name, double* value, FlagValueOrigin origin) { return doubleAtPut(name, strlen(name), value, origin); }

  static bool ccstrAt(char* name, size_t len, ccstr* value);
  static bool ccstrAt(char* name, ccstr* value)    { return ccstrAt(name, strlen(name), value); }
  static bool ccstrAtPut(char* name, size_t len, ccstr* value, FlagValueOrigin origin);
  static bool ccstrAtPut(char* name, ccstr* value, FlagValueOrigin origin) { return ccstrAtPut(name, strlen(name), value, origin); }

  // Returns false if name is not a command line flag.
  static bool wasSetOnCmdline(const char* name, bool* value);
  static void printSetFlags();

  static void printFlags();

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

// use this for flags that are true per default in the tiered build
// but false in non-tiered builds, and vice versa
#ifdef TIERED
#define  trueInTiered true
#define falseInTiered false
#else
#define  trueInTiered false
#define falseInTiered true
#endif

// develop flags are settable / visible only during development and are constant in the PRODUCT version
// product flags are always settable / visible
// notproduct flags are settable / visible only during development and are not declared in the PRODUCT version

// A flag must be declared with one of the following types:
// bool, intx, uintx, ccstr.
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

#define RUNTIME_FLAGS(develop, develop_pd, product, product_pd, diagnostic, experimental, notproduct, manageable, product_rw, lp64_product) \
                                                                            \
  lp64_product(bool, UseCompressedOops, false,                              \
            "Use 32-bit object references in 64-bit VM. "                   \
            "lp64_product means flag is always constant in 32 bit VM")      \
                                                                            \
  notproduct(bool, CheckCompressedOops, true,                               \
            "generate checks in encoding/decoding code in debug VM")        \
                                                                            \
  product_pd(uintx, HeapBaseMinAddress,                                     \
            "OS specific low limit for heap base address")                  \
                                                                            \
  diagnostic(bool, PrintCompressedOopsMode, false,                          \
            "Print compressed oops base address and encoding mode")         \
                                                                            \
  /* UseMembar is theoretically a temp flag used for memory barrier         \
   * removal testing.  It was supposed to be removed before FCS but has     \
   * been re-added (see 6401008) */                                         \
  product(bool, UseMembar, false,                                           \
          "(Unstable) Issues membars on thread state transitions")          \
                                                                            \
  diagnostic(bool, UnlockDiagnosticVMOptions, trueInDebug,                  \
          "Enable normal processing of flags relating to field diagnostics")\
                                                                            \
  experimental(bool, UnlockExperimentalVMOptions, false,                    \
          "Enable normal processing of flags relating to experimental features")\
                                                                            \
  product(bool, JavaMonitorsInStackTrace, true,                             \
          "Print info. about Java monitor locks when the stacks are dumped")\
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
  develop(bool, TracePageSizes, false,                                      \
          "Trace page size selection and usage.")                           \
                                                                            \
  product(bool, UseNUMA, false,                                             \
          "Use NUMA if available")                                          \
                                                                            \
  product(bool, ForceNUMA, false,                                           \
          "Force NUMA optimizations on single-node/UMA systems")            \
                                                                            \
  product(intx, NUMAChunkResizeWeight, 20,                                  \
          "Percentage (0-100) used to weight the current sample when "      \
          "computing exponentially decaying average for "                   \
          "AdaptiveNUMAChunkSizing")                                        \
                                                                            \
  product(intx, NUMASpaceResizeRate, 1*G,                                   \
          "Do not reallocate more that this amount per collection")         \
                                                                            \
  product(bool, UseAdaptiveNUMAChunkSizing, true,                           \
          "Enable adaptive chunk sizing for NUMA")                          \
                                                                            \
  product(bool, NUMAStats, false,                                           \
          "Print NUMA stats in detailed heap information")                  \
                                                                            \
  product(intx, NUMAPageScanRate, 256,                                      \
          "Maximum number of pages to include in the page scan procedure")  \
                                                                            \
  product_pd(bool, NeedsDeoptSuspend,                                       \
          "True for register window machines (sparc/ia64)")                 \
                                                                            \
  product(intx, UseSSE, 99,                                                 \
          "Highest supported SSE instructions set on x86/x64")              \
                                                                            \
  product(uintx, LargePageSizeInBytes, 0,                                   \
          "Large page size (0 to let VM choose the page size")              \
                                                                            \
  product(uintx, LargePageHeapSizeThreshold, 128*M,                         \
          "Use large pages if max heap is at least this big")               \
                                                                            \
  product(bool, ForceTimeHighResolution, false,                             \
          "Using high time resolution(For Win32 only)")                     \
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
          "a given threashold")                                             \
                                                                            \
  develop(bool, SafepointALot, false,                                       \
          "Generates a lot of safepoints. Works with "                      \
          "GuaranteedSafepointInterval")                                    \
                                                                            \
  product_pd(bool, BackgroundCompilation,                                   \
          "A thread requesting compilation is not blocked during "          \
          "compilation")                                                    \
                                                                            \
  product(bool, PrintVMQWaitTime, false,                                    \
          "Prints out the waiting time in VM operation queue")              \
                                                                            \
  develop(bool, BailoutToInterpreterForThrows, false,                       \
          "Compiled methods which throws/catches exceptions will be "       \
          "deopt and intp.")                                                \
                                                                            \
  develop(bool, NoYieldsInMicrolock, false,                                 \
          "Disable yields in microlock")                                    \
                                                                            \
  develop(bool, TraceOopMapGeneration, false,                               \
          "Shows oopmap generation")                                        \
                                                                            \
  product(bool, MethodFlushing, true,                                       \
          "Reclamation of zombie and not-entrant methods")                  \
                                                                            \
  develop(bool, VerifyStack, false,                                         \
          "Verify stack of each thread when it is entering a runtime call") \
                                                                            \
  develop(bool, ForceUnreachable, false,                                    \
          "(amd64) Make all non code cache addresses to be unreachable with rip-rel forcing use of 64bit literal fixups") \
                                                                            \
  notproduct(bool, StressDerivedPointers, false,                            \
          "Force scavenge when a derived pointers is detected on stack "    \
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
          "inline arraycopy native that is known to be part of "            \
          "base library DLL")                                               \
                                                                            \
  develop(bool, InlineObjectHash, true,                                     \
          "inline Object::hashCode() native that is known to be part "      \
          "of base library DLL")                                            \
                                                                            \
  develop(bool, InlineObjectCopy, true,                                     \
          "inline Object.clone and Arrays.copyOf[Range] intrinsics")        \
                                                                            \
  develop(bool, InlineNatives, true,                                        \
          "inline natives that are known to be part of base library DLL")   \
                                                                            \
  develop(bool, InlineMathNatives, true,                                    \
          "inline SinD, CosD, etc.")                                        \
                                                                            \
  develop(bool, InlineClassNatives, true,                                   \
          "inline Class.isInstance, etc")                                   \
                                                                            \
  develop(bool, InlineAtomicLong, true,                                     \
          "inline sun.misc.AtomicLong")                                     \
                                                                            \
  develop(bool, InlineThreadNatives, true,                                  \
          "inline Thread.currentThread, etc")                               \
                                                                            \
  develop(bool, InlineReflectionGetCallerClass, true,                       \
          "inline sun.reflect.Reflection.getCallerClass(), known to be part "\
          "of base library DLL")                                            \
                                                                            \
  develop(bool, InlineUnsafeOps, true,                                      \
          "inline memory ops (native methods) from sun.misc.Unsafe")        \
                                                                            \
  develop(bool, ConvertCmpD2CmpF, true,                                     \
          "Convert cmpD to cmpF when one input is constant in float range") \
                                                                            \
  develop(bool, ConvertFloat2IntClipping, true,                             \
          "Convert float2int clipping idiom to integer clipping")           \
                                                                            \
  develop(bool, SpecialStringCompareTo, true,                               \
          "special version of string compareTo")                            \
                                                                            \
  develop(bool, SpecialStringIndexOf, true,                                 \
          "special version of string indexOf")                              \
                                                                            \
  develop(bool, SpecialStringEquals, true,                                  \
          "special version of string equals")                               \
                                                                            \
  develop(bool, SpecialArraysEquals, true,                                  \
          "special version of Arrays.equals(char[],char[])")                \
                                                                            \
  product(bool, UseSSE42Intrinsics, false,                                  \
          "SSE4.2 versions of intrinsics")                                  \
                                                                            \
  develop(bool, TraceCallFixup, false,                                      \
          "traces all call fixups")                                         \
                                                                            \
  develop(bool, DeoptimizeALot, false,                                      \
          "deoptimize at every exit from the runtime system")               \
                                                                            \
  notproduct(ccstrlist, DeoptimizeOnlyAt, "",                               \
          "a comma separated list of bcis to deoptimize at")                \
                                                                            \
  product(bool, DeoptimizeRandom, false,                                    \
          "deoptimize random frames on random exit from the runtime system")\
                                                                            \
  notproduct(bool, ZombieALot, false,                                       \
          "creates zombies (non-entrant) at exit from the runt. system")    \
                                                                            \
  notproduct(bool, WalkStackALot, false,                                    \
          "trace stack (no print) at every exit from the runtime system")   \
                                                                            \
  develop(bool, Debugging, false,                                           \
          "set when executing debug methods in debug.ccp "                  \
          "(to prevent triggering assertions)")                             \
                                                                            \
  notproduct(bool, StrictSafepointChecks, trueInDebug,                      \
          "Enable strict checks that safepoints cannot happen for threads " \
          "that used No_Safepoint_Verifier")                                \
                                                                            \
  notproduct(bool, VerifyLastFrame, false,                                  \
          "Verify oops on last frame on entry to VM")                       \
                                                                            \
  develop(bool, TraceHandleAllocation, false,                               \
          "Prints out warnings when suspicious many handles are allocated") \
                                                                            \
  product(bool, UseCompilerSafepoints, true,                                \
          "Stop at safepoints in compiled code")                            \
                                                                            \
  product(bool, UseSplitVerifier, true,                                     \
          "use split verifier with StackMapTable attributes")               \
                                                                            \
  product(bool, FailOverToOldVerifier, true,                                \
          "fail over to old verifier when split verifier fails")            \
                                                                            \
  develop(bool, ShowSafepointMsgs, false,                                   \
          "Show msg. about safepoint synch.")                               \
                                                                            \
  product(bool, SafepointTimeout, false,                                    \
          "Time out and warn or fail after SafepointTimeoutDelay "          \
          "milliseconds if failed to reach safepoint")                      \
                                                                            \
  develop(bool, DieOnSafepointTimeout, false,                               \
          "Die upon failure to reach safepoint (see SafepointTimeout)")     \
                                                                            \
  /* 50 retries * (5 * current_retry_count) millis = ~6.375 seconds */      \
  /* typically, at most a few retries are needed */                         \
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
          "Bump the number of file descriptors to max in solaris.")         \
                                                                            \
  notproduct(bool, LogEvents, trueInDebug,                                  \
          "Enable Event log")                                               \
                                                                            \
  product(bool, BytecodeVerificationRemote, true,                           \
          "Enables the Java bytecode verifier for remote classes")          \
                                                                            \
  product(bool, BytecodeVerificationLocal, false,                           \
          "Enables the Java bytecode verifier for local classes")           \
                                                                            \
  develop(bool, ForceFloatExceptions, trueInDebug,                          \
          "Force exceptions on FP stack under/overflow")                    \
                                                                            \
  develop(bool, SoftMatchFailure, trueInProduct,                            \
          "If the DFA fails to match a node, print a message and bail out") \
                                                                            \
  develop(bool, VerifyStackAtCalls, false,                                  \
          "Verify that the stack pointer is unchanged after calls")         \
                                                                            \
  develop(bool, TraceJavaAssertions, false,                                 \
          "Trace java language assertions")                                 \
                                                                            \
  notproduct(bool, CheckAssertionStatusDirectives, false,                   \
          "temporary - see javaClasses.cpp")                                \
                                                                            \
  notproduct(bool, PrintMallocFree, false,                                  \
          "Trace calls to C heap malloc/free allocation")                   \
                                                                            \
  notproduct(bool, PrintOopAddress, false,                                  \
          "Always print the location of the oop")                           \
                                                                            \
  notproduct(bool, VerifyCodeCacheOften, false,                             \
          "Verify compiled-code cache often")                               \
                                                                            \
  develop(bool, ZapDeadCompiledLocals, false,                               \
          "Zap dead locals in compiler frames")                             \
                                                                            \
  notproduct(bool, ZapDeadLocalsOld, false,                                 \
          "Zap dead locals (old version, zaps all frames when "             \
          "entering the VM")                                                \
                                                                            \
  notproduct(bool, CheckOopishValues, false,                                \
          "Warn if value contains oop ( requires ZapDeadLocals)")           \
                                                                            \
  develop(bool, UseMallocOnly, false,                                       \
          "use only malloc/free for allocation (no resource area/arena)")   \
                                                                            \
  develop(bool, PrintMalloc, false,                                         \
          "print all malloc/free calls")                                    \
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
          "Print vm messages on console")                                   \
                                                                            \
  product(bool, PrintGCApplicationConcurrentTime, false,                    \
          "Print the time the application has been running")                \
                                                                            \
  product(bool, PrintGCApplicationStoppedTime, false,                       \
          "Print the time the application has been stopped")                \
                                                                            \
  develop(bool, Verbose, false,                                             \
          "Prints additional debugging information from other modes")       \
                                                                            \
  develop(bool, PrintMiscellaneous, false,                                  \
          "Prints uncategorized debugging information (requires +Verbose)") \
                                                                            \
  develop(bool, WizardMode, false,                                          \
          "Prints much more debugging information")                         \
                                                                            \
  product(bool, ShowMessageBoxOnError, false,                               \
          "Keep process alive on VM fatal error")                           \
                                                                            \
  product_pd(bool, UseOSErrorReporting,                                     \
          "Let VM fatal error propagate to the OS (ie. WER on Windows)")    \
                                                                            \
  product(bool, SuppressFatalErrorMessage, false,                           \
          "Do NO Fatal Error report [Avoid deadlock]")                      \
                                                                            \
  product(ccstrlist, OnError, "",                                           \
          "Run user-defined commands on fatal error; see VMError.cpp "      \
          "for examples")                                                   \
                                                                            \
  product(ccstrlist, OnOutOfMemoryError, "",                                \
          "Run user-defined commands on first java.lang.OutOfMemoryError")  \
                                                                            \
  manageable(bool, HeapDumpBeforeFullGC, false,                             \
          "Dump heap to file before any major stop-world GC")               \
                                                                            \
  manageable(bool, HeapDumpAfterFullGC, false,                              \
          "Dump heap to file after any major stop-world GC")                \
                                                                            \
  manageable(bool, HeapDumpOnOutOfMemoryError, false,                       \
          "Dump heap to file when java.lang.OutOfMemoryError is thrown")    \
                                                                            \
  manageable(ccstr, HeapDumpPath, NULL,                                     \
          "When HeapDumpOnOutOfMemoryError is on, the path (filename or"    \
          "directory) of the dump file (defaults to java_pid<pid>.hprof"    \
          "in the working directory)")                                      \
                                                                            \
  develop(uintx, SegmentedHeapDumpThreshold, 2*G,                           \
          "Generate a segmented heap dump (JAVA PROFILE 1.0.2 format) "     \
          "when the heap usage is larger than this")                        \
                                                                            \
  develop(uintx, HeapDumpSegmentSize, 1*G,                                  \
          "Approximate segment size when generating a segmented heap dump") \
                                                                            \
  develop(bool, BreakAtWarning, false,                                      \
          "Execute breakpoint upon encountering VM warning")                \
                                                                            \
  product_pd(bool, UseVectoredExceptions,                                   \
          "Temp Flag - Use Vectored Exceptions rather than SEH (Windows Only)") \
                                                                            \
  develop(bool, TraceVMOperation, false,                                    \
          "Trace vm operations")                                            \
                                                                            \
  develop(bool, UseFakeTimers, false,                                       \
          "Tells whether the VM should use system time or a fake timer")    \
                                                                            \
  diagnostic(bool, LogCompilation, false,                                   \
          "Log compilation activity in detail to hotspot.log or LogFile")   \
                                                                            \
  product(bool, PrintCompilation, false,                                    \
          "Print compilations")                                             \
                                                                            \
  diagnostic(bool, TraceNMethodInstalls, false,                             \
             "Trace nmethod intallation")                                   \
                                                                            \
  diagnostic(intx, ScavengeRootsInCode, 0,                                  \
             "0: do not allow scavengable oops in the code cache; "         \
             "1: allow scavenging from the code cache; "                    \
             "2: emit as many constants as the compiler can see")           \
                                                                            \
  diagnostic(bool, TraceOSRBreakpoint, false,                               \
             "Trace OSR Breakpoint ")                                       \
                                                                            \
  diagnostic(bool, TraceCompileTriggered, false,                            \
             "Trace compile triggered")                                     \
                                                                            \
  diagnostic(bool, TraceTriggers, false,                                    \
             "Trace triggers")                                              \
                                                                            \
  product(bool, AlwaysRestoreFPU, false,                                    \
          "Restore the FPU control word after every JNI call (expensive)")  \
                                                                            \
  notproduct(bool, PrintCompilation2, false,                                \
          "Print additional statistics per compilation")                    \
                                                                            \
  diagnostic(bool, PrintAdapterHandlers, false,                             \
          "Print code generated for i2c/c2i adapters")                      \
                                                                            \
  diagnostic(bool, PrintAssembly, false,                                    \
          "Print assembly code (using external disassembler.so)")           \
                                                                            \
  diagnostic(ccstr, PrintAssemblyOptions, NULL,                             \
          "Options string passed to disassembler.so")                       \
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
  develop(bool, InterceptOSException, false,                                \
          "Starts debugger when an implicit OS (e.g., NULL) "               \
          "exception happens")                                              \
                                                                            \
  notproduct(bool, PrintCodeCache, false,                                   \
          "Print the compiled_code cache when exiting")                     \
                                                                            \
  develop(bool, PrintCodeCache2, false,                                     \
          "Print detailed info on the compiled_code cache when exiting")    \
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
          "Prints byte code statictics when dumping profiler output")       \
                                                                            \
  product(bool, ProfilerRecordPC, false,                                    \
          "Collects tick for each 16 byte interval of compiled code")       \
                                                                            \
  product(bool, ProfileVM, false,                                           \
          "Profiles ticks that fall within VM (either in the VM Thread "    \
          "or VM code called through stubs)")                               \
                                                                            \
  product(bool, ProfileIntervals, false,                                    \
          "Prints profiles for each interval (see ProfileIntervalsTicks)")  \
                                                                            \
  notproduct(bool, ProfilerCheckIntervals, false,                           \
          "Collect and print info on spacing of profiler ticks")            \
                                                                            \
  develop(bool, PrintJVMWarnings, false,                                    \
          "Prints warnings for unimplemented JVM functions")                \
                                                                            \
  notproduct(uintx, WarnOnStalledSpinLock, 0,                               \
          "Prints warnings for stalled SpinLocks")                          \
                                                                            \
  develop(bool, InitializeJavaLangSystem, true,                             \
          "Initialize java.lang.System - turn off for individual "          \
          "method debugging")                                               \
                                                                            \
  develop(bool, InitializeJavaLangString, true,                             \
          "Initialize java.lang.String - turn off for individual "          \
          "method debugging")                                               \
                                                                            \
  develop(bool, InitializeJavaLangExceptionsErrors, true,                   \
          "Initialize various error and exception classes - turn off for "  \
          "individual method debugging")                                    \
                                                                            \
  product(bool, RegisterFinalizersAtInit, true,                             \
          "Register finalizable objects at end of Object.<init> or "        \
          "after allocation")                                               \
                                                                            \
  develop(bool, RegisterReferences, true,                                   \
          "Tells whether the VM should register soft/weak/final/phantom "   \
          "references")                                                     \
                                                                            \
  develop(bool, IgnoreRewrites, false,                                      \
          "Supress rewrites of bytecodes in the oopmap generator. "         \
          "This is unsafe!")                                                \
                                                                            \
  develop(bool, PrintCodeCacheExtension, false,                             \
          "Print extension of code cache")                                  \
                                                                            \
  develop(bool, UsePrivilegedStack, true,                                   \
          "Enable the security JVM functions")                              \
                                                                            \
  develop(bool, IEEEPrecision, true,                                        \
          "Enables IEEE precision (for INTEL only)")                        \
                                                                            \
  develop(bool, ProtectionDomainVerification, true,                         \
          "Verifies protection domain before resolution in system "         \
          "dictionary")                                                     \
                                                                            \
  product(bool, ClassUnloading, true,                                       \
          "Do unloading of classes")                                        \
                                                                            \
  diagnostic(bool, LinkWellKnownClasses, false,                             \
          "Resolve a well known class as soon as its name is seen")         \
                                                                            \
  develop(bool, DisableStartThread, false,                                  \
          "Disable starting of additional Java threads "                    \
          "(for debugging only)")                                           \
                                                                            \
  develop(bool, MemProfiling, false,                                        \
          "Write memory usage profiling to log file")                       \
                                                                            \
  notproduct(bool, PrintSystemDictionaryAtExit, false,                      \
          "Prints the system dictionary at exit")                           \
                                                                            \
  diagnostic(bool, UnsyncloadClass, false,                                  \
          "Unstable: VM calls loadClass unsynchronized. Custom "            \
          "class loader  must call VM synchronized for findClass "          \
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
          "Throw away obvious excess yield calls (for SOLARIS only)")       \
                                                                            \
  product_pd(bool, ConvertSleepToYield,                                     \
          "Converts sleep(0) to thread yield "                              \
          "(may be off for SOLARIS to improve GUI)")                        \
                                                                            \
  product(bool, ConvertYieldToSleep, false,                                 \
          "Converts yield to a sleep of MinSleepInterval to simulate Win32 "\
          "behavior (SOLARIS only)")                                        \
                                                                            \
  product(bool, UseBoundThreads, true,                                      \
          "Bind user level threads to kernel threads (for SOLARIS only)")   \
                                                                            \
  develop(bool, UseDetachedThreads, true,                                   \
          "Use detached threads that are recycled upon termination "        \
          "(for SOLARIS only)")                                             \
                                                                            \
  product(bool, UseLWPSynchronization, true,                                \
          "Use LWP-based instead of libthread-based synchronization "       \
          "(SPARC only)")                                                   \
                                                                            \
  product(ccstr, SyncKnobs, NULL,                                           \
          "(Unstable) Various monitor synchronization tunables")            \
                                                                            \
  product(intx, EmitSync, 0,                                                \
          "(Unsafe,Unstable) "                                              \
          " Controls emission of inline sync fast-path code")               \
                                                                            \
  product(intx, AlwaysInflate, 0, "(Unstable) Force inflation")             \
                                                                            \
  product(intx, Atomics, 0,                                                 \
          "(Unsafe,Unstable) Diagnostic - Controls emission of atomics")    \
                                                                            \
  product(intx, FenceInstruction, 0,                                        \
          "(Unsafe,Unstable) Experimental")                                 \
                                                                            \
  product(intx, SyncFlags, 0, "(Unsafe,Unstable) Experimental Sync flags" ) \
                                                                            \
  product(intx, SyncVerbose, 0, "(Unstable)" )                              \
                                                                            \
  product(intx, ClearFPUAtPark, 0, "(Unsafe,Unstable)" )                    \
                                                                            \
  product(intx, hashCode, 0,                                                \
         "(Unstable) select hashCode generation algorithm" )                \
                                                                            \
  product(intx, WorkAroundNPTLTimedWaitHang, 1,                             \
         "(Unstable, Linux-specific)"                                       \
         " avoid NPTL-FUTEX hang pthread_cond_timedwait" )                  \
                                                                            \
  product(bool, FilterSpuriousWakeups, true,                                \
          "Prevent spurious or premature wakeups from object.wait "         \
          "(Solaris only)")                                                 \
                                                                            \
  product(intx, NativeMonitorTimeout, -1, "(Unstable)" )                    \
  product(intx, NativeMonitorFlags, 0, "(Unstable)" )                       \
  product(intx, NativeMonitorSpinLimit, 20, "(Unstable)" )                  \
                                                                            \
  develop(bool, UsePthreads, false,                                         \
          "Use pthread-based instead of libthread-based synchronization "   \
          "(SPARC only)")                                                   \
                                                                            \
  product(bool, AdjustConcurrency, false,                                   \
          "call thr_setconcurrency at thread create time to avoid "         \
          "LWP starvation on MP systems (For Solaris Only)")                \
                                                                            \
  develop(bool, UpdateHotSpotCompilerFileOnError, true,                     \
          "Should the system attempt to update the compiler file when "     \
          "an error occurs?")                                               \
                                                                            \
  product(bool, ReduceSignalUsage, false,                                   \
          "Reduce the use of OS signals in Java and/or the VM")             \
                                                                            \
  notproduct(bool, ValidateMarkSweep, false,                                \
          "Do extra validation during MarkSweep collection")                \
                                                                            \
  notproduct(bool, RecordMarkSweepCompaction, false,                        \
          "Enable GC-to-GC recording and querying of compaction during "    \
          "MarkSweep")                                                      \
                                                                            \
  develop_pd(bool, ShareVtableStubs,                                        \
          "Share vtable stubs (smaller code but worse branch prediction")   \
                                                                            \
  develop(bool, LoadLineNumberTables, true,                                 \
          "Tells whether the class file parser loads line number tables")   \
                                                                            \
  develop(bool, LoadLocalVariableTables, true,                              \
          "Tells whether the class file parser loads local variable tables")\
                                                                            \
  develop(bool, LoadLocalVariableTypeTables, true,                          \
          "Tells whether the class file parser loads local variable type tables")\
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
  product(bool, UseSpinning, false,                                         \
          "Use spinning in monitor inflation and before entry")             \
                                                                            \
  product(bool, PreSpinYield, false,                                        \
          "Yield before inner spinning loop")                               \
                                                                            \
  product(bool, PostSpinYield, true,                                        \
          "Yield after inner spinning loop")                                \
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
  product(bool, UseFastJNIAccessors, true,                                  \
          "Use optimized versions of Get<Primitive>Field")                  \
                                                                            \
  product(bool, EagerXrunInit, false,                                       \
          "Eagerly initialize -Xrun libraries; allows startup profiling, "  \
          " but not all -Xrun libraries may support the state of the VM at this time") \
                                                                            \
  product(bool, PreserveAllAnnotations, false,                              \
          "Preserve RuntimeInvisibleAnnotations as well as RuntimeVisibleAnnotations") \
                                                                            \
  develop(uintx, PreallocatedOutOfMemoryErrorCount, 4,                      \
          "Number of OutOfMemoryErrors preallocated with backtrace")        \
                                                                            \
  product(bool, LazyBootClassLoader, true,                                  \
          "Enable/disable lazy opening of boot class path entries")         \
                                                                            \
  diagnostic(bool, UseIncDec, true,                                         \
          "Use INC, DEC instructions on x86")                               \
                                                                            \
  product(bool, UseNewLongLShift, false,                                    \
          "Use optimized bitwise shift left")                               \
                                                                            \
  product(bool, UseStoreImmI16, true,                                       \
          "Use store immediate 16-bits value instruction on x86")           \
                                                                            \
  product(bool, UseAddressNop, false,                                       \
          "Use '0F 1F [addr]' NOP instructions on x86 cpus")                \
                                                                            \
  product(bool, UseXmmLoadAndClearUpper, true,                              \
          "Load low part of XMM register and clear upper part")             \
                                                                            \
  product(bool, UseXmmRegToRegMoveAll, false,                               \
          "Copy all XMM register bits when moving value between registers") \
                                                                            \
  product(bool, UseXmmI2D, false,                                           \
          "Use SSE2 CVTDQ2PD instruction to convert Integer to Double")     \
                                                                            \
  product(bool, UseXmmI2F, false,                                           \
          "Use SSE2 CVTDQ2PS instruction to convert Integer to Float")      \
                                                                            \
  product(bool, UseXMMForArrayCopy, false,                                  \
          "Use SSE2 MOVQ instruction for Arraycopy")                        \
                                                                            \
  product(bool, UseUnalignedLoadStores, false,                              \
          "Use SSE2 MOVDQU instruction for Arraycopy")                      \
                                                                            \
  product(intx, FieldsAllocationStyle, 1,                                   \
          "0 - type based with oops first, 1 - with oops last")             \
                                                                            \
  product(bool, CompactFields, true,                                        \
          "Allocate nonstatic fields in gaps between previous fields")      \
                                                                            \
  notproduct(bool, PrintCompactFieldsSavings, false,                        \
          "Print how many words were saved with CompactFields")             \
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
  notproduct(bool, TraceJVMCalls, false,                                    \
          "Trace JVM calls")                                                \
                                                                            \
  product(ccstr, TraceJVMTI, NULL,                                          \
          "Trace flags for JVMTI functions and events")                     \
                                                                            \
  product(bool, ForceFullGCJVMTIEpilogues, false,                           \
          "Force 'Full GC' was done semantics for JVMTI GC epilogues")      \
                                                                            \
  /* This option can change an EMCP method into an obsolete method. */      \
  /* This can affect tests that except specific methods to be EMCP. */      \
  /* This option should be used with caution. */                            \
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
  develop(bool, TraceThreadEvents, false,                                   \
          "Trace all thread events")                                        \
                                                                            \
  develop(bool, TraceBytecodes, false,                                      \
          "Trace bytecode execution")                                       \
                                                                            \
  develop(bool, TraceClassInitialization, false,                            \
          "Trace class initialization")                                     \
                                                                            \
  develop(bool, TraceExceptions, false,                                     \
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
         "Exercise and verify the compilation dependency mechanism")        \
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
          "Trace rewritting of method oops during oop map generation")      \
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
  develop(bool, TraceHPI, false,                                            \
          "Trace Host Porting Interface (HPI)")                             \
                                                                            \
  product(ccstr, HPILibPath, NULL,                                          \
          "Specify alternate path to HPI library")                          \
                                                                            \
  develop(bool, TraceProtectionDomainVerification, false,                   \
          "Trace protection domain verifcation")                            \
                                                                            \
  develop(bool, TraceClearedExceptions, false,                              \
          "Prints when an exception is forcibly cleared")                   \
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
  /* assembler */                                                           \
  product(bool, Use486InstrsOnly, false,                                    \
          "Use 80486 Compliant instruction subset")                         \
                                                                            \
  /* gc */                                                                  \
                                                                            \
  product(bool, UseSerialGC, false,                                         \
          "Use the serial garbage collector")                               \
                                                                            \
  experimental(bool, UseG1GC, false,                                        \
          "Use the Garbage-First garbage collector")                        \
                                                                            \
  product(bool, UseParallelGC, false,                                       \
          "Use the Parallel Scavenge garbage collector")                    \
                                                                            \
  product(bool, UseParallelOldGC, false,                                    \
          "Use the Parallel Old garbage collector")                         \
                                                                            \
  product(bool, UseParallelOldGCCompacting, true,                           \
          "In the Parallel Old garbage collector use parallel compaction")  \
                                                                            \
  product(bool, UseParallelDensePrefixUpdate, true,                         \
          "In the Parallel Old garbage collector use parallel dense"        \
          " prefix update")                                                 \
                                                                            \
  product(uintx, HeapMaximumCompactionInterval, 20,                         \
          "How often should we maximally compact the heap (not allowing "   \
          "any dead space)")                                                \
                                                                            \
  product(uintx, HeapFirstMaximumCompactionCount, 3,                        \
          "The collection count for the first maximum compaction")          \
                                                                            \
  product(bool, UseMaximumCompactionOnSystemGC, true,                       \
          "In the Parallel Old garbage collector maximum compaction for "   \
          "a system GC")                                                    \
                                                                            \
  product(uintx, ParallelOldDeadWoodLimiterMean, 50,                        \
          "The mean used by the par compact dead wood"                      \
          "limiter (a number between 0-100).")                              \
                                                                            \
  product(uintx, ParallelOldDeadWoodLimiterStdDev, 80,                      \
          "The standard deviation used by the par compact dead wood"        \
          "limiter (a number between 0-100).")                              \
                                                                            \
  product(bool, UseParallelOldGCDensePrefix, true,                          \
          "Use a dense prefix with the Parallel Old garbage collector")     \
                                                                            \
  product(uintx, ParallelGCThreads, 0,                                      \
          "Number of parallel threads parallel gc will use")                \
                                                                            \
  product(uintx, ParallelCMSThreads, 0,                                     \
          "Max number of threads CMS will use for concurrent work")         \
                                                                            \
  develop(bool, ParallelOldGCSplitALot, false,                              \
          "Provoke splitting (copying data from a young gen space to"       \
          "multiple destination spaces)")                                   \
                                                                            \
  develop(uintx, ParallelOldGCSplitInterval, 3,                             \
          "How often to provoke splitting a young gen space")               \
                                                                            \
  develop(bool, TraceRegionTasksQueuing, false,                             \
          "Trace the queuing of the region tasks")                          \
                                                                            \
  product(uintx, ParallelMarkingThreads, 0,                                 \
          "Number of marking threads concurrent gc will use")               \
                                                                            \
  product(uintx, YoungPLABSize, 4096,                                       \
          "Size of young gen promotion labs (in HeapWords)")                \
                                                                            \
  product(uintx, OldPLABSize, 1024,                                         \
          "Size of old gen promotion labs (in HeapWords)")                  \
                                                                            \
  product(uintx, GCTaskTimeStampEntries, 200,                               \
          "Number of time stamp entries per gc worker thread")              \
                                                                            \
  product(bool, AlwaysTenure, false,                                        \
          "Always tenure objects in eden. (ParallelGC only)")               \
                                                                            \
  product(bool, NeverTenure, false,                                         \
          "Never tenure objects in eden, May tenure on overflow "           \
          "(ParallelGC only)")                                              \
                                                                            \
  product(bool, ScavengeBeforeFullGC, true,                                 \
          "Scavenge youngest generation before each full GC, "              \
          "used with UseParallelGC")                                        \
                                                                            \
  develop(bool, ScavengeWithObjectsInToSpace, false,                        \
          "Allow scavenges to occur when to_space contains objects.")       \
                                                                            \
  product(bool, UseConcMarkSweepGC, false,                                  \
          "Use Concurrent Mark-Sweep GC in the old generation")             \
                                                                            \
  product(bool, ExplicitGCInvokesConcurrent, false,                         \
          "A System.gc() request invokes a concurrent collection;"          \
          " (effective only when UseConcMarkSweepGC)")                      \
                                                                            \
  product(bool, ExplicitGCInvokesConcurrentAndUnloadsClasses, false,        \
          "A System.gc() request invokes a concurrent collection and "      \
          "also unloads classes during such a concurrent gc cycle "         \
          "(effective only when UseConcMarkSweepGC)")                       \
                                                                            \
  develop(bool, UseCMSAdaptiveFreeLists, true,                              \
          "Use Adaptive Free Lists in the CMS generation")                  \
                                                                            \
  develop(bool, UseAsyncConcMarkSweepGC, true,                              \
          "Use Asynchronous Concurrent Mark-Sweep GC in the old generation")\
                                                                            \
  develop(bool, RotateCMSCollectionTypes, false,                            \
          "Rotate the CMS collections among concurrent and STW")            \
                                                                            \
  product(bool, UseCMSBestFit, true,                                        \
          "Use CMS best fit allocation strategy")                           \
                                                                            \
  product(bool, UseCMSCollectionPassing, true,                              \
          "Use passing of collection from background to foreground")        \
                                                                            \
  product(bool, UseParNewGC, false,                                         \
          "Use parallel threads in the new generation.")                    \
                                                                            \
  product(bool, ParallelGCVerbose, false,                                   \
          "Verbose output for parallel GC.")                                \
                                                                            \
  product(intx, ParallelGCBufferWastePct, 10,                               \
          "wasted fraction of parallel allocation buffer.")                 \
                                                                            \
  product(bool, ParallelGCRetainPLAB, true,                                 \
          "Retain parallel allocation buffers across scavenges.")           \
                                                                            \
  product(intx, TargetPLABWastePct, 10,                                     \
          "target wasted space in last buffer as pct of overall allocation")\
                                                                            \
  product(uintx, PLABWeight, 75,                                            \
          "Percentage (0-100) used to weight the current sample when"       \
          "computing exponentially decaying average for ResizePLAB.")       \
                                                                            \
  product(bool, ResizePLAB, true,                                           \
          "Dynamically resize (survivor space) promotion labs")             \
                                                                            \
  product(bool, PrintPLAB, false,                                           \
          "Print (survivor space) promotion labs sizing decisions")         \
                                                                            \
  product(intx, ParGCArrayScanChunk, 50,                                    \
          "Scan a subset and push remainder, if array is bigger than this") \
                                                                            \
  product(bool, ParGCUseLocalOverflow, false,                               \
          "Instead of a global overflow list, use local overflow stacks")   \
                                                                            \
  product(bool, ParGCTrimOverflow, true,                                    \
          "Eagerly trim the local overflow lists (when ParGCUseLocalOverflow") \
                                                                            \
  notproduct(bool, ParGCWorkQueueOverflowALot, false,                       \
          "Whether we should simulate work queue overflow in ParNew")       \
                                                                            \
  notproduct(uintx, ParGCWorkQueueOverflowInterval, 1000,                   \
          "An `interval' counter that determines how frequently "           \
          "we simulate overflow; a smaller number increases frequency")     \
                                                                            \
  product(uintx, ParGCDesiredObjsFromOverflowList, 20,                      \
          "The desired number of objects to claim from the overflow list")  \
                                                                            \
  product(uintx, CMSParPromoteBlocksToClaim, 50,                            \
          "Number of blocks to attempt to claim when refilling CMS LAB for "\
          "parallel GC.")                                                   \
                                                                            \
  product(bool, AlwaysPreTouch, false,                                      \
          "It forces all freshly committed pages to be pre-touched.")       \
                                                                            \
  product(bool, CMSUseOldDefaults, false,                                   \
          "A flag temporarily introduced to allow reverting to some "       \
          "older default settings; older as of 6.0")                        \
                                                                            \
  product(intx, CMSYoungGenPerWorker, 16*M,                                 \
          "The amount of young gen chosen by default per GC worker "        \
          "thread available")                                               \
                                                                            \
  product(bool, GCOverheadReporting, false,                                 \
         "Enables the GC overhead reporting facility")                      \
                                                                            \
  product(intx, GCOverheadReportingPeriodMS, 100,                           \
          "Reporting period for conc GC overhead reporting, in ms ")        \
                                                                            \
  product(bool, CMSIncrementalMode, false,                                  \
          "Whether CMS GC should operate in \"incremental\" mode")          \
                                                                            \
  product(uintx, CMSIncrementalDutyCycle, 10,                               \
          "CMS incremental mode duty cycle (a percentage, 0-100).  If"      \
          "CMSIncrementalPacing is enabled, then this is just the initial"  \
          "value")                                                          \
                                                                            \
  product(bool, CMSIncrementalPacing, true,                                 \
          "Whether the CMS incremental mode duty cycle should be "          \
          "automatically adjusted")                                         \
                                                                            \
  product(uintx, CMSIncrementalDutyCycleMin, 0,                             \
          "Lower bound on the duty cycle when CMSIncrementalPacing is "     \
          "enabled (a percentage, 0-100)")                                  \
                                                                            \
  product(uintx, CMSIncrementalSafetyFactor, 10,                            \
          "Percentage (0-100) used to add conservatism when computing the " \
          "duty cycle")                                                     \
                                                                            \
  product(uintx, CMSIncrementalOffset, 0,                                   \
          "Percentage (0-100) by which the CMS incremental mode duty cycle" \
          " is shifted to the right within the period between young GCs")   \
                                                                            \
  product(uintx, CMSExpAvgFactor, 25,                                       \
          "Percentage (0-100) used to weight the current sample when "      \
          "computing exponential averages for CMS statistics")              \
                                                                            \
  product(uintx, CMS_FLSWeight, 50,                                         \
          "Percentage (0-100) used to weight the current sample when "      \
          "computing exponentially decating averages for CMS FLS statistics") \
                                                                            \
  product(uintx, CMS_FLSPadding, 2,                                         \
          "The multiple of deviation from mean to use for buffering "       \
          "against volatility in free list demand.")                        \
                                                                            \
  product(uintx, FLSCoalescePolicy, 2,                                      \
          "CMS: Aggression level for coalescing, increasing from 0 to 4")   \
                                                                            \
  product(uintx, CMS_SweepWeight, 50,                                       \
          "Percentage (0-100) used to weight the current sample when "      \
          "computing exponentially decaying average for inter-sweep "       \
          "duration")                                                       \
                                                                            \
  product(uintx, CMS_SweepPadding, 2,                                       \
          "The multiple of deviation from mean to use for buffering "       \
          "against volatility in inter-sweep duration.")                    \
                                                                            \
  product(uintx, CMS_SweepTimerThresholdMillis, 10,                         \
          "Skip block flux-rate sampling for an epoch unless inter-sweep "  \
          "duration exceeds this threhold in milliseconds")                 \
                                                                            \
  develop(bool, CMSTraceIncrementalMode, false,                             \
          "Trace CMS incremental mode")                                     \
                                                                            \
  develop(bool, CMSTraceIncrementalPacing, false,                           \
          "Trace CMS incremental mode pacing computation")                  \
                                                                            \
  develop(bool, CMSTraceThreadState, false,                                 \
          "Trace the CMS thread state (enable the trace_state() method)")   \
                                                                            \
  product(bool, CMSClassUnloadingEnabled, false,                            \
          "Whether class unloading enabled when using CMS GC")              \
                                                                            \
  product(uintx, CMSClassUnloadingMaxInterval, 0,                           \
          "When CMS class unloading is enabled, the maximum CMS cycle count"\
          " for which classes may not be unloaded")                         \
                                                                            \
  product(bool, CMSCompactWhenClearAllSoftRefs, true,                       \
          "Compact when asked to collect CMS gen with clear_all_soft_refs") \
                                                                            \
  product(bool, UseCMSCompactAtFullCollection, true,                        \
          "Use mark sweep compact at full collections")                     \
                                                                            \
  product(uintx, CMSFullGCsBeforeCompaction, 0,                             \
          "Number of CMS full collection done before compaction if > 0")    \
                                                                            \
  develop(intx, CMSDictionaryChoice, 0,                                     \
          "Use BinaryTreeDictionary as default in the CMS generation")      \
                                                                            \
  product(uintx, CMSIndexedFreeListReplenish, 4,                            \
          "Replenish and indexed free list with this number of chunks")     \
                                                                            \
  product(bool, CMSLoopWarn, false,                                         \
          "Warn in case of excessive CMS looping")                          \
                                                                            \
  develop(bool, CMSOverflowEarlyRestoration, false,                         \
          "Whether preserved marks should be restored early")               \
                                                                            \
  product(uintx, CMSMarkStackSize, NOT_LP64(32*K) LP64_ONLY(4*M),           \
          "Size of CMS marking stack")                                      \
                                                                            \
  product(uintx, CMSMarkStackSizeMax, NOT_LP64(4*M) LP64_ONLY(512*M),       \
          "Max size of CMS marking stack")                                  \
                                                                            \
  notproduct(bool, CMSMarkStackOverflowALot, false,                         \
          "Whether we should simulate frequent marking stack / work queue"  \
          " overflow")                                                      \
                                                                            \
  notproduct(uintx, CMSMarkStackOverflowInterval, 1000,                     \
          "An `interval' counter that determines how frequently"            \
          " we simulate overflow; a smaller number increases frequency")    \
                                                                            \
  product(uintx, CMSMaxAbortablePrecleanLoops, 0,                           \
          "(Temporary, subject to experimentation)"                         \
          "Maximum number of abortable preclean iterations, if > 0")        \
                                                                            \
  product(intx, CMSMaxAbortablePrecleanTime, 5000,                          \
          "(Temporary, subject to experimentation)"                         \
          "Maximum time in abortable preclean in ms")                       \
                                                                            \
  product(uintx, CMSAbortablePrecleanMinWorkPerIteration, 100,              \
          "(Temporary, subject to experimentation)"                         \
          "Nominal minimum work per abortable preclean iteration")          \
                                                                            \
  product(intx, CMSAbortablePrecleanWaitMillis, 100,                        \
          "(Temporary, subject to experimentation)"                         \
          " Time that we sleep between iterations when not given"           \
          " enough work per iteration")                                     \
                                                                            \
  product(uintx, CMSRescanMultiple, 32,                                     \
          "Size (in cards) of CMS parallel rescan task")                    \
                                                                            \
  product(uintx, CMSConcMarkMultiple, 32,                                   \
          "Size (in cards) of CMS concurrent MT marking task")              \
                                                                            \
  product(uintx, CMSRevisitStackSize, 1*M,                                  \
          "Size of CMS KlassKlass revisit stack")                           \
                                                                            \
  product(bool, CMSAbortSemantics, false,                                   \
          "Whether abort-on-overflow semantics is implemented")             \
                                                                            \
  product(bool, CMSParallelRemarkEnabled, true,                             \
          "Whether parallel remark enabled (only if ParNewGC)")             \
                                                                            \
  product(bool, CMSParallelSurvivorRemarkEnabled, true,                     \
          "Whether parallel remark of survivor space"                       \
          " enabled (effective only if CMSParallelRemarkEnabled)")          \
                                                                            \
  product(bool, CMSPLABRecordAlways, true,                                  \
          "Whether to always record survivor space PLAB bdries"             \
          " (effective only if CMSParallelSurvivorRemarkEnabled)")          \
                                                                            \
  product(bool, CMSConcurrentMTEnabled, true,                               \
          "Whether multi-threaded concurrent work enabled (if ParNewGC)")   \
                                                                            \
  product(bool, CMSPermGenPrecleaningEnabled, true,                         \
          "Whether concurrent precleaning enabled in perm gen"              \
          " (effective only when CMSPrecleaningEnabled is true)")           \
                                                                            \
  product(bool, CMSPrecleaningEnabled, true,                                \
          "Whether concurrent precleaning enabled")                         \
                                                                            \
  product(uintx, CMSPrecleanIter, 3,                                        \
          "Maximum number of precleaning iteration passes")                 \
                                                                            \
  product(uintx, CMSPrecleanNumerator, 2,                                   \
          "CMSPrecleanNumerator:CMSPrecleanDenominator yields convergence"  \
          " ratio")                                                         \
                                                                            \
  product(uintx, CMSPrecleanDenominator, 3,                                 \
          "CMSPrecleanNumerator:CMSPrecleanDenominator yields convergence"  \
          " ratio")                                                         \
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
          "Don't re-iterate if #dirty cards less than this")                \
                                                                            \
  product(bool, CMSCleanOnEnter, true,                                      \
          "Clean-on-enter optimization for reducing number of dirty cards") \
                                                                            \
  product(uintx, CMSRemarkVerifyVariant, 1,                                 \
          "Choose variant (1,2) of verification following remark")          \
                                                                            \
  product(uintx, CMSScheduleRemarkEdenSizeThreshold, 2*M,                   \
          "If Eden used is below this value, don't try to schedule remark") \
                                                                            \
  product(uintx, CMSScheduleRemarkEdenPenetration, 50,                      \
          "The Eden occupancy % at which to try and schedule remark pause") \
                                                                            \
  product(uintx, CMSScheduleRemarkSamplingRatio, 5,                         \
          "Start sampling Eden top at least before yg occupancy reaches"    \
          " 1/<ratio> of the size at which we plan to schedule remark")     \
                                                                            \
  product(uintx, CMSSamplingGrain, 16*K,                                    \
          "The minimum distance between eden samples for CMS (see above)")  \
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
  product(intx, CMSWaitDuration, 2000,                                      \
          "Time in milliseconds that CMS thread waits for young GC")        \
                                                                            \
  product(bool, CMSYield, true,                                             \
          "Yield between steps of concurrent mark & sweep")                 \
                                                                            \
  product(uintx, CMSBitMapYieldQuantum, 10*M,                               \
          "Bitmap operations should process at most this many bits"         \
          "between yields")                                                 \
                                                                            \
  diagnostic(bool, FLSVerifyAllHeapReferences, false,                       \
          "Verify that all refs across the FLS boundary "                   \
          " are to valid objects")                                          \
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
          "Do (expensive!) block offset array verification")                \
                                                                            \
  product(bool, BlockOffsetArrayUseUnallocatedBlock, trueInDebug,           \
          "Maintain _unallocated_block in BlockOffsetArray"                 \
          " (currently applicable only to CMS collector)")                  \
                                                                            \
  develop(bool, TraceCMSState, false,                                       \
          "Trace the state of the CMS collection")                          \
                                                                            \
  product(intx, RefDiscoveryPolicy, 0,                                      \
          "Whether reference-based(0) or referent-based(1)")                \
                                                                            \
  product(bool, ParallelRefProcEnabled, false,                              \
          "Enable parallel reference processing whenever possible")         \
                                                                            \
  product(bool, ParallelRefProcBalancingEnabled, true,                      \
          "Enable balancing of reference processing queues")                \
                                                                            \
  product(intx, CMSTriggerRatio, 80,                                        \
          "Percentage of MinHeapFreeRatio in CMS generation that is "       \
          "allocated before a CMS collection cycle commences")              \
                                                                            \
  product(intx, CMSTriggerPermRatio, 80,                                    \
          "Percentage of MinHeapFreeRatio in the CMS perm generation that " \
          "is allocated before a CMS collection cycle commences, that "     \
          "also collects the perm generation")                              \
                                                                            \
  product(uintx, CMSBootstrapOccupancy, 50,                                 \
          "Percentage CMS generation occupancy at which to "                \
          "initiate CMS collection for bootstrapping collection stats")     \
                                                                            \
  product(intx, CMSInitiatingOccupancyFraction, -1,                         \
          "Percentage CMS generation occupancy to start a CMS collection "  \
          "cycle. A negative value means that CMSTriggerRatio is used")     \
                                                                            \
  product(intx, CMSInitiatingPermOccupancyFraction, -1,                     \
          "Percentage CMS perm generation occupancy to start a "            \
          "CMScollection cycle. A negative value means that "               \
          "CMSTriggerPermRatio is used")                                    \
                                                                            \
  product(bool, UseCMSInitiatingOccupancyOnly, false,                       \
          "Only use occupancy as a crierion for starting a CMS collection") \
                                                                            \
  product(intx, CMSIsTooFullPercentage, 98,                                 \
          "An absolute ceiling above which CMS will always consider the "   \
          "perm gen ripe for collection")                                   \
                                                                            \
  develop(bool, CMSTestInFreeList, false,                                   \
          "Check if the coalesced range is already in the "                 \
          "free lists as claimed")                                          \
                                                                            \
  notproduct(bool, CMSVerifyReturnedBytes, false,                           \
          "Check that all the garbage collected was returned to the "       \
          "free lists.")                                                    \
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
  product(bool, HandlePromotionFailure, true,                               \
          "The youngest generation collection does not require "            \
          "a guarantee of full promotion of all live objects.")             \
                                                                            \
  notproduct(bool, PromotionFailureALot, false,                             \
          "Use promotion failure handling on every youngest generation "    \
          "collection")                                                     \
                                                                            \
  develop(uintx, PromotionFailureALotCount, 1000,                           \
          "Number of promotion failures occurring at ParGCAllocBuffer"      \
          "refill attempts (ParNew) or promotion attempts "                 \
          "(other young collectors) ")                                      \
                                                                            \
  develop(uintx, PromotionFailureALotInterval, 5,                           \
          "Total collections between promotion failures alot")              \
                                                                            \
  develop(intx, WorkStealingSleepMillis, 1,                                 \
          "Sleep time when sleep is used for yields")                       \
                                                                            \
  develop(uintx, WorkStealingYieldsBeforeSleep, 1000,                       \
          "Number of yields before a sleep is done during workstealing")    \
                                                                            \
  develop(uintx, WorkStealingHardSpins, 4096,                               \
          "Number of iterations in a spin loop between checks on "          \
          "time out of hard spin")                                          \
                                                                            \
  develop(uintx, WorkStealingSpinToYieldRatio, 10,                          \
          "Ratio of hard spins to calls to yield")                          \
                                                                            \
  product(uintx, PreserveMarkStackSize, 1024,                               \
          "Size for stack used in promotion failure handling")              \
                                                                            \
  product_pd(bool, UseTLAB, "Use thread-local object allocation")           \
                                                                            \
  product_pd(bool, ResizeTLAB,                                              \
          "Dynamically resize tlab size for threads")                       \
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
          "Print various TLAB related information")                         \
                                                                            \
  product(bool, PrintRevisitStats, false,                                   \
          "Print revisit (klass and MDO) stack related information")        \
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
  product(uintx, ErgoHeapSizeLimit, 0,                                      \
          "Maximum ergonomically set heap size (in bytes); zero means use " \
          "MaxRAM / MaxRAMFraction")                                        \
                                                                            \
  product(uintx, MaxRAMFraction, 4,                                         \
          "Maximum fraction (1/n) of real memory used for maximum heap "    \
          "size")                                                           \
                                                                            \
  product(uintx, DefaultMaxRAMFraction, 4,                                  \
          "Maximum fraction (1/n) of real memory used for maximum heap "    \
          "size; deprecated: to be renamed to MaxRAMFraction")              \
                                                                            \
  product(uintx, MinRAMFraction, 2,                                         \
          "Minimum fraction (1/n) of real memory used for maxmimum heap "   \
          "size on systems with small physical memory size")                \
                                                                            \
  product(uintx, InitialRAMFraction, 64,                                    \
          "Fraction (1/n) of real memory used for initial heap size")       \
                                                                            \
  product(bool, UseAutoGCSelectPolicy, false,                               \
          "Use automatic collection selection policy")                      \
                                                                            \
  product(uintx, AutoGCSelectPauseMillis, 5000,                             \
          "Automatic GC selection pause threshhold in ms")                  \
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
          "Use statistics from System.GC for adaptive size policy")         \
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
          "Policy for changeing generation size for throughput goals")      \
                                                                            \
  product(uintx, AdaptiveSizePausePolicy, 0,                                \
          "Policy for changing generation size for pause goals")            \
                                                                            \
  develop(bool, PSAdjustTenuredGenForMinorPause, false,                     \
          "Adjust tenured generation to achive a minor pause goal")         \
                                                                            \
  develop(bool, PSAdjustYoungGenForMajorPause, false,                       \
          "Adjust young generation to achive a major pause goal")           \
                                                                            \
  product(uintx, AdaptiveSizePolicyInitializingSteps, 20,                   \
          "Number of steps where heuristics is used before data is used")   \
                                                                            \
  develop(uintx, AdaptiveSizePolicyReadyThreshold, 5,                       \
          "Number of collections before the adaptive sizing is started")    \
                                                                            \
  product(uintx, AdaptiveSizePolicyOutputInterval, 0,                       \
          "Collecton interval for printing information; zero => never")     \
                                                                            \
  product(bool, UseAdaptiveSizePolicyFootprintGoal, true,                   \
          "Use adaptive minimum footprint as a goal")                       \
                                                                            \
  product(uintx, AdaptiveSizePolicyWeight, 10,                              \
          "Weight given to exponential resizing, between 0 and 100")        \
                                                                            \
  product(uintx, AdaptiveTimeWeight,       25,                              \
          "Weight given to time in adaptive policy, between 0 and 100")     \
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
  product(uintx, AdaptivePermSizeWeight, 20,                                \
          "Weight for perm gen exponential resizing, between 0 and 100")    \
                                                                            \
  product(uintx, PermGenPadding, 3,                                         \
          "How much buffer to keep for perm gen sizing")                    \
                                                                            \
  product(uintx, ThresholdTolerance, 10,                                    \
          "Allowed collection cost difference between generations")         \
                                                                            \
  product(uintx, AdaptiveSizePolicyCollectionCostMargin, 50,                \
          "If collection costs are within margin, reduce both by full "     \
          "delta")                                                          \
                                                                            \
  product(uintx, YoungGenerationSizeIncrement, 20,                          \
          "Adaptive size percentage change in young generation")            \
                                                                            \
  product(uintx, YoungGenerationSizeSupplement, 80,                         \
          "Supplement to YoungedGenerationSizeIncrement used at startup")   \
                                                                            \
  product(uintx, YoungGenerationSizeSupplementDecay, 8,                     \
          "Decay factor to YoungedGenerationSizeSupplement")                \
                                                                            \
  product(uintx, TenuredGenerationSizeIncrement, 20,                        \
          "Adaptive size percentage change in tenured generation")          \
                                                                            \
  product(uintx, TenuredGenerationSizeSupplement, 80,                       \
          "Supplement to TenuredGenerationSizeIncrement used at startup")   \
                                                                            \
  product(uintx, TenuredGenerationSizeSupplementDecay, 2,                   \
          "Decay factor to TenuredGenerationSizeIncrement")                 \
                                                                            \
  product(uintx, MaxGCPauseMillis, max_uintx,                               \
          "Adaptive size policy maximum GC pause time goal in msec, "       \
          "or (G1 Only) the max. GC time per MMU time slice")               \
                                                                            \
  product(intx, GCPauseIntervalMillis, 500,                                 \
          "Time slice for MMU specification")                               \
                                                                            \
  product(uintx, MaxGCMinorPauseMillis, max_uintx,                          \
          "Adaptive size policy maximum GC minor pause time goal in msec")  \
                                                                            \
  product(uintx, GCTimeRatio, 99,                                           \
          "Adaptive size policy application time to GC time ratio")         \
                                                                            \
  product(uintx, AdaptiveSizeDecrementScaleFactor, 4,                       \
          "Adaptive size scale down factor for shrinking")                  \
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
          "Initial ratio of eden/survivor space size")                      \
                                                                            \
  product(uintx, BaseFootPrintEstimate, 256*M,                              \
          "Estimate of footprint other than Java Heap")                     \
                                                                            \
  product(bool, UseGCOverheadLimit, true,                                   \
          "Use policy to limit of proportion of time spent in GC "          \
          "before an OutOfMemory error is thrown")                          \
                                                                            \
  product(uintx, GCTimeLimit, 98,                                           \
          "Limit of proportion of time spent in GC before an OutOfMemory"   \
          "error is thrown (used with GCHeapFreeLimit)")                    \
                                                                            \
  product(uintx, GCHeapFreeLimit, 2,                                        \
          "Minimum percentage of free space after a full GC before an "     \
          "OutOfMemoryError is thrown (used with GCTimeLimit)")             \
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
  develop(bool, UsePrefetchQueue, true,                                     \
          "Use the prefetch queue during PS promotion")                     \
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
  diagnostic(bool, VerifyRememberedSets, false,                             \
          "Verify GC remembered sets")                                      \
                                                                            \
  diagnostic(bool, VerifyObjectStartArray, true,                            \
          "Verify GC object start array if verify before/after")            \
                                                                            \
  product(bool, DisableExplicitGC, false,                                   \
          "Tells whether calling System.gc() does a full GC")               \
                                                                            \
  notproduct(bool, CheckMemoryInitialization, false,                        \
          "Checks memory initialization")                                   \
                                                                            \
  product(bool, CollectGen0First, false,                                    \
          "Collect youngest generation before each full GC")                \
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
          "number of times the coordinator GC thread will sleep while "     \
          "yielding before giving up and resuming GC")                      \
                                                                            \
  product(uintx, CMSYieldSleepCount, 0,                                     \
          "number of times a GC thread (minus the coordinator) "            \
          "will sleep while yielding before giving up and resuming GC")     \
                                                                            \
  /* gc tracing */                                                          \
  manageable(bool, PrintGC, false,                                          \
          "Print message at garbage collect")                               \
                                                                            \
  manageable(bool, PrintGCDetails, false,                                   \
          "Print more details at garbage collect")                          \
                                                                            \
  manageable(bool, PrintGCDateStamps, false,                                \
          "Print date stamps at garbage collect")                           \
                                                                            \
  manageable(bool, PrintGCTimeStamps, false,                                \
          "Print timestamps at garbage collect")                            \
                                                                            \
  product(bool, PrintGCTaskTimeStamps, false,                               \
          "Print timestamps for individual gc worker thread tasks")         \
                                                                            \
  develop(intx, ConcGCYieldTimeout, 0,                                      \
          "If non-zero, assert that GC threads yield within this # of ms.") \
                                                                            \
  notproduct(bool, TraceMarkSweep, false,                                   \
          "Trace mark sweep")                                               \
                                                                            \
  product(bool, PrintReferenceGC, false,                                    \
          "Print times spent handling reference objects during GC "         \
          " (enabled only when PrintGCDetails)")                            \
                                                                            \
  develop(bool, TraceReferenceGC, false,                                    \
          "Trace handling of soft/weak/final/phantom references")           \
                                                                            \
  develop(bool, TraceFinalizerRegistration, false,                          \
         "Trace registration of final references")                          \
                                                                            \
  notproduct(bool, TraceScavenge, false,                                    \
          "Trace scavenge")                                                 \
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
  product(bool, TraceGen0Time, false,                                       \
          "Trace accumulated time for Gen 0 collection")                    \
                                                                            \
  product(bool, TraceGen1Time, false,                                       \
          "Trace accumulated time for Gen 1 collection")                    \
                                                                            \
  product(bool, PrintTenuringDistribution, false,                           \
          "Print tenuring age information")                                 \
                                                                            \
  product_rw(bool, PrintHeapAtGC, false,                                    \
          "Print heap layout before and after each GC")                     \
                                                                            \
  product_rw(bool, PrintHeapAtGCExtended, false,                            \
          "Prints extended information about the layout of the heap "       \
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
  develop(bool, TraceGCTaskThread, false,                                   \
          "Trace actions of the GC task threads")                           \
                                                                            \
  product(bool, PrintParallelOldGCPhaseTimes, false,                        \
          "Print the time taken by each parallel old gc phase."             \
          "PrintGCDetails must also be enabled.")                           \
                                                                            \
  develop(bool, TraceParallelOldGCMarkingPhase, false,                      \
          "Trace parallel old gc marking phase")                            \
                                                                            \
  develop(bool, TraceParallelOldGCSummaryPhase, false,                      \
          "Trace parallel old gc summary phase")                            \
                                                                            \
  develop(bool, TraceParallelOldGCCompactionPhase, false,                   \
          "Trace parallel old gc compaction phase")                         \
                                                                            \
  develop(bool, TraceParallelOldGCDensePrefix, false,                       \
          "Trace parallel old gc dense prefix computation")                 \
                                                                            \
  develop(bool, IgnoreLibthreadGPFault, false,                              \
          "Suppress workaround for libthread GP fault")                     \
                                                                            \
  product(bool, PrintJNIGCStalls, false,                                    \
          "Print diagnostic message when GC is stalled"                     \
          "by JNI critical section")                                        \
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
  develop(bool, CIPrintCompileQueue, false,                                 \
          "display the contents of the compile queue whenever a "           \
          "compilation is enqueued")                                        \
                                                                            \
  develop(bool, CIPrintRequests, false,                                     \
          "display every request for compilation")                          \
                                                                            \
  product(bool, CITime, false,                                              \
          "collect timing information for compilation")                     \
                                                                            \
  develop(bool, CITimeEach, false,                                          \
          "display timing information after each successful compilation")   \
                                                                            \
  develop(bool, CICountOSR, true,                                           \
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
  develop(intx, CICloneLoopTestLimit, 100,                                  \
          "size limit for blocks heuristically cloned in ciTypeFlow")       \
                                                                            \
  /* temp diagnostics */                                                    \
                                                                            \
  diagnostic(bool, TraceRedundantCompiles, false,                           \
          "Have compile broker print when a request already in the queue is"\
          " requested again")                                               \
                                                                            \
  diagnostic(bool, InitialCompileFast, false,                               \
          "Initial compile at CompLevel_fast_compile")                      \
                                                                            \
  diagnostic(bool, InitialCompileReallyFast, false,                         \
          "Initial compile at CompLevel_really_fast_compile (no profile)")  \
                                                                            \
  diagnostic(bool, FullProfileOnReInterpret, true,                          \
          "On re-interpret unc-trap compile next at CompLevel_fast_compile")\
                                                                            \
  /* compiler */                                                            \
                                                                            \
  product(intx, CICompilerCount, CI_COMPILER_COUNT,                         \
          "Number of compiler threads to run")                              \
                                                                            \
  product(intx, CompilationPolicyChoice, 0,                                 \
          "which compilation policy (0/1)")                                 \
                                                                            \
  develop(bool, UseStackBanging, true,                                      \
          "use stack banging for stack overflow checks (required for "      \
          "proper StackOverflow handling; disable only to measure cost "    \
          "of stackbanging)")                                               \
                                                                            \
  develop(bool, Use24BitFPMode, true,                                       \
          "Set 24-bit FPU mode on a per-compile basis ")                    \
                                                                            \
  develop(bool, Use24BitFP, true,                                           \
          "use FP instructions that produce 24-bit precise results")        \
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
          "generate code for implicit null checks")                         \
                                                                            \
  product(bool, PrintSafepointStatistics, false,                            \
          "print statistics about safepoint synchronization")               \
                                                                            \
  product(intx, PrintSafepointStatisticsCount, 300,                         \
          "total number of safepoint statistics collected "                 \
          "before printing them out")                                       \
                                                                            \
  product(intx, PrintSafepointStatisticsTimeout,  -1,                       \
          "print safepoint statistics only when safepoint takes"            \
          " more than PrintSafepointSatisticsTimeout in millis")            \
                                                                            \
  develop(bool, InlineAccessors, true,                                      \
          "inline accessor methods (get/set)")                              \
                                                                            \
  product(bool, Inline, true,                                               \
          "enable inlining")                                                \
                                                                            \
  product(bool, ClipInlining, true,                                         \
          "clip inlining if aggregate method exceeds DesiredMethodLimit")   \
                                                                            \
  develop(bool, UseCHA, true,                                               \
          "enable CHA")                                                     \
                                                                            \
  product(bool, UseTypeProfile, true,                                       \
          "Check interpreter profile for historically monomorphic calls")   \
                                                                            \
  product(intx, TypeProfileMajorReceiverPercent, 90,                        \
          "% of major receiver type to all profiled receivers")             \
                                                                            \
  notproduct(bool, TimeCompiler, false,                                     \
          "time the compiler")                                              \
                                                                            \
  notproduct(bool, TimeCompiler2, false,                                    \
          "detailed time the compiler (requires +TimeCompiler)")            \
                                                                            \
  diagnostic(bool, PrintInlining, false,                                    \
          "prints inlining optimizations")                                  \
                                                                            \
  diagnostic(bool, PrintIntrinsics, false,                                  \
          "prints attempted and successful inlining of intrinsics")         \
                                                                            \
  product(bool, UseCountLeadingZerosInstruction, false,                     \
          "Use count leading zeros instruction")                            \
                                                                            \
  product(bool, UsePopCountInstruction, false,                              \
          "Use population count instruction")                               \
                                                                            \
  diagnostic(ccstrlist, DisableIntrinsic, "",                               \
          "do not expand intrinsics whose (internal) names appear here")    \
                                                                            \
  develop(bool, StressReflectiveCode, false,                                \
          "Use inexact types at allocations, etc., to test reflection")     \
                                                                            \
  develop(bool, EagerInitialization, false,                                 \
          "Eagerly initialize classes if possible")                         \
                                                                            \
  product(bool, Tier1UpdateMethodData, trueInTiered,                        \
          "Update methodDataOops in Tier1-generated code")                  \
                                                                            \
  develop(bool, TraceMethodReplacement, false,                              \
          "Print when methods are replaced do to recompilation")            \
                                                                            \
  develop(bool, PrintMethodFlushing, false,                                 \
          "print the nmethods being flushed")                               \
                                                                            \
  notproduct(bool, LogMultipleMutexLocking, false,                          \
          "log locking and unlocking of mutexes (only if multiple locks "   \
          "are held)")                                                      \
                                                                            \
  develop(bool, UseRelocIndex, false,                                       \
         "use an index to speed random access to relocations")              \
                                                                            \
  develop(bool, StressCodeBuffers, false,                                   \
         "Exercise code buffer expansion and other rare state changes")     \
                                                                            \
  diagnostic(bool, DebugNonSafepoints, trueInDebug,                         \
         "Generate extra debugging info for non-safepoints in nmethods")    \
                                                                            \
  diagnostic(bool, DebugInlinedCalls, true,                                 \
         "If false, restricts profiled locations to the root method only")  \
                                                                            \
  product(bool, PrintVMOptions, trueInDebug,                                \
         "Print flags that appeared on the command line")                   \
                                                                            \
  product(bool, IgnoreUnrecognizedVMOptions, false,                         \
         "Ignore unrecognized VM options")                                  \
                                                                            \
  product(bool, PrintCommandLineFlags, false,                               \
         "Print flags specified on command line or set by ergonomics")      \
                                                                            \
  product(bool, PrintFlagsInitial, false,                                   \
         "Print all VM flags before argument processing and exit VM")       \
                                                                            \
  product(bool, PrintFlagsFinal, false,                                     \
         "Print all VM flags after argument and ergonomic processing")      \
                                                                            \
  diagnostic(bool, SerializeVMOutput, true,                                 \
         "Use a mutex to serialize output to tty and hotspot.log")          \
                                                                            \
  diagnostic(bool, DisplayVMOutput, true,                                   \
         "Display all VM output on the tty, independently of LogVMOutput")  \
                                                                            \
  diagnostic(bool, LogVMOutput, trueInDebug,                                \
         "Save VM output to hotspot.log, or to LogFile")                    \
                                                                            \
  diagnostic(ccstr, LogFile, NULL,                                          \
         "If LogVMOutput is on, save VM output to this file [hotspot.log]") \
                                                                            \
  product(ccstr, ErrorFile, NULL,                                           \
         "If an error occurs, save the error data to this file "            \
         "[default: ./hs_err_pid%p.log] (%p replaced with pid)")            \
                                                                            \
  product(bool, DisplayVMOutputToStderr, false,                             \
         "If DisplayVMOutput is true, display all VM output to stderr")     \
                                                                            \
  product(bool, DisplayVMOutputToStdout, false,                             \
         "If DisplayVMOutput is true, display all VM output to stdout")     \
                                                                            \
  product(bool, UseHeavyMonitors, false,                                    \
          "use heavyweight instead of lightweight Java monitors")           \
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
  notproduct(bool, VerifyLoopOptimizations, false,                          \
          "verify major loop optimizations")                                \
                                                                            \
  product(bool, RangeCheckElimination, true,                                \
          "Split loop iterations to eliminate range checks")                \
                                                                            \
  develop_pd(bool, UncommonNullCast,                                        \
          "track occurrences of null in casts; adjust compiler tactics")    \
                                                                            \
  develop(bool, TypeProfileCasts,  true,                                    \
          "treat casts like calls for purposes of type profiling")          \
                                                                            \
  develop(bool, MonomorphicArrayCheck, true,                                \
          "Uncommon-trap array store checks that require full type check")  \
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
  notproduct(bool, CompileTheWorldIgnoreInitErrors, false,                  \
          "Compile all methods although class initializer failed")          \
                                                                            \
  develop(bool, TraceIterativeGVN, false,                                   \
          "Print progress during Iterative Global Value Numbering")         \
                                                                            \
  develop(bool, FillDelaySlots, true,                                       \
          "Fill delay slots (on SPARC only)")                               \
                                                                            \
  develop(bool, VerifyIterativeGVN, false,                                  \
          "Verify Def-Use modifications during sparse Iterative Global "    \
          "Value Numbering")                                                \
                                                                            \
  notproduct(bool, TracePhaseCCP, false,                                    \
          "Print progress during Conditional Constant Propagation")         \
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
  develop(bool, PrintDominators, false,                                     \
          "Print out dominator trees for GVN")                              \
                                                                            \
  develop(bool, UseLoopSafepoints, true,                                    \
          "Generate Safepoint nodes in every loop")                         \
                                                                            \
  notproduct(bool, TraceCISCSpill, false,                                   \
          "Trace allocators use of cisc spillable instructions")            \
                                                                            \
  notproduct(bool, TraceSpilling, false,                                    \
          "Trace spilling")                                                 \
                                                                            \
  develop(bool, DeutschShiffmanExceptions, true,                            \
          "Fast check to find exception handler for precisely typed "       \
          "exceptions")                                                     \
                                                                            \
  product(bool, SplitIfBlocks, true,                                        \
          "Clone compares and control flow through merge points to fold "   \
          "some branches")                                                  \
                                                                            \
  develop(intx, FastAllocateSizeLimit, 128*K,                               \
          /* Note:  This value is zero mod 1<<13 for a cheap sparc set. */  \
          "Inline allocations larger than this in doublewords must go slow")\
                                                                            \
  product(bool, AggressiveOpts, false,                                      \
          "Enable aggressive optimizations - see arguments.cpp")            \
                                                                            \
  product(bool, UseStringCache, false,                                      \
          "Enable String cache capabilities on String.java")                \
                                                                            \
  /* statistics */                                                          \
  develop(bool, UseVTune, false,                                            \
          "enable support for Intel's VTune profiler")                      \
                                                                            \
  develop(bool, CountCompiledCalls, false,                                  \
          "counts method invocations")                                      \
                                                                            \
  notproduct(bool, CountRuntimeCalls, false,                                \
          "counts VM runtime calls")                                        \
                                                                            \
  develop(bool, CountJNICalls, false,                                       \
          "counts jni method invocations")                                  \
                                                                            \
  notproduct(bool, CountJVMCalls, false,                                    \
          "counts jvm method invocations")                                  \
                                                                            \
  notproduct(bool, CountRemovableExceptions, false,                         \
          "count exceptions that could be replaced by branches due to "     \
          "inlining")                                                       \
                                                                            \
  notproduct(bool, ICMissHistogram, false,                                  \
          "produce histogram of IC misses")                                 \
                                                                            \
  notproduct(bool, PrintClassStatistics, false,                             \
          "prints class statistics at end of run")                          \
                                                                            \
  notproduct(bool, PrintMethodStatistics, false,                            \
          "prints method statistics at end of run")                         \
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
          "Prints the generated interpreter code")                          \
                                                                            \
  product(bool, UseInterpreter, true,                                       \
          "Use interpreter for non-compiled methods")                       \
                                                                            \
  develop(bool, UseFastSignatureHandlers, true,                             \
          "Use fast signature handlers for native calls")                   \
                                                                            \
  develop(bool, UseV8InstrsOnly, false,                                     \
          "Use SPARC-V8 Compliant instruction subset")                      \
                                                                            \
  product(bool, UseNiagaraInstrs, false,                                    \
          "Use Niagara-efficient instruction subset")                       \
                                                                            \
  develop(bool, UseCASForSwap, false,                                       \
          "Do not use swap instructions, but only CAS (in a loop) on SPARC")\
                                                                            \
  product(bool, UseLoopCounter, true,                                       \
          "Increment invocation counter on backward branch")                \
                                                                            \
  product(bool, UseFastEmptyMethods, true,                                  \
          "Use fast method entry code for empty methods")                   \
                                                                            \
  product(bool, UseFastAccessorMethods, true,                               \
          "Use fast method entry code for accessor methods")                \
                                                                            \
  product_pd(bool, UseOnStackReplacement,                                   \
           "Use on stack replacement, calls runtime if invoc. counter "     \
           "overflows in loop")                                             \
                                                                            \
  notproduct(bool, TraceOnStackReplacement, false,                          \
          "Trace on stack replacement")                                     \
                                                                            \
  develop(bool, PoisonOSREntry, true,                                       \
           "Detect abnormal calls to OSR code")                             \
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
  notproduct(bool, TraceTypeProfile, false,                                 \
          "Trace type profile")                                             \
                                                                            \
  develop_pd(bool, InlineIntrinsics,                                        \
           "Inline intrinsics that can be statically resolved")             \
                                                                            \
  product_pd(bool, ProfileInterpreter,                                      \
           "Profile at the bytecode level during interpretation")           \
                                                                            \
  develop_pd(bool, ProfileTraps,                                            \
          "Profile deoptimization traps at the bytecode level")             \
                                                                            \
  product(intx, ProfileMaturityPercentage, 20,                              \
          "number of method invocations/branches (expressed as % of "       \
          "CompileThreshold) before using the method's profile")            \
                                                                            \
  develop(bool, PrintMethodData, false,                                     \
           "Print the results of +ProfileInterpreter at end of run")        \
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
          "use compilation")                                                \
                                                                            \
  develop(bool, TraceCompilationPolicy, false,                              \
          "Trace compilation policy")                                       \
                                                                            \
  develop(bool, TimeCompilationPolicy, false,                               \
          "Time the compilation policy")                                    \
                                                                            \
  product(bool, UseCounterDecay, true,                                      \
           "adjust recompilation counters")                                 \
                                                                            \
  develop(intx, CounterHalfLifeTime,    30,                                 \
          "half-life time of invocation counters (in secs)")                \
                                                                            \
  develop(intx, CounterDecayMinIntervalLength,   500,                       \
          "Min. ms. between invocation of CounterDecay")                    \
                                                                            \
  product(bool, AlwaysCompileLoopMethods, false,                            \
          "when using recompilation, never interpret methods "              \
          "containing loops")                                               \
                                                                            \
  product(bool, DontCompileHugeMethods, true,                               \
          "don't compile methods > HugeMethodLimit")                        \
                                                                            \
  /* Bytecode escape analysis estimation. */                                \
  product(bool, EstimateArgEscape, true,                                    \
          "Analyze bytecodes to estimate escape state of arguments")        \
                                                                            \
  product(intx, BCEATraceLevel, 0,                                          \
          "How much tracing to do of bytecode escape analysis estimates")   \
                                                                            \
  product(intx, MaxBCEAEstimateLevel, 5,                                    \
          "Maximum number of nested calls that are analyzed by BC EA.")     \
                                                                            \
  product(intx, MaxBCEAEstimateSize, 150,                                   \
          "Maximum bytecode size of a method to be analyzed by BC EA.")     \
                                                                            \
  product(intx,  AllocatePrefetchStyle, 1,                                  \
          "0 = no prefetch, "                                               \
          "1 = prefetch instructions for each allocation, "                 \
          "2 = use TLAB watermark to gate allocation prefetch")             \
                                                                            \
  product(intx,  AllocatePrefetchDistance, -1,                              \
          "Distance to prefetch ahead of allocation pointer")               \
                                                                            \
  product(intx,  AllocatePrefetchLines, 1,                                  \
          "Number of lines to prefetch ahead of allocation pointer")        \
                                                                            \
  product(intx,  AllocatePrefetchStepSize, 16,                              \
          "Step size in bytes of sequential prefetch instructions")         \
                                                                            \
  product(intx,  AllocatePrefetchInstr, 0,                                  \
          "Prefetch instruction to prefetch ahead of allocation pointer")   \
                                                                            \
  product(intx,  ReadPrefetchInstr, 0,                                      \
          "Prefetch instruction to prefetch ahead")                         \
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
          "Max. no. of lines in the stack trace for Java exceptions "       \
          "(0 means all)")                                                  \
                                                                            \
  develop(intx, GuaranteedSafepointInterval, 1000,                          \
          "Guarantee a safepoint (at least) every so many milliseconds "    \
          "(0 means none)")                                                 \
                                                                            \
  product(intx, SafepointTimeoutDelay, 10000,                               \
          "Delay in milliseconds for option SafepointTimeout")              \
                                                                            \
  product(intx, NmethodSweepFraction, 4,                                    \
          "Number of invocations of sweeper to cover all nmethods")         \
                                                                            \
  notproduct(intx, MemProfilingInterval, 500,                               \
          "Time between each invocation of the MemProfiler")                \
                                                                            \
  develop(intx, MallocCatchPtr, -1,                                         \
          "Hit breakpoint when mallocing/freeing this pointer")             \
                                                                            \
  notproduct(intx, AssertRepeat, 1,                                         \
          "number of times to evaluate expression in assert "               \
          "(to estimate overhead); only works with -DUSE_REPEATED_ASSERTS") \
                                                                            \
  notproduct(ccstrlist, SuppressErrorAt, "",                                \
          "List of assertions (file:line) to muzzle")                       \
                                                                            \
  notproduct(uintx, HandleAllocationLimit, 1024,                            \
          "Threshold for HandleMark allocation when +TraceHandleAllocation "\
          "is used")                                                        \
                                                                            \
  develop(uintx, TotalHandleAllocationLimit, 1024,                          \
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
  product_pd(intx, InlineSmallCode,                                         \
          "Only inline already compiled methods if their code size is "     \
          "less than this")                                                 \
                                                                            \
  product(intx, MaxInlineSize, 35,                                          \
          "maximum bytecode size of a method to be inlined")                \
                                                                            \
  product_pd(intx, FreqInlineSize,                                          \
          "maximum bytecode size of a frequent method to be inlined")       \
                                                                            \
  product(intx, MaxTrivialSize, 6,                                          \
          "maximum bytecode size of a trivial method to be inlined")        \
                                                                            \
  product(intx, MinInliningThreshold, 250,                                  \
          "min. invocation count a method needs to have to be inlined")     \
                                                                            \
  develop(intx, AlignEntryCode, 4,                                          \
          "aligns entry code to specified value (in bytes)")                \
                                                                            \
  develop(intx, MethodHistogramCutoff, 100,                                 \
          "cutoff value for method invoc. histogram (+CountCalls)")         \
                                                                            \
  develop(intx, ProfilerNumberOfInterpretedMethods, 25,                     \
          "# of interpreted methods to show in profile")                    \
                                                                            \
  develop(intx, ProfilerNumberOfCompiledMethods, 25,                        \
          "# of compiled methods to show in profile")                       \
                                                                            \
  develop(intx, ProfilerNumberOfStubMethods, 25,                            \
          "# of stub methods to show in profile")                           \
                                                                            \
  develop(intx, ProfilerNumberOfRuntimeStubNodes, 25,                       \
          "# of runtime stub nodes to show in profile")                     \
                                                                            \
  product(intx, ProfileIntervalsTicks, 100,                                 \
          "# of ticks between printing of interval profile "                \
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
          "ConvertSleepToYield is off (used for SOLARIS)")                  \
                                                                            \
  product(intx, EventLogLength,  2000,                                      \
          "maximum nof events in event log")                                \
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
  develop(bool, StressNonEntrant, false,                                    \
          "Mark nmethods non-entrant at registration")                      \
                                                                            \
  diagnostic(intx, MallocVerifyInterval,     0,                             \
          "if non-zero, verify C heap after every N calls to "              \
          "malloc/realloc/free")                                            \
                                                                            \
  diagnostic(intx, MallocVerifyStart,     0,                                \
          "if non-zero, start verifying C heap after Nth call to "          \
          "malloc/realloc/free")                                            \
                                                                            \
  product(intx, TypeProfileWidth,      2,                                   \
          "number of receiver types to record in call/cast profile")        \
                                                                            \
  develop(intx, BciProfileWidth,      2,                                    \
          "number of return bci's to record in ret profile")                \
                                                                            \
  product(intx, PerMethodRecompilationCutoff, 400,                          \
          "After recompiling N times, stay in the interpreter (-1=>'Inf')") \
                                                                            \
  product(intx, PerBytecodeRecompilationCutoff, 100,                        \
          "Per-BCI limit on repeated recompilation (-1=>'Inf')")            \
                                                                            \
  product(intx, PerMethodTrapLimit,  100,                                   \
          "Limit on traps (of one kind) in a method (includes inlines)")    \
                                                                            \
  product(intx, PerBytecodeTrapLimit,  4,                                   \
          "Limit on traps (of one kind) at a particular BCI")               \
                                                                            \
  develop(intx, FreqCountInvocations,  1,                                   \
          "Scaling factor for branch frequencies (deprecated)")             \
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
  product(intx, AliasLevel,     3,                                          \
          "0 for no aliasing, 1 for oop/field/static/array split, "         \
          "2 for class split, 3 for unique instances")                      \
                                                                            \
  develop(bool, VerifyAliases, false,                                       \
          "perform extra checks on the results of alias analysis")          \
                                                                            \
  develop(intx, ProfilerNodeSize,  1024,                                    \
          "Size in K to allocate for the Profile Nodes of each thread")     \
                                                                            \
  develop(intx, V8AtomicOperationUnderLockSpinCount,    50,                 \
          "Number of times to spin wait on a v8 atomic operation lock")     \
                                                                            \
  product(intx, ReadSpinIterations,   100,                                  \
          "Number of read attempts before a yield (spin inner loop)")       \
                                                                            \
  product_pd(intx, PreInflateSpin,                                          \
          "Number of times to spin wait before inflation")                  \
                                                                            \
  product(intx, PreBlockSpin,    10,                                        \
          "Number of times to spin in an inflated lock before going to "    \
          "an OS lock")                                                     \
                                                                            \
  /* gc parameters */                                                       \
  product(uintx, InitialHeapSize, 0,                                        \
          "Initial heap size (in bytes); zero means OldSize + NewSize")     \
                                                                            \
  product(uintx, MaxHeapSize, ScaleForWordSize(96*M),                       \
          "Maximum heap size (in bytes)")                                   \
                                                                            \
  product(uintx, OldSize, ScaleForWordSize(4*M),                            \
          "Initial tenured generation size (in bytes)")                     \
                                                                            \
  product(uintx, NewSize, ScaleForWordSize(4*M),                            \
          "Initial new generation size (in bytes)")                         \
                                                                            \
  product(uintx, MaxNewSize, max_uintx,                                     \
          "Maximum new generation size (in bytes), max_uintx means set "    \
          "ergonomically")                                                  \
                                                                            \
  product(uintx, PretenureSizeThreshold, 0,                                 \
          "Maximum size in bytes of objects allocated in DefNew "           \
          "generation; zero means no maximum")                              \
                                                                            \
  product(uintx, TLABSize, 0,                                               \
          "Starting TLAB size (in bytes); zero means set ergonomically")    \
                                                                            \
  product(uintx, MinTLABSize, 2*K,                                          \
          "Minimum allowed TLAB size (in bytes)")                           \
                                                                            \
  product(uintx, TLABAllocationWeight, 35,                                  \
          "Allocation averaging weight")                                    \
                                                                            \
  product(uintx, TLABWasteTargetPercent, 1,                                 \
          "Percentage of Eden that can be wasted")                          \
                                                                            \
  product(uintx, TLABRefillWasteFraction,    64,                            \
          "Max TLAB waste at a refill (internal fragmentation)")            \
                                                                            \
  product(uintx, TLABWasteIncrement,    4,                                  \
          "Increment allowed waste at slow allocation")                     \
                                                                            \
  product(intx, SurvivorRatio, 8,                                           \
          "Ratio of eden/survivor space size")                              \
                                                                            \
  product(intx, NewRatio, 2,                                                \
          "Ratio of new/old generation sizes")                              \
                                                                            \
  product(uintx, MaxLiveObjectEvacuationRatio, 100,                         \
          "Max percent of eden objects that will be live at scavenge")      \
                                                                            \
  product_pd(uintx, NewSizeThreadIncrease,                                  \
          "Additional size added to desired new generation size per "       \
          "non-daemon thread (in bytes)")                                   \
                                                                            \
  product_pd(uintx, PermSize,                                               \
          "Initial size of permanent generation (in bytes)")                \
                                                                            \
  product_pd(uintx, MaxPermSize,                                            \
          "Maximum size of permanent generation (in bytes)")                \
                                                                            \
  product(uintx, MinHeapFreeRatio,    40,                                   \
          "Min percentage of heap free after GC to avoid expansion")        \
                                                                            \
  product(uintx, MaxHeapFreeRatio,    70,                                   \
          "Max percentage of heap free after GC to avoid shrinking")        \
                                                                            \
  product(intx, SoftRefLRUPolicyMSPerMB, 1000,                              \
          "Number of milliseconds per MB of free space in the heap")        \
                                                                            \
  product(uintx, MinHeapDeltaBytes, ScaleForWordSize(128*K),                \
          "Min change in heap space due to GC (in bytes)")                  \
                                                                            \
  product(uintx, MinPermHeapExpansion, ScaleForWordSize(256*K),             \
          "Min expansion of permanent heap (in bytes)")                     \
                                                                            \
  product(uintx, MaxPermHeapExpansion, ScaleForWordSize(4*M),               \
          "Max expansion of permanent heap without full GC (in bytes)")     \
                                                                            \
  product(intx, QueuedAllocationWarningCount, 0,                            \
          "Number of times an allocation that queues behind a GC "          \
          "will retry before printing a warning")                           \
                                                                            \
  diagnostic(uintx, VerifyGCStartAt,   0,                                   \
          "GC invoke count where +VerifyBefore/AfterGC kicks in")           \
                                                                            \
  diagnostic(intx, VerifyGCLevel,     0,                                    \
          "Generation level at which to start +VerifyBefore/AfterGC")       \
                                                                            \
  develop(uintx, ExitAfterGCNum,   0,                                       \
          "If non-zero, exit after this GC.")                               \
                                                                            \
  product(intx, MaxTenuringThreshold,    15,                                \
          "Maximum value for tenuring threshold")                           \
                                                                            \
  product(intx, InitialTenuringThreshold,     7,                            \
          "Initial value for tenuring threshold")                           \
                                                                            \
  product(intx, TargetSurvivorRatio,    50,                                 \
          "Desired percentage of survivor space used after scavenge")       \
                                                                            \
  product(uintx, MarkSweepDeadRatio,     5,                                 \
          "Percentage (0-100) of the old gen allowed as dead wood."         \
          "Serial mark sweep treats this as both the min and max value."    \
          "CMS uses this value only if it falls back to mark sweep."        \
          "Par compact uses a variable scale based on the density of the"   \
          "generation and treats this as the max value when the heap is"    \
          "either completely full or completely empty.  Par compact also"   \
          "has a smaller default value; see arguments.cpp.")                \
                                                                            \
  product(uintx, PermMarkSweepDeadRatio,    20,                             \
          "Percentage (0-100) of the perm gen allowed as dead wood."        \
          "See MarkSweepDeadRatio for collector-specific comments.")        \
                                                                            \
  product(intx, MarkSweepAlwaysCompactCount,     4,                         \
          "How often should we fully compact the heap (ignoring the dead "  \
          "space parameters)")                                              \
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
          "Delay in ms between expansion and allocation")                   \
                                                                            \
  product(intx, DeferThrSuspendLoopCount,     4000,                         \
          "(Unstable) Number of times to iterate in safepoint loop "        \
          " before blocking VM threads ")                                   \
                                                                            \
  product(intx, DeferPollingPageLoopCount,     -1,                          \
          "(Unsafe,Unstable) Number of iterations in safepoint loop "       \
          "before changing safepoint polling page to RO ")                  \
                                                                            \
  product(intx, SafepointSpinBeforeYield, 2000,  "(Unstable)")              \
                                                                            \
  product(bool, UseDepthFirstScavengeOrder, true,                           \
          "true: the scavenge order will be depth-first, "                  \
          "false: the scavenge order will be breadth-first")                \
                                                                            \
  product(bool, PSChunkLargeArrays, true,                                   \
          "true: process large arrays in chunks")                           \
                                                                            \
  product(uintx, GCDrainStackTargetSize, 64,                                \
          "how many entries we'll try to leave on the stack during "        \
          "parallel GC")                                                    \
                                                                            \
  /* stack parameters */                                                    \
  product_pd(intx, StackYellowPages,                                        \
          "Number of yellow zone (recoverable overflows) pages")            \
                                                                            \
  product_pd(intx, StackRedPages,                                           \
          "Number of red zone (unrecoverable overflows) pages")             \
                                                                            \
  product_pd(intx, StackShadowPages,                                        \
          "Number of shadow zone (for overflow checking) pages"             \
          " this should exceed the depth of the VM and native call stack")  \
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
  develop_pd(uintx, JVMInvokeMethodSlack,                                   \
          "Stack space (bytes) required for JVM_InvokeMethod to complete")  \
                                                                            \
  product(uintx, ThreadSafetyMargin, 50*M,                                  \
          "Thread safety margin is used on fixed-stack LinuxThreads (on "   \
          "Linux/x86 only) to prevent heap-stack collision. Set to 0 to "   \
          "disable this feature")                                           \
                                                                            \
  /* code cache parameters */                                               \
  develop(uintx, CodeCacheSegmentSize, 64,                                  \
          "Code cache segment size (in bytes) - smallest unit of "          \
          "allocation")                                                     \
                                                                            \
  develop_pd(intx, CodeEntryAlignment,                                      \
          "Code entry alignment for generated code (in bytes)")             \
                                                                            \
  product_pd(uintx, InitialCodeCacheSize,                                   \
          "Initial code cache size (in bytes)")                             \
                                                                            \
  product_pd(uintx, ReservedCodeCacheSize,                                  \
          "Reserved code cache size (in bytes) - maximum code cache size")  \
                                                                            \
  product(uintx, CodeCacheMinimumFreeSpace, 500*K,                          \
          "When less than X space left, we stop compiling.")                \
                                                                            \
  product_pd(uintx, CodeCacheExpansionSize,                                 \
          "Code cache expansion size (in bytes)")                           \
                                                                            \
  develop_pd(uintx, CodeCacheMinBlockLength,                                \
          "Minimum number of segments in a code cache block.")              \
                                                                            \
  notproduct(bool, ExitOnFullCodeCache, false,                              \
          "Exit the VM if we fill the code cache.")                         \
                                                                            \
  /* interpreter debugging */                                               \
  develop(intx, BinarySwitchThreshold, 5,                                   \
          "Minimal number of lookupswitch entries for rewriting to binary " \
          "switch")                                                         \
                                                                            \
  develop(intx, StopInterpreterAt, 0,                                       \
          "Stops interpreter execution at specified bytecode number")       \
                                                                            \
  develop(intx, TraceBytecodesAt, 0,                                        \
          "Traces bytecodes starting with specified bytecode number")       \
                                                                            \
  /* compiler interface */                                                  \
  develop(intx, CIStart, 0,                                                 \
          "the id of the first compilation to permit")                      \
                                                                            \
  develop(intx, CIStop,    -1,                                              \
          "the id of the last compilation to permit")                       \
                                                                            \
  develop(intx, CIStartOSR,     0,                                          \
          "the id of the first osr compilation to permit "                  \
          "(CICountOSR must be on)")                                        \
                                                                            \
  develop(intx, CIStopOSR,    -1,                                           \
          "the id of the last osr compilation to permit "                   \
          "(CICountOSR must be on)")                                        \
                                                                            \
  develop(intx, CIBreakAtOSR,    -1,                                        \
          "id of osr compilation to break at")                              \
                                                                            \
  develop(intx, CIBreakAt,    -1,                                           \
          "id of compilation to break at")                                  \
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
  product(bool, CICompilerCountPerCPU, false,                               \
          "1 compiler thread for log(N CPUs)")                              \
                                                                            \
  develop(intx, CIFireOOMAt,    -1,                                         \
          "Fire OutOfMemoryErrors throughout CI for testing the compiler "  \
          "(non-negative value throws OOM after this many CI accesses "     \
          "in each compile)")                                               \
                                                                            \
  develop(intx, CIFireOOMAtDelay, -1,                                       \
          "Wait for this many CI accesses to occur in all compiles before " \
          "beginning to throw OutOfMemoryErrors in each compile")           \
                                                                            \
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
          "    to normal native priority. Java priorities below NORM_PRIORITY"\
          "    map to lower native priority values. On Windows applications"\
          "    are allowed to use higher native priorities. However, with  "\
          "    ThreadPriorityPolicy=0, VM will not use the highest possible"\
          "    native priority, THREAD_PRIORITY_TIME_CRITICAL, as it may   "\
          "    interfere with system threads. On Linux thread priorities   "\
          "    are ignored because the OS does not support static priority "\
          "    in SCHED_OTHER scheduling class which is the only choice for"\
          "    non-root, non-realtime applications.                        "\
          "1 : Aggressive.                                                 "\
          "    Java thread priorities map over to the entire range of      "\
          "    native thread priorities. Higher Java thread priorities map "\
          "    to higher native thread priorities. This policy should be   "\
          "    used with care, as sometimes it can cause performance       "\
          "    degradation in the application and/or the entire system. On "\
          "    Linux this policy requires root privilege.")                 \
                                                                            \
  product(bool, ThreadPriorityVerbose, false,                               \
          "print priority changes")                                         \
                                                                            \
  product(intx, DefaultThreadPriority, -1,                                  \
          "what native priority threads run at if not specified elsewhere (-1 means no change)") \
                                                                            \
  product(intx, CompilerThreadPriority, -1,                                 \
          "what priority should compiler threads run at (-1 means no change)") \
                                                                            \
  product(intx, VMThreadPriority, -1,                                       \
          "what priority should VM threads run at (-1 means no change)")    \
                                                                            \
  product(bool, CompilerThreadHintNoPreempt, true,                          \
          "(Solaris only) Give compiler threads an extra quanta")           \
                                                                            \
  product(bool, VMThreadHintNoPreempt, false,                               \
          "(Solaris only) Give VM thread an extra quanta")                  \
                                                                            \
  product(intx, JavaPriority1_To_OSPriority, -1, "Map Java priorities to OS priorities") \
  product(intx, JavaPriority2_To_OSPriority, -1, "Map Java priorities to OS priorities") \
  product(intx, JavaPriority3_To_OSPriority, -1, "Map Java priorities to OS priorities") \
  product(intx, JavaPriority4_To_OSPriority, -1, "Map Java priorities to OS priorities") \
  product(intx, JavaPriority5_To_OSPriority, -1, "Map Java priorities to OS priorities") \
  product(intx, JavaPriority6_To_OSPriority, -1, "Map Java priorities to OS priorities") \
  product(intx, JavaPriority7_To_OSPriority, -1, "Map Java priorities to OS priorities") \
  product(intx, JavaPriority8_To_OSPriority, -1, "Map Java priorities to OS priorities") \
  product(intx, JavaPriority9_To_OSPriority, -1, "Map Java priorities to OS priorities") \
  product(intx, JavaPriority10_To_OSPriority,-1, "Map Java priorities to OS priorities") \
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
  product(intx, StarvationMonitorInterval,    200,                          \
          "Pause between each check in ms")                                 \
                                                                            \
  /* recompilation */                                                       \
  product_pd(intx, CompileThreshold,                                        \
          "number of interpreted method invocations before (re-)compiling") \
                                                                            \
  product_pd(intx, BackEdgeThreshold,                                       \
          "Interpreter Back edge threshold at which an OSR compilation is invoked")\
                                                                            \
  product(intx, Tier1BytecodeLimit,      10,                                \
          "Must have at least this many bytecodes before tier1"             \
          "invocation counters are used")                                   \
                                                                            \
  product_pd(intx, Tier2CompileThreshold,                                   \
          "threshold at which a tier 2 compilation is invoked")             \
                                                                            \
  product_pd(intx, Tier2BackEdgeThreshold,                                  \
          "Back edge threshold at which a tier 2 compilation is invoked")   \
                                                                            \
  product_pd(intx, Tier3CompileThreshold,                                   \
          "threshold at which a tier 3 compilation is invoked")             \
                                                                            \
  product_pd(intx, Tier3BackEdgeThreshold,                                  \
          "Back edge threshold at which a tier 3 compilation is invoked")   \
                                                                            \
  product_pd(intx, Tier4CompileThreshold,                                   \
          "threshold at which a tier 4 compilation is invoked")             \
                                                                            \
  product_pd(intx, Tier4BackEdgeThreshold,                                  \
          "Back edge threshold at which a tier 4 compilation is invoked")   \
                                                                            \
  product_pd(bool, TieredCompilation,                                       \
          "Enable two-tier compilation")                                    \
                                                                            \
  product(bool, StressTieredRuntime, false,                                 \
          "Alternate client and server compiler on compile requests")       \
                                                                            \
  product_pd(intx, OnStackReplacePercentage,                                \
          "NON_TIERED number of method invocations/branches (expressed as %"\
          "of CompileThreshold) before (re-)compiling OSR code")            \
                                                                            \
  product(intx, InterpreterProfilePercentage, 33,                           \
          "NON_TIERED number of method invocations/branches (expressed as %"\
          "of CompileThreshold) before profiling in the interpreter")       \
                                                                            \
  develop(intx, MaxRecompilationSearchLength,    10,                        \
          "max. # frames to inspect searching for recompilee")              \
                                                                            \
  develop(intx, MaxInterpretedSearchLength,     3,                          \
          "max. # interp. frames to skip when searching for recompilee")    \
                                                                            \
  develop(intx, DesiredMethodLimit,  8000,                                  \
          "desired max. method size (in bytecodes) after inlining")         \
                                                                            \
  develop(intx, HugeMethodLimit,  8000,                                     \
          "don't compile methods larger than this if "                      \
          "+DontCompileHugeMethods")                                        \
                                                                            \
  /* New JDK 1.4 reflection implementation */                               \
                                                                            \
  develop(bool, UseNewReflection, true,                                     \
          "Temporary flag for transition to reflection based on dynamic "   \
          "bytecode generation in 1.4; can no longer be turned off in 1.4 " \
          "JDK, and is unneeded in 1.3 JDK, but marks most places VM "      \
          "changes were needed")                                            \
                                                                            \
  develop(bool, VerifyReflectionBytecodes, false,                           \
          "Force verification of 1.4 reflection bytecodes. Does not work "  \
          "in situations like that described in 4486457 or for "            \
          "constructors generated for serialization, so can not be enabled "\
          "in product.")                                                    \
                                                                            \
  product(bool, ReflectionWrapResolutionErrors, true,                       \
          "Temporary flag for transition to AbstractMethodError wrapped "   \
          "in InvocationTargetException. See 6531596")                      \
                                                                            \
                                                                            \
  develop(intx, FastSuperclassLimit, 8,                                     \
          "Depth of hardwired instanceof accelerator array")                \
                                                                            \
  /* Properties for Java libraries  */                                      \
                                                                            \
  product(intx, MaxDirectMemorySize, -1,                                    \
          "Maximum total size of NIO direct-buffer allocations")            \
                                                                            \
  /* temporary developer defined flags  */                                  \
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
  product(bool, UsePerfData, true,                                          \
          "Flag to disable jvmstat instrumentation for performance testing" \
          "and problem isolation purposes.")                                \
                                                                            \
  product(bool, PerfDataSaveToFile, false,                                  \
          "Save PerfData memory to hsperfdata_<pid> file on exit")          \
                                                                            \
  product(ccstr, PerfDataSaveFile, NULL,                                    \
          "Save PerfData memory to the specified absolute pathname,"        \
           "%p in the file name if present will be replaced by pid")        \
                                                                            \
  product(intx, PerfDataSamplingInterval, 50 /*ms*/,                        \
          "Data sampling interval in milliseconds")                         \
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
  product(intx, PerfDataMemorySize, 32*K,                                   \
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
          "Unguard page and retry on no-execute fault (Win32 only)"         \
          "0=off, 1=conservative, 2=aggressive")                            \
                                                                            \
  /* Serviceability Support */                                              \
                                                                            \
  product(bool, ManagementServer, false,                                    \
          "Create JMX Management Server")                                   \
                                                                            \
  product(bool, DisableAttachMechanism, false,                              \
         "Disable mechanism that allows tools to attach to this VM")        \
                                                                            \
  product(bool, StartAttachListener, false,                                 \
          "Always start Attach Listener at VM startup")                     \
                                                                            \
  manageable(bool, PrintConcurrentLocks, false,                             \
          "Print java.util.concurrent locks in thread dump")                \
                                                                            \
  /* Shared spaces */                                                       \
                                                                            \
  product(bool, UseSharedSpaces, true,                                      \
          "Use shared spaces in the permanent generation")                  \
                                                                            \
  product(bool, RequireSharedSpaces, false,                                 \
          "Require shared spaces in the permanent generation")              \
                                                                            \
  product(bool, ForceSharedSpaces, false,                                   \
          "Require shared spaces in the permanent generation")              \
                                                                            \
  product(bool, DumpSharedSpaces, false,                                    \
           "Special mode: JVM reads a class list, loads classes, builds "   \
            "shared spaces, and dumps the shared spaces to a file to be "   \
            "used in future JVM runs.")                                     \
                                                                            \
  product(bool, PrintSharedSpaces, false,                                   \
          "Print usage of shared spaces")                                   \
                                                                            \
  product(uintx, SharedDummyBlockSize, 512*M,                               \
          "Size of dummy block used to shift heap addresses (in bytes)")    \
                                                                            \
  product(uintx, SharedReadWriteSize,  12*M,                                \
          "Size of read-write space in permanent generation (in bytes)")    \
                                                                            \
  product(uintx, SharedReadOnlySize,   10*M,                                \
          "Size of read-only space in permanent generation (in bytes)")     \
                                                                            \
  product(uintx, SharedMiscDataSize,    4*M,                                \
          "Size of the shared data area adjacent to the heap (in bytes)")   \
                                                                            \
  product(uintx, SharedMiscCodeSize,    4*M,                                \
          "Size of the shared code area adjacent to the heap (in bytes)")   \
                                                                            \
  diagnostic(bool, SharedOptimizeColdStart, true,                           \
          "At dump time, order shared objects to achieve better "           \
          "cold startup time.")                                             \
                                                                            \
  develop(intx, SharedOptimizeColdStartPolicy, 2,                           \
          "Reordering policy for SharedOptimizeColdStart "                  \
          "0=favor classload-time locality, 1=balanced, "                   \
          "2=favor runtime locality")                                       \
                                                                            \
  diagnostic(bool, SharedSkipVerify, false,                                 \
          "Skip assert() and verify() which page-in unwanted shared "       \
          "objects. ")                                                      \
                                                                            \
  product(bool, AnonymousClasses, false,                                    \
          "support sun.misc.Unsafe.defineAnonymousClass")                   \
                                                                            \
  experimental(bool, EnableMethodHandles, false,                            \
          "support method handles (true by default under JSR 292)")         \
                                                                            \
  diagnostic(intx, MethodHandlePushLimit, 3,                                \
          "number of additional stack slots a method handle may push")      \
                                                                            \
  develop(bool, TraceMethodHandles, false,                                  \
          "trace internal method handle operations")                        \
                                                                            \
  diagnostic(bool, VerifyMethodHandles, trueInDebug,                        \
          "perform extra checks when constructing method handles")          \
                                                                            \
  diagnostic(bool, OptimizeMethodHandles, true,                             \
          "when constructing method handles, try to improve them")          \
                                                                            \
  experimental(bool, EnableInvokeDynamic, false,                            \
          "recognize the invokedynamic instruction")                        \
                                                                            \
  develop(bool, TraceInvokeDynamic, false,                                  \
          "trace internal invoke dynamic operations")                       \
                                                                            \
  product(bool, TaggedStackInterpreter, false,                              \
          "Insert tags in interpreter execution stack for oopmap generaion")\
                                                                            \
  diagnostic(bool, PauseAtStartup,      false,                              \
          "Causes the VM to pause at startup time and wait for the pause "  \
          "file to be removed (default: ./vm.paused.<pid>)")                \
                                                                            \
  diagnostic(ccstr, PauseAtStartupFile, NULL,                               \
          "The file to create and for whose removal to await when pausing " \
          "at startup. (default: ./vm.paused.<pid>)")                       \
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
  diagnostic(bool, PrintDTraceDOF, false,                                   \
             "Print the DTrace DOF passed to the system for JSDT probes")   \
                                                                            \
  product(bool, UseVMInterruptibleIO, false,                                \
          "(Unstable, Solaris-specific) Thread interrupt before or with "   \
          "EINTR for I/O operations results in OS_INTRPT. The default value"\
          " of this flag is true for JDK 6 and earliers")


/*
 *  Macros for factoring of globals
 */

// Interface macros
#define DECLARE_PRODUCT_FLAG(type, name, value, doc)    extern "C" type name;
#define DECLARE_PD_PRODUCT_FLAG(type, name, doc)        extern "C" type name;
#define DECLARE_DIAGNOSTIC_FLAG(type, name, value, doc) extern "C" type name;
#define DECLARE_EXPERIMENTAL_FLAG(type, name, value, doc) extern "C" type name;
#define DECLARE_MANAGEABLE_FLAG(type, name, value, doc) extern "C" type name;
#define DECLARE_PRODUCT_RW_FLAG(type, name, value, doc) extern "C" type name;
#ifdef PRODUCT
#define DECLARE_DEVELOPER_FLAG(type, name, value, doc)  const type name = value;
#define DECLARE_PD_DEVELOPER_FLAG(type, name, doc)      const type name = pd_##name;
#define DECLARE_NOTPRODUCT_FLAG(type, name, value, doc)
#else
#define DECLARE_DEVELOPER_FLAG(type, name, value, doc)  extern "C" type name;
#define DECLARE_PD_DEVELOPER_FLAG(type, name, doc)      extern "C" type name;
#define DECLARE_NOTPRODUCT_FLAG(type, name, value, doc)  extern "C" type name;
#endif
// Special LP64 flags, product only needed for now.
#ifdef _LP64
#define DECLARE_LP64_PRODUCT_FLAG(type, name, value, doc) extern "C" type name;
#else
#define DECLARE_LP64_PRODUCT_FLAG(type, name, value, doc) const type name = value;
#endif // _LP64

// Implementation macros
#define MATERIALIZE_PRODUCT_FLAG(type, name, value, doc)   type name = value;
#define MATERIALIZE_PD_PRODUCT_FLAG(type, name, doc)       type name = pd_##name;
#define MATERIALIZE_DIAGNOSTIC_FLAG(type, name, value, doc) type name = value;
#define MATERIALIZE_EXPERIMENTAL_FLAG(type, name, value, doc) type name = value;
#define MATERIALIZE_MANAGEABLE_FLAG(type, name, value, doc) type name = value;
#define MATERIALIZE_PRODUCT_RW_FLAG(type, name, value, doc) type name = value;
#ifdef PRODUCT
#define MATERIALIZE_DEVELOPER_FLAG(type, name, value, doc) /* flag name is constant */
#define MATERIALIZE_PD_DEVELOPER_FLAG(type, name, doc)     /* flag name is constant */
#define MATERIALIZE_NOTPRODUCT_FLAG(type, name, value, doc)
#else
#define MATERIALIZE_DEVELOPER_FLAG(type, name, value, doc) type name = value;
#define MATERIALIZE_PD_DEVELOPER_FLAG(type, name, doc)     type name = pd_##name;
#define MATERIALIZE_NOTPRODUCT_FLAG(type, name, value, doc) type name = value;
#endif
#ifdef _LP64
#define MATERIALIZE_LP64_PRODUCT_FLAG(type, name, value, doc)   type name = value;
#else
#define MATERIALIZE_LP64_PRODUCT_FLAG(type, name, value, doc) /* flag is constant */
#endif // _LP64

RUNTIME_FLAGS(DECLARE_DEVELOPER_FLAG, DECLARE_PD_DEVELOPER_FLAG, DECLARE_PRODUCT_FLAG, DECLARE_PD_PRODUCT_FLAG, DECLARE_DIAGNOSTIC_FLAG, DECLARE_EXPERIMENTAL_FLAG, DECLARE_NOTPRODUCT_FLAG, DECLARE_MANAGEABLE_FLAG, DECLARE_PRODUCT_RW_FLAG, DECLARE_LP64_PRODUCT_FLAG)

RUNTIME_OS_FLAGS(DECLARE_DEVELOPER_FLAG, DECLARE_PD_DEVELOPER_FLAG, DECLARE_PRODUCT_FLAG, DECLARE_PD_PRODUCT_FLAG, DECLARE_DIAGNOSTIC_FLAG, DECLARE_NOTPRODUCT_FLAG)
