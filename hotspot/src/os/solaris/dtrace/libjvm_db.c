/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <gelf.h>

#include "libjvm_db.h"
#include "JvmOffsets.h"

#define LIBJVM_SO "libjvm.so"

#if defined(i386) || defined(__i386) || defined(__amd64)
#ifdef COMPILER2
#define X86_COMPILER2
#endif /* COMPILER2 */
#endif /* i386 */

typedef struct {
    short     vf_cnt; /* number of recognized java vframes */
    short     bci;    /* current frame method byte code index */
    int       line;   /* current frame method source line */
    uint64_t new_fp; /* fp for the next frame */
    uint64_t new_pc; /* pc for the next frame */
    uint64_t new_sp; /* "raw" sp for the next frame (includes extension by interpreter/adapter */
    char      locinf; /* indicates there is valid location info */
} Jframe_t;

int Jlookup_by_regs(jvm_agent_t* J, const prgregset_t regs, char *name,
                    size_t size, Jframe_t *jframe);

int main(int arg) { return arg; }

static int debug = 0;

static void failed(int err, const char * file, int line) {
  if (debug) {
    fprintf(stderr, "failed %d at %s:%d\n", err, file, line);
  }
}

static void warn(const char * file, int line, const char * msg) {
  if (debug) {
    fprintf(stderr, "warning: %s at %s:%d\n", msg, file, line);
  }
}

static void warn1(const char * file, int line, const char * msg, intptr_t arg1) {
  if (debug) {
    fprintf(stderr, "warning: ");
    fprintf(stderr, msg, arg1);
    fprintf(stderr, " at %s:%d\n", file, line);
  }
}

#define CHECK_FAIL(err) \
        if (err != PS_OK) { failed(err, __FILE__, __LINE__); goto fail; }
#define WARN(msg)  warn(__FILE__, __LINE__, msg)
#define WARN1(msg, arg1)  warn1(__FILE__, __LINE__, msg, arg1)

typedef struct VMStructEntry {
  const char * typeName;           /* The type name containing the given field (example: "Klass") */
  const char * fieldName;          /* The field name within the type           (example: "_name") */
  uint64_t address;                /* Address of field; only used for static fields */
                                   /* ("offset" can not be reused because of apparent SparcWorks compiler bug */
                                   /* in generation of initializer data) */
} VMStructEntry;

/* Prototyping inlined methods */

int sprintf(char *s, const char *format, ...);

#define SZ16  sizeof(int16_t)
#define SZ32  sizeof(int32_t)

#define COMP_METHOD_SIGN '*'

#define MAX_VFRAMES_CNT 256

typedef struct vframe {
  uint64_t method;
  int32_t  sender_decode_offset;
  int32_t  methodIdx;
  int32_t  bci;
  int32_t  line;
} Vframe_t;

typedef struct frame {
  uintptr_t fp;
  uintptr_t pc;
  uintptr_t sp;
  uintptr_t sender_sp; // The unextended sp of the caller
} Frame_t;

typedef struct Nmethod_t {
  struct jvm_agent* J;
  Jframe_t *jframe;

  uint64_t nm;                  /* _nmethod */
  uint64_t pc;
  uint64_t pc_desc;

  int32_t  orig_pc_offset;      /* _orig_pc_offset */
  int32_t  instrs_beg;          /* _code_offset */
  int32_t  instrs_end;
  int32_t  deopt_beg;           /* _deoptimize_offset */
  int32_t  scopes_data_beg;     /* _scopes_data_offset */
  int32_t  scopes_data_end;
  int32_t  metadata_beg;        /* _metadata_offset */
  int32_t  metadata_end;
  int32_t  scopes_pcs_beg;      /* _scopes_pcs_offset */
  int32_t  scopes_pcs_end;

  int      vf_cnt;
  Vframe_t vframes[MAX_VFRAMES_CNT];
} Nmethod_t;

struct jvm_agent {
  struct ps_prochandle* P;

  uint64_t nmethod_vtbl;
  uint64_t CodeBlob_vtbl;
  uint64_t BufferBlob_vtbl;
  uint64_t RuntimeStub_vtbl;
  uint64_t Method_vtbl;

  uint64_t Use_Compressed_Oops_address;
  uint64_t Universe_narrow_oop_base_address;
  uint64_t Universe_narrow_oop_shift_address;
  uint64_t CodeCache_heaps_address;

  /* Volatiles */
  uint8_t  Use_Compressed_Oops;
  uint64_t Universe_narrow_oop_base;
  uint32_t Universe_narrow_oop_shift;
  // Code cache heaps
  int32_t  Number_of_heaps;
  uint64_t* Heap_low;
  uint64_t* Heap_high;
  uint64_t* Heap_segmap_low;
  uint64_t* Heap_segmap_high;

  int32_t  SIZE_CodeCache_log2_segment;

  uint64_t methodPtr;
  uint64_t bcp;

  Nmethod_t *N;                 /*Inlined methods support */
  Frame_t   prev_fr;
  Frame_t   curr_fr;
};

static int
read_string(struct ps_prochandle *P,
        char *buf,              /* caller's buffer */
        size_t size,            /* upper limit on bytes to read */
        uintptr_t addr)         /* address in process */
{
  int err = PS_OK;
  while (size-- > 1 && err == PS_OK) {
    err = ps_pread(P, addr, buf, 1);
    if (*buf == '\0') {
      return PS_OK;
    }
    addr += 1;
    buf += 1;
  }
  return -1;
}

static int read_compressed_pointer(jvm_agent_t* J, uint64_t base, uint32_t *ptr) {
  int err = -1;
  uint32_t ptr32;
  err = ps_pread(J->P, base, &ptr32, sizeof(uint32_t));
  *ptr = ptr32;
  return err;
}

static int read_pointer(jvm_agent_t* J, uint64_t base, uint64_t* ptr) {
  int err = -1;
  uint32_t ptr32;

  switch (DATA_MODEL) {
  case PR_MODEL_LP64:
    err = ps_pread(J->P, base, ptr, sizeof(uint64_t));
    break;
  case PR_MODEL_ILP32:
    err = ps_pread(J->P, base, &ptr32, sizeof(uint32_t));
    *ptr = ptr32;
    break;
  }

  return err;
}

static int read_string_pointer(jvm_agent_t* J, uint64_t base, const char ** stringp) {
  uint64_t ptr;
  int err;
  char buffer[1024];

  *stringp = NULL;
  err = read_pointer(J, base, &ptr);
  CHECK_FAIL(err);
  if (ptr != 0) {
    err = read_string(J->P, buffer, sizeof(buffer), ptr);
    CHECK_FAIL(err);
    *stringp = strdup(buffer);
  }
  return PS_OK;

 fail:
  return err;
}

