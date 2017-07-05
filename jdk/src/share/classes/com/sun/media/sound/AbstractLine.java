/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.media.sound;

import java.util.Vector;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Control;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;


/**
 * AbstractLine
 *
 * @author Kara Kytle
 */
abstract class AbstractLine implements Line {

    protected Line.Info info;
    protected Control[] controls;
    protected AbstractMixer mixer;
    private boolean open     = false;
    private Vector listeners = new Vector();

    /**
     * Global event thread
     */
    private static final EventDispatcher eventDispatcher;

    static {
        // create and start the global event thread

        // $$kk: 12.21.98:
        // 1) probably don't want a single global event queue
        // 2) need a way to stop this thread when the engine is done

        eventDispatcher = new EventDispatcher();
        eventDispatcher.start();
    }


    /**
     * Constructs a new AbstractLine.
     * @param mixer the mixer with which this line is associated
     * @param controls set of supported controls
     */
    protected AbstractLine(Line.Info info, AbstractMixer mixer, Control[] controls) {

        if (controls == null) {
            controls = new Control[0];
        }

        this.info = info;
        this.mixer = mixer;
        this.controls = controls;
    }


    // LINE METHODS

    public Line.Info getLineInfo() {
        return info;
    }


    public boolean isOpen() {
        return open;
    }


    public void addLineListener(LineListener listener) {

        synchronized(listeners) {
            if ( ! (listeners.contains(listener)) ) {
                listeners.addElement(listener);
            }
        }
    }


    /**
     * Removes an audio listener.
     * @param listener listener to remove
     */
    public void removeLineListener(LineListener listener) {
        listeners.removeElement(listener);
    }


    /**
     * Obtains the set of controls supported by the
     * line.  If no controls are supported, returns an
     * array of length 0.
     * @return control set
     */
    public Control[] getControls() {

        Control[] returnedArray = new Control[controls.length];

        for (int i = 0; i < controls.length; i++) {
            returnedArray[i] = controls[i];
        }

        return returnedArray;
    }


    public boolean isControlSupported(Control.Type controlType) {

        // protect against a NullPointerException
        if (controlType == null) {
            return false;
        }

        for (int i = 0; i < controls.length; i++) {
            if (controlType == controls[i].getType()) {
                return true;
            }
        }

        return false;
    }


    public Control getControl(Control.Type controlType) {

        // protect against a NullPointerException
        if (controlType != null) {

            for (int i = 0; i < controls.length; i++) {
                if (controlType == controls[i].getType()) {
                    return controls[i];
                }
            }
        }

        throw new IllegalArgumentException("Unsupported control type: " + controlType);
    }


    // HELPER METHODS


    /**
     * This method sets the open state and generates
     * events if it changes.
     */
    protected void setOpen(boolean open) {

        if (Printer.trace) Printer.trace("> "+getClass().getName()+" (AbstractLine): setOpen(" + open + ")  this.open: " + this.open);

        boolean sendEvents = false;
        long position = getLongFramePosition();

        synchronized (this) {
            if (this.open != open) {
                this.open = open;
                sendEvents = true;
            }
        }

        if (sendEvents) {
            if (open) {
                sendEvents(new LineEvent(this, LineEvent.Type.OPEN, position));
            } else {
                sendEvents(new LineEvent(this, LineEvent.Type.CLOSE, position));
            }
        }
        if (Printer.trace) Printer.trace("< "+getClass().getName()+" (AbstractLine): setOpen(" + open + ")  this.open: " + this.open);
    }


    /**
     * Send line events.
     */
    protected void sendEvents(LineEvent event) {
        eventDispatcher.sendAudioEvents(event, listeners);
    }


    /**
     * This is an error in the API: getFramePosition
     * should return a long value. At CD quality,
     * the int value wraps around after 13 hours.
     */
    public final int getFramePosition() {
        return (int) getLongFramePosition();
    }


    /**
     * Return the frame position in a long value
     * This implementation returns AudioSystem.NOT_SPECIFIED.
     */
    public long getLongFramePosition() {
        return AudioSystem.NOT_SPECIFIED;
    }


    // $$kk: 06.03.99: returns the mixer used in construction.
    // this is a hold-over from when there was a public method like
    // this on line and should be fixed!!
    protected AbstractMixer getMixer() {
        return mixer;
    }

    protected EventDispatcher getEventDispatcher() {
        return eventDispatcher;
    }

    // ABSTRACT METHODS

    public abstract void open() throws LineUnavailableException;
    public abstract void close();
}
