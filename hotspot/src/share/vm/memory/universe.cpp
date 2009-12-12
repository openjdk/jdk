/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# include "incls/_universe.cpp.incl"

// Known objects
klassOop Universe::_boolArrayKlassObj                 = NULL;
klassOop Universe::_byteArrayKlassObj                 = NULL;
klassOop Universe::_charArrayKlassObj                 = NULL;
klassOop Universe::_intArrayKlassObj                  = NULL;
klassOop Universe::_shortArrayKlassObj                = NULL;
klassOop Universe::_longArrayKlassObj                 = NULL;
klassOop Universe::_singleArrayKlassObj               = NULL;
klassOop Universe::_doubleArrayKlassObj               = NULL;
klassOop Universe::_typeArrayKlassObjs[T_VOID+1]      = { NULL /*, NULL...*/ };
klassOop Universe::_objectArrayKlassObj               = NULL;
klassOop Universe::_symbolKlassObj                    = NULL;
klassOop Universe::_methodKlassObj                    = NULL;
klassOop Universe::_constMethodKlassObj               = NULL;
klassOop Universe::_methodDataKlassObj                = NULL;
klassOop Universe::_klassKlassObj                     = NULL;
klassOop Universe::_arrayKlassKlassObj                = NULL;
klassOop Universe::_objArrayKlassKlassObj             = NULL;
klassOop Universe::_typeArrayKlassKlassObj            = NULL;
klassOop Universe::_instanceKlassKlassObj             = NULL;
klassOop Universe::_constantPoolKlassObj              = NULL;
klassOop Universe::_constantPoolCacheKlassObj         = NULL;
klassOop Universe::_compiledICHolderKlassObj          = NULL;
klassOop Universe::_systemObjArrayKlassObj            = NULL;
oop Universe::_int_mirror                             = NULL;
oop Universe::_float_mirror                           = NULL;
oop Universe::_double_mirror                          = NULL;
oop Universe::_byte_mirror                            = NULL;
oop Universe::_bool_mirror                            = NULL;
oop Universe::_char_mirror                            = NULL;
oop Universe::_long_mirror                            = NULL;
oop Universe::_short_mirror                           = NULL;
oop Universe::_void_mirror                            = NULL;
oop Universe::_mirrors[T_VOID+1]                      = { NULL /*, NULL...*/ };
oop Universe::_main_thread_group                      = NULL;
oop Universe::_system_thread_group                    = NULL;
typeArrayOop Universe::_the_empty_byte_array          = NULL;
typeArrayOop Universe::_the_empty_short_array         = NULL;
typeArrayOop Universe::_the_empty_int_array           = NULL;
objArrayOop Universe::_the_empty_system_obj_array     = NULL;
objArrayOop Universe::_the_empty_class_klass_array    = NULL;
objArrayOop Universe::_the_array_interfaces_array     = NULL;
oop Universe::_the_null_string                        = NULL;
oop Universe::_the_min_jint_string                   = NULL;
LatestMethodOopCache* Universe::_finalizer_register_cache = NULL;
LatestMethodOopCache* Universe::_loader_addClass_cache    = NULL;
ActiveMethodOopsCache* Universe::_reflect_invoke_cache    = NULL;
oop Universe::_out_of_memory_error_java_heap          = NULL;
oop Universe::_out_of_memory_error_perm_gen           = NULL;
oop Universe::_out_of_memory_error_array_size         = NULL;
oop Universe::_out_of_memory_error_gc_overhead_limit  = NULL;
objArrayOop Universe::_preallocated_out_of_memory_error_array = NULL;
volatile jint Universe::_preallocated_out_of_memory_error_avail_count = 0;
bool Universe::_verify_in_progress                    = false;
oop Universe::_null_ptr_exception_instance            = NULL;
oop Universe::_arithmetic_exception_instance          = NULL;
oop Universe::_virtual_machine_error_instance         = NULL;
oop Universe::_vm_exception                           = NULL;
oop Universe::_emptySymbol                            = NULL;

// These variables are guarded by FullGCALot_lock.
debug_only(objArrayOop Universe::_fullgc_alot_dummy_array = NULL;)
debug_only(int Universe::_fullgc_alot_dummy_next      = 0;)


// Heap
int             Universe::_verify_count = 0;

int             Universe::_base_vtable_size = 0;
bool            Universe::_bootstrapping = false;
bool            Universe::_fully_initialized = false;

size_t          Universe::_heap_capacity_at_last_gc;
size_t          Universe::_heap_used_at_last_gc = 0;

CollectedHeap*  Universe::_collectedHeap = NULL;

NarrowOopStruct Universe::_narrow_oop = { NULL, 0, true };


void Universe::basic_type_classes_do(void f(klassOop)) {
  f(boolArrayKlassObj());
  f(byteArrayKlassObj());
  f(charArrayKlassObj());
  f(intArrayKlassObj());
  f(shortArrayKlassObj());
  f(longArrayKlassObj());
  f(singleArrayKlassObj());
  f(doubleArrayKlassObj());
}


void Universe::system_classes_do(void f(klassOop)) {
  f(symbolKlassObj());
  f(methodKlassObj());
  f(constMethodKlassObj());
  f(methodDataKlassObj());
  f(klassKlassObj());
  f(arrayKlassKlassObj());
  f(objArrayKlassKlassObj());
  f(typeArrayKlassKlassObj());
  f(instanceKlassKlassObj());
  f(constantPoolKlassObj());
  f(systemObjArrayKlassObj());
}

void Universe::oops_do(OopClosure* f, bool do_all) {

  f->do_oop((oop*) &_int_mirror);
  f->do_oop((oop*) &_float_mirror);
  f->do_oop((oop*) &_double_mirror);
  f->do_oop((oop*) &_byte_mirror);
  f->do_oop((oop*) &_bool_mirror);
  f->do_oop((oop*) &_char_mirror);
  f->do_oop((oop*) &_long_mirror);
  f->do_oop((oop*) &_short_mirror);
  f->do_oop((oop*) &_void_mirror);

  // It's important to iterate over these guys even if they are null,
  // since that's how shared heaps are restored.
  for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
    f->do_oop((oop*) &_mirrors[i]);
  }
  assert(_mirrors[0] == NULL && _mirrors[T_BOOLEAN - 1] == NULL, "checking");

  // %%% Consider moving those "shared oops" over here with the others.
  f->do_oop((oop*)&_boolArrayKlassObj);
  f->do_oop((oop*)&_byteArrayKlassObj);
  f->do_oop((oop*)&_charArrayKlassObj);
  f->do_oop((oop*)&_intArrayKlassObj);
  f->do_oop((oop*)&_shortArrayKlassObj);
  f->do_oop((oop*)&_longArrayKlassObj);
  f->do_oop((oop*)&_singleArrayKlassObj);
  f->do_oop((oop*)&_doubleArrayKlassObj);
  f->do_oop((oop*)&_objectArrayKlassObj);
  {
    for (int i = 0; i < T_VOID+1; i++) {
      if (_typeArrayKlassObjs[i] != NULL) {
        assert(i >= T_BOOLEAN, "checking");
        f->do_oop((oop*)&_typeArrayKlassObjs[i]);
      } else if (do_all) {
        f->do_oop((oop*)&_typeArrayKlassObjs[i]);
      }
    }
  }
  f->do_oop((oop*)&_symbolKlassObj);
  f->do_oop((oop*)&_methodKlassObj);
  f->do_oop((oop*)&_constMethodKlassObj);
  f->do_oop((oop*)&_methodDataKlassObj);
  f->do_oop((oop*)&_klassKlassObj);
  f->do_oop((oop*)&_arrayKlassKlassObj);
  f->do_oop((oop*)&_objArrayKlassKlassObj);
  f->do_oop((oop*)&_typeArrayKlassKlassObj);
  f->do_oop((oop*)&_instanceKlassKlassObj);
  f->do_oop((oop*)&_constantPoolKlassObj);
  f->do_oop((oop*)&_constantPoolCacheKlassObj);
  f->do_oop((oop*)&_compiledICHolderKlassObj);
  f->do_oop((oop*)&_systemObjArrayKlassObj);
  f->do_oop((oop*)&_the_empty_byte_array);
  f->do_oop((oop*)&_the_empty_short_array);
  f->do_oop((oop*)&_the_empty_int_array);
  f->do_oop((oop*)&_the_empty_system_obj_array);
  f->do_oop((oop*)&_the_empty_class_klass_array);
  f->do_oop((oop*)&_the_array_interfaces_array);
  f->do_oop((oop*)&_the_null_string);
  f->do_oop((oop*)&_the_min_jint_string);
  _finalizer_register_cache->oops_do(f);
  _loader_addClass_cache->oops_do(f);
  _reflect_invoke_cache->oops_do(f);
  f->do_oop((oop*)&_out_of_memory_error_java_heap);
  f->do_oop((oop*)&_out_of_memory_error_perm_gen);
  f->do_oop((oop*)&_out_of_memory_error_array_size);
  f->do_oop((oop*)&_out_of_memory_error_gc_overhead_limit);
  if (_preallocated_out_of_memory_error_array != (oop)NULL) {   // NULL when DumpSharedSpaces
    f->do_oop((oop*)&_preallocated_out_of_memory_error_array);
  }
  f->do_oop((oop*)&_null_ptr_exception_instance);
  f->do_oop((oop*)&_arithmetic_exception_instance);
  f->do_oop((oop*)&_virtual_machine_error_instance);
  f->do_oop((oop*)&_main_thread_group);
  f->do_oop((oop*)&_system_thread_group);
  f->do_oop((oop*)&_vm_exception);
  f->do_oop((oop*)&_emptySymbol);
  debug_only(f->do_oop((oop*)&_fullgc_alot_dummy_array);)
}


