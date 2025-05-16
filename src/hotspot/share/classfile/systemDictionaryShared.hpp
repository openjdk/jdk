/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_SYSTEMDICTIONARYSHARED_HPP
#define SHARE_CLASSFILE_SYSTEMDICTIONARYSHARED_HPP

#include "cds/cds_globals.hpp"
#include "cds/filemap.hpp"
#include "cds/dumpTimeClassInfo.hpp"
#include "cds/runTimeClassInfo.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/packageEntry.hpp"
#include "classfile/systemDictionary.hpp"
#include "oops/klass.hpp"
#include "oops/oopHandle.hpp"


/*===============================================================================

    Handling of the classes in the AppCDS archive

    To ensure safety and to simplify the implementation, archived classes are
    "segregated" into 2 types. The following rules describe how they
    are stored and looked up.

[1] Category of archived classes

    There are 2 disjoint groups of classes stored in the AppCDS archive:

    BUILTIN:              These classes may be defined ONLY by the BOOT/PLATFORM/APP
                          loaders.

    UNREGISTERED:         These classes may be defined ONLY by a ClassLoader
                          instance that's not listed above (using fingerprint matching)

[2] How classes from different categories are specified in the classlist:

    Starting from JDK9, each class in the classlist may be specified with
    these keywords: "id", "super", "interfaces", "loader" and "source".


    BUILTIN               Only the "id" keyword may be (optionally) specified. All other
                          keywords are forbidden.

                          The named class is looked up from the jimage and from
                          Xbootclasspath/a and CLASSPATH.

    UNREGISTERED:         The "id", "super", and "source" keywords must all be
                          specified.

                          The "interfaces" keyword must be specified if the class implements
                          one or more local interfaces. The "interfaces" keyword must not be
                          specified if the class does not implement local interfaces.

                          The named class is looked up from the location specified in the
                          "source" keyword.

    Example classlist:

    # BUILTIN
    java/lang/Object id: 0
    java/lang/Cloneable id: 1
    java/lang/String

    # UNREGISTERED
    Bar id: 3 super: 0 interfaces: 1 source: /foo.jar


[3] Identifying the category of archived classes

    BUILTIN:              (C->shared_classpath_index() >= 0)
    UNREGISTERED:         (C->shared_classpath_index() == UNREGISTERED_INDEX (-9999))

[4] Lookup of archived classes at run time:

    (a) BUILTIN loaders:

        search _builtin_dictionary

    (b) UNREGISTERED loaders:

        search _unregistered_dictionary for an entry that matches the
        (name, clsfile_len, clsfile_crc32).

===============================================================================*/
#define UNREGISTERED_INDEX -9999

class BootstrapInfo;
class ClassFileStream;
class ConstantPoolCache;
class Dictionary;
class DumpTimeClassInfo;
class DumpTimeSharedClassTable;
class RunTimeClassInfo;
class RunTimeSharedDictionary;

class SharedClassLoadingMark {
 private:
  Thread* THREAD;
  InstanceKlass* _klass;
 public:
  SharedClassLoadingMark(Thread* current, InstanceKlass* ik) : THREAD(current), _klass(ik) {}
  ~SharedClassLoadingMark() {
    assert(THREAD != nullptr, "Current thread is nullptr");
    assert(_klass != nullptr, "InstanceKlass is nullptr");
    if (HAS_PENDING_EXCEPTION) {
      if (_klass->is_shared()) {
        _klass->set_shared_loading_failed();
      }
    }
  }
};

class SystemDictionaryShared: public SystemDictionary {
  friend class LambdaProxyClassDictionary;

  struct ArchiveInfo {
    RunTimeSharedDictionary _builtin_dictionary;
    RunTimeSharedDictionary _unregistered_dictionary;

    void print_on(const char* prefix, outputStream* st, bool is_static_archive);
    void print_table_statistics(const char* prefix, outputStream* st, bool is_static_archive);
  };

private:

  static DumpTimeSharedClassTable* _dumptime_table;

  static ArchiveInfo _static_archive;
  static ArchiveInfo _dynamic_archive;

  static ArchiveInfo* get_archive(bool is_static_archive) {
    return is_static_archive ? &_static_archive : &_dynamic_archive;
  }

  static InstanceKlass* load_shared_class_for_builtin_loader(
                                               Symbol* class_name,
                                               Handle class_loader,
                                               TRAPS);
  static InstanceKlass* acquire_class_for_current_thread(
                                 InstanceKlass *ik,
                                 Handle class_loader,
                                 Handle protection_domain,
                                 const ClassFileStream* cfs,
                                 TRAPS);

  // Guaranteed to return non-null value for non-shared classes.
  // k must not be a shared class.
  static DumpTimeClassInfo* get_info(InstanceKlass* k);
  static DumpTimeClassInfo* get_info_locked(InstanceKlass* k);

  static void write_dictionary(RunTimeSharedDictionary* dictionary,
                               bool is_builtin);
  static bool is_jfr_event_class(InstanceKlass *k);
  static bool check_for_exclusion_impl(InstanceKlass* k);
  static void remove_dumptime_info(InstanceKlass* k) NOT_CDS_RETURN;
  static bool has_been_redefined(InstanceKlass* k);
  DEBUG_ONLY(static bool _class_loading_may_happen;)

  static void copy_verification_constraints_from_preimage(InstanceKlass* klass);
  static void copy_linking_constraints_from_preimage(InstanceKlass* klass);

public:
  static bool is_early_klass(InstanceKlass* k);   // Was k loaded while JvmtiExport::is_early_phase()==true
  static bool has_archived_enum_objs(InstanceKlass* ik);
  static void set_has_archived_enum_objs(InstanceKlass* ik);

  static InstanceKlass* find_builtin_class(Symbol* class_name);

  static const RunTimeClassInfo* find_record(RunTimeSharedDictionary* static_dict,
                                                   RunTimeSharedDictionary* dynamic_dict,
                                                   Symbol* name);

  static bool has_platform_or_app_classes();

  // Called by PLATFORM/APP loader only
  static InstanceKlass* find_or_load_shared_class(Symbol* class_name,
                                               Handle class_loader,
                                               TRAPS);


  static void allocate_shared_data_arrays(int size, TRAPS);

  static bool is_builtin_loader(ClassLoaderData* loader_data);

  static InstanceKlass* lookup_super_for_unregistered_class(Symbol* class_name,
                                                            Symbol* super_name,  bool is_superclass);

  static void initialize() NOT_CDS_RETURN;
  static void init_dumptime_info(InstanceKlass* k) NOT_CDS_RETURN;
  static void init_dumptime_info_from_preimage(InstanceKlass* k) NOT_CDS_RETURN;
  static void handle_class_unloading(InstanceKlass* k) NOT_CDS_RETURN;

  static Dictionary* boot_loader_dictionary() {
    return ClassLoaderData::the_null_class_loader_data()->dictionary();
  }

  static void update_shared_entry(InstanceKlass* klass, int id);
  static void set_shared_class_misc_info(InstanceKlass* k, ClassFileStream* cfs);

