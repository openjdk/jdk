/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciArrayKlass.hpp"
#include "ci/ciEnv.hpp"
#include "ci/ciKlass.hpp"
#include "ci/ciMethod.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "code/dependencies.hpp"
#include "compiler/compileLog.hpp"
#include "oops/oop.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "runtime/handles.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/copy.hpp"


#ifdef ASSERT
static bool must_be_in_vm() {
  Thread* thread = Thread::current();
  if (thread->is_Java_thread())
    return ((JavaThread*)thread)->thread_state() == _thread_in_vm;
  else
    return true;  //something like this: thread->is_VM_thread();
}
#endif //ASSERT

void Dependencies::initialize(ciEnv* env) {
  Arena* arena = env->arena();
  _oop_recorder = env->oop_recorder();
  _log = env->log();
  _dep_seen = new(arena) GrowableArray<int>(arena, 500, 0, 0);
#if INCLUDE_JVMCI
  _using_dep_values = false;
#endif
  DEBUG_ONLY(_deps[end_marker] = NULL);
  for (int i = (int)FIRST_TYPE; i < (int)TYPE_LIMIT; i++) {
    _deps[i] = new(arena) GrowableArray<ciBaseObject*>(arena, 10, 0, 0);
  }
  _content_bytes = NULL;
  _size_in_bytes = (size_t)-1;

  assert(TYPE_LIMIT <= (1<<LG2_TYPE_LIMIT), "sanity");
}

void Dependencies::assert_evol_method(ciMethod* m) {
  assert_common_1(evol_method, m);
}

void Dependencies::assert_leaf_type(ciKlass* ctxk) {
  if (ctxk->is_array_klass()) {
    // As a special case, support this assertion on an array type,
    // which reduces to an assertion on its element type.
    // Note that this cannot be done with assertions that
    // relate to concreteness or abstractness.
    ciType* elemt = ctxk->as_array_klass()->base_element_type();
    if (!elemt->is_instance_klass())  return;   // Ex:  int[][]
    ctxk = elemt->as_instance_klass();
    //if (ctxk->is_final())  return;            // Ex:  String[][]
  }
  check_ctxk(ctxk);
  assert_common_1(leaf_type, ctxk);
}

void Dependencies::assert_abstract_with_unique_concrete_subtype(ciKlass* ctxk, ciKlass* conck) {
  check_ctxk_abstract(ctxk);
  assert_common_2(abstract_with_unique_concrete_subtype, ctxk, conck);
}

void Dependencies::assert_abstract_with_no_concrete_subtype(ciKlass* ctxk) {
  check_ctxk_abstract(ctxk);
  assert_common_1(abstract_with_no_concrete_subtype, ctxk);
}

void Dependencies::assert_concrete_with_no_concrete_subtype(ciKlass* ctxk) {
  check_ctxk_concrete(ctxk);
  assert_common_1(concrete_with_no_concrete_subtype, ctxk);
}

void Dependencies::assert_unique_concrete_method(ciKlass* ctxk, ciMethod* uniqm) {
  check_ctxk(ctxk);
  assert_common_2(unique_concrete_method, ctxk, uniqm);
}

void Dependencies::assert_abstract_with_exclusive_concrete_subtypes(ciKlass* ctxk, ciKlass* k1, ciKlass* k2) {
  check_ctxk(ctxk);
  assert_common_3(abstract_with_exclusive_concrete_subtypes_2, ctxk, k1, k2);
}

void Dependencies::assert_exclusive_concrete_methods(ciKlass* ctxk, ciMethod* m1, ciMethod* m2) {
  check_ctxk(ctxk);
  assert_common_3(exclusive_concrete_methods_2, ctxk, m1, m2);
}

void Dependencies::assert_has_no_finalizable_subclasses(ciKlass* ctxk) {
  check_ctxk(ctxk);
  assert_common_1(no_finalizable_subclasses, ctxk);
}

void Dependencies::assert_call_site_target_value(ciCallSite* call_site, ciMethodHandle* method_handle) {
  assert_common_2(call_site_target_value, call_site, method_handle);
}

#if INCLUDE_JVMCI

Dependencies::Dependencies(Arena* arena, OopRecorder* oop_recorder, CompileLog* log) {
  _oop_recorder = oop_recorder;
  _log = log;
  _dep_seen = new(arena) GrowableArray<int>(arena, 500, 0, 0);
  _using_dep_values = true;
  DEBUG_ONLY(_dep_values[end_marker] = NULL);
  for (int i = (int)FIRST_TYPE; i < (int)TYPE_LIMIT; i++) {
    _dep_values[i] = new(arena) GrowableArray<DepValue>(arena, 10, 0, DepValue());
  }
  _content_bytes = NULL;
  _size_in_bytes = (size_t)-1;

  assert(TYPE_LIMIT <= (1<<LG2_TYPE_LIMIT), "sanity");
}

void Dependencies::assert_evol_method(Method* m) {
  assert_common_1(evol_method, DepValue(_oop_recorder, m));
}

void Dependencies::assert_has_no_finalizable_subclasses(Klass* ctxk) {
  check_ctxk(ctxk);
  assert_common_1(no_finalizable_subclasses, DepValue(_oop_recorder, ctxk));
}

void Dependencies::assert_leaf_type(Klass* ctxk) {
  if (ctxk->is_array_klass()) {
    // As a special case, support this assertion on an array type,
    // which reduces to an assertion on its element type.
    // Note that this cannot be done with assertions that
    // relate to concreteness or abstractness.
    BasicType elemt = ArrayKlass::cast(ctxk)->element_type();
    if (is_java_primitive(elemt))  return;   // Ex:  int[][]
    ctxk = ObjArrayKlass::cast(ctxk)->bottom_klass();
    //if (ctxk->is_final())  return;            // Ex:  String[][]
  }
  check_ctxk(ctxk);
  assert_common_1(leaf_type, DepValue(_oop_recorder, ctxk));
}

void Dependencies::assert_abstract_with_unique_concrete_subtype(Klass* ctxk, Klass* conck) {
  check_ctxk_abstract(ctxk);
  DepValue ctxk_dv(_oop_recorder, ctxk);
  DepValue conck_dv(_oop_recorder, conck, &ctxk_dv);
  assert_common_2(abstract_with_unique_concrete_subtype, ctxk_dv, conck_dv);
}

void Dependencies::assert_unique_concrete_method(Klass* ctxk, Method* uniqm) {
  check_ctxk(ctxk);
  assert_common_2(unique_concrete_method, DepValue(_oop_recorder, ctxk), DepValue(_oop_recorder, uniqm));
}

void Dependencies::assert_call_site_target_value(oop call_site, oop method_handle) {
  assert_common_2(call_site_target_value, DepValue(_oop_recorder, JNIHandles::make_local(call_site)), DepValue(_oop_recorder, JNIHandles::make_local(method_handle)));
}

#endif // INCLUDE_JVMCI


// Helper function.  If we are adding a new dep. under ctxk2,
// try to find an old dep. under a broader* ctxk1.  If there is
//
bool Dependencies::maybe_merge_ctxk(GrowableArray<ciBaseObject*>* deps,
                                    int ctxk_i, ciKlass* ctxk2) {
  ciKlass* ctxk1 = deps->at(ctxk_i)->as_metadata()->as_klass();
  if (ctxk2->is_subtype_of(ctxk1)) {
    return true;  // success, and no need to change
  } else if (ctxk1->is_subtype_of(ctxk2)) {
    // new context class fully subsumes previous one
    deps->at_put(ctxk_i, ctxk2);
    return true;
  } else {
    return false;
  }
}

void Dependencies::assert_common_1(DepType dept, ciBaseObject* x) {
  assert(dep_args(dept) == 1, "sanity");
  log_dependency(dept, x);
  GrowableArray<ciBaseObject*>* deps = _deps[dept];

  // see if the same (or a similar) dep is already recorded
  if (note_dep_seen(dept, x)) {
    assert(deps->find(x) >= 0, "sanity");
  } else {
    deps->append(x);
  }
}

void Dependencies::assert_common_2(DepType dept,
                                   ciBaseObject* x0, ciBaseObject* x1) {
  assert(dep_args(dept) == 2, "sanity");
  log_dependency(dept, x0, x1);
  GrowableArray<ciBaseObject*>* deps = _deps[dept];

  // see if the same (or a similar) dep is already recorded
  bool has_ctxk = has_explicit_context_arg(dept);
  if (has_ctxk) {
    assert(dep_context_arg(dept) == 0, "sanity");
    if (note_dep_seen(dept, x1)) {
      // look in this bucket for redundant assertions
      const int stride = 2;
      for (int i = deps->length(); (i -= stride) >= 0; ) {
        ciBaseObject* y1 = deps->at(i+1);
        if (x1 == y1) {  // same subject; check the context
          if (maybe_merge_ctxk(deps, i+0, x0->as_metadata()->as_klass())) {
            return;
          }
        }
      }
    }
  } else {
    if (note_dep_seen(dept, x0) && note_dep_seen(dept, x1)) {
      // look in this bucket for redundant assertions
      const int stride = 2;
      for (int i = deps->length(); (i -= stride) >= 0; ) {
        ciBaseObject* y0 = deps->at(i+0);
        ciBaseObject* y1 = deps->at(i+1);
        if (x0 == y0 && x1 == y1) {
          return;
        }
      }
    }
  }

  // append the assertion in the correct bucket:
  deps->append(x0);
  deps->append(x1);
}

