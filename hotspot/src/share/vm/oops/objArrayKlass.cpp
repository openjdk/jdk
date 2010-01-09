/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_objArrayKlass.cpp.incl"

int objArrayKlass::oop_size(oop obj) const {
  assert(obj->is_objArray(), "must be object array");
  return objArrayOop(obj)->object_size();
}

objArrayOop objArrayKlass::allocate(int length, TRAPS) {
  if (length >= 0) {
    if (length <= arrayOopDesc::max_array_length(T_OBJECT)) {
      int size = objArrayOopDesc::object_size(length);
      KlassHandle h_k(THREAD, as_klassOop());
      objArrayOop a = (objArrayOop)CollectedHeap::array_allocate(h_k, size, length, CHECK_NULL);
      assert(a->is_parsable(), "Can't publish unless parsable");
      return a;
    } else {
      report_java_out_of_memory("Requested array size exceeds VM limit");
      THROW_OOP_0(Universe::out_of_memory_error_array_size());
    }
  } else {
    THROW_0(vmSymbols::java_lang_NegativeArraySizeException());
  }
}

static int multi_alloc_counter = 0;

oop objArrayKlass::multi_allocate(int rank, jint* sizes, TRAPS) {
  int length = *sizes;
  // Call to lower_dimension uses this pointer, so most be called before a
  // possible GC
  KlassHandle h_lower_dimension(THREAD, lower_dimension());
  // If length < 0 allocate will throw an exception.
  objArrayOop array = allocate(length, CHECK_NULL);
  assert(array->is_parsable(), "Don't handlize unless parsable");
  objArrayHandle h_array (THREAD, array);
  if (rank > 1) {
    if (length != 0) {
      for (int index = 0; index < length; index++) {
        arrayKlass* ak = arrayKlass::cast(h_lower_dimension());
        oop sub_array = ak->multi_allocate(rank-1, &sizes[1], CHECK_NULL);
        assert(sub_array->is_parsable(), "Don't publish until parsable");
        h_array->obj_at_put(index, sub_array);
      }
    } else {
      // Since this array dimension has zero length, nothing will be
      // allocated, however the lower dimension values must be checked
      // for illegal values.
      for (int i = 0; i < rank - 1; ++i) {
        sizes += 1;
        if (*sizes < 0) {
          THROW_0(vmSymbols::java_lang_NegativeArraySizeException());
        }
      }
    }
  }
  return h_array();
}

// Either oop or narrowOop depending on UseCompressedOops.
template <class T> void objArrayKlass::do_copy(arrayOop s, T* src,
                               arrayOop d, T* dst, int length, TRAPS) {

  BarrierSet* bs = Universe::heap()->barrier_set();
  // For performance reasons, we assume we are that the write barrier we
  // are using has optimized modes for arrays of references.  At least one
  // of the asserts below will fail if this is not the case.
  assert(bs->has_write_ref_array_opt(), "Barrier set must have ref array opt");
  assert(bs->has_write_ref_array_pre_opt(), "For pre-barrier as well.");

  if (s == d) {
    // since source and destination are equal we do not need conversion checks.
    assert(length > 0, "sanity check");
    bs->write_ref_array_pre(dst, length);
    Copy::conjoint_oops_atomic(src, dst, length);
  } else {
    // We have to make sure all elements conform to the destination array
    klassOop bound = objArrayKlass::cast(d->klass())->element_klass();
    klassOop stype = objArrayKlass::cast(s->klass())->element_klass();
    if (stype == bound || Klass::cast(stype)->is_subtype_of(bound)) {
      // elements are guaranteed to be subtypes, so no check necessary
      bs->write_ref_array_pre(dst, length);
      Copy::conjoint_oops_atomic(src, dst, length);
    } else {
      // slow case: need individual subtype checks
      // note: don't use obj_at_put below because it includes a redundant store check
      T* from = src;
      T* end = from + length;
      for (T* p = dst; from < end; from++, p++) {
        // XXX this is going to be slow.
        T element = *from;
        // even slower now
        bool element_is_null = oopDesc::is_null(element);
        oop new_val = element_is_null ? oop(NULL)
                                      : oopDesc::decode_heap_oop_not_null(element);
        if (element_is_null ||
            Klass::cast((new_val->klass()))->is_subtype_of(bound)) {
          bs->write_ref_field_pre(p, new_val);
          *p = *from;
        } else {
          // We must do a barrier to cover the partial copy.
          const size_t pd = pointer_delta(p, dst, (size_t)heapOopSize);
          // pointer delta is scaled to number of elements (length field in
          // objArrayOop) which we assume is 32 bit.
          assert(pd == (size_t)(int)pd, "length field overflow");
          bs->write_ref_array((HeapWord*)dst, pd);
          THROW(vmSymbols::java_lang_ArrayStoreException());
          return;
        }
      }
    }
  }
  bs->write_ref_array((HeapWord*)dst, length);
}

