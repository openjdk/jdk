/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_PERIODIC_JFRREDACTEDEVENTS_HPP
#define SHARE_JFR_PERIODIC_JFRREDACTEDEVENTS_HPP

#include "memory/allocation.hpp"
#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"

#include <string.h>

class JVMFlag;

class JfrRedactedEvents: public AllStatic {
 public:
  // Called at startup
  static char* new_redacted_text();
  static bool set_argument_filter(const char* filters);
  static bool set_key_filter(const char* filters);
  // Synchronized in Java
  static bool emit_initial_environment_variables();
  static void emit_initial_system_properties();
  static void emit_jvm_information();
  static void emit_string_flags();
  // Called at shutdown
  static void destroy();

  template<typename T> static GrowableArray<T>* make_array(int size) {
    return new (mtTracing) GrowableArray<T>(size, mtTracing);
  }

  template<typename T> static void destroy_array(GrowableArray<T*>* array) {
    if (array == nullptr) {
      return;
    }
    for (int i = 0; i < array->length(); ++i) {
      delete array->at(i);
    }
    delete array;
  }

  class String: public CHeapObj<mtTracing> {
   public:
    String(size_t length) : _length(length) {
      _text = NEW_C_HEAP_ARRAY(char, length + 1, mtTracing);
      memset(_text, 0, length + 1);
    }
    String(const char* source, size_t length) : String(length) {
      memcpy(_text, source, length);
    }
    String(const char* source) : String(source, strlen(source)) {
    }
    String(const char* prefix, size_t prefix_length, const char* postfix, size_t postfix_length)
      : String(prefix_length + postfix_length) {
      memcpy(_text, prefix,  prefix_length);
      memcpy(_text + prefix_length, postfix, postfix_length);
    }
    ~String() {
      FREE_C_HEAP_ARRAY(char, _text);
    }
    const char* text() const {
      return _text;
    }
    char at(size_t index) const {
      assert(index < _length, "out of bounds");
      return _text[index];
    }
    void set(size_t index, char c) {
      assert(index < _length, "out of bounds");
      _text[index] = c;
    }
    size_t length() const {
      return _length;
    }
    static String* from_cstr(const char* s) {
      return s == nullptr ? nullptr : new String(s);
    }
    static const char* c_str(const String* s) {
      return s == nullptr ? nullptr : s->text();
    }
   private:
    const size_t _length;
    char* _text;
  };

  class StringArray: public CHeapObj<mtTracing> {
   public:
    StringArray() : _array(make_array<String*>(0)) {
    }
    StringArray(int capacity) : _array(make_array<String*>(capacity)) {
    }
    StringArray(const char* const array[], int count) : _array(make_array<String*>(count)) {
      for (int i = 0; i < count; ++i) {
        _array->append(new String(array[i]));
      }
    }
    ~StringArray() {
      destroy_array(_array);
    }
    int length() const {
      return _array->length();
    }
    const String* at(int i) const {
      return _array->at(i);
    }
    void add(String* s) {
      _array->append(s);
    }
    void add_contents(const String* s) {
      add(s->text());
    }
    void add(const char* s) {
      _array->append(new String(s));
    }
    void add(const char* s, size_t length) {
      _array->append(new String(s, length));
    }
   private:
    GrowableArray<String*>* const _array;
  };

  class StringKeyValue: public CHeapObj<mtTracing> {
   public:
    StringKeyValue(String* key, const char* value): _key(key), _value(String::from_cstr(value)) {
    }
    StringKeyValue(const char* key, const char* value) : StringKeyValue(new String(key), value) {
    }
    ~StringKeyValue() {
      delete _key;
      delete _value;
    }
    const char* key() const {
       return _key->text();
    }
    const char* value() const {
      return String::c_str(_value);
    }
   private:
    String* const _key;
    String* const _value;
  };

  class StringFlag: public CHeapObj<mtTracing> {
   public:
    StringFlag(const JVMFlag* flag, const char* redacted_value)
    : _flag(flag),
      _redacted_value(String::from_cstr(redacted_value)) {
    }
    ~StringFlag() {
      delete _redacted_value;
    }
    const JVMFlag* jvm_flag() const {
      return _flag;
    }
    const char* redacted_value() const {
      return _redacted_value == nullptr ? nullptr : _redacted_value->text();
    }
   private:
    const JVMFlag* const _flag;
    String* _redacted_value;
  };

 private:
  static bool _initialized;
  static StringArray* _argument_filters;
  static StringArray* _key_filters;
  static StringArray* _redacted_arguments;
  static String* _redacted_java_command_line;
  static String* _redacted_jvm_command_line;
  static String* _redacted_flags_command_line;
  static String* _redacted_flight_recorder_options;
  static GrowableArray<StringKeyValue*>* _initial_system_properties;
  static GrowableArray<StringKeyValue*>* _initial_environment_variables;
  static GrowableArray<StringFlag*>*     _string_flags;

  static bool append_filters(StringArray* target, bool argument, const char* filters);
  static void add_default_filters(StringArray* target, bool argument);
  static String* concatenate(StringArray* strings);
  static bool equals_case_insensitive(char a, char b);
  static bool is_redacted_key(const char* key);
  static bool is_separator(char c);
  static void ensure_initialized();
  static StringArray* make_java_args_array();
  static StringArray* make_jvm_args_array(char** jvm_args_array, int array_length);
  static StringArray* make_filter_array(const char* filter);
  static bool match(const char* pattern, const char* text);
  static bool match_flag(const char* flag_name, const char* argument);
  static int match_arguments(StringArray* filter_array, StringArray* arguments, int arg_index);
  static bool match_key(StringArray* array, const char* text);
  static bool read_file(StringArray* target, const char* filename);
  static String* redact_command_line(StringArray* arguments);
  static StringArray* split(const char* text, char separator);
};

#endif // SHARE_JFR_PERIODIC_JFRREDACTEDEVENTS_HPP
