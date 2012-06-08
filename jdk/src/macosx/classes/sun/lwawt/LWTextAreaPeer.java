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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.TextArea;
import java.awt.event.TextEvent;
import java.awt.peer.TextAreaPeer;

import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

final class LWTextAreaPeer
        extends LWTextComponentPeer<TextArea, LWTextAreaPeer.ScrollableJTextArea>
        implements TextAreaPeer {

    private static final int DEFAULT_COLUMNS = 60;
    private static final int DEFAULT_ROWS = 10;

    LWTextAreaPeer(final TextArea target,
                   final PlatformComponent platformComponent) {
        super(target, platformComponent);
    }

    @Override
    protected ScrollableJTextArea createDelegate() {
        return new ScrollableJTextArea();
    }

    @Override
    public void initialize() {
        super.initialize();
        final int visibility = getTarget().getScrollbarVisibility();
        synchronized (getDelegateLock()) {
            setScrollBarVisibility(visibility);
        }
    }

    @Override
    JTextComponent getTextComponent() {
        return getDelegate().getView();
    }

    @Override
    protected Cursor getCursor(final Point p) {
        final boolean isContains;
        synchronized (getDelegateLock()) {
            isContains = getDelegate().getViewport().getBounds().contains(p);
        }
        return isContains ? super.getCursor(p) : null;
    }

    @Override
    protected Component getDelegateFocusOwner() {
        return getTextComponent();
    }

    @Override
    public Dimension getMinimumSize() {
        return getMinimumSize(DEFAULT_ROWS, DEFAULT_COLUMNS);
    }

    @Override
    public Dimension getMinimumSize(final int rows, final int columns) {
        return getPreferredSize(rows, columns);
    }

    @Override
    public Dimension getPreferredSize(final int rows, final int columns) {
        final Dimension size = super.getPreferredSize(rows, columns);
        synchronized (getDelegateLock()) {
            final JScrollBar vbar = getDelegate().getVerticalScrollBar();
            final JScrollBar hbar = getDelegate().getHorizontalScrollBar();
            final int scrollbarW = vbar != null ? vbar.getWidth() : 0;
            final int scrollbarH = hbar != null ? hbar.getHeight() : 0;
            return new Dimension(size.width + scrollbarW,
                                 size.height + scrollbarH);
        }
    }

    @Override
    public void insert(final String text, final int pos) {
        final ScrollableJTextArea pane = getDelegate();
        synchronized (getDelegateLock()) {
            final JTextArea area = pane.getView();
            final boolean doScroll = pos >= area.getDocument().getLength()
                                     && area.getDocument().getLength() != 0;
            area.insert(text, pos);
            revalidate();
            if (doScroll) {
                final JScrollBar vbar = pane.getVerticalScrollBar();
                if (vbar != null) {
                    vbar.setValue(vbar.getMaximum() - vbar.getVisibleAmount());
                }
            }
        }
        repaintPeer();
    }

    @Override
    public void setText(final String l) {
        // Please note that we do not want to post an event
        // if TextArea.setText() replaces an empty text by an empty text,
        // that is, if component's text remains unchanged.
        if (!l.isEmpty() || getTextComponent().getDocument().getLength() != 0) {
            super.setText(l);
        }
    }

    @Override
    public void replaceRange(final String text, final int start,
                             final int end) {
        synchronized (getDelegateLock()) {
            // JTextArea.replaceRange() posts two different events.
            // Since we make no differences between text events,
            // the document listener has to be disabled while
            // JTextArea.replaceRange() is called.
            final Document document = getTextComponent().getDocument();
            document.removeDocumentListener(this);
            getDelegate().getView().replaceRange(text, start, end);
            revalidate();
            postEvent(new TextEvent(getTarget(), TextEvent.TEXT_VALUE_CHANGED));
            document.addDocumentListener(this);
        }
        repaintPeer();
    }

    private void setScrollBarVisibility(final int visibility) {
        final ScrollableJTextArea pane = getDelegate();
        final JTextArea view = pane.getView();
        view.setLineWrap(false);

        switch (visibility) {
            case TextArea.SCROLLBARS_NONE:
                pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
                view.setLineWrap(true);
                break;
            case TextArea.SCROLLBARS_VERTICAL_ONLY:
                pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
                view.setLineWrap(true);
                break;
            case TextArea.SCROLLBARS_HORIZONTAL_ONLY:
                pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
                pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
                break;
            default:
                pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
                pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
                break;
        }
    }

    @SuppressWarnings("serial")
    final class ScrollableJTextArea extends JScrollPane {

        ScrollableJTextArea() {
            super();
            getViewport().setView(new JTextAreaDelegate());
        }

        public JTextArea getView() {
            return (JTextArea) getViewport().getView();
        }

        @Override
        public void setEnabled(final boolean enabled) {
            getViewport().getView().setEnabled(enabled);
            super.setEnabled(enabled);
        }

        @SuppressWarnings("serial")
        private final class JTextAreaDelegate extends JTextArea {

            // Empty non private constructor was added because access to this
            // class shouldn't be emulated by a synthetic accessor method.
            JTextAreaDelegate() {
                super();
            }

            @Override
            public void replaceSelection(String content) {
                getDocument().removeDocumentListener(LWTextAreaPeer.this);
                super.replaceSelection(content);
                // post only one text event in this case
                postTextEvent();
                getDocument().addDocumentListener(LWTextAreaPeer.this);
            }

            @Override
            public boolean hasFocus() {
                return getTarget().hasFocus();
            }

            @Override
            public Point getLocationOnScreen() {
                return LWTextAreaPeer.this.getLocationOnScreen();
            }
        }
    }
}
