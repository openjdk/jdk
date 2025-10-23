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

#ifndef SHARE_RUNTIME_STUBINFO_HPP
#define SHARE_RUNTIME_STUBINFO_HPP

#include "logging/logStream.hpp"
#include "runtime/stubDeclarations.hpp"

// class StubInfo records details of the global stubgroup, blob, stub
// and entry hierarchy and provides APIs that
//
// 1) allow relationships between blobs, stubs and their entries to be
// identified.
//
// 2) Support conversion from a global blob/stub/entry id to a
// corresponding, unique, group-local blob/stub/entry offset from the
// first blob/stub/entry in the same stubgroup

// We have four distinct stub groups, each of which includes multiple
// blobs, stubs and entries.

enum class StubGroup : int {
  SHARED,
  C1,
  C2,
  STUBGEN,
  NUM_STUBGROUPS
};

// Generated code elements are used to implement the following enums:
//
// Global and Stub Group Local Blob/Stub/Entry Enumerations:
//
// The following enums uniquely list every generated blob, stub and
// entry across all four stub groups.
//
// enum StubId;   // unique id for every stub in the above groups
// enum BlobId;   // unique id for every blob in the above groups
// enum EntryId;  // unique id for every entry in the above groups
//
//
// Management APIs for these enums are defined in class StubInfo. The
// API methods rely on a small amount of code and data genertaed from
// the blob, stub and entry declarations.
//
// Global Group/Blob/Stub/Entry Id Hierarchy Traversal:
//
// traverse up
//
// StubGroup StubInfo::stubgroup(EntryId);
// StubGroup StubInfo::stubgroup(BlobId);
// StubGroup StubInfo::stubgroup(StubId);
//
// StubId  StubInfo::stub(EntryId);
// BlobId  StubInfo::blob(EntryId);
// BlobId  StubInfo::blob(StubId);
//
// traverse down
//
// BlobId  StubInfo::blob_base(StubGroup)
// BlobId  StubInfo::blob_max(StubGroup)
// int           StubInfo::blob_count(StubGroup)
//
// StubId  StubInfo::stub_base(StubGroup)
// StubId  StubInfo::stub_max(StubGroup)
// int     StubInfo::stub_count(StubGroup)
//
// EntryId StubInfo::entry_base(StubGroup)
// EntryId StubInfo::entry_max(StubGroup)
// int     StubInfo::entry_count(StubGroup)
//
// StubId  StubInfo::stub_base(BlobId);
// StubId  StubInfo::stub_max(BlobId);
// int     StubInfo::stub_count(BlobId);
//
// EntryId StubInfo::entry_base(StubId);
// EntryId StubInfo::entry_max(StubId);
// int     StubInfo::entry_count(StubId);
//
// EntryId StubInfo::entry_base(BlobId);
// EntryId StubInfo::entry_max(BlobId);
// int     StubInfo::entry_count(BlobId);
//
//
// Global <-> Local Id Management:
//
// check that a stub belongs to an expected stub group
//
// bool StubInfo::is_shared(StubId id);
// bool StubInfo::is_c1(StubId id);
// bool StubInfo::is_c2(StubId id);
// bool StubInfo::is_stubgen(StubId id);
//
// Convert a stub id to a unique, zero-based offset in the range of
// stub ids for a given stub group.
//
// int  StubInfo::shared_offset(StubId id);
// int  StubInfo::c1_offset(StubId id);
// int  StubInfo::c2_offset(StubId id);
// int  StubInfo::stubgen_offset(StubId id);
//
// Convert a blob id to a unique, zero-based offset in the range of
// blob ids for a given stub group. we only need this for stubgen
// blobs as for all other stub groups the stub indices and blob
// indices are identical.
//
// int  StubInfo::stubgen_offset(BlobId id);
//
// Convert an entry id to a unique, zero-based offset in the range
// of entry ids for a given stub group. we only need this for shared
// and stubgen blobs as for all other stub groups the stub indices
// and entry indices are identical.
//
// int  StubInfo::shared_offset(EntryId id);
// int  StubInfo::stubgen_offset(EntryId id);
//
// n.b. invalid interconversions from a global id to the wrong type of
// group id are caught by asserts


// Generate global blob, stub and entry enums from blob, stubs and
// entry declarations

