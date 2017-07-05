/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "precompiled.hpp"
#include "logging/log.hpp"
#include "logging/logConfiguration.hpp"
#include "logging/logDecorations.hpp"
#include "logging/logDecorators.hpp"
#include "logging/logDiagnosticCommand.hpp"
#include "logging/logFileOutput.hpp"
#include "logging/logOutput.hpp"
#include "logging/logTagLevelExpression.hpp"
#include "logging/logTagSet.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/semaphore.hpp"
#include "utilities/globalDefinitions.hpp"

LogOutput** LogConfiguration::_outputs = NULL;
size_t      LogConfiguration::_n_outputs = 0;

LogConfiguration::UpdateListenerFunction* LogConfiguration::_listener_callbacks = NULL;
size_t      LogConfiguration::_n_listener_callbacks = 0;

// Stack object to take the lock for configuring the logging.
// Should only be held during the critical parts of the configuration
// (when calling configure_output or reading/modifying the outputs array).
// Thread must never block when holding this lock.
class ConfigurationLock : public StackObj {
 private:
  // Semaphore used as lock
  static Semaphore _semaphore;
  debug_only(static intx _locking_thread_id;)
 public:
  ConfigurationLock() {
    _semaphore.wait();
    debug_only(_locking_thread_id = os::current_thread_id());
  }
  ~ConfigurationLock() {
    debug_only(_locking_thread_id = -1);
    _semaphore.signal();
  }
  debug_only(static bool current_thread_has_lock();)
};

Semaphore ConfigurationLock::_semaphore(1);
#ifdef ASSERT
intx ConfigurationLock::_locking_thread_id = -1;
bool ConfigurationLock::current_thread_has_lock() {
  return _locking_thread_id == os::current_thread_id();
}
#endif

void LogConfiguration::post_initialize() {
  LogDiagnosticCommand::registerCommand();
  Log(logging) log;
  log.info("Log configuration fully initialized.");
  log_develop_info(logging)("Develop logging is available.");
  if (log.is_trace()) {
    ResourceMark rm;
    describe(log.trace_stream());
  }
}

void LogConfiguration::initialize(jlong vm_start_time) {
  LogFileOutput::set_file_name_parameters(vm_start_time);
  LogDecorations::initialize(vm_start_time);
  assert(_outputs == NULL, "Should not initialize _outputs before this function, initialize called twice?");
  _outputs = NEW_C_HEAP_ARRAY(LogOutput*, 2, mtLogging);
  _outputs[0] = LogOutput::Stdout;
  _outputs[1] = LogOutput::Stderr;
  _n_outputs = 2;
}

void LogConfiguration::finalize() {
  for (size_t i = 2; i < _n_outputs; i++) {
    delete _outputs[i];
  }
  FREE_C_HEAP_ARRAY(LogOutput*, _outputs);
}

size_t LogConfiguration::find_output(const char* name) {
  for (size_t i = 0; i < _n_outputs; i++) {
    if (strcmp(_outputs[i]->name(), name) == 0) {
      return i;
    }
  }
  return SIZE_MAX;
}

LogOutput* LogConfiguration::new_output(char* name, const char* options, outputStream* errstream) {
  const char* type;
  char* equals_pos = strchr(name, '=');
  if (equals_pos == NULL) {
    type = "file";
  } else {
    *equals_pos = '\0';
    type = name;
    name = equals_pos + 1;
  }

  // Check if name is quoted, and if so, strip the quotes
  char* quote = strchr(name, '"');
  if (quote != NULL) {
    char* end_quote = strchr(name + 1, '"');
    if (end_quote == NULL) {
      errstream->print_cr("Output name has opening quote but is missing a terminating quote.");
      return NULL;
    } else if (quote != name || end_quote[1] != '\0') {
      errstream->print_cr("Output name can not be partially quoted."
                          " Either surround the whole name with quotation marks,"
                          " or do not use quotation marks at all.");
      return NULL;
    }
    name++;
    *end_quote = '\0';
  }

  LogOutput* output;
  if (strcmp(type, "file") == 0) {
    output = new LogFileOutput(name);
  } else {
    errstream->print_cr("Unsupported log output type.");
    return NULL;
  }

  bool success = output->initialize(options, errstream);
  if (!success) {
    errstream->print_cr("Initialization of output '%s' using options '%s' failed.", name, options);
    delete output;
    return NULL;
  }
  return output;
}

