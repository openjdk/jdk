/*
 * Copyright 1998-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_oopMap.cpp.incl"

// OopMapStream

OopMapStream::OopMapStream(OopMap* oop_map) {
  if(oop_map->omv_data() == NULL) {
    _stream = new CompressedReadStream(oop_map->write_stream()->buffer());
  } else {
    _stream = new CompressedReadStream(oop_map->omv_data());
  }
  _mask = OopMapValue::type_mask_in_place;
  _size = oop_map->omv_count();
  _position = 0;
  _valid_omv = false;
}


OopMapStream::OopMapStream(OopMap* oop_map, int oop_types_mask) {
  if(oop_map->omv_data() == NULL) {
    _stream = new CompressedReadStream(oop_map->write_stream()->buffer());
  } else {
    _stream = new CompressedReadStream(oop_map->omv_data());
  }
  _mask = oop_types_mask;
  _size = oop_map->omv_count();
  _position = 0;
  _valid_omv = false;
}


void OopMapStream::find_next() {
  while(_position++ < _size) {
    _omv.read_from(_stream);
    if(((int)_omv.type() & _mask) > 0) {
      _valid_omv = true;
      return;
    }
  }
  _valid_omv = false;
}


// OopMap

// frame_size units are stack-slots (4 bytes) NOT intptr_t; we can name odd
// slots to hold 4-byte values like ints and floats in the LP64 build.
OopMap::OopMap(int frame_size, int arg_count) {
  // OopMaps are usually quite so small, so pick a small initial size
  set_write_stream(new CompressedWriteStream(32));
  set_omv_data(NULL);
  set_omv_count(0);

#ifdef ASSERT
  _locs_length = VMRegImpl::stack2reg(0)->value() + frame_size + arg_count;
  _locs_used   = NEW_RESOURCE_ARRAY(OopMapValue::oop_types, _locs_length);
  for(int i = 0; i < _locs_length; i++) _locs_used[i] = OopMapValue::unused_value;
#endif
}


OopMap::OopMap(OopMap::DeepCopyToken, OopMap* source) {
  // This constructor does a deep copy
  // of the source OopMap.
  set_write_stream(new CompressedWriteStream(source->omv_count() * 2));
  set_omv_data(NULL);
  set_omv_count(0);
  set_offset(source->offset());

#ifdef ASSERT
  _locs_length = source->_locs_length;
  _locs_used = NEW_RESOURCE_ARRAY(OopMapValue::oop_types, _locs_length);
  for(int i = 0; i < _locs_length; i++) _locs_used[i] = OopMapValue::unused_value;
#endif

  // We need to copy the entries too.
  for (OopMapStream oms(source); !oms.is_done(); oms.next()) {
    OopMapValue omv = oms.current();
    omv.write_on(write_stream());
    increment_count();
  }
}


OopMap* OopMap::deep_copy() {
  return new OopMap(_deep_copy_token, this);
}


void OopMap::copy_to(address addr) {
  memcpy(addr,this,sizeof(OopMap));
  memcpy(addr + sizeof(OopMap),write_stream()->buffer(),write_stream()->position());
  OopMap* new_oop = (OopMap*)addr;
  new_oop->set_omv_data_size(write_stream()->position());
  new_oop->set_omv_data((unsigned char *)(addr + sizeof(OopMap)));
  new_oop->set_write_stream(NULL);
}


int OopMap::heap_size() const {
  int size = sizeof(OopMap);
  int align = sizeof(void *) - 1;
  if(write_stream() != NULL) {
    size += write_stream()->position();
  } else {
    size += omv_data_size();
  }
  // Align to a reasonable ending point
  size = ((size+align) & ~align);
  return size;
}

// frame_size units are stack-slots (4 bytes) NOT intptr_t; we can name odd
// slots to hold 4-byte values like ints and floats in the LP64 build.
void OopMap::set_xxx(VMReg reg, OopMapValue::oop_types x, VMReg optional) {

  assert(reg->value() < _locs_length, "too big reg value for stack size");
  assert( _locs_used[reg->value()] == OopMapValue::unused_value, "cannot insert twice" );
  debug_only( _locs_used[reg->value()] = x; )

  OopMapValue o(reg, x);

  if(x == OopMapValue::callee_saved_value) {
    // This can never be a stack location, so we don't need to transform it.
    assert(optional->is_reg(), "Trying to callee save a stack location");
    o.set_content_reg(optional);
  } else if(x == OopMapValue::derived_oop_value) {
    o.set_content_reg(optional);
  }

  o.write_on(write_stream());
  increment_count();
}


void OopMap::set_oop(VMReg reg) {
  set_xxx(reg, OopMapValue::oop_value, VMRegImpl::Bad());
}


void OopMap::set_value(VMReg reg) {
  // At this time, we only need value entries in our OopMap when ZapDeadCompiledLocals is active.
  if (ZapDeadCompiledLocals)
    set_xxx(reg, OopMapValue::value_value, VMRegImpl::Bad());
}


void OopMap::set_narrowoop(VMReg reg) {
  set_xxx(reg, OopMapValue::narrowoop_value, VMRegImpl::Bad());
}


void OopMap::set_callee_saved(VMReg reg, VMReg caller_machine_register ) {
  set_xxx(reg, OopMapValue::callee_saved_value, caller_machine_register);
}


void OopMap::set_derived_oop(VMReg reg, VMReg derived_from_local_register ) {
  if( reg == derived_from_local_register ) {
    // Actually an oop, derived shares storage with base,
    set_oop(reg);
  } else {
    set_xxx(reg, OopMapValue::derived_oop_value, derived_from_local_register);
  }
}

// OopMapSet

OopMapSet::OopMapSet() {
  set_om_size(MinOopMapAllocation);
  set_om_count(0);
  OopMap** temp = NEW_RESOURCE_ARRAY(OopMap*, om_size());
  set_om_data(temp);
}


void OopMapSet::grow_om_data() {
  int new_size = om_size() * 2;
  OopMap** new_data = NEW_RESOURCE_ARRAY(OopMap*, new_size);
  memcpy(new_data,om_data(),om_size() * sizeof(OopMap*));
  set_om_size(new_size);
  set_om_data(new_data);
}


void OopMapSet::copy_to(address addr) {
  address temp = addr;
  int align = sizeof(void *) - 1;
  // Copy this
  memcpy(addr,this,sizeof(OopMapSet));
  temp += sizeof(OopMapSet);
  temp = (address)((intptr_t)(temp + align) & ~align);
  // Do the needed fixups to the new OopMapSet
  OopMapSet* new_set = (OopMapSet*)addr;
  new_set->set_om_data((OopMap**)temp);
  // Allow enough space for the OopMap pointers
  temp += (om_count() * sizeof(OopMap*));

  for(int i=0; i < om_count(); i++) {
    OopMap* map = at(i);
    map->copy_to((address)temp);
    new_set->set(i,(OopMap*)temp);
    temp += map->heap_size();
  }
  // This "locks" the OopMapSet
  new_set->set_om_size(-1);
}


void OopMapSet::add_gc_map(int pc_offset, OopMap *map ) {
  assert(om_size() != -1,"Cannot grow a fixed OopMapSet");

  if(om_count() >= om_size()) {
    grow_om_data();
  }
  map->set_offset(pc_offset);

#ifdef ASSERT
  if(om_count() > 0) {
    OopMap* last = at(om_count()-1);
    if (last->offset() == map->offset() ) {
      fatal("OopMap inserted twice");
    }
    if(last->offset() > map->offset()) {
      tty->print_cr( "WARNING, maps not sorted: pc[%d]=%d, pc[%d]=%d",
                      om_count(),last->offset(),om_count()+1,map->offset());
    }
  }
#endif // ASSERT

  set(om_count(),map);
  increment_count();
}


int OopMapSet::heap_size() const {
  // The space we use
  int size = sizeof(OopMap);
  int align = sizeof(void *) - 1;
  size = ((size+align) & ~align);
  size += om_count() * sizeof(OopMap*);

  // Now add in the space needed for the indivdiual OopMaps
  for(int i=0; i < om_count(); i++) {
    size += at(i)->heap_size();
  }
  // We don't need to align this, it will be naturally pointer aligned
  return size;
}


OopMap* OopMapSet::singular_oop_map() {
  guarantee(om_count() == 1, "Make sure we only have a single gc point");
  return at(0);
}


OopMap* OopMapSet::find_map_at_offset(int pc_offset) const {
  int i, len = om_count();
  assert( len > 0, "must have pointer maps" );

  // Scan through oopmaps. Stop when current offset is either equal or greater
  // than the one we are looking for.
  for( i = 0; i < len; i++) {
    if( at(i)->offset() >= pc_offset )
      break;
  }

  assert( i < len, "oopmap not found" );

  OopMap* m = at(i);
  assert( m->offset() == pc_offset, "oopmap not found" );
  return m;
}

class DoNothingClosure: public OopClosure {
 public:
  void do_oop(oop* p)       {}
  void do_oop(narrowOop* p) {}
};
static DoNothingClosure do_nothing;

static void add_derived_oop(oop* base, oop* derived) {
#ifndef TIERED
  COMPILER1_PRESENT(ShouldNotReachHere();)
#endif // TIERED
#ifdef COMPILER2
  DerivedPointerTable::add(derived, base);
#endif // COMPILER2
}


#ifndef PRODUCT
static void trace_codeblob_maps(const frame *fr, const RegisterMap *reg_map) {
  // Print oopmap and regmap
  tty->print_cr("------ ");
  CodeBlob* cb = fr->cb();
  OopMapSet* maps = cb->oop_maps();
  OopMap* map = cb->oop_map_for_return_address(fr->pc());
  map->print();
  if( cb->is_nmethod() ) {
    nmethod* nm = (nmethod*)cb;
    // native wrappers have no scope data, it is implied
    if (nm->is_native_method()) {
      tty->print("bci: 0 (native)");
    } else {
      ScopeDesc* scope  = nm->scope_desc_at(fr->pc());
      tty->print("bci: %d ",scope->bci());
    }
  }
  tty->cr();
  fr->print_on(tty);
  tty->print("     ");
  cb->print_value_on(tty);  tty->cr();
  reg_map->print();
  tty->print_cr("------ ");

}
#endif // PRODUCT

void OopMapSet::oops_do(const frame *fr, const RegisterMap* reg_map, OopClosure* f) {
  // add derived oops to a table
  all_do(fr, reg_map, f, add_derived_oop, &do_nothing);
}


void OopMapSet::all_do(const frame *fr, const RegisterMap *reg_map,
                       OopClosure* oop_fn, void derived_oop_fn(oop*, oop*),
                       OopClosure* value_fn) {
  CodeBlob* cb = fr->cb();
  assert(cb != NULL, "no codeblob");

  NOT_PRODUCT(if (TraceCodeBlobStacks) trace_codeblob_maps(fr, reg_map);)

  OopMapSet* maps = cb->oop_maps();
  OopMap* map = cb->oop_map_for_return_address(fr->pc());
  assert(map != NULL, "no ptr map found");

  // handle derived pointers first (otherwise base pointer may be
  // changed before derived pointer offset has been collected)
  OopMapValue omv;
  {
    OopMapStream oms(map,OopMapValue::derived_oop_value);
    if (!oms.is_done()) {
#ifndef TIERED
      COMPILER1_PRESENT(ShouldNotReachHere();)
#endif // !TIERED
      // Protect the operation on the derived pointers.  This
      // protects the addition of derived pointers to the shared
      // derived pointer table in DerivedPointerTable::add().
      MutexLockerEx x(DerivedPointerTableGC_lock, Mutex::_no_safepoint_check_flag);
      do {
        omv = oms.current();
        oop* loc = fr->oopmapreg_to_location(omv.reg(),reg_map);
        if ( loc != NULL ) {
          oop *base_loc    = fr->oopmapreg_to_location(omv.content_reg(), reg_map);
          oop *derived_loc = loc;
          oop val = *base_loc;
          if (val == (oop)NULL || Universe::is_narrow_oop_base(val)) {
            // Ignore NULL oops and decoded NULL narrow oops which
            // equal to Universe::narrow_oop_base when a narrow oop
            // implicit null check is used in compiled code.
            // The narrow_oop_base could be NULL or be the address
            // of the page below heap depending on compressed oops mode.
          } else
            derived_oop_fn(base_loc, derived_loc);
        }
        oms.next();
      }  while (!oms.is_done());
    }
  }

  // We want coop, value and oop oop_types
  int mask = OopMapValue::oop_value | OopMapValue::value_value | OopMapValue::narrowoop_value;
  {
    for (OopMapStream oms(map,mask); !oms.is_done(); oms.next()) {
      omv = oms.current();
      oop* loc = fr->oopmapreg_to_location(omv.reg(),reg_map);
      if ( loc != NULL ) {
        if ( omv.type() == OopMapValue::oop_value ) {
          oop val = *loc;
          if (val == (oop)NULL || Universe::is_narrow_oop_base(val)) {
            // Ignore NULL oops and decoded NULL narrow oops which
            // equal to Universe::narrow_oop_base when a narrow oop
            // implicit null check is used in compiled code.
            // The narrow_oop_base could be NULL or be the address
            // of the page below heap depending on compressed oops mode.
            continue;
          }
#ifdef ASSERT
          if ((((uintptr_t)loc & (sizeof(*loc)-1)) != 0) ||
             !Universe::heap()->is_in_or_null(*loc)) {
            tty->print_cr("# Found non oop pointer.  Dumping state at failure");
            // try to dump out some helpful debugging information
            trace_codeblob_maps(fr, reg_map);
            omv.print();
            tty->print_cr("register r");
            omv.reg()->print();
            tty->print_cr("loc = %p *loc = %p\n", loc, (address)*loc);
            // do the real assert.
            assert(Universe::heap()->is_in_or_null(*loc), "found non oop pointer");
          }
#endif // ASSERT
          oop_fn->do_oop(loc);
        } else if ( omv.type() == OopMapValue::value_value ) {
          assert((*loc) == (oop)NULL || !Universe::is_narrow_oop_base(*loc),
                 "found invalid value pointer");
          value_fn->do_oop(loc);
        } else if ( omv.type() == OopMapValue::narrowoop_value ) {
          narrowOop *nl = (narrowOop*)loc;
#ifndef VM_LITTLE_ENDIAN
          if (!omv.reg()->is_stack()) {
            // compressed oops in registers only take up 4 bytes of an
            // 8 byte register but they are in the wrong part of the
            // word so adjust loc to point at the right place.
            nl = (narrowOop*)((address)nl + 4);
          }
#endif
          oop_fn->do_oop(nl);
        }
      }
    }
  }
}


// Update callee-saved register info for the following frame
void OopMapSet::update_register_map(const frame *fr, RegisterMap *reg_map) {
  ResourceMark rm;
  CodeBlob* cb = fr->cb();
  assert(cb != NULL, "no codeblob");

  // Any reg might be saved by a safepoint handler (see generate_handler_blob).
  const int max_saved_on_entry_reg_count = ConcreteRegisterImpl::number_of_registers;
  assert( reg_map->_update_for_id == NULL || fr->is_older(reg_map->_update_for_id),
         "already updated this map; do not 'update' it twice!" );
  debug_only(reg_map->_update_for_id = fr->id());

  // Check if caller must update oop argument
  assert((reg_map->include_argument_oops() ||
          !cb->caller_must_gc_arguments(reg_map->thread())),
         "include_argument_oops should already be set");

  int nof_callee = 0;
  oop*        locs[2*max_saved_on_entry_reg_count+1];
  VMReg regs[2*max_saved_on_entry_reg_count+1];
  // ("+1" because max_saved_on_entry_reg_count might be zero)

  // Scan through oopmap and find location of all callee-saved registers
  // (we do not do update in place, since info could be overwritten)

  address pc = fr->pc();

  OopMap* map  = cb->oop_map_for_return_address(pc);

  assert(map != NULL, " no ptr map found");

  OopMapValue omv;
  for(OopMapStream oms(map,OopMapValue::callee_saved_value); !oms.is_done(); oms.next()) {
    omv = oms.current();
    assert(nof_callee < 2*max_saved_on_entry_reg_count, "overflow");
    regs[nof_callee] = omv.content_reg();
    locs[nof_callee] = fr->oopmapreg_to_location(omv.reg(),reg_map);
    nof_callee++;
  }

  // Check that runtime stubs save all callee-saved registers
#ifdef COMPILER2
  assert(cb->is_compiled_by_c1() || !cb->is_runtime_stub() ||
         (nof_callee >= SAVED_ON_ENTRY_REG_COUNT || nof_callee >= C_SAVED_ON_ENTRY_REG_COUNT),
         "must save all");
#endif // COMPILER2

  // Copy found callee-saved register to reg_map
  for(int i = 0; i < nof_callee; i++) {
    reg_map->set_location(regs[i], (address)locs[i]);
  }
}

//=============================================================================
// Non-Product code

#ifndef PRODUCT

bool OopMap::has_derived_pointer() const {
#ifndef TIERED
  COMPILER1_PRESENT(return false);
#endif // !TIERED
#ifdef COMPILER2
  OopMapStream oms((OopMap*)this,OopMapValue::derived_oop_value);
  return oms.is_done();
#else
  return false;
#endif // COMPILER2
}

#endif //PRODUCT

// Printing code is present in product build for -XX:+PrintAssembly.

static
void print_register_type(OopMapValue::oop_types x, VMReg optional,
                         outputStream* st) {
  switch( x ) {
  case OopMapValue::oop_value:
    st->print("Oop");
    break;
  case OopMapValue::value_value:
    st->print("Value" );
    break;
  case OopMapValue::narrowoop_value:
    tty->print("NarrowOop" );
    break;
  case OopMapValue::callee_saved_value:
    st->print("Callers_" );
    optional->print_on(st);
    break;
  case OopMapValue::derived_oop_value:
    st->print("Derived_oop_" );
    optional->print_on(st);
    break;
  default:
    ShouldNotReachHere();
  }
}


void OopMapValue::print_on(outputStream* st) const {
  reg()->print_on(st);
  st->print("=");
  print_register_type(type(),content_reg(),st);
  st->print(" ");
}


void OopMap::print_on(outputStream* st) const {
  OopMapValue omv;
  st->print("OopMap{");
  for(OopMapStream oms((OopMap*)this); !oms.is_done(); oms.next()) {
    omv = oms.current();
    omv.print_on(st);
  }
  st->print("off=%d}", (int) offset());
}


void OopMapSet::print_on(outputStream* st) const {
  int i, len = om_count();

  st->print_cr("OopMapSet contains %d OopMaps\n",len);

  for( i = 0; i < len; i++) {
    OopMap* m = at(i);
    st->print_cr("#%d ",i);
    m->print_on(st);
    st->cr();
  }
}



//------------------------------DerivedPointerTable---------------------------

#ifdef COMPILER2

class DerivedPointerEntry : public CHeapObj {
 private:
  oop*     _location; // Location of derived pointer (also pointing to the base)
  intptr_t _offset;   // Offset from base pointer
 public:
  DerivedPointerEntry(oop* location, intptr_t offset) { _location = location; _offset = offset; }
  oop* location()    { return _location; }
  intptr_t  offset() { return _offset; }
};


GrowableArray<DerivedPointerEntry*>* DerivedPointerTable::_list = NULL;
bool DerivedPointerTable::_active = false;


void DerivedPointerTable::clear() {
  // The first time, we create the list.  Otherwise it should be
  // empty.  If not, then we have probably forgotton to call
  // update_pointers after last GC/Scavenge.
  assert (!_active, "should not be active");
  assert(_list == NULL || _list->length() == 0, "table not empty");
  if (_list == NULL) {
    _list = new (ResourceObj::C_HEAP) GrowableArray<DerivedPointerEntry*>(10, true); // Allocated on C heap
  }
  _active = true;
}


// Returns value of location as an int
intptr_t value_of_loc(oop *pointer) { return (intptr_t)(*pointer); }


void DerivedPointerTable::add(oop *derived_loc, oop *base_loc) {
  assert(Universe::heap()->is_in_or_null(*base_loc), "not an oop");
  assert(derived_loc != base_loc, "Base and derived in same location");
  if (_active) {
    assert(*derived_loc != (oop)base_loc, "location already added");
    assert(_list != NULL, "list must exist");
    intptr_t offset = value_of_loc(derived_loc) - value_of_loc(base_loc);
    assert(offset >= -1000000, "wrong derived pointer info");

    if (TraceDerivedPointers) {
      tty->print_cr(
        "Add derived pointer@" INTPTR_FORMAT
        " - Derived: " INTPTR_FORMAT
        " Base: " INTPTR_FORMAT " (@" INTPTR_FORMAT ") (Offset: %d)",
        derived_loc, (address)*derived_loc, (address)*base_loc, base_loc, offset
      );
    }
    // Set derived oop location to point to base.
    *derived_loc = (oop)base_loc;
    assert_lock_strong(DerivedPointerTableGC_lock);
    DerivedPointerEntry *entry = new DerivedPointerEntry(derived_loc, offset);
    _list->append(entry);
  }
}


void DerivedPointerTable::update_pointers() {
  assert(_list != NULL, "list must exist");
  for(int i = 0; i < _list->length(); i++) {
    DerivedPointerEntry* entry = _list->at(i);
    oop* derived_loc = entry->location();
    intptr_t offset  = entry->offset();
    // The derived oop was setup to point to location of base
    oop  base        = **(oop**)derived_loc;
    assert(Universe::heap()->is_in_or_null(base), "must be an oop");

    *derived_loc = (oop)(((address)base) + offset);
    assert(value_of_loc(derived_loc) - value_of_loc(&base) == offset, "sanity check");

    if (TraceDerivedPointers) {
      tty->print_cr("Updating derived pointer@" INTPTR_FORMAT
                    " - Derived: " INTPTR_FORMAT "  Base: " INTPTR_FORMAT " (Offset: %d)",
          derived_loc, (address)*derived_loc, (address)base, offset);
    }

    // Delete entry
    delete entry;
    _list->at_put(i, NULL);
  }
  // Clear list, so it is ready for next traversal (this is an invariant)
  if (TraceDerivedPointers && !_list->is_empty()) {
    tty->print_cr("--------------------------");
  }
  _list->clear();
  _active = false;
}

#endif // COMPILER2
