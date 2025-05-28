/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/vmSymbols.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/perfData.inline.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalCounter.inline.hpp"
#include "utilities/globalDefinitions.hpp"

PerfDataList*   PerfDataManager::_all = nullptr;
PerfDataList*   PerfDataManager::_constants = nullptr;
volatile bool   PerfDataManager::_has_PerfData = 0;

/*
 * The jvmstat global and subsystem jvmstat counter name spaces. The top
 * level name spaces imply the interface stability level of the counter,
 * which generally follows the Java package, class, and property naming
 * conventions. The CounterNS enumeration values should be used to index
 * into this array.
 */
const char* PerfDataManager::_name_spaces[] = {
  // top level name spaces
  "java",                   // stable and supported name space
  "com.sun",                // unstable but supported name space
  "sun",                    // unstable and unsupported name space
  // subsystem name spaces
  "java.gc",                // Garbage Collection name spaces
  "com.sun.gc",
  "sun.gc",
  "java.ci",                // Compiler name spaces
  "com.sun.ci",
  "sun.ci",
  "java.cls",               // Class Loader name spaces
  "com.sun.cls",
  "sun.cls",
  "java.rt",                // Runtime name spaces
  "com.sun.rt",
  "sun.rt",
  "java.os",                // Operating System name spaces
  "com.sun.os",
  "sun.os",
  "java.threads",           // Threads System name spaces
  "com.sun.threads",
  "sun.threads",
  "java.threads.cpu_time", //Thread CPU time name spaces
  "com.sun.threads.cpu_time",
  "sun.threads.cpu_time",
  "java.property",          // Java Property name spaces
  "com.sun.property",
  "sun.property",
  "",
};

PerfData::PerfData(CounterNS ns, const char* name, Units u, Variability v)
                  : _name(nullptr), _v(v), _u(u), _on_c_heap(false), _valuep(nullptr) {

  const char* prefix = PerfDataManager::ns_to_string(ns);

  const size_t _name_size = strlen(name) + strlen(prefix) + 2;
  _name = NEW_C_HEAP_ARRAY(char, _name_size, mtInternal);
  assert(strlen(name) != 0, "invalid name");

  if (ns == NULL_NS) {
     // No prefix is added to counters with the NULL_NS namespace.
     strcpy(_name, name);
     // set the F_Supported flag based on the counter name prefix.
     if (PerfDataManager::is_stable_supported(_name) ||
         PerfDataManager::is_unstable_supported(_name)) {
       _flags = F_Supported;
     }
     else {
       _flags = F_None;
     }
  }
  else {
    os::snprintf_checked(_name, _name_size, "%s.%s", prefix, name);
    // set the F_Supported flag based on the given namespace.
    if (PerfDataManager::is_stable_supported(ns) ||
        PerfDataManager::is_unstable_supported(ns)) {
      _flags = F_Supported;
    }
    else {
      _flags = F_None;
    }
  }
}

PerfData::~PerfData() {
  FREE_C_HEAP_ARRAY(char, _name);
  if (is_on_c_heap()) {
    FREE_C_HEAP_ARRAY(PerfDataEntry, _pdep);
  }
}

void PerfData::create_entry(BasicType dtype, size_t dsize, size_t vlen) {

  size_t dlen = vlen==0 ? 1 : vlen;

  size_t namelen = strlen(name()) + 1;  // include null terminator
  size_t size = sizeof(PerfDataEntry) + namelen;
  size_t pad_length = ((size % dsize) == 0) ? 0 : dsize - (size % dsize);
  size += pad_length;
  size_t data_start = size;
  size += (dsize * dlen);

  // align size to assure allocation in units of 8 bytes
  int align = sizeof(jlong) - 1;
  size = ((size + align) & ~align);
  char* psmp = PerfMemory::alloc(size);

  if (psmp == nullptr) {
    // out of PerfMemory memory resources. allocate on the C heap
    // to avoid vm termination.
    psmp = NEW_C_HEAP_ARRAY(char, size, mtInternal);
    _on_c_heap = true;
  }

  // compute the addresses for the name and data
  char* cname = psmp + sizeof(PerfDataEntry);

  // data is in the last dsize*dlen bytes of the entry
  void* valuep = (void*) (psmp + data_start);

  assert(is_on_c_heap() || PerfMemory::contains(cname), "just checking");
  assert(is_on_c_heap() || PerfMemory::contains((char*)valuep), "just checking");

  // copy the name, including null terminator, into PerfData memory
  strcpy(cname, name());


  // set the header values in PerfData memory
  PerfDataEntry* pdep = (PerfDataEntry*)psmp;
  pdep->entry_length = (jint)size;
  pdep->name_offset = (jint) ((uintptr_t) cname - (uintptr_t) psmp);
  pdep->vector_length = (jint)vlen;
  pdep->data_type = (jbyte) type2char(dtype);
  pdep->data_units = units();
  pdep->data_variability = variability();
  pdep->flags = (jbyte)flags();
  pdep->data_offset = (jint) data_start;

  log_debug(perf, datacreation)("name = %s, dtype = %d, variability = %d,"
                                " units = %d, dsize = %zu, vlen = %zu,"
                                " pad_length = %zu, size = %zu, on_c_heap = %s,"
                                " address = " INTPTR_FORMAT ","
                                " data address = " INTPTR_FORMAT,
                                cname, dtype, variability(),
                                units(), dsize, vlen,
                                pad_length, size, is_on_c_heap() ? "TRUE":"FALSE",
                                p2i(psmp), p2i(valuep));

  // record the start of the entry and the location of the data field.
  _pdep = pdep;
  _valuep = valuep;

  // mark the PerfData memory region as having been updated.
  PerfMemory::mark_updated();
}

