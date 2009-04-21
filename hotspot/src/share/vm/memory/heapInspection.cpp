/*
 * Copyright 2002-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# include "incls/_heapInspection.cpp.incl"

// HeapInspection

int KlassInfoEntry::compare(KlassInfoEntry* e1, KlassInfoEntry* e2) {
  if(e1->_instance_words > e2->_instance_words) {
    return -1;
  } else if(e1->_instance_words < e2->_instance_words) {
    return 1;
  }
  return 0;
}

void KlassInfoEntry::print_on(outputStream* st) const {
  ResourceMark rm;
  const char* name;;
  if (_klass->klass_part()->name() != NULL) {
    name = _klass->klass_part()->external_name();
  } else {
    if (_klass == Universe::klassKlassObj())             name = "<klassKlass>";             else
    if (_klass == Universe::arrayKlassKlassObj())        name = "<arrayKlassKlass>";        else
    if (_klass == Universe::objArrayKlassKlassObj())     name = "<objArrayKlassKlass>";     else
    if (_klass == Universe::instanceKlassKlassObj())     name = "<instanceKlassKlass>";     else
    if (_klass == Universe::typeArrayKlassKlassObj())    name = "<typeArrayKlassKlass>";    else
    if (_klass == Universe::symbolKlassObj())            name = "<symbolKlass>";            else
    if (_klass == Universe::boolArrayKlassObj())         name = "<boolArrayKlass>";         else
    if (_klass == Universe::charArrayKlassObj())         name = "<charArrayKlass>";         else
    if (_klass == Universe::singleArrayKlassObj())       name = "<singleArrayKlass>";       else
    if (_klass == Universe::doubleArrayKlassObj())       name = "<doubleArrayKlass>";       else
    if (_klass == Universe::byteArrayKlassObj())         name = "<byteArrayKlass>";         else
    if (_klass == Universe::shortArrayKlassObj())        name = "<shortArrayKlass>";        else
    if (_klass == Universe::intArrayKlassObj())          name = "<intArrayKlass>";          else
    if (_klass == Universe::longArrayKlassObj())         name = "<longArrayKlass>";         else
    if (_klass == Universe::methodKlassObj())            name = "<methodKlass>";            else
    if (_klass == Universe::constMethodKlassObj())       name = "<constMethodKlass>";       else
    if (_klass == Universe::methodDataKlassObj())        name = "<methodDataKlass>";        else
    if (_klass == Universe::constantPoolKlassObj())      name = "<constantPoolKlass>";      else
    if (_klass == Universe::constantPoolCacheKlassObj()) name = "<constantPoolCacheKlass>"; else
    if (_klass == Universe::compiledICHolderKlassObj())  name = "<compiledICHolderKlass>";  else
      name = "<no name>";
  }
  // simplify the formatting (ILP32 vs LP64) - always cast the numbers to 64-bit
  st->print_cr(INT64_FORMAT_W(13) "  " UINT64_FORMAT_W(13) "  %s",
               (jlong)  _instance_count,
               (julong) _instance_words * HeapWordSize,
               name);
}

KlassInfoEntry* KlassInfoBucket::lookup(const klassOop k) {
  KlassInfoEntry* elt = _list;
  while (elt != NULL) {
    if (elt->is_equal(k)) {
      return elt;
    }
    elt = elt->next();
  }
  elt = new KlassInfoEntry(k, list());
  // We may be out of space to allocate the new entry.
  if (elt != NULL) {
    set_list(elt);
  }
  return elt;
}

void KlassInfoBucket::iterate(KlassInfoClosure* cic) {
  KlassInfoEntry* elt = _list;
  while (elt != NULL) {
    cic->do_cinfo(elt);
    elt = elt->next();
  }
}

void KlassInfoBucket::empty() {
  KlassInfoEntry* elt = _list;
  _list = NULL;
  while (elt != NULL) {
    KlassInfoEntry* next = elt->next();
    delete elt;
    elt = next;
  }
}

KlassInfoTable::KlassInfoTable(int size, HeapWord* ref) {
  _size = 0;
  _ref = ref;
  _buckets = NEW_C_HEAP_ARRAY(KlassInfoBucket, size);
  if (_buckets != NULL) {
    _size = size;
    for (int index = 0; index < _size; index++) {
      _buckets[index].initialize();
    }
  }
}

KlassInfoTable::~KlassInfoTable() {
  if (_buckets != NULL) {
    for (int index = 0; index < _size; index++) {
      _buckets[index].empty();
    }
    FREE_C_HEAP_ARRAY(KlassInfoBucket, _buckets);
    _size = 0;
  }
}

uint KlassInfoTable::hash(klassOop p) {
  assert(Universe::heap()->is_in_permanent((HeapWord*)p), "all klasses in permgen");
  return (uint)(((uintptr_t)p - (uintptr_t)_ref) >> 2);
}

KlassInfoEntry* KlassInfoTable::lookup(const klassOop k) {
  uint         idx = hash(k) % _size;
  assert(_buckets != NULL, "Allocation failure should have been caught");
  KlassInfoEntry*  e   = _buckets[idx].lookup(k);
  // Lookup may fail if this is a new klass for which we
  // could not allocate space for an new entry.
  assert(e == NULL || k == e->klass(), "must be equal");
  return e;
}

// Return false if the entry could not be recorded on account
// of running out of space required to create a new entry.
bool KlassInfoTable::record_instance(const oop obj) {
  klassOop      k = obj->klass();
  KlassInfoEntry* elt = lookup(k);
  // elt may be NULL if it's a new klass for which we
  // could not allocate space for a new entry in the hashtable.
  if (elt != NULL) {
    elt->set_count(elt->count() + 1);
    elt->set_words(elt->words() + obj->size());
    return true;
  } else {
    return false;
  }
}

void KlassInfoTable::iterate(KlassInfoClosure* cic) {
  assert(_size == 0 || _buckets != NULL, "Allocation failure should have been caught");
  for (int index = 0; index < _size; index++) {
    _buckets[index].iterate(cic);
  }
}

int KlassInfoHisto::sort_helper(KlassInfoEntry** e1, KlassInfoEntry** e2) {
  return (*e1)->compare(*e1,*e2);
}

KlassInfoHisto::KlassInfoHisto(const char* title, int estimatedCount) :
  _title(title) {
  _elements = new (ResourceObj::C_HEAP) GrowableArray<KlassInfoEntry*>(estimatedCount,true);
}

KlassInfoHisto::~KlassInfoHisto() {
  delete _elements;
}

void KlassInfoHisto::add(KlassInfoEntry* cie) {
  elements()->append(cie);
}

void KlassInfoHisto::sort() {
  elements()->sort(KlassInfoHisto::sort_helper);
}

void KlassInfoHisto::print_elements(outputStream* st) const {
  // simplify the formatting (ILP32 vs LP64) - store the sum in 64-bit
  jlong total = 0;
  julong totalw = 0;
  for(int i=0; i < elements()->length(); i++) {
    st->print("%4d: ", i+1);
    elements()->at(i)->print_on(st);
    total += elements()->at(i)->count();
    totalw += elements()->at(i)->words();
  }
  st->print_cr("Total " INT64_FORMAT_W(13) "  " UINT64_FORMAT_W(13),
               total, totalw * HeapWordSize);
}

void KlassInfoHisto::print_on(outputStream* st) const {
  st->print_cr("%s",title());
  print_elements(st);
}

class HistoClosure : public KlassInfoClosure {
 private:
  KlassInfoHisto* _cih;
 public:
  HistoClosure(KlassInfoHisto* cih) : _cih(cih) {}

  void do_cinfo(KlassInfoEntry* cie) {
    _cih->add(cie);
  }
};

class RecordInstanceClosure : public ObjectClosure {
 private:
  KlassInfoTable* _cit;
  size_t _missed_count;
 public:
  RecordInstanceClosure(KlassInfoTable* cit) :
    _cit(cit), _missed_count(0) {}

  void do_object(oop obj) {
    if (!_cit->record_instance(obj)) {
      _missed_count++;
    }
  }

  size_t missed_count() { return _missed_count; }
};

void HeapInspection::heap_inspection(outputStream* st, bool need_prologue) {
  ResourceMark rm;
  HeapWord* ref;

  CollectedHeap* heap = Universe::heap();
  bool is_shared_heap = false;
  switch (heap->kind()) {
    case CollectedHeap::G1CollectedHeap:
    case CollectedHeap::GenCollectedHeap: {
      is_shared_heap = true;
      SharedHeap* sh = (SharedHeap*)heap;
      if (need_prologue) {
        sh->gc_prologue(false /* !full */); // get any necessary locks, etc.
      }
      ref = sh->perm_gen()->used_region().start();
      break;
    }
