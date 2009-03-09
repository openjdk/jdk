/*
 * Copyright 2002-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef SERVICES_KERNEL


// HeapInspection

// KlassInfoTable is a bucket hash table that
// maps klassOops to extra information:
//    instance count and instance word size.
//
// A KlassInfoBucket is the head of a link list
// of KlassInfoEntry's
//
// KlassInfoHisto is a growable array of pointers
// to KlassInfoEntry's and is used to sort
// the entries.

class KlassInfoEntry: public CHeapObj {
 private:
  KlassInfoEntry* _next;
  klassOop        _klass;
  long            _instance_count;
  size_t          _instance_words;

 public:
  KlassInfoEntry(klassOop k, KlassInfoEntry* next) :
    _klass(k), _instance_count(0), _instance_words(0), _next(next)
  {}
  KlassInfoEntry* next()     { return _next; }
  bool is_equal(klassOop k)  { return k == _klass; }
  klassOop klass()           { return _klass; }
  long count()               { return _instance_count; }
  void set_count(long ct)    { _instance_count = ct; }
  size_t words()             { return _instance_words; }
  void set_words(size_t wds) { _instance_words = wds; }
  int compare(KlassInfoEntry* e1, KlassInfoEntry* e2);
  void print_on(outputStream* st) const;
};

class KlassInfoClosure: public StackObj {
 public:
  // Called for each KlassInfoEntry.
  virtual void do_cinfo(KlassInfoEntry* cie) = 0;
};

class KlassInfoBucket: public CHeapObj {
 private:
  KlassInfoEntry* _list;
  KlassInfoEntry* list()           { return _list; }
  void set_list(KlassInfoEntry* l) { _list = l; }
 public:
  KlassInfoEntry* lookup(const klassOop k);
  void initialize() { _list = NULL; }
  void empty();
  void iterate(KlassInfoClosure* cic);
};

class KlassInfoTable: public StackObj {
 private:
  int _size;

  // An aligned reference address (typically the least
  // address in the perm gen) used for hashing klass
  // objects.
  HeapWord* _ref;

  KlassInfoBucket* _buckets;
  uint hash(klassOop p);
  KlassInfoEntry* lookup(const klassOop k);

 public:
  // Table size
  enum {
    cit_size = 20011
  };
  KlassInfoTable(int size, HeapWord* ref);
  ~KlassInfoTable();
  bool record_instance(const oop obj);
  void iterate(KlassInfoClosure* cic);
  bool allocation_failed() { return _buckets == NULL; }
};

class KlassInfoHisto : public StackObj {
 private:
  GrowableArray<KlassInfoEntry*>* _elements;
  GrowableArray<KlassInfoEntry*>* elements() const { return _elements; }
  const char* _title;
  const char* title() const { return _title; }
  static int sort_helper(KlassInfoEntry** e1, KlassInfoEntry** e2);
  void print_elements(outputStream* st) const;
 public:
  enum {
    histo_initial_size = 1000
  };
  KlassInfoHisto(const char* title,
             int estimatedCount);
  ~KlassInfoHisto();
  void add(KlassInfoEntry* cie);
  void print_on(outputStream* st) const;
  void sort();
};

#endif // SERVICES_KERNEL

class HeapInspection : public AllStatic {
 public:
  static void heap_inspection(outputStream* st, bool need_prologue) KERNEL_RETURN;
  static void find_instances_at_safepoint(klassOop k, GrowableArray<oop>* result) KERNEL_RETURN;
};
