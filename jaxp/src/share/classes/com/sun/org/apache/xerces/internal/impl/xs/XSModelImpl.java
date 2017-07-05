/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2002-2005 The Apache Software Foundation.
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

package com.sun.org.apache.xerces.internal.impl.xs;

import java.util.Vector;

import com.sun.org.apache.xerces.internal.xs.StringList;
import com.sun.org.apache.xerces.internal.xs.XSAttributeDeclaration;
import com.sun.org.apache.xerces.internal.xs.XSAttributeGroupDefinition;
import com.sun.org.apache.xerces.internal.xs.XSConstants;
import com.sun.org.apache.xerces.internal.xs.XSElementDeclaration;
import com.sun.org.apache.xerces.internal.xs.XSModel;
import com.sun.org.apache.xerces.internal.xs.XSModelGroupDefinition;
import com.sun.org.apache.xerces.internal.xs.XSNamedMap;
import com.sun.org.apache.xerces.internal.xs.XSNamespaceItemList;
import com.sun.org.apache.xerces.internal.xs.XSNotationDeclaration;
import com.sun.org.apache.xerces.internal.xs.XSObjectList;
import com.sun.org.apache.xerces.internal.xs.XSTypeDefinition;
import com.sun.org.apache.xerces.internal.impl.xs.util.NSItemListImpl;
import com.sun.org.apache.xerces.internal.impl.xs.util.StringListImpl;
import com.sun.org.apache.xerces.internal.impl.xs.util.XSNamedMap4Types;
import com.sun.org.apache.xerces.internal.impl.xs.util.XSNamedMapImpl;
import com.sun.org.apache.xerces.internal.impl.xs.util.XSObjectListImpl;
import com.sun.org.apache.xerces.internal.util.SymbolHash;
import com.sun.org.apache.xerces.internal.util.XMLSymbols;

/**
 * Implements XSModel:  a read-only interface that represents an XML Schema,
 * which could be components from different namespaces.
 *
 * @xerces.internal
 *
 * @author Sandy Gao, IBM
 *
 */
public class XSModelImpl implements XSModel {

    // the max index / the max value of XSObject type
    private static final short MAX_COMP_IDX = XSTypeDefinition.SIMPLE_TYPE;
    private static final boolean[] GLOBAL_COMP = {false,    // null
                                                  true,     // attribute
                                                  true,     // element
                                                  true,     // type
                                                  false,    // attribute use
                                                  true,     // attribute group
                                                  true,     // group
                                                  false,    // model group
                                                  false,    // particle
                                                  false,    // wildcard
                                                  false,    // idc
                                                  true,     // notation
                                                  false,    // annotation
                                                  false,    // facet
                                                  false,    // multi value facet
                                                  true,     // complex type
                                                  true      // simple type
                                                 };

    // number of grammars/namespaces stored here
    private int fGrammarCount;
    // all target namespaces
    private String[] fNamespaces;
    // all schema grammar objects (for each namespace)
    private SchemaGrammar[] fGrammarList;
    // a map from namespace to schema grammar
    private SymbolHash fGrammarMap;
    // a map from element declaration to its substitution group
    private SymbolHash fSubGroupMap;

    // store a certain kind of components from all namespaces
    private XSNamedMap[] fGlobalComponents;
    // store a certain kind of components from one namespace
    private XSNamedMap[][] fNSComponents;

    // store all annotations
    private XSObjectListImpl fAnnotations = null;

    // whether there is any IDC in this XSModel
    private boolean fHasIDC = false;

