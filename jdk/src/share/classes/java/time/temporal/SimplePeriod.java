/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Copyright (c) 2007-2012, Stephen Colebourne & Michael Nascimento Santos
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
package java.time.temporal;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.time.DateTimeException;
import java.util.Objects;

/**
 * A period of time, measured as an amount of a single unit, such as '3 Months'.
 * <p>
 * A {@code SimplePeriod} represents an amount of time measured in terms of a
 * single {@link TemporalUnit unit}. Any unit may be used with this class.
 * An alternative period implementation is {@link java.time.Period Period}, which
 * allows a combination of date and time units.
 * <p>
 * This class is the return type from {@link TemporalUnit#between}.
 * It can be used more generally, but is designed to enable the following code:
 * <pre>
 *  date = date.minus(MONTHS.between(start, end));
 * </pre>
 * The unit determines which calendar systems it can be added to.
 * <p>
 * The period is modeled as a directed amount of time, meaning that the period may
 * be negative. See {@link #abs()} to ensure the period is positive.
 *
 * <h3>Specification for implementors</h3>
 * This class is immutable and thread-safe, providing that the unit is immutable,
 * which it is required to be.
 *
 * @since 1.8
 */
public final class SimplePeriod
        implements TemporalAdder, TemporalSubtractor, Comparable<SimplePeriod>, Serializable {

    /**
     * Serialization version.
     */
    private static final long serialVersionUID = 3752975649629L;

    /**
     * The amount of the unit.
     */
    private final long amount;
    /**
     * The unit.
     */
    private final TemporalUnit unit;

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code SimplePeriod} from a period in the specified unit.
     * <p>
     * The parameters represent the two parts of a phrase like '6 Days'. For example:
     * <pre>
     *  SimplePeriod.of(3, SECONDS);
     *  SimplePeriod.of(5, YEARS);
     * </pre>
     *
     * @param amount  the amount of the period, measured in terms of the unit, positive or negative
     * @param unit  the unit that the period is measured in, not null
     * @return the period, not null
     */
    public static SimplePeriod of(long amount, TemporalUnit unit) {
        Objects.requireNonNull(unit, "unit");
        return new SimplePeriod(amount, unit);
    }

    //-----------------------------------------------------------------------
    /**
     * Constructor.
     *
     * @param amount  the amount of the period, measured in terms of the unit, positive or negative
     * @param unit  the unit that the period is measured in, not null
     */
    SimplePeriod(long amount, TemporalUnit unit) {
        this.amount = amount;
        this.unit = unit;
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the amount of this period.
     * <p>
     * In the phrase "2 Months", the amount is 2.
     *
     * @return the amount of the period, may be negative
     */
    public long getAmount() {
        return amount;
    }

    /**
     * Gets the unit of this period.
     * <p>
     * In the phrase "2 Months", the unit is "Months".
     *
     * @return the unit of the period, not null
     */
    public TemporalUnit getUnit() {
        return unit;
    }

    //-------------------------------------------------------------------------
    /**
     * Adds this period to the specified temporal object.
     * <p>
     * This returns a temporal object of the same observable type as the input
     * with this period added.
     * <p>
     * In most cases, it is clearer to reverse the calling pattern by using
     * {@link Temporal#plus(TemporalAdder)}.
     * <pre>
     *   // these two lines are equivalent, but the second approach is recommended
     *   dateTime = thisPeriod.addTo(dateTime);
     *   dateTime = dateTime.plus(thisPeriod);
     * </pre>
     * <p>
     * The calculation is equivalent to invoking {@link Temporal#plus(long, TemporalUnit)}.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param temporal  the temporal object to adjust, not null
     * @return an object of the same type with the adjustment made, not null
     * @throws DateTimeException if unable to add
     * @throws ArithmeticException if numeric overflow occurs
     */
    @Override
    public Temporal addTo(Temporal temporal) {
        return temporal.plus(amount, unit);
    }

    /**
     * Subtracts this period to the specified temporal object.
     * <p>
     * This returns a temporal object of the same observable type as the input
     * with this period subtracted.
     * <p>
     * In most cases, it is clearer to reverse the calling pattern by using
     * {@link Temporal#plus(TemporalAdder)}.
     * <pre>
     *   // these two lines are equivalent, but the second approach is recommended
     *   dateTime = thisPeriod.subtractFrom(dateTime);
     *   dateTime = dateTime.minus(thisPeriod);
     * </pre>
     * <p>
     * The calculation is equivalent to invoking {@link Temporal#minus(long, TemporalUnit)}.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param temporal  the temporal object to adjust, not null
     * @return an object of the same type with the adjustment made, not null
     * @throws DateTimeException if unable to subtract
     * @throws ArithmeticException if numeric overflow occurs
     */
    @Override
    public Temporal subtractFrom(Temporal temporal) {
        return temporal.minus(amount, unit);
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a copy of this period with a positive amount.
     * <p>
     * This returns a period with the absolute value of the amount and the same unit.
     * If the amount of this period is positive or zero, then this period is returned.
     * If the amount of this period is negative, then a period with the negated
     * amount is returned. If the amount equals {@code Long.MIN_VALUE},
     * an {@code ArithmeticException} is thrown
     * <p>
     * This is useful to convert the result of {@link TemporalUnit#between} to
     * a positive amount when you do not know which date is the earlier and
     * which is the later.
     *
     * @return a period with a positive amount and the same unit, not null
     * @throws ArithmeticException if the amount is {@code Long.MIN_VALUE}
     */
    public SimplePeriod abs() {
        if (amount == Long.MIN_VALUE) {
            throw new ArithmeticException("Unable to call abs() on MIN_VALUE");
        }
        return (amount >= 0 ? this : new SimplePeriod(-amount, unit));
    }

    //-----------------------------------------------------------------------
    /**
     * Compares this {@code SimplePeriod} to another period.
     * <p>
     * The comparison is based on the amount within the unit.
     * Only two periods with the same unit can be compared.
     *
     * @param other  the other period to compare to, not null
     * @return the comparator value, negative if less, positive if greater
     * @throws IllegalArgumentException if the units do not match
     */
    @Override
    public int compareTo(SimplePeriod other) {
        if (unit.equals(other.unit) == false) {
            throw new IllegalArgumentException("Unable to compare periods with different units");
        }
        return Long.compare(amount, other.amount);
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if this period is equal to another period.
     * <p>
     * The comparison is based on the amount and unit.
     *
     * @param obj  the object to check, null returns false
     * @return true if this is equal to the other period
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof SimplePeriod) {
            SimplePeriod other = (SimplePeriod) obj;
            return amount == other.amount && unit.equals(other.unit);
        }
        return false;
    }

    /**
     * A hash code for this period.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
        return unit.hashCode() ^ (int) (amount ^ (amount >>> 32));
    }

    //-----------------------------------------------------------------------
    /**
     * Outputs this period as a {@code String}, such as {@code 2 Months}.
     * <p>
     * The string consists of the amount, then a space, then the unit name.
     *
     * @return a string representation of this period, not null
     */
    @Override
    public String toString() {
        return amount + " " + unit.getName();
    }

    //-----------------------------------------------------------------------
    /**
     * Writes the object using a
     * <a href="../../../serialized-form.html#java.time.temporal.Ser">dedicated serialized form</a>.
     * <pre>
     *  out.writeByte(10);  // identifies this as a SimplePeriod
     *  out.writeLong(amount);
     *  out.writeObject(unit);
     * </pre>
     *
     * @return the instance of {@code Ser}, not null
     */
    private Object writeReplace() {
        return new Ser(Ser.SIMPLE_PERIOD_TYPE, this);
    }

    /**
     * Defend against malicious streams.
     * @return never
     * @throws InvalidObjectException always
     */
    private Object readResolve() throws ObjectStreamException {
        throw new InvalidObjectException("Deserialization via serialization delegate");
    }

    void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(amount);
        out.writeObject(unit);
    }

    static SimplePeriod readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        long amount = in.readLong();
        TemporalUnit unit = (TemporalUnit) in.readObject();
        return SimplePeriod.of(amount, unit);
    }

}
