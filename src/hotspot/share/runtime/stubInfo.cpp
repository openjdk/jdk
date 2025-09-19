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

#include "code/codeBlob.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/stubDeclarations.hpp"
#include "runtime/stubInfo.hpp"

// define static data fields of class Stubdata

struct StubInfo::GroupDetails StubInfo::_group_table[StubInfo::GROUP_TABLE_SIZE];
struct StubInfo::BlobDetails StubInfo::_blob_table[StubInfo::BLOB_TABLE_SIZE];
struct StubInfo::StubDetails StubInfo::_stub_table[StubInfo::STUB_TABLE_SIZE];
struct StubInfo::EntryDetails StubInfo::_entry_table[StubInfo::ENTRY_TABLE_SIZE];

// helpers to access table elements using enums as indices

struct StubInfo::GroupDetails& StubInfo::group_details(StubGroup g) {
  int idx = static_cast<int>(g);
  assert(idx >= 0 && idx < GROUP_TABLE_SIZE, "invalid stub group index %d", idx);
  return _group_table[idx];
}

struct StubInfo::BlobDetails& StubInfo::blob_details(BlobId b) {
  int idx = static_cast<int>(b);
  assert(idx >= 0 && idx < BLOB_TABLE_SIZE, "invalid blob index %d", idx);
  return _blob_table[idx];
}

struct StubInfo::StubDetails& StubInfo::stub_details(StubId s) {
  int idx = static_cast<int>(s);
  assert(idx >= 0 && idx < STUB_TABLE_SIZE, "invalid stub index %d", idx);
  return _stub_table[idx];
}

struct StubInfo::EntryDetails& StubInfo::entry_details(EntryId e) {
  int idx = static_cast<int>(e);
  assert(idx >= 0 && idx < ENTRY_TABLE_SIZE, "invalid entry index %d", idx);
  return _entry_table[idx];
}

// helpers to step through blob, stub or entry enum sequences

BlobId StubInfo::next(BlobId id) {
  int idx = static_cast<int>(id);
  // allow for id to be NO_BLOBID but not NUM_BLOBIDS
  assert(idx >= -1 && idx < BLOB_TABLE_SIZE, "invalid blob index %d", idx);
  return static_cast<BlobId>(idx + 1);
}

StubId StubInfo::next(StubId id) {
  int idx = static_cast<int>(id);
  // allow for id to be NO_STUBID but not NUM_STUBIDS
  assert(idx >= -1 && idx < STUB_TABLE_SIZE, "invalid stub index %d", idx);
  return static_cast<StubId>(idx + 1);
}

EntryId StubInfo::next(EntryId id) {
  int idx = static_cast<int>(id);
  // allow for id to be NO_ENTRYID but not NUM_ENTRYIDS
  assert(idx >= -1 && idx < ENTRY_TABLE_SIZE, "invalid entry index %d", idx);
  return static_cast<EntryId>(idx + 1);
}

BlobId StubInfo::next_in_group(StubGroup stub_group, BlobId blob_id) {
  int idx = static_cast<int>(blob_id);
  // id must be strictly between NO_BLOBID and NUM_BLOBIDS
  assert(idx > -1 && idx < STUB_TABLE_SIZE, "invalid stub index %d", idx);
  assert(blob_details(blob_id)._group == stub_group, "blob does not belong to stub group!");
  struct GroupDetails& group = group_details(stub_group);
  if (blob_id == group._max) {
    return BlobId::NO_BLOBID;
  } else {
    return static_cast<BlobId>(idx + 1);
  }
}

StubId StubInfo::next_in_blob(BlobId blob_id, StubId stub_id) {
  int idx = static_cast<int>(stub_id);
  // id must be strictly between NO_STUBID and NUM_STUBIDS
  assert(idx > -1 && idx < STUB_TABLE_SIZE, "invalid stub index %d", idx);
  assert(stub_details(stub_id)._blob == blob_id, "stub does not belong to blob!");
  struct BlobDetails& blob = blob_details(blob_id);
  if (stub_id == blob._max) {
    return StubId::NO_STUBID;
  } else {
    return static_cast<StubId>(idx + 1);
  }
}

EntryId StubInfo::next_in_stub(StubId stub_id, EntryId entry_id) {
  int idx = static_cast<int>(entry_id);
  // id must be strictly between NO_ENTRYID and NUM_ENTRYIDS
  assert(idx > -1 && idx < ENTRY_TABLE_SIZE, "invalid entry index %d", idx);
  assert(entry_details(entry_id)._stub == stub_id, "entry does not belong to stub!");
  struct StubDetails& stub = stub_details(stub_id);
  if (entry_id == stub._max) {
    return EntryId::NO_ENTRYID;
  } else {
    return static_cast<EntryId>(idx + 1);
  }
}

// name retrieval

const char* StubInfo::name(StubGroup stub_group) {
  return group_details(stub_group)._name;
}

const char* StubInfo::name(BlobId id) {
  return blob_details(id)._name;
}

const char* StubInfo::name(StubId id) {
  return stub_details(id)._name;
}

const char* StubInfo::name(EntryId id) {
  return entry_details(id)._name;
}

int StubInfo::span(EntryId second, EntryId first) {
  // normally when the two ids are equal the entry span is 1 but we
  // have a special case when the base and max are both NO_ENTRYID in
  // which case the entry count is 0
  int idx1 = static_cast<int>(first);
  int idx2 = static_cast<int>(second);
  assert ((idx1 < 0 && idx2  < 0) || (idx1 >= 0 && idx2 >= idx1), "bad entry ids first %d and second %d", idx1, idx2);
  if (idx1 < 0) {
    return 0;
  }
  // span is inclusive of first and second
  return idx2 + 1 - idx1;
}

