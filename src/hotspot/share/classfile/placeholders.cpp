/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/placeholders.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "oops/symbolHandle.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/resourceHash.hpp"

class PlaceholderKey {
  SymbolHandle _name;
  ClassLoaderData* _loader_data;
 public:
  PlaceholderKey(Symbol* name, ClassLoaderData* l) : _name(name), _loader_data(l) {}

  static bool equals(PlaceholderKey const& k1, PlaceholderKey const& k2) {
    return (k1._name == k2._name && k1._loader_data == k2._loader_data);
  }
  static unsigned hash(PlaceholderKey const& k) {
    return (unsigned) k._name->identity_hash() ^ (int)((intptr_t)k._loader_data >> 3);
  }
  void print_on(outputStream* st) const;
};

const int _placeholder_table_size = 503;   // Does this really have to be prime?
using InternalPlaceholderTable = ResourceHashtable<PlaceholderKey, PlaceholderEntry, _placeholder_table_size, AnyObj::C_HEAP, mtClass,
                  PlaceholderKey::hash, PlaceholderKey::equals>;
static InternalPlaceholderTable* _placeholders;

// SeenThread objects represent list of threads that are
// currently performing a load action on a class.
// For class circularity, set before loading a superclass.
// For bootclasssearchpath, set before calling load_instance_class.
// Defining must be single threaded on a class/classloader basis
// For DEFINE_CLASS, the head of the queue owns the
// define token and the rest of the threads wait to return the
// result the first thread gets.
class SeenThread: public CHeapObj<mtInternal> {
private:
   JavaThread* _thread;
   SeenThread* _stnext;
   SeenThread* _stprev;
public:
   SeenThread(JavaThread* thread) {
       _thread = thread;
       _stnext = nullptr;
       _stprev = nullptr;
   }
   JavaThread* thread()          const { return _thread;}
   void set_thread(JavaThread* thread) { _thread = thread; }

   SeenThread* next()        const { return _stnext;}
   void set_next(SeenThread* seen) { _stnext = seen; }
   void set_prev(SeenThread* seen) { _stprev = seen; }

  static void print_action_queue(SeenThread* seen, outputStream* st) {
    while (seen != nullptr) {
      seen->thread()->print_value_on(st);
      st->print(", ");
      seen = seen->next();
    }
  }
};

SeenThread* PlaceholderEntry::actionToQueue(PlaceholderTable::classloadAction action) {
  SeenThread* queuehead = nullptr;
  switch (action) {
    case PlaceholderTable::LOAD_INSTANCE:
       queuehead = _loadInstanceThreadQ;
       break;
    case PlaceholderTable::LOAD_SUPER:
       queuehead = _superThreadQ;
       break;
    case PlaceholderTable::DEFINE_CLASS:
       queuehead = _defineThreadQ;
       break;
    default: Unimplemented();
  }
  return queuehead;
}

void PlaceholderEntry::set_threadQ(SeenThread* seenthread, PlaceholderTable::classloadAction action) {
  switch (action) {
    case PlaceholderTable::LOAD_INSTANCE:
       _loadInstanceThreadQ = seenthread;
       break;
    case PlaceholderTable::LOAD_SUPER:
       _superThreadQ = seenthread;
       break;
    case PlaceholderTable::DEFINE_CLASS:
       _defineThreadQ = seenthread;
       break;
    default: Unimplemented();
  }
  return;
}

// Doubly-linked list of Threads per action for class/classloader pair
// Class circularity support: links in thread before loading superclass
// bootstrap loader support:  links in a thread before load_instance_class
// definers: use as queue of define requestors, including owner of
// define token. Appends for debugging of requestor order
void PlaceholderEntry::add_seen_thread(JavaThread* thread, PlaceholderTable::classloadAction action) {
  assert_lock_strong(SystemDictionary_lock);
  SeenThread* threadEntry = new SeenThread(thread);
  SeenThread* seen = actionToQueue(action);

  if (seen == nullptr) {
    set_threadQ(threadEntry, action);
    return;
  }
  SeenThread* next;
  while ((next = seen->next()) != nullptr) {
    seen = next;
  }
  seen->set_next(threadEntry);
  threadEntry->set_prev(seen);
  return;
}

bool PlaceholderEntry::check_seen_thread(JavaThread* thread, PlaceholderTable::classloadAction action) {
  assert_lock_strong(SystemDictionary_lock);
  SeenThread* threadQ = actionToQueue(action);
  SeenThread* seen = threadQ;
  while (seen) {
    if (thread == seen->thread()) {
      return true;
    }
    seen = seen->next();
  }
  return false;
}

// returns true if seenthreadQ is now empty
// Note, caller must ensure probe still exists while holding
// SystemDictionary_lock
// ignores if cleanup has already been done
// if found, deletes SeenThread
bool PlaceholderEntry::remove_seen_thread(JavaThread* thread, PlaceholderTable::classloadAction action) {
  assert_lock_strong(SystemDictionary_lock);
  SeenThread* threadQ = actionToQueue(action);
  SeenThread* seen = threadQ;
  SeenThread* prev = nullptr;
  while (seen) {
    if (thread == seen->thread()) {
      if (prev) {
        prev->set_next(seen->next());
      } else {
        set_threadQ(seen->next(), action);
      }
      if (seen->next()) {
        seen->next()->set_prev(prev);
      }
      delete seen;
      break;
    }
    prev = seen;
    seen = seen->next();
  }
  return (actionToQueue(action) == nullptr);
}


void PlaceholderEntry::set_supername(Symbol* supername) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(_supername == nullptr || _supername->refcount() > 1, "must be referenced also by the loader");
  _supername = supername;
}

