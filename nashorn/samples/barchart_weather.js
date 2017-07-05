#// Usage: jjs -fx barchart_weather.js

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

// Example that retrieves weather data from a URL in JSON
// format and draws bar chart using JavaFX

// -fx check
if (! $OPTIONS._fx) {
    print("Usage: jjs -fx barchart_weather.js");
    exit(1);
}

// Java classes used
var URL = Java.type("java.net.URL");
var BufferedReader = Java.type("java.io.BufferedReader");
var InputStreamReader = Java.type("java.io.InputStreamReader");

// function to retrieve text content of the given URL
function readTextFromURL(url) {
    var str = '';
    var u = new URL(url);
    var reader = new BufferedReader(
        new InputStreamReader(u.openStream()));
    try {
        reader.lines().forEach(function(x) str += x);
        return str;
    } finally {
        reader.close();
    }
}

// change URL for your city here!
var url = "http://api.openweathermap.org/data/2.5/forecast?q=chennai,india&units=metric&mode=json";

// download JSON document and parse
var json = readTextFromURL(url);
var weather = JSON.parse(json);

// View JSON of this using site such as http://www.jsoneditoronline.org/ to know
// about the JSON data format used by this site

// Extracted data from the json object
var temp = weather.list.map(function(x) x.main.temp);
var temp_min = weather.list.map(function(x) x.main.temp_min);
var temp_max = weather.list.map(function(x) x.main.temp_max);
var date = weather.list.map(function(x) x.dt_txt);

// JavaFX classes used
var Scene = Java.type("javafx.scene.Scene");
var BarChart = Java.type("javafx.scene.chart.BarChart");
var CategoryAxis = Java.type("javafx.scene.chart.CategoryAxis");
var NumberAxis = Java.type("javafx.scene.chart.NumberAxis");
var XYChart = Java.type("javafx.scene.chart.XYChart");

function start(stage) {
    stage.title="Chennai Weather Bar Chart";
    var xAxis = new CategoryAxis();
    xAxis.label = "date/time";
    var yAxis = new NumberAxis();
    yAxis.label = "temp in C";
    var bc = new BarChart(xAxis, yAxis);

    // 3 bars per datetime item - temp, min temp and max temp
    var s1 = new XYChart.Series();
    s1.name = "temp";
    for (d in date) {
        s1.data.add(new XYChart.Data(date[d], temp[d]));
    }

    var s2 = new XYChart.Series();
    s2.name = "min temp";
    for (d in date) {
        s2.data.add(new XYChart.Data(date[d], temp_min[d]));
    }

    var s3 = new XYChart.Series();
    s3.name = "max temp";
    for (d in date) {
        s3.data.add(new XYChart.Data(date[d], temp_max[d]));
    }

    bc.data.addAll(s1, s2, s3);

    stage.scene = new Scene(bc, 800, 600);
    stage.show();
}