static int parse_vmstruct_entry(jvm_agent_t* J, uint64_t base, VMStructEntry* vmp) {
  uint64_t ptr;
  int err;

  err = read_string_pointer(J, base + OFFSET_VMStructEntrytypeName, &vmp->typeName);
  CHECK_FAIL(err);
  err = read_string_pointer(J, base + OFFSET_VMStructEntryfieldName, &vmp->fieldName);
  CHECK_FAIL(err);
  err = read_pointer(J, base + OFFSET_VMStructEntryaddress, &vmp->address);
  CHECK_FAIL(err);

  return PS_OK;

 fail:
  if (vmp->typeName != NULL) free((void*)vmp->typeName);
  if (vmp->fieldName != NULL) free((void*)vmp->fieldName);
  return err;
}

static int parse_vmstructs(jvm_agent_t* J) {
  VMStructEntry  vmVar;
  VMStructEntry* vmp = &vmVar;
  uint64_t gHotSpotVMStructs;
  psaddr_t sym_addr;
  uint64_t base;
  int err;

  /* Clear *vmp now in case we jump to fail: */
  memset(vmp, 0, sizeof(VMStructEntry));

  err = ps_pglobal_lookup(J->P, LIBJVM_SO, "gHotSpotVMStructs", &sym_addr);
  CHECK_FAIL(err);
  err = read_pointer(J, sym_addr, &gHotSpotVMStructs);
  CHECK_FAIL(err);
  base = gHotSpotVMStructs;

  err = PS_OK;
  while (err == PS_OK) {
    memset(vmp, 0, sizeof(VMStructEntry));
    err = parse_vmstruct_entry(J, base, vmp);
    if (err != PS_OK || vmp->typeName == NULL) {
      break;
    }

    if (vmp->typeName[0] == 'C' && strcmp("CodeCache", vmp->typeName) == 0) {
      /* Read _heaps field of type GrowableArray<CodeHeaps*>*      */
      if (strcmp("_heaps", vmp->fieldName) == 0) {
        err = read_pointer(J, vmp->address, &J->CodeCache_heaps_address);
      }
    } else if (vmp->typeName[0] == 'U' && strcmp("Universe", vmp->typeName) == 0) {
      if (strcmp("_narrow_oop._base", vmp->fieldName) == 0) {
        J->Universe_narrow_oop_base_address = vmp->address;
      }
      if (strcmp("_narrow_oop._shift", vmp->fieldName) == 0) {
        J->Universe_narrow_oop_shift_address = vmp->address;
      }
    }
    CHECK_FAIL(err);

    base += SIZE_VMStructEntry;
    if (vmp->typeName != NULL) free((void*)vmp->typeName);
    if (vmp->fieldName != NULL) free((void*)vmp->fieldName);
  }

  return PS_OK;

 fail:
  if (vmp->typeName != NULL) free((void*)vmp->typeName);
  if (vmp->fieldName != NULL) free((void*)vmp->fieldName);
  return -1;
}

static int find_symbol(jvm_agent_t* J, const char *name, uint64_t* valuep) {
  psaddr_t sym_addr;
  int err;

  err = ps_pglobal_lookup(J->P, LIBJVM_SO, name, &sym_addr);
  if (err != PS_OK) goto fail;
  *valuep = sym_addr;
  return PS_OK;

 fail:
  return err;
}

static int read_volatiles(jvm_agent_t* J) {
  int i;
  uint64_t array_data;
  uint64_t code_heap_address;
  int err;

  err = find_symbol(J, "UseCompressedOops", &J->Use_Compressed_Oops_address);
  if (err == PS_OK) {
    err = ps_pread(J->P,  J->Use_Compressed_Oops_address, &J->Use_Compressed_Oops, sizeof(uint8_t));
    CHECK_FAIL(err);
  } else {
    J->Use_Compressed_Oops = 0;
  }

  err = read_pointer(J, J->Universe_narrow_oop_base_address, &J->Universe_narrow_oop_base);
  CHECK_FAIL(err);
  err = ps_pread(J->P,  J->Universe_narrow_oop_shift_address, &J->Universe_narrow_oop_shift, sizeof(uint32_t));
  CHECK_FAIL(err);

  /* CodeCache_heaps_address points to GrowableArray<CodeHeaps*>, read _data field
     pointing to the first entry of type CodeCache* in the array */
  err = read_pointer(J, J->CodeCache_heaps_address + OFFSET_GrowableArray_CodeHeap_data, &array_data);
  /* Read _len field containing the number of code heaps */
  err = ps_pread(J->P, J->CodeCache_heaps_address + OFFSET_GrowableArray_CodeHeap_len,
                 &J->Number_of_heaps, sizeof(J->Number_of_heaps));

  /* Allocate memory for heap configurations */
  J->Heap_low         = (uint64_t*)calloc(J->Number_of_heaps, sizeof(uint64_t));
  J->Heap_high        = (uint64_t*)calloc(J->Number_of_heaps, sizeof(uint64_t));
  J->Heap_segmap_low  = (uint64_t*)calloc(J->Number_of_heaps, sizeof(uint64_t));
  J->Heap_segmap_high = (uint64_t*)calloc(J->Number_of_heaps, sizeof(uint64_t));

  /* Read code heap configurations */
  for (i = 0; i < J->Number_of_heaps; ++i) {
    /* Read address of heap */
    err = read_pointer(J, array_data, &code_heap_address);
    CHECK_FAIL(err);

    err = read_pointer(J, code_heap_address + OFFSET_CodeHeap_memory +
                       OFFSET_VirtualSpace_low, &J->Heap_low[i]);
    CHECK_FAIL(err);
    err = read_pointer(J, code_heap_address + OFFSET_CodeHeap_memory +
                       OFFSET_VirtualSpace_high, &J->Heap_high[i]);
    CHECK_FAIL(err);
    err = read_pointer(J, code_heap_address + OFFSET_CodeHeap_segmap +
                       OFFSET_VirtualSpace_low, &J->Heap_segmap_low[i]);
    CHECK_FAIL(err);
    err = read_pointer(J, code_heap_address + OFFSET_CodeHeap_segmap +
                       OFFSET_VirtualSpace_high, &J->Heap_segmap_high[i]);
    CHECK_FAIL(err);

    /* Increment pointer to next entry */
    array_data = array_data + POINTER_SIZE;
  }

  err = ps_pread(J->P, code_heap_address + OFFSET_CodeHeap_log2_segment_size,
                 &J->SIZE_CodeCache_log2_segment, sizeof(J->SIZE_CodeCache_log2_segment));
  CHECK_FAIL(err);

  return PS_OK;

 fail:
  return err;
}

static int codeheap_contains(int heap_num, jvm_agent_t* J, uint64_t ptr) {
  return (J->Heap_low[heap_num] <= ptr && ptr < J->Heap_high[heap_num]);
}

static int codecache_contains(jvm_agent_t* J, uint64_t ptr) {
  int i;
  for (i = 0; i < J->Number_of_heaps; ++i) {
    if (codeheap_contains(i, J, ptr)) {
      return 1;
    }
  }
  return 0;
}

static uint64_t segment_for(int heap_num, jvm_agent_t* J, uint64_t p) {
  return (p - J->Heap_low[heap_num]) >> J->SIZE_CodeCache_log2_segment;
}

static uint64_t block_at(int heap_num, jvm_agent_t* J, int i) {
  return J->Heap_low[heap_num] + (i << J->SIZE_CodeCache_log2_segment);
}