void objArrayKlass::copy_array(arrayOop s, int src_pos, arrayOop d,
                               int dst_pos, int length, TRAPS) {
  assert(s->is_objArray(), "must be obj array");

  if (!d->is_objArray()) {
    THROW(vmSymbols::java_lang_ArrayStoreException());
  }

  // Check is all offsets and lengths are non negative
  if (src_pos < 0 || dst_pos < 0 || length < 0) {
    THROW(vmSymbols::java_lang_ArrayIndexOutOfBoundsException());
  }
  // Check if the ranges are valid
  if  ( (((unsigned int) length + (unsigned int) src_pos) > (unsigned int) s->length())
     || (((unsigned int) length + (unsigned int) dst_pos) > (unsigned int) d->length()) ) {
    THROW(vmSymbols::java_lang_ArrayIndexOutOfBoundsException());
  }

  // Special case. Boundary cases must be checked first
  // This allows the following call: copy_array(s, s.length(), d.length(), 0).
  // This is correct, since the position is supposed to be an 'in between point', i.e., s.length(),
  // points to the right of the last element.
  if (length==0) {
    return;
  }
  if (UseCompressedOops) {
    narrowOop* const src = objArrayOop(s)->obj_at_addr<narrowOop>(src_pos);
    narrowOop* const dst = objArrayOop(d)->obj_at_addr<narrowOop>(dst_pos);
    do_copy<narrowOop>(s, src, d, dst, length, CHECK);
  } else {
    oop* const src = objArrayOop(s)->obj_at_addr<oop>(src_pos);
    oop* const dst = objArrayOop(d)->obj_at_addr<oop>(dst_pos);
    do_copy<oop> (s, src, d, dst, length, CHECK);
  }
}


klassOop objArrayKlass::array_klass_impl(bool or_null, int n, TRAPS) {
  objArrayKlassHandle h_this(THREAD, as_klassOop());
  return array_klass_impl(h_this, or_null, n, CHECK_NULL);
}


klassOop objArrayKlass::array_klass_impl(objArrayKlassHandle this_oop, bool or_null, int n, TRAPS) {

  assert(this_oop->dimension() <= n, "check order of chain");
  int dimension = this_oop->dimension();
  if (dimension == n)
    return this_oop();

  objArrayKlassHandle ak (THREAD, this_oop->higher_dimension());
  if (ak.is_null()) {
    if (or_null)  return NULL;

    ResourceMark rm;
    JavaThread *jt = (JavaThread *)THREAD;
    {
      MutexLocker mc(Compile_lock, THREAD);   // for vtables
      // Ensure atomic creation of higher dimensions
      MutexLocker mu(MultiArray_lock, THREAD);

      // Check if another thread beat us
      ak = objArrayKlassHandle(THREAD, this_oop->higher_dimension());
      if( ak.is_null() ) {

        // Create multi-dim klass object and link them together
        klassOop new_klass =
          objArrayKlassKlass::cast(Universe::objArrayKlassKlassObj())->
          allocate_objArray_klass(dimension + 1, this_oop, CHECK_NULL);
        ak = objArrayKlassHandle(THREAD, new_klass);
        this_oop->set_higher_dimension(ak());
        ak->set_lower_dimension(this_oop());
        assert(ak->oop_is_objArray(), "incorrect initialization of objArrayKlass");
      }
    }
  } else {
    CHECK_UNHANDLED_OOPS_ONLY(Thread::current()->clear_unhandled_oops());
  }

  if (or_null) {
    return ak->array_klass_or_null(n);
  }
  return ak->array_klass(n, CHECK_NULL);
}