void Universe::check_alignment(uintx size, uintx alignment, const char* name) {
  if (size < alignment || size % alignment != 0) {
    ResourceMark rm;
    stringStream st;
    st.print("Size of %s (%ld bytes) must be aligned to %ld bytes", name, size, alignment);
    char* error = st.as_string();
    vm_exit_during_initialization(error);
  }
}


void Universe::genesis(TRAPS) {
  ResourceMark rm;
  { FlagSetting fs(_bootstrapping, true);

    { MutexLocker mc(Compile_lock);

      // determine base vtable size; without that we cannot create the array klasses
      compute_base_vtable_size();

      if (!UseSharedSpaces) {
        _klassKlassObj          = klassKlass::create_klass(CHECK);
        _arrayKlassKlassObj     = arrayKlassKlass::create_klass(CHECK);

        _objArrayKlassKlassObj  = objArrayKlassKlass::create_klass(CHECK);
        _instanceKlassKlassObj  = instanceKlassKlass::create_klass(CHECK);
        _typeArrayKlassKlassObj = typeArrayKlassKlass::create_klass(CHECK);

        _symbolKlassObj         = symbolKlass::create_klass(CHECK);

        _emptySymbol            = oopFactory::new_symbol("", CHECK);

        _boolArrayKlassObj      = typeArrayKlass::create_klass(T_BOOLEAN, sizeof(jboolean), CHECK);
        _charArrayKlassObj      = typeArrayKlass::create_klass(T_CHAR,    sizeof(jchar),    CHECK);
        _singleArrayKlassObj    = typeArrayKlass::create_klass(T_FLOAT,   sizeof(jfloat),   CHECK);
        _doubleArrayKlassObj    = typeArrayKlass::create_klass(T_DOUBLE,  sizeof(jdouble),  CHECK);
        _byteArrayKlassObj      = typeArrayKlass::create_klass(T_BYTE,    sizeof(jbyte),    CHECK);
        _shortArrayKlassObj     = typeArrayKlass::create_klass(T_SHORT,   sizeof(jshort),   CHECK);
        _intArrayKlassObj       = typeArrayKlass::create_klass(T_INT,     sizeof(jint),     CHECK);
        _longArrayKlassObj      = typeArrayKlass::create_klass(T_LONG,    sizeof(jlong),    CHECK);

        _typeArrayKlassObjs[T_BOOLEAN] = _boolArrayKlassObj;
        _typeArrayKlassObjs[T_CHAR]    = _charArrayKlassObj;
        _typeArrayKlassObjs[T_FLOAT]   = _singleArrayKlassObj;
        _typeArrayKlassObjs[T_DOUBLE]  = _doubleArrayKlassObj;
        _typeArrayKlassObjs[T_BYTE]    = _byteArrayKlassObj;
        _typeArrayKlassObjs[T_SHORT]   = _shortArrayKlassObj;
        _typeArrayKlassObjs[T_INT]     = _intArrayKlassObj;
        _typeArrayKlassObjs[T_LONG]    = _longArrayKlassObj;

        _methodKlassObj             = methodKlass::create_klass(CHECK);
        _constMethodKlassObj        = constMethodKlass::create_klass(CHECK);
        _methodDataKlassObj         = methodDataKlass::create_klass(CHECK);
        _constantPoolKlassObj       = constantPoolKlass::create_klass(CHECK);
        _constantPoolCacheKlassObj  = constantPoolCacheKlass::create_klass(CHECK);

        _compiledICHolderKlassObj   = compiledICHolderKlass::create_klass(CHECK);
        _systemObjArrayKlassObj     = objArrayKlassKlass::cast(objArrayKlassKlassObj())->allocate_system_objArray_klass(CHECK);

        _the_empty_byte_array       = oopFactory::new_permanent_byteArray(0, CHECK);
        _the_empty_short_array      = oopFactory::new_permanent_shortArray(0, CHECK);
        _the_empty_int_array        = oopFactory::new_permanent_intArray(0, CHECK);
        _the_empty_system_obj_array = oopFactory::new_system_objArray(0, CHECK);

        _the_array_interfaces_array = oopFactory::new_system_objArray(2, CHECK);
        _vm_exception               = oopFactory::new_symbol("vm exception holder", CHECK);
      } else {
        FileMapInfo *mapinfo = FileMapInfo::current_info();
        char* buffer = mapinfo->region_base(CompactingPermGenGen::md);
        void** vtbl_list = (void**)buffer;
        init_self_patching_vtbl_list(vtbl_list,
                                     CompactingPermGenGen::vtbl_list_size);
      }
    }

    vmSymbols::initialize(CHECK);

    SystemDictionary::initialize(CHECK);

    klassOop ok = SystemDictionary::object_klass();

    _the_null_string            = StringTable::intern("null", CHECK);
    _the_min_jint_string       = StringTable::intern("-2147483648", CHECK);

    if (UseSharedSpaces) {
      // Verify shared interfaces array.
      assert(_the_array_interfaces_array->obj_at(0) ==
             SystemDictionary::cloneable_klass(), "u3");
      assert(_the_array_interfaces_array->obj_at(1) ==
             SystemDictionary::serializable_klass(), "u3");

      // Verify element klass for system obj array klass
      assert(objArrayKlass::cast(_systemObjArrayKlassObj)->element_klass() == ok, "u1");
      assert(objArrayKlass::cast(_systemObjArrayKlassObj)->bottom_klass() == ok, "u2");

      // Verify super class for the classes created above
      assert(Klass::cast(boolArrayKlassObj()     )->super() == ok, "u3");
      assert(Klass::cast(charArrayKlassObj()     )->super() == ok, "u3");
      assert(Klass::cast(singleArrayKlassObj()   )->super() == ok, "u3");
      assert(Klass::cast(doubleArrayKlassObj()   )->super() == ok, "u3");
      assert(Klass::cast(byteArrayKlassObj()     )->super() == ok, "u3");
      assert(Klass::cast(shortArrayKlassObj()    )->super() == ok, "u3");
      assert(Klass::cast(intArrayKlassObj()      )->super() == ok, "u3");
      assert(Klass::cast(longArrayKlassObj()     )->super() == ok, "u3");
      assert(Klass::cast(constantPoolKlassObj()  )->super() == ok, "u3");
      assert(Klass::cast(systemObjArrayKlassObj())->super() == ok, "u3");
    } else {
      // Set up shared interfaces array.  (Do this before supers are set up.)
      _the_array_interfaces_array->obj_at_put(0, SystemDictionary::cloneable_klass());
      _the_array_interfaces_array->obj_at_put(1, SystemDictionary::serializable_klass());

      // Set element klass for system obj array klass
      objArrayKlass::cast(_systemObjArrayKlassObj)->set_element_klass(ok);
      objArrayKlass::cast(_systemObjArrayKlassObj)->set_bottom_klass(ok);

      // Set super class for the classes created above
      Klass::cast(boolArrayKlassObj()     )->initialize_supers(ok, CHECK);
      Klass::cast(charArrayKlassObj()     )->initialize_supers(ok, CHECK);
      Klass::cast(singleArrayKlassObj()   )->initialize_supers(ok, CHECK);
      Klass::cast(doubleArrayKlassObj()   )->initialize_supers(ok, CHECK);
      Klass::cast(byteArrayKlassObj()     )->initialize_supers(ok, CHECK);
      Klass::cast(shortArrayKlassObj()    )->initialize_supers(ok, CHECK);
      Klass::cast(intArrayKlassObj()      )->initialize_supers(ok, CHECK);
      Klass::cast(longArrayKlassObj()     )->initialize_supers(ok, CHECK);
      Klass::cast(constantPoolKlassObj()  )->initialize_supers(ok, CHECK);
      Klass::cast(systemObjArrayKlassObj())->initialize_supers(ok, CHECK);
      Klass::cast(boolArrayKlassObj()     )->set_super(ok);
      Klass::cast(charArrayKlassObj()     )->set_super(ok);
      Klass::cast(singleArrayKlassObj()   )->set_super(ok);
      Klass::cast(doubleArrayKlassObj()   )->set_super(ok);
      Klass::cast(byteArrayKlassObj()     )->set_super(ok);
      Klass::cast(shortArrayKlassObj()    )->set_super(ok);
      Klass::cast(intArrayKlassObj()      )->set_super(ok);
      Klass::cast(longArrayKlassObj()     )->set_super(ok);
      Klass::cast(constantPoolKlassObj()  )->set_super(ok);
      Klass::cast(systemObjArrayKlassObj())->set_super(ok);
    }

    Klass::cast(boolArrayKlassObj()     )->append_to_sibling_list();
    Klass::cast(charArrayKlassObj()     )->append_to_sibling_list();
    Klass::cast(singleArrayKlassObj()   )->append_to_sibling_list();
    Klass::cast(doubleArrayKlassObj()   )->append_to_sibling_list();
    Klass::cast(byteArrayKlassObj()     )->append_to_sibling_list();
    Klass::cast(shortArrayKlassObj()    )->append_to_sibling_list();
    Klass::cast(intArrayKlassObj()      )->append_to_sibling_list();
    Klass::cast(longArrayKlassObj()     )->append_to_sibling_list();
    Klass::cast(constantPoolKlassObj()  )->append_to_sibling_list();
    Klass::cast(systemObjArrayKlassObj())->append_to_sibling_list();
  } // end of core bootstrapping

  // Initialize _objectArrayKlass after core bootstraping to make
  // sure the super class is set up properly for _objectArrayKlass.
  _objectArrayKlassObj = instanceKlass::
    cast(SystemDictionary::object_klass())->array_klass(1, CHECK);
  // Add the class to the class hierarchy manually to make sure that
  // its vtable is initialized after core bootstrapping is completed.
  Klass::cast(_objectArrayKlassObj)->append_to_sibling_list();

  // Compute is_jdk version flags.
  // Only 1.3 or later has the java.lang.Shutdown class.
  // Only 1.4 or later has the java.lang.CharSequence interface.
  // Only 1.5 or later has the java.lang.management.MemoryUsage class.
  if (JDK_Version::is_partially_initialized()) {
    uint8_t jdk_version;
    klassOop k = SystemDictionary::resolve_or_null(
        vmSymbolHandles::java_lang_management_MemoryUsage(), THREAD);
    CLEAR_PENDING_EXCEPTION; // ignore exceptions
    if (k == NULL) {
      k = SystemDictionary::resolve_or_null(
          vmSymbolHandles::java_lang_CharSequence(), THREAD);
      CLEAR_PENDING_EXCEPTION; // ignore exceptions
      if (k == NULL) {
        k = SystemDictionary::resolve_or_null(
            vmSymbolHandles::java_lang_Shutdown(), THREAD);
        CLEAR_PENDING_EXCEPTION; // ignore exceptions
        if (k == NULL) {
          jdk_version = 2;
        } else {
          jdk_version = 3;
        }
      } else {
        jdk_version = 4;
      }
    } else {
      jdk_version = 5;
    }
    JDK_Version::fully_initialize(jdk_version);
  }

  #ifdef ASSERT
  if (FullGCALot) {
    // Allocate an array of dummy objects.
    // We'd like these to be at the bottom of the old generation,
    // so that when we free one and then collect,
    // (almost) the whole heap moves
    // and we find out if we actually update all the oops correctly.
    // But we can't allocate directly in the old generation,
    // so we allocate wherever, and hope that the first collection
    // moves these objects to the bottom of the old generation.
    // We can allocate directly in the permanent generation, so we do.
    int size;
    if (UseConcMarkSweepGC) {
      warning("Using +FullGCALot with concurrent mark sweep gc "
              "will not force all objects to relocate");
      size = FullGCALotDummies;
    } else {
      size = FullGCALotDummies * 2;
    }
    objArrayOop    naked_array = oopFactory::new_system_objArray(size, CHECK);
    objArrayHandle dummy_array(THREAD, naked_array);
    int i = 0;
    while (i < size) {
      if (!UseConcMarkSweepGC) {
        // Allocate dummy in old generation
        oop dummy = instanceKlass::cast(SystemDictionary::object_klass())->allocate_instance(CHECK);
        dummy_array->obj_at_put(i++, dummy);
      }
      // Allocate dummy in permanent generation
      oop dummy = instanceKlass::cast(SystemDictionary::object_klass())->allocate_permanent_instance(CHECK);
      dummy_array->obj_at_put(i++, dummy);
    }
    {
      // Only modify the global variable inside the mutex.
      // If we had a race to here, the other dummy_array instances
      // and their elements just get dropped on the floor, which is fine.
      MutexLocker ml(FullGCALot_lock);
      if (_fullgc_alot_dummy_array == NULL) {
        _fullgc_alot_dummy_array = dummy_array();
      }
    }
    assert(i == _fullgc_alot_dummy_array->length(), "just checking");
  }
  #endif
}


