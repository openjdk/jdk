/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_CLASSUNLOADINGCONTEXT_HPP
#define SHARE_GC_SHARED_CLASSUNLOADINGCONTEXT_HPP

#include "memory/allocation.hpp"
#include "utilities/growableArray.hpp"

class ClassLoaderData;
class Klass;
class nmethod;

class ClassUnloadingContext : public CHeapObj<mtGC> {
  static ClassUnloadingContext* _context;

  ClassLoaderData* volatile _cld_head;

  const uint _num_nmethod_unlink_workers;

  using NMethodSet = GrowableArrayCHeap<nmethod*, mtGC>;
  NMethodSet** _unlinked_nmethods;

  bool _lock_codeblob_free_separately;

public:
  static ClassUnloadingContext* context() { assert(_context != nullptr, "context not set"); return _context; }

  // Num_nmethod_unlink_workers configures the maximum numbers of threads unlinking
  //     nmethods.
  // lock_codeblob_free_separately determines whether freeing the code blobs takes
  //     the CodeCache_lock during the whole operation (=false) or per code blob
  //     free operation (=true).
  ClassUnloadingContext(uint num_nmethod_unlink_workers,
                        bool lock_codeblob_free_separately);
  ~ClassUnloadingContext();

  bool has_unloaded_classes() const;

  void register_unloading_class_loader_data(ClassLoaderData* cld);
  void purge_class_loader_data();

  void classes_unloading_do(void f(Klass* const));

  // Register unloading nmethods, potentially in parallel.
  void register_unlinked_nmethod(nmethod* nm);
  void purge_nmethods();
  void free_code_blobs();

  void purge_and_free_nmethods() {
    purge_nmethods();
    free_code_blobs();
  }
};

#endif // SHARE_GC_SHARED_CLASSUNLOADINGCONTEXT_HPP
