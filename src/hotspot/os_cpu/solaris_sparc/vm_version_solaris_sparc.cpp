/*
 * Copyright (c) 2006, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/os.hpp"
#include "vm_version_sparc.hpp"

#include <sys/auxv.h>
#include <sys/systeminfo.h>
#include <picl.h>
#include <dlfcn.h>
#include <link.h>

extern "C" static int PICL_visit_cpu_helper(picl_nodehdl_t nodeh, void *result);

// Functions from the library we need (signatures should match those in picl.h)
extern "C" {
  typedef int (*picl_initialize_func_t)(void);
  typedef int (*picl_shutdown_func_t)(void);
  typedef int (*picl_get_root_func_t)(picl_nodehdl_t *nodehandle);
  typedef int (*picl_walk_tree_by_class_func_t)(picl_nodehdl_t rooth,
      const char *classname, void *c_args,
      int (*callback_fn)(picl_nodehdl_t hdl, void *args));
  typedef int (*picl_get_prop_by_name_func_t)(picl_nodehdl_t nodeh, const char *nm,
      picl_prophdl_t *ph);
  typedef int (*picl_get_propval_func_t)(picl_prophdl_t proph, void *valbuf, size_t sz);
  typedef int (*picl_get_propinfo_func_t)(picl_prophdl_t proph, picl_propinfo_t *pi);
}

class PICL {
  // Pointers to functions in the library
  picl_initialize_func_t _picl_initialize;
  picl_shutdown_func_t _picl_shutdown;
  picl_get_root_func_t _picl_get_root;
  picl_walk_tree_by_class_func_t _picl_walk_tree_by_class;
  picl_get_prop_by_name_func_t _picl_get_prop_by_name;
  picl_get_propval_func_t _picl_get_propval;
  picl_get_propinfo_func_t _picl_get_propinfo;
  // Handle to the library that is returned by dlopen
  void *_dl_handle;

  bool open_library();
  void close_library();

  template<typename FuncType> bool bind(FuncType& func, const char* name);
  bool bind_library_functions();

  // Get a value of the integer property. The value in the tree can be either 32 or 64 bit
  // depending on the platform. The result is converted to int.
  int get_int_property(picl_nodehdl_t nodeh, const char* name, int* result) {
    picl_propinfo_t pinfo;
    picl_prophdl_t proph;
    if (_picl_get_prop_by_name(nodeh, name, &proph) != PICL_SUCCESS ||
        _picl_get_propinfo(proph, &pinfo) != PICL_SUCCESS) {
      return PICL_FAILURE;
    }

    if (pinfo.type != PICL_PTYPE_INT && pinfo.type != PICL_PTYPE_UNSIGNED_INT) {
      assert(false, "Invalid property type");
      return PICL_FAILURE;
    }
    if (pinfo.size == sizeof(int64_t)) {
      int64_t val;
      if (_picl_get_propval(proph, &val, sizeof(int64_t)) != PICL_SUCCESS) {
        return PICL_FAILURE;
      }
      *result = static_cast<int>(val);
    } else if (pinfo.size == sizeof(int32_t)) {
      int32_t val;
      if (_picl_get_propval(proph, &val, sizeof(int32_t)) != PICL_SUCCESS) {
        return PICL_FAILURE;
      }
      *result = static_cast<int>(val);
    } else {
      assert(false, "Unexpected integer property size");
      return PICL_FAILURE;
    }
    return PICL_SUCCESS;
  }

  // Visitor and a state machine that visits integer properties and verifies that the
  // values are the same. Stores the unique value observed.
  class UniqueValueVisitor {
    PICL *_picl;
    enum {
      INITIAL,        // Start state, no assignments happened
      ASSIGNED,       // Assigned a value
      INCONSISTENT    // Inconsistent value seen
    } _state;
    int _value;
  public:
    UniqueValueVisitor(PICL* picl) : _picl(picl), _state(INITIAL) { }
    int value() {
      assert(_state == ASSIGNED, "Precondition");
      return _value;
    }
    void set_value(int value) {
      assert(_state == INITIAL, "Precondition");
      _value = value;
      _state = ASSIGNED;
    }
    bool is_initial()       { return _state == INITIAL;      }
    bool is_assigned()      { return _state == ASSIGNED;     }
    bool is_inconsistent()  { return _state == INCONSISTENT; }
    void set_inconsistent() { _state = INCONSISTENT;         }

    bool visit(picl_nodehdl_t nodeh, const char* name) {
      assert(!is_inconsistent(), "Precondition");
      int curr;
      if (_picl->get_int_property(nodeh, name, &curr) == PICL_SUCCESS) {
        if (!is_assigned()) { // first iteration
          set_value(curr);
        } else if (curr != value()) { // following iterations
          set_inconsistent();
        }
        return true;
      }
      return false;
    }
  };

  class CPUVisitor {
    UniqueValueVisitor _l1_visitor;
    UniqueValueVisitor _l2_visitor;
    int _limit; // number of times visit() can be run
  public:
    CPUVisitor(PICL *picl, int limit) : _l1_visitor(picl), _l2_visitor(picl), _limit(limit) {}
    static int visit(picl_nodehdl_t nodeh, void *arg) {
      CPUVisitor *cpu_visitor = static_cast<CPUVisitor*>(arg);
      UniqueValueVisitor* l1_visitor = cpu_visitor->l1_visitor();
      UniqueValueVisitor* l2_visitor = cpu_visitor->l2_visitor();
      if (!l1_visitor->is_inconsistent()) {
        l1_visitor->visit(nodeh, "l1-dcache-line-size");
      }
      static const char* l2_data_cache_line_property_name = NULL;
      // On the first visit determine the name of the l2 cache line size property and memoize it.
      if (l2_data_cache_line_property_name == NULL) {
        assert(!l2_visitor->is_inconsistent(), "First iteration cannot be inconsistent");
        l2_data_cache_line_property_name = "l2-cache-line-size";
        if (!l2_visitor->visit(nodeh, l2_data_cache_line_property_name)) {
          l2_data_cache_line_property_name = "l2-dcache-line-size";
          l2_visitor->visit(nodeh, l2_data_cache_line_property_name);
        }
      } else {
        if (!l2_visitor->is_inconsistent()) {
          l2_visitor->visit(nodeh, l2_data_cache_line_property_name);
        }
      }

      if (l1_visitor->is_inconsistent() && l2_visitor->is_inconsistent()) {
        return PICL_WALK_TERMINATE;
      }
      cpu_visitor->_limit--;
      if (cpu_visitor->_limit <= 0) {
        return PICL_WALK_TERMINATE;
      }
      return PICL_WALK_CONTINUE;
    }
    UniqueValueVisitor* l1_visitor() { return &_l1_visitor; }
    UniqueValueVisitor* l2_visitor() { return &_l2_visitor; }
  };
  int _L1_data_cache_line_size;
  int _L2_data_cache_line_size;
public:
  static int visit_cpu(picl_nodehdl_t nodeh, void *state) {
    return CPUVisitor::visit(nodeh, state);
  }

  PICL(bool is_fujitsu, bool is_sun4v) : _L1_data_cache_line_size(0), _L2_data_cache_line_size(0), _dl_handle(NULL) {
    if (!open_library()) {
      return;
    }
    if (_picl_initialize() == PICL_SUCCESS) {
      picl_nodehdl_t rooth;
      if (_picl_get_root(&rooth) == PICL_SUCCESS) {
        const char* cpu_class = "cpu";
        // If it's a Fujitsu machine, it's a "core"
        if (is_fujitsu) {
          cpu_class = "core";
        }
        CPUVisitor cpu_visitor(this, (is_sun4v && !is_fujitsu) ? 1 : os::processor_count());
        _picl_walk_tree_by_class(rooth, cpu_class, &cpu_visitor, PICL_visit_cpu_helper);
        if (cpu_visitor.l1_visitor()->is_assigned()) { // Is there a value?
          _L1_data_cache_line_size = cpu_visitor.l1_visitor()->value();
        }
        if (cpu_visitor.l2_visitor()->is_assigned()) {
          _L2_data_cache_line_size = cpu_visitor.l2_visitor()->value();
        }
      }
      _picl_shutdown();
    }
    close_library();
  }

  unsigned int L1_data_cache_line_size() const { return _L1_data_cache_line_size; }
  unsigned int L2_data_cache_line_size() const { return _L2_data_cache_line_size; }
};


extern "C" static int PICL_visit_cpu_helper(picl_nodehdl_t nodeh, void *result) {
  return PICL::visit_cpu(nodeh, result);
}

template<typename FuncType>
bool PICL::bind(FuncType& func, const char* name) {
  func = reinterpret_cast<FuncType>(dlsym(_dl_handle, name));
  return func != NULL;
}

bool PICL::bind_library_functions() {
  assert(_dl_handle != NULL, "library should be open");
  return bind(_picl_initialize,         "picl_initialize"        ) &&
         bind(_picl_shutdown,           "picl_shutdown"          ) &&
         bind(_picl_get_root,           "picl_get_root"          ) &&
         bind(_picl_walk_tree_by_class, "picl_walk_tree_by_class") &&
         bind(_picl_get_prop_by_name,   "picl_get_prop_by_name"  ) &&
         bind(_picl_get_propval,        "picl_get_propval"       ) &&
         bind(_picl_get_propinfo,       "picl_get_propinfo"      );
}

bool PICL::open_library() {
  _dl_handle = dlopen("libpicl.so.1", RTLD_LAZY);
  if (_dl_handle == NULL) {
    return false;
  }
  if (!bind_library_functions()) {
    assert(false, "unexpected PICL API change");
    close_library();
    return false;
  }
  return true;
}

void PICL::close_library() {
  assert(_dl_handle != NULL, "library should be open");
  dlclose(_dl_handle);
  _dl_handle = NULL;
}

class Sysinfo {
  char* _string;
public:
  Sysinfo(int si) : _string(NULL) {
    char   tmp;
    size_t bufsize = sysinfo(si, &tmp, 1);

    if (bufsize != -1) {
      char* buf = (char*) os::malloc(bufsize, mtInternal);
      if (buf != NULL) {
        if (sysinfo(si, buf, bufsize) == bufsize) {
          _string = buf;
        } else {
          os::free(buf);
        }
      }
    }
  }

  ~Sysinfo() {
    if (_string != NULL) {
      os::free(_string);
    }
  }

  const char* value() const {
    return _string;
  }

  bool valid() const {
    return _string != NULL;
  }

  bool match(const char* s) const {
    return valid() ? strcmp(_string, s) == 0 : false;
  }

  bool match_substring(const char* s) const {
    return valid() ? strstr(_string, s) != NULL : false;
  }
};

class Sysconf {
  int _value;
public:
  Sysconf(int sc) : _value(-1) {
    _value = sysconf(sc);
  }
  bool valid() const {
    return _value != -1;
  }
  int value() const {
    return _value;
  }
};


#ifndef _SC_DCACHE_LINESZ
#define _SC_DCACHE_LINESZ       508     /* Data cache line size */
#endif

