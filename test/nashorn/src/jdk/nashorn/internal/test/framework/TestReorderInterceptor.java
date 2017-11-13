/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.test.framework;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.testng.IMethodInstance;
import org.testng.IMethodInterceptor;
import org.testng.ITest;
import org.testng.ITestContext;

/**
 * TestNG method interceptor to make sure that the tests are run in the test name order.
 */
public final class TestReorderInterceptor implements IMethodInterceptor {
    @Override
    public List<IMethodInstance> intercept(final List<IMethodInstance> methods, final ITestContext context) {
        Collections.sort(methods, new Comparator<IMethodInstance>() {
            @Override
            public int compare(final IMethodInstance mi1, final IMethodInstance mi2) {
                // get test instances to order the tests.
                final Object o1 = mi1.getInstance();
                final Object o2 = mi2.getInstance();
                if (o1 instanceof ITest && o2 instanceof ITest) {
                    return ((ITest)o1).getTestName().compareTo(((ITest)o2).getTestName());
                } else if (o1 instanceof ITest) {
                    return 1;
                } else if (o2 instanceof ITest) {
                    return -1;
                }
                // something else, don't care about the order
                return 0;
            }
        });

        return methods;
    }
}
