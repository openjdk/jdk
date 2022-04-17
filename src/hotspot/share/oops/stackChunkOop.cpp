/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "code/compiledMethod.hpp"
#include "code/scopeDesc.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/memRegion.hpp"
#include "oops/instanceStackChunkKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/stackChunkOop.inline.hpp"
#include "runtime/frame.hpp"
#include "runtime/registerMap.hpp"
#include "runtime/smallRegisterMap.inline.hpp"
#include "runtime/stackChunkFrameStream.inline.hpp"
#if INCLUDE_SHENANDOAHGC
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#endif
#if INCLUDE_ZGC
#include "gc/z/zAddress.inline.hpp"
#endif

frame stackChunkOopDesc::top_frame(RegisterMap* map) {
  assert(!is_empty(), "");
  StackChunkFrameStream<ChunkFrames::Mixed> fs(this);

  map->set_stack_chunk(this);
  fs.initialize_register_map(map);

  frame f = fs.to_frame();

  assert(to_offset(f.sp()) == sp(), "f.offset_sp(): %d sp(): %d async: %d", f.offset_sp(), sp(), map->is_async());
  relativize_frame(f);
  f.set_frame_index(0);
  return f;
}

frame stackChunkOopDesc::sender(const frame& f, RegisterMap* map) {
  assert(map->in_cont(), "");
  assert(!map->include_argument_oops(), "");
  assert(!f.is_empty(), "");
  assert(map->stack_chunk() == this, "");
  assert(!is_empty(), "");

  int index = f.frame_index(); // we need to capture the index before calling derelativize, which destroys it
  StackChunkFrameStream<ChunkFrames::Mixed> fs(this, derelativize(f));
  fs.next(map);

  if (!fs.is_done()) {
    frame sender = fs.to_frame();
    assert(is_usable_in_chunk(sender.unextended_sp()), "");
    relativize_frame(sender);

    sender.set_frame_index(index+1);
    return sender;
  }

  if (parent() != nullptr) {
    assert(!parent()->is_empty(), "");
    return parent()->top_frame(map);
  }

  return Continuation::continuation_parent_frame(map);
}

static int num_java_frames(CompiledMethod* cm, address pc) {
  int count = 0;
  for (ScopeDesc* scope = cm->scope_desc_at(pc); scope != nullptr; scope = scope->sender()) {
    count++;
  }
  return count;
}

static int num_java_frames(const StackChunkFrameStream<ChunkFrames::Mixed>& f) {
  assert(f.is_interpreted()
         || (f.cb() != nullptr && f.cb()->is_compiled() && f.cb()->as_compiled_method()->is_java_method()), "");
  return f.is_interpreted() ? 1 : num_java_frames(f.cb()->as_compiled_method(), f.orig_pc());
}

int stackChunkOopDesc::num_java_frames() const {
  int n = 0;
  for (StackChunkFrameStream<ChunkFrames::Mixed> f(const_cast<stackChunkOopDesc*>(this)); !f.is_done();
       f.next(SmallRegisterMap::instance)) {
    if (!f.is_stub()) {
      n += ::num_java_frames(f);
    }
  }
  return n;
}

// The high-order bit tagging has only been verified to work on these platforms.
#if (defined(X86) && defined(_LP64)) || defined(AARCH64)
#define HIGH_ORDER_BIT_TAGGING_SUPPORTED
#endif

// We use the high-order (sign) bit to tag derived oop offsets.
// However, we cannot rely on simple nagation, as offsets could hypothetically be zero or even negative

static inline bool is_derived_oop_offset(intptr_t value) {
  return value < 0;
}

#ifdef HIGH_ORDER_BIT_TAGGING_SUPPORTED
static const intptr_t OFFSET_MAX = (intptr_t)((uintptr_t)1 << 35); // 32 bit offset + 3 for jlong (offset in bytes)
static const intptr_t OFFSET_MIN = -OFFSET_MAX - 1;
#endif