static int find_start(jvm_agent_t* J, uint64_t ptr, uint64_t *startp) {
  int err;
  int i;

  for (i = 0; i < J->Number_of_heaps; ++i) {
    *startp = 0;
    if (codeheap_contains(i, J, ptr)) {
      int32_t used;
      uint64_t segment = segment_for(i, J, ptr);
      uint64_t block = J->Heap_segmap_low[i];
      uint8_t tag;
      err = ps_pread(J->P, block + segment, &tag, sizeof(tag));
      CHECK_FAIL(err);
      if (tag == 0xff)
        return PS_OK;
      while (tag > 0) {
        err = ps_pread(J->P, block + segment, &tag, sizeof(tag));
        CHECK_FAIL(err);
        segment -= tag;
      }
      block = block_at(i, J, segment);
      err = ps_pread(J->P, block + OFFSET_HeapBlockHeader_used, &used, sizeof(used));
      CHECK_FAIL(err);
      if (used) {
        *startp = block + SIZE_HeapBlockHeader;
      }
    }
    return PS_OK;
  }

 fail:
  return -1;
}

static int find_jlong_constant(jvm_agent_t* J, const char *name, uint64_t* valuep) {
  psaddr_t sym_addr;
  int err = ps_pglobal_lookup(J->P, LIBJVM_SO, name, &sym_addr);
  if (err == PS_OK) {
    err = ps_pread(J->P, sym_addr, valuep, sizeof(uint64_t));
    return err;
  }
  *valuep = -1;
  return -1;
}

jvm_agent_t *Jagent_create(struct ps_prochandle *P, int vers) {
  jvm_agent_t* J;
  int err;

  if (vers != JVM_DB_VERSION) {
    errno = ENOTSUP;
    return NULL;
  }

  J = (jvm_agent_t*)calloc(sizeof(struct jvm_agent), 1);

  debug = getenv("LIBJVMDB_DEBUG") != NULL;
  if (debug) debug = 3;

  if (debug) {
      fprintf(stderr, "Jagent_create: debug=%d\n", debug);
#ifdef X86_COMPILER2
      fprintf(stderr, "Jagent_create: R_SP=%d, R_FP=%d, POINTER_SIZE=%d\n", R_SP, R_FP, POINTER_SIZE);
#endif  /* X86_COMPILER2 */
  }

  J->P = P;

  // Initialize the initial previous frame

  J->prev_fr.fp = 0;
  J->prev_fr.pc = 0;
  J->prev_fr.sp = 0;
  J->prev_fr.sender_sp = 0;

  err = find_symbol(J, "__1cHnmethodG__vtbl_", &J->nmethod_vtbl);
  CHECK_FAIL(err);
  err = find_symbol(J, "__1cKBufferBlobG__vtbl_", &J->BufferBlob_vtbl);
  if (err != PS_OK) J->BufferBlob_vtbl = 0;
  err = find_symbol(J, "__1cICodeBlobG__vtbl_", &J->CodeBlob_vtbl);
  CHECK_FAIL(err);
  err = find_symbol(J, "__1cLRuntimeStubG__vtbl_", &J->RuntimeStub_vtbl);
  CHECK_FAIL(err);
  err = find_symbol(J, "__1cGMethodG__vtbl_", &J->Method_vtbl);
  CHECK_FAIL(err);

  err = parse_vmstructs(J);
  CHECK_FAIL(err);
  err = read_volatiles(J);
  CHECK_FAIL(err);

  return J;

 fail:
  Jagent_destroy(J);
  return NULL;
}

void Jagent_destroy(jvm_agent_t *J) {
  if (J != NULL) {
    free(J);
  }
}

static int is_method(jvm_agent_t* J, uint64_t methodPtr) {
  uint64_t klass;
  int err = read_pointer(J, methodPtr, &klass);
  if (err != PS_OK) goto fail;
  return klass == J->Method_vtbl;

 fail:
  return 0;
}

static int
name_for_methodPtr(jvm_agent_t* J, uint64_t methodPtr, char * result, size_t size)
{
  short nameIndex;
  short signatureIndex;
  uint64_t constantPool;
  uint64_t constMethod;
  uint64_t nameSymbol;
  uint64_t signatureSymbol;
  uint64_t klassPtr;
  uint64_t klassSymbol;
  short klassSymbolLength;
  short nameSymbolLength;
  short signatureSymbolLength;
  char * nameString = NULL;
  char * klassString = NULL;
  char * signatureString = NULL;
  int err;

  err = read_pointer(J, methodPtr + OFFSET_Method_constMethod, &constMethod);
  CHECK_FAIL(err);
  err = read_pointer(J, constMethod + OFFSET_ConstMethod_constants, &constantPool);
  CHECK_FAIL(err);

  /* To get name string */
  err = ps_pread(J->P, constMethod + OFFSET_ConstMethod_name_index, &nameIndex, 2);
  CHECK_FAIL(err);
  err = read_pointer(J, constantPool + nameIndex * POINTER_SIZE + SIZE_ConstantPool, &nameSymbol);
  CHECK_FAIL(err);
  // The symbol is a CPSlot and has lower bit set to indicate metadata
  nameSymbol &= (~1); // remove metadata lsb
  err = ps_pread(J->P, nameSymbol + OFFSET_Symbol_length, &nameSymbolLength, 2);
  CHECK_FAIL(err);
  nameString = (char*)calloc(nameSymbolLength + 1, 1);
  err = ps_pread(J->P, nameSymbol + OFFSET_Symbol_body, nameString, nameSymbolLength);
  CHECK_FAIL(err);

  /* To get signature string */
  err = ps_pread(J->P, constMethod + OFFSET_ConstMethod_signature_index, &signatureIndex, 2);
  CHECK_FAIL(err);
  err = read_pointer(J, constantPool + signatureIndex * POINTER_SIZE + SIZE_ConstantPool, &signatureSymbol);
  CHECK_FAIL(err);
  signatureSymbol &= (~1);  // remove metadata lsb
  err = ps_pread(J->P, signatureSymbol + OFFSET_Symbol_length, &signatureSymbolLength, 2);
  CHECK_FAIL(err);
  signatureString = (char*)calloc(signatureSymbolLength + 1, 1);
  err = ps_pread(J->P, signatureSymbol + OFFSET_Symbol_body, signatureString, signatureSymbolLength);
  CHECK_FAIL(err);

  /* To get klass string */
  err = read_pointer(J, constantPool + OFFSET_ConstantPool_pool_holder, &klassPtr);
  CHECK_FAIL(err);
  err = read_pointer(J, klassPtr + OFFSET_Klass_name, &klassSymbol);
  CHECK_FAIL(err);
  err = ps_pread(J->P, klassSymbol + OFFSET_Symbol_length, &klassSymbolLength, 2);
  CHECK_FAIL(err);
  klassString = (char*)calloc(klassSymbolLength + 1, 1);
  err = ps_pread(J->P, klassSymbol + OFFSET_Symbol_body, klassString, klassSymbolLength);
  CHECK_FAIL(err);

  result[0] = '\0';
  if (snprintf(result, size,
    "%s.%s%s",
    klassString,
    nameString,
    signatureString) >= size) {
    // truncation
    goto fail;
  }

  if (nameString != NULL) free(nameString);
  if (klassString != NULL) free(klassString);
  if (signatureString != NULL) free(signatureString);

  return PS_OK;

 fail:
  if (debug) {
      fprintf(stderr, "name_for_methodPtr: FAIL \n\n");
  }
  if (nameString != NULL) free(nameString);
  if (klassString != NULL) free(klassString);
  if (signatureString != NULL) free(signatureString);
  return -1;
}

