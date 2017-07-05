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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


/* **************************************************************************
 *
 * Set of malloc/realloc/calloc/strdup/free replacement macros that
 *    insert some extra words around each allocation for debugging purposes
 *    and also attempt to detect invalid uses of the malloc heap through
 *    various tricks like inserting clobber words at the head and tail of
 *    the user's area, delayed free() calls, and setting the memory to
 *    a fixed pattern on allocation and when freed.  The allocations also
 *    can include warrants so that when an area is clobbered, this
 *    package can report where the allocation took place.
 *    The macros included are:
 *              malloc(size)
 *              realloc(ptr,size)
 *              calloc(nelem,elsize)
 *              strdup(s1)
 *              free(ptr)
 *              malloc_police()   <--- Not a system function
 *    The above macros match the standard behavior of the system functions.
 *
 *    They should be used through the include file "debug_malloc.h".
 *
 *       IMPORTANT: All source files that call any of these macros
 *                  should include debug_malloc.h. This package will
 *                  not work if the memory isn't allocated and freed
 *                  by the macros in debug_malloc.h. The important issue
 *                  is that any malloc() from debug_malloc.h must be
 *                  freed by the free() in debug_malloc.h.
 *
 *    The macros in debug_malloc.h will override the normal use of
 *       malloc, realloc, calloc, strdup, and free with the functions below.
 *
 *    These functions include:
 *         void *debug_malloc(size_t, void*, int);
 *         void *debug_realloc(void*, size_t, void*, int);
 *         void *debug_calloc(size_t, size_t, void*, int);
 *         void  debug_free(void *, void*, int);
 *
 *   In addition the function debug_malloc_police() can be called to
 *      tell you what memory has not been freed.
 *         void debug_malloc_police(void*, int);
 *      The function debug_malloc_police() is available through the macro
 *      malloc_police(). Normally you would want to call this at exit()
 *      time to find out what memory is still allocated.
 *
 *   The variable malloc_watch determines if the warrants are generated.
 *      warrants are structures that include the filename and line number
 *      of the caller who allocated the memory. This structure is stored
 *      at the tail of the malloc space, which is allocated large enough
 *      to hold some clobber words at the head and tail, the user's request
 *      and the warrant record (if malloc_watch is non-zero).
 *
 *   The macro LEFT_OVER_CHAR is what the trailing bytes of an allocation
 *     are set to (when the allocation is not a multiple of 8) on allocation.
 *     At free(0 time, these bytes are double checked to make sure they were
 *     not clobbered. To remove this feature #undef LEFT_OVER_CHAR.
 *
 *   The memory freed will have the FREED_CHAR put into it. To remove this
 *     feature #undef FREED_CHAR.
 *
 *   The memory allocated (not calloc'd) will have the ALLOC_CHAR put into it
 *     at the time of allocation. To remove this feature #undef ALLOC_CHAR.
 *
 *   The macro MAX_FREE_DELAY_COUNT controls how many free blocks will
 *     be kept around before being freed. This creates a delayed affect
 *     so that free space that gets clobbered just might get detected.
 *     The free() call will immediately set the user space to the FREED_CHAR,
 *     leaving the clobber words and warrant in place (making sure they
 *     haven't been clobbered). Then the free() pointer is added to a
 *     queue of MAX_FREE_DELAY_COUNT long, and if the queue was full, the
 *     oldest free()'d memory is actually freed, getting it's entire
 *     memory length set to the FREED_CHAR.
 *
 *  WARNING: This can significantly slow down an application, depending
 *           on how many allocations are made. Also the additional memory
 *           needed for the clobber words and the warrants can be significant
 *           again, depending on how many allocations are made.
 *           In addition, the delayed free calls can create situations
 *           where you might run out of memory prematurely.
 *
 * **************************************************************************
 */

#ifdef DEBUG

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <stdarg.h>
#include "hprof.h"

/* ***************************************************************************
 * Space normally looks like (clobber Word is 64 bits and aligned to 8 bytes):
 *
 *                  -----------------
 * malloc/free get->| clobber Word  |   ---> contains -size requested by user
 *                  -----------------
 *    User gets --->| user space    |
 *                  |               |
 *                  |  | left_over  |  ---> left_over bytes will be <= 7
 *                  -----------------
 *                  | clobber Word  |   ---> contains -size requested by user
 *                  -----------------
 *                  |   Warrant     |   ---> Optional (malloc_watch!=0)
 *                  |               |        Contains filename and line number
 *                  |               |          where allocation happened
 *                  |               |
 *                  -----------------
 ***************************************************************************/