void Dependencies::assert_common_3(DepType dept,
                                   ciKlass* ctxk, ciBaseObject* x, ciBaseObject* x2) {
  assert(dep_context_arg(dept) == 0, "sanity");
  assert(dep_args(dept) == 3, "sanity");
  log_dependency(dept, ctxk, x, x2);
  GrowableArray<ciBaseObject*>* deps = _deps[dept];

  // try to normalize an unordered pair:
  bool swap = false;
  switch (dept) {
  case abstract_with_exclusive_concrete_subtypes_2:
    swap = (x->ident() > x2->ident() && x->as_metadata()->as_klass() != ctxk);
    break;
  case exclusive_concrete_methods_2:
    swap = (x->ident() > x2->ident() && x->as_metadata()->as_method()->holder() != ctxk);
    break;
  }
  if (swap) { ciBaseObject* t = x; x = x2; x2 = t; }

  // see if the same (or a similar) dep is already recorded
  if (note_dep_seen(dept, x) && note_dep_seen(dept, x2)) {
    // look in this bucket for redundant assertions
    const int stride = 3;
    for (int i = deps->length(); (i -= stride) >= 0; ) {
      ciBaseObject* y  = deps->at(i+1);
      ciBaseObject* y2 = deps->at(i+2);
      if (x == y && x2 == y2) {  // same subjects; check the context
        if (maybe_merge_ctxk(deps, i+0, ctxk)) {
          return;
        }
      }
    }
  }
  // append the assertion in the correct bucket:
  deps->append(ctxk);
  deps->append(x);
  deps->append(x2);
}

#if INCLUDE_JVMCI
bool Dependencies::maybe_merge_ctxk(GrowableArray<DepValue>* deps,
                                    int ctxk_i, DepValue ctxk2_dv) {
  Klass* ctxk1 = deps->at(ctxk_i).as_klass(_oop_recorder);
  Klass* ctxk2 = ctxk2_dv.as_klass(_oop_recorder);
  if (ctxk2->is_subtype_of(ctxk1)) {
    return true;  // success, and no need to change
  } else if (ctxk1->is_subtype_of(ctxk2)) {
    // new context class fully subsumes previous one
    deps->at_put(ctxk_i, ctxk2_dv);
    return true;
  } else {
    return false;
  }
}

void Dependencies::assert_common_1(DepType dept, DepValue x) {
  assert(dep_args(dept) == 1, "sanity");
  //log_dependency(dept, x);
  GrowableArray<DepValue>* deps = _dep_values[dept];

  // see if the same (or a similar) dep is already recorded
  if (note_dep_seen(dept, x)) {
    assert(deps->find(x) >= 0, "sanity");
  } else {
    deps->append(x);
  }
}

void Dependencies::assert_common_2(DepType dept,
                                   DepValue x0, DepValue x1) {
  assert(dep_args(dept) == 2, "sanity");
  //log_dependency(dept, x0, x1);
  GrowableArray<DepValue>* deps = _dep_values[dept];

  // see if the same (or a similar) dep is already recorded
  bool has_ctxk = has_explicit_context_arg(dept);
  if (has_ctxk) {
    assert(dep_context_arg(dept) == 0, "sanity");
    if (note_dep_seen(dept, x1)) {
      // look in this bucket for redundant assertions
      const int stride = 2;
      for (int i = deps->length(); (i -= stride) >= 0; ) {
        DepValue y1 = deps->at(i+1);
        if (x1 == y1) {  // same subject; check the context
          if (maybe_merge_ctxk(deps, i+0, x0)) {
            return;
          }
        }
      }
    }
  } else {
    if (note_dep_seen(dept, x0) && note_dep_seen(dept, x1)) {
      // look in this bucket for redundant assertions
      const int stride = 2;
      for (int i = deps->length(); (i -= stride) >= 0; ) {
        DepValue y0 = deps->at(i+0);
        DepValue y1 = deps->at(i+1);
        if (x0 == y0 && x1 == y1) {
          return;
        }
      }
    }
  }

  // append the assertion in the correct bucket:
  deps->append(x0);
  deps->append(x1);
}
#endif // INCLUDE_JVMCI

/// Support for encoding dependencies into an nmethod:

void Dependencies::copy_to(nmethod* nm) {
  address beg = nm->dependencies_begin();
  address end = nm->dependencies_end();
  guarantee(end - beg >= (ptrdiff_t) size_in_bytes(), "bad sizing");
  Copy::disjoint_words((HeapWord*) content_bytes(),
                       (HeapWord*) beg,
                       size_in_bytes() / sizeof(HeapWord));
  assert(size_in_bytes() % sizeof(HeapWord) == 0, "copy by words");
}

static int sort_dep(ciBaseObject** p1, ciBaseObject** p2, int narg) {
  for (int i = 0; i < narg; i++) {
    int diff = p1[i]->ident() - p2[i]->ident();
    if (diff != 0)  return diff;
  }
  return 0;
}
static int sort_dep_arg_1(ciBaseObject** p1, ciBaseObject** p2)
{ return sort_dep(p1, p2, 1); }
static int sort_dep_arg_2(ciBaseObject** p1, ciBaseObject** p2)
{ return sort_dep(p1, p2, 2); }
static int sort_dep_arg_3(ciBaseObject** p1, ciBaseObject** p2)
{ return sort_dep(p1, p2, 3); }

#if INCLUDE_JVMCI
// metadata deps are sorted before object deps
static int sort_dep_value(Dependencies::DepValue* p1, Dependencies::DepValue* p2, int narg) {
  for (int i = 0; i < narg; i++) {
    int diff = p1[i].sort_key() - p2[i].sort_key();
    if (diff != 0)  return diff;
  }
  return 0;
}
static int sort_dep_value_arg_1(Dependencies::DepValue* p1, Dependencies::DepValue* p2)
{ return sort_dep_value(p1, p2, 1); }
static int sort_dep_value_arg_2(Dependencies::DepValue* p1, Dependencies::DepValue* p2)
{ return sort_dep_value(p1, p2, 2); }
static int sort_dep_value_arg_3(Dependencies::DepValue* p1, Dependencies::DepValue* p2)
{ return sort_dep_value(p1, p2, 3); }
#endif // INCLUDE_JVMCI

void Dependencies::sort_all_deps() {
#if INCLUDE_JVMCI
  if (_using_dep_values) {
    for (int deptv = (int)FIRST_TYPE; deptv < (int)TYPE_LIMIT; deptv++) {
      DepType dept = (DepType)deptv;
      GrowableArray<DepValue>* deps = _dep_values[dept];
      if (deps->length() <= 1)  continue;
      switch (dep_args(dept)) {
      case 1: deps->sort(sort_dep_value_arg_1, 1); break;
      case 2: deps->sort(sort_dep_value_arg_2, 2); break;
      case 3: deps->sort(sort_dep_value_arg_3, 3); break;
      default: ShouldNotReachHere();
      }
    }
    return;
  }
#endif // INCLUDE_JVMCI
  for (int deptv = (int)FIRST_TYPE; deptv < (int)TYPE_LIMIT; deptv++) {
    DepType dept = (DepType)deptv;
    GrowableArray<ciBaseObject*>* deps = _deps[dept];
    if (deps->length() <= 1)  continue;
    switch (dep_args(dept)) {
    case 1: deps->sort(sort_dep_arg_1, 1); break;
    case 2: deps->sort(sort_dep_arg_2, 2); break;
    case 3: deps->sort(sort_dep_arg_3, 3); break;
    default: ShouldNotReachHere();
    }
  }
}

size_t Dependencies::estimate_size_in_bytes() {
  size_t est_size = 100;
#if INCLUDE_JVMCI
  if (_using_dep_values) {
    for (int deptv = (int)FIRST_TYPE; deptv < (int)TYPE_LIMIT; deptv++) {
      DepType dept = (DepType)deptv;
      GrowableArray<DepValue>* deps = _dep_values[dept];
      est_size += deps->length() * 2;  // tags and argument(s)
    }
    return est_size;
  }
#endif // INCLUDE_JVMCI
  for (int deptv = (int)FIRST_TYPE; deptv < (int)TYPE_LIMIT; deptv++) {
    DepType dept = (DepType)deptv;
    GrowableArray<ciBaseObject*>* deps = _deps[dept];
    est_size += deps->length()*2;  // tags and argument(s)
  }
  return est_size;
}

