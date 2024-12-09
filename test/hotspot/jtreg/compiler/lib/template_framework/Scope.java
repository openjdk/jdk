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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import jdk.test.lib.Utils;

/**
 * TODO public?
 */
public class Scope {
    private static final Random RANDOM = Utils.getRandomInstance();

    public final Scope parent;
    public final long fuel;
    public final CodeStream stream;

    private class VariableSet {
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

        public String sample(String type) {
            int c = count(type);
            System.out.println("Sample count: " + c);
            if (c == 0) {
                // No variable of this type
                return null;
            }

            // Maybe sample from parent.
            if (parent != null) {
                int pc = parent.count(type);
                int r = RANDOM.nextInt(c);
                if (r < pc) {
                    System.out.println("Sample parent: " + pc + " " + r);
                    return parent.sample(type);
                }
            }

            System.out.println("Sample local");
            ArrayList<String> locals = variables.get(type);
            int r = RANDOM.nextInt(locals.size());
            return locals.get(r);
        }

        public void add(String name, String type) {
            // Fetch list of variables - if non-existant create a new one.
            ArrayList<String> variablesWithType = variables.get(type);
            if (variablesWithType == null) {
                variablesWithType = new ArrayList<String>();
                variables.put(type, variablesWithType);
            }
            variablesWithType.add(name);
            System.out.println("Count after add: " + count(type));
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

    public final VariableSet allVariables;
    public final VariableSet mutableVariables;

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

    public Scope(Scope parent, long fuel) {
        this.parent = parent;
        this.fuel = fuel;
        this.stream = new CodeStream();

        this.allVariables     = new VariableSet(this.parent != null ? this.parent.allVariables : null);
        this.mutableVariables = new VariableSet(this.parent != null ? this.parent.mutableVariables : null);
    }

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

    public CodeGeneratorLibrary library() {
        return this.parent.library();
    }

    public void close() {
        if (this.debugContext == null) {
            print();
            throw new TemplateFrameworkException("No debug context set until end of scope.");
        }
        stream.close();
    }

    public void addVariable(String name, String type, boolean mutable) {
        allVariables.add(name, type);
        if (mutable) {
            mutableVariables.add(name, type);
        }
    }

    public String sampleVariable(String type, boolean mutable) {
        System.out.println("sample " + type + " " + mutable);
        allVariables.print(0);
        mutableVariables.print(0);
        return mutable ? mutableVariables.sample(type) : allVariables.sample(type);
    }


    /**
     * Next outer Scope (not this) that is a ClassScope.
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
     * Next outer Scope (not this) that is a MethodScope.
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
     * Compute the relative indentation to an outer (recursive parent) Scope.
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

    public final void print() {
        printName();
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

    public void printName() {
        System.out.println("Scope:");
    }
}