#ifndef SERIALGC
    case CollectedHeap::ParallelScavengeHeap: {
      ParallelScavengeHeap* psh = (ParallelScavengeHeap*)heap;
      ref = psh->perm_gen()->object_space()->used_region().start();
      break;
    }
#endif // SERIALGC
    default:
      ShouldNotReachHere(); // Unexpected heap kind for this op
  }
  // Collect klass instance info
  KlassInfoTable cit(KlassInfoTable::cit_size, ref);
  if (!cit.allocation_failed()) {
    // Iterate over objects in the heap
    RecordInstanceClosure ric(&cit);
    // If this operation encounters a bad object when using CMS,
    // consider using safe_object_iterate() which avoids perm gen
    // objects that may contain bad references.
    Universe::heap()->object_iterate(&ric);

    // Report if certain classes are not counted because of
    // running out of C-heap for the histogram.
    size_t missed_count = ric.missed_count();
    if (missed_count != 0) {
      st->print_cr("WARNING: Ran out of C-heap; undercounted " SIZE_FORMAT
                   " total instances in data below",
                   missed_count);
    }
    // Sort and print klass instance info
    KlassInfoHisto histo("\n"
                     " num     #instances         #bytes  class name\n"
                     "----------------------------------------------",
                     KlassInfoHisto::histo_initial_size);
    HistoClosure hc(&histo);
    cit.iterate(&hc);
    histo.sort();
    histo.print_on(st);
  } else {
    st->print_cr("WARNING: Ran out of C-heap; histogram not generated");
  }
  st->flush();

  if (need_prologue && is_shared_heap) {
    SharedHeap* sh = (SharedHeap*)heap;
    sh->gc_epilogue(false /* !full */); // release all acquired locks, etc.
  }
}

class FindInstanceClosure : public ObjectClosure {
 private:
  klassOop _klass;
  GrowableArray<oop>* _result;

 public:
  FindInstanceClosure(klassOop k, GrowableArray<oop>* result) : _klass(k), _result(result) {};

  void do_object(oop obj) {
    if (obj->is_a(_klass)) {
      _result->append(obj);
    }
  }
};

void HeapInspection::find_instances_at_safepoint(klassOop k, GrowableArray<oop>* result) {
  assert(SafepointSynchronize::is_at_safepoint(), "all threads are stopped");
  assert(Heap_lock->is_locked(), "should have the Heap_lock")

  // Ensure that the heap is parsable
  Universe::heap()->ensure_parsability(false);  // no need to retire TALBs

  // Iterate over objects in the heap
  FindInstanceClosure fic(k, result);
  // If this operation encounters a bad object when using CMS,
  // consider using safe_object_iterate() which avoids perm gen
  // objects that may contain bad references.
  Universe::heap()->object_iterate(&fic);
}