ciKlass* Dependencies::ctxk_encoded_as_null(DepType dept, ciBaseObject* x) {
  switch (dept) {
  case abstract_with_exclusive_concrete_subtypes_2:
    return x->as_metadata()->as_klass();
  case unique_concrete_method:
  case exclusive_concrete_methods_2:
    return x->as_metadata()->as_method()->holder();
  }
  return NULL;  // let NULL be NULL
}

Klass* Dependencies::ctxk_encoded_as_null(DepType dept, Metadata* x) {
  assert(must_be_in_vm(), "raw oops here");
  switch (dept) {
  case abstract_with_exclusive_concrete_subtypes_2:
    assert(x->is_klass(), "sanity");
    return (Klass*) x;
  case unique_concrete_method:
  case exclusive_concrete_methods_2:
    assert(x->is_method(), "sanity");
    return ((Method*)x)->method_holder();
  }
  return NULL;  // let NULL be NULL
}

void Dependencies::encode_content_bytes() {
  sort_all_deps();

  // cast is safe, no deps can overflow INT_MAX
  CompressedWriteStream bytes((int)estimate_size_in_bytes());

#if INCLUDE_JVMCI
  if (_using_dep_values) {
    for (int deptv = (int)FIRST_TYPE; deptv < (int)TYPE_LIMIT; deptv++) {
      DepType dept = (DepType)deptv;
      GrowableArray<DepValue>* deps = _dep_values[dept];
      if (deps->length() == 0)  continue;
      int stride = dep_args(dept);
      int ctxkj  = dep_context_arg(dept);  // -1 if no context arg
      assert(stride > 0, "sanity");
      for (int i = 0; i < deps->length(); i += stride) {
        jbyte code_byte = (jbyte)dept;
        int skipj = -1;
        if (ctxkj >= 0 && ctxkj+1 < stride) {
          Klass*  ctxk = deps->at(i+ctxkj+0).as_klass(_oop_recorder);
          DepValue x = deps->at(i+ctxkj+1);  // following argument
          if (ctxk == ctxk_encoded_as_null(dept, x.as_metadata(_oop_recorder))) {
            skipj = ctxkj;  // we win:  maybe one less oop to keep track of
            code_byte |= default_context_type_bit;
          }
        }
        bytes.write_byte(code_byte);
        for (int j = 0; j < stride; j++) {
          if (j == skipj)  continue;
          DepValue v = deps->at(i+j);
          int idx = v.index();
          bytes.write_int(idx);
        }
      }
    }
  } else {
#endif // INCLUDE_JVMCI
  for (int deptv = (int)FIRST_TYPE; deptv < (int)TYPE_LIMIT; deptv++) {
    DepType dept = (DepType)deptv;
    GrowableArray<ciBaseObject*>* deps = _deps[dept];
    if (deps->length() == 0)  continue;
    int stride = dep_args(dept);
    int ctxkj  = dep_context_arg(dept);  // -1 if no context arg
    assert(stride > 0, "sanity");
    for (int i = 0; i < deps->length(); i += stride) {
      jbyte code_byte = (jbyte)dept;
      int skipj = -1;
      if (ctxkj >= 0 && ctxkj+1 < stride) {
        ciKlass*  ctxk = deps->at(i+ctxkj+0)->as_metadata()->as_klass();
        ciBaseObject* x     = deps->at(i+ctxkj+1);  // following argument
        if (ctxk == ctxk_encoded_as_null(dept, x)) {
          skipj = ctxkj;  // we win:  maybe one less oop to keep track of
          code_byte |= default_context_type_bit;
        }
      }
      bytes.write_byte(code_byte);
      for (int j = 0; j < stride; j++) {
        if (j == skipj)  continue;
        ciBaseObject* v = deps->at(i+j);
        int idx;
        if (v->is_object()) {
          idx = _oop_recorder->find_index(v->as_object()->constant_encoding());
        } else {
          ciMetadata* meta = v->as_metadata();
          idx = _oop_recorder->find_index(meta->constant_encoding());
        }
        bytes.write_int(idx);
      }
    }
  }
#if INCLUDE_JVMCI
  }
#endif

  // write a sentinel byte to mark the end
  bytes.write_byte(end_marker);

  // round it out to a word boundary
  while (bytes.position() % sizeof(HeapWord) != 0) {
    bytes.write_byte(end_marker);
  }

  // check whether the dept byte encoding really works
  assert((jbyte)default_context_type_bit != 0, "byte overflow");

  _content_bytes = bytes.buffer();
  _size_in_bytes = bytes.position();
}


const char* Dependencies::_dep_name[TYPE_LIMIT] = {
  "end_marker",
  "evol_method",
  "leaf_type",
  "abstract_with_unique_concrete_subtype",
  "abstract_with_no_concrete_subtype",
  "concrete_with_no_concrete_subtype",
  "unique_concrete_method",
  "abstract_with_exclusive_concrete_subtypes_2",
  "exclusive_concrete_methods_2",
  "no_finalizable_subclasses",
  "call_site_target_value"
};

int Dependencies::_dep_args[TYPE_LIMIT] = {
  -1,// end_marker
  1, // evol_method m
  1, // leaf_type ctxk
  2, // abstract_with_unique_concrete_subtype ctxk, k
  1, // abstract_with_no_concrete_subtype ctxk
  1, // concrete_with_no_concrete_subtype ctxk
  2, // unique_concrete_method ctxk, m
  3, // unique_concrete_subtypes_2 ctxk, k1, k2
  3, // unique_concrete_methods_2 ctxk, m1, m2
  1, // no_finalizable_subclasses ctxk
  2  // call_site_target_value call_site, method_handle
};

const char* Dependencies::dep_name(Dependencies::DepType dept) {
  if (!dept_in_mask(dept, all_types))  return "?bad-dep?";
  return _dep_name[dept];
}

int Dependencies::dep_args(Dependencies::DepType dept) {
  if (!dept_in_mask(dept, all_types))  return -1;
  return _dep_args[dept];
}

void Dependencies::check_valid_dependency_type(DepType dept) {
  guarantee(FIRST_TYPE <= dept && dept < TYPE_LIMIT, "invalid dependency type: %d", (int) dept);
}

// for the sake of the compiler log, print out current dependencies:
void Dependencies::log_all_dependencies() {
  if (log() == NULL)  return;
  ResourceMark rm;
  for (int deptv = (int)FIRST_TYPE; deptv < (int)TYPE_LIMIT; deptv++) {
    DepType dept = (DepType)deptv;
    GrowableArray<ciBaseObject*>* deps = _deps[dept];
    int deplen = deps->length();
    if (deplen == 0) {
      continue;
    }
    int stride = dep_args(dept);
    GrowableArray<ciBaseObject*>* ciargs = new GrowableArray<ciBaseObject*>(stride);
    for (int i = 0; i < deps->length(); i += stride) {
      for (int j = 0; j < stride; j++) {
        // flush out the identities before printing
        ciargs->push(deps->at(i+j));
      }
      write_dependency_to(log(), dept, ciargs);
      ciargs->clear();
    }
    guarantee(deplen == deps->length(), "deps array cannot grow inside nested ResoureMark scope");
  }
}

void Dependencies::write_dependency_to(CompileLog* log,
                                       DepType dept,
                                       GrowableArray<DepArgument>* args,
                                       Klass* witness) {
  if (log == NULL) {
    return;
  }
  ResourceMark rm;
  ciEnv* env = ciEnv::current();
  GrowableArray<ciBaseObject*>* ciargs = new GrowableArray<ciBaseObject*>(args->length());
  for (GrowableArrayIterator<DepArgument> it = args->begin(); it != args->end(); ++it) {
    DepArgument arg = *it;
    if (arg.is_oop()) {
      ciargs->push(env->get_object(arg.oop_value()));
    } else {
      ciargs->push(env->get_metadata(arg.metadata_value()));
    }
  }
  int argslen = ciargs->length();
  Dependencies::write_dependency_to(log, dept, ciargs, witness);
  guarantee(argslen == ciargs->length(), "ciargs array cannot grow inside nested ResoureMark scope");
}

void Dependencies::write_dependency_to(CompileLog* log,
                                       DepType dept,
                                       GrowableArray<ciBaseObject*>* args,
                                       Klass* witness) {
  if (log == NULL) {
    return;
  }
  ResourceMark rm;
  GrowableArray<int>* argids = new GrowableArray<int>(args->length());
  for (GrowableArrayIterator<ciBaseObject*> it = args->begin(); it != args->end(); ++it) {
    ciBaseObject* obj = *it;
    if (obj->is_object()) {
      argids->push(log->identify(obj->as_object()));
    } else {
      argids->push(log->identify(obj->as_metadata()));
    }
  }
  if (witness != NULL) {
    log->begin_elem("dependency_failed");
  } else {
    log->begin_elem("dependency");
  }
  log->print(" type='%s'", dep_name(dept));
  const int ctxkj = dep_context_arg(dept);  // -1 if no context arg
  if (ctxkj >= 0 && ctxkj < argids->length()) {
    log->print(" ctxk='%d'", argids->at(ctxkj));
  }
  // write remaining arguments, if any.
  for (int j = 0; j < argids->length(); j++) {
    if (j == ctxkj)  continue;  // already logged
    if (j == 1) {
      log->print(  " x='%d'",    argids->at(j));
    } else {
      log->print(" x%d='%d'", j, argids->at(j));
    }
  }
  if (witness != NULL) {
    log->object("witness", witness);
    log->stamp();
  }
  log->end_elem();
}

