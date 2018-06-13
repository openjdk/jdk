/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "jvm.h"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "vm_version_ext_sparc.hpp"

// VM_Version_Ext statics
int   VM_Version_Ext::_no_of_threads = 0;
int   VM_Version_Ext::_no_of_cores = 0;
int   VM_Version_Ext::_no_of_sockets = 0;
#if defined(SOLARIS)
kid_t VM_Version_Ext::_kcid = -1;
#endif
char  VM_Version_Ext::_cpu_name[CPU_TYPE_DESC_BUF_SIZE] = {0};
char  VM_Version_Ext::_cpu_desc[CPU_DETAILED_DESC_BUF_SIZE] = {0};

#if defined(SOLARIS)
// get cpu information. It takes into account if the kstat chain id
// has been changed and update the info if necessary.
bool VM_Version_Ext::initialize_cpu_information(void) {

  int core_id = -1;
  int chip_id = -1;
  int len = 0;
  char* src_string = NULL;
  kstat_ctl_t* kc = kstat_open();
  if (!kc) {
    return false;
  }

  // check if kstat chain has been updated
  kid_t kcid = kstat_chain_update(kc);
  if (kcid == -1) {
    kstat_close(kc);
    return false;
  }

  bool updated = ((kcid > 0) && (kcid != _kcid)) ||
                 ((kcid == 0) && (_kcid == -1));
  if (!updated) {
    kstat_close(kc);
    return true;
  }

  // update the cached _kcid
  _kcid = kcid;

  // find the number of online processors
  // for modern processsors, it is also known as the
  // hardware threads.
  _no_of_threads  = sysconf(_SC_NPROCESSORS_ONLN);

  if (_no_of_threads <= 0 ) {
    kstat_close(kc);
    return false;
  }

  _no_of_cores = 0;
  _no_of_sockets = 0;

  // loop through the kstat chain
  kstat_t* ksp = NULL;
  for (ksp = kc->kc_chain; ksp != NULL; ksp = ksp->ks_next) {
    // only interested in "cpu_info"
    if (strcmp(ksp->ks_module, (char*)CPU_INFO) == 0) {
      if (kstat_read(kc, ksp, NULL) == -1) {
        kstat_close(kc);
        return false;
      }
      if (ksp->ks_data != NULL) {
        kstat_named_t* knm = (kstat_named_t *)ksp->ks_data;
        // loop through the number of fields in each record
        for (int i = 0; i < ksp->ks_ndata; i++) {
          // set cpu type if it hasn't been already set
          if ((strcmp((const char*)&(knm[i].name), CPU_TYPE) == 0) &&
                     (_cpu_name[0] == '\0')) {
            if (knm[i].data_type == KSTAT_DATA_STRING) {
              src_string = (char*)KSTAT_NAMED_STR_PTR(&knm[i]);
            } else {
              src_string = (char*)&(knm[i].value.c[0]);
            }
            len = strlen(src_string);
            if (len < CPU_TYPE_DESC_BUF_SIZE) {
              jio_snprintf(_cpu_name, CPU_TYPE_DESC_BUF_SIZE,
                                         "%s", src_string);
            }
          }

          // set cpu description if it hasn't been already set
          if ((strcmp((const char*)&(knm[i].name), CPU_DESCRIPTION) == 0) &&
                      (_cpu_desc[0] == '\0')) {
            if (knm[i].data_type == KSTAT_DATA_STRING) {
              src_string = (char*)KSTAT_NAMED_STR_PTR(&knm[i]);
            } else {
              src_string = (char*)&(knm[i].value.c[0]);
            }
            len = strlen(src_string);
            if (len < CPU_DETAILED_DESC_BUF_SIZE) {
              jio_snprintf(_cpu_desc, CPU_DETAILED_DESC_BUF_SIZE,
                                         "%s", src_string);
            }
          }

          // count the number of sockets based on the chip id
          if (strcmp((const char*)&(knm[i].name), CHIP_ID) == 0) {
            if (chip_id != knm[i].value.l) {
              chip_id = knm[i].value.l;
              _no_of_sockets++;
            }
          }

          // count the number of cores based on the core id
          if (strcmp((const char*)&(knm[i].name), CORE_ID) == 0) {
            if (core_id != knm[i].value.l) {
              core_id = knm[i].value.l;
              _no_of_cores++;
            }
          }
        }
      }
    }
  }

  kstat_close(kc);
  return true;
}
#elif defined(LINUX)
// get cpu information.
bool VM_Version_Ext::initialize_cpu_information(void) {
  // Not yet implemented.
  return false;
}
#endif

int VM_Version_Ext::number_of_threads(void) {
  initialize_cpu_information();
  return _no_of_threads;
}

int VM_Version_Ext::number_of_cores(void) {
  initialize_cpu_information();
  return _no_of_cores;
}

int VM_Version_Ext::number_of_sockets(void) {
  initialize_cpu_information();
  return _no_of_sockets;
}

const char* VM_Version_Ext::cpu_name(void) {
  if (!initialize_cpu_information()) {
    return NULL;
  }
  char* tmp = NEW_C_HEAP_ARRAY_RETURN_NULL(char, CPU_TYPE_DESC_BUF_SIZE, mtTracing);
  if (NULL == tmp) {
    return NULL;
  }
  strncpy(tmp, _cpu_name, CPU_TYPE_DESC_BUF_SIZE);
  return tmp;
}

const char* VM_Version_Ext::cpu_description(void) {
  if (!initialize_cpu_information()) {
    return NULL;
  }
  char* tmp = NEW_C_HEAP_ARRAY_RETURN_NULL(char, CPU_DETAILED_DESC_BUF_SIZE, mtTracing);
  if (NULL == tmp) {
    return NULL;
  }
  strncpy(tmp, _cpu_desc, CPU_DETAILED_DESC_BUF_SIZE);
  return tmp;
}
