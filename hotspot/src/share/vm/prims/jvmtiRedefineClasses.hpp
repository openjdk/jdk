/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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

// Introduction:
//
// The RedefineClasses() API is used to change the definition of one or
// more classes. While the API supports redefining more than one class
// in a single call, in general, the API is discussed in the context of
// changing the definition of a single current class to a single new
// class. For clarity, the current class is will always be called
// "the_class" and the new class will always be called "scratch_class".
//
// The name "the_class" is used because there is only one structure
// that represents a specific class; redefinition does not replace the
// structure, but instead replaces parts of the structure. The name
// "scratch_class" is used because the structure that represents the
// new definition of a specific class is simply used to carry around
// the parts of the new definition until they are used to replace the
// appropriate parts in the_class. Once redefinition of a class is
// complete, scratch_class is thrown away.
//
//
// Implementation Overview:
//
// The RedefineClasses() API is mostly a wrapper around the VM op that
// does the real work. The work is split in varying degrees between
// doit_prologue(), doit() and doit_epilogue().
//
// 1) doit_prologue() is called by the JavaThread on the way to a
//    safepoint. It does parameter verification and loads scratch_class
//    which involves:
//    - parsing the incoming class definition using the_class' class
//      loader and security context
//    - linking scratch_class
//    - merging constant pools and rewriting bytecodes as needed
//      for the merged constant pool
//    - verifying the bytecodes in scratch_class
//    - setting up the constant pool cache and rewriting bytecodes
//      as needed to use the cache
//    - finally, scratch_class is compared to the_class to verify
//      that it is a valid replacement class
//    - if everything is good, then scratch_class is saved in an
//      instance field in the VM operation for the doit() call
//
//    Note: A JavaThread must do the above work.
//
// 2) doit() is called by the VMThread during a safepoint. It installs
//    the new class definition(s) which involves:
//    - retrieving the scratch_class from the instance field in the
//      VM operation
//    - house keeping (flushing breakpoints and caches, deoptimizing
//      dependent compiled code)
//    - replacing parts in the_class with parts from scratch_class
//    - adding weak reference(s) to track the obsolete but interesting
//      parts of the_class
//    - adjusting constant pool caches and vtables in other classes
//      that refer to methods in the_class. These adjustments use the
//      SystemDictionary::classes_do() facility which only allows
//      a helper method to be specified. The interesting parameters
//      that we would like to pass to the helper method are saved in
//      static global fields in the VM operation.
//    - telling the SystemDictionary to notice our changes
//
//    Note: the above work must be done by the VMThread to be safe.
//
// 3) doit_epilogue() is called by the JavaThread after the VM op
//    is finished and the safepoint is done. It simply cleans up
//    memory allocated in doit_prologue() and used in doit().
//
//
// Constant Pool Details:
//
// When the_class is redefined, we cannot just replace the constant
// pool in the_class with the constant pool from scratch_class because
// that could confuse obsolete methods that may still be running.
// Instead, the constant pool from the_class, old_cp, is merged with
// the constant pool from scratch_class, scratch_cp. The resulting
// constant pool, merge_cp, replaces old_cp in the_class.
//
// The key part of any merging algorithm is the entry comparison
// function so we have to know the types of entries in a constant pool
// in order to merge two of them together. Constant pools can contain
// up to 12 different kinds of entries; the JVM_CONSTANT_Unicode entry
// is not presently used so we only have to worry about the other 11
// entry types. For the purposes of constant pool merging, it is
// helpful to know that the 11 entry types fall into 3 different
// subtypes: "direct", "indirect" and "double-indirect".
//
// Direct CP entries contain data and do not contain references to
// other CP entries. The following are direct CP entries:
//     JVM_CONSTANT_{Double,Float,Integer,Long,Utf8}
//
// Indirect CP entries contain 1 or 2 references to a direct CP entry
// and no other data. The following are indirect CP entries:
//     JVM_CONSTANT_{Class,NameAndType,String}
//
// Double-indirect CP entries contain two references to indirect CP
// entries and no other data. The following are double-indirect CP
// entries:
//     JVM_CONSTANT_{Fieldref,InterfaceMethodref,Methodref}
//
// When comparing entries between two constant pools, the entry types
// are compared first and if they match, then further comparisons are
// made depending on the entry subtype. Comparing direct CP entries is
// simply a matter of comparing the data associated with each entry.
// Comparing both indirect and double-indirect CP entries requires
// recursion.
//
// Fortunately, the recursive combinations are limited because indirect
// CP entries can only refer to direct CP entries and double-indirect
// CP entries can only refer to indirect CP entries. The following is
// an example illustration of the deepest set of indirections needed to
// access the data associated with a JVM_CONSTANT_Fieldref entry:
//
//     JVM_CONSTANT_Fieldref {
//         class_index => JVM_CONSTANT_Class {
//             name_index => JVM_CONSTANT_Utf8 {
//                 <data-1>
//             }
//         }
//         name_and_type_index => JVM_CONSTANT_NameAndType {
//             name_index => JVM_CONSTANT_Utf8 {
//                 <data-2>
//             }
//             descriptor_index => JVM_CONSTANT_Utf8 {
//                 <data-3>
//             }
//         }
//     }
//
// The above illustration is not a data structure definition for any
// computer language. The curly braces ('{' and '}') are meant to
// delimit the context of the "fields" in the CP entry types shown.
// Each indirection from the JVM_CONSTANT_Fieldref entry is shown via
// "=>", e.g., the class_index is used to indirectly reference a
// JVM_CONSTANT_Class entry where the name_index is used to indirectly
// reference a JVM_CONSTANT_Utf8 entry which contains the interesting
// <data-1>. In order to understand a JVM_CONSTANT_Fieldref entry, we
// have to do a total of 5 indirections just to get to the CP entries
// that contain the interesting pieces of data and then we have to
// fetch the three pieces of data. This means we have to do a total of
// (5 + 3) * 2 == 16 dereferences to compare two JVM_CONSTANT_Fieldref
// entries.
//
// Here is the indirection, data and dereference count for each entry
// type:
//
//    JVM_CONSTANT_Class               1 indir, 1 data, 2 derefs
//    JVM_CONSTANT_Double              0 indir, 1 data, 1 deref
//    JVM_CONSTANT_Fieldref            2 indir, 3 data, 8 derefs
//    JVM_CONSTANT_Float               0 indir, 1 data, 1 deref
//    JVM_CONSTANT_Integer             0 indir, 1 data, 1 deref
//    JVM_CONSTANT_InterfaceMethodref  2 indir, 3 data, 8 derefs
//    JVM_CONSTANT_Long                0 indir, 1 data, 1 deref
//    JVM_CONSTANT_Methodref           2 indir, 3 data, 8 derefs
//    JVM_CONSTANT_NameAndType         1 indir, 2 data, 4 derefs
//    JVM_CONSTANT_String              1 indir, 1 data, 2 derefs
//    JVM_CONSTANT_Utf8                0 indir, 1 data, 1 deref
//
// So different subtypes of CP entries require different amounts of
// work for a proper comparison.
//
// Now that we've talked about the different entry types and how to
// compare them we need to get back to merging. This is not a merge in
// the "sort -u" sense or even in the "sort" sense. When we merge two
// constant pools, we copy all the entries from old_cp to merge_cp,
// preserving entry order. Next we append all the unique entries from
// scratch_cp to merge_cp and we track the index changes from the
// location in scratch_cp to the possibly new location in merge_cp.
// When we are done, any obsolete code that is still running that
// uses old_cp should not be able to observe any difference if it
// were to use merge_cp. As for the new code in scratch_class, it is
// modified to use the appropriate index values in merge_cp before it
// is used to replace the code in the_class.
//
// There is one small complication in copying the entries from old_cp
// to merge_cp. Two of the CP entry types are special in that they are
// lazily resolved. Before explaining the copying complication, we need
// to digress into CP entry resolution.
//
// JVM_CONSTANT_Class and JVM_CONSTANT_String entries are present in
// the class file, but are not stored in memory as such until they are
// resolved. The entries are not resolved unless they are used because
// resolution is expensive. During class file parsing the entries are
// initially stored in memory as JVM_CONSTANT_ClassIndex and
// JVM_CONSTANT_StringIndex entries. These special CP entry types
// indicate that the JVM_CONSTANT_Class and JVM_CONSTANT_String entries
// have been parsed, but the index values in the entries have not been
// validated. After the entire constant pool has been parsed, the index
// values can be validated and then the entries are converted into
// JVM_CONSTANT_UnresolvedClass and JVM_CONSTANT_UnresolvedString
// entries. During this conversion process, the UTF8 values that are
// indirectly referenced by the JVM_CONSTANT_ClassIndex and
// JVM_CONSTANT_StringIndex entries are changed into symbolOops and the
// entries are modified to refer to the symbolOops. This optimization
// eliminates one level of indirection for those two CP entry types and
// gets the entries ready for verification. During class file parsing
// it is also possible for JVM_CONSTANT_UnresolvedString entries to be
// resolved into JVM_CONSTANT_String entries. Verification expects to
// find JVM_CONSTANT_UnresolvedClass and either JVM_CONSTANT_String or
// JVM_CONSTANT_UnresolvedString entries and not JVM_CONSTANT_Class
// entries.
//
// Now we can get back to the copying complication. When we copy
// entries from old_cp to merge_cp, we have to revert any
// JVM_CONSTANT_Class entries to JVM_CONSTANT_UnresolvedClass entries
// or verification will fail.
//
// It is important to explicitly state that the merging algorithm
// effectively unresolves JVM_CONSTANT_Class entries that were in the
// old_cp when they are changed into JVM_CONSTANT_UnresolvedClass
// entries in the merge_cp. This is done both to make verification
// happy and to avoid adding more brittleness between RedefineClasses
// and the constant pool cache. By allowing the constant pool cache
// implementation to (re)resolve JVM_CONSTANT_UnresolvedClass entries
// into JVM_CONSTANT_Class entries, we avoid having to embed knowledge
// about those algorithms in RedefineClasses.
//
// Appending unique entries from scratch_cp to merge_cp is straight
// forward for direct CP entries and most indirect CP entries. For the
// indirect CP entry type JVM_CONSTANT_NameAndType and for the double-
// indirect CP entry types, the presence of more than one piece of
// interesting data makes appending the entries more complicated.
//
// For the JVM_CONSTANT_{Double,Float,Integer,Long,Utf8} entry types,
// the entry is simply copied from scratch_cp to the end of merge_cp.
// If the index in scratch_cp is different than the destination index
// in merge_cp, then the change in index value is tracked.
//
// Note: the above discussion for the direct CP entries also applies
// to the JVM_CONSTANT_Unresolved{Class,String} entry types.
//
// For the JVM_CONSTANT_{Class,String} entry types, since there is only
// one data element at the end of the recursion, we know that we have
// either one or two unique entries. If the JVM_CONSTANT_Utf8 entry is
// unique then it is appended to merge_cp before the current entry.
// If the JVM_CONSTANT_Utf8 entry is not unique, then the current entry
// is updated to refer to the duplicate entry in merge_cp before it is
// appended to merge_cp. Again, any changes in index values are tracked
// as needed.
//
// Note: the above discussion for JVM_CONSTANT_{Class,String} entry
// types is theoretical. Since those entry types have already been
// optimized into JVM_CONSTANT_Unresolved{Class,String} entry types,
// they are handled as direct CP entries.
//
// For the JVM_CONSTANT_NameAndType entry type, since there are two
// data elements at the end of the recursions, we know that we have
// between one and three unique entries. Any unique JVM_CONSTANT_Utf8
// entries are appended to merge_cp before the current entry. For any
// JVM_CONSTANT_Utf8 entries that are not unique, the current entry is
// updated to refer to the duplicate entry in merge_cp before it is
// appended to merge_cp. Again, any changes in index values are tracked
// as needed.
//
// For the JVM_CONSTANT_{Fieldref,InterfaceMethodref,Methodref} entry
// types, since there are two indirect CP entries and three data
// elements at the end of the recursions, we know that we have between
// one and six unique entries. See the JVM_CONSTANT_Fieldref diagram
// above for an example of all six entries. The uniqueness algorithm
// for the JVM_CONSTANT_Class and JVM_CONSTANT_NameAndType entries is
// covered above. Any unique entries are appended to merge_cp before
// the current entry. For any entries that are not unique, the current
// entry is updated to refer to the duplicate entry in merge_cp before
// it is appended to merge_cp. Again, any changes in index values are
// tracked as needed.
//
//
// Other Details:
//
// Details for other parts of RedefineClasses need to be written.
// This is a placeholder section.
//
//
// Open Issues (in no particular order):
//
// - How do we serialize the RedefineClasses() API without deadlocking?
//
// - SystemDictionary::parse_stream() was called with a NULL protection
//   domain since the initial version. This has been changed to pass
//   the_class->protection_domain(). This change has been tested with
//   all NSK tests and nothing broke, but what will adding it now break
//   in ways that we don't test?
//
// - GenerateOopMap::rewrite_load_or_store() has a comment in its
//   (indirect) use of the Relocator class that the max instruction
//   size is 4 bytes. goto_w and jsr_w are 5 bytes and wide/iinc is
//   6 bytes. Perhaps Relocator only needs a 4 byte buffer to do
//   what it does to the bytecodes. More investigation is needed.
//
// - java.lang.Object methods can be called on arrays. This is
//   implemented via the arrayKlassOop vtable which we don't
//   update. For example, if we redefine java.lang.Object.toString(),
//   then the new version of the method will not be called for array
//   objects.
//
// - How do we know if redefine_single_class() and the guts of
//   instanceKlass are out of sync? I don't think this can be
//   automated, but we should probably order the work in
//   redefine_single_class() to match the order of field
//   definitions in instanceKlass. We also need to add some
//   comments about keeping things in sync.
//
// - set_new_constant_pool() is huge and we should consider refactoring
//   it into smaller chunks of work.
//
// - The exception table update code in set_new_constant_pool() defines
//   const values that are also defined in a local context elsewhere.
//   The same literal values are also used in elsewhere. We need to
//   coordinate a cleanup of these constants with Runtime.
//

