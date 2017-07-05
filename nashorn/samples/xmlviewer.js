#jjs -fx xmlviewer.js [-- <url-of-xml-doc>]

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

if (! $OPTIONS._fx) {
    print("Usage: jjs -fx xmlviewer.js [-- <url-of-xml-doc>]");
    exit(1);
}

// Using JavaFX from Nashorn. See also:
// http://docs.oracle.com/javase/8/docs/technotes/guides/scripting/nashorn/javafx.html

// Simple sample to view a XML document as a JavaFX tree.

// JavaFX classes used
var StackPane = Java.type("javafx.scene.layout.StackPane");
var Scene     = Java.type("javafx.scene.Scene");
var TreeItem  = Java.type("javafx.scene.control.TreeItem");
var TreeView  = Java.type("javafx.scene.control.TreeView");

// XML DocumentBuilderFactory
var DocBuilderFac = Java.type("javax.xml.parsers.DocumentBuilderFactory");
var Attr = Java.type("org.w3c.dom.Attr");
var Element = Java.type("org.w3c.dom.Element");
var Text = Java.type("org.w3c.dom.Text");

// parse XML from uri and return Document
function parseXML(uri) {
    var docBuilder = DocBuilderFac.newInstance().newDocumentBuilder();
    docBuilder.validating = false;
    return docBuilder["parse(java.lang.String)"](uri);
}

// Create a javafx TreeItem to view a XML element
function treeItemForObject(element, name) {
    var item = new TreeItem(name);
    item.expanded = true;
    var attrs = element.attributes;
    var numAttrs = attrs.length;
    for (var a = 0; a < numAttrs; a++) {
        var attr = attrs.item(a);
        var subitem = new TreeItem(attr.name + " = " + attr.value);
        item.children.add(subitem);
    }

    var childNodes = element.childNodes;
    var numNodes = childNodes.length;
    for (var n = 0; n < numNodes; n++) {
       var node = childNodes.item(n);
       if (node instanceof Element) {
           var subitem = treeItemForObject(node, node.tagName);
           item.children.add(subitem);
       }
    }
    
    return item;
}

// Ofcourse, the best default URL is cricket score :) 
var DEFAULT_URL = "http://synd.cricbuzz.com/j2me/1.0/livematches.xml";

var url = arguments.length == 0? DEFAULT_URL : arguments[0];
var element = parseXML(url).getDocumentElement();

// JavaFX start method
function start(stage) {
    stage.title = "XML Viewer: " + url;
    var rootItem = treeItemForObject(element, element.tagName);
    var tree = new TreeView(rootItem);
    var root = new StackPane();
    root.children.add(tree);
    stage.scene = new Scene(root, 300, 450);
    stage.show();
}
