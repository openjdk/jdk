/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/* Allocations from large blocks, no individual free's */

#include "hprof.h"

/*
 * This file contains some allocation code that allows you
 *   to have space allocated via larger blocks of space.
 * The only free allowed is of all the blocks and all the elements.
 * Elements can be of different alignments and fixed or variable sized.
 * The space allocated never moves.
 *
 */

/* Get the real size allocated based on alignment and bytes needed */
static int
real_size(int alignment, int nbytes)
{
    if ( alignment > 1 ) {
        int wasted;

        wasted = alignment - ( nbytes % alignment );
        if ( wasted != alignment ) {
            nbytes += wasted;
        }
    }
    return nbytes;
}

/* Add a new current_block to the Blocks* chain, adjust size if nbytes big. */
static void
add_block(Blocks *blocks, int nbytes)
{
    int header_size;
    int block_size;
    BlockHeader *block_header;

    HPROF_ASSERT(blocks!=NULL);
    HPROF_ASSERT(nbytes>0);

    header_size          = real_size(blocks->alignment, sizeof(BlockHeader));
    block_size           = blocks->elem_size*blocks->population;
    if ( nbytes > block_size ) {
        block_size = real_size(blocks->alignment, nbytes);
    }
    block_header         = (BlockHeader*)HPROF_MALLOC(block_size+header_size);
    block_header->next   = NULL;
    block_header->bytes_left = block_size;
    block_header->next_pos   = header_size;

    /* Link in new block */
    if ( blocks->current_block != NULL ) {
        blocks->current_block->next = block_header;
    }
    blocks->current_block = block_header;
    if ( blocks->first_block == NULL ) {
        blocks->first_block = block_header;
    }
}

/* Initialize a new Blocks */
Blocks *
blocks_init(int alignment, int elem_size, int population)
{
    Blocks *blocks;

    HPROF_ASSERT(alignment>0);
    HPROF_ASSERT(elem_size>0);
    HPROF_ASSERT(population>0);

    blocks                = (Blocks*)HPROF_MALLOC(sizeof(Blocks));
    blocks->alignment     = alignment;
    blocks->elem_size     = elem_size;
    blocks->population    = population;
    blocks->first_block   = NULL;
    blocks->current_block = NULL;
    return blocks;
}

/* Allocate bytes from a Blocks area. */
void *
blocks_alloc(Blocks *blocks, int nbytes)
{
    BlockHeader *block;
    int   pos;
    void *ptr;

    HPROF_ASSERT(blocks!=NULL);
    HPROF_ASSERT(nbytes>=0);
    if ( nbytes == 0 ) {
        return NULL;
    }

    block = blocks->current_block;
    nbytes = real_size(blocks->alignment, nbytes);
    if ( block == NULL || block->bytes_left < nbytes ) {
        add_block(blocks, nbytes);
        block = blocks->current_block;
    }
    pos = block->next_pos;
    ptr = (void*)(((char*)block)+pos);
    block->next_pos   += nbytes;
    block->bytes_left -= nbytes;
    return ptr;
}

/* Terminate the Blocks */
void
blocks_term(Blocks *blocks)
{
    BlockHeader *block;

    HPROF_ASSERT(blocks!=NULL);

    block = blocks->first_block;
    while ( block != NULL ) {
        BlockHeader *next_block;

        next_block = block->next;
        HPROF_FREE(block);
        block = next_block;
    }
    HPROF_FREE(blocks);
}
