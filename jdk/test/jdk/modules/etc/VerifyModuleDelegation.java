/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify the defining class loader of each module never delegates
 *          to its child class loader. Also sanity check java.compact2
 *          requires.
 * @run testng VerifyModuleDelegation
 */

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.reflect.Layer;
import java.util.Set;

import static java.lang.module.ModuleDescriptor.Requires.Modifier.*;

import org.testng.annotations.*;

import static org.testng.Assert.*;

public class VerifyModuleDelegation {
    private static final String JAVA_BASE = "java.base";
    private static final String JAVA_COMPACT1 = "java.compact1";
    private static final String JAVA_COMPACT2 = "java.compact2";

    private static final ModuleDescriptor BASE
        = new ModuleDescriptor.Builder(JAVA_BASE).build();

    private static final ModuleDescriptor COMPACT2
        = new ModuleDescriptor.Builder(JAVA_COMPACT2)
            .requires(MANDATED, JAVA_BASE)
            .requires(PUBLIC, JAVA_COMPACT1)
            .requires(PUBLIC, "java.rmi")
            .requires(PUBLIC, "java.sql")
            .requires(PUBLIC, "java.xml")
            .build();

    private static final Set<ModuleReference> MREFS
            = ModuleFinder.ofSystem().findAll();

    private void check(ModuleDescriptor md, ModuleDescriptor ref) {
        assertTrue(md.requires().size() == ref.requires().size());
        assertTrue(md.requires().containsAll(ref.requires()));
    }

    @Test
    public void checkJavaBase() {
        ModuleDescriptor md =
                MREFS.stream().map(ModuleReference::descriptor)
                     .filter(d -> d.name().equals(JAVA_BASE))
                     .findFirst().orElseThrow(Error::new);

        check(md, BASE);
    }
    @Test
    public void checkCompact2() {
        ModuleDescriptor md =
                MREFS.stream().map(ModuleReference::descriptor)
                     .filter(d -> d.name().equals(JAVA_COMPACT2))
                     .findFirst().orElseThrow(Error::new);
        check(md, COMPACT2);
    }

    @Test
    public void checkLoaderDelegation() {
        Layer boot = Layer.boot();
        MREFS.stream().map(ModuleReference::descriptor)
             .forEach(md -> md.requires().stream().forEach(req ->
                 {
                     // check if M requires D and D's loader must be either the
                     // same or an ancestor of M's loader
                     ClassLoader loader1 = boot.findLoader(md.name());
                     ClassLoader loader2 = boot.findLoader(req.name());
                     if (loader1 != loader2 && !isAncestor(loader2, loader1)) {
                         throw new Error(md.name() + " can't delegate to " +
                                         "find classes from " + req.name());
                     }
                 }));
    }

    // Returns true if p is an ancestor of cl i.e. class loader 'p' can
    // be found in the cl's delegation chain
    private static boolean isAncestor(ClassLoader p, ClassLoader cl) {
        if (p != null && cl == null) {
            return false;
        }
        ClassLoader acl = cl;
        do {
            acl = acl.getParent();
            if (p == acl) {
                return true;
            }
        } while (acl != null);
        return false;
    }
}
