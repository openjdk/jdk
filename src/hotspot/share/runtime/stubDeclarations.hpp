/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_RUNTIME_STUBDECLARATIONS_HPP
#define SHARE_RUNTIME_STUBDECLARATIONS_HPP

#include "code/codeBlob.hpp"
#include "oops/klass.hpp"
#include "utilities/macros.hpp"

// Macros for generating definitions and declarations for shared, c1,
// opto and stubgen blobs and associated stub and entry ids.
//
// The template macros that follow define blobs, stubs and entries in
// each stub group. Invocations of the macros with different macro
// arguments can be used to generate definitions and declarations of
// types, data and methods/functions which support blob, stub and
// entry management.
//
// In particular, they are used to generate 3 global enums that list
// all blobs, stubs and entries across stub groups. They are also used
// to generate local (per-stub group) enums listing every stub in the
// group. The former are provided ot allow systematic management of
// blobs, stubs and entries by generic code. The latter are used by
// code which generates and consumes stubs in a specific group. An API
// is provided to convert between global and local ids where needed
// (see class StubInfo).

// Shared stub declarations
//
// Every shared stub has a unique associated blob whose type must be
// defined as part of the stub declaration. The blob type determines
// how many entries are associated with the stub, normally 1. A build
// may optionally include some JFR stubs.
//
// n.b resolve, handler and throw stubs must remain grouped
// contiguously and in the same order so that id values can be range
// checked
//
// Alongside the global and local enums, shared declations are used to
// generate the following code elements in class SharedRuntime:
//
// Shared Stub blob fields
//
// Static field declarations/definitons for fields of class
// SharedRuntime are generated to store shared blobs
//
// static <blobtype>* _<stubname>;
//
// Shared stub field names
//
// Stubs are provided with names in the format "Shared Runtime
// <stubname> _blob".
//

#if INCLUDE_JFR
// do_blob(name, type)
#define SHARED_JFR_STUBS_DO(do_blob)                                   \
  do_blob(jfr_write_checkpoint, RuntimeStub)                           \
  do_blob(jfr_return_lease, RuntimeStub)                               \

#else
#define SHARED_JFR_STUBS_DO(do_blob)
#endif

// client macro to operate on shared stubs
//
// do_blob(name, type)
#define SHARED_STUBS_DO(do_blob)                                       \
  do_blob(deopt, DeoptimizationBlob)                                   \
  /* resolve stubs */                                                  \
  do_blob(wrong_method, RuntimeStub)                                   \
  do_blob(wrong_method_abstract, RuntimeStub)                          \
  do_blob(ic_miss, RuntimeStub)                                        \
  do_blob(resolve_opt_virtual_call, RuntimeStub)                       \
  do_blob(resolve_virtual_call, RuntimeStub)                           \
  do_blob(resolve_static_call, RuntimeStub)                            \
  /* handler stubs */                                                  \
  do_blob(polling_page_vectors_safepoint_handler, SafepointBlob)       \
  do_blob(polling_page_safepoint_handler, SafepointBlob)               \
  do_blob(polling_page_return_handler, SafepointBlob)                  \
  /* throw stubs */                                                    \
  do_blob(throw_AbstractMethodError, RuntimeStub)                      \
  do_blob(throw_IncompatibleClassChangeError, RuntimeStub)             \
  do_blob(throw_NullPointerException_at_call, RuntimeStub)             \
  do_blob(throw_StackOverflowError, RuntimeStub)                       \
  do_blob(throw_delayed_StackOverflowError, RuntimeStub)               \
  /* other stubs */                                                    \
  SHARED_JFR_STUBS_DO(do_blob)                                         \

// C1 stub declarations
//
// C1 stubs are always generated in a unique associated generic
// CodeBlob with a single entry. C1 stubs are stored in an array
// indexed by local enum. So, no other code elements need to be
// generated via this macro.

#ifdef COMPILER1
// client macro to operate on c1 stubs
//
// do_blob(name)
#define C1_STUBS_DO(do_blob)                                           \
  do_blob(dtrace_object_alloc)                                         \
  do_blob(unwind_exception)                                            \
  do_blob(forward_exception)                                           \
  do_blob(throw_range_check_failed)       /* throws ArrayIndexOutOfBoundsException */ \
  do_blob(throw_index_exception)          /* throws IndexOutOfBoundsException */ \
  do_blob(throw_div0_exception)                                        \
  do_blob(throw_null_pointer_exception)                                \
  do_blob(register_finalizer)                                          \
  do_blob(new_instance)                                                \
  do_blob(fast_new_instance)                                           \
  do_blob(fast_new_instance_init_check)                                \
  do_blob(new_type_array)                                              \
  do_blob(new_object_array)                                            \
  do_blob(new_multi_array)                                             \
  do_blob(handle_exception_nofpu)         /* optimized version that does not preserve fpu registers */ \
  do_blob(handle_exception)                                            \
  do_blob(handle_exception_from_callee)                                \
  do_blob(throw_array_store_exception)                                 \
  do_blob(throw_class_cast_exception)                                  \
  do_blob(throw_incompatible_class_change_error)                       \
  do_blob(slow_subtype_check)                                          \
  do_blob(is_instance_of)                                              \
  do_blob(monitorenter)                                                \
  do_blob(monitorenter_nofpu)             /* optimized version that does not preserve fpu registers */ \
  do_blob(monitorexit)                                                 \
  do_blob(monitorexit_nofpu)              /* optimized version that does not preserve fpu registers */ \
  do_blob(deoptimize)                                                  \
  do_blob(access_field_patching)                                       \
  do_blob(load_klass_patching)                                         \
  do_blob(load_mirror_patching)                                        \
  do_blob(load_appendix_patching)                                      \
  do_blob(fpu2long_stub)                                               \
  do_blob(counter_overflow)                                            \
  do_blob(predicate_failed_trap)                                       \

#else
#define C1_STUBS_DO(do_blob)
#endif

// C2 stub declarations
//
// C2 stubs are always generated in a unique associated generic
// CodeBlob and have a single entry. In some cases, including JVMTI
// stubs, a standard code blob is employed and only the stub entry
// address is retained. In others a specialized code blob with
// stub-specific properties (e.g. frame size) is required so the blob
// address needs to be stored. In these latter cases the declaration
// includes the relevant storage type.
//
// n.b. blob and stub enum tags are generated in the order defined by
// C2_STUBS_DO, allowing dependencies from any givem stub on its
// predecessors to be guaranteed. That explains the initial placement
// of the blob declarations and intermediate placement of the jvmti
// stubs.
//
// Alongside the local and global enums, C2 declarations are used to
// generate several elements of class OptoRuntime.
//
// C2 Stub blob/address fields
//
// Static field declarations/definitions for fields of class
// OptoRuntime are generated to store either C2 blob or C2 blob entry
// addresses:
//
// static <blobtype>* _<stubname>_Java;
// static address _<stubname>;
//
// C2 stub blob/field names
//
// C2 stubs are provided with names in the format "C2 Runtime
// <stubname> _blob".
//
// A stub creation method OptoRuntime::generate(ciEnv* env) is
// generated which invokes the C2 compiler to generate each stub in
// declaration order.

#ifdef COMPILER2
// do_jvmti_stub(name)
#if INCLUDE_JVMTI
#define C2_JVMTI_STUBS_DO(do_jvmti_stub)                               \
  do_jvmti_stub(notify_jvmti_vthread_start)                            \
  do_jvmti_stub(notify_jvmti_vthread_end)                              \
  do_jvmti_stub(notify_jvmti_vthread_mount)                            \
  do_jvmti_stub(notify_jvmti_vthread_unmount)                          \

