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

package com.sun.codemodel.internal;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A generated Java class/interface/enum/....
 *
 * <p>
 * This class models a declaration, and since a declaration can be always
 * used as a reference, it inherits {@link JClass}.
 *
 * <h2>Where to go from here?</h2>
 * <p>
 * You'd want to generate fields and methods on a class.
 * See {@link #method(int, JType, String)} and {@link #field(int, JType, String)}.
 */
public class JDefinedClass
    extends JClass
    implements JDeclaration, JClassContainer, JGenerifiable, JAnnotatable {

    /** Name of this class. Null if anonymous. */
    private String name = null;


    /** Modifiers for the class declaration */
    private JMods mods;

    /** Name of the super class of this class. */
    private JClass superClass;

    /** List of interfaces that this class implements */
    private final Set<JClass> interfaces = new TreeSet<JClass>();

    /** Fields keyed by their names. */
    /*package*/ final Map<String,JFieldVar> fields = new LinkedHashMap<String,JFieldVar>();

    /** Static initializer, if this class has one */
    private JBlock init = null;

    /** class javadoc */
    private JDocComment jdoc = null;

    /** Set of constructors for this class, if any */
    private final List<JMethod> constructors = new ArrayList<JMethod>();

    /** Set of methods that are members of this class */
    private final List<JMethod> methods = new ArrayList<JMethod>();

    /**
     * Nested classes as a map from name to JDefinedClass.
     * The name is all capitalized in a case sensitive file system
     * ({@link JCodeModel#isCaseSensitiveFileSystem}) to avoid conflicts.
     *
     * Lazily created to save footprint.
     *
     * @see #getClasses()
     */
    private Map<String,JDefinedClass> classes;


    /**
     * Flag that controls whether this class should be really generated or not.
     *
     * Sometimes it is useful to generate code that refers to class X,
     * without actually generating the code of X.
     * This flag is used to surpress X.java file in the output.
     */
    private boolean hideFile = false;

    /**
     * Client-app spcific metadata associated with this user-created class.
     */
    public Object metadata;

    /**
     * String that will be put directly inside the generated code.
     * Can be null.
     */
    private String directBlock;

    /**
     * If this is a package-member class, this is {@link JPackage}.
     * If this is a nested class, this is {@link JDefinedClass}.
     * If this is an anonymous class, this constructor shouldn't be used.
     */
    private JClassContainer outer = null;


    /** Default value is class or interface
     *  or annotationTypeDeclaration
     *  or enum
     *
     */
    private final ClassType classType;

    /** List containing the enum value declarations
     *
     */
//    private List enumValues = new ArrayList();

    /**
     * Set of enum constants that are keyed by names.
     * In Java, enum constant order is actually significant,
     * because of order ID they get. So let's preserve the order.
     */
    private final Map<String,JEnumConstant> enumConstantsByName = new LinkedHashMap<String,JEnumConstant>();

    /**
     * Annotations on this variable. Lazily created.
     */
    private List<JAnnotationUse> annotations = null;


    /**
     * Helper class to implement {@link JGenerifiable}.
     */
    private final JGenerifiableImpl generifiable = new JGenerifiableImpl() {
        protected JCodeModel owner() {
            return JDefinedClass.this.owner();
        }
    };

    JDefinedClass(JClassContainer parent, int mods, String name, ClassType classTypeval) {
        this(mods, name, parent, parent.owner(), classTypeval);
    }

    /**
     * Constructor for creating anonymous inner class.
     */
    JDefinedClass(
        JCodeModel owner,
        int mods,
        String name) {
        this(mods, name, null, owner);
    }

    private JDefinedClass(
            int mods,
            String name,
            JClassContainer parent,
            JCodeModel owner) {
        this (mods,name,parent,owner,ClassType.CLASS);
    }

    /**
     * JClass constructor
     *
     * @param mods
     *        Modifiers for this class declaration
     *
     * @param name
     *        Name of this class
     */
    private JDefinedClass(
        int mods,
        String name,
        JClassContainer parent,
        JCodeModel owner,
                ClassType classTypeVal) {
        super(owner);

        if(name!=null) {
            if (name.trim().length() == 0)
                throw new IllegalArgumentException("JClass name empty");

            if (!Character.isJavaIdentifierStart(name.charAt(0))) {
                String msg =
                    "JClass name "
                        + name
                        + " contains illegal character"
                        + " for beginning of identifier: "
                        + name.charAt(0);
                throw new IllegalArgumentException(msg);
            }
            for (int i = 1; i < name.length(); i++) {
                if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                    String msg =
                        "JClass name "
                            + name
                            + " contains illegal character "
                            + name.charAt(i);
                    throw new IllegalArgumentException(msg);
                }
            }
        }

        this.classType = classTypeVal;
        if (isInterface())
            this.mods = JMods.forInterface(mods);
        else
            this.mods = JMods.forClass(mods);

        this.name = name;

        this.outer = parent;
    }

    /**
     * Returns true if this is an anonymous class.
     */
    public final boolean isAnonymous() {
        return name == null;
    }

    /**
     * This class extends the specifed class.
     *
     * @param superClass
     *        Superclass for this class
     *
     * @return This class
     */
    public JDefinedClass _extends(JClass superClass) {
        if (this.classType==ClassType.INTERFACE)
            throw new IllegalArgumentException("unable to set the super class for an interface");
        if (superClass == null)
            throw new NullPointerException();

        for( JClass o=superClass.outer(); o!=null; o=o.outer() ){
            if(this==o){
                throw new IllegalArgumentException("Illegal class inheritance loop." +
                "  Outer class " + this.name + " may not subclass from inner class: " + o.name());
            }
        }

        this.superClass = superClass;
        return this;
    }

    public JDefinedClass _extends(Class superClass) {
        return _extends(owner().ref(superClass));
    }

    /**
     * Returns the class extended by this class.
     */
    public JClass _extends() {
        if(superClass==null)
            superClass = owner().ref(Object.class);
        return superClass;
    }

    /**
     * This class implements the specifed interface.
     *
     * @param iface
     *        Interface that this class implements
     *
     * @return This class
     */
    public JDefinedClass _implements(JClass iface) {
        interfaces.add(iface);
        return this;
    }

    public JDefinedClass _implements(Class iface) {
        return _implements(owner().ref(iface));
    }

    /**
     * Returns an iterator that walks the nested classes defined in this
     * class.
     */
    public Iterator<JClass> _implements() {
        return interfaces.iterator();
    }

    /**
     * JClass name accessor.
     *
     * <p>
     * For example, for <code>java.util.List</code>, this method
     * returns <code>"List"</code>"
     *
     * @return Name of this class
     */
    public String name() {
        return name;
    }

    /**
     * If the named enum already exists, the reference to it is returned.
     * Otherwise this method generates a new enum reference with the given
     * name and returns it.
     *
     * @param name
     *          The name of the constant.
     * @return
     *      The generated type-safe enum constant.
     */
    public JEnumConstant enumConstant(String name){
        JEnumConstant ec = enumConstantsByName.get(name);
        if (null == ec) {
            ec = new JEnumConstant(this, name);
            enumConstantsByName.put(name, ec);
        }
        return ec;
    }

    /**
     * Gets the fully qualified name of this class.
     */
    public String fullName() {
        if (outer instanceof JDefinedClass)
            return ((JDefinedClass) outer).fullName() + '.' + name();

        JPackage p = _package();
        if (p.isUnnamed())
            return name();
        else
            return p.name() + '.' + name();
    }

    public String binaryName() {
        if (outer instanceof JDefinedClass)
            return ((JDefinedClass) outer).binaryName() + '$' + name();
        else
            return fullName();
    }

    public boolean isInterface() {
        return this.classType==ClassType.INTERFACE;
    }

    public boolean isAbstract() {
        return mods.isAbstract();
    }

    /**
     * Adds a field to the list of field members of this JDefinedClass.
     *
     * @param mods
     *        Modifiers for this field
     *
     * @param type
     *        JType of this field
     *
     * @param name
     *        Name of this field
     *
     * @return Newly generated field
     */
    public JFieldVar field(int mods, JType type, String name) {
        return field(mods, type, name, null);
    }

    public JFieldVar field(int mods, Class type, String name) {
        return field(mods, owner()._ref(type), name);
    }

    /**
     * Adds a field to the list of field members of this JDefinedClass.
     *
     * @param mods
     *        Modifiers for this field.
     * @param type
     *        JType of this field.
     * @param name
     *        Name of this field.
     * @param init
     *        Initial value of this field.
     *
     * @return Newly generated field
     */
    public JFieldVar field(
        int mods,
        JType type,
        String name,
        JExpression init) {
        JFieldVar f = new JFieldVar(this,JMods.forField(mods), type, name, init);

        if(fields.put(name, f)!=null)
            throw new IllegalArgumentException("trying to create the same field twice: "+name);

        return f;
    }

    /**  This method indicates if the interface
     *   is an annotationTypeDeclaration
     *
     */
    public boolean isAnnotationTypeDeclaration() {
        return this.classType==ClassType.ANNOTATION_TYPE_DECL;


    }

    /**
     * Add an annotationType Declaration to this package
     * @param name
     *      Name of the annotation Type declaration to be added to this package
     * @return
     *      newly created Annotation Type Declaration
     * @exception JClassAlreadyExistsException
     *      When the specified class/interface was already created.
     */
    public JDefinedClass _annotationTypeDeclaration(String name) throws JClassAlreadyExistsException {
        return _class (JMod.PUBLIC,name,ClassType.ANNOTATION_TYPE_DECL);
    }

    /**
     * Add a public enum to this package
     * @param name
     *      Name of the enum to be added to this package
     * @return
     *      newly created Enum
     * @exception JClassAlreadyExistsException
     *      When the specified class/interface was already created.

     */
    public JDefinedClass _enum (String name) throws JClassAlreadyExistsException {
        return _class (JMod.PUBLIC,name,ClassType.ENUM);
    }

    /**
     * Add a public enum to this package
     * @param name
     *      Name of the enum to be added to this package
     * @param mods
     *          Modifiers for this enum declaration
     * @return
     *      newly created Enum
     * @exception JClassAlreadyExistsException
     *      When the specified class/interface was already created.

     */
    public JDefinedClass _enum (int mods,String name) throws JClassAlreadyExistsException {
        return _class (mods,name,ClassType.ENUM);
    }





    public ClassType getClassType(){
        return this.classType;
    }

    public JFieldVar field(
        int mods,
        Class type,
        String name,
        JExpression init) {
        return field(mods, owner()._ref(type), name, init);
    }

    /**
     * Returns all the fields declred in this class.
     * The returned {@link Map} is a read-only live view.
     *
     * @return always non-null.
     */
    public Map<String,JFieldVar> fields() {
        return Collections.unmodifiableMap(fields);
    }

    /**
     * Removes a {@link JFieldVar} from this class.
     *
     * @throws IllegalArgumentException
     *      if the given field is not a field on this class.
     */
    public void removeField(JFieldVar field) {
        if(fields.remove(field.name())!=field)
            throw new IllegalArgumentException();
    }

    /**
     * Creates, if necessary, and returns the static initializer
     * for this class.
     *
     * @return JBlock containing initialization statements for this class
     */
    public JBlock init() {
        if (init == null)
            init = new JBlock();
        return init;
    }

    /**
     * Adds a constructor to this class.
     *
     * @param mods
     *        Modifiers for this constructor
     */
    public JMethod constructor(int mods) {
        JMethod c = new JMethod(mods, this);
        constructors.add(c);
        return c;
    }

    /**
     * Returns an iterator that walks the constructors defined in this class.
     */
    public Iterator constructors() {
        return constructors.iterator();
    }

    /**
     * Looks for a method that has the specified method signature
     * and return it.
     *
     * @return
     *      null if not found.
     */
    public JMethod getConstructor(JType[] argTypes) {
        for (JMethod m : constructors) {
            if (m.hasSignature(argTypes))
                return m;
        }
        return null;
    }

    /**
     * Add a method to the list of method members of this JDefinedClass instance.
     *
     * @param mods
     *        Modifiers for this method
     *
     * @param type
     *        Return type for this method
     *
     * @param name
     *        Name of the method
     *
     * @return Newly generated JMethod
     */
    public JMethod method(int mods, JType type, String name) {
        // XXX problems caught in M constructor
        JMethod m = new JMethod(this, mods, type, name);
        methods.add(m);
        return m;
    }

    public JMethod method(int mods, Class type, String name) {
        return method(mods, owner()._ref(type), name);
    }

    /**
     * Returns the set of methods defined in this class.
     */
    public Collection<JMethod> methods() {
        return methods;
    }

    /**
     * Looks for a method that has the specified method signature
     * and return it.
     *
     * @return
     *      null if not found.
     */
    public JMethod getMethod(String name, JType[] argTypes) {
        for (JMethod m : methods) {
            if (!m.name().equals(name))
                continue;

            if (m.hasSignature(argTypes))
                return m;
        }
        return null;
    }

    public boolean isClass() {
        return true;
    }
    public boolean isPackage() {
        return false;
    }
    public JPackage getPackage() { return parentContainer().getPackage(); }

    /**
     * Add a new nested class to this class.
     *
     * @param mods
     *        Modifiers for this class declaration
     *
     * @param name
     *        Name of class to be added to this package
     *
     * @return Newly generated class
     */
    public JDefinedClass _class(int mods, String name)
        throws JClassAlreadyExistsException {
        return _class(mods, name, ClassType.CLASS);
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated
     */
    public JDefinedClass _class(int mods, String name, boolean isInterface) throws JClassAlreadyExistsException {
        return _class(mods,name,isInterface?ClassType.INTERFACE:ClassType.CLASS);
    }

    public JDefinedClass _class(int mods, String name, ClassType classTypeVal)
        throws JClassAlreadyExistsException {

        String NAME;
        if (JCodeModel.isCaseSensitiveFileSystem)
            NAME = name.toUpperCase();
        else
            NAME = name;

        if (getClasses().containsKey(NAME))
            throw new JClassAlreadyExistsException(getClasses().get(NAME));
        else {
            // XXX problems caught in the NC constructor
            JDefinedClass c = new JDefinedClass(this, mods, name, classTypeVal);
            getClasses().put(NAME,c);
            return c;
        }
    }

    /**
     * Add a new public nested class to this class.
     */
    public JDefinedClass _class(String name)
        throws JClassAlreadyExistsException {
        return _class(JMod.PUBLIC, name);
    }

    /**
     * Add an interface to this package.
     *
     * @param mods
     *        Modifiers for this interface declaration
     *
     * @param name
     *        Name of interface to be added to this package
     *
     * @return Newly generated interface
     */
    public JDefinedClass _interface(int mods, String name)
        throws JClassAlreadyExistsException {
        return _class(mods, name, ClassType.INTERFACE);
    }

    /**
     * Adds a public interface to this package.
     */
    public JDefinedClass _interface(String name)
        throws JClassAlreadyExistsException {
        return _interface(JMod.PUBLIC, name);
    }

    /**
     * Creates, if necessary, and returns the class javadoc for this
     * JDefinedClass
     *
     * @return JDocComment containing javadocs for this class
     */
    public JDocComment javadoc() {
        if (jdoc == null)
            jdoc = new JDocComment(owner());
        return jdoc;
    }

    /**
     * Mark this file as hidden, so that this file won't be
     * generated.
     *
     * <p>
     * This feature could be used to generate code that refers
     * to class X, without actually generating X.java.
     */
    public void hide() {
        hideFile = true;
    }

    public boolean isHidden() {
        return hideFile;
    }

    /**
     * Returns an iterator that walks the nested classes defined in this
     * class.
     */
    public final Iterator<JDefinedClass> classes() {
        if(classes==null)
            return Collections.<JDefinedClass>emptyList().iterator();
        else
            return classes.values().iterator();
    }

    private Map<String,JDefinedClass> getClasses() {
        if(classes==null)
            classes = new TreeMap<String,JDefinedClass>();
        return classes;
    }


    /**
     * Returns all the nested classes defined in this class.
     */
    public final JClass[] listClasses() {
        if(classes==null)
            return new JClass[0];
        else
            return classes.values().toArray(new JClass[classes.values().size()]);
    }

    @Override
    public JClass outer() {
        if (outer.isClass())
            return (JClass) outer;
        else
            return null;
    }

    public void declare(JFormatter f) {
        if (jdoc != null)
            f.nl().g(jdoc);

        if (annotations != null){
            for (JAnnotationUse annotation : annotations)
                f.g(annotation).nl();
        }

        f.g(mods).p(classType.declarationToken).id(name).d(generifiable);

        if (superClass != null && superClass != owner().ref(Object.class))
            f.nl().i().p("extends").g(superClass).nl().o();

        if (!interfaces.isEmpty()) {
            if (superClass == null)
                f.nl();
            f.i().p(classType==ClassType.INTERFACE ? "extends" : "implements");
            f.g(interfaces);
            f.nl().o();
        }
        declareBody(f);
    }

    /**
     * prints the body of a class.
     */
    protected void declareBody(JFormatter f) {
        f.p('{').nl().nl().i();
        boolean first = true;

        if (!enumConstantsByName.isEmpty()) {
            for (JEnumConstant c : enumConstantsByName.values()) {
                if (!first) f.p(',').nl();
                f.d(c);
                first = false;
            }
                f.p(';').nl();
        }

        for( JFieldVar field : fields.values() )
            f.d(field);
        if (init != null)
            f.nl().p("static").s(init);
        for (JMethod m : constructors) {
            f.nl().d(m);
        }
        for (JMethod m : methods) {
            f.nl().d(m);
        }
        if(classes!=null)
            for (JDefinedClass dc : classes.values())
                f.nl().d(dc);


        if (directBlock != null)
            f.p(directBlock);
        f.nl().o().p('}').nl();
    }

    /**
     * Places the given string directly inside the generated class.
     *
     * This method can be used to add methods/fields that are not
     * generated by CodeModel.
     * This method should be used only as the last resort.
     */
    public void direct(String string) {
        if (directBlock == null)
            directBlock = string;
        else
            directBlock += string;
    }

    public final JPackage _package() {
        JClassContainer p = outer;
        while (!(p instanceof JPackage))
            p = p.parentContainer();
        return (JPackage) p;
    }

    public final JClassContainer parentContainer() {
        return outer;
    }

    public JTypeVar generify(String name) {
        return generifiable.generify(name);
    }
    public JTypeVar generify(String name, Class bound) {
        return generifiable.generify(name, bound);
    }
    public JTypeVar generify(String name, JClass bound) {
        return generifiable.generify(name, bound);
    }
    @Override
    public JTypeVar[] typeParams() {
        return generifiable.typeParams();
    }

    protected JClass substituteParams(
        JTypeVar[] variables,
        List<JClass> bindings) {
        return this;
    }

    /** Adding ability to annotate a class
     * @param clazz
     *          The annotation class to annotate the class with
     */
    public JAnnotationUse annotate(Class <? extends Annotation> clazz){
        return annotate(owner().ref(clazz));
    }

    /** Adding ability to annotate a class
      * @param clazz
      *          The annotation class to annotate the class with
      */
     public JAnnotationUse annotate(JClass clazz){
        if(annotations==null)
           annotations = new ArrayList<JAnnotationUse>();
        JAnnotationUse a = new JAnnotationUse(clazz);
        annotations.add(a);
        return a;
    }

    public <W extends JAnnotationWriter> W annotate2(Class<W> clazz) {
        return TypedAnnotationWriter.create(clazz,this);
    }
}
