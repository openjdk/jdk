/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;

import static org.testng.Assert.*;

/*
 * @test
 * @summary white-box test to check that the built-in resolver
 *  is used by default.
 * @modules java.base/java.net:open
 * @run testng/othervm BuiltInResolverTest
 */

public class BuiltInResolverTest {

    private Field builtInResolverField, resolverField;

    @BeforeTest
    public void beforeTest() throws NoSuchFieldException {
        Class<InetAddress> inetAddressClass = InetAddress.class;
        // Needs to happen for InetAddress.resolver to be initialized
        try {
            InetAddress.getByName("test");
        } catch (UnknownHostException e) {
            // Do nothing, only want to assign resolver
        }
        builtInResolverField = inetAddressClass.getDeclaredField("BUILTIN_RESOLVER");
        builtInResolverField.setAccessible(true);
        resolverField = inetAddressClass.getDeclaredField("resolver");
        resolverField.setAccessible(true);
    }

    @Test
    public void testDefaultNSContext() throws IllegalAccessException {
        // Test that the resolver used by default is the BUILTIN_RESOLVER
        Object defaultResolverObject = builtInResolverField.get(InetAddressResolver.class);
        Object usedResolverObject = resolverField.get(InetAddressResolver.class);

        assertTrue(defaultResolverObject == usedResolverObject);

        String defaultClassName = defaultResolverObject.getClass().getCanonicalName();
        String currentClassName = usedResolverObject.getClass().getCanonicalName();

        assertNotNull(defaultClassName, "defaultClassName not set");
        assertNotNull(currentClassName, "currentClassName name not set");

        assertEquals(currentClassName, defaultClassName,
                "BUILTIN_RESOLVER resolver was not used.");
        System.err.println("Resolver used by default is the built-in resolver");
    }
}