klassOop objArrayKlass::array_klass_impl(bool or_null, TRAPS) {
  return array_klass_impl(or_null, dimension() +  1, CHECK_NULL);
}

bool objArrayKlass::can_be_primary_super_slow() const {
  if (!bottom_klass()->klass_part()->can_be_primary_super())
    // array of interfaces
    return false;
  else
    return Klass::can_be_primary_super_slow();
}

objArrayOop objArrayKlass::compute_secondary_supers(int num_extra_slots, TRAPS) {
  // interfaces = { cloneable_klass, serializable_klass, elemSuper[], ... };
  objArrayOop es = Klass::cast(element_klass())->secondary_supers();
  objArrayHandle elem_supers (THREAD, es);
  int num_elem_supers = elem_supers.is_null() ? 0 : elem_supers->length();
  int num_secondaries = num_extra_slots + 2 + num_elem_supers;
  if (num_secondaries == 2) {
    // Must share this for correct bootstrapping!
    return Universe::the_array_interfaces_array();
  } else {
    objArrayOop sec_oop = oopFactory::new_system_objArray(num_secondaries, CHECK_NULL);
    objArrayHandle secondaries(THREAD, sec_oop);
    secondaries->obj_at_put(num_extra_slots+0, SystemDictionary::Cloneable_klass());
    secondaries->obj_at_put(num_extra_slots+1, SystemDictionary::Serializable_klass());
    for (int i = 0; i < num_elem_supers; i++) {
      klassOop elem_super = (klassOop) elem_supers->obj_at(i);
      klassOop array_super = elem_super->klass_part()->array_klass_or_null();
      assert(array_super != NULL, "must already have been created");
      secondaries->obj_at_put(num_extra_slots+2+i, array_super);
    }
    return secondaries();
  }
}

bool objArrayKlass::compute_is_subtype_of(klassOop k) {
  if (!k->klass_part()->oop_is_objArray())
    return arrayKlass::compute_is_subtype_of(k);

  objArrayKlass* oak = objArrayKlass::cast(k);
  return element_klass()->klass_part()->is_subtype_of(oak->element_klass());
}

void objArrayKlass::initialize(TRAPS) {
  Klass::cast(bottom_klass())->initialize(THREAD);  // dispatches to either instanceKlass or typeArrayKlass
}

#define ObjArrayKlass_SPECIALIZED_OOP_ITERATE(T, a, p, do_oop) \
{                                   \
  T* p         = (T*)(a)->base();   \
  T* const end = p + (a)->length(); \
  while (p < end) {                 \
    do_oop;                         \
    p++;                            \
  }                                 \
}

#define ObjArrayKlass_SPECIALIZED_BOUNDED_OOP_ITERATE(T, a, p, low, high, do_oop) \
{                                   \
  T* const l = (T*)(low);           \
  T* const h = (T*)(high);          \
  T* p       = (T*)(a)->base();     \
  T* end     = p + (a)->length();   \
  if (p < l) p = l;                 \
  if (end > h) end = h;             \
  while (p < end) {                 \
    do_oop;                         \
    ++p;                            \
  }                                 \
}

