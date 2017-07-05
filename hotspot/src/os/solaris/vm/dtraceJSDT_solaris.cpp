/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "code/codeBlob.hpp"
#include "memory/allocation.hpp"
#include "prims/jvm.h"
#include "runtime/dtraceJSDT.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/os.hpp"
#include "runtime/signature.hpp"
#include "utilities/globalDefinitions.hpp"

#ifdef HAVE_DTRACE_H

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <dtrace.h>

static const char* devname    = "/dev/dtrace/helper";
static const char* olddevname = "/devices/pseudo/dtrace@0:helper";

static const char* string_sig = "uintptr_t";
static const char* int_sig    = "long";
static const char* long_sig   = "long long";

static void printDOFHelper(dof_helper_t* helper);

static int dofhelper_open() {
  int fd;
  if ((fd = open64(devname, O_RDWR)) < 0) {
    // Optimize next calls
    devname = olddevname;
    if ((fd = open64(devname, O_RDWR)) < 0) {
      return -1;
    }
  }
  return fd;
}

static jint dof_register(jstring module, uint8_t* dof, void* modaddr) {
  int probe;
  dof_helper_t dh;
  int fd;

  memset(&dh, 0, sizeof(dh));

  char* module_name = java_lang_String::as_utf8_string(
        JNIHandles::resolve_non_null(module));
  jio_snprintf(dh.dofhp_mod, sizeof(dh.dofhp_mod), "%s", module_name);
  dh.dofhp_dof  = (uint64_t)dof;
  dh.dofhp_addr = (uint64_t)modaddr;

  fd = dofhelper_open();
  if (fd < 0)
    return -1;
  probe = ioctl(fd, DTRACEHIOC_ADDDOF, &dh);
  close(fd);
  if (PrintDTraceDOF) {
    printDOFHelper(&dh);
    tty->print_cr("DOF helper id = %d", probe);
  }
  return probe;
}