size_t LogConfiguration::add_output(LogOutput* output) {
  size_t idx = _n_outputs++;
  _outputs = REALLOC_C_HEAP_ARRAY(LogOutput*, _outputs, _n_outputs, mtLogging);
  _outputs[idx] = output;
  return idx;
}

void LogConfiguration::delete_output(size_t idx) {
  assert(idx > 1 && idx < _n_outputs,
         "idx must be in range 1 < idx < _n_outputs, but idx = " SIZE_FORMAT
         " and _n_outputs = " SIZE_FORMAT, idx, _n_outputs);
  LogOutput* output = _outputs[idx];
  // Swap places with the last output and shrink the array
  _outputs[idx] = _outputs[--_n_outputs];
  _outputs = REALLOC_C_HEAP_ARRAY(LogOutput*, _outputs, _n_outputs, mtLogging);
  delete output;
}

void LogConfiguration::configure_output(size_t idx, const LogTagLevelExpression& tag_level_expression, const LogDecorators& decorators) {
  assert(ConfigurationLock::current_thread_has_lock(), "Must hold configuration lock to call this function.");
  assert(idx < _n_outputs, "Invalid index, idx = " SIZE_FORMAT " and _n_outputs = " SIZE_FORMAT, idx, _n_outputs);
  LogOutput* output = _outputs[idx];

  // Clear the previous config description
  output->clear_config_string();

  bool enabled = false;
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    LogLevelType level = tag_level_expression.level_for(*ts);

    // Ignore tagsets that do not, and will not log on the output
    if (!ts->has_output(output) && (level == LogLevel::NotMentioned || level == LogLevel::Off)) {
      continue;
    }

    // Update decorators before adding/updating output level,
    // so that the tagset will have the necessary decorators when requiring them.
    if (level != LogLevel::Off) {
      ts->update_decorators(decorators);
    }

    // Set the new level, if it changed
    if (level != LogLevel::NotMentioned) {
      ts->set_output_level(output, level);
    }

    if (level != LogLevel::Off) {
      // Keep track of whether or not the output is ever used by some tagset
      enabled = true;

      if (level == LogLevel::NotMentioned) {
        // Look up the previously set level for this output on this tagset
        level = ts->level_for(output);
      }

      // Update the config description with this tagset and level
      output->add_to_config_string(ts, level);
    }
  }

  // It is now safe to set the new decorators for the actual output
  output->set_decorators(decorators);

  // Update the decorators on all tagsets to get rid of unused decorators
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    ts->update_decorators();
  }

  if (enabled) {
    assert(strlen(output->config_string()) > 0,
           "Should always have a config description if the output is enabled.");
  } else if (idx > 1) {
    // Output is unused and should be removed.
    delete_output(idx);
  } else {
    // Output is either stdout or stderr, which means we can't remove it.
    // Update the config description to reflect that the output is disabled.
    output->set_config_string("all=off");
  }
}

void LogConfiguration::disable_output(size_t idx) {
  LogOutput* out = _outputs[idx];

  // Remove the output from all tagsets.
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    ts->set_output_level(out, LogLevel::Off);
    ts->update_decorators();
  }

  // Delete the output unless stdout/stderr
  if (out != LogOutput::Stderr && out != LogOutput::Stdout) {
    delete_output(find_output(out->name()));
  } else {
    out->set_config_string("all=off");
  }
}

void LogConfiguration::disable_logging() {
  ConfigurationLock cl;
  for (size_t i = 0; i < _n_outputs; i++) {
    disable_output(i);
  }
  notify_update_listeners();
}

void LogConfiguration::configure_stdout(LogLevelType level, bool exact_match, ...) {
  size_t i;
  va_list ap;
  LogTagLevelExpression expr;
  va_start(ap, exact_match);
  for (i = 0; i < LogTag::MaxTags; i++) {
    LogTagType tag = static_cast<LogTagType>(va_arg(ap, int));
    expr.add_tag(tag);
    if (tag == LogTag::__NO_TAG) {
      assert(i > 0, "Must specify at least one tag!");
      break;
    }
  }
  assert(i < LogTag::MaxTags || static_cast<LogTagType>(va_arg(ap, int)) == LogTag::__NO_TAG,
         "Too many tags specified! Can only have up to " SIZE_FORMAT " tags in a tag set.", LogTag::MaxTags);
  va_end(ap);

  if (!exact_match) {
    expr.set_allow_other_tags();
  }
  expr.set_level(level);
  expr.new_combination();

  // Apply configuration to stdout (output #0), with the same decorators as before.
  ConfigurationLock cl;
  configure_output(0, expr, LogOutput::Stdout->decorators());
  notify_update_listeners();
}

