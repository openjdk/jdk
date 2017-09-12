/*
 * Copyright (c) 2002, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "memory/heapInspection.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/stack.inline.hpp"
#if INCLUDE_ALL_GCS
#include "gc/parallel/parallelScavengeHeap.hpp"
#endif // INCLUDE_ALL_GCS

// HeapInspection

int KlassSizeStats::count(oop x) {
  return (HeapWordSize * (((x) != NULL) ? (x)->size() : 0));
}

int KlassSizeStats::count_array(objArrayOop x) {
  return (HeapWordSize * (((x) != NULL) ? (x)->size() : 0));
}

inline KlassInfoEntry::~KlassInfoEntry() {
  if (_subclasses != NULL) {
    delete _subclasses;
  }
}

inline void KlassInfoEntry::add_subclass(KlassInfoEntry* cie) {
  if (_subclasses == NULL) {
    _subclasses = new  (ResourceObj::C_HEAP, mtInternal) GrowableArray<KlassInfoEntry*>(4, true);
  }
  _subclasses->append(cie);
}

int KlassInfoEntry::compare(KlassInfoEntry* e1, KlassInfoEntry* e2) {
  if(e1->_instance_words > e2->_instance_words) {
    return -1;
  } else if(e1->_instance_words < e2->_instance_words) {
    return 1;
  }
  // Sort alphabetically, note 'Z' < '[' < 'a', but it's better to group
  // the array classes before all the instance classes.
  ResourceMark rm;
  const char* name1 = e1->klass()->external_name();
  const char* name2 = e2->klass()->external_name();
  bool d1 = (name1[0] == '[');
  bool d2 = (name2[0] == '[');
  if (d1 && !d2) {
    return -1;
  } else if (d2 && !d1) {
    return 1;
  } else {
    return strcmp(name1, name2);
  }
}

const char* KlassInfoEntry::name() const {
  const char* name;
  if (_klass->name() != NULL) {
    name = _klass->external_name();
  } else {
    if (_klass == Universe::boolArrayKlassObj())         name = "<boolArrayKlass>";         else
    if (_klass == Universe::charArrayKlassObj())         name = "<charArrayKlass>";         else
    if (_klass == Universe::singleArrayKlassObj())       name = "<singleArrayKlass>";       else
    if (_klass == Universe::doubleArrayKlassObj())       name = "<doubleArrayKlass>";       else
    if (_klass == Universe::byteArrayKlassObj())         name = "<byteArrayKlass>";         else
    if (_klass == Universe::shortArrayKlassObj())        name = "<shortArrayKlass>";        else
    if (_klass == Universe::intArrayKlassObj())          name = "<intArrayKlass>";          else
    if (_klass == Universe::longArrayKlassObj())         name = "<longArrayKlass>";         else
      name = "<no name>";
  }
  return name;
}

void KlassInfoEntry::print_on(outputStream* st) const {
  ResourceMark rm;

  // simplify the formatting (ILP32 vs LP64) - always cast the numbers to 64-bit
  ModuleEntry* module = _klass->module();
  if (module->is_named()) {
    st->print_cr(INT64_FORMAT_W(13) "  " UINT64_FORMAT_W(13) "  %s (%s@%s)",
                 (int64_t)_instance_count,
                 (uint64_t)_instance_words * HeapWordSize,
                 name(),
                 module->name()->as_C_string(),
                 module->version() != NULL ? module->version()->as_C_string() : "");
  } else {
    st->print_cr(INT64_FORMAT_W(13) "  " UINT64_FORMAT_W(13) "  %s",
                 (int64_t)_instance_count,
                 (uint64_t)_instance_words * HeapWordSize,
                 name());
  }
}

KlassInfoEntry* KlassInfoBucket::lookup(Klass* const k) {
  KlassInfoEntry* elt = _list;
  while (elt != NULL) {
    if (elt->is_equal(k)) {
      return elt;
    }
    elt = elt->next();
  }
  elt = new (std::nothrow) KlassInfoEntry(k, list());
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

void KlassInfoTable::AllClassesFinder::do_klass(Klass* k) {
  // This has the SIDE EFFECT of creating a KlassInfoEntry
  // for <k>, if one doesn't exist yet.
  _table->lookup(k);
}

KlassInfoTable::KlassInfoTable(bool add_all_classes) {
  _size_of_instances_in_words = 0;
  _size = 0;
  _ref = (HeapWord*) Universe::boolArrayKlassObj();
  _buckets =
    (KlassInfoBucket*)  AllocateHeap(sizeof(KlassInfoBucket) * _num_buckets,
       mtInternal, CURRENT_PC, AllocFailStrategy::RETURN_NULL);
  if (_buckets != NULL) {
    _size = _num_buckets;
    for (int index = 0; index < _size; index++) {
      _buckets[index].initialize();
    }
    if (add_all_classes) {
      AllClassesFinder finder(this);
      ClassLoaderDataGraph::classes_do(&finder);
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

uint KlassInfoTable::hash(const Klass* p) {
  return (uint)(((uintptr_t)p - (uintptr_t)_ref) >> 2);
}

KlassInfoEntry* KlassInfoTable::lookup(Klass* k) {
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
  Klass*        k = obj->klass();
  KlassInfoEntry* elt = lookup(k);
  // elt may be NULL if it's a new klass for which we
  // could not allocate space for a new entry in the hashtable.
  if (elt != NULL) {
    elt->set_count(elt->count() + 1);
    elt->set_words(elt->words() + obj->size());
    _size_of_instances_in_words += obj->size();
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

size_t KlassInfoTable::size_of_instances_in_words() const {
  return _size_of_instances_in_words;
}

int KlassInfoHisto::sort_helper(KlassInfoEntry** e1, KlassInfoEntry** e2) {
  return (*e1)->compare(*e1,*e2);
}

KlassInfoHisto::KlassInfoHisto(KlassInfoTable* cit) :
  _cit(cit) {
  _elements = new (ResourceObj::C_HEAP, mtInternal) GrowableArray<KlassInfoEntry*>(_histo_initial_size, true);
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
  int64_t total = 0;
  uint64_t totalw = 0;
  for(int i=0; i < elements()->length(); i++) {
    st->print("%4d: ", i+1);
    elements()->at(i)->print_on(st);
    total += elements()->at(i)->count();
    totalw += elements()->at(i)->words();
  }
  st->print_cr("Total " INT64_FORMAT_W(13) "  " UINT64_FORMAT_W(13),
               total, totalw * HeapWordSize);
}

#define MAKE_COL_NAME(field, name, help)     #name,
#define MAKE_COL_HELP(field, name, help)     help,

static const char *name_table[] = {
  HEAP_INSPECTION_COLUMNS_DO(MAKE_COL_NAME)
};

static const char *help_table[] = {
  HEAP_INSPECTION_COLUMNS_DO(MAKE_COL_HELP)
};

bool KlassInfoHisto::is_selected(const char *col_name) {
  if (_selected_columns == NULL) {
    return true;
  }
  if (strcmp(_selected_columns, col_name) == 0) {
    return true;
  }

  const char *start = strstr(_selected_columns, col_name);
  if (start == NULL) {
    return false;
  }

  // The following must be true, because _selected_columns != col_name
  if (start > _selected_columns && start[-1] != ',') {
    return false;
  }
  char x = start[strlen(col_name)];
  if (x != ',' && x != '\0') {
    return false;
  }

  return true;
}

void KlassInfoHisto::print_title(outputStream* st, bool csv_format,
                                 bool selected[], int width_table[],
                                 const char *name_table[]) {
  if (csv_format) {
    st->print("Index,Super");
    for (int c=0; c<KlassSizeStats::_num_columns; c++) {
       if (selected[c]) {st->print(",%s", name_table[c]);}
    }
    st->print(",ClassName");
  } else {
    st->print("Index Super");
    for (int c = 0; c < KlassSizeStats::_num_columns; c++) {
      if (selected[c]) {
        st->print("%*s", width_table[c], name_table[c]);
      }
    }
    st->print(" ClassName");
  }

  if (is_selected("ClassLoader")) {
    st->print(",ClassLoader");
  }
  st->cr();
}

class HierarchyClosure : public KlassInfoClosure {
private:
  GrowableArray<KlassInfoEntry*> *_elements;
public:
  HierarchyClosure(GrowableArray<KlassInfoEntry*> *_elements) : _elements(_elements) {}

  void do_cinfo(KlassInfoEntry* cie) {
    // ignore array classes
    if (cie->klass()->is_instance_klass()) {
      _elements->append(cie);
    }
  }
};

void KlassHierarchy::print_class_hierarchy(outputStream* st, bool print_interfaces,
                                           bool print_subclasses, char* classname) {
  ResourceMark rm;
  Stack <KlassInfoEntry*, mtClass> class_stack;
  GrowableArray<KlassInfoEntry*> elements;

  // Add all classes to the KlassInfoTable, which allows for quick lookup.
  // A KlassInfoEntry will be created for each class.
  KlassInfoTable cit(true);
  if (cit.allocation_failed()) {
    st->print_cr("ERROR: Ran out of C-heap; hierarchy not generated");
    return;
  }

  // Add all created KlassInfoEntry instances to the elements array for easy
  // iteration, and to allow each KlassInfoEntry instance to have a unique index.
  HierarchyClosure hc(&elements);
  cit.iterate(&hc);

  for(int i = 0; i < elements.length(); i++) {
    KlassInfoEntry* cie = elements.at(i);
    Klass* super = cie->klass()->super();

    // Set the index for the class.
    cie->set_index(i + 1);

    // Add the class to the subclass array of its superclass.
    if (super != NULL) {
      KlassInfoEntry* super_cie = cit.lookup(super);
      assert(super_cie != NULL, "could not lookup superclass");
      super_cie->add_subclass(cie);
    }
  }

  // Set the do_print flag for each class that should be printed.
  for(int i = 0; i < elements.length(); i++) {
    KlassInfoEntry* cie = elements.at(i);
    if (classname == NULL) {
      // We are printing all classes.
      cie->set_do_print(true);
    } else {
      // We are only printing the hierarchy of a specific class.
      if (strcmp(classname, cie->klass()->external_name()) == 0) {
        KlassHierarchy::set_do_print_for_class_hierarchy(cie, &cit, print_subclasses);
      }
    }
  }

  // Now we do a depth first traversal of the class hierachry. The class_stack will
  // maintain the list of classes we still need to process. Start things off
  // by priming it with java.lang.Object.
  KlassInfoEntry* jlo_cie = cit.lookup(SystemDictionary::Object_klass());
  assert(jlo_cie != NULL, "could not lookup java.lang.Object");
  class_stack.push(jlo_cie);

  // Repeatedly pop the top item off the stack, print its class info,
  // and push all of its subclasses on to the stack. Do this until there
  // are no classes left on the stack.
  while (!class_stack.is_empty()) {
    KlassInfoEntry* curr_cie = class_stack.pop();
    if (curr_cie->do_print()) {
      print_class(st, curr_cie, print_interfaces);
      if (curr_cie->subclasses() != NULL) {
        // Current class has subclasses, so push all of them onto the stack.
        for (int i = 0; i < curr_cie->subclasses()->length(); i++) {
          KlassInfoEntry* cie = curr_cie->subclasses()->at(i);
          if (cie->do_print()) {
            class_stack.push(cie);
          }
        }
      }
    }
  }

  st->flush();
}

// Sets the do_print flag for every superclass and subclass of the specified class.
void KlassHierarchy::set_do_print_for_class_hierarchy(KlassInfoEntry* cie, KlassInfoTable* cit,
                                                      bool print_subclasses) {
  // Set do_print for all superclasses of this class.
  Klass* super = ((InstanceKlass*)cie->klass())->java_super();
  while (super != NULL) {
    KlassInfoEntry* super_cie = cit->lookup(super);
    super_cie->set_do_print(true);
    super = super->super();
  }

  // Set do_print for this class and all of its subclasses.
  Stack <KlassInfoEntry*, mtClass> class_stack;
  class_stack.push(cie);
  while (!class_stack.is_empty()) {
    KlassInfoEntry* curr_cie = class_stack.pop();
    curr_cie->set_do_print(true);
    if (print_subclasses && curr_cie->subclasses() != NULL) {
      // Current class has subclasses, so push all of them onto the stack.
      for (int i = 0; i < curr_cie->subclasses()->length(); i++) {
        KlassInfoEntry* cie = curr_cie->subclasses()->at(i);
        class_stack.push(cie);
      }
    }
  }
}

static void print_indent(outputStream* st, int indent) {
  while (indent != 0) {
    st->print("|");
    indent--;
    if (indent != 0) {
      st->print("  ");
    }
  }
}

// Print the class name and its unique ClassLoader identifer.
static void print_classname(outputStream* st, Klass* klass) {
  oop loader_oop = klass->class_loader_data()->class_loader();
  st->print("%s/", klass->external_name());
  if (loader_oop == NULL) {
    st->print("null");
  } else {
    st->print(INTPTR_FORMAT, p2i(klass->class_loader_data()));
  }
}

static void print_interface(outputStream* st, Klass* intf_klass, const char* intf_type, int indent) {
  print_indent(st, indent);
  st->print("  implements ");
  print_classname(st, intf_klass);
  st->print(" (%s intf)\n", intf_type);
}

void KlassHierarchy::print_class(outputStream* st, KlassInfoEntry* cie, bool print_interfaces) {
  ResourceMark rm;
  InstanceKlass* klass = (InstanceKlass*)cie->klass();
  int indent = 0;

  // Print indentation with proper indicators of superclass.
  Klass* super = klass->super();
  while (super != NULL) {
    super = super->super();
    indent++;
  }
  print_indent(st, indent);
  if (indent != 0) st->print("--");

  // Print the class name, its unique ClassLoader identifer, and if it is an interface.
  print_classname(st, klass);
  if (klass->is_interface()) {
    st->print(" (intf)");
  }
  st->print("\n");

  // Print any interfaces the class has.
  if (print_interfaces) {
    Array<Klass*>* local_intfs = klass->local_interfaces();
    Array<Klass*>* trans_intfs = klass->transitive_interfaces();
    for (int i = 0; i < local_intfs->length(); i++) {
      print_interface(st, local_intfs->at(i), "declared", indent);
    }
    for (int i = 0; i < trans_intfs->length(); i++) {
      Klass* trans_interface = trans_intfs->at(i);
      // Only print transitive interfaces if they are not also declared.
      if (!local_intfs->contains(trans_interface)) {
        print_interface(st, trans_interface, "inherited", indent);
      }
    }
  }
}

void KlassInfoHisto::print_class_stats(outputStream* st,
                                      bool csv_format, const char *columns) {
  ResourceMark rm;
  KlassSizeStats sz, sz_sum;
  int i;
  julong *col_table = (julong*)(&sz);
  julong *colsum_table = (julong*)(&sz_sum);
  int width_table[KlassSizeStats::_num_columns];
  bool selected[KlassSizeStats::_num_columns];

  _selected_columns = columns;

  memset(&sz_sum, 0, sizeof(sz_sum));
  for (int c=0; c<KlassSizeStats::_num_columns; c++) {
    selected[c] = is_selected(name_table[c]);
  }

  for(i=0; i < elements()->length(); i++) {
    elements()->at(i)->set_index(i+1);
  }

  // First iteration is for accumulating stats totals in colsum_table[].
  // Second iteration is for printing stats for each class.
  for (int pass=1; pass<=2; pass++) {
    if (pass == 2) {
      print_title(st, csv_format, selected, width_table, name_table);
    }
    for(i=0; i < elements()->length(); i++) {
      KlassInfoEntry* e = (KlassInfoEntry*)elements()->at(i);
      const Klass* k = e->klass();

      // Get the stats for this class.
      memset(&sz, 0, sizeof(sz));
      sz._inst_count = e->count();
      sz._inst_bytes = HeapWordSize * e->words();
      k->collect_statistics(&sz);
      sz._total_bytes = sz._ro_bytes + sz._rw_bytes;

      if (pass == 1) {
        // Add the stats for this class to the overall totals.
        for (int c=0; c<KlassSizeStats::_num_columns; c++) {
          colsum_table[c] += col_table[c];
        }
      } else {
        int super_index = -1;
        // Print the stats for this class.
        if (k->is_instance_klass()) {
          Klass* super = k->super();
          if (super) {
            KlassInfoEntry* super_e = _cit->lookup(super);
            if (super_e) {
              super_index = super_e->index();
            }
          }
        }

        if (csv_format) {
          st->print("%ld,%d", e->index(), super_index);
          for (int c=0; c<KlassSizeStats::_num_columns; c++) {
            if (selected[c]) {st->print("," JULONG_FORMAT, col_table[c]);}
          }
          st->print(",%s",e->name());
        } else {
          st->print("%5ld %5d", e->index(), super_index);
          for (int c=0; c<KlassSizeStats::_num_columns; c++) {
            if (selected[c]) {print_julong(st, width_table[c], col_table[c]);}
          }
          st->print(" %s", e->name());
        }
        if (is_selected("ClassLoader")) {
          ClassLoaderData* loader_data = k->class_loader_data();
          st->print(",");
          loader_data->print_value_on(st);
        }
        st->cr();
      }
    }

    if (pass == 1) {
      // Calculate the minimum width needed for the column by accounting for the
      // column header width and the width of the largest value in the column.
      for (int c=0; c<KlassSizeStats::_num_columns; c++) {
        width_table[c] = col_width(colsum_table[c], name_table[c]);
      }
    }
  }

  sz_sum._inst_size = 0;

  // Print the column totals.
  if (csv_format) {
    st->print(",");
    for (int c=0; c<KlassSizeStats::_num_columns; c++) {
      if (selected[c]) {st->print("," JULONG_FORMAT, colsum_table[c]);}
    }
  } else {
    st->print("           ");
    for (int c=0; c<KlassSizeStats::_num_columns; c++) {
      if (selected[c]) {print_julong(st, width_table[c], colsum_table[c]);}
    }
    st->print(" Total");
    if (sz_sum._total_bytes > 0) {
      st->cr();
      st->print("           ");
      for (int c=0; c<KlassSizeStats::_num_columns; c++) {
        if (selected[c]) {
          switch (c) {
          case KlassSizeStats::_index_inst_size:
          case KlassSizeStats::_index_inst_count:
          case KlassSizeStats::_index_method_count:
            st->print("%*s", width_table[c], "-");
            break;
          default:
            {
              double perc = (double)(100) * (double)(colsum_table[c]) / (double)sz_sum._total_bytes;
              st->print("%*.1f%%", width_table[c]-1, perc);
            }
          }
        }
      }
    }
  }
  st->cr();

  if (!csv_format) {
    print_title(st, csv_format, selected, width_table, name_table);
  }
}

julong KlassInfoHisto::annotations_bytes(Array<AnnotationArray*>* p) const {
  julong bytes = 0;
  if (p != NULL) {
    for (int i = 0; i < p->length(); i++) {
      bytes += count_bytes_array(p->at(i));
    }
    bytes += count_bytes_array(p);
  }
  return bytes;
}

void KlassInfoHisto::print_histo_on(outputStream* st, bool print_stats,
                                    bool csv_format, const char *columns) {
  if (print_stats) {
    print_class_stats(st, csv_format, columns);
  } else {
    st->print_cr(" num     #instances         #bytes  class name (module)");
    st->print_cr("-------------------------------------------------------");
    print_elements(st);
  }
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
  BoolObjectClosure* _filter;
 public:
  RecordInstanceClosure(KlassInfoTable* cit, BoolObjectClosure* filter) :
    _cit(cit), _missed_count(0), _filter(filter) {}

  void do_object(oop obj) {
    if (should_visit(obj)) {
      if (!_cit->record_instance(obj)) {
        _missed_count++;
      }
    }
  }

  size_t missed_count() { return _missed_count; }

 private:
  bool should_visit(oop obj) {
    return _filter == NULL || _filter->do_object_b(obj);
  }
};

size_t HeapInspection::populate_table(KlassInfoTable* cit, BoolObjectClosure *filter) {
  ResourceMark rm;

  RecordInstanceClosure ric(cit, filter);
  Universe::heap()->object_iterate(&ric);
  return ric.missed_count();
}

void HeapInspection::heap_inspection(outputStream* st) {
  ResourceMark rm;

  if (_print_help) {
    for (int c=0; c<KlassSizeStats::_num_columns; c++) {
      st->print("%s:\n\t", name_table[c]);
      const int max_col = 60;
      int col = 0;
      for (const char *p = help_table[c]; *p; p++,col++) {
        if (col >= max_col && *p == ' ') {
          st->print("\n\t");
          col = 0;
        } else {
          st->print("%c", *p);
        }
      }
      st->print_cr(".\n");
    }
    return;
  }

  KlassInfoTable cit(_print_class_stats);
  if (!cit.allocation_failed()) {
    // populate table with object allocation info
    size_t missed_count = populate_table(&cit);
    if (missed_count != 0) {
      st->print_cr("WARNING: Ran out of C-heap; undercounted " SIZE_FORMAT
                   " total instances in data below",
                   missed_count);
    }

    // Sort and print klass instance info
    KlassInfoHisto histo(&cit);
    HistoClosure hc(&histo);

    cit.iterate(&hc);

    histo.sort();
    histo.print_histo_on(st, _print_class_stats, _csv_format, _columns);
  } else {
    st->print_cr("ERROR: Ran out of C-heap; histogram not generated");
  }
  st->flush();
}

class FindInstanceClosure : public ObjectClosure {
 private:
  Klass* _klass;
  GrowableArray<oop>* _result;

 public:
  FindInstanceClosure(Klass* k, GrowableArray<oop>* result) : _klass(k), _result(result) {};

  void do_object(oop obj) {
    if (obj->is_a(_klass)) {
      _result->append(obj);
    }
  }
};

void HeapInspection::find_instances_at_safepoint(Klass* k, GrowableArray<oop>* result) {
  assert(SafepointSynchronize::is_at_safepoint(), "all threads are stopped");
  assert(Heap_lock->is_locked(), "should have the Heap_lock");

  // Ensure that the heap is parsable
  Universe::heap()->ensure_parsability(false);  // no need to retire TALBs

  // Iterate over objects in the heap
  FindInstanceClosure fic(k, result);
  // If this operation encounters a bad object when using CMS,
  // consider using safe_object_iterate() which avoids metadata
  // objects that may contain bad references.
  Universe::heap()->object_iterate(&fic);
}
