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
import compiler.lib.ir_framework.SetupInfo;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Arrays;

/**
 * This interface provides arguments (and can set fields) for a test method. Different implementations are chosen
 * based on the @Arguments annotation for the @Test method.
 */
interface ArgumentsProvider {
    /**
     * Compute arguments (and possibly set fields) for a test method.
     *
     * @param invocationTarget object on which the test method is called, or null if the test method is static.
     * @param invocationCounter is incremented for every set of arguments to be provided for the test method.
     *                          It can be used to create deterministic inputs, that vary between different
     *                          invocations of the test method.
     * @return Returns the arguments to be passed into the test method.
     */
    Object[] getArguments(Object invocationTarget, int invocationCounter);

}

/**
 * For a test method, determine what ArgumentsProvider is to be constructed, given its @Arguments annotation,
 * and the available setup methods.
 */
class ArgumentsProviderBuilder {
   public static ArgumentsProvider build(Method method,
                                         HashMap<String, Method> setupMethodMap) {
        Arguments argumentsAnnotation = method.getAnnotation(Arguments.class);
        if (argumentsAnnotation == null) {
            return new DefaultArgumentsProvider();
        }

        Argument[] values = argumentsAnnotation.values();
        String setupMethodName = argumentsAnnotation.setup();

        if (!setupMethodName.isEmpty()) {
            TestFormat.check(values.length == 0,
                             "@Arguments: Can only specify \"setup\" or \"values\" but not both in " + method);
            TestFormat.check(setupMethodMap.containsKey(setupMethodName),
                             "@Arguments setup: did not find " + setupMethodName +
                             " for " + method);
            Method setupMethod = setupMethodMap.get(setupMethodName);
            return new SetupArgumentsProvider(setupMethod);
        } else {
            TestFormat.check(values.length > 0,
                             "@Arguments: Empty annotation not allowed. Either specify \"values\" or \"setup\" in " + method);
            ArgumentValue[] argumentValues = ArgumentValue.getArgumentValues(method, values);
            return new ValueArgumentsProvider(argumentValues);
        }
    }
}

/**
 * Default: when no @Arguments annotation is provided (including for custom run tests).
 */
final class DefaultArgumentsProvider implements ArgumentsProvider {
    @Override
    public Object[] getArguments(Object invocationTarget, int invocationCounter) {
        return new Object[]{};
    }
}

/**
 * Used for @Arguments(values = {...}) to specify individual arguments directly.
 */
final class ValueArgumentsProvider implements ArgumentsProvider {
    ArgumentValue[] argumentValues;

    ValueArgumentsProvider(ArgumentValue[] argumentValues) {
        this.argumentValues = argumentValues;
    }

    @Override
    public Object[] getArguments(Object invocationTarget, int invocationCounter) {
        return Arrays.stream(argumentValues).map(v -> v.getValue()).toArray();
    }
}

/**
 * Used for @Arguments(setup = "setupMethodName") to specify a setup method to provide arguments
 * and possibly set fields.
 */
final class SetupArgumentsProvider implements ArgumentsProvider {
    Method setupMethod;

    SetupArgumentsProvider(Method setupMethod) {
        this.setupMethod = setupMethod;
    }

    @Override
    public Object[] getArguments(Object invocationTarget, int invocationCounter) {
        Object target = Modifier.isStatic(setupMethod.getModifiers()) ? null
                                                                      : invocationTarget;
        try {
            if (setupMethod.getParameterCount() == 1) {
                return (Object[]) setupMethod.invoke(target, new SetupInfo(invocationCounter));
            } else {
                return (Object[]) setupMethod.invoke(target);
            }
        } catch (Exception e) {
            throw new TestRunException("There was an error while invoking setup method " +
                                       setupMethod + " on " + target, e);
        }
    }
}

