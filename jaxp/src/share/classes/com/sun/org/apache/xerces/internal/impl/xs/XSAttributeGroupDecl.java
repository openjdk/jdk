/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2001-2004 The Apache Software Foundation.
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

import com.sun.org.apache.xerces.internal.impl.dv.ValidatedInfo;
import com.sun.org.apache.xerces.internal.xs.XSAnnotation;
import com.sun.org.apache.xerces.internal.xs.XSAttributeGroupDefinition;
import com.sun.org.apache.xerces.internal.xs.XSAttributeUse;
import com.sun.org.apache.xerces.internal.xs.XSConstants;
import com.sun.org.apache.xerces.internal.xs.XSNamespaceItem;
import com.sun.org.apache.xerces.internal.xs.XSObjectList;
import com.sun.org.apache.xerces.internal.xs.XSWildcard;
import com.sun.org.apache.xerces.internal.impl.xs.util.XSObjectListImpl;

/**
 * The XML representation for an attribute group declaration
 * schema component is a global <attributeGroup> element information item
 *
 * @xerces.internal
 *
 * @author Sandy Gao, IBM
 * @author Rahul Srivastava, Sun Microsystems Inc.
 *
 */
public class XSAttributeGroupDecl implements XSAttributeGroupDefinition {

    // name of the attribute group
    public String fName = null;
    // target namespace of the attribute group
    public String fTargetNamespace = null;
    // number of attribute uses included by this attribute group
    int fAttrUseNum = 0;
    // attribute uses included by this attribute group
    private static final int INITIAL_SIZE = 5;
    XSAttributeUseImpl[] fAttributeUses = new XSAttributeUseImpl[INITIAL_SIZE];
    // attribute wildcard included by this attribute group
    public XSWildcardDecl fAttributeWC = null;
    // whether there is an attribute use whose type is or is derived from ID.
    public String fIDAttrName = null;

    // optional annotation
    public XSAnnotationImpl fAnnotation;

    protected XSObjectListImpl fAttrUses = null;

    // add an attribute use
    // if the type is derived from ID, but there is already another attribute
    // use of type ID, then return the name of the other attribute use;
    // otherwise, return null
    public String addAttributeUse(XSAttributeUseImpl attrUse) {

        if (fAttrUseNum == fAttributeUses.length) {
            fAttributeUses = resize(fAttributeUses, fAttrUseNum*2);
        }
        fAttributeUses[fAttrUseNum++] = attrUse;
        // if this attribute use is prohibited, then don't check whether it's
        // of type ID
        if (attrUse.fUse == SchemaSymbols.USE_PROHIBITED)
            return null;

        if (attrUse.fAttrDecl.fType.isIDType()) {
            // if there is already an attribute use of type ID, return it' sname
            if (fIDAttrName == null)
                fIDAttrName = attrUse.fAttrDecl.fName;
            else
                return fIDAttrName;
        }

        return null;
    }

    public XSAttributeUse getAttributeUse(String namespace, String name) {
        for (int i=0; i<fAttrUseNum; i++) {
            if ( (fAttributeUses[i].fAttrDecl.fTargetNamespace == namespace) &&
                 (fAttributeUses[i].fAttrDecl.fName == name) )
                return fAttributeUses[i];
        }

        return null;
    }

    public void removeProhibitedAttrs() {
        if (fAttrUseNum == 0) return;
        int pCount = 0;
        XSAttributeUseImpl[] pUses = new XSAttributeUseImpl[fAttrUseNum];
        for (int i = 0; i < fAttrUseNum; i++) {
            if (fAttributeUses[i].fUse == SchemaSymbols.USE_PROHIBITED) {
                pCount++;
                // we use the entries at the end, so that we can use the
                // first entries to store non-prohibited attribute uses,
                // hence avoid creating a new array.
                pUses[fAttrUseNum-pCount] = fAttributeUses[i];
            }
        }

        int newCount = 0;
        if (pCount > 0) {
            OUTER: for (int i = 0; i < fAttrUseNum; i++) {
                if (fAttributeUses[i].fUse == SchemaSymbols.USE_PROHIBITED)
                    continue;
                for (int j = 1; j <= pCount; j++) {
                    if (fAttributeUses[i].fAttrDecl.fName == pUses[fAttrUseNum-pCount].fAttrDecl.fName &&
                        fAttributeUses[i].fAttrDecl.fTargetNamespace == pUses[fAttrUseNum-pCount].fAttrDecl.fTargetNamespace) {
                        continue OUTER;
                    }
                }
                pUses[newCount++] = fAttributeUses[i];
            }
            fAttributeUses = pUses;
            fAttrUseNum = newCount;
        }
    }

