/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CODE_DEBUGINFO_HPP
#define SHARE_CODE_DEBUGINFO_HPP

#include "code/compressedStream.hpp"
#include "code/location.hpp"
#include "code/nmethod.hpp"
#include "code/oopRecorder.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/growableArray.hpp"

// Classes used for serializing debugging information.
// These abstractions are introducted to provide symmetric
// read and write operations.

// ScopeValue        describes the value of a variable/expression in a scope
// - LocationValue   describes a value in a given location (in frame or register)
// - ConstantValue   describes a constant

class ConstantOopReadValue;
class ConstantOopWriteValue;
class LocationValue;
class ObjectValue;
class ObjectMergeValue;

class ScopeValue: public AnyObj {
 public:
  // Testers
  virtual bool is_location() const { return false; }
  virtual bool is_object() const { return false; }
  virtual bool is_object_merge() const { return false; }
  virtual bool is_auto_box() const { return false; }
  virtual bool is_marker() const { return false; }
  virtual bool is_constant_int() const { return false; }
  virtual bool is_constant_double() const { return false; }
  virtual bool is_constant_long() const { return false; }
  virtual bool is_constant_oop() const { return false; }
  virtual bool equals(ScopeValue* other) const { return false; }

  ConstantOopReadValue* as_ConstantOopReadValue() {
    assert(is_constant_oop(), "must be");
    return (ConstantOopReadValue*) this;
  }

  ConstantOopWriteValue* as_ConstantOopWriteValue() {
    assert(is_constant_oop(), "must be");
    return (ConstantOopWriteValue*) this;
  }

  ObjectValue* as_ObjectValue() {
    assert(is_object(), "must be");
    return (ObjectValue*)this;
  }

  ObjectMergeValue* as_ObjectMergeValue() {
    assert(is_object_merge(), "must be");
    return (ObjectMergeValue*)this;
  }

  LocationValue* as_LocationValue() {
    assert(is_location(), "must be");
    return (LocationValue*)this;
  }

  // Serialization of debugging information
  virtual void write_on(DebugInfoWriteStream* stream) = 0;
  static ScopeValue* read_from(DebugInfoReadStream* stream);
};


// A Location value describes a value in a given location; i.e. the corresponding
// logical entity (e.g., a method temporary) lives in this location.

class LocationValue: public ScopeValue {
 private:
  Location  _location;
 public:
  LocationValue(Location location)           { _location = location; }
  bool      is_location() const              { return true; }
  Location  location() const                 { return _location; }

  // Serialization of debugging information
  LocationValue(DebugInfoReadStream* stream);
  void write_on(DebugInfoWriteStream* stream);

  // Printing
  void print_on(outputStream* st) const;
};

// A placeholder value that has no concrete meaning other than helping constructing
// other values.

class MarkerValue: public ScopeValue {
public:
  bool      is_marker() const                { return true; }

  // Serialization of debugging information
  void write_on(DebugInfoWriteStream* stream);

  // Printing
  void print_on(outputStream* st) const;
};

// An ObjectValue describes an object eliminated by escape analysis.

class ObjectValue: public ScopeValue {
 protected:
  int                        _id;
  ScopeValue*                _klass;
  GrowableArray<ScopeValue*> _field_values;
  Handle                     _value;
  bool                       _visited;
  bool                       _is_root;   // Will be true if this object is referred to
                                         // as a local/expression/monitor in the JVMs.
                                         // Otherwise false, meaning it's just a candidate
                                         // in an object allocation merge.
 public:
  ObjectValue(int id, ScopeValue* klass)
     : _id(id)
     , _klass(klass)
     , _field_values()
     , _value()
     , _visited(false)
     , _is_root(true) {
    assert(klass->is_constant_oop(), "should be constant java mirror oop");
  }

  ObjectValue(int id)
     : _id(id)
     , _klass(nullptr)
     , _field_values()
     , _value()
     , _visited(false)
     , _is_root(true) {}

  // Accessors
  bool                        is_object() const           { return true; }
  int                         id() const                  { return _id; }
  virtual ScopeValue*         klass() const               { return _klass; }
  virtual GrowableArray<ScopeValue*>* field_values()      { return &_field_values; }
  virtual ScopeValue*         field_at(int i) const       { return _field_values.at(i); }
  virtual int                 field_size()                { return _field_values.length(); }
  virtual Handle              value() const               { return _value; }
  bool                        is_visited() const          { return _visited; }
  bool                        is_root() const             { return _is_root; }

