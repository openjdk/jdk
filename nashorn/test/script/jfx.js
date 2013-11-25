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
 * Base library for JavaFX canvas run by Nashorn testing.
 * @subtest
 * 
 * 
 */

var System               = Java.type("java.lang.System");
var AWTImage             = Java.type("org.jemmy.image.AWTImage");
var PNGDecoder           = Java.type("org.jemmy.image.PNGDecoder");
var JemmyFxRoot          = Java.type("org.jemmy.fx.Root");
var AWTRobotCapturer     = Java.type("org.jemmy.image.AWTRobotCapturer");
var ByWindowType         = Java.type("org.jemmy.fx.ByWindowType");
var Scene                = Java.type("javafx.scene.Scene");
var Stage                = Java.type("javafx.stage.Stage");
var File                 = Java.type("java.io.File");
var OSInfo               = Java.type("sun.awt.OSInfo");
var OSType               = Java.type("sun.awt.OSInfo.OSType");
var StringBuffer         = Java.type("java.lang.StringBuffer");
var Paint                = Java.type("javafx.scene.paint.Paint");
var Color                = Java.type("javafx.scene.paint.Color");
var Image                = Java.type("javafx.scene.image.Image");
var Canvas               = Java.type("javafx.scene.canvas.Canvas");
var BorderPane           = Java.type("javafx.scene.layout.BorderPane");
var StackPane            = Java.type("javafx.scene.layout.StackPane");
var StrokeLineCap        = Java.type("javafx.scene.shape.StrokeLineCap");
var Platform             = Java.type("javafx.application.Platform");
var Runnable             = Java.type("java.lang.Runnable");
var RunnableExtend       = Java.extend(Runnable);
var AnimationTimer       = Java.type("javafx.animation.AnimationTimer");
var AnimationTimerExtend = Java.extend(AnimationTimer);
var Timer                = Java.type("java.util.Timer");
var TimerTask            = Java.type("java.util.TimerTask");

var TESTNAME = "test";
var fsep = System.getProperty("file.separator");

function checkImageAndExit() {
    var raceTimer = new Timer(true);
    var timerTask = new TimerTask() {
        run: function run() {
            var tmpdir = System.getProperty("java.io.tmpdir");
            var timenow = (new Date()).getTime();
            var scrShotTmp = tmpdir + fsep + "screenshot" + timenow +".png";
            var goldenImageDir = __DIR__ + "jfx" + fsep + TESTNAME + fsep + "golden";
            makeScreenShot(scrShotTmp);
            var dupImg = isDuplicateImages(scrShotTmp, goldenImageDir);
            (new File(scrShotTmp)).delete();
            if (!dupImg) System.err.println("ERROR: screenshot does not match the golden image");
            exit(0);
        }
    };
    raceTimer.schedule(timerTask, 100);
}

function makeScreenShot(shootToImg) {
   JemmyFxRoot.ROOT.getEnvironment().setImageCapturer(new AWTRobotCapturer());
   var wrap = JemmyFxRoot.ROOT.lookup(new ByWindowType($STAGE.class)).lookup(Scene.class).wrap(0);
   var imageJemmy = wrap.getScreenImage();
   imageJemmy.save(shootToImg);
}

function isDuplicateImages(screenShot, goldenDir) {
    var f1 = new File(screenShot);
    var f2;
    var sb = new StringBuffer(goldenDir);
    if (OSInfo.getOSType() == OSType.WINDOWS) {
        f2 = new File(sb.append(fsep + "windows.png").toString());
    } else if (OSInfo.getOSType() == OSType.LINUX) {
        f2 = new File(sb.append(fsep + "linux.png").toString());
    } else if (OSInfo.getOSType() == OSType.MACOSX) {
        f2 = new File(sb.append(fsep + "macosx.png").toString());
    }
    if (f1.exists() && f2.exists()) {
        var image1 = new AWTImage(PNGDecoder.decode(f1.getAbsolutePath()));
        var image2 = new AWTImage(PNGDecoder.decode(f2.getAbsolutePath()));
        return image1.compareTo(image2) == null ? true : false;
    }
    return false;
}
