/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/serializeClosure.hpp"
#include "classfile/vmClasses.hpp"
#include "compiler/oopMap.inline.hpp"
#include "gc/shared/gc_globals.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceStackChunkKlass.inline.hpp"
#include "oops/stackChunkOop.inline.hpp"
#include "runtime/continuation.hpp"
#include "runtime/continuationJavaClasses.inline.hpp"
#include "runtime/frame.hpp"
#include "runtime/handles.hpp"
#include "runtime/registerMap.hpp"
#include "runtime/smallRegisterMap.inline.hpp"
#include "runtime/stackChunkFrameStream.inline.hpp"
#include "utilities/devirtualizer.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

int InstanceStackChunkKlass::_offset_of_stack = 0;

#if INCLUDE_CDS
void InstanceStackChunkKlass::serialize_offsets(SerializeClosure* f) {
  f->do_int(&_offset_of_stack);
}
#endif

InstanceStackChunkKlass::InstanceStackChunkKlass() {
  assert(CDSConfig::is_dumping_static_archive() || UseSharedSpaces, "only for CDS");
}

InstanceStackChunkKlass::InstanceStackChunkKlass(const ClassFileParser& parser)
  : InstanceKlass(parser, Kind) {
  // Change the layout_helper to use the slow path because StackChunkOops are
  // variable sized InstanceOops.
  const jint lh = Klass::instance_layout_helper(size_helper(), true);
  set_layout_helper(lh);
}

size_t InstanceStackChunkKlass::oop_size(oop obj) const {
  return instance_size(jdk_internal_vm_StackChunk::size(obj));
}

#ifndef PRODUCT
void InstanceStackChunkKlass::oop_print_on(oop obj, outputStream* st) {
  print_chunk(stackChunkOopDesc::cast(obj), false, st);
}
#endif

template<typename OopClosureType>
class StackChunkOopIterateFilterClosure: public OopClosure {
private:
  OopClosureType* const _closure;
  MemRegion _bound;

public:

  StackChunkOopIterateFilterClosure(OopClosureType* closure, MemRegion bound)
    : _closure(closure),
      _bound(bound) {}

  virtual void do_oop(oop* p)       override { do_oop_work(p); }
  virtual void do_oop(narrowOop* p) override { do_oop_work(p); }

  template <typename T>
  void do_oop_work(T* p) {
    if (_bound.contains(p)) {
      Devirtualizer::do_oop(_closure, p);
    }
  }
};

class DoMethodsStackChunkFrameClosure {
  OopIterateClosure* _closure;

public:
  DoMethodsStackChunkFrameClosure(OopIterateClosure* cl) : _closure(cl) {}

  template <ChunkFrames frame_kind, typename RegisterMapT>
  bool do_frame(const StackChunkFrameStream<frame_kind>& f, const RegisterMapT* map) {
    if (f.is_interpreted()) {
      Method* m = f.to_frame().interpreter_frame_method();
      _closure->do_method(m);
    } else if (f.is_compiled()) {
      nmethod* nm = f.cb()->as_nmethod();
      // The do_nmethod function takes care of having the right synchronization
      // when keeping the nmethod alive during concurrent execution.
      _closure->do_nmethod(nm);
      // There is no need to mark the Method, as class redefinition will walk the
      // CodeCache, noting their Methods
    }
    return true;
  }
};

void InstanceStackChunkKlass::do_methods(stackChunkOop chunk, OopIterateClosure* cl) {
  DoMethodsStackChunkFrameClosure closure(cl);
  chunk->iterate_stack(&closure);
}

class OopIterateStackChunkFrameClosure {
  OopIterateClosure* const _closure;
  MemRegion _bound;
  const bool _do_metadata;

public:
  OopIterateStackChunkFrameClosure(OopIterateClosure* closure, MemRegion mr)
    : _closure(closure),
      _bound(mr),
      _do_metadata(_closure->do_metadata()) {
    assert(_closure != nullptr, "must be set");
  }

  template <ChunkFrames frame_kind, typename RegisterMapT>
  bool do_frame(const StackChunkFrameStream<frame_kind>& f, const RegisterMapT* map) {
    if (_do_metadata) {
      DoMethodsStackChunkFrameClosure(_closure).do_frame(f, map);
    }

    StackChunkOopIterateFilterClosure<OopIterateClosure> cl(_closure, _bound);
    f.iterate_oops(&cl, map);

    return true;
  }
};

void InstanceStackChunkKlass::oop_oop_iterate_stack_slow(stackChunkOop chunk, OopIterateClosure* closure, MemRegion mr) {
  if (UseZGC || UseShenandoahGC) {
    // An OopClosure could apply barriers to a stack chunk. The side effects
    // of the load barriers could destroy derived pointers, which must be
    // processed before their base oop is processed. So we force processing
    // of derived pointers before applying the closures.
    chunk->relativize_derived_pointers_concurrently();
  }
  OopIterateStackChunkFrameClosure frame_closure(closure, mr);
  chunk->iterate_stack(&frame_closure);
}

