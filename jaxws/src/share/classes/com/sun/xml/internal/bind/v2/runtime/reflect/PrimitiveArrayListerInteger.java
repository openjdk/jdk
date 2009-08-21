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
final class PrimitiveArrayListerInteger<BeanT> extends Lister<BeanT,int[],Integer,PrimitiveArrayListerInteger.IntegerArrayPack> {

    private PrimitiveArrayListerInteger() {
    }

    /*package*/ static void register() {
        Lister.primitiveArrayListers.put(Integer.TYPE,new PrimitiveArrayListerInteger());
    }

    public ListIterator<Integer> iterator(final int[] objects, XMLSerializer context) {
        return new ListIterator<Integer>() {
            int idx=0;
            public boolean hasNext() {
                return idx<objects.length;
            }

            public Integer next() {
                return objects[idx++];
            }
        };
    }

    public IntegerArrayPack startPacking(BeanT current, Accessor<BeanT, int[]> acc) {
        return new IntegerArrayPack();
    }

    public void addToPack(IntegerArrayPack objects, Integer o) {
        objects.add(o);
    }

    public void endPacking( IntegerArrayPack pack, BeanT bean, Accessor<BeanT,int[]> acc ) throws AccessorException {
        acc.set(bean,pack.build());
    }

    public void reset(BeanT o,Accessor<BeanT,int[]> acc) throws AccessorException {
        acc.set(o,new int[0]);
    }

    static final class IntegerArrayPack {
        int[] buf = new int[16];
        int size;

        void add(Integer b) {
            if(buf.length==size) {
                // realloc
                int[] nb = new int[buf.length*2];
                System.arraycopy(buf,0,nb,0,buf.length);
                buf = nb;
            }
            if(b!=null)
                buf[size++] = b;
        }

        int[] build() {
            if(buf.length==size)
                // if we are lucky enough
                return buf;

            int[] r = new int[size];
            System.arraycopy(buf,0,r,0,size);
            return r;
        }
    }
}