static int nmethod_info(Nmethod_t *N)
{
  jvm_agent_t *J = N->J;
  uint64_t    nm = N->nm;
  int32_t err;

  if (debug > 2 )
      fprintf(stderr, "\t nmethod_info: BEGIN \n");

  /* Instructions */
  err = ps_pread(J->P, nm + OFFSET_CodeBlob_code_offset, &N->instrs_beg, SZ32);
  CHECK_FAIL(err);
  err = ps_pread(J->P, nm + OFFSET_CodeBlob_data_offset, &N->instrs_end, SZ32);
  CHECK_FAIL(err);
  err = ps_pread(J->P, nm + OFFSET_nmethod_deoptimize_offset, &N->deopt_beg, SZ32);
  CHECK_FAIL(err);
  err = ps_pread(J->P, nm + OFFSET_nmethod_orig_pc_offset, &N->orig_pc_offset, SZ32);
  CHECK_FAIL(err);

  /* Metadata */
  err = ps_pread(J->P, nm + OFFSET_nmethod_metadata_offset, &N->metadata_beg, SZ32);
  CHECK_FAIL(err);
  err = ps_pread(J->P, nm + OFFSET_nmethod_scopes_data_offset, &N->metadata_end, SZ32);
  CHECK_FAIL(err);

  /* scopes_pcs */
  err = ps_pread(J->P, nm + OFFSET_nmethod_scopes_pcs_offset, &N->scopes_pcs_beg, SZ32);
  CHECK_FAIL(err);
  err = ps_pread(J->P, nm + OFFSET_nmethod_dependencies_offset, &N->scopes_pcs_end, SZ32);
  CHECK_FAIL(err);

  /* scopes_data */
  err = ps_pread(J->P, nm + OFFSET_nmethod_scopes_data_offset, &N->scopes_data_beg, SZ32);
  CHECK_FAIL(err);

  if (debug > 2 ) {
      N->scopes_data_end = N->scopes_pcs_beg;

      fprintf(stderr, "\t nmethod_info: instrs_beg: %#x, instrs_end: %#x\n",
                       N->instrs_beg, N->instrs_end);

      fprintf(stderr, "\t nmethod_info: deopt_beg: %#x \n",
                       N->deopt_beg);

      fprintf(stderr, "\t nmethod_info: orig_pc_offset: %#x \n",
                       N->orig_pc_offset);

      fprintf(stderr, "\t nmethod_info: metadata_beg: %#x, metadata_end: %#x\n",
                       N->metadata_beg, N->metadata_end);

      fprintf(stderr, "\t nmethod_info: scopes_data_beg: %#x, scopes_data_end: %#x\n",
                       N->scopes_data_beg, N->scopes_data_end);

      fprintf(stderr, "\t nmethod_info: scopes_pcs_beg: %#x, scopes_pcs_end: %#x\n",
                       N->scopes_pcs_beg, N->scopes_pcs_end);

      fprintf(stderr, "\t nmethod_info: END \n\n");
  }
  return PS_OK;

 fail:
  return err;
}

static int
raw_read_int(jvm_agent_t* J, uint64_t *buffer, int32_t *val)
{
  int shift = 0;
  int value = 0;
  uint8_t ch = 0;
  int32_t  err;
  int32_t sum;
  // Constants for UNSIGNED5 coding of Pack200
  // see compressedStream.hpp
  enum {
    lg_H = 6,
    H = 1<<lg_H,
    BitsPerByte = 8,
    L = (1<<BitsPerByte)-H,
  };
  int i;

  err = ps_pread(J->P, (*buffer)++, &ch, sizeof(uint8_t));
  CHECK_FAIL(err);
  if (debug > 2)
      fprintf(stderr, "\t\t\t raw_read_int: *buffer: %#llx, ch: %#x\n", *buffer, ch);

  sum = ch;
  if ( sum >= L ) {
    int32_t lg_H_i = lg_H;
    // Read maximum of 5 total bytes (we've already read 1).
    // See CompressedReadStream::read_int_mb
    for ( i = 0;  i < 4; i++) {
      err = ps_pread(J->P, (*buffer)++, &ch, sizeof(uint8_t));
      CHECK_FAIL(err);
      sum += ch << lg_H_i;
      if (ch < L ) {
        *val = sum;
        return PS_OK;
      }
      lg_H_i += lg_H;
    }
  }
  *val = sum;
  return PS_OK;

 fail:
  return err;
}

static int
read_pair(jvm_agent_t* J, uint64_t *buffer, int32_t *bci, int32_t *line)
{
  uint8_t next = 0;
  int32_t bci_delta;
  int32_t line_delta;
  int32_t err;

  if (debug > 2)
      fprintf(stderr, "\t\t read_pair: BEGIN\n");

  err = ps_pread(J->P, (*buffer)++, &next, sizeof(uint8_t));
  CHECK_FAIL(err);

  if (next == 0) {
      if (debug > 2)
          fprintf(stderr, "\t\t read_pair: END: next == 0\n");
      return 1; /* stream terminated */
  }
  if (next == 0xFF) {
      if (debug > 2)
          fprintf(stderr, "\t\t read_pair: END: next == 0xFF\n");

      /* Escape character, regular compression used */

      err = raw_read_int(J, buffer, &bci_delta);
      CHECK_FAIL(err);

      err = raw_read_int(J, buffer, &line_delta);
      CHECK_FAIL(err);

      *bci  += bci_delta;
      *line += line_delta;

      if (debug > 2) {
          fprintf(stderr, "\t\t read_pair: delta = (line %d: %d)\n",
                          line_delta, bci_delta);
          fprintf(stderr, "\t\t read_pair: unpack= (line %d: %d)\n",
                          *line, *bci);
      }
  } else {
      /* Single byte compression used */
      *bci  += next >> 3;
      *line += next & 0x7;
      if (debug > 2) {
          fprintf(stderr, "\t\t read_pair: delta = (line %d: %d)\n",
                          next & 0x7, next >> 3);
          fprintf(stderr, "\t\t read_pair: unpack= (line %d: %d)\n",
                          *line, *bci);
      }
  }
  if (debug > 2)
      fprintf(stderr, "\t\t read_pair: END\n");
  return PS_OK;

 fail:
  if (debug)
      fprintf(stderr, "\t\t read_pair: FAIL\n");
  return err;
}

