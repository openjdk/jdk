/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_HEAPINSPECTION_HPP
#define SHARE_VM_MEMORY_HEAPINSPECTION_HPP

#include "memory/allocation.inline.hpp"
#include "memory/klassInfoClosure.hpp"
#include "oops/oop.inline.hpp"
#include "oops/annotations.hpp"
#include "utilities/macros.hpp"

#if INCLUDE_SERVICES


// HeapInspection

// KlassInfoTable is a bucket hash table that
// maps Klass*s to extra information:
//    instance count and instance word size.
//
// A KlassInfoBucket is the head of a link list
// of KlassInfoEntry's
//
// KlassInfoHisto is a growable array of pointers
// to KlassInfoEntry's and is used to sort
// the entries.

#define HEAP_INSPECTION_COLUMNS_DO(f) \
    f(inst_size, InstSize, \
        "Size of each object instance of the Java class") \
    f(inst_count, InstCount, \
        "Number of object instances of the Java class")  \
    f(inst_bytes, InstBytes, \
        "This is usually (InstSize * InstNum). The only exception is " \
        "java.lang.Class, whose InstBytes also includes the slots " \
        "used to store static fields. InstBytes is not counted in " \
        "ROAll, RWAll or Total") \
    f(mirror_bytes, Mirror, \
        "Size of the Klass::java_mirror() object") \
    f(klass_bytes, KlassBytes, \
        "Size of the InstanceKlass or ArrayKlass for this class. " \
        "Note that this includes VTab, ITab, OopMap") \
    f(secondary_supers_bytes, K_secondary_supers, \
        "Number of bytes used by the Klass::secondary_supers() array") \
    f(vtab_bytes, VTab, \
        "Size of the embedded vtable in InstanceKlass") \
    f(itab_bytes, ITab, \
        "Size of the embedded itable in InstanceKlass") \
    f(nonstatic_oopmap_bytes, OopMap, \
        "Size of the embedded nonstatic_oop_map in InstanceKlass") \
    f(methods_array_bytes, IK_methods, \
        "Number of bytes used by the InstanceKlass::methods() array") \
    f(method_ordering_bytes, IK_method_ordering, \
        "Number of bytes used by the InstanceKlass::method_ordering() array") \
    f(local_interfaces_bytes, IK_local_interfaces, \
        "Number of bytes used by the InstanceKlass::local_interfaces() array") \
    f(transitive_interfaces_bytes, IK_transitive_interfaces, \
        "Number of bytes used by the InstanceKlass::transitive_interfaces() array") \
    f(fields_bytes, IK_fields, \
        "Number of bytes used by the InstanceKlass::fields() array") \
    f(inner_classes_bytes, IK_inner_classes, \
        "Number of bytes used by the InstanceKlass::inner_classes() array") \
    f(signers_bytes, IK_signers, \
        "Number of bytes used by the InstanceKlass::singers() array") \
    f(class_annotations_bytes, class_annotations, \
        "Size of class annotations") \
    f(class_type_annotations_bytes, class_type_annotations, \
        "Size of class type annotations") \
    f(fields_annotations_bytes, fields_annotations, \
        "Size of field annotations") \
    f(fields_type_annotations_bytes, fields_type_annotations, \
        "Size of field type annotations") \
    f(methods_annotations_bytes, methods_annotations, \
        "Size of method annotations") \
    f(methods_parameter_annotations_bytes, methods_parameter_annotations, \
        "Size of method parameter annotations") \
    f(methods_type_annotations_bytes, methods_type_annotations, \
        "Size of methods type annotations") \
    f(methods_default_annotations_bytes, methods_default_annotations, \
        "Size of methods default annotations") \
    f(annotations_bytes, annotations, \
        "Size of all annotations") \
    f(cp_bytes, Cp, \
        "Size of InstanceKlass::constants()") \
    f(cp_tags_bytes, CpTags, \
        "Size of InstanceKlass::constants()->tags()") \
    f(cp_cache_bytes, CpCache, \
        "Size of InstanceKlass::constants()->cache()") \
    f(cp_operands_bytes, CpOperands, \
        "Size of InstanceKlass::constants()->operands()") \
    f(cp_refmap_bytes, CpRefMap, \
        "Size of InstanceKlass::constants()->reference_map()") \
    f(cp_all_bytes, CpAll, \
        "Sum of Cp + CpTags + CpCache + CpOperands + CpRefMap") \
    f(method_count, MethodCount, \
        "Number of methods in this class") \
    f(method_bytes, MethodBytes, \
        "Size of the Method object") \
    f(const_method_bytes, ConstMethod, \
        "Size of the ConstMethod object") \
    f(method_data_bytes, MethodData, \
        "Size of the MethodData object") \
    f(stackmap_bytes, StackMap, \
        "Size of the stackmap_data") \
    f(bytecode_bytes, Bytecodes, \
        "Of the MethodBytes column, how much are the space taken up by bytecodes") \
    f(method_all_bytes, MethodAll, \
        "Sum of MethodBytes + Constmethod + Stackmap + Methoddata") \
    f(ro_bytes, ROAll, \
        "Size of all class meta data that could (potentially) be placed " \
        "in read-only memory. (This could change with CDS design)") \
    f(rw_bytes, RWAll, \
        "Size of all class meta data that must be placed in read/write " \
        "memory. (This could change with CDS design) ") \
    f(total_bytes, Total, \
        "ROAll + RWAll. Note that this does NOT include InstBytes.")

