/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */
/* $XConsortium: list.c /main/4 1996/10/14 15:03:56 swick $ */
/** ------------------------------------------------------------------------
        This file contains routines for manipulating generic lists.
        Lists are implemented with a "harness".  In other words, each
        node in the list consists of two pointers, one to the data item
        and one to the next node in the list.  The head of the list is
        the same struct as each node, but the "item" ptr is used to point
        to the current member of the list (used by the first_in_list and
        next_in_list functions).

 This file is available under and governed by the GNU General Public
 License version 2 only, as published by the Free Software Foundation.
 However, the following notice accompanied the original version of this
 file:

Copyright (c) 1994 Hewlett-Packard Co.
Copyright (c) 1996  X Consortium

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be included
in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE X CONSORTIUM BE LIABLE FOR ANY CLAIM, DAMAGES OR
OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of the X Consortium shall
not be used in advertising or otherwise to promote the sale, use or
other dealings in this Software without prior written authorization
from the X Consortium.

  ----------------------------------------------------------------------- **/

#include <stdio.h>
#include <malloc.h>
#include "list.h"


/** ------------------------------------------------------------------------
        Sets the pointers of the specified list to NULL.
    --------------------------------------------------------------------- **/
#if NeedFunctionPrototypes
void zero_list(list_ptr lp)
#else
void zero_list(lp)
    list_ptr lp;
#endif
{
    lp->next = NULL;
    lp->ptr.item = NULL;
}


/** ------------------------------------------------------------------------
        Adds item to the list pointed to by lp.  Finds the end of the
        list, then mallocs a new list node onto the end of the list.
        The item pointer in the new node is set to "item" passed in,
        and the next pointer in the new node is set to NULL.
        Returns 1 if successful, 0 if the malloc failed.
    -------------------------------------------------------------------- **/
#if NeedFunctionPrototypes
int32_t add_to_list(list_ptr lp, void *item)
#else
int32_t add_to_list(lp, item)
    list_ptr lp;
    void *item;
#endif
{
    while (lp->next) {
        lp = lp->next;
    }
    if ((lp->next = (list_ptr) malloc( sizeof( list_item))) == NULL) {

        return 0;
    }
    lp->next->ptr.item = item;
    lp->next->next = NULL;

    return 1;
}


/** ------------------------------------------------------------------------
        Creates a new list and sets its pointers to NULL.
        Returns a pointer to the new list.
    -------------------------------------------------------------------- **/
list_ptr new_list ()
{
    list_ptr lp;

    if (lp = (list_ptr) malloc( sizeof( list_item))) {
        lp->next = NULL;
        lp->ptr.item = NULL;
    }

    return lp;
}


/** ------------------------------------------------------------------------
        Creates a new list head, pointing to the same list as the one
        passed in.  If start_at_curr is TRUE, the new list's first item
        is the "current" item (as set by calls to first/next_in_list()).
        If start_at_curr is FALSE, the first item in the new list is the
        same as the first item in the old list.  In either case, the
        curr pointer in the new list is the same as in the old list.
        Returns a pointer to the new list head.
    -------------------------------------------------------------------- **/
#if NeedFunctionPrototypes
list_ptr dup_list_head(list_ptr lp, int32_t start_at_curr)
#else
list_ptr dup_list_head(lp, start_at_curr)
    list_ptr lp;
    int32_t start_at_curr;
#endif
{
    list_ptr new_list;

    if ((new_list = (list_ptr) malloc( sizeof( list_item))) == NULL) {

        return (list_ptr)NULL;
    }
    new_list->next = start_at_curr ? lp->ptr.curr : lp->next;
    new_list->ptr.curr = lp->ptr.curr;

    return new_list;
}


/** ------------------------------------------------------------------------
        Returns the number of items in the list.
    -------------------------------------------------------------------- **/
#if NeedFunctionPrototypes
uint32_t list_length(list_ptr lp)
#else
uint32_t list_length(lp)
    list_ptr lp;
#endif
{
    uint32_t count = 0;

    while (lp->next) {
        count++;
        lp = lp->next;
    }

    return count;
}


/** ------------------------------------------------------------------------
        Scans thru list, looking for a node whose ptr.item is equal to
        the "item" passed in.  "Equal" here means the same address - no
        attempt is made to match equivalent values stored in different
        locations.  If a match is found, that node is deleted from the
        list.  Storage for the node is freed, but not for the item itself.
        Returns a pointer to the item, so the caller can free it if it
        so desires.  If a match is not found, returns NULL.
    -------------------------------------------------------------------- **/
#if NeedFunctionPrototypes
void *delete_from_list(list_ptr lp, void *item)
#else
void *delete_from_list(lp, item)
    list_ptr lp;
    void *item;
#endif
{
    list_ptr new_next;

    while (lp->next) {
        if (lp->next->ptr.item == item) {
            new_next = lp->next->next;
            free (lp->next);
            lp->next = new_next;

            return item;
        }
        lp = lp->next;
    }

    return NULL;
}


/** ------------------------------------------------------------------------
        Deletes each node in the list *except the head*.  This allows
        the deletion of lists where the head is not malloced or created
        with new_list().  If free_items is true, each item pointed to
        from the node is freed, in addition to the node itself.
    -------------------------------------------------------------------- **/
#if NeedFunctionPrototypes
void delete_list(list_ptr lp, int32_t free_items)
#else
void delete_list(lp, free_items)
    list_ptr lp;
    int32_t free_items;
#endif
{
    list_ptr del_node;
    void *item;

    while (lp->next) {
        del_node = lp->next;
        item = del_node->ptr.item;
        lp->next = del_node->next;
        free (del_node);
        if (free_items) {
            free( item);
        }
    }
}

#if NeedFunctionPrototypes
void delete_list_destroying(list_ptr lp, void destructor(void *item))
#else
void delete_list_destroying(lp, destructor)
    list_ptr lp;
    void (*destructor)();
#endif
{
    list_ptr del_node;
    void *item;

    while (lp->next) {
        del_node = lp->next;
        item = del_node->ptr.item;
        lp->next = del_node->next;
        free( del_node);
        if (destructor) {
            destructor( item);
        }
    }
}


/** ------------------------------------------------------------------------
        Returns a ptr to the first *item* (not list node) in the list.
        Sets the list head node's curr ptr to the first node in the list.
        Returns NULL if the list is empty.
    -------------------------------------------------------------------- **/
#if NeedFunctionPrototypes
void * first_in_list(list_ptr lp)
#else
void * first_in_list(lp)
    list_ptr lp;
#endif
{
    if (! lp) {

        return NULL;
    }
    lp->ptr.curr = lp->next;

    return lp->ptr.curr ? lp->ptr.curr->ptr.item : NULL;
}

/** ------------------------------------------------------------------------
        Returns a ptr to the next *item* (not list node) in the list.
        Sets the list head node's curr ptr to the next node in the list.
        first_in_list must have been called prior.
        Returns NULL if no next item.
    -------------------------------------------------------------------- **/
#if NeedFunctionPrototypes
void * next_in_list(list_ptr lp)
#else
void * next_in_list(lp)
    list_ptr lp;
#endif
{
    if (! lp) {

        return NULL;
    }
    if (lp->ptr.curr) {
        lp->ptr.curr = lp->ptr.curr->next;
    }

    return lp->ptr.curr ? lp->ptr.curr->ptr.item : NULL;
}

#if NeedFunctionPrototypes
int32_t list_is_empty(list_ptr lp)
#else
int32_t list_is_empty(lp)
    list_ptr lp;
#endif
{
    return (lp == NULL || lp->next == NULL);
}
