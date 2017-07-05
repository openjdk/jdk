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

import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;

/**
 * Software synthesizer internal instrument.
 *
 * @author Karl Helgason
 */
public class SoftInstrument extends Instrument {

    private SoftPerformer[] performers;
    private ModelPerformer[] modelperformers;
    private Object data;
    private ModelInstrument ins;

    public SoftInstrument(ModelInstrument ins) {
        super(ins.getSoundbank(), ins.getPatch(), ins.getName(),
                ins.getDataClass());
        data = ins.getData();
        this.ins = ins;
        initPerformers(((ModelInstrument)ins).getPerformers());
    }

    public SoftInstrument(ModelInstrument ins,
            ModelPerformer[] overrideperformers) {
        super(ins.getSoundbank(), ins.getPatch(), ins.getName(),
                ins.getDataClass());
        data = ins.getData();
        this.ins = ins;
        initPerformers(overrideperformers);
    }

    private void initPerformers(ModelPerformer[] modelperformers) {
        this.modelperformers = modelperformers;
        performers = new SoftPerformer[modelperformers.length];
        for (int i = 0; i < modelperformers.length; i++)
            performers[i] = new SoftPerformer(modelperformers[i]);
    }

    public ModelDirector getDirector(MidiChannel channel,
            ModelDirectedPlayer player) {
        return ins.getDirector(modelperformers, channel, player);
    }

    public ModelInstrument getSourceInstrument() {
        return ins;
    }

    public Object getData() {
        return data;
    }

    public SoftPerformer[] getPerformers() {
        return performers;
    }
}
