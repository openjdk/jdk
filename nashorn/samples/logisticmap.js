#// Usage: jjs -fx -scripting logisticmap.js -- <initial_x> <R>

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

// Logistic map viewer using Java8 Streams and JavaFX
// See also http://en.wikipedia.org/wiki/Logistic_map

if (!$OPTIONS._fx || arguments.length < 2) {
    print("Usage: jjs -fx -scripting logisticmap.js -- <initial_x> <R>");
    exit(1);
}

// parameters for the logistic map
var x = parseFloat(arguments[0]);
var R = parseFloat(arguments[1]);
var NUM_POINTS = arguments.length > 2? parseFloat(arguments[2]) : 20;

// Java classes used
var DoubleStream = Java.type('java.util.stream.DoubleStream');
var LineChart = Java.type("javafx.scene.chart.LineChart");
var NumberAxis = Java.type("javafx.scene.chart.NumberAxis");
var Scene = Java.type("javafx.scene.Scene");
var Stage = Java.type("javafx.stage.Stage");
var XYChart = Java.type("javafx.scene.chart.XYChart");

function start(stage) {
    stage.title = "Logistic Map: initial x = ${x}, R = ${R}";
    // make chart
    var xAxis = new NumberAxis();
    var yAxis = new NumberAxis();
    var lineChart = new LineChart(xAxis, yAxis);
    xAxis.setLabel("iteration");
    yAxis.setLabel("x");
    // make chart data series
    var series = new XYChart.Series();
    var data = series.data;
    // populate data using logistic iteration
    var i = 0;
    DoubleStream
        .generate(function() x = R*x*(1-x))
        .limit(NUM_POINTS)
        .forEach(
            function(value) {
                data.add(new XYChart.Data(i, value));
                i++;
            }
         );
    // add to stage
    var scene = new Scene(lineChart, 800, 600);
    lineChart.data.add(series);
    stage.scene = scene;
    stage.show();
}