static inline void add_vtable(void** list, int* n, Klass* o, int count) {
  list[(*n)++] = *(void**)&o->vtbl_value();
  guarantee((*n) <= count, "vtable list too small.");
}


void Universe::init_self_patching_vtbl_list(void** list, int count) {
  int n = 0;
  { klassKlass o;             add_vtable(list, &n, &o, count); }
  { arrayKlassKlass o;        add_vtable(list, &n, &o, count); }
  { objArrayKlassKlass o;     add_vtable(list, &n, &o, count); }
  { instanceKlassKlass o;     add_vtable(list, &n, &o, count); }
  { instanceKlass o;          add_vtable(list, &n, &o, count); }
  { instanceRefKlass o;       add_vtable(list, &n, &o, count); }
  { typeArrayKlassKlass o;    add_vtable(list, &n, &o, count); }
  { symbolKlass o;            add_vtable(list, &n, &o, count); }
  { typeArrayKlass o;         add_vtable(list, &n, &o, count); }
  { methodKlass o;            add_vtable(list, &n, &o, count); }
  { constMethodKlass o;       add_vtable(list, &n, &o, count); }
  { constantPoolKlass o;      add_vtable(list, &n, &o, count); }
  { constantPoolCacheKlass o; add_vtable(list, &n, &o, count); }
  { objArrayKlass o;          add_vtable(list, &n, &o, count); }
  { methodDataKlass o;        add_vtable(list, &n, &o, count); }
  { compiledICHolderKlass o;  add_vtable(list, &n, &o, count); }
}