static int
line_number_from_bci(jvm_agent_t* J, Vframe_t *vf)
{
  uint64_t buffer;
  uint16_t code_size;
  uint64_t code_end_delta;
  uint64_t constMethod;
  int8_t   access_flags;
  int32_t  best_bci    = 0;
  int32_t  stream_bci  = 0;
  int32_t  stream_line = 0;
  int32_t  err;

  if (debug > 2) {
      char name[256];
      err = name_for_methodPtr(J, vf->method, name, 256);
      CHECK_FAIL(err);
      fprintf(stderr, "\t line_number_from_bci: BEGIN, method name: %s, targ bci: %d\n",
                       name, vf->bci);
  }

  err = read_pointer(J, vf->method + OFFSET_Method_constMethod, &constMethod);
  CHECK_FAIL(err);

  vf->line = 0;
  err = ps_pread(J->P, constMethod + OFFSET_ConstMethod_flags, &access_flags, sizeof(int8_t));
  CHECK_FAIL(err);

  if (!(access_flags & ConstMethod_has_linenumber_table)) {
      if (debug > 2)
          fprintf(stderr, "\t line_number_from_bci: END: !HAS_LINE_NUMBER_TABLE \n\n");
      return PS_OK;
  }

  /*  The line numbers are a short array of 2-tuples [start_pc, line_number].
   *  Not necessarily sorted and not necessarily one-to-one.
   */

  err = ps_pread(J->P, constMethod + OFFSET_ConstMethod_code_size, &code_size, SZ16);
  CHECK_FAIL(err);

  /* inlined_table_start() */
  code_end_delta = (uint64_t) (access_flags & AccessFlags_NATIVE) ? 2*POINTER_SIZE : 0;
  buffer = constMethod + (uint64_t) SIZE_ConstMethod + (uint64_t) code_size + code_end_delta;

  if (debug > 2) {
      fprintf(stderr, "\t\t line_number_from_bci: method: %#llx, native: %d\n",
                      vf->method, (access_flags & AccessFlags_NATIVE));
      fprintf(stderr, "\t\t line_number_from_bci: buffer: %#llx, code_size: %d\n",
                      buffer, (int) code_size);
  }

  while (read_pair(J, &buffer, &stream_bci, &stream_line) == 0) {
      if (stream_bci == vf->bci) {
          /* perfect match */
          if (debug > 2)
              fprintf(stderr, "\t line_number_from_bci: END: exact line: %d \n\n", vf->line);
          vf->line = stream_line;
          return PS_OK;
      } else {
          /* update best_bci/line */
          if (stream_bci < vf->bci && stream_bci >= best_bci) {
              best_bci = stream_bci;
              vf->line = stream_line;
              if (debug > 2) {
                  fprintf(stderr, "\t line_number_from_bci: best_bci: %d, best_line: %d\n",
                                   best_bci, vf->line);
              }
          }
      }
  }
  if (debug > 2)
      fprintf(stderr, "\t line_number_from_bci: END: line: %d \n\n", vf->line);
  return PS_OK;

 fail:
  if (debug)
      fprintf(stderr, "\t line_number_from_bci: FAIL\n");
  return err;
}

static int
get_real_pc(Nmethod_t *N, uint64_t pc_desc, uint64_t *real_pc)
{
  int32_t pc_offset;
  int32_t err;

  err = ps_pread(N->J->P, pc_desc + OFFSET_PcDesc_pc_offset, &pc_offset, SZ32);
  CHECK_FAIL(err);

  *real_pc = N->nm + N->instrs_beg + pc_offset;
  if (debug > 2) {
      fprintf(stderr, "\t\t get_real_pc: pc_offset: %lx, real_pc: %llx\n",
                       pc_offset, *real_pc);
  }
  return PS_OK;

 fail:
  return err;
}

/* Finds a PcDesc with real-pc equal to N->pc */
static int pc_desc_at(Nmethod_t *N)
{
  uint64_t pc_diff = 999;
  int32_t offs;
  int32_t err;

  if (debug > 2)
      fprintf(stderr, "\t pc_desc_at: BEGIN\n");

  N->vf_cnt  = 0;
  N->pc_desc = 0;

  for (offs = N->scopes_pcs_beg; offs < N->scopes_pcs_end; offs += SIZE_PcDesc) {
      uint64_t pd;
      uint64_t best_pc_diff = 16;       /* some approximation */
      uint64_t real_pc = 0;

      pd = N->nm + offs;
      err = get_real_pc(N, pd, &real_pc);
      CHECK_FAIL(err);

      pc_diff = real_pc - N->pc;

      /* In general, this fragment should work */
      if (pc_diff == 0) {
          N->pc_desc = pd;
          if (debug) {
            fprintf(stderr, "\t pc_desc_at: END: pc_desc: FOUND: %#lx \n\n", pd);
          }
          return PS_OK;
      }
      /* This fragment is to be able to find out an appropriate
       * pc_desc entry even if pc_desc info is inaccurate.
       */
      if (best_pc_diff > pc_diff && pc_diff > 0) {
          best_pc_diff = pc_diff;
          N->pc_desc = pd;
      }
  }
  if (debug) {
      fprintf(stderr, "\t pc_desc_at: END: pc_desc NOT FOUND");
      if (pc_diff < 20)
          fprintf(stderr, ", best pc_diff: %d\n\n", pc_diff);
      else
          fprintf(stderr, "\n\n");
  }
  return PS_OK;

 fail:
  return err;
}

static int
scope_desc_at(Nmethod_t *N, int32_t decode_offset, Vframe_t *vf)
{
  uint64_t buffer;
  int32_t  err;

  if (debug > 2) {
      fprintf(stderr, "\t\t scope_desc_at: BEGIN \n");
  }

  buffer = N->nm + N->scopes_data_beg + decode_offset;

  err = raw_read_int(N->J, &buffer, &vf->sender_decode_offset);
  CHECK_FAIL(err);

  err = raw_read_int(N->J, &buffer, &vf->methodIdx);
  CHECK_FAIL(err);

  err = raw_read_int(N->J, &buffer, &vf->bci);
  CHECK_FAIL(err);

  if (debug > 2) {
      fprintf(stderr, "\t\t scope_desc_at: sender_decode_offset: %#x\n",
                      vf->sender_decode_offset);
      fprintf(stderr, "\t\t scope_desc_at: methodIdx: %d\n", vf->methodIdx);
      fprintf(stderr, "\t\t scope_desc_at: bci: %d\n", vf->bci);

      fprintf(stderr, "\t\t scope_desc_at: END \n\n");
  }
  return PS_OK;

 fail:
  return err;
}

