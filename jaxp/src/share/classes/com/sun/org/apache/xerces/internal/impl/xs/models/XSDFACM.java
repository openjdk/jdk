/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2004 The Apache Software Foundation.
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

package com.sun.org.apache.xerces.internal.impl.xs.models;

import com.sun.org.apache.xerces.internal.xni.QName;
import com.sun.org.apache.xerces.internal.impl.dtd.models.CMNode;
import com.sun.org.apache.xerces.internal.impl.dtd.models.CMStateSet;
import com.sun.org.apache.xerces.internal.impl.xs.SubstitutionGroupHandler;
import com.sun.org.apache.xerces.internal.impl.xs.XSElementDecl;
import com.sun.org.apache.xerces.internal.impl.xs.XSParticleDecl;
import com.sun.org.apache.xerces.internal.impl.xs.XSModelGroupImpl;
import com.sun.org.apache.xerces.internal.impl.xs.XSWildcardDecl;
import com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaException;
import com.sun.org.apache.xerces.internal.impl.xs.XSConstraints;

import java.util.Vector;

/**
 * DFAContentModel is the implementation of XSCMValidator that does
 * all of the non-trivial element content validation. This class does
 * the conversion from the regular expression to the DFA that
 * it then uses in its validation algorithm.
 *
 * @xerces.internal
 *
 * @author Neil Graham, IBM
 */
