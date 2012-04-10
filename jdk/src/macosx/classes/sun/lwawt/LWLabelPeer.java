/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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


package sun.lwawt;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Label;
import java.awt.peer.LabelPeer;

import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * Lightweight implementation of {@link LabelPeer}. Delegates most of the work
 * to the {@link JLabel}.
 */
final class LWLabelPeer extends LWComponentPeer<Label, JLabel>
        implements LabelPeer {

    private static final int TEXT_XPAD = 5;
    private static final int TEXT_YPAD = 1;

    LWLabelPeer(final Label target, final PlatformComponent platformComponent) {
        super(target, platformComponent);
    }

    @Override
    protected JLabel createDelegate() {
        final JLabel label = new JLabel();
        label.setVerticalAlignment(SwingConstants.TOP);
        return label;
    }

    @Override
    public void initialize() {
        super.initialize();
        setText(getTarget().getText());
        setAlignment(getTarget().getAlignment());
    }

    @Override
    public void setText(final String label) {
        synchronized (getDelegateLock()) {
            getDelegate().setText(label);
        }
    }

    @Override
    public void setAlignment(final int alignment) {
        synchronized (getDelegateLock()) {
            getDelegate().setHorizontalAlignment(convertAlignment(alignment));
        }
    }

    @Override
    public Dimension getMinimumSize() {
        int w = TEXT_XPAD;
        int h = TEXT_YPAD;
        final FontMetrics fm = getFontMetrics(getFont());
        if (fm != null) {
            final String text;
            synchronized (getDelegateLock()) {
                text = getDelegate().getText();
            }
            if (text != null) {
                w += fm.stringWidth(text);
            }
            h += fm.getHeight();
        }
        return new Dimension(w, h);
    }

    /**
     * Converts {@code Label} alignment constant to the {@code JLabel} constant.
     * If wrong Label alignment provided returns default alignment.
     *
     * @param alignment {@code Label} constant.
     *
     * @return {@code JLabel} constant.
     */
    private static int convertAlignment(final int alignment) {
        switch (alignment) {
            case Label.CENTER:
                return SwingConstants.CENTER;
            case Label.RIGHT:
                return SwingConstants.RIGHT;
            default:
                return SwingConstants.LEFT;
        }
    }
}