int DTraceJSDT::pd_activate(
    void* moduleBaseAddress, jstring module,
    jint providers_count, JVM_DTraceProvider* providers) {

  // We need sections:
  //  (1) STRTAB
  //  (
  //    (2) PROVIDER
  //    (3) PROBES
  //    (4) PROBOFFS
  //    (5) PROBARGS
  //  ) * Number of Providers

  // Type of sections we create
  enum {
    STRTAB = 0,
    PROVIDERS = 1,
    PROBES = 2,
    PROBE_OFFSETS = 3,
    ARG_OFFSETS = 4,
    NUM_SECTIONS = 5
  };

  static int alignment_for[NUM_SECTIONS] = { 1, 4, 8, 4, 1 };

  ResourceMark rm;

  uint32_t num_sections = 1 + 4 * providers_count;
  uint32_t offset = sizeof(dof_hdr_t) + (num_sections * sizeof(dof_sec_t));
  uint32_t* secoffs = NEW_RESOURCE_ARRAY(uint32_t, num_sections);
  uint32_t* secsize = NEW_RESOURCE_ARRAY(uint32_t, num_sections);

  // Store offsets of all strings here in such order:
  //  zero-string (always 0)
  //  provider1-name
  //    probe1-function
  //    probe1-name
  //    arg-1
  //    arg-2
  //    ...
  //    probe2-function
  //    probe2-name
  //    arg-1
  //    arg-2
  //  provider2-name
  //    ...

  uint32_t strcount  = 0;
  // Count the number of strings we'll need
  for(int prvc = 0; prvc < providers_count; ++prvc) {
    JVM_DTraceProvider* provider = &providers[prvc];
    // Provider name
    ++strcount;
    for(int prbc = 0; prbc < provider->probe_count; ++prbc) {
      JVM_DTraceProbe* p = &(provider->probes[prbc]);
      Symbol* sig = Method::resolve_jmethod_id(p->method)->signature();
      // function + name + one per argument
      strcount += 2 + ArgumentCount(sig).size();
    }
  }

  // Create place for string offsets
  uint32_t* stroffs = NEW_RESOURCE_ARRAY(uint32_t, strcount + 1);
  uint32_t string_index = 0;
  uint32_t curstr = 0;

  // First we need an empty string: ""
  stroffs[curstr++] = string_index;
  string_index += strlen("") + 1;

  for(int prvc = 0; prvc < providers_count; ++prvc) {
    JVM_DTraceProvider* provider = &providers[prvc];
    char* provider_name = java_lang_String::as_utf8_string(
        JNIHandles::resolve_non_null(provider->name));
    stroffs[curstr++] = string_index;
    string_index += strlen(provider_name) + 1;

    // All probes
    for(int prbc = 0; prbc < provider->probe_count; ++prbc) {
      JVM_DTraceProbe* p = &(provider->probes[prbc]);

      char* function = java_lang_String::as_utf8_string(
          JNIHandles::resolve_non_null(p->function));
      stroffs[curstr++] = string_index;
      string_index += strlen(function) + 1;

      char* name = java_lang_String::as_utf8_string(
          JNIHandles::resolve_non_null(p->name));
      stroffs[curstr++] = string_index;
      string_index += strlen(name) + 1;

      Symbol* sig = Method::resolve_jmethod_id(p->method)->signature();
      SignatureStream ss(sig);
      for ( ; !ss.at_return_type(); ss.next()) {
        BasicType bt = ss.type();
        const char* t = NULL;
        if (bt == T_OBJECT &&
            ss.as_symbol_or_null() == vmSymbols::java_lang_String()) {
          t = string_sig;
        } else if (bt == T_LONG) {
          t = long_sig;
        } else {
          t = int_sig;
        }
        stroffs[curstr++] = string_index;
        string_index += strlen(t) + 1;
      }
    }
  }
  secoffs[STRTAB] = offset;
  secsize[STRTAB] = string_index;
  offset += string_index;

  // Calculate the size of the rest
  for(int prvc = 0; prvc < providers_count; ++prvc) {
    JVM_DTraceProvider* provider = &providers[prvc];
    size_t provider_sec  = PROVIDERS     + prvc * 4;
    size_t probe_sec     = PROBES        + prvc * 4;
    size_t probeoffs_sec = PROBE_OFFSETS + prvc * 4;
    size_t argoffs_sec   = ARG_OFFSETS   + prvc * 4;

    // Allocate space for the provider data struction
    secoffs[provider_sec] = align_size_up(offset, alignment_for[PROVIDERS]);
    secsize[provider_sec] = sizeof(dof_provider_t);
    offset = secoffs[provider_sec] + secsize[provider_sec];

    // Allocate space for all the probes
    secoffs[probe_sec] = align_size_up(offset, alignment_for[PROBES]);
    secsize[probe_sec] = sizeof(dof_probe_t) * provider->probe_count;
    offset = secoffs[probe_sec] + secsize[probe_sec];

    // Allocate space for the probe offsets
    secoffs[probeoffs_sec] = align_size_up(offset, alignment_for[PROBE_OFFSETS]);
    secsize[probeoffs_sec] = sizeof(uint32_t) * provider->probe_count;
    offset = secoffs[probeoffs_sec] + secsize[probeoffs_sec];

    // We need number of arguments argoffs
    uint32_t argscount = 0;
    for(int prbc = 0; prbc < provider->probe_count; ++prbc) {
       JVM_DTraceProbe* p = &(provider->probes[prbc]);
       Symbol* sig = Method::resolve_jmethod_id(p->method)->signature();
       argscount += ArgumentCount(sig).size();
    }
    secoffs[argoffs_sec] = align_size_up(offset, alignment_for[ARG_OFFSETS]);
    secsize[argoffs_sec] = sizeof(uint8_t) * argscount;
    offset = secoffs[argoffs_sec] + secsize[argoffs_sec];
  }

  uint32_t size = offset;

  uint8_t* dof = NEW_RESOURCE_ARRAY(uint8_t, size);
  if (!dof) {
    return -1;
  }
  memset((void*)dof, 0, size);

  // Fill memory with proper values
  dof_hdr_t* hdr = (dof_hdr_t*)dof;
  hdr->dofh_ident[DOF_ID_MAG0]     = DOF_MAG_MAG0;
  hdr->dofh_ident[DOF_ID_MAG1]     = DOF_MAG_MAG1;
  hdr->dofh_ident[DOF_ID_MAG2]     = DOF_MAG_MAG2;
  hdr->dofh_ident[DOF_ID_MAG3]     = DOF_MAG_MAG3;
  hdr->dofh_ident[DOF_ID_MODEL]    = DOF_MODEL_NATIVE;  // No variants
  hdr->dofh_ident[DOF_ID_ENCODING] = DOF_ENCODE_NATIVE; // No variants
  hdr->dofh_ident[DOF_ID_VERSION]  = DOF_VERSION_1;     // No variants
  hdr->dofh_ident[DOF_ID_DIFVERS]  = DIF_VERSION_2;     // No variants
  // all other fields of ident to zero

  hdr->dofh_flags   = 0;
  hdr->dofh_hdrsize = sizeof(dof_hdr_t);
  hdr->dofh_secsize = sizeof(dof_sec_t);
  hdr->dofh_secnum  = num_sections;
  hdr->dofh_secoff  = sizeof(dof_hdr_t);
  hdr->dofh_loadsz  = size;
  hdr->dofh_filesz  = size;

  // First section: STRTAB
  dof_sec_t* sec = (dof_sec_t*)(dof + sizeof(dof_hdr_t));
  sec->dofs_type    = DOF_SECT_STRTAB;
  sec->dofs_align   = alignment_for[STRTAB];
  sec->dofs_flags   = DOF_SECF_LOAD;
  sec->dofs_entsize = 0;
  sec->dofs_offset  = secoffs[STRTAB];
  sec->dofs_size    = secsize[STRTAB];
  // Make data for this section
  char* str = (char*)(dof + sec->dofs_offset);

  *str = 0; str += 1; // ""

  // Run through all strings again
  for(int prvc = 0; prvc < providers_count; ++prvc) {
    JVM_DTraceProvider* provider = &providers[prvc];
    char* provider_name = java_lang_String::as_utf8_string(
        JNIHandles::resolve_non_null(provider->name));
    strcpy(str, provider_name);
    str += strlen(provider_name) + 1;

    // All probes
    for(int prbc = 0; prbc < provider->probe_count; ++prbc) {
      JVM_DTraceProbe* p = &(provider->probes[prbc]);

      char* function = java_lang_String::as_utf8_string(
          JNIHandles::resolve_non_null(p->function));
      strcpy(str, function);
      str += strlen(str) + 1;

      char* name = java_lang_String::as_utf8_string(
          JNIHandles::resolve_non_null(p->name));
      strcpy(str, name);
      str += strlen(name) + 1;

      Symbol* sig = Method::resolve_jmethod_id(p->method)->signature();
      SignatureStream ss(sig);
      for ( ; !ss.at_return_type(); ss.next()) {
        BasicType bt = ss.type();
        const char* t;
        if (bt == T_OBJECT &&
            ss.as_symbol_or_null() == vmSymbols::java_lang_String()) {
          t = string_sig;
        } else if (bt == T_LONG) {
          t = long_sig;
        } else {
          t = int_sig;
        }
        strcpy(str, t);
        str += strlen(t) + 1;
      }
    }
  }

  curstr = 1;
  for(int prvc = 0; prvc < providers_count; ++prvc) {
    JVM_DTraceProvider* provider = &providers[prvc];
    size_t provider_sec  = PROVIDERS     + prvc * 4;
    size_t probe_sec     = PROBES        + prvc * 4;
    size_t probeoffs_sec = PROBE_OFFSETS + prvc * 4;
    size_t argoffs_sec   = ARG_OFFSETS   + prvc * 4;

    // PROVIDER ///////////////////////////////////////////////////////////////
    // Section header
    sec = (dof_sec_t*)
        (dof + sizeof(dof_hdr_t) + sizeof(dof_sec_t) * provider_sec);
    sec->dofs_type    = DOF_SECT_PROVIDER;
    sec->dofs_align   = alignment_for[PROVIDERS];
    sec->dofs_flags   = DOF_SECF_LOAD;
    sec->dofs_entsize = 0;
    sec->dofs_offset  = secoffs[provider_sec];
    sec->dofs_size    = secsize[provider_sec];
    // Make provider decriiption
    dof_provider_t* prv = (dof_provider_t*)(dof + sec->dofs_offset);
    prv->dofpv_strtab   = STRTAB;
    prv->dofpv_probes   = probe_sec;
    prv->dofpv_prargs   = argoffs_sec;
    prv->dofpv_proffs   = probeoffs_sec;
    prv->dofpv_name     = stroffs[curstr++]; // Index in string table
    prv->dofpv_provattr = DOF_ATTR(
        provider->providerAttributes.nameStability,
        provider->providerAttributes.dataStability,
        provider->providerAttributes.dependencyClass);
    prv->dofpv_modattr = DOF_ATTR(
        provider->moduleAttributes.nameStability,
        provider->moduleAttributes.dataStability,
        provider->moduleAttributes.dependencyClass);
    prv->dofpv_funcattr = DOF_ATTR(
        provider->functionAttributes.nameStability,
        provider->functionAttributes.dataStability,
        provider->functionAttributes.dependencyClass);
    prv->dofpv_nameattr = DOF_ATTR(
        provider->nameAttributes.nameStability,
        provider->nameAttributes.dataStability,
        provider->nameAttributes.dependencyClass);
    prv->dofpv_argsattr = DOF_ATTR(
        provider->argsAttributes.nameStability,
        provider->argsAttributes.dataStability,
        provider->argsAttributes.dependencyClass);

    // PROBES /////////////////////////////////////////////////////////////////
    // Section header
    sec = (dof_sec_t*)
        (dof + sizeof(dof_hdr_t) + sizeof(dof_sec_t) * probe_sec);
    sec->dofs_type    = DOF_SECT_PROBES;
    sec->dofs_align   = alignment_for[PROBES];
    sec->dofs_flags   = DOF_SECF_LOAD;
    sec->dofs_entsize = sizeof(dof_probe_t);
    sec->dofs_offset  = secoffs[probe_sec];
    sec->dofs_size    = secsize[probe_sec];
    // Make probes descriptions
    uint32_t argsoffs = 0;
    for(int prbc = 0; prbc < provider->probe_count; ++prbc) {
      JVM_DTraceProbe* probe = &(provider->probes[prbc]);
      Method* m = Method::resolve_jmethod_id(probe->method);
      int arg_count = ArgumentCount(m->signature()).size();
      assert(m->code() != NULL, "must have an nmethod");

      dof_probe_t* prb =
         (dof_probe_t*)(dof + sec->dofs_offset + prbc * sizeof(dof_probe_t));

      prb->dofpr_addr   = (uint64_t)m->code()->entry_point();
      prb->dofpr_func   = stroffs[curstr++]; // Index in string table
      prb->dofpr_name   = stroffs[curstr++]; // Index in string table
      prb->dofpr_nargv  = stroffs[curstr  ]; // Index in string table
      // We spent siglen strings here
      curstr += arg_count;
      prb->dofpr_xargv  = prb->dofpr_nargv;  // Same bunch of strings
      prb->dofpr_argidx = argsoffs;
      prb->dofpr_offidx = prbc;
      prb->dofpr_nargc  = arg_count;
      prb->dofpr_xargc  = arg_count;
      prb->dofpr_noffs  = 1; // Number of offsets
      // Next bunch of offsets
      argsoffs += arg_count;
    }

    // PROFFS /////////////////////////////////////////////////////////////////
    // Section header
    sec = (dof_sec_t*)
        (dof + sizeof(dof_hdr_t) + sizeof(dof_sec_t) * probeoffs_sec);
    sec->dofs_type    = DOF_SECT_PROFFS;
    sec->dofs_align   = alignment_for[PROBE_OFFSETS];
    sec->dofs_flags   = DOF_SECF_LOAD;
    sec->dofs_entsize = sizeof(uint32_t);
    sec->dofs_offset  = secoffs[probeoffs_sec];
    sec->dofs_size    = secsize[probeoffs_sec];
    // Make offsets
    for (int prbc = 0; prbc < provider->probe_count; ++prbc) {
      uint32_t* pof =
          (uint32_t*)(dof + sec->dofs_offset + sizeof(uint32_t) * prbc);
      JVM_DTraceProbe* probe = &(provider->probes[prbc]);
      Method* m = Method::resolve_jmethod_id(probe->method);
      *pof = m->code()->trap_offset();
    }

    // PRARGS /////////////////////////////////////////////////////////////////
    // Section header
    sec = (dof_sec_t*)
        (dof + sizeof(dof_hdr_t) + sizeof(dof_sec_t) * argoffs_sec);
    sec->dofs_type    = DOF_SECT_PRARGS;
    sec->dofs_align   = alignment_for[ARG_OFFSETS];
    sec->dofs_flags   = DOF_SECF_LOAD;
    sec->dofs_entsize = sizeof(uint8_t);
    sec->dofs_offset  = secoffs[argoffs_sec];
    sec->dofs_size    = secsize[argoffs_sec];
    // Make arguments
    uint8_t* par = (uint8_t*)(dof + sec->dofs_offset);
    for (int prbc = 0; prbc < provider->probe_count; ++prbc) {
      JVM_DTraceProbe* p = &(provider->probes[prbc]);
      Symbol* sig = Method::resolve_jmethod_id(p->method)->signature();
      uint8_t count = (uint8_t)ArgumentCount(sig).size();
      for (uint8_t i = 0; i < count; ++i) {
        *par++ = i;
      }
    }
  }

  // Register module
  return dof_register(module, dof, moduleBaseAddress);
}


