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

#include "jfr/jfrEvents.hpp"
#include "jfr/periodic/jfrRedactedEvents.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "logging/log.hpp"
#include "runtime/arguments.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/os.hpp"
#include "runtime/vm_version.hpp"
#include "services/diagnosticArgument.hpp"
#include "services/diagnosticFramework.hpp"
#include "services/management.hpp"
#include "utilities/ostream.hpp"

#include <ctype.h>
#include <stdio.h>
#include <string.h>

using StringArray = JfrRedactedEvents::StringArray;
using String = JfrRedactedEvents::String;
using StringFlag = JfrRedactedEvents::StringFlag;
using StringKeyValueArray = GrowableArray<JfrRedactedEvents::StringKeyValue*>*;

static const char REDACTED[] = "[REDACTED]";
static const char DELIMITER[] = "<DELIMITER>";
static const char REDACT_ARGUMENT_EQUAL[] = "redact-argument=";

static const size_t REDACTED_LENGTH = sizeof(REDACTED) -1;
static const size_t DELIMITER_LENGTH = sizeof(DELIMITER) -1;
static const size_t REDACT_ARGUMENT_EQUAL_LENGTH = sizeof(REDACT_ARGUMENT_EQUAL) -1;

String* JfrRedactedEvents::_redacted_java_command_line = nullptr;
String* JfrRedactedEvents::_redacted_jvm_command_line = nullptr;
String* JfrRedactedEvents::_redacted_flags_command_line = nullptr;
String* JfrRedactedEvents::_redacted_flight_recorder_options = nullptr;

StringKeyValueArray JfrRedactedEvents::_initial_environment_variables = nullptr;
StringKeyValueArray JfrRedactedEvents::_initial_system_properties = nullptr;
GrowableArray<StringFlag*>* JfrRedactedEvents::_string_flags = nullptr;

StringArray* JfrRedactedEvents::_redacted_arguments = nullptr;
StringArray* JfrRedactedEvents::_argument_filters = nullptr;
StringArray* JfrRedactedEvents::_key_filters = nullptr;

bool JfrRedactedEvents::_initialized = false;

void JfrRedactedEvents::destroy() {
  delete _redacted_java_command_line;
  _redacted_java_command_line = nullptr;

  delete _redacted_jvm_command_line;
  _redacted_jvm_command_line = nullptr;

  delete _redacted_flags_command_line;
  _redacted_flags_command_line = nullptr;

  delete _redacted_flight_recorder_options;
  _redacted_flight_recorder_options = nullptr;

  delete _redacted_arguments;
  _redacted_arguments = nullptr;

  delete _argument_filters;
  _argument_filters = nullptr;

  delete _key_filters;
  _key_filters = nullptr;

  destroy_array(_initial_system_properties);
  _initial_system_properties = nullptr;

  destroy_array(_initial_environment_variables);
  _initial_environment_variables = nullptr;

  destroy_array(_string_flags);
  _string_flags = nullptr;

  _initialized = false;
}

bool JfrRedactedEvents::set_argument_filter(const char* filters) {
  assert (_argument_filters == nullptr, "invariant");
  assert (filters != nullptr, "invariant");
  _argument_filters = new StringArray();
  return append_filters(_argument_filters, true, filters);
}

bool JfrRedactedEvents::set_key_filter(const char* filters) {
  assert (_key_filters == nullptr, "invariant");
  assert (filters != nullptr, "invariant");
  _key_filters = new StringArray();
  return append_filters(_key_filters, false, filters);
}

