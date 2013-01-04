/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.tools.nasgen;

import static jdk.nashorn.internal.tools.nasgen.StringConstants.OBJECT_ARRAY_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.OBJECT_DESC;

import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.nashorn.internal.objects.annotations.Where;

/**
 * Details about a Java method or field annotated with any of the field/method
 * annotations from the jdk.nashorn.internal.objects.annotations package.
 */
public final class MemberInfo implements Cloneable {
    /**
     * The different kinds of available class annotations
     */
    public static enum Kind {
        /** This is a script class */
        SCRIPT_CLASS,
        /** This is a constructor */
        CONSTRUCTOR,
        /** This is a function */
        FUNCTION,
        /** This is a getter */
        GETTER,
        /** This is a setter */
        SETTER,
        /** This is a property */
        PROPERTY,
        /** This is a specialized version of a function */
        SPECIALIZED_FUNCTION,
        /** This is a specialized version of a constructor */
        SPECIALIZED_CONSTRUCTOR
    }

    // keep in sync with jdk.nashorn.internal.objects.annotations.Attribute
    static final int DEFAULT_ATTRIBUTES = 0x0;

    static final int DEFAULT_ARITY = -2;

    // the kind of the script annotation - one of the above constants
    private MemberInfo.Kind kind;
    // script property name
    private String name;
    // script property attributes
    private int attributes;
    // name of the java member
    private String javaName;
    // type descriptor of the java member
    private String javaDesc;
    // access bits of the Java field or method
    private int javaAccess;
    // initial value for static @Property fields
    private Object value;
    // class whose object is created to fill property value
    private String initClass;
    // arity of the Function or Constructor
    private int arity;

    private Where where;

