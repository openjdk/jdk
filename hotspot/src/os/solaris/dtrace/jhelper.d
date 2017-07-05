/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

/* This file is auto-generated */
#include "JvmOffsetsIndex.h"

#define DEBUG

#ifdef DEBUG
#define MARK_LINE this->line = __LINE__
#else
#define MARK_LINE 
#endif

#ifdef _LP64
#define STACK_BIAS 0x7ff
#define pointer uint64_t
#else
#define STACK_BIAS 0
#define pointer uint32_t
#endif

extern pointer __JvmOffsets;

/* GrowableArray<CodeHeaps*>* */
extern pointer __1cJCodeCacheG_heaps_;

extern pointer __1cIUniverseO_collectedHeap_;

extern pointer __1cHnmethodG__vtbl_;
extern pointer __1cGMethodG__vtbl_;
extern pointer __1cKBufferBlobG__vtbl_;

#define copyin_ptr(ADDR)    *(pointer*)  copyin((pointer) (ADDR), sizeof(pointer))
#define copyin_uchar(ADDR)  *(uchar_t*)  copyin((pointer) (ADDR), sizeof(uchar_t))
#define copyin_uint16(ADDR) *(uint16_t*) copyin((pointer) (ADDR), sizeof(uint16_t))
#define copyin_uint32(ADDR) *(uint32_t*) copyin((pointer) (ADDR), sizeof(uint32_t))
#define copyin_int32(ADDR)  *(int32_t*)  copyin((pointer) (ADDR), sizeof(int32_t))
#define copyin_uint8(ADDR)  *(uint8_t*)  copyin((pointer) (ADDR), sizeof(uint8_t))

#define SAME(x) x
#define copyin_offset(JVM_CONST)  JVM_CONST = \
	copyin_int32(JvmOffsetsPtr + SAME(IDX_)JVM_CONST * sizeof(int32_t))

int init_done;

dtrace:helper:ustack:
{
  MARK_LINE;
  this->done = 0;
  /*
   * TBD:
   * Here we initialize init_done, otherwise jhelper does not work.
   * Therefore, copyin_offset() statements work multiple times now.
   * There is a hope we could avoid it in the future, and so,
   * this initialization can be removed.
   */
  init_done  = 0;
  this->error = (char *) NULL;
  this->result = (char *) NULL;
  this->isMethod = 0;
  this->codecache = 0;
  this->klass = (pointer) NULL;
  this->vtbl  = (pointer) NULL;
  this->suffix = '\0';
}

dtrace:helper:ustack:
{
  MARK_LINE;
  /* Initialization of JvmOffsets constants */
  JvmOffsetsPtr = (pointer) &``__JvmOffsets;
}

dtrace:helper:ustack:
/!init_done && !this->done/
{
  MARK_LINE;
  
  copyin_offset(POINTER_SIZE);
  copyin_offset(COMPILER);
  copyin_offset(OFFSET_CollectedHeap_reserved);
  copyin_offset(OFFSET_MemRegion_start);
  copyin_offset(OFFSET_MemRegion_word_size);
  copyin_offset(SIZE_HeapWord);

  copyin_offset(OFFSET_interpreter_frame_method);
  copyin_offset(OFFSET_Klass_name);
  copyin_offset(OFFSET_ConstantPool_pool_holder);

  copyin_offset(OFFSET_HeapBlockHeader_used);
  copyin_offset(OFFSET_oopDesc_metadata);

  copyin_offset(OFFSET_Symbol_length);
  copyin_offset(OFFSET_Symbol_body);

  copyin_offset(OFFSET_Method_constMethod);
  copyin_offset(OFFSET_ConstMethod_constants);
  copyin_offset(OFFSET_ConstMethod_name_index);
  copyin_offset(OFFSET_ConstMethod_signature_index);

  copyin_offset(OFFSET_CodeHeap_memory);
  copyin_offset(OFFSET_CodeHeap_segmap);
  copyin_offset(OFFSET_CodeHeap_log2_segment_size);

  copyin_offset(OFFSET_GrowableArray_CodeHeap_data);
  copyin_offset(OFFSET_GrowableArray_CodeHeap_len);

  copyin_offset(OFFSET_VirtualSpace_low);
  copyin_offset(OFFSET_VirtualSpace_high);

  copyin_offset(OFFSET_CodeBlob_name);

  copyin_offset(OFFSET_nmethod_method);
  copyin_offset(SIZE_HeapBlockHeader);
  copyin_offset(SIZE_oopDesc);
  copyin_offset(SIZE_ConstantPool);

  copyin_offset(OFFSET_NarrowPtrStruct_base);
  copyin_offset(OFFSET_NarrowPtrStruct_shift);

  /*
   * The PC to translate is in arg0.
   */
  this->pc = arg0;

  /*
   * The methodPtr is in %l2 on SPARC.  This can be found at
   * offset 8 from the frame pointer on 32-bit processes.
   */
#if   defined(__sparc)
  this->methodPtr = copyin_ptr(arg1 + 2 * sizeof(pointer) + STACK_BIAS);
#elif defined(__i386) || defined(__amd64)
  this->methodPtr = copyin_ptr(arg1 + OFFSET_interpreter_frame_method);
#else
#error "Don't know architecture"
#endif

  /* Read address of GrowableArray<CodeHeaps*> */
  this->code_heaps_address = copyin_ptr(&``__1cJCodeCacheG_heaps_);
  /* Read address of _data array field in GrowableArray */
  this->code_heaps_array_address = copyin_ptr(this->code_heaps_address + OFFSET_GrowableArray_CodeHeap_data);
  this->number_of_heaps = copyin_uint32(this->code_heaps_address + OFFSET_GrowableArray_CodeHeap_len);

  this->Method_vtbl = (pointer) &``__1cGMethodG__vtbl_;

  /*
   * Get Java heap bounds
   */
  this->Universe_collectedHeap = copyin_ptr(&``__1cIUniverseO_collectedHeap_);
  this->heap_start = copyin_ptr(this->Universe_collectedHeap +
      OFFSET_CollectedHeap_reserved +
      OFFSET_MemRegion_start);
  this->heap_size = SIZE_HeapWord *
    copyin_ptr(this->Universe_collectedHeap +
        OFFSET_CollectedHeap_reserved +
        OFFSET_MemRegion_word_size
        );
  this->heap_end = this->heap_start + this->heap_size;
}