void DTraceJSDT::pd_dispose(int handle) {
  int fd;
  if (handle == -1) {
    return;
  }
  fd = dofhelper_open();
  if (fd < 0)
    return;
  ioctl(fd, DTRACEHIOC_REMOVE, handle);
  close(fd);
}

jboolean DTraceJSDT::pd_is_supported() {
  int fd = dofhelper_open();
  if (fd < 0) {
    return false;
  }
  close(fd);
  return true;
}

static const char* dofSecTypeFor(uint32_t type) {
  switch (type) {
    case 0:  return "DOF_SECT_NONE";
    case 1:  return "DOF_SECT_COMMENTS";
    case 2:  return "DOF_SECT_SOURCE";
    case 3:  return "DOF_SECT_ECBDESC";
    case 4:  return "DOF_SECT_PROBEDESC";
    case 5:  return "DOF_SECT_ACTDESC";
    case 6:  return "DOF_SECT_DIFOHDR";
    case 7:  return "DOF_SECT_DIF";
    case 8:  return "DOF_SECT_STRTAB";
    case 9:  return "DOF_SECT_VARTAB";
    case 10: return "DOF_SECT_RELTAB";
    case 11: return "DOF_SECT_TYPETAB";
    case 12: return "DOF_SECT_URELHDR";
    case 13: return "DOF_SECT_KRELHDR";
    case 14: return "DOF_SECT_OPTDESC";
    case 15: return "DOF_SECT_PROVIDER";
    case 16: return "DOF_SECT_PROBES";
    case 17: return "DOF_SECT_PRARGS";
    case 18: return "DOF_SECT_PROFFS";
    case 19: return "DOF_SECT_INTTAB";
    case 20: return "DOF_SECT_UTSNAME";
    case 21: return "DOF_SECT_XLTAB";
    case 22: return "DOF_SECT_XLMEMBERS";
    case 23: return "DOF_SECT_XLIMPORT";
    case 24: return "DOF_SECT_XLEXPORT";
    case 25: return "DOF_SECT_PREXPORT";
    case 26: return "DOF_SECT_PRENOFFS";
    default: return "<unknown>";
  }
}