class FixupMirrorClosure: public ObjectClosure {
 public:
  virtual void do_object(oop obj) {
    if (obj->is_klass()) {
      EXCEPTION_MARK;
      KlassHandle k(THREAD, klassOop(obj));
      // We will never reach the CATCH below since Exceptions::_throw will cause
      // the VM to exit if an exception is thrown during initialization
      java_lang_Class::create_mirror(k, CATCH);
      // This call unconditionally creates a new mirror for k,
      // and links in k's component_mirror field if k is an array.
      // If k is an objArray, k's element type must already have
      // a mirror.  In other words, this closure must process
      // the component type of an objArray k before it processes k.
      // This works because the permgen iterator presents arrays
      // and their component types in order of creation.
    }
  }
};

void Universe::initialize_basic_type_mirrors(TRAPS) {
  if (UseSharedSpaces) {
    assert(_int_mirror != NULL, "already loaded");
    assert(_void_mirror == _mirrors[T_VOID], "consistently loaded");
  } else {

    assert(_int_mirror==NULL, "basic type mirrors already initialized");
    _int_mirror     =
      java_lang_Class::create_basic_type_mirror("int",    T_INT, CHECK);
    _float_mirror   =
      java_lang_Class::create_basic_type_mirror("float",  T_FLOAT,   CHECK);
    _double_mirror  =
      java_lang_Class::create_basic_type_mirror("double", T_DOUBLE,  CHECK);
    _byte_mirror    =
      java_lang_Class::create_basic_type_mirror("byte",   T_BYTE, CHECK);
    _bool_mirror    =
      java_lang_Class::create_basic_type_mirror("boolean",T_BOOLEAN, CHECK);
    _char_mirror    =
      java_lang_Class::create_basic_type_mirror("char",   T_CHAR, CHECK);
    _long_mirror    =
      java_lang_Class::create_basic_type_mirror("long",   T_LONG, CHECK);
    _short_mirror   =
      java_lang_Class::create_basic_type_mirror("short",  T_SHORT,   CHECK);
    _void_mirror    =
      java_lang_Class::create_basic_type_mirror("void",   T_VOID, CHECK);

    _mirrors[T_INT]     = _int_mirror;
    _mirrors[T_FLOAT]   = _float_mirror;
    _mirrors[T_DOUBLE]  = _double_mirror;
    _mirrors[T_BYTE]    = _byte_mirror;
    _mirrors[T_BOOLEAN] = _bool_mirror;
    _mirrors[T_CHAR]    = _char_mirror;
    _mirrors[T_LONG]    = _long_mirror;
    _mirrors[T_SHORT]   = _short_mirror;
    _mirrors[T_VOID]    = _void_mirror;
    //_mirrors[T_OBJECT]  = instanceKlass::cast(_object_klass)->java_mirror();
    //_mirrors[T_ARRAY]   = instanceKlass::cast(_object_klass)->java_mirror();
  }
}

void Universe::fixup_mirrors(TRAPS) {
  // Bootstrap problem: all classes gets a mirror (java.lang.Class instance) assigned eagerly,
  // but we cannot do that for classes created before java.lang.Class is loaded. Here we simply
  // walk over permanent objects created so far (mostly classes) and fixup their mirrors. Note
  // that the number of objects allocated at this point is very small.
  assert(SystemDictionary::class_klass_loaded(), "java.lang.Class should be loaded");
  FixupMirrorClosure blk;
  Universe::heap()->permanent_object_iterate(&blk);
}


static bool has_run_finalizers_on_exit = false;

void Universe::run_finalizers_on_exit() {
  if (has_run_finalizers_on_exit) return;
  has_run_finalizers_on_exit = true;

  // Called on VM exit. This ought to be run in a separate thread.
  if (TraceReferenceGC) tty->print_cr("Callback to run finalizers on exit");
  {
    PRESERVE_EXCEPTION_MARK;
    KlassHandle finalizer_klass(THREAD, SystemDictionary::finalizer_klass());
    JavaValue result(T_VOID);
    JavaCalls::call_static(
      &result,
      finalizer_klass,
      vmSymbolHandles::run_finalizers_on_exit_name(),
      vmSymbolHandles::void_method_signature(),
      THREAD
    );
    // Ignore any pending exceptions
    CLEAR_PENDING_EXCEPTION;
  }
}


// initialize_vtable could cause gc if
// 1) we specified true to initialize_vtable and
// 2) this ran after gc was enabled
// In case those ever change we use handles for oops
void Universe::reinitialize_vtable_of(KlassHandle k_h, TRAPS) {
  // init vtable of k and all subclasses
  Klass* ko = k_h()->klass_part();
  klassVtable* vt = ko->vtable();
  if (vt) vt->initialize_vtable(false, CHECK);
  if (ko->oop_is_instance()) {
    instanceKlass* ik = (instanceKlass*)ko;
    for (KlassHandle s_h(THREAD, ik->subklass()); s_h() != NULL; s_h = (THREAD, s_h()->klass_part()->next_sibling())) {
      reinitialize_vtable_of(s_h, CHECK);
    }
  }
}


void initialize_itable_for_klass(klassOop k, TRAPS) {
  instanceKlass::cast(k)->itable()->initialize_itable(false, CHECK);
}


void Universe::reinitialize_itables(TRAPS) {
  SystemDictionary::classes_do(initialize_itable_for_klass, CHECK);

}


bool Universe::on_page_boundary(void* addr) {
  return ((uintptr_t) addr) % os::vm_page_size() == 0;
}


bool Universe::should_fill_in_stack_trace(Handle throwable) {
  // never attempt to fill in the stack trace of preallocated errors that do not have
  // backtrace. These errors are kept alive forever and may be "re-used" when all
  // preallocated errors with backtrace have been consumed. Also need to avoid
  // a potential loop which could happen if an out of memory occurs when attempting
  // to allocate the backtrace.
  return ((throwable() != Universe::_out_of_memory_error_java_heap) &&
          (throwable() != Universe::_out_of_memory_error_perm_gen)  &&
          (throwable() != Universe::_out_of_memory_error_array_size) &&
          (throwable() != Universe::_out_of_memory_error_gc_overhead_limit));
}


oop Universe::gen_out_of_memory_error(oop default_err) {
  // generate an out of memory error:
  // - if there is a preallocated error with backtrace available then return it wth
  //   a filled in stack trace.
  // - if there are no preallocated errors with backtrace available then return
  //   an error without backtrace.
  int next;
  if (_preallocated_out_of_memory_error_avail_count > 0) {
    next = (int)Atomic::add(-1, &_preallocated_out_of_memory_error_avail_count);
    assert(next < (int)PreallocatedOutOfMemoryErrorCount, "avail count is corrupt");
  } else {
    next = -1;
  }
  if (next < 0) {
    // all preallocated errors have been used.
    // return default
    return default_err;
  } else {
    // get the error object at the slot and set set it to NULL so that the
    // array isn't keeping it alive anymore.
    oop exc = preallocated_out_of_memory_errors()->obj_at(next);
    assert(exc != NULL, "slot has been used already");
    preallocated_out_of_memory_errors()->obj_at_put(next, NULL);

    // use the message from the default error
    oop msg = java_lang_Throwable::message(default_err);
    assert(msg != NULL, "no message");
    java_lang_Throwable::set_message(exc, msg);

    // populate the stack trace and return it.
    java_lang_Throwable::fill_in_stack_trace_of_preallocated_backtrace(exc);
    return exc;
  }
}

static intptr_t non_oop_bits = 0;

void* Universe::non_oop_word() {
  // Neither the high bits nor the low bits of this value is allowed
  // to look like (respectively) the high or low bits of a real oop.
  //
  // High and low are CPU-specific notions, but low always includes
  // the low-order bit.  Since oops are always aligned at least mod 4,
  // setting the low-order bit will ensure that the low half of the
  // word will never look like that of a real oop.
  //
  // Using the OS-supplied non-memory-address word (usually 0 or -1)
  // will take care of the high bits, however many there are.

  if (non_oop_bits == 0) {
    non_oop_bits = (intptr_t)os::non_memory_address_word() | 1;
  }

  return (void*)non_oop_bits;
}

