#Usage: jjs -fx fxmlrunner.js -- <.fxml file>

/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 
// See also https://docs.oracle.com/javase/8/javafx/api/javafx/fxml/doc-files/introduction_to_fxml.html

// Simple script to "run" a .FXML file specified in
// command line. FXML file is expected to have inline
// fx:script to handle GUI events. i.e., self-contained
// FXML file is assumed.
 
var file = arguments[0];
var File = Java.type("java.io.File"); 
if (!$OPTIONS._fx || !file || !new File(file).isFile()) {
    print("Usage: jjs -fx fxmlrunner.js -- <.fxml file> [width] [height]");
    exit(1);
}

// optional stage width and height from command line
var width = arguments[1]? parseInt(arguments[1]) : 400;
var height = arguments[2]? parseInt(arguments[2]) : 300;

// JavaFX classes used
var FXMLLoader = Java.type("javafx.fxml.FXMLLoader");
var Scene = Java.type("javafx.scene.Scene");
 
function start(stage) {
    // load FXML
    var root = FXMLLoader.load(new File(file).toURL());
    // show it in a scene
    var scene = new Scene(root, width, height);
    stage.title = file;
    stage.scene = scene;
    stage.show();
} 
