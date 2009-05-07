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
package com.sun.xml.internal.bind.v2.runtime.reflect;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;

/**
 * {@link Lister} for primitive type arrays.
 *
 * <p>
 * B y t e ArrayLister is used as the master to generate the rest of the
 * lister classes. Do not modify the generated copies.
 */
final class PrimitiveArrayListerBoolean<BeanT> extends Lister<BeanT,boolean[],Boolean,PrimitiveArrayListerBoolean.BooleanArrayPack> {

    private PrimitiveArrayListerBoolean() {
    }

    /*package*/ static void register() {
        Lister.primitiveArrayListers.put(Boolean.TYPE,new PrimitiveArrayListerBoolean());
    }

    public ListIterator<Boolean> iterator(final boolean[] objects, XMLSerializer context) {
        return new ListIterator<Boolean>() {
            int idx=0;
            public boolean hasNext() {
                return idx<objects.length;
            }

            public Boolean next() {
                return objects[idx++];
            }
        };
    }

    public BooleanArrayPack startPacking(BeanT current, Accessor<BeanT, boolean[]> acc) {
        return new BooleanArrayPack();
    }

    public void addToPack(BooleanArrayPack objects, Boolean o) {
        objects.add(o);
    }

    public void endPacking( BooleanArrayPack pack, BeanT bean, Accessor<BeanT,boolean[]> acc ) throws AccessorException {
        acc.set(bean,pack.build());
    }

    public void reset(BeanT o,Accessor<BeanT,boolean[]> acc) throws AccessorException {
        acc.set(o,new boolean[0]);
    }

    static final class BooleanArrayPack {
        boolean[] buf = new boolean[16];
        int size;

        void add(Boolean b) {
            if(buf.length==size) {
                // realloc
                boolean[] nb = new boolean[buf.length*2];
                System.arraycopy(buf,0,nb,0,buf.length);
                buf = nb;
            }
            buf[size++] = b;
        }

        boolean[] build() {
            if(buf.length==size)
                // if we are lucky enough
                return buf;

            boolean[] r = new boolean[size];
            System.arraycopy(buf,0,r,0,size);
            return r;
        }
    }
}
