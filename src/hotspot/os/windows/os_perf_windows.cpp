/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "iphlp_interface.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "pdh_interface.hpp"
#include "runtime/os_perf.hpp"
#include "runtime/os.hpp"
#include "utilities/macros.hpp"
#include "vm_version_ext_x86.hpp"
#include <math.h>
#include <psapi.h>
#include <TlHelp32.h>

/*
 * Windows provides a vast plethora of performance objects and counters,
 * consumption of which is assisted using the Performance Data Helper (PDH) interface.
 * We import a selected few api entry points from PDH, see pdh_interface.hpp.
 *
 * The code located in this file is to a large extent an abstraction over much of the
 * plumbing needed to start consuming an object and/or counter of choice.
 *
 */

 /*
 * How to use:
 * 1. Create query
 * 2. Add counters to the query
 * 3. Collect the performance data using the query
 * 4. Display the performance data using the counters associated with the query
 * 5. Destroy query (counter destruction implied)
 */

/*
 * Every PDH artifact, like processor, process, thread, memory, and so forth are
 * identified with an index that is always the same irrespective
 * of the localized version of the operating system or service pack installed.
 * INFO: Using PDH APIs Correctly in a Localized Language (Q287159)
 *   http://support.microsoft.com/default.aspx?scid=kb;EN-US;q287159
 *
 * To find the correct index for an object or counter, inspect the registry key / value:
 * [HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Perflib\009\Counter]
 *
 * some common PDH indexes
 */
static const DWORD PDH_PROCESSOR_IDX = 238;
static const DWORD PDH_PROCESSOR_TIME_IDX = 6;
static const DWORD PDH_PRIV_PROCESSOR_TIME_IDX = 144;
static const DWORD PDH_PROCESS_IDX = 230;
static const DWORD PDH_ID_PROCESS_IDX = 784;
static const DWORD PDH_CONTEXT_SWITCH_RATE_IDX = 146;
static const DWORD PDH_SYSTEM_IDX = 2;

/* useful pdh fmt's */
static const char* const OBJECT_COUNTER_FMT = "\\%s\\%s";
static const size_t OBJECT_COUNTER_FMT_LEN = 2;
static const char* const OBJECT_WITH_INSTANCES_COUNTER_FMT = "\\%s(%s)\\%s";
static const size_t OBJECT_WITH_INSTANCES_COUNTER_FMT_LEN = 4;
static const char* const PROCESS_OBJECT_INSTANCE_COUNTER_FMT = "\\%s(%s#%s)\\%s";
static const size_t PROCESS_OBJECT_INSTANCE_COUNTER_FMT_LEN = 5;

static const char* process_image_name = NULL; // for example, "java" but could have another image name
static char* pdh_IDProcess_counter_fmt = NULL;   // "\Process(java#%d)\ID Process" */

// Need to limit how often we update a query to minimize the heisenberg effect.
// (PDH behaves erratically if the counters are queried too often, especially counters that
// store and use values from two consecutive updates, like cpu load.)
static const int min_update_interval_millis = 500;

/*
* Structs for PDH queries.
*/
typedef struct {
  HQUERY query;
  s8     lastUpdate; // Last time query was updated (current millis).
} UpdateQueryS, *UpdateQueryP;


typedef struct {
  UpdateQueryS query;
  HCOUNTER     counter;
  bool         initialized;
} CounterQueryS, *CounterQueryP;

typedef struct {
  UpdateQueryS query;
  HCOUNTER*    counters;
  int          noOfCounters;
  bool         initialized;
} MultiCounterQueryS, *MultiCounterQueryP;

typedef struct {
  MultiCounterQueryP queries;
  int                size;
  bool               initialized;
} MultiCounterQuerySetS, *MultiCounterQuerySetP;

typedef struct {
  MultiCounterQuerySetS set;
  int                   process_index;
} ProcessQueryS, *ProcessQueryP;

static void pdh_cleanup(HQUERY* const query, HCOUNTER* const counter) {
  if (counter != NULL && *counter != NULL) {
    PdhDll::PdhRemoveCounter(*counter);
    *counter = NULL;
  }
  if (query != NULL && *query != NULL) {
    PdhDll::PdhCloseQuery(*query);
    *query = NULL;
  }
}

static CounterQueryP create_counter_query() {
  CounterQueryP const query = NEW_C_HEAP_ARRAY(CounterQueryS, 1, mtInternal);
  memset(query, 0, sizeof(CounterQueryS));
  return query;
}

static void destroy_counter_query(CounterQueryP query) {
  assert(query != NULL, "invariant");
  pdh_cleanup(&query->query.query, &query->counter);
  FREE_C_HEAP_ARRAY(CounterQueryS, query);
}

static MultiCounterQueryP create_multi_counter_query() {
  MultiCounterQueryP const query = NEW_C_HEAP_ARRAY(MultiCounterQueryS, 1, mtInternal);
  memset(query, 0, sizeof(MultiCounterQueryS));
  return query;
}

static void destroy_counter_query(MultiCounterQueryP counter_query) {
  if (counter_query != NULL) {
    for (int i = 0; i < counter_query->noOfCounters; ++i) {
      pdh_cleanup(NULL, &counter_query->counters[i]);
    }
    FREE_C_HEAP_ARRAY(char, counter_query->counters);
    pdh_cleanup(&counter_query->query.query, NULL);
    FREE_C_HEAP_ARRAY(MultiCounterQueryS, counter_query);
  }
}

static void destroy_multi_counter_query(MultiCounterQuerySetP counter_query_set) {
  for (int i = 0; i < counter_query_set->size; i++) {
    for (int j = 0; j < counter_query_set->queries[i].noOfCounters; ++j) {
      pdh_cleanup(NULL, &counter_query_set->queries[i].counters[j]);
    }
    FREE_C_HEAP_ARRAY(char, counter_query_set->queries[i].counters);
    pdh_cleanup(&counter_query_set->queries[i].query.query, NULL);
  }
  FREE_C_HEAP_ARRAY(MultiCounterQueryS, counter_query_set->queries);
}

static void destroy_counter_query(MultiCounterQuerySetP counter_query_set) {
  destroy_multi_counter_query(counter_query_set);
  FREE_C_HEAP_ARRAY(MultiCounterQuerySetS, counter_query_set);
}

static void destroy_counter_query(ProcessQueryP process_query) {
  destroy_multi_counter_query(&process_query->set);
  FREE_C_HEAP_ARRAY(ProcessQueryS, process_query);
}

static int open_query(HQUERY* query) {
  return PdhDll::PdhOpenQuery(NULL, 0, query);
}

template <typename QueryP>
static int open_query(QueryP query) {
  return open_query(&query->query);
}

