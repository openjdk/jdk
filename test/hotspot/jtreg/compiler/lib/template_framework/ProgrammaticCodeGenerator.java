/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * The {@link ProgrammaticCodeGenerator} is to be used when a {@link Template} is not enough powerful, and
 * one instead needs to programmaticall generate code. The user provided lambda {@link Instantiator}
 * function gets called during the instantiation, and has direct access to the {@link Scope} and
 * {@link Parameters}, and has to directly generate code into the {@link CodeStream} of the {@link Scope}.
 */
public final class ProgrammaticCodeGenerator extends CodeGenerator {

    /**
     * Interface definition for instantiator lambda functions.
     */
    public interface Instantiator {
        /**
         * The provided lambda function is called during instantiation.
         *
         * @param scope Scope into which the code is generated.
         * @param parameters Provides the parameters for the instantiation, as well as a unique ID for identifier
         *                   name generation (e.g. variable of method names).
         */
        public void call(Scope scope, Parameters parameters);
    }

    private final Instantiator instantiator;

    /**
     * Create a new {@link ProgrammaticCodeGenerator}.
     *
     * @param generatorName Name of the generator, can be used for lookup in the
     *                      {@link CodeGeneratorLibrary} if the {@link Template}
     *                      is added to a library.
     * @param instantiator Lambda function provided for instantiation.
     * @param fuelCost The {@link fuelCost} for the {@link CodeGenerator}.
     */
    public ProgrammaticCodeGenerator(String generatorName, Instantiator instantiator, int fuelCost) {
        super(generatorName, fuelCost);
        this.instantiator = instantiator;
    }

    /**
     * Instantiate the {@link ProgrammaticCodeGenerator}.
     *
     * @param scope Scope into which the code is generated.
     * @param parameters Provides the parameters for the instantiation, as well as a unique ID for identifier
     *                   name generation (e.g. variable of method names).
     */
    @Override
    public void instantiate(Scope scope, Parameters parameters) {
        scope.setDebugContext(name, parameters);
        instantiator.call(scope, parameters);
    };
}
