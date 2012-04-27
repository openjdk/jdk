/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2001, 2002,2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xerces.internal.impl.validation;

import com.sun.org.apache.xerces.internal.util.SymbolTable;
import com.sun.org.apache.xerces.internal.impl.dv.ValidationContext;

import com.sun.org.apache.xerces.internal.xni.NamespaceContext;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Implementation of ValidationContext inteface. Used to establish an
 * environment for simple type validation.
 *
 * @xerces.internal
 *
 * @author Elena Litani, IBM
 * @version $Id: ValidationState.java,v 1.7 2010-11-01 04:39:53 joehw Exp $
 */
public class ValidationState implements ValidationContext {

    //
    // private data
    //
    private boolean fExtraChecking              = true;
    private boolean fFacetChecking              = true;
    private boolean fNormalize                  = true;
    private boolean fNamespaces                 = true;

    private EntityState fEntityState            = null;
    private NamespaceContext fNamespaceContext  = null;
    private SymbolTable fSymbolTable            = null;
    private Locale fLocale                      = null;

    private ArrayList<String> fIdList;
    private ArrayList<String> fIdRefList;

    //
    // public methods
    //
    public void setExtraChecking(boolean newValue) {
        fExtraChecking = newValue;
    }

    public void setFacetChecking(boolean newValue) {
        fFacetChecking = newValue;
    }

    public void setNormalizationRequired (boolean newValue) {
          fNormalize = newValue;
    }

    public void setUsingNamespaces (boolean newValue) {
          fNamespaces = newValue;
    }

    public void setEntityState(EntityState state) {
        fEntityState = state;
    }

    public void setNamespaceSupport(NamespaceContext namespace) {
        fNamespaceContext = namespace;
    }

    public void setSymbolTable(SymbolTable sTable) {
        fSymbolTable = sTable;
    }

    /**
     * return null if all IDREF values have a corresponding ID value;
     * otherwise return the first IDREF value without a matching ID value.
     */
    public String checkIDRefID () {
        if (fIdList == null) {
            if (fIdRefList != null) {
                return fIdRefList.get(0);
            }
        }

        if (fIdRefList != null) {
            String key;
            for (int i = 0; i < fIdRefList.size(); i++) {
                key = fIdRefList.get(i);
                if (!fIdList.contains(key)) {
                      return key;
                }
            }
        }
        return null;
    }

    public void reset () {
        fExtraChecking = true;
        fFacetChecking = true;
        fNamespaces = true;
        fIdList = null;
        fIdRefList = null;
        fEntityState = null;
        fNamespaceContext = null;
        fSymbolTable = null;
    }

    /**
     * The same validation state can be used to validate more than one (schema)
     * validation roots. Entity/Namespace/Symbol are shared, but each validation
     * root needs its own id/idref tables. So we need this method to reset only
     * the two tables.
     */
    public void resetIDTables() {
        fIdList = null;
        fIdRefList = null;
    }

    //
    // implementation of ValidationContext methods
    //

    // whether to do extra id/idref/entity checking
    public boolean needExtraChecking() {
        return fExtraChecking;
    }

    // whether to validate against facets
    public boolean needFacetChecking() {
        return fFacetChecking;
    }

    public boolean needToNormalize (){
        return fNormalize;
    }

    public boolean useNamespaces() {
        return fNamespaces;
    }

    // entity
    public boolean isEntityDeclared (String name) {
        if (fEntityState !=null) {
            return fEntityState.isEntityDeclared(getSymbol(name));
        }
        return false;
    }
    public boolean isEntityUnparsed (String name) {
        if (fEntityState !=null) {
            return fEntityState.isEntityUnparsed(getSymbol(name));
        }
        return false;
    }

    // id
    public boolean isIdDeclared(String name) {
        if (fIdList == null) return false;
        return fIdList.contains(name);
    }
    public void addId(String name) {
        if (fIdList == null) fIdList = new ArrayList();
        fIdList.add(name);
    }

    // idref
    public void addIdRef(String name) {
        if (fIdRefList == null) fIdRefList = new ArrayList();
        fIdRefList.add(name);
    }
    // get symbols

    public String getSymbol (String symbol) {
        if (fSymbolTable != null)
            return fSymbolTable.addSymbol(symbol);
        // if there is no symbol table, we return java-internalized string,
        // because symbol table strings are also java-internalzied.
        // this guarantees that the returned string from this method can be
        // compared by reference with other symbol table string. -SG
        return symbol.intern();
    }
    // qname, notation
    public String getURI(String prefix) {
        if (fNamespaceContext !=null) {
            return fNamespaceContext.getURI(prefix);
        }
        return null;
    }

    // Locale

    public void setLocale(Locale locale) {
        fLocale = locale;
    }

    public Locale getLocale() {
        return fLocale;
    }
}