int StubInfo::span(StubId second, StubId first) {
  // normally when the two ids are equal the entry span is 1 but we
  // have a special case when the base and max are both NO_STUBID in
  // which case the entry count is 0. n.b. that only happens in the
  // case where a stub group is empty e.g. when either C1 or C2 is
  // omitted from the build
  int idx1 = static_cast<int>(first);
  int idx2 = static_cast<int>(second);
  assert((idx1 < 0 && idx2  < 0) || (idx1 >= 0 && idx2 >= idx1), "bad stub ids first %d and second %d", idx1, idx2);
  if (idx1 < 0) {
    return 0;
  }
  // span is inclusive of first and second
  return idx2 + 1 - idx1;
}

int StubInfo::span(BlobId second, BlobId first) {
  // normally when the two ids are equal the entry span is 1 but we
  // have a special case when the base and max are both NO_BLOBID in
  // which case the entry count is 0. n.b. that only happens in the
  // case where a stub group is empty e.g. when either C1 or C2 is
  // omitted from the build
  int idx1 = static_cast<int>(first);
  int idx2 = static_cast<int>(second);
  assert((idx1 < 0 && idx2  < 0) || (idx1 >= 0 && idx2 >= idx1), "bad blob ids first %d and second %d", idx1, idx2);
  if (idx1 < 0) {
    return 0;
  }
  // span is inclusive of first and second
  return idx2 + 1 - idx1;
}

#ifdef ASSERT
// helpers to check sequencing of blobs stubs and entries
bool StubInfo::is_next(BlobId second, BlobId first) {
  return next(first) == second;
}

bool StubInfo::is_next(StubId second, StubId first) {
  return next(first) == second;
}

bool StubInfo::is_next(EntryId second, EntryId first) {
  return next(first) == second;
}
#endif // ASSERT

// implementation of the counting methods used to populate the
// stubgroup, blob, stub and entry tables

void StubInfo::process_shared_blob(StubGroup& group_cursor,
                                   BlobId&  blob_cursor,
                                   StubId& stub_cursor,
                                   EntryId& entry_cursor,
                                   const char* name,
                                   BlobId declaredBlob,
                                   StubId declaredStub,
                                   EntryId declaredEntry,
                                   EntryId declaredMax) {
  // for shared declarations we update the blob, stub and entry tables
  // all in one go based on each unique blob declaration. We may need
  // to write more than one entry table element if the stub has
  // multiple entries
  assert(group_cursor == StubGroup::SHARED, "must be");
  assert(is_next (declaredBlob, blob_cursor), "Out of order declaration for shared blob %s", name);
  assert(is_next(declaredStub, stub_cursor), "Out of order declaration for shared stub %s", name);
  assert(is_next(declaredEntry, entry_cursor), "Out of order declaration for shared entry %s", name);
  assert(span(declaredMax, declaredEntry) > 0, "Invalid entry count %d for entry %s", span(declaredMax, declaredEntry), name);
  // if this is the first shared blob then record it as teh base id
  // and also update entry base
  if (group_details(group_cursor)._base == BlobId::NO_BLOBID) {
    group_details(group_cursor)._base = declaredBlob;
    group_details(group_cursor)._entry_base = declaredEntry;
  }
  // update the high water mark for blobs and entries in the stub
  // group unconditionally
  group_details(group_cursor)._max = declaredBlob;
  group_details(group_cursor)._entry_max = declaredMax;
  // move forward to this blob
  blob_cursor = declaredBlob;
  // link the blob to its group and its unique stub
  blob_details(blob_cursor)._group = group_cursor;
  blob_details(blob_cursor)._base = declaredStub;
  blob_details(blob_cursor)._max = declaredStub;
  blob_details(blob_cursor)._name = name;
  // move forward to this stub
  stub_cursor = declaredStub;
  // link the stub to its blob and its entries
  stub_details(stub_cursor)._blob = declaredBlob;
  stub_details(stub_cursor)._base = declaredEntry;
  stub_details(stub_cursor)._max = declaredMax;
  stub_details(stub_cursor)._is_entry_array = false;
  stub_details(stub_cursor)._name = name;
  // move forward to last entry
  entry_cursor = declaredMax;
  // fill out the entry table for the the declared entry up to last entry
  EntryId id = declaredEntry;
  entry_details(id)._stub = declaredStub;
  entry_details(id)._array_base = EntryId::NO_ENTRYID;
  entry_details(id)._name = name;
  // fill any subsequent entries
  while (id != declaredMax) {
    id = next(id);
    entry_details(id)._stub = declaredStub;
    entry_details(id)._array_base = EntryId::NO_ENTRYID;
    entry_details(id)._name = name;
  }
}

void StubInfo::process_c1_blob(StubGroup& group_cursor,
                               BlobId&  blob_cursor,
                               StubId& stub_cursor,
                               EntryId& entry_cursor,
                               const char* name,
                               BlobId declaredBlob,
                               StubId declaredStub,
                               EntryId declaredEntry) {
  // for c1 declarations we update the blob, stub and entry tables all
  // in one go based on each unique blob declaration
  assert(group_cursor == StubGroup::C1, "must be");
  assert(is_next(declaredBlob, blob_cursor), "Out of order declaration for c1 blob %s", name);
  assert(is_next(declaredStub, stub_cursor), "Out of order declaration for c1 stub %s", name);
  assert(is_next(declaredEntry, entry_cursor), "Out of order declaration for c1 entry %s", name);
  // if this is the first c1 blob then record it and the entry
  if (group_details(group_cursor)._base == BlobId::NO_BLOBID) {
    group_details(group_cursor)._base = declaredBlob;
    group_details(group_cursor)._entry_base = declaredEntry;
  }
  // update the high water mark for blobs and entries in the stub
  // group unconditionally
  group_details(group_cursor)._max = declaredBlob;
  group_details(group_cursor)._entry_max = declaredEntry;
  // move forward to this blob
  blob_cursor = declaredBlob;
  // link the blob to its group and its unique stub
  blob_details(blob_cursor)._group = group_cursor;
  blob_details(blob_cursor)._base = declaredStub;
  blob_details(blob_cursor)._max = declaredStub;
  blob_details(blob_cursor)._name = name;
  // move forward to this stub
  stub_cursor = declaredStub;
  // link the stub to its blob and its entries
  stub_details(stub_cursor)._blob = declaredBlob;
  stub_details(stub_cursor)._base = declaredEntry;
  stub_details(stub_cursor)._max = declaredEntry;
  stub_details(stub_cursor)._is_entry_array = false;
  stub_details(stub_cursor)._name = name;
  // move forward to entry
  entry_cursor = declaredEntry;
  // fill out the entry table element
  entry_details(entry_cursor)._stub = declaredStub;
  entry_details(entry_cursor)._array_base = EntryId::NO_ENTRYID;
  entry_details(entry_cursor)._name = name;
}

