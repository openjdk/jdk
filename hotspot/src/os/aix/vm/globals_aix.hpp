/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2015 SAP AG. All rights reserved.
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

#ifndef OS_AIX_VM_GLOBALS_AIX_HPP
#define OS_AIX_VM_GLOBALS_AIX_HPP

//
// Defines Aix specific flags. They are not available on other platforms.
//
#define RUNTIME_OS_FLAGS(develop, develop_pd, product, product_pd, diagnostic, notproduct, range, constraint) \
                                                                                    \
  /* Use 64K pages for virtual memory (shmat). */                                   \
  product(bool, Use64KPages, true,                                                  \
          "Use 64K pages if available.")                                            \
                                                                                    \
  /* If UseLargePages == true allow or deny usage of 16M pages. 16M pages are  */   \
  /* a scarce resource and there may be situations where we do not want the VM */   \
  /* to run with 16M pages. (Will fall back to 64K pages).                     */   \
  product_pd(bool, Use16MPages,                                                     \
             "Use 16M pages if available.")                                         \
                                                                                    \
  /*  use optimized addresses for the polling page, */                              \
  /* e.g. map it to a special 32-bit address.       */                              \
  product_pd(bool, OptimizePollingPageLocation,                                     \
             "Optimize the location of the polling page used for Safepoints")       \
                                                                                    \
  product_pd(intx, AttachListenerTimeout,                                           \
             "Timeout in ms the attach listener waits for a request")               \
             range(0, 2147483)                                                      \
                                                                                    \

// Per default, do not allow 16M pages. 16M pages have to be switched on specifically.
define_pd_global(bool, Use16MPages, false);
define_pd_global(bool, OptimizePollingPageLocation, true);
define_pd_global(intx, AttachListenerTimeout, 1000);

//
// Defines Aix-specific default values. The flags are available on all
// platforms, but they may have different default values on other platforms.
//
define_pd_global(bool, UseLargePages, false);
define_pd_global(bool, UseLargePagesIndividualAllocation, false);
define_pd_global(bool, UseOSErrorReporting, false);
define_pd_global(bool, UseThreadPriorities, true) ;

#endif // OS_AIX_VM_GLOBALS_AIX_HPP