// Global enumeration for all blobs
//
// n.b. the stubgroup is included in tag because the same name may be
// reused across groups (e.g. c1 and c2 both use new_instance)
//
//    enum BlobId {
//      shared_deopt_id,
//      . . .
//      c1_unwind_exception_id,
//      . . .
//      c2_uncommon_trap_id,
//      . . .
//      stubgen_initial_id,
//      . . .
//      NUM_BLOBIDS,
//    };


#define SHARED_DECLARE_TAG(name, type) JOIN3(shared, name, id) ,
#define C1_DECLARE_TAG(name) JOIN3(c1, name, id) ,
#define C2_DECLARE_TAG1(name) JOIN3(c2, name, id) ,
#define C2_DECLARE_TAG2(name, _1) JOIN3(c2, name, id) ,
#define C2_DECLARE_TAG4(name, _1, _2, _3) JOIN3(c2, name, id) ,
#define STUBGEN_DECLARE_TAG(name) JOIN3(stubgen, name, id) ,

enum class BlobId : int {
  NO_BLOBID = -1,
  // declare an enum tag for each shared runtime blob
  SHARED_STUBS_DO(SHARED_DECLARE_TAG)
  // declare an enum tag for each c1 runtime blob
  C1_STUBS_DO(C1_DECLARE_TAG)
  // declare an enum tag for each opto runtime blob or stub
  C2_STUBS_DO(C2_DECLARE_TAG2,
              C2_DECLARE_TAG4,
              C2_DECLARE_TAG1)
  // declare an enum tag for each stubgen blob
  STUBGEN_BLOBS_DO(STUBGEN_DECLARE_TAG)
  NUM_BLOBIDS
};

#undef SHARED_DECLARE_TAG
#undef C1_DECLARE_TAG
#undef C2_DECLARE_TAG1
#undef C2_DECLARE_TAG2
#undef C2_DECLARE_TAG4
#undef STUBGEN_DECLARE_TAG

// Global enumeration for all stubs
//
// n.b. the stubgroup is included in tag because the same name may be
// reused across groups (e.g. c1 and c2 both use new_instance). For
// stubgen stubs the blob name is omitted from the tag because all
// stub names may not be reused scross different stubgen blobs.
//
//    enum StubId {
//      shared_deopt_id,
//      . . .
//      c1_unwind_exception_id,
//      . . .
//      c2_uncommon_trap_id,
//      . . .
//      stubgen_call_stub_id,
//      stubgen_forward_exception_id,
//      . . .
//      NUM_BLOBIDS,
//    };
//

#define SHARED_DECLARE_TAG(name, type) JOIN3(shared, name, id) ,
#define C1_DECLARE_TAG(name) JOIN3(c1, name, id) ,
#define C2_DECLARE_TAG1(name) JOIN3(c2, name, id) ,
#define C2_DECLARE_TAG2(name, _1) JOIN3(c2, name, id) ,
#define C2_DECLARE_TAG4(name, _1, _2, _3) JOIN3(c2, name, id) ,
#define STUBGEN_DECLARE_TAG(blob, name) JOIN3(stubgen, name, id) ,

enum class StubId : int {
  NO_STUBID = -1,
  // declare an enum tag for each shared runtime blob
  SHARED_STUBS_DO(SHARED_DECLARE_TAG)
  // declare an enum tag for each c1 runtime blob
  C1_STUBS_DO(C1_DECLARE_TAG)
  // declare an enum tag for each opto runtime blob or stub
  C2_STUBS_DO(C2_DECLARE_TAG2,
              C2_DECLARE_TAG4,
              C2_DECLARE_TAG1)
  // declare an enum tag for each stubgen runtime stub
  STUBGEN_STUBS_DO(STUBGEN_DECLARE_TAG)
  NUM_STUBIDS
};

#undef SHARED_DECLARE_TAG
#undef C1_DECLARE_TAG
#undef C2_DECLARE_TAG1
#undef C2_DECLARE_TAG2
#undef C2_DECLARE_TAG4
#undef STUBGEN_DECLARE_TAG