void StubInfo::process_c2_blob(StubGroup& group_cursor,
                               BlobId&  blob_cursor,
                               StubId& stub_cursor,
                               EntryId& entry_cursor,
                               const char* name,
                               BlobId declaredBlob,
                               StubId declaredStub,
                               EntryId declaredEntry) {
  // for c2 declarations we update the blob, stub and entry tables all
  // in one go based on the same details garnered from each unique
  // blob, stub r jvmti stub declaration
  assert(group_cursor == StubGroup::C2, "must be");
  assert(is_next(declaredBlob, blob_cursor), "Out of order declaration for c2 blob %s", name);
  assert(is_next(declaredStub, stub_cursor), "Out of order declaration for c2 stub %s", name);
  assert(is_next(declaredEntry, entry_cursor), "Out of order declaration for c2 entry %s", name);
  // if this is the first c2 blob then record it and the entry
  if (group_details(group_cursor)._base == BlobId::NO_BLOBID) {
    group_details(group_cursor)._base = declaredBlob;
    group_details(group_cursor)._entry_base = declaredEntry;
  }
  // update the high water mark for blobs and entries in the stub
  // group unconditionally
  group_details(group_cursor)._max = declaredBlob;
  group_details(group_cursor)._entry_max = declaredEntry;
  // move forward to this blob
  blob_cursor = declaredBlob;
  // link the blob to its group and its unique stub
  blob_details(blob_cursor)._group = group_cursor;
  blob_details(blob_cursor)._base = declaredStub;
  blob_details(blob_cursor)._max = declaredStub;
  blob_details(blob_cursor)._name = name;
  // move forward to this stub
  stub_cursor = declaredStub;
  // link the stub to its blob and its entries
  stub_details(stub_cursor)._blob = declaredBlob;
  stub_details(stub_cursor)._base = declaredEntry;
  stub_details(stub_cursor)._max = declaredEntry;
  stub_details(stub_cursor)._is_entry_array = false;
  stub_details(stub_cursor)._name = name;
  // move forward to entry
  entry_cursor = declaredEntry;
  // fill out the entry table element
  entry_details(entry_cursor)._stub = declaredStub;
  entry_details(entry_cursor)._array_base = EntryId::NO_ENTRYID;
  entry_details(entry_cursor)._name = name;
}

void StubInfo::process_stubgen_blob(StubGroup& group_cursor,
                                    BlobId&  blob_cursor,
                                    StubId& stub_cursor,
                                    EntryId& entry_cursor,
                                    const char* name,
                                    BlobId declaredBlob) {
  // for stubgen blob declarations we update the blob table, allowing
  // us to link subsequent stubs to that blob
  assert(group_cursor == StubGroup::STUBGEN, "must be");
  assert(is_next(declaredBlob, blob_cursor), "Out of order declaration for stubgen blob %s", name);
  // if this is the first stubgen blob then record it
  if (group_details(group_cursor)._base == BlobId::NO_BLOBID) {
    group_details(group_cursor)._base = declaredBlob;
  }
  // update the high water mark for blobs in the stub group unconditionally
  group_details(group_cursor)._max = declaredBlob;
  // move forward to this blob
  blob_cursor = declaredBlob;
  // link the blob to its group
  blob_details(blob_cursor)._group = group_cursor;
  // clear the blob table base and max - they are set when we first
  // encounter a stub. likewise the blob table entry base and entry
  // max -- they are set when we first encounter an entry
  blob_details(blob_cursor)._base = StubId::NO_STUBID;
  blob_details(blob_cursor)._max = StubId::NO_STUBID;
  blob_details(blob_cursor)._entry_base = EntryId::NO_ENTRYID;
  blob_details(blob_cursor)._entry_max = EntryId::NO_ENTRYID;
  blob_details(blob_cursor)._name = name;
}

void StubInfo::process_stubgen_stub(StubGroup& group_cursor,
                                    BlobId&  blob_cursor,
                                    StubId& stub_cursor,
                                    EntryId& entry_cursor,
                                    const char* name,
                                    BlobId declaredBlob,
                                    StubId declaredStub) {
  // for stubgen stub declarations we update the stub table, allowing
  // us to link subsequent entries to that stub
  assert(group_cursor == StubGroup::STUBGEN, "must be");
  // FIXME use stub name here
  assert(declaredBlob == blob_cursor, "Stubgen stub %s in scope of incorrect blob %s", name, blob_details(blob_cursor)._name);
  assert(is_next(declaredStub, stub_cursor), "Out of order declaration for stubgen stub %s", name);
  // if this is the first stubgen stub then record it
  if (blob_details(blob_cursor)._base == StubId::NO_STUBID) {
    blob_details(blob_cursor)._base = declaredStub;
  }
  // update the high water mark for stubs in the blob unconditionally
  blob_details(blob_cursor)._max = declaredStub;
  // move forward to this stub
  stub_cursor = declaredStub;
  // link the stub to its blob
  stub_details(stub_cursor)._blob = blob_cursor;
  // clear the stub table base and max - they are set when we
  // encounter an entry
  stub_details(stub_cursor)._base = EntryId::NO_ENTRYID;
  stub_details(stub_cursor)._max = EntryId::NO_ENTRYID;
  stub_details(stub_cursor)._name = name;;
}

