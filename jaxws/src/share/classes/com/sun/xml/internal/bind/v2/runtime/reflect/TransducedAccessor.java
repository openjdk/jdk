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

import java.io.IOException;
import java.util.concurrent.Callable;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.stream.XMLStreamException;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.istack.internal.SAXException2;
import com.sun.xml.internal.bind.WhiteSpaceProcessor;
import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.model.core.ID;
import com.sun.xml.internal.bind.v2.model.impl.RuntimeModelBuilder;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeNonElementRef;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimePropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.Name;
import com.sun.xml.internal.bind.v2.runtime.Transducer;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;
import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.reflect.opt.OptimizedTransducedAccessorFactory;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Patcher;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallingContext;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.LocatorEx;

import org.xml.sax.SAXException;

/**
 * {@link Accessor} and {@link Transducer} combined into one object.
 *
 * <p>
 * This allows efficient conversions between primitive values and
 * String without using boxing.
 *
 * <p>
 * This abstraction only works for a single-value property.
 *
 * <p>
 * An instance of {@link TransducedAccessor} implicitly holds a
 * field of the {@code BeanT} that the accessors access.
 *
 * @author Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public abstract class TransducedAccessor<BeanT> {

    /**
     * @see Transducer#useNamespace()
     */
    public boolean useNamespace() {
        return false;
    }

    /**
     * Obtain the value of the field and declares the namespace URIs used in
     * the value.
     *
     * @see Transducer#declareNamespace(Object, XMLSerializer)
     */
    public void declareNamespace( BeanT o, XMLSerializer w ) throws AccessorException, SAXException {
    }

    /**
     * Prints the responsible field of the given bean to the writer.
     *
     * <p>
     * Use {@link XMLSerializer#getInstance()} to access to the namespace bindings
     *
     * @return
     *      if the accessor didn't yield a value, return null.
     */
    public abstract @Nullable CharSequence print(@NotNull BeanT o) throws AccessorException, SAXException;

    /**
     * Parses the text value into the responsible field of the given bean.
     *
     * <p>
     * Use {@link UnmarshallingContext#getInstance()} to access to the namespace bindings
     *
     * @throws AccessorException
     *      if the transducer is used to parse an user bean that uses {@link XmlValue},
     *      then this exception may occur when it tries to set the leaf value to the bean.
     * @throws RuntimeException
     *      if the lexical form is incorrect. The method may throw a RuntimeException,
     *      but it shouldn't cause the entire unmarshalling to fail.
     * @throws SAXException
     *      if the parse method found an error, the error is reported, and then
     *      the processing is aborted.
     */
    public abstract void parse(BeanT o, CharSequence lexical) throws AccessorException, SAXException;

    /**
     * Checks if the field has a value.
     */
    public abstract boolean hasValue(BeanT o) throws AccessorException;











    /**
     * Gets the {@link TransducedAccessor} appropriately configured for
     * the given property.
     *
     * <p>
     * This allows the implementation to use an optimized code.
     */
    public static <T> TransducedAccessor<T> get(JAXBContextImpl context, RuntimeNonElementRef ref) {
        Transducer xducer = RuntimeModelBuilder.createTransducer(ref);
        RuntimePropertyInfo prop = ref.getSource();

        if(prop.isCollection()) {
            return new ListTransducedAccessorImpl(xducer,prop.getAccessor(),
                    Lister.create(Navigator.REFLECTION.erasure(prop.getRawType()),prop.id(),
                    prop.getAdapter()));
        }

        if(prop.id()==ID.IDREF)
            return new IDREFTransducedAccessorImpl(prop.getAccessor());

        if(xducer.isDefault() && !context.fastBoot) {
            TransducedAccessor xa = OptimizedTransducedAccessorFactory.get(prop);
            if(xa!=null)    return xa;
        }

        if(xducer.useNamespace())
            return new CompositeContextDependentTransducedAccessorImpl( context, xducer, prop.getAccessor() );
        else
            return new CompositeTransducedAccessorImpl( context, xducer, prop.getAccessor() );
    }

    /**
     * Convenience method to write the value as a text inside an element
     * without any attributes.
     * Can be overridden for improved performance.
     *
     * <p>
     * The callee assumes that there's an associated value in the field.
     * No @xsi:type handling is expected.
     */
    public abstract void writeLeafElement(XMLSerializer w, Name tagName, BeanT o, String fieldName) throws SAXException, AccessorException, IOException, XMLStreamException;

    /**
     * Invokes one of the {@link XMLSerializer#text(String, String)} method
     * with the representation of data bested suited for this transduced accessor.
     */
    public abstract void writeText(XMLSerializer w, BeanT o, String fieldName) throws AccessorException, SAXException, IOException, XMLStreamException;

    static class CompositeContextDependentTransducedAccessorImpl<BeanT,ValueT> extends CompositeTransducedAccessorImpl<BeanT,ValueT> {
        public CompositeContextDependentTransducedAccessorImpl(JAXBContextImpl context,Transducer<ValueT> xducer, Accessor<BeanT,ValueT> acc) {
            super(context,xducer,acc);
            assert xducer.useNamespace();
        }

        public boolean useNamespace() {
            return true;
        }

        public void declareNamespace(BeanT bean, XMLSerializer w) throws AccessorException {
            ValueT o = acc.get(bean);
            if(o!=null)
                xducer.declareNamespace(o,w);
        }

        @Override
        public void writeLeafElement(XMLSerializer w, Name tagName, BeanT o, String fieldName) throws SAXException, AccessorException, IOException, XMLStreamException {
            w.startElement(tagName,null);
            declareNamespace(o,w);
            w.endNamespaceDecls(null);
            w.endAttributes();
            xducer.writeText(w,acc.get(o),fieldName);
            w.endElement();
        }
    }


    /**
     * Implementation of {@link TransducedAccessor} that
     * simply combines a {@link Transducer} and {@link Accessor}.
     */
    static class CompositeTransducedAccessorImpl<BeanT,ValueT> extends TransducedAccessor<BeanT> {
        protected final Transducer<ValueT> xducer;
        protected final Accessor<BeanT,ValueT> acc;

        public CompositeTransducedAccessorImpl(JAXBContextImpl context, Transducer<ValueT> xducer, Accessor<BeanT,ValueT> acc) {
            this.xducer = xducer;
            this.acc = acc.optimize(context);
        }

        public CharSequence print(BeanT bean) throws AccessorException {
            ValueT o = acc.get(bean);
            if(o==null)     return null;
            return xducer.print(o);
        }

        public void parse(BeanT bean, CharSequence lexical) throws AccessorException, SAXException {
            acc.set(bean,xducer.parse(lexical));
        }

        public boolean hasValue(BeanT bean) throws AccessorException {
            return acc.getUnadapted(bean)!=null;
        }

        @Override
        public void writeLeafElement(XMLSerializer w, Name tagName, BeanT o, String fieldName) throws SAXException, AccessorException, IOException, XMLStreamException {
            xducer.writeLeafElement(w,tagName,acc.get(o),fieldName);
        }

        @Override
        public void writeText(XMLSerializer w, BeanT o, String fieldName) throws AccessorException, SAXException, IOException, XMLStreamException {
            xducer.writeText(w,acc.get(o),fieldName);
        }
    }

    /**
     * {@link TransducedAccessor} for IDREF.
     *
     * BeanT: the type of the bean that contains this the IDREF field.
     * TargetT: the type of the bean pointed by IDREF.
     */
    private static final class IDREFTransducedAccessorImpl<BeanT,TargetT> extends DefaultTransducedAccessor<BeanT> {
        private final Accessor<BeanT,TargetT> acc;
        /**
         * The object that an IDREF resolves to should be
         * assignable to this type.
         */
        private final Class<TargetT> targetType;

        public IDREFTransducedAccessorImpl(Accessor<BeanT, TargetT> acc) {
            this.acc = acc;
            this.targetType = acc.getValueType();
        }

        public String print(BeanT bean) throws AccessorException, SAXException {
            TargetT target = acc.get(bean);
            if(target==null)    return null;

            XMLSerializer w = XMLSerializer.getInstance();
            try {
                String id = w.grammar.getBeanInfo(target,true).getId(target,w);
                if(id==null)
                    w.errorMissingId(target);
                return id;
            } catch (JAXBException e) {
                w.reportError(null,e);
                return null;
            }
        }

        private void assign( BeanT bean, TargetT t, UnmarshallingContext context ) throws AccessorException {
            if(!targetType.isInstance(t))
                context.handleError(Messages.UNASSIGNABLE_TYPE.format(targetType,t.getClass()));
            else
                acc.set(bean,t);
        }

        public void parse(final BeanT bean, CharSequence lexical) throws AccessorException, SAXException {
            final String idref = WhiteSpaceProcessor.trim(lexical).toString();
            final UnmarshallingContext context = UnmarshallingContext.getInstance();

            final Callable callable = context.getObjectFromId(idref,acc.valueType);
            if(callable==null) {
                // the IDResolver decided to abort it now
                context.errorUnresolvedIDREF(bean,idref,context.getLocator());
                return;
            }

            TargetT t;
            try {
                t = (TargetT)callable.call();
            } catch (SAXException e) {// from callable.call
                throw e;
            } catch (RuntimeException e) {// from callable.call
                throw e;
            } catch (Exception e) {// from callable.call
                throw new SAXException2(e);
            }
            if(t!=null) {
                assign(bean,t,context);
            } else {
                // try again later
                final LocatorEx loc = new LocatorEx.Snapshot(context.getLocator());
                context.addPatcher(new Patcher() {
                    public void run() throws SAXException {
                        try {
                            TargetT t = (TargetT)callable.call();
                            if(t==null) {
                                context.errorUnresolvedIDREF(bean,idref,loc);
                            } else {
                                assign(bean,t,context);
                            }
                        } catch (AccessorException e) {
                            context.handleError(e);
                        } catch (SAXException e) {// from callable.call
                            throw e;
                        } catch (RuntimeException e) {// from callable.call
                            throw e;
                        } catch (Exception e) {// from callable.call
                            throw new SAXException2(e);
                        }
                    }
                });
            }
        }

        public boolean hasValue(BeanT bean) throws AccessorException {
            return acc.get(bean)!=null;
        }
    }
}
