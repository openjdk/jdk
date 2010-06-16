/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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

class ValueMapEntry: public CompilationResourceObj {
 private:
  intx           _hash;
  Value          _value;
  int            _nesting;
  ValueMapEntry* _next;

 public:
  ValueMapEntry(intx hash, Value value, int nesting, ValueMapEntry* next)
    : _hash(hash)
    , _value(value)
    , _nesting(nesting)
    , _next(next)
  {
  }

  intx           hash()      { return _hash; }
  Value          value()     { return _value; }
  int            nesting()   { return _nesting; }
  ValueMapEntry* next()      { return _next; }

  void set_next(ValueMapEntry* next) { _next = next; }
};

define_array(ValueMapEntryArray, ValueMapEntry*)
define_stack(ValueMapEntryList, ValueMapEntryArray)

// ValueMap implements nested hash tables for value numbering.  It
// maintains a set _killed_values which represents the instructions
// which have been killed so far and an array of linked lists of
// ValueMapEntries names _entries.  Each ValueMapEntry has a nesting
// which indicates what ValueMap nesting it belongs to.  Higher
// nesting values are always before lower values in the linked list.
// This allows cloning of parent ValueMaps by simply copying the heads
// of the list.  _entry_count represents the number of reachable
// entries in the ValueMap.  A ValueMap is only allowed to mutate
// ValueMapEntries with the same nesting level.  Adding or removing
// entries at the current nesting level requires updating
// _entry_count.  Elements in the parent's list that get killed can be
// skipped if they are at the head of the list by simply moving to the
// next element in the list and decrementing _entry_count.

class ValueMap: public CompilationResourceObj {
 private:
  int           _nesting;
  ValueMapEntryArray _entries;
  ValueSet      _killed_values;
  int           _entry_count;

  int           nesting()                        { return _nesting; }
  bool          is_local_value_numbering()       { return _nesting == 0; }
  bool          is_global_value_numbering()      { return _nesting > 0; }

  int           entry_count()                    { return _entry_count; }
  int           size()                           { return _entries.length(); }
  ValueMapEntry* entry_at(int i)                 { return _entries.at(i); }

  // calculates the index of a hash value in a hash table of size n
  int           entry_index(intx hash, int n)    { return (unsigned int)hash % n; }

  // if entry_count > size_threshold, the size of the hash table is increased
  int           size_threshold()                 { return size(); }

  // management of the killed-bitset for global value numbering
  void          kill_value(Value v)              { if (is_global_value_numbering()) _killed_values.put(v); }
  bool          is_killed(Value v)               { if (is_global_value_numbering()) return _killed_values.contains(v); else return false; }

  // helper functions
  void          increase_table_size();

#ifndef PRODUCT
  static int _number_of_finds;
  static int _number_of_hits;
  static int _number_of_kills;
#endif // PRODUCT

 public:
  // creation
  ValueMap();                // empty value map
  ValueMap(ValueMap* old);   // value map with increased nesting

  // manipulation
  Value find_insert(Value x);

  void kill_memory();
  void kill_field(ciField* field);
  void kill_array(ValueType* type);
  void kill_exception();
  void kill_map(ValueMap* map);
  void kill_all();

#ifndef PRODUCT
  // debugging/printing
  void print();

  static void reset_statistics();
  static void print_statistics();
#endif
};

define_array(ValueMapArray, ValueMap*)


class ValueNumberingVisitor: public InstructionVisitor {
 protected:
  // called by visitor functions for instructions that kill values
  virtual void kill_memory() = 0;
  virtual void kill_field(ciField* field) = 0;
  virtual void kill_array(ValueType* type) = 0;

  // visitor functions
  void do_StoreField     (StoreField*      x) {
    if (!x->is_initialized()) {
      kill_memory();
    } else {
      kill_field(x->field());
    }
  }
  void do_StoreIndexed   (StoreIndexed*    x) { kill_array(x->type()); }
  void do_MonitorEnter   (MonitorEnter*    x) { kill_memory(); }
  void do_MonitorExit    (MonitorExit*     x) { kill_memory(); }
  void do_Invoke         (Invoke*          x) { kill_memory(); }
  void do_UnsafePutRaw   (UnsafePutRaw*    x) { kill_memory(); }
  void do_UnsafePutObject(UnsafePutObject* x) { kill_memory(); }
  void do_Intrinsic      (Intrinsic*       x) { if (!x->preserves_state()) kill_memory(); }

