/*
 * Copyright 1996-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "awt_Pen.h"

GDIHashtable AwtPen::cache("Pen cache", DeleteAwtPen);

AwtPen::AwtPen(COLORREF color) {
    if (!EnsureGDIObjectAvailability()) {
        // If we've run out of GDI objects, don't try to create
        // a new one
        return;
    }
    SetColor(color);
    HPEN pen = ::CreatePen(PS_SOLID, 1, color);
    /*
     * Fix for BugTraq ID 4191297.
     * If GDI resource creation failed flush all GDIHashtables
     * to destroy unreferenced GDI resources.
     */
    if (pen == NULL) {
        cache.flushAll();
        pen = ::CreatePen(PS_SOLID, 1, color);
    }
    DASSERT(pen != NULL);
    SetHandle(pen);
    if (pen == NULL) {
        // We've already incremented the counter: decrement if
        // creation failed
        Decrement();
    }
}

AwtPen* AwtPen::Get(COLORREF color) {

    CriticalSection::Lock l(cache.getManagerLock());

    AwtPen* obj = static_cast<AwtPen*>(cache.get(
        reinterpret_cast<void*>(static_cast<INT_PTR>(color))));
    if (obj == NULL) {
        obj = new AwtPen(color);
        VERIFY(cache.put(
            reinterpret_cast<void*>(static_cast<INT_PTR>(color)),
            obj) == NULL);
    }
    obj->IncrRefCount();
    return obj;
}

void AwtPen::ReleaseInCache() {

    CriticalSection::Lock l(cache.getManagerLock());

    if (DecrRefCount() == 0) {
        cache.release(
            reinterpret_cast<void*>(static_cast<INT_PTR>(GetColor())));
    }
}

void AwtPen::DeleteAwtPen(void* pPen) {
    delete (AwtPen*)pPen;
}