void StubInfo::process_stubgen_entry(StubGroup& group_cursor,
                                     BlobId&  blob_cursor,
                                     StubId& stub_cursor,
                                     EntryId& entry_cursor,
                                     const char* name,
                                     BlobId declaredBlob,
                                     StubId declaredStub,
                                     EntryId declaredEntry,
                                     int arrayCount) {
  // for stubgen entry declarations we update the entry table
  assert(group_cursor == StubGroup::STUBGEN, "must be");
  assert(declaredBlob == blob_cursor, "Stubgen entry %s in scope of wrong blob %s", name, blob_details(blob_cursor)._name);
  assert(declaredStub == stub_cursor, "Stubgen entry %s declares stub in scope of wrong stub %s", name, stub_details(stub_cursor)._name);
  assert(is_next(declaredEntry, entry_cursor), "Out of order declaration for stubgen entry %s", name);
  assert(arrayCount >= 0, "Invalid array count %d", arrayCount);
  // if this is the first stubgen entry in the group then record it
  if (group_details(group_cursor)._entry_base == EntryId::NO_ENTRYID) {
    group_details(group_cursor)._entry_base = declaredEntry;
  }
  // update the high water mark for entries in the group unconditionally
  group_details(group_cursor)._entry_max = declaredEntry;
  // if this is the first stubgen entry in the blob then record it
  if (blob_details(blob_cursor)._entry_base == EntryId::NO_ENTRYID) {
    blob_details(blob_cursor)._entry_base = declaredEntry;
  }
  // update the high water mark for entries in the group unconditionally
  blob_details(blob_cursor)._entry_max = declaredEntry;
  // if this is the first stubgen entry in the stub then record it
  if (stub_details(stub_cursor)._base == EntryId::NO_ENTRYID) {
    stub_details(stub_cursor)._base = declaredEntry;
  }
  // move forward to this entry
  if (arrayCount == 0) {
    // simply link the entry to its blob
    entry_cursor = declaredEntry;
    entry_details(entry_cursor)._stub = stub_cursor;
    entry_details(entry_cursor)._array_base = EntryId::NO_ENTRYID;
    entry_details(entry_cursor)._name = name;
  } else {
    // populate multiple entries and link them all to the first entry
    for (int i = 0; i < arrayCount; i++) {
      entry_cursor = next(entry_cursor);
      entry_details(entry_cursor)._stub = stub_cursor;
      entry_details(entry_cursor)._array_base = declaredEntry;
      // TODO: consider allocating names labelled with index
      entry_details(entry_cursor)._name = name;
    }
  }
  // update the high water mark for entries in the stub unconditionally
  stub_details(stub_cursor)._max = entry_cursor;
}

// The stubgroup, blob, stub and entry tables defined above are
// populated by iterating over all blob, stub and entry declarations
// and incrementally updating the associated table entries. The
// following macros invoke static methods of StubInfo that receive
// and, where appropriate, update cursors identifying current
// positions in each table.

#define PROCESS_SHARED_BLOB(name, type)                                 \
  process_shared_blob(_group_cursor, _blob_cursor,                      \
                      _stub_cursor, _entry_cursor,                      \
                      "Shared Runtime " # name "_blob",                 \
                      BlobId:: JOIN3(shared, name, id),                 \
                      StubId:: JOIN3(shared, name, id),                 \
                      EntryId:: JOIN3(shared, name, id),                \
                      EntryId:: JOIN3(shared, name, max));              \

#define PROCESS_C1_BLOB(name)                                     \
  process_c1_blob(_group_cursor, _blob_cursor,                    \
                  _stub_cursor, _entry_cursor,                    \
                  "C1 Runtime " # name "_blob",                   \
                  BlobId:: JOIN3(c1, name, id),                   \
                  StubId:: JOIN3(c1, name, id),                   \
                  EntryId:: JOIN3(c1, name, id));                 \

#define PROCESS_C2_BLOB(name, type)                         \
  process_c2_blob(_group_cursor, _blob_cursor,              \
                  _stub_cursor, _entry_cursor,              \
                  "C2 Runtime " # name "_blob",             \
                  BlobId:: JOIN3(c2, name, id),             \
                  StubId:: JOIN3(c2, name, id),             \
                  EntryId:: JOIN3(c2, name, id));           \

#define PROCESS_C2_STUB(name, fancy_jump, pass_tls, return_pc)    \
  process_c2_blob(_group_cursor, _blob_cursor,                    \
                  _stub_cursor, _entry_cursor,                    \
                  "C2 Runtime " # name "_blob",                   \
                  BlobId:: JOIN3(c2, name, id),                   \
                  StubId:: JOIN3(c2, name, id),                   \
                  EntryId:: JOIN3(c2, name, id));                 \

#define PROCESS_C2_JVMTI_STUB(name)                               \
  process_c2_blob(_group_cursor, _blob_cursor,                    \
                  _stub_cursor, _entry_cursor,                    \
                  "C2 Runtime " # name "_blob",                   \
                  BlobId:: JOIN3(c2, name, id),                   \
                  StubId:: JOIN3(c2, name, id),                   \
                  EntryId:: JOIN3(c2, name, id));                 \

#define PROCESS_STUBGEN_BLOB(blob)                                \
  process_stubgen_blob(_group_cursor, _blob_cursor,               \
                       _stub_cursor, _entry_cursor,               \
                       "Stub Generator " # blob "_blob",          \
                       BlobId:: JOIN3(stubgen, blob, id));        \