static int allocate_counters(MultiCounterQueryP query, size_t nofCounters) {
  assert(query != NULL, "invariant");
  assert(!query->initialized, "invariant");
  assert(0 == query->noOfCounters, "invariant");
  assert(query->counters == NULL, "invariant");
  query->counters = (HCOUNTER*)NEW_C_HEAP_ARRAY(char, nofCounters * sizeof(HCOUNTER), mtInternal);
  if (query->counters == NULL) {
    return OS_ERR;
  }
  memset(query->counters, 0, nofCounters * sizeof(HCOUNTER));
  query->noOfCounters = (int)nofCounters;
  return OS_OK;
}

static int allocate_counters(MultiCounterQuerySetP query_set, size_t nofCounters) {
  assert(query_set != NULL, "invariant");
  assert(!query_set->initialized, "invariant");
  for (int i = 0; i < query_set->size; ++i) {
    if (allocate_counters(&query_set->queries[i], nofCounters) != OS_OK) {
      return OS_ERR;
    }
  }
  return OS_OK;
}

static int allocate_counters(ProcessQueryP process_query, size_t nofCounters) {
  assert(process_query != NULL, "invariant");
  return allocate_counters(&process_query->set, nofCounters);
}

static void deallocate_counters(MultiCounterQueryP query) {
  if (query->counters != NULL) {
    FREE_C_HEAP_ARRAY(char, query->counters);
    query->counters = NULL;
    query->noOfCounters = 0;
  }
}

static OSReturn add_counter(UpdateQueryP query, HCOUNTER* counter, const char* path, bool first_sample_on_init) {
  assert(query != NULL, "invariant");
  assert(counter != NULL, "invariant");
  assert(path != NULL, "invariant");
  if (query->query == NULL) {
    if (open_query(query) != ERROR_SUCCESS) {
      return OS_ERR;
    }
  }
  assert(query->query != NULL, "invariant");
  PDH_STATUS status = PdhDll::PdhAddCounter(query->query, path, 0, counter);
  if (PDH_CSTATUS_NO_OBJECT == status || PDH_CSTATUS_NO_COUNTER == status) {
    return OS_ERR;
  }
  /*
  * According to the MSDN documentation, rate counters must be read twice:
  *
  * "Obtaining the value of rate counters such as Page faults/sec requires that
  *  PdhCollectQueryData be called twice, with a specific time interval between
  *  the two calls, before calling PdhGetFormattedCounterValue. Call Sleep to
  *  implement the waiting period between the two calls to PdhCollectQueryData."
  *
  *  Take the first sample here already to allow for the next "real" sample
  *  to succeed.
  */
  if (first_sample_on_init) {
    PdhDll::PdhCollectQueryData(query->query);
  }
  return OS_OK;
}

template <typename QueryP>
static OSReturn add_counter(QueryP counter_query, HCOUNTER* counter, const char* path, bool first_sample_on_init) {
  assert(counter_query != NULL, "invariant");
  assert(counter != NULL, "invariant");
  assert(path != NULL, "invariant");
  return add_counter(&counter_query->query, counter, path, first_sample_on_init);
}

static OSReturn add_counter(CounterQueryP counter_query, const char* path, bool first_sample_on_init) {
  if (add_counter(counter_query, &counter_query->counter, path, first_sample_on_init) != OS_OK) {
    // performance counter might be disabled in the registry
    return OS_ERR;
  }
  counter_query->initialized = true;
  return OS_OK;
}

static OSReturn add_process_counter(MultiCounterQueryP query, int slot_index, const char* path, bool first_sample_on_init) {
  assert(query != NULL, "invariant");
  assert(slot_index < query->noOfCounters, "invariant");
  assert(query->counters[slot_index] == NULL, "invariant");
  const OSReturn ret = add_counter(query, &query->counters[slot_index], path, first_sample_on_init);
  if (OS_OK == ret) {
    if (slot_index + 1 == query->noOfCounters) {
      query->initialized = true;
    }
  }
  return ret;
}

static int collect_query_data(UpdateQueryP update_query) {
  assert(update_query != NULL, "invariant");
  const s8 now = os::javaTimeMillis();
  if (now - update_query->lastUpdate > min_update_interval_millis) {
    if (PdhDll::PdhCollectQueryData(update_query->query) != ERROR_SUCCESS) {
      return OS_ERR;
    }
    update_query->lastUpdate = now;
  }
  return OS_OK;
}

template <typename Query>
static int collect_query_data(Query* counter_query) {
  assert(counter_query != NULL, "invariant");
  return collect_query_data(&counter_query->query);
}

static int formatted_counter_value(HCOUNTER counter, DWORD format, PDH_FMT_COUNTERVALUE* const value) {
  assert(value != NULL, "invariant");
  if (PdhDll::PdhGetFormattedCounterValue(counter, format, NULL, value) != ERROR_SUCCESS) {
    return OS_ERR;
  }
  return OS_OK;
}

/*
* Working against the Process object and it's related counters is inherently problematic
* when using the PDH API:
*
* Using PDH, a process is not primarily identified by the process id,
* but with a sequential number, for example \Process(java#0), \Process(java#1), ...
* The really bad part is that this list is reset as soon as a process exits:
* If \Process(java#1) exits, \Process(java#3) now becomes \Process(java#2) etc.
*
* The PDH api requires a process identifier to be submitted when registering
* a query, but as soon as the list resets, the query is invalidated (since the name changed).
*
* Solution:
* The #number identifier for a Process query can only decrease after process creation.
*
* We therefore create an array of counter queries for all process object instances
* up to and including ourselves:
*
* Ex. we come in as third process instance (java#2), we then create and register
* queries for the following Process object instances:
* java#0, java#1, java#2
*
* current_query_index_for_process() keeps track of the current "correct" query
* (in order to keep this index valid when the list resets from underneath,
* ensure to call current_query_index_for_process() before every query involving
* Process object instance data).
*
* if unable to query, returns OS_ERR(-1)
*/
static int current_query_index_for_process() {
  assert(process_image_name != NULL, "invariant");
  assert(pdh_IDProcess_counter_fmt != NULL, "invariant");
  HQUERY tmpQuery = NULL;
  if (open_query(&tmpQuery) != ERROR_SUCCESS) {
    return OS_ERR;
  }
  char counter[512];
  HCOUNTER handle_counter = NULL;
  // iterate over all instance indexes and try to find our own pid
  for (int index = 0; index < max_intx; index++) {
    jio_snprintf(counter, sizeof(counter) - 1, pdh_IDProcess_counter_fmt, index);
    assert(strlen(counter) < sizeof(counter), "invariant");
    if (PdhDll::PdhAddCounter(tmpQuery, counter, 0, &handle_counter) != ERROR_SUCCESS) {
      pdh_cleanup(&tmpQuery, &handle_counter);
      return OS_ERR;
    }
    const PDH_STATUS res = PdhDll::PdhCollectQueryData(tmpQuery);
    if (res == PDH_INVALID_HANDLE || res == PDH_NO_DATA) {
      pdh_cleanup(&tmpQuery, &handle_counter);
      return OS_ERR;
    } else {
      PDH_FMT_COUNTERVALUE counter_value;
      formatted_counter_value(handle_counter, PDH_FMT_LONG, &counter_value);
      pdh_cleanup(NULL, &handle_counter);
      if ((LONG)os::current_process_id() == counter_value.longValue) {
        pdh_cleanup(&tmpQuery, NULL);
        return index;
      }
    }
  }
  pdh_cleanup(&tmpQuery, NULL);
  return OS_ERR;
}