void Dependencies::write_dependency_to(xmlStream* xtty,
                                       DepType dept,
                                       GrowableArray<DepArgument>* args,
                                       Klass* witness) {
  if (xtty == NULL) {
    return;
  }
  ResourceMark rm;
  ttyLocker ttyl;
  int ctxkj = dep_context_arg(dept);  // -1 if no context arg
  if (witness != NULL) {
    xtty->begin_elem("dependency_failed");
  } else {
    xtty->begin_elem("dependency");
  }
  xtty->print(" type='%s'", dep_name(dept));
  if (ctxkj >= 0) {
    xtty->object("ctxk", args->at(ctxkj).metadata_value());
  }
  // write remaining arguments, if any.
  for (int j = 0; j < args->length(); j++) {
    if (j == ctxkj)  continue;  // already logged
    DepArgument arg = args->at(j);
    if (j == 1) {
      if (arg.is_oop()) {
        xtty->object("x", arg.oop_value());
      } else {
        xtty->object("x", arg.metadata_value());
      }
    } else {
      char xn[10]; sprintf(xn, "x%d", j);
      if (arg.is_oop()) {
        xtty->object(xn, arg.oop_value());
      } else {
        xtty->object(xn, arg.metadata_value());
      }
    }
  }
  if (witness != NULL) {
    xtty->object("witness", witness);
    xtty->stamp();
  }
  xtty->end_elem();
}

void Dependencies::print_dependency(DepType dept, GrowableArray<DepArgument>* args,
                                    Klass* witness, outputStream* st) {
  ResourceMark rm;
  ttyLocker ttyl;   // keep the following output all in one block
  st->print_cr("%s of type %s",
                (witness == NULL)? "Dependency": "Failed dependency",
                dep_name(dept));
  // print arguments
  int ctxkj = dep_context_arg(dept);  // -1 if no context arg
  for (int j = 0; j < args->length(); j++) {
    DepArgument arg = args->at(j);
    bool put_star = false;
    if (arg.is_null())  continue;
    const char* what;
    if (j == ctxkj) {
      assert(arg.is_metadata(), "must be");
      what = "context";
      put_star = !Dependencies::is_concrete_klass((Klass*)arg.metadata_value());
    } else if (arg.is_method()) {
      what = "method ";
      put_star = !Dependencies::is_concrete_method((Method*)arg.metadata_value(), NULL);
    } else if (arg.is_klass()) {
      what = "class  ";
    } else {
      what = "object ";
    }
    st->print("  %s = %s", what, (put_star? "*": ""));
    if (arg.is_klass()) {
      st->print("%s", ((Klass*)arg.metadata_value())->external_name());
    } else if (arg.is_method()) {
      ((Method*)arg.metadata_value())->print_value_on(st);
    } else if (arg.is_oop()) {
      arg.oop_value()->print_value_on(st);
    } else {
      ShouldNotReachHere(); // Provide impl for this type.
    }

    st->cr();
  }
  if (witness != NULL) {
    bool put_star = !Dependencies::is_concrete_klass(witness);
    st->print_cr("  witness = %s%s",
                  (put_star? "*": ""),
                  witness->external_name());
  }
}

void Dependencies::DepStream::log_dependency(Klass* witness) {
  if (_deps == NULL && xtty == NULL)  return;  // fast cutout for runtime
  ResourceMark rm;
  const int nargs = argument_count();
  GrowableArray<DepArgument>* args = new GrowableArray<DepArgument>(nargs);
  for (int j = 0; j < nargs; j++) {
    if (is_oop_argument(j)) {
      args->push(argument_oop(j));
    } else {
      args->push(argument(j));
    }
  }
  int argslen = args->length();
  if (_deps != NULL && _deps->log() != NULL) {
    if (ciEnv::current() != NULL) {
      Dependencies::write_dependency_to(_deps->log(), type(), args, witness);
    } else {
      // Treat the CompileLog as an xmlstream instead
      Dependencies::write_dependency_to((xmlStream*)_deps->log(), type(), args, witness);
    }
  } else {
    Dependencies::write_dependency_to(xtty, type(), args, witness);
  }
  guarantee(argslen == args->length(), "args array cannot grow inside nested ResoureMark scope");
}

void Dependencies::DepStream::print_dependency(Klass* witness, bool verbose, outputStream* st) {
  ResourceMark rm;
  int nargs = argument_count();
  GrowableArray<DepArgument>* args = new GrowableArray<DepArgument>(nargs);
  for (int j = 0; j < nargs; j++) {
    if (is_oop_argument(j)) {
      args->push(argument_oop(j));
    } else {
      args->push(argument(j));
    }
  }
  int argslen = args->length();
  Dependencies::print_dependency(type(), args, witness, st);
  if (verbose) {
    if (_code != NULL) {
      st->print("  code: ");
      _code->print_value_on(st);
      st->cr();
    }
  }
  guarantee(argslen == args->length(), "args array cannot grow inside nested ResoureMark scope");
}


/// Dependency stream support (decodes dependencies from an nmethod):

#ifdef ASSERT
void Dependencies::DepStream::initial_asserts(size_t byte_limit) {
  assert(must_be_in_vm(), "raw oops here");
  _byte_limit = byte_limit;
  _type       = (DepType)(end_marker-1);  // defeat "already at end" assert
  assert((_code!=NULL) + (_deps!=NULL) == 1, "one or t'other");
}
#endif //ASSERT

bool Dependencies::DepStream::next() {
  assert(_type != end_marker, "already at end");
  if (_bytes.position() == 0 && _code != NULL
      && _code->dependencies_size() == 0) {
    // Method has no dependencies at all.
    return false;
  }
  int code_byte = (_bytes.read_byte() & 0xFF);
  if (code_byte == end_marker) {
    DEBUG_ONLY(_type = end_marker);
    return false;
  } else {
    int ctxk_bit = (code_byte & Dependencies::default_context_type_bit);
    code_byte -= ctxk_bit;
    DepType dept = (DepType)code_byte;
    _type = dept;
    Dependencies::check_valid_dependency_type(dept);
    int stride = _dep_args[dept];
    assert(stride == dep_args(dept), "sanity");
    int skipj = -1;
    if (ctxk_bit != 0) {
      skipj = 0;  // currently the only context argument is at zero
      assert(skipj == dep_context_arg(dept), "zero arg always ctxk");
    }
    for (int j = 0; j < stride; j++) {
      _xi[j] = (j == skipj)? 0: _bytes.read_int();
    }
    DEBUG_ONLY(_xi[stride] = -1);   // help detect overruns
    return true;
  }
}

inline Metadata* Dependencies::DepStream::recorded_metadata_at(int i) {
  Metadata* o = NULL;
  if (_code != NULL) {
    o = _code->metadata_at(i);
  } else {
    o = _deps->oop_recorder()->metadata_at(i);
  }
  return o;
}

inline oop Dependencies::DepStream::recorded_oop_at(int i) {
  return (_code != NULL)
         ? _code->oop_at(i)
    : JNIHandles::resolve(_deps->oop_recorder()->oop_at(i));
}

Metadata* Dependencies::DepStream::argument(int i) {
  Metadata* result = recorded_metadata_at(argument_index(i));

  if (result == NULL) { // Explicit context argument can be compressed
    int ctxkj = dep_context_arg(type());  // -1 if no explicit context arg
    if (ctxkj >= 0 && i == ctxkj && ctxkj+1 < argument_count()) {
      result = ctxk_encoded_as_null(type(), argument(ctxkj+1));
    }
  }

  assert(result == NULL || result->is_klass() || result->is_method(), "must be");
  return result;
}

/**
 * Returns a unique identifier for each dependency argument.
 */
uintptr_t Dependencies::DepStream::get_identifier(int i) {
  if (is_oop_argument(i)) {
    return (uintptr_t)(oopDesc*)argument_oop(i);
  } else {
    return (uintptr_t)argument(i);
  }
}

oop Dependencies::DepStream::argument_oop(int i) {
  oop result = recorded_oop_at(argument_index(i));
  assert(result == NULL || result->is_oop(), "must be");
  return result;
}

Klass* Dependencies::DepStream::context_type() {
  assert(must_be_in_vm(), "raw oops here");

  // Most dependencies have an explicit context type argument.
  {
    int ctxkj = dep_context_arg(type());  // -1 if no explicit context arg
    if (ctxkj >= 0) {
      Metadata* k = argument(ctxkj);
      assert(k != NULL && k->is_klass(), "type check");
      return (Klass*)k;
    }
  }

  // Some dependencies are using the klass of the first object
  // argument as implicit context type.
  {
    int ctxkj = dep_implicit_context_arg(type());
    if (ctxkj >= 0) {
      Klass* k = argument_oop(ctxkj)->klass();
      assert(k != NULL && k->is_klass(), "type check");
      return (Klass*) k;
    }
  }

  // And some dependencies don't have a context type at all,
  // e.g. evol_method.
  return NULL;
}