    /**
     * @return the kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * @param kind the kind to set
     */
    public void setKind(final Kind kind) {
        this.kind = kind;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * @return the attributes
     */
    public int getAttributes() {
        return attributes;
    }

    /**
     * @param attributes the attributes to set
     */
    public void setAttributes(final int attributes) {
        this.attributes = attributes;
    }

    /**
     * @return the javaName
     */
    public String getJavaName() {
        return javaName;
    }

    /**
     * @param javaName the javaName to set
     */
    public void setJavaName(final String javaName) {
        this.javaName = javaName;
    }

    /**
     * @return the javaDesc
     */
    public String getJavaDesc() {
        return javaDesc;
    }

    void setJavaDesc(final String javaDesc) {
        this.javaDesc = javaDesc;
    }

    int getJavaAccess() {
        return javaAccess;
    }

    void setJavaAccess(final int access) {
        this.javaAccess = access;
    }

    Object getValue() {
        return value;
    }

    void setValue(final Object value) {
        this.value = value;
    }

    Where getWhere() {
        return where;
    }

    void setWhere(final Where where) {
        this.where = where;
    }

    boolean isFinal() {
        return (javaAccess & Opcodes.ACC_FINAL) != 0;
    }

    boolean isStatic() {
        return (javaAccess & Opcodes.ACC_STATIC) != 0;
    }

    boolean isStaticFinal() {
        return isStatic() && isFinal();
    }

    boolean isInstanceGetter() {
        return kind == Kind.GETTER && where == Where.INSTANCE;
    }

    /**
     * Check whether this MemberInfo is a getter that resides in the instance
     * @return true if instance setter
     */
    boolean isInstanceSetter() {
        return kind == Kind.SETTER && where == Where.INSTANCE;
    }

    boolean isInstanceProperty() {
        return kind == Kind.PROPERTY && where == Where.INSTANCE;
    }

    boolean isInstanceFunction() {
        return kind == Kind.FUNCTION && where == Where.INSTANCE;
    }

    boolean isPrototypeGetter() {
        return kind == Kind.GETTER && where == Where.PROTOTYPE;
    }

    boolean isPrototypeSetter() {
        return kind == Kind.SETTER && where == Where.PROTOTYPE;
    }

    boolean isPrototypeProperty() {
        return kind == Kind.PROPERTY && where == Where.PROTOTYPE;
    }

    boolean isPrototypeFunction() {
        return kind == Kind.FUNCTION && where == Where.PROTOTYPE;
    }

    boolean isConstructorGetter() {
        return kind == Kind.GETTER && where == Where.CONSTRUCTOR;
    }

    boolean isConstructorSetter() {
        return kind == Kind.SETTER && where == Where.CONSTRUCTOR;
    }

    boolean isConstructorProperty() {
        return kind == Kind.PROPERTY && where == Where.CONSTRUCTOR;
    }

    boolean isConstructorFunction() {
        return kind == Kind.FUNCTION && where == Where.CONSTRUCTOR;
    }

    boolean isConstructor() {
        return kind == Kind.CONSTRUCTOR;
    }

    void verify() {
        if (kind == Kind.CONSTRUCTOR) {
            final Type returnType = Type.getReturnType(javaDesc);
            if (! returnType.toString().equals(OBJECT_DESC)) {
                error("return value should be of Object type, found" + returnType);
            }
            final Type[] argTypes = Type.getArgumentTypes(javaDesc);
            if (argTypes.length < 2) {
                error("constructor methods should have at least 2 args");
            }
            if (! argTypes[0].equals(Type.BOOLEAN_TYPE)) {
                error("first argument should be of boolean type, found" + argTypes[0]);
            }
            if (! argTypes[1].toString().equals(OBJECT_DESC)) {
                error("second argument should be of Object type, found" + argTypes[0]);
            }

            if (argTypes.length > 2) {
                for (int i = 2; i < argTypes.length - 1; i++) {
                    if (! argTypes[i].toString().equals(OBJECT_DESC)) {
                        error(i + "'th argument should be of Object type, found " + argTypes[i]);
                    }
                }

                final String lastArgType = argTypes[argTypes.length - 1].toString();
                final boolean isVarArg = lastArgType.equals(OBJECT_ARRAY_DESC);
                if (!lastArgType.equals(OBJECT_DESC) && !isVarArg) {
                    error("last argument is neither Object nor Object[] type: " + lastArgType);
                }

                if (isVarArg && argTypes.length > 3) {
                    error("vararg constructor has more than 3 arguments");
                }
            }
        } else if (kind == Kind.FUNCTION) {
            final Type returnType = Type.getReturnType(javaDesc);
            if (! returnType.toString().equals(OBJECT_DESC)) {
                error("return value should be of Object type, found" + returnType);
            }
            final Type[] argTypes = Type.getArgumentTypes(javaDesc);
            if (argTypes.length < 1) {
                error("function methods should have at least 1 arg");
            }
            if (! argTypes[0].toString().equals(OBJECT_DESC)) {
                error("first argument should be of Object type, found" + argTypes[0]);
            }

            if (argTypes.length > 1) {
                for (int i = 1; i < argTypes.length - 1; i++) {
                    if (! argTypes[i].toString().equals(OBJECT_DESC)) {
                        error(i + "'th argument should be of Object type, found " + argTypes[i]);
                    }
                }

                final String lastArgType = argTypes[argTypes.length - 1].toString();
                final boolean isVarArg = lastArgType.equals(OBJECT_ARRAY_DESC);
                if (!lastArgType.equals(OBJECT_DESC) && !isVarArg) {
                    error("last argument is neither Object nor Object[] type: " + lastArgType);
                }

                if (isVarArg && argTypes.length > 2) {
                    error("vararg function has more than 2 arguments");
                }
            }
        } else if (kind == Kind.GETTER) {
            final Type[] argTypes = Type.getArgumentTypes(javaDesc);
            if (argTypes.length != 1) {
                error("getter methods should have one argument");
            }
            if (! argTypes[0].toString().equals(OBJECT_DESC)) {
                error("first argument of getter should be of Object type, found: " + argTypes[0]);
            }
            if (Type.getReturnType(javaDesc).equals(Type.VOID_TYPE)) {
                error("return type of getter should not be void");
            }
        } else if (kind == Kind.SETTER) {
            final Type[] argTypes = Type.getArgumentTypes(javaDesc);
            if (argTypes.length != 2) {
                error("setter methods should have two arguments");
            }
            if (! argTypes[0].toString().equals(OBJECT_DESC)) {
                error("first argument of setter should be of Object type, found: " + argTypes[0]);
            }
            if (!Type.getReturnType(javaDesc).toString().equals("V")) {
                error("return type of setter should be void, found: " + Type.getReturnType(javaDesc));
            }
        }
    }

    private void error(final String msg) {
        throw new RuntimeException(javaName + javaDesc + " : " + msg);
    }

    /**
     * @return the initClass
     */
    String getInitClass() {
        return initClass;
    }

    /**
     * @param initClass the initClass to set
     */
    void setInitClass(final String initClass) {
        this.initClass = initClass;
    }

    @Override
    protected Object clone() {
        try {
            return super.clone();
        } catch (final CloneNotSupportedException e) {
            assert false : "clone not supported " + e;
            return null;
        }
    }

    /**
     * @return the arity
     */
    int getArity() {
        return arity;
    }

    /**
     * @param arity the arity to set
     */
    void setArity(final int arity) {
        this.arity = arity;
    }
}
