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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import jdk.test.lib.Utils;

/**
 * The {@link Scope} defines the the relative location within a recursive {@link CodeGenerator}
 * instantiation, and provides access to the available variables, and the output {@link CodeStream}
 * for the current scope of code generation.
 */
public sealed class Scope permits BaseScope, DispatchScope {
    private static final Random RANDOM = Utils.getRandomInstance();

    /**
     * Parent {@link Scope}, which transitively gives access to all outer scopes.
     */
    public final Scope parent;

    /**
     * Remaining fuel in the current scope for recursive {@link CodeGenerator} instantiations,
     * used as a guide to limit the recursion depth.
     */
    public final long fuel;

    /**
     * Output {@link CodeStream} into which all code of this scope and its nested scopes
     * are generated into.
     */
    public final CodeStream stream;

    /**
     * Helper class to keep track of the local variables defined inside the scope.
     */
    private final class VariableSet {
        public final VariableSet parent;
        public final HashMap<String,ArrayList<String>> variables;

        public VariableSet(VariableSet parent) {
            this.parent = parent;
            this.variables = new HashMap<String,ArrayList<String>>();
        }

        public int countLocal(String type) {
            ArrayList<String> locals = variables.get(type);
            return (locals == null) ? 0 : locals.size();
        }

        public int count(String type) {
            int c = countLocal(type);
            if (parent != null) {
                return c + parent.count(type);
            }
            return c;
        }

        /**
         * Randomly sample a variable from this scope or a parent scope, restricted to the specified type.
         */
        public String sample(String type) {
            int c = count(type);
            if (c == 0) {
                // No variable of this type
                return null;
            }

            // Maybe sample from parent.
            if (parent != null) {
                int pc = parent.count(type);
                int r = RANDOM.nextInt(c);
                if (r < pc) {
                    return parent.sample(type);
                }
            }

            ArrayList<String> locals = variables.get(type);
            int r = RANDOM.nextInt(locals.size());
            return locals.get(r);
        }

        /**
         * Add a variable of a specified type to the scope.
         */
        public void add(String name, String type) {
            // Fetch list of variables - if non-existant create a new one.
            ArrayList<String> variablesWithType = variables.get(type);
            if (variablesWithType == null) {
                variablesWithType = new ArrayList<String>();
                variables.put(type, variablesWithType);
            }
            variablesWithType.add(name);
        }

        public void printLocals() {
            for (Map.Entry<String,ArrayList<String>> e : variables.entrySet()) {
                String type = e.getKey();
                ArrayList<String> locals = e.getValue();
                System.out.println("    type: " + type + " - " + count(type));
                for (String v : locals) {
                  System.out.println("      " + v);
                }
            }
            if (variables.isEmpty()) {
                System.out.println("    empty");
            }
        }

        public void print(int i) {
            System.out.println("print " + i);
            printLocals();
            if (parent != null) {
                parent.print(i+1);
            }
        }
    }

    /**
     * We have two sets of variables: mutable and immutable (read-only) variables.
     */
    private final VariableSet allVariables;
    private final VariableSet mutableVariables;

    /**
     * Helper record, providing debugging information for the scope, which can be printed when
     * an error is encountered in the Template Framework, providing a "scope-trace" with helpful
     * information for users of the framework.
     */
    record DebugContext(String description, Parameters parameters) {
        public void print() {
            System.out.println("  " + description);
            if (parameters == null) {
                System.out.println("  No parameters.");
            } else {
                parameters.print();
            }
        }
    }

    DebugContext debugContext;

    /**
     * Create a new {@link Scope}.
     *
     * @param parent Parent scope or null if the new scope is an outermost scope.
     * @param fuel Remaining fuel for recursive {@link CodeGenerator} instantiations.
     */
    public Scope(Scope parent, long fuel) {
        this.parent = parent;
        this.fuel = fuel;
        this.stream = new CodeStream();

        this.allVariables     = new VariableSet(this.parent != null ? this.parent.allVariables : null);
        this.mutableVariables = new VariableSet(this.parent != null ? this.parent.mutableVariables : null);
    }

