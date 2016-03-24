/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @run testng ModuleReferenceTest
 * @summary Basic tests for java.lang.module.ModuleReference
 */

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.util.function.Supplier;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class ModuleReferenceTest {

    private Supplier<ModuleReader> makeSupplier() {
        return () -> { throw new UnsupportedOperationException(); };
    }

    public void testBasic() throws Exception {
        ModuleDescriptor descriptor
            = new ModuleDescriptor.Builder("m")
                .exports("p")
                .exports("q")
                .conceals("p.internal")
                .build();

        URI uri = URI.create("module:/m");

        Supplier<ModuleReader> supplier = makeSupplier();

        ModuleReference mref = new ModuleReference(descriptor, uri, supplier);

        assertTrue(mref.descriptor().equals(descriptor));
        assertTrue(mref.location().get().equals(uri));

        // check that the supplier is called
        try {
            mref.open();
            assertTrue(false);
        } catch (UnsupportedOperationException expected) { }
    }


    @Test(expectedExceptions = { NullPointerException.class })
    public void testNullDescriptor() throws Exception {
        URI location = URI.create("module:/m");
        new ModuleReference(null, location, makeSupplier());
    }

    public void testNullLocation() {
        ModuleDescriptor descriptor
            = new ModuleDescriptor.Builder("m")
                .exports("p")
                .build();
        Supplier<ModuleReader> supplier = makeSupplier();
        ModuleReference mref = new ModuleReference(descriptor, null, supplier);
        assertTrue(!mref.location().isPresent());
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testNullSupplier() throws Exception {
        ModuleDescriptor descriptor = new ModuleDescriptor.Builder("m").build();
        URI location = URI.create("module:/m");
        new ModuleReference(descriptor, location, null);
    }


    public void testEqualsAndHashCode() {
        ModuleDescriptor descriptor1
            = new ModuleDescriptor.Builder("m1")
                .exports("p")
                .build();
        ModuleDescriptor descriptor2
            = new ModuleDescriptor.Builder("m1")
                .exports("p")
                .build();

        URI uri = URI.create("module:/m1");
        Supplier<ModuleReader> supplier = makeSupplier();

        ModuleReference mref1 = new ModuleReference(descriptor1, uri, supplier);
        ModuleReference mref2 = new ModuleReference(descriptor2, uri, supplier);
        ModuleReference mref3 = new ModuleReference(descriptor1, null, supplier);

        assertTrue(mref1.equals(mref1));
        assertTrue(mref1.equals(mref1));
        assertTrue(mref2.equals(mref1));
        assertTrue(mref1.hashCode() == mref2.hashCode());

        assertTrue(mref3.equals(mref3));
        assertFalse(mref3.equals(mref1));
        assertFalse(mref1.equals(mref3));
    }


    public void testToString() {
        ModuleDescriptor descriptor = new ModuleDescriptor.Builder("m1").build();
        URI uri = URI.create("module:/m1");
        Supplier<ModuleReader> supplier = makeSupplier();
        ModuleReference mref = new ModuleReference(descriptor, uri, supplier);
        String s = mref.toString();
        assertTrue(s.contains("m1"));
        assertTrue(s.contains(uri.toString()));
    }

}
