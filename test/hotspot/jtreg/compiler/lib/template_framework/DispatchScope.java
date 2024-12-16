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

package compiler.lib.template_framework;

import java.util.HashMap;

/**
 * A {@link DispatchScope} allows the use of {@link dispatch} for dispatching {@link CodeGenerator}s
 * to the top of a {@link ClassScope} or {@link MethodScope}.
 */
public abstract sealed class DispatchScope extends Scope permits ClassScope, MethodScope{

    /**
     * The {@link CodeStream} used for dispatched code at the top of the scope.
     */
    public final CodeStream dispatchStream;

    /**
     * Create a new {@link DispatchScope}.
     *
     * @param parent Parent scope or null if the new scope is an outermost scope.
     * @param fuel Remaining fuel for recursive {@link CodeGenerator} instantiations.
     */
    public DispatchScope(Scope parent, long fuel) {
        super(parent, fuel);
        this.dispatchStream = new CodeStream();
    }

    /**
     * Dispatch a {@link CodeGenerator} to generate code at the top of the scope.
     *
     * @param generator The generator that is dispatched to the top of the scope.
     * @param parameterMap The parameter key-value pairs for the generator instantiation.
     */
    public void dispatch(CodeGenerator generator, HashMap<String,String> parameterMap) {
        Scope dispatchScope = new Scope(this, this.fuel - generator.fuelCost);
        Parameters parameters = new Parameters(parameterMap);
        generator.instantiate(dispatchScope, parameters);
        dispatchScope.stream.addNewline();
        dispatchScope.close();
        dispatchStream.addCodeStream(dispatchScope.stream);
    }

    /**
     * Close the {@link DispatchScope}.
     * <p>
     * Close the {@link dispatchStream} for all the dispatched code at the top of the scope, and prepend
     * the code to the scope's {@link CodeStream}, then close that stream after which no more code can be
     * generated into the scope.
     */
    @Override
    public void close() {
        dispatchStream.close();
        stream.prependCodeStream(dispatchStream);
        super.close();
    }
}
