/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Copyright (c) 2011-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package build.tools.tzdb;

import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectOutput;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneOffsetTransitionRule;
import java.time.zone.ZoneOffsetTransitionRule.TimeDefinition;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Duplicated code of javax.time.zone.ZoneRules, ZoneOffsetTransitionRule
 * and Ser to generate the serialization form output of ZoneRules for
 * tzdb.jar.
 *
 * Implementation here is the copy/paste of ZoneRules, ZoneOffsetTransitionRule
 * and Ser in javax.time.zone package. Make sure the code here is synchrionozed
 * with the serialization implementation there.
 *
 * @since 1.8
 */

final class ZoneRules {

    /**
     * The transitions between standard offsets (epoch seconds), sorted.
     */
    private final long[] standardTransitions;
    /**
     * The standard offsets.
     */
    private final ZoneOffset[] standardOffsets;
    /**
     * The transitions between instants (epoch seconds), sorted.
     */
    private final long[] savingsInstantTransitions;

    /**
     * The wall offsets.
     */
    private final ZoneOffset[] wallOffsets;
    /**
     * The last rule.
     */
    private final ZoneOffsetTransitionRule[] lastRules;

    /**
     * Creates an instance.
     *
     * @param baseStandardOffset  the standard offset to use before legal rules were set, not null
     * @param baseWallOffset  the wall offset to use before legal rules were set, not null
     * @param standardOffsetTransitionList  the list of changes to the standard offset, not null
     * @param transitionList  the list of transitions, not null
     * @param lastRules  the recurring last rules, size 16 or less, not null
     */
    ZoneRules(ZoneOffset baseStandardOffset,
              ZoneOffset baseWallOffset,
              List<ZoneOffsetTransition> standardOffsetTransitionList,
              List<ZoneOffsetTransition> transitionList,
              List<ZoneOffsetTransitionRule> lastRules) {

        this.standardTransitions = new long[standardOffsetTransitionList.size()];

        this.standardOffsets = new ZoneOffset[standardOffsetTransitionList.size() + 1];
        this.standardOffsets[0] = baseStandardOffset;
        for (int i = 0; i < standardOffsetTransitionList.size(); i++) {
            this.standardTransitions[i] = standardOffsetTransitionList.get(i).toEpochSecond();
            this.standardOffsets[i + 1] = standardOffsetTransitionList.get(i).getOffsetAfter();
        }

        // convert savings transitions to locals
        List<ZoneOffset> localTransitionOffsetList = new ArrayList<>();
        localTransitionOffsetList.add(baseWallOffset);
        for (ZoneOffsetTransition trans : transitionList) {
            localTransitionOffsetList.add(trans.getOffsetAfter());
        }

        this.wallOffsets = localTransitionOffsetList.toArray(new ZoneOffset[localTransitionOffsetList.size()]);

        // convert savings transitions to instants
        this.savingsInstantTransitions = new long[transitionList.size()];
        for (int i = 0; i < transitionList.size(); i++) {
            this.savingsInstantTransitions[i] = transitionList.get(i).toEpochSecond();
        }

        // last rules
        if (lastRules.size() > 16) {
            throw new IllegalArgumentException("Too many transition rules");
        }
        this.lastRules = lastRules.toArray(new ZoneOffsetTransitionRule[lastRules.size()]);
    }

    /** Type for ZoneRules. */
    static final byte ZRULES = 1;

    /**
     * Writes the state to the stream.
     *
     * @param out  the output stream, not null
     * @throws IOException if an error occurs
     */
    void writeExternal(DataOutput out) throws IOException {
        out.writeByte(ZRULES);
        out.writeInt(standardTransitions.length);
        for (long trans : standardTransitions) {
            writeEpochSec(trans, out);
        }
        for (ZoneOffset offset : standardOffsets) {
            writeOffset(offset, out);
        }
        out.writeInt(savingsInstantTransitions.length);
        for (long trans : savingsInstantTransitions) {
            writeEpochSec(trans, out);
        }
        for (ZoneOffset offset : wallOffsets) {
            writeOffset(offset, out);
        }
        out.writeByte(lastRules.length);
        for (ZoneOffsetTransitionRule rule : lastRules) {
            writeRule(rule, out);
        }
    }

    /**
     * Writes the state the ZoneOffset to the stream.
     *
     * @param offset  the offset, not null
     * @param out  the output stream, not null
     * @throws IOException if an error occurs
     */
    static void writeOffset(ZoneOffset offset, DataOutput out) throws IOException {
        final int offsetSecs = offset.getTotalSeconds();
        int offsetByte = offsetSecs % 900 == 0 ? offsetSecs / 900 : 127;  // compress to -72 to +72
        out.writeByte(offsetByte);
        if (offsetByte == 127) {
            out.writeInt(offsetSecs);
        }
    }