void JfrRedactedEvents::add_default_filters(StringArray* target, bool argument) {
  if (argument) {
    target->add("--*jaas*config<DELIMITER>*");
    target->add("--*password<DELIMITER>*");
    target->add("--*passwd<DELIMITER>*");
    target->add("--*pwd<DELIMITER>*");
    target->add("--*passphrase<DELIMITER>*");
    target->add("--*token<DELIMITER>*");
    target->add("--*secret<DELIMITER>*");
    target->add("--*credential<DELIMITER>*");
    target->add("--*api-key<DELIMITER>*");
    target->add("--*api_key<DELIMITER>*");
    target->add("--*apikey<DELIMITER>*");
    target->add("--*client-secret<DELIMITER>*");
    target->add("--*client_secret<DELIMITER>*");
    target->add("--*clientsecret<DELIMITER>*");
    target->add("--*private-key<DELIMITER>*");
    target->add("--*private_key<DELIMITER>*");
    target->add("--*privatekey<DELIMITER>*");
  } else {
    target->add("*auth*");
  }
  target->add("*jaas*config*");
  target->add("*password*");
  target->add("*passwd*");
  target->add("*pwd*");
  target->add("*passphrase*");
  target->add("*token*");
  target->add("*secret*");
  target->add("*credential*");
  target->add("*api-key*");
  target->add("*api_key*");
  target->add("*apikey*");
  target->add("*client-secret*");
  target->add("*client_secret*");
  target->add("*clientsecret*");
  target->add("*private-key*");
  target->add("*private_key*");
  target->add("*privatekey*");
}

bool JfrRedactedEvents::append_filters(StringArray* target, bool argument, const char* filters) {
  if (strlen(filters) == 0 || strcmp(filters, "none") == 0) {
    return true;
  }
  if (filters[0] == '+') {
    filters++;
    add_default_filters(target, argument);
  }
  StringArray* filter_array = split(filters, ';');
  bool success = true;
  for (int i = 0; i < filter_array->length(); i++) {
    const String* filter = filter_array->at(i);
    if (filter->at(0) == '@') {
      const char* filename = filter->text() + 1;
      if (!read_file(target, filename)) {
        success = false;
      }
    } else {
      target->add_contents(filter);
    }
  }
  delete filter_array;
  return success;
}

char* JfrRedactedEvents::new_redacted_text() {
  char* result = NEW_C_HEAP_ARRAY(char, REDACTED_LENGTH + 1, mtTracing);
  strncpy(result, REDACTED, REDACTED_LENGTH + 1);
  return result;
}

bool JfrRedactedEvents::emit_initial_environment_variables() {
  if (_initial_environment_variables == nullptr) {
    ensure_initialized();
    char** envp = os::get_environ();
    if (envp == nullptr) {
      return false;
    }
    _initial_environment_variables = make_array<StringKeyValue*>(0);
    for (char** p = envp; *p != nullptr; ++p) {
      char* variable = *p;
      char* equal_sign = strchr(variable, '=');
      if (equal_sign != nullptr) {
        ptrdiff_t length = equal_sign - variable;
        String* key = new String(variable, (size_t)length);
        const char* value = equal_sign + 1;
        if (is_redacted_key(key->text())) {
          value = REDACTED;
        }
        _initial_environment_variables->append(new StringKeyValue(key, value));
      }
    }
  }
  JfrTicks time_stamp = JfrTicks::now();
  for (int i = 0; i < _initial_environment_variables->length(); i++) {
    StringKeyValue* pair = _initial_environment_variables->at(i);
    EventInitialEnvironmentVariable event(UNTIMED);
    event.set_starttime(time_stamp);
    event.set_endtime(time_stamp);
    event.set_key(pair->key());
    event.set_value(pair->value());
    event.commit();
  }
  return true;
}

void JfrRedactedEvents::emit_initial_system_properties() {
  if (_initial_system_properties == nullptr) {
    ensure_initialized();
    _initial_system_properties = make_array<StringKeyValue*>(0);
    for (SystemProperty* p = Arguments::system_properties(); p != nullptr; p = p->next()) {
      if (!p->internal()) {
        const char* value = p->value();
        if (strcmp(p->key(), "sun.java.command") == 0) {
           value = String::c_str(_redacted_java_command_line);
        }
        if (is_redacted_key(p->key())) {
           value = REDACTED;
        }
        _initial_system_properties->append(new StringKeyValue(p->key(), value));
      }
    }
  }

  JfrTicks time_stamp = JfrTicks::now();
  for (int i = 0; i < _initial_system_properties->length(); i++) {
    StringKeyValue* pair = _initial_system_properties->at(i);
    EventInitialSystemProperty event(UNTIMED);
    event.set_key(pair->key());
    event.set_value(pair->value());
    event.set_starttime(time_stamp);
    event.set_endtime(time_stamp);
    event.commit();
  }
}