#else
#define C2_JVMTI_STUBS_DO(do_jvmti_stub)
#endif // INCLUDE_JVMTI

// client macro to operate on c2 stubs
//
// do_blob(name, type)
// do_stub(name, fancy_jump, pass_tls, return_pc)
// do_jvmti_stub(name)
//
// do_blob is used for stubs that are generated via direct invocation
// of the assembler to write into a blob of the appropriate type
//
// do_stub is used for stubs that are generated as C2 compiler IR
// intrinsics, using the supplied arguments to determine wheher nodes
// in the IR graph employ a special type of jump (0, 1 or 2) or
// provide access to TLS and the return pc.
//
// do_jvmti_stub generates a JVMTI stub as an IR intrinsic which
// employs jump 0, and requires no special access

#define C2_STUBS_DO(do_blob, do_stub, do_jvmti_stub)                   \
  do_blob(uncommon_trap, UncommonTrapBlob)                             \
  do_blob(exception, ExceptionBlob)                                    \
  do_stub(new_instance, 0, true, false)                                \
  do_stub(new_array, 0, true, false)                                   \
  do_stub(new_array_nozero, 0, true, false)                            \
  do_stub(multianewarray2, 0, true, false)                             \
  do_stub(multianewarray3, 0, true, false)                             \
  do_stub(multianewarray4, 0, true, false)                             \
  do_stub(multianewarray5, 0, true, false)                             \
  do_stub(multianewarrayN, 0, true, false)                             \
  C2_JVMTI_STUBS_DO(do_jvmti_stub)                                     \
  do_stub(complete_monitor_locking, 0, false, false)                   \
  do_stub(monitor_notify, 0, false, false)                             \
  do_stub(monitor_notifyAll, 0, false, false)                          \
  do_stub(rethrow, 2, true, true)                                      \
  do_stub(slow_arraycopy, 0, false, false)                             \
  do_stub(register_finalizer, 0, false, false)                         \

#else
#define C2_STUBS_DO(do_blob, do_stub, do_jvmti_stub)
#endif

// Stubgen stub declarations
//
// Stub Generator Blobs, Stubs and Entries Overview
//
// StubGenerator stubs do not require their own individual blob. They
// are generated in batches into one of five distinct BufferBlobs:
//
// 1) PreUniverse stubs
// 2) Initial stubs
// 3) Continuation stubs
// 4) Compiler stubs
// 5) Final stubs
//
// Most StubGen stubs have a single entry point. However, in some
// cases there are additional entry points.
//
// Creation of each successive BufferBlob is staged to ensure that
// specific VM subsystems required by those stubs are suitably
// initialized before generated code attempts to reference data or
// addresses exported by those subsystems. The sequencing of
// initialization must be taken into account when adding a new stub
// declaration.
//
// StubGen blobs, stubs and entries are declared using template
// macros, grouped hierarchically by blob and stub, with arch-specific
// stubs for any given blob declared after generic stubs for that
// blob. Stub declarations must follow the blob start (do_blob)
// declaration for their containing blob. Entry declarations must
// follow the the stub start (do_stub) declaration for their
// containing stub.
//
// Blob and stub declarations are used to generate a variety of C++
// code elements needed to manage stubs, including the global and
// local blob, stub and entry enum types mentioned above. The blob
// declaration order must reflect the order in which blob create
// operations are invoked during startup. Stubs within a blob are
// currently generated in an order determined by the arch-specific
// generator code which may not always reflect the order of stub
// declarations (it is not straightforward to enforce a strict
// ordering, not least because arch-specific stub creation may need to
// be interleaved with generic stub creation).
//
// Alongside the global enums, the stubgen declarations are used to
// define the following elements of class StubRoutines:
//
// Stub names and associated getters:
//
// Name strings are generated for each blob where a blob declared with
// name argument <blob_name> will be named using string "<blob_name>".
//
// Name strings are also generated for each stub where a stub declared
// with name argument <stub_name> will be named using string
// "<stub_name>".
//
// Corresponding public static lookup methods are generated to allow
// names to be looked up by blob or global stub id.
//
//  const char* StubRoutines::get_blob_name(BlobId id)
//  const char* StubRoutines::get_stub_name(StubId id)
//
// These name lookup methods should be used by generic and
// cpu-specific client code to ensure that blobs and stubs are
// identified consistently.
//
// Blob code buffer sizes:
//
// An enumeration enum platform_dependent_constants is generated in
// the architecture specific StubRoutines header. For each blob named
// <nnn> an associated enum tag is generated which defines the
// relevant size
//
//  _<nnn>_stubs_code_size      = <size>,
//
// For example,
//
// enum platform_dependent_constants {
//   _preuniverse_stubs_code_size  =   500,
//   _initial_stubs_code_size      = 10000,
//   _continuation_stubs_code_size =  2000,
//   . . .
//
// Blob fields and associated getters:
//
// For each blob named <nnn> a private field declaration will be
// generated: static field address StubRoutines::_<nnn>_stubs_code and
// a declaration provided to initialise it to nullptr. A corresponding
// public getter method address StubRoutines::_<nnn>_stubs_code() will
// be generated.
//
// Blob initialization routines:
//
// For each blob named <nnn> an initalization function is defined
// which allows clients to schedule blob and stub generation during
// JVM bootstrap:
//
// void <nnn>_stubs_init() { StubRoutines::initialize_<nnn>_stubs(); }
//
// A declaration and definition of each underlying implementation
// method StubRoutines::initialize_<nnn>_stubs() is also generated.
//
// Stub entry points and associated getters:
//
// Some generated stubs require their main entry point and, possibly,
// auxiliary entry points to be stored in fields declared either as
// members of class SharedRuntime. For stubs that are specific to a
// given cpu, the field needs to be declared in an arch-specific inner
// class of SharedRuntime.
//
// For a generic stub named <nnn> the corresponding main entry usually
// has the same name: static field address StubRoutines::_<nnn> modulo
// an _ prefix.  An associated getter method is also generated, again
// normally using the same name: address StubRoutines::<nnn>() e.g.
//
//  class StubRoutines {
//    . . .
//    static address _aescrypt_encryptBlock;
//    . . .
//    address aescrypt_encryptBlock() { return _aescrypt_encryptBlock; }
//
// Multiple fields and getters may be generated where a stub has more
// than one entry point, each provided with their own unique field and
// getter name e.g.
//
//    . . .
//    static address _call_stub;
//    static address _call_stub_return_address;
//    . . .
//    static address call_stub_entry() { return _call_stub; }
//    static address call_stub_return_address() { return _call_stub_return_address; }
//
// In special cases a stub may declare a (compile-time) fixed size
// array of entries, in which case an address array field is
// generated,along with a getter that accepts an index as argument:
//
//    . . .
//   static address _lookup_secondary_supers_table[Klass::SECONDARY_SUPERS_TABLE_SIZE];
//   . . .
//   static address lookup_secondary_supers_table(int i);
//
// CPU-specific stub entry points and associated getters:
//
// For an arch-specific stub with name <nnn> belonging to architecture
// <arch> private field address StubRoutines::<arch>::_<nnn> is
// generated to hold the entry address. An associated public getter
// method address StubRoutines::<arch>::<nnn>() is also generated e.g.
//
//  class StubRoutines {
//    . . .
//    class x86 {
//      . . .
//      static address _f2i_fixup;
//      . . .
//      static address f2i_fixup() { return _f2i_fixup; }
//      static void set_f2i_fixup(address a) { _f2i_fixup = a; }
//