bool PerfData::name_equals(const char* name) const {
  return strcmp(name, this->name()) == 0;
}

PerfLong::PerfLong(CounterNS ns, const char* namep, Units u, Variability v)
                 : PerfData(ns, namep, u, v) {

  create_entry(T_LONG, sizeof(jlong));
}

PerfByteArray::PerfByteArray(CounterNS ns, const char* namep, Units u,
                             Variability v, jint length)
                            : PerfData(ns, namep, u, v), _length(length) {

  create_entry(T_BYTE, sizeof(jbyte), (size_t)_length);
}

void PerfString::set_string(const char* s2) {

  // copy n bytes of the string, assuring the null string is
  // copied if s2 == nullptr.
  strncpy((char *)_valuep, s2 == nullptr ? "" : s2, _length);

  // assure the string is null terminated when strlen(s2) >= _length
  ((char*)_valuep)[_length-1] = '\0';
}

PerfStringConstant::PerfStringConstant(CounterNS ns, const char* namep,
                                       const char* initial_value)
                     : PerfString(ns, namep, V_Constant,
                                  initial_value == nullptr ? 1 :
                                  MIN2((jint)(strlen((char*)initial_value)+1),
                                       (jint)(PerfMaxStringConstLength+1)),
                                  initial_value) {

  if (PrintMiscellaneous && Verbose) {
    if (is_valid() && initial_value != nullptr &&
        ((jint)strlen(initial_value) > (jint)PerfMaxStringConstLength)) {

      warning("Truncating PerfStringConstant: name = %s,"
              " length = " INT32_FORMAT ","
              " PerfMaxStringConstLength = " INT32_FORMAT "\n",
              namep,
              (jint)strlen(initial_value),
              (jint)PerfMaxStringConstLength);
    }
  }
}


void PerfDataManager::destroy() {

  if (_all == nullptr)
    // destroy already called, or initialization never happened
    return;

  // About to delete the counters than might still be accessed by other threads.
  // The shutdown is performed in two stages: a) clear the flag to notify future
  // counter users that we are at shutdown; b) sync up with current users, waiting
  // for them to finish with counters.
  //
  Atomic::store(&_has_PerfData, false);
  GlobalCounter::write_synchronize();

  log_debug(perf, datacreation)("Total = %d, Constants = %d",
                                _all->length(),
                                _constants == nullptr ? 0 : _constants->length());

  for (int index = 0; index < _all->length(); index++) {
    PerfData* p = _all->at(index);
    delete p;
  }

  delete(_all);
  delete(_constants);

  _all = nullptr;
  _constants = nullptr;
}

void PerfDataManager::add_item(PerfData* p) {

  MutexLocker ml(PerfDataManager_lock);

  // Default sizes determined using -Xlog:perf+datacreation=debug
  if (_all == nullptr) {
    _all = new PerfDataList(191);
    Atomic::release_store(&_has_PerfData, true);
  }

  assert(!_all->contains(p->name()), "duplicate name added: %s", p->name());

  // add to the list of all perf data items
  _all->append(p);

  if (p->variability() == PerfData::V_Constant) {
    if (_constants == nullptr) {
      _constants = new PerfDataList(51);
    }
    _constants->append(p);
    return;
  }
}

char* PerfDataManager::counter_name(const char* ns, const char* name) {
   assert(ns != nullptr, "ns string required");
   assert(name != nullptr, "name string required");

   size_t len = strlen(ns) + strlen(name) + 2;
   char* result = NEW_RESOURCE_ARRAY(char, len);
   os::snprintf_checked(result, len, "%s.%s", ns, name);
   return result;
}