static inline intptr_t tag_derived_oop_offset(intptr_t untagged) {
#ifdef HIGH_ORDER_BIT_TAGGING_SUPPORTED
  assert(untagged <= OFFSET_MAX, "");
  assert(untagged >= OFFSET_MIN, "");
  intptr_t tagged = (intptr_t)((uintptr_t)untagged | ((uintptr_t)1 << (BitsPerWord-1)));
  assert(is_derived_oop_offset(tagged), "");
  return tagged;
#else
  Unimplemented();
  return 0;
#endif
}

static inline intptr_t untag_derived_oop_offset(intptr_t tagged) {
#ifdef HIGH_ORDER_BIT_TAGGING_SUPPORTED
  assert(is_derived_oop_offset(tagged), "");
  intptr_t untagged = (intptr_t)((uintptr_t)tagged << 1) >> 1;  // unsigned left shift; signed right shift
  assert(untagged <= OFFSET_MAX, "");
  assert(untagged >= OFFSET_MIN, "");
  return untagged;
#else
  Unimplemented();
  return 0;
#endif
}

template <stackChunkOopDesc::BarrierType barrier>
class DoBarriersStackClosure {
  const stackChunkOop _chunk;

public:
  DoBarriersStackClosure(stackChunkOop chunk) : _chunk(chunk) {}

  template <ChunkFrames frame_kind, typename RegisterMapT>
  bool do_frame(const StackChunkFrameStream<frame_kind>& f, const RegisterMapT* map) {
    _chunk->do_barriers0<barrier>(f, map);
    return true;
  }
};

template <stackChunkOopDesc::BarrierType barrier>
void stackChunkOopDesc::do_barriers() {
  DoBarriersStackClosure<barrier> closure(this);
  iterate_stack(&closure);
}

template void stackChunkOopDesc::do_barriers<stackChunkOopDesc::BarrierType::Load> ();
template void stackChunkOopDesc::do_barriers<stackChunkOopDesc::BarrierType::Store>();

// We replace derived oops with offsets; the converse is done in DerelativizeDerivedOopClosure
class RelativizeDerivedOopClosure : public DerivedOopClosure {
public:
  virtual void do_derived_oop(oop* base_loc, derived_pointer* derived_loc) override {
    // The ordering in the following is crucial
    OrderAccess::loadload();

    // Read the base value once and use it for all calculations below
    oop base = Atomic::load((oop*)base_loc);
    if (base == nullptr) {
      return;
    }
    assert(!CompressedOops::is_base(base), "");

#if INCLUDE_ZGC
    if (UseZGC) {
      // This closure can be concurrently called from both GC marking and from
      // the mutator. For it to work it is important that the old base value is
      // used for the offset calculation below. As soon at the base oop has
      // been updated we must *not* proceed to run the code below this check.
      //
      // This code only works if there's only one possible transition from old
      // to good. This isn't necessarily for oops when objects are marked from
      // finalizers. In that case oops can go from old to finalizable_marked
      // to good. This breaks this check, because finalizable_marked are not
      // considered good, but it's also not the old value. To workaround this
      // we upgrade finalizable marking through stack chunks to be strong
      // marking. See: ZMark::follow_object.
      if (ZAddress::is_good(cast_from_oop<uintptr_t>(base))) {
        return;
      }
    }
#endif
#if INCLUDE_SHENANDOAHGC
    if (UseShenandoahGC) {
      if (!ShenandoahHeap::heap()->in_collection_set(base)) {
        return;
      }
    }
#endif

    OrderAccess::loadload();
    intptr_t derived_int_val = Atomic::load((intptr_t*)derived_loc);

    if (is_derived_oop_offset(derived_int_val)) {
      // The derived oop had already been converted to an offset.
      return;
    }

    // At this point, we've seen a non-offset value *after* we've
    // read the base, but we write the offset *before* fixing the base, so we
    // are guaranteed that the value in derived_loc is consistent with base.
    //
    // Note above how ZGC could be writing the base concurrently with the store
    // and how we handle it by checking the state of the read base oop.

    intptr_t offset = derived_int_val - cast_from_oop<intptr_t>(base);
    Atomic::store((intptr_t*)derived_loc, tag_derived_oop_offset(offset));
  }
};

