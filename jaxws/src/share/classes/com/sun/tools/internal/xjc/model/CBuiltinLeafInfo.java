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
package com.sun.tools.internal.xjc.model;

import java.awt.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.MimeType;
import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import javax.xml.bind.annotation.adapters.NormalizedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;

import com.sun.codemodel.internal.JExpr;
import com.sun.codemodel.internal.JExpression;
import com.sun.codemodel.internal.JType;
import com.sun.tools.internal.xjc.model.nav.NClass;
import com.sun.tools.internal.xjc.model.nav.NType;
import com.sun.tools.internal.xjc.model.nav.NavigatorImpl;
import com.sun.tools.internal.xjc.outline.Aspect;
import com.sun.tools.internal.xjc.outline.Outline;
import com.sun.tools.internal.xjc.runtime.ZeroOneBooleanAdapter;
import com.sun.tools.internal.xjc.util.NamespaceContextAdapter;
import com.sun.xml.internal.bind.DatatypeConverterImpl;
import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import com.sun.xml.internal.bind.v2.model.core.ID;
import com.sun.xml.internal.bind.v2.model.impl.BuiltinLeafInfoImpl;
import com.sun.xml.internal.xsom.XSComponent;
import com.sun.xml.internal.xsom.XmlString;

import org.xml.sax.Locator;

