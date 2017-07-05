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
package com.sun.tools.internal.xjc.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

/**
 * This class implements the actual logic of {@link JAXBContext#newInstance}.
 *
 * <p>
 * This class works as a facade and all the actual work is delegated to
 * a JAXB provider that happens to be in the runtime (not necessarily the JAXB RI.)
 * This allows the generated code to be run with any JAXB provider.
 *
 * <p>
 * This code is only used when XJC generates interfaces/implementations.
 *
 * <p>
 * The trick to make this work is two ObjectFactory classes that we generate
 * in the interface/implementation mode.
 *
 * <p>
 * The public ObjectFactory follows the spec, and this is the one that's exposed
 * to users. The public ObjectFactory refers to interfaces, so they aren't
 * directly usable by a JAXB 2.0 implementation.
 *
 * <p>
 * The private one lives in the impl package, and this one is indistinguishable
 * from the ObjectFactory that we generate for the value class generation mode.
 * This private ObjectFactory refers to implementation classes, which are
 * also indistinguishable from value classes that JAXB generates.
 *
 * <p>
 * All in all, the private ObjectFactory plus implementation classes give
 * a JAXB provider an illusion that they are dealing with value classes
 * that happens to implement some interfaces.
 *
 * <p>
 * In this way, the JAXB RI can provide the portability even for the
 * interface/implementation generation mode.
 *
 * @since 2.0
 * @author Kohsuke Kawaguchi
 */
public class JAXBContextFactory {
    private static final String DOT_OBJECT_FACTORY = ".ObjectFactory";
    private static final String IMPL_DOT_OBJECT_FACTORY = ".impl.ObjectFactory";

    /**
     * The JAXB API will invoke this method via reflection
     */
    public static JAXBContext createContext( Class[] classes, Map properties ) throws JAXBException {
        Class[] r = new Class[classes.length];
        boolean modified = false;

        // find any reference to our 'public' ObjectFactory and
        // replace that to our 'private' ObjectFactory.
        for( int i=0; i<r.length; i++ ) {
            Class c = classes[i];
            String name = c.getName();
            if(name.endsWith(DOT_OBJECT_FACTORY)
            && !name.endsWith(IMPL_DOT_OBJECT_FACTORY)) {
                // we never generate into the root package, so no need to worry about FQCN "ObjectFactory"

                // if we find one, tell the real JAXB provider to
                // load foo.bar.impl.ObjectFactory
                name = name.substring(0,name.length()-DOT_OBJECT_FACTORY.length())+IMPL_DOT_OBJECT_FACTORY;

                try {
                    c = c.getClassLoader().loadClass(name);
                } catch (ClassNotFoundException e) {
                    throw new JAXBException(e);
                }

                modified = true;
            }

            r[i] = c;
        }

        if(!modified) {
            // if the class list doesn't contain any of our classes,
            // this ContextFactory shouldn't have been called in the first place
            // if we simply continue, we'll just end up with the infinite recursion.

            // the only case that I can think of where this could happen is
            // when the user puts additional classes into the JAXB-generated
            // package and pass them to JAXBContext.newInstance().
            // Under normal use, this shouldn't happen.

            // anyway, bail out now.
            // if you hit this problem and wondering how to get around the problem,
            // subscribe and send a note to users@jaxb.dev.java.net (http://jaxb.dev.java.net/)
            throw new JAXBException("Unable to find a JAXB implementation to delegate");
        }

        // delegate to the JAXB provider in the system
        return JAXBContext.newInstance(r,properties);
    }


    /**
     * The JAXB API will invoke this method via reflection
     */
    public static JAXBContext createContext( String contextPath,
                                             ClassLoader classLoader, Map properties ) throws JAXBException {

        List<Class> classes = new ArrayList<Class>();
        StringTokenizer tokens = new StringTokenizer(contextPath,":");

        // each package should be pointing to a JAXB RI generated
        // content interface package.
        //
        // translate them into a list of private ObjectFactories.
        try {
            while(tokens.hasMoreTokens()) {
                String pkg = tokens.nextToken();
                classes.add(classLoader.loadClass(pkg+IMPL_DOT_OBJECT_FACTORY));
            }
        } catch (ClassNotFoundException e) {
            throw new JAXBException(e);
        }

        // delegate to the JAXB provider in the system
        return JAXBContext.newInstance(classes.toArray(new Class[classes.size()]),properties);
    }
}
