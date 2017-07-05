/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.xml.internal.ws.client.sei;

import com.sun.xml.internal.ws.model.ParameterImpl;

import javax.jws.WebParam.Mode;
import javax.xml.ws.Holder;

/**
 * Gets a value from an object that represents a parameter passed
 * as a method argument.
 *
 * <p>
 * This abstraction hides the handling of {@link Holder}.
 *
 * <p>
 * {@link ValueGetter} is a stateless behavior encapsulation.
 *
 * @author Kohsuke Kawaguchi
 */
enum ValueGetter {
    /**
     * {@link ValueGetter} that works for {@link Mode#IN}  parameter.
     *
     * <p>
     * Since it's the IN mode, the parameter is not a {@link Holder},
     * therefore the parameter itself is a value.
     */
    PLAIN() {
        Object get(Object parameter) {
            return parameter;
        }
    },
    /**
     * Creates {@link ValueGetter} that works for {@link Holder},
     * which is  {@link Mode#INOUT} or  {@link Mode#OUT}.
     *
     * <p>
     * In those {@link Mode}s, the parameter is a {@link Holder},
     * so the value to be sent is obtained by getting the value of the holder.
     */
    HOLDER() {
        Object get(Object parameter) {
            if(parameter==null)
                // the user is allowed to pass in null where a Holder is expected.
                return null;
            return ((Holder)parameter).value;
        }
    };

    /**
     * Gets the value to be sent, from a parameter given as a method argument.
     */
    abstract Object get(Object parameter);

    /**
     * Returns a {@link ValueGetter} suitable for the given {@link Parameter}.
     */
    static ValueGetter get(ParameterImpl p) {
        // return value is always PLAIN
        if(p.getMode()== Mode.IN || p.getIndex() == -1)
            return PLAIN;
        else
            return HOLDER;
    }
}