//--------------------------------------------------
// Stub Generator Blob, Stub and Entry Declarations
// -------------------------------------------------
//
// The formal declarations of blobs, stubs and entries provided below
// are used to schedule application of template macros that either
// declare or define the C++ code we need to manage those blobs, stubs
// and entries.
//
// All ports employ the same blobs. However, the organization of the
// stubs and entry points in a blob can vary from one port to the
// next. A template macro is provided to specify the details of each
// blob, including generic and arch-specific variations.
//
// If you want to define a new stub or entry then you can do so by
// adding suitable declarations within the scope of the relevant blob.
// For the blob with name BLOB_NAME add your declarations to macro
// STUBGEN_<BLOB_NAME>_STUBS_DO. Generic stubs and entries are
// declared using the do_stub, do_entry and do_entry_init and
// array_entry templates (see below for full details). The do_blob
// and end_blob templates should never need to be modified.
//
// Some stubs and their associated entries are architecture-specific.
// They need to be declared in the architecture-specific header file
// src/cpu/<arch>stubDecolaration_<arch>.cpp. For the blob with name
// BLOB_NAME the correspnding declarations macro are provided by macro
// STUBGEN_<BLOB_NAME>_STUBS_ARCH_DO. Arch-specific stubs and entries
// are declared using the do_stub, do_arch_entry and
// do_arch_entry_init templates (see below for details). An
// architecure also needs to specify architecture parameters used when
// creating each blob. These are defined using the do_arch_blob
// template (see below).
//
// Note, the client macro STUBGEN_ALL_DO is provided to allow client
// code to iterate over all blob, stub or entry declarations. It has
// only been split into separate per-blob generic submacros,
// STUBGEN_<BLOB_NAME>_BLOBS_DO and arch-specific per-blob submacros
// STUBGEN_<BLOB_NAME>_BLOBS_ARCH_DO for convenience, to make it
// easier to manage definitions. The blob_specific sub-macros should
// not be called directly by client code (in class StubRoutines and
// StubGenerator),
//
// A client wishing to generate blob, stub or entry code elements is
// expected to pass template macros as arguments to STUBGEN_ALL_DO.
// This will schedule code generation code for whatever C++ code
// elements are required to implement a declaration or definition
// relevant to each blob, stub or entry. Alternatively, a client can
// operate on a subset of the declarations by calling macros
// STUBGEN_BLOBS_DO, STUBGEN_STUBS_DO, STUBGEN_BLOBS_STUBS_DO,
// STUBGEN_ENTRIES_DO and STUBGEN_ARCH_ENTRIES_DO.
//
// The do_blob and end_blob templates receive a blob name as argument.
//
// do_blob(blob_name)
// end_blob(blob_name)
//
// do_blob is primarily used to define a global enum tag for a blob
// and an associated constant string name, both for use by client
// code.
//
// end_blob is provided for use in combination with do_blob to to open
// and close a blob-local enum type identifying all stubs within a
// given blob. This enum is private to the stub management code and
// used to validate correct use of stubs within a given blob.
//
// The do_stub template receives a blob name and stub name as
// argument.
//
// do_stub(blob_name, stub_name)
//
// do_stub is primarily used to define values associated with the stub
// wiht name stub_name, a global enum tag for it and a constant string
// name, both for use by client code. It is also used to declare a tag
// within the blob-local enum type used to validate correct use of
// stubs within their declared blob. Finally, it is also used to
// declare a name string for the stub.
//
// The do_entry and do_entry_array templates receive 4 or 5 arguments
//
// do_entry(blob_name, stub_name, field_name, getter_name)
//
// do_entry_init(blob_name, stub_name, field_name, getter_name, init_function)
//
// do_entry_array(blob_name, stub_name, field_name, getter_name, count)
//
// do_entry is used to declare or define a static field of class
// StubRoutines with type address that stores a specific entry point
// for a given stub. n.b. the number of entries associated with a stub
// is often one but it can be more than one and, in a few special
// cases, it is zero. do_entry is also used to declare and define an
// associated getter method for the field. do_entry is used to declare
// fields that should be initialized to nullptr.
//
// do_entry_init is used when the field needs to be initialized a
// specific function or method .
//
// do_entry_array is used for the special case where a stub employs an
// array to store multiple entries which are stored at generate time
// and subsequently accessed using an associated index (e.g. the
// secondary supers table stub which has 63 qassociated entries).
// Note that this distinct from the case where a stub generates
// multiple entries each of them stored in its own named field with
// its own named getter. In the latter case multiple do_entry or
// do_entry_init declarations are associated with the stub.
//
// All the above entry macros are used to declare enum tages that
// identify the entry. Three different enums are generated via these
// macros: a per-stub enum that indexes and provides a count for the
// entries associated with the owning stub; a per-blob enume that
// indexes and provides a count for the entries associated with the
// owning blob; and a global enum that indexes and provides a count
// for all entries associated with generated stubs.
//
// blob_name and stub_name are the names of the blob and stub to which
// the entry belongs.
//
// field_name is prefixed with a leading '_' to produce the name of
// the field used to store an entry address for the stub. For stubs
// with one entry field_name is normally, but not always, the same as
// stub_name.  Obviously when a stub has multiple entries secondary
// names must be different to stub_name. For normal entry declarations
// the field type is address. For do_entry_array declarations the field
// type is an address[] whose size is defined by then parameter.
//
// getter_name is the name of a getter that is generated to allow
// access to the field. It is normally, but not always, the same as
// stub_name. For normal entry declarations the getter signature is
// (void).  For do_entry_array declarations the getter signature is
// (int).
//
// init_function is the name of an function or method which should be
// assigned to the field as a default value (n.b. fields declared
// using do_entry are intialised to nullptr, array fields declared
// using do_entry_array have their elements initalized to nullptr).
//
// Architecture-specific blob details need to be specified using the
// do_arch_blob template
//
// do_arch_blob(blob_name, size)
//
// Currently, the do_arch_blob macro is only used to define the size
// of the code buffer into which blob-specific stub code is to be
// generated.
//
// Architecture-specific entries need to be declared using the
// do_arch_entry template
//
// do_arch_entry(arch, blob_name, stub_name, field_name, getter_name)
//
// do_arch_entry_init(arch, blob_name, stub_name, field_name,
//                    getter_name, init_function)
//
// The only difference between these templates and the generic ones is
// that they receive an extra argument which identifies the current
// architecture e.g. x86, aarch64 etc.
//
// Currently there is no support for a do_arch_array_entry template.

// Include arch-specific stub and entry declarations and make sure the
// relevant template macros have been defined

#include CPU_HEADER(stubDeclarations)

#ifndef STUBGEN_PREUNIVERSE_BLOBS_ARCH_DO
#error "Arch-specific directory failed to declare required initial stubs and entries"
#endif

#ifndef STUBGEN_INITIAL_BLOBS_ARCH_DO
#error "Arch-specific directory failed to declare required initial stubs and entries"
#endif

#ifndef STUBGEN_CONTINUATION_BLOBS_ARCH_DO
#error "Arch-specific directory failed to declare required continuation stubs and entries"
#endif

#ifndef STUBGEN_COMPILER_BLOBS_ARCH_DO
#error "Arch-specific directory failed to declare required compiler stubs and entries"
#endif

#ifndef STUBGEN_FINAL_BLOBS_ARCH_DO
#error "Arch-specific directory failed to declare required final stubs and entries"
#endif

