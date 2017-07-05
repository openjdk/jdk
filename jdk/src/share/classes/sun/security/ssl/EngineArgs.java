/*
 * Copyright 2004-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.ssl;

import javax.net.ssl.*;
import java.nio.*;

/*
 * A multi-purpose class which handles all of the SSLEngine arguments.
 * It validates arguments, checks for RO conditions, does space
 * calculations, performs scatter/gather, etc.
 *
 * @author Brad R. Wetmore
 */
class EngineArgs {

    /*
     * Keep track of the input parameters.
     */
    ByteBuffer netData;
    ByteBuffer [] appData;

    private int offset;         // offset/len for the appData array.
    private int len;

    /*
     * The initial pos/limit conditions.  This is useful because we can
     * quickly calculate the amount consumed/produced in successful
     * operations, or easily return the buffers to their pre-error
     * conditions.
     */
    private int netPos;
    private int netLim;

    private int [] appPoss;
    private int [] appLims;

    /*
     * Sum total of the space remaining in all of the appData buffers
     */
    private int appRemaining = 0;

    private boolean wrapMethod;

    /*
     * Called by the SSLEngine.wrap() method.
     */
    EngineArgs(ByteBuffer [] appData, int offset, int len,
            ByteBuffer netData) {
        this.wrapMethod = true;
        init(netData, appData, offset, len);
    }

    /*
     * Called by the SSLEngine.unwrap() method.
     */
    EngineArgs(ByteBuffer netData, ByteBuffer [] appData, int offset,
            int len) {
        this.wrapMethod = false;
        init(netData, appData, offset, len);
    }

    /*
     * The main initialization method for the arguments.  Most
     * of them are pretty obvious as to what they do.
     *
     * Since we're already iterating over appData array for validity
     * checking, we also keep track of how much remainging space is
     * available.  Info is used in both unwrap (to see if there is
     * enough space available in the destination), and in wrap (to
     * determine how much more we can copy into the outgoing data
     * buffer.
     */
    private void init(ByteBuffer netData, ByteBuffer [] appData,
            int offset, int len) {

        if ((netData == null) || (appData == null)) {
            throw new IllegalArgumentException("src/dst is null");
        }

        if ((offset < 0) || (len < 0) || (offset > appData.length - len)) {
            throw new IndexOutOfBoundsException();
        }

        if (wrapMethod && netData.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }

        netPos = netData.position();
        netLim = netData.limit();

        appPoss = new int [appData.length];
        appLims = new int [appData.length];

        for (int i = offset; i < offset + len; i++) {
            if (appData[i] == null) {
                throw new IllegalArgumentException(
                    "appData[" + i + "] == null");
            }

            /*
             * If we're unwrapping, then check to make sure our
             * destination bufffers are writable.
             */
            if (!wrapMethod && appData[i].isReadOnly()) {
                throw new ReadOnlyBufferException();
            }

            appRemaining += appData[i].remaining();

            appPoss[i] = appData[i].position();
            appLims[i] = appData[i].limit();
        }

        /*
         * Ok, looks like we have a good set of args, let's
         * store the rest of this stuff.
         */
        this.netData = netData;
        this.appData = appData;
        this.offset = offset;
        this.len = len;
    }

    /*
     * Given spaceLeft bytes to transfer, gather up that much data
     * from the appData buffers (starting at offset in the array),
     * and transfer it into the netData buffer.
     *
     * The user has already ensured there is enough room.
     */
    void gather(int spaceLeft) {
        for (int i = offset; (i < (offset + len)) && (spaceLeft > 0); i++) {
            int amount = Math.min(appData[i].remaining(), spaceLeft);
            appData[i].limit(appData[i].position() + amount);
            netData.put(appData[i]);
            spaceLeft -= amount;
        }
    }

    /*
     * Using the supplied buffer, scatter the data into the appData buffers
     * (starting at offset in the array).
     *
     * The user has already ensured there is enough room.
     */
    void scatter(ByteBuffer readyData) {
        int amountLeft = readyData.remaining();

        for (int i = offset; (i < (offset + len)) && (amountLeft > 0);
                i++) {
            int amount = Math.min(appData[i].remaining(), amountLeft);
            readyData.limit(readyData.position() + amount);
            appData[i].put(readyData);
            amountLeft -= amount;
        }
        assert(readyData.remaining() == 0);
    }

    int getAppRemaining() {
        return appRemaining;
    }

    /*
     * Calculate the bytesConsumed/byteProduced.  Aren't you glad
     * we saved this off earlier?
     */
    int deltaNet() {
        return (netData.position() - netPos);
    }

    /*
     * Calculate the bytesConsumed/byteProduced.  Aren't you glad
     * we saved this off earlier?
     */
    int deltaApp() {
        int sum = 0;    // Only calculating 2^14 here, don't need a long.

        for (int i = offset; i < offset + len; i++) {
            sum += appData[i].position() - appPoss[i];
        }

        return sum;
    }

    /*
     * In the case of Exception, we want to reset the positions
     * to appear as though no data has been consumed or produced.
     */
    void resetPos() {
        netData.position(netPos);
        for (int i = offset; i < offset + len; i++) {
            appData[i].position(appPoss[i]);
        }
    }

    /*
     * We are doing lots of ByteBuffer manipulations, in which case
     * we need to make sure that the limits get set back correctly.
     * This is one of the last things to get done before returning to
     * the user.
     */
    void resetLim() {
        netData.limit(netLim);
        for (int i = offset; i < offset + len; i++) {
            appData[i].limit(appLims[i]);
        }
    }
}