class VM_RedefineClasses: public VM_Operation {
 private:
  // These static fields are needed by SystemDictionary::classes_do()
  // facility and the adjust_cpool_cache_and_vtable() helper:
  static objArrayOop     _old_methods;
  static objArrayOop     _new_methods;
  static methodOop*      _matching_old_methods;
  static methodOop*      _matching_new_methods;
  static methodOop*      _deleted_methods;
  static methodOop*      _added_methods;
  static int             _matching_methods_length;
  static int             _deleted_methods_length;
  static int             _added_methods_length;
  static klassOop        _the_class_oop;

  // The instance fields are used to pass information from
  // doit_prologue() to doit() and doit_epilogue().
  jint                        _class_count;
  const jvmtiClassDefinition *_class_defs;  // ptr to _class_count defs

  // This operation is used by both RedefineClasses and
  // RetransformClasses.  Indicate which.
  JvmtiClassLoadKind          _class_load_kind;

  // _index_map_count is just an optimization for knowing if
  // _index_map_p contains any entries.
  int                         _index_map_count;
  intArray *                  _index_map_p;
  // ptr to _class_count scratch_classes
  instanceKlassHandle *       _scratch_classes;
  jvmtiError                  _res;

  // Performance measurement support. These timers do not cover all
  // the work done for JVM/TI RedefineClasses() but they do cover
  // the heavy lifting.
  elapsedTimer  _timer_rsc_phase1;
  elapsedTimer  _timer_rsc_phase2;
  elapsedTimer  _timer_vm_op_prologue;