    /**
     * Writes the epoch seconds to the stream.
     *
     * @param epochSec  the epoch seconds, not null
     * @param out  the output stream, not null
     * @throws IOException if an error occurs
     */
    static void writeEpochSec(long epochSec, DataOutput out) throws IOException {
        if (epochSec >= -4575744000L && epochSec < 10413792000L && epochSec % 900 == 0) {  // quarter hours between 1825 and 2300
            int store = (int) ((epochSec + 4575744000L) / 900);
            out.writeByte((store >>> 16) & 255);
            out.writeByte((store >>> 8) & 255);
            out.writeByte(store & 255);
        } else {
            out.writeByte(255);
            out.writeLong(epochSec);
        }
    }

    /**
     * Writes the state of the transition rule to the stream.
     *
     * @param rule  the transition rule, not null
     * @param out  the output stream, not null
     * @throws IOException if an error occurs
     */
    static void writeRule(ZoneOffsetTransitionRule rule, DataOutput out) throws IOException {
        int month = rule.getMonth().getValue();
        byte dom = (byte)rule.getDayOfMonthIndicator();
        int dow = (rule.getDayOfWeek() == null ? -1 : rule.getDayOfWeek().getValue());
        LocalTime time = rule.getLocalTime();
        boolean timeEndOfDay = rule.isMidnightEndOfDay();
        TimeDefinition timeDefinition = rule.getTimeDefinition();
        ZoneOffset standardOffset = rule.getStandardOffset();
        ZoneOffset offsetBefore = rule.getOffsetBefore();
        ZoneOffset offsetAfter = rule.getOffsetAfter();

        int timeSecs = (timeEndOfDay ? 86400 : time.toSecondOfDay());
        int stdOffset = standardOffset.getTotalSeconds();
        int beforeDiff = offsetBefore.getTotalSeconds() - stdOffset;
        int afterDiff = offsetAfter.getTotalSeconds() - stdOffset;
        int timeByte = (timeSecs % 3600 == 0 ? (timeEndOfDay ? 24 : time.getHour()) : 31);
        int stdOffsetByte = (stdOffset % 900 == 0 ? stdOffset / 900 + 128 : 255);
        int beforeByte = (beforeDiff == 0 || beforeDiff == 1800 || beforeDiff == 3600 ? beforeDiff / 1800 : 3);
        int afterByte = (afterDiff == 0 || afterDiff == 1800 || afterDiff == 3600 ? afterDiff / 1800 : 3);
        int dowByte = (dow == -1 ? 0 : dow);
        int b = (month << 28) +                     // 4 bytes
                ((dom + 32) << 22) +                // 6 bytes
                (dowByte << 19) +                   // 3 bytes
                (timeByte << 14) +                  // 5 bytes
                (timeDefinition.ordinal() << 12) +  // 2 bytes
                (stdOffsetByte << 4) +              // 8 bytes
                (beforeByte << 2) +                 // 2 bytes
                afterByte;                          // 2 bytes
        out.writeInt(b);
        if (timeByte == 31) {
            out.writeInt(timeSecs);
        }
        if (stdOffsetByte == 255) {
            out.writeInt(stdOffset);
        }
        if (beforeByte == 3) {
            out.writeInt(offsetBefore.getTotalSeconds());
        }
        if (afterByte == 3) {
            out.writeInt(offsetAfter.getTotalSeconds());
        }
    }

    /**
     * Checks if this set of rules equals another.
     * <p>
     * Two rule sets are equal if they will always result in the same output
     * for any given input instant or local date-time.
     * Rules from two different groups may return false even if they are in fact the same.
     * <p>
     * This definition should result in implementations comparing their entire state.
     *
     * @param otherRules  the other rules, null returns false
     * @return true if this rules is the same as that specified
     */
    @Override
    public boolean equals(Object otherRules) {
        if (this == otherRules) {
           return true;
        }
        if (otherRules instanceof ZoneRules) {
            ZoneRules other = (ZoneRules) otherRules;
            return Arrays.equals(standardTransitions, other.standardTransitions) &&
                    Arrays.equals(standardOffsets, other.standardOffsets) &&
                    Arrays.equals(savingsInstantTransitions, other.savingsInstantTransitions) &&
                    Arrays.equals(wallOffsets, other.wallOffsets) &&
                    Arrays.equals(lastRules, other.lastRules);
        }
        return false;
    }

    /**
     * Returns a suitable hash code given the definition of {@code #equals}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(standardTransitions) ^
                Arrays.hashCode(standardOffsets) ^
                Arrays.hashCode(savingsInstantTransitions) ^
                Arrays.hashCode(wallOffsets) ^
                Arrays.hashCode(lastRules);
    }

}
