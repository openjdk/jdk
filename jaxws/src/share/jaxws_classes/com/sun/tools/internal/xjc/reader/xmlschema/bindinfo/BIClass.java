/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.reader.xmlschema.bindinfo;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;

import com.sun.tools.internal.xjc.reader.Const;
import com.sun.xml.internal.bind.api.impl.NameConverter;
import com.sun.istack.internal.Nullable;

/**
 * Class declaration.
 *
 * This customization turns arbitrary schema component into a Java
 * content interface.
 *
 * <p>
 * This customization is acknowledged by the ClassSelector.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
@XmlRootElement(name="class")
public final class BIClass extends AbstractDeclarationImpl {
    protected BIClass() {
    }

    @XmlAttribute(name="name")
    private String className;

    /**
     * Gets the specified class name, or null if not specified.
     * (Not a fully qualified name.)
     *
     * @return
     *      Returns a class name. The caller should <em>NOT</em>
     *      apply XML-to-Java name conversion to the name
     *      returned from this method.
     */
    public @Nullable String getClassName() {
        if( className==null )   return null;

        BIGlobalBinding gb = getBuilder().getGlobalBinding();
        NameConverter nc = getBuilder().model.getNameConverter();

        if(gb.isJavaNamingConventionEnabled()) return nc.toClassName(className);
        else
            // don't change it
            return className;
    }

    @XmlAttribute(name="implClass")
    private String userSpecifiedImplClass;

    /**
     * Gets the fully qualified name of the
     * user-specified implementation class, if any.
     * Or null.
     */
    public String getUserSpecifiedImplClass() {
        return userSpecifiedImplClass;
    }

    @XmlAttribute(name="ref")
    private String ref;

    @XmlAttribute(name="recursive", namespace=Const.XJC_EXTENSION_URI)
    private String recursive;

    /**
     * Reference to the existing class, or null.
     * Fully qualified name.
     *
     * <p>
     * Caller needs to perform error check on this.
     */
    public String getExistingClassRef() {
        return ref;
    }

    public String getRecursive() {
        return recursive;
    }

    @XmlElement
    private String javadoc;
    /**
     * Gets the javadoc comment specified in the customization.
     * Can be null if none is specified.
     */
    public String getJavadoc() { return javadoc; }

    public QName getName() { return NAME; }

    public void setParent(BindInfo p) {
        super.setParent(p);
        // if this specifies a reference to external class,
        // then it's OK even if noone actually refers this class.
        if(ref!=null)
            markAsAcknowledged();
    }

    /** Name of this declaration. */
    public static final QName NAME = new QName( Const.JAXB_NSURI, "class" );
}
