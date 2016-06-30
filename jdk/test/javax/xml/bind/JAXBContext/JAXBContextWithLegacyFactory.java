/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.security.Permission;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.Validator;

/**
 * @test
 * @bug 8150173
 * @summary Verifies that a JAXBContext can be created with a legacy
 *          factory class that has static createContext methods.
 * @compile -addmods java.xml.bind JAXBContextWithLegacyFactory.java
 * @run main/othervm -addmods java.xml.bind JAXBContextWithLegacyFactory
 */
public class JAXBContextWithLegacyFactory {
    private static JAXBContext tmp;

        public static class JAXBContextImpl extends JAXBContext {
        public final Class<?> creator;
        JAXBContextImpl(Class<?> creator) {
            this.creator = creator;
        }

        @Override
        public Unmarshaller createUnmarshaller() throws JAXBException {
            return tmp.createUnmarshaller();
        }

        @Override
        public Marshaller createMarshaller() throws JAXBException {
            return tmp.createMarshaller();
        }

        @Override
        public Validator createValidator() throws JAXBException {
            return tmp.createValidator();
        }
    }

    public static abstract class FactoryBase {
        public static JAXBContext createContext(Class<?>[] classesToBeBound,
                Map<String, ?> properties) throws JAXBException {
            return new JAXBContextImpl(FactoryBase.class);
        }

        public static JAXBContext createContext(String contextPath,
                ClassLoader classLoader, Map<String, ?> properties)
                throws JAXBException {
            return new JAXBContextImpl(FactoryBase.class);
        }
    }

    public static class Factory1 extends FactoryBase {}

    public static class Factory2 extends FactoryBase {
        public static JAXBContext createContext(Class<?>[] classesToBeBound,
                Map<String, ?> properties) throws JAXBException {
            return new JAXBContextImpl(Factory2.class);
        }

        public static JAXBContext createContext(String contextPath,
                ClassLoader classLoader, Map<String, ?> properties)
                throws JAXBException {
            return new JAXBContextImpl(Factory2.class);
        }
    }

    // test both without and then with a security manager as the code path
    // can be different when System.getSecurityManager() != null;
    public static void main(String[] args) throws JAXBException {
        System.out.println("\nWithout security manager\n");
        test(FactoryBase.class, FactoryBase.class);
        test(Factory1.class, FactoryBase.class);
        test(Factory2.class, Factory2.class);

        System.out.println("\nWith security manager\n");
        Policy.setPolicy(new Policy() {
            @Override
            public boolean implies(ProtectionDomain domain, Permission permission) {
                return true; // allow all
            }
        });
        System.setSecurityManager(new SecurityManager());
        test(FactoryBase.class, FactoryBase.class);
        test(Factory1.class, FactoryBase.class);
        test(Factory2.class, Factory2.class);
    }

    public static void test(Class<?> factoryClass, Class<?> creatorClass) throws JAXBException {
        System.clearProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
        System.out.println("** Testing  with Factory Class: " + factoryClass.getName());
        System.out.println(JAXBContext.JAXB_CONTEXT_FACTORY + " = "
                + System.getProperty(JAXBContext.JAXB_CONTEXT_FACTORY, ""));
        System.out.println("Calling "
                + "JAXBContext.newInstance(JAXBContextWithLegacyFactory.class)");
        tmp = JAXBContext.newInstance(JAXBContextWithLegacyFactory.class);
        System.setProperty(JAXBContext.JAXB_CONTEXT_FACTORY,
                factoryClass.getName());
        System.out.println(JAXBContext.JAXB_CONTEXT_FACTORY + " = "
                + System.getProperty(JAXBContext.JAXB_CONTEXT_FACTORY));
        System.out.println("Calling "
                + "JAXBContext.newInstance(JAXBContextWithLegacyFactory.class)");
        JAXBContext ctxt = JAXBContext.newInstance(JAXBContextWithLegacyFactory.class);
        System.out.println("Successfully loaded JAXBcontext: " +
                System.identityHashCode(ctxt) + "@" + ctxt.getClass().getName());
        if (ctxt.getClass() != JAXBContextImpl.class) {
            throw new RuntimeException("Wrong JAXBContext class"
                + "\n\texpected: "
                + System.identityHashCode(tmp) + "@" + JAXBContextImpl.class.getName()
                + "\n\tactual:   "
                + System.identityHashCode(ctxt) + "@" + ctxt.getClass().getName());
        }
        if (((JAXBContextImpl)ctxt).creator != creatorClass) {
            throw new RuntimeException("Wrong Factory class"
                + "\n\texpected: "
                + System.identityHashCode(tmp) + "@" + creatorClass.getName()
                + "\n\tactual:   "
                + System.identityHashCode(ctxt) + "@" + ((JAXBContextImpl)ctxt).creator.getName());
        }
    }
}
