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
import javax.xml.bind.JAXBContextFactory;
import javax.xml.bind.JAXBException;

/**
 * @test
 * @bug 8150173
 * @summary Verifies that a factory which inherit its createContext method
 *          from an abstract subclass of JAXBContextFactory can be instantiated.
 * @modules java.xml.bind
 * @run main/othervm JAXBContextWithAbstractFactory
 */
public class JAXBContextWithAbstractFactory {
    private static JAXBContext tmp;

    public static abstract class FactoryBase implements JAXBContextFactory {
        @Override
        public JAXBContext createContext(Class<?>[] classesToBeBound,
                Map<String, ?> properties) throws JAXBException {
            return tmp;
        }

        @Override
        public JAXBContext createContext(String contextPath,
                ClassLoader classLoader, Map<String, ?> properties)
                throws JAXBException {
            return tmp;
        }
    }

    public static class Factory extends FactoryBase {}

    // test both without and then with a security manager as the code path
    // can be different when System.getSecurityManager() != null;
    public static void main(String[] args) throws JAXBException {
        System.out.println("\nWithout security manager\n");
        test();

        System.out.println("\nWith security manager\n");
        Policy.setPolicy(new Policy() {
            @Override
            public boolean implies(ProtectionDomain domain, Permission permission) {
                return true; // allow all
            }
        });
        System.setSecurityManager(new SecurityManager());
        test();
    }

    public static void test() throws JAXBException {
        System.clearProperty(JAXBContext.JAXB_CONTEXT_FACTORY);
        System.out.println(JAXBContext.JAXB_CONTEXT_FACTORY + " = "
                + System.getProperty(JAXBContext.JAXB_CONTEXT_FACTORY, ""));
        System.out.println("Calling "
                + "JAXBContext.newInstance(JAXBContextWithAbstractFactory.class)");
        tmp = JAXBContext.newInstance(JAXBContextWithAbstractFactory.class);
        System.setProperty(JAXBContext.JAXB_CONTEXT_FACTORY,
                "JAXBContextWithAbstractFactory$Factory");
        System.out.println(JAXBContext.JAXB_CONTEXT_FACTORY + " = "
                + System.getProperty(JAXBContext.JAXB_CONTEXT_FACTORY));
        System.out.println("Calling "
                + "JAXBContext.newInstance(JAXBContextWithAbstractFactory.class)");
        JAXBContext ctxt = JAXBContext.newInstance(JAXBContextWithAbstractFactory.class);
        System.out.println("Successfully loaded JAXBcontext: " +
                System.identityHashCode(ctxt) + "@" + ctxt.getClass().getName());
        if (ctxt != tmp) {
            throw new RuntimeException("Wrong JAXBContext instance"
                + "\n\texpected: "
                + System.identityHashCode(tmp) + "@" + tmp.getClass().getName()
                + "\n\tactual:   "
                + System.identityHashCode(ctxt) + "@" + ctxt.getClass().getName());
        }
    }
}
