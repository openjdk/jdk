/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.dnd.peer.DragSourceContextPeer;
import java.awt.font.TextAttribute;
import java.awt.im.InputMethodHighlight;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.peer.*;
import java.net.URL;
import java.util.Map;
import java.util.Properties;


/**
 * @test
 * @bug 7172833
 * @summary java.awt.Toolkit methods is/setDynamicLayout() should be consistent.
 * @author Sergey Bylokhov
 */
public final class bug7172833 {

    public static void main(final String[] args) throws Exception {
        final StubbedToolkit t = new StubbedToolkit();

        t.setDynamicLayout(true);
        if(!t.isDynamicLayoutSet()){
            throw new RuntimeException("'true' expected but 'false' returned");
        }

        t.setDynamicLayout(false);
        if(t.isDynamicLayoutSet()){
            throw new RuntimeException("'false' expected but 'true' returned");
        }
    }

    static final class StubbedToolkit extends Toolkit {

        @Override
        protected boolean isDynamicLayoutSet() throws HeadlessException {
            return super.isDynamicLayoutSet();
        }

        @Override
        protected DesktopPeer createDesktopPeer(final Desktop target)
                throws HeadlessException {
            return null;
        }

        @Override
        protected ButtonPeer createButton(final Button target)
                throws HeadlessException {
            return null;
        }

        @Override
        protected TextFieldPeer createTextField(final TextField target)
                throws HeadlessException {
            return null;
        }

        @Override
        protected LabelPeer createLabel(final Label target) throws HeadlessException {
            return null;
        }

        @Override
        protected ListPeer createList(final List target) throws HeadlessException {
            return null;
        }

        @Override
        protected CheckboxPeer createCheckbox(final Checkbox target)
                throws HeadlessException {
            return null;
        }

        @Override
        protected ScrollbarPeer createScrollbar(final Scrollbar target)
                throws HeadlessException {
            return null;
        }

        @Override
        protected ScrollPanePeer createScrollPane(final ScrollPane target)
                throws HeadlessException {
            return null;
        }

        @Override
        protected TextAreaPeer createTextArea(final TextArea target)
                throws HeadlessException {
            return null;
        }

        @Override
        protected ChoicePeer createChoice(final Choice target)
                throws HeadlessException {
            return null;
        }

        @Override
        protected FramePeer createFrame(final Frame target) throws HeadlessException {
            return null;
        }

        @Override
        protected CanvasPeer createCanvas(final Canvas target) {
            return null;
        }

        @Override
        protected PanelPeer createPanel(final Panel target) {
            return null;
        }

        @Override
        protected WindowPeer createWindow(final Window target)
                throws HeadlessException {
            return null;
        }

        @Override
        protected DialogPeer createDialog(final Dialog target)
                throws HeadlessException {
            return null;
        }

        @Override
        protected MenuBarPeer createMenuBar(final MenuBar target)
                throws HeadlessException {
            return null;
        }

        @Override
        protected MenuPeer createMenu(final Menu target) throws HeadlessException {
            return null;
        }

        @Override
        protected PopupMenuPeer createPopupMenu(final PopupMenu target)
                throws HeadlessException {
            return null;
        }

        @Override
        protected MenuItemPeer createMenuItem(final MenuItem target)
                throws HeadlessException {
            return null;
        }

        @Override
        protected FileDialogPeer createFileDialog(final FileDialog target)
                throws HeadlessException {
            return null;
        }

        @Override
        protected CheckboxMenuItemPeer createCheckboxMenuItem(
                final CheckboxMenuItem target) throws HeadlessException {
            return null;
        }

        @Override
        protected FontPeer getFontPeer(final String name, final int style) {
            return null;
        }

        @Override
        public Dimension getScreenSize() throws HeadlessException {
            return null;
        }

        @Override
        public int getScreenResolution() throws HeadlessException {
            return 0;
        }

        @Override
        public ColorModel getColorModel() throws HeadlessException {
            return null;
        }

        @Override
        public String[] getFontList() {
            return new String[0];
        }

        @Override
        public FontMetrics getFontMetrics(final Font font) {
            return null;
        }

        @Override
        public void sync() {

        }

        @Override
        public Image getImage(final String filename) {
            return null;
        }

        @Override
        public Image getImage(final URL url) {
            return null;
        }

        @Override
        public Image createImage(final String filename) {
            return null;
        }

        @Override
        public Image createImage(final URL url) {
            return null;
        }

        @Override
        public boolean prepareImage(
                final Image image, final int width, final int height,
                                    final ImageObserver observer) {
            return false;
        }

        @Override
        public int checkImage(final Image image, final int width, final int height,
                              final ImageObserver observer) {
            return 0;
        }

        @Override
        public Image createImage(final ImageProducer producer) {
            return null;
        }

        @Override
        public Image createImage(final byte[] imagedata, final int imageoffset,
                                 final int imagelength) {
            return null;
        }

        @Override
        public PrintJob getPrintJob(final Frame frame, final String jobtitle,
                                    final Properties props) {
            return null;
        }

        @Override
        public void beep() {

        }

        @Override
        public Clipboard getSystemClipboard() throws HeadlessException {
            return null;
        }

        @Override
        protected EventQueue getSystemEventQueueImpl() {
            return null;
        }

        @Override
        public DragSourceContextPeer createDragSourceContextPeer(
                final DragGestureEvent dge) throws InvalidDnDOperationException {
            return null;
        }

        @Override
        public boolean isModalityTypeSupported(
                final Dialog.ModalityType modalityType) {
            return false;
        }

        @Override
        public boolean isModalExclusionTypeSupported(
                final Dialog.ModalExclusionType modalExclusionType) {
            return false;
        }

        @Override
        public Map<TextAttribute, ?> mapInputMethodHighlight(
                final InputMethodHighlight highlight) throws HeadlessException {
            return null;
        }
    }
}