static void printDOFStringTabSec(void* dof, dof_sec_t* sec) {
  size_t tab = sec->dofs_offset;
  size_t limit = sec->dofs_size;
  tty->print_cr("//   String Table:");
  for (size_t idx = 0; idx < limit; /*empty*/) {
    char* str = ((char*)dof) + tab + idx;
    tty->print_cr("//   [0x%x + 0x%x] '%s'", tab, idx, str);
    idx += strlen(str) + 1;
  }
}

static void printDOFProviderSec(void* dof, dof_sec_t* sec) {
  dof_provider_t* prov = (dof_provider_t*)((char*)dof + sec->dofs_offset);
  tty->print_cr("//   dof_provider_t {");
  tty->print_cr("//     dofpv_strtab = %d", prov->dofpv_strtab);
  tty->print_cr("//     dofpv_probes = %d", prov->dofpv_probes);
  tty->print_cr("//     dofpv_prargs = %d", prov->dofpv_prargs);
  tty->print_cr("//     dofpv_proffs = %d", prov->dofpv_proffs);
  tty->print_cr("//     dofpv_name = 0x%x", prov->dofpv_name);
  tty->print_cr("//     dofpv_provattr = 0x%08x", prov->dofpv_provattr);
  tty->print_cr("//     dofpv_modattr = 0x%08x", prov->dofpv_modattr);
  tty->print_cr("//     dofpv_funcattr = 0x%08x", prov->dofpv_funcattr);
  tty->print_cr("//     dofpv_nameattr = 0x%08x", prov->dofpv_nameattr);
  tty->print_cr("//     dofpv_argsattr = 0x%08x", prov->dofpv_argsattr);
  tty->print_cr("//   }");
}