char* PerfDataManager::name_space(const char* ns, const char* sub,
                                  int instance) {
   char intbuf[40];
   jio_snprintf(intbuf, 40, UINT32_FORMAT, instance);
   return name_space(ns, name_space(sub, intbuf));
}

char *PerfDataManager::name_space(const char* ns, int instance) {
   char intbuf[40];
   jio_snprintf(intbuf, 40, UINT32_FORMAT, instance);
   return name_space(ns, intbuf);
}

PerfStringConstant* PerfDataManager::create_string_constant(CounterNS ns,
                                                            const char* name,
                                                            const char* s,
                                                            TRAPS) {

  PerfStringConstant* p = new PerfStringConstant(ns, name, s);

  if (!p->is_valid()) {
    // allocation of native resources failed.
    delete p;
    THROW_NULL(vmSymbols::java_lang_OutOfMemoryError());
  }

  add_item(p);

  return p;
}

PerfLongConstant* PerfDataManager::create_long_constant(CounterNS ns,
                                                        const char* name,
                                                        PerfData::Units u,
                                                        jlong val, TRAPS) {

  PerfLongConstant* p = new PerfLongConstant(ns, name, u, val);

  if (!p->is_valid()) {
    // allocation of native resources failed.
    delete p;
    THROW_NULL(vmSymbols::java_lang_OutOfMemoryError());
  }

  add_item(p);

  return p;
}

PerfStringVariable* PerfDataManager::create_string_variable(CounterNS ns,
                                                            const char* name,
                                                            int max_length,
                                                            const char* s,
                                                            TRAPS) {

  if (max_length == 0 && s != nullptr) max_length = (int)strlen(s);

  assert(max_length != 0, "PerfStringVariable with length 0");

  PerfStringVariable* p = new PerfStringVariable(ns, name, max_length, s);

  if (!p->is_valid()) {
    // allocation of native resources failed.
    delete p;
    THROW_NULL(vmSymbols::java_lang_OutOfMemoryError());
  }

  add_item(p);

  return p;
}

PerfLongVariable* PerfDataManager::create_long_variable(CounterNS ns,
                                                        const char* name,
                                                        PerfData::Units u,
                                                        jlong ival, TRAPS) {

  PerfLongVariable* p = new PerfLongVariable(ns, name, u, ival);

  if (!p->is_valid()) {
    // allocation of native resources failed.
    delete p;
    THROW_NULL(vmSymbols::java_lang_OutOfMemoryError());
  }

  add_item(p);

  return p;
}

PerfLongCounter* PerfDataManager::create_long_counter(CounterNS ns,
                                                      const char* name,
                                                      PerfData::Units u,
                                                      jlong ival, TRAPS) {

  PerfLongCounter* p = new PerfLongCounter(ns, name, u, ival);

  if (!p->is_valid()) {
    // allocation of native resources failed.
    delete p;
    THROW_NULL(vmSymbols::java_lang_OutOfMemoryError());
  }

  add_item(p);

  return p;
}

/*
 * Call into java.lang.System.getProperty to check that the value of the
 * specified property matches
 */
void PerfDataManager::assert_system_property(const char* name, const char* value, TRAPS) {
#ifdef ASSERT
  ResourceMark rm(THREAD);

  // setup the arguments to getProperty
  Handle key_str   = java_lang_String::create_from_str(name, CHECK);

  // return value
  JavaValue result(T_OBJECT);

  // public static String getProperty(String key, String def);
  JavaCalls::call_static(&result, vmClasses::System_klass(),
                         vmSymbols::getProperty_name(),
                         vmSymbols::string_string_signature(), key_str, CHECK);

  oop value_oop = result.get_oop();
  assert(value_oop != nullptr, "property must have a value");

  // convert Java String to utf8 string
  char *system_value = java_lang_String::as_utf8_string(value_oop);

  assert(strcmp(value, system_value) == 0, "property value mustn't differ from System.getProperty. Our value is: %s, System.getProperty is: %s",
         value, system_value);
#endif // ASSERT
}

/*
 * Adds a constant counter of the given property. Asserts the value does not
 * differ from the value retrievable from System.getProperty(name)
 */
void PerfDataManager::add_property_constant(CounterNS name_space, const char* name, const char* value, TRAPS) {
  // the property must exist
  assert(value != nullptr, "property name should be have a value: %s", name);
  assert_system_property(name, value, CHECK);

  // create the property counter
  PerfDataManager::create_string_constant(name_space, name, value, CHECK);
}