#define PROCESS_STUBGEN_STUB(blob, stub)                          \
  process_stubgen_stub(_group_cursor, _blob_cursor,               \
                       _stub_cursor, _entry_cursor,               \
                       "Stub Generator " # stub "_stub",          \
                       BlobId:: JOIN3(stubgen, blob, id),         \
                       StubId:: JOIN3(stubgen, stub, id));        \

#define PROCESS_STUBGEN_ENTRY(blob, stub, field_name, getter_name)      \
  process_stubgen_entry(_group_cursor, _blob_cursor,                    \
                        _stub_cursor, _entry_cursor,                    \
                        "Stub Generator " # field_name "_entry",        \
                        BlobId:: JOIN3(stubgen, blob, id),              \
                        StubId:: JOIN3(stubgen, stub, id),              \
                        EntryId:: JOIN3(stubgen, field_name, id),       \
                        0);                                             \

#define PROCESS_STUBGEN_ENTRY_INIT(blob, stub, field_name, getter_name, \
                                   init_funcion)                        \
  process_stubgen_entry(_group_cursor, _blob_cursor,                    \
                        _stub_cursor, _entry_cursor,                    \
                        "Stub Generator " # field_name "_entry",        \
                        BlobId:: JOIN3(stubgen, blob, id),              \
                        StubId:: JOIN3(stubgen, stub, id),              \
                        EntryId:: JOIN3(stubgen, field_name, id),       \
                        0);                                             \

#define PROCESS_STUBGEN_ENTRY_ARRAY(blob, stub, field_name, getter_name, \
                                    count)                              \
  process_stubgen_entry(_group_cursor, _blob_cursor,                    \
                        _stub_cursor, _entry_cursor,                    \
                        "Stub Generator " # field_name "_entry",        \
                        BlobId:: JOIN3(stubgen, blob, id),              \
                        StubId:: JOIN3(stubgen, stub, id),              \
                        EntryId:: JOIN3(stubgen, field_name, id),       \
                        count);                                         \

#define PROCESS_STUBGEN_ENTRY_ARCH(arch_name, blob, stub, field_name,   \
                                   getter_name)                         \
  process_stubgen_entry(_group_cursor, _blob_cursor,                    \
                        _stub_cursor, _entry_cursor,                    \
                        #arch_name "_" # field_name,                    \
                        BlobId:: JOIN3(stubgen, blob, id),              \
                        StubId:: JOIN3(stubgen, stub, id),              \
                        EntryId:: JOIN4(stubgen, arch_name,             \
                                        field_name, id),                \
                        0);                                             \

#define PROCESS_STUBGEN_ENTRY_ARCH_INIT(arch_name, blob, stub,          \
                                        field_name, getter_name,        \
                                        init_function)                  \
  process_stubgen_entry(_group_cursor, _blob_cursor,                    \
                        _stub_cursor, _entry_cursor,                    \
                        "Stub Generator " # arch_name "_" # field_name "_entry", \
                        BlobId:: JOIN3(stubgen, blob, id),              \
                        StubId:: JOIN3(stubgen, stub, id),              \
                        EntryId:: JOIN4(stubgen, arch_name,             \
                                        field_name, id),                \
                        0);                                             \

void StubInfo::populate_stub_tables() {
  StubGroup _group_cursor;
  BlobId _blob_cursor = BlobId::NO_BLOBID;
  StubId _stub_cursor = StubId::NO_STUBID;
  EntryId _entry_cursor = EntryId::NO_ENTRYID;

  _group_cursor = StubGroup::SHARED;
  group_details(_group_cursor)._name = "Shared Stubs";
  group_details(_group_cursor)._base = BlobId::NO_BLOBID;
  group_details(_group_cursor)._max = BlobId::NO_BLOBID;
  group_details(_group_cursor)._entry_base = EntryId::NO_ENTRYID;
  group_details(_group_cursor)._entry_max = EntryId::NO_ENTRYID;
  SHARED_STUBS_DO(PROCESS_SHARED_BLOB);

  _group_cursor = StubGroup::C1;
  group_details(_group_cursor)._name = "C1 Stubs";
  group_details(_group_cursor)._base = BlobId::NO_BLOBID;
  group_details(_group_cursor)._max = BlobId::NO_BLOBID;
  group_details(_group_cursor)._entry_base = EntryId::NO_ENTRYID;
  group_details(_group_cursor)._entry_max = EntryId::NO_ENTRYID;
  C1_STUBS_DO(PROCESS_C1_BLOB);

  _group_cursor = StubGroup::C2;
  group_details(_group_cursor)._name = "C2 Stubs";
  group_details(_group_cursor)._base = BlobId::NO_BLOBID;
  group_details(_group_cursor)._max = BlobId::NO_BLOBID;
  group_details(_group_cursor)._entry_base = EntryId::NO_ENTRYID;
  group_details(_group_cursor)._entry_max = EntryId::NO_ENTRYID;
  C2_STUBS_DO(PROCESS_C2_BLOB, PROCESS_C2_STUB, PROCESS_C2_JVMTI_STUB);

  _group_cursor = StubGroup::STUBGEN;
  group_details(_group_cursor)._name = "StubGen Stubs";
  group_details(_group_cursor)._base = BlobId::NO_BLOBID;
  group_details(_group_cursor)._max = BlobId::NO_BLOBID;
  group_details(_group_cursor)._entry_base = EntryId::NO_ENTRYID;
  group_details(_group_cursor)._entry_max = EntryId::NO_ENTRYID;
  STUBGEN_ALL_DO(PROCESS_STUBGEN_BLOB, DO_BLOB_EMPTY1,
                 PROCESS_STUBGEN_STUB,
                 PROCESS_STUBGEN_ENTRY, PROCESS_STUBGEN_ENTRY_INIT,
                 PROCESS_STUBGEN_ENTRY_ARRAY,
                 DO_ARCH_BLOB_EMPTY2,
                 PROCESS_STUBGEN_ENTRY_ARCH, PROCESS_STUBGEN_ENTRY_ARCH_INIT);
  assert(next(_blob_cursor) == BlobId::NUM_BLOBIDS, "should have exhausted all blob ids!");
  assert(next(_stub_cursor) == StubId::NUM_STUBIDS, "should have exhausted all stub ids!");
  assert(next(_entry_cursor) == EntryId::NUM_ENTRYIDS, "should have exhausted all entry ids!");
#ifdef ASSERT
  // run further sanity checks
  verify_stub_tables();
#endif // ASSERT
}