bool JfrRedactedEvents::match_flag(const char* flag_name, const char* arg) {
  if (flag_name == nullptr || arg == nullptr) {
    return false;
  }
  while (*flag_name) {
    if (*arg != *flag_name) {
        return false;
    }
    flag_name++;
    arg++;
  }
  return *arg == '\0' || *arg == '=';
}

void JfrRedactedEvents::emit_string_flags() {
  if (_string_flags == nullptr) {
    ensure_initialized();
   _string_flags = make_array<StringFlag*>(0);
   JVMFlag *flag = JVMFlag::flags;
     while (flag->name() != nullptr) {
       if (flag->is_ccstr() && flag->is_unlocked()) {
         const char* value = nullptr;
         for (int i = 0; i < _redacted_arguments->length(); i++) {
           if (match_flag(flag->name(), _redacted_arguments->at(i)->text())) {
             value = REDACTED;
             break;
           }
         }
         if (strcmp("FlightRecorderOptions", flag->name()) == 0) {
           if (_redacted_flight_recorder_options != nullptr) {
             value = _redacted_flight_recorder_options->text();
           }
         }
         _string_flags->append(new StringFlag(flag, value));
      }
      ++flag;
    }
  }
  for (int i = 0; i < _string_flags->length(); i++) {
    StringFlag* sf = _string_flags->at(i);
    const JVMFlag* flag = sf->jvm_flag();
    EventStringFlag event;
    event.set_name(flag->name());
    if (sf->redacted_value() != nullptr) {
      // If a flag is redacted and later changed at runtime,
      // the event will still show the redacted text.
      event.set_value(sf->redacted_value());
    } else {
      // If a flag is not redacted the first time,
      // it will not be redacted later if it's changed at runtime.
      event.set_value(flag->get_ccstr());
    }
    event.set_origin(static_cast<u8>(flag->get_origin()));
    event.commit();
  }
}

void JfrRedactedEvents::emit_jvm_information() {
  ensure_initialized();
  EventJVMInformation event;
  event.set_jvmName(VM_Version::vm_name());
  event.set_jvmVersion(VM_Version::internal_vm_info_string());
  event.set_javaArguments(String::c_str(_redacted_java_command_line));
  event.set_jvmArguments(String::c_str(_redacted_jvm_command_line));
  event.set_jvmFlags(String::c_str(_redacted_flags_command_line));
  event.set_jvmStartTime(Management::vm_init_done_time());
  event.set_pid(os::current_process_id());
  event.commit();
}

void JfrRedactedEvents::ensure_initialized() {
  if (_initialized) {
    return;
  }
  if (_key_filters == nullptr) {
      _key_filters =  new StringArray();
      add_default_filters(_key_filters, false);
  }
  if (_argument_filters == nullptr) {
      _argument_filters =  new StringArray();
      add_default_filters(_argument_filters, true);
  }
  if (FlightRecorderOptions != nullptr) {
    if (strstr(FlightRecorderOptions, REDACT_ARGUMENT_EQUAL) != nullptr) {
      DCmdIter iterator(FlightRecorderOptions, ',');
      stringStream result;
      size_t pos = 0;
      while(iterator.has_next()) {
        CmdLine line = iterator.next();
        const char* start = line.cmd_addr();
        if (strncmp(start, REDACT_ARGUMENT_EQUAL, REDACT_ARGUMENT_EQUAL_LENGTH) == 0) {
          result.print(REDACT_ARGUMENT_EQUAL);
          result.print(REDACTED);
          // Preserve ',' if there are more tokens
          pos = iterator.has_next() ? iterator.cursor() - 1 : iterator.cursor();
        }
        while (pos < iterator.cursor()) {
          result.write(FlightRecorderOptions + pos, 1);
          pos++;
        }
      }
      _redacted_flight_recorder_options = new String(result.base());
    } else {
      _redacted_flight_recorder_options = new String(FlightRecorderOptions);
    }
  }

  _redacted_arguments = new StringArray();

  StringArray* java_args = make_java_args_array();
  _redacted_java_command_line = redact_command_line(java_args);
  delete java_args;

  StringArray* jvm_args = make_jvm_args_array(Arguments::jvm_args_array(), Arguments::num_jvm_args());
  _redacted_jvm_command_line = redact_command_line(jvm_args);
  delete jvm_args;

  StringArray* flags_args = make_jvm_args_array(Arguments::jvm_flags_array(), Arguments::num_jvm_flags());
  _redacted_flags_command_line = redact_command_line(flags_args);
  delete flags_args;

  _initialized = true;
}

