/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.DatatypeConverterImpl;
import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import com.sun.xml.internal.bind.v2.runtime.JaxBeanInfo;
import com.sun.istack.internal.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Looks at @xsi:type and forwards to the right {@link Loader}.
 *
 * @author Kohsuke Kawaguchi
 */
public class XsiTypeLoader extends Loader {

    /**
     * Use this when no @xsi:type was found.
     */
    private final JaxBeanInfo defaultBeanInfo;

    public XsiTypeLoader(JaxBeanInfo defaultBeanInfo) {
        super(true);
        this.defaultBeanInfo = defaultBeanInfo;
    }

    public void startElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
        JaxBeanInfo beanInfo = parseXsiType(state,ea,defaultBeanInfo);
        if(beanInfo==null)
            beanInfo = defaultBeanInfo;

        Loader loader = beanInfo.getLoader(null,false);
        state.setLoader(loader);
        loader.startElement(state,ea);
    }

    /*pacakge*/ static JaxBeanInfo parseXsiType(UnmarshallingContext.State state, TagName ea, @Nullable JaxBeanInfo defaultBeanInfo) throws SAXException {
        UnmarshallingContext context = state.getContext();
        JaxBeanInfo beanInfo = null;

        // look for @xsi:type
        Attributes atts = ea.atts;
        int idx = atts.getIndex(WellKnownNamespace.XML_SCHEMA_INSTANCE,"type");

        if(idx>=0) {
            // we'll consume the value only when it's a recognized value,
            // so don't consume it just yet.
            String value = atts.getValue(idx);

            QName type = DatatypeConverterImpl._parseQName(value,context);
            if(type==null) {
                reportError(Messages.NOT_A_QNAME.format(value),true);
            } else {
                if(defaultBeanInfo!=null && defaultBeanInfo.getTypeNames().contains(type))
                    // if this xsi:type is something that the default type can already handle,
                    // let it do so. This is added as a work around to bug https://jax-ws.dev.java.net/issues/show_bug.cgi?id=195
                    // where a redundant xsi:type="xs:dateTime" causes JAXB to unmarshal XMLGregorianCalendar,
                    // where Date is expected.
                    // this is not a complete fix, as we still won't be able to handle simple type substitution in general,
                    // but none-the-less
                    return defaultBeanInfo;

                beanInfo = context.getJAXBContext().getGlobalType(type);
                if(beanInfo==null) { // let's report an error
                    if (context.parent.hasEventHandler() // is somebody listening?
                            && context.shouldErrorBeReported()) { // should we report error?
                        String nearest = context.getJAXBContext().getNearestTypeName(type);
                        if(nearest!=null)
                            reportError(Messages.UNRECOGNIZED_TYPE_NAME_MAYBE.format(type,nearest),true);
                        else
                            reportError(Messages.UNRECOGNIZED_TYPE_NAME.format(type),true);
                    }
                }
                // TODO: resurrect the following check
        //                    else
        //                    if(!target.isAssignableFrom(actual)) {
        //                        reportError(context,
        //                            Messages.UNSUBSTITUTABLE_TYPE.format(value,actual.getName(),target.getName()),
        //                            true);
        //                        actual = targetBeanInfo;  // ditto
        //                    }
            }
        }
        return beanInfo;
    }

    static final QName XsiTypeQNAME = new QName(WellKnownNamespace.XML_SCHEMA_INSTANCE,"type");

    @Override
    public Collection<QName> getExpectedAttributes() {
        final Collection<QName> expAttrs =  new HashSet<QName>();
        expAttrs.addAll(super.getExpectedAttributes());
        expAttrs.add(XsiTypeQNAME);
        return Collections.unmodifiableCollection(expAttrs);
    }
}
