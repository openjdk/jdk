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

package com.sun.tools.internal.ws.wsdl.document;

import com.sun.tools.internal.ws.wsdl.framework.*;
import com.sun.tools.internal.ws.wsdl.parser.MetadataFinder;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

/**
 * A WSDL document.
 *
 * @author WS Development Team
 */
public class WSDLDocument extends AbstractDocument{

    public WSDLDocument(MetadataFinder forest, ErrorReceiver errReceiver) {
        super(forest, errReceiver);
    }

    public Definitions getDefinitions() {
        return _definitions;
    }

    public void setDefinitions(Definitions d) {
        _definitions = d;
    }

    public QName[] getAllServiceQNames() {

        ArrayList serviceQNames = new ArrayList();

        for (Iterator iter = getDefinitions().services(); iter.hasNext();) {
            Service next = (Service) iter.next();
            String targetNamespace = next.getDefining().getTargetNamespaceURI();
            String localName = next.getName();
            QName serviceQName = new QName(targetNamespace, localName);
            serviceQNames.add(serviceQName);
        }
        return (QName[]) serviceQNames.toArray(new QName[serviceQNames.size()]);
    }

    public QName[] getAllPortQNames() {
        ArrayList portQNames = new ArrayList();

        for (Iterator iter = getDefinitions().services(); iter.hasNext();) {
            Service next = (Service) iter.next();
            //Iterator ports = next.ports();
            for (Iterator piter = next.ports(); piter.hasNext();) {
                // If it's a relative import
                Port pnext = (Port) piter.next();
                String targetNamespace =
                    pnext.getDefining().getTargetNamespaceURI();
                String localName = pnext.getName();
                QName portQName = new QName(targetNamespace, localName);
                portQNames.add(portQName);
            }
        }
        return (QName[]) portQNames.toArray(new QName[portQNames.size()]);
    }

    public QName[] getPortQNames(String serviceNameLocalPart) {

        ArrayList portQNames = new ArrayList();

        for (Iterator iter = getDefinitions().services(); iter.hasNext();) {
            Service next = (Service) iter.next();
            if (next.getName().equals(serviceNameLocalPart)) {
                for (Iterator piter = next.ports(); piter.hasNext();) {
                    Port pnext = (Port) piter.next();
                    String targetNamespace =
                        pnext.getDefining().getTargetNamespaceURI();
                    String localName = pnext.getName();
                    QName portQName = new QName(targetNamespace, localName);
                    portQNames.add(portQName);
                }
            }
        }
        return (QName[]) portQNames.toArray(new QName[portQNames.size()]);
    }

    public void accept(WSDLDocumentVisitor visitor) throws Exception {
        _definitions.accept(visitor);
    }

    public void validate(EntityReferenceValidator validator) {
        GloballyValidatingAction action =
            new GloballyValidatingAction(this, validator);
        withAllSubEntitiesDo(action);
        if (action.getException() != null) {
            throw action.getException();
        }
    }

    protected Entity getRoot() {
        return _definitions;
    }

    private Definitions _definitions;

    private class GloballyValidatingAction
        implements EntityAction, EntityReferenceAction {
        public GloballyValidatingAction(
            AbstractDocument document,
            EntityReferenceValidator validator) {
            _document = document;
            _validator = validator;
        }

        public void perform(Entity entity) {
            try {
                entity.validateThis();
                entity.withAllEntityReferencesDo(this);
                entity.withAllSubEntitiesDo(this);
            } catch (ValidationException e) {
                if (_exception == null) {
                    _exception = e;
                }
            }
        }

        public void perform(Kind kind, QName name) {
            try {
                GloballyKnown entity = _document.find(kind, name);
            } catch (NoSuchEntityException e) {
                // failed to resolve, check with the validator
                if (_exception == null) {
                    if (_validator == null
                        || !_validator.isValid(kind, name)) {
                        _exception = e;
                    }
                }
            }
        }

        public ValidationException getException() {
            return _exception;
        }

        private ValidationException _exception;
        private AbstractDocument _document;
        private EntityReferenceValidator _validator;
    }
}
