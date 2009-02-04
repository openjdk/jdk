/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.media.sound;

/**
 * Soundfont modulator container.
 *
 * @author Karl Helgason
 */
public class SF2Modulator {

    public final static int SOURCE_NONE = 0;
    public final static int SOURCE_NOTE_ON_VELOCITY = 2;
    public final static int SOURCE_NOTE_ON_KEYNUMBER = 3;
    public final static int SOURCE_POLY_PRESSURE = 10;
    public final static int SOURCE_CHANNEL_PRESSURE = 13;
    public final static int SOURCE_PITCH_WHEEL = 14;
    public final static int SOURCE_PITCH_SENSITIVITY = 16;
    public final static int SOURCE_MIDI_CONTROL = 128 * 1;
    public final static int SOURCE_DIRECTION_MIN_MAX = 256 * 0;
    public final static int SOURCE_DIRECTION_MAX_MIN = 256 * 1;
    public final static int SOURCE_POLARITY_UNIPOLAR = 512 * 0;
    public final static int SOURCE_POLARITY_BIPOLAR = 512 * 1;
    public final static int SOURCE_TYPE_LINEAR = 1024 * 0;
    public final static int SOURCE_TYPE_CONCAVE = 1024 * 1;
    public final static int SOURCE_TYPE_CONVEX = 1024 * 2;
    public final static int SOURCE_TYPE_SWITCH = 1024 * 3;
    public final static int TRANSFORM_LINEAR = 0;
    public final static int TRANSFORM_ABSOLUTE = 2;
    protected int sourceOperator;
    protected int destinationOperator;
    protected short amount;
    protected int amountSourceOperator;
    protected int transportOperator;

    public short getAmount() {
        return amount;
    }

    public void setAmount(short amount) {
        this.amount = amount;
    }

    public int getAmountSourceOperator() {
        return amountSourceOperator;
    }

    public void setAmountSourceOperator(int amountSourceOperator) {
        this.amountSourceOperator = amountSourceOperator;
    }

    public int getTransportOperator() {
        return transportOperator;
    }

    public void setTransportOperator(int transportOperator) {
        this.transportOperator = transportOperator;
    }

    public int getDestinationOperator() {
        return destinationOperator;
    }

    public void setDestinationOperator(int destinationOperator) {
        this.destinationOperator = destinationOperator;
    }

    public int getSourceOperator() {
        return sourceOperator;
    }

    public void setSourceOperator(int sourceOperator) {
        this.sourceOperator = sourceOperator;
    }
}