static ProcessQueryP create_process_query() {
  const int current_process_idx = current_query_index_for_process();
  if (OS_ERR == current_process_idx) {
    return NULL;
  }
  ProcessQueryP const process_query = NEW_C_HEAP_ARRAY(ProcessQueryS, 1, mtInternal);
  memset(process_query, 0, sizeof(ProcessQueryS));
  process_query->set.queries = NEW_C_HEAP_ARRAY(MultiCounterQueryS, current_process_idx + 1, mtInternal);
  memset(process_query->set.queries, 0, sizeof(MultiCounterQueryS) * (current_process_idx + 1));
  process_query->process_index = current_process_idx;
  process_query->set.size = current_process_idx + 1;
  assert(process_query->set.size > process_query->process_index, "invariant");
  return process_query;
}

static MultiCounterQueryP current_process_counter_query(ProcessQueryP process_query) {
  assert(process_query != NULL, "invariant");
  assert(process_query->process_index < process_query->set.size, "invariant");
  return &process_query->set.queries[process_query->process_index];
}

static void clear_multi_counter(MultiCounterQueryP query) {
  for (int i = 0; i < query->noOfCounters; ++i) {
    pdh_cleanup(NULL, &query->counters[i]);
  }
  pdh_cleanup(&query->query.query, NULL);
  query->initialized = false;
}

static int ensure_valid_process_query_index(ProcessQueryP process_query) {
  assert(process_query != NULL, "invariant");
  const int previous_process_idx = process_query->process_index;
  if (previous_process_idx == 0) {
    return previous_process_idx;
  }
  const int current_process_idx = current_query_index_for_process();
  if (current_process_idx == previous_process_idx || OS_ERR == current_process_idx ||
    current_process_idx >= process_query->set.size) {
    return previous_process_idx;
  }

  assert(current_process_idx >= 0 && current_process_idx < process_query->set.size, "out of bounds!");
  while (current_process_idx < process_query->set.size - 1) {
    const int new_size = --process_query->set.size;
    clear_multi_counter(&process_query->set.queries[new_size]);
  }
  assert(current_process_idx < process_query->set.size, "invariant");
  process_query->process_index = current_process_idx;
  return current_process_idx;
}

static MultiCounterQueryP current_process_query(ProcessQueryP process_query) {
  assert(process_query != NULL, "invariant");
  const int current_process_idx = ensure_valid_process_query_index(process_query);
  assert(current_process_idx == process_query->process_index, "invariant");
  assert(current_process_idx < process_query->set.size, "invariant");
  return &process_query->set.queries[current_process_idx];
}

static int collect_process_query_data(ProcessQueryP process_query) {
  assert(process_query != NULL, "invariant");
  return collect_query_data(current_process_query(process_query));
}

static int query_process_counter(ProcessQueryP process_query, int slot_index, DWORD format, PDH_FMT_COUNTERVALUE* const value) {
  MultiCounterQueryP const current_query = current_process_counter_query(process_query);
  assert(current_query != NULL, "invariant");
  assert(slot_index < current_query->noOfCounters, "invariant");
  assert(current_query->counters[slot_index] != NULL, "invariant");
  return formatted_counter_value(current_query->counters[slot_index], format, value);
}

/*
 * Construct a fully qualified PDH path
 *
 * @param objectName   a PDH Object string representation(required)
 * @param counterName  a PDH Counter string representation(required)
 * @param imageName    a process image name string, ex. "java" (opt)
 * @param instance     an instance string, ex. "0", "1", ... (opt)
 * @return             the fully qualified PDH path.
 *
 * Caller will need a ResourceMark.
 *
 * (PdhMakeCounterPath() seems buggy on concatenating instances, hence this function instead)
 */
static const char* make_fully_qualified_counter_path(const char* object_name,
                                                     const char* counter_name,
                                                     const char* image_name = NULL,
                                                     const char* instance = NULL) {
  assert(object_name != NULL, "invariant");
  assert(counter_name != NULL, "invariant");
  size_t full_counter_path_len = strlen(object_name) + strlen(counter_name);

  char* full_counter_path;
  size_t jio_snprintf_result = 0;
  if (image_name) {
    /*
    * For paths using the "Process" Object.
    *
    * Examples:
    * form:   "\object_name(image_name#instance)\counter_name"
    * actual: "\Process(java#2)\ID Process"
    */
    full_counter_path_len += PROCESS_OBJECT_INSTANCE_COUNTER_FMT_LEN;
    full_counter_path_len += strlen(image_name);
    /*
    * image_name must be passed together with an associated
    * instance "number" ("0", "1", "2", ...).
    * This is required in order to create valid "Process" Object paths.
    *
    * Examples: "\Process(java#0)", \Process(java#1"), ...
    */
    assert(instance != NULL, "invariant");
    full_counter_path_len += strlen(instance);
    full_counter_path = NEW_RESOURCE_ARRAY_RETURN_NULL(char, full_counter_path_len + 1);
    if (full_counter_path == NULL) {
      return NULL;
    }
    jio_snprintf_result = jio_snprintf(full_counter_path,
                                       full_counter_path_len + 1,
                                       PROCESS_OBJECT_INSTANCE_COUNTER_FMT,
                                       object_name,
                                       image_name,
                                       instance,
                                       counter_name);
  } else {
    if (instance) {
      /*
      * For paths where the Object has multiple instances.
      *
      * Examples:
      * form:   "\object_name(instance)\counter_name"
      * actual: "\Processor(0)\% Privileged Time"
      */
      full_counter_path_len += strlen(instance);
      full_counter_path_len += OBJECT_WITH_INSTANCES_COUNTER_FMT_LEN;
    } else {
      /*
      * For "normal" paths.
      *
      * Examples:
      * form:   "\object_name\counter_name"
      * actual: "\Memory\Available Mbytes"
      */
      full_counter_path_len += OBJECT_COUNTER_FMT_LEN;
    }
    full_counter_path = NEW_RESOURCE_ARRAY_RETURN_NULL(char, full_counter_path_len + 1);
    if (full_counter_path == NULL) {
      return NULL;
    }
    if (instance) {
      jio_snprintf_result = jio_snprintf(full_counter_path,
                                         full_counter_path_len + 1,
                                         OBJECT_WITH_INSTANCES_COUNTER_FMT,
                                         object_name,
                                         instance,
                                         counter_name);
    } else {
      jio_snprintf_result = jio_snprintf(full_counter_path,
                                         full_counter_path_len + 1,
                                         OBJECT_COUNTER_FMT,
                                         object_name,
                                         counter_name);
    }
  }
  assert(full_counter_path_len == jio_snprintf_result, "invariant");
  return full_counter_path;
}

