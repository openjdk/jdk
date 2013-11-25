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

/**
 * Testing JavaFX canvas run by Nashorn.
 *
 * @test/nocompare
 * @run
 * @fork
 */

TESTNAME = "flyingimage";

var WIDTH = 800;
var HEIGHT = 600;
var canvas = new Canvas(WIDTH, HEIGHT);
function fileToURL(file) {
    return new File(file).toURI().toURL().toExternalForm();
}
var imageUrl = fileToURL(__DIR__ + "flyingimage/flyingimage.png");
var img = new Image(imageUrl);
var isFrameRendered = false;
function renderFrame() {
    var t = frame;
    var gc = canvas.graphicsContext2D;
    gc.setFill(Color.web("#cccccc"));
    gc.fillRect(0, 0, WIDTH, HEIGHT);
    gc.setStroke(Color.web("#000000"));
    gc.setLineWidth(1);
    gc.strokeRect(5, 5, WIDTH - 10, HEIGHT - 10);
    var c = 200;
    var msc= 0.5 * HEIGHT / img.height;
    var sp0 = 0.003;
    for (var h = 0; h < c; h++) {
        gc.setTransform(1, 0, 0, 1, 0, 0);
        var yh = h / (c - 1);
        gc.translate((0.5 + Math.sin(t * sp0 + h * 0.1) / 3) * WIDTH, 25 + (HEIGHT * 3 / 4 - 40) * (yh * yh));
        var sc = 30 / img.height + msc * yh * yh;
        gc.rotate(90 * Math.sin(t * sp0 + h * 0.1 + Math.PI));
        gc.scale(sc, sc);
        gc.drawImage(img, -img.width / 2, -img.height / 2);
    }
    gc.setTransform(1, 0, 0, 1, 0, 0);
    isFrameRendered = true;
}
var stack = new StackPane();
var pane = new BorderPane();
pane.setCenter(canvas);
stack.getChildren().add(pane);
$STAGE.scene = new Scene(stack);
var frame = 0;
var timer = new AnimationTimerExtend() {
    handle: function handle(now) {
        if (frame < 200) {
            renderFrame();
            frame++;
        } else {
            checkImageAndExit();        
            timer.stop();
        }
    }
};
timer.start();
 