/*
 * IMPORTANT: At the moment the ustack helper supports up to 5 code heaps in 
 * the code cache. If more code heaps are added the following probes have to 
 * be extended. This is done by simply adding a probe to get the heap bounds
 * and another probe to set the code heap address of the newly created heap.
 */

/*
 * ----- BEGIN: Get bounds of code heaps -----
 */
dtrace:helper:ustack:
/init_done < 1 && this->number_of_heaps >= 1 && !this->done/
{
  MARK_LINE;
  /* CodeHeap 1 */
  init_done = 1;
  this->code_heap1_address = copyin_ptr(this->code_heaps_array_address);
  this->code_heap1_low = copyin_ptr(this->code_heap1_address + 
      OFFSET_CodeHeap_memory + OFFSET_VirtualSpace_low);
  this->code_heap1_high = copyin_ptr(this->code_heap1_address +
      OFFSET_CodeHeap_memory + OFFSET_VirtualSpace_high);
}

dtrace:helper:ustack:
/init_done < 2 && this->number_of_heaps >= 2 && !this->done/
{
  MARK_LINE;
  /* CodeHeap 2 */
  init_done = 2;
  this->code_heaps_array_address = this->code_heaps_array_address + POINTER_SIZE;
  this->code_heap2_address = copyin_ptr(this->code_heaps_array_address);
  this->code_heap2_low = copyin_ptr(this->code_heap2_address + 
      OFFSET_CodeHeap_memory + OFFSET_VirtualSpace_low);
  this->code_heap2_high = copyin_ptr(this->code_heap2_address +
      OFFSET_CodeHeap_memory + OFFSET_VirtualSpace_high);
}

dtrace:helper:ustack:
/init_done < 3 && this->number_of_heaps >= 3 && !this->done/
{
  /* CodeHeap 3 */
  init_done = 3;
  this->code_heaps_array_address = this->code_heaps_array_address + POINTER_SIZE;
  this->code_heap3_address = copyin_ptr(this->code_heaps_array_address);
  this->code_heap3_low = copyin_ptr(this->code_heap3_address + 
      OFFSET_CodeHeap_memory + OFFSET_VirtualSpace_low);
  this->code_heap3_high = copyin_ptr(this->code_heap3_address +
      OFFSET_CodeHeap_memory + OFFSET_VirtualSpace_high);
}

dtrace:helper:ustack:
/init_done < 4 && this->number_of_heaps >= 4 && !this->done/
{
  /* CodeHeap 4 */
  init_done = 4;
  this->code_heaps_array_address = this->code_heaps_array_address + POINTER_SIZE;
  this->code_heap4_address = copyin_ptr(this->code_heaps_array_address);
  this->code_heap4_low = copyin_ptr(this->code_heap4_address + 
      OFFSET_CodeHeap_memory + OFFSET_VirtualSpace_low);
  this->code_heap4_high = copyin_ptr(this->code_heap4_address +
      OFFSET_CodeHeap_memory + OFFSET_VirtualSpace_high);
}

