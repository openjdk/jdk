/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.invoke.MethodType;
import java.util.Map;
import jdk.nashorn.internal.codegen.CompileUnit;
import jdk.nashorn.internal.codegen.FunctionSignature;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.FunctionNode;

/**
 * Class that contains information allowing us to look up a method handle implementing a JavaScript function
 * from a generated class. This is used both for code coming from codegen and for persistent serialized code.
 */
public final class FunctionInitializer implements Serializable {

    private final String className;
    private final MethodType methodType;
    private final int flags;
    private transient Map<Integer, Type> invalidatedProgramPoints;
    private transient Class<?> code;

    private static final long serialVersionUID = -5420835725902966692L;

    /**
     * Constructor.
     *
     * @param functionNode the function node
     */
    public FunctionInitializer(final FunctionNode functionNode) {
        this(functionNode, null);
    }

    /**
     * Constructor.
     *
     * @param functionNode the function node
     * @param invalidatedProgramPoints invalidated program points
     */
    public FunctionInitializer(final FunctionNode functionNode, final Map<Integer, Type> invalidatedProgramPoints) {
        this.className  = functionNode.getCompileUnit().getUnitClassName();
        this.methodType = new FunctionSignature(functionNode).getMethodType();
        this.flags = functionNode.getFlags();
        this.invalidatedProgramPoints = invalidatedProgramPoints;

        final CompileUnit cu = functionNode.getCompileUnit();
        if (cu != null) {
            this.code = cu.getCode();
        }

        assert className != null;
    }

    /**
     * Returns the name of the class implementing the function.
     *
     * @return the class name
     */
    public String getClassName() {
        return className;
    }

    /**
     * Returns the type of the method implementing the function.
     *
     * @return the method type
     */
    public MethodType getMethodType() {
        return methodType;
    }

    /**
     * Returns the function flags.
     *
     * @return function flags
     */
    public int getFlags() {
        return flags;
    }

    /**
     * Returns the class implementing the function.
     *
     * @return the class
     */
    public Class<?> getCode() {
        return code;
    }

    /**
     * Set the class implementing the function
     * @param code the class
     */
    public void setCode(final Class<?> code) {
        // Make sure code has not been set and has expected class name
        if (this.code != null) {
            throw new IllegalStateException("code already set");
        }
        assert className.equals(code.getTypeName().replace('.', '/')) : "unexpected class name";
        this.code = code;
    }

    /**
     * Returns the map of invalidated program points.
     *
     * @return invalidated program points
     */
    public Map<Integer, Type> getInvalidatedProgramPoints() {
        return invalidatedProgramPoints;
    }

    private void writeObject(final ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        Type.writeTypeMap(invalidatedProgramPoints, out);
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        invalidatedProgramPoints = Type.readTypeMap(in);
    }
}
