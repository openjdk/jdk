/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Module;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Propagates openness of JAXB annottated classess packages to JAXB impl module.
 *
 * @author Roman Grigoriadi
 */
class ModuleUtil {

    private static Logger logger = Logger.getLogger("javax.xml.bind");

    /**
     * Resolves classes from context path.
     * Only one class per package is needed to access its {@link java.lang.Module}
     */
    static Class[] getClassesFromContextPath(String contextPath, ClassLoader classLoader) throws JAXBException {
        List<Class> classes = new ArrayList<>();
        if (contextPath == null || contextPath.isEmpty()){
          return classes.toArray(new Class[]{});
        }

        String [] tokens = contextPath.split(":");
        for (String pkg : tokens){

           // look for ObjectFactory and load it
           final Class<?> o;
           try {
               o = classLoader.loadClass(pkg+".ObjectFactory");
               classes.add(o);
               continue;
           } catch (ClassNotFoundException e) {
               // not necessarily an error
           }

           // look for jaxb.index and load the list of classes
           try {
               final Class firstByJaxbIndex = findFirstByJaxbIndex(pkg, classLoader);
               if (firstByJaxbIndex != null) {
                   classes.add(firstByJaxbIndex);
               }
           } catch (IOException e) {
               throw new JAXBException(e);
           }
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Resolved classes from context path: {0}", classes);
        }
        return classes.toArray(new Class[]{});
    }

    /**
     * Find first class in package by {@code jaxb.index} file.
     */
    static Class findFirstByJaxbIndex(String pkg, ClassLoader classLoader) throws IOException, JAXBException {
        final String resource = pkg.replace('.', '/') + "/jaxb.index";
        final InputStream resourceAsStream = classLoader.getResourceAsStream(resource);

        if (resourceAsStream == null) {
            return null;
        }

        BufferedReader in =
                new BufferedReader(new InputStreamReader(resourceAsStream, "UTF-8"));
        try {
            String className = in.readLine();
            while (className != null) {
                className = className.trim();
                if (className.startsWith("#") || (className.length() == 0)) {
                    className = in.readLine();
                    continue;
                }

                try {
                    return classLoader.loadClass(pkg + '.' + className);
                } catch (ClassNotFoundException e) {
                    throw new JAXBException(Messages.format(Messages.ERROR_LOAD_CLASS, className, pkg), e);
                }

            }
        } finally {
            in.close();
        }
        return null;
    }

    /**
     * Implementation may be defined in other module than {@code java.xml.bind}. In that case openness
     * {@linkplain Module#isOpen open} of classes should be delegated to implementation module.
     *
     * @param classes used to resolve module for {@linkplain Module#addOpens(String, Module)}
     * @param factorySPI used to resolve {@link Module} of the implementation.
     *
     * @throws JAXBException if ony of a classes package is not open to {@code java.xml.bind} module.
     */
    static void delegateAddOpensToImplModule(Class[] classes, Class<?> factorySPI) throws JAXBException {
        final Module implModule = factorySPI.getModule();

        Module jaxbModule = JAXBContext.class.getModule();

        for (Class cls : classes) {
            final Module classModule = cls.getModule();
            final String packageName = cls.getPackageName();
            //no need for unnamed
            if (!classModule.isNamed()) {
                continue;
            }
            //report error if they are not open to java.xml.bind
            if (!classModule.isOpen(packageName, jaxbModule)) {
                throw new JAXBException(Messages.format(Messages.JAXB_CLASSES_NOT_OPEN,
                                                        packageName, cls.getName(), classModule.getName()));
            }
            //propagate openness to impl module
            classModule.addOpens(packageName, implModule);
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Propagating openness of package {0} in {1} to {2}.",
                           new String[]{ packageName, classModule.getName(), implModule.getName() });
            }
        }
    }

}