static void printDOFProbesSec(void* dof, dof_sec_t* sec) {
  size_t idx = sec->dofs_offset;
  size_t limit = idx + sec->dofs_size;
  for (size_t idx = sec->dofs_offset; idx < limit; idx += sec->dofs_entsize) {
    dof_probe_t* prb = (dof_probe_t*)((char*)dof + idx);
    tty->print_cr("//   dof_probe_t {");
    tty->print_cr("//     dofpr_addr = 0x%016llx", prb->dofpr_addr);
    tty->print_cr("//     dofpr_func = 0x%x", prb->dofpr_func);
    tty->print_cr("//     dofpr_name = 0x%x", prb->dofpr_name);
    tty->print_cr("//     dofpr_nargv = 0x%x", prb->dofpr_nargv);
    tty->print_cr("//     dofpr_xargv = 0x%x", prb->dofpr_xargv);
    tty->print_cr("//     dofpr_argidx = 0x%x", prb->dofpr_argidx);
    tty->print_cr("//     dofpr_offidx = 0x%x", prb->dofpr_offidx);
    tty->print_cr("//     dofpr_nargc = %d", prb->dofpr_nargc);
    tty->print_cr("//     dofpr_xargc = %d", prb->dofpr_xargc);
    tty->print_cr("//     dofpr_noffs = %d", prb->dofpr_noffs);
    tty->print_cr("//   }");
  }
}

