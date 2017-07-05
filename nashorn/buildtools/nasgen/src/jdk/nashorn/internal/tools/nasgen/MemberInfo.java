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
import static jdk.nashorn.internal.tools.nasgen.StringConstants.OBJ_PKG;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.RUNTIME_PKG;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTS_PKG;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.SCRIPTOBJECT_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.STRING_DESC;
import static jdk.nashorn.internal.tools.nasgen.StringConstants.TYPE_SYMBOL;

import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;

/**
 * Details about a Java method or field annotated with any of the field/method
 * annotations from the jdk.nashorn.internal.objects.annotations package.
 */
public final class MemberInfo implements Cloneable {
    // class loader of this class
    private static final ClassLoader MY_LOADER = MemberInfo.class.getClassLoader();

    /**
     * The different kinds of available class annotations
     */
    public static enum Kind {

        /**
         * This is a script class
         */
        SCRIPT_CLASS,
        /**
         * This is a constructor
         */
        CONSTRUCTOR,
        /**
         * This is a function
         */
        FUNCTION,
        /**
         * This is a getter
         */
        GETTER,
        /**
         * This is a setter
         */
        SETTER,
        /**
         * This is a property
         */
        PROPERTY,
        /**
         * This is a specialized version of a function
         */
        SPECIALIZED_FUNCTION,
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

    private Type linkLogicClass;

    private boolean isSpecializedConstructor;

    private boolean isOptimistic;

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
     * Tag something as specialized constructor or not
     * @param isSpecializedConstructor boolean, true if specialized constructor
     */
    public void setIsSpecializedConstructor(final boolean isSpecializedConstructor) {
        this.isSpecializedConstructor = isSpecializedConstructor;
    }

    /**
     * Check if something is a specialized constructor
     * @return true if specialized constructor
     */
    public boolean isSpecializedConstructor() {
        return isSpecializedConstructor;
    }

    /**
     * Check if this is an optimistic builtin function
     * @return true if optimistic builtin
     */
    public boolean isOptimistic() {
        return isOptimistic;
    }

    /**
     * Tag something as optimistic builtin or not
     * @param isOptimistic boolean, true if builtin constructor
     */
    public void setIsOptimistic(final boolean isOptimistic) {
        this.isOptimistic = isOptimistic;
    }

    /**
     * Get the SpecializedFunction guard for specializations, i.e. optimistic
     * builtins
     * @return specialization, null if none
     */
    public Type getLinkLogicClass() {
        return linkLogicClass;
    }

    /**
     * Set the SpecializedFunction link logic class for specializations, i.e. optimistic
     * builtins
     * @param linkLogicClass link logic class
     */

