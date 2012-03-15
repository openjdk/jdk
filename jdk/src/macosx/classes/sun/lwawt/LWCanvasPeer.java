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

import java.awt.BufferCapabilities;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.peer.CanvasPeer;

import javax.swing.JComponent;

final class LWCanvasPeer extends LWComponentPeer<Component, JComponent>
        implements CanvasPeer {

    LWCanvasPeer(final Canvas target, PlatformComponent platformComponent) {
        super(target, platformComponent);
    }

    // ---- PEER METHODS ---- //

    @Override
    public void createBuffers(int numBuffers, BufferCapabilities caps) {
        // TODO
    }

    @Override
    public Image getBackBuffer() {
        // TODO
        return null;
    }

    @Override
    public void flip(int x1, int y1, int x2, int y2,
                     BufferCapabilities.FlipContents flipAction) {
        // TODO
    }

    @Override
    public void destroyBuffers() {
        // TODO
    }

    @Override
    public GraphicsConfiguration getAppropriateGraphicsConfiguration(
            GraphicsConfiguration gc)
    {
        // TODO
        return gc;
    }
}
