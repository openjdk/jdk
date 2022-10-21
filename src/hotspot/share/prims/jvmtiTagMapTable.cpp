/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/oopStorage.hpp"
#include "jvmtifiles/jvmtiEnv.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "oops/weakHandle.inline.hpp"
#include "prims/jvmtiEventController.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiTagMapTable.hpp"
#include "utilities/hashtable.inline.hpp"
#include "utilities/macros.hpp"
#include "gc/shared/oopStorage.hpp"

JvmtiTagMapEntry::JvmtiTagMapEntry(oop obj){
  _released = false;
  _wh = WeakHandle(JvmtiExport::weak_tag_storage(), obj);
}

JvmtiTagMapEntry::JvmtiTagMapEntry(oop obj) {
  _released = false;
  _wh = WeakHandle(JvmtiExport::weak_tag_storage(), obj);
}

JvmtiTagMapEntry::~JvmtiTagMapEntry(){
  release();
}

void JvmtiTagMapEntry::release()
{
  if(_released)
    return;
  _wh.release(JvmtiExport::weak_tag_storage());
  _released = true;
}

oop JvmtiTagMapEntry::object() const {
  return _wh.resolve();
}

oop JvmtiTagMapEntry::object_no_keepalive() const {
  return _wh.peek();
}

JvmtiTagMapTable::JvmtiTagMapTable(){
  _rrht_table = new (ResourceObj::C_HEAP, mtInternal)  ResizableResourceHT(Constants::_table_size);
}

void JvmtiTagMapTable::clear() {
  struct RemoveAll{
    bool do_entry(JvmtiTagMapEntry   & entry, jlong const &  tag)
    {
      entry.release();
      return true;
    }
  }RemoveAll;
  _rrht_table->unlink(&RemoveAll);

  assert(_rrht_table->number_of_entries() == 0, "should have removed all entries");
}

JvmtiTagMapTable::~JvmtiTagMapTable() {
  clear();
}

jlong JvmtiTagMapTable::find(oop obj) {
  JvmtiTagMapEntry jtme(obj);
  jlong* found = _rrht_table->get(jtme);
  return found == NULL ? 0 : *found;
}

bool JvmtiTagMapTable::add(oop obj, jlong tag) {
  JvmtiTagMapEntry new_entry(obj);
  bool is_added = _rrht_table->put(new_entry, tag);
  if ( is_added ) {
    new_entry.set_released(true);// do not release on dtor, since there is acopied entry inside the table
  }
  return is_added;
}

<<<<<<< HEAD
void JvmtiTagMapTable::remove(oop obj) {
  JvmtiTagMapEntry jtme(obj);
  _rrht_table->remove(jtme);
=======
bool JvmtiTagMapTable::remove(oop obj) {

  JvmtiTagMapEntry jtme(obj, true);
  jlong* found = _rrht_table.get(jtme);
  if (found == NULL) {
    log_debug(jvmti,table)("entry not found to remove.\n");
    return true;
  }
  if( _rrht_table.remove(jtme)) {
    jtme.release();
    return true;
  } else {
    log_info(jvmti,table)("removing object failed.");
  }
  return false;
}
int JvmtiTagMapTable::add_update_remove(JvmtiTagMapEntry &entry_par,oop obj, jlong tag){
  assert(obj != NULL, "obj should not be NULL.");
  JvmtiTagMapEntry entry (obj, true);
  bool found = find(entry, obj);
  bool to_be_added = tag != 0 ;
  bool to_be_updated = tag != 0 ;
  bool to_be_removed = tag == 0 ;

  if (!found && to_be_added ) {
    if ( add(obj, tag) ){
      return AddUpdateRemove::Added;
    } else {
      log_info(jvmti,table)("expected to add entry with new tag, but the tag gets updated.\n");
    }
  }
  if ( found && to_be_removed){
    if ( remove(obj) ) {
      return AddUpdateRemove::Removed;
    } else {
      log_info(jvmti,table)("request for removing an entry failed.\n");
      return AddUpdateRemove::Failed;
    }
  }
  if (found && to_be_updated ){
    if ( _rrht_table.put(entry, tag) ) {
      log_info(jvmti,table)("expected to update entry's tag, but it is added.\n");
    }
    entry_par.set_tag(tag);
    return AddUpdateRemove::Updated;
  }
  return AddUpdateRemove::Failed;
>>>>>>> a9b7f953b0c (step 5)
}
void JvmtiTagMapTable::entry_iterate(JvmtiTagMapEntryClosure* closure) {
  _rrht_table->iterate(closure);
}

void JvmtiTagMapTable::resize_if_needed() {
  _rrht_table->maybe_grow();
}

void JvmtiTagMapTable::remove_dead_entries(GrowableArray<jlong>* objects) {
  struct IsDead{
    GrowableArray<jlong>* _objects;
    int count;
    IsDead(GrowableArray<jlong>* objects) : _objects(objects),count(0){}
    bool do_entry(JvmtiTagMapEntry & entry, jlong tag){
      if ( entry.object_no_keepalive() == NULL){
        log_info(jvmti,table)("%d objects found dead.\n",++count);
        if(_objects!=NULL){
          _objects->append(tag);
          entry.release();
          log_info(jvmti,table)("dead object is appended to GrowableArray.\n");
        }
        return true;
      }
      return false;;
    }
  }IsDead(objects);
  _rrht_table->unlink(&IsDead);
}


