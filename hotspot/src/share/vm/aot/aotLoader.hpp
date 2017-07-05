/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_VM_AOT_AOTLOADER_HPP
#define SHARE_VM_AOT_AOTLOADER_HPP

#include "runtime/globals_extension.hpp"
#include "runtime/handles.hpp"

class AOTCodeHeap;
class AOTLib;
class CodeBlob;
template <class T> class GrowableArray;
class InstanceKlass;
class JavaThread;
class Metadata;
class OopClosure;

class AOTLoader {
private:
#if INCLUDE_AOT
  static GrowableArray<AOTCodeHeap*>* _heaps;
  static GrowableArray<AOTLib*>* _libraries;
#endif
  static void load_library(const char* name, bool exit_on_error);

public:
#if INCLUDE_AOT
  static GrowableArray<AOTCodeHeap*>* heaps();
  static GrowableArray<AOTLib*>* libraries();
  static int heaps_count();
  static int libraries_count();
  static void add_heap(AOTCodeHeap *heap);
  static void add_library(AOTLib *lib);
#endif
  static void initialize() NOT_AOT({ FLAG_SET_ERGO(bool, UseAOT, false); });

  static void universe_init() NOT_AOT_RETURN;
  static void set_narrow_klass_shift() NOT_AOT_RETURN;
  static bool contains(address p) NOT_AOT({ return false; });
  static void load_for_klass(instanceKlassHandle, Thread* thread) NOT_AOT_RETURN;
  static bool find_klass(InstanceKlass* ik) NOT_AOT({ return false; });
  static uint64_t get_saved_fingerprint(InstanceKlass* ik) NOT_AOT({ return 0; });
  static void oops_do(OopClosure* f) NOT_AOT_RETURN;
  static void metadata_do(void f(Metadata*)) NOT_AOT_RETURN;
  static address exception_begin(JavaThread* thread, CodeBlob* blob, address return_address) NOT_AOT({ return NULL; });

  NOT_PRODUCT( static void print_statistics() NOT_AOT_RETURN; )

#ifdef HOTSWAP
  // Flushing and deoptimization in case of evolution
  static void flush_evol_dependents_on(instanceKlassHandle dependee) NOT_AOT_RETURN;
#endif // HOTSWAP

};

#endif // SHARE_VM_AOT_AOTLOADER_HPP