/*
 *  Flag that tells debug_malloc/debug_free/debug_realloc to police
 *   heap space usage. (This is a dynamic flag that can be turned on/off)
 */
static int      malloc_watch = 1;

/* Character to stuff into freed space */
#define FREED_CHAR  'F'

/* Character to stuff into allocated space */
#define ALLOC_CHAR  'A'

/* Character to stuff into left over trailing bytes */
#define LEFT_OVER_CHAR  'Z'

/* Number of 'free' calls that will be delayed until the end */
#define MAX_FREE_DELAY_COUNT    1
#undef MAX_FREE_DELAY_COUNT

/* Maximum name of __FILE_ stored in each malloc'd area */
#define WARRANT_NAME_MAX (32-1) /* 1 less than multiple of 8 is best */

/* Macro to convert a user pointer to the malloc pointer */
#define user2malloc_(uptr)   (((char*)(void*)uptr)-sizeof(Word))

/* Macro to convert a macro pointer to the user pointer */
#define malloc2user_(mptr)   (((char*)(void*)(mptr))+sizeof(Word))

/* Size of the warrant record (this is dynamic) */
#define warrant_space  ( malloc_watch?sizeof(Warrant_Record):0 )

/* Macro to round up a number of bytes to a multiple of sizeof(Word) bytes */
#define round_up_(n) \
        ((n)==0?0:(sizeof(Word)+(((n)-1)/sizeof(Word))*sizeof(Word)))

/* Macro to calculate the needed malloc bytes from the user's request. */
#define rbytes_(nbytes) \
    (size_t)( sizeof(Word) + round_up_(nbytes) + sizeof(Word) + warrant_space )

/* Macro to get the -size stored in space through the malloc pointer */
#define nsize1_(mptr)           (((Word*)(void*)(mptr))->nsize1)
#define nsize2_(mptr)           (((Word*)(void*)(mptr))->nsize2)

/* Macro to get the -size stored in the tail of the space through */
/*     the malloc pointer */
#define tail_nsize1_(mptr)     \
        nsize1_(((char*)(void*)(mptr))+round_up_(-nsize1_(mptr))+sizeof(Word))
#define tail_nsize2_(mptr)     \
        nsize2_(((char*)(void*)(mptr))+round_up_(-nsize1_(mptr))+sizeof(Word))

/* Macro to get the -size stored in space through the user pointer */
#define user_nsize1_(uptr)      nsize1_(user2malloc_(uptr))
#define user_nsize2_(uptr)      nsize2_(user2malloc_(uptr))

/* Macro to get the -size stored in the tail of the space through */
/*     the user pointer */
#define user_tail_nsize1_(uptr) tail_nsize1_(user2malloc_(uptr))
#define user_tail_nsize2_(uptr) tail_nsize2_(user2malloc_(uptr))

/* Macro to get the int* of the last 32bit word of user space */
#define last_user_word_(mptr)   \
        ((int*)(((char*)(void*)(mptr))+round_up_(-nsize1_(mptr))))

/* Macros to get at the warrant contents from the malloc pointer */
#define warrant_(mptr) \
  (*((Warrant_Record*)(void*)(((char*)(void*)(mptr))+round_up_(-nsize1_(mptr))+sizeof(Word)*2)))

/* This struct is allocated after the tail clobber word if malloc_watch */
/*    is true. */
typedef struct {
    void           *link;       /* Next mptr in list */
    char            name[WARRANT_NAME_MAX + 1]; /* Name of allocator */
    int             line;       /* Line number where allocated */
    int             id;         /* Nth allocation */
}               Warrant_Record;
#define warrant_link_(mptr) warrant_(mptr).link
#define warrant_name_(mptr) warrant_(mptr).name
#define warrant_line_(mptr) warrant_(mptr).line
#define warrant_id_(mptr)   warrant_(mptr).id
#define MFILE(mptr) (malloc_watch?warrant_name_(mptr):"?")
#define MLINE(mptr) (malloc_watch?warrant_line_(mptr):0)
#define MID(mptr)   (malloc_watch?warrant_id_(mptr):0)

/* This should be one machine word and is also the clobber word struct */
typedef struct {
    int             nsize1;
    int             nsize2;
}               Word;           /* Largest basic type , sizeof(double)? */