#undef PROCESS_SHARED_BLOB
#undef PROCESS_C1_BLOB
#undef PROCESS_C2_BLOB
#undef PROCESS_C2_STUB
#undef PROCESS_C2_JVMTI_STUB
#undef PROCESS_STUBGEN_BLOB
#undef PROCESS_STUBGEN_STUB
#undef PROCESS_STUBGEN_ENTRY
#undef PROCESS_STUBGEN_ENTRY_INIT
#undef PROCESS_STUBGEN_ENTRY_ARRAY
#undef PROCESS_STUBGEN_ENTRY_ARCH
#undef PROCESS_STUBGEN_ENTRY_ARCH_INIT

#ifdef ASSERT

void StubInfo::verify_stub_tables() {
  // exercise the traversal and interconversion APIs
  const int NUM_STUBGROUPS = static_cast<int>(StubGroup::NUM_STUBGROUPS);
  StubGroup groups[NUM_STUBGROUPS] = {
    StubGroup::SHARED,
    StubGroup::C1,
    StubGroup::C2,
    StubGroup::STUBGEN };

  // check that the statically defined blob, stub and entry counts
  // match the computed totals
  assert(blob_count(StubGroup::SHARED) == StubInfo::SHARED_STUB_COUNT,
         "miscounted number of shared blobs %d vs %d",
         blob_count(StubGroup::SHARED), StubInfo::SHARED_STUB_COUNT);

  assert(stub_count(StubGroup::SHARED) == StubInfo::SHARED_STUB_COUNT,
         "miscounted number of shared stubs %d vs %d",
         stub_count(StubGroup::SHARED), StubInfo::SHARED_STUB_COUNT);

  assert(entry_count(StubGroup::SHARED) == StubInfo::SHARED_ENTRY_COUNT,
         "miscounted number of shared entries %d vs %d",
         entry_count(StubGroup::SHARED), StubInfo::SHARED_ENTRY_COUNT);

  assert(blob_count(StubGroup::C1) == StubInfo::C1_STUB_COUNT,
         "miscounted number of c1 blobs %d vs %d",
         blob_count(StubGroup::C1), StubInfo::C1_STUB_COUNT);

  assert(stub_count(StubGroup::C1) == StubInfo::C1_STUB_COUNT,
         "miscounted number of c1 stubs %d vs %d",
         stub_count(StubGroup::C1), StubInfo::C1_STUB_COUNT);

  assert(entry_count(StubGroup::C1) == StubInfo::C1_STUB_COUNT,
         "miscounted number of c1 entries %d vs %d",
         entry_count(StubGroup::C1), StubInfo::C1_STUB_COUNT);

  assert(blob_count(StubGroup::C2) == StubInfo::C2_STUB_COUNT,
         "miscounted number of c2 blobs %d vs %d",
         blob_count(StubGroup::C2), StubInfo::C2_STUB_COUNT);

  assert(stub_count(StubGroup::C2) == StubInfo::C2_STUB_COUNT,
         "miscounted number of c2 stubs %d vs %d",
         stub_count(StubGroup::C2), StubInfo::C2_STUB_COUNT);

  assert(entry_count(StubGroup::C2) == StubInfo::C2_STUB_COUNT,
         "miscounted number of c2 entries %d vs %d",
         entry_count(StubGroup::C2), StubInfo::C2_STUB_COUNT);

  assert(blob_count(StubGroup::STUBGEN) == StubInfo::STUBGEN_BLOB_COUNT,
         "miscounted number of stubgen blobs %d vs %d",
         blob_count(StubGroup::STUBGEN), StubInfo::STUBGEN_STUB_COUNT);

  assert(stub_count(StubGroup::STUBGEN) == StubInfo::STUBGEN_STUB_COUNT,
         "miscounted number of stubgen stubs %d vs %d",
         stub_count(StubGroup::STUBGEN), StubInfo::STUBGEN_STUB_COUNT);

  assert(entry_count(StubGroup::STUBGEN) == StubInfo::STUBGEN_ENTRY_COUNT,
         "miscounted number of stubgen entries %d vs %d",
         entry_count(StubGroup::STUBGEN), StubInfo::STUBGEN_ENTRY_COUNT);

  // 1) check that the per-group blob counts add up
  for (int gidx = 0; gidx < NUM_STUBGROUPS ; gidx++) {
    StubGroup group = groups[gidx];
    BlobId blob = blob_base(group);
    int group_blob_total = blob_count(group);
    while (blob != BlobId::NO_BLOBID) {
      // predecrement total
      group_blob_total--;
      assert(group_blob_total > 0 || blob == blob_max(group), "must be!");
      assert(stubgroup(blob) == group, "iterated out of group %s to blob %s", name(group), name(blob));
      blob = next_in_group(group, blob);
    }
    assert(group_blob_total == 0, "must be!");
  }

  // 2) check that the per-group and per-blob stub counts add up
  for (int gidx = 0; gidx < NUM_STUBGROUPS; gidx++) {
    StubGroup group = groups[gidx];
    BlobId blob = blob_base(group);
    StubId group_stub = stub_base(group);
    int group_stub_total = stub_count(group);
    while (blob != BlobId::NO_BLOBID) {
      StubId stub = stub_base(blob);
      int stub_total = stub_count(blob);
      while (stub != StubId::NO_STUBID) {
        // iterations via group and blob should proceed in parallel
        assert(stub == group_stub, "must be!");
        // predecrement totals
        group_stub_total--;
        stub_total--;
        assert(stub_total > 0 || stub == stub_max(blob), "must be!");
        assert(group_stub_total > 0 || stub == stub_max(group), "must be!");
        assert(stubgroup(stub) == group, "iterated out of group %s to stub %s", name(group), name(stub));
        stub = next_in_blob(blob, stub);
        group_stub = next(group_stub);
      }
      assert(stub_total == 0, "must be!");
      blob = next_in_group(group, blob);
    }
    assert(group_stub_total == 0, "must be!");
  }

  // 3) check that the per-group, per-blob and per-stub entry counts add up
  for (int gidx = 0; gidx < NUM_STUBGROUPS; gidx++) {
    StubGroup group = groups[gidx];
    BlobId blob = blob_base(group);
    StubId group_stub = stub_base(group);
    EntryId group_entry = entry_base(group);
    int group_entry_total = entry_count(group);
    while (blob != BlobId::NO_BLOBID) {
      StubId stub = stub_base(blob);
      while (stub != StubId::NO_STUBID) {
        EntryId entry = entry_base(stub);
        int entry_total = entry_count(stub);
        while (entry != EntryId::NO_ENTRYID) {
          // iterations via group and blob should proceed in parallel
          assert(entry == group_entry, "must be!");
          // predecrement totals
          group_entry_total--;
          entry_total--;
          assert(entry_total > 0 || entry == entry_max(stub), "must be!");
          assert(group_entry_total > 0 || entry == entry_max(group), "must be!");
          assert(stubgroup(entry) == group, "iterated out of group %s to entry %s", name(group), name(entry));
          entry = next_in_stub(stub, entry);
          group_entry = next(group_entry);
        }
        assert(entry_total == 0, "must be!");
        stub = next_in_blob(blob, stub);
        group_stub = next(group_stub);
      }
      blob = next_in_group(group, blob);
    }
    assert(group_entry_total == 0, "must be!");
  }
}

