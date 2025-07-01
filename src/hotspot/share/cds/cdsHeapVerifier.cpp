/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotClassInitializer.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/cdsHeapVerifier.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "oops/fieldStreams.inline.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/fieldDescriptor.inline.hpp"

#if INCLUDE_CDS_JAVA_HEAP

// CDSHeapVerifier is used to check for problems where an archived object references a
// static field that may be get a different value at runtime.
//
// *Please see comments in aotClassInitializer.cpp for how to avoid such problems*,
//
// In the following example,
//      Foo.get.test()
// correctly returns true when CDS disabled, but incorrectly returns false when CDS is enabled,
// because the archived archivedFoo.bar value is different than Bar.bar.
//
// class Foo {
//     static final Foo archivedFoo; // this field is archived by CDS
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
// However, it's legal for *some* static fields to be referenced. The reasons are explained
// in the table of ADD_EXCL below.
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
# define ADD_EXCL(...) { static const char* e[] = {__VA_ARGS__, nullptr}; add_exclusion(e); }

  // Unfortunately this needs to be manually maintained. If
  // test/hotspot/jtreg/runtime/cds/appcds/cacheObject/ArchivedEnumTest.java fails,
  // you might need to fix the core library code, or fix the ADD_EXCL entries below.
  //
  //       class                                         field                     type
  ADD_EXCL("java/lang/ClassLoader",                      "scl");                   // A
  ADD_EXCL("java/lang/Module",                           "ALL_UNNAMED_MODULE",     // A
                                                         "ALL_UNNAMED_MODULE_SET", // A
                                                         "EVERYONE_MODULE",        // A
                                                         "EVERYONE_SET");          // A

  // This is the same as java/util/ImmutableCollections::EMPTY_SET, which is archived
  ADD_EXCL("java/lang/reflect/AccessFlag$Location",      "EMPTY_SET");             // E

  ADD_EXCL("java/lang/System",                           "bootLayer");             // A

  ADD_EXCL("java/util/Collections",                      "EMPTY_LIST");            // E

  // A dummy object used by HashSet. The value doesn't matter and it's never
  // tested for equality.
  ADD_EXCL("java/util/HashSet",                          "PRESENT");               // E

  ADD_EXCL("jdk/internal/loader/BootLoader",             "UNNAMED_MODULE");        // A
  ADD_EXCL("jdk/internal/loader/BuiltinClassLoader",     "packageToModule");       // A
  ADD_EXCL("jdk/internal/loader/ClassLoaders",           "BOOT_LOADER",            // A
                                                         "APP_LOADER",             // A
                                                         "PLATFORM_LOADER");       // A
  ADD_EXCL("jdk/internal/module/Builder",                "cachedVersion");         // D
  ADD_EXCL("jdk/internal/module/ModuleLoaderMap$Mapper", "APP_CLASSLOADER",        // A
                                                         "APP_LOADER_INDEX",       // A
                                                         "PLATFORM_CLASSLOADER",   // A
                                                         "PLATFORM_LOADER_INDEX"); // A
  ADD_EXCL("jdk/internal/module/ServicesCatalog",        "CLV");                   // A

  // This just points to an empty Map
  ADD_EXCL("jdk/internal/reflect/Reflection",            "methodFilterMap");       // E

  // Integer for 0 and 1 are in java/lang/Integer$IntegerCache and are archived
  ADD_EXCL("sun/invoke/util/ValueConversions",           "ONE_INT",                // E
                                                         "ZERO_INT");              // E

  if (CDSConfig::is_dumping_method_handles()) {
    ADD_EXCL("java/lang/invoke/InvokerBytecodeGenerator", "MEMBERNAME_FACTORY",    // D
                                                          "CD_Object_array",       // E same as <...>ConstantUtils.CD_Object_array::CD_Object
                                                          "INVOKER_SUPER_DESC");   // E same as java.lang.constant.ConstantDescs::CD_Object

    ADD_EXCL("java/lang/runtime/ObjectMethods",           "CLASS_IS_INSTANCE",     // D
                                                          "FALSE",                 // D
                                                          "TRUE",                  // D
                                                          "ZERO");                 // D
  }

