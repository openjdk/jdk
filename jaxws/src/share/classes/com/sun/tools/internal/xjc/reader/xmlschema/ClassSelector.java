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
package com.sun.tools.internal.xjc.reader.xmlschema;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JJavaName;
import com.sun.codemodel.internal.JPackage;
import com.sun.codemodel.internal.util.JavadocEscapeWriter;
import com.sun.istack.internal.NotNull;
import com.sun.tools.internal.xjc.model.CBuiltinLeafInfo;
import com.sun.tools.internal.xjc.model.CClassInfo;
import com.sun.tools.internal.xjc.model.CClassInfoParent;
import com.sun.tools.internal.xjc.model.CElement;
import com.sun.tools.internal.xjc.model.CElementInfo;
import com.sun.tools.internal.xjc.model.CTypeInfo;
import com.sun.tools.internal.xjc.model.TypeUse;
import com.sun.tools.internal.xjc.model.CClass;
import com.sun.tools.internal.xjc.model.CNonElement;
import com.sun.tools.internal.xjc.reader.Ring;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIProperty;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BISchemaBinding;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.LocalScoping;
import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSComponent;
import com.sun.xml.internal.xsom.XSDeclaration;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSSchema;
import com.sun.xml.internal.xsom.XSSchemaSet;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSType;
import com.sun.xml.internal.xsom.impl.util.SchemaWriter;
import com.sun.xml.internal.xsom.util.ComponentNameFunction;

import org.xml.sax.Locator;