/* The first malloc pointer for the warrants */
static void    *first_warrant_mptr = NULL;

/* Counter of allocations */
static int id_counter = 0;
static int largest_size = 0;
static void * largest_addr = NULL;
static void * smallest_addr = NULL;

/* Used to isolate what the error is */
static char *debug_check;
static void *clobbered_ptr;

/* Minumum macro */
#define minimum(a,b) ((a)<(b)?(a):(b))

/* Message routine */
static void
error_message(const char * format, ...)
{
    FILE *error_fp = stderr; /* All debug_malloc.c messages */
    va_list ap;
    va_start(ap, format);
    (void)fprintf(error_fp, "debug_malloc: ");
    (void)vfprintf(error_fp, format, ap);
    (void)fprintf(error_fp, "\n");
    (void)fflush(error_fp);
    va_end(ap);
}

/* This function prints out a memory error for the memory function
 *   'name' which was called in file 'file' at line number 'line'.  The malloc
 *   pointer with the error is in 'mptr'.
 */
static void
memory_error(void *mptr, const char *name, int mid, const char *mfile, int mline, const char *file, int line)
{
    char  nice_words[512];
    char  temp[256];
    int   len;
    void *mptr_walk;

    if (name == NULL)
        name = "UNKNOWN_NAME";
    if (file == NULL)
        file = "UNKNOWN_FILE";
    md_system_error(temp, (int)sizeof(temp));
    (void)strcpy(nice_words, temp);
    if ( debug_check!=NULL ) {
       (void)md_snprintf(nice_words, sizeof(nice_words),
                    "%s The %s at %p appears to have been hit.",
                    temp, debug_check, clobbered_ptr);
    }
    len = -nsize1_(mptr);
    error_message("Error: "
                   "%s The malloc space #%d is at %p [user size=%d(0x%x)],"
                   " and was allocated from file \"%s\" at line %d."
                   " [The debug function %s() detected this error "
                   "in file \"%s\" at line %d.]",
                   nice_words, mid, mptr, len, len, mfile, mline,
                   name, file, line);

    /* Print out contents of this allocation */
    {
        int i;
        void *uptr = malloc2user_(mptr);
        char *pmess;
        pmess = temp;
        for(i=0;i<(int)sizeof(temp);i++) {
            int ch = ((unsigned char*)uptr)[i];
            if ( isprint(ch) ) {
                *pmess++ = ch;
            } else {
                *pmess++ = '\\';
                *pmess++ = 'x';
                (void)sprintf(pmess,"%02x",ch);
                pmess+=2;
            }
        }
        *pmess = 0;
        error_message("Error: %p contains user data: %s", uptr, temp);
    }

    /* Try and print out table */
    if (!malloc_watch) {
        return;
    }
    mptr_walk = first_warrant_mptr;
    if (mptr_walk != NULL) {
        error_message("Active allocations: "
           "count=%d, largest_size=%d, address range (%p,%p)",
                        id_counter, largest_size, smallest_addr, largest_addr);
        do {
            int size1;
            int size2;
            char *mfile_walk;

            if ( mptr_walk > largest_addr || mptr_walk < smallest_addr ) {
                error_message("Terminating list due to pointer corruption");
                break;
            }
            size1 = -nsize1_(mptr_walk);
            size2 = -nsize2_(mptr_walk);
            mfile_walk = MFILE(mptr_walk);
            error_message("#%d: addr=%p size1=%d size2=%d file=\"%.*s\" line=%d",
                MID(mptr_walk), mptr_walk, size1, size2,
                WARRANT_NAME_MAX, mfile_walk, MLINE(mptr_walk));
            if ( size1 != size2 || size1 > largest_size || size1 < 0 ) {
                error_message("Terminating list due to size corruption");
                break;
            }
            mptr_walk = warrant_link_(mptr_walk);
        } while (mptr_walk != NULL);
    }
    abort();
}

/* This function sets the clobber word and sets up the warrant for the input
 *   malloc pointer "mptr".
 */