#endif // ASSERT

// info support

void StubInfo::dump_group_table(LogStream& ls) {
  ls.print_cr("STUB GROUP TABLE");
  for (int i = 0; i < GROUP_TABLE_SIZE; i++) {
    GroupDetails& g = _group_table[i];
    ls.print_cr("%1d: %-8s", i, g._name);
    if (g._base == g._max) {
      // some groups don't have a blob
      if (g._base == BlobId::NO_BLOBID) {
        ls.print_cr("  blobs: %s(%d)",
                    "no_blobs",
                    static_cast<int>(g._base));
      } else {
        ls.print_cr("  blobs: %s(%d)",
                    blob_details(g._base)._name,
                    static_cast<int>(g._base));
      }
    } else {
      ls.print_cr(" blobs: %s(%d) ... %s(%d)",
                  blob_details(g._base)._name,
                  static_cast<int>(g._base),
                  blob_details(g._max)._name,
                  static_cast<int>(g._max));
    }
  }
}

void StubInfo::dump_blob_table(LogStream& ls) {
  ls.print_cr("BLOB TABLE");
  for (int i = 0; i < BLOB_TABLE_SIZE; i++) {
    BlobDetails& b = _blob_table[i];
    ls.print_cr("%-3d: %s", i, b._name);
    if (b._base == b._max) {
      // some blobs don't have a stub
      if (b._base == StubId::NO_STUBID) {
        ls.print_cr("  stubs: %s(%d)",
                    "no_stubs",
                    static_cast<int>(b._base));
      } else {
        ls.print_cr("  stubs: %s(%d)",
                    stub_details(b._base)._name,
                    static_cast<int>(b._base));
      }
    } else {
      ls.print_cr("  stubs: %s(%d) ... %s(%d)",
                  stub_details(b._base)._name,
                  static_cast<int>(b._base),
                  stub_details(b._max)._name,
                  static_cast<int>(b._max));
    }
  }
}

void StubInfo::dump_stub_table(LogStream& ls) {
  ls.print_cr("STUB TABLE");
  for (int i = 0; i < STUB_TABLE_SIZE; i++) {
    StubDetails& s = _stub_table[i];
    ls.print_cr("%-3d: %s %s", i, s._name,
                (s._is_entry_array ? "array" : ""));
    ls.print_cr("  blob: %d", static_cast<int>(s._blob));
    if (s._base == s._max) {
      // some stubs don't have an entry
      if (s._base == EntryId::NO_ENTRYID) {
        ls.print_cr("  entries: %s(%d)",
                    "no_entry",
                    static_cast<int>(s._base));
      } else {
        ls.print_cr("  entries: %s(%d)",
                    entry_details(s._base)._name,
                    static_cast<int>(s._base));
      }
    } else {
      ls.print_cr("  entries: %s(%d) ... %s(%d)",
                  entry_details(s._base)._name,
                  static_cast<int>(s._base),
                  entry_details(s._max)._name,
                  static_cast<int>(s._max));
    }
  }
}

void StubInfo::dump_entry_table(LogStream& ls) {
  ls.print_cr("ENTRY TABLE");
  for (int i = 0; i < ENTRY_TABLE_SIZE; i++) {
    EntryDetails& e = _entry_table[i];
    ls.print_cr("%-3d: %s", i, e._name);
    if (e._array_base != EntryId::NO_ENTRYID) {
      ls.print_cr("  array base: %d", static_cast<int>(e._array_base));
    }
    ls.print_cr("  stub: %d", static_cast<int>(e._stub));
  }
}

void StubInfo::dump_tables(LogStream& ls) {
  dump_group_table(ls);
  ls.print_cr("");
  dump_blob_table(ls);
  ls.print_cr("");
  dump_stub_table(ls);
  ls.print_cr("");
  dump_entry_table(ls);
}

// Global Group/Blob/Stub/Entry Id Hierarchy Traversal:

