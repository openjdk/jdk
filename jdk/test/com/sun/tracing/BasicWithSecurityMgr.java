/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * @test
 * @bug 6899605
 * @summary Basic unit test for tracing framework with security manager
 *          enabled
 */

import com.sun.tracing.*;
import java.lang.reflect.Method;

@ProviderName("NamedProvider")
interface BasicProvider extends Provider {
    void plainProbe();
    void probeWithArgs(int a, float f, String s, Long l);
    @ProbeName("namedProbe") void probeWithName();
    void overloadedProbe();
    void overloadedProbe(int i);
}

interface InvalidProvider extends Provider {
    int nonVoidProbe();
}

public class BasicWithSecurityMgr {

    public static ProviderFactory factory;
    public static BasicProvider bp;

    public static void main(String[] args) throws Exception {
        // enable security manager
        System.setSecurityManager(new SecurityManager());

        factory = ProviderFactory.getDefaultFactory();
        if (factory != null) {
            bp = factory.createProvider(BasicProvider.class);
        }

        testProviderFactory();
        testProbe();
        testProvider();
    }

    static void fail(String s) throws Exception {
        throw new Exception(s);
    }

    static void testProviderFactory() throws Exception {
        if (factory == null) {
            fail("ProviderFactory.getDefaultFactory: Did not create factory");
        }
        if (bp == null) {
            fail("ProviderFactory.createProvider: Did not create provider");
        }
        try {
            factory.createProvider(null);
            fail("ProviderFactory.createProvider: Did not throw NPE for null");
        } catch (NullPointerException e) {}

       try {
           factory.createProvider(InvalidProvider.class);
           fail("Factory.createProvider: Should error with non-void probes");
       } catch (IllegalArgumentException e) {}
    }

    public static void testProvider() throws Exception {

       // These just shouldn't throw any exeptions:
       bp.plainProbe();
       bp.probeWithArgs(42, (float)3.14, "spam", new Long(2L));
       bp.probeWithArgs(42, (float)3.14, null, null);
       bp.probeWithName();
       bp.overloadedProbe();
       bp.overloadedProbe(42);

       Method m = BasicProvider.class.getMethod("plainProbe");
       Probe p = bp.getProbe(m);
       if (p == null) {
           fail("Provider.getProbe: Did not return probe");
       }

       Method m2 = BasicWithSecurityMgr.class.getMethod("testProvider");
       p = bp.getProbe(m2);
       if (p != null) {
           fail("Provider.getProbe: Got probe with invalid spec");
       }

       bp.dispose();
       // These just shouldn't throw any exeptions:
       bp.plainProbe();
       bp.probeWithArgs(42, (float)3.14, "spam", new Long(2L));
       bp.probeWithArgs(42, (float)3.14, null, null);
       bp.probeWithName();
       bp.overloadedProbe();
       bp.overloadedProbe(42);

       if (bp.getProbe(m) != null) {
           fail("Provider.getProbe: Should return null after dispose()");
       }

       bp.dispose(); // just to make sure nothing bad happens
    }

    static void testProbe() throws Exception {
       Method m = BasicProvider.class.getMethod("plainProbe");
       Probe p = bp.getProbe(m);
       p.isEnabled(); // just make sure it doesn't do anything bad
       p.trigger();

       try {
         p.trigger(0);
         fail("Probe.trigger: too many arguments not caught");
       } catch (IllegalArgumentException e) {}

       p = bp.getProbe(BasicProvider.class.getMethod(
           "probeWithArgs", int.class, float.class, String.class, Long.class));
       try {
         p.trigger();
         fail("Probe.trigger: too few arguments not caught");
       } catch (IllegalArgumentException e) {}

       try {
         p.trigger((float)3.14, (float)3.14, "", new Long(0L));
         fail("Probe.trigger: wrong type primitive arguments not caught");
       } catch (IllegalArgumentException e) {}
    }
}
