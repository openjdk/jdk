/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_PLACEHOLDERS_HPP
#define SHARE_VM_CLASSFILE_PLACEHOLDERS_HPP

#include "runtime/thread.hpp"
#include "utilities/hashtable.hpp"

class PlaceholderEntry;

// Placeholder objects. These represent classes currently
// being loaded, as well as arrays of primitives.
//

class PlaceholderTable : public Hashtable<Symbol*, mtClass> {

public:
  PlaceholderTable(int table_size);

  PlaceholderEntry* new_entry(int hash, Symbol* name, ClassLoaderData* loader_data, bool havesupername, Symbol* supername);
  void free_entry(PlaceholderEntry* entry);

  PlaceholderEntry* bucket(int i) const {
    return (PlaceholderEntry*)Hashtable<Symbol*, mtClass>::bucket(i);
  }

  PlaceholderEntry** bucket_addr(int i) {
    return (PlaceholderEntry**)Hashtable<Symbol*, mtClass>::bucket_addr(i);
  }

  void add_entry(int index, PlaceholderEntry* new_entry) {
    Hashtable<Symbol*, mtClass>::add_entry(index, (HashtableEntry<Symbol*, mtClass>*)new_entry);
  }

  void add_entry(int index, unsigned int hash, Symbol* name,
                ClassLoaderData* loader_data, bool havesupername, Symbol* supername);

  // This returns a Symbol* to match type for SystemDictionary
  Symbol* find_entry(int index, unsigned int hash,
                       Symbol* name, ClassLoaderData* loader_data);

  PlaceholderEntry* get_entry(int index, unsigned int hash,
                       Symbol* name, ClassLoaderData* loader_data);

// caller to create a placeholder entry must enumerate an action
// caller claims ownership of that action
// For parallel classloading:
// multiple LOAD_INSTANCE threads can proceed in parallel
// multiple LOAD_SUPER threads can proceed in parallel
// LOAD_SUPER needed to check for class circularity
// DEFINE_CLASS: ultimately define class must be single threaded
// on a class/classloader basis
// so the head of that queue owns the token
// and the rest of the threads return the result the first thread gets
 enum classloadAction {
    LOAD_INSTANCE = 1,             // calling load_instance_class
    LOAD_SUPER = 2,                // loading superclass for this class
    DEFINE_CLASS = 3               // find_or_define class
 };

  // find_and_add returns probe pointer - old or new
  // If no entry exists, add a placeholder entry and push SeenThread for classloadAction
  // If entry exists, reuse entry and push SeenThread for classloadAction
  PlaceholderEntry* find_and_add(int index, unsigned int hash,
                                 Symbol* name, ClassLoaderData* loader_data,
                                 classloadAction action, Symbol* supername,
                                 Thread* thread);

  void remove_entry(int index, unsigned int hash,
                    Symbol* name, ClassLoaderData* loader_data);

  // find_and_remove first removes SeenThread for classloadAction
  // If all queues are empty and definer is null, remove the PlacheholderEntry completely
  void find_and_remove(int index, unsigned int hash,
                       Symbol* name, ClassLoaderData* loader_data,
                       classloadAction action, Thread* thread);

  void print_on(outputStream* st) const;
  void verify();
};

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
   Thread *_thread;
   SeenThread* _stnext;
   SeenThread* _stprev;
public:
   SeenThread(Thread *thread) {
       _thread = thread;
       _stnext = NULL;
       _stprev = NULL;
   }
   Thread* thread()                const { return _thread;}
   void set_thread(Thread *thread) { _thread = thread; }

   SeenThread* next()              const { return _stnext;}
   void set_next(SeenThread *seen) { _stnext = seen; }
   void set_prev(SeenThread *seen) { _stprev = seen; }

  void print_action_queue(outputStream* st) {
    SeenThread* seen = this;
    while (seen != NULL) {
      seen->thread()->print_value_on(st);
      st->print(", ");
      seen = seen->next();
    }
  }
};

// Placeholder objects represent classes currently being loaded.
// All threads examining the placeholder table must hold the
// SystemDictionary_lock, so we don't need special precautions
// on store ordering here.
// The system dictionary is the only user of this class.

class PlaceholderEntry : public HashtableEntry<Symbol*, mtClass> {