static int scopeDesc_chain(Nmethod_t *N) {
  int32_t decode_offset = 0;
  int32_t err;

  if (debug > 2) {
    fprintf(stderr, "\t scopeDesc_chain: BEGIN\n");
  }

  err = ps_pread(N->J->P, N->pc_desc + OFFSET_PcDesc_scope_decode_offset,
                 &decode_offset, SZ32);
  CHECK_FAIL(err);

  while (decode_offset > 0) {
    Vframe_t *vf = &N->vframes[N->vf_cnt];

    if (debug > 2) {
      fprintf(stderr, "\t scopeDesc_chain: decode_offset: %#x\n", decode_offset);
    }

    err = scope_desc_at(N, decode_offset, vf);
    CHECK_FAIL(err);

    if (vf->methodIdx > ((N->metadata_end - N->metadata_beg) / POINTER_SIZE)) {
      fprintf(stderr, "\t scopeDesc_chain: (methodIdx > metadata length) !\n");
      return -1;
    }
    err = read_pointer(N->J, N->nm + N->metadata_beg + (vf->methodIdx-1)*POINTER_SIZE,
                       &vf->method);
    CHECK_FAIL(err);

    if (vf->method) {
      N->vf_cnt++;
      err = line_number_from_bci(N->J, vf);
      CHECK_FAIL(err);
      if (debug > 2) {
        fprintf(stderr, "\t scopeDesc_chain: method: %#8llx, line: %d\n",
                vf->method, vf->line);
      }
    }
    decode_offset = vf->sender_decode_offset;
  }
  if (debug > 2) {
    fprintf(stderr, "\t scopeDesc_chain: END \n\n");
  }
  return PS_OK;

 fail:
  if (debug) {
    fprintf(stderr, "\t scopeDesc_chain: FAIL \n\n");
  }
  return err;
}


static int
name_for_nmethod(jvm_agent_t* J,
                 uint64_t nm,
                 uint64_t pc,
                 uint64_t method,
                 char *result,
                 size_t size,
                 Jframe_t *jframe
) {
  Nmethod_t *N;
  Vframe_t *vf;
  int32_t err;
  int deoptimized = 0;

  if (debug) {
      fprintf(stderr, "name_for_nmethod: BEGIN: nmethod: %#llx, pc: %#llx\n", nm, pc);
  }
  if (J->N == NULL) {
    J->N = (Nmethod_t *) malloc(sizeof(Nmethod_t));
  }
  memset(J->N, 0, sizeof(Nmethod_t));   /* Initial stat: all values are zeros */
  N     = J->N;
  N->J  = J;
  N->nm = nm;
  N->pc = pc;
  N->jframe = jframe;

  err = nmethod_info(N);
  CHECK_FAIL(err);
  if (debug) {
      fprintf(stderr, "name_for_nmethod: pc: %#llx, deopt_pc:  %#llx\n",
              pc, N->nm + N->deopt_beg);
  }

  /* check for a deoptimized frame */
  if ( pc == N->nm + N->deopt_beg) {
    uint64_t base;
    if (debug) {
        fprintf(stderr, "name_for_nmethod: found deoptimized frame\n");
    }
    if (J->prev_fr.sender_sp != 0) {
      base = J->prev_fr.sender_sp + N->orig_pc_offset;
    } else {
      base = J->curr_fr.sp + N->orig_pc_offset;
    }
    err = read_pointer(J, base, &N->pc);
    CHECK_FAIL(err);
    if (debug) {
        fprintf(stderr, "name_for_nmethod: found deoptimized frame converting pc from %#8llx to %#8llx\n",
        pc,  N->pc);
    }
    deoptimized = 1;
  }

  err = pc_desc_at(N);
  CHECK_FAIL(err);

  if (N->pc_desc > 0) {
      jframe->locinf = 1;
      err = scopeDesc_chain(N);
      CHECK_FAIL(err);
  }
  result[0] = COMP_METHOD_SIGN;
  vf = &N->vframes[0];
  if (N->vf_cnt > 0) {
      jframe->vf_cnt = N->vf_cnt;
      jframe->bci  = vf->bci;
      jframe->line = vf->line;
      err = name_for_methodPtr(J, N->vframes[0].method, result+1, size-1);
      CHECK_FAIL(err);
  } else {
      err = name_for_methodPtr(J, method, result+1, size-1);
      CHECK_FAIL(err);
  }
  if (deoptimized) {
    strncat(result, " [deoptimized frame]; ", size - strlen(result) - 1);
  } else {
    strncat(result, " [compiled] ", size - strlen(result) - 1);
  }
  if (debug)
      fprintf(stderr, "name_for_nmethod: END: method name: %s, vf_cnt: %d\n\n",
                      result, N->vf_cnt);
  return PS_OK;

 fail:
  if (debug)
      fprintf(stderr, "name_for_nmethod: FAIL \n\n");
  return err;
}

static int
name_for_imethod(jvm_agent_t* J,
                 uint64_t bcp,
                 uint64_t method,
                 char *result,
                 size_t size,
                 Jframe_t *jframe
) {
  uint64_t bci;
  uint64_t constMethod;
  Vframe_t vframe = {0};
  Vframe_t *vf = &vframe;
  int32_t   err;

  err = read_pointer(J, method + OFFSET_Method_constMethod, &constMethod);
  CHECK_FAIL(err);

  bci = bcp - (constMethod + (uint64_t) SIZE_ConstMethod);

  if (debug)
      fprintf(stderr, "\t name_for_imethod: BEGIN: method: %#llx\n", method);

  err = name_for_methodPtr(J, method, result, size);
  CHECK_FAIL(err);
  if (debug)
      fprintf(stderr, "\t name_for_imethod: method name: %s\n", result);

  if (bci > 0) {
      vf->method = method;
      vf->bci       = bci;
      err = line_number_from_bci(J, vf);
      CHECK_FAIL(err);
  }
  jframe->bci  = vf->bci;
  jframe->line = vf->line;
  jframe->locinf = 1;

  if (debug) {
      fprintf(stderr, "\t name_for_imethod: END: bci: %d, line: %d\n\n",
                      vf->bci, vf->line);
  }
  return PS_OK;

 fail:
  if (debug)
      fprintf(stderr, "\t name_for_imethod: FAIL\n");
  return err;
}

