/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

/**
 * An abstract class for documents containing entities.
 *
 * @author WS Development Team
 */
public abstract class AbstractDocument {

    protected AbstractDocument() {
        _kinds = new HashMap();
        _identifiables = new HashMap();
        _importedEntities = new ArrayList();
        _importedDocuments = new HashSet();
        _includedEntities = new ArrayList();
        _includedDocuments = new HashSet();
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
            _importedDocuments.add(s);
        }
    }

    public void addIncludedDocument(String systemId) {
        _includedDocuments.add(systemId);
    }

    public boolean isIncludedDocument(String systemId) {
        return _includedDocuments.contains(systemId);
    }

    public void addIncludedEntity(Entity entity) {
        _includedEntities.add(entity);
    }

    public void addImportedDocument(String systemId) {
        _importedDocuments.add(systemId);
    }

    public boolean isImportedDocument(String systemId) {
        return _importedDocuments.contains(systemId);
    }

    public void addImportedEntity(Entity entity) {
        _importedEntities.add(entity);
    }

    public void withAllSubEntitiesDo(EntityAction action) {
        if (getRoot() != null) {
            action.perform(getRoot());
        }

        for (Iterator iter = _importedEntities.iterator(); iter.hasNext();) {
            action.perform((Entity) iter.next());
        }

        for (Iterator iter = _includedEntities.iterator(); iter.hasNext();) {
            action.perform((Entity) iter.next());
        }
    }

    public Map getMap(Kind k) {
        Map m = (Map) _kinds.get(k.getName());
        if (m == null) {
            m = new HashMap();
            _kinds.put(k.getName(), m);
        }
        return m;
    }

    public void define(GloballyKnown e) {
        Map map = getMap(e.getKind());
        if (e.getName() == null)
            return;
        QName name =
            new QName(e.getDefining().getTargetNamespaceURI(), e.getName());

        if (map.containsKey(name))
            throw new DuplicateEntityException(e);
        else
            map.put(name, e);
    }

    public void undefine(GloballyKnown e) {
        Map map = getMap(e.getKind());
        if (e.getName() == null)
            return;
        QName name =
            new QName(e.getDefining().getTargetNamespaceURI(), e.getName());

        if (map.containsKey(name))
            throw new NoSuchEntityException(name);
        else
            map.remove(name);
    }

    public GloballyKnown find(Kind k, QName name) {
        Map map = getMap(k);
        Object result = map.get(name);
        if (result == null)
            throw new NoSuchEntityException(name);
        return (GloballyKnown) result;
    }

    public void defineID(Identifiable e) {
        String id = e.getID();
        if (id == null)
            return;

        if (_identifiables.containsKey(id))
            throw new DuplicateEntityException(e);
        else
            _identifiables.put(id, e);
    }

    public void undefineID(Identifiable e) {
        String id = e.getID();

        if (id == null)
            return;

        if (_identifiables.containsKey(id))
            throw new NoSuchEntityException(id);
        else
            _identifiables.remove(id);
    }

    public Identifiable findByID(String id) {
        Object result = _identifiables.get(id);
        if (result == null)
            throw new NoSuchEntityException(id);
        return (Identifiable) result;
    }

    public Set collectAllQNames() {
        final Set result = new HashSet();
        EntityAction action = new EntityAction() {
            public void perform(Entity entity) {
                entity.withAllQNamesDo(new QNameAction() {
                    public void perform(QName name) {
                        result.add(name);
                    }
                });
                entity.withAllSubEntitiesDo(this);
            }
        };
        withAllSubEntitiesDo(action);
        return result;
    }

    public Set collectAllNamespaces() {
        final Set result = new HashSet();

        EntityAction action = new EntityAction() {
            public void perform(Entity entity) {
                entity.withAllQNamesDo(new QNameAction() {
                    public void perform(QName name) {
                        result.add(name.getNamespaceURI());
                    }
                });

                entity.withAllSubEntitiesDo(this);
            }
        };
        withAllSubEntitiesDo(action);
        return result;
    }

    public void validateLocally() {
        LocallyValidatingAction action = new LocallyValidatingAction();
        withAllSubEntitiesDo(action);
        if (action.getException() != null) {
            throw action.getException();
        }
    }

    public void validate() {
        validate(null);
    }

    public abstract void validate(EntityReferenceValidator validator);

    protected abstract Entity getRoot();

    private Map _kinds;
    private Map _identifiables;
    private String _systemId;
    private Set _importedDocuments;
    private List _importedEntities;
    private Set _includedDocuments;
    private List _includedEntities;

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