// traverse up

StubGroup StubInfo::stubgroup(EntryId id) {
  // delegate
  return stubgroup(stub(id));
}

StubGroup StubInfo::stubgroup(BlobId id) {
  return blob_details(id)._group;
}

StubGroup StubInfo::stubgroup(StubId id) {
  // delegate
  return stubgroup(blob(id));
}

StubId StubInfo::stub(EntryId id) {
  return entry_details(id)._stub;
}

BlobId StubInfo::blob(EntryId id) {
  // delegate
  return blob(stub(id));
}

BlobId StubInfo::blob(StubId id) {
  return stub_details(id)._blob;
}

// traverse down

BlobId StubInfo::blob_base(StubGroup stub_group) {
  return group_details(stub_group)._base;
}

BlobId StubInfo::blob_max(StubGroup stub_group) {
  return group_details(stub_group)._max;
}

int StubInfo::blob_count(StubGroup stub_group) {
  return span(blob_max(stub_group), blob_base(stub_group));
}

StubId StubInfo::stub_base(StubGroup stub_group) {
  BlobId base = blob_base(stub_group);
  return (base == BlobId::NO_BLOBID ? StubId::NO_STUBID : stub_base(base));
}

StubId StubInfo::stub_max(StubGroup stub_group) {
  BlobId base = blob_max(stub_group);
  return (base == BlobId::NO_BLOBID ? StubId::NO_STUBID : stub_max(base));
}

int StubInfo::stub_count(StubGroup stub_group) {
  return span(stub_max(stub_group), stub_base(stub_group));
}

EntryId StubInfo::entry_base(StubGroup stub_group) {
  return group_details(stub_group)._entry_base;
}

EntryId StubInfo::entry_max(StubGroup stub_group) {
  return group_details(stub_group)._entry_max;
}

int StubInfo::entry_count(StubGroup stub_group) {
  return span(entry_max(stub_group), entry_base(stub_group));
}

StubId StubInfo::stub_base(BlobId id) {
  return blob_details(id)._base;
}

StubId StubInfo::stub_max(BlobId id) {
  return blob_details(id)._max;
}

int StubInfo::stub_count(BlobId id) {
  return span(stub_max(id), stub_base(id));
}

EntryId StubInfo::entry_base(StubId id) {
  return stub_details(id)._base;
}

EntryId StubInfo::entry_max(StubId id) {
  return stub_details(id)._max;
}

int StubInfo::entry_count(StubId id) {
  return span(entry_max(id), entry_base(id));
}

EntryId StubInfo::entry_base(BlobId id) {
  return blob_details(id)._entry_base;
}

EntryId StubInfo::entry_max(BlobId id) {
  return blob_details(id)._entry_max;
}

int StubInfo::entry_count(BlobId id) {
  return span(entry_base(id), entry_max(id));
}

// Global <-> Local Id Management:

// private helpers

bool StubInfo::has_group(BlobId id, StubGroup group) {
  return stubgroup(id) == group;
}

bool StubInfo::has_group(StubId id, StubGroup group) {
  return stubgroup(id) == group;
}

bool StubInfo::has_group(EntryId id, StubGroup group) {
  return stubgroup(id) == group;
}

// Convert a blob, entry or stub id to a unique, zero-based offset in
// the range of blob/stub/entry ids for a given stub group.

int StubInfo::local_offset(StubGroup group, BlobId id) {
  assert(has_group(id, group), "id %s is not a %s blob!", name(id), name(group));
  BlobId base = blob_base(group);
  int s = span(id, base);
  assert(s >= 1, "must be");
  return s - 1;
}

int StubInfo::local_offset(StubGroup group, StubId id) {
  assert(has_group(id, group), "id %s is not a %s stub!", name(id), name(group));
  StubId base = stub_base(group);
  int s = span(id, base);
  assert(s >= 1, "must be");
  return s - 1;
}

int StubInfo::local_offset(StubGroup group, EntryId id) {
  assert(has_group(id, group), "id %s is not a %s entry!", name(id), name(group));
  EntryId base = entry_base(group);
  int s = span(id, base);
  assert(s >= 1, "must be");
  return s - 1;
}

// public API

// check that a stub belongs to an expected stub group

bool StubInfo::is_shared(StubId id) {
  return has_group(id, StubGroup::SHARED);
}

bool StubInfo::is_c1(StubId id) {
  return has_group(id, StubGroup::C1);
}

bool StubInfo::is_c2(StubId id) {
  return has_group(id, StubGroup::C2);
}

bool StubInfo::is_stubgen(StubId id) {
  return has_group(id, StubGroup::STUBGEN);
}

// check that a stub belongs to an expected stub group

bool StubInfo::is_shared(BlobId id) {
  return has_group(id, StubGroup::SHARED);
}

bool StubInfo::is_c1(BlobId id) {
  return has_group(id, StubGroup::C1);
}

bool StubInfo::is_c2(BlobId id) {
  return has_group(id, StubGroup::C2);
}

bool StubInfo::is_stubgen(BlobId id) {
  return has_group(id, StubGroup::STUBGEN);
}

// Convert a stub id to a unique, zero-based offset in the range of
// stub ids for a given stub group.

int StubInfo::shared_offset(StubId id) {
  return local_offset(StubGroup::SHARED, id);
}

int StubInfo::c1_offset(StubId id) {
  return local_offset(StubGroup::C1, id);
}

int StubInfo::c2_offset(StubId id) {
  return local_offset(StubGroup::C2, id);
}

int StubInfo::stubgen_offset(StubId id) {
  return local_offset(StubGroup::STUBGEN, id);
}

// initialization function called to populate blob. stub and entry
// tables. this must be called before any stubs are generated
void initialize_stub_info() {
  ResourceMark rm;
  StubInfo::populate_stub_tables();

  LogTarget(Debug, stubs) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    StubInfo::dump_tables(ls);
  }
}
