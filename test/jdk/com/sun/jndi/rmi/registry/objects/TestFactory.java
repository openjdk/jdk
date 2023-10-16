/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.test;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;
import java.util.Hashtable;

public class TestFactory implements ObjectFactory {

    /**
     * Get the number of {@code TestFactory.getObjectInstance} calls
     *
     * @return the number of calls
     */
    public static int getNumberOfGetInstanceCalls() {
        return timesGetInstanceCalled;
    }

    /**
     * @param obj         The possibly null object containing location or reference
     *                    information that can be used in creating an object.
     * @param name        The name of this object relative to {@code nameCtx},
     *                    or null if no name is specified.
     * @param nameCtx     The context relative to which the {@code name}
     *                    parameter is specified, or null if {@code name} is
     *                    relative to the default initial context.
     * @param environment The possibly null environment that is used in
     *                    creating the object.
     * @return If specified object is a {@code Reference} returns a {@code String} with a class
     * name specified in the reference, otherwise returns {@code "TestObj"}
     * @throws Exception
     */
    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) throws Exception {
        timesGetInstanceCalled++;
        String loadedObject;
        if (obj instanceof Reference) {
            Reference r = (Reference) obj;
            System.err.println("TestFactory: loading javax.naming.Reference:");
            System.err.println("\tFactory location=" + r.getFactoryClassLocation());
            System.err.println("\tFactory class name=" + r.getFactoryClassName());
            System.err.println("\tClass name=" + r.getClassName());
            loadedObject = r.getClassName();
        } else {
            System.err.println("TestFactory: loading " + obj.getClass().getName());
            loadedObject = "TestObj";
        }
        return loadedObject;
    }

    private static volatile int timesGetInstanceCalled;
}

