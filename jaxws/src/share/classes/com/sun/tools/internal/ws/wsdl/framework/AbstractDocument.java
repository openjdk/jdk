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

package com.sun.tools.internal.ws.wsdl.framework;

import com.sun.tools.internal.ws.wsdl.parser.MetadataFinder;
import com.sun.tools.internal.ws.wsdl.parser.DOMForest;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.wscompile.AbortException;
import com.sun.tools.internal.ws.resources.WsdlMessages;

import javax.xml.namespace.QName;
import java.util.*;

import org.xml.sax.helpers.LocatorImpl;

/**
 * An abstract class for documents containing entities.
 *
 * @author WS Development Team
 */
public abstract class AbstractDocument {

    protected final DOMForest forest;
    protected final ErrorReceiver errReceiver;

    protected AbstractDocument(MetadataFinder forest, ErrorReceiver errReceiver) {
        this.forest = forest;
        this.errReceiver = errReceiver;
        kinds = new HashMap();
        importedEntities = new ArrayList();
        importedDocuments = new HashSet();
        includedEntities = new ArrayList();
        includedDocuments = new HashSet();
    }

    public String getSystemId() {
        return _systemId;
    }

    public void setSystemId(String s) {
        if (_systemId != null && !_systemId.equals(s)) {
            // avoid redefinition of a system identifier
            throw new IllegalArgumentException();
        }

        _systemId = s;
        if (s != null) {
            importedDocuments.add(s);
        }
    }

    public void addIncludedDocument(String systemId) {
        includedDocuments.add(systemId);
    }

    public boolean isIncludedDocument(String systemId) {
        return includedDocuments.contains(systemId);
    }

    public void addIncludedEntity(Entity entity) {
        includedEntities.add(entity);
    }

    public void addImportedDocument(String systemId) {
        importedDocuments.add(systemId);
    }

    public boolean isImportedDocument(String systemId) {
        return importedDocuments.contains(systemId);
    }

    public void addImportedEntity(Entity entity) {
        importedEntities.add(entity);
    }

    public void withAllSubEntitiesDo(EntityAction action) {
        if (getRoot() != null) {
            action.perform(getRoot());
        }

        for (Iterator iter = importedEntities.iterator(); iter.hasNext();) {
            action.perform((Entity) iter.next());
        }

        for (Iterator iter = includedEntities.iterator(); iter.hasNext();) {
            action.perform((Entity) iter.next());
        }
    }

    public Map getMap(Kind k) {
        Map m = (Map) kinds.get(k.getName());
        if (m == null) {
            m = new HashMap();
            kinds.put(k.getName(), m);
        }
        return m;
    }

    public void define(GloballyKnown e) {
        Map map = getMap(e.getKind());
        if (e.getName() == null)
            return;
        QName name =
            new QName(e.getDefining().getTargetNamespaceURI(), e.getName());

        if (map.containsKey(name)){
            errReceiver.error(e.getLocator(), WsdlMessages.ENTITY_DUPLICATE_WITH_TYPE(e.getElementName().getLocalPart(), e.getName()));
            throw new AbortException();
        }else{
            map.put(name, e);
        }
    }

    public GloballyKnown find(Kind k, QName name) {
        Map map = getMap(k);
        Object result = map.get(name);
        if (result == null){
            errReceiver.error(null, WsdlMessages.ENTITY_NOT_FOUND_BY_Q_NAME(k.getName(), name, _systemId));
            throw new AbortException();
        }
        return (GloballyKnown) result;
    }

    public void validateLocally() {
        LocallyValidatingAction action = new LocallyValidatingAction();
        withAllSubEntitiesDo(action);
        if (action.getException() != null) {
            throw action.getException();
        }
    }

    public abstract void validate(EntityReferenceValidator validator);

    protected abstract Entity getRoot();

    private final Map kinds;
    private String _systemId;
    private final Set importedDocuments;
    private final List importedEntities;
    private final Set includedDocuments;
    private final List includedEntities;

    private class LocallyValidatingAction implements EntityAction {
        public LocallyValidatingAction() {
        }

        public void perform(Entity entity) {
            try {
                entity.validateThis();
                entity.withAllSubEntitiesDo(this);
            } catch (ValidationException e) {
                if (_exception == null) {
                    _exception = e;
                }
            }
        }

        public ValidationException getException() {
            return _exception;
        }

        private ValidationException _exception;
    }
}