// Iterator macros to apply templates to all relevant blobs, stubs and
// entries. Clients should use STUBGEN_ALL_DO, STUBGEN_BLOBS_DO,
// STUBGEN_STUBS_DO, STUBGEN_BLOBS_STUBS_DO, STUBGEN_ENTRIES_DO,
// STUBGEN_ARCH_BLOBS_DO and STUBGEN_ARCH_ENTRIES_DO.
//
// n.b. Client macros appear after the STUBGEN_<BLOB_NAME>_BLOBS_DO
// submacros which follow next. These submacros are not intended to be
// called directly. They serve to define the main client macro
// STUBGEN_ALL_DO and, from there, the other more specific client
// macros. n.b. multiple, 'per-blob' submacros are used to declare
// each group of stubs and entries, because that makes it simpler to
// lookup and update related elements. If you need to update these
// submacros to change the list of stubs or entries be sure to locate
// stubs within the correct blob and locate entry declarations
// immediately after their associated stub declaration.

#define STUBGEN_PREUNIVERSE_BLOBS_DO(do_blob, end_blob,                 \
                                     do_stub,                           \
                                     do_entry, do_entry_init,           \
                                     do_entry_array,                    \
                                     do_arch_blob,                      \
                                     do_arch_entry, do_arch_entry_init) \
  do_blob(preuniverse)                                                  \
  do_stub(preuniverse, fence)                                           \
  do_entry(preuniverse, fence, fence_entry, fence_entry)                \
  do_stub(preuniverse, atomic_add)                                      \
  do_entry(preuniverse, atomic_add, atomic_add_entry, atomic_add_entry) \
  do_stub(preuniverse, atomic_xchg)                                     \
  do_entry(preuniverse, atomic_xchg, atomic_xchg_entry,                 \
           atomic_xchg_entry)                                           \
  do_stub(preuniverse, atomic_cmpxchg)                                  \
  do_entry(preuniverse, atomic_cmpxchg, atomic_cmpxchg_entry,           \
           atomic_cmpxchg_entry)                                        \
  do_stub(preuniverse, atomic_cmpxchg_long)                             \
  do_entry(preuniverse, atomic_cmpxchg_long, atomic_cmpxchg_long_entry, \
           atomic_cmpxchg_long_entry)                                   \
  /* merge in stubs and entries declared in arch header */              \
  STUBGEN_PREUNIVERSE_BLOBS_ARCH_DO(do_stub, do_arch_blob,              \
                                    do_arch_entry, do_arch_entry_init)  \
  end_blob(preuniverse)                                                 \

#define STUBGEN_INITIAL_BLOBS_DO(do_blob, end_blob,                     \
                                 do_stub,                               \
                                 do_entry, do_entry_init,               \
                                 do_entry_array,                        \
                                 do_arch_blob,                          \
                                 do_arch_entry, do_arch_entry_init)     \
  do_blob(initial)                                                      \
  do_stub(initial, call_stub)                                           \
  do_entry(initial, call_stub, call_stub_entry, call_stub_entry)        \
  do_entry(initial, call_stub, call_stub_return_address,                \
           call_stub_return_address)                                    \
  do_stub(initial, forward_exception)                                   \
  do_entry(initial, forward_exception, forward_exception_entry,         \
           forward_exception_entry)                                     \
  do_stub(initial, catch_exception)                                     \
  do_entry(initial, catch_exception, catch_exception_entry,             \
           catch_exception_entry)                                       \
  do_stub(initial, updateBytesCRC32)                                    \
  do_entry(initial, updateBytesCRC32, updateBytesCRC32,                 \
           updateBytesCRC32)                                            \
  do_stub(initial, updateBytesCRC32C)                                   \
  do_entry(initial, updateBytesCRC32C, updateBytesCRC32C,               \
           updateBytesCRC32C)                                           \
  do_stub(initial, f2hf)                                                \
  do_entry(initial, f2hf, f2hf, f2hf_adr)                               \
  do_stub(initial, hf2f)                                                \
  do_entry(initial, hf2f, hf2f, hf2f_adr)                               \
  do_stub(initial, dexp)                                                \
  do_entry(initial, dexp, dexp, dexp)                                   \
  do_stub(initial, dlog)                                                \
  do_entry(initial, dlog, dlog, dlog)                                   \
  do_stub(initial, dlog10)                                              \
  do_entry(initial, dlog10, dlog10, dlog10)                             \
  do_stub(initial, dpow)                                                \
  do_entry(initial, dpow, dpow, dpow)                                   \
  do_stub(initial, dsin)                                                \
  do_entry(initial, dsin, dsin, dsin)                                   \
  do_stub(initial, dcos)                                                \
  do_entry(initial, dcos, dcos, dcos)                                   \
  do_stub(initial, dtan)                                                \
  do_entry(initial, dtan, dtan, dtan)                                   \
  do_stub(initial, dtanh)                                               \
  do_entry(initial, dtanh, dtanh, dtanh)                                \
  do_stub(initial, dcbrt)                                               \
  do_entry(initial, dcbrt, dcbrt, dcbrt)                                \
  do_stub(initial, fmod)                                                \
  do_entry(initial, fmod, fmod, fmod)                                   \
  /* following generic entries should really be x86_32 only */          \
  do_stub(initial, dlibm_sin_cos_huge)                                  \
  do_entry(initial, dlibm_sin_cos_huge, dlibm_sin_cos_huge,             \
           dlibm_sin_cos_huge)                                          \
  do_stub(initial, dlibm_reduce_pi04l)                                  \
  do_entry(initial, dlibm_reduce_pi04l, dlibm_reduce_pi04l,             \
           dlibm_reduce_pi04l)                                          \
  do_stub(initial, dlibm_tan_cot_huge)                                  \
  do_entry(initial, dlibm_tan_cot_huge, dlibm_tan_cot_huge,             \
           dlibm_tan_cot_huge)                                          \
  /* merge in stubs and entries declared in arch header */              \
  STUBGEN_INITIAL_BLOBS_ARCH_DO(do_stub, do_arch_blob,                  \
                                do_arch_entry, do_arch_entry_init)      \
  end_blob(initial)                                                     \


#define STUBGEN_CONTINUATION_BLOBS_DO(do_blob, end_blob,                \
                                      do_stub,                          \
                                      do_entry, do_entry_init,          \
                                      do_entry_array,                   \
                                      do_arch_blob,                     \
                                      do_arch_entry,                    \
                                      do_arch_entry_init)               \
  do_blob(continuation)                                                 \
  do_stub(continuation, cont_thaw)                                      \
  do_entry(continuation, cont_thaw, cont_thaw, cont_thaw)               \
  do_stub(continuation, cont_preempt)                                   \
  do_entry(continuation, cont_preempt, cont_preempt_stub,               \
           cont_preempt_stub)                                           \
  do_stub(continuation, cont_returnBarrier)                             \
  do_entry(continuation, cont_returnBarrier, cont_returnBarrier,        \
           cont_returnBarrier)                                          \
  do_stub(continuation, cont_returnBarrierExc)                          \
  do_entry(continuation, cont_returnBarrierExc, cont_returnBarrierExc,  \
           cont_returnBarrierExc)                                       \
  /* merge in stubs and entries declared in arch header */              \
  STUBGEN_CONTINUATION_BLOBS_ARCH_DO(do_stub, do_arch_blob,             \
                                     do_arch_entry, do_arch_entry_init) \
  end_blob(continuation)                                                \