  void                        set_id(int id)              { _id = id; }
  virtual void                set_value(oop value);
  void                        set_visited(bool visited)   { _visited = visited; }
  void                        set_root(bool root)         { _is_root = root; }

  // Serialization of debugging information
  void read_object(DebugInfoReadStream* stream);
  void write_on(DebugInfoWriteStream* stream);

  // Printing
  void print_on(outputStream* st) const;
  void print_fields_on(outputStream* st) const;
};

// An ObjectMergeValue describes objects that were inputs to a Phi in C2 and at
// least one of them was scalar replaced.
// '_selector' is an integer value that will be '-1' if during the execution of
// the C2 compiled code the path taken was that of the Phi input that was NOT
// scalar replaced. In that case '_merge_pointer' is a pointer to an already
// allocated object. If '_selector' is not '-1' then it should be the index of
// an object in '_possible_objects'. That object is an ObjectValue describing an
// object that was scalar replaced.

class ObjectMergeValue: public ObjectValue {
protected:
  ScopeValue*                _selector;
  ScopeValue*                _merge_pointer;
  GrowableArray<ScopeValue*> _possible_objects;

  // This holds the ObjectValue that should be used in place of this
  // ObjectMergeValue. I.e., it's the ScopeValue from _possible_objects that was
  // selected by 'select()' or is a on-the-fly created ScopeValue representing
  // the _merge_pointer if _selector is -1.
  //
  // We need to keep this reference around because there will be entries in
  // ScopeDesc that reference this ObjectMergeValue directly. After
  // rematerialization ObjectMergeValue will be just a wrapper for the
  // Objectvalue pointed by _selected.
  ObjectValue*               _selected;
public:
  ObjectMergeValue(int id, ScopeValue* merge_pointer, ScopeValue* selector)
     : ObjectValue(id)
     , _selector(selector)
     , _merge_pointer(merge_pointer)
     , _possible_objects()
     , _selected(nullptr) {}

  ObjectMergeValue(int id)
     : ObjectValue(id)
     , _selector(nullptr)
     , _merge_pointer(nullptr)
     , _possible_objects()
     , _selected(nullptr) {}

  bool                        is_object_merge() const         { return true; }
  ScopeValue*                 selector() const                { return _selector; }
  ScopeValue*                 merge_pointer() const           { return _merge_pointer; }
  GrowableArray<ScopeValue*>* possible_objects()              { return &_possible_objects; }
  ObjectValue*                select(frame& fr, RegisterMap& reg_map) ;

  ScopeValue*                 klass() const                   { ShouldNotReachHere(); return nullptr; }
  GrowableArray<ScopeValue*>* field_values()                  { ShouldNotReachHere(); return nullptr; }
  ScopeValue*                 field_at(int i) const           { ShouldNotReachHere(); return nullptr; }
  int                         field_size()                    { ShouldNotReachHere(); return -1; }

  Handle                      value() const;
  void                        set_value(oop value)            { assert(_selected != nullptr, "Should call select() first."); _selected->set_value(value); }

  // Serialization of debugging information
  void read_object(DebugInfoReadStream* stream);
  void write_on(DebugInfoWriteStream* stream);
};

class AutoBoxObjectValue : public ObjectValue {
  bool                       _cached;
public:
  bool                       is_auto_box() const        { return true; }
  bool                       is_cached() const          { return _cached; }
  void                       set_cached(bool cached)    { _cached = cached; }
  AutoBoxObjectValue(int id, ScopeValue* klass) : ObjectValue(id, klass), _cached(false) { }
  AutoBoxObjectValue(int id) : ObjectValue(id), _cached(false) { }
};


// A ConstantIntValue describes a constant int; i.e., the corresponding logical entity
// is either a source constant or its computation has been constant-folded.

class ConstantIntValue: public ScopeValue {
 private:
  jint _value;
 public:
  ConstantIntValue(jint value)         { _value = value; }
  jint value() const                   { return _value;  }
  bool is_constant_int() const         { return true;    }
  bool equals(ScopeValue* other) const { return false;   }

  // Serialization of debugging information
  ConstantIntValue(DebugInfoReadStream* stream);
  void write_on(DebugInfoWriteStream* stream);

  // Printing
  void print_on(outputStream* st) const;
};

class ConstantLongValue: public ScopeValue {
 private:
  jlong _value;
 public:
  ConstantLongValue(jlong value)       { _value = value; }
  jlong value() const                  { return _value;  }
  bool is_constant_long() const        { return true;    }
  bool equals(ScopeValue* other) const { return false;   }