bool LogConfiguration::parse_command_line_arguments(const char* opts) {
  char* copy = os::strdup_check_oom(opts, mtLogging);

  // Split the option string to its colon separated components.
  char* str = copy;
  char* substrings[4] = {0};
  for (int i = 0 ; i < 4; i++) {
    substrings[i] = str;

    // Find the next colon or quote
    char* next = strpbrk(str, ":\"");
    while (next != NULL && *next == '"') {
      char* end_quote = strchr(next + 1, '"');
      if (end_quote == NULL) {
        log_error(logging)("Missing terminating quote in -Xlog option '%s'", str);
        os::free(copy);
        return false;
      }
      // Keep searching after the quoted substring
      next = strpbrk(end_quote + 1, ":\"");
    }

    if (next != NULL) {
      *next = '\0';
      str = next + 1;
    } else {
      break;
    }
  }

  // Parse and apply the separated configuration options
  char* what = substrings[0];
  char* output = substrings[1];
  char* decorators = substrings[2];
  char* output_options = substrings[3];
  char errbuf[512];
  stringStream ss(errbuf, sizeof(errbuf));
  bool success = parse_log_arguments(output, what, decorators, output_options, &ss);
  if (!success) {
    errbuf[strlen(errbuf) - 1] = '\0'; // Strip trailing newline.
    log_error(logging)("%s", errbuf);
  }

  os::free(copy);
  return success;
}

bool LogConfiguration::parse_log_arguments(const char* outputstr,
                                           const char* what,
                                           const char* decoratorstr,
                                           const char* output_options,
                                           outputStream* errstream) {
  if (outputstr == NULL || strlen(outputstr) == 0) {
    outputstr = "stdout";
  }

  LogTagLevelExpression expr;
  if (!expr.parse(what, errstream)) {
    return false;
  }

  LogDecorators decorators;
  if (!decorators.parse(decoratorstr, errstream)) {
    return false;
  }

  ConfigurationLock cl;
  size_t idx;
  if (outputstr[0] == '#') {
    int ret = sscanf(outputstr+1, SIZE_FORMAT, &idx);
    if (ret != 1 || idx >= _n_outputs) {
      errstream->print_cr("Invalid output index '%s'", outputstr);
      return false;
    }
  } else {
    idx = find_output(outputstr);
    if (idx == SIZE_MAX) {
      char* tmp = os::strdup_check_oom(outputstr, mtLogging);
      LogOutput* output = new_output(tmp, output_options, errstream);
      os::free(tmp);
      if (output == NULL) {
        return false;
      }
      idx = add_output(output);
    } else if (output_options != NULL && strlen(output_options) > 0) {
      errstream->print_cr("Output options for existing outputs are ignored.");
    }
  }
  configure_output(idx, expr, decorators);
  notify_update_listeners();
  return true;
}

void LogConfiguration::describe(outputStream* out) {
  out->print("Available log levels:");
  for (size_t i = 0; i < LogLevel::Count; i++) {
    out->print("%s %s", (i == 0 ? "" : ","), LogLevel::name(static_cast<LogLevelType>(i)));
  }
  out->cr();

  out->print("Available log decorators:");
  for (size_t i = 0; i < LogDecorators::Count; i++) {
    LogDecorators::Decorator d = static_cast<LogDecorators::Decorator>(i);
    out->print("%s %s (%s)", (i == 0 ? "" : ","), LogDecorators::name(d), LogDecorators::abbreviation(d));
  }
  out->cr();

  out->print("Available log tags:");
  for (size_t i = 1; i < LogTag::Count; i++) {
    out->print("%s %s", (i == 1 ? "" : ","), LogTag::name(static_cast<LogTagType>(i)));
  }
  out->cr();

  ConfigurationLock cl;
  out->print_cr("Log output configuration:");
  for (size_t i = 0; i < _n_outputs; i++) {
    out->print("#" SIZE_FORMAT ": %s %s ", i, _outputs[i]->name(), _outputs[i]->config_string());
    for (size_t d = 0; d < LogDecorators::Count; d++) {
      LogDecorators::Decorator decorator = static_cast<LogDecorators::Decorator>(d);
      if (_outputs[i]->decorators().is_decorator(decorator)) {
        out->print("%s,", LogDecorators::name(decorator));
      }
    }
    out->cr();
  }
}