# undef ADD_EXCL

  ClassLoaderDataGraph::classes_do(this);
}

CDSHeapVerifier::~CDSHeapVerifier() {
  if (_problems > 0) {
    log_error(aot, heap)("Scanned %d objects. Found %d case(s) where "
                         "an object points to a static field that "
                         "may hold a different value at runtime.", _archived_objs, _problems);
    log_error(aot, heap)("Please see cdsHeapVerifier.cpp and aotClassInitializer.cpp for details");
    MetaspaceShared::unrecoverable_writing_error();
  }
}

class CDSHeapVerifier::CheckStaticFields : public FieldClosure {
  CDSHeapVerifier* _verifier;
  InstanceKlass* _ik; // The class whose static fields are being checked.
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

    if (fd->signature()->equals("Ljdk/internal/access/JavaLangAccess;")) {
      // A few classes have static fields that point to SharedSecrets.getJavaLangAccess().
      // This object carries no state and we can create a new one in the production run.
      return;
    }
    oop static_obj_field = _ik->java_mirror()->obj_field(fd->offset());
    if (static_obj_field != nullptr) {
      Klass* field_type = static_obj_field->klass();
      if (_exclusions != nullptr) {
        for (const char** p = _exclusions; *p != nullptr; p++) {
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

      if (field_type->is_instance_klass()) {
        InstanceKlass* field_ik = InstanceKlass::cast(field_type);
        if (field_ik->is_enum_subclass()) {
          if (field_ik->has_archived_enum_objs() || ArchiveUtils::has_aot_initialized_mirror(field_ik)) {
            // This field is an Enum. If any instance of this Enum has been archived, we will archive
            // all static fields of this Enum as well.
            return;
          }
        }

        if (field_ik->is_hidden() && ArchiveUtils::has_aot_initialized_mirror(field_ik)) {
          // We have a static field in a core-library class that points to a method reference, which
          // are safe to archive.
          guarantee(_ik->module()->name() == vmSymbols::java_base(), "sanity");
          return;
        }

        if (field_ik == vmClasses::MethodType_klass()) {
          // The identity of MethodTypes are preserved between assembly phase and production runs
          // (by MethodType::AOTHolder::archivedMethodTypes). No need to check.
          return;
        }

        if (field_ik == vmClasses::internal_Unsafe_klass() && ArchiveUtils::has_aot_initialized_mirror(field_ik)) {
          // There's only a single instance of jdk/internal/misc/Unsafe, so all references will
          // be pointing to this singleton, which has been archived.
          return;
        }
      }

      // This field *may* be initialized to a different value at runtime. Remember it
      // and check later if it appears in the archived object graph.
      _verifier->add_static_obj_field(_ik, static_obj_field, fd->name());
    }
  }
};

// Remember all the static object fields of every class that are currently
// loaded. Later, we will check if any archived objects reference one of
// these fields.
void CDSHeapVerifier::do_klass(Klass* k) {
  if (k->is_instance_klass()) {
    InstanceKlass* ik = InstanceKlass::cast(k);

    if (HeapShared::is_subgraph_root_class(ik)) {
      // ik is inside one of the ArchivableStaticFieldInfo tables
      // in heapShared.cpp. We assume such classes are programmed to
      // update their static fields correctly at runtime.
      return;
    }

    if (ArchiveUtils::has_aot_initialized_mirror(ik)) {
      // ik's <clinit> won't be executed at runtime, the static fields in
      // ik will carry their values to runtime.
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

// This function is called once for every archived heap object. Warn if this object is referenced by
// a static field of a class that's not aot-initialized.
inline bool CDSHeapVerifier::do_entry(oop& orig_obj, HeapShared::CachedOopInfo& value) {
  _archived_objs++;

  if (java_lang_String::is_instance(orig_obj) && HeapShared::is_dumped_interned_string(orig_obj)) {
    // It's quite often for static fields to have interned strings. These are most likely not
    // problematic (and are hard to filter). So we will ignore them.
    return true; /* keep on iterating */
  }

  StaticFieldInfo* info = _table.get(orig_obj);
  if (info != nullptr) {
    ResourceMark rm;
    char* class_name = info->_holder->name()->as_C_string();
    char* field_name = info->_name->as_C_string();
    LogStream ls(Log(aot, heap)::warning());
    ls.print_cr("Archive heap points to a static field that may hold a different value at runtime:");
    ls.print_cr("Field: %s::%s", class_name, field_name);
    ls.print("Value: ");
    orig_obj->print_on(&ls);
    ls.print_cr("--- trace begin ---");
    trace_to_root(&ls, orig_obj, nullptr, &value);
    ls.print_cr("--- trace end ---");
    ls.cr();
    _problems ++;
  }

  return true; /* keep on iterating */
}

class CDSHeapVerifier::TraceFields : public FieldClosure {
  oop _orig_obj;
  oop _orig_field;
  outputStream* _st;

public:
  TraceFields(oop orig_obj, oop orig_field, outputStream* st)
    : _orig_obj(orig_obj), _orig_field(orig_field), _st(st) {}

  void do_field(fieldDescriptor* fd) {
    if (fd->field_type() == T_OBJECT || fd->field_type() == T_ARRAY) {
      oop obj_field = _orig_obj->obj_field(fd->offset());
      if (obj_field == _orig_field) {
        _st->print("::%s (offset = %d)", fd->name()->as_C_string(), fd->offset());
      }
    }
  }
};

// Call this function (from gdb, etc) if you want to know why an object is archived.
void CDSHeapVerifier::trace_to_root(outputStream* st, oop orig_obj) {
  HeapShared::CachedOopInfo* info = HeapShared::archived_object_cache()->get(orig_obj);
  if (info != nullptr) {
    trace_to_root(st, orig_obj, nullptr, info);
  } else {
    st->print_cr("Not an archived object??");
  }
}

const char* static_field_name(oop mirror, oop field) {
  Klass* k = java_lang_Class::as_Klass(mirror);
  if (k->is_instance_klass()) {
    for (JavaFieldStream fs(InstanceKlass::cast(k)); !fs.done(); fs.next()) {
      if (fs.access_flags().is_static()) {
        fieldDescriptor& fd = fs.field_descriptor();
        switch (fd.field_type()) {
        case T_OBJECT:
        case T_ARRAY:
          if (mirror->obj_field(fd.offset()) == field) {
            return fs.name()->as_C_string();
          }
          break;
        default:
          break;
        }
      }
    }
  }

  return "<unknown>";
}

int CDSHeapVerifier::trace_to_root(outputStream* st, oop orig_obj, oop orig_field, HeapShared::CachedOopInfo* info) {
  int level = 0;
  if (info->orig_referrer() != nullptr) {
    HeapShared::CachedOopInfo* ref = HeapShared::archived_object_cache()->get(info->orig_referrer());
    assert(ref != nullptr, "sanity");
    level = trace_to_root(st, info->orig_referrer(), orig_obj, ref) + 1;
  } else if (java_lang_String::is_instance(orig_obj)) {
    st->print_cr("[%2d] (shared string table)", level++);
  }
  Klass* k = orig_obj->klass();
  ResourceMark rm;
  st->print("[%2d] ", level);
  orig_obj->print_address_on(st);
  st->print(" %s", k->internal_name());
  if (java_lang_Class::is_instance(orig_obj)) {
    st->print(" (%s::%s)", java_lang_Class::as_Klass(orig_obj)->external_name(), static_field_name(orig_obj, orig_field));
  }
  if (orig_field != nullptr) {
    if (k->is_instance_klass()) {
      TraceFields clo(orig_obj, orig_field, st);
      InstanceKlass::cast(k)->do_nonstatic_fields(&clo);
    } else {
      assert(orig_obj->is_objArray(), "must be");
      objArrayOop array = (objArrayOop)orig_obj;
      for (int i = 0; i < array->length(); i++) {
        if (array->obj_at(i) == orig_field) {
          st->print(" @[%d]", i);
          break;
        }
      }
    }
  }
  st->cr();

  return level;
}

void CDSHeapVerifier::verify() {
  CDSHeapVerifier verf;
  HeapShared::archived_object_cache()->iterate(&verf);
}

#endif // INCLUDE_CDS_JAVA_HEAP
