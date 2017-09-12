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

// Nashorn port of ColorfulCircles.java JavaFX animation example at
// https://docs.oracle.com/javafx/2/get_started/ColorfulCircles.java.html
// ColorfulCircles.java is under the following license terms:
 
/*
* Copyright (c) 2011, 2012 Oracle and/or its affiliates.
* All rights reserved. Use is subject to license terms.
*
* This file is available and licensed under the following license:
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions
* are met:
*
* - Redistributions of source code must retain the above copyright
* notice, this list of conditions and the following disclaimer.
* - Redistributions in binary form must reproduce the above copyright
* notice, this list of conditions and the following disclaimer in
* the documentation and/or other materials provided with the distribution.
* - Neither the name of Oracle nor the names of its
* contributors may be used to endorse or promote products derived
* from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
* "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
* LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
* A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
* OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
* SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
* DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
* THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
 
// Usage: jjs -fx colorfulcircles.fx
 
// Porting note: No imports - just load these fx scripts!
load("fx:controls.js");
load("fx:graphics.js");
 
// Porting note: whatever is inside
// public void start(Stage primaryStage)
// goes into "start" function
 
function start(primaryStage) {
    // Porting note: Replace types with 'var'. "Group root" becomes "var root".
    // and so on..
 
    var root = new Group();
    var scene = new Scene(root, 800, 600, Color.BLACK);
    primaryStage.setScene(scene);
    var circles = new Group();
    // Porting note: for (int i = 0....) becomes for (var i = 0...)
 
    for (var i = 0; i < 30; i++) {
        var circle = new Circle(150, Color.web("white", 0.05));
        circle.setStrokeType(StrokeType.OUTSIDE);
        circle.setStroke(Color.web("white", 0.16));
        circle.setStrokeWidth(4);
        circles.getChildren().add(circle);
    }
 
    // Porting note: There is no "f" suffix for float literals in JS.
    // LinearGradient(0f, 1f, 1f, 0f,..) becomes just
    // LinearGradient(0, 1, 1, 0,..)
 
    // Porting note: LinearGradient's constructor is a varargs method
    // No need to create Stop[] just pass more Stop objects at the end!
    var colors = new Rectangle(scene.getWidth(), scene.getHeight(),
       new LinearGradient(0, 1, 1, 0, true, CycleMethod.NO_CYCLE,
       new Stop(0, Color.web("#f8bd55")),
       new Stop(0.14, Color.web("#c0fe56")),
       new Stop(0.28, Color.web("#5dfbc1")),
       new Stop(0.43, Color.web("#64c2f8")),
       new Stop(0.57, Color.web("#be4af7")),
       new Stop(0.71, Color.web("#ed5fc2")),
       new Stop(0.85, Color.web("#ef504c")),
       new Stop(1, Color.web("#f2660f"))));
    colors.widthProperty().bind(scene.widthProperty());
    colors.heightProperty().bind(scene.heightProperty());
    var blendModeGroup =
       new Group(new Group(new Rectangle(scene.getWidth(), scene.getHeight(),
         Color.BLACK), circles), colors);
    colors.setBlendMode(BlendMode.OVERLAY);
    root.getChildren().add(blendModeGroup);
    circles.setEffect(new BoxBlur(10, 10, 3));
 
    // Porting note: Java code uses static import of
    // java.lang.Math.random. Just use JS Math.random here
    var random = Math.random;
 
    var timeline = new Timeline();
    // Porting note: Java enhanced for loop
    // for (Node circle : circles.getChildren())
    // becomes
    // for each (var circle: circles.getChildren())
  
    for each (var circle in circles.getChildren()) {
        timeline.getKeyFrames().addAll(
            new KeyFrame(Duration.ZERO, // set start position at 0
            new KeyValue(circle.translateXProperty(), random() * 800),
            new KeyValue(circle.translateYProperty(), random() * 600)),
            new KeyFrame(new Duration(40000), // set end position at 40s
            new KeyValue(circle.translateXProperty(), random() * 800),
            new KeyValue(circle.translateYProperty(), random() * 600)));
    }

    // play 40s of animation
    timeline.play();
    primaryStage.show();
} 
