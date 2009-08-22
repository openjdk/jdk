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

import com.sun.xml.internal.ws.api.FeatureConstructor;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.bind.api.TypeReference;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import javax.xml.ws.WebServiceFeature;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * A {@link WebServiceFeature} that instructs the JAX-WS runtime to use a specific {@link JAXBContextFactory}
 * instance of creating {@link JAXBContext}.
 *
 * @see UsesJAXBContext
 * @since 2.1.5
 * @author Kohsuke Kawaguchi
 */
public class UsesJAXBContextFeature extends WebServiceFeature {
    /**
     * Constant value identifying the {@link UsesJAXBContext} feature.
     */
    public static final String ID = "http://jax-ws.dev.java.net/features/uses-jaxb-context";

    private final JAXBContextFactory factory;

    /**
     * Creates {@link UsesJAXBContextFeature}.
     *
     * @param factoryClass
     *      This class has to have a public no-arg constructor, which will be invoked to create
     *      a new instance. {@link JAXBContextFactory#createJAXBContext(SEIModel, List, List)} will
     *      be then called to create {@link JAXBContext}.
     */
    @FeatureConstructor("value")
    public UsesJAXBContextFeature(@NotNull Class<? extends JAXBContextFactory> factoryClass) {
        try {
            factory = factoryClass.getConstructor().newInstance();
        } catch (InstantiationException e) {
            Error x = new InstantiationError(e.getMessage());
            x.initCause(e);
            throw x;
        } catch (IllegalAccessException e) {
            Error x = new IllegalAccessError(e.getMessage());
            x.initCause(e);
            throw x;
        } catch (InvocationTargetException e) {
            Error x = new InstantiationError(e.getMessage());
            x.initCause(e);
            throw x;
        } catch (NoSuchMethodException e) {
            Error x = new NoSuchMethodError(e.getMessage());
            x.initCause(e);
            throw x;
        }
    }

    /**
     * Creates {@link UsesJAXBContextFeature}.
     * This version allows {@link JAXBContextFactory} to carry application specific state.
     *
     * @param factory
     *      Uses a specific instance of {@link JAXBContextFactory} to create {@link JAXBContext}.
     */
    public UsesJAXBContextFeature(@Nullable JAXBContextFactory factory) {
        this.factory = factory;
    }

    /**
     * Creates {@link UsesJAXBContextFeature}.
     * This version allows you to create {@link JAXBRIContext} upfront and uses it.
     */
    public UsesJAXBContextFeature(@Nullable final JAXBRIContext context) {
        this.factory = new JAXBContextFactory() {
            @NotNull
            public JAXBRIContext createJAXBContext(@NotNull SEIModel sei, @NotNull List<Class> classesToBind, @NotNull List<TypeReference> typeReferences) throws JAXBException {
                return context;
            }
        };
    }

    /**
     * Gets the {@link JAXBContextFactory} instance to be used for creating {@link JAXBContext} for SEI.
     *
     * @return
     *      null if the default {@link JAXBContext} shall be used.
     */
    public @Nullable JAXBContextFactory getFactory() {
        return factory;
    }

    public String getID() {
        return ID;
    }
}
