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

package com.sun.org.apache.xerces.internal.impl.xs;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * A class used to hold the internal schema grammar set for the current instance
 *
 * @xerces.internal
 *
 * @author Sandy Gao, IBM
 * @version $Id: XSGrammarBucket.java,v 1.7 2010-11-01 04:39:55 joehw Exp $
 */
public class XSGrammarBucket {

    // Data

    /**
     * Hashtable that maps between Namespace and a Grammar
     */
    Map<String, SchemaGrammar> fGrammarRegistry = new HashMap();
    SchemaGrammar fNoNSGrammar = null;

    /**
     * Get the schema grammar for the specified namespace
     *
     * @param namespace
     * @return SchemaGrammar associated with the namespace
     */
    public SchemaGrammar getGrammar(String namespace) {
        if (namespace == null)
            return fNoNSGrammar;
        return (SchemaGrammar)fGrammarRegistry.get(namespace);
    }

    /**
     * Put a schema grammar into the registry
     * This method is for internal use only: it assumes that a grammar with
     * the same target namespace is not already in the bucket.
     *
     * @param grammar   the grammar to put in the registry
     */
    public void putGrammar(SchemaGrammar grammar) {
        if (grammar.getTargetNamespace() == null)
            fNoNSGrammar = grammar;
        else
            fGrammarRegistry.put(grammar.getTargetNamespace(), grammar);
    }

    /**
     * put a schema grammar and any grammars imported by it (directly or
     * inderectly) into the registry. when a grammar with the same target
     * namespace is already in the bucket, and different from the one being
     * added, it's an error, and no grammar will be added into the bucket.
     *
     * @param grammar   the grammar to put in the registry
     * @param deep      whether to add imported grammars
     * @return          whether the process succeeded
     */
    public boolean putGrammar(SchemaGrammar grammar, boolean deep) {
        // whether there is one with the same tns
        SchemaGrammar sg = getGrammar(grammar.fTargetNamespace);
        if (sg != null) {
            // if the one we have is different from the one passed, it's an error
            return sg == grammar;
        }
        // not deep import, then just add this one grammar
        if (!deep) {
            putGrammar(grammar);
            return true;
        }

        // get all imported grammars, and make a copy of the Vector, so that
        // we can recursively process the grammars, and add distinct ones
        // to the same vector
        Vector currGrammars = (Vector)grammar.getImportedGrammars();
        if (currGrammars == null) {
            putGrammar(grammar);
            return true;
        }

        Vector grammars = ((Vector)currGrammars.clone());
        SchemaGrammar sg1, sg2;
        Vector gs;
        // for all (recursively) imported grammars
        for (int i = 0; i < grammars.size(); i++) {
            // get the grammar
            sg1 = (SchemaGrammar)grammars.elementAt(i);
            // check whether the bucket has one with the same tns
            sg2 = getGrammar(sg1.fTargetNamespace);
            if (sg2 == null) {
                // we need to add grammars imported by sg1 too
                gs = sg1.getImportedGrammars();
                // for all grammars imported by sg2, but not in the vector
                // we add them to the vector
                if(gs == null) continue;
                for (int j = gs.size() - 1; j >= 0; j--) {
                    sg2 = (SchemaGrammar)gs.elementAt(j);
                    if (!grammars.contains(sg2))
                        grammars.addElement(sg2);
                }
            }
            // we found one with the same target namespace
            // if the two grammars are not the same object, then it's an error
            else if (sg2 != sg1) {
                return false;
            }
        }

        // now we have all imported grammars stored in the vector. add them
        putGrammar(grammar);
        for (int i = grammars.size() - 1; i >= 0; i--)
            putGrammar((SchemaGrammar)grammars.elementAt(i));

        return true;
    }

    /**
     * put a schema grammar and any grammars imported by it (directly or
     * inderectly) into the registry. when a grammar with the same target
     * namespace is already in the bucket, and different from the one being
     * added, no grammar will be added into the bucket.
     *
     * @param grammar        the grammar to put in the registry
     * @param deep           whether to add imported grammars
     * @param ignoreConflict whether to ignore grammars that already exist in the grammar
     *                       bucket or not - including 'grammar' parameter.
     * @return               whether the process succeeded
     */
    public boolean putGrammar(SchemaGrammar grammar, boolean deep, boolean ignoreConflict) {
        if (!ignoreConflict) {
            return putGrammar(grammar, deep);
        }

        // if grammar already exist in the bucket, we ignore the request
        SchemaGrammar sg = getGrammar(grammar.fTargetNamespace);
        if (sg == null) {
            putGrammar(grammar);
        }

        // not adding the imported grammars
        if (!deep) {
            return true;
        }

        // get all imported grammars, and make a copy of the Vector, so that
        // we can recursively process the grammars, and add distinct ones
        // to the same vector
        Vector currGrammars = (Vector)grammar.getImportedGrammars();
        if (currGrammars == null) {
            return true;
        }

        Vector grammars = ((Vector)currGrammars.clone());
        SchemaGrammar sg1, sg2;
        Vector gs;
        // for all (recursively) imported grammars
        for (int i = 0; i < grammars.size(); i++) {
            // get the grammar
            sg1 = (SchemaGrammar)grammars.elementAt(i);
            // check whether the bucket has one with the same tns
            sg2 = getGrammar(sg1.fTargetNamespace);
            if (sg2 == null) {
                // we need to add grammars imported by sg1 too
                gs = sg1.getImportedGrammars();
                // for all grammars imported by sg2, but not in the vector
                // we add them to the vector
                if(gs == null) continue;
                for (int j = gs.size() - 1; j >= 0; j--) {
                    sg2 = (SchemaGrammar)gs.elementAt(j);
                    if (!grammars.contains(sg2))
                        grammars.addElement(sg2);
                }
            }
            // we found one with the same target namespace, ignore it
            else  {
                grammars.remove(sg1);
            }
        }

        // now we have all imported grammars stored in the vector. add them
        for (int i = grammars.size() - 1; i >= 0; i--) {
            putGrammar((SchemaGrammar)grammars.elementAt(i));
        }

        return true;
    }

    /**
     * get all grammars in the registry
     *
     * @return an array of SchemaGrammars.
     */
    public SchemaGrammar[] getGrammars() {
        // get the number of grammars
        int count = fGrammarRegistry.size() + (fNoNSGrammar==null ? 0 : 1);
        SchemaGrammar[] grammars = new SchemaGrammar[count];
        // get grammars with target namespace
        int i = 0;
        for(Map.Entry<String, SchemaGrammar> entry : fGrammarRegistry.entrySet()){
            grammars[i++] = entry.getValue();
        }

        // add the grammar without target namespace, if any
        if (fNoNSGrammar != null)
            grammars[count-1] = fNoNSGrammar;
        return grammars;
    }

    /**
     * Clear the registry.
     * REVISIT: update to use another XSGrammarBucket
     */
    public void reset() {
        fNoNSGrammar = null;
        fGrammarRegistry.clear();
    }

} // class XSGrammarBucket