//
// Global enumeration for all entries
//
// n.b. the stubgroup is included in tag because the same name may be
// reused across groups (e.g. c1 and c2 both use new_instance)
//
//    enum EntryId : int {
//      NO_ENTRYID = -1,
//      shared_deopt_id,
//      shared_deopt_max =
//        shared_deopt_id + DeoptimzationBlob::NUM_ENTRIES -1,
//      . . .
//      c1_unwind_exception_id,
//      . . .
//      c2_uncommon_trap_id,
//      . . .
//      stubgen_call_stub_id,
//      stubgen_call_stub_return_address_id,
//      stubgen_forward_exception_id,
//      . . .
//      stubgen_aarch64_large_array_equals_id,
//      . . .
//      stubgen_lookup_secondary_supers_table_stubs_id,
//      stubgen_lookup_secondary_supers_table_stubs_max =
//        stubgen_lookup_secondary_supers_table_stubs_id +
//        Klass::SECONDARY_SUPERS_TABLE_SIZE,
//      . . .
//      NUM_ENTRYIDS,
//    };
//
// - global id tags include a stub group prefix because some of the
// stub names are used in more than one group (e.g. new_instance,
// forward_exception). arch specific stubgen stubs also include the
// arch name in the tag.
//
// - for shared stub entries we only need to allocate a single enum
// tag for most blobs since they have only one entry. However, we need
// to bump up the index by an extra 3 (or 5 with JVMCI included) when
// we are generating the deoptimization blob because it has 4
// (respectively, 6) entries. So, in that case we allocate a single
// enum tag identifying the index of the first entry and a max tag
// identifying the index of the last entry
//
// - for stubgen stubs which employ an array of entries we allocate a
// single enum tag identifying the index of the first entry and a max
// tag identifying the index of the last entry e.g. for
// lookup_secondary_supers_table we generate
//
//      . . .
//      stubgen_lookup_secondary_supers_table_stubs_id,
//      stubgen_lookup_secondary_supers_table_stubs_max = stubgen_lookup_secondary_supers_table_stubs_id + Klass::SECONDARY_SUPERS_TABLE_SIZE,
//      . . .
//

// macro to declare tags for shared entries with a base id for the
// first (and usually only) entry and a max id that identifies the
// last (usually same as first) entry in the blob, ensuring the entry
// for the next stub has the correct index.

#define SHARED_DECLARE_TAG(name, type)                                  \
  JOIN3(shared, name, id),                                              \
  JOIN3(shared, name, max) = JOIN3(shared, name, id) +                  \
    type ::ENTRY_COUNT - 1,                                             \

// macros to declare a tag for a C1 generated blob or a C2 generated
// blob, stub or JVMTI stub all of which have a single unique entry

#define C1_DECLARE_TAG(name)           \
  JOIN3(c1, name, id),                 \

#define C2_DECLARE_BLOB_TAG(name, type)                               \
  JOIN3(c2, name, id),                                                \

#define C2_DECLARE_STUB_TAG(name, fancy_jump, pass_tls, return_pc)    \
  JOIN3(c2, name, id),                                                \

#define C2_DECLARE_JVMTI_STUB_TAG(name)                               \
  JOIN3(c2, name, id),                                                \

// macros to declare a tag for a StubGen normal entry or initialized
// entry

#define STUBGEN_DECLARE_TAG(blob_name, stub_name,                       \
                            field_name, getter_name)                    \
  JOIN3(stubgen, field_name, id),                                       \

#define STUBGEN_DECLARE_INIT_TAG(blob_name, stub_name,                  \
                                 field_name, getter_name,               \
                                 init_function)                         \
  JOIN3(stubgen, field_name, id),                                       \

// macro to declare a tag for a StubGen entry array. this macro
// declares a base id for the first entry then a max id that
// identifies the last entry in the array, ensuring the entry for the
// next stub has the correct index.

#define STUBGEN_DECLARE_ARRAY_TAG(blob_name, stub_name,                 \
                                  field_name, getter_name,              \
                                  count)                                \
  JOIN3(stubgen, field_name, id),                                       \
  JOIN3(stubgen, field_name, max) = JOIN3(stubgen, field_name, id) +    \
    count - 1,                                                          \

// macros to declare a tag for StubGen arch entries

#define STUBGEN_DECLARE_ARCH_TAG(arch_name, blob_name, stub_name,       \
                                 field_name, getter_name)               \
  JOIN4(stubgen, arch_name, field_name, id),                            \

#define STUBGEN_DECLARE_ARCH_INIT_TAG(arch_name, blob_name, stub_name,  \
                                      field_name, getter_name,          \
                                      init_function)                    \
  JOIN4(stubgen, arch_name, field_name, id),                            \

// the above macros are enough to declare the enum