public class XSDFACM
    implements XSCMValidator {

    //
    // Constants
    //
    private static final boolean DEBUG = false;

    // special strings

    // debugging

    /** Set to true to debug content model validation. */
    private static final boolean DEBUG_VALIDATE_CONTENT = false;

    //
    // Data
    //

    /**
     * This is the map of unique input symbol elements to indices into
     * each state's per-input symbol transition table entry. This is part
     * of the built DFA information that must be kept around to do the
     * actual validation.  Note tat since either XSElementDecl or XSParticleDecl object
     * can live here, we've got to use an Object.
     */
    private Object fElemMap[] = null;

    /**
     * This is a map of whether the element map contains information
     * related to ANY models.
     */
    private int fElemMapType[] = null;

    /**
     * id of the unique input symbol
     */
    private int fElemMapId[] = null;

    /** The element map size. */
    private int fElemMapSize = 0;

    /**
     * This is an array of booleans, one per state (there are
     * fTransTableSize states in the DFA) that indicates whether that
     * state is a final state.
     */
    private boolean fFinalStateFlags[] = null;

    /**
     * The list of follow positions for each NFA position (i.e. for each
     * non-epsilon leaf node.) This is only used during the building of
     * the DFA, and is let go afterwards.
     */
    private CMStateSet fFollowList[] = null;

    /**
     * This is the head node of our intermediate representation. It is
     * only non-null during the building of the DFA (just so that it
     * does not have to be passed all around.) Once the DFA is built,
     * this is no longer required so its nulled out.
     */
    private CMNode fHeadNode = null;

    /**
     * The count of leaf nodes. This is an important number that set some
     * limits on the sizes of data structures in the DFA process.
     */
    private int fLeafCount = 0;

    /**
     * An array of non-epsilon leaf nodes, which is used during the DFA
     * build operation, then dropped.
     */
    private XSCMLeaf fLeafList[] = null;

    /** Array mapping ANY types to the leaf list. */
    private int fLeafListType[] = null;

    /**
     * This is the transition table that is the main by product of all
     * of the effort here. It is an array of arrays of ints. The first
     * dimension is the number of states we end up with in the DFA. The
     * second dimensions is the number of unique elements in the content
     * model (fElemMapSize). Each entry in the second dimension indicates
     * the new state given that input for the first dimension's start
     * state.
     * <p>
     * The fElemMap array handles mapping from element indexes to
     * positions in the second dimension of the transition table.
     */
    private int fTransTable[][] = null;

    /**
     * The number of valid entries in the transition table, and in the other
     * related tables such as fFinalStateFlags.
     */
    private int fTransTableSize = 0;

    private int fOneTransitionCounter = 0;

    private Object fUserData;

    // temp variables

    //
    // Constructors
    //

    /**
     * Constructs a DFA content model.
     *
     * @param syntaxTree    The syntax tree of the content model.
     * @param leafCount     The number of leaves.
     *
     * @exception RuntimeException Thrown if DFA can't be built.
     */

   public XSDFACM(CMNode syntaxTree, int leafCount) {

        // Store away our index and pools in members
        fLeafCount = leafCount;

        fUserData = syntaxTree.getUserData();

        //
        //  Create some string pool indexes that represent the names of some
        //  magical nodes in the syntax tree.
        //  (already done in static initialization...
        //

        //
        //  Ok, so lets grind through the building of the DFA. This method
        //  handles the high level logic of the algorithm, but it uses a
        //  number of helper classes to do its thing.
        //
        //  In order to avoid having hundreds of references to the error and
        //  string handlers around, this guy and all of his helper classes
        //  just throw a simple exception and we then pass it along.
        //

        if(DEBUG_VALIDATE_CONTENT) {
            XSDFACM.time -= System.currentTimeMillis();
        }

        buildDFA(syntaxTree);

        if(DEBUG_VALIDATE_CONTENT) {
            XSDFACM.time += System.currentTimeMillis();
            System.out.println("DFA build: " + XSDFACM.time + "ms");
        }
    }

    private static long time = 0;

    //
    // XSCMValidator methods
    //

    /**
     * Return the number of times the <code>oneTransition()</code> method
     * was called, resulting on the DFA to move into a non-error state.
     * This is used to check the minOccurs and maxOccurs bounds using a
     * constant space algorithm.
     */
    public int getOneTransitionCounter() {
        return fOneTransitionCounter;
    }

    /**
     * Allows the user to get arbitrary data originally set on the content
     * model node used to create this DFA.
     */
    public Object getUserData() {
        return fUserData;
    }

    /**
     * check whether the given state is one of the final states
     *
     * @param state       the state to check
     *
     * @return whether it's a final state
     */
    public boolean isFinalState (int state) {
        return (state < 0)? false :
            fFinalStateFlags[state];
    }

    /**
     * one transition only
     *
     * @param curElem The current element's QName
     * @param state stack to store the previous state
     * @param subGroupHandler the substitution group handler
     *
     * @return  null if transition is invalid; otherwise the Object corresponding to the
     *      XSElementDecl or XSWildcardDecl identified.  Also, the
     *      state array will be modified to include the new state; this so that the validator can
     *      store it away.
     *
     * @exception RuntimeException thrown on error
     */
    public Object oneTransition(QName curElem, int[] state, SubstitutionGroupHandler subGroupHandler) {
        int curState = state[0];

        if(curState == XSCMValidator.FIRST_ERROR || curState == XSCMValidator.SUBSEQUENT_ERROR) {
            // there was an error last time; so just go find correct Object in fElemmMap.
            // ... after resetting state[0].
            if(curState == XSCMValidator.FIRST_ERROR)
                state[0] = XSCMValidator.SUBSEQUENT_ERROR;

            return findMatchingDecl(curElem, subGroupHandler);
        }

        int nextState = 0;
        int elemIndex = 0;
        Object matchingDecl = null;

        for (; elemIndex < fElemMapSize; elemIndex++) {
            nextState = fTransTable[curState][elemIndex];
            if (nextState == -1)
                continue;
            int type = fElemMapType[elemIndex] ;
            if (type == XSParticleDecl.PARTICLE_ELEMENT) {
                matchingDecl = subGroupHandler.getMatchingElemDecl(curElem, (XSElementDecl)fElemMap[elemIndex]);
                if (matchingDecl != null) {
                    break;
                }
            }
            else if (type == XSParticleDecl.PARTICLE_WILDCARD) {
                if(((XSWildcardDecl)fElemMap[elemIndex]).allowNamespace(curElem.uri)) {
                    matchingDecl = fElemMap[elemIndex];
                    break;
                }
            }
        }

        // if we still can't find a match, set the state to first_error
        // and return null
        if (elemIndex == fElemMapSize) {
            state[1] = state[0];
            state[0] = XSCMValidator.FIRST_ERROR;
            return findMatchingDecl(curElem, subGroupHandler);
        }

        fOneTransitionCounter++;
        state[0] = nextState;
        return matchingDecl;
    } // oneTransition(QName, int[], SubstitutionGroupHandler):  Object

    Object findMatchingDecl(QName curElem, SubstitutionGroupHandler subGroupHandler) {
        Object matchingDecl = null;

        for (int elemIndex = 0; elemIndex < fElemMapSize; elemIndex++) {
            int type = fElemMapType[elemIndex] ;
            if (type == XSParticleDecl.PARTICLE_ELEMENT) {
                matchingDecl = subGroupHandler.getMatchingElemDecl(curElem, (XSElementDecl)fElemMap[elemIndex]);
                if (matchingDecl != null) {
                    return matchingDecl;
                }
            }
            else if (type == XSParticleDecl.PARTICLE_WILDCARD) {
                if(((XSWildcardDecl)fElemMap[elemIndex]).allowNamespace(curElem.uri))
                    return fElemMap[elemIndex];
            }
        }

        return null;
    }

    // This method returns the start states of the content model.
    public int[] startContentModel() {
        int[] val = new int[2];
        val[0] = 0;
        fOneTransitionCounter = 0;      // reset transition counter
        return val;
    } // startContentModel():int[]

    // this method returns whether the last state was a valid final state
    public boolean endContentModel(int[] state) {
        return fFinalStateFlags[state[0]];
    } // endContentModel(int[]):  boolean

    // Killed off whatCanGoHere; we may need it for DOM canInsert(...) etc.,
    // but we can put it back later.

    //
    // Private methods
    //

    /**
     * Builds the internal DFA transition table from the given syntax tree.
     *
     * @param syntaxTree The syntax tree.
     *
     * @exception RuntimeException Thrown if DFA cannot be built.
     */
    private void buildDFA(CMNode syntaxTree) {
        //
        //  The first step we need to take is to rewrite the content model
        //  using our CMNode objects, and in the process get rid of any
        //  repetition short cuts, converting them into '*' style repetitions
        //  or getting rid of repetitions altogether.
        //
        //  The conversions done are:
        //
        //  x+ -> (x|x*)
        //  x? -> (x|epsilon)
        //
        //  This is a relatively complex scenario. What is happening is that
        //  we create a top level binary node of which the special EOC value
        //  is set as the right side node. The the left side is set to the
        //  rewritten syntax tree. The source is the original content model
        //  info from the decl pool. The rewrite is done by buildSyntaxTree()
        //  which recurses the decl pool's content of the element and builds
        //  a new tree in the process.
        //
        //  Note that, during this operation, we set each non-epsilon leaf
        //  node's DFA state position and count the number of such leafs, which
        //  is left in the fLeafCount member.
        //
        //  The nodeTmp object is passed in just as a temp node to use during
        //  the recursion. Otherwise, we'd have to create a new node on every
        //  level of recursion, which would be piggy in Java (as is everything
        //  for that matter.)
        //

        /* MODIFIED (Jan, 2001)
         *
         * Use following rules.
         *   nullable(x+) := nullable(x), first(x+) := first(x),  last(x+) := last(x)
         *   nullable(x?) := true, first(x?) := first(x),  last(x?) := last(x)
         *
         * The same computation of follow as x* is applied to x+
         *
         * The modification drastically reduces computation time of
         * "(a, (b, a+, (c, (b, a+)+, a+, (d,  (c, (b, a+)+, a+)+, (b, a+)+, a+)+)+)+)+"
         */

        //
        //  And handle specially the EOC node, which also must be numbered
        //  and counted as a non-epsilon leaf node. It could not be handled
        //  in the above tree build because it was created before all that
        //  started. We save the EOC position since its used during the DFA
        //  building loop.
        //
        int EOCPos = fLeafCount;
        XSCMLeaf nodeEOC = new XSCMLeaf(XSParticleDecl.PARTICLE_ELEMENT, null, -1, fLeafCount++);
        fHeadNode = new XSCMBinOp(
            XSModelGroupImpl.MODELGROUP_SEQUENCE,
            syntaxTree,
            nodeEOC
        );

        //
        //  Ok, so now we have to iterate the new tree and do a little more
        //  work now that we know the leaf count. One thing we need to do is
        //  to calculate the first and last position sets of each node. This
        //  is cached away in each of the nodes.
        //
        //  Along the way we also set the leaf count in each node as the
        //  maximum state count. They must know this in order to create their
        //  first/last pos sets.
        //
        //  We also need to build an array of references to the non-epsilon
        //  leaf nodes. Since we iterate it in the same way as before, this
        //  will put them in the array according to their position values.
        //
        fLeafList = new XSCMLeaf[fLeafCount];
        fLeafListType = new int[fLeafCount];
        postTreeBuildInit(fHeadNode);

        //
        //  And, moving onward... We now need to build the follow position
        //  sets for all the nodes. So we allocate an array of state sets,
        //  one for each leaf node (i.e. each DFA position.)
        //
        fFollowList = new CMStateSet[fLeafCount];
        for (int index = 0; index < fLeafCount; index++)
            fFollowList[index] = new CMStateSet(fLeafCount);
        calcFollowList(fHeadNode);
        //
        //  And finally the big push... Now we build the DFA using all the
        //  states and the tree we've built up. First we set up the various
        //  data structures we are going to use while we do this.
        //
        //  First of all we need an array of unique element names in our
        //  content model. For each transition table entry, we need a set of
        //  contiguous indices to represent the transitions for a particular
        //  input element. So we need to a zero based range of indexes that
        //  map to element types. This element map provides that mapping.
        //
        fElemMap = new Object[fLeafCount];
        fElemMapType = new int[fLeafCount];
        fElemMapId = new int[fLeafCount];
        fElemMapSize = 0;
        for (int outIndex = 0; outIndex < fLeafCount; outIndex++) {
            // optimization from Henry Zongaro:
            //fElemMap[outIndex] = new Object ();
            fElemMap[outIndex] = null;

            int inIndex = 0;
            final int id = fLeafList[outIndex].getParticleId();
            for (; inIndex < fElemMapSize; inIndex++) {
                if (id == fElemMapId[inIndex])
                    break;
            }

            // If it was not in the list, then add it, if not the EOC node
            if (inIndex == fElemMapSize) {
                fElemMap[fElemMapSize] = fLeafList[outIndex].getLeaf();
                fElemMapType[fElemMapSize] = fLeafListType[outIndex];
                fElemMapId[fElemMapSize] = id;
                fElemMapSize++;
            }
        }

        // the last entry in the element map must be the EOC element.
        // remove it from the map.
        if (DEBUG) {
            if (fElemMapId[fElemMapSize-1] != -1)
                System.err.println("interal error in DFA: last element is not EOC.");
        }
        fElemMapSize--;

        /***
         * Optimization(Jan, 2001); We sort fLeafList according to
         * elemIndex which is *uniquely* associated to each leaf.
         * We are *assuming* that each element appears in at least one leaf.
         **/

        int[] fLeafSorter = new int[fLeafCount + fElemMapSize];
        int fSortCount = 0;

        for (int elemIndex = 0; elemIndex < fElemMapSize; elemIndex++) {
            final int id = fElemMapId[elemIndex];
            for (int leafIndex = 0; leafIndex < fLeafCount; leafIndex++) {
                if (id == fLeafList[leafIndex].getParticleId())
                    fLeafSorter[fSortCount++] = leafIndex;
            }
            fLeafSorter[fSortCount++] = -1;
        }

        /* Optimization(Jan, 2001) */

        //
        //  Next lets create some arrays, some that hold transient
        //  information during the DFA build and some that are permament.
        //  These are kind of sticky since we cannot know how big they will
        //  get, but we don't want to use any Java collections because of
        //  performance.
        //
        //  Basically they will probably be about fLeafCount*2 on average,
        //  but can be as large as 2^(fLeafCount*2), worst case. So we start
        //  with fLeafCount*4 as a middle ground. This will be very unlikely
        //  to ever have to expand, though it if does, the overhead will be
        //  somewhat ugly.
        //
        int curArraySize = fLeafCount * 4;
        CMStateSet[] statesToDo = new CMStateSet[curArraySize];
        fFinalStateFlags = new boolean[curArraySize];
        fTransTable = new int[curArraySize][];

        //
        //  Ok we start with the initial set as the first pos set of the
        //  head node (which is the seq node that holds the content model
        //  and the EOC node.)
        //
        CMStateSet setT = fHeadNode.firstPos();

        //
        //  Init our two state flags. Basically the unmarked state counter
        //  is always chasing the current state counter. When it catches up,
        //  that means we made a pass through that did not add any new states
        //  to the lists, at which time we are done. We could have used a
        //  expanding array of flags which we used to mark off states as we
        //  complete them, but this is easier though less readable maybe.
        //
        int unmarkedState = 0;
        int curState = 0;

        //
        //  Init the first transition table entry, and put the initial state
        //  into the states to do list, then bump the current state.
        //
        fTransTable[curState] = makeDefStateList();
        statesToDo[curState] = setT;
        curState++;

        /* Optimization(Jan, 2001); This is faster for
         * a large content model such as, "(t001+|t002+|.... |t500+)".
         */

        java.util.Hashtable stateTable = new java.util.Hashtable();

        /* Optimization(Jan, 2001) */

        //
        //  Ok, almost done with the algorithm... We now enter the
        //  loop where we go until the states done counter catches up with
        //  the states to do counter.
        //
        while (unmarkedState < curState) {
            //
            //  Get the first unmarked state out of the list of states to do.
            //  And get the associated transition table entry.
            //
            setT = statesToDo[unmarkedState];
            int[] transEntry = fTransTable[unmarkedState];

            // Mark this one final if it contains the EOC state
            fFinalStateFlags[unmarkedState] = setT.getBit(EOCPos);

            // Bump up the unmarked state count, marking this state done
            unmarkedState++;

            // Loop through each possible input symbol in the element map
            CMStateSet newSet = null;
            /* Optimization(Jan, 2001) */
            int sorterIndex = 0;
            /* Optimization(Jan, 2001) */
            for (int elemIndex = 0; elemIndex < fElemMapSize; elemIndex++) {
                //
                //  Build up a set of states which is the union of all of
                //  the follow sets of DFA positions that are in the current
                //  state. If we gave away the new set last time through then
                //  create a new one. Otherwise, zero out the existing one.
                //
                if (newSet == null)
                    newSet = new CMStateSet(fLeafCount);
                else
                    newSet.zeroBits();

                /* Optimization(Jan, 2001) */
                int leafIndex = fLeafSorter[sorterIndex++];

                while (leafIndex != -1) {
                    // If this leaf index (DFA position) is in the current set...
                    if (setT.getBit(leafIndex)) {
                        //
                        //  If this leaf is the current input symbol, then we
                        //  want to add its follow list to the set of states to
                        //  transition to from the current state.
                        //
                        newSet.union(fFollowList[leafIndex]);
                    }

                   leafIndex = fLeafSorter[sorterIndex++];
                }
                /* Optimization(Jan, 2001) */

                //
                //  If this new set is not empty, then see if its in the list
                //  of states to do. If not, then add it.
                //
                if (!newSet.isEmpty()) {
                    //
                    //  Search the 'states to do' list to see if this new
                    //  state set is already in there.
                    //

                    /* Optimization(Jan, 2001) */
                    Integer stateObj = (Integer)stateTable.get(newSet);
                    int stateIndex = (stateObj == null ? curState : stateObj.intValue());
                    /* Optimization(Jan, 2001) */

                    // If we did not find it, then add it
                    if (stateIndex == curState) {
                        //
                        //  Put this new state into the states to do and init
                        //  a new entry at the same index in the transition
                        //  table.
                        //
                        statesToDo[curState] = newSet;
                        fTransTable[curState] = makeDefStateList();

                        /* Optimization(Jan, 2001) */
                        stateTable.put(newSet, new Integer(curState));
                        /* Optimization(Jan, 2001) */

                        // We now have a new state to do so bump the count
                        curState++;

                        //
                        //  Null out the new set to indicate we adopted it.
                        //  This will cause the creation of a new set on the
                        //  next time around the loop.
                        //
                        newSet = null;
                    }

                    //
                    //  Now set this state in the transition table's entry
                    //  for this element (using its index), with the DFA
                    //  state we will move to from the current state when we
                    //  see this input element.
                    //
                    transEntry[elemIndex] = stateIndex;

                    // Expand the arrays if we're full
                    if (curState == curArraySize) {
                        //
                        //  Yikes, we overflowed the initial array size, so
                        //  we've got to expand all of these arrays. So adjust
                        //  up the size by 50% and allocate new arrays.
                        //
                        final int newSize = (int)(curArraySize * 1.5);
                        CMStateSet[] newToDo = new CMStateSet[newSize];
                        boolean[] newFinalFlags = new boolean[newSize];
                        int[][] newTransTable = new int[newSize][];

                        // Copy over all of the existing content
                        for (int expIndex = 0; expIndex < curArraySize; expIndex++) {
                            newToDo[expIndex] = statesToDo[expIndex];
                            newFinalFlags[expIndex] = fFinalStateFlags[expIndex];
                            newTransTable[expIndex] = fTransTable[expIndex];
                        }

                        // Store the new array size
                        curArraySize = newSize;
                        statesToDo = newToDo;
                        fFinalStateFlags = newFinalFlags;
                        fTransTable = newTransTable;
                    }
                }
            }
        }

        //
        //  And now we can say bye bye to the temp representation since we've
        //  built the DFA.
        //
        if (DEBUG_VALIDATE_CONTENT)
            dumpTree(fHeadNode, 0);
        fHeadNode = null;
        fLeafList = null;
        fFollowList = null;
        fLeafListType = null;
        fElemMapId = null;
    }

    /**
     * Calculates the follow list of the current node.
     *
     * @param nodeCur The curent node.
     *
     * @exception RuntimeException Thrown if follow list cannot be calculated.
     */
    private void calcFollowList(CMNode nodeCur) {
        // Recurse as required
        if (nodeCur.type() == XSModelGroupImpl.MODELGROUP_CHOICE) {
            // Recurse only
            calcFollowList(((XSCMBinOp)nodeCur).getLeft());
            calcFollowList(((XSCMBinOp)nodeCur).getRight());
        }
         else if (nodeCur.type() == XSModelGroupImpl.MODELGROUP_SEQUENCE) {
            // Recurse first
            calcFollowList(((XSCMBinOp)nodeCur).getLeft());
            calcFollowList(((XSCMBinOp)nodeCur).getRight());

            //
            //  Now handle our level. We use our left child's last pos
            //  set and our right child's first pos set, so go ahead and
            //  get them ahead of time.
            //
            final CMStateSet last  = ((XSCMBinOp)nodeCur).getLeft().lastPos();
            final CMStateSet first = ((XSCMBinOp)nodeCur).getRight().firstPos();

            //
            //  Now, for every position which is in our left child's last set
            //  add all of the states in our right child's first set to the
            //  follow set for that position.
            //
            for (int index = 0; index < fLeafCount; index++) {
                if (last.getBit(index))
                    fFollowList[index].union(first);
            }
        }
         else if (nodeCur.type() == XSParticleDecl.PARTICLE_ZERO_OR_MORE
        || nodeCur.type() == XSParticleDecl.PARTICLE_ONE_OR_MORE) {
            // Recurse first
            calcFollowList(((XSCMUniOp)nodeCur).getChild());

            //
            //  Now handle our level. We use our own first and last position
            //  sets, so get them up front.
            //
            final CMStateSet first = nodeCur.firstPos();
            final CMStateSet last  = nodeCur.lastPos();

            //
            //  For every position which is in our last position set, add all
            //  of our first position states to the follow set for that
            //  position.
            //
            for (int index = 0; index < fLeafCount; index++) {
                if (last.getBit(index))
                    fFollowList[index].union(first);
            }
        }

        else if (nodeCur.type() == XSParticleDecl.PARTICLE_ZERO_OR_ONE) {
            // Recurse only
            calcFollowList(((XSCMUniOp)nodeCur).getChild());
        }

    }

    /**
     * Dumps the tree of the current node to standard output.
     *
     * @param nodeCur The current node.
     * @param level   The maximum levels to output.
     *
     * @exception RuntimeException Thrown on error.
     */
    private void dumpTree(CMNode nodeCur, int level) {
        for (int index = 0; index < level; index++)
            System.out.print("   ");

        int type = nodeCur.type();

        switch(type ) {

        case XSModelGroupImpl.MODELGROUP_CHOICE:
        case XSModelGroupImpl.MODELGROUP_SEQUENCE: {
            if (type == XSModelGroupImpl.MODELGROUP_CHOICE)
                System.out.print("Choice Node ");
            else
                System.out.print("Seq Node ");

            if (nodeCur.isNullable())
                System.out.print("Nullable ");

            System.out.print("firstPos=");
            System.out.print(nodeCur.firstPos().toString());
            System.out.print(" lastPos=");
            System.out.println(nodeCur.lastPos().toString());

            dumpTree(((XSCMBinOp)nodeCur).getLeft(), level+1);
            dumpTree(((XSCMBinOp)nodeCur).getRight(), level+1);
            break;
        }
        case XSParticleDecl.PARTICLE_ZERO_OR_MORE:
        case XSParticleDecl.PARTICLE_ONE_OR_MORE:
        case XSParticleDecl.PARTICLE_ZERO_OR_ONE: {
            System.out.print("Rep Node ");

            if (nodeCur.isNullable())
                System.out.print("Nullable ");

            System.out.print("firstPos=");
            System.out.print(nodeCur.firstPos().toString());
            System.out.print(" lastPos=");
            System.out.println(nodeCur.lastPos().toString());

            dumpTree(((XSCMUniOp)nodeCur).getChild(), level+1);
            break;
        }
        case XSParticleDecl.PARTICLE_ELEMENT: {
            System.out.print
            (
                "Leaf: (pos="
                + ((XSCMLeaf)nodeCur).getPosition()
                + "), "
                + "(elemIndex="
                + ((XSCMLeaf)nodeCur).getLeaf()
                + ") "
            );

            if (nodeCur.isNullable())
                System.out.print(" Nullable ");

            System.out.print("firstPos=");
            System.out.print(nodeCur.firstPos().toString());
            System.out.print(" lastPos=");
            System.out.println(nodeCur.lastPos().toString());
            break;
        }
        case XSParticleDecl.PARTICLE_WILDCARD:
              System.out.print("Any Node: ");

            System.out.print("firstPos=");
            System.out.print(nodeCur.firstPos().toString());
            System.out.print(" lastPos=");
            System.out.println(nodeCur.lastPos().toString());
            break;
        default: {
            throw new RuntimeException("ImplementationMessages.VAL_NIICM");
        }
        }

    }


    /**
     * -1 is used to represent bad transitions in the transition table
     * entry for each state. So each entry is initialized to an all -1
     * array. This method creates a new entry and initializes it.
     */
    private int[] makeDefStateList()
    {
        int[] retArray = new int[fElemMapSize];
        for (int index = 0; index < fElemMapSize; index++)
            retArray[index] = -1;
        return retArray;
    }

    /** Post tree build initialization. */
    private void postTreeBuildInit(CMNode nodeCur) throws RuntimeException {
        // Set the maximum states on this node
        nodeCur.setMaxStates(fLeafCount);

        XSCMLeaf leaf = null;
        int pos = 0;
        // Recurse as required
        if (nodeCur.type() == XSParticleDecl.PARTICLE_WILDCARD) {
            leaf = (XSCMLeaf)nodeCur;
            pos = leaf.getPosition();
            fLeafList[pos] = leaf;
            fLeafListType[pos] = XSParticleDecl.PARTICLE_WILDCARD;
        }
        else if ((nodeCur.type() == XSModelGroupImpl.MODELGROUP_CHOICE) ||
                 (nodeCur.type() == XSModelGroupImpl.MODELGROUP_SEQUENCE)) {
            postTreeBuildInit(((XSCMBinOp)nodeCur).getLeft());
            postTreeBuildInit(((XSCMBinOp)nodeCur).getRight());
        }
        else if (nodeCur.type() == XSParticleDecl.PARTICLE_ZERO_OR_MORE ||
                 nodeCur.type() == XSParticleDecl.PARTICLE_ONE_OR_MORE ||
                 nodeCur.type() == XSParticleDecl.PARTICLE_ZERO_OR_ONE) {
            postTreeBuildInit(((XSCMUniOp)nodeCur).getChild());
        }
        else if (nodeCur.type() == XSParticleDecl.PARTICLE_ELEMENT) {
            //  Put this node in the leaf list at the current index if its
            //  a non-epsilon leaf.
            leaf = (XSCMLeaf)nodeCur;
            pos = leaf.getPosition();
            fLeafList[pos] = leaf;
            fLeafListType[pos] = XSParticleDecl.PARTICLE_ELEMENT;
        }
        else {
            throw new RuntimeException("ImplementationMessages.VAL_NIICM");
        }
    }

    /**
     * check whether this content violates UPA constraint.
     *
     * @param subGroupHandler the substitution group handler
     * @return true if this content model contains other or list wildcard
     */
    public boolean checkUniqueParticleAttribution(SubstitutionGroupHandler subGroupHandler) throws XMLSchemaException {
        // Unique Particle Attribution
        // store the conflict results between any two elements in fElemMap
        // 0: not compared; -1: no conflict; 1: conflict
        // initialize the conflict table (all 0 initially)
        byte conflictTable[][] = new byte[fElemMapSize][fElemMapSize];

        // for each state, check whether it has overlap transitions
        for (int i = 0; i < fTransTable.length && fTransTable[i] != null; i++) {
            for (int j = 0; j < fElemMapSize; j++) {
                for (int k = j+1; k < fElemMapSize; k++) {
                    if (fTransTable[i][j] != -1 &&
                        fTransTable[i][k] != -1) {
                        if (conflictTable[j][k] == 0) {
                            conflictTable[j][k] = XSConstraints.overlapUPA
                                                   (fElemMap[j],fElemMap[k],
                                                   subGroupHandler) ?
                                                   (byte)1 : (byte)-1;
                        }
                    }
                }
            }
        }

        // report all errors
        for (int i = 0; i < fElemMapSize; i++) {
            for (int j = 0; j < fElemMapSize; j++) {
                if (conflictTable[i][j] == 1) {
                    //errors.newError("cos-nonambig", new Object[]{fElemMap[i].toString(),
                    //                                             fElemMap[j].toString()});
                    // REVISIT: do we want to report all errors? or just one?
                    throw new XMLSchemaException("cos-nonambig", new Object[]{fElemMap[i].toString(),
                                                                              fElemMap[j].toString()});
                }
            }
        }

        // if there is a other or list wildcard, we need to check this CM
        // again, if this grammar is cached.
        for (int i = 0; i < fElemMapSize; i++) {
            if (fElemMapType[i] == XSParticleDecl.PARTICLE_WILDCARD) {
                XSWildcardDecl wildcard = (XSWildcardDecl)fElemMap[i];
                if (wildcard.fType == XSWildcardDecl.NSCONSTRAINT_LIST ||
                    wildcard.fType == XSWildcardDecl.NSCONSTRAINT_NOT) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check which elements are valid to appear at this point. This method also
     * works if the state is in error, in which case it returns what should
     * have been seen.
     *
     * @param state  the current state
     * @return       a Vector whose entries are instances of
     *               either XSWildcardDecl or XSElementDecl.
     */
    public Vector whatCanGoHere(int[] state) {
        int curState = state[0];
        if (curState < 0)
            curState = state[1];

        Vector ret = new Vector();
        for (int elemIndex = 0; elemIndex < fElemMapSize; elemIndex++) {
            if (fTransTable[curState][elemIndex] != -1)
                ret.addElement(fElemMap[elemIndex]);
        }
        return ret;
    }

} // class DFAContentModel