   /**
    * Construct an XSModelImpl, by storing some grammars and grammars imported
    * by them to this object.
    *
    * @param grammars   the array of schema grammars
    */
    public XSModelImpl(SchemaGrammar[] grammars) {
        // copy namespaces/grammars from the array to our arrays
        int len = grammars.length;
        fNamespaces = new String[Math.max(len+1, 5)];
        fGrammarList = new SchemaGrammar[Math.max(len+1, 5)];
        boolean hasS4S = false;
        for (int i = 0; i < len; i++) {
            fNamespaces[i] = grammars[i].getTargetNamespace();
            fGrammarList[i] = grammars[i];
            if (fNamespaces[i] == SchemaSymbols.URI_SCHEMAFORSCHEMA)
                hasS4S = true;
        }
        // If a schema for the schema namespace isn't included, include it here.
        if (!hasS4S) {
            fNamespaces[len] = SchemaSymbols.URI_SCHEMAFORSCHEMA;
            fGrammarList[len++] = SchemaGrammar.SG_SchemaNS;
        }

        SchemaGrammar sg1, sg2;
        Vector gs;
        int i, j, k;
        // and recursively get all imported grammars, add them to our arrays
        for (i = 0; i < len; i++) {
            // get the grammar
            sg1 = fGrammarList[i];
            gs = sg1.getImportedGrammars();
            // for each imported grammar
            for (j = gs == null ? -1 : gs.size() - 1; j >= 0; j--) {
                sg2 = (SchemaGrammar)gs.elementAt(j);
                // check whether this grammar is already in the list
                for (k = 0; k < len; k++) {
                    if (sg2 == fGrammarList[k])
                        break;
                }
                // if it's not, add it to the list
                if (k == len) {
                    // ensure the capacity of the arrays
                    if (len == fGrammarList.length) {
                        String[] newSA = new String[len*2];
                        System.arraycopy(fNamespaces, 0, newSA, 0, len);
                        fNamespaces = newSA;
                        SchemaGrammar[] newGA = new SchemaGrammar[len*2];
                        System.arraycopy(fGrammarList, 0, newGA, 0, len);
                        fGrammarList = newGA;
                    }
                    fNamespaces[len] = sg2.getTargetNamespace();
                    fGrammarList[len] = sg2;
                    len++;
                }
            }
        }

        // establish the mapping from namespace to grammars
        fGrammarMap = new SymbolHash(len*2);
        for (i = 0; i < len; i++) {
            fGrammarMap.put(null2EmptyString(fNamespaces[i]), fGrammarList[i]);
            // update the idc field
            if (fGrammarList[i].hasIDConstraints())
                fHasIDC = true;
        }

        fGrammarCount = len;
        fGlobalComponents = new XSNamedMap[MAX_COMP_IDX+1];
        fNSComponents = new XSNamedMap[len][MAX_COMP_IDX+1];

        // build substitution groups
        buildSubGroups();
    }

    private void buildSubGroups() {
        SubstitutionGroupHandler sgHandler = new SubstitutionGroupHandler(null);
        for (int i = 0 ; i < fGrammarCount; i++) {
            sgHandler.addSubstitutionGroup(fGrammarList[i].getSubstitutionGroups());
        }

        XSNamedMap elements = getComponents(XSConstants.ELEMENT_DECLARATION);
        int len = elements.getLength();
        fSubGroupMap = new SymbolHash(len*2);
        XSElementDecl head;
        XSElementDeclaration[] subGroup;
        for (int i = 0; i < len; i++) {
            head = (XSElementDecl)elements.item(i);
            subGroup = sgHandler.getSubstitutionGroup(head);
            fSubGroupMap.put(head, subGroup.length > 0 ?
                    new XSObjectListImpl(subGroup, subGroup.length) : XSObjectListImpl.EMPTY_LIST);
        }
    }

    /**
     * Convenience method. Returns a list of all namespaces that belong to
     * this schema.
     * @return A list of all namespaces that belong to this schema or
     *   <code>null</code> if all components don't have a targetNamespace.
     */
    public StringList getNamespaces() {
        // REVISIT: should the type of fNamespace be StringListImpl?
        return new StringListImpl(fNamespaces, fGrammarCount);
    }


    public XSNamespaceItemList getNamespaceItems() {

        // REVISIT: should the type of fGrammarList be NSItemListImpl?
        return new NSItemListImpl(fGrammarList, fGrammarCount);
    }