template <ChunkFrames frame_kind, typename RegisterMapT>
static void relativize_derived_oops_in_frame(const StackChunkFrameStream<frame_kind>& f,
                                             const RegisterMapT* map) {
  assert(!f.is_compiled() || f.oopmap()->has_derived_oops() == f.oopmap()->has_any(OopMapValue::derived_oop_value), "");
  bool has_derived = f.is_compiled() && f.oopmap()->has_derived_oops();
  if (has_derived) {
    RelativizeDerivedOopClosure derived_closure;
    f.iterate_derived_pointers(&derived_closure, map);
  }
}

class RelativizeDerivedOopsStackChunkFrameClosure {
public:
  template <ChunkFrames frame_kind, typename RegisterMapT>
  bool do_frame(const StackChunkFrameStream<frame_kind>& f, const RegisterMapT* map) {
    relativize_derived_oops_in_frame(f, map);
    return true;
  }
};

void stackChunkOopDesc::relativize_derived_oops() {
  assert(!is_gc_mode(), "Should only be called once per chunk");
  set_gc_mode(true);
  OrderAccess::storestore();
  RelativizeDerivedOopsStackChunkFrameClosure closure;
  iterate_stack(&closure);

  // The store of the derived oops must be ordered with the store of the base.
  // RelativizeDerivedOopsStackChunkFrameClosure stored the derived oops,
  // the GC code calling this function will update the base oop.
  OrderAccess::storestore();
}

#ifdef ASSERT
template <ChunkFrames frame_kind, typename RegisterMapT>
static void assert_relativized_derived_oops_in_frame(const StackChunkFrameStream<frame_kind>& f,
                                                     const RegisterMapT* map) {
  class AssertRelativeDerivedOopClosure : public DerivedOopClosure {
  public:
    virtual void do_derived_oop(oop* base_loc, derived_pointer* derived_loc) override {
      assert(*base_loc == nullptr || is_derived_oop_offset(*(intptr_t*)derived_loc), "");
    }
  };
  assert(!f.is_compiled() || f.oopmap()->has_derived_oops() == f.oopmap()->has_any(OopMapValue::derived_oop_value), "");
  if (f.is_compiled() && f.oopmap()->has_derived_oops()) {
    AssertRelativeDerivedOopClosure derived_closure;
    f.iterate_derived_pointers(&derived_closure, map);
  }
}
#endif

enum class OopKind { Narrow, Wide };

template <OopKind kind>
class CompressOopsAndBuildBitmapOopClosure : public OopClosure {
  stackChunkOop _chunk;
  BitMapView _bm;

  void convert_oop_to_narrowOop(oop* p) {
    oop obj = *p;
    *p = nullptr;
    *(narrowOop*)p = CompressedOops::encode(obj);
  }

  template <typename T>
  void do_oop_work(T* p) {
    BitMap::idx_t index = _chunk->bit_index_for(p);
    assert(!_bm.at(index), "must not be set already");
    _bm.set_bit(index);
  }

public:
  CompressOopsAndBuildBitmapOopClosure(stackChunkOop chunk)
    : _chunk(chunk), _bm(chunk->bitmap()) {}

  virtual void do_oop(oop* p) override {
    if (kind == OopKind::Narrow) {
      // Convert all oops to narrow before marking the oop in the bitmap.
      convert_oop_to_narrowOop(p);
      do_oop_work((narrowOop*)p);
    } else {
      do_oop_work(p);
    }
  }

  virtual void do_oop(narrowOop* p) override {
    do_oop_work(p);
  }
};

template <OopKind kind>
class TransformStackChunkClosure {
  stackChunkOop _chunk;

public:
  TransformStackChunkClosure(stackChunkOop chunk) : _chunk(chunk) { }

