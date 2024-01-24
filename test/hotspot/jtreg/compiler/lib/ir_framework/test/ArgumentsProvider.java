/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.test;

import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestRunException;
import compiler.lib.ir_framework.Argument;
import compiler.lib.ir_framework.Arguments;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;

// TODO spec
abstract class ArgumentsProvider {

    // TODO spec
    public static ArgumentsProvider getArgumentsProvider(Method method,
                                                         HashMap<String, Method> setupMethodMap) {
        Arguments argumentsAnnotation = method.getAnnotation(Arguments.class);
        if (argumentsAnnotation == null) {
            return new DefaultArgumentsProvider();
        }

        Argument[] values = argumentsAnnotation.values();
        String[] setup = argumentsAnnotation.setup();

        if (setup.length != 0) {
            TestFormat.check(setup.length == 1,
                             "@Arguments: setup should specify exactly one setup method " +
                             " but got " + setup.length + " in " + method);
            TestFormat.check(values.length == 0,
                             "@Arguments: specify only one of setup and values in " + method);
            String setupMethodName = setup[0];
            TestFormat.check(setupMethodMap.containsKey(setupMethodName),
                             "@Arguments setup: did not find " + setupMethodName +
                             " for " + method);
            Method setupMethod = setupMethodMap.get(setupMethodName);
            return new SetupArgumentsProvider(setupMethod);
	}
        // TODO other cases
        
 
        return new DefaultArgumentsProvider();
    }

    // TODO spec
    public boolean isDefault() {
        return this instanceof DefaultArgumentsProvider;
    }

    /**
     *
     * invocationTarget: of the test method, or null if static.
     *
     */
    // TODO spec
    public abstract Object[] getArguments(Object invocationTarget, int index);
}

final class DefaultArgumentsProvider extends ArgumentsProvider {
    @Override
    public Object[] getArguments(Object invocationTarget, int index) {
        return new Object[]{};
    }
}

final class ValueArgumentsProvider extends ArgumentsProvider {
    @Override
    public Object[] getArguments(Object invocationTarget, int index) {
        return new Object[]{};
    }
}

final class SetupArgumentsProvider extends ArgumentsProvider {
    Method setupMethod;

    SetupArgumentsProvider(Method setupMethod) {
        this.setupMethod = setupMethod;
    }
    
    @Override
    public Object[] getArguments(Object invocationTarget, int index) {
        Object target = Modifier.isStatic(setupMethod.getModifiers()) ? null
                                                                      : invocationTarget;
        try {
            return (Object[]) setupMethod.invoke(target, index);
        } catch (Exception e) {
            throw new TestRunException("There was an error while invoking Setup method " +
                                       setupMethod + " on " + target + ", " + e);
        }
    }
}

