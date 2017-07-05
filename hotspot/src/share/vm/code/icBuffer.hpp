/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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

//
// For CompiledIC's:
//
// In cases where we do not have MT-safe state transformation,
// we go to a transition state, using ICStubs. At a safepoint,
// the inline caches are transferred from the transitional code:
//
//    instruction_address --> 01 set xxx_oop, Ginline_cache_klass
//                            23 jump_to Gtemp, yyyy
//                            4  nop

class ICStub: public Stub {
 private:
  int                 _size;       // total size of the stub incl. code
  address             _ic_site;    // points at call instruction of owning ic-buffer
  /* stub code follows here */
 protected:
  friend class ICStubInterface;
  // This will be called only by ICStubInterface
  void    initialize(int size) { _size = size; _ic_site = NULL; }
  void    finalize(); // called when a method is removed

  // General info
  int     size() const                           { return _size; }
  static  int code_size_to_size(int code_size)   { return round_to(sizeof(ICStub), CodeEntryAlignment) + code_size; }

 public:
  // Creation
  void set_stub(CompiledIC *ic, oop cached_value, address dest_addr);

  // Code info
  address code_begin() const                     { return (address)this + round_to(sizeof(ICStub), CodeEntryAlignment); }
  address code_end() const                       { return (address)this + size(); }

  // Call site info
  address ic_site() const                        { return _ic_site; }
  void    clear();
  bool    is_empty() const                       { return _ic_site == NULL; }

  // stub info
  address destination() const;  // destination of jump instruction
  oop     cached_oop() const;   // cached_oop for stub

  // Debugging
  void    verify()            PRODUCT_RETURN;
  void    print()             PRODUCT_RETURN;

  // Creation
  friend ICStub* ICStub_from_destination_address(address destination_address);
};

// ICStub Creation
inline ICStub* ICStub_from_destination_address(address destination_address) {
  ICStub* stub = (ICStub*) (destination_address - round_to(sizeof(ICStub), CodeEntryAlignment));
  #ifdef ASSERT
  stub->verify();
  #endif
  return stub;
}

class InlineCacheBuffer: public AllStatic {
 private:
  // friends
  friend class ICStub;

  static int ic_stub_code_size();

  static StubQueue* _buffer;
  static ICStub*    _next_stub;

  static StubQueue* buffer()                         { return _buffer;         }
  static void       set_next_stub(ICStub* next_stub) { _next_stub = next_stub; }
  static ICStub*    get_next_stub()                  { return _next_stub;      }

  static void       init_next_stub();

  static ICStub* new_ic_stub();


  // Machine-dependent implementation of ICBuffer
  static void    assemble_ic_buffer_code(address code_begin, oop cached_oop, address entry_point);
  static address ic_buffer_entry_point  (address code_begin);
  static oop     ic_buffer_cached_oop   (address code_begin);

 public:

    // Initialization; must be called before first usage
  static void initialize();

  // Access
  static bool contains(address instruction_address);

    // removes the ICStubs after backpatching
  static void update_inline_caches();

  // for debugging
  static bool is_empty();


  // New interface
  static void    create_transition_stub(CompiledIC *ic, oop cached_oop, address entry);
  static address ic_destination_for(CompiledIC *ic);
  static oop     cached_oop_for(CompiledIC *ic);
};