  // These routines are roughly in call order unless otherwise noted.

  // Load the caller's new class definition(s) into _scratch_classes.
  // Constant pool merging work is done here as needed. Also calls
  // compare_and_normalize_class_versions() to verify the class
  // definition(s).
  jvmtiError load_new_class_versions(TRAPS);

  // Verify that the caller provided class definition(s) that meet
  // the restrictions of RedefineClasses. Normalize the order of
  // overloaded methods as needed.
  jvmtiError compare_and_normalize_class_versions(
    instanceKlassHandle the_class, instanceKlassHandle scratch_class);

  // Swap annotations[i] with annotations[j]
  // Used by compare_and_normalize_class_versions() when normalizing
  // overloaded methods or changing idnum as when adding or deleting methods.
  void swap_all_method_annotations(int i, int j, instanceKlassHandle scratch_class);

  // Figure out which new methods match old methods in name and signature,
  // which methods have been added, and which are no longer present
  void compute_added_deleted_matching_methods();

  // Change jmethodIDs to point to the new methods
  void update_jmethod_ids();

  // In addition to marking methods as obsolete, this routine
  // records which methods are EMCP (Equivalent Module Constant
  // Pool) in the emcp_methods BitMap and returns the number of
  // EMCP methods via emcp_method_count_p. This information is
  // used when information about the previous version of the_class
  // is squirreled away.
  void check_methods_and_mark_as_obsolete(BitMap *emcp_methods,
         int * emcp_method_count_p);
  void transfer_old_native_function_registrations(instanceKlassHandle the_class);

