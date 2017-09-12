/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/bufferingOopClosure.hpp"
#include "gc/g1/g1CodeBlobClosure.hpp"
#include "gc/g1/g1OopClosures.hpp"
#include "memory/iterator.hpp"

class G1CollectedHeap;
class G1ParScanThreadState;

// Simple holder object for a complete set of closures used by the G1 evacuation code.
template <G1Mark Mark, bool use_ext = false>
class G1SharedClosures VALUE_OBJ_CLASS_SPEC {
public:
  G1ParCopyClosure<G1BarrierNone,  Mark, use_ext> _oops;
  G1ParCopyClosure<G1BarrierKlass, Mark, use_ext> _oop_in_klass;
  G1KlassScanClosure                              _klass_in_cld_closure;
  CLDToKlassAndOopClosure                         _clds;
  G1CodeBlobClosure                               _codeblobs;
  BufferingOopClosure                             _buffered_oops;

  G1SharedClosures(G1CollectedHeap* g1h, G1ParScanThreadState* pss, bool process_only_dirty_klasses, bool must_claim_cld) :
    _oops(g1h, pss),
    _oop_in_klass(g1h, pss),
    _klass_in_cld_closure(&_oop_in_klass, process_only_dirty_klasses),
    _clds(&_klass_in_cld_closure, &_oops, must_claim_cld),
    _codeblobs(&_oops),
    _buffered_oops(&_oops) {}
};