static int
name_for_codecache(jvm_agent_t* J, uint64_t fp, uint64_t pc, char * result,
                   size_t size, Jframe_t *jframe, int* is_interpreted)
{
  uint64_t start;
  uint64_t vtbl;
  int32_t err;
  *is_interpreted = 0;

  result[0] = '\0';

  err = find_start(J, pc, &start);
  CHECK_FAIL(err);

  err = read_pointer(J, start, &vtbl);
  CHECK_FAIL(err);

  if (vtbl == J->nmethod_vtbl) {
    uint64_t method;

    err = read_pointer(J, start + OFFSET_nmethod_method, &method);
    CHECK_FAIL(err);

    if (debug) {
        fprintf(stderr, "name_for_codecache: start: %#8llx, pc: %#8llx, method: %#8llx \n",
                        start, pc, method);
    }
    err = name_for_nmethod(J, start, pc, method, result, size, jframe);
    CHECK_FAIL(err);
  } else if (vtbl == J->BufferBlob_vtbl) {
    const char * name;

    err = read_string_pointer(J, start + OFFSET_CodeBlob_name, &name);

    /*
     * Temporary usage of string "Interpreter".
     * We need some other way to distinguish "StubRoutines"
     * and regular interpreted frames.
     */
    if (err == PS_OK && strncmp(name, "Interpreter", 11) == 0) {
      *is_interpreted = 1;
      if (is_method(J, J->methodPtr)) {
        return name_for_imethod(J, J->bcp, J->methodPtr, result, size, jframe);
      }
    }

    if (err == PS_OK) {
      strncpy(result, name, size);
      free((void*)name);
    } else {
      strncpy(result, "<unknown BufferBlob>", size);
    }
    /* return PS_OK; */
  } else {
    const char * name;

    err = read_string_pointer(J, start + OFFSET_CodeBlob_name, &name);
    if (err == PS_OK) {
      strncpy(result, name, size);
      free((void*)name);
    } else {
      strncpy(result, "<unknown CodeBlob>", size);
      WARN1("unknown CodeBlob: vtbl = 0x%x", vtbl);
    }
  }
  result[size-1] = '\0';

#ifdef X86_COMPILER2
  if (vtbl != J->RuntimeStub_vtbl) {
    uint64_t trial_pc;
    int frame_size;
    err = ps_pread(J->P, start + OFFSET_CodeBlob_frame_size,
                         &frame_size, SZ32);
    CHECK_FAIL(err);

    // frame_size is in words, we want bytes.
    frame_size *= POINTER_SIZE; /* word => byte conversion */

    /*
      Because c2 doesn't use FP as a framepointer the value of sp/fp we receive
      in the initial entry to a set of stack frames containing server frames
      will pretty much be nonsense. We can detect that nonsense by looking to
      see if the PC we received is correct if we look at the expected storage
      location in relation to the FP (ie. POINTER_SIZE(FP) )
    */

    err = read_pointer(J, fp + POINTER_SIZE , &trial_pc);
    if ( (err != PS_OK || trial_pc != pc) && frame_size > 0 ) {
      // Either we couldn't even read at the "fp" or the pc didn't match
      // both are sure clues that the fp is bogus. We no search the stack
      // for a reasonable number of words trying to find the bogus fp
      // and the current pc in adjacent words. The we will be able to
      // deduce an approximation of the frame pointer and actually get
      // the correct stack pointer. Which we can then unwind for the
      // next frame.
      int i;
      uint64_t check;
      uint64_t base = J->curr_fr.sp;
      uint64_t prev_fp = 0;
      for ( i = 0; i < frame_size * 5 ; i++, base += POINTER_SIZE ) {
        err = read_pointer(J, base , &check);
        CHECK_FAIL(err);
        if (check == fp) {
          base += POINTER_SIZE;
          err = read_pointer(J, base , &check);
          CHECK_FAIL(err);
          if (check == pc) {
            if (debug) {
              fprintf(stderr, "name_for_codecache: found matching fp/pc combo at 0x%llx\n", base - POINTER_SIZE);
            }
            prev_fp = base - 2 * POINTER_SIZE;
            break;
          }
        }
      }
      if ( prev_fp != 0 ) {
        // real_sp is the sp we should have received for this frame
        uint64_t real_sp = prev_fp + 2 * POINTER_SIZE;
        // +POINTER_SIZE because callee owns the return address so caller's sp is +1 word
        jframe->new_sp = real_sp + frame_size + POINTER_SIZE;
        err = read_pointer(J, jframe->new_sp - POINTER_SIZE , &jframe->new_pc);
        CHECK_FAIL(err);
        err = read_pointer(J, jframe->new_sp - 2*POINTER_SIZE, &jframe->new_fp);
        CHECK_FAIL(err);
        return PS_OK;
      }
    }

    /* A prototype to workaround FP absence */
    /*
     * frame_size can be 0 for StubRoutines (1) frame.
     * In this case it should work with fp as usual.
     */
    if (frame_size > 0) {
      jframe->new_fp = J->prev_fr.fp + frame_size;
      jframe->new_sp = jframe->new_fp + 2 * POINTER_SIZE;
    } else {
      memset(&J->curr_fr, 0, sizeof(Frame_t));
      err = read_pointer(J,  fp, &jframe->new_fp);
      CHECK_FAIL(err);

      err = read_pointer(J,  jframe->new_fp + POINTER_SIZE,  &jframe->new_pc);
      CHECK_FAIL(err);
    }
    if (debug) {
      fprintf(stderr, "name_for_codecache: %s, frame_size=%#lx\n",
                       result, frame_size);
      fprintf(stderr, "name_for_codecache: prev_fr.fp=%#lx, fp=%#lx\n",
                       J->prev_fr.fp, jframe->new_fp);
    }
  }
#endif /* X86_COMPILER2 */

  return PS_OK;

 fail:
  return err;
}

int Jget_vframe(jvm_agent_t* J, int vframe_no,
                char *name, size_t size, Jframe_t *jframe)
{
  Nmethod_t *N = J->N;
  Vframe_t  *vf;
  int32_t   err;

  if (vframe_no >= N->vf_cnt) {
     (void) sprintf(name, "Wrong inlinedMethod%1d()", vframe_no);
     return -1;
  }
  vf = N->vframes + vframe_no;
  name[0] = COMP_METHOD_SIGN;
  err = name_for_methodPtr(J, vf->method, name + 1, size);
  CHECK_FAIL(err);

  jframe->bci = vf->bci;
  jframe->line = vf->line;
  if (debug) {
      fprintf(stderr, "\t Jget_vframe: method name: %s, line: %d\n",
                       name, vf->line);
  }
  return PS_OK;

 fail:
  if (debug) {
      fprintf(stderr, "\t Jget_vframe: FAIL\n");
  }
  return err;
}

#define MAX_SYM_SIZE 256