  template <ChunkFrames frame_kind, typename RegisterMapT>
  bool do_frame(const StackChunkFrameStream<frame_kind>& f, const RegisterMapT* map) {
    relativize_derived_oops_in_frame(f, map);

    // This code is called from the STW collectors and don't have concurrent
    // access to the derived oops. Therefore there's no need to add a
    // storestore barrier here.
    assert(SafepointSynchronize::is_at_safepoint(), "Should only be used by STW collectors");

    CompressOopsAndBuildBitmapOopClosure<kind> cl(_chunk);
    f.iterate_oops(&cl, map);

    return true;
  }
};

void stackChunkOopDesc::transform() {
  assert(!is_gc_mode(), "Should only be called once per chunk");
  set_gc_mode(true);

  assert(!has_bitmap(), "Should only be set once");
  set_has_bitmap(true);
  bitmap().clear();

  if (UseCompressedOops) {
    TransformStackChunkClosure<OopKind::Narrow> closure(this);
    iterate_stack(&closure);
  } else {
    TransformStackChunkClosure<OopKind::Wide> closure(this);
    iterate_stack(&closure);
  }
}

template <stackChunkOopDesc::BarrierType barrier, bool compressedOopsWithBitmap>
class BarrierClosure: public OopClosure {
  NOT_PRODUCT(intptr_t* _sp;)

public:
  BarrierClosure(intptr_t* sp) NOT_PRODUCT(: _sp(sp)) {}

  virtual void do_oop(oop* p)       override { compressedOopsWithBitmap ? do_oop_work((narrowOop*)p) : do_oop_work(p); }
  virtual void do_oop(narrowOop* p) override { do_oop_work(p); }

  template <class T> inline void do_oop_work(T* p) {
    oop value = (oop)HeapAccess<>::oop_load(p);
    if (barrier == stackChunkOopDesc::BarrierType::Store) {
      HeapAccess<>::oop_store(p, value);
    }
  }
};

template <stackChunkOopDesc::BarrierType barrier, ChunkFrames frame_kind, typename RegisterMapT>
void stackChunkOopDesc::do_barriers0(const StackChunkFrameStream<frame_kind>& f, const RegisterMapT* map) {
  // We need to invoke the write barriers so as not to miss oops in old chunks that haven't yet been concurrently scanned
  if (f.is_done()) {
    return;
  }
  assert (!f.is_done(), "");

  if (f.is_interpreted()) {
    Method* m = f.to_frame().interpreter_frame_method();
    // Class redefinition support
    m->record_gc_epoch();
  } else if (f.is_compiled()) {
    nmethod* nm = f.cb()->as_nmethod();
    // The entry barrier takes care of having the right synchronization
    // when keeping the nmethod alive during concurrent execution.
    nm->run_nmethod_entry_barrier();
    // There is no need to mark the Method, as class redefinition will walk the
    // CodeCache, noting their Methods
  }

  if (UseZGC) {
    relativize_derived_oops_in_frame(f, map);
  } else {
    DEBUG_ONLY(assert_relativized_derived_oops_in_frame(f, map));
  }

  // The store of the derived oops must be ordered with the store of the base.
  // RelativizeDerivedOopsStackChunkFrameClosure stored the derived oops,
  // the GC code calling this function will update the base oop.
  OrderAccess::storestore();

  if (has_bitmap() && UseCompressedOops) {
    BarrierClosure<barrier, true> oops_closure(f.sp());
    f.iterate_oops(&oops_closure, map);
  } else {
    BarrierClosure<barrier, false> oops_closure(f.sp());
    f.iterate_oops(&oops_closure, map);
  }
}