  static InstanceKlass* lookup_from_stream(Symbol* class_name,
                                           Handle class_loader,
                                           Handle protection_domain,
                                           const ClassFileStream* st,
                                           TRAPS);
  // "verification_constraints" are a set of checks performed by
  // VerificationType::is_reference_assignable_from when verifying a shared class during
  // dump time.
  //
  // With AppCDS, it is possible to override archived classes by calling
  // ClassLoader.defineClass() directly. SystemDictionary::load_shared_class() already
  // ensures that you cannot load a shared class if its super type(s) are changed. However,
  // we need an additional check to ensure that the verification_constraints did not change
  // between dump time and runtime.
  static void add_verification_constraint(InstanceKlass* k, Symbol* name,
                  Symbol* from_name, bool from_field_is_protected,
                  bool from_is_array, bool from_is_object,
                  bool* skip_assignability_check);
  static void check_verification_constraints(InstanceKlass* klass,
                                             TRAPS) NOT_CDS_RETURN;
  static void add_enum_klass_static_field(InstanceKlass* ik, int root_index);
  static void set_class_has_failed_verification(InstanceKlass* ik) NOT_CDS_RETURN;
  static bool has_class_failed_verification(InstanceKlass* ik) NOT_CDS_RETURN_(false);
  static bool check_linking_constraints(Thread* current, InstanceKlass* klass) NOT_CDS_RETURN_(false);
  static void record_linking_constraint(Symbol* name, InstanceKlass* klass,
                                     Handle loader1, Handle loader2) NOT_CDS_RETURN;
  static bool is_builtin(const InstanceKlass* k) {
    return (k->shared_classpath_index() != UNREGISTERED_INDEX);
  }
  static bool add_unregistered_class(Thread* current, InstanceKlass* k);
  static InstanceKlass* get_unregistered_class(Symbol* name);
  static void copy_unregistered_class_size_and_crc32(InstanceKlass* klass);

  static void finish_exclusion_checks();
  static DumpTimeSharedClassTable* dumptime_table() { return _dumptime_table; }

  static bool should_be_excluded(Klass* k);
  static bool check_for_exclusion(InstanceKlass* k, DumpTimeClassInfo* info);
  static void validate_before_archiving(InstanceKlass* k);
  static bool is_excluded_class(InstanceKlass* k);
  static void set_excluded(InstanceKlass* k);
  static void set_excluded_locked(InstanceKlass* k);
  static void set_from_class_file_load_hook(InstanceKlass* k) NOT_CDS_RETURN;
  static bool warn_excluded(InstanceKlass* k, const char* reason);
  static void dumptime_classes_do(class MetaspaceClosure* it);
  static void write_to_archive(bool is_static_archive = true);
  static void serialize_dictionary_headers(class SerializeClosure* soc,
                                           bool is_static_archive = true);
  static void serialize_vm_classes(class SerializeClosure* soc);
  static const char* loader_type_for_shared_class(Klass* k);
  static void print() { return print_on(tty); }
  static void print_on(outputStream* st) NOT_CDS_RETURN;
  static void print_shared_archive(outputStream* st, bool is_static = true) NOT_CDS_RETURN;
  static void print_table_statistics(outputStream* st) NOT_CDS_RETURN;
  static bool is_dumptime_table_empty() NOT_CDS_RETURN_(true);
  DEBUG_ONLY(static bool class_loading_may_happen() {return _class_loading_may_happen;})

#ifdef ASSERT
  // This object marks a critical period when writing the CDS archive. During this
  // period, the JVM must not load any new classes, so as to avoid adding new
  // items in the SystemDictionaryShared::_dumptime_table.
  class NoClassLoadingMark: public StackObj {
  public:
    NoClassLoadingMark() {
      assert(_class_loading_may_happen, "must not be nested");
      _class_loading_may_happen = false;
    }
    ~NoClassLoadingMark() {
      _class_loading_may_happen = true;
    }
  };
#endif

  template <typename T>
  static unsigned int hash_for_shared_dictionary_quick(T* ptr) {
    assert(MetaspaceObj::is_shared((const MetaspaceObj*)ptr), "must be");
    assert(ptr > (T*)SharedBaseAddress, "must be");
    uintx offset = uintx(ptr) - uintx(SharedBaseAddress);
    return primitive_hash<uintx>(offset);
  }

  static unsigned int hash_for_shared_dictionary(address ptr);
};

#endif // SHARE_CLASSFILE_SYSTEMDICTIONARYSHARED_HPP