    public void setLinkLogicClass(final Type linkLogicClass) {
        this.linkLogicClass = linkLogicClass;
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
     *
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
        switch (kind) {
            case CONSTRUCTOR: {
                final Type returnType = Type.getReturnType(javaDesc);
                if (!isJSObjectType(returnType)) {
                    error("return value of a @Constructor method should be of Object type, found " + returnType);
                }
                final Type[] argTypes = Type.getArgumentTypes(javaDesc);
                if (argTypes.length < 2) {
                    error("@Constructor methods should have at least 2 args");
                }
                if (!argTypes[0].equals(Type.BOOLEAN_TYPE)) {
                    error("first argument of a @Constructor method should be of boolean type, found " + argTypes[0]);
                }
                if (!isJavaLangObject(argTypes[1])) {
                    error("second argument of a @Constructor method should be of Object type, found " + argTypes[0]);
                }

                if (argTypes.length > 2) {
                    for (int i = 2; i < argTypes.length - 1; i++) {
                        if (!isJavaLangObject(argTypes[i])) {
                            error(i + "'th argument of a @Constructor method should be of Object type, found " + argTypes[i]);
                        }
                    }

                    final String lastArgTypeDesc = argTypes[argTypes.length - 1].getDescriptor();
                    final boolean isVarArg = lastArgTypeDesc.equals(OBJECT_ARRAY_DESC);
                    if (!lastArgTypeDesc.equals(OBJECT_DESC) && !isVarArg) {
                        error("last argument of a @Constructor method is neither Object nor Object[] type: " + lastArgTypeDesc);
                    }

                    if (isVarArg && argTypes.length > 3) {
                        error("vararg of a @Constructor method has more than 3 arguments");
                    }
                }
            }
            break;
            case FUNCTION: {
                final Type returnType = Type.getReturnType(javaDesc);
                if (!(isValidJSType(returnType) || Type.VOID_TYPE == returnType)) {
                    error("return value of a @Function method should be a valid JS type, found " + returnType);
                }
                final Type[] argTypes = Type.getArgumentTypes(javaDesc);
                if (argTypes.length < 1) {
                    error("@Function methods should have at least 1 arg");
                }
                if (!isJavaLangObject(argTypes[0])) {
                    error("first argument of a @Function method should be of Object type, found " + argTypes[0]);
                }

                if (argTypes.length > 1) {
                    for (int i = 1; i < argTypes.length - 1; i++) {
                        if (!isJavaLangObject(argTypes[i])) {
                            error(i + "'th argument of a @Function method should be of Object type, found " + argTypes[i]);
                        }
                    }

                    final String lastArgTypeDesc = argTypes[argTypes.length - 1].getDescriptor();
                    final boolean isVarArg = lastArgTypeDesc.equals(OBJECT_ARRAY_DESC);
                    if (!lastArgTypeDesc.equals(OBJECT_DESC) && !isVarArg) {
                        error("last argument of a @Function method is neither Object nor Object[] type: " + lastArgTypeDesc);
                    }

                    if (isVarArg && argTypes.length > 2) {
                        error("vararg @Function method has more than 2 arguments");
                    }
                }
            }
            break;
            case SPECIALIZED_FUNCTION: {
                final Type returnType = Type.getReturnType(javaDesc);
                if (!(isValidJSType(returnType) || (isSpecializedConstructor() && Type.VOID_TYPE == returnType))) {
                    error("return value of a @SpecializedFunction method should be a valid JS type, found " + returnType);
                }
                final Type[] argTypes = Type.getArgumentTypes(javaDesc);
                for (int i = 0; i < argTypes.length; i++) {
                    if (!isValidJSType(argTypes[i])) {
                        error(i + "'th argument of a @SpecializedFunction method is not valid JS type, found " + argTypes[i]);
                    }
                }
            }
            break;
            case GETTER: {
                final Type[] argTypes = Type.getArgumentTypes(javaDesc);
                if (argTypes.length != 1) {
                    error("@Getter methods should have one argument");
                }
                if (!isJavaLangObject(argTypes[0])) {
                    error("first argument of a @Getter method should be of Object type, found: " + argTypes[0]);
                }

                if (Type.getReturnType(javaDesc).equals(Type.VOID_TYPE)) {
                    error("return type of getter should not be void");
                }
            }
            break;
            case SETTER: {
                final Type[] argTypes = Type.getArgumentTypes(javaDesc);
                if (argTypes.length != 2) {
                    error("@Setter methods should have two arguments");
                }
                if (!isJavaLangObject(argTypes[0])) {
                    error("first argument of a @Setter method should be of Object type, found: " + argTypes[0]);
                }
                if (!Type.getReturnType(javaDesc).toString().equals("V")) {
                    error("return type of of a @Setter method should be void, found: " + Type.getReturnType(javaDesc));
                }
            }
            break;
            case PROPERTY: {
                if (where == Where.CONSTRUCTOR) {
                    if (isStatic()) {
                        if (!isFinal()) {
                            error("static Where.CONSTRUCTOR @Property should be final");
                        }

                        if (!isJSPrimitiveType(Type.getType(javaDesc))) {
                            error("static Where.CONSTRUCTOR @Property should be a JS primitive");
                        }
                    }
                } else if (where == Where.PROTOTYPE) {
                    if (isStatic()) {
                        if (!isFinal()) {
                            error("static Where.PROTOTYPE @Property should be final");
                        }

                        if (!isJSPrimitiveType(Type.getType(javaDesc))) {
                            error("static Where.PROTOTYPE @Property should be a JS primitive");
                        }
                    }
                }
            }
            break;

            default:
            break;
        }
    }

    /**
     * Returns if the given (internal) name of a class represents a ScriptObject subtype.
     */
    public static boolean isScriptObject(final String name) {
        // very crude check for ScriptObject subtype!
        if (name.startsWith(OBJ_PKG + "Native") ||
            name.equals(OBJ_PKG + "Global") ||
            name.equals(OBJ_PKG + "ArrayBufferView")) {
            return true;
        }

        if (name.startsWith(RUNTIME_PKG)) {
            final String simpleName = name.substring(name.lastIndexOf('/') + 1);
            switch (simpleName) {
                case "ScriptObject":
                case "ScriptFunction":
                case "NativeJavaPackage":
                case "Scope":
                    return true;
            }
        }

        if (name.startsWith(SCRIPTS_PKG)) {
            final String simpleName = name.substring(name.lastIndexOf('/') + 1);
            switch (simpleName) {
                case "JD":
                case "JO":
                    return true;
            }
        }

        return false;
    }

    private static boolean isValidJSType(final Type type) {
        return isJSPrimitiveType(type) || isJSObjectType(type);
    }

    private static boolean isJSPrimitiveType(final Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.INT:
            case Type.DOUBLE:
                return true;
            default:
                return type != TYPE_SYMBOL;
        }
    }

    private static boolean isJSObjectType(final Type type) {
        return isJavaLangObject(type) || isJavaLangString(type) || isScriptObject(type);
    }

    private static boolean isJavaLangObject(final Type type) {
        return type.getDescriptor().equals(OBJECT_DESC);
    }

    private static boolean isJavaLangString(final Type type) {
        return type.getDescriptor().equals(STRING_DESC);
    }

    private static boolean isScriptObject(final Type type) {
        if (type.getSort() != Type.OBJECT) {
            return false;
        }

        return isScriptObject(type.getInternalName());
    }

    private void error(final String msg) {
        throw new RuntimeException(javaName + " of type " + javaDesc + " : " + msg);
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

    String getDocumentationKey(final String objName) {
        if (kind == Kind.FUNCTION) {
            StringBuilder buf = new StringBuilder(objName);
            switch (where) {
                case CONSTRUCTOR:
                    break;
                case PROTOTYPE:
                    buf.append(".prototype");
                    break;
                case INSTANCE:
                    buf.append(".this");
                    break;
            }
            buf.append('.');
            buf.append(name);
            return buf.toString();
        }

        return null;
    }
}