jint universe_init() {
  assert(!Universe::_fully_initialized, "called after initialize_vtables");
  guarantee(1 << LogHeapWordSize == sizeof(HeapWord),
         "LogHeapWordSize is incorrect.");
  guarantee(sizeof(oop) >= sizeof(HeapWord), "HeapWord larger than oop?");
  guarantee(sizeof(oop) % sizeof(HeapWord) == 0,
            "oop size is not not a multiple of HeapWord size");
  TraceTime timer("Genesis", TraceStartupTime);
  GC_locker::lock();  // do not allow gc during bootstrapping
  JavaClasses::compute_hard_coded_offsets();

  // Get map info from shared archive file.
  if (DumpSharedSpaces)
    UseSharedSpaces = false;

  FileMapInfo* mapinfo = NULL;
  if (UseSharedSpaces) {
    mapinfo = NEW_C_HEAP_OBJ(FileMapInfo);
    memset(mapinfo, 0, sizeof(FileMapInfo));

    // Open the shared archive file, read and validate the header. If
    // initialization files, shared spaces [UseSharedSpaces] are
    // disabled and the file is closed.

    if (mapinfo->initialize()) {
      FileMapInfo::set_current_info(mapinfo);
    } else {
      assert(!mapinfo->is_open() && !UseSharedSpaces,
             "archive file not closed or shared spaces not disabled.");
    }
  }

  jint status = Universe::initialize_heap();
  if (status != JNI_OK) {
    return status;
  }

  // We have a heap so create the methodOop caches before
  // CompactingPermGenGen::initialize_oops() tries to populate them.
  Universe::_finalizer_register_cache = new LatestMethodOopCache();
  Universe::_loader_addClass_cache    = new LatestMethodOopCache();
  Universe::_reflect_invoke_cache     = new ActiveMethodOopsCache();

  if (UseSharedSpaces) {

    // Read the data structures supporting the shared spaces (shared
    // system dictionary, symbol table, etc.).  After that, access to
    // the file (other than the mapped regions) is no longer needed, and
    // the file is closed. Closing the file does not affect the
    // currently mapped regions.

    CompactingPermGenGen::initialize_oops();
    mapinfo->close();

  } else {
    SymbolTable::create_table();
    StringTable::create_table();
    ClassLoader::create_package_info_table();
  }

  return JNI_OK;
}

// Choose the heap base address and oop encoding mode
// when compressed oops are used:
// Unscaled  - Use 32-bits oops without encoding when
//     NarrowOopHeapBaseMin + heap_size < 4Gb
// ZeroBased - Use zero based compressed oops with encoding when
//     NarrowOopHeapBaseMin + heap_size < 32Gb
// HeapBased - Use compressed oops with heap base + encoding.

// 4Gb
static const uint64_t NarrowOopHeapMax = (uint64_t(max_juint) + 1);
// 32Gb
static const uint64_t OopEncodingHeapMax = NarrowOopHeapMax << LogMinObjAlignmentInBytes;

char* Universe::preferred_heap_base(size_t heap_size, NARROW_OOP_MODE mode) {
  size_t base = 0;
#ifdef _LP64
  if (UseCompressedOops) {
    assert(mode == UnscaledNarrowOop  ||
           mode == ZeroBasedNarrowOop ||
           mode == HeapBasedNarrowOop, "mode is invalid");
    const size_t total_size = heap_size + HeapBaseMinAddress;
    // Return specified base for the first request.
    if (!FLAG_IS_DEFAULT(HeapBaseMinAddress) && (mode == UnscaledNarrowOop)) {
      base = HeapBaseMinAddress;
    } else if (total_size <= OopEncodingHeapMax && (mode != HeapBasedNarrowOop)) {
      if (total_size <= NarrowOopHeapMax && (mode == UnscaledNarrowOop) &&
          (Universe::narrow_oop_shift() == 0)) {
        // Use 32-bits oops without encoding and
        // place heap's top on the 4Gb boundary
        base = (NarrowOopHeapMax - heap_size);
      } else {
        // Can't reserve with NarrowOopShift == 0
        Universe::set_narrow_oop_shift(LogMinObjAlignmentInBytes);
        if (mode == UnscaledNarrowOop ||
            mode == ZeroBasedNarrowOop && total_size <= NarrowOopHeapMax) {
          // Use zero based compressed oops with encoding and
          // place heap's top on the 32Gb boundary in case
          // total_size > 4Gb or failed to reserve below 4Gb.
          base = (OopEncodingHeapMax - heap_size);
        }
      }
    } else {
      // Can't reserve below 32Gb.
      Universe::set_narrow_oop_shift(LogMinObjAlignmentInBytes);
    }
    // Set narrow_oop_base and narrow_oop_use_implicit_null_checks
    // used in ReservedHeapSpace() constructors.
    // The final values will be set in initialize_heap() below.
    if (base != 0 && (base + heap_size) <= OopEncodingHeapMax) {
      // Use zero based compressed oops
      Universe::set_narrow_oop_base(NULL);
      // Don't need guard page for implicit checks in indexed
      // addressing mode with zero based Compressed Oops.
      Universe::set_narrow_oop_use_implicit_null_checks(true);
    } else {
      // Set to a non-NULL value so the ReservedSpace ctor computes
      // the correct no-access prefix.
      // The final value will be set in initialize_heap() below.
      Universe::set_narrow_oop_base((address)NarrowOopHeapMax);
#ifdef _WIN64
      if (UseLargePages) {
        // Cannot allocate guard pages for implicit checks in indexed
        // addressing mode when large pages are specified on windows.
        Universe::set_narrow_oop_use_implicit_null_checks(false);
      }
#endif //  _WIN64
    }
  }
#endif
  return (char*)base; // also return NULL (don't care) for 32-bit VM
}

jint Universe::initialize_heap() {

  if (UseParallelGC) {
#ifndef SERIALGC
    Universe::_collectedHeap = new ParallelScavengeHeap();
#else  // SERIALGC
    fatal("UseParallelGC not supported in java kernel vm.");
#endif // SERIALGC

  } else if (UseG1GC) {
#ifndef SERIALGC
    G1CollectorPolicy* g1p = new G1CollectorPolicy_BestRegionsFirst();
    G1CollectedHeap* g1h = new G1CollectedHeap(g1p);
    Universe::_collectedHeap = g1h;
#else  // SERIALGC
    fatal("UseG1GC not supported in java kernel vm.");
#endif // SERIALGC

  } else {
    GenCollectorPolicy *gc_policy;

    if (UseSerialGC) {
      gc_policy = new MarkSweepPolicy();
    } else if (UseConcMarkSweepGC) {
#ifndef SERIALGC
      if (UseAdaptiveSizePolicy) {
        gc_policy = new ASConcurrentMarkSweepPolicy();
      } else {
        gc_policy = new ConcurrentMarkSweepPolicy();
      }
#else   // SERIALGC
    fatal("UseConcMarkSweepGC not supported in java kernel vm.");
#endif // SERIALGC
    } else { // default old generation
      gc_policy = new MarkSweepPolicy();
    }

    Universe::_collectedHeap = new GenCollectedHeap(gc_policy);
  }

  jint status = Universe::heap()->initialize();
  if (status != JNI_OK) {
    return status;
  }

#ifdef _LP64
  if (UseCompressedOops) {
    // Subtract a page because something can get allocated at heap base.
    // This also makes implicit null checking work, because the
    // memory+1 page below heap_base needs to cause a signal.
    // See needs_explicit_null_check.
    // Only set the heap base for compressed oops because it indicates
    // compressed oops for pstack code.
    if (PrintCompressedOopsMode) {
      tty->cr();
      tty->print("heap address: "PTR_FORMAT, Universe::heap()->base());
    }
    if ((uint64_t)Universe::heap()->reserved_region().end() > OopEncodingHeapMax) {
      // Can't reserve heap below 32Gb.
      Universe::set_narrow_oop_base(Universe::heap()->base() - os::vm_page_size());
      Universe::set_narrow_oop_shift(LogMinObjAlignmentInBytes);
      if (PrintCompressedOopsMode) {
        tty->print(", Compressed Oops with base: "PTR_FORMAT, Universe::narrow_oop_base());
      }
    } else {
      Universe::set_narrow_oop_base(0);
      if (PrintCompressedOopsMode) {
        tty->print(", zero based Compressed Oops");
      }
#ifdef _WIN64
      if (!Universe::narrow_oop_use_implicit_null_checks()) {
        // Don't need guard page for implicit checks in indexed addressing
        // mode with zero based Compressed Oops.
        Universe::set_narrow_oop_use_implicit_null_checks(true);
      }
#endif //  _WIN64
      if((uint64_t)Universe::heap()->reserved_region().end() > NarrowOopHeapMax) {
        // Can't reserve heap below 4Gb.
        Universe::set_narrow_oop_shift(LogMinObjAlignmentInBytes);
      } else {
        Universe::set_narrow_oop_shift(0);
        if (PrintCompressedOopsMode) {
          tty->print(", 32-bits Oops");
        }
      }
    }
    if (PrintCompressedOopsMode) {
      tty->cr();
      tty->cr();
    }
  }
  assert(Universe::narrow_oop_base() == (Universe::heap()->base() - os::vm_page_size()) ||
         Universe::narrow_oop_base() == NULL, "invalid value");
  assert(Universe::narrow_oop_shift() == LogMinObjAlignmentInBytes ||
         Universe::narrow_oop_shift() == 0, "invalid value");
#endif

  // We will never reach the CATCH below since Exceptions::_throw will cause
  // the VM to exit if an exception is thrown during initialization

  if (UseTLAB) {
    assert(Universe::heap()->supports_tlab_allocation(),
           "Should support thread-local allocation buffers");
    ThreadLocalAllocBuffer::startup_initialization();
  }
  return JNI_OK;
}