template void stackChunkOopDesc::do_barriers0<stackChunkOopDesc::BarrierType::Load> (const StackChunkFrameStream<ChunkFrames::Mixed>& f, const RegisterMap* map);
template void stackChunkOopDesc::do_barriers0<stackChunkOopDesc::BarrierType::Store>(const StackChunkFrameStream<ChunkFrames::Mixed>& f, const RegisterMap* map);
template void stackChunkOopDesc::do_barriers0<stackChunkOopDesc::BarrierType::Load> (const StackChunkFrameStream<ChunkFrames::CompiledOnly>& f, const RegisterMap* map);
template void stackChunkOopDesc::do_barriers0<stackChunkOopDesc::BarrierType::Store>(const StackChunkFrameStream<ChunkFrames::CompiledOnly>& f, const RegisterMap* map);
template void stackChunkOopDesc::do_barriers0<stackChunkOopDesc::BarrierType::Load> (const StackChunkFrameStream<ChunkFrames::Mixed>& f, const SmallRegisterMap* map);
template void stackChunkOopDesc::do_barriers0<stackChunkOopDesc::BarrierType::Store>(const StackChunkFrameStream<ChunkFrames::Mixed>& f, const SmallRegisterMap* map);
template void stackChunkOopDesc::do_barriers0<stackChunkOopDesc::BarrierType::Load> (const StackChunkFrameStream<ChunkFrames::CompiledOnly>& f, const SmallRegisterMap* map);
template void stackChunkOopDesc::do_barriers0<stackChunkOopDesc::BarrierType::Store>(const StackChunkFrameStream<ChunkFrames::CompiledOnly>& f, const SmallRegisterMap* map);

class DerelativizeDerivedOopClosure : public DerivedOopClosure {
public:
  virtual void do_derived_oop(oop* base_loc, derived_pointer* derived_loc) override {
    oop base = *base_loc;
    if (base != nullptr) {
      assert(!CompressedOops::is_base(base), "");
      ZGC_ONLY(assert(ZAddress::is_good(cast_from_oop<uintptr_t>(base)), "");)

      intptr_t offset = *(intptr_t*)derived_loc;

      if (is_derived_oop_offset(offset)) {
        offset = untag_derived_oop_offset(offset);
        *(intptr_t*)derived_loc = cast_from_oop<intptr_t>(base) + offset;
      }
    }
  }
};

class UncompressOopsOopClosure : public OopClosure {
public:
  void do_oop(oop* p) override {
    assert(UseCompressedOops, "Only needed with compressed oops");
    oop obj = CompressedOops::decode(*(narrowOop*)p);
    assert(obj == nullptr || dbg_is_good_oop(obj), "p: " INTPTR_FORMAT " obj: " INTPTR_FORMAT, p2i(p), p2i((oopDesc*)obj));
    *p = obj;
  }

  void do_oop(narrowOop* p) override {}
};

template <typename RegisterMapT>
void stackChunkOopDesc::fix_thawed_frame(const frame& f, const RegisterMapT* map) {
  if (has_bitmap() && UseCompressedOops) {
    UncompressOopsOopClosure oop_closure;
    if (f.is_interpreted_frame()) {
      f.oops_interpreted_do(&oop_closure, nullptr);
    } else {
      OopMapDo<UncompressOopsOopClosure, DerivedOopClosure, SkipNullValue> visitor(&oop_closure, nullptr);
      visitor.oops_do(&f, map, f.oop_map());
    }
  }

  if ((is_gc_mode() || UseZGC) && f.is_compiled_frame() && f.oop_map()->has_derived_oops()) {
    OrderAccess::loadload();
    DerelativizeDerivedOopClosure derived_closure;
    OopMapDo<OopClosure, DerelativizeDerivedOopClosure, SkipNullValue> visitor(nullptr, &derived_closure);
    visitor.oops_do(&f, map, f.oop_map());
  }
}

template void stackChunkOopDesc::fix_thawed_frame(const frame& f, const RegisterMap* map);
template void stackChunkOopDesc::fix_thawed_frame(const frame& f, const SmallRegisterMap* map);