// ----------------- DependencySignature --------------------------------------
bool DependencySignature::equals(DependencySignature const& s1, DependencySignature const& s2) {
  if ((s1.type() != s2.type()) || (s1.args_count() != s2.args_count())) {
    return false;
  }

  for (int i = 0; i < s1.args_count(); i++) {
    if (s1.arg(i) != s2.arg(i)) {
      return false;
    }
  }
  return true;
}

/// Checking dependencies:

// This hierarchy walker inspects subtypes of a given type,
// trying to find a "bad" class which breaks a dependency.
// Such a class is called a "witness" to the broken dependency.
// While searching around, we ignore "participants", which
// are already known to the dependency.
class ClassHierarchyWalker {
 public:
  enum { PARTICIPANT_LIMIT = 3 };

 private:
  // optional method descriptor to check for:
  Symbol* _name;
  Symbol* _signature;

  // special classes which are not allowed to be witnesses:
  Klass*    _participants[PARTICIPANT_LIMIT+1];
  int       _num_participants;

  // cache of method lookups
  Method* _found_methods[PARTICIPANT_LIMIT+1];

  // if non-zero, tells how many witnesses to convert to participants
  int       _record_witnesses;

  void initialize(Klass* participant) {
    _record_witnesses = 0;
    _participants[0]  = participant;
    _found_methods[0] = NULL;
    _num_participants = 0;
    if (participant != NULL) {
      // Terminating NULL.
      _participants[1] = NULL;
      _found_methods[1] = NULL;
      _num_participants = 1;
    }
  }

  void initialize_from_method(Method* m) {
    assert(m != NULL && m->is_method(), "sanity");
    _name      = m->name();
    _signature = m->signature();
  }

 public:
  // The walker is initialized to recognize certain methods and/or types
  // as friendly participants.
  ClassHierarchyWalker(Klass* participant, Method* m) {
    initialize_from_method(m);
    initialize(participant);
  }
  ClassHierarchyWalker(Method* m) {
    initialize_from_method(m);
    initialize(NULL);
  }
  ClassHierarchyWalker(Klass* participant = NULL) {
    _name      = NULL;
    _signature = NULL;
    initialize(participant);
  }

  // This is common code for two searches:  One for concrete subtypes,
  // the other for concrete method implementations and overrides.
  bool doing_subtype_search() {
    return _name == NULL;
  }

  int num_participants() { return _num_participants; }
  Klass* participant(int n) {
    assert((uint)n <= (uint)_num_participants, "oob");
    return _participants[n];
  }

  // Note:  If n==num_participants, returns NULL.
  Method* found_method(int n) {
    assert((uint)n <= (uint)_num_participants, "oob");
    Method* fm = _found_methods[n];
    assert(n == _num_participants || fm != NULL, "proper usage");
    if (fm != NULL && fm->method_holder() != _participants[n]) {
      // Default methods from interfaces can be added to classes. In
      // that case the holder of the method is not the class but the
      // interface where it's defined.
      assert(fm->is_default_method(), "sanity");
      return NULL;
    }
    return fm;
  }

#ifdef ASSERT
  // Assert that m is inherited into ctxk, without intervening overrides.
  // (May return true even if this is not true, in corner cases where we punt.)
  bool check_method_context(Klass* ctxk, Method* m) {
    if (m->method_holder() == ctxk)
      return true;  // Quick win.
    if (m->is_private())
      return false; // Quick lose.  Should not happen.
    if (!(m->is_public() || m->is_protected()))
      // The override story is complex when packages get involved.
      return true;  // Must punt the assertion to true.
    Klass* k = ctxk;
    Method* lm = k->lookup_method(m->name(), m->signature());
    if (lm == NULL && k->is_instance_klass()) {
      // It might be an interface method
      lm = InstanceKlass::cast(k)->lookup_method_in_ordered_interfaces(m->name(),
                                                                 m->signature());
    }
    if (lm == m)
      // Method m is inherited into ctxk.
      return true;
    if (lm != NULL) {
      if (!(lm->is_public() || lm->is_protected())) {
        // Method is [package-]private, so the override story is complex.
        return true;  // Must punt the assertion to true.
      }
      if (lm->is_static()) {
        // Static methods don't override non-static so punt
        return true;
      }
      if (   !Dependencies::is_concrete_method(lm, k)
          && !Dependencies::is_concrete_method(m, ctxk)
          && lm->method_holder()->is_subtype_of(m->method_holder()))
        // Method m is overridden by lm, but both are non-concrete.
        return true;
    }
    ResourceMark rm;
    tty->print_cr("Dependency method not found in the associated context:");
    tty->print_cr("  context = %s", ctxk->external_name());
    tty->print(   "  method = "); m->print_short_name(tty); tty->cr();
    if (lm != NULL) {
      tty->print( "  found = "); lm->print_short_name(tty); tty->cr();
    }
    return false;
  }
#endif

  void add_participant(Klass* participant) {
    assert(_num_participants + _record_witnesses < PARTICIPANT_LIMIT, "oob");
    int np = _num_participants++;
    _participants[np] = participant;
    _participants[np+1] = NULL;
    _found_methods[np+1] = NULL;
  }

  void record_witnesses(int add) {
    if (add > PARTICIPANT_LIMIT)  add = PARTICIPANT_LIMIT;
    assert(_num_participants + add < PARTICIPANT_LIMIT, "oob");
    _record_witnesses = add;
  }

  bool is_witness(Klass* k) {
    if (doing_subtype_search()) {
      return Dependencies::is_concrete_klass(k);
    } else if (!k->is_instance_klass()) {
      return false; // no methods to find in an array type
    } else {
      // Search class hierarchy first.
      Method* m = InstanceKlass::cast(k)->find_instance_method(_name, _signature);
      if (!Dependencies::is_concrete_method(m, k)) {
        // Check interface defaults also, if any exist.
        Array<Method*>* default_methods = InstanceKlass::cast(k)->default_methods();
        if (default_methods == NULL)
            return false;
        m = InstanceKlass::cast(k)->find_method(default_methods, _name, _signature);
        if (!Dependencies::is_concrete_method(m, NULL))
            return false;
      }
      _found_methods[_num_participants] = m;
      // Note:  If add_participant(k) is called,
      // the method m will already be memoized for it.
      return true;
    }
  }

  bool is_participant(Klass* k) {
    if (k == _participants[0]) {
      return true;
    } else if (_num_participants <= 1) {
      return false;
    } else {
      return in_list(k, &_participants[1]);
    }
  }
  bool ignore_witness(Klass* witness) {
    if (_record_witnesses == 0) {
      return false;
    } else {
      --_record_witnesses;
      add_participant(witness);
      return true;
    }
  }
  static bool in_list(Klass* x, Klass** list) {
    for (int i = 0; ; i++) {
      Klass* y = list[i];
      if (y == NULL)  break;
      if (y == x)  return true;
    }
    return false;  // not in list
  }

 private:
  // the actual search method:
  Klass* find_witness_anywhere(Klass* context_type,
                                 bool participants_hide_witnesses,
                                 bool top_level_call = true);
  // the spot-checking version:
  Klass* find_witness_in(KlassDepChange& changes,
                         Klass* context_type,
                           bool participants_hide_witnesses);
 public:
  Klass* find_witness_subtype(Klass* context_type, KlassDepChange* changes = NULL) {
    assert(doing_subtype_search(), "must set up a subtype search");
    // When looking for unexpected concrete types,
    // do not look beneath expected ones.
    const bool participants_hide_witnesses = true;
    // CX > CC > C' is OK, even if C' is new.
    // CX > { CC,  C' } is not OK if C' is new, and C' is the witness.
    if (changes != NULL) {
      return find_witness_in(*changes, context_type, participants_hide_witnesses);
    } else {
      return find_witness_anywhere(context_type, participants_hide_witnesses);
    }
  }
  Klass* find_witness_definer(Klass* context_type, KlassDepChange* changes = NULL) {
    assert(!doing_subtype_search(), "must set up a method definer search");
    // When looking for unexpected concrete methods,
    // look beneath expected ones, to see if there are overrides.
    const bool participants_hide_witnesses = true;
    // CX.m > CC.m > C'.m is not OK, if C'.m is new, and C' is the witness.
    if (changes != NULL) {
      return find_witness_in(*changes, context_type, !participants_hide_witnesses);
    } else {
      return find_witness_anywhere(context_type, !participants_hide_witnesses);
    }
  }
};

