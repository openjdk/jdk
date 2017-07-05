/*
 * Copyright (c) 1996, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.tree;

import sun.tools.java.*;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public final
class Vset implements Constants {
    long vset;                  // DA bits for first 64 variables
    long uset;                  // DU bits for first 64 variables

    // The extension array is interleaved, consisting of alternating
    // blocks of 64 DA bits followed by 64 DU bits followed by 64 DA
    // bits, and so on.

    long x[];                   // extension array for more bits

    // An infinite vector of zeroes or an infinite vector of ones is
    // represented by a special value of the extension array.
    //
    // IMPORTANT: The condition 'this.x == fullX' is used as a marker for
    // unreachable code, i.e., for a dead-end.  We maintain the invariant
    // that (this.x != fullX || (this.vset == -1 && this.uset == -1)).
    // A dead-end has the peculiar property that all variables are both
    // definitely assigned and definitely unassigned.  We always force this
    // condition to hold, even when the normal bitvector operations performed
    // during DA/DU analysis would produce a different result.  This supresses
    // reporting of DA/DU errors in unreachable code.

    static final long emptyX[] = new long[0]; // all zeroes
    static final long fullX[]  = new long[0]; // all ones

    // For more thorough testing of long vset support, it is helpful to
    // temporarily redefine this value to a smaller number, such as 1 or 2.

    static final int VBITS = 64; // number of bits in vset (uset)

    /**
     * This is the Vset which reports all vars assigned and unassigned.
     * This impossibility is degenerately true exactly when
     * control flow cannot reach this point.
     */

    // We distinguish a canonical dead-end value generated initially for
    // statements that do not complete normally, making the next one unreachable.
    // Once an unreachable statement is reported, a non-canonical dead-end value
    // is used for subsequent statements in order to suppress redundant error
    // messages.

    static final Vset DEAD_END = new Vset(-1, -1, fullX);

    /**
     * Create an empty Vset.
     */
    public Vset() {
        this.x = emptyX;
    }

    private Vset(long vset, long uset, long x[]) {
        this.vset = vset;
        this.uset = uset;
        this.x = x;
    }

    /**
     * Create an copy of the given Vset.
     * (However, DEAD_END simply returns itself.)
     */
    public Vset copy() {
        if (this == DEAD_END) {
            return this;
        }
        Vset vs = new Vset(vset, uset, x);
        if (x.length > 0) {
            vs.growX(x.length); // recopy the extension vector
        }
        return vs;
    }

    private void growX(int length) {
        long newX[] = new long[length];
        long oldX[] = x;
        for (int i = 0; i < oldX.length; i++) {
            newX[i] = oldX[i];
        }
        x = newX;
    }

    /**
     * Ask if this is a vset for a dead end.
     * Answer true only for the canonical dead-end, DEAD_END.
     * A canonical dead-end is produced only as a result of
     * a statement that cannot complete normally, as specified
     * by the JLS.  Due to the special-case rules for if-then
     * and if-then-else, this may fail to detect actual unreachable
     * code that could easily be identified.
     */

    public boolean isDeadEnd() {
        return (this == DEAD_END);
    }

    /**
     * Ask if this is a vset for a dead end.
     * Answer true for any dead-end.
     * Since 'clearDeadEnd' has no effect on this predicate,
     * if-then and if-then-else are handled in the more 'obvious'
     * and precise way.  This predicate is to be preferred for
     * dead code elimination purposes.
     * (Presently used in workaround for bug 4173473 in MethodExpression.java)
     */
    public boolean isReallyDeadEnd() {
        return (x == fullX);
    }

    /**
     * Replace canonical DEAD_END with a distinct but
     * equivalent Vset.  The bits are unaltered, but
     * the result does not answer true to 'isDeadEnd'.
     * <p>
     * Used mostly for error recovery, but see
     * 'IfStatement.check', where it is used to
     * implement the special-case treatment of
     * statement reachability for such statements.
     */
    public Vset clearDeadEnd() {
        if (this == DEAD_END) {
            return new Vset(-1, -1, fullX);
        }
        return this;
    }

    /**
     * Ask if a var is definitely assigned.
     */
    public boolean testVar(int varNumber) {
        long bit = (1L << varNumber);
        if (varNumber >= VBITS) {
            int i = (varNumber / VBITS - 1) * 2;
            if (i >= x.length) {
                return (x == fullX);
            }
            return (x[i] & bit) != 0;
        } else {
            return (vset & bit) != 0;
        }
    }

    /**
     * Ask if a var is definitely un-assigned.
     * (This is not just the negation of testVar:
     * It's possible for neither to be true.)
     */
    public boolean testVarUnassigned(int varNumber) {
        long bit = (1L << varNumber);
        if (varNumber >= VBITS) {
            // index "uset" extension
            int i = ((varNumber / VBITS - 1) * 2) + 1;
            if (i >= x.length) {
                return (x == fullX);
            }
            return (x[i] & bit) != 0;
        } else {
            return (uset & bit) != 0;
        }
    }

    /**
     * Note that a var is definitely assigned.
     * (Side-effecting.)
     */
    public Vset addVar(int varNumber) {
        if (x == fullX) {
            return this;
        }

        // gen DA, kill DU

        long bit = (1L << varNumber);
        if (varNumber >= VBITS) {
            int i = (varNumber / VBITS - 1) * 2;
            if (i >= x.length) {
                growX(i+1);
            }
            x[i] |= bit;
            if (i+1 < x.length) {
                x[i+1] &=~ bit;
            }
        } else {
            vset |= bit;
            uset &=~ bit;
        }
        return this;
    }

    /**
     * Note that a var is definitely un-assigned.
     * (Side-effecting.)
     */
    public Vset addVarUnassigned(int varNumber) {
        if (x == fullX) {
            return this;
        }

        // gen DU, kill DA

        long bit = (1L << varNumber);
        if (varNumber >= VBITS) {
            // index "uset" extension
            int i = ((varNumber / VBITS - 1) * 2) + 1;
            if (i >= x.length) {
                growX(i+1);
            }
            x[i] |= bit;
            x[i-1] &=~ bit;
        } else {
            uset |= bit;
            vset &=~ bit;
        }
        return this;
    }

    /**
     * Retract any assertion about the var.
     * This operation is ineffective on a dead-end.
     * (Side-effecting.)
     */
    public Vset clearVar(int varNumber) {
        if (x == fullX) {
            return this;
        }
        long bit = (1L << varNumber);
        if (varNumber >= VBITS) {
            int i = (varNumber / VBITS - 1) * 2;
            if (i >= x.length) {
                return this;
            }
            x[i] &=~ bit;
            if (i+1 < x.length) {
                x[i+1] &=~ bit;
            }
        } else {
            vset &=~ bit;
            uset &=~ bit;
        }
        return this;
    }

    /**
     * Join with another vset.  This is set intersection.
     * (Side-effecting.)
     */
    public Vset join(Vset other) {

        // Return a dead-end if both vsets are dead-ends.
        // Return the canonical DEAD_END only if both vsets
        // are the canonical DEAD_END.  Otherwise, an incoming
        // dead-end vset has already produced an error message,
        // and is now assumed to be reachable.
        if (this == DEAD_END) {
            return other.copy();
        }
        if (other == DEAD_END) {
            return this;
        }
        if (x == fullX) {
            return other.copy();
        }
        if (other.x == fullX) {
            return this;
        }

        // DA = DA intersection DA
        // DU = DU intersection DU

        vset &= other.vset;
        uset &= other.uset;

        if (other.x == emptyX) {
            x = emptyX;
        } else {
            // ASSERT(otherX.length > 0);
            long otherX[] = other.x;
            int selfLength = x.length;
            int limit = (otherX.length < selfLength) ? otherX.length : selfLength;
            for (int i = 0; i < limit; i++) {
                x[i] &= otherX[i];
            }
            // If self is longer than other, all remaining
            // bits are implicitly 0.  In the result, then,
            // the remaining DA and DU bits are cleared.
            for (int i = limit; i < selfLength; i++) {
                x[i] = 0;
            }
        }
        return this;
    }

    /**
     * Add in the definite assignment bits of another vset,
     * but join the definite unassignment bits.  This unusual
     * operation is used only for 'finally' blocks.  The
     * original vset 'this' is destroyed by this operation.
     * (Part of fix for 4068688.)
     */

    public Vset addDAandJoinDU(Vset other) {

        // Return a dead-end if either vset is a dead end.
        // If either vset is the canonical DEAD_END, the
        // result is also the canonical DEAD_END.
        if (this == DEAD_END) {
            return this;
        }
        if (other == DEAD_END) {
            return other;
        }
        if (x == fullX) {
            return this;
        }
        if (other.x == fullX) {
            return other.copy();
        }

        // DA = DA union DA'
        // DU = (DU intersection DU') - DA'

        vset = vset | other.vset;
        uset = (uset & other.uset) & ~other.vset;

        int selfLength = x.length;
        long otherX[] = other.x;
        int otherLength = otherX.length;

        if (otherX != emptyX) {
            // ASSERT(otherX.length > 0);
            if (otherLength > selfLength) {
                growX(otherLength);
            }
            int i = 0;
            while (i < otherLength) {
                x[i] |= otherX[i];
                i++;
                if (i == otherLength) break;
                x[i] = ((x[i] & otherX[i]) & ~otherX[i-1]);
                i++;
            }
        }
        // If self is longer than other, all remaining
        // bits are implicitly 0. In the result, then,
        // the remaining DA bits are left unchanged, and
        // the DU bits are all cleared. First, align
        // index to the next block of DU bits (odd index).
        for (int i = (otherLength | 1); i < selfLength; i += 2) {
            x[i] = 0;
        }
        return this;
    }


    /**
     * Construct a vset consisting of the DA bits of the first argument
     * and the DU bits of the second argument.  This is a higly unusual
     * operation, as it implies a case where the flowgraph for DA analysis
     * differs from that for DU analysis.  It is only needed for analysing
     * 'try' blocks.  The result is a dead-end iff the first argument is
     * dead-end. (Part of fix for 4068688.)
     */

    public static Vset firstDAandSecondDU(Vset sourceDA, Vset sourceDU) {

        // Note that reachability status is received via 'sourceDA' only!
        // This is a consequence of the fact that reachability and DA
        // analysis are performed on an identical flow graph, whereas the
        // flowgraph for DU analysis differs in the case of a 'try' statement.
        if (sourceDA.x == fullX) {
            return sourceDA.copy();
        }

        long sourceDAx[] = sourceDA.x;
        int lenDA = sourceDAx.length;
        long sourceDUx[] = sourceDU.x;
        int lenDU = sourceDUx.length;
        int limit = (lenDA > lenDU) ? lenDA : lenDU;
        long x[] = emptyX;

        if (limit > 0) {
            x = new long[limit];
            for (int i = 0; i < lenDA; i += 2) {
                x[i] = sourceDAx[i];
            }
            for (int i = 1; i < lenDU; i += 2) {
                x[i] = sourceDUx[i];
            }
        }

        return new Vset(sourceDA.vset, sourceDU.uset, x);
    }

    /**
     * Remove variables from the vset that are no longer part of
     * a context.  Zeroes are stored past varNumber.
     * (Side-effecting.)<p>
     * However, if this is a dead end, keep it so.
     * That is, leave an infinite tail of bits set.
     */
    public Vset removeAdditionalVars(int varNumber) {
        if (x == fullX) {
            return this;
        }
        long bit = (1L << varNumber);
        if (varNumber >= VBITS) {
            int i = (varNumber / VBITS - 1) * 2;
            if (i < x.length) {
                x[i] &= (bit - 1);
                if (++i < x.length) {
                    x[i] &= (bit - 1); // do the "uset" extension also
                }
                while (++i < x.length) {
                    x[i] = 0;
                }
            }
        } else {
            if (x.length > 0) {
                x = emptyX;
            }
            vset &= (bit - 1);
            uset &= (bit - 1);
        }
        return this;
    }

    /**
     * Return one larger than the highest bit set.
     */
    public int varLimit() {
        long vset;
        int result;
    scan: {
            for (int i = (x.length / 2) * 2; i >= 0; i -= 2) {
                if (i == x.length)  continue; // oops
                vset = x[i];
                if (i+1 < x.length) {
                    vset |= x[i+1]; // check the "uset" also
                }
                if (vset != 0) {
                    result = (i/2 + 1) * VBITS;
                    break scan;
                }
            }
            vset = this.vset;
            vset |= this.uset;  // check the "uset" also
            if (vset != 0) {
                result = 0;
                break scan;
            } else {
                return 0;
            }
        }
        while (vset != 0) {
            result += 1;
            vset >>>= 1;
        }
        return result;
    }

    public String toString() {
        if (this == DEAD_END)
            return "{DEAD_END}";
        StringBuilder sb = new StringBuilder("{");
        int maxVar = VBITS * (1 + (x.length+1)/2);
        for (int i = 0; i < maxVar; i++) {
            if (!testVarUnassigned(i)) {
                if (sb.length() > 1) {
                    sb.append(' ');
                }
                sb.append(i);
                if (!testVar(i)) {
                    sb.append('?'); // not definitely unassigned
                }
            }
        }
        if (x == fullX) {
            sb.append("...DEAD_END");
        }
        sb.append('}');
        return sb.toString();
    }

}