#define STUBGEN_COMPILER_BLOBS_DO(do_blob, end_blob,                    \
                                  do_stub,                              \
                                  do_entry, do_entry_init,              \
                                  do_entry_array,                       \
                                  do_arch_blob,                         \
                                  do_arch_entry, do_arch_entry_init)    \
  do_blob(compiler)                                                     \
  do_stub(compiler, array_sort)                                         \
  do_entry(compiler, array_sort, array_sort, select_arraysort_function) \
  do_stub(compiler, array_partition)                                    \
  do_entry(compiler, array_partition, array_partition,                  \
           select_array_partition_function)                             \
  do_stub(compiler, aescrypt_encryptBlock)                              \
  do_entry(compiler, aescrypt_encryptBlock, aescrypt_encryptBlock,      \
           aescrypt_encryptBlock)                                       \
  do_stub(compiler, aescrypt_decryptBlock)                              \
  do_entry(compiler, aescrypt_decryptBlock, aescrypt_decryptBlock,      \
           aescrypt_decryptBlock)                                       \
  do_stub(compiler, cipherBlockChaining_encryptAESCrypt)                \
  do_entry(compiler, cipherBlockChaining_encryptAESCrypt,               \
           cipherBlockChaining_encryptAESCrypt,                         \
           cipherBlockChaining_encryptAESCrypt)                         \
  do_stub(compiler, cipherBlockChaining_decryptAESCrypt)                \
  do_entry(compiler, cipherBlockChaining_decryptAESCrypt,               \
           cipherBlockChaining_decryptAESCrypt,                         \
           cipherBlockChaining_decryptAESCrypt)                         \
  do_stub(compiler, electronicCodeBook_encryptAESCrypt)                 \
  do_entry(compiler, electronicCodeBook_encryptAESCrypt,                \
           electronicCodeBook_encryptAESCrypt,                          \
           electronicCodeBook_encryptAESCrypt)                          \
  do_stub(compiler, electronicCodeBook_decryptAESCrypt)                 \
  do_entry(compiler, electronicCodeBook_decryptAESCrypt,                \
           electronicCodeBook_decryptAESCrypt,                          \
           electronicCodeBook_decryptAESCrypt)                          \
  do_stub(compiler, counterMode_AESCrypt)                               \
  do_entry(compiler, counterMode_AESCrypt, counterMode_AESCrypt,        \
           counterMode_AESCrypt)                                        \
  do_stub(compiler, galoisCounterMode_AESCrypt)                         \
  do_entry(compiler, galoisCounterMode_AESCrypt,                        \
           galoisCounterMode_AESCrypt, galoisCounterMode_AESCrypt)      \
  do_stub(compiler, ghash_processBlocks)                                \
  do_entry(compiler, ghash_processBlocks, ghash_processBlocks,          \
           ghash_processBlocks)                                         \
  do_stub(compiler, chacha20Block)                                      \
  do_entry(compiler, chacha20Block, chacha20Block, chacha20Block)       \
  do_stub(compiler, kyberNtt)                                           \
  do_entry(compiler, kyberNtt, kyberNtt, kyberNtt)                      \
  do_stub(compiler, kyberInverseNtt)                                    \
  do_entry(compiler, kyberInverseNtt, kyberInverseNtt, kyberInverseNtt) \
  do_stub(compiler, kyberNttMult)                                       \
  do_entry(compiler, kyberNttMult, kyberNttMult, kyberNttMult)          \
  do_stub(compiler, kyberAddPoly_2)                                     \
  do_entry(compiler, kyberAddPoly_2, kyberAddPoly_2, kyberAddPoly_2)    \
  do_stub(compiler, kyberAddPoly_3)                                     \
  do_entry(compiler, kyberAddPoly_3, kyberAddPoly_3, kyberAddPoly_3)    \
  do_stub(compiler, kyber12To16)                                        \
  do_entry(compiler, kyber12To16, kyber12To16, kyber12To16)             \
  do_stub(compiler, kyberBarrettReduce)                                 \
  do_entry(compiler, kyberBarrettReduce, kyberBarrettReduce,            \
           kyberBarrettReduce)                                          \
  do_stub(compiler, dilithiumAlmostNtt)                                 \
  do_entry(compiler, dilithiumAlmostNtt,                                \
           dilithiumAlmostNtt, dilithiumAlmostNtt)                      \
  do_stub(compiler, dilithiumAlmostInverseNtt)                          \
  do_entry(compiler, dilithiumAlmostInverseNtt,                         \
           dilithiumAlmostInverseNtt, dilithiumAlmostInverseNtt)        \
  do_stub(compiler, dilithiumNttMult)                                   \
  do_entry(compiler, dilithiumNttMult,                                  \
           dilithiumNttMult, dilithiumNttMult)                          \
  do_stub(compiler, dilithiumMontMulByConstant)                         \
  do_entry(compiler, dilithiumMontMulByConstant,                        \
           dilithiumMontMulByConstant, dilithiumMontMulByConstant)      \
  do_stub(compiler, dilithiumDecomposePoly)                             \
  do_entry(compiler, dilithiumDecomposePoly,                            \
           dilithiumDecomposePoly, dilithiumDecomposePoly)              \
  do_stub(compiler, data_cache_writeback)                               \
  do_entry(compiler, data_cache_writeback, data_cache_writeback,        \
           data_cache_writeback)                                        \
  do_stub(compiler, data_cache_writeback_sync)                          \
  do_entry(compiler, data_cache_writeback_sync,                         \
           data_cache_writeback_sync, data_cache_writeback_sync)        \
  do_stub(compiler, base64_encodeBlock)                                 \
  do_entry(compiler, base64_encodeBlock, base64_encodeBlock,            \
           base64_encodeBlock)                                          \
  do_stub(compiler, base64_decodeBlock)                                 \
  do_entry(compiler, base64_decodeBlock, base64_decodeBlock,            \
           base64_decodeBlock)                                          \
  do_stub(compiler, poly1305_processBlocks)                             \
  do_entry(compiler, poly1305_processBlocks, poly1305_processBlocks,    \
           poly1305_processBlocks)                                      \
  do_stub(compiler, intpoly_montgomeryMult_P256)                        \
  do_entry(compiler, intpoly_montgomeryMult_P256,                       \
           intpoly_montgomeryMult_P256, intpoly_montgomeryMult_P256)    \
  do_stub(compiler, intpoly_assign)                                     \
  do_entry(compiler, intpoly_assign, intpoly_assign, intpoly_assign)    \
  do_stub(compiler, md5_implCompress)                                   \
  do_entry(compiler, md5_implCompress, md5_implCompress,                \
           md5_implCompress)                                            \
  do_stub(compiler, md5_implCompressMB)                                 \
  do_entry(compiler, md5_implCompressMB, md5_implCompressMB,            \
           md5_implCompressMB)                                          \
  do_stub(compiler, sha1_implCompress)                                  \
  do_entry(compiler, sha1_implCompress, sha1_implCompress,              \
           sha1_implCompress)                                           \
  do_stub(compiler, sha1_implCompressMB)                                \
  do_entry(compiler, sha1_implCompressMB, sha1_implCompressMB,          \
           sha1_implCompressMB)                                         \
  do_stub(compiler, sha256_implCompress)                                \
  do_entry(compiler, sha256_implCompress, sha256_implCompress,          \
           sha256_implCompress)                                         \
  do_stub(compiler, sha256_implCompressMB)                              \
  do_entry(compiler, sha256_implCompressMB, sha256_implCompressMB,      \
           sha256_implCompressMB)                                       \
  do_stub(compiler, sha512_implCompress)                                \
  do_entry(compiler, sha512_implCompress, sha512_implCompress,          \
           sha512_implCompress)                                         \
  do_stub(compiler, sha512_implCompressMB)                              \
  do_entry(compiler, sha512_implCompressMB, sha512_implCompressMB,      \
           sha512_implCompressMB)                                       \
  do_stub(compiler, sha3_implCompress)                                  \
  do_entry(compiler, sha3_implCompress, sha3_implCompress,              \
           sha3_implCompress)                                           \
  do_stub(compiler, double_keccak)                                      \
  do_entry(compiler, double_keccak, double_keccak, double_keccak)       \
  do_stub(compiler, sha3_implCompressMB)                                \
  do_entry(compiler, sha3_implCompressMB, sha3_implCompressMB,          \
           sha3_implCompressMB)                                         \
  do_stub(compiler, updateBytesAdler32)                                 \
  do_entry(compiler, updateBytesAdler32, updateBytesAdler32,            \
           updateBytesAdler32)                                          \
  do_stub(compiler, multiplyToLen)                                      \
  do_entry(compiler, multiplyToLen, multiplyToLen, multiplyToLen)       \
  do_stub(compiler, squareToLen)                                        \
  do_entry(compiler, squareToLen, squareToLen, squareToLen)             \
  do_stub(compiler, mulAdd)                                             \
  do_entry(compiler, mulAdd, mulAdd, mulAdd)                            \
  do_stub(compiler, montgomeryMultiply)                                 \
  do_entry(compiler, montgomeryMultiply, montgomeryMultiply,            \
           montgomeryMultiply)                                          \
  do_stub(compiler, montgomerySquare)                                   \
  do_entry(compiler, montgomerySquare, montgomerySquare,                \
           montgomerySquare)                                            \
  do_stub(compiler, bigIntegerRightShiftWorker)                         \
  do_entry(compiler, bigIntegerRightShiftWorker,                        \
           bigIntegerRightShiftWorker, bigIntegerRightShift)            \
  do_stub(compiler, bigIntegerLeftShiftWorker)                          \
  do_entry(compiler, bigIntegerLeftShiftWorker,                         \
           bigIntegerLeftShiftWorker, bigIntegerLeftShift)              \
  /* merge in stubs and entries declared in arch header */              \
  STUBGEN_COMPILER_BLOBS_ARCH_DO(do_stub, do_arch_blob,                 \
                                     do_arch_entry, do_arch_entry_init) \
  end_blob(compiler)                                                    \