// Size statistics for a Klass - filled in by Klass::collect_statistics()
class KlassSizeStats {
public:
#define COUNT_KLASS_SIZE_STATS_FIELD(field, name, help)   _index_ ## field,
#define DECLARE_KLASS_SIZE_STATS_FIELD(field, name, help) julong _ ## field;

  enum {
    HEAP_INSPECTION_COLUMNS_DO(COUNT_KLASS_SIZE_STATS_FIELD)
    _num_columns
  };

  HEAP_INSPECTION_COLUMNS_DO(DECLARE_KLASS_SIZE_STATS_FIELD)

  static int count(oop x) {
    return (HeapWordSize * ((x) ? (x)->size() : 0));
  }

  static int count_array(objArrayOop x) {
    return (HeapWordSize * ((x) ? (x)->size() : 0));
  }

  template <class T> static int count(T* x) {
    return (HeapWordSize * ((x) ? (x)->size() : 0));
  }

  template <class T> static int count_array(T* x) {
    if (x == NULL) {
      return 0;
    }
    if (x->length() == 0) {
      // This is a shared array, e.g., Universe::the_empty_int_array(). Don't
      // count it to avoid double-counting.
      return 0;
    }
    return HeapWordSize * x->size();
  }
};




class KlassInfoEntry: public CHeapObj<mtInternal> {
 private:
  KlassInfoEntry* _next;
  Klass*          _klass;
  long            _instance_count;
  size_t          _instance_words;
  long            _index;

 public:
  KlassInfoEntry(Klass* k, KlassInfoEntry* next) :
    _klass(k), _instance_count(0), _instance_words(0), _next(next), _index(-1)
  {}
  KlassInfoEntry* next() const   { return _next; }
  bool is_equal(const Klass* k)  { return k == _klass; }
  Klass* klass()  const      { return _klass; }
  long count()    const      { return _instance_count; }
  void set_count(long ct)    { _instance_count = ct; }
  size_t words()  const      { return _instance_words; }
  void set_words(size_t wds) { _instance_words = wds; }
  void set_index(long index) { _index = index; }
  long index()    const      { return _index; }
  int compare(KlassInfoEntry* e1, KlassInfoEntry* e2);
  void print_on(outputStream* st) const;
  const char* name() const;
};

class KlassInfoBucket: public CHeapObj<mtInternal> {
 private:
  KlassInfoEntry* _list;
  KlassInfoEntry* list()           { return _list; }
  void set_list(KlassInfoEntry* l) { _list = l; }
 public:
  KlassInfoEntry* lookup(Klass* k);
  void initialize() { _list = NULL; }
  void empty();
  void iterate(KlassInfoClosure* cic);
};

class KlassInfoTable: public StackObj {
 private:
  int _size;
  static const int _num_buckets = 20011;
  size_t _size_of_instances_in_words;

  // An aligned reference address (typically the least
  // address in the perm gen) used for hashing klass
  // objects.
  HeapWord* _ref;

  KlassInfoBucket* _buckets;
  uint hash(const Klass* p);
  KlassInfoEntry* lookup(Klass* k); // allocates if not found!

  class AllClassesFinder : public KlassClosure {
    KlassInfoTable *_table;
   public:
    AllClassesFinder(KlassInfoTable* table) : _table(table) {}
    virtual void do_klass(Klass* k);
  };