void stackChunkOopDesc::print_on(bool verbose, outputStream* st) const {
  if (*((juint*)this) == badHeapWordVal) {
    st->print("BAD WORD");
  } else if (*((juint*)this) == badMetaWordVal) {
    st->print("BAD META WORD");
  } else {
    InstanceStackChunkKlass::print_chunk(const_cast<stackChunkOopDesc*>(this), verbose, st);
  }
}

#ifdef ASSERT

template <typename P>
static inline oop safe_load(P* addr) {
  oop obj = RawAccess<>::oop_load(addr);
  return NativeAccess<>::oop_load(&obj);
}

class StackChunkVerifyOopsClosure : public OopClosure {
  stackChunkOop _chunk;
  int _count;

public:
  StackChunkVerifyOopsClosure(stackChunkOop chunk)
    : _chunk(chunk), _count(0) {}

  void do_oop(oop* p) override { (_chunk->has_bitmap() && UseCompressedOops) ? do_oop_work((narrowOop*)p) : do_oop_work(p); }
  void do_oop(narrowOop* p) override { do_oop_work(p); }

  template <typename T> inline void do_oop_work(T* p) {
     _count++;
    oop obj = safe_load(p);
    assert(obj == nullptr || dbg_is_good_oop(obj), "p: " INTPTR_FORMAT " obj: " INTPTR_FORMAT, p2i(p), p2i((oopDesc*)obj));
    if (_chunk->has_bitmap()) {
      BitMap::idx_t index = _chunk->bit_index_for(p);
      assert(_chunk->bitmap().at(index), "Bit not set at index " SIZE_FORMAT " corresponding to " INTPTR_FORMAT, index, p2i(p));
    }
  }

  int count() const { return _count; }
};

class StackChunkVerifyDerivedPointersClosure : public DerivedOopClosure {
  stackChunkOop _chunk;
  const bool    _requires_barriers;

public:
  StackChunkVerifyDerivedPointersClosure(stackChunkOop chunk)
    : _chunk(chunk),
      _requires_barriers(chunk->requires_barriers()) {}

  virtual void do_derived_oop(oop* base_loc, derived_pointer* derived_loc) override {
    oop base = (_chunk->has_bitmap() && UseCompressedOops)
                  ? CompressedOops::decode(Atomic::load((narrowOop*)base_loc))
                  : Atomic::load((oop*)base_loc);
    if (base == nullptr) {
      return;
    }

#if INCLUDE_ZGC
    if (UseZGC && !ZAddress::is_good(cast_from_oop<uintptr_t>(base))) {
      // If the base oops has not been fixed, other threads could be fixing
      // the derived oops concurrently with this code. Don't proceed with the
      // verification.
      return;
    }
#endif

    assert(!UseCompressedOops || !CompressedOops::is_base(base), "Should not be the compressed oops base");
    assert(oopDesc::is_oop(base), "Should be a valid oop");

    // Order the loads of the base oop and the derived oop
    OrderAccess::loadload();

    intptr_t value = Atomic::load((intptr_t*)derived_loc);
    if (UseZGC && _requires_barriers) {
      // For ZGC when the _chunk has transition to a state where the layout has
      // become stable(requires_barriers()), the earlier is_good check should
      // guarantee that all derived oops have been converted too offsets.
      assert(is_derived_oop_offset(value), "Unexpected non-offset value: " PTR_FORMAT, value);
    } else {
      if (is_derived_oop_offset(value)) {
        intptr_t offset = untag_derived_oop_offset(value); // for assertions
      } else {
        // The offset was a non-offset derived pointer that
        // had not been converted to an offset yet.
        intptr_t offset = value - cast_from_oop<intptr_t>(base);
        tag_derived_oop_offset(offset); // for assertions
      }
    }

    // There's not much else we can assert about derived pointers. Their offsets are allowed to
    // fall outside of their base object, and even be negative.
  }
};

class VerifyStackChunkFrameClosure {
  stackChunkOop _chunk;

public:
  intptr_t* _sp;
  CodeBlob* _cb;
  bool _callee_interpreted;
  int _size;
  int _argsize;
  int _num_oops;
  int _num_frames;
  int _num_interpreted_frames;
  int _num_i2c;