#define STUBGEN_FINAL_BLOBS_DO(do_blob, end_blob,                       \
                               do_stub,                                 \
                               do_entry, do_entry_init,                 \
                               do_entry_array,                          \
                               do_arch_blob,                            \
                               do_arch_entry, do_arch_entry_init)       \
  do_blob(final)                                                        \
  do_stub(final, verify_oop)                                            \
  do_entry(final, verify_oop, verify_oop_subroutine_entry,              \
           verify_oop_subroutine_entry)                                 \
  do_stub(final, unsafecopy_common)                                     \
  do_entry(final, unsafecopy_common, unsafecopy_common_exit,            \
           unsafecopy_common_exit)                                      \
  do_stub(final, jbyte_arraycopy)                                       \
  do_entry_init(final, jbyte_arraycopy, jbyte_arraycopy,                \
                jbyte_arraycopy, StubRoutines::jbyte_copy)              \
  do_stub(final, jshort_arraycopy)                                      \
  do_entry_init(final, jshort_arraycopy, jshort_arraycopy,              \
                jshort_arraycopy, StubRoutines::jshort_copy)            \
  do_stub(final, jint_arraycopy)                                        \
  do_entry_init(final, jint_arraycopy, jint_arraycopy,                  \
                jint_arraycopy, StubRoutines::jint_copy)                \
  do_stub(final, jlong_arraycopy)                                       \
  do_entry_init(final, jlong_arraycopy, jlong_arraycopy,                \
                jlong_arraycopy, StubRoutines::jlong_copy)              \
  do_stub(final, oop_arraycopy)                                         \
  do_entry_init(final, oop_arraycopy, oop_arraycopy,                    \
                oop_arraycopy_entry, StubRoutines::oop_copy)            \
  do_stub(final, oop_arraycopy_uninit)                                  \
  do_entry_init(final, oop_arraycopy_uninit, oop_arraycopy_uninit,      \
                oop_arraycopy_uninit_entry,                             \
                StubRoutines::oop_copy_uninit)                          \
  do_stub(final, jbyte_disjoint_arraycopy)                              \
  do_entry_init(final, jbyte_disjoint_arraycopy,                        \
                jbyte_disjoint_arraycopy, jbyte_disjoint_arraycopy,     \
                StubRoutines::jbyte_copy)                               \
  do_stub(final, jshort_disjoint_arraycopy)                             \
  do_entry_init(final, jshort_disjoint_arraycopy,                       \
                jshort_disjoint_arraycopy, jshort_disjoint_arraycopy,   \
                StubRoutines::jshort_copy)                              \
  do_stub(final, jint_disjoint_arraycopy)                               \
  do_entry_init(final, jint_disjoint_arraycopy,                         \
                jint_disjoint_arraycopy, jint_disjoint_arraycopy,       \
                StubRoutines::jint_copy)                                \
  do_stub(final, jlong_disjoint_arraycopy)                              \
  do_entry_init(final, jlong_disjoint_arraycopy,                        \
                jlong_disjoint_arraycopy, jlong_disjoint_arraycopy,     \
                StubRoutines::jlong_copy)                               \
  do_stub(final, oop_disjoint_arraycopy)                                \
  do_entry_init(final, oop_disjoint_arraycopy, oop_disjoint_arraycopy,  \
                oop_disjoint_arraycopy_entry, StubRoutines::oop_copy)   \
  do_stub(final, oop_disjoint_arraycopy_uninit)                         \
  do_entry_init(final, oop_disjoint_arraycopy_uninit,                   \
                oop_disjoint_arraycopy_uninit,                          \
                oop_disjoint_arraycopy_uninit_entry,                    \
                StubRoutines::oop_copy_uninit)                          \
  do_stub(final, arrayof_jbyte_arraycopy)                               \
  do_entry_init(final, arrayof_jbyte_arraycopy,                         \
                arrayof_jbyte_arraycopy, arrayof_jbyte_arraycopy,       \
                StubRoutines::arrayof_jbyte_copy)                       \
  do_stub(final, arrayof_jshort_arraycopy)                              \
  do_entry_init(final, arrayof_jshort_arraycopy,                        \
                arrayof_jshort_arraycopy, arrayof_jshort_arraycopy,     \
                StubRoutines::arrayof_jshort_copy)                      \
  do_stub(final, arrayof_jint_arraycopy)                                \
  do_entry_init(final, arrayof_jint_arraycopy, arrayof_jint_arraycopy,  \
                arrayof_jint_arraycopy,                                 \
                StubRoutines::arrayof_jint_copy)                        \
  do_stub(final, arrayof_jlong_arraycopy)                               \
  do_entry_init(final, arrayof_jlong_arraycopy,                         \
                arrayof_jlong_arraycopy, arrayof_jlong_arraycopy,       \
                StubRoutines::arrayof_jlong_copy)                       \
  do_stub(final, arrayof_oop_arraycopy)                                 \
  do_entry_init(final, arrayof_oop_arraycopy, arrayof_oop_arraycopy,    \
                arrayof_oop_arraycopy, StubRoutines::arrayof_oop_copy)  \
  do_stub(final, arrayof_oop_arraycopy_uninit)                          \
  do_entry_init(final, arrayof_oop_arraycopy_uninit,                    \
                arrayof_oop_arraycopy_uninit,                           \
                arrayof_oop_arraycopy_uninit,                           \
                StubRoutines::arrayof_oop_copy_uninit)                  \
  do_stub(final, arrayof_jbyte_disjoint_arraycopy)                      \
  do_entry_init(final, arrayof_jbyte_disjoint_arraycopy,                \
                arrayof_jbyte_disjoint_arraycopy,                       \
                arrayof_jbyte_disjoint_arraycopy,                       \
                StubRoutines::arrayof_jbyte_copy)                       \
  do_stub(final, arrayof_jshort_disjoint_arraycopy)                     \
  do_entry_init(final, arrayof_jshort_disjoint_arraycopy,               \
                arrayof_jshort_disjoint_arraycopy,                      \
                arrayof_jshort_disjoint_arraycopy,                      \
                StubRoutines::arrayof_jshort_copy)                      \
  do_stub(final, arrayof_jint_disjoint_arraycopy)                       \
  do_entry_init(final, arrayof_jint_disjoint_arraycopy,                 \
                arrayof_jint_disjoint_arraycopy,                        \
                arrayof_jint_disjoint_arraycopy,                        \
                StubRoutines::arrayof_jint_copy)                        \
  do_stub(final, arrayof_jlong_disjoint_arraycopy)                      \
  do_entry_init(final, arrayof_jlong_disjoint_arraycopy,                \
                arrayof_jlong_disjoint_arraycopy,                       \
                arrayof_jlong_disjoint_arraycopy,                       \
                StubRoutines::arrayof_jlong_copy)                       \
  do_stub(final, arrayof_oop_disjoint_arraycopy)                        \
  do_entry_init(final, arrayof_oop_disjoint_arraycopy,                  \
                arrayof_oop_disjoint_arraycopy,                         \
                arrayof_oop_disjoint_arraycopy_entry,                   \
                StubRoutines::arrayof_oop_copy)                         \
  do_stub(final, arrayof_oop_disjoint_arraycopy_uninit)                 \
  do_entry_init(final, arrayof_oop_disjoint_arraycopy_uninit,           \
                arrayof_oop_disjoint_arraycopy_uninit,                  \
                arrayof_oop_disjoint_arraycopy_uninit_entry,            \
                StubRoutines::arrayof_oop_copy_uninit)                  \
  do_stub(final, checkcast_arraycopy)                                   \
  do_entry(final, checkcast_arraycopy, checkcast_arraycopy,             \
           checkcast_arraycopy_entry)                                   \
  do_stub(final, checkcast_arraycopy_uninit)                            \
  do_entry(final, checkcast_arraycopy_uninit,                           \
           checkcast_arraycopy_uninit,                                  \
           checkcast_arraycopy_uninit_entry)                            \
  do_stub(final, unsafe_arraycopy)                                      \
  do_entry(final, unsafe_arraycopy, unsafe_arraycopy, unsafe_arraycopy) \
  do_stub(final, generic_arraycopy)                                     \
  do_entry(final, generic_arraycopy, generic_arraycopy,                 \
           generic_arraycopy)                                           \
  do_stub(final, unsafe_setmemory)                                      \
  do_entry(final, unsafe_setmemory, unsafe_setmemory, unsafe_setmemory) \
  do_stub(final, jbyte_fill)                                            \
  do_entry(final, jbyte_fill, jbyte_fill, jbyte_fill)                   \
  do_stub(final, jshort_fill)                                           \
  do_entry(final, jshort_fill, jshort_fill, jshort_fill)                \
  do_stub(final, jint_fill)                                             \
  do_entry(final, jint_fill, jint_fill, jint_fill)                      \
  do_stub(final, arrayof_jbyte_fill)                                    \
  do_entry(final, arrayof_jbyte_fill, arrayof_jbyte_fill,               \
           arrayof_jbyte_fill)                                          \
  do_stub(final, arrayof_jshort_fill)                                   \
  do_entry(final, arrayof_jshort_fill, arrayof_jshort_fill,             \
           arrayof_jshort_fill)                                         \
  do_stub(final, arrayof_jint_fill)                                     \
  do_entry(final, arrayof_jint_fill, arrayof_jint_fill,                 \
           arrayof_jint_fill)                                           \
  do_stub(final, method_entry_barrier)                                  \
  do_entry(final, method_entry_barrier, method_entry_barrier,           \
           method_entry_barrier)                                        \
  do_stub(final, vectorizedMismatch) /* only used by x86! */            \
  do_entry(final, vectorizedMismatch, vectorizedMismatch,               \
           vectorizedMismatch)                                          \
  do_stub(final, upcall_stub_exception_handler)                         \
  do_entry(final, upcall_stub_exception_handler,                        \
           upcall_stub_exception_handler,                               \
           upcall_stub_exception_handler)                               \
  do_stub(final, upcall_stub_load_target)                               \
  do_entry(final, upcall_stub_load_target, upcall_stub_load_target,     \
           upcall_stub_load_target)                                     \
  do_stub(final, lookup_secondary_supers_table)                         \
  do_entry_array(final, lookup_secondary_supers_table,                  \
                 lookup_secondary_supers_table_stubs,                   \
                 lookup_secondary_supers_table_stub,                    \
                 Klass::SECONDARY_SUPERS_TABLE_SIZE)                    \
  do_stub(final, lookup_secondary_supers_table_slow_path)               \
  do_entry(final, lookup_secondary_supers_table_slow_path,              \
           lookup_secondary_supers_table_slow_path_stub,                \
           lookup_secondary_supers_table_slow_path_stub)                \
  /* merge in stubs and entries declared in arch header */              \
  STUBGEN_FINAL_BLOBS_ARCH_DO(do_stub,  do_arch_blob,                   \
                              do_arch_entry, do_arch_entry_init)        \
  end_blob(final)                                                       \


