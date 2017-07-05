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

// Converting between #javascript Date and #java8 LocalDateTime with #nashorn

// JavaScript Date with current time
var d = new Date();
print(d);
 
// Java 8 java.time classes used
var Instant = java.time.Instant;
var LocalDateTime = java.time.LocalDateTime;
var ZoneId = java.time.ZoneId;
 
// Date.prototype.getTime

// getTime() method returns the numeric value corresponding to the time
// for the specified date according to universal time. The value returned
// by the getTime() method is the number of milliseconds since 1 January 1970 00:00:00 UTC.
// See https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getTime
 
// Java Instant.ofEpochMilli to convert time in milliseconds to Instant object
// https://docs.oracle.com/javase/8/docs/api/java/time/Instant.html#ofEpochMilli-long-
 
var instant = Instant.ofEpochMilli(d.getTime());
 
// Instant to LocalDateTime using LocalDateTime.ofInstant
// https://docs.oracle.com/javase/8/docs/api/java/time/LocalDateTime.html#ofInstant-java.time.Instant-java.time.ZoneId-
 
var ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
print(ldt);
 
// converting a LocalDateTime to JavaScript Date
// convert LocalDateTime to Instant first
// https://docs.oracle.com/javase/8/docs/api/java/time/LocalDateTime.html#atZone-java.time.ZoneId-
 
var instant = ldt.atZone(ZoneId.systemDefault()).toInstant();
 
// instant to to epoch milliseconds
// https://docs.oracle.com/javase/8/docs/api/java/time/Instant.html#toEpochMilli--
// and then to JavaScript Date from time in milliseconds
// https://developer.mozilla.org/en/docs/Web/JavaScript/Reference/Global_Objects/Date

var d1 = new Date(instant.toEpochMilli());
print(d1); 
