/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/archiveBuilder.hpp"
#include "cds/cdsHeapVerifier.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "oops/fieldStreams.inline.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/fieldDescriptor.inline.hpp"

#if INCLUDE_CDS_JAVA_HEAP

// CDSHeapVerifier is used to check for problems where an archived object references a
// static field that may be reinitialized at runtime. In the following example,
//      Foo.get.test()
// correctly returns true when CDS disabled, but incorrectly returns false when CDS is enabled.
//
// class Foo {
//     final Foo archivedFoo; // this field is archived by CDS
//     Bar bar;
//     static {
//         CDS.initializeFromArchive(Foo.class);
//         if (archivedFoo == null) {
//             archivedFoo = new Foo();
//             archivedFoo.bar = Bar.bar;
//         }
//     }
//     static Foo get() { return archivedFoo; }
//     boolean test() {
//         return bar == Bar.bar;
//     }
// }
//
// class Bar {
//     // this field is initialized in both CDS dump time and runtime.
//     static final Bar bar = new Bar();
// }
//
// The check itself is simple:
// [1] CDSHeapVerifier::do_klass() collects all static fields
// [2] CDSHeapVerifier::do_entry() checks all the archived objects. None of them
//     should be in [1]
//
// However, it's legal for *some* static fields to be references. This leads to the
// table of ADD_EXCL below.
//
// [A] In most of the cases, the module bootstrap code will update the static field
//     to point to part of the archived module graph. E.g.,
//     - java/lang/System::bootLayer
//     - jdk/internal/loader/ClassLoaders::BOOT_LOADER
// [B] A final static String that's explicitly initialized inside <clinit>, but
//     its value is deterministic and is always the same string literal.
// [C] A non-final static string that is assigned a string literal during class
//     initialization; this string is never changed during -Xshare:dump.
// [D] Simple caches whose value doesn't matter.
// [E] Other cases (see comments in-line below).

CDSHeapVerifier::CDSHeapVerifier() : _archived_objs(0), _problems(0)
{
# define ADD_EXCL(...) { static const char* e[] = {__VA_ARGS__, NULL}; add_exclusion(e); }

  // Unfortunately this needs to be manually maintained. If
  // test/hotspot/jtreg/runtime/cds/appcds/cacheObject/ArchivedEnumTest.java fails,
  // you might need to fix the core library code, or fix the ADD_EXCL entries below.
  //
  //       class                                         field                     type
  ADD_EXCL("java/lang/ClassLoader",                      "scl");                   // A
  ADD_EXCL("java/lang/invoke/InvokerBytecodeGenerator",  "DONTINLINE_SIG",         // B
                                                         "FORCEINLINE_SIG",        // B
                                                         "HIDDEN_SIG",             // B
                                                         "INJECTEDPROFILE_SIG",    // B
                                                         "LF_COMPILED_SIG");       // B
  ADD_EXCL("java/lang/Module",                           "ALL_UNNAMED_MODULE",     // A
                                                         "ALL_UNNAMED_MODULE_SET", // A
                                                         "EVERYONE_MODULE",        // A
                                                         "EVERYONE_SET");          // A
  ADD_EXCL("java/lang/System",                           "bootLayer");             // A
  ADD_EXCL("java/lang/VersionProps",                     "VENDOR_URL_BUG",         // C
                                                         "VENDOR_URL_VM_BUG",      // C
                                                         "VENDOR_VERSION");        // C
  ADD_EXCL("java/net/URL$DefaultFactory",                "PREFIX");                // B FIXME: JDK-8276561

  // A dummy object used by HashSet. The value doesn't matter and it's never
  // tested for equality.
  ADD_EXCL("java/util/HashSet",                          "PRESENT");               // E
  ADD_EXCL("jdk/internal/loader/BuiltinClassLoader",     "packageToModule");       // A
  ADD_EXCL("jdk/internal/loader/ClassLoaders",           "BOOT_LOADER",            // A
                                                         "APP_LOADER",             // A
                                                         "PLATFORM_LOADER");       // A
  ADD_EXCL("jdk/internal/loader/URLClassPath",           "JAVA_VERSION");          // B
  ADD_EXCL("jdk/internal/module/Builder",                "cachedVersion");         // D
  ADD_EXCL("jdk/internal/module/ModuleLoaderMap$Mapper", "APP_CLASSLOADER",        // A
                                                         "APP_LOADER_INDEX",       // A
                                                         "PLATFORM_CLASSLOADER",   // A
                                                         "PLATFORM_LOADER_INDEX"); // A
  ADD_EXCL("jdk/internal/module/ServicesCatalog",        "CLV");                   // A

  // This just points to an empty Map
  ADD_EXCL("jdk/internal/reflect/Reflection",            "methodFilterMap");       // E
  ADD_EXCL("jdk/internal/util/StaticProperty",           "FILE_ENCODING");         // C

  // Integer for 0 and 1 are in java/lang/Integer$IntegerCache and are archived
  ADD_EXCL("sun/invoke/util/ValueConversions",           "ONE_INT",                // E
                                                         "ZERO_INT");              // E
  ADD_EXCL("sun/security/util/SecurityConstants",        "PROVIDER_VER");          // C


# undef ADD_EXCL

  ClassLoaderDataGraph::classes_do(this);
}

CDSHeapVerifier::~CDSHeapVerifier() {
  if (_problems > 0) {
    log_warning(cds, heap)("Scanned %d objects. Found %d case(s) where "
                           "an object points to a static field that may be "
                           "reinitialized at runtime.", _archived_objs, _problems);
  }
}

