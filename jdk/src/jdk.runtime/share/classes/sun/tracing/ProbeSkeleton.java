/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.tracing;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import com.sun.tracing.Probe;

/**
 * Provides common code for implementation of {@code Probe} classes.
 *
 * @since 1.7
 */
public abstract class ProbeSkeleton implements Probe {

    protected Class<?>[] parameters;

    protected ProbeSkeleton(Class<?>[] parameters) {
        this.parameters = parameters;
    }

    public abstract boolean isEnabled();  // framework-dependent

    /**
     * Triggers the probe with verified arguments.
     *
     * The caller of this method must have already determined that the
     * arity and types of the arguments match what the probe was
     * declared with.
     */
    public abstract void uncheckedTrigger(Object[] args); // framework-dependent

    private static boolean isAssignable(Object o, Class<?> formal) {
        if (o != null) {
            if ( !formal.isInstance(o) ) {
                if ( formal.isPrimitive() ) { // o might be a boxed primitive
                    try {
                        // Yuck.  There must be a better way of doing this
                        Field f = o.getClass().getField("TYPE");
                        return formal.isAssignableFrom((Class<?>)f.get(null));
                    } catch (Exception e) {
                        /* fall-through. */
                    }
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Performs a type-check of the parameters before triggering the probe.
     */
    public void trigger(Object ... args) {
        if (args.length != parameters.length) {
            throw new IllegalArgumentException("Wrong number of arguments");
        } else {
            for (int i = 0; i < parameters.length; ++i) {
                if ( !isAssignable(args[i], parameters[i]) ) {
                    throw new IllegalArgumentException(
                            "Wrong type of argument at position " + i);
                }
            }
            uncheckedTrigger(args);
        }
    }
}