int Jlookup_by_regs(jvm_agent_t* J, const prgregset_t regs, char *name,
                    size_t size, Jframe_t *jframe) {
  uintptr_t fp;
  uintptr_t pc;
  /* arguments given to read_pointer need to be worst case sized */
  uint64_t methodPtr = 0;
  uint64_t sender_sp;
  uint64_t bcp = 0;
  int is_interpreted = 0;
  int result = PS_OK;
  int err = PS_OK;

  if (J == NULL) {
    return -1;
  }

  jframe->vf_cnt = 1;
  jframe->new_fp = 0;
  jframe->new_pc = 0;
  jframe->line   = 0;
  jframe->bci    = 0;
  jframe->locinf = 0;

  read_volatiles(J);
  pc = (uintptr_t) regs[R_PC];
  J->curr_fr.pc = pc;
  J->curr_fr.fp = regs[R_FP];
  J->curr_fr.sp = regs[R_SP];

  if (debug)
      fprintf(stderr, "Jlookup_by_regs: BEGINs: fp=%#lx, pc=%#lx\n", regs[R_FP], pc);

#if defined(sparc) || defined(__sparc)
    /* The following workaround is for SPARC. CALL instruction occupates 8 bytes.
     * In the pcDesc structure return pc offset is recorded for CALL instructions.
     * regs[R_PC] contains a CALL instruction pc offset.
     */
    pc += 8;
    bcp          = (uintptr_t) regs[R_L1];
    methodPtr = (uintptr_t) regs[R_L2];
    sender_sp = regs[R_I5];
    if (debug > 2) {
        fprintf(stderr, "\nregs[R_I1]=%lx, regs[R_I2]=%lx, regs[R_I5]=%lx, regs[R_L1]=%lx, regs[R_L2]=%lx\n",
                         regs[R_I1], regs[R_I2], regs[R_I5], regs[R_L1], regs[R_L2]);
    }
#elif defined(i386) || defined(__i386) || defined(__amd64)

    fp = (uintptr_t) regs[R_FP];
    if (J->prev_fr.fp == 0) {
#ifdef X86_COMPILER2
        /* A workaround for top java frames */
        J->prev_fr.fp = (uintptr_t)(regs[R_SP] - 2 * POINTER_SIZE);
#else
        J->prev_fr.fp = (uintptr_t)(regs[R_SP] - POINTER_SIZE);
#endif /* COMPILER2 */
    }
    if (debug > 2) {
        printf("Jlookup_by_regs: J->prev_fr.fp = %#lx\n", J->prev_fr.fp);
    }

    if (read_pointer(J,  fp + OFFSET_interpreter_frame_method, &methodPtr) != PS_OK) {
      methodPtr = 0;
    }
    if (read_pointer(J,  fp + OFFSET_interpreter_frame_sender_sp, &sender_sp) != PS_OK) {
      sender_sp = 0;
    }
    if (read_pointer(J,  fp + OFFSET_interpreter_frame_bcp_offset, &bcp) != PS_OK) {
      bcp = 0;
    }
#endif /* i386 */

  J->methodPtr = methodPtr;
  J->bcp = bcp;

  /* On x86 with C2 JVM: native frame may have wrong regs[R_FP]
   * For example: JVM_SuspendThread frame poins to the top interpreted frame.
   * If we call is_method(J, methodPtr) before codecache_contains(J, pc)
   * then we go over and omit both: nmethod and I2CAdapter frames.
   * Note, that regs[R_PC] is always correct if frame defined correctly.
   * So it is better to call codecache_contains(J, pc) from the beginning.
   */
#ifndef X86_COMPILER2
  if (is_method(J, J->methodPtr)) {
    result = name_for_imethod(J, bcp, J->methodPtr, name, size, jframe);
    /* If the methodPtr is a method then this is highly likely to be
       an interpreter frame */
    if (result >= 0) {
      is_interpreted = 1;
    }
  } else
#endif /* ! X86_COMPILER2 */

  if (codecache_contains(J, pc)) {
    result = name_for_codecache(J, fp, pc, name, size, jframe, &is_interpreted);
  }
#ifdef X86_COMPILER2
  else if (is_method(J, J->methodPtr)) {
    result = name_for_imethod(J, bcp, J->methodPtr, name, size, jframe);
    /* If the methodPtr is a method then this is highly likely to be
       an interpreter frame */
    if (result >= 0) {
      is_interpreted = 1;
    }
  }
#endif /* X86_COMPILER2 */
  else {
    if (debug) {
        fprintf(stderr, "Jlookup_by_regs: END with -1\n\n");
    }
    result = -1;
  }
  if (!is_interpreted) {
    sender_sp = 0;
  }
  J->curr_fr.sender_sp = sender_sp;

#ifdef X86_COMPILER2
  if (!J->curr_fr.fp) {
    J->curr_fr.fp = (jframe->new_fp) ? jframe->new_fp : (uintptr_t)regs[R_FP];
  }
  if (!jframe->new_pc && jframe->new_fp) {
    // This seems dubious
    read_pointer(J,  jframe->new_fp + POINTER_SIZE,  &jframe->new_pc);
    CHECK_FAIL(err);
    if (debug > 2) {
        printf("Jlookup_by_regs: (update pc) jframe->new_fp: %#llx, jframe->new_pc: %#llx\n",
               jframe->new_fp, jframe->new_pc);
    }
  }

#endif /* X86_COMPILER2 */
  J->prev_fr = J->curr_fr;

  if (debug)
      fprintf(stderr, "Jlookup_by_regs: END\n\n");

  return result;

 fail:
  return err;
}

void update_gregs(prgregset_t gregs, Jframe_t jframe) {
#ifdef X86_COMPILER2
    if (debug > 0) {
      fprintf(stderr, "update_gregs: before update sp = 0x%llx, fp = 0x%llx, pc = 0x%llx\n", gregs[R_SP], gregs[R_FP], gregs[R_PC]);
    }
    /*
     * A workaround for java C2 frames with unconventional FP.
     * may have to modify regset with new values for FP/PC/SP when needed.
     */
     if (jframe.new_sp) {
         *((uintptr_t *) &gregs[R_SP]) = (uintptr_t) jframe.new_sp;
     } else {
         // *((uintptr_t *) &gregs[R_SP]) = (uintptr_t) gregs[R_FP] + 2 * POINTER_SIZE;
     }

     if (jframe.new_fp) {
         *((uintptr_t *) &gregs[R_FP]) = (uintptr_t) jframe.new_fp;
     }
     if (jframe.new_pc) {
         *((uintptr_t *) &gregs[R_PC]) = (uintptr_t) jframe.new_pc;
     }
    if (debug > 0) {
      fprintf(stderr, "update_gregs: after update sp = 0x%llx, fp = 0x%llx, pc = 0x%llx\n", gregs[R_SP], gregs[R_FP], gregs[R_PC]);
    }
#endif  /* X86_COMPILER2 */
}

/*
 * Iterates over java frames at current location given by 'gregs'.
 *
 *  Returns -1 if no java frames are present or if an error is encountered.
 *  Returns the result of calling 'func' if the return value is non-zero.
 *  Returns 0 otherwise.
 */
int Jframe_iter(jvm_agent_t *J, prgregset_t gregs, java_stack_f *func, void* cld) {
    char buf[MAX_SYM_SIZE + 1];
    Jframe_t jframe;
    int i = 0, res;
#ifdef X86_COMPILER2
    if (debug > 0) {
      fprintf(stderr, "Jframe_iter: Entry sp = 0x%llx, fp = 0x%llx, pc = 0x%llx\n", gregs[R_SP], gregs[R_FP], gregs[R_PC]);
    }
#endif  /* X86_COMPILER2 */

    memset(&jframe, 0, sizeof(Jframe_t));
    memset(buf, 0, sizeof(buf));
    res =  Jlookup_by_regs(J, gregs, buf, sizeof(buf), &jframe);
    if (res != PS_OK)
        return (-1);


    res = func(cld, gregs, buf, (jframe.locinf)? jframe.bci : -1,
               jframe.line, NULL);
    if (res != 0) {
        update_gregs(gregs, jframe);
        return (res);
    }
    for (i = 1; i < jframe.vf_cnt; i++) {
        Jget_vframe(J, i, buf, sizeof(buf), &jframe);
        res = func(cld, gregs, buf, (jframe.locinf)? jframe.bci : -1,
                   jframe.line, NULL);
        if (res != 0) {
            update_gregs(gregs, jframe);
            return (res);
        }
    }
    update_gregs(gregs, jframe);
    return (0);
}
