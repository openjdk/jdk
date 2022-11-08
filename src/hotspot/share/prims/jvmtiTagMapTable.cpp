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


JvmtiTagMapEntry::JvmtiTagMapEntry(oop obj) {
  _released = false;
   _lookup = true;
   _lookup_oop = obj;
}
JvmtiTagMapEntry::JvmtiTagMapEntry(const JvmtiTagMapEntry& org) {
   if (org._lookup) {
     _lookup = false;
     _wh = WeakHandle(JvmtiExport::weak_tag_storage(), org._lookup_oop);
   } else { // original was a real entry
     _lookup = false;
     _wh = org._wh; // Take ownership of oppStorage allocation
   }
   _released = false;
 }
 JvmtiTagMapEntry::~JvmtiTagMapEntry(){
   if (!_lookup) {
     release();
   }
}
void JvmtiTagMapEntry::release()
{
  if(_released)
    return;
  assert(!_lookup, "Bad");
  _wh.release(JvmtiExport::weak_tag_storage());
  _released = true;
}
oop JvmtiTagMapEntry::object() const {
  if (!_lookup) {
     return _wh.resolve();
   }
   return _lookup_oop;}

oop JvmtiTagMapEntry::object_no_keepalive() const{
  // Just peek at the object without keeping it alive.
  if (!_lookup) {
     return _wh.peek();
   }
   return _lookup_oop;
}

JvmtiTagMapTable::JvmtiTagMapTable()

   {
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
   return _rrht_table->put(new_entry, tag);
}

void JvmtiTagMapTable::remove(oop obj) {
  JvmtiTagMapEntry jtme(obj);
  _rrht_table->remove(jtme);
}

void JvmtiTagMapTable::entry_iterate(JvmtiTagMapEntryClosure* closure) {
  _rrht_table->iterate(closure);
}

const int _resize_load_trigger = 5;       // load factor that will trigger the resize
static bool _resizable = true;

void JvmtiTagMapTable::resize_if_needed() {
  _rrht_table->maybe_grow();
}

void JvmtiTagMapTable::remove_dead_entries(GrowableArray<jlong>* objects) {
  struct IsDead{
    GrowableArray<jlong>* _objects;
    IsDead(GrowableArray<jlong>* objects) : _objects(objects){}
    bool do_entry(JvmtiTagMapEntry const & entry, jlong tag){
      if ( entry.object_no_keepalive() == NULL){
        if(_objects!=NULL){
          _objects->append(tag);
        }
        return true;
      }
      return false;;
    }
  }IsDead(objects);
  _rrht_table->unlink(&IsDead);
}

// Rehash oops in the table
void JvmtiTagMapTable::rehash() {
  remove_dead_entries(NULL);
  ResourceMark rm;
   ResizableResourceHT* new_rrht_table = new (ResourceObj::C_HEAP, mtInternal) ResizableResourceHT(_rrht_table->table_size());
   struct CopyEntry : public JvmtiTagMapEntryClosure {
     ResizableResourceHT* new_rrht_table;
     CopyEntry(ResizableResourceHT* table) : new_rrht_table(table) {
     }
     bool do_entry(JvmtiTagMapEntry &key, jlong &value) {
       new_rrht_table->put(key, value);
       key.invalidate();
       return true;
    }
   } copy_entry(new_rrht_table);
   entry_iterate(&copy_entry);
   assert(new_rrht_table->number_of_entries() == _rrht_table->number_of_entries(), "Must be same size");
   // Swap and
   ResizableResourceHT* to_be_freed = _rrht_table;
   _rrht_table = new_rrht_table;

   delete to_be_freed;
}
