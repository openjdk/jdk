/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

class ciBitData;
class ciCounterData;
class ciJumpData;
class ciReceiverTypeData;
class ciRetData;
class ciBranchData;
class ciArrayData;
class ciMultiBranchData;
class ciArgInfoData;

typedef ProfileData ciProfileData;

class ciBitData : public BitData {
public:
  ciBitData(DataLayout* layout) : BitData(layout) {};
};

class ciCounterData : public CounterData {
public:
  ciCounterData(DataLayout* layout) : CounterData(layout) {};
};

class ciJumpData : public JumpData {
public:
  ciJumpData(DataLayout* layout) : JumpData(layout) {};
};

class ciReceiverTypeData : public ReceiverTypeData {
public:
  ciReceiverTypeData(DataLayout* layout) : ReceiverTypeData(layout) {};

  void set_receiver(uint row, ciKlass* recv) {
    assert((uint)row < row_limit(), "oob");
    set_intptr_at(receiver0_offset + row * receiver_type_row_cell_count,
                  (intptr_t) recv);
  }

  ciKlass* receiver(uint row) {
    assert((uint)row < row_limit(), "oob");
    ciObject* recv = (ciObject*)intptr_at(receiver0_offset + row * receiver_type_row_cell_count);
    assert(recv == NULL || recv->is_klass(), "wrong type");
    return (ciKlass*)recv;
  }

  // Copy & translate from oop based ReceiverTypeData
  virtual void translate_from(ProfileData* data) {
    translate_receiver_data_from(data);
  }
  void translate_receiver_data_from(ProfileData* data);
#ifndef PRODUCT
  void print_data_on(outputStream* st);
  void print_receiver_data_on(outputStream* st);
#endif
};

class ciVirtualCallData : public VirtualCallData {
  // Fake multiple inheritance...  It's a ciReceiverTypeData also.
  ciReceiverTypeData* rtd_super() { return (ciReceiverTypeData*) this; }

public:
  ciVirtualCallData(DataLayout* layout) : VirtualCallData(layout) {};

  void set_receiver(uint row, ciKlass* recv) {
    rtd_super()->set_receiver(row, recv);
  }

  ciKlass* receiver(uint row) {
    return rtd_super()->receiver(row);
  }

  // Copy & translate from oop based VirtualCallData
  virtual void translate_from(ProfileData* data) {
    rtd_super()->translate_receiver_data_from(data);
  }
#ifndef PRODUCT
  void print_data_on(outputStream* st);
#endif
};


class ciRetData : public RetData {
public:
  ciRetData(DataLayout* layout) : RetData(layout) {};
};

class ciBranchData : public BranchData {
public:
  ciBranchData(DataLayout* layout) : BranchData(layout) {};
};

class ciArrayData : public ArrayData {
public:
  ciArrayData(DataLayout* layout) : ArrayData(layout) {};
};

class ciMultiBranchData : public MultiBranchData {
public:
  ciMultiBranchData(DataLayout* layout) : MultiBranchData(layout) {};
};

class ciArgInfoData : public ArgInfoData {
public:
  ciArgInfoData(DataLayout* layout) : ArgInfoData(layout) {};
};

// ciMethodData
//
// This class represents a methodDataOop in the HotSpot virtual
// machine.

class ciMethodData : public ciObject {
  CI_PACKAGE_ACCESS

private:
  // Size in bytes
  int _data_size;
  int _extra_data_size;

  // Data entries
  intptr_t* _data;

  // Cached hint for data_before()
  int _hint_di;

  // Is data attached?  And is it mature?
  enum { empty_state, immature_state, mature_state };
  u_char _state;

  // Set this true if empty extra_data slots are ever witnessed.
  u_char _saw_free_extra_data;

  // Support for interprocedural escape analysis
  intx              _eflags;          // flags on escape information
  intx              _arg_local;       // bit set of non-escaping arguments
  intx              _arg_stack;       // bit set of stack-allocatable arguments
  intx              _arg_returned;    // bit set of returned arguments

  // Maturity of the oop when the snapshot is taken.
  int _current_mileage;

  // These counters hold the age of MDO in tiered. In tiered we can have the same method
  // running at different compilation levels concurrently. So, in order to precisely measure
  // its maturity we need separate counters.
  int _invocation_counter;
  int _backedge_counter;

  // Coherent snapshot of original header.
  methodDataOopDesc _orig;

  ciMethodData(methodDataHandle h_md);
  ciMethodData();

