/*
 * Copyright (c) 1994, 2004, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.java;

/**
 * This class represents an Java class declaration. It refers
 * to either a binary or source definition.
 *
 * ClassDefinitions are loaded on demand, this means that
 * class declarations are late bound. The definition of the
 * class is obtained in stages. The status field describes
 * the state of the class definition:
 *
 * CS_UNDEFINED - the definition is not yet loaded
 * CS_UNDECIDED - a binary definition is loaded, but it is
 *                still unclear if the source definition need to
 *                be loaded
 * CS_BINARY    - the binary class is loaded
 * CS_PARSED    - the class is loaded from the source file, the
 *                type information is available, but the class has
 *                not yet been compiled.
 * CS_CHECKED   - the class is loaded from the source file and has
 *                been type-checked.
 * CS_COMPILED  - the class has been type checked, compiled,
 *                and written out.
 * CS_NOTFOUND  - no class definition could be found
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */

public final
class ClassDeclaration implements Constants {
    int status;
    Type type;
    ClassDefinition definition;

    /**
     * Constructor
     */
    public ClassDeclaration(Identifier name) {
        this.type = Type.tClass(name);
    }

    /**
     * Get the status of the class
     */
    public int getStatus() {
        return status;
    }

    /**
     * Get the name of the class
     */
    public Identifier getName() {
       return type.getClassName();
    }

    /**
     * Get the type of the class
     */
    public Type getType() {
        return type;
    }

    /**
     * Check if the class is defined
     */
    public boolean isDefined() {
        switch (status) {
          case CS_BINARY:
          case CS_PARSED:
          case CS_CHECKED:
          case CS_COMPILED:
            return true;
        }
        return false;
    }

    /**
     * Get the definition of this class. Returns null if
     * the class is not yet defined.
     */
    public ClassDefinition getClassDefinition() {
        return definition;
    }

    /**
     * This is a flag for use by getClassDefinition(env).  It is
     * used to mark that a class has been successfully looked up
     * by that method before.
     */
    private boolean found = false;

    /**
     * Get the definition of this class, if the class is not
     * yet defined, load the definition. Loading a class may
     * throw various exceptions.
     */
    public ClassDefinition getClassDefinition(Environment env)
    throws ClassNotFound {
        if (tracing) env.dtEvent("getClassDefinition: " +
                                 getName() + ", status " + getStatus());

        // The majority of calls to getClassDefinition() are duplicates.
        // This check makes them fast.  It also allows us to avoid
        // duplicate, useless calls to basicCheck().  In the future it
        // would be good to add an additional status value, CS_BASICCHECKED.
        if (found) {
            return definition;
        }

        for(;;) {
            switch (status) {
                case CS_UNDEFINED:
                case CS_UNDECIDED:
                case CS_SOURCE:
                    env.loadDefinition(this);
                    break;

                case CS_BINARY:
                case CS_PARSED:
                    //+FIX FOR BUGID 4056065
                    //definition.basicCheck(env);
                    if (!definition.isInsideLocal()) {
                        // Classes inside a block, including anonymous classes,
                        // are checked when their surrounding member is checked.
                        definition.basicCheck(env);
                    }
                    //-FIX FOR BUGID 4056065
                    found = true;
                    return definition;

                case CS_CHECKED:
                case CS_COMPILED:
                    found = true;
                    return definition;

                default:
                    throw new ClassNotFound(getName());
                }
        }
    }

    /**
     * Get the definition of this class, if the class is not
     * yet defined, load the definition. Loading a class may
     * throw various exceptions.  Perform no basicCheck() on this
     * class.
     */
    public ClassDefinition getClassDefinitionNoCheck(Environment env) throws ClassNotFound {
        if (tracing) env.dtEvent("getClassDefinition: " +
                                 getName() + ", status " + getStatus());
        for(;;) {
            switch (status) {
                case CS_UNDEFINED:
                case CS_UNDECIDED:
                case CS_SOURCE:
                    env.loadDefinition(this);
                    break;

                case CS_BINARY:
                case CS_PARSED:
                case CS_CHECKED:
                case CS_COMPILED:
                    return definition;

                default:
                    throw new ClassNotFound(getName());
                }
        }
    }

   /**
     * Set the class definition
     */
    public void setDefinition(ClassDefinition definition, int status) {

        // Sanity checks.

        // The name of the definition should match that of the declaration.
        if ((definition != null) && !getName().equals(definition.getName())) {
            throw new CompilerError("setDefinition: name mismatch: " +
                                    this + ", " + definition);
        }

        // The status states can be considered ordered in the same
        // manner as their numerical values. We expect classes to
        // progress through a sequence of monotonically increasing
        // states. NOTE: There are currently exceptions to this rule
        // which are believed to be legitimate.  In particular, a
        // class may be checked more than once, though we believe that
        // this is unnecessary and may be avoided.
        /*-----------------*
        if (status <= this.status) {
            System.out.println("STATUS REGRESSION: " +
                               this + " FROM " + this.status + " TO " + status);
        }
        *------------------*/

        this.definition = definition;
        this.status = status;
    }

    /**
     * Equality
     */
    public boolean equals(Object obj) {
        if ((obj != null) && (obj instanceof ClassDeclaration)) {
            return type.equals(((ClassDeclaration)obj).type);
        }
        return false;
    }

    /**
     * toString
     */
    public String toString() {
        String name = getName().toString();
        String type = "type ";
        String nested = getName().isInner() ? "nested " : "";
        if (getClassDefinition() != null) {
            if (getClassDefinition().isInterface()) {
                type = "interface ";
            } else {
                type = "class ";
            }
            if (!getClassDefinition().isTopLevel()) {
                nested = "inner ";
                if (getClassDefinition().isLocal()) {
                    nested = "local ";
                    if (!getClassDefinition().isAnonymous()) {
                        name = getClassDefinition().getLocalName() +
                            " (" + name + ")";
                    }
                }
            }
        }
        return nested + type + name;
    }
}
