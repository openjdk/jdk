/*
 * Copyright 1996-1999 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

#ifndef CMDIDLIST_H
#define CMDIDLIST_H

#include "awt.h"
#include "awt_Object.h"

// Mapping from command ids to objects.
class AwtCmdIDList {
public:
    AwtCmdIDList();
    ~AwtCmdIDList();

    UINT Add(AwtObject* obj);
    AwtObject* Lookup(UINT id);
    void Remove(UINT id);

    CriticalSection    m_lock;

private:

    // next_free_index is used to build a list of free ids.  Since the
    // array index is less then 32k, we can't confuse in-use entry
    // (pointer) with an index of the next free entry.  NIL is -1.
    union CmdIDEntry {
        int next_free_index;    // index of the next entry in the free list
        AwtObject *obj;         // object that is assigned this id
    };

    CmdIDEntry *m_array;  // the vector's contents

    int m_first_free;     // head of the free list, may be -1 (nil)
    UINT m_capacity;      // size of currently allocated m_array

    void BuildFreeList(UINT first_index);
};


#endif // CMDIDLIST_H