enum class EntryId : int {
  NO_ENTRYID = -1,
  // declare an enum tag for each shared runtime blob
  SHARED_STUBS_DO(SHARED_DECLARE_TAG)
  // declare an enum tag for each c1 runtime blob
  C1_STUBS_DO(C1_DECLARE_TAG)
  // declare an enum tag for each opto runtime blob or stub
  C2_STUBS_DO(C2_DECLARE_BLOB_TAG,
              C2_DECLARE_STUB_TAG,
              C2_DECLARE_JVMTI_STUB_TAG)
  // declare an enum tag for each stubgen entry or, in the case of an
  // array of entries for the first and last entries.
  STUBGEN_ALL_ENTRIES_DO(STUBGEN_DECLARE_TAG,
                         STUBGEN_DECLARE_INIT_TAG,
                         STUBGEN_DECLARE_ARRAY_TAG,
                         STUBGEN_DECLARE_ARCH_TAG,
                         STUBGEN_DECLARE_ARCH_INIT_TAG)
  NUM_ENTRYIDS
};

#undef SHARED_DECLARE_TAG
#undef C1_DECLARE_TAG
#undef C2_DECLARE_BLOB_TAG
#undef C2_DECLARE_STUB_TAG
#undef C2_DECLARE_JVMTI_STUB_TAG
#undef STUBGEN_DECLARE_TAG
#undef STUBGEN_DECLARE_INIT_TAG
#undef STUBGEN_DECLARE_ARRAY_TAG
#undef STUBGEN_DECLARE_ARCH_TAG
#undef STUBGEN_DECLARE_ARCH_INIT_TAG

// we need static init expressions for blob, stub and entry counts in
// each stubgroup

#define SHARED_STUB_COUNT_INITIALIZER           \
  0 SHARED_STUBS_DO(COUNT2)

#define SHARED_ENTRY_COUNT_INITIALIZER          \
  0 SHARED_STUBS_DO(SHARED_COUNT2)

#define C1_STUB_COUNT_INITIALIZER               \
  0 C1_STUBS_DO(COUNT1)

#define C2_STUB_COUNT_INITIALIZER               \
  0 C2_STUBS_DO(COUNT2, COUNT4, COUNT1)

#define STUBGEN_BLOB_COUNT_INITIALIZER          \
  0 STUBGEN_BLOBS_DO(COUNT1)

#define STUBGEN_STUB_COUNT_INITIALIZER          \
  0 STUBGEN_STUBS_DO(COUNT2)

#define STUBGEN_ENTRY_COUNT_INITIALIZER          \
  0 STUBGEN_ALL_ENTRIES_DO(COUNT4, COUNT5,       \
                           STUBGEN_COUNT5,       \
                           COUNT5, COUNT6)

// Declare management class StubInfo

class StubInfo: AllStatic {
private:
  // element types for tables recording stubgroup, blob, stub and
  // entry properties and relationships

  // map each stubgroup to its initial and final blobs
  struct GroupDetails {
    BlobId _base;       // first blob id belonging to stub group
    BlobId _max;        // last blob id belonging to stub group
    // some stubs have no entries so we have to explicitly track the
    // first and last entry associated with the group rather than
    // deriving it from the first and last blob/stub pair
    EntryId _entry_base;  // first entry id belonging to stub
    EntryId _entry_max;   // last entry id belonging to stub
    const char* _name;          // name of stubgroup
  };

  // a blob table element enables the stub group of a guven blob to be
  // identified and all stubs within the blob to be identified
  //
  // invariant: the number of stubs in a blob must be 1 unless the
  // blob belongs to the StubGen stub group

  struct BlobDetails {
    StubGroup _group;           // stub group to which blob belongs
    StubId _base;         // first stub id belonging to blob
    StubId _max;          // last stub id belonging to blob
    // some stubs have no entries so we have to explicitly track the
    // first and last entry associated with the blob rather than
    // deriving it from the first and last stub
    EntryId _entry_base;  // first entry id belonging to stub
    EntryId _entry_max;   // last entry id belonging to stub
    const char* _name;          // name of blob
  };

  // a stub table element enables the blob of a given stub to be
  // identified and all entries within the stub to be identified
  //
  // invariant: the number of entries in a blob must be 1 unless the
  // blob belongs to the StubGen group or the Shared stub group