#define ObjArrayKlass_OOP_ITERATE(a, p, do_oop)      \
  if (UseCompressedOops) {                           \
    ObjArrayKlass_SPECIALIZED_OOP_ITERATE(narrowOop, \
      a, p, do_oop)                                  \
  } else {                                           \
    ObjArrayKlass_SPECIALIZED_OOP_ITERATE(oop,       \
      a, p, do_oop)                                  \
  }

#define ObjArrayKlass_BOUNDED_OOP_ITERATE(a, p, low, high, do_oop) \
  if (UseCompressedOops) {                                   \
    ObjArrayKlass_SPECIALIZED_BOUNDED_OOP_ITERATE(narrowOop, \
      a, p, low, high, do_oop)                               \
  } else {                                                   \
    ObjArrayKlass_SPECIALIZED_BOUNDED_OOP_ITERATE(oop,       \
      a, p, low, high, do_oop)                               \
  }

void objArrayKlass::oop_follow_contents(oop obj) {
  assert (obj->is_array(), "obj must be array");
  objArrayOop a = objArrayOop(obj);
  a->follow_header();
  ObjArrayKlass_OOP_ITERATE( \
    a, p, \
    /* we call mark_and_follow here to avoid excessive marking stack usage */ \
    MarkSweep::mark_and_follow(p))
}

#ifndef SERIALGC
void objArrayKlass::oop_follow_contents(ParCompactionManager* cm,
                                        oop obj) {
  assert (obj->is_array(), "obj must be array");
  objArrayOop a = objArrayOop(obj);
  a->follow_header(cm);
  ObjArrayKlass_OOP_ITERATE( \
    a, p, \
    /* we call mark_and_follow here to avoid excessive marking stack usage */ \
    PSParallelCompact::mark_and_follow(cm, p))
}
#endif // SERIALGC

#define ObjArrayKlass_OOP_OOP_ITERATE_DEFN(OopClosureType, nv_suffix)           \
                                                                                \
int objArrayKlass::oop_oop_iterate##nv_suffix(oop obj,                          \
                                              OopClosureType* closure) {        \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::oa); \
  assert (obj->is_array(), "obj must be array");                                \
  objArrayOop a = objArrayOop(obj);                                             \
  /* Get size before changing pointers. */                                      \
  /* Don't call size() or oop_size() since that is a virtual call. */           \
  int size = a->object_size();                                                  \
  if (closure->do_header()) {                                                   \
    a->oop_iterate_header(closure);                                             \
  }                                                                             \
  ObjArrayKlass_OOP_ITERATE(a, p, (closure)->do_oop##nv_suffix(p))              \
  return size;                                                                  \
}

#define ObjArrayKlass_OOP_OOP_ITERATE_DEFN_m(OopClosureType, nv_suffix)         \
                                                                                \
int objArrayKlass::oop_oop_iterate##nv_suffix##_m(oop obj,                      \
                                                  OopClosureType* closure,      \
                                                  MemRegion mr) {               \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::oa); \
  assert(obj->is_array(), "obj must be array");                                 \
  objArrayOop a  = objArrayOop(obj);                                            \
  /* Get size before changing pointers. */                                      \
  /* Don't call size() or oop_size() since that is a virtual call */            \
  int size = a->object_size();                                                  \
  if (closure->do_header()) {                                                   \
    a->oop_iterate_header(closure, mr);                                         \
  }                                                                             \
  ObjArrayKlass_BOUNDED_OOP_ITERATE(                                            \
    a, p, mr.start(), mr.end(), (closure)->do_oop##nv_suffix(p))                \
  return size;                                                                  \
}

// Like oop_oop_iterate but only iterates over a specified range and only used
// for objArrayOops.
#define ObjArrayKlass_OOP_OOP_ITERATE_DEFN_r(OopClosureType, nv_suffix)         \
                                                                                \