dtrace:helper:ustack:
/init_done < 5 && this->number_of_heaps >= 5 && !this->done/
{
  /* CodeHeap 5 */
  init_done = 5;
  this->code_heaps_array_address = this->code_heaps_array_address + POINTER_SIZE;
  this->code_heap5_address = copyin_ptr(this->code_heaps_array_address);
  this->code_heap5_low = copyin_ptr(this->code_heap5_address + 
      OFFSET_CodeHeap_memory + OFFSET_VirtualSpace_low);
  this->code_heap5_high = copyin_ptr(this->code_heap5_address +
      OFFSET_CodeHeap_memory + OFFSET_VirtualSpace_high);
}
/*
 * ----- END: Get bounds of code heaps -----
 */

/*
 * ----- BEGIN: Get address of the code heap pc points to -----
 */
dtrace:helper:ustack:
/!this->done && this->number_of_heaps >= 1 && this->code_heap1_low <= this->pc && this->pc < this->code_heap1_high/
{
  MARK_LINE;
  this->codecache = 1;
  this->code_heap_address = this->code_heap1_address;
}

dtrace:helper:ustack:
/!this->done && this->number_of_heaps >= 2 && this->code_heap2_low <= this->pc && this->pc < this->code_heap2_high/
{
  MARK_LINE;
  this->codecache = 1;
  this->code_heap_address = this->code_heap2_address;
}

dtrace:helper:ustack:
/!this->done && this->number_of_heaps >= 3 && this->code_heap3_low <= this->pc && this->pc < this->code_heap3_high/
{
  MARK_LINE;
  this->codecache = 1;
  this->code_heap_address = this->code_heap3_address;
}

dtrace:helper:ustack:
/!this->done && this->number_of_heaps >= 4 && this->code_heap4_low <= this->pc && this->pc < this->code_heap4_high/
{
  MARK_LINE;
  this->codecache = 1;
  this->code_heap_address = this->code_heap4_address;
}

dtrace:helper:ustack:
/!this->done && this->number_of_heaps >= 5 && this->code_heap5_low <= this->pc && this->pc < this->code_heap5_high/
{
  MARK_LINE;
  this->codecache = 1;
  this->code_heap_address = this->code_heap5_address;
}
/*
 * ----- END: Get address of the code heap pc points to -----
 */

dtrace:helper:ustack:
/!this->done && this->codecache/
{
  MARK_LINE;
  /* 
   * Get code heap configuration
   */
  this->code_heap_low = copyin_ptr(this->code_heap_address + 
      OFFSET_CodeHeap_memory + OFFSET_VirtualSpace_low);
  this->code_heap_segmap_low = copyin_ptr(this->code_heap_address +
      OFFSET_CodeHeap_segmap + OFFSET_VirtualSpace_low);
  this->code_heap_log2_segment_size = copyin_uint32(
      this->code_heap_address + OFFSET_CodeHeap_log2_segment_size);

  /*
   * Find start
   */
  this->segment = (this->pc - this->code_heap_low) >>
    this->code_heap_log2_segment_size;
  this->block = this->code_heap_segmap_low;
  this->tag = copyin_uchar(this->block + this->segment);
}

dtrace:helper:ustack:
/!this->done && this->codecache && this->tag > 0/
{
  MARK_LINE;
  this->tag = copyin_uchar(this->block + this->segment);
  this->segment = this->segment - this->tag;
}

dtrace:helper:ustack:
/!this->done && this->codecache && this->tag > 0/
{
  MARK_LINE;
  this->tag = copyin_uchar(this->block + this->segment);
  this->segment = this->segment - this->tag;
}

dtrace:helper:ustack:
/!this->done && this->codecache && this->tag > 0/
{
  MARK_LINE;
  this->tag = copyin_uchar(this->block + this->segment);
  this->segment = this->segment - this->tag;
}

dtrace:helper:ustack:
/!this->done && this->codecache && this->tag > 0/
{
  MARK_LINE;
  this->tag = copyin_uchar(this->block + this->segment);
  this->segment = this->segment - this->tag;
}

dtrace:helper:ustack:
/!this->done && this->codecache && this->tag > 0/
{
  MARK_LINE;
  this->tag = copyin_uchar(this->block + this->segment);
  this->segment = this->segment - this->tag;
}

dtrace:helper:ustack:
/!this->done && this->codecache && this->tag > 0/
{
  MARK_LINE;
  this->error = "<couldn't find start>";
  this->done = 1;
}

dtrace:helper:ustack:
/!this->done && this->codecache/
{
  MARK_LINE;
  this->block = this->code_heap_low +
    (this->segment << this->code_heap_log2_segment_size);
  this->used = copyin_uint32(this->block + OFFSET_HeapBlockHeader_used);
}

dtrace:helper:ustack:
/!this->done && this->codecache && !this->used/
{
  MARK_LINE;
  this->error = "<block not in use>";
  this->done = 1;
}