// It's the caller's repsonsibility to ensure glitch-freedom
// (if required).
void Universe::update_heap_info_at_gc() {
  _heap_capacity_at_last_gc = heap()->capacity();
  _heap_used_at_last_gc     = heap()->used();
}



void universe2_init() {
  EXCEPTION_MARK;
  Universe::genesis(CATCH);
  // Although we'd like to verify here that the state of the heap
  // is good, we can't because the main thread has not yet added
  // itself to the threads list (so, using current interfaces
  // we can't "fill" its TLAB), unless TLABs are disabled.
  if (VerifyBeforeGC && !UseTLAB &&
      Universe::heap()->total_collections() >= VerifyGCStartAt) {
     Universe::heap()->prepare_for_verify();
     Universe::verify();   // make sure we're starting with a clean slate
  }
}


// This function is defined in JVM.cpp
extern void initialize_converter_functions();

bool universe_post_init() {
  Universe::_fully_initialized = true;
  EXCEPTION_MARK;
  { ResourceMark rm;
    Interpreter::initialize();      // needed for interpreter entry points
    if (!UseSharedSpaces) {
      KlassHandle ok_h(THREAD, SystemDictionary::object_klass());
      Universe::reinitialize_vtable_of(ok_h, CHECK_false);
      Universe::reinitialize_itables(CHECK_false);
    }
  }

  klassOop k;
  instanceKlassHandle k_h;
  if (!UseSharedSpaces) {
    // Setup preallocated empty java.lang.Class array
    Universe::_the_empty_class_klass_array = oopFactory::new_objArray(SystemDictionary::class_klass(), 0, CHECK_false);
    // Setup preallocated OutOfMemoryError errors
    k = SystemDictionary::resolve_or_fail(vmSymbolHandles::java_lang_OutOfMemoryError(), true, CHECK_false);
    k_h = instanceKlassHandle(THREAD, k);
    Universe::_out_of_memory_error_java_heap = k_h->allocate_permanent_instance(CHECK_false);
    Universe::_out_of_memory_error_perm_gen = k_h->allocate_permanent_instance(CHECK_false);
    Universe::_out_of_memory_error_array_size = k_h->allocate_permanent_instance(CHECK_false);
    Universe::_out_of_memory_error_gc_overhead_limit =
      k_h->allocate_permanent_instance(CHECK_false);

    // Setup preallocated NullPointerException
    // (this is currently used for a cheap & dirty solution in compiler exception handling)
    k = SystemDictionary::resolve_or_fail(vmSymbolHandles::java_lang_NullPointerException(), true, CHECK_false);
    Universe::_null_ptr_exception_instance = instanceKlass::cast(k)->allocate_permanent_instance(CHECK_false);
    // Setup preallocated ArithmeticException
    // (this is currently used for a cheap & dirty solution in compiler exception handling)
    k = SystemDictionary::resolve_or_fail(vmSymbolHandles::java_lang_ArithmeticException(), true, CHECK_false);
    Universe::_arithmetic_exception_instance = instanceKlass::cast(k)->allocate_permanent_instance(CHECK_false);
    // Virtual Machine Error for when we get into a situation we can't resolve
    k = SystemDictionary::resolve_or_fail(
      vmSymbolHandles::java_lang_VirtualMachineError(), true, CHECK_false);
    bool linked = instanceKlass::cast(k)->link_class_or_fail(CHECK_false);
    if (!linked) {
      tty->print_cr("Unable to link/verify VirtualMachineError class");
      return false; // initialization failed
    }
    Universe::_virtual_machine_error_instance =
      instanceKlass::cast(k)->allocate_permanent_instance(CHECK_false);
  }
  if (!DumpSharedSpaces) {
    // These are the only Java fields that are currently set during shared space dumping.
    // We prefer to not handle this generally, so we always reinitialize these detail messages.
    Handle msg = java_lang_String::create_from_str("Java heap space", CHECK_false);
    java_lang_Throwable::set_message(Universe::_out_of_memory_error_java_heap, msg());

    msg = java_lang_String::create_from_str("PermGen space", CHECK_false);
    java_lang_Throwable::set_message(Universe::_out_of_memory_error_perm_gen, msg());

    msg = java_lang_String::create_from_str("Requested array size exceeds VM limit", CHECK_false);
    java_lang_Throwable::set_message(Universe::_out_of_memory_error_array_size, msg());

    msg = java_lang_String::create_from_str("GC overhead limit exceeded", CHECK_false);
    java_lang_Throwable::set_message(Universe::_out_of_memory_error_gc_overhead_limit, msg());

    msg = java_lang_String::create_from_str("/ by zero", CHECK_false);
    java_lang_Throwable::set_message(Universe::_arithmetic_exception_instance, msg());

    // Setup the array of errors that have preallocated backtrace
    k = Universe::_out_of_memory_error_java_heap->klass();
    assert(k->klass_part()->name() == vmSymbols::java_lang_OutOfMemoryError(), "should be out of memory error");
    k_h = instanceKlassHandle(THREAD, k);

    int len = (StackTraceInThrowable) ? (int)PreallocatedOutOfMemoryErrorCount : 0;
    Universe::_preallocated_out_of_memory_error_array = oopFactory::new_objArray(k_h(), len, CHECK_false);
    for (int i=0; i<len; i++) {
      oop err = k_h->allocate_permanent_instance(CHECK_false);
      Handle err_h = Handle(THREAD, err);
      java_lang_Throwable::allocate_backtrace(err_h, CHECK_false);
      Universe::preallocated_out_of_memory_errors()->obj_at_put(i, err_h());
    }
    Universe::_preallocated_out_of_memory_error_avail_count = (jint)len;
  }


  // Setup static method for registering finalizers
  // The finalizer klass must be linked before looking up the method, in
  // case it needs to get rewritten.
  instanceKlass::cast(SystemDictionary::finalizer_klass())->link_class(CHECK_false);
  methodOop m = instanceKlass::cast(SystemDictionary::finalizer_klass())->find_method(
                                  vmSymbols::register_method_name(),
                                  vmSymbols::register_method_signature());
  if (m == NULL || !m->is_static()) {
    THROW_MSG_(vmSymbols::java_lang_NoSuchMethodException(),
      "java.lang.ref.Finalizer.register", false);
  }
  Universe::_finalizer_register_cache->init(
    SystemDictionary::finalizer_klass(), m, CHECK_false);

  // Resolve on first use and initialize class.
  // Note: No race-condition here, since a resolve will always return the same result

  // Setup method for security checks
  k = SystemDictionary::resolve_or_fail(vmSymbolHandles::java_lang_reflect_Method(), true, CHECK_false);
  k_h = instanceKlassHandle(THREAD, k);
  k_h->link_class(CHECK_false);
  m = k_h->find_method(vmSymbols::invoke_name(), vmSymbols::object_array_object_object_signature());
  if (m == NULL || m->is_static()) {
    THROW_MSG_(vmSymbols::java_lang_NoSuchMethodException(),
      "java.lang.reflect.Method.invoke", false);
  }
  Universe::_reflect_invoke_cache->init(k_h(), m, CHECK_false);

  // Setup method for registering loaded classes in class loader vector
  instanceKlass::cast(SystemDictionary::classloader_klass())->link_class(CHECK_false);
  m = instanceKlass::cast(SystemDictionary::classloader_klass())->find_method(vmSymbols::addClass_name(), vmSymbols::class_void_signature());
  if (m == NULL || m->is_static()) {
    THROW_MSG_(vmSymbols::java_lang_NoSuchMethodException(),
      "java.lang.ClassLoader.addClass", false);
  }
  Universe::_loader_addClass_cache->init(
    SystemDictionary::classloader_klass(), m, CHECK_false);

  // The folowing is initializing converter functions for serialization in
  // JVM.cpp. If we clean up the StrictMath code above we may want to find
  // a better solution for this as well.
  initialize_converter_functions();

  // This needs to be done before the first scavenge/gc, since
  // it's an input to soft ref clearing policy.
  {
    MutexLocker x(Heap_lock);
    Universe::update_heap_info_at_gc();
  }

  // ("weak") refs processing infrastructure initialization
  Universe::heap()->post_initialize();

  GC_locker::unlock();  // allow gc after bootstrapping

  MemoryService::set_universe_heap(Universe::_collectedHeap);
  return true;
}