 public:
  KlassInfoTable(bool need_class_stats);
  ~KlassInfoTable();
  bool record_instance(const oop obj);
  void iterate(KlassInfoClosure* cic);
  bool allocation_failed() { return _buckets == NULL; }
  size_t size_of_instances_in_words() const;

  friend class KlassInfoHisto;
};

class KlassInfoHisto : public StackObj {
 private:
  static const int _histo_initial_size = 1000;
  KlassInfoTable *_cit;
  GrowableArray<KlassInfoEntry*>* _elements;
  GrowableArray<KlassInfoEntry*>* elements() const { return _elements; }
  const char* _title;
  const char* title() const { return _title; }
  static int sort_helper(KlassInfoEntry** e1, KlassInfoEntry** e2);
  void print_elements(outputStream* st) const;
  void print_class_stats(outputStream* st, bool csv_format, const char *columns);
  julong annotations_bytes(Array<AnnotationArray*>* p) const;
  const char *_selected_columns;
  bool is_selected(const char *col_name);
  void print_title(outputStream* st, bool csv_format,
                   bool selected_columns_table[], int width_table[],
                   const char *name_table[]);

  template <class T> static int count_bytes(T* x) {
    return (HeapWordSize * ((x) ? (x)->size() : 0));
  }

  template <class T> static int count_bytes_array(T* x) {
    if (x == NULL) {
      return 0;
    }
    if (x->length() == 0) {
      // This is a shared array, e.g., Universe::the_empty_int_array(). Don't
      // count it to avoid double-counting.
      return 0;
    }
    return HeapWordSize * x->size();
  }

  // returns a format string to print a julong with the given width. E.g,
  // printf(num_fmt(6), julong(10)) would print out the number 10 with 4
  // leading spaces.
  static void print_julong(outputStream* st, int width, julong n) {
    int num_spaces = width - julong_width(n);
    if (num_spaces > 0) {
      st->print(str_fmt(num_spaces), "");
    }
    st->print(JULONG_FORMAT, n);
  }

  static char* perc_fmt(int width) {
    static char buf[32];
    jio_snprintf(buf, sizeof(buf), "%%%d.1f%%%%", width-1);
    return buf;
  }

  static char* str_fmt(int width) {
    static char buf[32];
    jio_snprintf(buf, sizeof(buf), "%%%ds", width);
    return buf;
  }

  static int julong_width(julong n) {
    if (n == 0) {
      return 1;
    }
    int w = 0;
    while (n > 0) {
      n /= 10;
      w += 1;
    }
    return w;
  }

  static int col_width(julong n, const char *name) {
    int w = julong_width(n);
    int min = (int)(strlen(name));
    if (w < min) {
        w = min;
    }
    // add a leading space for separation.
    return w + 1;
  }

 public:
  KlassInfoHisto(KlassInfoTable* cit, const char* title);
  ~KlassInfoHisto();
  void add(KlassInfoEntry* cie);
  void print_histo_on(outputStream* st, bool print_class_stats, bool csv_format, const char *columns);
  void sort();
};

#endif // INCLUDE_SERVICES

// These declarations are needed since teh declaration of KlassInfoTable and
// KlassInfoClosure are guarded by #if INLCUDE_SERVICES
class KlassInfoTable;
class KlassInfoClosure;

class HeapInspection : public StackObj {
  bool _csv_format; // "comma separated values" format for spreadsheet.
  bool _print_help;
  bool _print_class_stats;
  const char* _columns;
 public:
  HeapInspection(bool csv_format, bool print_help,
                 bool print_class_stats, const char *columns) :
      _csv_format(csv_format), _print_help(print_help),
      _print_class_stats(print_class_stats), _columns(columns) {}
  void heap_inspection(outputStream* st) NOT_SERVICES_RETURN;
  size_t populate_table(KlassInfoTable* cit, BoolObjectClosure* filter = NULL) NOT_SERVICES_RETURN;
  static void find_instances_at_safepoint(Klass* k, GrowableArray<oop>* result) NOT_SERVICES_RETURN;
 private:
  void iterate_over_heap(KlassInfoTable* cit, BoolObjectClosure* filter = NULL);
};

#endif // SHARE_VM_MEMORY_HEAPINSPECTION_HPP