  // Unevolving classes may point to methods of the_class directly
  // from their constant pool caches, itables, and/or vtables. We
  // use the SystemDictionary::classes_do() facility and this helper
  // to fix up these pointers.
  static void adjust_cpool_cache_and_vtable(klassOop k_oop, oop loader, TRAPS);

  // Install the redefinition of a class
  void redefine_single_class(jclass the_jclass,
    instanceKlassHandle scratch_class, TRAPS);

  // Increment the classRedefinedCount field in the specific instanceKlass
  // and in all direct and indirect subclasses.
  void increment_class_counter(instanceKlass *ik, TRAPS);

  // Support for constant pool merging (these routines are in alpha
  // order):
  void append_entry(constantPoolHandle scratch_cp, int scratch_i,
    constantPoolHandle *merge_cp_p, int *merge_cp_length_p, TRAPS);
  int find_new_index(int old_index);
  bool is_unresolved_class_mismatch(constantPoolHandle cp1, int index1,
    constantPoolHandle cp2, int index2);
  bool is_unresolved_string_mismatch(constantPoolHandle cp1, int index1,
    constantPoolHandle cp2, int index2);
  void map_index(constantPoolHandle scratch_cp, int old_index, int new_index);
  bool merge_constant_pools(constantPoolHandle old_cp,
    constantPoolHandle scratch_cp, constantPoolHandle *merge_cp_p,
    int *merge_cp_length_p, TRAPS);
  jvmtiError merge_cp_and_rewrite(instanceKlassHandle the_class,
    instanceKlassHandle scratch_class, TRAPS);
  u2 rewrite_cp_ref_in_annotation_data(
    typeArrayHandle annotations_typeArray, int &byte_i_ref,
    const char * trace_mesg, TRAPS);
  bool rewrite_cp_refs(instanceKlassHandle scratch_class, TRAPS);
  bool rewrite_cp_refs_in_annotation_struct(
    typeArrayHandle class_annotations, int &byte_i_ref, TRAPS);
  bool rewrite_cp_refs_in_annotations_typeArray(
    typeArrayHandle annotations_typeArray, int &byte_i_ref, TRAPS);
  bool rewrite_cp_refs_in_class_annotations(
    instanceKlassHandle scratch_class, TRAPS);
  bool rewrite_cp_refs_in_element_value(
    typeArrayHandle class_annotations, int &byte_i_ref, TRAPS);
  bool rewrite_cp_refs_in_fields_annotations(
    instanceKlassHandle scratch_class, TRAPS);
  void rewrite_cp_refs_in_method(methodHandle method,
    methodHandle * new_method_p, TRAPS);
  bool rewrite_cp_refs_in_methods(instanceKlassHandle scratch_class, TRAPS);
  bool rewrite_cp_refs_in_methods_annotations(
    instanceKlassHandle scratch_class, TRAPS);
  bool rewrite_cp_refs_in_methods_default_annotations(
    instanceKlassHandle scratch_class, TRAPS);
  bool rewrite_cp_refs_in_methods_parameter_annotations(
    instanceKlassHandle scratch_class, TRAPS);
  void rewrite_cp_refs_in_stack_map_table(methodHandle method, TRAPS);
  void rewrite_cp_refs_in_verification_type_info(
         address& stackmap_addr_ref, address stackmap_end, u2 frame_i,
         u1 frame_size, TRAPS);
  void set_new_constant_pool(instanceKlassHandle scratch_class,
    constantPoolHandle scratch_cp, int scratch_cp_length, bool shrink, TRAPS);

  void flush_dependent_code(instanceKlassHandle k_h, TRAPS);

  static void check_class(klassOop k_oop, oop initiating_loader, TRAPS) PRODUCT_RETURN;

  static void dump_methods()   PRODUCT_RETURN;

 public:
  VM_RedefineClasses(jint class_count,
                     const jvmtiClassDefinition *class_defs,
                     JvmtiClassLoadKind class_load_kind);
  VMOp_Type type() const { return VMOp_RedefineClasses; }
  bool doit_prologue();
  void doit();
  void doit_epilogue();

  bool allow_nested_vm_operations() const        { return true; }
  jvmtiError check_error()                       { return _res; }

  // Modifiable test must be shared between IsModifiableClass query
  // and redefine implementation
  static bool is_modifiable_class(oop klass_mirror);
};
