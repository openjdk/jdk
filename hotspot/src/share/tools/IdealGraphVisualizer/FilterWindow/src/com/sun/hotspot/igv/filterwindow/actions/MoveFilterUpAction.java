/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */
package com.sun.hotspot.igv.filterwindow.actions;

import com.sun.hotspot.igv.filterwindow.FilterTopComponent;
import com.sun.hotspot.igv.filter.Filter;
import javax.swing.Action;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CookieAction;

/**
 *
 * @author Thomas Wuerthinger
 */
public final class MoveFilterUpAction extends CookieAction {

    protected void performAction(Node[] activatedNodes) {
        for (Node n : activatedNodes) {
            Filter c = n.getLookup().lookup(Filter.class);
            FilterTopComponent.findInstance().getSequence().moveFilterUp(c);
        }
    }

    protected int mode() {
        return CookieAction.MODE_EXACTLY_ONE;
    }

    public MoveFilterUpAction() {
        putValue(Action.SHORT_DESCRIPTION, "Move filter upwards");
    }

    public String getName() {
        return NbBundle.getMessage(MoveFilterUpAction.class, "CTL_MoveFilterUpAction");
    }

    protected Class[] cookieClasses() {
        return new Class[]{
            Filter.class
        };
    }

    @Override
    protected String iconResource() {
        return "com/sun/hotspot/igv/filterwindow/images/up.gif";
    }

    @Override
    protected void initialize() {
        super.initialize();
        putValue("noIconInMenu", Boolean.TRUE);
    }

    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }
}