static void
setup_space_and_issue_warrant(void *mptr, size_t size, const char *file, int line)
{
    register int    nbytes;

    /*LINTED*/
    nbytes = (int)size;
    if ( nbytes > largest_size || largest_addr == NULL ) largest_size = nbytes;
    /*LINTED*/
    if ( mptr > largest_addr ) largest_addr = mptr;
    /*LINTED*/
    if ( mptr < smallest_addr || smallest_addr == NULL ) smallest_addr = mptr;

    /* Must be done first: */
    nsize1_(mptr) = -nbytes;
    nsize2_(mptr) = -nbytes;
    tail_nsize1_(mptr) = -nbytes;
    tail_nsize2_(mptr) = -nbytes;

#ifdef LEFT_OVER_CHAR
    /* Fill in those few extra bytes just before the tail Word structure */
    {
        register int    trailing_extra_bytes;
        /* LINTED */
        trailing_extra_bytes = (int) (round_up_(nbytes) - nbytes);
        if (  trailing_extra_bytes > 0 ) {
            register char  *p;
            register int    i;
            p = ((char *) mptr) + sizeof(Word) + nbytes;
            for (i = 0; i < trailing_extra_bytes; i++)
                p[i] = LEFT_OVER_CHAR;
        }
    }
#endif

    /* Fill out warrant */
    if (malloc_watch) {
        static Warrant_Record zero_warrant;
        register void  *p1,
                       *p2;
        size_t len;
        int start_pos = 0;
        warrant_(mptr) = zero_warrant;
        p1 = warrant_name_(mptr);
        len = strlen(file);
        if ( len >  WARRANT_NAME_MAX )  {
            /*LINTED*/
            start_pos = (int)len - WARRANT_NAME_MAX;
        }
        p2 = ((char*)file) + start_pos;
        /*LINTED*/
        (void) memcpy(p1, p2, minimum(((int)len), WARRANT_NAME_MAX));
        warrant_line_(mptr) = line;
        warrant_id_(mptr)   = ++id_counter;
        warrant_link_(mptr) = first_warrant_mptr;
        first_warrant_mptr = mptr;
    }
}

/* This function checks the clobber words at the beginning and end of the
 *   allocated space.
 */
static void
memory_check(void *uptr, int mid, const char *mfile, int mline, const char *file, int line)
{
    int             neg_nbytes;
    int             nbytes;

    debug_check = "pointer value itself";
    clobbered_ptr = uptr;
    if (uptr == NULL)
        memory_error((void *) NULL, "memory_check", mid, mfile, mline, file, line);

    /* Check both Word structures */

    debug_check = "first beginning clobber word";
    clobbered_ptr = (char*)&user_nsize1_(uptr);
    neg_nbytes = user_nsize1_(uptr);
    if (neg_nbytes >= 0)
        memory_error(user2malloc_(uptr), "memory_check", mid, mfile, mline, file, line);

    debug_check = "second beginning clobber word";
    clobbered_ptr = (char*)&user_nsize2_(uptr);
    if (neg_nbytes != user_nsize2_(uptr))
        memory_error(user2malloc_(uptr), "memory_check", mid, mfile, mline, file, line);

    debug_check = "first ending clobber word";
    clobbered_ptr = (char*)&user_tail_nsize1_(uptr);
    if (neg_nbytes != user_tail_nsize1_(uptr))
        memory_error(user2malloc_(uptr), "memory_check", mid, mfile, mline, file, line);

    debug_check = "second ending clobber word";
    clobbered_ptr = (char*)&user_tail_nsize2_(uptr);
    if (neg_nbytes != user_tail_nsize2_(uptr))
        memory_error(user2malloc_(uptr), "memory_check", mid, mfile, mline, file, line);

    /* Get a positive count of bytes */
    nbytes = -neg_nbytes;

#ifdef LEFT_OVER_CHAR
    {
        /* Check those few extra bytes just before the tail Word structure */
        register int    trailing_extra_bytes;
        register int    i;
        register char  *p;
        /* LINTED */
        trailing_extra_bytes = (int) (round_up_(nbytes) - nbytes);
        p = ((char *) (uptr)) + nbytes;
        debug_check = "trailing left over area";
        for (i = 0; i < trailing_extra_bytes; i++) {
            clobbered_ptr = p+1;
            if (p[i] != LEFT_OVER_CHAR) {
                memory_error(user2malloc_(uptr), "memory_check", mid, mfile, mline, file, line);
            }
        }
    }
#endif

    /* Make sure debug_check is cleared */
    debug_check = NULL;
}

/* This function looks for the given malloc pointer in the police line up
 *   and removes it from the warrant list.
 *      mptr            The pointer to the malloc space being removed
 */
