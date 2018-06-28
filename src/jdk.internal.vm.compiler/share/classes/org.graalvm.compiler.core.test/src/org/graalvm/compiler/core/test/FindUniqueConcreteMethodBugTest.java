/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class FindUniqueConcreteMethodBugTest extends GraalCompilerTest {

    // To cause a C1 or C2 crash: -DFindUniqueConcreteMethodBugTest.ITERATIONS=10000
    private static final int ITERATIONS = Integer.getInteger("FindUniqueConcreteMethodBugTest.ITERATIONS", 100);

    /**
     * Executing {@link ResolvedJavaType#findUniqueConcreteMethod(ResolvedJavaMethod)} for the
     * method {@link Person#getName()} on the type {@link AbstractPerson} should return null as both
     * {@link PersonImpl} and {@link TenantImpl} provide implementations (namely
     * {@link PersonImpl#getName()} and {@link Tenant#getName()}).
     */
    @Test
    @Ignore("fix HotSpotResolvedObjectTypeImpl.findUniqueConcreteMethod")
    public void test() throws NoSuchMethodException {
        ResolvedJavaMethod ifaceMethod = getMetaAccess().lookupJavaMethod(Person.class.getDeclaredMethod("getName"));

        PersonImpl person = new PersonImpl("maya");
        TenantImpl tenant = new TenantImpl(0xdeadbeef);

        // Ensure the relevant methods are linked
        person.getName();
        tenant.getName();

        for (int i = 0; i < ITERATIONS; i++) {
            getLabelLength(person);
            getLabelLength(tenant);
        }

        // Until HotSpotResolvedObjectTypeImpl.findUniqueConcreteMethod is fixed,
        // this causes a VM crash as getLabelLength() directly invokes PersonImpl.getName().
        test("getLabelLength", tenant);

        ResolvedJavaMethod expected = null;
        AssumptionResult<ResolvedJavaMethod> actual = getMetaAccess().lookupJavaType(AbstractPerson.class).findUniqueConcreteMethod(ifaceMethod);
        Assert.assertEquals(expected, actual.getResult());

    }

    public int getLabelLength(AbstractPerson person) {
        return person.getName().length();
    }

    interface Person {
        String getName();

        default int getId() {
            return -1;
        }
    }

    interface Tenant extends Person {
        @Override
        default String getName() {
            return getAddress();
        }

        String getAddress();
    }

    abstract static class AbstractPerson implements Person {
    }

    static class PersonImpl extends AbstractPerson {
        public String name;

        PersonImpl(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    static class TenantImpl extends AbstractPerson implements Tenant {
        public int id;

        TenantImpl(int id) {
            this.id = id;
        }

        @Override
        public String getAddress() {
            return String.valueOf(id);
        }
    }
}
