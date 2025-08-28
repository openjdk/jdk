/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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

// Precompiled headers are turned off if the user passes
// --disable-precompiled-headers to configure.

// These header files are selected using the output of Clang
// '-ftime-trace' as a measure of how much time we spend
// compiling them.

#include "classfile/javaClasses.inline.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/instanceStackChunkKlass.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#if INCLUDE_SHENANDOAHGC
#include "gc/shenandoah/shenandoahBarrierSet.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#endif
#if INCLUDE_ZGC
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#endif