dtrace:helper:ustack:
/!this->done && this->codecache/
{
  MARK_LINE;
  this->start = this->block + SIZE_HeapBlockHeader;
  this->vtbl = copyin_ptr(this->start);

  this->nmethod_vtbl            = (pointer) &``__1cHnmethodG__vtbl_;
  this->BufferBlob_vtbl         = (pointer) &``__1cKBufferBlobG__vtbl_;
}

dtrace:helper:ustack:
/!this->done && this->vtbl == this->nmethod_vtbl/
{
  MARK_LINE;
  this->methodPtr = copyin_ptr(this->start + OFFSET_nmethod_method);
  this->suffix = '*';
  this->isMethod = 1;
}

dtrace:helper:ustack:
/!this->done && this->vtbl == this->BufferBlob_vtbl/
{
  MARK_LINE;
  this->name = copyin_ptr(this->start + OFFSET_CodeBlob_name);
}


dtrace:helper:ustack:
/!this->done && this->vtbl == this->BufferBlob_vtbl && this->methodPtr != 0/
{
  MARK_LINE;
  this->klass = copyin_ptr(this->methodPtr);
  this->isMethod = this->klass == this->Method_vtbl;
  this->done = !this->isMethod;
}

dtrace:helper:ustack:
/!this->done && !this->isMethod/
{
  MARK_LINE;
  this->name = copyin_ptr(this->start + OFFSET_CodeBlob_name);
  this->result = this->name != 0 ? copyinstr(this->name) : "<CodeBlob>";
  this->done = 1;
}

dtrace:helper:ustack:
/!this->done && this->isMethod/
{
  MARK_LINE;
  this->constMethod = copyin_ptr(this->methodPtr +
      OFFSET_Method_constMethod);

  this->nameIndex = copyin_uint16(this->constMethod +
      OFFSET_ConstMethod_name_index);

  this->signatureIndex = copyin_uint16(this->constMethod +
      OFFSET_ConstMethod_signature_index);

  this->constantPool = copyin_ptr(this->constMethod +
      OFFSET_ConstMethod_constants);

  this->nameSymbol = copyin_ptr(this->constantPool +
      this->nameIndex * sizeof (pointer) + SIZE_ConstantPool);
  /* The symbol is a CPSlot and has lower bit set to indicate metadata */
  this->nameSymbol &= (~1); /* remove metadata lsb */

  this->nameSymbolLength = copyin_uint16(this->nameSymbol +
      OFFSET_Symbol_length);

  this->signatureSymbol = copyin_ptr(this->constantPool +
      this->signatureIndex * sizeof (pointer) + SIZE_ConstantPool);
  this->signatureSymbol &= (~1); /* remove metadata lsb */

  this->signatureSymbolLength = copyin_uint16(this->signatureSymbol +
      OFFSET_Symbol_length);

  this->klassPtr = copyin_ptr(this->constantPool +
      OFFSET_ConstantPool_pool_holder);

  this->klassSymbol = copyin_ptr(this->klassPtr +
      OFFSET_Klass_name);

  this->klassSymbolLength = copyin_uint16(this->klassSymbol +
      OFFSET_Symbol_length);

  /*
   * Enough for three strings, plus the '.', plus the trailing '\0'.
   */
  this->result = (char *) alloca(this->klassSymbolLength +
      this->nameSymbolLength +
      this->signatureSymbolLength + 2 + 1);

  copyinto(this->klassSymbol + OFFSET_Symbol_body,
      this->klassSymbolLength, this->result);

  /*
   * Add the '.' between the class and the name.
   */
  this->result[this->klassSymbolLength] = '.';

  copyinto(this->nameSymbol + OFFSET_Symbol_body,
      this->nameSymbolLength,
      this->result + this->klassSymbolLength + 1);

  copyinto(this->signatureSymbol + OFFSET_Symbol_body,
      this->signatureSymbolLength,
      this->result + this->klassSymbolLength +
      this->nameSymbolLength + 1);

  /*
   * Now we need to add a trailing '\0' and possibly a tag character.
   */
  this->result[this->klassSymbolLength + 1 + 
      this->nameSymbolLength +
      this->signatureSymbolLength] = this->suffix;
  this->result[this->klassSymbolLength + 2 + 
      this->nameSymbolLength +
      this->signatureSymbolLength] = '\0';

  this->done = 1;
}

dtrace:helper:ustack:
/this->done && this->error == (char *) NULL/
{
  this->result;   
}

dtrace:helper:ustack:
/this->done && this->error != (char *) NULL/
{
  this->error;
}

dtrace:helper:ustack:
/!this->done && this->codecache/
{
  this->done = 1;
  "error";
}


dtrace:helper:ustack:
/!this->done/
{
  NULL;
}
