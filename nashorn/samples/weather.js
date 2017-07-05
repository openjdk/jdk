#// usage: jjs -scripting weather.js

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

// Simple nashorn example showing back-quote exec process,
// JSON and Java8 streams

var Arrays = Java.type("java.util.Arrays");

// use curl to download JSON weather data from the net
// use backquote -scripting mode syntax to exec a process

`curl http://api.openweathermap.org/data/2.5/forecast/daily?q=Chennai&amp;mode=json&amp;units=metric&amp;cnt=7`;

// parse JSON
var weather = JSON.parse($OUT);

// pull out humidity as array
var humidity = weather.list.map(function(curVal) {
    return curVal.humidity;
})

// Stream API to print stat
print("Humidity");
print(Arrays["stream(int[])"](humidity).summaryStatistics());

// pull maximum day time temperature
var temp = weather.list.map(function(curVal) {
    return curVal.temp.max;
});

// Stream API to print stat
print("Max Temperature");
print(Arrays["stream(double[])"](temp).summaryStatistics());
