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

#ifndef SHARE_CLASSFILE_PLACEHOLDERS_HPP
#define SHARE_CLASSFILE_PLACEHOLDERS_HPP

#include "oops/symbolHandle.hpp"

class PlaceholderEntry;
class Thread;
class ClassLoaderData;

// Placeholder objects. These represent classes currently
// being loaded, as well as arrays of primitives.
//

class PlaceholderTable : public AllStatic {
 public:
  // caller to create a placeholder entry must enumerate an action
  // caller claims ownership of that action
  // For parallel classloading:
  // multiple LOAD_INSTANCE threads can proceed in parallel
  // multiple DETECT_CIRCULARITY threads can proceed in parallel
  // DETECT_CIRCULARITY needed to check for class circularity
  // DEFINE_CLASS: ultimately define class must be single threaded
  // on a class/classloader basis
  // so the head of that queue owns the token
  // and the rest of the threads return the result the first thread gets
  enum classloadAction {
    LOAD_INSTANCE = 1,             // calling load_instance_class
    DETECT_CIRCULARITY = 2,        // loading while detecting class circularity
    DEFINE_CLASS = 3               // find_or_define class
  };
  static void initialize();
  static PlaceholderEntry* get_entry(Symbol* name, ClassLoaderData* loader_data);

  // find_and_add returns probe pointer - old or new
  // If no entry exists, add a placeholder entry and push SeenThread for classloadAction
  // If entry exists, reuse entry and push SeenThread for classloadAction
  static PlaceholderEntry* find_and_add(Symbol* name, ClassLoaderData* loader_data,
                                        classloadAction action, Symbol* supername,
                                        JavaThread* thread);

  // find_and_remove first removes SeenThread for classloadAction
  // If all queues are empty and definer is null, remove the PlacheholderEntry completely
  static void find_and_remove(Symbol* name, ClassLoaderData* loader_data,
                       classloadAction action, JavaThread* thread);

  static void print_on(outputStream* st);
  static void print();
};

class SeenThread;

// Placeholder objects represent classes currently being loaded.
// All threads examining the placeholder table must hold the
// SystemDictionary_lock, so we don't need special precautions
// on store ordering here.
// The system dictionary is the only user of this class.
class PlaceholderEntry {
  friend class PlaceholderTable;
 private:
  SymbolHandle      _next_klass_name;     // next step in the recursive process of class loading
  JavaThread*       _definer;             // owner of define token
  InstanceKlass*    _instanceKlass;       // InstanceKlass from successful define
  SeenThread*       _circularityThreadQ;  // doubly-linked queue of Threads loading with circularity detection
  SeenThread*       _loadInstanceThreadQ; // loadInstance thread
                                          // This can't be multiple threads since class loading
                                          // waits for this token to be removed.

  SeenThread*       _defineThreadQ; // queue of Threads trying to define this class
                                    // including _definer
                                    // _definer owns token
                                    // queue waits for and returns results from _definer

  SeenThread* actionToQueue(PlaceholderTable::classloadAction action);
  void set_threadQ(SeenThread* seenthread, PlaceholderTable::classloadAction action);
  void add_seen_thread(JavaThread* thread, PlaceholderTable::classloadAction action);
  bool remove_seen_thread(JavaThread* thread, PlaceholderTable::classloadAction action);

  SeenThread*        circularityThreadQ()  const { return _circularityThreadQ; }
  void               set_circularityThreadQ(SeenThread* SeenThread) { _circularityThreadQ = SeenThread; }

  SeenThread*        loadInstanceThreadQ() const { return _loadInstanceThreadQ; }
  void               set_loadInstanceThreadQ(SeenThread* SeenThread) { _loadInstanceThreadQ = SeenThread; }

  SeenThread*        defineThreadQ()       const { return _defineThreadQ; }
  void               set_defineThreadQ(SeenThread* SeenThread) { _defineThreadQ = SeenThread; }
 public:
  PlaceholderEntry() :
     _definer(nullptr), _instanceKlass(nullptr),
     _circularityThreadQ(nullptr), _loadInstanceThreadQ(nullptr), _defineThreadQ(nullptr) { }

  Symbol*            next_klass_name()     const { return _next_klass_name; }
  void               set_next_klass_name(Symbol* next_klass_name);

  JavaThread*        definer()             const {return _definer; }
  void               set_definer(JavaThread* definer) { _definer = definer; }

  InstanceKlass*     instance_klass()      const {return _instanceKlass; }
  void               set_instance_klass(InstanceKlass* ik) { _instanceKlass = ik; }

  bool circularity_detection_in_progress() {
     return (_circularityThreadQ != nullptr);
  }

  bool instance_load_in_progress() {
    return (_loadInstanceThreadQ != nullptr);
  }

  bool define_class_in_progress() {
    return (_defineThreadQ != nullptr);
  }

  // Used for ClassCircularityError checking
  bool check_seen_thread(JavaThread* thread, PlaceholderTable::classloadAction action);

  void print_on(outputStream* st) const;
};

#endif // SHARE_CLASSFILE_PLACEHOLDERS_HPP
