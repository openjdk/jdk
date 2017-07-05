/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

/* Lookup Table of generic elements. */

/*
 * Each table has a unique lock, all accesses are protected.
 *
 * Table elements are identified with a 32bit unsigned int.
 *   (Also see HARE trick below, which makes the TableIndex unique per table).
 *
 * Each element has a key (N bytes) and possible additional info.
 *
 * Two elements with the same key should be the same element.
 *
 * The storage for the Key and Info cannot move, the table itself can.
 *
 * The hash table will only be allocated if we have keys, and will resize
 *    when the table needs to resize. The hash buckets just provide the
 *    reference to the first TableIndex in the hash bucket, the next
 *    field of the TableElement takes you to the next item in the hash
 *    bucket. Lookups will drift the looked up item to the head of the
 *    list.
 *
 * The full 32bit hashcode and key length is saved for comparisons, the
 *    last thing done is the actual comparison of the Key contents with
 *    keys_equal().
 *
 * Freed elements (not many tables actually free items) are managed with
 *    a bit vector and a low index where a freed element might be found.
 *    Bytes are inspected until a non-zero byte indicates a freed bit is
 *    set. A count of freed elements is also kept.
 *
 */

#include "hprof.h"

/* Macros for bit vectors: unsigned char 2^3==8 OR  unsigned int 2^5==32 */

#define BV_CHUNK_POWER_2         3  /* 2 to this power == BV_CHUNK_BITSIZE */
#define BV_CHUNK_TYPE            unsigned char

#define BV_CHUNK_BITSIZE         (((int)sizeof(BV_CHUNK_TYPE))<<3) /* x8 */
#define BV_CHUNK_INDEX_MASK      ( (1 << BV_CHUNK_POWER_2) - 1 )
#define BV_ELEMENT_COUNT(nelems) ((((nelems+1)) >> BV_CHUNK_POWER_2) + 1)

#define BV_CHUNK_ROUND(i) ((i) & ~(BV_CHUNK_INDEX_MASK))
#define BV_CHUNK(ptr, i)          \
                (((BV_CHUNK_TYPE*)(ptr))[(i) >> BV_CHUNK_POWER_2])
#define BV_CHUNK_MASK(i)          \
                (1 << ((i) & BV_CHUNK_INDEX_MASK))

/* Hash code value */

typedef unsigned HashCode;

/* Basic key for an element. What makes the element unique. */

typedef struct TableKey {
    void        *ptr;   /* Pointer to arbitrary data that forms the key. */
    int          len;   /* Length in bytes of this key. */
} TableKey;

/* Basic TableElement (but only allocated if keys are used) */

typedef struct TableElement {
    TableKey     key;   /* The element key. */
    HashCode     hcode; /* The full 32bit hashcode for the key. */
    TableIndex   next;  /* The next TableElement in the hash bucket chain. */
    void        *info;  /* Info pointer */
} TableElement;

/* Generic Lookup Table structure */

typedef struct LookupTable {
    char           name[48];            /* Name of table. */
    void          *table;               /* Pointer to array of elements. */
    TableIndex    *hash_buckets;        /* Pointer to hash bucket chains. */
    Blocks        *info_blocks;         /* Blocks space for info */
    Blocks        *key_blocks;          /* Blocks space for keys */
    TableIndex     next_index;          /* Next element available. */
    TableIndex     table_size;          /* Current size of table. */
    TableIndex     table_incr;          /* Suggested increment size. */
    TableIndex     hash_bucket_count;   /* Number of hash buckets. */
    int            elem_size;           /* Size of element. */
    int            info_size;           /* Size of info structure. */
    void          *freed_bv;            /* Freed element bit vector */
    int            freed_count;         /* Count of freed'd elements */
    TableIndex     freed_start;         /* First freed in table */
    int            resizes;             /* Count of table resizes done. */
    unsigned       bucket_walks;        /* Count of bucket walks. */
    jrawMonitorID  lock;                /* Lock for table access. */
    SerialNumber   serial_num;          /* Table serial number. */
    TableIndex     hare;                /* Rabbit (HARE) trick. */
} LookupTable;

/* To get a pointer to an element, regardless of element size. */

#define ELEMENT_PTR(ltable, i) \
        ((void*)(((char*)(ltable)->table) + (ltable)->elem_size * (i)))

/* Sanity, check all the time. */