  struct StubDetails {
    BlobId _blob;         // blob to which stub belongs
    EntryId _base;        // first entry id belonging to stub
    EntryId _max;         // last entry id belonging to stub
    bool _is_entry_array;       // true iff stub has array of entries
    const char* _name;          // name of stub
  };

  // a stub table element enables the blob of a given stub to be
  // identified and all entries within the stub to be identified
  //
  // invariant: the number of entries in a blob must be 1 unless the
  // blob belongs to the StubGen group or the Shared stub group

  struct EntryDetails {
    StubId _stub;          // stub to which the entry belongs
    EntryId _array_base;   // base entry id for entry array stubs
    const char* _name;           // name of stub
  };

  // tables are sized and indexed using the global ids
  static const int GROUP_TABLE_SIZE = static_cast<int>(StubGroup::NUM_STUBGROUPS);
  static const int BLOB_TABLE_SIZE = static_cast<int>(BlobId::NUM_BLOBIDS);
  static const int STUB_TABLE_SIZE = static_cast<int>(StubId::NUM_STUBIDS);
  static const int ENTRY_TABLE_SIZE = static_cast<int>(EntryId::NUM_ENTRYIDS);

  static struct GroupDetails _group_table[GROUP_TABLE_SIZE];
  static struct BlobDetails _blob_table[BLOB_TABLE_SIZE];
  static struct StubDetails _stub_table[STUB_TABLE_SIZE];
  static struct EntryDetails _entry_table[ENTRY_TABLE_SIZE];

  // helpers to access table elements using enums as indices
  static struct GroupDetails& group_details(StubGroup g);
  static struct BlobDetails& blob_details(BlobId b);
  static struct StubDetails& stub_details(StubId s);
  static struct EntryDetails& entry_details(EntryId e);

  // helpers for counting entries/stubs in a given stub/blob

  static int span(EntryId second, EntryId first);
  static int span(StubId second, StubId first);
  static int span(BlobId second, BlobId first);

  // helper for testing whether a blob, stub or entry lies in a
  // specific stubgroup
  static bool has_group(BlobId id, StubGroup group);
  static bool has_group(StubId id, StubGroup group);
  static bool has_group(EntryId id, StubGroup group);

  // helpers for computing blob, stub or entry offsets within
  // a specific stub group

  static int local_offset(StubGroup group, BlobId id);
  static int local_offset(StubGroup group, StubId id);
  static int local_offset(StubGroup group, EntryId id);

  // implementation of methods used to populate the stubgroup, blob,
  // stub and entry tables
  static void process_shared_blob(StubGroup& group_cursor,
                                  BlobId&  blob_cursor,
                                  StubId& stub_cursor,
                                  EntryId& entry_cursor,
                                  const char* name,
                                  BlobId declaredBlob,
                                  StubId declaredStub,
                                  EntryId declaredEntry,
                                  EntryId declaredMax);
  static void process_c1_blob(StubGroup& group_cursor,
                              BlobId&  blob_cursor,
                              StubId& stub_cursor,
                              EntryId& entry_cursor,
                              const char* name,
                              BlobId declaredBlob,
                              StubId declaredStub,
                              EntryId declaredEntry);
  static void process_c2_blob(StubGroup& group_cursor,
                              BlobId&  blob_cursor,
                              StubId& stub_cursor,
                              EntryId& entry_cursor,
                              const char* name,
                              BlobId declaredBlob,
                              StubId declaredStub,
                              EntryId declaredEntry);
  static void process_stubgen_blob(StubGroup& group_cursor,
                                   BlobId&  blob_cursor,
                                   StubId& stub_cursor,
                                   EntryId& entry_cursor,
                                   const char* name,
                                   BlobId declaredBlob);
  static void process_stubgen_stub(StubGroup& group_cursor,
                                   BlobId&  blob_cursor,
                                   StubId& stub_cursor,
                                   EntryId& entry_cursor,
                                   const char* name,
                                   BlobId declaredBlob,
                                   StubId declaredStub);
  static void process_stubgen_entry(StubGroup& group_cursor,
                                    BlobId&  blob_cursor,
                                    StubId& stub_cursor,
                                    EntryId& entry_cursor,
                                    const char* name,
                                    BlobId declaredBlob,
                                    StubId declaredStub,
                                    EntryId declaredEntry,
                                    int arrayCount);

  static void dump_group_table(LogStream& ls);
  static void dump_blob_table(LogStream& ls);
  static void dump_stub_table(LogStream& ls);
  static void dump_entry_table(LogStream& ls);