static void log_invalid_pdh_index(DWORD index) {
  log_warning(os)("Unable to resolve PDH index: (%ld)", index);
  log_warning(os)("Please check the registry if this performance object/counter is disabled");
}

static bool is_valid_pdh_index(DWORD index) {
  DWORD dummy = 0;
  if (PdhDll::PdhLookupPerfNameByIndex(NULL, index, NULL, &dummy) != PDH_MORE_DATA) {
    log_invalid_pdh_index(index);
    return false;
  }
  return true;
}

/*
 * Maps an index to a resource area allocated string for the localized PDH artifact.
 *
 * Caller will need a ResourceMark.
 *
 * @param index    the counter index as specified in the registry
 * @param ppBuffer pointer to a char*
 * @return         OS_OK if successful, OS_ERR on failure.
 */
static OSReturn lookup_name_by_index(DWORD index, char** p_string) {
  assert(p_string != NULL, "invariant");
  if (!is_valid_pdh_index(index)) {
    return OS_ERR;
  }
  // determine size needed
  DWORD size = 0;
  PDH_STATUS status = PdhDll::PdhLookupPerfNameByIndex(NULL, index, NULL, &size);
  assert(status == PDH_MORE_DATA, "invariant");
  *p_string = NEW_RESOURCE_ARRAY_RETURN_NULL(char, size);
  if (*p_string== NULL) {
    return OS_ERR;
  }
  if (PdhDll::PdhLookupPerfNameByIndex(NULL, index, *p_string, &size) != ERROR_SUCCESS) {
    return OS_ERR;
  }
  if (0 == size || *p_string == NULL) {
    return OS_ERR;
  }
  // windows vista does not null-terminate the string (although the docs says it will)
  (*p_string)[size - 1] = '\0';
  return OS_OK;
}

static const char* copy_string_to_c_heap(const char* string) {
  assert(string != NULL, "invariant");
  const size_t len = strlen(string);
  char* const cheap_allocated_string = NEW_C_HEAP_ARRAY(char, len + 1, mtInternal);
  if (NULL == cheap_allocated_string) {
    return NULL;
  }
  strncpy(cheap_allocated_string, string, len + 1);
  return cheap_allocated_string;
}

/*
* Maps an index to a resource area allocated string for the localized PDH artifact.
*
* Caller will need a ResourceMark.
*
* @param index    the counter index as specified in the registry
* @return         localized pdh artifact string if successful, NULL on failure.
*/
static const char* pdh_localized_artifact(DWORD pdh_artifact_index) {
  char* pdh_localized_artifact_string = NULL;
  // get localized name from pdh artifact index
  if (lookup_name_by_index(pdh_artifact_index, &pdh_localized_artifact_string) != OS_OK) {
    return NULL;
  }
  return pdh_localized_artifact_string;
}

/*
 * Returns the PDH string identifying the current process image name.
 * Use this prefix when getting counters from the PDH process object
 * representing your process.
 * Ex. "Process(java#0)\Virtual Bytes" - where "java" is the PDH process
 * image description.
 *
 * Caller needs ResourceMark.
 *
 * @return the process image description. NULL if the call failed.
*/
static const char* pdh_process_image_name() {
  char* module_name = NEW_RESOURCE_ARRAY_RETURN_NULL(char, MAX_PATH);
  if (NULL == module_name) {
    return NULL;
  }
  // Find our module name and use it to extract the image name used by PDH
  DWORD getmfn_return = GetModuleFileName(NULL, module_name, MAX_PATH);
  if (getmfn_return >= MAX_PATH || 0 == getmfn_return) {
    return NULL;
  }
  if (os::get_last_error() == ERROR_INSUFFICIENT_BUFFER) {
    return NULL;
  }
  char* process_image_name = strrchr(module_name, '\\'); //drop path
  process_image_name++;                                  //skip slash
  char* dot_pos = strrchr(process_image_name, '.');      //drop .exe
  dot_pos[0] = '\0';
  return process_image_name;
}

static void deallocate_pdh_constants() {
  if (process_image_name != NULL) {
    FREE_C_HEAP_ARRAY(char, process_image_name);
    process_image_name = NULL;
  }
  if (pdh_IDProcess_counter_fmt != NULL) {
    FREE_C_HEAP_ARRAY(char, pdh_IDProcess_counter_fmt);
    pdh_IDProcess_counter_fmt = NULL;
  }
}

static int allocate_pdh_constants() {
  assert(process_image_name == NULL, "invariant");
  const char* pdh_image_name = pdh_process_image_name();
  if (pdh_image_name == NULL) {
    return OS_ERR;
  }
  process_image_name = copy_string_to_c_heap(pdh_image_name);

  const char* pdh_localized_process_object = pdh_localized_artifact(PDH_PROCESS_IDX);
  if (pdh_localized_process_object == NULL) {
    return OS_ERR;
  }

  const char* pdh_localized_IDProcess_counter = pdh_localized_artifact(PDH_ID_PROCESS_IDX);
  if (pdh_localized_IDProcess_counter == NULL) {
    return OS_ERR;
  }

  size_t pdh_IDProcess_counter_fmt_len = strlen(process_image_name);
  pdh_IDProcess_counter_fmt_len += strlen(pdh_localized_process_object);
  pdh_IDProcess_counter_fmt_len += strlen(pdh_localized_IDProcess_counter);
  pdh_IDProcess_counter_fmt_len += PROCESS_OBJECT_INSTANCE_COUNTER_FMT_LEN;
  pdh_IDProcess_counter_fmt_len += 2; // "%d"

  assert(pdh_IDProcess_counter_fmt == NULL, "invariant");
  pdh_IDProcess_counter_fmt = NEW_C_HEAP_ARRAY_RETURN_NULL(char, pdh_IDProcess_counter_fmt_len + 1, mtInternal);
  if (pdh_IDProcess_counter_fmt == NULL) {
    return OS_ERR;
  }

  /* "\Process(java#%d)\ID Process" */
  const size_t len = jio_snprintf(pdh_IDProcess_counter_fmt,
                                  pdh_IDProcess_counter_fmt_len + 1,
                                  PROCESS_OBJECT_INSTANCE_COUNTER_FMT,
                                  pdh_localized_process_object,
                                  process_image_name,
                                  "%d",
                                  pdh_localized_IDProcess_counter);

  assert(pdh_IDProcess_counter_fmt != NULL, "invariant");
  assert(len == pdh_IDProcess_counter_fmt_len, "invariant");
  return OS_OK;
}