#ifndef _SC_L2CACHE_LINESZ
#define _SC_L2CACHE_LINESZ      527     /* Size of L2 cache line */
#endif

void VM_Version::platform_features() {
  uint64_t features = ISA_v9_msk;   // Basic SPARC-V9 required (V8 not supported).

  assert(Sysinfo(SI_ARCHITECTURE_64).match("sparcv9"), "must be");

  // Extract valid instruction set extensions.
  uint32_t avs[AV_HW2_IDX + 1];
  uint_t avn = getisax(avs, AV_HW2_IDX + 1);
  assert(avn <= 2, "should return two or less av's");

  log_info(os, cpu)("getisax(2) returned %d words:", avn);
  for (int i = 0; i < avn; i++) {
    log_info(os, cpu)("    word %d: " PTR32_FORMAT, i, avs[i]);
  }

  uint32_t av = avs[AV_HW1_IDX];

  // Obsolete and 32b legacy mode capabilites NOT probed here, despite being
  // set by Solaris 11.4 (onward) also on V9; AV_SPARC_MUL32, AV_SPARC_DIV32
  // and AV_SPARC_FSMULD (and AV_SPARC_V8PLUS).

  if (av & AV_SPARC_POPC) features |= ISA_popc_msk;
  if (av & AV_SPARC_VIS)  features |= ISA_vis1_msk;
  if (av & AV_SPARC_VIS2) features |= ISA_vis2_msk;

  // Hardware capability defines introduced after Solaris 11.1:

#ifndef AV_SPARC_FMAF
#define AV_SPARC_FMAF         0x00000100 // Fused Multiply-Add
#endif

  if (av & AV_SPARC_ASI_BLK_INIT) features |= ISA_blk_init_msk;
  if (av & AV_SPARC_FMAF)         features |= ISA_fmaf_msk;
  if (av & AV_SPARC_VIS3)         features |= ISA_vis3_msk;
  if (av & AV_SPARC_HPC)          features |= ISA_hpc_msk;
  if (av & AV_SPARC_IMA)          features |= ISA_ima_msk;
  if (av & AV_SPARC_AES)          features |= ISA_aes_msk;
  if (av & AV_SPARC_DES)          features |= ISA_des_msk;
  if (av & AV_SPARC_KASUMI)       features |= ISA_kasumi_msk;
  if (av & AV_SPARC_CAMELLIA)     features |= ISA_camellia_msk;
  if (av & AV_SPARC_MD5)          features |= ISA_md5_msk;
  if (av & AV_SPARC_SHA1)         features |= ISA_sha1_msk;
  if (av & AV_SPARC_SHA256)       features |= ISA_sha256_msk;
  if (av & AV_SPARC_SHA512)       features |= ISA_sha512_msk;
  if (av & AV_SPARC_MPMUL)        features |= ISA_mpmul_msk;
  if (av & AV_SPARC_MONT)         features |= ISA_mont_msk;
  if (av & AV_SPARC_PAUSE)        features |= ISA_pause_msk;
  if (av & AV_SPARC_CBCOND)       features |= ISA_cbcond_msk;
  if (av & AV_SPARC_CRC32C)       features |= ISA_crc32c_msk;

#ifndef AV2_SPARC_FJATHPLUS
#define AV2_SPARC_FJATHPLUS  0x00000001 // Fujitsu Athena+ insns
#endif
#ifndef AV2_SPARC_VIS3B
#define AV2_SPARC_VIS3B      0x00000002 // VIS3 present on multiple chips
#endif
#ifndef AV2_SPARC_ADI
#define AV2_SPARC_ADI        0x00000004 // Application Data Integrity
#endif
#ifndef AV2_SPARC_SPARC5
#define AV2_SPARC_SPARC5     0x00000008 // The 29 new fp and sub instructions
#endif
#ifndef AV2_SPARC_MWAIT
#define AV2_SPARC_MWAIT      0x00000010 // mwait instruction and load/monitor ASIs
#endif
#ifndef AV2_SPARC_XMPMUL
#define AV2_SPARC_XMPMUL     0x00000020 // XOR multiple precision multiply
#endif
#ifndef AV2_SPARC_XMONT
#define AV2_SPARC_XMONT      0x00000040 // XOR Montgomery mult/sqr instructions
#endif
#ifndef AV2_SPARC_PAUSE_NSEC
#define AV2_SPARC_PAUSE_NSEC 0x00000080 // pause instruction with support for nsec timings
#endif
#ifndef AV2_SPARC_VAMASK
#define AV2_SPARC_VAMASK     0x00000100 // Virtual Address masking
#endif

#ifndef AV2_SPARC_SPARC6
#define AV2_SPARC_SPARC6     0x00000200 // REVB*, FPSLL*, RDENTROPY, LDM* and STM*
#endif
#ifndef AV2_SPARC_DICTUNP
#define AV2_SPARC_DICTUNP    0x00002000 // Dictionary unpack instruction
#endif
#ifndef AV2_SPARC_FPCMPSHL
#define AV2_SPARC_FPCMPSHL   0x00004000 // Partition compare with shifted result
#endif
#ifndef AV2_SPARC_RLE
#define AV2_SPARC_RLE        0x00008000 // Run-length encoded burst and length
#endif
#ifndef AV2_SPARC_SHA3
#define AV2_SPARC_SHA3       0x00010000 // SHA3 instructions
#endif
#ifndef AV2_SPARC_FJATHPLUS2
#define AV2_SPARC_FJATHPLUS2 0x00020000 // Fujitsu Athena++ insns
#endif
#ifndef AV2_SPARC_VIS3C
#define AV2_SPARC_VIS3C      0x00040000 // Subset of VIS3 insns provided by Athena++
#endif
#ifndef AV2_SPARC_SPARC5B
#define AV2_SPARC_SPARC5B    0x00080000 // subset of SPARC5 insns (fpadd8, fpsub8)
#endif
#ifndef AV2_SPARC_MME
#define AV2_SPARC_MME        0x00100000 // Misaligned Mitigation Enable
#endif

  if (avn > 1) {
    uint32_t av2 = avs[AV_HW2_IDX];

    if (av2 & AV2_SPARC_FJATHPLUS)  features |= ISA_fjathplus_msk;
    if (av2 & AV2_SPARC_VIS3B)      features |= ISA_vis3b_msk;
    if (av2 & AV2_SPARC_ADI)        features |= ISA_adi_msk;
    if (av2 & AV2_SPARC_SPARC5)     features |= ISA_sparc5_msk;
    if (av2 & AV2_SPARC_MWAIT)      features |= ISA_mwait_msk;
    if (av2 & AV2_SPARC_XMPMUL)     features |= ISA_xmpmul_msk;
    if (av2 & AV2_SPARC_XMONT)      features |= ISA_xmont_msk;
    if (av2 & AV2_SPARC_PAUSE_NSEC) features |= ISA_pause_nsec_msk;
    if (av2 & AV2_SPARC_VAMASK)     features |= ISA_vamask_msk;

    if (av2 & AV2_SPARC_SPARC6)     features |= ISA_sparc6_msk;
    if (av2 & AV2_SPARC_DICTUNP)    features |= ISA_dictunp_msk;
    if (av2 & AV2_SPARC_FPCMPSHL)   features |= ISA_fpcmpshl_msk;
    if (av2 & AV2_SPARC_RLE)        features |= ISA_rle_msk;
    if (av2 & AV2_SPARC_SHA3)       features |= ISA_sha3_msk;
    if (av2 & AV2_SPARC_FJATHPLUS2) features |= ISA_fjathplus2_msk;
    if (av2 & AV2_SPARC_VIS3C)      features |= ISA_vis3c_msk;
    if (av2 & AV2_SPARC_SPARC5B)    features |= ISA_sparc5b_msk;
    if (av2 & AV2_SPARC_MME)        features |= ISA_mme_msk;
  }

  _features = features;     // ISA feature set completed, update state.

  Sysinfo machine(SI_MACHINE);

  bool is_sun4v = machine.match("sun4v");   // All Oracle SPARC + Fujitsu Athena+/++
  bool is_sun4u = machine.match("sun4u");   // All other Fujitsu

  // Handle Athena+/++ conservatively (simply because we are lacking info.).

  bool an_athena = has_athena_plus() || has_athena_plus2();
  bool do_sun4v  = is_sun4v && !an_athena;
  bool do_sun4u  = is_sun4u ||  an_athena;

  uint64_t synthetic = 0;

  if (do_sun4v) {
    // Indirect and direct branches are equally fast.
    synthetic = CPU_fast_ind_br_msk;
    // Fast IDIV, BIS and LD available on Niagara Plus.
    if (has_vis2()) {
      synthetic |= (CPU_fast_idiv_msk | CPU_fast_ld_msk);
      // ...on Core C4 however, we prefer not to use BIS.
      if (!has_sparc5()) {
        synthetic |= CPU_fast_bis_msk;
      }
    }
    // SPARC Core C3 supports fast RDPC and block zeroing.
    if (has_ima()) {
      synthetic |= (CPU_fast_rdpc_msk | CPU_blk_zeroing_msk);
    }
    // SPARC Core C3 and C4 have slow CMOVE.
    if (!has_ima()) {
      synthetic |= CPU_fast_cmove_msk;
    }
  } else if (do_sun4u) {
    // SPARC64 only have fast IDIV and RDPC.
    synthetic |= (CPU_fast_idiv_msk | CPU_fast_rdpc_msk);
  } else {
    log_info(os, cpu)("Unable to derive CPU features: %s", machine.value());
  }

  _features += synthetic;   // Including CPU derived/synthetic features.

  Sysconf l1_dcache_line_size(_SC_DCACHE_LINESZ);
  Sysconf l2_dcache_line_size(_SC_L2CACHE_LINESZ);

  // Require both Sysconf requests to be valid or use fall-back.

  if (l1_dcache_line_size.valid() &&
      l2_dcache_line_size.valid()) {
    _L1_data_cache_line_size = l1_dcache_line_size.value();
    _L2_data_cache_line_size = l2_dcache_line_size.value();
  } else {
    // Otherwise figure out the cache line sizes using PICL.
    PICL picl(is_sun4u, is_sun4v);
    _L1_data_cache_line_size = picl.L1_data_cache_line_size();
    _L2_data_cache_line_size = picl.L2_data_cache_line_size();
  }
}