  // Accessors
  int data_size() const { return _data_size; }
  int extra_data_size() const { return _extra_data_size; }
  intptr_t * data() const { return _data; }

  methodDataOop get_methodDataOop() const {
    if (handle() == NULL) return NULL;
    methodDataOop mdo = (methodDataOop)get_oop();
    assert(mdo != NULL, "illegal use of unloaded method data");
    return mdo;
  }

  const char* type_string()                      { return "ciMethodData"; }

  void print_impl(outputStream* st);

  DataLayout* data_layout_at(int data_index) const {
    assert(data_index % sizeof(intptr_t) == 0, "unaligned");
    return (DataLayout*) (((address)_data) + data_index);
  }

  bool out_of_bounds(int data_index) {
    return data_index >= data_size();
  }

  // hint accessors
  int      hint_di() const  { return _hint_di; }
  void set_hint_di(int di)  {
    assert(!out_of_bounds(di), "hint_di out of bounds");
    _hint_di = di;
  }
  ciProfileData* data_before(int bci) {
    // avoid SEGV on this edge case
    if (data_size() == 0)
      return NULL;
    int hint = hint_di();
    if (data_layout_at(hint)->bci() <= bci)
      return data_at(hint);
    return first_data();
  }


  // What is the index of the first data entry?
  int first_di() { return 0; }

  ciArgInfoData *arg_info() const;

public:
  bool is_method_data()  { return true; }
  bool is_empty() { return _state == empty_state; }
  bool is_mature() { return _state == mature_state; }

  int creation_mileage() { return _orig.creation_mileage(); }
  int current_mileage()  { return _current_mileage; }

  int invocation_count() { return _invocation_counter; }
  int backedge_count()   { return _backedge_counter;   }
  // Transfer information about the method to methodDataOop.
  // would_profile means we would like to profile this method,
  // meaning it's not trivial.
  void set_would_profile(bool p);
  // Also set the numer of loops and blocks in the method.
  // Again, this is used to determine if a method is trivial.
  void set_compilation_stats(short loops, short blocks);

  void load_data();

  // Convert a dp (data pointer) to a di (data index).
  int dp_to_di(address dp) {
    return dp - ((address)_data);
  }

  // Get the data at an arbitrary (sort of) data index.
  ciProfileData* data_at(int data_index);

  // Walk through the data in order.
  ciProfileData* first_data() { return data_at(first_di()); }
  ciProfileData* next_data(ciProfileData* current);
  bool is_valid(ciProfileData* current) { return current != NULL; }

  // Get the data at an arbitrary bci, or NULL if there is none.
  ciProfileData* bci_to_data(int bci);
  ciProfileData* bci_to_extra_data(int bci, bool create_if_missing);

  uint overflow_trap_count() const {
    return _orig.overflow_trap_count();
  }
  uint overflow_recompile_count() const {
    return _orig.overflow_recompile_count();
  }
  uint decompile_count() const {
    return _orig.decompile_count();
  }
  uint trap_count(int reason) const {
    return _orig.trap_count(reason);
  }
  uint trap_reason_limit() const { return _orig.trap_reason_limit(); }
  uint trap_count_limit()  const { return _orig.trap_count_limit(); }

  // Helpful query functions that decode trap_state.
  int has_trap_at(ciProfileData* data, int reason);
  int has_trap_at(int bci, int reason) {
    return has_trap_at(bci_to_data(bci), reason);
  }
  int trap_recompiled_at(ciProfileData* data);
  int trap_recompiled_at(int bci) {
    return trap_recompiled_at(bci_to_data(bci));
  }

  void clear_escape_info();
  bool has_escape_info();
  void update_escape_info();

  void set_eflag(methodDataOopDesc::EscapeFlag f);
  void clear_eflag(methodDataOopDesc::EscapeFlag f);
  bool eflag_set(methodDataOopDesc::EscapeFlag f) const;

  void set_arg_local(int i);
  void set_arg_stack(int i);
  void set_arg_returned(int i);
  void set_arg_modified(int arg, uint val);

  bool is_arg_local(int i) const;
  bool is_arg_stack(int i) const;
  bool is_arg_returned(int i) const;
  uint arg_modified(int arg) const;

  // Code generation helper
  ByteSize offset_of_slot(ciProfileData* data, ByteSize slot_offset_in_data);
  int      byte_offset_of_slot(ciProfileData* data, ByteSize slot_offset_in_data) { return in_bytes(offset_of_slot(data, slot_offset_in_data)); }

#ifndef PRODUCT
  // printing support for method data
  void print();
  void print_data_on(outputStream* st);
#endif
};
