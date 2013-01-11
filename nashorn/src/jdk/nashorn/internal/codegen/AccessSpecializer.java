/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.codegen;

import java.util.HashSet;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.Assignment;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ReferenceNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.TypeOverride;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.Debug;
import jdk.nashorn.internal.runtime.DebugLogger;

/**
 * This is a post pass for Lower, that removes casts for accessors to objects in
 * Scope, and replaces the accessor type with a narrower one with possible.
 *
 * Any node that implements TypeOverride will be subject to access specialization.
 *
 * TypeOverride basically means "type inference has determined that the field x
 * is an object, but if you do x & 17, it can be read as an int, which we hope
 * coincides with its internal representation. In that case there is no boxing
 * that may or may not be removed by the JVM and less data bandwidth.
 *
 * Ideally this should not be a post pass, but it requires slot AND scope info, and has
 * to be run where it is, which is called from {@link CodeGenerator}.
 *
 * @see TypeOverride
 */

final class AccessSpecializer extends NodeOperatorVisitor {
    /** Debug logger for access specialization. Enable it with --log=access:level
        or -Dnashorn.callsiteaccess.debug */
    private static final DebugLogger LOG   = new DebugLogger("access", "nashorn.callsiteaccess.debug");
    private static final boolean     DEBUG = LOG.isEnabled();

    @Override
    public Node leave(final VarNode varNode) {
        if (varNode.isAssignment()) {
            return leaveAssign(varNode);
        }

        return varNode;
    }

    @Override
    public Node leave(final CallNode callNode) {
        final Node function = callNode.getFunction();
        if (function instanceof ReferenceNode) {
            changeType(callNode, ((ReferenceNode)function).getReference().getType());
        }
        return callNode;
    }