/*
 * Enuerate the Processor PDH object and returns a buffer containing the enumerated instances.
 * Caller needs ResourceMark;
 *
 * @return  buffer if successful, NULL on failure.
*/
static const char* enumerate_cpu_instances() {
  char* processor; //'Processor' == PDH_PROCESSOR_IDX
  if (lookup_name_by_index(PDH_PROCESSOR_IDX, &processor) != OS_OK) {
    return NULL;
  }
  DWORD c_size = 0;
  DWORD i_size = 0;
  // enumerate all processors.
  PDH_STATUS pdhStat = PdhDll::PdhEnumObjectItems(NULL, // reserved
                                                  NULL, // local machine
                                                  processor, // object to enumerate
                                                  NULL,
                                                  &c_size,
                                                  NULL, // instance buffer is NULL and
                                                  &i_size,  // pass 0 length in order to get the required size
                                                  PERF_DETAIL_WIZARD, // counter detail level
                                                  0);
  if (PdhDll::PdhStatusFail((pdhStat))) {
    return NULL;
  }
  char* const instances = NEW_RESOURCE_ARRAY_RETURN_NULL(char, i_size);
  if (instances == NULL) {
    return NULL;
  }
  c_size = 0;
  pdhStat = PdhDll::PdhEnumObjectItems(NULL, // reserved
                                       NULL, // local machine
                                       processor, // object to enumerate
                                       NULL,
                                       &c_size,
                                       instances, // now instance buffer is allocated to be filled in
                                       &i_size, // and the required size is known
                                       PERF_DETAIL_WIZARD, // counter detail level
                                       0);
  if (PdhDll::PdhStatusFail((pdhStat))) {
    return NULL;
  }
  return instances;
}

static int count_logical_cpus(const char* instances) {
  assert(instances != NULL, "invariant");
  // count logical instances.
  DWORD count;
  char* tmp;
  for (count = 0, tmp = const_cast<char*>(instances); *tmp != '\0'; tmp = &tmp[strlen(tmp) + 1], count++);
  // PDH reports an instance for each logical processor plus an instance for the total (_Total)
  assert(count == os::processor_count() + 1, "invalid enumeration!");
  return count - 1;
}

static int number_of_logical_cpus() {
  static int numberOfCPUS = 0;
  if (numberOfCPUS == 0) {
    const char* instances = enumerate_cpu_instances();
    if (instances == NULL) {
      return OS_ERR;
    }
    numberOfCPUS = count_logical_cpus(instances);
  }
  return numberOfCPUS;
}

static double cpu_factor() {
  static DWORD  numCpus = 0;
  static double cpuFactor = .0;
  if (numCpus == 0) {
    numCpus = number_of_logical_cpus();
    assert(os::processor_count() <= (int)numCpus, "invariant");
    cpuFactor = numCpus * 100;
  }
  return cpuFactor;
}

static void log_error_message_on_no_PDH_artifact(const char* full_counter_name) {
  log_warning(os)("Unable to register PDH query for \"%s\"", full_counter_name);
  log_warning(os)("Please check the registry if this performance object/counter is disabled");
}

static int initialize_cpu_query_counters(MultiCounterQueryP cpu_query, DWORD pdh_counter_idx) {
  assert(cpu_query != NULL, "invariant");
  assert(cpu_query->counters != NULL, "invariant");
  char* processor; //'Processor' == PDH_PROCESSOR_IDX
  if (lookup_name_by_index(PDH_PROCESSOR_IDX, &processor) != OS_OK) {
    return OS_ERR;
  }
  char* counter_name = NULL;
  if (lookup_name_by_index(pdh_counter_idx, &counter_name) != OS_OK) {
    return OS_ERR;
  }
  if (cpu_query->query.query == NULL) {
    if (open_query(cpu_query)) {
      return OS_ERR;
    }
  }
  assert(cpu_query->query.query != NULL, "invariant");
  size_t counter_len = strlen(processor);
  counter_len += strlen(counter_name);
  counter_len += OBJECT_WITH_INSTANCES_COUNTER_FMT_LEN; // "\\%s(%s)\\%s"

  DWORD index;
  char* tmp;
  const char* instances = enumerate_cpu_instances();
  for (index = 0, tmp = const_cast<char*>(instances); *tmp != '\0'; tmp = &tmp[strlen(tmp) + 1], index++) {
    const size_t tmp_len = strlen(tmp);
    char* counter_path = NEW_RESOURCE_ARRAY_RETURN_NULL(char, counter_len + tmp_len + 1);
    if (counter_path == NULL) {
      return OS_ERR;
    }
    const size_t jio_snprintf_result = jio_snprintf(counter_path,
                                                    counter_len + tmp_len + 1,
                                                    OBJECT_WITH_INSTANCES_COUNTER_FMT,
                                                    processor,
                                                    tmp, // instance "0", "1", .."_Total"
                                                    counter_name);
    assert(counter_len + tmp_len == jio_snprintf_result, "invariant");
    if (add_counter(cpu_query, &cpu_query->counters[index], counter_path, false) != OS_OK) {
      // performance counter is disabled in registry and not accessible via PerfLib
      log_error_message_on_no_PDH_artifact(counter_path);
      // return OS_OK to have the system continue to run without the missing counter
      return OS_OK;
    }
  }
  cpu_query->initialized = true;
  // Query once to initialize the counters which require at least two samples
  // (like the % CPU usage) to calculate correctly.
  collect_query_data(cpu_query);
  return OS_OK;
}

static int initialize_cpu_query(MultiCounterQueryP cpu_query, DWORD pdh_counter_idx) {
  assert(cpu_query != NULL, "invariant");
  assert(!cpu_query->initialized, "invariant");
  const int logical_cpu_count = number_of_logical_cpus();
  assert(logical_cpu_count >= os::processor_count(), "invariant");
  // we also add another counter for instance "_Total"
  if (allocate_counters(cpu_query, logical_cpu_count + 1) != OS_OK) {
    return OS_ERR;
  }
  assert(cpu_query->noOfCounters == logical_cpu_count + 1, "invariant");
  return initialize_cpu_query_counters(cpu_query, pdh_counter_idx);
}

static int initialize_process_counter(ProcessQueryP process_query, int slot_index, DWORD pdh_counter_index) {
  char* localized_process_object;
  if (lookup_name_by_index(PDH_PROCESS_IDX, &localized_process_object) != OS_OK) {
    return OS_ERR;
  }
  assert(localized_process_object != NULL, "invariant");
  char* localized_counter_name;
  if (lookup_name_by_index(pdh_counter_index, &localized_counter_name) != OS_OK) {
    return OS_ERR;
  }
  assert(localized_counter_name != NULL, "invariant");
  for (int i = 0; i < process_query->set.size; ++i) {
    char instanceIndexBuffer[32];
    const char* counter_path = make_fully_qualified_counter_path(localized_process_object,
                                                                 localized_counter_name,
                                                                 process_image_name,
                                                                 itoa(i, instanceIndexBuffer, 10));
    if (counter_path == NULL) {
      return OS_ERR;
    }
    MultiCounterQueryP const query = &process_query->set.queries[i];
    if (add_process_counter(query, slot_index, counter_path, true)) {
      return OS_ERR;
    }
  }
  return OS_OK;
}

