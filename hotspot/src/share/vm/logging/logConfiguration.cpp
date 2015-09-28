/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/globalDefinitions.hpp"

LogOutput** LogConfiguration::_outputs = NULL;
size_t      LogConfiguration::_n_outputs = 0;

void LogConfiguration::post_initialize() {
  assert(LogConfiguration_lock != NULL, "Lock must be initialized before post-initialization");
  LogDiagnosticCommand::registerCommand();
  LogHandle(logging) log;
  log.info("Log configuration fully initialized.");
  if (log.is_trace()) {
    ResourceMark rm;
    MutexLocker ml(LogConfiguration_lock);
    describe(log.trace_stream());
  }
}

void LogConfiguration::initialize(jlong vm_start_time) {
  LogFileOutput::set_file_name_parameters(vm_start_time);
  LogDecorations::set_vm_start_time_millis(vm_start_time);

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

LogOutput* LogConfiguration::new_output(char* name, const char* options) {
  const char* type;
  char* equals_pos = strchr(name, '=');
  if (equals_pos == NULL) {
    type = "file";
  } else {
    *equals_pos = '\0';
    type = name;
    name = equals_pos + 1;
  }

  LogOutput* output;
  if (strcmp(type, "file") == 0) {
    output = new LogFileOutput(name);
  } else {
    // unsupported log output type
    return NULL;
  }

  bool success = output->initialize(options);
  if (!success) {
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
         err_msg("idx must be in range 1 < idx < _n_outputs, but idx = " SIZE_FORMAT
                 " and _n_outputs = " SIZE_FORMAT, idx, _n_outputs));
  LogOutput* output = _outputs[idx];
  // Swap places with the last output and shrink the array
  _outputs[idx] = _outputs[--_n_outputs];
  _outputs = REALLOC_C_HEAP_ARRAY(LogOutput*, _outputs, _n_outputs, mtLogging);
  delete output;
}

void LogConfiguration::configure_output(size_t idx, const LogTagLevelExpression& tag_level_expression, const LogDecorators& decorators) {
  assert(idx < _n_outputs, err_msg("Invalid index, idx = " SIZE_FORMAT " and _n_outputs = " SIZE_FORMAT, idx, _n_outputs));
  LogOutput* output = _outputs[idx];
  output->set_decorators(decorators);
  output->set_config_string(tag_level_expression.to_string());
  bool enabled = false;
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    LogLevelType level = tag_level_expression.level_for(*ts);
    if (level != LogLevel::Off) {
      enabled = true;
    }
    ts->update_decorators(decorators);
    ts->set_output_level(output, level);
  }

  // If the output is not used by any tagset it should be removed, unless it is stdout/stderr.
  if (!enabled && idx > 1) {
    delete_output(idx);
  }
}

void LogConfiguration::disable_output(size_t idx) {
  LogOutput* out = _outputs[idx];
  LogDecorators empty_decorators;
  empty_decorators.clear();

  // Remove the output from all tagsets.
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    ts->set_output_level(out, LogLevel::Off);
    ts->update_decorators(empty_decorators);
  }

  // Delete the output unless stdout/stderr
  if (out != LogOutput::Stderr && out != LogOutput::Stdout) {
    delete_output(find_output(out->name()));
  } else {
    out->set_config_string("all=off");
  }
}

void LogConfiguration::disable_logging() {
  assert(LogConfiguration_lock == NULL || LogConfiguration_lock->owned_by_self(),
         "LogConfiguration lock must be held when calling this function");
  for (size_t i = 0; i < _n_outputs; i++) {
    disable_output(i);
  }
}

bool LogConfiguration::parse_command_line_arguments(const char* opts) {
  char* copy = os::strdup_check_oom(opts, mtLogging);

  // Split the option string to its colon separated components.
  char* what = NULL;
  char* output_str = NULL;
  char* decorators_str = NULL;
  char* output_options = NULL;

  what = copy;
  char* colon = strchr(what, ':');
  if (colon != NULL) {
    *colon = '\0';
    output_str = colon + 1;
    colon = strchr(output_str, ':');
    if (colon != NULL) {
      *colon = '\0';
      decorators_str = colon + 1;
      colon = strchr(decorators_str, ':');
      if (colon != NULL) {
        *colon = '\0';
        output_options = colon + 1;
      }
    }
  }

  // Parse each argument
  char errbuf[512];
  stringStream ss(errbuf, sizeof(errbuf));
  bool success = parse_log_arguments(output_str, what, decorators_str, output_options, &ss);
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
  assert(LogConfiguration_lock == NULL || LogConfiguration_lock->owned_by_self(),
         "LogConfiguration lock must be held when calling this function");
  if (outputstr == NULL || strlen(outputstr) == 0) {
    outputstr = "stdout";
  }

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
      LogOutput* output = new_output(tmp, output_options);
      os::free(tmp);
      if (output == NULL) {
        errstream->print("Unable to add output '%s'", outputstr);
        if (output_options != NULL && strlen(output_options) > 0) {
          errstream->print(" with options '%s'", output_options);
        }
        errstream->cr();
        return false;
      }
      idx = add_output(output);
    } else if (output_options != NULL && strlen(output_options) > 0) {
      errstream->print_cr("Output options for existing outputs are ignored.");
    }
  }

  LogTagLevelExpression expr;
  if (!expr.parse(what, errstream)) {
    return false;
  }

  LogDecorators decorators;
  if (!decorators.parse(decoratorstr, errstream)) {
    return false;
  }

  configure_output(idx, expr, decorators);
  return true;
}

void LogConfiguration::describe(outputStream* out) {
  assert(LogConfiguration_lock == NULL || LogConfiguration_lock->owned_by_self(),
         "LogConfiguration lock must be held when calling this function");

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
