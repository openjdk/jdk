/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_VM_VERSION_HPP
#define SHARE_VM_RUNTIME_VM_VERSION_HPP

#include "memory/allocation.hpp"
#include "utilities/ostream.hpp"

// VM_Version provides information about the VM.

class Abstract_VM_Version: AllStatic {
  friend class VMStructs;
  friend class JVMCIVMStructs;

 protected:
  static const char*  _s_vm_release;
  static const char*  _s_internal_vm_info_string;

  // CPU feature flags.
  static uint64_t _features;
  static const char* _features_string;

  // These are set by machine-dependent initializations
  static bool         _supports_cx8;
  static bool         _supports_atomic_getset4;
  static bool         _supports_atomic_getset8;
  static bool         _supports_atomic_getadd4;
  static bool         _supports_atomic_getadd8;
  static unsigned int _logical_processors_per_package;
  static unsigned int _L1_data_cache_line_size;
  static int          _vm_major_version;
  static int          _vm_minor_version;
  static int          _vm_security_version;
  static int          _vm_patch_version;
  static int          _vm_build_number;
  static unsigned int _parallel_worker_threads;
  static bool         _parallel_worker_threads_initialized;
  static int          _reserve_for_allocation_prefetch;

  static unsigned int nof_parallel_worker_threads(unsigned int num,
                                                  unsigned int dem,
                                                  unsigned int switch_pt);
 public:
  // Called as part of the runtime services initialization which is
  // called from the management module initialization (via init_globals())
  // after argument parsing and attaching of the main thread has
  // occurred.  Examines a variety of the hardware capabilities of
  // the platform to determine which features can be used to execute the
  // program.
  static void initialize();

  // This allows for early initialization of VM_Version information
  // that may be needed later in the initialization sequence but before
  // full VM_Version initialization is possible. It can not depend on any
  // other part of the VM being initialized when called. Platforms that
  // need to specialize this define VM_Version::early_initialize().
  static void early_initialize() { }

  // Called to initialize VM variables needing initialization
  // after command line parsing. Platforms that need to specialize
  // this should define VM_Version::init_before_ergo().
  static void init_before_ergo() {}

  // Name
  static const char* vm_name();
  // Vendor
  static const char* vm_vendor();
  // VM version information string printed by launcher (java -version)
  static const char* vm_info_string();
  static const char* vm_release();
  static const char* vm_platform_string();
  static const char* vm_build_user();

  static int vm_major_version()               { return _vm_major_version; }
  static int vm_minor_version()               { return _vm_minor_version; }
  static int vm_security_version()            { return _vm_security_version; }
  static int vm_patch_version()               { return _vm_patch_version; }
  static int vm_build_number()                { return _vm_build_number; }

  // Gets the jvm_version_info.jvm_version defined in jvm.h
  static unsigned int jvm_version();

  // Internal version providing additional build information
  static const char* internal_vm_info_string();
  static const char* jre_release_version();
  static const char* jdk_debug_level();
  static const char* printable_jdk_debug_level();

  static uint64_t features() {
    return _features;
  }

  static const char* features_string() {
    return _features_string;
  }

  // does HW support an 8-byte compare-exchange operation?
  static bool supports_cx8()  {
#ifdef SUPPORTS_NATIVE_CX8
    return true;
#else
    return _supports_cx8;
#endif
  }
  // does HW support atomic get-and-set or atomic get-and-add?  Used
  // to guide intrinsification decisions for Unsafe atomic ops
  static bool supports_atomic_getset4()  {return _supports_atomic_getset4;}
  static bool supports_atomic_getset8()  {return _supports_atomic_getset8;}
  static bool supports_atomic_getadd4()  {return _supports_atomic_getadd4;}
  static bool supports_atomic_getadd8()  {return _supports_atomic_getadd8;}

  static unsigned int logical_processors_per_package() {
    return _logical_processors_per_package;
  }

  static unsigned int L1_data_cache_line_size() {
    return _L1_data_cache_line_size;
  }

  // Need a space at the end of TLAB for prefetch instructions
  // which may fault when accessing memory outside of heap.
  static int reserve_for_allocation_prefetch() {
    return _reserve_for_allocation_prefetch;
  }

  // ARCH specific policy for the BiasedLocking
  static bool use_biased_locking()  { return true; }

  // Number of page sizes efficiently supported by the hardware.  Most chips now
  // support two sizes, thus this default implementation.  Processor-specific
  // subclasses should define new versions to hide this one as needed.  Note
  // that the O/S may support more sizes, but at most this many are used.
  static uint page_size_count() { return 2; }

  // Returns the number of parallel threads to be used for VM
  // work.  If that number has not been calculated, do so and
  // save it.  Returns ParallelGCThreads if it is set on the
  // command line.
  static unsigned int parallel_worker_threads();
  // Calculates and returns the number of parallel threads.  May
  // be VM version specific.
  static unsigned int calc_parallel_worker_threads();
};

#ifdef TARGET_ARCH_x86
# include "vm_version_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "vm_version_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "vm_version_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "vm_version_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "vm_version_ppc.hpp"
#endif

#endif // SHARE_VM_RUNTIME_VM_VERSION_HPP