#ifndef PRODUCT
static int deps_find_witness_calls = 0;
static int deps_find_witness_steps = 0;
static int deps_find_witness_recursions = 0;
static int deps_find_witness_singles = 0;
static int deps_find_witness_print = 0; // set to -1 to force a final print
static bool count_find_witness_calls() {
  if (TraceDependencies || LogCompilation) {
    int pcount = deps_find_witness_print + 1;
    bool final_stats      = (pcount == 0);
    bool initial_call     = (pcount == 1);
    bool occasional_print = ((pcount & ((1<<10) - 1)) == 0);
    if (pcount < 0)  pcount = 1; // crude overflow protection
    deps_find_witness_print = pcount;
    if (VerifyDependencies && initial_call) {
      tty->print_cr("Warning:  TraceDependencies results may be inflated by VerifyDependencies");
    }
    if (occasional_print || final_stats) {
      // Every now and then dump a little info about dependency searching.
      if (xtty != NULL) {
       ttyLocker ttyl;
       xtty->elem("deps_find_witness calls='%d' steps='%d' recursions='%d' singles='%d'",
                   deps_find_witness_calls,
                   deps_find_witness_steps,
                   deps_find_witness_recursions,
                   deps_find_witness_singles);
      }
      if (final_stats || (TraceDependencies && WizardMode)) {
        ttyLocker ttyl;
        tty->print_cr("Dependency check (find_witness) "
                      "calls=%d, steps=%d (avg=%.1f), recursions=%d, singles=%d",
                      deps_find_witness_calls,
                      deps_find_witness_steps,
                      (double)deps_find_witness_steps / deps_find_witness_calls,
                      deps_find_witness_recursions,
                      deps_find_witness_singles);
      }
    }
    return true;
  }
  return false;
}
#else
#define count_find_witness_calls() (0)
#endif //PRODUCT


Klass* ClassHierarchyWalker::find_witness_in(KlassDepChange& changes,
                                               Klass* context_type,
                                               bool participants_hide_witnesses) {
  assert(changes.involves_context(context_type), "irrelevant dependency");
  Klass* new_type = changes.new_type();

  (void)count_find_witness_calls();
  NOT_PRODUCT(deps_find_witness_singles++);

  // Current thread must be in VM (not native mode, as in CI):
  assert(must_be_in_vm(), "raw oops here");
  // Must not move the class hierarchy during this check:
  assert_locked_or_safepoint(Compile_lock);

  int nof_impls = InstanceKlass::cast(context_type)->nof_implementors();
  if (nof_impls > 1) {
    // Avoid this case: *I.m > { A.m, C }; B.m > C
    // %%% Until this is fixed more systematically, bail out.
    // See corresponding comment in find_witness_anywhere.
    return context_type;
  }

  assert(!is_participant(new_type), "only old classes are participants");
  if (participants_hide_witnesses) {
    // If the new type is a subtype of a participant, we are done.
    for (int i = 0; i < num_participants(); i++) {
      Klass* part = participant(i);
      if (part == NULL)  continue;
      assert(changes.involves_context(part) == new_type->is_subtype_of(part),
             "correct marking of participants, b/c new_type is unique");
      if (changes.involves_context(part)) {
        // new guy is protected from this check by previous participant
        return NULL;
      }
    }
  }

  if (is_witness(new_type) &&
      !ignore_witness(new_type)) {
    return new_type;
  }

  return NULL;
}


// Walk hierarchy under a context type, looking for unexpected types.
// Do not report participant types, and recursively walk beneath
// them only if participants_hide_witnesses is false.
// If top_level_call is false, skip testing the context type,
// because the caller has already considered it.
Klass* ClassHierarchyWalker::find_witness_anywhere(Klass* context_type,
                                                     bool participants_hide_witnesses,
                                                     bool top_level_call) {
  // Current thread must be in VM (not native mode, as in CI):
  assert(must_be_in_vm(), "raw oops here");
  // Must not move the class hierarchy during this check:
  assert_locked_or_safepoint(Compile_lock);

  bool do_counts = count_find_witness_calls();

  // Check the root of the sub-hierarchy first.
  if (top_level_call) {
    if (do_counts) {
      NOT_PRODUCT(deps_find_witness_calls++);
      NOT_PRODUCT(deps_find_witness_steps++);
    }
    if (is_participant(context_type)) {
      if (participants_hide_witnesses)  return NULL;
      // else fall through to search loop...
    } else if (is_witness(context_type) && !ignore_witness(context_type)) {
      // The context is an abstract class or interface, to start with.
      return context_type;
    }
  }

  // Now we must check each implementor and each subclass.
  // Use a short worklist to avoid blowing the stack.
  // Each worklist entry is a *chain* of subklass siblings to process.
  const int CHAINMAX = 100;  // >= 1 + InstanceKlass::implementors_limit
  Klass* chains[CHAINMAX];
  int    chaini = 0;  // index into worklist
  Klass* chain;       // scratch variable
#define ADD_SUBCLASS_CHAIN(k)                     {  \
    assert(chaini < CHAINMAX, "oob");                \
    chain = k->subklass();                           \
    if (chain != NULL)  chains[chaini++] = chain;    }

  // Look for non-abstract subclasses.
  // (Note:  Interfaces do not have subclasses.)
  ADD_SUBCLASS_CHAIN(context_type);

  // If it is an interface, search its direct implementors.
  // (Their subclasses are additional indirect implementors.
  // See InstanceKlass::add_implementor.)
  // (Note:  nof_implementors is always zero for non-interfaces.)
  if (top_level_call) {
    int nof_impls = InstanceKlass::cast(context_type)->nof_implementors();
    if (nof_impls > 1) {
      // Avoid this case: *I.m > { A.m, C }; B.m > C
      // Here, I.m has 2 concrete implementations, but m appears unique
      // as A.m, because the search misses B.m when checking C.
      // The inherited method B.m was getting missed by the walker
      // when interface 'I' was the starting point.
      // %%% Until this is fixed more systematically, bail out.
      // (Old CHA had the same limitation.)
      return context_type;
    }
    if (nof_impls > 0) {
      Klass* impl = InstanceKlass::cast(context_type)->implementor();
      assert(impl != NULL, "just checking");
      // If impl is the same as the context_type, then more than one
      // implementor has seen. No exact info in this case.
      if (impl == context_type) {
        return context_type;  // report an inexact witness to this sad affair
      }
      if (do_counts)
        { NOT_PRODUCT(deps_find_witness_steps++); }
      if (is_participant(impl)) {
        if (!participants_hide_witnesses) {
          ADD_SUBCLASS_CHAIN(impl);
        }
      } else if (is_witness(impl) && !ignore_witness(impl)) {
        return impl;
      } else {
        ADD_SUBCLASS_CHAIN(impl);
      }
    }
  }

  // Recursively process each non-trivial sibling chain.
  while (chaini > 0) {
    Klass* chain = chains[--chaini];
    for (Klass* sub = chain; sub != NULL; sub = sub->next_sibling()) {
      if (do_counts) { NOT_PRODUCT(deps_find_witness_steps++); }
      if (is_participant(sub)) {
        if (participants_hide_witnesses)  continue;
        // else fall through to process this guy's subclasses
      } else if (is_witness(sub) && !ignore_witness(sub)) {
        return sub;
      }
      if (chaini < (VerifyDependencies? 2: CHAINMAX)) {
        // Fast path.  (Partially disabled if VerifyDependencies.)
        ADD_SUBCLASS_CHAIN(sub);
      } else {
        // Worklist overflow.  Do a recursive call.  Should be rare.
        // The recursive call will have its own worklist, of course.
        // (Note that sub has already been tested, so that there is
        // no need for the recursive call to re-test.  That's handy,
        // since the recursive call sees sub as the context_type.)
        if (do_counts) { NOT_PRODUCT(deps_find_witness_recursions++); }
        Klass* witness = find_witness_anywhere(sub,
                                                 participants_hide_witnesses,
                                                 /*top_level_call=*/ false);
        if (witness != NULL)  return witness;
      }
    }
  }

  // No witness found.  The dependency remains unbroken.
  return NULL;
#undef ADD_SUBCLASS_CHAIN
}


bool Dependencies::is_concrete_klass(Klass* k) {
  if (k->is_abstract())  return false;
  // %%% We could treat classes which are concrete but
  // have not yet been instantiated as virtually abstract.
  // This would require a deoptimization barrier on first instantiation.
  //if (k->is_not_instantiated())  return false;
  return true;
}

bool Dependencies::is_concrete_method(Method* m, Klass * k) {
  // NULL is not a concrete method,
  // statics are irrelevant to virtual call sites,
  // abstract methods are not concrete,
  // overpass (error) methods are not concrete if k is abstract
  //
  // note "true" is conservative answer --
  //     overpass clause is false if k == NULL, implies return true if
  //     answer depends on overpass clause.
  return ! ( m == NULL || m -> is_static() || m -> is_abstract() ||
             m->is_overpass() && k != NULL && k -> is_abstract() );
}