/**
 * Encapsulates the default handling for leaf classes (which are bound
 * to text in XML.) In particular this class knows how to convert
 * the lexical value into the Java class according to this default rule.
 *
 * <p>
 * This represents the spec-defined default handling for the Java
 * type ({@link #getType()}.
 *
 * <p>
 * For those Java classes (such as {@link String} or {@link Boolean})
 * where the spec designates a specific default handling, there are
 * constants in this class (such as {@link #STRING} or {@link #BOOLEAN}.)
 *
 * <p>
 * The generated type-safe enum classes are also a leaf class,
 * and as such there are {@link CEnumLeafInfo} that represents it
 * as {@link CBuiltinLeafInfo}.
 *
 * <p>
 * This class represents the <b>default handling</b>, and therefore
 * we can only have one instance per one {@link NType}. Handling of
 * other XML Schema types (such as xs:token) are represented as
 * a general {@link TypeUse} objects.
 *
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class CBuiltinLeafInfo extends BuiltinLeafInfoImpl<NType,NClass> implements CNonElement {

    private final ID id;

    // no derived class other than the spec-defined ones. definitely not for enum.
    private CBuiltinLeafInfo(NType typeToken, QName typeName, ID id) {
        super(typeToken,typeName);
        this.id = id;
    }

    /**
     * Gets the code model representation of this type.
     */
    public JType toType(Outline o, Aspect aspect) {
        return getType().toType(o,aspect);
    }

    /**
     * Since {@link CBuiltinLeafInfo} represents a default binding,
     * it is never a collection.
     */
    @Deprecated
    public final boolean isCollection() {
        return false;
    }

    /**
     * Guaranteed to return this.
     */
    @Deprecated
    public CNonElement getInfo() {
        return this;
    }

    public ID idUse() {
        return id;
    }

    /**
     * {@link CBuiltinLeafInfo} never has a default associated MIME type.
     */
    public MimeType getExpectedMimeType() {
        return null;
    }

    @Deprecated
    public final CAdapter getAdapterUse() {
        return null;
    }

    public Locator getLocator() {
        return Model.EMPTY_LOCATOR;
    }

    public final XSComponent getSchemaComponent() {
        throw new UnsupportedOperationException("TODO. If you hit this, let us know.");
    }

    /**
     * Creates a {@link TypeUse} that represents a collection of this {@link CBuiltinLeafInfo}.
     */
    public final TypeUse makeCollection() {
        return TypeUseFactory.makeCollection(this);
    }

    /**
     * Creates a {@link TypeUse} that represents an adapted use of this {@link CBuiltinLeafInfo}.
     */
    public final TypeUse makeAdapted( Class<? extends XmlAdapter> adapter, boolean copy ) {
        return TypeUseFactory.adapt(this,adapter,copy);
    }

    /**
     * Creates a {@link TypeUse} that represents a MIME-type assocaited version of this {@link CBuiltinLeafInfo}.
     */
    public final TypeUse makeMimeTyped( MimeType mt ) {
        return TypeUseFactory.makeMimeTyped(this,mt);
    }

    /**
     * {@link CBuiltinLeafInfo} for Java classes that have
     * the spec defined built-in binding semantics.
     */
    private static abstract class Builtin extends CBuiltinLeafInfo {
        protected Builtin(Class c, String typeName) {
            this(c,typeName,com.sun.xml.internal.bind.v2.model.core.ID.NONE);
        }
        protected Builtin(Class c, String typeName, ID id) {
            super(NavigatorImpl.theInstance.ref(c), new QName(WellKnownNamespace.XML_SCHEMA,typeName),id);
            LEAVES.put(getType(),this);
        }

        /**
         * No vendor customization in the built-in classes.
         */
        public CCustomizations getCustomizations() {
            return CCustomizations.EMPTY;
        }
    }

    private static final class NoConstantBuiltin extends Builtin {
        public NoConstantBuiltin(Class c, String typeName) {
            super(c, typeName);
        }
        public JExpression createConstant(Outline outline, XmlString lexical) {
            return null;
        }
    }

    /**
     * All built-in leaves.
     */
    public static final Map<NType,CBuiltinLeafInfo> LEAVES = new HashMap<NType,CBuiltinLeafInfo>();


    public static final CBuiltinLeafInfo ANYTYPE = new NoConstantBuiltin(Object.class,"anyType");
    public static final CBuiltinLeafInfo STRING = new Builtin(String.class,"string") {
            public JExpression createConstant(Outline outline, XmlString lexical) {
                return JExpr.lit(lexical.value);
            }
    };
    public static final CBuiltinLeafInfo BOOLEAN = new Builtin(Boolean.class,"boolean") {
            public JExpression createConstant(Outline outline, XmlString lexical) {
                return JExpr.lit(DatatypeConverterImpl._parseBoolean(lexical.value));
            }
    };
    public static final CBuiltinLeafInfo INT = new Builtin(Integer.class,"int") {
        public JExpression createConstant(Outline outline, XmlString lexical) {
            return JExpr.lit(DatatypeConverterImpl._parseInt(lexical.value));
        }
    };
    public static final CBuiltinLeafInfo LONG = new Builtin(Long.class,"long") {
        public JExpression createConstant(Outline outline, XmlString lexical) {
            return JExpr.lit(DatatypeConverterImpl._parseLong(lexical.value));
        }
    };
    public static final CBuiltinLeafInfo BYTE = new Builtin(Byte.class,"byte") {
        public JExpression createConstant(Outline outline, XmlString lexical) {
            return JExpr.cast(
                    outline.getCodeModel().BYTE,
                    JExpr.lit(DatatypeConverterImpl._parseByte(lexical.value)));
        }
    };
    public static final CBuiltinLeafInfo SHORT = new Builtin(Short.class,"short") {
        public JExpression createConstant(Outline outline, XmlString lexical) {
            return JExpr.cast(
                    outline.getCodeModel().SHORT,
                    JExpr.lit(DatatypeConverterImpl._parseShort(lexical.value)));
        }
    };
    public static final CBuiltinLeafInfo FLOAT = new Builtin(Float.class,"float") {
        public JExpression createConstant(Outline outline, XmlString lexical) {
            return JExpr.lit(DatatypeConverterImpl._parseFloat(lexical.value));
        }
    };
    public static final CBuiltinLeafInfo DOUBLE = new Builtin(Double.class,"double") {
        public JExpression createConstant(Outline outline, XmlString lexical) {
            return JExpr.lit(DatatypeConverterImpl._parseDouble(lexical.value));
        }
    };
    public static final CBuiltinLeafInfo QNAME = new Builtin(QName.class,"QName") {
        public JExpression createConstant(Outline outline, XmlString lexical) {
            QName qn = DatatypeConverterImpl._parseQName(lexical.value,new NamespaceContextAdapter(lexical));
            return JExpr._new(outline.getCodeModel().ref(QName.class))
                .arg(qn.getNamespaceURI())
                .arg(qn.getLocalPart())
                .arg(qn.getPrefix());
        }
    };
    // XMLGregorianCalendar is mutable, so we can't support default values anyhow.
    public static final CBuiltinLeafInfo CALENDAR = new NoConstantBuiltin(XMLGregorianCalendar.class,"dateTime");
    public static final CBuiltinLeafInfo DURATION = new NoConstantBuiltin(Duration.class,"duration");

    public static final CBuiltinLeafInfo BIG_INTEGER = new Builtin(BigInteger.class,"integer") {
        public JExpression createConstant(Outline outline, XmlString lexical) {
            return JExpr._new(outline.getCodeModel().ref(BigInteger.class)).arg(lexical.value.trim());
        }
    };

    public static final CBuiltinLeafInfo BIG_DECIMAL = new Builtin(BigDecimal.class,"decimal") {
        public JExpression createConstant(Outline outline, XmlString lexical) {
            return JExpr._new(outline.getCodeModel().ref(BigDecimal.class)).arg(lexical.value.trim());
        }
    };

    public static final CBuiltinLeafInfo BASE64_BYTE_ARRAY = new Builtin(byte[].class,"base64Binary") {
        public JExpression createConstant(Outline outline, XmlString lexical) {
            return outline.getCodeModel().ref(DatatypeConverter.class).staticInvoke("parseBase64Binary").arg(lexical.value);
        }
    };

    public static final CBuiltinLeafInfo DATA_HANDLER = new NoConstantBuiltin(DataHandler.class,"base64Binary");
    public static final CBuiltinLeafInfo IMAGE = new NoConstantBuiltin(Image.class,"base64Binary");
    public static final CBuiltinLeafInfo XML_SOURCE = new NoConstantBuiltin(Source.class,"base64Binary");

    public static final TypeUse HEXBIN_BYTE_ARRAY =
        STRING.makeAdapted(HexBinaryAdapter.class,false);


    // TODO: not sure if they should belong here,
    // but I couldn't find other places that fit.
    public static final TypeUse TOKEN =
            STRING.makeAdapted(CollapsedStringAdapter.class,false);

    public static final TypeUse NORMALIZED_STRING =
            STRING.makeAdapted(NormalizedStringAdapter.class,false);

    public static final TypeUse ID = TypeUseFactory.makeID(TOKEN,com.sun.xml.internal.bind.v2.model.core.ID.ID);

    /**
     * boolean restricted to 0 or 1.
     */
    public static final TypeUse BOOLEAN_ZERO_OR_ONE =
            STRING.makeAdapted(ZeroOneBooleanAdapter.class,true);

    /**
     * IDREF.
     *
     * IDREF is has a whitespace normalization semantics of token, but
     * we don't want {@link XmlJavaTypeAdapter} and {@link XmlIDREF} to interact.
     */
    public static final TypeUse IDREF = TypeUseFactory.makeID(ANYTYPE,com.sun.xml.internal.bind.v2.model.core.ID.IDREF);

    /**
     * For all list of strings, such as NMTOKENS, ENTITIES.
     */
    public static final TypeUse STRING_LIST =
            STRING.makeCollection();
}