  static void verify_stub_tables();
public:

  // Define statically sized counts for blobs, stubs and entries in
  // each stub group. n.b. we omit cases where the blob or entry count
  // equals the stub count.
  static const int SHARED_STUB_COUNT = SHARED_STUB_COUNT_INITIALIZER;
  static const int SHARED_ENTRY_COUNT = SHARED_ENTRY_COUNT_INITIALIZER;

  static const int C1_STUB_COUNT = C1_STUB_COUNT_INITIALIZER;

  static const int C2_STUB_COUNT = C2_STUB_COUNT_INITIALIZER;

  static const int STUBGEN_STUB_COUNT = STUBGEN_STUB_COUNT_INITIALIZER;
  static const int STUBGEN_BLOB_COUNT = STUBGEN_BLOB_COUNT_INITIALIZER;
  static const int STUBGEN_ENTRY_COUNT = STUBGEN_ENTRY_COUNT_INITIALIZER;

  // init method called from a static initializer
  static void populate_stub_tables();
  // for logging
  static void dump_tables(LogStream& ls);

  // helpers to step through blob, stub or entry enum sequences.
  // input id may be NO_BLOB/STUB/ENTRYID. returned id may be
  // NUM_BLOB/STUB/ENTRYIDs
  static BlobId next(BlobId id);
  static StubId next(StubId id);
  static EntryId next(EntryId id);

  // helpers to step through blob/stub/entry enum sequence within
  // (respectively) the enclosing group/blob/stub. returned id will be
  // a valid blob/stub/entry id or NO_BLOB/STUB/ENTRYID if the
  // group/blob/stub contains no more stubs/entries.
  static BlobId next_in_group(StubGroup stub_group, BlobId blob_id);
  static StubId next_in_blob(BlobId blob_id, StubId stub_id);
  static EntryId next_in_stub(StubId stub_id, EntryId entry_id);

#ifdef ASSERT
  // helpers to check sequencing of blobs stubs and entries
  static bool is_next(BlobId second, BlobId first);
  static bool is_next(StubId second, StubId first);
  static bool is_next(EntryId second, EntryId first);
#endif // ASSERT

  // name retrieval
  static const char* name(StubGroup stub_group);
  static const char* name(BlobId id);
  static const char* name(StubId id);
  static const char* name(EntryId id);

  // Global Group/Blob/Stub/Entry Id Hierarchy Traversal:

  // traverse up

  static StubGroup stubgroup(EntryId id);
  static StubGroup stubgroup(BlobId id);
  static StubGroup stubgroup(StubId id);

  static StubId  stub(EntryId id);
  static BlobId  blob(EntryId id);
  static BlobId  blob(StubId id);

  // traverse down

  static BlobId  blob_base(StubGroup stub_group);
  static BlobId  blob_max(StubGroup stub_group);
  static int     blob_count(StubGroup stub_group);

  static StubId  stub_base(StubGroup stub_group);
  static StubId  stub_max(StubGroup stub_group);
  static int     stub_count(StubGroup stub_group);

  static EntryId entry_base(StubGroup stub_group);
  static EntryId entry_max(StubGroup stub_group);
  static int     entry_count(StubGroup stub_group);

  static StubId  stub_base(BlobId id);
  static StubId  stub_max(BlobId id);
  static int     stub_count(BlobId id);

  static EntryId entry_base(BlobId id);
  static EntryId entry_max(BlobId id);
  static int     entry_count(BlobId id);

  static EntryId entry_base(StubId id);
  static EntryId entry_max(StubId id);
  static int     entry_count(StubId id);

  // Global <-> Local Id Management:

  // check that a blob/stub belongs to an expected stub group

  static bool is_shared(StubId id);
  static bool is_c1(StubId id);
  static bool is_c2(StubId id);
  static bool is_stubgen(StubId id);

  static bool is_shared(BlobId id);
  static bool is_c1(BlobId id);
  static bool is_c2(BlobId id);
  static bool is_stubgen(BlobId id);

  // Convert a stub id to a unique, zero-based offset in the range of
  // stub ids for a given stub group.

  static int  shared_offset(StubId id);
  static int  c1_offset(StubId id);
  static int  c2_offset(StubId id);
  static int  stubgen_offset(StubId id);
};


#endif // SHARE_RUNTIME_STUBINFO_HPP