String* JfrRedactedEvents::redact_command_line(StringArray* arguments) {
  if (arguments == nullptr) {
    return nullptr;
  }
  GrowableArray<StringArray*>* filters = make_array<StringArray*>(_argument_filters->length());
  for (int i = 0 ; i < _argument_filters->length(); i++) {
    filters->append(make_filter_array(_argument_filters->at(i)->text()));
  }
  StringArray* result = new StringArray();
  int arg_index = 0;
  while (arg_index < arguments->length()) {
    int next_index = arg_index;
    for (int i = 0; i < filters->length(); i++) {
      next_index = match_arguments(filters->at(i), arguments, arg_index);
      if (next_index > arg_index) {
        break;
      }
    }
    if (next_index > arg_index) {
      for (int j = arg_index; j < next_index; j++) {
        result->add(REDACTED);
        const char* arg = arguments->at(j)->text();
        if (arg != nullptr && strncmp(arg, "-XX:", 4) == 0) {
          _redacted_arguments->add(arg + 4);
        }
      }
      arg_index = next_index;
    } else {
      result->add_contents(arguments->at(arg_index));
      arg_index++;
    }
  }
  destroy_array(filters);
  String* ret = concatenate(result);
  delete result;
  return ret;
}

String* JfrRedactedEvents::concatenate(StringArray* strings) {
  size_t length = 0;
  for (int i = 0; i < strings->length(); i++) {
    length += strings->at(i)->length();
    if (i != 0) {
      length += 1;
    }
  }
  String* text = new String(length);
  size_t index = 0;
  for (int i = 0; i < strings->length(); i++) {
    if (i != 0) {
      text->set(index++, ' ');
    }
    const String* s = strings->at(i);
    size_t len = s->length();
    for (size_t j = 0; j < len; j++) {
      text->set(index++, s->at(j));
    }
  }
  return text;
}

bool JfrRedactedEvents::equals_case_insensitive(char a, char b) {
  return tolower((unsigned char)a) == tolower((unsigned char)b);
}

bool JfrRedactedEvents::is_redacted_key(const char* key) {
  return match_key(_key_filters, key);
}

bool JfrRedactedEvents::is_separator(char c) {
  return c == ' ' || c == '\t' || c == '\n' || c == '\r';
}

StringArray* JfrRedactedEvents::make_filter_array(const char* filter) {
  StringArray* result = new StringArray();

  while (*filter) {
    const char* q = strstr(filter, DELIMITER);
    if (q == nullptr) {
      size_t len = strlen(filter);
      if (len > 0) {
        result->add(filter, len);
      }
      break;
    }
    size_t len = (size_t)(q - filter);
    if (len > 0) {
      result->add(filter, len);
    }
    filter = q + DELIMITER_LENGTH;
  }

  return result;
}

