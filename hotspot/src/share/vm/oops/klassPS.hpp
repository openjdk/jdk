/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
#ifndef KLASS_PS_H
#define KLASS_PS_H

  // Expands to Parallel Scavenge and Parallel Old declarations

#ifndef SERIALGC
#define PARALLEL_GC_DECLS \
  virtual void oop_push_contents(PSPromotionManager* pm, oop obj);          \
  /* Parallel Old GC support                                                \
                                                                            \
   The 2-arg version of oop_update_pointers is for objects that are         \
   known not to cross chunk boundaries.  The 4-arg version is for           \
   objects that do (or may) cross chunk boundaries; it updates only those   \
   oops that are in the region [beg_addr, end_addr).  */                    \
  virtual void oop_follow_contents(ParCompactionManager* cm, oop obj);      \
  virtual int  oop_update_pointers(ParCompactionManager* cm, oop obj);      \
  virtual int  oop_update_pointers(ParCompactionManager* cm, oop obj,       \
                                   HeapWord* beg_addr, HeapWord* end_addr);

// Pure virtual version for klass.hpp
#define PARALLEL_GC_DECLS_PV \
  virtual void oop_push_contents(PSPromotionManager* pm, oop obj) = 0;      \
  virtual void oop_follow_contents(ParCompactionManager* cm, oop obj) = 0;  \
  virtual int  oop_update_pointers(ParCompactionManager* cm, oop obj) = 0;  \
  virtual int  oop_update_pointers(ParCompactionManager* cm, oop obj,       \
                                   HeapWord* beg_addr, HeapWord* end_addr) = 0;
#else  // SERIALGC
#define PARALLEL_GC_DECLS
#define PARALLEL_GC_DECLS_PV
#endif // SERIALGC

#endif // KLASS_PS_H