static int
remove_warrant(void *mptr)
{
    void           *mptr1,
                   *last_mptr1;

    /* Free it up from the list */
    if (malloc_watch && mptr != NULL) {
        int found;

        found = 0;
        last_mptr1 = NULL;
        mptr1 = first_warrant_mptr;
        while (mptr1 != NULL) {
            if (mptr1 == mptr) {
                if (last_mptr1 == NULL)
                    first_warrant_mptr = warrant_link_(mptr1);
                else
                    warrant_link_(last_mptr1) = warrant_link_(mptr1);
                found = 1;
                break;
            }
            last_mptr1 = mptr1;
            mptr1 = warrant_link_(mptr1);
        }
        return found;
    }
    return 1;
}

static void
actual_free(void *uptr, const char *file, int line)
{
    void *mptr;
    const char *mfile;
    int mline;
    int mid;
    if ( uptr == NULL )
        return;
    mptr = user2malloc_(uptr);
    memory_check(uptr, (mid=MID(mptr)), (mfile=MFILE(mptr)), (mline=MLINE(mptr)), file, line);
    if (malloc_watch && remove_warrant(mptr)==0 )
        memory_check(uptr, mid, mfile, mline, file, line);
#ifdef FREED_CHAR
    if ( mptr!=NULL ) {
        size_t nbytes = -nsize1_(mptr);
        /* LINTED */
        (void)memset(mptr, FREED_CHAR, rbytes_(nbytes));
    }
#endif
    free(mptr);
}

#ifdef MAX_FREE_DELAY_COUNT

static void *free_delay[MAX_FREE_DELAY_COUNT];
static int free_delay_pos = 0;

static void
delayed_free(void *uptr, const char* file, int line)
{
    void *mptr;
    void *olduptr = free_delay[free_delay_pos];
    size_t nbytes;
    if ( uptr==NULL )
        return;
    mptr = user2malloc_(uptr);
    memory_check(uptr, MID(mptr), MFILE(mptr), MLINE(mptr), file, line);
    if ( olduptr!=NULL ) {
        actual_free(olduptr, file, line);
    }
    free_delay[free_delay_pos] = uptr;
    free_delay_pos++;
    free_delay_pos = free_delay_pos % MAX_FREE_DELAY_COUNT;
    nbytes = -user_nsize1_(uptr);
#ifdef FREED_CHAR
    (void)memset(uptr, FREED_CHAR, (size_t)nbytes);
#endif
}

static void
delayed_free_all(const char *file, int line)
{
    int i;
    for ( i=0; i< MAX_FREE_DELAY_COUNT; i++) {
        void *olduptr = free_delay[i];
        free_delay[i] = NULL;
        if ( olduptr!=NULL ) {
            actual_free(olduptr, file, line);
        }
    }
}

#endif

void
debug_free(void *uptr, const char *file, int line)
{
    int mid = 0;

    if (uptr == NULL)
        memory_error((void *) NULL, "debug_free", mid, file, line, file, line);
#ifdef MAX_FREE_DELAY_COUNT
    delayed_free(uptr, file, line);
#else
    actual_free(uptr, file, line);
#endif
}

/* This function calls malloc(). */
void           *
debug_malloc(size_t nbytes, const char *file, int line)
{
    void           *mptr;
    void           *uptr;
    int mid = id_counter;

    /*LINTED*/
    if ((int)nbytes <= 0)
        memory_error((void *) NULL, "debug_malloc", mid, file, line, file, line);
    /* LINTED */
    mptr = malloc(rbytes_(nbytes));
    if (mptr == NULL)
        memory_error((void *) NULL, "debug_malloc", mid, file, line, file, line);
    setup_space_and_issue_warrant(mptr, nbytes, file, line);
    uptr = malloc2user_(mptr);
#ifdef ALLOC_CHAR
    (void)memset(uptr, ALLOC_CHAR, (size_t)nbytes);
#endif
    return uptr;
}

