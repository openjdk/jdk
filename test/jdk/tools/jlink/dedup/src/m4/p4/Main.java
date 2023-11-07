/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package p4;

import p3.ServiceInterface;

import java.lang.module.ModuleFinder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;

public class Main {

    public static void main(String[] args) throws Exception {
        List<ServiceInterface> services = getServices();
        for (var service : services) {
            System.out.println("Service name  " + service.getServiceName());
            System.out.println("Service string " + service.getString());
        }
        var moduleClass = Class.forName("jdk.internal.module.SystemModules$all");
        long subMethodCount = Arrays.stream(moduleClass.getDeclaredMethods())
                                    .filter(method -> method.getName().startsWith("sub"))
                                    .count();

        // one subX method per each module is generated as the image is linked with
        // --system-modules=batchSize=1
        var moduleCount = (long) ModuleFinder.ofSystem().findAll().size();
        if (subMethodCount != moduleCount) {
            throw new AssertionError("Difference in generated sub module methods count! Expected: " +
                    moduleCount + " but was " + subMethodCount);
        }
    }

    private static List<ServiceInterface> getServices() {
        List<ServiceInterface> services = new ArrayList<>();
        ServiceLoader.load(ServiceInterface.class).forEach(services::add);
        return services;
    }
}