static CounterQueryP create_counter_query(DWORD pdh_object_idx, DWORD pdh_counter_idx) {
  if (!((is_valid_pdh_index(pdh_object_idx) && is_valid_pdh_index(pdh_counter_idx)))) {
    return NULL;
  }
  CounterQueryP const query = create_counter_query();
  const char* object = pdh_localized_artifact(pdh_object_idx);
  assert(object != NULL, "invariant");
  const char* counter = pdh_localized_artifact(pdh_counter_idx);
  assert(counter != NULL, "invariant");
  const char* full_counter_path = make_fully_qualified_counter_path(object, counter);
  assert(full_counter_path != NULL, "invariant");
  add_counter(query, full_counter_path, true);
  return query;
}

static void deallocate() {
  deallocate_pdh_constants();
  PdhDll::PdhDetach();
}

static LONG critical_section = 0;
static LONG reference_count = 0;
static bool pdh_initialized = false;

static void on_initialization_failure() {
  // still holder of critical section
  deallocate();
  InterlockedExchangeAdd(&reference_count, -1);
}

static OSReturn initialize() {
  ResourceMark rm;
  if (!PdhDll::PdhAttach()) {
    return OS_ERR;
  }
  if (allocate_pdh_constants() != OS_OK) {
    on_initialization_failure();
    return OS_ERR;
  }
  return OS_OK;
}

/*
* Helper to initialize the PDH library, function pointers, constants and counters.
*
* Reference counting allows for unloading of pdh.dll granted all sessions use the pair:
*
*   pdh_acquire();
*   pdh_release();
*
* @return  OS_OK if successful, OS_ERR on failure.
*/
static bool pdh_acquire() {
  while (InterlockedCompareExchange(&critical_section, 1, 0) == 1);
  InterlockedExchangeAdd(&reference_count, 1);
  if (pdh_initialized) {
    return true;
  }
  const OSReturn ret = initialize();
  if (OS_OK == ret) {
    pdh_initialized = true;
  }
  while (InterlockedCompareExchange(&critical_section, 0, 1) == 0);
  return ret == OS_OK;
}

static void pdh_release() {
  while (InterlockedCompareExchange(&critical_section, 1, 0) == 1);
  const LONG prev_ref_count = InterlockedExchangeAdd(&reference_count, -1);
  if (1 == prev_ref_count) {
    deallocate();
    pdh_initialized = false;
  }
  while (InterlockedCompareExchange(&critical_section, 0, 1) == 0);
}

class CPUPerformanceInterface::CPUPerformance : public CHeapObj<mtInternal> {
  friend class CPUPerformanceInterface;
 private:
  CounterQueryP _context_switches;
  ProcessQueryP _process_cpu_load;
  MultiCounterQueryP _machine_cpu_load;

  int cpu_load(int which_logical_cpu, double* cpu_load);
  int context_switch_rate(double* rate);
  int cpu_load_total_process(double* cpu_load);
  int cpu_loads_process(double* jvm_user_load, double* jvm_kernel_load, double* psystemTotalLoad);
  CPUPerformance();
  ~CPUPerformance();
  bool initialize();
};

class SystemProcessInterface::SystemProcesses : public CHeapObj<mtInternal> {
  friend class SystemProcessInterface;
 private:
  class ProcessIterator : public CHeapObj<mtInternal> {
    friend class SystemProcessInterface::SystemProcesses;
   private:
    HANDLE         _hProcessSnap;
    PROCESSENTRY32 _pe32;
    BOOL           _valid;
    char           _exePath[MAX_PATH];
    ProcessIterator();
    ~ProcessIterator();
    bool initialize();

    int current(SystemProcess* const process_info);
    int next_process();
    bool is_valid() const { return _valid != FALSE; }
    char* allocate_string(const char* str) const;
    int snapshot();
  };

  ProcessIterator* _iterator;
  SystemProcesses();
  ~SystemProcesses();
  bool initialize();

  // information about system processes
  int system_processes(SystemProcess** system_processes, int* no_of_sys_processes) const;
};

CPUPerformanceInterface::CPUPerformance::CPUPerformance() : _context_switches(NULL), _process_cpu_load(NULL), _machine_cpu_load(NULL) {}

bool CPUPerformanceInterface::CPUPerformance::initialize() {
  if (!pdh_acquire()) {
    return true;
  }
  _context_switches = create_counter_query(PDH_SYSTEM_IDX, PDH_CONTEXT_SWITCH_RATE_IDX);
  _process_cpu_load = create_process_query();
  if (_process_cpu_load == NULL) {
    return true;
  }
  if (allocate_counters(_process_cpu_load, 2) != OS_OK) {
    return true;
  }
  if (initialize_process_counter(_process_cpu_load, 0, PDH_PROCESSOR_TIME_IDX) != OS_OK) {
    return true;
  }
  if (initialize_process_counter(_process_cpu_load, 1, PDH_PRIV_PROCESSOR_TIME_IDX) != OS_OK) {
    return true;
  }
  _process_cpu_load->set.initialized = true;
  _machine_cpu_load = create_multi_counter_query();
  if (_machine_cpu_load == NULL) {
    return true;
  }
  initialize_cpu_query(_machine_cpu_load, PDH_PROCESSOR_TIME_IDX);
  return true;
}

CPUPerformanceInterface::CPUPerformance::~CPUPerformance() {
  if (_context_switches != NULL) {
    destroy_counter_query(_context_switches);
    _context_switches = NULL;
  }
  if (_process_cpu_load != NULL) {
    destroy_counter_query(_process_cpu_load);
    _process_cpu_load = NULL;
  }
  if (_machine_cpu_load != NULL) {
    destroy_counter_query(_machine_cpu_load);
    _machine_cpu_load = NULL;
  }
  pdh_release();
}

CPUPerformanceInterface::CPUPerformanceInterface() {
  _impl = NULL;
}

bool CPUPerformanceInterface::initialize() {
  _impl = new CPUPerformanceInterface::CPUPerformance();
  return _impl != NULL && _impl->initialize();
}

CPUPerformanceInterface::~CPUPerformanceInterface() {
  if (_impl != NULL) {
    delete _impl;
  }
}

int CPUPerformanceInterface::cpu_load(int which_logical_cpu, double* cpu_load) const {
  return _impl->cpu_load(which_logical_cpu, cpu_load);
}

int CPUPerformanceInterface::context_switch_rate(double* rate) const {
  return _impl->context_switch_rate(rate);
}

int CPUPerformanceInterface::cpu_load_total_process(double* cpu_load) const {
  return _impl->cpu_load_total_process(cpu_load);
}