 private:
  ClassLoaderData*  _loader_data;   // initiating loader
  bool              _havesupername; // distinguish between null supername, and unknown
  Symbol*           _supername;
  Thread*           _definer;       // owner of define token
  InstanceKlass*    _instanceKlass; // InstanceKlass from successful define
  SeenThread*       _superThreadQ;  // doubly-linked queue of Threads loading a superclass for this class
  SeenThread*       _loadInstanceThreadQ;  // loadInstance thread
                                    // can be multiple threads if classloader object lock broken by application
                                    // or if classloader supports parallel classloading

  SeenThread*       _defineThreadQ; // queue of Threads trying to define this class
                                    // including _definer
                                    // _definer owns token
                                    // queue waits for and returns results from _definer

 public:
  // Simple accessors, used only by SystemDictionary
  Symbol*            klassname()           const { return literal(); }

  ClassLoaderData*   loader_data()         const { return _loader_data; }
  void               set_loader_data(ClassLoaderData* loader_data) { _loader_data = loader_data; }

  bool               havesupername()       const { return _havesupername; }
  void               set_havesupername(bool havesupername) { _havesupername = havesupername; }

  Symbol*            supername()           const { return _supername; }
  void               set_supername(Symbol* supername) {
    _supername = supername;
    if (_supername != NULL) _supername->increment_refcount();
  }

  Thread*            definer()             const {return _definer; }
  void               set_definer(Thread* definer) { _definer = definer; }

  InstanceKlass*     instance_klass()      const {return _instanceKlass; }
  void               set_instance_klass(InstanceKlass* ik) { _instanceKlass = ik; }

  SeenThread*        superThreadQ()        const { return _superThreadQ; }
  void               set_superThreadQ(SeenThread* SeenThread) { _superThreadQ = SeenThread; }

  SeenThread*        loadInstanceThreadQ() const { return _loadInstanceThreadQ; }
  void               set_loadInstanceThreadQ(SeenThread* SeenThread) { _loadInstanceThreadQ = SeenThread; }

  SeenThread*        defineThreadQ()        const { return _defineThreadQ; }
  void               set_defineThreadQ(SeenThread* SeenThread) { _defineThreadQ = SeenThread; }

  PlaceholderEntry* next() const {
    return (PlaceholderEntry*)HashtableEntry<Symbol*, mtClass>::next();
  }

  PlaceholderEntry** next_addr() {
    return (PlaceholderEntry**)HashtableEntry<Symbol*, mtClass>::next_addr();
  }

  // Test for equality
  // Entries are unique for class/classloader name pair
  bool equals(Symbol* class_name, ClassLoaderData* loader) const {
    return (klassname() == class_name && loader_data() == loader);
  }

  SeenThread* actionToQueue(PlaceholderTable::classloadAction action) {
    SeenThread* queuehead = NULL;
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

  void set_threadQ(SeenThread* seenthread, PlaceholderTable::classloadAction action) {
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

  bool super_load_in_progress() {
     return (_superThreadQ != NULL);
  }

  bool instance_load_in_progress() {
    return (_loadInstanceThreadQ != NULL);
  }

  bool define_class_in_progress() {
    return (_defineThreadQ != NULL);
  }

// Doubly-linked list of Threads per action for class/classloader pair
// Class circularity support: links in thread before loading superclass
// bootstrapsearchpath support: links in a thread before load_instance_class
// definers: use as queue of define requestors, including owner of
// define token. Appends for debugging of requestor order
  void add_seen_thread(Thread* thread, PlaceholderTable::classloadAction action) {
    assert_lock_strong(SystemDictionary_lock);
    SeenThread* threadEntry = new SeenThread(thread);
    SeenThread* seen = actionToQueue(action);

    if (seen == NULL) {
      set_threadQ(threadEntry, action);
      return;
    }
    SeenThread* next;
    while ((next = seen->next()) != NULL) {
      seen = next;
    }
    seen->set_next(threadEntry);
    threadEntry->set_prev(seen);
    return;
  }

  bool check_seen_thread(Thread* thread, PlaceholderTable::classloadAction action) {
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
  bool remove_seen_thread(Thread* thread, PlaceholderTable::classloadAction action) {
    assert_lock_strong(SystemDictionary_lock);
    SeenThread* threadQ = actionToQueue(action);
    SeenThread* seen = threadQ;
    SeenThread* prev = NULL;
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
    return (actionToQueue(action) == NULL);
  }

  // Print method doesn't append a cr
  void print_entry(outputStream* st) const;
  void verify() const;
};

#endif // SHARE_VM_CLASSFILE_PLACEHOLDERS_HPP
