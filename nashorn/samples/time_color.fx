#// Usage: jjs -fx time_color.js [-- true/false]

/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// A simple javafx program that changes background color
// of scene based on current time value (once per sec).
// inspired by http://whatcolourisit.scn9a.org/

if (!$OPTIONS._fx) {
    print("Usage: jjs -fx time_color.js");
    print("       jjs -fx time_color.js -- true");
    exit(1);
}

// JavaFX classes used
var Color = Java.type("javafx.scene.paint.Color");
var Group = Java.type("javafx.scene.Group");
var Label = Java.type("javafx.scene.control.Label");
var Platform = Java.type("javafx.application.Platform");
var Scene = Java.type("javafx.scene.Scene");
var Timer = Java.type("java.util.Timer");

// execute function periodically once per given time in millisec
function setInterval(func, ms) {
    // New timer, run as daemon so the application can quit
    var timer = new Timer("setInterval", true);
    timer.schedule(function() Platform.runLater(func), ms, ms);	
    return timer;
}

// do you want to flip hour/min/sec for RGB?
var flip = arguments.length > 0? "true".equals(arguments[0]) : false;

// JavaFX start method
function start(stage) {
    start.title = "Time Color";
    var root = new Group();
    var label = new Label("time");
    label.textFill = Color.WHITE;
    root.children.add(label); 
    stage.scene = new Scene(root, 700, 500);

    setInterval(function() {
        var d = new Date();
        var hours = d.getHours();
	var mins = d.getMinutes();
	var secs = d.getSeconds();

        if (hours < 10) hours = "0" + hours;
        if (mins < 10) mins = "0" + mins;
        if (secs < 10) secs = "0" + secs;

	var hex = flip?
            "#" + secs + mins + hours : "#" + hours + mins + secs;
        label.text = "Color: " + hex;
        stage.scene.fill = Color.web(hex);
    }, 1000);

    stage.show();
}
