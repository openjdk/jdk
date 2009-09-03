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
package com.sun.xml.internal.ws.developer;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.bind.api.TypeReference;
import com.sun.xml.internal.ws.api.model.SEIModel;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.util.List;

/**
 * Factory to create {@link JAXBContext}.
 *
 * <p>
 * JAX-WS uses JAXB to perform databinding when you use the service endpoint interface, and normally
 * the JAX-WS RI drives JAXB and creates a necessary {@link JAXBContext} automatically.
 *
 * <p>
 * This annotation is a JAX-WS RI vendor-specific feature, which lets applications create {@link JAXBRIContext}
 * (which is the JAXB RI's {@link JAXBContext} implementation.)
 * Combined with the JAXB RI vendor extensions defined in {@link JAXBRIContext}, appliation can use this to
 * fine-tune how the databinding happens, such as by adding more classes to the binding context,
 * by controlling the namespace mappings, and so on.
 *
 * <p>
 * Applications should either use {@link UsesJAXBContextFeature} or {@link UsesJAXBContext} to instruct
 * the JAX-WS runtime to use a custom factory.
 *
 * @author Kohsuke Kawaguchi
 * @since 2.1.5
 */
public interface JAXBContextFactory {
    /**
     * Called by the JAX-WS runtime to create a {@link JAXBRIContext} for the given SEI.
     *
     * @param sei
     *      The {@link SEIModel} object being constructed. This object provides you access to
     *      what SEI is being processed, and therefore useful if you are writing a generic
     *      {@link JAXBContextFactory} that can work with arbitrary SEI classes.
     *
     * @param classesToBind
     *      List of classes that needs to be bound by JAXB. This value is computed according to
     *      the JAX-WS spec and given to you.
     *
     *      The calling JAX-WS runtime expects the returned {@link JAXBRIContext} to be capable of
     *      handling all these classes, but you can add more (which is more common), or remove some
     *      (if you know what you are doing.)
     *
     *      The callee is free to mutate this list.
     *
     * @param typeReferences
     *      List of {@link TypeReference}s, which is also a part of the input to the JAXB RI to control
     *      how the databinding happens. Most likely this will be just a pass-through to the
     *      {@link JAXBRIContext#newInstance} method.
     *
     * @return
     *      A non-null valid {@link JAXBRIContext} object.
     *
     * @throws JAXBException
     *      If the callee encounters a fatal problem and wants to abort the JAX-WS runtime processing
     *      of the given SEI, throw a {@link JAXBException}. This will cause the port instantiation
     *      to fail (if on client), or the application deployment to fail (if on server.)
     */
    @NotNull JAXBRIContext createJAXBContext(@NotNull SEIModel sei, @NotNull List<Class> classesToBind, @NotNull List<TypeReference> typeReferences) throws JAXBException;

    /**
     * The default implementation that creates {@link JAXBRIContext} according to the standard behavior.
     */
    public static final JAXBContextFactory DEFAULT = new JAXBContextFactory() {
        @NotNull
        public JAXBRIContext createJAXBContext(@NotNull SEIModel sei, @NotNull List<Class> classesToBind, @NotNull List<TypeReference> typeReferences) throws JAXBException {
            return JAXBRIContext.newInstance(classesToBind.toArray(new Class[classesToBind.size()]),
                    typeReferences, null, sei.getTargetNamespace(), false, null);
        }
    };
}