  VerifyStackChunkFrameClosure(stackChunkOop chunk, int num_frames, int size)
    : _chunk(chunk), _sp(nullptr), _cb(nullptr), _callee_interpreted(false),
      _size(size), _argsize(0), _num_oops(0), _num_frames(num_frames), _num_interpreted_frames(0), _num_i2c(0) {}

  template <ChunkFrames frame_kind, typename RegisterMapT>
  bool do_frame(const StackChunkFrameStream<frame_kind>& f, const RegisterMapT* map) {
    _sp = f.sp();
    _cb = f.cb();

    int fsize = f.frame_size() - ((f.is_interpreted() == _callee_interpreted) ? _argsize : 0);
    int num_oops = f.num_oops();
    assert(num_oops >= 0, "");

    _argsize   = f.stack_argsize();
    _size     += fsize;
    _num_oops += num_oops;
    if (f.is_interpreted()) {
      _num_interpreted_frames++;
    }

    log_develop_trace(continuations)("debug_verify_stack_chunk frame: %d sp: " INTPTR_FORMAT " pc: " INTPTR_FORMAT " interpreted: %d size: %d argsize: %d oops: %d", _num_frames, f.sp() - _chunk->start_address(), p2i(f.pc()), f.is_interpreted(), fsize, _argsize, num_oops);
    LogTarget(Trace, continuations) lt;
    if (lt.develop_is_enabled()) {
      LogStream ls(lt);
      f.print_value_on(&ls);
    }
    assert(f.pc() != nullptr,
           "young: %d num_frames: %d sp: " INTPTR_FORMAT " start: " INTPTR_FORMAT " end: " INTPTR_FORMAT,
           !_chunk->requires_barriers(), _num_frames, p2i(f.sp()), p2i(_chunk->start_address()), p2i(_chunk->bottom_address()));

    if (_num_frames == 0) {
      assert(f.pc() == _chunk->pc(), "");
    }

    if (_num_frames > 0 && !_callee_interpreted && f.is_interpreted()) {
      log_develop_trace(continuations)("debug_verify_stack_chunk i2c");
      _num_i2c++;
    }

    StackChunkVerifyOopsClosure oops_closure(_chunk);
    f.iterate_oops(&oops_closure, map);
    assert(oops_closure.count() == num_oops, "oops: %d oopmap->num_oops(): %d", oops_closure.count(), num_oops);

    StackChunkVerifyDerivedPointersClosure derived_oops_closure(_chunk);
    f.iterate_derived_pointers(&derived_oops_closure, map);

    _callee_interpreted = f.is_interpreted();
    _num_frames++;
    return true;
  }
};

template <typename T>
class StackChunkVerifyBitmapClosure : public BitMapClosure {
  stackChunkOop _chunk;

public:
  int _count;

  StackChunkVerifyBitmapClosure(stackChunkOop chunk) : _chunk(chunk), _count(0) {}

  bool do_bit(BitMap::idx_t index) override {
    T* p = _chunk->address_for_bit<T>(index);
    _count++;

    oop obj = safe_load(p);
    assert(obj == nullptr || dbg_is_good_oop(obj),
           "p: " INTPTR_FORMAT " obj: " INTPTR_FORMAT " index: " SIZE_FORMAT,
           p2i(p), p2i((oopDesc*)obj), index);

    return true; // continue processing
  }
};