  // Serialization of debugging information
  ConstantLongValue(DebugInfoReadStream* stream);
  void write_on(DebugInfoWriteStream* stream);

  // Printing
  void print_on(outputStream* st) const;
};

class ConstantDoubleValue: public ScopeValue {
 private:
  jdouble _value;
 public:
  ConstantDoubleValue(jdouble value)   { _value = value; }
  jdouble value() const                { return _value;  }
  bool is_constant_double() const      { return true;    }
  bool equals(ScopeValue* other) const { return false;   }

  // Serialization of debugging information
  ConstantDoubleValue(DebugInfoReadStream* stream);
  void write_on(DebugInfoWriteStream* stream);

  // Printing
  void print_on(outputStream* st) const;
};

// A ConstantOopWriteValue is created by the compiler to
// be written as debugging information.

class ConstantOopWriteValue: public ScopeValue {
 private:
  jobject _value;
 public:
  ConstantOopWriteValue(jobject value) { _value = value; }
  jobject value() const                { return _value;  }
  bool is_constant_oop() const         { return true;    }
  bool equals(ScopeValue* other) const { return false;   }

  // Serialization of debugging information
  void write_on(DebugInfoWriteStream* stream);

  // Printing
  void print_on(outputStream* st) const;
};

// A ConstantOopReadValue is created by the VM when reading
// debug information

class ConstantOopReadValue: public ScopeValue {
 private:
  Handle _value;
 public:
  Handle value() const                 { return _value;  }
  bool is_constant_oop() const         { return true;    }
  bool equals(ScopeValue* other) const { return false;   }

  // Serialization of debugging information
  ConstantOopReadValue(DebugInfoReadStream* stream);
  void write_on(DebugInfoWriteStream* stream);

  // Printing
  void print_on(outputStream* st) const;
};

// MonitorValue describes the pair used for monitor_enter and monitor_exit.

class MonitorValue: public ResourceObj {
 private:
  ScopeValue* _owner;
  Location    _basic_lock;
  bool        _eliminated;
 public:
  // Constructor
  MonitorValue(ScopeValue* owner, Location basic_lock, bool eliminated = false);

  // Accessors
  ScopeValue*  owner()      const { return _owner; }
  Location     basic_lock() const { return _basic_lock;  }
  bool         eliminated() const { return _eliminated; }

  // Serialization of debugging information
  MonitorValue(DebugInfoReadStream* stream);
  void write_on(DebugInfoWriteStream* stream);

  // Printing
  void print_on(outputStream* st) const;
};

// DebugInfoReadStream specializes CompressedReadStream for reading
// debugging information. Used by ScopeDesc.

class DebugInfoReadStream : public CompressedReadStream {
 private:
  const CompiledMethod* _code;
  const CompiledMethod* code() const { return _code; }
  GrowableArray<ScopeValue*>* _obj_pool;
 public:
  DebugInfoReadStream(const CompiledMethod* code, int offset, GrowableArray<ScopeValue*>* obj_pool = nullptr) :
    CompressedReadStream(code->scopes_data_begin(), offset) {
    _code = code;
    _obj_pool = obj_pool;

  } ;

  oop read_oop();
  Method* read_method() {
    Method* o = (Method*)(code()->metadata_at(read_int()));
    // is_metadata() is a faster check than is_metaspace_object()
    assert(o == nullptr || o->is_metadata(), "meta data only");
    return o;
  }
  ScopeValue* read_object_value(bool is_auto_box);
  ScopeValue* read_object_merge_value();
  ScopeValue* get_cached_object();
  // BCI encoding is mostly unsigned, but -1 is a distinguished value
  int read_bci() { return read_int() + InvocationEntryBci; }
};

// DebugInfoWriteStream specializes CompressedWriteStream for
// writing debugging information. Used by ScopeDescRecorder.

class DebugInfoWriteStream : public CompressedWriteStream {
 private:
  DebugInformationRecorder* _recorder;
  DebugInformationRecorder* recorder() const { return _recorder; }
 public:
  DebugInfoWriteStream(DebugInformationRecorder* recorder, int initial_size);
  void write_handle(jobject h);
  void write_bci(int bci) { write_int(bci - InvocationEntryBci); }

  void write_metadata(Metadata* m);
};

#endif // SHARE_CODE_DEBUGINFO_HPP
