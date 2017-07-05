/*
 * Copyright (c) 2000, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt;

import java.awt.*;
import java.awt.dnd.*;
import java.awt.dnd.peer.DragSourceContextPeer;
import java.awt.peer.*;

/**
 * Interface for component creation support in toolkits
 */
public interface ComponentFactory {

    CanvasPeer createCanvas(Canvas target) throws HeadlessException;

    PanelPeer createPanel(Panel target) throws HeadlessException;

    WindowPeer createWindow(Window target) throws HeadlessException;

    FramePeer createFrame(Frame target) throws HeadlessException;

    DialogPeer createDialog(Dialog target) throws HeadlessException;

    ButtonPeer createButton(Button target) throws HeadlessException;

    TextFieldPeer createTextField(TextField target)
        throws HeadlessException;

    ChoicePeer createChoice(Choice target) throws HeadlessException;

    LabelPeer createLabel(Label target) throws HeadlessException;

    ListPeer createList(List target) throws HeadlessException;

    CheckboxPeer createCheckbox(Checkbox target)
        throws HeadlessException;

    ScrollbarPeer createScrollbar(Scrollbar target)
        throws HeadlessException;

    ScrollPanePeer createScrollPane(ScrollPane target)
        throws HeadlessException;

    TextAreaPeer createTextArea(TextArea target)
        throws HeadlessException;

    FileDialogPeer createFileDialog(FileDialog target)
        throws HeadlessException;

    MenuBarPeer createMenuBar(MenuBar target) throws HeadlessException;

    MenuPeer createMenu(Menu target) throws HeadlessException;

    PopupMenuPeer createPopupMenu(PopupMenu target)
        throws HeadlessException;

    MenuItemPeer createMenuItem(MenuItem target)
        throws HeadlessException;

    CheckboxMenuItemPeer createCheckboxMenuItem(CheckboxMenuItem target)
        throws HeadlessException;

    DragSourceContextPeer createDragSourceContextPeer(
        DragGestureEvent dge)
        throws InvalidDnDOperationException, HeadlessException;

    FontPeer getFontPeer(String name, int style);

    RobotPeer createRobot(Robot target, GraphicsDevice screen)
        throws AWTException, HeadlessException;

}
