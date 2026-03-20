/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CODE_AOTPDCONFIG_HPP
#define SHARE_CODE_AOTPDCONFIG_HPP

#define VERIFY_VM_PARAMETER(rt_param, type_ignored) \
  if (VMParam_##rt_param != rt_param) { \
    failure_msg.print("VM paramter %s in cache=" INTPTR_FORMAT ", current=" INTPTR_FORMAT, #rt_param, (intptr_t)VMParam_##rt_param, (intptr_t)rt_param); \
    return false; \
  }

#define VERIFY_VM_FLAG_SAME(rt_flag) \
  if (cached_flag(VMFlag_##rt_flag) != rt_flag) { \
    failure_msg.print("VM flag %s in cache=%s, current=%s", #rt_flag, cached_to_str(VMFlag_##rt_flag), BOOL_TO_STR(rt_flag)); \
    return false; \
  }

#define VERIFY_VM_FLAG_SET(rt_flag) \
  if (cached_flag(VMFlag_##rt_flag) && !rt_flag) { \
    failure_msg.print("VM flag %s in cache=%s, current=%s", #rt_flag, cached_to_str(VMFlag_##rt_flag), BOOL_TO_STR(rt_flag)); \
    return false; \
  }

#define VERIFY_VM_FLAG_NOT_SET(rt_flag) \
  if (!cached_flag(VMFlag_##rt_flag) && rt_flag) { \
    failure_msg.print("VM flag %s in cache=%s, current=%s", #rt_flag, cached_to_str(VMFlag_##rt_flag), BOOL_TO_STR(rt_flag)); \
    return false; \
  }

#define BIT_MASK(flag) (1ULL<<(flag))

// This class encapsulates all the platform dependent configuration that can affect
// the AOT code like stubs, blobs, adapters or nmethods.
// This configuration is stored in AOTCodeCache. If any of the config changes
// between assembly and production run, then AOTCodeCache is invalidated.
// Platform dependent VM flags and VM parameters are declared in platform-specific header files
// named as aot_config_xxx.hpp (eg aot_config_x86.hpp).
// Convention is to use "VM flags" to refer to VM settings of type boolean.
// All other VM settings are referred as "VM parameters".
class AOTPDConfig {
private:
  #include CPU_HEADER(aot_config)

  uint64_t _aot_flags;

  void set_flag(AOTCodeVMFlags flag) {
    _aot_flags |= BIT_MASK(flag);
  }
 
  bool cached_flag(AOTCodeVMFlags flag) const {
    return (_aot_flags & BIT_MASK(flag)) != 0;
  }
 
  const char* cached_to_str(AOTCodeVMFlags flag) const {
    bool flag_value = cached_flag(flag);
    return BOOL_TO_STR(flag_value);
  }

public:
  AOTPDConfig() {}
  // platform-dependent method defined in aot_config_xxx.cpp
  void record();
  // platform-dependent method defined in aot_config_xxx.cpp
  bool verify(stringStream& failure_msg) const;
};

#endif // SHARE_CODE_AOTPDCONFIG_HPP
