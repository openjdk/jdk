/*
 * Copyright (c) 2017, 2022, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHWORKERPOLICY_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHWORKERPOLICY_HPP

#include "memory/allStatic.hpp"

class ShenandoahWorkerPolicy : AllStatic {
public:
  // Normal GC cycle:
  static uint calc_workers_for_init_marking()         { return ParallelGCThreads; }
  static uint calc_workers_for_conc_marking()         { return ConcGCThreads;     }
  static uint calc_workers_for_final_marking()        { return ParallelGCThreads; }
  static uint calc_workers_for_conc_refs_processing() { return ConcGCThreads;     }
  static uint calc_workers_for_conc_root_processing() { return ConcGCThreads;     }
  static uint calc_workers_for_conc_evac()            { return ConcGCThreads;     }
  static uint calc_workers_for_conc_update_ref()      { return ConcGCThreads;     }
  static uint calc_workers_for_final_update_ref()     { return ParallelGCThreads; }
  static uint calc_workers_for_conc_reset()           { return ConcGCThreads;     }

  // STW GC cycles:
  static uint calc_workers_for_stw_degenerated()      { return ParallelGCThreads; }
  static uint calc_workers_for_fullgc()               { return ParallelGCThreads; }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHWORKERPOLICY_HPP