int CPUPerformanceInterface::cpu_loads_process(double* pjvmUserLoad,
                                               double* pjvmKernelLoad,
                                               double* psystemTotalLoad) const {
  return _impl->cpu_loads_process(pjvmUserLoad, pjvmKernelLoad, psystemTotalLoad);
}

int CPUPerformanceInterface::CPUPerformance::cpu_load(int which_logical_cpu, double* cpu_load) {
  *cpu_load = .0;
  if (_machine_cpu_load == NULL || !_machine_cpu_load->initialized) {
    return OS_ERR;
  }
  assert(_machine_cpu_load != NULL, "invariant");
  assert(which_logical_cpu < _machine_cpu_load->noOfCounters, "invariant");

  if (collect_query_data(_machine_cpu_load)) {
    return OS_ERR;
  }
  // -1 is total (all cpus)
  const int counter_idx = -1 == which_logical_cpu ? _machine_cpu_load->noOfCounters - 1 : which_logical_cpu;
  PDH_FMT_COUNTERVALUE counter_value;
  formatted_counter_value(_machine_cpu_load->counters[counter_idx], PDH_FMT_DOUBLE, &counter_value);
  *cpu_load = counter_value.doubleValue / 100;
  return OS_OK;
}

int CPUPerformanceInterface::CPUPerformance::cpu_load_total_process(double* cpu_load) {
  *cpu_load = .0;
  if (_process_cpu_load == NULL || !_process_cpu_load->set.initialized) {
    return OS_ERR;
  }
  assert(_process_cpu_load != NULL, "invariant");
  if (collect_process_query_data(_process_cpu_load)) {
    return OS_ERR;
  }
  PDH_FMT_COUNTERVALUE counter_value;
  if (query_process_counter(_process_cpu_load, 0, PDH_FMT_DOUBLE | PDH_FMT_NOCAP100, &counter_value) != OS_OK) {
    return OS_ERR;
  }
  double process_load = counter_value.doubleValue / cpu_factor();
  process_load = MIN2<double>(1, process_load);
  process_load = MAX2<double>(0, process_load);
  *cpu_load = process_load;
  return OS_OK;
}

int CPUPerformanceInterface::CPUPerformance::cpu_loads_process(double* pjvmUserLoad,
                                                               double* pjvmKernelLoad,
                                                               double* psystemTotalLoad) {
  assert(pjvmUserLoad != NULL, "pjvmUserLoad is NULL!");
  assert(pjvmKernelLoad != NULL, "pjvmKernelLoad is NULL!");
  assert(psystemTotalLoad != NULL, "psystemTotalLoad is NULL!");
  *pjvmUserLoad = .0;
  *pjvmKernelLoad = .0;
  *psystemTotalLoad = .0;

  if (_process_cpu_load == NULL || !_process_cpu_load->set.initialized) {
    return OS_ERR;
  }
  assert(_process_cpu_load != NULL, "invariant");
  if (collect_process_query_data(_process_cpu_load)) {
    return OS_ERR;
  }
  double process_load = .0;
  PDH_FMT_COUNTERVALUE counter_value;
  // Read  PDH_PROCESSOR_TIME_IDX
  if (query_process_counter(_process_cpu_load, 0, PDH_FMT_DOUBLE | PDH_FMT_NOCAP100, &counter_value) != OS_OK) {
    return OS_ERR;
  }
  process_load = counter_value.doubleValue / cpu_factor();
  process_load = MIN2<double>(1, process_load);
  process_load = MAX2<double>(0, process_load);
  // Read PDH_PRIV_PROCESSOR_TIME_IDX
  if (query_process_counter(_process_cpu_load, 1, PDH_FMT_DOUBLE | PDH_FMT_NOCAP100, &counter_value) != OS_OK) {
    return OS_ERR;
  }
  double kernel_load = counter_value.doubleValue / cpu_factor();
  kernel_load = MIN2<double>(1, kernel_load);
  kernel_load = MAX2<double>(0, kernel_load);
  *pjvmKernelLoad = kernel_load;

  double user_load = process_load - kernel_load;
  user_load = MIN2<double>(1, user_load);
  user_load = MAX2<double>(0, user_load);
  *pjvmUserLoad = user_load;

  if (collect_query_data(_machine_cpu_load)) {
    return OS_ERR;
  }
  if (formatted_counter_value(_machine_cpu_load->counters[_machine_cpu_load->noOfCounters - 1], PDH_FMT_DOUBLE, &counter_value) != OS_OK) {
    return OS_ERR;
  }
  double machine_load = counter_value.doubleValue / 100;
  assert(machine_load >= 0, "machine_load is negative!");
  // clamp at user+system and 1.0
  if (*pjvmKernelLoad + *pjvmUserLoad > machine_load) {
    machine_load = MIN2(*pjvmKernelLoad + *pjvmUserLoad, 1.0);
  }
  *psystemTotalLoad = machine_load;
  return OS_OK;
}

int CPUPerformanceInterface::CPUPerformance::context_switch_rate(double* rate) {
  assert(rate != NULL, "invariant");
  *rate = .0;
  if (_context_switches == NULL || !_context_switches->initialized) {
    return OS_ERR;
  }
  assert(_context_switches != NULL, "invariant");
  if (collect_query_data(_context_switches) != OS_OK) {
    return OS_ERR;
  }
  PDH_FMT_COUNTERVALUE counter_value;
  if (formatted_counter_value(_context_switches->counter, PDH_FMT_DOUBLE, &counter_value) != OS_OK) {
    return OS_ERR;
  }
  *rate = counter_value.doubleValue;
  return OS_OK;
}

SystemProcessInterface::SystemProcesses::ProcessIterator::ProcessIterator() {
  _hProcessSnap = INVALID_HANDLE_VALUE;
  _valid = FALSE;
  _pe32.dwSize = sizeof(PROCESSENTRY32);
}

bool SystemProcessInterface::SystemProcesses::ProcessIterator::initialize() {
  return true;
}

int SystemProcessInterface::SystemProcesses::ProcessIterator::snapshot() {
  // take snapshot of all process in the system
  _hProcessSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
  if (_hProcessSnap == INVALID_HANDLE_VALUE) {
    return OS_ERR;
  }
  // step to first process
  _valid = Process32First(_hProcessSnap, &_pe32);
  return is_valid() ? OS_OK : OS_ERR;
}

SystemProcessInterface::SystemProcesses::ProcessIterator::~ProcessIterator() {
  if (_hProcessSnap != INVALID_HANDLE_VALUE) {
    CloseHandle(_hProcessSnap);
  }
}

int SystemProcessInterface::SystemProcesses::ProcessIterator::current(SystemProcess* process_info) {
  assert(is_valid(), "no current process to be fetched!");
  assert(process_info != NULL, "process_info is NULL!");
  char* exePath = NULL;
  HANDLE hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, false, _pe32.th32ProcessID);
  if (hProcess != NULL) {
    HMODULE hMod;
    DWORD cbNeeded;
    if (EnumProcessModules(hProcess, &hMod, sizeof(hMod), &cbNeeded) != 0) {
      if (GetModuleFileNameExA(hProcess, hMod, _exePath, sizeof(_exePath)) != 0) {
        exePath = _exePath;
      }
    }
    CloseHandle (hProcess);
  }
  process_info->set_pid((int)_pe32.th32ProcessID);
  process_info->set_name(allocate_string(_pe32.szExeFile));
  process_info->set_path(allocate_string(exePath));
  return OS_OK;
}