    /**
     * Returns a list of top-level components, i.e. element declarations,
     * attribute declarations, etc.
     * @param objectType The type of the declaration, i.e.
     *   <code>ELEMENT_DECLARATION</code>. Note that
     *   <code>XSTypeDefinition.SIMPLE_TYPE</code> and
     *   <code>XSTypeDefinition.COMPLEX_TYPE</code> can also be used as the
     *   <code>objectType</code> to retrieve only complex types or simple
     *   types, instead of all types.
     * @return  A list of top-level definitions of the specified type in
     *   <code>objectType</code> or an empty <code>XSNamedMap</code> if no
     *   such definitions exist.
     */
    public synchronized XSNamedMap getComponents(short objectType) {
        if (objectType <= 0 || objectType > MAX_COMP_IDX ||
            !GLOBAL_COMP[objectType]) {
            return XSNamedMapImpl.EMPTY_MAP;
        }

        SymbolHash[] tables = new SymbolHash[fGrammarCount];
        // get all hashtables from all namespaces for this type of components
        if (fGlobalComponents[objectType] == null) {
            for (int i = 0; i < fGrammarCount; i++) {
                switch (objectType) {
                case XSConstants.TYPE_DEFINITION:
                case XSTypeDefinition.COMPLEX_TYPE:
                case XSTypeDefinition.SIMPLE_TYPE:
                    tables[i] = fGrammarList[i].fGlobalTypeDecls;
                    break;
                case XSConstants.ATTRIBUTE_DECLARATION:
                    tables[i] = fGrammarList[i].fGlobalAttrDecls;
                    break;
                case XSConstants.ELEMENT_DECLARATION:
                    tables[i] = fGrammarList[i].fGlobalElemDecls;
                    break;
                case XSConstants.ATTRIBUTE_GROUP:
                    tables[i] = fGrammarList[i].fGlobalAttrGrpDecls;
                    break;
                case XSConstants.MODEL_GROUP_DEFINITION:
                    tables[i] = fGrammarList[i].fGlobalGroupDecls;
                    break;
                case XSConstants.NOTATION_DECLARATION:
                    tables[i] = fGrammarList[i].fGlobalNotationDecls;
                    break;
                }
            }
            // for complex/simple types, create a special implementation,
            // which take specific types out of the hash table
            if (objectType == XSTypeDefinition.COMPLEX_TYPE ||
                objectType == XSTypeDefinition.SIMPLE_TYPE) {
                fGlobalComponents[objectType] = new XSNamedMap4Types(fNamespaces, tables, fGrammarCount, objectType);
            }
            else {
                fGlobalComponents[objectType] = new XSNamedMapImpl(fNamespaces, tables, fGrammarCount);
            }
        }

        return fGlobalComponents[objectType];
    }

    /**
     * Convenience method. Returns a list of top-level component declarations
     * that are defined within the specified namespace, i.e. element
     * declarations, attribute declarations, etc.
     * @param objectType The type of the declaration, i.e.
     *   <code>ELEMENT_DECLARATION</code>.
     * @param namespace The namespace to which the declaration belongs or
     *   <code>null</code> (for components with no target namespace).
     * @return  A list of top-level definitions of the specified type in
     *   <code>objectType</code> and defined in the specified
     *   <code>namespace</code> or an empty <code>XSNamedMap</code>.
     */
    public synchronized XSNamedMap getComponentsByNamespace(short objectType,
                                                            String namespace) {
        if (objectType <= 0 || objectType > MAX_COMP_IDX ||
            !GLOBAL_COMP[objectType]) {
            return XSNamedMapImpl.EMPTY_MAP;
        }

        // try to find the grammar
        int i = 0;
        if (namespace != null) {
            for (; i < fGrammarCount; ++i) {
                if (namespace.equals(fNamespaces[i]))
                    break;
            }
        }
        else {
            for (; i < fGrammarCount; ++i) {
                if (fNamespaces[i] == null)
                    break;
            }
        }
        if (i == fGrammarCount)
            return XSNamedMapImpl.EMPTY_MAP;

        // get the hashtable for this type of components
        if (fNSComponents[i][objectType] == null) {
            SymbolHash table = null;
            switch (objectType) {
            case XSConstants.TYPE_DEFINITION:
            case XSTypeDefinition.COMPLEX_TYPE:
            case XSTypeDefinition.SIMPLE_TYPE:
                table = fGrammarList[i].fGlobalTypeDecls;
                break;
            case XSConstants.ATTRIBUTE_DECLARATION:
                table = fGrammarList[i].fGlobalAttrDecls;
                break;
            case XSConstants.ELEMENT_DECLARATION:
                table = fGrammarList[i].fGlobalElemDecls;
                break;
            case XSConstants.ATTRIBUTE_GROUP:
                table = fGrammarList[i].fGlobalAttrGrpDecls;
                break;
            case XSConstants.MODEL_GROUP_DEFINITION:
                table = fGrammarList[i].fGlobalGroupDecls;
                break;
            case XSConstants.NOTATION_DECLARATION:
                table = fGrammarList[i].fGlobalNotationDecls;
                break;
            }

            // for complex/simple types, create a special implementation,
            // which take specific types out of the hash table
            if (objectType == XSTypeDefinition.COMPLEX_TYPE ||
                objectType == XSTypeDefinition.SIMPLE_TYPE) {
                fNSComponents[i][objectType] = new XSNamedMap4Types(namespace, table, objectType);
            }
            else {
                fNSComponents[i][objectType] = new XSNamedMapImpl(namespace, table);
            }
        }

        return fNSComponents[i][objectType];
    }

    /**
     * Convenience method. Returns a top-level simple or complex type
     * definition.
     * @param name The name of the definition.
     * @param namespace The namespace of the definition, otherwise null.
     * @return An <code>XSTypeDefinition</code> or null if such definition
     *   does not exist.
     */
    public XSTypeDefinition getTypeDefinition(String name,
                                              String namespace) {
        SchemaGrammar sg = (SchemaGrammar)fGrammarMap.get(null2EmptyString(namespace));
        if (sg == null)
            return null;
        return (XSTypeDefinition)sg.fGlobalTypeDecls.get(name);
    }

