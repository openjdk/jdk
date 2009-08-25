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

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Stack;
import java.util.concurrent.Callable;

import javax.xml.bind.JAXBException;

import com.sun.istack.internal.SAXException2;
import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.ClassFactory;
import com.sun.xml.internal.bind.v2.TODO;
import com.sun.xml.internal.bind.v2.model.core.Adapter;
import com.sun.xml.internal.bind.v2.model.core.ID;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Patcher;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallingContext;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.LocatorEx;

import org.xml.sax.SAXException;

/**
 * Used to list individual values of a multi-value property, and
 * to pack individual values into a multi-value property.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public abstract class Lister<BeanT,PropT,ItemT,PackT> {

    protected Lister() {}

    /**
     * Iterates values of a multi-value property.
     *
     * @param context
     *      This parameter is used to support ID/IDREF handling.
     */
    public abstract ListIterator<ItemT> iterator(PropT multiValueProp, XMLSerializer context);

    /**
     * Setting values to a multi-value property starts by creating
     * a transient object called "pack" from the current field.
     */
    public abstract PackT startPacking(BeanT bean, Accessor<BeanT, PropT> acc) throws AccessorException;

    /**
     * Once the {@link #startPacking} is called, you can
     * add values to the pack by using this method.
     */
    public abstract void addToPack( PackT pack, ItemT newValue ) throws AccessorException;

    /**
     * Finally, call this method to
     * wraps up the {@code pack}. This method may update the field of
     * the given bean.
     */
    public abstract void endPacking( PackT pack, BeanT bean, Accessor<BeanT,PropT> acc ) throws AccessorException;

    /**
     * Clears the values of the property.
     */
    public abstract void reset(BeanT o,Accessor<BeanT,PropT> acc) throws AccessorException;


    /**
     * Gets a reference to the appropriate {@link Lister} object
     * if the field is a multi-value field. Otherwise null.
     *
     * @param fieldType
     *      the type of the field that stores the collection
     * @param idness
     *      ID-ness of the property.
     * @param adapter
     *      adapter to be used for individual items. can be null.
     */
    public static <BeanT,PropT,ItemT,PackT>
        Lister<BeanT,PropT,ItemT,PackT> create(Type fieldType,ID idness, Adapter<Type,Class> adapter) {

        Class rawType = Navigator.REFLECTION.erasure(fieldType);
        Class itemType;

        Lister l;
        if( rawType.isArray() ) {
            itemType = rawType.getComponentType();
            l = getArrayLister(itemType);
        } else
        if( Collection.class.isAssignableFrom(rawType) ) {
            Type bt = Navigator.REFLECTION.getBaseClass(fieldType,Collection.class);
            if(bt instanceof ParameterizedType)
                itemType = Navigator.REFLECTION.erasure(((ParameterizedType)bt).getActualTypeArguments()[0]);
            else
                itemType = Object.class;
            l = new CollectionLister(getImplClass(rawType));
        } else
            return null;

        if(idness==ID.IDREF)
            l = new IDREFS(l,itemType);

        if(adapter!=null)
            l = new AdaptedLister(l,adapter.adapterType);

        return l;
    }

    private static Class getImplClass(Class<?> fieldType) {
        return ClassFactory.inferImplClass(fieldType,COLLECTION_IMPL_CLASSES);
    }

    /**
     * Cache instances of {@link ArrayLister}s.
     */
    private static final Map<Class,WeakReference<Lister>> arrayListerCache =
        Collections.synchronizedMap(new WeakHashMap<Class,WeakReference<Lister>>());

    /**
     * Creates a lister for array type.
     */
    private static Lister getArrayLister( Class componentType ) {
        Lister l=null;
        if(componentType.isPrimitive())
            l = primitiveArrayListers.get(componentType);
        else {
            WeakReference<Lister> wr = arrayListerCache.get(componentType);
            if(wr!=null)
                l = wr.get();
            if(l==null) {
                l = new ArrayLister(componentType);
                arrayListerCache.put(componentType,new WeakReference<Lister>(l));
            }
        }
        assert l!=null;
        return l;
    }

    /**
     * {@link Lister} for an array.
     *
     * <p>
     * Array packing is slower, but we expect this to be used less frequently than
     * the {@link CollectionLister}.
     */
    private static final class ArrayLister<BeanT,ItemT> extends Lister<BeanT,ItemT[],ItemT,Pack<ItemT>> {

        private final Class<ItemT> itemType;

        public ArrayLister(Class<ItemT> itemType) {
            this.itemType = itemType;
        }

        public ListIterator<ItemT> iterator(final ItemT[] objects, XMLSerializer context) {
            return new ListIterator<ItemT>() {
                int idx=0;
                public boolean hasNext() {
                    return idx<objects.length;
                }

                public ItemT next() {
                    return objects[idx++];
                }
            };
        }

        public Pack startPacking(BeanT current, Accessor<BeanT, ItemT[]> acc) {
            return new Pack<ItemT>(itemType);
        }

        public void addToPack(Pack<ItemT> objects, ItemT o) {
            objects.add(o);
        }

        public void endPacking( Pack<ItemT> pack, BeanT bean, Accessor<BeanT,ItemT[]> acc ) throws AccessorException {
            acc.set(bean,pack.build());
        }

        public void reset(BeanT o,Accessor<BeanT,ItemT[]> acc) throws AccessorException {
            acc.set(o,(ItemT[])Array.newInstance(itemType,0));
        }

    }

    public static final class Pack<ItemT> extends ArrayList<ItemT> {
        private final Class<ItemT> itemType;

        public Pack(Class<ItemT> itemType) {
            this.itemType = itemType;
        }

        public ItemT[] build() {
            return super.toArray( (ItemT[])Array.newInstance(itemType,size()) );
        }
    }

    /**
     * Listers for the primitive type arrays, keyed by their primitive Class object.
     */
    /*package*/ static final Map<Class,Lister> primitiveArrayListers = new HashMap<Class,Lister>();

    static {
        // register primitive array listers
        PrimitiveArrayListerBoolean.register();
        PrimitiveArrayListerByte.register();
        PrimitiveArrayListerCharacter.register();
        PrimitiveArrayListerDouble.register();
        PrimitiveArrayListerFloat.register();
        PrimitiveArrayListerInteger.register();
        PrimitiveArrayListerLong.register();
        PrimitiveArrayListerShort.register();
    }

    /**
     * {@link Lister} for a collection
     */
    public static final class CollectionLister<BeanT,T extends Collection> extends Lister<BeanT,T,Object,T> {

        /**
         * Sometimes we need to create a new instance of a collection.
         * This is such an implementation class.
         */
        private final Class<? extends T> implClass;

        public CollectionLister(Class<? extends T> implClass) {
            this.implClass = implClass;
        }

        public ListIterator iterator(T collection, XMLSerializer context) {
            final Iterator itr = collection.iterator();
            return new ListIterator() {
                public boolean hasNext() {
                    return itr.hasNext();
                }
                public Object next() {
                    return itr.next();
                }
            };
        }

        public T startPacking(BeanT bean, Accessor<BeanT, T> acc) throws AccessorException {
            T collection = acc.get(bean);
            if(collection==null) {
                collection = ClassFactory.create(implClass);
                if(!acc.isAdapted())
                    acc.set(bean,collection);
            }
            collection.clear();
            return collection;
        }

        public void addToPack(T collection, Object o) {
            collection.add(o);
        }

        public void endPacking( T collection, BeanT bean, Accessor<BeanT,T> acc ) throws AccessorException {
            // this needs to be done in the endPacking, because
            // sometimes the accessor uses an adapter, and the adapter needs to see
            // the whole thing.

            // but always doing so causes a problem when this collection property
            // is getter-only

            if(acc.isAdapted())
                acc.set(bean,collection);
        }

        public void reset(BeanT bean, Accessor<BeanT, T> acc) throws AccessorException {
            T collection = acc.get(bean);
            if(collection == null) {
                return;
            }
            collection.clear();
        }
    }

    /**
     * {@link Lister} for IDREFS.
     */
    private static final class IDREFS<BeanT,PropT> extends Lister<BeanT,PropT,String,IDREFS<BeanT,PropT>.Pack> {
        private final Lister<BeanT,PropT,Object,Object> core;
        /**
         * Expected type to which IDREF resolves to.
         */
        private final Class itemType;

        public IDREFS(Lister core, Class itemType) {
            this.core = core;
            this.itemType = itemType;
        }

        public ListIterator<String> iterator(PropT prop, XMLSerializer context) {
            final ListIterator i = core.iterator(prop,context);

            return new IDREFSIterator(i, context);
        }

        public Pack startPacking(BeanT bean, Accessor<BeanT, PropT> acc) {
            return new Pack(bean,acc);
        }

        public void addToPack(Pack pack, String item) {
            pack.add(item);
        }

        public void endPacking(Pack pack, BeanT bean, Accessor<BeanT, PropT> acc) {
        }

        public void reset(BeanT bean, Accessor<BeanT, PropT> acc) throws AccessorException {
            core.reset(bean,acc);
        }

        /**
         * PackT for this lister.
         */
        private class Pack implements Patcher {
            private final BeanT bean;
            private final List<String> idrefs = new ArrayList<String>();
            private final UnmarshallingContext context;
            private final Accessor<BeanT,PropT> acc;
            private final LocatorEx location;

            public Pack(BeanT bean, Accessor<BeanT,PropT> acc) {
                this.bean = bean;
                this.acc = acc;
                this.context = UnmarshallingContext.getInstance();
                this.location = new LocatorEx.Snapshot(context.getLocator());
                context.addPatcher(this);
            }

            public void add(String item) {
                idrefs.add(item);
            }

            /**
             * Resolves IDREFS and fill in the actual array.
             */
            public void run() throws SAXException {
                try {
                    Object pack = core.startPacking(bean,acc);

                    for( String id : idrefs ) {
                        Callable callable = context.getObjectFromId(id,itemType);
                        Object t;

                        try {
                            t = (callable!=null) ? callable.call() : null;
                        } catch (SAXException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new SAXException2(e);
                        }

                        if(t==null) {
                            context.errorUnresolvedIDREF(bean,id,location);
                        } else {
                            TODO.prototype(); // TODO: check if the type of t is proper.
                            core.addToPack(pack,t);
                        }
                    }

                    core.endPacking(pack,bean,acc);
                } catch (AccessorException e) {
                    context.handleError(e);
                }
            }
        }
    }

    /**
     * {@link Iterator} for IDREFS lister.
     *
     * <p>
     * Only in ArrayElementProperty we need to get the actual
     * referenced object. This is a kind of ugly way to make that work.
     */
    public static final class IDREFSIterator implements ListIterator<String> {
        private final ListIterator i;
        private final XMLSerializer context;
        private Object last;

        private IDREFSIterator(ListIterator i, XMLSerializer context) {
            this.i = i;
            this.context = context;
        }

        public boolean hasNext() {
            return i.hasNext();
        }

        /**
         * Returns the last referenced object (not just its ID)
         */
        public Object last() {
            return last;
        }

        public String next() throws SAXException, JAXBException {
            last = i.next();
            String id = context.grammar.getBeanInfo(last,true).getId(last,context);
            if(id==null) {
                context.errorMissingId(last);
            }
            return id;
        }
    }

    /**
     * Gets the special {@link Lister} used to recover from an error.
     */
    @SuppressWarnings("unchecked")
    public static <A,B,C,D> Lister<A,B,C,D> getErrorInstance() {
        return ERROR;
    }

    public static final Lister ERROR = new Lister() {
        public ListIterator iterator(Object o, XMLSerializer context) {
            return EMPTY_ITERATOR;
        }

        public Object startPacking(Object o, Accessor accessor) {
            return null;
        }

        public void addToPack(Object o, Object o1) {
        }

        public void endPacking(Object o, Object o1, Accessor accessor) {
        }

        public void reset(Object o, Accessor accessor) {
        }
    };

    private static final ListIterator EMPTY_ITERATOR = new ListIterator() {
        public boolean hasNext() {
            return false;
        }

        public Object next() {
            throw new IllegalStateException();
        }
    };

    private static final Class[] COLLECTION_IMPL_CLASSES = new Class[] {
        ArrayList.class,
        LinkedList.class,
        HashSet.class,
        TreeSet.class,
        Stack.class
    };
}