Klass* Dependencies::find_finalizable_subclass(Klass* k) {
  if (k->is_interface())  return NULL;
  if (k->has_finalizer()) return k;
  k = k->subklass();
  while (k != NULL) {
    Klass* result = find_finalizable_subclass(k);
    if (result != NULL) return result;
    k = k->next_sibling();
  }
  return NULL;
}


bool Dependencies::is_concrete_klass(ciInstanceKlass* k) {
  if (k->is_abstract())  return false;
  // We could also return false if k does not yet appear to be
  // instantiated, if the VM version supports this distinction also.
  //if (k->is_not_instantiated())  return false;
  return true;
}

bool Dependencies::has_finalizable_subclass(ciInstanceKlass* k) {
  return k->has_finalizable_subclass();
}


// Any use of the contents (bytecodes) of a method must be
// marked by an "evol_method" dependency, if those contents
// can change.  (Note: A method is always dependent on itself.)
Klass* Dependencies::check_evol_method(Method* m) {
  assert(must_be_in_vm(), "raw oops here");
  // Did somebody do a JVMTI RedefineClasses while our backs were turned?
  // Or is there a now a breakpoint?
  // (Assumes compiled code cannot handle bkpts; change if UseFastBreakpoints.)
  if (m->is_old()
      || m->number_of_breakpoints() > 0) {
    return m->method_holder();
  } else {
    return NULL;
  }
}

// This is a strong assertion:  It is that the given type
// has no subtypes whatever.  It is most useful for
// optimizing checks on reflected types or on array types.
// (Checks on types which are derived from real instances
// can be optimized more strongly than this, because we
// know that the checked type comes from a concrete type,
// and therefore we can disregard abstract types.)
Klass* Dependencies::check_leaf_type(Klass* ctxk) {
  assert(must_be_in_vm(), "raw oops here");
  assert_locked_or_safepoint(Compile_lock);
  InstanceKlass* ctx = InstanceKlass::cast(ctxk);
  Klass* sub = ctx->subklass();
  if (sub != NULL) {
    return sub;
  } else if (ctx->nof_implementors() != 0) {
    // if it is an interface, it must be unimplemented
    // (if it is not an interface, nof_implementors is always zero)
    Klass* impl = ctx->implementor();
    assert(impl != NULL, "must be set");
    return impl;
  } else {
    return NULL;
  }
}

// Test the assertion that conck is the only concrete subtype* of ctxk.
// The type conck itself is allowed to have have further concrete subtypes.
// This allows the compiler to narrow occurrences of ctxk by conck,
// when dealing with the types of actual instances.
Klass* Dependencies::check_abstract_with_unique_concrete_subtype(Klass* ctxk,
                                                                   Klass* conck,
                                                                   KlassDepChange* changes) {
  ClassHierarchyWalker wf(conck);
  return wf.find_witness_subtype(ctxk, changes);
}

// If a non-concrete class has no concrete subtypes, it is not (yet)
// instantiatable.  This can allow the compiler to make some paths go
// dead, if they are gated by a test of the type.
Klass* Dependencies::check_abstract_with_no_concrete_subtype(Klass* ctxk,
                                                               KlassDepChange* changes) {
  // Find any concrete subtype, with no participants:
  ClassHierarchyWalker wf;
  return wf.find_witness_subtype(ctxk, changes);
}


// If a concrete class has no concrete subtypes, it can always be
// exactly typed.  This allows the use of a cheaper type test.
Klass* Dependencies::check_concrete_with_no_concrete_subtype(Klass* ctxk,
                                                               KlassDepChange* changes) {
  // Find any concrete subtype, with only the ctxk as participant:
  ClassHierarchyWalker wf(ctxk);
  return wf.find_witness_subtype(ctxk, changes);
}


// Find the unique concrete proper subtype of ctxk, or NULL if there
// is more than one concrete proper subtype.  If there are no concrete
// proper subtypes, return ctxk itself, whether it is concrete or not.
// The returned subtype is allowed to have have further concrete subtypes.
// That is, return CC1 for CX > CC1 > CC2, but NULL for CX > { CC1, CC2 }.
Klass* Dependencies::find_unique_concrete_subtype(Klass* ctxk) {
  ClassHierarchyWalker wf(ctxk);   // Ignore ctxk when walking.
  wf.record_witnesses(1);          // Record one other witness when walking.
  Klass* wit = wf.find_witness_subtype(ctxk);
  if (wit != NULL)  return NULL;   // Too many witnesses.
  Klass* conck = wf.participant(0);
  if (conck == NULL) {
#ifndef PRODUCT
    // Make sure the dependency mechanism will pass this discovery:
    if (VerifyDependencies) {
      // Turn off dependency tracing while actually testing deps.
      FlagSetting fs(TraceDependencies, false);
      if (!Dependencies::is_concrete_klass(ctxk)) {
        guarantee(NULL ==
                  (void *)check_abstract_with_no_concrete_subtype(ctxk),
                  "verify dep.");
      } else {
        guarantee(NULL ==
                  (void *)check_concrete_with_no_concrete_subtype(ctxk),
                  "verify dep.");
      }
    }
#endif //PRODUCT
    return ctxk;                   // Return ctxk as a flag for "no subtypes".
  } else {
#ifndef PRODUCT
    // Make sure the dependency mechanism will pass this discovery:
    if (VerifyDependencies) {
      // Turn off dependency tracing while actually testing deps.
      FlagSetting fs(TraceDependencies, false);
      if (!Dependencies::is_concrete_klass(ctxk)) {
        guarantee(NULL == (void *)
                  check_abstract_with_unique_concrete_subtype(ctxk, conck),
                  "verify dep.");
      }
    }
#endif //PRODUCT
    return conck;
  }
}

// Test the assertion that the k[12] are the only concrete subtypes of ctxk,
// except possibly for further subtypes of k[12] themselves.
// The context type must be abstract.  The types k1 and k2 are themselves
// allowed to have further concrete subtypes.
Klass* Dependencies::check_abstract_with_exclusive_concrete_subtypes(
                                                Klass* ctxk,
                                                Klass* k1,
                                                Klass* k2,
                                                KlassDepChange* changes) {
  ClassHierarchyWalker wf;
  wf.add_participant(k1);
  wf.add_participant(k2);
  return wf.find_witness_subtype(ctxk, changes);
}

// Search ctxk for concrete implementations.  If there are klen or fewer,
// pack them into the given array and return the number.
// Otherwise, return -1, meaning the given array would overflow.
// (Note that a return of 0 means there are exactly no concrete subtypes.)
// In this search, if ctxk is concrete, it will be reported alone.
// For any type CC reported, no proper subtypes of CC will be reported.
int Dependencies::find_exclusive_concrete_subtypes(Klass* ctxk,
                                                   int klen,
                                                   Klass* karray[]) {
  ClassHierarchyWalker wf;
  wf.record_witnesses(klen);
  Klass* wit = wf.find_witness_subtype(ctxk);
  if (wit != NULL)  return -1;  // Too many witnesses.
  int num = wf.num_participants();
  assert(num <= klen, "oob");
  // Pack the result array with the good news.
  for (int i = 0; i < num; i++)
    karray[i] = wf.participant(i);
#ifndef PRODUCT
  // Make sure the dependency mechanism will pass this discovery:
  if (VerifyDependencies) {
    // Turn off dependency tracing while actually testing deps.
    FlagSetting fs(TraceDependencies, false);
    switch (Dependencies::is_concrete_klass(ctxk)? -1: num) {
    case -1: // ctxk was itself concrete
      guarantee(num == 1 && karray[0] == ctxk, "verify dep.");
      break;
    case 0:
      guarantee(NULL == (void *)check_abstract_with_no_concrete_subtype(ctxk),
                "verify dep.");
      break;
    case 1:
      guarantee(NULL == (void *)
                check_abstract_with_unique_concrete_subtype(ctxk, karray[0]),
                "verify dep.");
      break;
    case 2:
      guarantee(NULL == (void *)
                check_abstract_with_exclusive_concrete_subtypes(ctxk,
                                                                karray[0],
                                                                karray[1]),
                "verify dep.");
      break;
    default:
      ShouldNotReachHere();  // klen > 2 yet supported
    }
  }
#endif //PRODUCT
  return num;
}

// If a class (or interface) has a unique concrete method uniqm, return NULL.
// Otherwise, return a class that contains an interfering method.
Klass* Dependencies::check_unique_concrete_method(Klass* ctxk, Method* uniqm,
                                                    KlassDepChange* changes) {
  // Here is a missing optimization:  If uniqm->is_final(),
  // we don't really need to search beneath it for overrides.
  // This is probably not important, since we don't use dependencies
  // to track final methods.  (They can't be "definalized".)
  ClassHierarchyWalker wf(uniqm->method_holder(), uniqm);
  return wf.find_witness_definer(ctxk, changes);
}