// The whole shebang!
//
// client macro for emitting StubGenerator blobs, stubs and entries

#define STUBGEN_ALL_DO(do_blob, end_blob,                               \
                       do_stub,                                         \
                       do_entry, do_entry_init,                         \
                       do_entry_array,                                  \
                       do_arch_blob,                                    \
                       do_arch_entry, do_arch_entry_init)               \
  STUBGEN_PREUNIVERSE_BLOBS_DO(do_blob, end_blob,                       \
                               do_stub,                                 \
                               do_entry, do_entry_init,                 \
                               do_entry_array,                          \
                               do_arch_blob,                            \
                               do_arch_entry, do_arch_entry_init)       \
  STUBGEN_INITIAL_BLOBS_DO(do_blob, end_blob,                           \
                           do_stub,                                     \
                           do_entry, do_entry_init,                     \
                           do_entry_array,                              \
                           do_arch_blob,                                \
                           do_arch_entry, do_arch_entry_init)           \
  STUBGEN_CONTINUATION_BLOBS_DO(do_blob, end_blob,                      \
                                do_stub,                                \
                                do_entry, do_entry_init,                \
                                do_entry_array,                         \
                                do_arch_blob,                           \
                                do_arch_entry, do_arch_entry_init)      \
  STUBGEN_COMPILER_BLOBS_DO(do_blob, end_blob,                          \
                            do_stub,                                    \
                            do_entry, do_entry_init,                    \
                            do_entry_array,                             \
                            do_arch_blob,                               \
                            do_arch_entry, do_arch_entry_init)          \
  STUBGEN_FINAL_BLOBS_DO(do_blob, end_blob,                             \
                         do_stub,                                       \
                         do_entry, do_entry_init,                       \
                         do_entry_array,                                \
                         do_arch_blob,                                  \
                         do_arch_entry, do_arch_entry_init)             \