    /**
     * Check that the attributes in this group validly restrict those from a base group.
     * If an error is found, an Object[] is returned. This contains the arguments for the error message
     * describing the error. The last element in the array (at index arr.length - 1) is the the error code.
     * Returns null if there is no error.
     *
     * REVISIT: is there a better way of returning the appropriate information for the error?
     *
     * @param typeName the name of the type containing this attribute group, used for error reporting purposes
     * @param baseGroup the XSAttributeGroupDecl that is the base we are checking against
     */
    public Object[] validRestrictionOf(String typeName, XSAttributeGroupDecl baseGroup) {

        Object[] errorArgs = null;
        XSAttributeUseImpl attrUse = null;
        XSAttributeDecl attrDecl = null;
        XSAttributeUseImpl baseAttrUse = null;
        XSAttributeDecl baseAttrDecl = null;

        for (int i=0; i<fAttrUseNum; i++) {

            attrUse = fAttributeUses[i];
            attrDecl = attrUse.fAttrDecl;

            // Look for a match in the base
            baseAttrUse = (XSAttributeUseImpl)baseGroup.getAttributeUse(attrDecl.fTargetNamespace,attrDecl.fName);
            if (baseAttrUse != null) {
                //
                // derivation-ok-restriction.  Constraint 2.1.1
                //

                if (baseAttrUse.getRequired() && !attrUse.getRequired()) {
                    errorArgs = new Object[]{typeName, attrDecl.fName,
                                             attrUse.fUse == SchemaSymbols.USE_OPTIONAL ? SchemaSymbols.ATTVAL_OPTIONAL : SchemaSymbols.ATTVAL_PROHIBITED,
                                             "derivation-ok-restriction.2.1.1"};
                    return errorArgs;
                }

                // if this attribute is prohibited in the derived type, don't
                // need to check any of the following constraints.
                if (attrUse.fUse == SchemaSymbols.USE_PROHIBITED) {
                    continue;
                }

                baseAttrDecl = baseAttrUse.fAttrDecl;
                //
                // derivation-ok-restriction.  Constraint 2.1.1
                //
                if (! XSConstraints.checkSimpleDerivationOk(attrDecl.fType,
                                                            baseAttrDecl.fType,
                                                            baseAttrDecl.fType.getFinal()) ) {
                                        errorArgs = new Object[]{typeName, attrDecl.fName, attrDecl.fType.getName(),
                                                                     baseAttrDecl.fType.getName(), "derivation-ok-restriction.2.1.2"};
                                        return errorArgs;
                }


                //
                // derivation-ok-restriction.  Constraint 2.1.3
                //
                int baseConsType=baseAttrUse.fConstraintType!=XSConstants.VC_NONE?
                                 baseAttrUse.fConstraintType:baseAttrDecl.getConstraintType();
                int thisConstType = attrUse.fConstraintType!=XSConstants.VC_NONE?
                                    attrUse.fConstraintType:attrDecl.getConstraintType();

                if (baseConsType == XSConstants.VC_FIXED) {

                    if (thisConstType != XSConstants.VC_FIXED) {
                                                errorArgs = new Object[]{typeName, attrDecl.fName,
                                                                                                 "derivation-ok-restriction.2.1.3.a"};
                                                return errorArgs;
                    } else {
                        // check the values are the same.
                        ValidatedInfo baseFixedValue=(baseAttrUse.fDefault!=null ?
                                                      baseAttrUse.fDefault: baseAttrDecl.fDefault);
                        ValidatedInfo thisFixedValue=(attrUse.fDefault!=null ?
                                                      attrUse.fDefault: attrDecl.fDefault);
                        if (!baseFixedValue.actualValue.equals(thisFixedValue.actualValue)) {
                                                        errorArgs = new Object[]{typeName, attrDecl.fName, thisFixedValue.stringValue(),
                                                                                                         baseFixedValue.stringValue(), "derivation-ok-restriction.2.1.3.b"};
                                                        return errorArgs;
                        }

                    }

                }
            } else {
                // No matching attribute in base - there should be a matching wildcard

                //
                // derivation-ok-restriction.  Constraint 2.2
                //
                if (baseGroup.fAttributeWC == null) {
                                        errorArgs = new Object[]{typeName, attrDecl.fName,
                                                                                         "derivation-ok-restriction.2.2.a"};
                                        return errorArgs;
                }
                else if (!baseGroup.fAttributeWC.allowNamespace(attrDecl.fTargetNamespace)) {
                                        errorArgs = new Object[]{typeName, attrDecl.fName,
                                             attrDecl.fTargetNamespace==null?"":attrDecl.fTargetNamespace,
                                                                                         "derivation-ok-restriction.2.2.b"};
                                        return errorArgs;
                }
            }
        }

        //
        // Check that any REQUIRED attributes in the base have matching attributes
        // in this group
        // derivation-ok-restriction.  Constraint 3
        //
        for (int i=0; i<baseGroup.fAttrUseNum; i++) {

            baseAttrUse = baseGroup.fAttributeUses[i];

            if (baseAttrUse.fUse == SchemaSymbols.USE_REQUIRED) {

                baseAttrDecl = baseAttrUse.fAttrDecl;
                // Look for a match in this group
                if (getAttributeUse(baseAttrDecl.fTargetNamespace,baseAttrDecl.fName) == null) {
                                        errorArgs = new Object[]{typeName, baseAttrUse.fAttrDecl.fName,
                                                                                         "derivation-ok-restriction.3"};
                                        return errorArgs;
                }
            }
        }


        // Now, check wildcards
        //
        // derivation-ok-restriction.  Constraint 4
        //
        if (fAttributeWC != null) {
            if (baseGroup.fAttributeWC == null) {
                                errorArgs = new Object[]{typeName, "derivation-ok-restriction.4.1"};
                                return errorArgs;
            }
            if (! fAttributeWC.isSubsetOf(baseGroup.fAttributeWC)) {
                                errorArgs = new Object[]{typeName, "derivation-ok-restriction.4.2"};
                                return errorArgs;
            }
            if (fAttributeWC.weakerProcessContents(baseGroup.fAttributeWC)) {
                                errorArgs = new Object[]{typeName,
                                                                                 fAttributeWC.getProcessContentsAsString(),
                                                                                 baseGroup.fAttributeWC.getProcessContentsAsString(),
                                                                                 "derivation-ok-restriction.4.3"};
                                return errorArgs;
            }
        }

        return null;

    }