    /**
     * Add debugging information to the scope, when using it in an instantiation.
     *
     * @param description Description, which contains information about how this scope is used.
     * @param parameters The {@link Parameters} used in the instantiantion, or null if not relevant.
     */
    public void setDebugContext(String description, Parameters parameters) {
        DebugContext newDebugContext = new DebugContext(description, parameters);
        if (this.debugContext != null) {
            System.out.println("Setting debug context a second time. New context");
            newDebugContext.print();
            System.out.println("Old trace:");
            print();
            throw new TemplateFrameworkException("Duplicate setting debug context not allowed.");
        }
        this.debugContext = newDebugContext;
    }

    /**
     * Access the {@link CodeGeneratorLibrary} associated with the {@link BaseScope}.
     *
     * @return The library associated with the {@link BaseScope}.
     */
    public CodeGeneratorLibrary library() {
        return this.parent.library();
    }

    /**
     * Close the scope, together with its {@link CodeStream}, after which no more code can be generated
     * into the scope.
     */
    public void close() {
        if (this.debugContext == null) {
            print();
            throw new TemplateFrameworkException("No debug context set until end of scope.");
        }
        stream.close();
    }

    /**
     * Add a variable to the scope.
     *
     * @param name Name of the variable.
     * @param type Type of the variable.
     * @param mutable Indicates if the variable is to be mutated or used for read-only purposes.
     */
    public void addVariable(String name, String type, boolean mutable) {
        allVariables.add(name, type);
        if (mutable) {
            mutableVariables.add(name, type);
        }
    }

    /**
     * Sample a random variable from the set of variables defined in the scope or outer scopes.
     * @param type Type of the variable.
     * @param mutable Indicates if the variable is to be mutated or used for read-only purposes.
     * @return Name of the sampled variable.
     */
    public String sampleVariable(String type, boolean mutable) {
        return mutable ? mutableVariables.sample(type) : allVariables.sample(type);
    }

    /**
     * Next outer {@link Scope} (not this) that is a {@link ClassScope}.
     *
     * @param errorMessage Error message added to the exception if no such {@link ClassScope} is found.
     * @return The outer {@link ClassScope}.
     * @throws TemplateFrameworkException If no such outer {@link ClassScope} is found.
     */
    public final ClassScope classScope(String errorMessage) {
        Scope current = this;
        while (current.parent != null) {
          if (current.parent instanceof ClassScope s) {
            return s;
          }
          current = current.parent;
        }
        print();
        throw new TemplateFrameworkException("Could not find ClassScope / '#open(class)' " + errorMessage);
    }

    /**
     * Next outer {@link Scope} (not this) that is a {@link MethodScope}.
     *
     * @param errorMessage Error message added to the exception if no such {@link MethodScope} is found.
     * @return The outer {@link MethodScope}.
     * @throws TemplateFrameworkException If no such outer {@link MethodScope} is found.
     */
    public final MethodScope methodScope(String errorMessage) {
        Scope current = this;
        while (current.parent != null) {
          if (current.parent instanceof MethodScope s) {
            return s;
          }
          current = current.parent;
        }
        print();
        throw new TemplateFrameworkException("Could not find MethodScope / '#open(method)' " + errorMessage);
    }

    /**
     * Compute the relative indentation to an outer (recursive parent) scope.
     *
     * @param outer Some (recursive parent) outer scope.
     * @return Indentation relative to some outer scope.
     */
    public final int indentationFrom(Scope outer) {
        int difference = 0;
        Scope current = this;
        while (current != null && current != outer) {
            current = current.parent;
            difference += current.stream.getIndentation();
        }
        if (current == null) {
            System.out.println("This scope:");
            print();
            System.out.println("Outer scope:");
            outer.print();
            throw new TemplateFrameworkException("Outer scope not found.");
        }
        return difference;
    }

    /**
     * Printing the "scope-trace" for debbuging.
     */
    public final void print() {
        System.out.println(this.getClass().getSimpleName() + ":");
        if (debugContext != null) {
            debugContext.print();
        } else {
            System.out.println("  No debug context set yet.");
        }
        System.out.println("  mutable variables:");
        mutableVariables.printLocals();
        System.out.println("  all variables:");
        allVariables.printLocals();
        if (parent != null) {
            parent.print();
        }
    }
}