void Universe::compute_base_vtable_size() {
  _base_vtable_size = ClassLoader::compute_Object_vtable();
}


// %%% The Universe::flush_foo methods belong in CodeCache.

// Flushes compiled methods dependent on dependee.
void Universe::flush_dependents_on(instanceKlassHandle dependee) {
  assert_lock_strong(Compile_lock);

  if (CodeCache::number_of_nmethods_with_dependencies() == 0) return;

  // CodeCache can only be updated by a thread_in_VM and they will all be
  // stopped dring the safepoint so CodeCache will be safe to update without
  // holding the CodeCache_lock.

  DepChange changes(dependee);

  // Compute the dependent nmethods
  if (CodeCache::mark_for_deoptimization(changes) > 0) {
    // At least one nmethod has been marked for deoptimization
    VM_Deoptimize op;
    VMThread::execute(&op);
  }
}

#ifdef HOTSWAP
// Flushes compiled methods dependent on dependee in the evolutionary sense
void Universe::flush_evol_dependents_on(instanceKlassHandle ev_k_h) {
  // --- Compile_lock is not held. However we are at a safepoint.
  assert_locked_or_safepoint(Compile_lock);
  if (CodeCache::number_of_nmethods_with_dependencies() == 0) return;

  // CodeCache can only be updated by a thread_in_VM and they will all be
  // stopped dring the safepoint so CodeCache will be safe to update without
  // holding the CodeCache_lock.

  // Compute the dependent nmethods
  if (CodeCache::mark_for_evol_deoptimization(ev_k_h) > 0) {
    // At least one nmethod has been marked for deoptimization

    // All this already happens inside a VM_Operation, so we'll do all the work here.
    // Stuff copied from VM_Deoptimize and modified slightly.

    // We do not want any GCs to happen while we are in the middle of this VM operation
    ResourceMark rm;
    DeoptimizationMarker dm;

    // Deoptimize all activations depending on marked nmethods
    Deoptimization::deoptimize_dependents();

    // Make the dependent methods not entrant (in VM_Deoptimize they are made zombies)
    CodeCache::make_marked_nmethods_not_entrant();
  }
}
#endif // HOTSWAP


// Flushes compiled methods dependent on dependee
void Universe::flush_dependents_on_method(methodHandle m_h) {
  // --- Compile_lock is not held. However we are at a safepoint.
  assert_locked_or_safepoint(Compile_lock);

  // CodeCache can only be updated by a thread_in_VM and they will all be
  // stopped dring the safepoint so CodeCache will be safe to update without
  // holding the CodeCache_lock.

  // Compute the dependent nmethods
  if (CodeCache::mark_for_deoptimization(m_h()) > 0) {
    // At least one nmethod has been marked for deoptimization

    // All this already happens inside a VM_Operation, so we'll do all the work here.
    // Stuff copied from VM_Deoptimize and modified slightly.

    // We do not want any GCs to happen while we are in the middle of this VM operation
    ResourceMark rm;
    DeoptimizationMarker dm;

    // Deoptimize all activations depending on marked nmethods
    Deoptimization::deoptimize_dependents();

    // Make the dependent methods not entrant (in VM_Deoptimize they are made zombies)
    CodeCache::make_marked_nmethods_not_entrant();
  }
}

void Universe::print() { print_on(gclog_or_tty); }

void Universe::print_on(outputStream* st) {
  st->print_cr("Heap");
  heap()->print_on(st);
}

void Universe::print_heap_at_SIGBREAK() {
  if (PrintHeapAtSIGBREAK) {
    MutexLocker hl(Heap_lock);
    print_on(tty);
    tty->cr();
    tty->flush();
  }
}

void Universe::print_heap_before_gc(outputStream* st) {
  st->print_cr("{Heap before GC invocations=%u (full %u):",
               heap()->total_collections(),
               heap()->total_full_collections());
  heap()->print_on(st);
}

void Universe::print_heap_after_gc(outputStream* st) {
  st->print_cr("Heap after GC invocations=%u (full %u):",
               heap()->total_collections(),
               heap()->total_full_collections());
  heap()->print_on(st);
  st->print_cr("}");
}

void Universe::verify(bool allow_dirty, bool silent, bool option) {
  if (SharedSkipVerify) {
    return;
  }

  // The use of _verify_in_progress is a temporary work around for
  // 6320749.  Don't bother with a creating a class to set and clear
  // it since it is only used in this method and the control flow is
  // straight forward.
  _verify_in_progress = true;

  COMPILER2_PRESENT(
    assert(!DerivedPointerTable::is_active(),
         "DPT should not be active during verification "
         "(of thread stacks below)");
  )

  ResourceMark rm;
  HandleMark hm;  // Handles created during verification can be zapped
  _verify_count++;

  if (!silent) gclog_or_tty->print("[Verifying ");
  if (!silent) gclog_or_tty->print("threads ");
  Threads::verify();
  heap()->verify(allow_dirty, silent, option);

  if (!silent) gclog_or_tty->print("syms ");
  SymbolTable::verify();
  if (!silent) gclog_or_tty->print("strs ");
  StringTable::verify();
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    if (!silent) gclog_or_tty->print("zone ");
    CodeCache::verify();
  }
  if (!silent) gclog_or_tty->print("dict ");
  SystemDictionary::verify();
  if (!silent) gclog_or_tty->print("hand ");
  JNIHandles::verify();
  if (!silent) gclog_or_tty->print("C-heap ");
  os::check_heap();
  if (!silent) gclog_or_tty->print_cr("]");

  _verify_in_progress = false;
}

// Oop verification (see MacroAssembler::verify_oop)

static uintptr_t _verify_oop_data[2]   = {0, (uintptr_t)-1};
static uintptr_t _verify_klass_data[2] = {0, (uintptr_t)-1};


static void calculate_verify_data(uintptr_t verify_data[2],
                                  HeapWord* low_boundary,
                                  HeapWord* high_boundary) {
  assert(low_boundary < high_boundary, "bad interval");

  // decide which low-order bits we require to be clear:
  size_t alignSize = MinObjAlignmentInBytes;
  size_t min_object_size = oopDesc::header_size();

  // make an inclusive limit:
  uintptr_t max = (uintptr_t)high_boundary - min_object_size*wordSize;
  uintptr_t min = (uintptr_t)low_boundary;
  assert(min < max, "bad interval");
  uintptr_t diff = max ^ min;

  // throw away enough low-order bits to make the diff vanish
  uintptr_t mask = (uintptr_t)(-1);
  while ((mask & diff) != 0)
    mask <<= 1;
  uintptr_t bits = (min & mask);
  assert(bits == (max & mask), "correct mask");
  // check an intermediate value between min and max, just to make sure:
  assert(bits == ((min + (max-min)/2) & mask), "correct mask");

  // require address alignment, too:
  mask |= (alignSize - 1);

  if (!(verify_data[0] == 0 && verify_data[1] == (uintptr_t)-1)) {
    assert(verify_data[0] == mask && verify_data[1] == bits, "mask stability");
  }
  verify_data[0] = mask;
  verify_data[1] = bits;
}