// Find the set of all non-abstract methods under ctxk that match m.
// (The method m must be defined or inherited in ctxk.)
// Include m itself in the set, unless it is abstract.
// If this set has exactly one element, return that element.
Method* Dependencies::find_unique_concrete_method(Klass* ctxk, Method* m) {
  // Return NULL if m is marked old; must have been a redefined method.
  if (m->is_old()) {
    return NULL;
  }
  ClassHierarchyWalker wf(m);
  assert(wf.check_method_context(ctxk, m), "proper context");
  wf.record_witnesses(1);
  Klass* wit = wf.find_witness_definer(ctxk);
  if (wit != NULL)  return NULL;  // Too many witnesses.
  Method* fm = wf.found_method(0);  // Will be NULL if num_parts == 0.
  if (Dependencies::is_concrete_method(m, ctxk)) {
    if (fm == NULL) {
      // It turns out that m was always the only implementation.
      fm = m;
    } else if (fm != m) {
      // Two conflicting implementations after all.
      // (This can happen if m is inherited into ctxk and fm overrides it.)
      return NULL;
    }
  }
#ifndef PRODUCT
  // Make sure the dependency mechanism will pass this discovery:
  if (VerifyDependencies && fm != NULL) {
    guarantee(NULL == (void *)check_unique_concrete_method(ctxk, fm),
              "verify dep.");
  }
#endif //PRODUCT
  return fm;
}

Klass* Dependencies::check_exclusive_concrete_methods(Klass* ctxk,
                                                        Method* m1,
                                                        Method* m2,
                                                        KlassDepChange* changes) {
  ClassHierarchyWalker wf(m1);
  wf.add_participant(m1->method_holder());
  wf.add_participant(m2->method_holder());
  return wf.find_witness_definer(ctxk, changes);
}

Klass* Dependencies::check_has_no_finalizable_subclasses(Klass* ctxk, KlassDepChange* changes) {
  Klass* search_at = ctxk;
  if (changes != NULL)
    search_at = changes->new_type(); // just look at the new bit
  return find_finalizable_subclass(search_at);
}

Klass* Dependencies::check_call_site_target_value(oop call_site, oop method_handle, CallSiteDepChange* changes) {
  assert(!oopDesc::is_null(call_site), "sanity");
  assert(!oopDesc::is_null(method_handle), "sanity");
  assert(call_site->is_a(SystemDictionary::CallSite_klass()),     "sanity");

  if (changes == NULL) {
    // Validate all CallSites
    if (java_lang_invoke_CallSite::target(call_site) != method_handle)
      return call_site->klass();  // assertion failed
  } else {
    // Validate the given CallSite
    if (call_site == changes->call_site() && java_lang_invoke_CallSite::target(call_site) != changes->method_handle()) {
      assert(method_handle != changes->method_handle(), "must be");
      return call_site->klass();  // assertion failed
    }
  }
  return NULL;  // assertion still valid
}

void Dependencies::DepStream::trace_and_log_witness(Klass* witness) {
  if (witness != NULL) {
    if (TraceDependencies) {
      print_dependency(witness, /*verbose=*/ true);
    }
    // The following is a no-op unless logging is enabled:
    log_dependency(witness);
  }
}


Klass* Dependencies::DepStream::check_klass_dependency(KlassDepChange* changes) {
  assert_locked_or_safepoint(Compile_lock);
  Dependencies::check_valid_dependency_type(type());

  Klass* witness = NULL;
  switch (type()) {
  case evol_method:
    witness = check_evol_method(method_argument(0));
    break;
  case leaf_type:
    witness = check_leaf_type(context_type());
    break;
  case abstract_with_unique_concrete_subtype:
    witness = check_abstract_with_unique_concrete_subtype(context_type(), type_argument(1), changes);
    break;
  case abstract_with_no_concrete_subtype:
    witness = check_abstract_with_no_concrete_subtype(context_type(), changes);
    break;
  case concrete_with_no_concrete_subtype:
    witness = check_concrete_with_no_concrete_subtype(context_type(), changes);
    break;
  case unique_concrete_method:
    witness = check_unique_concrete_method(context_type(), method_argument(1), changes);
    break;
  case abstract_with_exclusive_concrete_subtypes_2:
    witness = check_abstract_with_exclusive_concrete_subtypes(context_type(), type_argument(1), type_argument(2), changes);
    break;
  case exclusive_concrete_methods_2:
    witness = check_exclusive_concrete_methods(context_type(), method_argument(1), method_argument(2), changes);
    break;
  case no_finalizable_subclasses:
    witness = check_has_no_finalizable_subclasses(context_type(), changes);
    break;
  default:
    witness = NULL;
    break;
  }
  trace_and_log_witness(witness);
  return witness;
}


Klass* Dependencies::DepStream::check_call_site_dependency(CallSiteDepChange* changes) {
  assert_locked_or_safepoint(Compile_lock);
  Dependencies::check_valid_dependency_type(type());

  Klass* witness = NULL;
  switch (type()) {
  case call_site_target_value:
    witness = check_call_site_target_value(argument_oop(0), argument_oop(1), changes);
    break;
  default:
    witness = NULL;
    break;
  }
  trace_and_log_witness(witness);
  return witness;
}


Klass* Dependencies::DepStream::spot_check_dependency_at(DepChange& changes) {
  // Handle klass dependency
  if (changes.is_klass_change() && changes.as_klass_change()->involves_context(context_type()))
    return check_klass_dependency(changes.as_klass_change());

  // Handle CallSite dependency
  if (changes.is_call_site_change())
    return check_call_site_dependency(changes.as_call_site_change());

  // irrelevant dependency; skip it
  return NULL;
}


void DepChange::print() {
  int nsup = 0, nint = 0;
  for (ContextStream str(*this); str.next(); ) {
    Klass* k = str.klass();
    switch (str.change_type()) {
    case Change_new_type:
      tty->print_cr("  dependee = %s", k->external_name());
      break;
    case Change_new_sub:
      if (!WizardMode) {
        ++nsup;
      } else {
        tty->print_cr("  context super = %s", k->external_name());
      }
      break;
    case Change_new_impl:
      if (!WizardMode) {
        ++nint;
      } else {
        tty->print_cr("  context interface = %s", k->external_name());
      }
      break;
    }
  }
  if (nsup + nint != 0) {
    tty->print_cr("  context supers = %d, interfaces = %d", nsup, nint);
  }
}

void DepChange::ContextStream::start() {
  Klass* new_type = _changes.is_klass_change() ? _changes.as_klass_change()->new_type() : (Klass*) NULL;
  _change_type = (new_type == NULL ? NO_CHANGE : Start_Klass);
  _klass = new_type;
  _ti_base = NULL;
  _ti_index = 0;
  _ti_limit = 0;
}

bool DepChange::ContextStream::next() {
  switch (_change_type) {
  case Start_Klass:             // initial state; _klass is the new type
    _ti_base = InstanceKlass::cast(_klass)->transitive_interfaces();
    _ti_index = 0;
    _change_type = Change_new_type;
    return true;
  case Change_new_type:
    // fall through:
    _change_type = Change_new_sub;
  case Change_new_sub:
    // 6598190: brackets workaround Sun Studio C++ compiler bug 6629277
    {
      _klass = _klass->super();
      if (_klass != NULL) {
        return true;
      }
    }
    // else set up _ti_limit and fall through:
    _ti_limit = (_ti_base == NULL) ? 0 : _ti_base->length();
    _change_type = Change_new_impl;
  case Change_new_impl:
    if (_ti_index < _ti_limit) {
      _klass = _ti_base->at(_ti_index++);
      return true;
    }
    // fall through:
    _change_type = NO_CHANGE;  // iterator is exhausted
  case NO_CHANGE:
    break;
  default:
    ShouldNotReachHere();
  }
  return false;
}

void KlassDepChange::initialize() {
  // entire transaction must be under this lock:
  assert_lock_strong(Compile_lock);

  // Mark all dependee and all its superclasses
  // Mark transitive interfaces
  for (ContextStream str(*this); str.next(); ) {
    Klass* d = str.klass();
    assert(!InstanceKlass::cast(d)->is_marked_dependent(), "checking");
    InstanceKlass::cast(d)->set_is_marked_dependent(true);
  }
}

KlassDepChange::~KlassDepChange() {
  // Unmark all dependee and all its superclasses
  // Unmark transitive interfaces
  for (ContextStream str(*this); str.next(); ) {
    Klass* d = str.klass();
    InstanceKlass::cast(d)->set_is_marked_dependent(false);
  }
}

bool KlassDepChange::involves_context(Klass* k) {
  if (k == NULL || !k->is_instance_klass()) {
    return false;
  }
  InstanceKlass* ik = InstanceKlass::cast(k);
  bool is_contained = ik->is_marked_dependent();
  assert(is_contained == new_type()->is_subtype_of(k),
         "correct marking of potential context types");
  return is_contained;
}

#ifndef PRODUCT
void Dependencies::print_statistics() {
  if (deps_find_witness_print != 0) {
    // Call one final time, to flush out the data.
    deps_find_witness_print = -1;
    count_find_witness_calls();
  }
}
#endif