int objArrayKlass::oop_oop_iterate_range##nv_suffix(oop obj,                    \
                                                  OopClosureType* closure,      \
                                                  int start, int end) {         \
  SpecializationStats::record_iterate_call##nv_suffix(SpecializationStats::oa); \
  assert(obj->is_array(), "obj must be array");                                 \
  objArrayOop a  = objArrayOop(obj);                                            \
  /* Get size before changing pointers. */                                      \
  /* Don't call size() or oop_size() since that is a virtual call */            \
  int size = a->object_size();                                                  \
  if (UseCompressedOops) {                                                      \
    HeapWord* low = start == 0 ? (HeapWord*)a : (HeapWord*)a->obj_at_addr<narrowOop>(start);\
    /* this might be wierd if end needs to be aligned on HeapWord boundary */   \
    HeapWord* high = (HeapWord*)((narrowOop*)a->base() + end);                  \
    MemRegion mr(low, high);                                                    \
    if (closure->do_header()) {                                                 \
      a->oop_iterate_header(closure, mr);                                       \
    }                                                                           \
    ObjArrayKlass_SPECIALIZED_BOUNDED_OOP_ITERATE(narrowOop,                    \
      a, p, low, high, (closure)->do_oop##nv_suffix(p))                         \
  } else {                                                                      \
    HeapWord* low = start == 0 ? (HeapWord*)a : (HeapWord*)a->obj_at_addr<oop>(start);  \
    HeapWord* high = (HeapWord*)((oop*)a->base() + end);                        \
    MemRegion mr(low, high);                                                    \
    if (closure->do_header()) {                                                 \
      a->oop_iterate_header(closure, mr);                                       \
    }                                                                           \
    ObjArrayKlass_SPECIALIZED_BOUNDED_OOP_ITERATE(oop,                          \
      a, p, low, high, (closure)->do_oop##nv_suffix(p))                         \
  }                                                                             \
  return size;                                                                  \
}

ALL_OOP_OOP_ITERATE_CLOSURES_1(ObjArrayKlass_OOP_OOP_ITERATE_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_2(ObjArrayKlass_OOP_OOP_ITERATE_DEFN)
ALL_OOP_OOP_ITERATE_CLOSURES_1(ObjArrayKlass_OOP_OOP_ITERATE_DEFN_m)
ALL_OOP_OOP_ITERATE_CLOSURES_2(ObjArrayKlass_OOP_OOP_ITERATE_DEFN_m)
ALL_OOP_OOP_ITERATE_CLOSURES_1(ObjArrayKlass_OOP_OOP_ITERATE_DEFN_r)
ALL_OOP_OOP_ITERATE_CLOSURES_2(ObjArrayKlass_OOP_OOP_ITERATE_DEFN_r)

int objArrayKlass::oop_adjust_pointers(oop obj) {
  assert(obj->is_objArray(), "obj must be obj array");
  objArrayOop a = objArrayOop(obj);
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = a->object_size();
  a->adjust_header();
  ObjArrayKlass_OOP_ITERATE(a, p, MarkSweep::adjust_pointer(p))
  return size;
}

#ifndef SERIALGC
void objArrayKlass::oop_copy_contents(PSPromotionManager* pm, oop obj) {
  assert(!pm->depth_first(), "invariant");
  assert(obj->is_objArray(), "obj must be obj array");
  ObjArrayKlass_OOP_ITERATE( \
    objArrayOop(obj), p, \
    if (PSScavenge::should_scavenge(p)) { \
      pm->claim_or_forward_breadth(p); \
    })
}

void objArrayKlass::oop_push_contents(PSPromotionManager* pm, oop obj) {
  assert(pm->depth_first(), "invariant");
  assert(obj->is_objArray(), "obj must be obj array");
  ObjArrayKlass_OOP_ITERATE( \
    objArrayOop(obj), p, \
    if (PSScavenge::should_scavenge(p)) { \
      pm->claim_or_forward_depth(p); \
    })
}

int objArrayKlass::oop_update_pointers(ParCompactionManager* cm, oop obj) {
  assert (obj->is_objArray(), "obj must be obj array");
  objArrayOop a = objArrayOop(obj);
  ObjArrayKlass_OOP_ITERATE(a, p, PSParallelCompact::adjust_pointer(p))
  return a->object_size();
}

int objArrayKlass::oop_update_pointers(ParCompactionManager* cm, oop obj,
                                       HeapWord* beg_addr, HeapWord* end_addr) {
  assert (obj->is_objArray(), "obj must be obj array");
  objArrayOop a = objArrayOop(obj);
  ObjArrayKlass_BOUNDED_OOP_ITERATE( \
     a, p, beg_addr, end_addr, \
     PSParallelCompact::adjust_pointer(p))
  return a->object_size();
}
#endif // SERIALGC

// JVM support

jint objArrayKlass::compute_modifier_flags(TRAPS) const {
  // The modifier for an objectArray is the same as its element
  if (element_klass() == NULL) {
    assert(Universe::is_bootstrapping(), "partial objArray only at startup");
    return JVM_ACC_ABSTRACT | JVM_ACC_FINAL | JVM_ACC_PUBLIC;
  }
  // Return the flags of the bottom element type.
  jint element_flags = Klass::cast(bottom_klass())->compute_modifier_flags(CHECK_0);

  return (element_flags & (JVM_ACC_PUBLIC | JVM_ACC_PRIVATE | JVM_ACC_PROTECTED))
                        | (JVM_ACC_ABSTRACT | JVM_ACC_FINAL);
}


#ifndef PRODUCT
// Printing

void objArrayKlass::oop_print_on(oop obj, outputStream* st) {
  arrayKlass::oop_print_on(obj, st);
  assert(obj->is_objArray(), "must be objArray");
  objArrayOop oa = objArrayOop(obj);
  int print_len = MIN2((intx) oa->length(), MaxElementPrintSize);
  for(int index = 0; index < print_len; index++) {
    st->print(" - %3d : ", index);
    oa->obj_at(index)->print_value_on(st);
    st->cr();
  }
  int remaining = oa->length() - print_len;
  if (remaining > 0) {
    tty->print_cr(" - <%d more elements, increase MaxElementPrintSize to print>", remaining);
  }
}

static int max_objArray_print_length = 4;

void objArrayKlass::oop_print_value_on(oop obj, outputStream* st) {
  assert(obj->is_objArray(), "must be objArray");
  st->print("a ");
  element_klass()->print_value_on(st);
  int len = objArrayOop(obj)->length();
  st->print("[%d] ", len);
  obj->print_address_on(st);
  if (PrintOopAddress || PrintMiscellaneous && (WizardMode || Verbose)) {
    st->print("{");
    for (int i = 0; i < len; i++) {
      if (i > max_objArray_print_length) {
        st->print("..."); break;
      }
      st->print(" "INTPTR_FORMAT, (intptr_t)(void*)objArrayOop(obj)->obj_at(i));
    }
    st->print(" }");
  }
}

#endif // PRODUCT

const char* objArrayKlass::internal_name() const {
  return external_name();
}

// Verification

void objArrayKlass::oop_verify_on(oop obj, outputStream* st) {
  arrayKlass::oop_verify_on(obj, st);
  guarantee(obj->is_objArray(), "must be objArray");
  objArrayOop oa = objArrayOop(obj);
  for(int index = 0; index < oa->length(); index++) {
    guarantee(oa->obj_at(index)->is_oop_or_null(), "should be oop");
  }
}

void objArrayKlass::oop_verify_old_oop(oop obj, oop* p, bool allow_dirty) {
  /* $$$ move into remembered set verification?
  RememberedSet::verify_old_oop(obj, p, allow_dirty, true);
  */
}
void objArrayKlass::oop_verify_old_oop(oop obj, narrowOop* p, bool allow_dirty) {}
