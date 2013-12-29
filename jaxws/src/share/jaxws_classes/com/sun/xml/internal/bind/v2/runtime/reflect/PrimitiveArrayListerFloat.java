/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime.reflect;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;

/**
 * {@link Lister} for primitive type arrays.
 * <p><b>
 *     Auto-generated, do not edit.
 * </b></p>
 * <p>
 *     B y t e ArrayLister is used as the master to generate the rest of the
 *     lister classes. Do not modify the generated copies.
 * </p>
 */
final class PrimitiveArrayListerFloat<BeanT> extends Lister<BeanT,float[],Float,PrimitiveArrayListerFloat.FloatArrayPack> {

    private PrimitiveArrayListerFloat() {
    }

    /*package*/ static void register() {
        Lister.primitiveArrayListers.put(Float.TYPE,new PrimitiveArrayListerFloat());
    }

    public ListIterator<Float> iterator(final float[] objects, XMLSerializer context) {
        return new ListIterator<Float>() {
            int idx=0;
            public boolean hasNext() {
                return idx<objects.length;
            }

            public Float next() {
                return objects[idx++];
            }
        };
    }

    public FloatArrayPack startPacking(BeanT current, Accessor<BeanT, float[]> acc) {
        return new FloatArrayPack();
    }

    public void addToPack(FloatArrayPack objects, Float o) {
        objects.add(o);
    }

    public void endPacking( FloatArrayPack pack, BeanT bean, Accessor<BeanT,float[]> acc ) throws AccessorException {
        acc.set(bean,pack.build());
    }

    public void reset(BeanT o,Accessor<BeanT,float[]> acc) throws AccessorException {
        acc.set(o,new float[0]);
    }

    static final class FloatArrayPack {
        float[] buf = new float[16];
        int size;

        void add(Float b) {
            if(buf.length==size) {
                // realloc
                float[] nb = new float[buf.length*2];
                System.arraycopy(buf,0,nb,0,buf.length);
                buf = nb;
            }
            if(b!=null)
                buf[size++] = b;
        }

        float[] build() {
            if(buf.length==size)
                // if we are lucky enough
                return buf;

            float[] r = new float[size];
            System.arraycopy(buf,0,r,0,size);
            return r;
        }
    }
}
