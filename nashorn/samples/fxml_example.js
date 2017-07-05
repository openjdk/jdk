#Usage: jjs -fx fxml_example.js
#nashorn simple example using FXML with #javafx

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
 
if (! $OPTIONS._fx) {
    print("Usage: jjs -fx fxml_example.js");
    exit(1);
}
 
// inline FXML document here
var fxml = <<EOF
 
<?import javafx.scene.*?>
<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>
 
<VBox xmlns:fx="http://javafx.com/fxml">
    <children>
    <!-- ids will be used script later -->
    <HBox>
        <Label text="Your name please:"/>
        <TextField fx:id="nameText" text="Nashorn"/>
    </HBox>
    <Button fx:id="clickButton" text="Click!"/>
    </children>
</VBox>
 
EOF
 
// Java and FX classes used
var ByteArrayInputStream = Java.type("java.io.ByteArrayInputStream");
var FXMLLoader = Java.type("javafx.fxml.FXMLLoader");
var Scene = Java.type("javafx.scene.Scene");
 
function start(stage) {
    var loader = new FXMLLoader();
    // load FXML from a string
    var root = loader.load(new ByteArrayInputStream(fxml.getBytes("UTF-8")));
 
    // get the button and the text field controls
    var button = root.lookup("#clickButton");
    var textField = root.lookup("#nameText");
 
    // event handler for button
    var clickCount = 0;
    button.onAction = function() {
        print(textField.text + ", you clicked me: " + ++clickCount + " time(s)");
    }
 
    var scene = new Scene(root, 300, 275);
    stage.title = "FXML Example";
    stage.scene = scene;
    stage.show();
} 