/*
 * Adds a string constant of the given property. Retrieves the value via
 * Arguments::get_property() and asserts the value for the does not differ from
 * the value retrievable from System.getProperty()
 */
void PerfDataManager::add_property_constant(CounterNS name_space, const char* name, TRAPS) {
  add_property_constant(name_space, name, Arguments::get_property(name), CHECK);
}

/*
 * Adds a string constant of the given property. Retrieves the value via
 * Arguments::get_property() and asserts the value for the does not differ from
 * the value retrievable from System.getProperty()
 */
void PerfDataManager::add_optional_property_constant(CounterNS name_space, const char* name, TRAPS) {
  const char* value = Arguments::get_property(name);

  if (value != nullptr) {
    add_property_constant(name_space, name, value, CHECK);
  }
}

void PerfDataManager::create_system_property_instrumentation(TRAPS) {

  // Non-writeable, constant properties
  add_property_constant(JAVA_PROPERTY, "java.vm.specification.name", "Java Virtual Machine Specification", CHECK);
  add_property_constant(JAVA_PROPERTY, "java.version", JDK_Version::java_version(), CHECK);
  add_property_constant(JAVA_PROPERTY, "java.vm.version", VM_Version::vm_release(), CHECK);
  add_property_constant(JAVA_PROPERTY, "java.vm.name", VM_Version::vm_name(), CHECK);
  add_property_constant(JAVA_PROPERTY, "java.vm.vendor", VM_Version::vm_vendor(), CHECK);
  add_property_constant(JAVA_PROPERTY, "jdk.debug", VM_Version::jdk_debug_level(), CHECK);

  // Get remaining property constants via Arguments::get_property,
  // which does a linear search over the internal system properties list.

  // SUN_PROPERTY properties
  add_property_constant(SUN_PROPERTY, "sun.boot.library.path", CHECK);

  // JAVA_PROPERTY properties
  add_property_constant(JAVA_PROPERTY, "java.vm.specification.version", CHECK);
  add_property_constant(JAVA_PROPERTY, "java.vm.specification.vendor", CHECK);
  add_property_constant(JAVA_PROPERTY, "java.vm.info", CHECK);
  add_property_constant(JAVA_PROPERTY, "java.library.path", CHECK);
  add_property_constant(JAVA_PROPERTY, "java.class.path", CHECK);
  add_property_constant(JAVA_PROPERTY, "java.home", CHECK);

  add_optional_property_constant(JAVA_PROPERTY, "jdk.module.path", CHECK);
  add_optional_property_constant(JAVA_PROPERTY, "jdk.module.upgrade.path", CHECK);
  add_optional_property_constant(JAVA_PROPERTY, "jdk.module.main", CHECK);
}

void PerfDataManager::create_misc_perfdata() {

  ResourceMark rm;
  EXCEPTION_MARK;

  // numeric constants

  // frequency of the native high resolution timer
  create_constant(SUN_OS, "hrt.frequency", PerfData::U_Hertz,
    os::elapsed_frequency(), CHECK);

  // string constants

  // create string instrumentation for various Java properties.
  create_system_property_instrumentation(CHECK);

  // HotSpot flags (from .hotspotrc) and args (from command line)
  //
  create_string_constant(JAVA_RT, "vmFlags", Arguments::jvm_flags(), CHECK);
  create_string_constant(JAVA_RT, "vmArgs", Arguments::jvm_args(), CHECK);

  // java class name/jar file and arguments to main class
  // note: name is coordinated with launcher and Arguments.cpp
  create_string_constant(SUN_RT, "javaCommand", Arguments::java_command(), CHECK);

  // the Java VM Internal version string
  create_string_constant(SUN_RT, "internalVersion",
                         VM_Version::internal_vm_info_string(), CHECK);
}

PerfDataList::PerfDataList(int length) {

  _set = new (mtInternal) PerfDataArray(length, mtInternal);
}

PerfDataList::PerfDataList(PerfDataList* p) {

  _set = new (mtInternal) PerfDataArray(p->length(), mtInternal);

  _set->appendAll(p->get_impl());
}

PerfDataList::~PerfDataList() {

  delete _set;

}

PerfData* PerfDataList::find_by_name(const char* name) {

  int i = _set->find_if([&](PerfData* pd) { return pd->name_equals(name); });

  if (i >= 0 && i <= _set->length())
    return _set->at(i);
  else
    return nullptr;
}

PerfDataList* PerfDataList::clone() {

  PerfDataList* copy = new PerfDataList(this);

  assert(copy != nullptr, "just checking");

  return copy;
}