bool stackChunkOopDesc::verify(size_t* out_size, int* out_oops, int* out_frames, int* out_interpreted_frames) {
  DEBUG_ONLY(if (!VerifyContinuations) return true;)

  assert(oopDesc::is_oop(this), "");

  assert(stack_size() >= 0, "");
  assert(argsize() >= 0, "");
  assert(!has_bitmap() || is_gc_mode(), "");

  if (is_empty()) {
    assert(argsize() == 0, "");
    assert(max_size() == 0, "");
  }

  assert(oopDesc::is_oop_or_null(parent()), "");

  const bool concurrent = !Thread::current()->is_Java_thread();

  // If argsize == 0 and the chunk isn't mixed, the chunk contains the metadata (pc, fp -- frame::sender_sp_offset)
  // for the top frame (below sp), and *not* for the bottom frame.
  int size = stack_size() - argsize() - sp();
  assert(size >= 0, "");
  assert((size == 0) == is_empty(), "");

  const StackChunkFrameStream<ChunkFrames::Mixed> first(this);
  const bool has_safepoint_stub_frame = first.is_stub();

  VerifyStackChunkFrameClosure closure(this,
                                       has_safepoint_stub_frame ? 1 : 0, // Iterate_stack skips the safepoint stub
                                       has_safepoint_stub_frame ? first.frame_size() : 0);
  iterate_stack(&closure);

  assert(!is_empty() || closure._cb == nullptr, "");
  if (closure._cb != nullptr && closure._cb->is_compiled()) {
    assert(argsize() ==
      (closure._cb->as_compiled_method()->method()->num_stack_arg_slots()*VMRegImpl::stack_slot_size) >>LogBytesPerWord,
      "chunk argsize: %d bottom frame argsize: %d", argsize(),
      (closure._cb->as_compiled_method()->method()->num_stack_arg_slots()*VMRegImpl::stack_slot_size) >>LogBytesPerWord);
  }

  assert(closure._num_interpreted_frames == 0 || has_mixed_frames(), "");

  if (!concurrent) {
    assert(closure._size <= size + argsize() + frame::metadata_words,
           "size: %d argsize: %d closure.size: %d end sp: " PTR_FORMAT " start sp: %d chunk size: %d",
           size, argsize(), closure._size, closure._sp - start_address(), sp(), stack_size());
    assert(argsize() == closure._argsize,
           "argsize(): %d closure.argsize: %d closure.callee_interpreted: %d",
           argsize(), closure._argsize, closure._callee_interpreted);

    int calculated_max_size = closure._size
                              + closure._num_i2c * frame::align_wiggle
                              + closure._num_interpreted_frames * frame::align_wiggle;
    assert(max_size() == calculated_max_size,
           "max_size(): %d calculated_max_size: %d argsize: %d num_i2c: %d",
           max_size(), calculated_max_size, closure._argsize, closure._num_i2c);

    if (out_size   != nullptr) *out_size   += size;
    if (out_oops   != nullptr) *out_oops   += closure._num_oops;
    if (out_frames != nullptr) *out_frames += closure._num_frames;
    if (out_interpreted_frames != nullptr) *out_interpreted_frames += closure._num_interpreted_frames;
  } else {
    assert(out_size == nullptr, "");
    assert(out_oops == nullptr, "");
    assert(out_frames == nullptr, "");
    assert(out_interpreted_frames == nullptr, "");
  }

  if (has_bitmap()) {
    assert(bitmap().size() == InstanceStackChunkKlass::bitmap_size_in_bits(stack_size()),
           "bitmap().size(): %zu stack_size: %d",
           bitmap().size(), stack_size());

    int oop_count;
    if (UseCompressedOops) {
      StackChunkVerifyBitmapClosure<narrowOop> bitmap_closure(this);
      bitmap().iterate(&bitmap_closure,
                       bit_index_for((narrowOop*)(sp_address() - frame::metadata_words)),
                       bit_index_for((narrowOop*)end_address()));
      oop_count = bitmap_closure._count;
    } else {
      StackChunkVerifyBitmapClosure<oop> bitmap_closure(this);
      bitmap().iterate(&bitmap_closure,
                       bit_index_for((oop*)(sp_address() - frame::metadata_words)),
                       bit_index_for((oop*)end_address()));
      oop_count = bitmap_closure._count;
    }
    assert(oop_count == closure._num_oops,
           "bitmap_closure._count: %d closure._num_oops: %d", oop_count, closure._num_oops);
  }

  return true;
}
#endif