// Placeholder objects represent classes currently being loaded.
// All threads examining the placeholder table must hold the
// SystemDictionary_lock, so we don't need special precautions
// on store ordering here.
static PlaceholderEntry* add_entry(Symbol* class_name, ClassLoaderData* loader_data,
                                   Symbol* supername){
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(class_name != nullptr, "adding nullptr obj");

  PlaceholderEntry entry;
  entry.set_supername(supername);
  PlaceholderKey key(class_name, loader_data);
  bool created;
  PlaceholderEntry* table_copy = _placeholders->put_if_absent(key, entry, &created);
  assert(created, "better be absent");
  return table_copy;
}

// Remove a placeholder object.
static void remove_entry(Symbol* class_name, ClassLoaderData* loader_data) {
  assert_locked_or_safepoint(SystemDictionary_lock);

  PlaceholderKey key(class_name, loader_data);
  _placeholders->remove(key);
}


PlaceholderEntry* PlaceholderTable::get_entry(Symbol* class_name, ClassLoaderData* loader_data) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  PlaceholderKey key(class_name, loader_data);
  return _placeholders->get(key);
}

static const char* action_to_string(PlaceholderTable::classloadAction action) {
  switch (action) {
  case PlaceholderTable::LOAD_INSTANCE: return "LOAD_INSTANCE";
  case PlaceholderTable::LOAD_SUPER:    return "LOAD_SUPER";
  case PlaceholderTable::DEFINE_CLASS:  return "DEFINE_CLASS";
 }
 return "";
}

inline void log(Symbol* name, PlaceholderEntry* entry, const char* function, PlaceholderTable::classloadAction action) {
  if (log_is_enabled(Debug, class, load, placeholders)) {
    LogTarget(Debug, class, load, placeholders) lt;
    ResourceMark rm;
    LogStream ls(lt);
    ls.print("entry %s : %s %s ", name->as_C_string(), function, action_to_string(action));
    entry->print_on(&ls);
  }
}

// find_and_add returns probe pointer - old or new
// If no entry exists, add a placeholder entry
// If entry exists, reuse entry
// For both, push SeenThread for classloadAction
// If LOAD_SUPER, this is used for circularity detection for instanceklass loading.
PlaceholderEntry* PlaceholderTable::find_and_add(Symbol* name,
                                                 ClassLoaderData* loader_data,
                                                 classloadAction action,
                                                 Symbol* supername,
                                                 JavaThread* thread) {
  assert(action != LOAD_SUPER || supername != nullptr, "must have a super class name");
  PlaceholderEntry* probe = get_entry(name, loader_data);
  if (probe == nullptr) {
    // Nothing found, add place holder
    probe = add_entry(name, loader_data, supername);
  } else {
    if (action == LOAD_SUPER) {
      probe->set_supername(supername);
    }
  }
  probe->add_seen_thread(thread, action);
  log(name, probe, "find_and_add", action);
  return probe;
}

void PlaceholderTable::initialize(){
  _placeholders = new (mtClass) InternalPlaceholderTable();
}


// placeholder is used to track class loading internal states
// superthreadQ tracks class circularity, while loading superclass/superinterface
// loadInstanceThreadQ tracks load_instance_class calls
// definer() tracks the single thread that owns define token
// defineThreadQ tracks waiters on defining thread's results
// 1st claimant creates placeholder
// find_and_add adds SeenThread entry for appropriate queue
// All claimants remove SeenThread after completing action
// On removal: if definer and all queues empty, remove entry
// Note: you can be in both placeholders and systemDictionary
// Therefore - must always check SD first
void PlaceholderTable::find_and_remove(Symbol* name, ClassLoaderData* loader_data,
                                       classloadAction action,
                                       JavaThread* thread) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  PlaceholderEntry* probe = get_entry(name, loader_data);
  assert(probe != nullptr, "must find an entry");
  log(name, probe, "find_and_remove", action);
  probe->remove_seen_thread(thread, action);
  if (probe->superThreadQ() == nullptr) {
    probe->set_supername(nullptr);
  }
  // If no other threads using this entry, and this thread is not using this entry for other states
  if ((probe->superThreadQ() == nullptr) && (probe->loadInstanceThreadQ() == nullptr)
      && (probe->defineThreadQ() == nullptr) && (probe->definer() == nullptr)) {
    remove_entry(name, loader_data);
  }
}

void PlaceholderKey::print_on(outputStream* st) const {
  _name->print_value_on(st);
  st->print(", loader ");
  _loader_data->print_value_on(st);
}

void PlaceholderEntry::print_on(outputStream* st) const {
  if (supername() != nullptr) {
    st->print(", supername ");
    supername()->print_value_on(st);
  }
  if (definer() != nullptr) {
    st->print(", definer ");
    definer()->print_value_on(st);
  }
  if (instance_klass() != nullptr) {
    st->print(", InstanceKlass ");
    instance_klass()->print_value_on(st);
  }
  st->cr();
  st->print("loadInstanceThreadQ threads:");
  SeenThread::print_action_queue(loadInstanceThreadQ(), st);
  st->cr();
  st->print("superThreadQ threads:");
  SeenThread::print_action_queue(superThreadQ(), st);
  st->cr();
  st->print("defineThreadQ threads:");
  SeenThread::print_action_queue(defineThreadQ(), st);
  st->cr();
}

void PlaceholderTable::print_on(outputStream* st) {
  auto printer = [&] (PlaceholderKey& key, PlaceholderEntry& entry) {
      st->print("placeholder ");
      key.print_on(st);
      entry.print_on(st);
      return true;
  };
  st->print_cr("Placeholder table (table_size=%d, placeholders=%d)",
                _placeholders->table_size(), _placeholders->number_of_entries());
  _placeholders->iterate(printer);
}

void PlaceholderTable::print() { return print_on(tty); }