static void printDOFOffsetsSec(void* dof, dof_sec_t* sec) {
  size_t tab = sec->dofs_offset;
  size_t limit = sec->dofs_size;
  tty->print_cr("//   Offsets:");
  for (size_t idx = 0; idx < limit; idx += sec->dofs_entsize) {
    uint32_t* off = (uint32_t*)((char*)dof + tab + idx);
    tty->print_cr("//   [0x%x + 0x%x]: %d", tab, idx, *off);
  }
}

static void printDOFArgsSec(void* dof, dof_sec_t* sec) {
  size_t tab = sec->dofs_offset;
  size_t limit = sec->dofs_size;
  tty->print_cr("//   Arguments:");
  for (size_t idx = 0; idx < limit; idx += sec->dofs_entsize) {
    uint8_t* arg = (uint8_t*)((char*)dof + tab + idx);
    tty->print_cr("//   [0x%x + 0x%x]: %d", tab, idx, *arg);
  }
}

static void printDOFSection(void* dof, dof_sec_t* sec) {
  tty->print_cr("//   dof_sec_t {");
  tty->print_cr("//     dofs_type = 0x%x /* %s */",
                sec->dofs_type, dofSecTypeFor(sec->dofs_type));
  tty->print_cr("//     dofs_align = %d", sec->dofs_align);
  tty->print_cr("//     dofs_flags = 0x%x", sec->dofs_flags);
  tty->print_cr("//     dofs_entsize = %d", sec->dofs_entsize);
  tty->print_cr("//     dofs_offset = 0x%llx", sec->dofs_offset);
  tty->print_cr("//     dofs_size = %lld", sec->dofs_size);
  tty->print_cr("//   }");
  switch (sec->dofs_type) {
    case DOF_SECT_STRTAB:    printDOFStringTabSec(dof, sec); break;
    case DOF_SECT_PROVIDER:  printDOFProviderSec(dof, sec);  break;
    case DOF_SECT_PROBES:    printDOFProbesSec(dof, sec);    break;
    case DOF_SECT_PROFFS:    printDOFOffsetsSec(dof, sec);   break;
    case DOF_SECT_PRARGS:    printDOFArgsSec(dof, sec);      break;
    default: tty->print_cr("//   <section type not recognized>");
  }
}