class CDSHeapVerifier::CheckStaticFields : public FieldClosure {
  CDSHeapVerifier* _verifier;
  InstanceKlass* _ik;
  const char** _exclusions;
public:
  CheckStaticFields(CDSHeapVerifier* verifier, InstanceKlass* ik)
    : _verifier(verifier), _ik(ik) {
    _exclusions = _verifier->find_exclusion(_ik);
  }

  void do_field(fieldDescriptor* fd) {
    if (fd->field_type() != T_OBJECT) {
      return;
    }

    oop static_obj_field = _ik->java_mirror()->obj_field(fd->offset());
    if (static_obj_field != NULL) {
      Klass* klass = static_obj_field->klass();
      if (_exclusions != NULL) {
        for (const char** p = _exclusions; *p != NULL; p++) {
          if (fd->name()->equals(*p)) {
            return;
          }
        }
      }

      if (fd->is_final() && java_lang_String::is_instance(static_obj_field) && fd->has_initial_value()) {
        // This field looks like like this in the Java source:
        //    static final SOME_STRING = "a string literal";
        // This string literal has been stored in the shared string table, so it's OK
        // for the archived objects to refer to it.
        return;
      }
      if (fd->is_final() && java_lang_Class::is_instance(static_obj_field)) {
        // This field points to an archived mirror.
        return;
      }
      if (klass->has_archived_enum_objs()) {
        // This klass is a subclass of java.lang.Enum. If any instance of this klass
        // has been archived, we will archive all static fields of this klass.
        // See HeapShared::initialize_enum_klass().
        return;
      }

      // This field *may* be initialized to a different value at runtime. Remember it
      // and check later if it appears in the archived object graph.
      _verifier->add_static_obj_field(_ik, static_obj_field, fd->name());
    }
  }
};

// Remember all the static object fields of every class that are currently
// loaded.
void CDSHeapVerifier::do_klass(Klass* k) {
  if (k->is_instance_klass()) {
    InstanceKlass* ik = InstanceKlass::cast(k);

    if (HeapShared::is_subgraph_root_class(ik)) {
      // ik is inside one of the ArchivableStaticFieldInfo tables
      // in heapShared.cpp. We assume such classes are programmed to
      // update their static fields correctly at runtime.
      return;
    }

    CheckStaticFields csf(this, ik);
    ik->do_local_static_fields(&csf);
  }
}

void CDSHeapVerifier::add_static_obj_field(InstanceKlass* ik, oop field, Symbol* name) {
  StaticFieldInfo info = {ik, name};
  _table.put(field, info);
}

inline bool CDSHeapVerifier::do_entry(oop& orig_obj, HeapShared::CachedOopInfo& value) {
  _archived_objs++;

  StaticFieldInfo* info = _table.get(orig_obj);
  if (info != NULL) {
    ResourceMark rm;
    LogStream ls(Log(cds, heap)::warning());
    ls.print_cr("Archive heap points to a static field that may be reinitialized at runtime:");
    ls.print_cr("Field: %s::%s", info->_holder->name()->as_C_string(), info->_name->as_C_string());
    ls.print("Value: ");
    orig_obj->print_on(&ls);
    ls.print_cr("--- trace begin ---");
    trace_to_root(orig_obj, NULL, &value);
    ls.print_cr("--- trace end ---");
    ls.cr();
    _problems ++;
  }

  return true; /* keep on iterating */
}

class CDSHeapVerifier::TraceFields : public FieldClosure {
  oop _orig_obj;
  oop _orig_field;
  LogStream* _ls;

public:
  TraceFields(oop orig_obj, oop orig_field, LogStream* ls)
    : _orig_obj(orig_obj), _orig_field(orig_field), _ls(ls) {}

  void do_field(fieldDescriptor* fd) {
    if (fd->field_type() == T_OBJECT || fd->field_type() == T_ARRAY) {
      oop obj_field = _orig_obj->obj_field(fd->offset());
      if (obj_field == _orig_field) {
        _ls->print("::%s (offset = %d)", fd->name()->as_C_string(), fd->offset());
      }
    }
  }
};

// Hint: to exercise this function, uncomment out one of the ADD_EXCL lines above.
int CDSHeapVerifier::trace_to_root(oop orig_obj, oop orig_field, HeapShared::CachedOopInfo* p) {
  int level = 0;
  LogStream ls(Log(cds, heap)::warning());
  if (p->_referrer != NULL) {
    HeapShared::CachedOopInfo* ref = HeapShared::archived_object_cache()->get(p->_referrer);
    assert(ref != NULL, "sanity");
    level = trace_to_root(p->_referrer, orig_obj, ref) + 1;
  } else if (java_lang_String::is_instance(orig_obj)) {
    ls.print_cr("[%2d] (shared string table)", level++);
  }
  Klass* k = orig_obj->klass();
  ResourceMark rm;
  ls.print("[%2d] ", level);
  orig_obj->print_address_on(&ls);
  ls.print(" %s", k->internal_name());
  if (orig_field != NULL) {
    if (k->is_instance_klass()) {
      TraceFields clo(orig_obj, orig_field, &ls);;
      InstanceKlass::cast(k)->do_nonstatic_fields(&clo);
    } else {
      assert(orig_obj->is_objArray(), "must be");
      objArrayOop array = (objArrayOop)orig_obj;
      for (int i = 0; i < array->length(); i++) {
        if (array->obj_at(i) == orig_field) {
          ls.print(" @[%d]", i);
          break;
        }
      }
    }
  }
  ls.cr();

  return level;
}

#ifdef ASSERT
void CDSHeapVerifier::verify() {
  CDSHeapVerifier verf;
  HeapShared::archived_object_cache()->iterate(&verf);
}
#endif

#endif // INCLUDE_CDS_JAVA_HEAP