// Oop verification (see MacroAssembler::verify_oop)
#ifndef PRODUCT

uintptr_t Universe::verify_oop_mask() {
  MemRegion m = heap()->reserved_region();
  calculate_verify_data(_verify_oop_data,
                        m.start(),
                        m.end());
  return _verify_oop_data[0];
}



uintptr_t Universe::verify_oop_bits() {
  verify_oop_mask();
  return _verify_oop_data[1];
}


uintptr_t Universe::verify_klass_mask() {
  /* $$$
  // A klass can never live in the new space.  Since the new and old
  // spaces can change size, we must settle for bounds-checking against
  // the bottom of the world, plus the smallest possible new and old
  // space sizes that may arise during execution.
  size_t min_new_size = Universe::new_size();   // in bytes
  size_t min_old_size = Universe::old_size();   // in bytes
  calculate_verify_data(_verify_klass_data,
          (HeapWord*)((uintptr_t)_new_gen->low_boundary + min_new_size + min_old_size),
          _perm_gen->high_boundary);
                        */
  // Why doesn't the above just say that klass's always live in the perm
  // gen?  I'll see if that seems to work...
  MemRegion permanent_reserved;
  switch (Universe::heap()->kind()) {
  default:
    // ???: What if a CollectedHeap doesn't have a permanent generation?
    ShouldNotReachHere();
    break;
  case CollectedHeap::GenCollectedHeap:
  case CollectedHeap::G1CollectedHeap: {
    SharedHeap* sh = (SharedHeap*) Universe::heap();
    permanent_reserved = sh->perm_gen()->reserved();
   break;
  }
#ifndef SERIALGC
  case CollectedHeap::ParallelScavengeHeap: {
    ParallelScavengeHeap* psh = (ParallelScavengeHeap*) Universe::heap();
    permanent_reserved = psh->perm_gen()->reserved();
    break;
  }
#endif // SERIALGC
  }
  calculate_verify_data(_verify_klass_data,
                        permanent_reserved.start(),
                        permanent_reserved.end());

  return _verify_klass_data[0];
}



uintptr_t Universe::verify_klass_bits() {
  verify_klass_mask();
  return _verify_klass_data[1];
}


uintptr_t Universe::verify_mark_mask() {
  return markOopDesc::lock_mask_in_place;
}



uintptr_t Universe::verify_mark_bits() {
  intptr_t mask = verify_mark_mask();
  intptr_t bits = (intptr_t)markOopDesc::prototype();
  assert((bits & ~mask) == 0, "no stray header bits");
  return bits;
}
#endif // PRODUCT


void Universe::compute_verify_oop_data() {
  verify_oop_mask();
  verify_oop_bits();
  verify_mark_mask();
  verify_mark_bits();
  verify_klass_mask();
  verify_klass_bits();
}


void CommonMethodOopCache::init(klassOop k, methodOop m, TRAPS) {
  if (!UseSharedSpaces) {
    _klass = k;
  }
#ifndef PRODUCT
  else {
    // sharing initilization should have already set up _klass
    assert(_klass != NULL, "just checking");
  }
#endif

  _method_idnum = m->method_idnum();
  assert(_method_idnum >= 0, "sanity check");
}


ActiveMethodOopsCache::~ActiveMethodOopsCache() {
  if (_prev_methods != NULL) {
    for (int i = _prev_methods->length() - 1; i >= 0; i--) {
      jweak method_ref = _prev_methods->at(i);
      if (method_ref != NULL) {
        JNIHandles::destroy_weak_global(method_ref);
      }
    }
    delete _prev_methods;
    _prev_methods = NULL;
  }
}


void ActiveMethodOopsCache::add_previous_version(const methodOop method) {
  assert(Thread::current()->is_VM_thread(),
    "only VMThread can add previous versions");

  if (_prev_methods == NULL) {
    // This is the first previous version so make some space.
    // Start with 2 elements under the assumption that the class
    // won't be redefined much.
    _prev_methods = new (ResourceObj::C_HEAP) GrowableArray<jweak>(2, true);
  }

  // RC_TRACE macro has an embedded ResourceMark
  RC_TRACE(0x00000100,
    ("add: %s(%s): adding prev version ref for cached method @%d",
    method->name()->as_C_string(), method->signature()->as_C_string(),
    _prev_methods->length()));

  methodHandle method_h(method);
  jweak method_ref = JNIHandles::make_weak_global(method_h);
  _prev_methods->append(method_ref);

  // Using weak references allows previous versions of the cached
  // method to be GC'ed when they are no longer needed. Since the
  // caller is the VMThread and we are at a safepoint, this is a good
  // time to clear out unused weak references.

  for (int i = _prev_methods->length() - 1; i >= 0; i--) {
    jweak method_ref = _prev_methods->at(i);
    assert(method_ref != NULL, "weak method ref was unexpectedly cleared");
    if (method_ref == NULL) {
      _prev_methods->remove_at(i);
      // Since we are traversing the array backwards, we don't have to
      // do anything special with the index.
      continue;  // robustness
    }

    methodOop m = (methodOop)JNIHandles::resolve(method_ref);
    if (m == NULL) {
      // this method entry has been GC'ed so remove it
      JNIHandles::destroy_weak_global(method_ref);
      _prev_methods->remove_at(i);
    } else {
      // RC_TRACE macro has an embedded ResourceMark
      RC_TRACE(0x00000400, ("add: %s(%s): previous cached method @%d is alive",
        m->name()->as_C_string(), m->signature()->as_C_string(), i));
    }
  }
} // end add_previous_version()


bool ActiveMethodOopsCache::is_same_method(const methodOop method) const {
  instanceKlass* ik = instanceKlass::cast(klass());
  methodOop check_method = ik->method_with_idnum(method_idnum());
  assert(check_method != NULL, "sanity check");
  if (check_method == method) {
    // done with the easy case
    return true;
  }

  if (_prev_methods != NULL) {
    // The cached method has been redefined at least once so search
    // the previous versions for a match.
    for (int i = 0; i < _prev_methods->length(); i++) {
      jweak method_ref = _prev_methods->at(i);
      assert(method_ref != NULL, "weak method ref was unexpectedly cleared");
      if (method_ref == NULL) {
        continue;  // robustness
      }

      check_method = (methodOop)JNIHandles::resolve(method_ref);
      if (check_method == method) {
        // a previous version matches
        return true;
      }
    }
  }

  // either no previous versions or no previous version matched
  return false;
}


methodOop LatestMethodOopCache::get_methodOop() {
  instanceKlass* ik = instanceKlass::cast(klass());
  methodOop m = ik->method_with_idnum(method_idnum());
  assert(m != NULL, "sanity check");
  return m;
}


#ifdef ASSERT
// Release dummy object(s) at bottom of heap
bool Universe::release_fullgc_alot_dummy() {
  MutexLocker ml(FullGCALot_lock);
  if (_fullgc_alot_dummy_array != NULL) {
    if (_fullgc_alot_dummy_next >= _fullgc_alot_dummy_array->length()) {
      // No more dummies to release, release entire array instead
      _fullgc_alot_dummy_array = NULL;
      return false;
    }
    if (!UseConcMarkSweepGC) {
      // Release dummy at bottom of old generation
      _fullgc_alot_dummy_array->obj_at_put(_fullgc_alot_dummy_next++, NULL);
    }
    // Release dummy at bottom of permanent generation
    _fullgc_alot_dummy_array->obj_at_put(_fullgc_alot_dummy_next++, NULL);
  }
  return true;
}

#endif // ASSERT