    static final XSAttributeUseImpl[] resize(XSAttributeUseImpl[] oldArray, int newSize) {
        XSAttributeUseImpl[] newArray = new XSAttributeUseImpl[newSize];
        System.arraycopy(oldArray, 0, newArray, 0, Math.min(oldArray.length, newSize));
        return newArray;
    }

    // reset the attribute group declaration
    public void reset(){
        fName = null;
        fTargetNamespace = null;
        // reset attribute uses
        for (int i=0;i<fAttrUseNum;i++) {
            fAttributeUses[i] = null;
        }
        fAttrUseNum = 0;
        fAttributeWC = null;
        fAnnotation = null;
        fIDAttrName = null;

    }

    /**
     * Get the type of the object, i.e ELEMENT_DECLARATION.
     */
    public short getType() {
        return XSConstants.ATTRIBUTE_GROUP;
    }

    /**
     * The <code>name</code> of this <code>XSObject</code> depending on the
     * <code>XSObject</code> type.
     */
    public String getName() {
        return fName;
    }

    /**
     * The namespace URI of this node, or <code>null</code> if it is
     * unspecified.  defines how a namespace URI is attached to schema
     * components.
     */
    public String getNamespace() {
        return fTargetNamespace;
    }

    /**
     * {attribute uses} A set of attribute uses.
     */
    public XSObjectList getAttributeUses() {
        if (fAttrUses == null){
            fAttrUses = new XSObjectListImpl(fAttributeUses, fAttrUseNum);
        }
        return fAttrUses;
    }

    /**
     * {attribute wildcard} Optional. A wildcard.
     */
    public XSWildcard getAttributeWildcard() {
        return fAttributeWC;
    }

    /**
     * Optional. Annotation.
     */
    public XSAnnotation getAnnotation() {
        return fAnnotation;
    }

        /**
         * @see com.sun.org.apache.xerces.internal.xs.XSObject#getNamespaceItem()
         */
        public XSNamespaceItem getNamespaceItem() {
        //REVISIT: implement
                return null;
        }

} // class XSAttributeGroupDecl