void LogConfiguration::print_command_line_help(FILE* out) {
  jio_fprintf(out, "-Xlog Usage: -Xlog[:[what][:[output][:[decorators][:output-options]]]]\n"
              "\t where 'what' is a combination of tags and levels on the form tag1[+tag2...][*][=level][,...]\n"
              "\t Unless wildcard (*) is specified, only log messages tagged with exactly the tags specified will be matched.\n\n");

  jio_fprintf(out, "Available log levels:\n");
  for (size_t i = 0; i < LogLevel::Count; i++) {
    jio_fprintf(out, "%s %s", (i == 0 ? "" : ","), LogLevel::name(static_cast<LogLevelType>(i)));
  }

  jio_fprintf(out, "\n\nAvailable log decorators: \n");
  for (size_t i = 0; i < LogDecorators::Count; i++) {
    LogDecorators::Decorator d = static_cast<LogDecorators::Decorator>(i);
    jio_fprintf(out, "%s %s (%s)", (i == 0 ? "" : ","), LogDecorators::name(d), LogDecorators::abbreviation(d));
  }
  jio_fprintf(out, "\n Decorators can also be specified as 'none' for no decoration.\n\n");

  jio_fprintf(out, "Available log tags:\n");
  for (size_t i = 1; i < LogTag::Count; i++) {
    jio_fprintf(out, "%s %s", (i == 1 ? "" : ","), LogTag::name(static_cast<LogTagType>(i)));
  }
  jio_fprintf(out, "\n Specifying 'all' instead of a tag combination matches all tag combinations.\n\n");

  jio_fprintf(out, "Available log outputs:\n"
              " stdout, stderr, file=<filename>\n"
              " Specifying %%p and/or %%t in the filename will expand to the JVM's PID and startup timestamp, respectively.\n\n"

              "Some examples:\n"
              " -Xlog\n"
              "\t Log all messages using 'info' level to stdout with 'uptime', 'levels' and 'tags' decorations.\n"
              "\t (Equivalent to -Xlog:all=info:stdout:uptime,levels,tags).\n\n"

              " -Xlog:gc\n"
              "\t Log messages tagged with 'gc' tag using 'info' level to stdout, with default decorations.\n\n"

              " -Xlog:gc=debug:file=gc.txt:none\n"
              "\t Log messages tagged with 'gc' tag using 'debug' level to file 'gc.txt' with no decorations.\n\n"

              " -Xlog:gc=trace:file=gctrace.txt:uptimemillis,pids:filecount=5,filesize=1024\n"
              "\t Log messages tagged with 'gc' tag using 'trace' level to a rotating fileset of 5 files of size 1MB,\n"
              "\t using the base name 'gctrace.txt', with 'uptimemillis' and 'pid' decorations.\n\n"

              " -Xlog:gc::uptime,tid\n"
              "\t Log messages tagged with 'gc' tag using 'info' level to output 'stdout', using 'uptime' and 'tid' decorations.\n\n"

              " -Xlog:gc*=info,rt*=off\n"
              "\t Log messages tagged with at least 'gc' using 'info' level, but turn off logging of messages tagged with 'rt'.\n"
              "\t (Messages tagged with both 'gc' and 'rt' will not be logged.)\n\n"

              " -Xlog:disable -Xlog:rt=trace:rttrace.txt\n"
              "\t Turn off all logging, including warnings and errors,\n"
              "\t and then enable messages tagged with 'rt' using 'trace' level to file 'rttrace.txt'.\n");
}

void LogConfiguration::rotate_all_outputs() {
  // Start from index 2 since neither stdout nor stderr can be rotated.
  for (size_t idx = 2; idx < _n_outputs; idx++) {
    _outputs[idx]->force_rotate();
  }
}

void LogConfiguration::register_update_listener(UpdateListenerFunction cb) {
  assert(cb != NULL, "Should not register NULL as listener");
  ConfigurationLock cl;
  size_t idx = _n_listener_callbacks++;
  _listener_callbacks = REALLOC_C_HEAP_ARRAY(UpdateListenerFunction,
                                             _listener_callbacks,
                                             _n_listener_callbacks,
                                             mtLogging);
  _listener_callbacks[idx] = cb;
}

void LogConfiguration::notify_update_listeners() {
  assert(ConfigurationLock::current_thread_has_lock(), "notify_update_listeners must be called in ConfigurationLock scope (lock held)");
  for (size_t i = 0; i < _n_listener_callbacks; i++) {
    _listener_callbacks[i]();
  }
}
