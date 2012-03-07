/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind;

import java.util.concurrent.Callable;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.annotation.XmlIDREF;

import org.xml.sax.SAXException;

/**
 * Pluggable ID/IDREF handling layer.
 *
 * <p>
 * <b>THIS INTERFACE IS SUBJECT TO CHANGE WITHOUT NOTICE.</b>
 *
 * <p>
 * This 'interface' can be implemented by applications and specified to
 * {@link Unmarshaller#setProperty(String, Object)} to ovierride the ID/IDREF
 * processing of the JAXB RI like this:
 *
 * <pre>
 * unmarshaller.setProperty(IDResolver.class.getName(),new MyIDResolverImpl());
 * </pre>
 *
 * <h2>Error Handling</h2>
 * <p>
 * This component runs inside the JAXB RI unmarshaller. Therefore, it needs
 * to coordinate with the JAXB RI unmarshaller when it comes to reporting
 * errors. This makes sure that applications see consistent error handling behaviors.
 *
 * <p>
 * When the {@link #startDocument(ValidationEventHandler)} method is invoked,
 * the unmarshaller passes in a {@link ValidationEventHandler} that can be used
 * by this component to report any errors encountered during the ID/IDREF processing.
 *
 * <p>
 * When an error is detected, the error should be first reported to this
 * {@link ValidationEventHandler}. If the error is fatal or the event handler
 * decided to abort, the implementation should throw a {@link SAXException}.
 * This signals the unmarshaller to abort the processing.
 *
 * @author Kohsuke Kawaguchi
 * @since JAXB 2.0 beta
 */
public abstract class IDResolver {

    /**
     * Called when the unmarshalling starts.
     *
     * <p>
     * Since one {@link Unmarshaller} may be used multiple times
     * to unmarshal documents, one {@link IDResolver} may be used multiple times, too.
     *
     * @param eventHandler
     *      Any errors found during the unmarshalling should be reported to this object.
     */
    public void startDocument(ValidationEventHandler eventHandler) throws SAXException {

    }

    /**
     * Called after the unmarshalling completes.
     *
     * <p>
     * This is a good opporunity to reset any internal state of this object,
     * so that it doesn't keep references to other objects unnecessarily.
     */
    public void endDocument() throws SAXException {

    }

    /**
     * Binds the given object to the specified ID.
     *
     * <p>
     * While a document is being unmarshalled, every time
     * an ID value is found, this method is invoked to
     * remember the association between ID and objects.
     * This association is supposed to be used later to resolve
     * IDREFs.
     *
     * <p>
     * This method is invoked right away as soon as a new ID value is found.
     *
     * @param id
     *      The ID value found in the document being unmarshalled.
     *      Always non-null.
     * @param obj
     *      The object being unmarshalled which is going to own the ID.
     *      Always non-null.
     */
    public abstract void bind( String id, Object obj ) throws SAXException;

    /**
     * Obtains the object to be pointed by the IDREF value.
     *
     * <p>
     * While a document is being unmarshalled, every time
     * an IDREF value is found, this method is invoked immediately to
     * obtain the object that the IDREF is pointing to.
     *
     * <p>
     * This method returns a {@link Callable} to support forward-references.
     * When this method returns with a non-null return value,
     * the JAXB RI unmarshaller invokes the {@link Callable#call()} method immediately.
     * If the implementation can find the target object (in which case
     * it was a backward reference), then a non-null object shall be returned,
     * and it is used as the target object.
     *
     * <p>
     * When a forward-reference happens, the <tt>call</tt> method
     * should return null. In this case the JAXB RI unmarshaller invokes
     * the <tt>call</tt> method again after all the documents are fully unmarshalled.
     * If the <tt>call</tt> method still returns null, then the JAXB RI unmarshaller
     * treats it as an error.
     *
     * <p>
     * A {@link Callable} object returned from this method may not throw
     * any exception other than a {@link SAXException} (which means a fatal error.)
     *
     * @param id
     *      The IDREF value found in the document being unmarshalled.
     *      Always non-null.
     * @param targetType
     *      The expected type to which ID resolves to. JAXB infers this
     *      information from the signature of the fields that has {@link XmlIDREF}.
     *      When a property is a collection, this parameter will be the type
     *      of the individual item in the collection.
     * @return
     *      null if the implementation is sure that the parameter combination
     *      will never yield a valid object. Otherwise non-null.
     */
    public abstract Callable<?> resolve( String id, Class targetType ) throws SAXException;
}
