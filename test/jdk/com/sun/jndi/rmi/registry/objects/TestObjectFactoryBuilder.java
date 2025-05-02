/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import javax.naming.ConfigurationException;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;
import javax.naming.spi.ObjectFactoryBuilder;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Hashtable;

/**
 * Test library class that implements {@code javax.naming.spi.ObjectFactoryBuilder} interface.
 * Its implementation allows object factory class loading from any remote location.
 */
public class TestObjectFactoryBuilder implements ObjectFactoryBuilder {

    @Override
    public ObjectFactory createObjectFactory(Object obj, Hashtable<?, ?> environment) throws NamingException {
        System.err.println("TestObjectFactoryBuilder: Creating new object factory");
        System.err.println("Builder for object: " + obj);
        System.err.println("And for environment: " + environment);
        // Only objects of the Reference type are supported, others are rejected
        if (obj instanceof Reference ref) {
            String objectFactoryLocation = ref.getFactoryClassLocation();
            try {
                URL factoryURL = new URL(objectFactoryLocation);
                var cl = new URLClassLoader(new URL[]{factoryURL});
                Class<?> factoryClass = cl.loadClass(ref.getFactoryClassName());
                System.err.println("Loaded object factory: " + factoryClass);
                if (ObjectFactory.class.isAssignableFrom(factoryClass)) {
                    return (ObjectFactory) factoryClass
                            .getDeclaredConstructor().newInstance();
                } else {
                    throw new ConfigurationException("Test configuration error -" +
                            " loaded object factory of wrong type");
                }
            } catch (MalformedURLException e) {
                throw new ConfigurationException("Error constructing test object factory");
            } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException |
                     IllegalAccessException | InvocationTargetException e) {
                throw new ConfigurationException("Test configuration error: " +
                        "factory class cannot be loaded from the provided " +
                        "object factory location");
            }
        } else {
            throw new ConfigurationException("Test factory builder " +
                    "supports only Reference types");
        }
    }
}