  void do_Phi            (Phi*             x) { /* nothing to do */ }
  void do_Local          (Local*           x) { /* nothing to do */ }
  void do_Constant       (Constant*        x) { /* nothing to do */ }
  void do_LoadField      (LoadField*       x) {
    if (!x->is_initialized()) {
      kill_memory();
    }
  }
  void do_ArrayLength    (ArrayLength*     x) { /* nothing to do */ }
  void do_LoadIndexed    (LoadIndexed*     x) { /* nothing to do */ }
  void do_NegateOp       (NegateOp*        x) { /* nothing to do */ }
  void do_ArithmeticOp   (ArithmeticOp*    x) { /* nothing to do */ }
  void do_ShiftOp        (ShiftOp*         x) { /* nothing to do */ }
  void do_LogicOp        (LogicOp*         x) { /* nothing to do */ }
  void do_CompareOp      (CompareOp*       x) { /* nothing to do */ }
  void do_IfOp           (IfOp*            x) { /* nothing to do */ }
  void do_Convert        (Convert*         x) { /* nothing to do */ }
  void do_NullCheck      (NullCheck*       x) { /* nothing to do */ }
  void do_NewInstance    (NewInstance*     x) { /* nothing to do */ }
  void do_NewTypeArray   (NewTypeArray*    x) { /* nothing to do */ }
  void do_NewObjectArray (NewObjectArray*  x) { /* nothing to do */ }
  void do_NewMultiArray  (NewMultiArray*   x) { /* nothing to do */ }
  void do_CheckCast      (CheckCast*       x) { /* nothing to do */ }
  void do_InstanceOf     (InstanceOf*      x) { /* nothing to do */ }
  void do_BlockBegin     (BlockBegin*      x) { /* nothing to do */ }
  void do_Goto           (Goto*            x) { /* nothing to do */ }
  void do_If             (If*              x) { /* nothing to do */ }
  void do_IfInstanceOf   (IfInstanceOf*    x) { /* nothing to do */ }
  void do_TableSwitch    (TableSwitch*     x) { /* nothing to do */ }
  void do_LookupSwitch   (LookupSwitch*    x) { /* nothing to do */ }
  void do_Return         (Return*          x) { /* nothing to do */ }
  void do_Throw          (Throw*           x) { /* nothing to do */ }
  void do_Base           (Base*            x) { /* nothing to do */ }
  void do_OsrEntry       (OsrEntry*        x) { /* nothing to do */ }
  void do_ExceptionObject(ExceptionObject* x) { /* nothing to do */ }
  void do_RoundFP        (RoundFP*         x) { /* nothing to do */ }
  void do_UnsafeGetRaw   (UnsafeGetRaw*    x) { /* nothing to do */ }
  void do_UnsafeGetObject(UnsafeGetObject* x) { /* nothing to do */ }
  void do_UnsafePrefetchRead (UnsafePrefetchRead*  x) { /* nothing to do */ }
  void do_UnsafePrefetchWrite(UnsafePrefetchWrite* x) { /* nothing to do */ }
  void do_ProfileCall    (ProfileCall*     x) { /* nothing to do */ }
  void do_ProfileCounter (ProfileCounter*  x) { /* nothing to do */ }
};


class ValueNumberingEffects: public ValueNumberingVisitor {
 private:
  ValueMap*     _map;

 public:
  // implementation for abstract methods of ValueNumberingVisitor
  void          kill_memory()                    { _map->kill_memory(); }
  void          kill_field(ciField* field)       { _map->kill_field(field); }
  void          kill_array(ValueType* type)      { _map->kill_array(type); }

  ValueNumberingEffects(ValueMap* map): _map(map) {}
};


class GlobalValueNumbering: public ValueNumberingVisitor {
 private:
  ValueMap*     _current_map;     // value map of current block
  ValueMapArray _value_maps;      // list of value maps for all blocks

 public:
  // accessors
  ValueMap*     current_map()                    { return _current_map; }
  ValueMap*     value_map_of(BlockBegin* block)  { return _value_maps.at(block->linear_scan_number()); }
  void          set_value_map_of(BlockBegin* block, ValueMap* map)   { assert(value_map_of(block) == NULL, ""); _value_maps.at_put(block->linear_scan_number(), map); }

  // implementation for abstract methods of ValueNumberingVisitor
  void          kill_memory()                    { current_map()->kill_memory(); }
  void          kill_field(ciField* field)       { current_map()->kill_field(field); }
  void          kill_array(ValueType* type)      { current_map()->kill_array(type); }

  // main entry point that performs global value numbering
  GlobalValueNumbering(IR* ir);
};