#define SANITY_CHECK(condition) ( (condition) ? (void)0 : \
                HPROF_ERROR(JNI_FALSE, "SANITY IN QUESTION: " #condition))

/* To see if an index is valid. */

#define SANITY_CHECK_INDEX(ltable,i) SANITY_CHECK((i) < ltable->next_index)

/* Small rabbits (hares) can be hidden in the index value returned.
 *   Only the right rabbits are allowed in certain pens (LookupTables).
 *   When herding rabbits it's important to keep them separate,
 *   there are lots of rabbits, all different kinds and sizes,
 *   keeping them all separate is important to avoid cross breeding.
 */

#define _SANITY_USE_HARE
#ifdef _SANITY_USE_HARE
    #define SANITY_ADD_HARE(i,hare)    (SANITY_REMOVE_HARE(i) | (hare))
    #define SANITY_REMOVE_HARE(i)      ((i)  & 0x0FFFFFFF)
    #define SANITY_CHECK_HARE(i,hare)  SANITY_CHECK(SANITY_ADD_HARE(i,hare)==(i))
#else
    #define SANITY_ADD_HARE(i,hare)    (i)
    #define SANITY_REMOVE_HARE(i)      (i)
    #define SANITY_CHECK_HARE(i,hare)
#endif

static jrawMonitorID
lock_create(char *name)
{
    jrawMonitorID stanley;

    stanley = createRawMonitor(name);
    return stanley;
}

static void
lock_destroy(jrawMonitorID stanley)
{
    if ( stanley != NULL ) {
        destroyRawMonitor(stanley);
    }
}

static void
lock_enter(jrawMonitorID stanley)
{
    if ( stanley != NULL ) {
        rawMonitorEnter(stanley);
    }
}

static void
lock_exit(jrawMonitorID stanley)
{
    if ( stanley != NULL ) {
        rawMonitorExit(stanley);
    }
}

static void
get_key(LookupTable *ltable, TableIndex index, void **pkey_ptr, int *pkey_len)
{
    *pkey_ptr = ((TableElement*)ELEMENT_PTR(ltable,index))->key.ptr;
    *pkey_len = ((TableElement*)ELEMENT_PTR(ltable,index))->key.len;
}

static void *
get_info(LookupTable *ltable, TableIndex index)
{
    TableElement *element;

    if ( ltable->info_size == 0 ) {
        return NULL;
    }
    element = (TableElement*)ELEMENT_PTR(ltable,index);
    return element->info;
}

static void
hash_out(LookupTable *ltable, TableIndex index)
{
    if ( ltable->hash_bucket_count > 0 ) {
        TableElement *element;
        TableElement *prev_e;
        TableIndex    bucket;
        TableIndex    i;

        element = (TableElement*)ELEMENT_PTR(ltable,index);
        bucket = (element->hcode % ltable->hash_bucket_count);
        i = ltable->hash_buckets[bucket];
        HPROF_ASSERT(i!=0);
        prev_e = NULL;
        while ( i != 0 && i != index ) {
            prev_e = (TableElement*)ELEMENT_PTR(ltable,i);
            i = prev_e->next;
        }
        HPROF_ASSERT(i==index);
        if ( prev_e == NULL ) {
            ltable->hash_buckets[bucket] = element->next;
        } else {
            prev_e->next = element->next;
        }
        element->next = 0;
        element->hcode = 0;
    }
}

static jboolean
is_freed_entry(LookupTable *ltable, TableIndex index)
{
    if ( ltable->freed_bv == NULL ) {
        return JNI_FALSE;
    }
    if ( ( BV_CHUNK(ltable->freed_bv, index) & BV_CHUNK_MASK(index) ) != 0 ) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

static void
set_freed_bit(LookupTable *ltable, TableIndex index)
{
    void *p;

    HPROF_ASSERT(!is_freed_entry(ltable, index));
    p = ltable->freed_bv;
    if ( p == NULL ) {
        int size;

        /* First time for a free */
        HPROF_ASSERT(ltable->freed_start==0);
        HPROF_ASSERT(ltable->freed_start==0);
        size             = BV_ELEMENT_COUNT(ltable->table_size);
        p                = HPROF_MALLOC(size*(int)sizeof(BV_CHUNK_TYPE));
        ltable->freed_bv = p;
        (void)memset(p, 0, size*(int)sizeof(BV_CHUNK_TYPE));
    }
    BV_CHUNK(p, index) |= BV_CHUNK_MASK(index);
    ltable->freed_count++;
    if ( ltable->freed_count == 1 ) {
        /* Set freed_start for first time. */
        HPROF_ASSERT(ltable->freed_start==0);
        ltable->freed_start = index;
    } else if ( index < ltable->freed_start ) {
        /* Set freed_start to smaller value so we can be smart about search */
        HPROF_ASSERT(ltable->freed_start!=0);
        ltable->freed_start = index;
    }
    HPROF_ASSERT(ltable->freed_start!=0);
    HPROF_ASSERT(ltable->freed_start < ltable->next_index);
    HPROF_ASSERT(is_freed_entry(ltable, index));
}

static TableIndex
find_freed_entry(LookupTable *ltable)
{
    if ( ltable->freed_count > 0 ) {
        TableIndex i;
        TableIndex istart;
        void *p;
        BV_CHUNK_TYPE chunk;

        HPROF_ASSERT(BV_CHUNK_BITSIZE==(1<<BV_CHUNK_POWER_2));

        p = ltable->freed_bv;
        HPROF_ASSERT(p!=NULL);

        /* Go to beginning of chunk */
        HPROF_ASSERT(ltable->freed_start!=0);
        HPROF_ASSERT(ltable->freed_start < ltable->next_index);
        istart = BV_CHUNK_ROUND(ltable->freed_start);

        /* Find chunk with any bit set */
        chunk = 0;
        for( ; istart < ltable->next_index ; istart += BV_CHUNK_BITSIZE ) {
            chunk = BV_CHUNK(p, istart);
            if ( chunk != 0 ) {
                break;
            }
        }
        HPROF_ASSERT(chunk!=0);
        HPROF_ASSERT(chunk==BV_CHUNK(p,istart));
        HPROF_ASSERT(istart < ltable->next_index);

        /* Find bit in chunk and return index of freed item */
        for( i = istart ; i < (istart+BV_CHUNK_BITSIZE) ; i++) {
            BV_CHUNK_TYPE mask;

            mask = BV_CHUNK_MASK(i);
            if ( (chunk & mask) != 0 ) {
                HPROF_ASSERT(chunk==BV_CHUNK(p,i));
                chunk &= ~mask;
                BV_CHUNK(p, i) = chunk;
                ltable->freed_count--;
                HPROF_ASSERT(i < ltable->next_index);
                if ( ltable->freed_count > 0 ) {
                    /* Set freed_start so we can be smart about search */
                    HPROF_ASSERT((i+1) < ltable->next_index);
                    ltable->freed_start = i+1;
                } else {
                    /* Clear freed_start because there are no freed entries */
                    ltable->freed_start = 0;
                }
                HPROF_ASSERT(!is_freed_entry(ltable, i));
                return i;
            }
        }
        HPROF_ASSERT(0);
    }
    return 0;
}

static void
free_entry(LookupTable *ltable, TableIndex index)
{
    set_freed_bit(ltable, index);
    hash_out(ltable, index);
}

/* Fairly generic hash code generator (not a hash table index) */
static HashCode
hashcode(void *key_ptr, int key_len)
{
    unsigned char *     p;
    HashCode            hcode;
    int                 i;

    hcode       = 0;
    if ( key_ptr == NULL || key_len == 0 ) {
        return hcode;
    }
    i           = 0;
    p           = (unsigned char*)key_ptr;
    for ( ; i < key_len-3 ; i += 4 ) {
        /* Do a little loop unrolling */
        hcode += (
                ( (unsigned)(p[i])   << 24 ) |
                ( (unsigned)(p[i+1]) << 16 ) |
                ( (unsigned)(p[i+2]) <<  8 ) |
                ( (unsigned)(p[i+3])       )
                );
    }
    for ( ; i < key_len ; i++ ) {
        hcode += (unsigned)(p[i]);
    }
    return hcode;
}

static void
hash_in(LookupTable *ltable, TableIndex index, HashCode hcode)
{
    if ( ltable->hash_bucket_count > 0 ) {
        TableElement *element;
        TableIndex    bucket;

        bucket                        = (hcode % ltable->hash_bucket_count);
        element                       = (TableElement*)ELEMENT_PTR(ltable, index);
        element->hcode                = hcode;
        element->next                 = ltable->hash_buckets[bucket];
        ltable->hash_buckets[bucket]  = index;
    }
}

static void
resize_hash_buckets(LookupTable *ltable)
{
    /*    Don't want to do this too often. */

    /* Hash table needs resizing when it's smaller than 1/16 the number of
     *   elements used in the table. This is just a guess.
     */
    if (    ( ltable->hash_bucket_count < (ltable->next_index >> 4) )
         && ( ltable->hash_bucket_count > 0 )
         && ( ( ltable->resizes % 10 ) == 0 )
         && ( ltable->bucket_walks > 1000*ltable->hash_bucket_count )
         ) {
        int         old_size;
        int         new_size;
        TableIndex *new_buckets;
        TableIndex *old_buckets;
        int         bucket;

        /* Increase size of hash_buckets array, and rehash all elements */

        LOG3("Table resize", ltable->name, ltable->resizes);

        old_size    = ltable->hash_bucket_count;
        old_buckets = ltable->hash_buckets;
        new_size    = (ltable->next_index >> 3); /* 1/8 current used count */
        SANITY_CHECK(new_size > old_size);
        new_buckets = HPROF_MALLOC(new_size*(int)sizeof(TableIndex));
        (void)memset(new_buckets, 0, new_size*(int)sizeof(TableIndex));
        ltable->hash_bucket_count = new_size;
        ltable->hash_buckets      = new_buckets;

        for ( bucket = 0 ; bucket < old_size ; bucket++ ) {
            TableIndex    index;

            index = old_buckets[bucket];
            while ( index != 0 ) {
                TableElement *element;
                TableIndex    next;

                element       = (TableElement*)ELEMENT_PTR(ltable, index);
                next          = element->next;
                element->next = 0;
                hash_in(ltable, index, element->hcode);
                index         = next;
            }
        }
        HPROF_FREE(old_buckets);

        ltable->bucket_walks = 0;
    }
}

static void
resize(LookupTable *ltable)
{
    int   old_size;
    int   new_size;
    void *old_table;
    void *new_table;
    int   nbytes;
    int   obytes;

    LOG3("Table resize", ltable->name, ltable->resizes);

    /* Adjust increment on every resize
     *    Minimum is 1/4 the size of the current table or 512.
     */
    old_size = ltable->table_size;
    if ( ltable->table_incr < (unsigned)(old_size >> 2) ) {
        ltable->table_incr = (old_size >> 2);
    }
    if ( ltable->table_incr < 512 ) {
        ltable->table_incr = 512;
    }
    new_size  = old_size + ltable->table_incr;

    /* Basic table element array */
    obytes    = old_size * ltable->elem_size;
    nbytes    = new_size * ltable->elem_size;
    old_table = ltable->table;
    new_table = HPROF_MALLOC(nbytes);
    (void)memcpy(new_table, old_table, obytes);
    (void)memset(((char*)new_table)+obytes, 0, nbytes-obytes);
    ltable->table      = new_table;
    ltable->table_size = new_size;
    HPROF_FREE(old_table);

    /* Then bit vector for freed entries */
    if ( ltable->freed_bv != NULL ) {
        void *old_bv;
        void *new_bv;

        obytes = BV_ELEMENT_COUNT(old_size)*(int)sizeof(BV_CHUNK_TYPE);
        nbytes = BV_ELEMENT_COUNT(new_size)*(int)sizeof(BV_CHUNK_TYPE);
        old_bv = ltable->freed_bv;
        new_bv = HPROF_MALLOC(nbytes);
        (void)memcpy(new_bv, old_bv, obytes);
        (void)memset(((char*)new_bv)+obytes, 0, nbytes-obytes);
        ltable->freed_bv = new_bv;
        HPROF_FREE(old_bv);
    }

    /* Check to see if the hash table needs resizing */
    resize_hash_buckets(ltable);

    ltable->resizes++;
}

static jboolean
keys_equal(void *key_ptr1, void *key_ptr2, int key_len)
{
    unsigned char *     p1;
    unsigned char *     p2;
    int                 i;

    if ( key_len == 0 ) {
        return JNI_TRUE;
    }

    /* We know these are aligned because we malloc'd them. */

    /* Compare word by word, then byte by byte */
    p1 = (unsigned char*)key_ptr1;
    p2 = (unsigned char*)key_ptr2;
    for ( i = 0 ; i < key_len-3 ; i += 4 ) {
        /*LINTED*/
        if ( *(unsigned*)(p1+i) != *(unsigned*)(p2+i) ) {
            return JNI_FALSE;
        }
    }
    for ( ; i < key_len ; i++ ) {
        if ( p1[i] != p2[i] ) {
            return JNI_FALSE;
        }
    }
    return JNI_TRUE;
}

static TableIndex
find_entry(LookupTable *ltable, void *key_ptr, int key_len, HashCode hcode)
{
    TableIndex index;

    HPROF_ASSERT(ltable!=NULL);

    index = 0;
    if ( ltable->hash_bucket_count > 0 ) {
        TableIndex bucket;
        TableIndex prev_index;

        HPROF_ASSERT(key_ptr!=NULL);
        HPROF_ASSERT(key_len>0);
        prev_index  = 0;
        bucket      = (hcode % ltable->hash_bucket_count);
        index       = ltable->hash_buckets[bucket];
        while ( index != 0 ) {
            TableElement *element;
            TableElement *prev_element;

            element = (TableElement*)ELEMENT_PTR(ltable, index);
            if ( hcode == element->hcode &&
                 key_len == element->key.len &&
                 keys_equal(key_ptr, element->key.ptr, key_len) ) {
                /* Place this guy at the head of the bucket list */
                if ( prev_index != 0 ) {
                    prev_element = (TableElement*)ELEMENT_PTR(ltable, prev_index);
                    prev_element->next  = element->next;
                    element->next       = ltable->hash_buckets[bucket];
                    ltable->hash_buckets[bucket]    = index;
                }
                break;
            }
            prev_index = index;
            index      = element->next;
            ltable->bucket_walks++;
        }
    }
    return index;
}

static TableIndex
setup_new_entry(LookupTable *ltable, void *key_ptr, int key_len, void *info_ptr)
{
    TableIndex    index;
    TableElement *element;
    void         *info;
    void         *dup_key;

    /* Assume we need new allocations for key and info */
    dup_key  = NULL;
    info     = NULL;

    /* Look for a freed element */
    index = 0;
    if ( ltable->freed_count > 0 ) {
        index    = find_freed_entry(ltable);
    }
    if ( index != 0 ) {
        int old_key_len;

        /* Found a freed element, re-use what we can but clean it up. */
        element     = (TableElement*)ELEMENT_PTR(ltable, index);
        dup_key     = element->key.ptr;
        old_key_len = element->key.len;
        info        = element->info;
        (void)memset(element, 0, ltable->elem_size);

        /* Toss the key space if size is too small to hold new key */
        if ( key_ptr != NULL ) {
            if ( old_key_len < key_len ) {
                /* This could leak space in the Blocks if keys are variable
                 *    in size AND the table does frees of elements.
                 */
                dup_key = NULL;
            }
        }
    } else {

        /* Brand new table element */
        if ( ltable->next_index >= ltable->table_size ) {
            resize(ltable);
        }
        index = ltable->next_index++;
        element = (TableElement*)ELEMENT_PTR(ltable, index);
    }

    /* Setup info area */
    if ( ltable->info_size > 0 ) {
        if ( info == NULL ) {
            info = blocks_alloc(ltable->info_blocks, ltable->info_size);
        }
        if ( info_ptr==NULL ) {
            (void)memset(info, 0, ltable->info_size);
        } else {
            (void)memcpy(info, info_ptr, ltable->info_size);
        }
    }

    /* Setup key area if one was provided */
    if ( key_ptr != NULL ) {
        if ( dup_key == NULL ) {
            dup_key  = blocks_alloc(ltable->key_blocks, key_len);
        }
        (void)memcpy(dup_key, key_ptr, key_len);
    }

    /* Fill in element */
    element->key.ptr = dup_key;
    element->key.len = key_len;
    element->info    = info;

    return index;
}

LookupTable *
table_initialize(const char *name, int size, int incr, int bucket_count,
                        int info_size)
{
    LookupTable * ltable;
    char          lock_name[80];
    int           elem_size;
    int           key_size;

    HPROF_ASSERT(name!=NULL);
    HPROF_ASSERT(size>0);
    HPROF_ASSERT(incr>0);
    HPROF_ASSERT(bucket_count>=0);
    HPROF_ASSERT(info_size>=0);

    key_size = 1;
    ltable = (LookupTable *)HPROF_MALLOC((int)sizeof(LookupTable));
    (void)memset(ltable, 0, (int)sizeof(LookupTable));

    (void)strncpy(ltable->name, name, sizeof(ltable->name));

    elem_size = (int)sizeof(TableElement);

    ltable->next_index          = 1; /* Never use index 0 */
    ltable->table_size          = size;
    ltable->table_incr          = incr;
    ltable->hash_bucket_count   = bucket_count;
    ltable->elem_size           = elem_size;
    ltable->info_size           = info_size;
    if ( info_size > 0 ) {
        ltable->info_blocks     = blocks_init(8, info_size, incr);
    }
    if ( key_size > 0 ) {
        ltable->key_blocks      = blocks_init(8, key_size, incr);
    }
    ltable->table               = HPROF_MALLOC(size * elem_size);
    (void)memset(ltable->table, 0, size * elem_size);
    if ( bucket_count > 0 ) {
        int nbytes;

        nbytes               = (int)(bucket_count*sizeof(TableIndex));
        ltable->hash_buckets = (TableIndex*)HPROF_MALLOC(nbytes);
        (void)memset(ltable->hash_buckets, 0, nbytes);
    }

    (void)md_snprintf(lock_name, sizeof(lock_name),
                "HPROF %s table lock", name);
    lock_name[sizeof(lock_name)-1] = 0;
    ltable->lock        = lock_create(lock_name);
    ltable->serial_num  = gdata->table_serial_number_counter++;
    ltable->hare        = (ltable->serial_num << 28);

    LOG3("Table initialized", ltable->name, ltable->table_size);
    return ltable;
}

int
table_element_count(LookupTable *ltable)
{
    int nelems;

    HPROF_ASSERT(ltable!=NULL);

    lock_enter(ltable->lock); {
        nelems = ltable->next_index-1;
    } lock_exit(ltable->lock);

    return nelems;
}

void
table_free_entry(LookupTable *ltable, TableIndex index)
{
    HPROF_ASSERT(ltable!=NULL);
    SANITY_CHECK_HARE(index, ltable->hare);
    index = SANITY_REMOVE_HARE(index);
    SANITY_CHECK_INDEX(ltable, index);

    lock_enter(ltable->lock); {
        HPROF_ASSERT(!is_freed_entry(ltable, index));
        free_entry(ltable, index);
    } lock_exit(ltable->lock);
}

void
table_walk_items(LookupTable *ltable, LookupTableIterator func, void* arg)
{
    if ( ltable == NULL || ltable->next_index <= 1 ) {
        return;
    }
    HPROF_ASSERT(func!=NULL);

    lock_enter(ltable->lock); {
        TableIndex index;
        int        fcount;

        LOG3("table_walk_items() count+free", ltable->name, ltable->next_index);
        fcount = 0;
        for ( index = 1 ; index < ltable->next_index ; index++ ) {
            if ( ! is_freed_entry(ltable, index) ) {
                void *key_ptr;
                int   key_len;
                void *info;

                get_key(ltable, index, &key_ptr, &key_len);
                info = get_info(ltable, index);
                (*func)(SANITY_ADD_HARE(index, ltable->hare), key_ptr, key_len, info, arg);
                if ( is_freed_entry(ltable, index) ) {
                    fcount++;
                }
            } else {
                fcount++;
            }
        }
        LOG3("table_walk_items() count-free", ltable->name, ltable->next_index);
        HPROF_ASSERT(fcount==ltable->freed_count);
    } lock_exit(ltable->lock);
}

void
table_cleanup(LookupTable *ltable, LookupTableIterator func, void *arg)
{
    if ( ltable == NULL ) {
        return;
    }

    if ( func != NULL ) {
        table_walk_items(ltable, func, arg);
    }

    lock_enter(ltable->lock); {

        HPROF_FREE(ltable->table);
        if ( ltable->hash_buckets != NULL ) {
            HPROF_FREE(ltable->hash_buckets);
        }
        if ( ltable->freed_bv != NULL ) {
            HPROF_FREE(ltable->freed_bv);
        }
        if ( ltable->info_blocks != NULL ) {
            blocks_term(ltable->info_blocks);
            ltable->info_blocks = NULL;
        }
        if ( ltable->key_blocks != NULL ) {
            blocks_term(ltable->key_blocks);
            ltable->key_blocks = NULL;
        }

    } lock_exit(ltable->lock);

    lock_destroy(ltable->lock);
    ltable->lock = NULL;

    HPROF_FREE(ltable);
    ltable = NULL;
}

TableIndex
table_create_entry(LookupTable *ltable, void *key_ptr, int key_len, void *info_ptr)
{
    TableIndex index;
    HashCode   hcode;

    HPROF_ASSERT(ltable!=NULL);

    /* Create hash code if needed */
    hcode = 0;
    if ( ltable->hash_bucket_count > 0 ) {
        hcode = hashcode(key_ptr, key_len);
    }

    /* Create a new entry */
    lock_enter(ltable->lock); {

        /* Need to create a new entry */
        index = setup_new_entry(ltable, key_ptr, key_len, info_ptr);

        /* Add to hash table if we have one */
        if ( ltable->hash_bucket_count > 0 ) {
            hash_in(ltable, index, hcode);
        }

    } lock_exit(ltable->lock);
    return SANITY_ADD_HARE(index, ltable->hare);
}

TableIndex
table_find_entry(LookupTable *ltable, void *key_ptr, int key_len)
{
    TableIndex index;
    HashCode   hcode;

    /* Create hash code if needed */
    hcode = 0;
    if ( ltable->hash_bucket_count > 0 ) {
        hcode = hashcode(key_ptr, key_len);
    }

    /* Look for element */
    lock_enter(ltable->lock); {
        index = find_entry(ltable, key_ptr, key_len, hcode);
    } lock_exit(ltable->lock);

    return index==0 ? index : SANITY_ADD_HARE(index, ltable->hare);
}

TableIndex
table_find_or_create_entry(LookupTable *ltable, void *key_ptr, int key_len,
                jboolean *pnew_entry, void *info_ptr)
{
    TableIndex index;
    HashCode   hcode;

    /* Assume it is NOT a new entry for now */
    if ( pnew_entry ) {
        *pnew_entry = JNI_FALSE;
    }

    /* Create hash code if needed */
    hcode = 0;
    if ( ltable->hash_bucket_count > 0 ) {
        hcode = hashcode(key_ptr, key_len);
    }

    /* Look for element */
    index = 0;
    lock_enter(ltable->lock); {
        if ( ltable->hash_bucket_count > 0 ) {
            index = find_entry(ltable, key_ptr, key_len, hcode);
        }
        if ( index == 0 ) {

            /* Need to create a new entry */
            index = setup_new_entry(ltable, key_ptr, key_len, info_ptr);

            /* Add to hash table if we have one */
            if ( ltable->hash_bucket_count > 0 ) {
                hash_in(ltable, index, hcode);
            }

            if ( pnew_entry ) {
                *pnew_entry = JNI_TRUE;
            }
        }
    } lock_exit(ltable->lock);

    return SANITY_ADD_HARE(index, ltable->hare);
}

void *
table_get_info(LookupTable *ltable, TableIndex index)
{
    void *info;

    HPROF_ASSERT(ltable!=NULL);
    HPROF_ASSERT(ltable->info_size > 0);
    SANITY_CHECK_HARE(index, ltable->hare);
    index = SANITY_REMOVE_HARE(index);
    SANITY_CHECK_INDEX(ltable, index);

    lock_enter(ltable->lock); {
        HPROF_ASSERT(!is_freed_entry(ltable, index));
        info = get_info(ltable,index);
    } lock_exit(ltable->lock);

    return info;
}

void
table_get_key(LookupTable *ltable, TableIndex index, void **pkey_ptr, int *pkey_len)
{
    HPROF_ASSERT(ltable!=NULL);
    HPROF_ASSERT(pkey_ptr!=NULL);
    HPROF_ASSERT(pkey_len!=NULL);
    SANITY_CHECK_HARE(index, ltable->hare);
    HPROF_ASSERT(ltable->elem_size!=0);
    index = SANITY_REMOVE_HARE(index);
    SANITY_CHECK_INDEX(ltable, index);

    lock_enter(ltable->lock); {
        HPROF_ASSERT(!is_freed_entry(ltable, index));
        get_key(ltable, index, pkey_ptr, pkey_len);
    } lock_exit(ltable->lock);
}

void
table_lock_enter(LookupTable *ltable)
{
    lock_enter(ltable->lock);
}

void
table_lock_exit(LookupTable *ltable)
{
    lock_exit(ltable->lock);
}