// Convenience macros for use by template implementations

#define JOIN2(name, suffix)                     \
  name ## _ ## suffix

#define JOIN3(prefix, name, suffix)             \
  prefix ## _ ## name ## _ ## suffix

#define JOIN4(prefix, prefix2, name, suffix)            \
  prefix ## _ ## prefix2 ## _ ## name ## _ ## suffix

#define STUB_ID_NAME(base) JOIN2(base, id)

// emit a runtime or stubgen stub field name

#define STUB_FIELD_NAME(base) _##base

// emit a runtime blob field name

#define BLOB_FIELD_NAME(base) _## base ## _blob

// emit a stubgen blob field name

#define STUBGEN_BLOB_FIELD_NAME(base) _ ## base ## _stubs_code

// first some macros that add an increment

#define COUNT1(_1)                              \
  + 1

#define COUNT2(_1, _2)                          \
  + 1

#define COUNT4(_1, _2, _3, _4)                  \
  + 1

#define COUNT5(_1, _2, _3, _4, _5)              \
  + 1

#define COUNT6(_1, _2, _3, _4, _5, _6)          \
  + 1

#define SHARED_COUNT2(_1, type)                 \
  + type :: ENTRY_COUNT

#define STUBGEN_COUNT5(_1, _2, _3, _4, count)   \
  + count

// Convenience templates that emit nothing

// ignore do_blob(blob_name, type) declarations
#define DO_BLOB_EMPTY2(blob_name, type)

// ignore do_blob(blob_name) and end_blob(blob_name) declarations
#define DO_BLOB_EMPTY1(blob_name)

// ignore do_stub(name, fancy_jump, pass_tls, return_pc) declarations
#define DO_STUB_EMPTY4(name, fancy_jump, pass_tls, return_pc)

// ignore do_jvmti_stub(name) declarations
#define DO_JVMTI_STUB_EMPTY1(stub_name)

// ignore do_stub(blob_name, stub_name) declarations
#define DO_STUB_EMPTY2(blob_name, stub_name)

// ignore do_entry(blob_name, stub_name, fieldname, getter_name) declarations
#define DO_ENTRY_EMPTY4(blob_name, stub_name, fieldname, getter_name)

// ignore do_entry(blob_name, stub_name, fieldname, getter_name, init_function) and
// do_entry_array(blob_name, stub_name, fieldname, getter_name, count) declarations
#define DO_ENTRY_EMPTY5(blob_name, stub_name, fieldname, getter_name, init_function)

// ignore do_arch_blob(blob_name, size) declarations
#define DO_ARCH_BLOB_EMPTY2(arch, size)

// ignore do_arch_entry(arch, blob_name, stub_name, fieldname, getter_name) declarations
#define DO_ARCH_ENTRY_EMPTY5(arch, blob_name, stub_name, field_name, getter_name)

// ignore do_arch_entry(arch, blob_name, stub_name, fieldname, getter_name, init_function) declarations
#define DO_ARCH_ENTRY_EMPTY6(arch, blob_name, stub_name, field_name, getter_name, init_function)

// client macro to operate only on StubGenerator blobs

#define STUBGEN_BLOBS_DO(do_blob)                                       \
  STUBGEN_ALL_DO(do_blob, DO_BLOB_EMPTY1,                               \
                 DO_STUB_EMPTY2,                                        \
                 DO_ENTRY_EMPTY4, DO_ENTRY_EMPTY5,                      \
                 DO_ENTRY_EMPTY5,                                       \
                 DO_ARCH_BLOB_EMPTY2,                                   \
                 DO_ARCH_ENTRY_EMPTY5, DO_ARCH_ENTRY_EMPTY6)            \

// client macro to operate only on StubGenerator stubs

#define STUBGEN_STUBS_DO(do_stub)                                       \
  STUBGEN_ALL_DO(DO_BLOB_EMPTY1, DO_BLOB_EMPTY1,                        \
                 do_stub,                                               \
                 DO_ENTRY_EMPTY4, DO_ENTRY_EMPTY5,                      \
                 DO_ENTRY_EMPTY5,                                       \
                 DO_ARCH_BLOB_EMPTY2,                                   \
                 DO_ARCH_ENTRY_EMPTY5, DO_ARCH_ENTRY_EMPTY6)            \

// client macros to operate only on StubGenerator blobs and stubs

#define STUBGEN_BLOBS_STUBS_DO(do_blob, end_blob, do_stub)              \
  STUBGEN_ALL_DO(do_blob, end_blob,                                     \
                 do_stub,                                               \
                 DO_ENTRY_EMPTY4, DO_ENTRY_EMPTY5,                      \
                 DO_ENTRY_EMPTY5,                                       \
                 DO_ARCH_BLOB_EMPTY2,                                   \
                 DO_ARCH_ENTRY_EMPTY5,DO_ARCH_ENTRY_EMPTY6)             \

// client macro to operate only on StubGenerator generci and arch entries

#define STUBGEN_ALL_ENTRIES_DO(do_entry, do_entry_init, do_entry_array, \
                               do_arch_entry, do_arch_entry_init)       \
  STUBGEN_ALL_DO(DO_BLOB_EMPTY1, DO_BLOB_EMPTY1,                        \
                 DO_STUB_EMPTY2,                                        \
                 do_entry, do_entry_init,                               \
                 do_entry_array,                                        \
                 DO_ARCH_BLOB_EMPTY2,                                   \
                 do_arch_entry, do_arch_entry_init)                     \

// client macro to operate only on StubGenerator entries

#define STUBGEN_ENTRIES_DO(do_entry, do_entry_init, do_entry_array)     \
  STUBGEN_ALL_DO(DO_BLOB_EMPTY1, DO_BLOB_EMPTY1,                        \
                 DO_STUB_EMPTY2,                                        \
                 do_entry, do_entry_init,                               \
                 do_entry_array,                                        \
                 DO_ARCH_BLOB_EMPTY2,                                   \
                 DO_ARCH_ENTRY_EMPTY5, DO_ARCH_ENTRY_EMPTY6)            \

// client macro to operate only on StubGenerator arch blobs

#define STUBGEN_ARCH_BLOBS_DO(do_arch_blob)                             \
  STUBGEN_ALL_DO(DO_BLOB_EMPTY1, DO_BLOB_EMPTY1,                        \
                 DO_STUB_EMPTY2,                                        \
                 DO_ENTRY_EMPTY4, DO_ENTRY_EMPTY5,                      \
                 DO_ENTRY_EMPTY5,                                       \
                 do_arch_blob,                                          \
                 DO_ARCH_ENTRY_EMPTY5, DO_ARCH_ENTRY_EMPTY6)            \

// client macro to operate only on StubGenerator arch entries

#define STUBGEN_ARCH_ENTRIES_DO(do_arch_entry, do_arch_entry_init)      \
  STUBGEN_ALL_DO(DO_BLOB_EMPTY1, DO_BLOB_EMPTY1,                        \
                 DO_STUB_EMPTY2,                                        \
                 DO_ENTRY_EMPTY4, DO_ENTRY_EMPTY5,                      \
                 DO_ENTRY_EMPTY5,                                       \
                 DO_ARCH_BLOB_EMPTY2,                                   \
                 do_arch_entry, do_arch_entry_init)                     \

#endif // SHARE_RUNTIME_STUBDECLARATIONS_HPP