    @Override
    public Node leaveASSIGN(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_ADD(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_AND(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_OR(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_XOR(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_DIV(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_MOD(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_MUL(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SAR(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SHL(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SHR(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SUB(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode);
    }

    @Override
    public Node leaveCOMMALEFT(final BinaryNode binaryNode) {
        return propagateResultType(binaryNode, binaryNode.lhs().getType());
    }

    @Override
    public Node leaveCOMMARIGHT(final BinaryNode binaryNode) {
        return propagateResultType(binaryNode, binaryNode.rhs().getType());
    }

    @Override
    public Node leaveCONVERT(final UnaryNode unaryNode) {
        final Type castTo = unaryNode.getType();

        Node rhs = unaryNode.rhs();

        // Go through all conversions until we find the first non-convert node.
        while (rhs.tokenType() == TokenType.CONVERT) {
            rhs = ((UnaryNode)rhs).rhs();
        }

        // If this node can be type changed
        if (canHaveCallSiteType(rhs) && isSupportedCallSiteType(castTo)) {
            /*
             * Just add a callsite type and throw away the cast, appropriate
             * getter/setter is selected, which will do the conversion for us
             */
            changeType(rhs, castTo);
            LOG.fine("*** cast: converting " + debugNode(unaryNode) + " to " + debugNode(rhs));

            return rhs;
        }

        // Micro optimization for node hygiene and pattern detection - remove unnecessary same type cast
        if (unaryNode.getType().isEquivalentTo(rhs.getType())) {
            return rhs;
        }

        return unaryNode;
    }

    @Override
    public Node leaveDECINC(final UnaryNode unaryNode) {
        assert unaryNode.isAssignment();

        final Node dest = unaryNode.getAssignmentDest();
        if (canHaveCallSiteType(dest) && isSupportedCallSiteType(unaryNode.getType())) {
            changeTypeInAssignment(dest, unaryNode.getType());
        }

        return unaryNode;
    }

    /**
     * Is this a node that can have its type overridden. This is true for
     * AccessNodes, IndexNodes and IdentNodes
     *
     * @param node the node to check
     * @return true if node can have a callsite type
     */
    private static boolean canHaveCallSiteType(final Node node) {
        return node instanceof TypeOverride && ((TypeOverride)node).canHaveCallSiteType();
    }

    /**
     * Is the specialization type supported. Currently we treat booleans as objects
     * and have no special boolean type accessor, thus booleans are ignored.
     * TODO - support booleans? NASHORN-590
     *
     * @param castTo the type to check
     * @return true if call site type is supported
     */
    private static boolean isSupportedCallSiteType(final Type castTo) {
        return castTo.isNumeric(); // don't specializable for boolean
    }

    /**
     * Turn a node into a covert node
     *
     * @param  node the node
     * @return the node as a convert node
     */
    private static Node convert(final Node node) {
        return new UnaryNode(node.getSource(),
                Token.recast(node.getToken(), TokenType.CONVERT),
                node);
    }

    private static Node leaveAssign(final Node node) {
        assert node.isAssignment() : node + " is not an assignment";


        final Node lhs = ((Assignment<?>)node).getAssignmentDest();
        Node       rhs = ((Assignment<?>)node).getAssignmentSource();

        /**
         * Nodes with local variable slots  are assumed to be of their optimal type
         * already and aren't affected here. This is not strictly true, for instance
         * with doubles instead of in a bounded loop. TODO - range check: NASHORN-363
         *
         * This is also not strictly true for var y = x = 55; where y has no other uses
         * Then y can be an int, but lower conservatively does an object of the assign to
         * scope
         */
        final Symbol lhsSymbol = lhs.getSymbol();

        if (lhsSymbol.hasSlot() && !lhsSymbol.isScope()) {
            LOG.finest(lhs.getSymbol() + " has slot!");
            if (!lhs.getType().isEquivalentTo(rhs.getType())) {
                LOG.finest("\tslot assignment: " +lhs.getType()+ " " +rhs.getType() + " " + debugNode(node));

                final Node c = convert(rhs);
                c.setSymbol(lhsSymbol);
                ((Assignment<?>)node).setAssignmentSource(c);

                LOG.fine("*** slot assignment turned to : " + debugNode(node));
            } else {
                LOG.finest("aborted - type equivalence between lhs and rhs");
            }

            return node;
        }

        // e.g. __DIR__, __LINE__, __FILE__ - don't try to change these
        if (lhs instanceof IdentNode && ((IdentNode)lhs).isSpecialIdentity()) {
            return node;
        }

        /**
         * Try to cast to the type of the right hand side, now pruned. E.g. an (object)17 should
         * now be just 17, an int.
         */
        Type castTo = rhs.getType();

        // If LHS can't get a new type, neither can rhs - retain the convert
        if (!canHaveCallSiteType(lhs)) {
            return node;
        }

        // Take the narrowest type of the entire cast sequence
        while (rhs.tokenType() == TokenType.CONVERT) {
            rhs    = ((UnaryNode)rhs).rhs();
            castTo = Type.narrowest(rhs.getType(), castTo); //e.g. (object)(int) -> int even though object is outermost
        }

        // If castTo is wider than widestOperationType, castTo can be further slowed down
        final Type widestOperationType = node.getWidestOperationType();
        LOG.finest("node wants to be " + castTo + " and its widest operation is " + widestOperationType);

        if (widestOperationType != castTo && Type.widest(castTo, widestOperationType) == castTo) {
            LOG.info("###" + node + " castTo was " + castTo + " but could be downgraded to " + node.getWidestOperationType());
            castTo = node.getWidestOperationType();
            if (rhs instanceof TypeOverride) {
                changeType(rhs, castTo);
            }
        }

        /*
         * If this is a self modifying op, we can't be narrower than the widest optype
         * or e.g. x = x + 12 and x += 12 will turn into different things
         */
        if (node.isSelfModifying()) {
            castTo = Type.widest(widestOperationType, castTo);
        }

        // We only specialize for numerics, not for booleans.
        if (isSupportedCallSiteType(castTo)) {
            if (rhs.getType() != castTo) {
                LOG.finest("cast was necessary, abort: " + node + " " + rhs.getType() + " != " + castTo);
                return node;
            }

            LOG.finest("assign: " + debugNode(node));

            changeTypeInAssignment(lhs, castTo);
            ((Assignment<?>)node).setAssignmentSource(rhs);

            LOG.info("### modified to " + debugNode(node) + " (given type override " + castTo + ")");

            propagateResultType(node, castTo);
        }

        return node;
    }

    private static Node propagateResultType(final Node node, final Type type) {
        //warning! this CANNOT be done for non temporaries as they are used in other computations
        if (isSupportedCallSiteType(type)) {
            if (node.getSymbol().isTemp()) {
                LOG.finest("changing temporary type: " + debugNode(node) + " to " + type);
                node.getSymbol().setTypeOverride(type);
                LOG.info("### node modified to " + debugNode(node) + " (given type override " + type + ")");
            }
        }
        return node;
    }

    private static void changeTypeInAssignment(final Node dest, final Type newType) {
        if (changeType(dest, newType)) {
            LOG.finest("changed assignment " + dest + " " + dest.getSymbol());
            assert !newType.isObject();

            final HashSet<Node> exclude = new HashSet<>();

            dest.accept(new NodeVisitor() {

                private void setCanBePrimitive(final Symbol symbol) {
                    LOG.fine("*** can be primitive symbol " + symbol + " " + Debug.id(symbol));
                    symbol.setCanBePrimitive(newType);
                }

                @Override
                public Node enter(final IdentNode identNode) {
                    if (!exclude.contains(identNode)) {
                        setCanBePrimitive(identNode.getSymbol());
                    }
                    return null;
                }

                @Override
                public Node enter(final AccessNode accessNode) {
                    setCanBePrimitive(accessNode.getProperty().getSymbol());
                    return null;
                }

                @Override
                public Node enter(final IndexNode indexNode) {
                    exclude.add(indexNode.getBase()); //prevent array base node to be flagged as primitive, but k in a[k++] is fine
                    return indexNode;
                }

            });
        }
    }

    private static boolean changeType(final Node node, final Type newType) {
        if (!node.getType().equals(newType)) {
            ((TypeOverride)node).setType(newType);
            return true;
        }
        return false;
    }

    private static String debugNode(final Node node) {
        if (DEBUG) {
            return node.toString();
        }
        return "";
    }
}
