/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2018 SAP SE. All rights reserved.
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

#ifndef CPU_PPC_VM_VM_VERSION_PPC_HPP
#define CPU_PPC_VM_VM_VERSION_PPC_HPP

#include "runtime/globals_extension.hpp"
#include "runtime/vm_version.hpp"

class VM_Version: public Abstract_VM_Version {
protected:
  enum Feature_Flag {
    fsqrt,
    fsqrts,
    isel,
    lxarxeh,
    cmpb,
    popcntb,
    popcntw,
    fcfids,
    vand,
    lqarx,
    vcipher,
    vpmsumb,
    mfdscr,
    vsx,
    ldbrx,
    stdbrx,
    vshasig,
    rtm,
    darn,
    num_features // last entry to count features
  };
  enum Feature_Flag_Set {
    unknown_m             = 0,
    fsqrt_m               = (1 << fsqrt  ),
    fsqrts_m              = (1 << fsqrts ),
    isel_m                = (1 << isel   ),
    lxarxeh_m             = (1 << lxarxeh),
    cmpb_m                = (1 << cmpb   ),
    popcntb_m             = (1 << popcntb),
    popcntw_m             = (1 << popcntw),
    fcfids_m              = (1 << fcfids ),
    vand_m                = (1 << vand   ),
    lqarx_m               = (1 << lqarx  ),
    vcipher_m             = (1 << vcipher),
    vpmsumb_m             = (1 << vpmsumb),
    mfdscr_m              = (1 << mfdscr ),
    vsx_m                 = (1 << vsx    ),
    ldbrx_m               = (1 << ldbrx  ),
    stdbrx_m              = (1 << stdbrx ),
    vshasig_m             = (1 << vshasig),
    rtm_m                 = (1 << rtm    ),
    darn_m                = (1 << darn   ),
    all_features_m        = (unsigned long)-1
  };

  static bool _is_determine_features_test_running;

  static void print_features();
  static void determine_features(); // also measures cache line size
  static void config_dscr(); // Power 8: Configure Data Stream Control Register.
  static void determine_section_size();
  static void power6_micro_bench();
public:
  // Initialization
  static void initialize();

  // Override Abstract_VM_Version implementation
  static bool use_biased_locking();

  static bool is_determine_features_test_running() { return _is_determine_features_test_running; }
  // CPU instruction support
  static bool has_fsqrt()   { return (_features & fsqrt_m) != 0; }
  static bool has_fsqrts()  { return (_features & fsqrts_m) != 0; }
  static bool has_isel()    { return (_features & isel_m) != 0; }
  static bool has_lxarxeh() { return (_features & lxarxeh_m) !=0; }
  static bool has_cmpb()    { return (_features & cmpb_m) != 0; }
  static bool has_popcntb() { return (_features & popcntb_m) != 0; }
  static bool has_popcntw() { return (_features & popcntw_m) != 0; }
  static bool has_fcfids()  { return (_features & fcfids_m) != 0; }
  static bool has_vand()    { return (_features & vand_m) != 0; }
  static bool has_lqarx()   { return (_features & lqarx_m) != 0; }
  static bool has_vcipher() { return (_features & vcipher_m) != 0; }
  static bool has_vpmsumb() { return (_features & vpmsumb_m) != 0; }
  static bool has_mfdscr()  { return (_features & mfdscr_m) != 0; }
  static bool has_vsx()     { return (_features & vsx_m) != 0; }
  static bool has_ldbrx()   { return (_features & ldbrx_m) != 0; }
  static bool has_stdbrx()  { return (_features & stdbrx_m) != 0; }
  static bool has_vshasig() { return (_features & vshasig_m) != 0; }
  static bool has_tm()      { return (_features & rtm_m) != 0; }
  static bool has_darn()    { return (_features & darn_m) != 0; }

  static bool has_mtfprd()  { return has_vpmsumb(); } // alias for P8

  // Assembler testing
  static void allow_all();
  static void revert();

  // POWER 8: DSCR current value.
  static uint64_t _dscr_val;
};

#endif // CPU_PPC_VM_VM_VERSION_PPC_HPP