char* SystemProcessInterface::SystemProcesses::ProcessIterator::allocate_string(const char* str) const {
  if (str != NULL) {
    return os::strdup_check_oom(str, mtInternal);
  }
  return NULL;
}

int SystemProcessInterface::SystemProcesses::ProcessIterator::next_process() {
  _valid = Process32Next(_hProcessSnap, &_pe32);
  return OS_OK;
}

SystemProcessInterface::SystemProcesses::SystemProcesses() {
  _iterator = NULL;
}

bool SystemProcessInterface::SystemProcesses::initialize() {
  _iterator = new SystemProcessInterface::SystemProcesses::ProcessIterator();
  return _iterator != NULL && _iterator->initialize();
}

SystemProcessInterface::SystemProcesses::~SystemProcesses() {
  if (_iterator != NULL) {
    delete _iterator;
    _iterator = NULL;
  }
}

int SystemProcessInterface::SystemProcesses::system_processes(SystemProcess** system_processes,
                                                              int* no_of_sys_processes) const {
  assert(system_processes != NULL, "system_processes pointer is NULL!");
  assert(no_of_sys_processes != NULL, "system_processes counter pointers is NULL!");
  assert(_iterator != NULL, "iterator is NULL!");

  // initialize pointers
  *no_of_sys_processes = 0;
  *system_processes = NULL;

  // take process snapshot
  if (_iterator->snapshot() != OS_OK) {
    return OS_ERR;
  }

  while (_iterator->is_valid()) {
    SystemProcess* tmp = new SystemProcess();
    _iterator->current(tmp);

    //if already existing head
    if (*system_processes != NULL) {
      //move "first to second"
      tmp->set_next(*system_processes);
    }
    // new head
    *system_processes = tmp;
    // increment
    (*no_of_sys_processes)++;
    // step forward
    _iterator->next_process();
  }
  return OS_OK;
}

int SystemProcessInterface::system_processes(SystemProcess** system_procs,
                                             int* no_of_sys_processes) const {
  return _impl->system_processes(system_procs, no_of_sys_processes);
}

SystemProcessInterface::SystemProcessInterface() {
  _impl = NULL;
}

bool SystemProcessInterface::initialize() {
  _impl = new SystemProcessInterface::SystemProcesses();
  return _impl != NULL && _impl->initialize();
}

SystemProcessInterface::~SystemProcessInterface() {
  if (_impl != NULL) {
    delete _impl;
  }
}

CPUInformationInterface::CPUInformationInterface() {
  _cpu_info = NULL;
}

bool CPUInformationInterface::initialize() {
  _cpu_info = new CPUInformation();
  if (NULL == _cpu_info) {
    return false;
  }
  _cpu_info->set_number_of_hardware_threads(VM_Version_Ext::number_of_threads());
  _cpu_info->set_number_of_cores(VM_Version_Ext::number_of_cores());
  _cpu_info->set_number_of_sockets(VM_Version_Ext::number_of_sockets());
  _cpu_info->set_cpu_name(VM_Version_Ext::cpu_name());
  _cpu_info->set_cpu_description(VM_Version_Ext::cpu_description());
  return true;
}

CPUInformationInterface::~CPUInformationInterface() {
  if (_cpu_info != NULL) {
    const char* cpu_name = _cpu_info->cpu_name();
    if (cpu_name != NULL) {
      FREE_C_HEAP_ARRAY(char, cpu_name);
      _cpu_info->set_cpu_name(NULL);
    }
    const char* cpu_desc = _cpu_info->cpu_description();
    if (cpu_desc != NULL) {
      FREE_C_HEAP_ARRAY(char, cpu_desc);
      _cpu_info->set_cpu_description(NULL);
    }
    delete _cpu_info;
    _cpu_info = NULL;
  }
}

int CPUInformationInterface::cpu_information(CPUInformation& cpu_info) {
  if (NULL == _cpu_info) {
    return OS_ERR;
  }
  cpu_info = *_cpu_info; // shallow copy assignment
  return OS_OK;
}

class NetworkPerformanceInterface::NetworkPerformance : public CHeapObj<mtInternal> {
  friend class NetworkPerformanceInterface;
 private:
  bool _iphlp_attached;

  NetworkPerformance();
  NetworkPerformance(const NetworkPerformance& rhs); // no impl
  NetworkPerformance& operator=(const NetworkPerformance& rhs); // no impl
  bool initialize();
  ~NetworkPerformance();
  int network_utilization(NetworkInterface** network_interfaces) const;
};

NetworkPerformanceInterface::NetworkPerformance::NetworkPerformance()
: _iphlp_attached(false) {
}

bool NetworkPerformanceInterface::NetworkPerformance::initialize() {
  _iphlp_attached = IphlpDll::IphlpAttach();
  return _iphlp_attached;
}

NetworkPerformanceInterface::NetworkPerformance::~NetworkPerformance() {
  if (_iphlp_attached) {
    IphlpDll::IphlpDetach();
  }
}

int NetworkPerformanceInterface::NetworkPerformance::network_utilization(NetworkInterface** network_interfaces) const {
  MIB_IF_TABLE2* table;

  if (IphlpDll::GetIfTable2(&table) != NO_ERROR) {
    return OS_ERR;
  }

  NetworkInterface* ret = NULL;
  for (ULONG i = 0; i < table->NumEntries; ++i) {
    if (table->Table[i].InterfaceAndOperStatusFlags.FilterInterface) {
      continue;
    }

    char buf[256];
    if (WideCharToMultiByte(CP_UTF8, 0, table->Table[i].Description, -1, buf, sizeof(buf), NULL, NULL) == 0) {
      continue;
    }

    NetworkInterface* cur = new NetworkInterface(buf, table->Table[i].InOctets, table->Table[i].OutOctets, ret);
    ret = cur;
  }

  IphlpDll::FreeMibTable(table);
  *network_interfaces = ret;

  return OS_OK;
}

NetworkPerformanceInterface::NetworkPerformanceInterface() {
  _impl = NULL;
}

NetworkPerformanceInterface::~NetworkPerformanceInterface() {
  if (_impl != NULL) {
    delete _impl;
  }
}

bool NetworkPerformanceInterface::initialize() {
  _impl = new NetworkPerformanceInterface::NetworkPerformance();
  return _impl != NULL && _impl->initialize();
}

int NetworkPerformanceInterface::network_utilization(NetworkInterface** network_interfaces) const {
  return _impl->network_utilization(network_interfaces);
}