void           *
debug_realloc(void *uptr, size_t nbytes, const char *file, int line)
{
    void           *mptr;
    void           *oldmptr;
    void           *newuptr;
    size_t         oldnbytes;
    int mid = id_counter;

    oldmptr = user2malloc_(uptr);
    oldnbytes = 0;
    if ((int)nbytes <= 0)
        memory_error(oldmptr, "debug_realloc", mid, file, line, file, line);
    if (uptr != NULL) {
        memory_check(uptr, MID(oldmptr), MFILE(oldmptr), MLINE(oldmptr), file, line);
        oldnbytes = -user_nsize1_(uptr);
        if ( malloc_watch && remove_warrant(oldmptr)==0 )
            memory_check(uptr, MID(oldmptr), MFILE(oldmptr), MLINE(oldmptr), file, line);
    }
    if (uptr == NULL) {
        /* LINTED */
        mptr = malloc(rbytes_(nbytes));
    } else {
        /* LINTED */
        mptr = realloc(oldmptr, rbytes_(nbytes));
    }
    if (mptr == NULL)
        memory_error(oldmptr, "debug_realloc", mid, file, line, file, line);
    setup_space_and_issue_warrant(mptr, nbytes, file, line);
    newuptr = malloc2user_(mptr);
#ifdef ALLOC_CHAR
    if (uptr == NULL)
        (void)memset(newuptr, ALLOC_CHAR, (size_t)nbytes);
    else if ( nbytes > oldnbytes )
        (void)memset(((char*)newuptr)+oldnbytes, ALLOC_CHAR, (size_t)nbytes-oldnbytes);
#endif
    return newuptr;
}

/* This function calls calloc(). */
void           *
debug_calloc(size_t nelem, size_t elsize, const char *file, int line)
{
    void           *mptr;
    size_t          nbytes;
    int mid = id_counter;

    nbytes = nelem*elsize;
    /*LINTED*/
    if ((int)nbytes <= 0)
        memory_error((void *) NULL, "debug_calloc", mid, file, line, file, line);
    /* LINTED */
    mptr = calloc(rbytes_(nbytes),1);
    if (mptr == NULL)
        memory_error((void *) NULL, "debug_calloc", mid, file, line, file, line);
    setup_space_and_issue_warrant(mptr, nbytes, file, line);
    return malloc2user_(mptr);
}

/* This function replaces strdup(). */
char           *
debug_strdup(const char *s1, const char *file, int line)
{
    void           *mptr;
    void           *uptr;
    size_t          nbytes;
    int mid = id_counter;

    if (s1 == NULL)
        memory_error((void *) NULL, "debug_strdup", mid, file, line, file, line);
    nbytes = strlen(s1)+1;
    /*LINTED*/
    if ((int)nbytes < 0)
        memory_error((void *) NULL, "debug_strdup", mid, file, line, file, line);
    /* LINTED */
    mptr = malloc(rbytes_(nbytes));
    if (mptr == NULL)
        memory_error((void *) NULL, "debug_strdup", mid, file, line, file, line);
    setup_space_and_issue_warrant(mptr, nbytes, file, line);
    uptr = malloc2user_(mptr);
    (void)strcpy((char*)uptr, s1);
    return (char*)uptr;
}

void
debug_malloc_verify(const char *file, int line)
{
    void           *mptr;

#ifdef MAX_FREE_DELAY_COUNT
    delayed_free_all(file,line);
#endif

    if (!malloc_watch) {
        return;
    }
    mptr = first_warrant_mptr;
    if (mptr != NULL) {
        /* Check all this memory first */
        do {
            memory_check(malloc2user_(mptr), MID(mptr), MFILE(mptr), MLINE(mptr), file, line);
            mptr = warrant_link_(mptr);
        } while (mptr != NULL);
    }
}

/* Report outstanding space warrants to console. */
void
debug_malloc_police(const char *file, int line)
{
    void           *mptr;

#ifdef MAX_FREE_DELAY_COUNT
    delayed_free_all(file,line);
#endif

    if (!malloc_watch) {
        return;
    }

    mptr = first_warrant_mptr;
    if (mptr != NULL) {
        debug_malloc_verify(file, line);
        /* Now issue warrants */
        mptr = first_warrant_mptr;
        do {
            error_message("Outstanding space warrant: %p (%d bytes) allocated by %s at line %d, allocation #%d",
               mptr, -nsize1_(mptr), warrant_name_(mptr),
               warrant_line_(mptr), warrant_id_(mptr));

            mptr = warrant_link_(mptr);
        } while (mptr != NULL);
    }
}

#else

void
debug_malloc_verify(const char *file, int line)
{
    file = file;
    line = line;
}

void
debug_malloc_police(const char *file, int line)
{
    file = file;
    line = line;
}

#endif