static void printDOFHeader(dof_hdr_t* hdr) {
  tty->print_cr("//   dof_hdr_t {");
  tty->print_cr("//     dofh_ident[DOF_ID_MAG0] = 0x%x",
                hdr->dofh_ident[DOF_ID_MAG0]);
  tty->print_cr("//     dofh_ident[DOF_ID_MAG1] = 0x%x",
                hdr->dofh_ident[DOF_ID_MAG1]);
  tty->print_cr("//     dofh_ident[DOF_ID_MAG2] = 0x%x",
                hdr->dofh_ident[DOF_ID_MAG2]);
  tty->print_cr("//     dofh_ident[DOF_ID_MAG3] = 0x%x",
                hdr->dofh_ident[DOF_ID_MAG3]);
  tty->print_cr("//     dofh_ident[DOF_ID_MODEL] = 0x%x",
                hdr->dofh_ident[DOF_ID_MODEL]);
  tty->print_cr("//     dofh_ident[DOF_ID_ENCODING] = 0x%x",
                hdr->dofh_ident[DOF_ID_ENCODING]);
  tty->print_cr("//     dofh_ident[DOF_ID_VERSION] = 0x%x",
                hdr->dofh_ident[DOF_ID_VERSION]);
  tty->print_cr("//     dofh_ident[DOF_ID_DIFVERS] = 0x%x",
                hdr->dofh_ident[DOF_ID_DIFVERS]);
  tty->print_cr("//     dofh_flags = 0x%x", hdr->dofh_flags);
  tty->print_cr("//     dofh_hdrsize = %d", hdr->dofh_hdrsize);
  tty->print_cr("//     dofh_secsize = %d", hdr->dofh_secsize);
  tty->print_cr("//     dofh_secnum = %d", hdr->dofh_secnum);
  tty->print_cr("//     dofh_secoff = %lld", hdr->dofh_secoff);
  tty->print_cr("//     dofh_loadsz = %lld", hdr->dofh_loadsz);
  tty->print_cr("//     dofh_filesz = %lld", hdr->dofh_filesz);
  tty->print_cr("//   }");
}

static void printDOF(void* dof) {
  dof_hdr_t* hdr = (dof_hdr_t*)dof;
  printDOFHeader(hdr);
  for (int i = 0; i < hdr->dofh_secnum; ++i) {
    dof_sec_t* sec =
      (dof_sec_t*)((char*)dof + sizeof(dof_hdr_t) + i * sizeof(dof_sec_t));
    tty->print_cr("//   [Section #%d]", i);
    printDOFSection(dof, sec);
  }
}

static void printDOFHelper(dof_helper_t* helper) {
  tty->print_cr("// dof_helper_t {");
  tty->print_cr("//   dofhp_mod = \"%s\"", helper->dofhp_mod);
  tty->print_cr("//   dofhp_addr = 0x%016llx", helper->dofhp_addr);
  tty->print_cr("//   dofhp_dof = 0x%016llx", helper->dofhp_dof);
  printDOF((void*)helper->dofhp_dof);
  tty->print_cr("// }");
  size_t len = ((dof_hdr_t*)helper)->dofh_loadsz;
  tty->print_data((void*)helper->dofhp_dof, len, true);
}

#else // ndef HAVE_DTRACE_H

// Get here if we're not building on at least Solaris 10
int DTraceJSDT::pd_activate(
  void* baseAddress, jstring module,
  jint provider_count, JVM_DTraceProvider* providers) {
  return -1;
}

void DTraceJSDT::pd_dispose(int handle) {
}

jboolean DTraceJSDT::pd_is_supported() {
  return false;
}
#endif
