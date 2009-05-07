/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.rngom.digested;

import java.util.Iterator;


/**
 * A pattern that can contain other patterns.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public abstract class DContainerPattern extends DPattern implements Iterable<DPattern> {
    private DPattern head;
    private DPattern tail;

    public DPattern firstChild() {
        return head;
    }

    public DPattern lastChild() {
        return tail;
    }

    public int countChildren() {
        int i=0;
        for( DPattern p=firstChild(); p!=null; p=p.next)
            i++;
        return i;
    }

    public Iterator<DPattern> iterator() {
        return new Iterator<DPattern>() {
            DPattern next = head;
            public boolean hasNext() {
                return next!=null;
            }

            public DPattern next() {
                DPattern r = next;
                next = next.next;
                return r;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    void add( DPattern child ) {
        if(tail==null) {
            child.prev = child.next = null;
            head = tail = child;
        } else {
            child.prev = tail;
            tail.next = child;
            child.next = null;
            tail = child;
        }
    }
}