#ifdef ASSERT

class DescribeStackChunkClosure {
  stackChunkOop _chunk;
  FrameValues _values;
  RegisterMap _map;
  int _frame_no;

public:
  DescribeStackChunkClosure(stackChunkOop chunk)
    : _chunk(chunk),
      _map(nullptr,
           RegisterMap::UpdateMap::include,
           RegisterMap::ProcessFrames::skip,
           RegisterMap::WalkContinuation::include),
      _frame_no(0) {
    _map.set_include_argument_oops(false);
  }

  const RegisterMap* get_map(const RegisterMap* map,      intptr_t* sp) { return map; }
  const RegisterMap* get_map(const SmallRegisterMap* map, intptr_t* sp) { return map->copy_to_RegisterMap(&_map, sp); }

  template <ChunkFrames frame_kind, typename RegisterMapT>
  bool do_frame(const StackChunkFrameStream<frame_kind>& f, const RegisterMapT* map) {
    ResetNoHandleMark rnhm;
    HandleMark hm(Thread::current());

    frame fr = f.to_frame();
    fr.describe(_values, _frame_no++, get_map(map, f.sp()));
    return true;
  }

  void describe_chunk() {
    // _values.describe(-1, _chunk->start_address(), "CHUNK START");
    _values.describe(-1, _chunk->sp_address(),         "CHUNK SP");
    _values.describe(-1, _chunk->bottom_address() - 1, "CHUNK ARGS");
    _values.describe(-1, _chunk->end_address() - 1,    "CHUNK END");
  }

  void print_on(outputStream* out) {
    if (_frame_no > 0) {
      describe_chunk();
      _values.print_on(_chunk, out);
    } else {
      out->print_cr(" EMPTY");
    }
  }
};
#endif

class PrintStackChunkClosure {
  outputStream* _st;

public:
  PrintStackChunkClosure(outputStream* st) : _st(st) {}

  template <ChunkFrames frame_kind, typename RegisterMapT>
  bool do_frame(const StackChunkFrameStream<frame_kind>& fs, const RegisterMapT* map) {
    frame f = fs.to_frame();
    _st->print_cr("-- frame sp: " PTR_FORMAT " interpreted: %d size: %d argsize: %d",
                  p2i(fs.sp()), fs.is_interpreted(), f.frame_size(),
                  fs.is_interpreted() ? 0 : f.compiled_frame_stack_argsize());
  #ifdef ASSERT
    f.print_value_on(_st, nullptr);
  #else
    f.print_on(_st);
  #endif
    const ImmutableOopMap* oopmap = fs.oopmap();
    if (oopmap != nullptr) {
      oopmap->print_on(_st);
      _st->cr();
    }
    return true;
  }
};

void InstanceStackChunkKlass::print_chunk(const stackChunkOop c, bool verbose, outputStream* st) {
  if (c == nullptr) {
    st->print_cr("CHUNK null");
    return;
  }

  st->print_cr("CHUNK " PTR_FORMAT " " PTR_FORMAT " - " PTR_FORMAT " :: " INTPTR_FORMAT,
               p2i(c), p2i(c->start_address()), p2i(c->end_address()), c->identity_hash());
  st->print_cr("       barriers: %d gc_mode: %d bitmap: %d parent: " PTR_FORMAT,
               c->requires_barriers(), c->is_gc_mode(), c->has_bitmap(), p2i(c->parent()));
  st->print_cr("       flags mixed: %d", c->has_mixed_frames());
  st->print_cr("       size: %d argsize: %d max_size: %d sp: %d pc: " PTR_FORMAT,
               c->stack_size(), c->argsize(), c->max_thawing_size(), c->sp(), p2i(c->pc()));

  if (verbose) {
    st->cr();
    st->print_cr("------ chunk frames end: " PTR_FORMAT, p2i(c->bottom_address()));
    PrintStackChunkClosure closure(st);
    c->iterate_stack(&closure);
    st->print_cr("------");

  #ifdef ASSERT
    ResourceMark rm;
    DescribeStackChunkClosure describe(c);
    c->iterate_stack(&describe);
    describe.print_on(st);
    st->print_cr("======");
  #endif
  }
}

void InstanceStackChunkKlass::init_offset_of_stack() {
  // Cache the offset of the static fields in the Class instance
  assert(_offset_of_stack == 0, "once");
  _offset_of_stack = cast(vmClasses::StackChunk_klass())->size_helper() << LogHeapWordSize;
}