    /**
     * Convenience method. Returns a top-level attribute declaration.
     * @param name The name of the declaration.
     * @param namespace The namespace of the definition, otherwise null.
     * @return A top-level attribute declaration or null if such declaration
     *   does not exist.
     */
    public XSAttributeDeclaration getAttributeDeclaration(String name,
                                                   String namespace) {
        SchemaGrammar sg = (SchemaGrammar)fGrammarMap.get(null2EmptyString(namespace));
        if (sg == null)
            return null;
        return (XSAttributeDeclaration)sg.fGlobalAttrDecls.get(name);
    }

    /**
     * Convenience method. Returns a top-level element declaration.
     * @param name The name of the declaration.
     * @param namespace The namespace of the definition, otherwise null.
     * @return A top-level element declaration or null if such declaration
     *   does not exist.
     */
    public XSElementDeclaration getElementDeclaration(String name,
                                               String namespace) {
        SchemaGrammar sg = (SchemaGrammar)fGrammarMap.get(null2EmptyString(namespace));
        if (sg == null)
            return null;
        return (XSElementDeclaration)sg.fGlobalElemDecls.get(name);
    }

    /**
     * Convenience method. Returns a top-level attribute group definition.
     * @param name The name of the definition.
     * @param namespace The namespace of the definition, otherwise null.
     * @return A top-level attribute group definition or null if such
     *   definition does not exist.
     */
    public XSAttributeGroupDefinition getAttributeGroup(String name,
                                                        String namespace) {
        SchemaGrammar sg = (SchemaGrammar)fGrammarMap.get(null2EmptyString(namespace));
        if (sg == null)
            return null;
        return (XSAttributeGroupDefinition)sg.fGlobalAttrGrpDecls.get(name);
    }

    /**
     * Convenience method. Returns a top-level model group definition.
     *
     * @param name      The name of the definition.
     * @param namespace The namespace of the definition, otherwise null.
     * @return A top-level model group definition definition or null if such
     *         definition does not exist.
     */
    public XSModelGroupDefinition getModelGroupDefinition(String name,
                                                          String namespace) {
        SchemaGrammar sg = (SchemaGrammar)fGrammarMap.get(null2EmptyString(namespace));
        if (sg == null)
            return null;
        return (XSModelGroupDefinition)sg.fGlobalGroupDecls.get(name);
    }


    /**
     * @see com.sun.org.apache.xerces.internal.xs.XSModel#getNotationDeclaration(String, String)
     */
    public XSNotationDeclaration getNotationDeclaration(String name,
                                                 String namespace) {
        SchemaGrammar sg = (SchemaGrammar)fGrammarMap.get(null2EmptyString(namespace));
        if (sg == null)
            return null;
        return (XSNotationDeclaration)sg.fGlobalNotationDecls.get(name);
    }

    /**
     *  {annotations} A set of annotations.
     */
    public synchronized XSObjectList getAnnotations() {
        if(fAnnotations != null)
            return fAnnotations;

        // do this in two passes to avoid inaccurate array size
        int totalAnnotations = 0;
        for (int i = 0; i < fGrammarCount; i++) {
            totalAnnotations += fGrammarList[i].fNumAnnotations;
        }
        XSAnnotationImpl [] annotations = new XSAnnotationImpl [totalAnnotations];
        int currPos = 0;
        for (int i = 0; i < fGrammarCount; i++) {
            SchemaGrammar currGrammar = fGrammarList[i];
            if (currGrammar.fNumAnnotations > 0) {
                System.arraycopy(currGrammar.fAnnotations, 0, annotations, currPos, currGrammar.fNumAnnotations);
                currPos += currGrammar.fNumAnnotations;
            }
        }
        fAnnotations = new XSObjectListImpl(annotations, annotations.length);
        return fAnnotations;
    }

    private static final String null2EmptyString(String str) {
        return str == null ? XMLSymbols.EMPTY_STRING : str;
    }

    /**
     * REVISIT: to expose identity constraints from XSModel.
     * For now, we only expose whether there are any IDCs.
     * We also need to add these methods to the public
     * XSModel interface.
     */
    public boolean hasIDConstraints() {
        return fHasIDC;
    }

    /**
     * REVISIT: to expose substitution group of a given element.
     * We need to add this to the XSModel interface.
     */
    public XSObjectList getSubstitutionGroup(XSElementDeclaration head) {
        return (XSObjectList)fSubGroupMap.get(head);
    }

} // class XSModelImpl
