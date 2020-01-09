/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_GLOBALS_SHARED_HPP
#define SHARE_RUNTIME_GLOBALS_SHARED_HPP

#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

#include <float.h> // for DBL_MAX

// The larger HeapWordSize for 64bit requires larger heaps
// for the same application running in 64bit.  See bug 4967770.
// The minimum alignment to a heap word size is done.  Other
// parts of the memory system may require additional alignment
// and are responsible for those alignments.
#ifdef _LP64
#define ScaleForWordSize(x) align_down_((x) * 13 / 10, HeapWordSize)
#else
#define ScaleForWordSize(x) (x)
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

// Only materialize src code for range checking when required, ignore otherwise
#define IGNORE_RANGE(a, b)
// Only materialize src code for contraint checking when required, ignore otherwise
#define IGNORE_CONSTRAINT(func,type)

#define VM_FLAGS(             \
    develop,                  \
    develop_pd,               \
    product,                  \
    product_pd,               \
    diagnostic,               \
    diagnostic_pd,            \
    experimental,             \
    notproduct,               \
    manageable,               \
    product_rw,               \
    lp64_product,             \
    range,                    \
    constraint)               \
                              \
  RUNTIME_FLAGS(              \
    develop,                  \
    develop_pd,               \
    product,                  \
    product_pd,               \
    diagnostic,               \
    diagnostic_pd,            \
    experimental,             \
    notproduct,               \
    manageable,               \
    product_rw,               \
    lp64_product,             \
    range,                    \
    constraint)               \
                              \
  GC_FLAGS(                   \
    develop,                  \
    develop_pd,               \
    product,                  \
    product_pd,               \
    diagnostic,               \
    diagnostic_pd,            \
    experimental,             \
    notproduct,               \
    manageable,               \
    product_rw,               \
    lp64_product,             \
    range,                    \
    constraint)               \


#define ALL_FLAGS(            \
    develop,                  \
    develop_pd,               \
    product,                  \
    product_pd,               \
    diagnostic,               \
    diagnostic_pd,            \
    experimental,             \
    notproduct,               \
    manageable,               \
    product_rw,               \
    lp64_product,             \
    range,                    \
    constraint)               \
                              \
  VM_FLAGS(                   \
    develop,                  \
    develop_pd,               \
    product,                  \
    product_pd,               \
    diagnostic,               \
    diagnostic_pd,            \
    experimental,             \
    notproduct,               \
    manageable,               \
    product_rw,               \
    lp64_product,             \
    range,                    \
    constraint)               \
                              \
  RUNTIME_OS_FLAGS(           \
    develop,                  \
    develop_pd,               \
    product,                  \
    product_pd,               \
    diagnostic,               \
    diagnostic_pd,            \
    notproduct,               \
    range,                    \
    constraint)               \
                              \
  JVMCI_ONLY(JVMCI_FLAGS(     \
    develop,                  \
    develop_pd,               \
    product,                  \
    product_pd,               \
    diagnostic,               \
    diagnostic_pd,            \
    experimental,             \
    notproduct,               \
    range,                    \
    constraint))              \
                              \
  COMPILER1_PRESENT(C1_FLAGS( \
    develop,                  \
    develop_pd,               \
    product,                  \
    product_pd,               \
    diagnostic,               \
    diagnostic_pd,            \
    notproduct,               \
    range,                    \
    constraint))              \
                              \
  COMPILER2_PRESENT(C2_FLAGS( \
    develop,                  \
    develop_pd,               \
    product,                  \
    product_pd,               \
    diagnostic,               \
    diagnostic_pd,            \
    experimental,             \
    notproduct,               \
    range,                    \
    constraint))              \
                              \
  ARCH_FLAGS(                 \
    develop,                  \
    product,                  \
    diagnostic,               \
    experimental,             \
    notproduct,               \
    range,                    \
    constraint)

#endif // SHARE_RUNTIME_GLOBALS_SHARED_HPP