/**
 * Manages association between {@link XSComponent}s and generated
 * {@link CTypeInfo}s.
 *
 * <p>
 * This class determines which component is mapped to (or is not mapped to)
 * what types.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class ClassSelector extends BindingComponent {
    /** Center of owner classes. */
    private final BGMBuilder builder = Ring.get(BGMBuilder.class);


    /**
     * Map from XSComponents to {@link Binding}s. Keeps track of all
     * content interfaces that are already built or being built.
     */
    private final Map<XSComponent,Binding> bindMap = new HashMap<XSComponent,Binding>();

    /**
     * UGLY HACK.
     * <p>
     * To avoid cyclic dependency between binding elements and types,
     * we need additional markers that tell which elements are definitely not bound
     * to a class.
     * <p>
     * the cyclic dependency is as follows:
     * elements need to bind its types first, because otherwise it can't
     * determine T of JAXBElement<T>.
     * OTOH, types need to know whether its parent is bound to a class to decide
     * which class name to use.
     */
    /*package*/ final Map<XSComponent,CElementInfo> boundElements = new HashMap<XSComponent,CElementInfo>();

    /**
     * A list of {@link Binding}s object that needs to be built.
     */
    private final Stack<Binding> bindQueue = new Stack<Binding>();

    /**
     * {@link CClassInfo}s that are already {@link Binding#build() built}.
     */
    private final Set<CClassInfo> built = new HashSet<CClassInfo>();

    /**
     * Object that determines components that are mapped
     * to classes.
     */
    private final ClassBinder classBinder;

    /**
     * {@link CClassInfoParent}s that determines where a new class
     * should be created.
     */
    private final Stack<CClassInfoParent> classScopes = new Stack<CClassInfoParent>();

    /**
     * The component that is being bound to {@link #currentBean}.
     */
    private XSComponent currentRoot;
    /**
     * The bean representation we are binding right now.
     */
    private CClassInfo currentBean;


    private final class Binding {
        private final XSComponent sc;
        private final CTypeInfo bean;

        public Binding(XSComponent sc, CTypeInfo bean) {
            this.sc = sc;
            this.bean = bean;
        }

        void build() {
            if(!(this.bean instanceof CClassInfo))
                return; // no need to "build"

            CClassInfo bean = (CClassInfo)this.bean;

            if(!built.add(bean))
                return; // already built

            for( String reservedClassName : reservedClassNames ) {
                if( bean.getName().equals(reservedClassName) ) {
                    getErrorReporter().error( sc.getLocator(),
                        Messages.ERR_RESERVED_CLASS_NAME, reservedClassName );
                    break;
                }
            }

            // if this schema component is an element declaration
            // and it satisfies a set of conditions specified in the spec,
            // this class will receive a constructor.
            if(needValueConstructor(sc)) {
                // TODO: fragile. There is no guarantee that the property name
                // is in fact "value".
                bean.addConstructor("value");
            }

            if(bean.javadoc==null)
                addSchemaFragmentJavadoc(bean,sc);

            // build the body
            if(builder.getGlobalBinding().getFlattenClasses()==LocalScoping.NESTED)
                pushClassScope(bean);
            else
                pushClassScope(bean.parent());
            XSComponent oldRoot = currentRoot;
            CClassInfo oldBean = currentBean;
            currentRoot = sc;
            currentBean = bean;
            sc.visit(Ring.get(BindRed.class));
            currentBean = oldBean;
            currentRoot = oldRoot;
            popClassScope();

            // acknowledge property customization on this schema component,
            // since it is OK to have a customization at the point of declaration
            // even when no one is using it.
            BIProperty prop = builder.getBindInfo(sc).get(BIProperty.class);
            if(prop!=null)  prop.markAsAcknowledged();
        }
    }


    // should be instanciated only from BGMBuilder.
    public ClassSelector() {
        classBinder = new Abstractifier(new DefaultClassBinder());
        Ring.add(ClassBinder.class,classBinder);

        classScopes.push(null);  // so that the getClassFactory method returns null

        XSComplexType anyType = Ring.get(XSSchemaSet.class).getComplexType(WellKnownNamespace.XML_SCHEMA,"anyType");
        bindMap.put(anyType,new Binding(anyType,CBuiltinLeafInfo.ANYTYPE));
    }

    /** Gets the current class scope. */
    public final CClassInfoParent getClassScope() {
        assert !classScopes.isEmpty();
        return classScopes.peek();
    }

    public final void pushClassScope( CClassInfoParent clsFctry ) {
        assert clsFctry!=null;
        classScopes.push(clsFctry);
    }

    public final void popClassScope() {
        classScopes.pop();
    }

    public XSComponent getCurrentRoot() {
        return currentRoot;
    }

    public CClassInfo getCurrentBean() {
        return currentBean;
    }

    /**
     * Checks if the given component is bound to a class.
     */
    public final CElement isBound( XSElementDecl x, XSComponent referer ) {
        CElementInfo r = boundElements.get(x);
        if(r!=null)
            return r;
        return bindToType(x,referer);
    }

    /**
     * Checks if the given component is being mapped to a type.
     * If so, build that type and return that object.
     * If it is not being mapped to a type item, return null.
     */
    public CTypeInfo bindToType( XSComponent sc, XSComponent referer ) {
        return _bindToClass(sc,referer,false);
    }

    //
    // some schema components are guaranteed to map to a particular CTypeInfo.
    // the following versions capture those constraints in the signature
    // and making the bindToType invocation more type safe.
    //

    public CElement bindToType( XSElementDecl e, XSComponent referer ) {
        return (CElement)_bindToClass(e,referer,false);
    }

    public CClass bindToType( XSComplexType t, XSComponent referer, boolean cannotBeDelayed ) {
        // this assumption that a complex type always binds to a ClassInfo
        // does not hold for xs:anyType --- our current approach of handling
        // this idiosynchracy is to make sure that xs:anyType doesn't use
        // this codepath.
        return (CClass)_bindToClass(t,referer,cannotBeDelayed);
    }

    public TypeUse bindToType( XSType t, XSComponent referer ) {
        if(t instanceof XSSimpleType) {
            return Ring.get(SimpleTypeBuilder.class).build((XSSimpleType)t);
        } else
            return (CNonElement)_bindToClass(t,referer,false);
    }

    /**
     * The real meat of the "bindToType" code.
     *
     * @param cannotBeDelayed
     *      if the binding of the body of the class cannot be defered
     *      and needs to be done immediately. If the flag is false,
     *      the binding of the body will be done later, to avoid
     *      cyclic binding problem.
     * @param referer
     *      The component that refers to <tt>sc</tt>. This can be null,
     *      if figuring out the referer is too hard, in which case
     *      the error message might be less user friendly.
     */
    // TODO: consider getting rid of "cannotBeDelayed"
    CTypeInfo _bindToClass( @NotNull XSComponent sc, XSComponent referer, boolean cannotBeDelayed ) {
        // check if this class is already built.
        if(!bindMap.containsKey(sc)) {
            // craete a bind task

            // if this is a global declaration, make sure they will be generated
            // under a package.
            boolean isGlobal = false;
            if( sc instanceof XSDeclaration ) {
                isGlobal = ((XSDeclaration)sc).isGlobal();
                if( isGlobal )
                    pushClassScope( new CClassInfoParent.Package(
                        getPackage(((XSDeclaration)sc).getTargetNamespace())) );
            }

            // otherwise check if this component should become a class.
            CElement bean = sc.apply(classBinder);

            if( isGlobal )
                popClassScope();

            if(bean==null)
                return null;

            // can this namespace generate a class?
            if (bean instanceof CClassInfo) {
                XSSchema os = sc.getOwnerSchema();
                BISchemaBinding sb = builder.getBindInfo(os).get(BISchemaBinding.class);
                if(sb!=null && !sb.map) {
                    // nope
                    getErrorReporter().error(sc.getLocator(),
                        Messages.ERR_REFERENCE_TO_NONEXPORTED_CLASS, sc.apply( new ComponentNameFunction() ) );
                    getErrorReporter().error(sb.getLocation(),
                        Messages.ERR_REFERENCE_TO_NONEXPORTED_CLASS_MAP_FALSE, os.getTargetNamespace() );
                    if(referer!=null)
                        getErrorReporter().error(referer.getLocator(),
                            Messages.ERR_REFERENCE_TO_NONEXPORTED_CLASS_REFERER, referer.apply( new ComponentNameFunction() ) );
                }
            }


            queueBuild( sc, bean );
        }

        Binding bind = bindMap.get(sc);
        if( cannotBeDelayed )
            bind.build();

        return bind.bean;
    }

    /**
     * Runs all the pending build tasks.
     */
    public void executeTasks() {
        while( bindQueue.size()!=0 )
            bindQueue.pop().build();
    }








    /**
     * Determines if the given component needs to have a value
     * constructor (a constructor that takes a parmater.) on ObjectFactory.
     */
    private boolean needValueConstructor( XSComponent sc ) {
        if(!(sc instanceof XSElementDecl))  return false;

        XSElementDecl decl = (XSElementDecl)sc;
        if(!decl.getType().isSimpleType())  return false;

        return true;
    }

    private static final String[] reservedClassNames = new String[]{"ObjectFactory"};

    public void queueBuild( XSComponent sc, CElement bean ) {
        // it is an error if the same component is built twice,
        // or the association is modified.
        Binding b = new Binding(sc,bean);
        bindQueue.push(b);
        Binding old = bindMap.put(sc, b);
        assert old==null || old.bean==bean;
    }


    /**
     * Copies a schema fragment into the javadoc of the generated class.
     */
    private void addSchemaFragmentJavadoc( CClassInfo bean, XSComponent sc ) {

        // first, pick it up from <documentation> if any.
        String doc = builder.getBindInfo(sc).getDocumentation();
        if(doc!=null)
            append(bean, doc);

        // then the description of where this component came from
        Locator loc = sc.getLocator();
        String fileName = null;
        if(loc!=null) {
            fileName = loc.getPublicId();
            if(fileName==null)
                fileName = loc.getSystemId();
        }
        if(fileName==null)  fileName="";

        String lineNumber=Messages.format( Messages.JAVADOC_LINE_UNKNOWN);
        if(loc!=null && loc.getLineNumber()!=-1)
            lineNumber = String.valueOf(loc.getLineNumber());

        String componentName = sc.apply( new ComponentNameFunction() );
        String jdoc = Messages.format( Messages.JAVADOC_HEADING, componentName, fileName, lineNumber );
        append(bean,jdoc);

        // then schema fragment
        StringWriter out = new StringWriter();
        out.write("<pre>\n");
        SchemaWriter sw = new SchemaWriter(new JavadocEscapeWriter(out));
        sc.visit(sw);
        out.write("</pre>");
        append(bean,out.toString());
    }

    private void append(CClassInfo bean, String doc) {
        if(bean.javadoc==null)
            bean.javadoc = doc+'\n';
        else
            bean.javadoc += '\n'+doc+'\n';
    }


    /**
     * Set of package names that are tested (set of <code>String</code>s.)
     *
     * This set is used to avoid duplicating "incorrect package name"
     * errors.
     */
    private static Set<String> checkedPackageNames = new HashSet<String>();

    /**
     * Gets the Java package to which classes from
     * this namespace should go.
     *
     * <p>
     * Usually, the getOuterClass method should be used
     * to determine where to put a class.
     */
    public JPackage getPackage(String targetNamespace) {
        XSSchema s = Ring.get(XSSchemaSet.class).getSchema(targetNamespace);

        BISchemaBinding sb =
            builder.getBindInfo(s).get(BISchemaBinding.class);
        if(sb!=null)    sb.markAsAcknowledged();

        String name = null;

        // "-p" takes precedence over everything else
        if( builder.defaultPackage1 != null )
            name = builder.defaultPackage1;

        // use the <jaxb:package> customization
        if( name == null && sb!=null && sb.getPackageName()!=null )
            name = sb.getPackageName();

        // the JAX-RPC option goes below the <jaxb:package>
        if( name == null && builder.defaultPackage2 != null )
            name = builder.defaultPackage2;

        // generate the package name from the targetNamespace
        if( name == null )
            name = builder.getNameConverter().toPackageName( targetNamespace );

        // hardcode a package name because the code doesn't compile
        // if it generated into the default java package
        if( name == null )
            name = "generated"; // the last resort


        // check if the package name is a valid name.
        if( checkedPackageNames.add(name) ) {
            // this is the first time we hear about this package name.
            if( !JJavaName.isJavaPackageName(name) )
                // TODO: s.getLocator() is not very helpful.
                // ideally, we'd like to use the locator where this package name
                // comes from.
                getErrorReporter().error(s.getLocator(),
                    Messages.ERR_INCORRECT_PACKAGE_NAME, targetNamespace, name );
        }

        return Ring.get(JCodeModel.class)._package(name);
    }
}
