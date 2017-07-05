/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.spi.db;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;


import com.oracle.webservices.internal.api.databinding.DatabindingModeFeature;
import com.sun.xml.internal.ws.db.glassfish.JAXBRIContextFactory;
import com.sun.xml.internal.ws.util.ServiceConfigurationError;
import com.sun.xml.internal.ws.util.ServiceFinder;

/**
 * BindingContextFactory
 *
 * @author shih-chang.chen@oracle.com
 */
abstract public class BindingContextFactory {
    public static final String DefaultDatabindingMode = DatabindingModeFeature.GLASSFISH_JAXB;
    public static final String JAXB_CONTEXT_FACTORY_PROPERTY = BindingContextFactory.class.getName();
    public static final Logger LOGGER = Logger.getLogger(BindingContextFactory.class.getName());

    // This iterator adds exception checking for proper logging.
    public static Iterator<BindingContextFactory> serviceIterator() {
        final ServiceFinder<BindingContextFactory> sf = ServiceFinder
                .find(BindingContextFactory.class);
        final Iterator<BindingContextFactory> ibcf = sf.iterator();

        return new Iterator<BindingContextFactory>() {
            private BindingContextFactory bcf;

            public boolean hasNext() {
                while (true) {
                    try {
                        if (ibcf.hasNext()) {
                            bcf = ibcf.next();
                            return true;
                        } else
                            return false;
                    } catch (ServiceConfigurationError e) {
                        LOGGER.warning("skipping factory: ServiceConfigurationError: "
                                + e.getMessage());
                    } catch (NoClassDefFoundError ncdfe) {
                        LOGGER.fine("skipping factory: NoClassDefFoundError: "
                                + ncdfe.getMessage());
                    }
                }
            }

            public BindingContextFactory next() {
                if (LOGGER.isLoggable(Level.FINER))
                    LOGGER.finer("SPI found provider: " +
                            bcf.getClass().getName());
                return bcf;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    static private List<BindingContextFactory> factories() {
        List<BindingContextFactory> factories = new java.util.ArrayList<BindingContextFactory>();
        Iterator<BindingContextFactory> ibcf = serviceIterator();
        while (ibcf.hasNext())
            factories.add(ibcf.next());

        // There should always be at least one factory available.
        if (factories.isEmpty()) {
            if (LOGGER.isLoggable(Level.FINER))
                LOGGER.log(Level.FINER, "No SPI providers for BindingContextFactory found, adding: "
                        + JAXBRIContextFactory.class.getName());
            factories.add(new JAXBRIContextFactory());
        }
        return factories;
    }

        abstract protected BindingContext newContext(JAXBContext context);

        abstract protected BindingContext newContext(BindingInfo bi);

        /**
         * Check to see if the BindingContextFactory is for the databinding mode/flavor. The
         * String parameter can be the package name of the JAXBContext implementation as well.
         * @param databinding mode/flavor or the package name of the JAXBContext implementation.
         * @return
         */
        abstract protected boolean isFor(String databinding);

        /**
         * @deprecated - Does jaxws need this?
         */
        abstract protected BindingContext getContext(Marshaller m);

    static private BindingContextFactory getFactory(String mode) {
        for (BindingContextFactory f: factories()) {
            if (f.isFor(mode))
                return f;
        }
        return null;
    }

        static public BindingContext create(JAXBContext context) throws DatabindingException {
                return getJAXBFactory(context).newContext(context);
        }

    static public BindingContext create(BindingInfo bi) {
        // Any mode configured in AbstractSEIModelImpl trumps all.
        // System property comes next, then SPI-located.
        String mode = bi.getDatabindingMode();
        if (mode != null) {
            if (LOGGER.isLoggable(Level.FINE))
                LOGGER.log(Level.FINE, "Using SEI-configured databindng mode: "
                        + mode);
        } else if ((mode = System.getProperty("BindingContextFactory")) != null) {
            // The following is left for backward compatibility and should
            // eventually be removed.
            bi.setDatabindingMode(mode);
            if (LOGGER.isLoggable(Level.FINE))
                LOGGER.log(Level.FINE, "Using databindng: " + mode
                        + " based on 'BindingContextFactory' System property");
        } else if ((mode = System.getProperty(JAXB_CONTEXT_FACTORY_PROPERTY)) != null) {
            bi.setDatabindingMode(mode);
            if (LOGGER.isLoggable(Level.FINE))
                LOGGER.log(Level.FINE, "Using databindng: " + mode
                        + " based on '" + JAXB_CONTEXT_FACTORY_PROPERTY
                        + "' System property");
        } else {
            // Find a default provider.  Note we always ensure the list
            // is always non-empty.
            for (BindingContextFactory factory : factories()) {
                if (LOGGER.isLoggable(Level.FINE))
                    LOGGER.log(Level.FINE,
                            "Using SPI-determined databindng mode: "
                                    + factory.getClass().getName());
                // Special case: no name lookup used.
                return factory.newContext(bi);
            }

            // Should never get here as the list is non-empty.
            LOGGER.log(Level.SEVERE, "No Binding Context Factories found.");
            throw new DatabindingException("No Binding Context Factories found.");
        }
        BindingContextFactory f = getFactory(mode);
        if (f != null)
            return f.newContext(bi);
        LOGGER.severe("Unknown Databinding mode: " + mode);
        throw new DatabindingException("Unknown Databinding mode: " + mode);
    }

        static public boolean isContextSupported(Object o) {
            if (o == null) return false;
                String pkgName = o.getClass().getPackage().getName();
                for (BindingContextFactory f: factories()) if (f.isFor(pkgName)) return true;
                return false;
        }

        static BindingContextFactory getJAXBFactory(Object o) {
                String pkgName = o.getClass().getPackage().getName();
                BindingContextFactory f = getFactory(pkgName);
                if (f != null) return f;
                throw new DatabindingException("Unknown JAXBContext implementation: " + o.getClass());

        }

        /**
         * @deprecated - Does jaxws need this?
         */
        static public BindingContext getBindingContext(Marshaller m) {
                return getJAXBFactory(m).getContext(m);
        }

    /**
     * Creates a new {@link BindingContext}.
     *
     * <p>
     * {@link JAXBContext#newInstance(Class[]) JAXBContext.newInstance()} methods may
     * return other JAXB providers that are not compatible with the JAX-RPC RI.
     * This method guarantees that the JAX-WS RI will finds the JAXB RI.
     *
     * @param classes
     *      Classes to be bound. See {@link JAXBContext#newInstance(Class[])} for the meaning.
     * @param typeRefs
     *      See {@link #TYPE_REFERENCES} for the meaning of this parameter.
     *      Can be null.
     * @param subclassReplacements
     *      See {@link #SUBCLASS_REPLACEMENTS} for the meaning of this parameter.
     *      Can be null.
     * @param defaultNamespaceRemap
     *      See {@link #DEFAULT_NAMESPACE_REMAP} for the meaning of this parameter.
     *      Can be null (and should be null for ordinary use of JAXB.)
     * @param c14nSupport
     *      See {@link #CANONICALIZATION_SUPPORT} for the meaning of this parameter.
     * @param ar
     *      See {@link #ANNOTATION_READER} for the meaning of this parameter.
     *      Can be null.
     * @since JAXB 2.1 EA2
     */
//    public static BindingContext newInstance(@NotNull Class[] classes,
//       @Nullable Collection<TypeInfo> typeRefs,
//       @Nullable Map<Class,Class> subclassReplacements,
//       @Nullable String defaultNamespaceRemap, boolean c14nSupport,
//       @Nullable RuntimeAnnotationReader ar) throws JAXBException {
//        return ContextFactory.createContext(classes, typeRefs, subclassReplacements,
//                defaultNamespaceRemap, c14nSupport, ar, false, false, false);
//    }
//
//    /**
//     * @deprecated
//     *      Compatibility with older versions.
//     */
//    public static BindingContext newInstance(@NotNull Class[] classes,
//        @Nullable Collection<TypeInfo> typeRefs,
//        @Nullable String defaultNamespaceRemap, boolean c14nSupport ) throws JAXBException {
//        return newInstance(classes,typeRefs, Collections.<Class,Class>emptyMap(),
//                defaultNamespaceRemap,c14nSupport,null);
//    }
}