StringArray* JfrRedactedEvents::make_jvm_args_array(char** jvm_args_array, int array_length) {
  if (jvm_args_array == nullptr) {
    return nullptr;
  }
  StringArray* result = new StringArray(array_length);
  for(int i = 0; i < array_length; i++) {
    char* argument = jvm_args_array[i];
    if (_redacted_flight_recorder_options != nullptr &&
        strncmp(argument, "-XX:FlightRecorderOptions", 25) == 0) {
      const char* text = _redacted_flight_recorder_options->text();
      size_t length = _redacted_flight_recorder_options->length();
      // Length must be at least 26 or the JVM will not start.
      result->add(new String(argument, 26, text, length));
      continue;
    }
    if (strncmp(argument, "-D", 2) == 0) {
      const char* key_start = argument + 2;
      const char* eq = strchr(key_start, '=');
      if (eq != nullptr) {
        size_t key_length = (size_t)(eq - key_start);
        String* key_tmp = new String(key_start, key_length);
        bool redact = match_key(_key_filters, key_tmp->text());
        delete key_tmp;
        if (redact) {
          size_t unsensitive_length = (size_t)(eq - argument) + 1;
          result->add(new String(argument, unsensitive_length, REDACTED, REDACTED_LENGTH));
          continue;
        }
      }
    }
    result->add(argument);
  }
  return result;
}

StringArray* JfrRedactedEvents::make_java_args_array() {
  StringArray* array = new StringArray();
  const char* p = Arguments::java_command();
  if (p == nullptr) {
    return array;
  }
  const char* end = p + strlen(p);
  while (p < end) {
    while (p < end && is_separator(*p)) {
      p++;
    }
    if (p >= end) {
      break;
    }
    const char* start = p;
    while (p < end && !is_separator(*p)) {
      p++;
    }
    size_t length = (size_t)(p - start);
    if (length > 0) {
      array->add(start, length);
    }
  }
  return array;
}

bool JfrRedactedEvents::match(const char* filter, const char* text) {
  const char* filter_last = nullptr;
  const char* text_last = nullptr;
  while (*text != '\0') {
    if (*filter == '*') {
      filter_last = filter;
      text_last = text;
      filter++;
    } else if (*filter == '?' || equals_case_insensitive(*filter, *text)) {
      filter++;
      text++;
    } else if (filter_last != nullptr) {
      text_last++;
      text = text_last;
      filter = filter_last + 1;
    } else {
      return false;
    }
  }
  while (*filter == '*') {
    filter++;
  }
  return *filter == '\0';
}

int JfrRedactedEvents::match_arguments(StringArray* filter, StringArray* arguments, int arg_index) {
  int index = arg_index;
  if (arg_index + filter->length() <= arguments->length()) {
    for (int i = 0; i < filter->length(); i++) {
      if (!match(filter->at(i)->text(), arguments->at(index)->text())) {
        return arg_index;
      }
      index++;
    }
  }
  return index;
}

bool JfrRedactedEvents::match_key(StringArray* filters, const char* text) {
  for (int i = 0; i < filters->length(); i++) {
     const char* filter = filters->at(i)->text();
    if (match(filter, text)) {
      return true;
    }
  }
  return false;
}


bool JfrRedactedEvents::read_file(StringArray* target, const char* filename) {
  FILE* file = os::fopen(filename, "r");
  if (file == nullptr) {
    log_error(jfr, redact)("Failed to open redaction file: %s", filename);
    return false;
  }

  stringStream ss;
  while (true) {
    const int c = fgetc(file);
    if (c == EOF && ferror(file) != 0) {
      log_error(jfr, redact)("Error reading redaction file: %s", filename);
      fclose(file);
      return false;
    }
    if (c == EOF || c == '\n') {
      const char* line = ss.base();
      size_t end = ss.size();
      while (end > 0 && is_separator(line[end - 1])) {
        end--;
      }
      if (end > 0) {
        target->add(line, end);
      }
      ss.reset();
      if (c == EOF) {
        break;
      }
    } else {
      ss.put((char)c);
    }
  }
  fclose(file);
  log_debug(jfr, redact)("Redaction file %s read successfully", filename);
  return true;
}

StringArray* JfrRedactedEvents::split(const char* text, char separator) {
  const char* last = text;
  StringArray* result = new StringArray();
  for (const char* position = text; *position != '\0'; position++) {
    if (*position == separator) {
      if (position > last) {
        result->add(last, (size_t)(position - last));
      }
      last = position + 1;
    }
  }
  if (*last != '\0') {
    result->add(last, strlen(last));
  }
  return result;
}
