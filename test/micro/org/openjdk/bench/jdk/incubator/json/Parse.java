/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.jdk.incubator.json;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jdk.incubator.json.Json;
import jdk.incubator.json.JsonValue;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgs = {"--add-modules=jdk.incubator.json"})
@State(Scope.Benchmark)
public class Parse {

    public String json;

    @Setup
    public void setup() throws IOException {
        json = createJson();
    }

    @Benchmark
    public JsonValue parse() {
        return Json.parse(json);
    }

    public static void main(String... args) throws Exception {
        Options opts = new OptionsBuilder().include(Parse.class.getSimpleName()).shouldDoGC(true).build();
        new Runner(opts).run();
    }

    private static String createJson() {
        return """
            {
              "expand": "renderedFields,names,schema,operations,editmeta,changelog,versionedRepresentations",
              "id": "5143806",
              "self": "https://bugs.openjdk.org/rest/api/2/issue/5143806",
              "key": "JDK-8344154",
              "fields": {
                "issuetype": {
                  "self": "https://bugs.openjdk.org/rest/api/2/issuetype/9",
                  "id": "9",
                  "description": "Java Enhancement Proposal",
                  "iconUrl": "https://bugs.openjdk.org/secure/viewavatar?size=xsmall&avatarId=14711&avatarType=issuetype",
                  "name": "JEP",
                  "subtask": false,
                  "avatarId": 14711
                },
                "timespent": null,
                "project": {
                  "self": "https://bugs.openjdk.org/rest/api/2/project/10100",
                  "id": "10100",
                  "key": "JDK",
                  "name": "JDK",
                  "projectTypeKey": "software",
                  "avatarUrls": {
                    "48x48": "https://bugs.openjdk.org/secure/projectavatar?pid=10100&avatarId=10301",
                    "24x24": "https://bugs.openjdk.org/secure/projectavatar?size=small&pid=10100&avatarId=10301",
                    "16x16": "https://bugs.openjdk.org/secure/projectavatar?size=xsmall&pid=10100&avatarId=10301",
                    "32x32": "https://bugs.openjdk.org/secure/projectavatar?size=medium&pid=10100&avatarId=10301"
                  },
                  "projectCategory": {
                    "self": "https://bugs.openjdk.org/rest/api/2/projectCategory/10100",
                    "id": "10100",
                    "description": "Open JDK Projects",
                    "name": "Open JDK Projects"
                  }
                },
                "customfield_11000": null,
                "fixVersions": [],
                "customfield_11001": null,
                "aggregatetimespent": null,
                "resolution": null,
                "customfield_11004": "9223372036854775807",
                "customfield_11005": null,
                "customfield_10700": "Naoto Sato, Paul Sandoz, Justin Lu, Stuart Marks",
                "customfield_10701": "540",
                "customfield_10702": {
                  "self": "https://bugs.openjdk.org/rest/api/2/customFieldOption/19103",
                  "value": "Feature",
                  "id": "19103",
                  "disabled": false
                },
                "customfield_10901": "core dash libs dash dev at openjdk dot org",
                "customfield_10703": {
                  "self": "https://bugs.openjdk.org/rest/api/2/customFieldOption/19104",
                  "value": "Open",
                  "id": "19104",
                  "disabled": false
                },
                "customfield_10704": {
                  "self": "https://bugs.openjdk.org/rest/api/2/customFieldOption/19107",
                  "value": "JDK",
                  "id": "19107",
                  "disabled": false
                },
                "resolutiondate": null,
                "customfield_10705": null,
                "workratio": -1,
                "customfield_10709": {
                  "self": "https://bugs.openjdk.org/rest/api/2/customFieldOption/19111",
                  "value": "M",
                  "id": "19111",
                  "disabled": false
                },
                "lastViewed": null,
                "watches": {
                  "self": "https://bugs.openjdk.org/rest/api/2/issue/JDK-8344154/watchers",
                  "watchCount": 17,
                  "isWatching": false
                },
                "created": "2024-11-13T23:54:45.000+0000",
                "customfield_12000": null,
                "customfield_12002": null,
                "customfield_12001": null,
                "priority": {
                  "self": "https://bugs.openjdk.org/rest/api/2/priority/2",
                  "iconUrl": "https://bugs.openjdk.org/images/jbsImages/p2.png",
                  "name": "P2",
                  "id": "2"
                },
                "customfield_12004": null,
                "customfield_12003": null,
                "labels": [],
                "customfield_11700": "{}",
                "customfield_11900": null,
                "timeestimate": null,
                "aggregatetimeoriginalestimate": null,
                "issuelinks": [
                  {
                    "id": "4480253",
                    "self": "https://bugs.openjdk.org/rest/api/2/issueLink/4480253",
                    "outwardIssue": {
                      "id": "5187950",
                      "key": "JDK-8381976",
                      "self": "https://bugs.openjdk.org/rest/api/2/issue/5187950",
                      "fields": {
                        "summary": "Implementation for Simple JSON API",
                        "status": {
                          "self": "https://bugs.openjdk.org/rest/api/2/status/1",
                          "description": "The issue is open and ready for the assignee to start work on it.",
                          "iconUrl": "https://bugs.openjdk.org/images/icons/statuses/open.png",
                          "name": "Open",
                          "id": "1",
                          "statusCategory": {
                            "self": "https://bugs.openjdk.org/rest/api/2/statuscategory/2",
                            "id": 2,
                            "key": "new",
                            "colorName": "default",
                            "name": "To Do"
                          }
                        },
                        "priority": {
                          "self": "https://bugs.openjdk.org/rest/api/2/priority/2",
                          "iconUrl": "https://bugs.openjdk.org/images/jbsImages/p2.png",
                          "name": "P2",
                          "id": "2"
                        },
                        "issuetype": {
                          "self": "https://bugs.openjdk.org/rest/api/2/issuetype/7",
                          "id": "7",
                          "description": "",
                          "iconUrl": "https://bugs.openjdk.org/secure/viewavatar?size=xsmall&avatarId=14707&avatarType=issuetype",
                          "name": "Enhancement",
                          "subtask": false,
                          "avatarId": 14707
                        }
                      }
                    },
                    "type": {
                      "id": "10003",
                      "name": "Relates",
                      "inward": "relates to",
                      "outward": "relates to",
                      "self": "https://bugs.openjdk.org/rest/api/2/issueLinkType/10003"
                    }
                  }
                ],
                "assignee": {
                  "self": "https://bugs.openjdk.org/rest/api/2/user?username=naoto",
                  "name": "naoto",
                  "key": "naoto",
                  "avatarUrls": {
                    "48x48": "https://bugs.openjdk.org/secure/useravatar?ownerId=naoto&avatarId=17312",
                    "24x24": "https://bugs.openjdk.org/secure/useravatar?size=small&ownerId=naoto&avatarId=17312",
                    "16x16": "https://bugs.openjdk.org/secure/useravatar?size=xsmall&ownerId=naoto&avatarId=17312",
                    "32x32": "https://bugs.openjdk.org/secure/useravatar?size=medium&ownerId=naoto&avatarId=17312"
                  },
                  "displayName": "Naoto Sato",
                  "active": true,
                  "timeZone": "America/Los_Angeles"
                },
                "updated": "2026-07-28T17:45:37.627+0000",
                "status": {
                  "self": "https://bugs.openjdk.org/rest/api/2/status/10003",
                  "description": "",
                  "iconUrl": "https://bugs.openjdk.org/images/icons/statuses/generic.png",
                  "name": "Candidate",
                  "id": "10003",
                  "statusCategory": {
                    "self": "https://bugs.openjdk.org/rest/api/2/statuscategory/4",
                    "id": 4,
                    "key": "indeterminate",
                    "colorName": "inprogress",
                    "name": "In Progress"
                  }
                },
                "components": [
                  {
                    "self": "https://bugs.openjdk.org/rest/api/2/component/10300",
                    "id": "10300",
                    "name": "core-libs"
                  }
                ],
                "timeoriginalestimate": null,
                "description": "\\u003C!--- DO NOT EDIT IN JIRA. SEE COMMENTS. --\\u003E\\r\\n\\r\\nSummary\\r\\n-------\\r\\n\\r\\nDefine a simple, standard API for parsing and generating JSON documents\\r\\nso that doing so does not require an external library. Enable many JSON\\r\\nprocessing tasks to be accomplished with little coding. This is an\\r\\n[incubating API](https://openjdk.org/jeps/11).\\r\\n\\r\\n\\r\\nHistory\\r\\n-------\\r\\n\\r\\nThis JEP supersedes [JEP 198](https://openjdk.org/jeps/198),\\r\\n_Light-Weight JSON API_, which was written in 2014. Circumstances have\\r\\nchanged in the intervening years, so here we take a different approach.\\r\\n\\r\\n\\r\\n## Goals\\r\\n\\r\\n- Provide a standard means in the Java Platform to process\\r\\n  [RFC&nbsp;8259] compliant JSON documents with low ceremony.\\r\\n\\r\\n[RFC&nbsp;8259]: https://www.rfc-editor.org/info/rfc8259/\\r\\n\\r\\n- Keep the API small, simple, and easy to learn. Provide only those data\\r\\n  types and operations required for strict conformance to RFC&nbsp;8259,\\r\\n  in order to facilitate machine-to-machine communication. Avoid\\r\\n  features such as multiple parsing configurations, syntax extensions,\\r\\n  data binding, and streaming.\\r\\n\\r\\n- Ensure that code that navigates and extracts data from JSON documents\\r\\n  with a known structure is simple and readable. Because JSON documents\\r\\n  do not have schemas, such code serves as a _de facto_ schema and\\r\\n  should be readable as such.\\r\\n\\r\\n- Enable easy and quick exploration of unfamiliar JSON documents. We\\r\\n  often interact with JSON documents in an exploratory manner, writing\\r\\n  code not using a specification but instead trying it out against\\r\\n  example documents. The API should provide methods that fail fast with\\r\\n  clear error messages, enabling quick exploration.\\r\\n\\r\\n- Ensure that missing or unexpected values can be handled in a resilient\\r\\n  fashion, since JSON document structures can evolve over time.\\r\\n\\r\\n- Make the JDK itself capable of parsing and generating JSON documents.\\r\\n\\r\\n\\r\\n## Non-Goals\\r\\n\\r\\n- It is not a goal to create an API that supplants established external\\r\\n  JSON libraries.\\r\\n\\r\\n\\r\\n## Motivation\\r\\n\\r\\nJSON is ubiquitous in modern computing. The Java ecosystem contains a\\r\\nwide range of established JSON libraries:\\r\\n[Jackson](https://github.com/FasterXML/jackson),\\r\\n[Gson](https://google.github.io/gson/), Jakarta JSON\\r\\n[Processing](https://jakarta.ee/specifications/jsonp/2.1/) and\\r\\n[Binding](https://jakarta.ee/specifications/jsonb/3.0/),\\r\\n[Fastjson&nbsp;2](https://github.com/alibaba/fastjson2), and more. Not\\r\\nonly do these libraries enable the parsing and generation of JSON\\r\\ndocuments, but they also support extended JSON syntaxes such as\\r\\n[JSON5](https://json5.org/) and include higher-level features such as\\r\\ndata binding, i.e., converting Java objects to and from JSON with a high\\r\\ndegree of customization, and event-based streaming.\\r\\n\\r\\nWe often, however, just need to perform simple tasks such as extracting\\r\\nsome data from a JSON document. The Python or Go code to accomplish such\\r\\ntasks is simple; the Java code should be equally simple.\\r\\n\\r\\nFor example, consider the task of computing the average of a set of\\r\\nforecast temperatures in a response from the [U.S.&nbsp;National Weather\\r\\nService REST API]. The response is a JSON document that looks like this:\\r\\n\\r\\n```\\r\\n{\\r\\n  ...\\r\\n  \\"properties\\": {\\r\\n    ...\\r\\n    \\"periods\\": [\\r\\n      {\\r\\n        \\"number\\": 1,\\r\\n        \\"name\\": \\"Today\\",\\r\\n        \\"startTime\\": \\"2026-04-22T06:00:00-04:00\\",\\r\\n        \\"endTime\\": \\"2026-04-22T18:00:00-04:00\\",\\r\\n        \\"isDaytime\\": true,\\r\\n        \\"temperature\\": 54,\\r\\n        \\"temperatureUnit\\": \\"F\\",\\r\\n        ...\\r\\n      },\\r\\n      {\\r\\n        \\"number\\": 2,\\r\\n        \\"name\\": \\"Tonight\\",\\r\\n        \\"startTime\\": \\"2026-04-22T18:00:00-04:00\\",\\r\\n        \\"endTime\\": \\"2026-04-23T06:00:00-04:00\\",\\r\\n        \\"isDaytime\\": false,\\r\\n        \\"temperature\\": 48,\\r\\n        \\"temperatureUnit\\": \\"F\\",\\r\\n        ...\\r\\n      },\\r\\n      {\\r\\n        \\"number\\": 3,\\r\\n        \\"name\\": \\"Thursday\\",\\r\\n        \\"startTime\\": \\"2026-04-23T06:00:00-04:00\\",\\r\\n        \\"endTime\\": \\"2026-04-23T18:00:00-04:00\\",\\r\\n        \\"isDaytime\\": true,\\r\\n        \\"temperature\\": 68,\\r\\n        \\"temperatureUnit\\": \\"F\\",\\r\\n        ...\\r\\n      },\\r\\n      ...\\r\\n    ]\\r\\n  }\\r\\n}\\r\\n```\\r\\n\\r\\nTo compute the average forecast temperature requires parsing the\\r\\ndocument, navigating to the location in the structure that contains the\\r\\nforecasts, and iterating over the array of forecasts while extracting\\r\\nthe temperature data. We should be able to tackle simple tasks like this\\r\\nwith simple Java code, without installing an external library and\\r\\nwithout suspecting that another language might make us more productive.\\r\\n\\r\\nA key goal driving the recent evolution of the Java Platform has been to\\r\\nenable simple tasks to be accomplished more easily and with less\\r\\nceremony. Features serving this goal include [convenience factory\\r\\nmethods for collections](https://openjdk.org/jeps/269), [`var`\\r\\ndeclarations](https://openjdk.org/jeps/286), [running programs from\\r\\nsource files](https://openjdk.org/jeps/330), and [compact source files\\r\\nand instance main methods](https://openjdk.org/jeps/512). A simple JSON\\r\\nAPI for parsing and generating JSON documents would also serve this\\r\\nimportant goal.\\r\\n\\r\\n\\r\\n### Using JSON in the JDK\\r\\n\\r\\nA standard JSON API in the Java Platform would also pave the way for\\r\\nfurther use of JSON in the Platform and by the JDK itself, since the JDK\\r\\ncannot have external dependencies. One potential use case is\\r\\nconfiguration files. The JDK uses the [property\\r\\nfile](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/Properties.html)\\r\\nformat for various configuration files, such as [security properties\\r\\nfiles](https://docs.oracle.com/en/java/javase/26/security/security-properties-file.html).\\r\\nA weakness of this format is that it cannot express structured data. To\\r\\nrepresent an array in a property file, you must use clumsy workarounds\\r\\nsuch as sequentially numbered properties:\\r\\n\\r\\n```\\r\\nsecurity.provider.1=SUN\\r\\nsecurity.provider.2=SunRsaSign\\r\\nsecurity.provider.3=SunEC\\r\\n...\\r\\n```\\r\\n\\r\\nWith JSON built into the JDK, configuration files could represent arrays\\r\\nnaturally, using JSON arrays:\\r\\n\\r\\n```\\r\\n{\\r\\n  \\"providers\\": [ \\"SUN\\", \\"SunRsaSign\\", \\"SunEC\\" ],\\r\\n  ...\\r\\n}\\r\\n```\\r\\n\\r\\n\\r\\n## Description\\r\\n\\r\\nThe\\r\\n[`jdk.incubator.json`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/package-summary.html)\\r\\nAPI is organized around the\\r\\n[`JsonValue`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html)\\r\\ninterface, which represents a JSON value.\\r\\n\\r\\nThe JSON syntax has four kinds of primitives:\\r\\n\\r\\n  1. JSON strings, delimited with double quotes:\\r\\n\\r\\n     ```\\r\\n     \\"Hello\\"\\r\\n     \\"My name is 'Bob'\\"\\r\\n     \\"\\\\u006a\\\\u0061\\\\u0076\\\\u0061\\"\\r\\n     ```\\r\\n\\r\\n  2. JSON numbers, represented in base 10 using decimal digits:\\r\\n\\r\\n     ```\\r\\n     6  6.0  31.84  2.9E+5\\r\\n     ```\\r\\n\\r\\n  3. JSON boolean literals: `true` and `false`\\r\\n\\r\\n  4. The JSON null literal: `null`\\r\\n\\r\\nand two kinds of structures:\\r\\n\\r\\n  5. JSON objects, delimited by `{` `}` and composed of comma-separated\\r\\n     members. A member has a name, also called a key, and a value,\\r\\n     separated by a colon:\\r\\n\\r\\n     ```\\r\\n     {\\r\\n       \\"address\\" : \\"123 Smith Street\\",\\r\\n       \\"value\\" : 31.84,\\r\\n       \\"coordinates\\" : [ [ 37, 23, 41 ], [ -121, 57, 10 ] ]\\r\\n     }\\r\\n     ```\\r\\n\\r\\n  6. JSON arrays, delimited by `[` `]` and composed of comma-separated\\r\\n     JSON values:\\r\\n\\r\\n     ```\\r\\n     [ 1, 2, 3, { \\"value\\": \\"4\\" }, [ 5, 6 ] ]\\r\\n     ```\\r\\n\\r\\nThe `JsonValue` interface thus has six corresponding sub-interfaces:\\r\\n[`JsonString`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonString.html),\\r\\n[`JsonNumber`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonNumber.html),\\r\\n[`JsonBoolean`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonBoolean.html),\\r\\n[`JsonNull`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonNull.html),\\r\\n[`JsonObject`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonObject.html),\\r\\nand\\r\\n[`JsonArray`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonArray.html).\\r\\nEach interface declares operations appropriate to its corresponding JSON\\r\\nsyntactic element: Instances of the primitive sub-interfaces offer\\r\\nconversions to Java primitives and strings, `JsonObject` instances\\r\\nexpose members, and `JsonArray` instances expose array elements.\\r\\n\\r\\nThe `JsonValue` interface is [sealed](https://openjdk.org/jeps/409),\\r\\nwhich guarantees that any `JsonValue` instance is always one of this\\r\\nfixed set of subtypes and thus exhaustive `switch` expressions and\\r\\nstatements do not require a `default` clause.\\r\\n\\r\\nThe JSON API makes it easy to parse JSON documents that conform to\\r\\n[RFC&nbsp;8259]. The\\r\\n[`parse`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/Json.html#parse(java.lang.String))\\r\\nmethod of the\\r\\n[`Json`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/Json.html)\\r\\nclass returns a tree of `JsonValue` instances that expose the names,\\r\\ntypes, and values of the parsed JSON data. Returning to the National\\r\\nWeather Service example, we can compute the average forecast temperature\\r\\nin just a few lines:\\r\\n\\r\\n```\\r\\nString body = ... REST response body, which is a JSON document ... ;\\r\\nJsonValue json = Json.parse(body);\\r\\njson.get(\\"properties\\").get(\\"periods\\").asList().stream()\\r\\n    .mapToInt(j -\\u003E j.get(\\"temperature\\").asInt())\\r\\n    .average()\\r\\n    .ifPresent(IO::println);\\r\\n```\\r\\n\\r\\n(The complete example is shown in the [Appendix](#appendix).)\\r\\n\\r\\nThe API also makes it easy to generate JSON documents. For example, this\\r\\ncode:\\r\\n\\r\\n\\r\\n```\\r\\nIO.println(JsonObject.of(Map.of(\\"providers\\",\\r\\n                                JsonArray.of(List.of(JsonString.of(\\"SUN\\"),\\r\\n                                                     JsonString.of(\\"SunRsaSign\\"),\\r\\n                                                     JsonString.of(\\"SunEC\\"))))));\\r\\n```\\r\\n\\r\\nproduces the output:\\r\\n\\r\\n```\\r\\n{\\"providers\\":[\\"SUN\\",\\"SunRsaSign\\",\\"SunEC\\"]}\\r\\n```\\r\\n\\r\\n\\r\\n### Parsing and navigating JSON documents\\r\\n\\r\\nThe `Json` class can parse a JSON document contained in either a\\r\\n`String` or a `char` array. A JSON document might be a REST API response\\r\\nbody read from the network, a configuration file read from disk, or some\\r\\nother text payload produced by an application.\\r\\n\\r\\nParsing a JSON document requires a single call to one of the\\r\\n[`Json.parse`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/Json.html#parse(java.lang.String))\\r\\nmethods:\\r\\n\\r\\n```\\r\\nJsonValue root = Json.parse(doc);\\r\\n```\\r\\n\\r\\nParsing is strict: The document must conform to [RFC&nbsp;8259]. Syntax\\r\\nextensions such as trailing commas and comments are not supported.\\r\\nAdditionally, documents must not have objects with duplicate member\\r\\nnames. This policy, permitted by the RFC, provides maximum\\r\\ninteroperability and predictability, and reduces concerns about\\r\\nprocessing malformed or ambiguous JSON documents. (See\\r\\n[below](#duplicates) for a full discussion.)\\r\\n\\r\\nSuccessful parsing returns an instance of `JsonValue`. Unsuccessful\\r\\nparsing throws an unchecked\\r\\n[`JsonParseException`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonParseException.html).\\r\\nThe exception includes a detail message that provides specific\\r\\ninformation about the error, its path from the root of the document, and\\r\\nits location within the document. For example, when a document contains duplicate\\r\\nmember names in an object, the exception thrown has the form:\\r\\n\\r\\n```\\r\\njdk.incubator.json.JsonParseException: The duplicate member name: \\"providers\\" was\\r\\nalready parsed. Path: \\"{\\". Location: line 2, position 4.\\r\\n```\\r\\n\\r\\nMost JSON documents have a JSON object or JSON array at the root. For\\r\\nexample, a [JSON-formatted thread dump produced by the `jcmd`\\r\\ntool](https://download.java.net/java/early_access/jdk27/docs/api/jdk.management/com/sun/management/doc-files/threadDump.html)\\r\\ncontains a root object:\\r\\n\\r\\n```\\r\\n{\\r\\n  \\"threadDump\\": {\\r\\n    \\"formatVersion\\": 2,\\r\\n    \\"processId\\": 45178,\\r\\n    \\"time\\": \\"2026-04-16T23:13:02.709630Z\\",\\r\\n    \\"runtimeVersion\\": \\"27-internal\\",\\r\\n    \\"threadContainers\\": [\\r\\n      {\\r\\n        \\"container\\": \\"\\u003Croot\\u003E\\",\\r\\n        \\"parent\\": null,\\r\\n        \\"owner\\": null,\\r\\n        \\"threads\\": [\\r\\n          {\\r\\n            \\"tid\\": 3,\\r\\n            \\"time\\": \\"2026-04-16T23:13:02.906891Z\\",\\r\\n            \\"name\\": \\"main\\",\\r\\n            \\"state\\": \\"WAITING\\",\\r\\n            ...\\r\\n```\\r\\n\\r\\nThe root object contains a single member, the nested `threadDump`\\r\\nobject, and `threadDump` itself contains both primitive and structural\\r\\nJSON values.\\r\\n\\r\\nOnce you have obtained the root `JsonValue` via `Json.parse(...)`, you\\r\\ncan retrieve values from objects and arrays via their *access methods*,\\r\\nwhich return the requested member value or array element as a\\r\\n`JsonValue`. It is not necessary to downcast a `JsonValue` to `JsonObject`\\r\\nto access a member value, or to `JsonArray` to access an array element.\\r\\n\\r\\n- [`get(String)`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#get(java.lang.String))\\r\\n  obtains the value of an object member. To obtain the\\r\\n  thread dump object:\\r\\n\\r\\n  ```\\r\\n  JsonValue threadDump = root.get(\\"threadDump\\");\\r\\n  ```\\r\\n\\r\\n- [`get(int)`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#get(int))\\r\\n  obtains an array element. To obtain the root thread container:\\r\\n\\r\\n  ```\\r\\n  JsonValue firstContainer = threadDump.get(\\"threadContainers\\").get(0);\\r\\n  ```\\r\\n\\r\\nIf the `JsonValue` instance is of the wrong type, or if the requested\\r\\nmember or element does not exist, the access methods throw a\\r\\n[`JsonValueException`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValueException.html).\\r\\n\\r\\n\\r\\n### Converting JSON values to Java values\\r\\n\\r\\nYou can convert a JSON value to a Java value by calling one of the\\r\\n*conversion methods* of the `JsonValue` interface. For a conversion to\\r\\nsucceed, the `JsonValue` must be an instance of the appropriate subtype\\r\\nof `JsonValue`:\\r\\n\\r\\n\\u003Ctable\\u003E\\r\\n  \\u003Cthead\\u003E\\r\\n    \\u003Ctr\\u003E\\r\\n      \\u003Cth\\u003ESubtype\\u003C/th\\u003E\\r\\n      \\u003Cth\\u003EMethod\\u003C/th\\u003E\\r\\n      \\u003Cth\\u003EResulting Java type\\u003C/th\\u003E\\r\\n    \\u003C/tr\\u003E\\r\\n  \\u003C/thead\\u003E\\r\\n  \\u003Ctbody\\u003E\\r\\n    \\u003Ctr\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ca href=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonString.html\\"\\u003E\\u003Ccode\\u003EJsonString\\u003C/code\\u003E\\u003C/a\\u003E\\u003C/td\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ca href=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asString()\\"\\u003E\\u003Ccode\\u003EasString()\\u003C/code\\u003E\\u003C/a\\u003E\\u003C/td\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ccode\\u003Ejava.lang.String\\u003C/code\\u003E\\u003C/td\\u003E\\r\\n    \\u003C/tr\\u003E\\r\\n    \\u003Ctr\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ca href=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonNumber.html\\"\\u003E\\u003Ccode\\u003EJsonNumber\\u003C/code\\u003E\\u003C/a\\u003E\\u003C/td\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ca href=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asInt()\\"\\u003E\\u003Ccode\\u003EasInt()\\u003C/code\\u003E\\u003C/a\\u003E\\u003C/td\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ccode\\u003Eint\\u003C/code\\u003E\\u003C/td\\u003E\\r\\n    \\u003C/tr\\u003E\\r\\n    \\u003Ctr\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ca href=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonNumber.html\\"\\u003E\\u003Ccode\\u003EJsonNumber\\u003C/code\\u003E\\u003C/a\\u003E\\u003C/td\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ca href=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asLong()\\"\\u003E\\u003Ccode\\u003EasLong()\\u003C/code\\u003E\\u003C/a\\u003E\\u003C/td\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ccode\\u003Elong\\u003C/code\\u003E\\u003C/td\\u003E\\r\\n    \\u003C/tr\\u003E\\r\\n    \\u003Ctr\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ca href=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonNumber.html\\"\\u003E\\u003Ccode\\u003EJsonNumber\\u003C/code\\u003E\\u003C/a\\u003E\\u003C/td\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ca href=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asDouble()\\"\\u003E\\u003Ccode\\u003EasDouble()\\u003C/code\\u003E\\u003C/a\\u003E\\u003C/td\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ccode\\u003Edouble\\u003C/code\\u003E\\u003C/td\\u003E\\r\\n    \\u003C/tr\\u003E\\r\\n    \\u003Ctr\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ca href=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonBoolean.html\\"\\u003E\\u003Ccode\\u003EJsonBoolean\\u003C/code\\u003E\\u003C/a\\u003E\\u003C/td\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ca href=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asBoolean()\\"\\u003E\\u003Ccode\\u003EasBoolean()\\u003C/code\\u003E\\u003C/a\\u003E\\u003C/td\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ccode\\u003Eboolean\\u003C/code\\u003E\\u003C/td\\u003E\\r\\n    \\u003C/tr\\u003E\\r\\n    \\u003Ctr\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ca href=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonObject.html\\"\\u003E\\u003Ccode\\u003EJsonObject\\u003C/code\\u003E\\u003C/a\\u003E\\u003C/td\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ca href=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asMap()\\"\\u003E\\u003Ccode\\u003EasMap()\\u003C/code\\u003E\\u003C/a\\u003E\\u003C/td\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ccode\\u003Ejava.util.Map\\u003C/code\\u003E\\u003C/td\\u003E\\r\\n    \\u003C/tr\\u003E\\r\\n    \\u003Ctr\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ca href=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonArray.html\\"\\u003E\\u003Ccode\\u003EJsonArray\\u003C/code\\u003E\\u003C/a\\u003E\\u003C/td\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ca href=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asList()\\"\\u003E\\u003Ccode\\u003EasList()\\u003C/code\\u003E\\u003C/a\\u003E\\u003C/td\\u003E\\r\\n      \\u003Ctd\\u003E\\u003Ccode\\u003Ejava.util.List\\u003C/code\\u003E\\u003C/td\\u003E\\r\\n    \\u003C/tr\\u003E\\r\\n  \\u003C/tbody\\u003E\\r\\n\\u003C/table\\u003E\\r\\n\\r\\nFor example, you can retrieve the Java `String` value associated with\\r\\nthe thread dump's \\"time\\" member:\\r\\n\\r\\n```\\r\\nJsonValue threadDumpTime = threadDump.get(\\"time\\");\\r\\nString time = threadDumpTime.asString();\\r\\n```\\r\\n\\r\\nYou can convert the thread containers array into a `List` of `JsonValue`\\r\\ninstances and process each instance:\\r\\n\\r\\n```\\r\\nthreadDump.get(\\"threadContainers\\").asList().forEach(jv -\\u003E ...);\\r\\n```\\r\\n\\r\\nYou can access the thread dump object as a `Map` to retrieve the number\\r\\nof members:\\r\\n\\r\\n```\\r\\nint count = threadDump.asMap().size();\\r\\n```\\r\\n\\r\\nYou can navigate deeply into a JSON document, chaining access methods\\r\\nand converting to a Java value only at the end. To retrieve the thread\\r\\nidentifier value of the first thread in the root thread container:\\r\\n\\r\\n```\\r\\nlong tid = threadDump.get(\\"threadContainers\\").get(0)\\r\\n                     .get(\\"threads\\").get(0).get(\\"tid\\").asLong();\\r\\n```\\r\\n\\r\\nThe design of the conversion methods eliminates most `instanceof`\\r\\nchecking and downcasting in cases where a specific JSON data type is\\r\\nexpected in a document:\\r\\n\\r\\n- [`asString()`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asString())\\r\\n  converts a `JsonString` instance into a Java `String` with RFC 8259\\r\\n  JSON escape sequences translated to their corresponding characters.\\r\\n\\r\\n- [`asInt()`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asInt())\\r\\n  converts a `JsonNumber` instance to a Java `int` if its numeric value\\r\\n  can be represented exactly.\\r\\n\\r\\n- [`asLong()`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asLong())\\r\\n  converts a `JsonNumber` instance to a Java `long` if its numeric value\\r\\n  can be represented exactly.\\r\\n\\r\\n- [`asDouble()`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asDouble())\\r\\n  converts a `JsonNumber` instance to a Java `double` if its numeric\\r\\n  value can be represented accurately.\\r\\n\\r\\n- [`asBoolean()`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asBoolean())\\r\\n  converts a `JsonBoolean` instance to a Java `boolean` value of `true`\\r\\n  or `false`.\\r\\n\\r\\n- [`asMap()`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asMap())\\r\\n  converts a `JsonObject` instance into an unmodifiable Java `Map`. If\\r\\n  the JSON object contains no members, an empty `Map` is returned.\\r\\n\\r\\n- [`asList()`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asList())\\r\\n  converts a `JsonArray` instance into an unmodifiable Java `List`. If\\r\\n  the JSON array contains no elements, an empty `List` is returned.\\r\\n\\r\\nThere is no conversion method for the JSON null value.\\r\\n[`JsonNull`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonNull.html)\\r\\ninstances can be handled by testing for `instanceof JsonNull` or via the\\r\\n[`tryValue`](#Handling-null-values) method.\\r\\n\\r\\nIf a `JsonValue` is not an instance of the appropriate subtype for a\\r\\nconversion method then the method throws a `JsonValueException`. For\\r\\nexample, calling `asInt()` on a `JsonValue` that is an instance of\\r\\n`JsonString` will always throw this exception. No attempt is made to\\r\\nparse the string value into a number.\\r\\n\\r\\nNumeric conversions can fail for reasons such as the numeric value not\\r\\nbeing representable in the target Java numeric type, which also causes a\\r\\n`JsonValueException` to be thrown. See [below](#JSON-numbers) for a\\r\\ndeeper discussion of number handling and conversions.\\r\\n\\r\\n\\r\\n### Handling JSON document evolution\\r\\n\\r\\nJSON documents from a particular source may evolve, over time, in ways\\r\\nthat violate your previous expectations of their structure and content:\\r\\n\\r\\n- You might call access methods expecting member names or array indices\\r\\n  that do not exist in the JSON objects and JSON arrays of the document.\\r\\n\\r\\n- You might call conversion methods applicable to one JSON type on\\r\\n  values of a different type.\\r\\n\\r\\nIf you call access or conversion methods on the wrong type, they throw a\\r\\n`JsonValueException`. This exception is unchecked, so that scripts and\\r\\nsmall programs are easier to read and write.\\r\\n\\r\\nContinuing with the thread dump example, recall that the root JSON value\\r\\nis a JSON object with a single member, `threadDump`. This code:\\r\\n\\r\\n```\\r\\nJsonValue name = root.get(\\"threadName\\");\\r\\n```\\r\\n\\r\\nthrows a `JsonValueException` with a message that makes it clear the JSON\\r\\nobject does not contain a member with the name `\\"threadName\\"`, while this code:\\r\\n\\r\\n```\\r\\nList\\u003CJsonValue\\u003E threadDumpList = threadDump.asList();\\r\\n```\\r\\n\\r\\nthrows a `JsonValueException`, whose message makes it clear the `threadDump` member\\r\\nis a JSON object, not a JSON array.\\r\\n\\r\\nThe exception message describes the path leading from the root of the\\r\\nJSON document to the unexpected JSON value, as well as the position in\\r\\nthe JSON document. This is helpful when a chain of access methods\\r\\nnavigates deeply into the document. For example, if the earlier code\\r\\nsnippet to extract the thread identifier incorrectly converted it to a\\r\\n`boolean` instead of a `long`:\\r\\n\\r\\n```\\r\\nboolean tid = threadDump.get(\\"threadContainers\\").get(0)\\r\\n                        .get(\\"threads\\").get(0).get(\\"tid\\").asBoolean();\\r\\n```\\r\\n\\r\\nthen the exception thrown by `asBoolean()` would have the form:\\r\\n\\r\\n```\\r\\njdk.incubator.json.JsonValueException: JsonNumber is not a JsonBoolean. Path:\\r\\n \\"{threadDump{threadContainers[0{threads[0{tid\\". Location: line 13, position 19.\\r\\n```\\r\\n\\r\\n### Handling optional members\\r\\n\\r\\nIf you do not know whether a JSON object has a member with a given name,\\r\\nyou can use the\\r\\n[`tryGet`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#tryGet(java.lang.String))\\r\\naccess method. This returns an `Optional` instance containing the\\r\\nmember's value, or else an empty `Optional` if the member does not\\r\\nexist. (The `get` method, by contrast, confirms that the member exists\\r\\nand throws an exception if it does not.) The `tryGet` method throws a\\r\\n`JsonValueException` if it is not called on a `JsonObject`.\\r\\n\\r\\nConsider the following thread object:\\r\\n\\r\\n```\\r\\n{\\r\\n  \\"tid\\": 11,\\r\\n  \\"time\\": \\"2026-04-16T23:13:02.918321Z\\",\\r\\n  \\"name\\": \\"Finalizer\\",\\r\\n  \\"state\\": \\"WAITING\\",\\r\\n  \\"waitingOn\\": \\"java.lang.Object@c10f5b9\\",\\r\\n  \\"stack\\": [\\r\\n    ...\\r\\n```\\r\\n\\r\\nA thread object contains multiple optional members. One of them is the\\r\\n`waitingOn` member, which contains the JSON string representation of the\\r\\nobject on which the thread is waiting. However, in cases where the\\r\\nthread is not waiting, the thread object may look like this:\\r\\n\\r\\n```\\r\\n{\\r\\n  \\"tid\\": 10,\\r\\n  \\"time\\": \\"2026-04-16T23:13:02.918177Z\\",\\r\\n  \\"name\\": \\"Reference Handler\\",\\r\\n  \\"state\\": \\"RUNNABLE\\",\\r\\n  \\"stack\\": [\\r\\n    ...\\r\\n```\\r\\n\\r\\nThus, when processing thread objects from a thread dump, you must be\\r\\nprepared for the `waitingOn` member to be absent. You can handle this\\r\\nvia `tryGet`:\\r\\n\\r\\n```\\r\\nJsonValue thread = ...\\r\\nthread.tryGet(\\"waitingOn\\")\\r\\n      .ifPresent(result -\\u003E ...);\\r\\n```\\r\\n\\r\\nThe lambda passed to `ifPresent` is called only if the `waitingOn`\\r\\nmember is present.\\r\\n\\r\\n\\r\\n### Handling null values\\r\\n\\r\\nIf you do not know whether a JSON value is a JSON null, you can use the\\r\\n[`tryValue`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#tryValue())\\r\\naccess method. This method returns an empty `Optional` if the JSON value\\r\\nupon which it is invoked is a `JsonNull`; otherwise, it returns that\\r\\nvalue.\\r\\n\\r\\nFor example, a thread container object typically looks like this:\\r\\n\\r\\n```\\r\\n{\\r\\n  \\"container\\": \\"java.util.concurrent.ThreadPoolExecutor@1936a586\\",\\r\\n  \\"parent\\": \\"\\u003Croot\\u003E\\",\\r\\n  ...\\r\\n```\\r\\n\\r\\nHere, the `parent` member's value is a JSON string, the parent\\r\\ncontainer's name. However, the container named \\"\\\\\\u003Croot\\\\\\u003E\\" is the root of\\r\\nall containers and looks like this:\\r\\n\\r\\n```\\r\\n{\\r\\n  \\"container\\": \\"\\u003Croot\\u003E\\",\\r\\n  \\"parent\\": null,\\r\\n  ...\\r\\n```\\r\\n\\r\\nThe root container has no parent, so the `parent` member's value is a\\r\\nJSON null. Thus, when processing container objects from a thread dump,\\r\\nyou must be prepared for the parent member to be either a JSON string or\\r\\na JSON null. You can handle this via `tryValue`:\\r\\n\\r\\n```\\r\\nJsonValue container = ...\\r\\ncontainer.get(\\"parent\\").tryValue()\\r\\n         .ifPresent(result -\\u003E ...);\\r\\n```\\r\\n\\r\\nThe lambda passed to `ifPresent` is called only if the \\"parent\\" member's\\r\\nvalue is not a JSON null.\\r\\n\\r\\n\\r\\n### Handling variable structure and content\\r\\n\\r\\nThe structure and content of JSON documents in a particular context is\\r\\noften uniform, but sometimes it is variable. It might vary across\\r\\ndifferent sources, or over time from a particular source which itself\\r\\nevolves, or even within the same document.\\r\\n\\r\\nFor example, in thread dumps in JDK 26 and earlier releases, thread\\r\\nidentifiers are represented as JSON strings; in [JDK 27 and later\\r\\nreleases](https://bugs.openjdk.org/browse/JDK-8381002), thread\\r\\nidentifiers are represented as JSON numbers.\\r\\n\\r\\nCode that expects the `tid` to be a JSON number, for example:\\r\\n\\r\\n```\\r\\nlong tid = thread.get(\\"tid\\").asLong();\\r\\n```\\r\\n\\r\\nwill fail with a `JsonValueException` if it encounters a thread dump\\r\\nproduced by a version of the JDK that emits `tid` values as JSON\\r\\nstrings.\\r\\n\\r\\nIn either representation, the numeric value is specified to fit in a\\r\\nJava `long`. You could use `instanceof` to check whether you have a\\r\\n`JsonNumber` or a `JsonString`, but it is clearer to use type patterns\\r\\nin a `switch` statement:\\r\\n\\r\\n```\\r\\nlong tid = switch (thread.get(\\"tid\\")) {\\r\\n    case JsonNumber jn -\\u003E jn.asLong();\\r\\n    case JsonString js -\\u003E Long.parseLong(js.asString());\\r\\n    default -\\u003E throw new JsonValueException(\\"Unexpected type for \\\\\\"tid\\\\\\"\\");\\r\\n};\\r\\n```\\r\\n\\r\\n\\r\\n### Generating JSON documents\\r\\n\\r\\nTo generate a JSON document, in string form, from a `JsonValue`, simply\\r\\ninvoke its\\r\\n[`toString`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#toString())\\r\\nmethod. This method returns a compact string representation in which all\\r\\nmembers, elements, and values are emitted on the same line, with no\\r\\nwhitespace between them.\\r\\n\\r\\nFor example, this code:\\r\\n\\r\\n```\\r\\nJsonValue json = Json.parse(\\"\\"\\"\\r\\n  {\\r\\n    \\"service\\" : \\"web_server\\",\\r\\n    \\"id\\" : 3\\r\\n  }\\r\\n  \\"\\"\\");\\r\\nIO.println(json.toString());\\r\\n```\\r\\n\\r\\nprints:\\r\\n\\r\\n```\\r\\n{\\"service\\":\\"web_server\\",\\"id\\":3}\\r\\n```\\r\\n\\r\\n(The `toString` method is distinct from the `asString` method, which\\r\\nthrows an exception if the `JsonValue` upon which it is invoked is not a\\r\\n`JsonString`.)\\r\\n\\r\\nThe static method\\r\\n[`Json.toDisplayString`](https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/Json.html#toDisplayString(jdk.incubator.json.JsonValue,int))\\r\\nemits a pretty-printed form of a JSON document, where members and\\r\\nelements are separated by newlines and nested structures are indented by\\r\\na given amount. For example, this code:\\r\\n\\r\\n```\\r\\nIO.println(Json.toDisplayString(json, 2));\\r\\n```\\r\\n\\r\\nprints the above structure with two spaces of indentation:\\r\\n\\r\\n```\\r\\n{\\r\\n  \\"service\\": \\"web_server\\",\\r\\n  \\"id\\": 3\\r\\n}\\r\\n```\\r\\n\\r\\nThe outputs of both the `toString` and `Json.toDisplayString` methods\\r\\nare parsable by the `Json.parse` method, which will produce a\\r\\n`JsonValue` that is equivalent to the original.\\r\\n\\r\\n\\r\\n### JSON numbers\\r\\n\\r\\nThe syntax for JSON numbers defined in [RFC&nbsp;8259] can represent\\r\\ndecimal values of arbitrary precision and range. The JSON API enables\\r\\nJSON numbers to be processed losslessly; in most applications, however,\\r\\ncommon numeric types suffice.\\r\\n\\r\\nRFC&nbsp;8259 advises that good interoperability among JSON libraries\\r\\ncan be achieved by using\\r\\n[IEEE&nbsp;754](https://en.wikipedia.org/wiki/IEEE_754) 64-bit binary\\r\\nfloating point values, corresponding to the Java `double` type. The \\u003Ca\\r\\nhref=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asDouble()\\"\\u003E\\u003Ccode\\u003EasDouble()\\u003C/code\\u003E\\u003C/a\\u003E\\r\\nmethod therefore converts a numeric JSON value to a Java `double`. The\\r\\nJSON value must lie within the range that a `double` can represent; if\\r\\nthe value is out of range, a `JsonValueException` is thrown. Infinity\\r\\nand not-a-number (\\"NaN\\") values are not representable in JSON, and thus\\r\\nare never returned. Negative zero, however, is representable in JSON,\\r\\nand thus may be returned.\\r\\n\\r\\nIf the JSON value has more precision than can be represented in a\\r\\n`double`, the value is rounded to the closest `double` value. For\\r\\nexample:\\r\\n\\r\\n```\\r\\ndouble d1 = Json.parse(\\"3.141592653589793238462643383279\\").asDouble();\\r\\n// d1 is 3.141592653589793, the nearest double value\\r\\n\\r\\ndouble d2 = Json.parse(\\"1.8E309\\").asDouble();\\r\\n// throws JsonValueException, out of range\\r\\n```\\r\\n\\r\\nIntegral numeric values are frequently used, so the \\u003Ca\\r\\nhref=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asInt()\\"\\u003E\\u003Ccode\\u003EasInt()\\u003C/code\\u003E\\u003C/a\\u003E\\r\\nmethod converts a numeric JSON value to a Java `int` value. The JSON\\r\\nvalue must be exactly representable as an `int`, otherwise an exception\\r\\nis thrown. Numbers that have a syntactic fractional part but that\\r\\nrepresent integral values are converted; for example:\\r\\n\\r\\n```\\r\\nint i1 = Json.parse(\\"123.0\\").asInt();       // succeeds\\r\\nint i2 = Json.parse(\\"234.56E2\\").asInt();    // succeeds\\r\\nint i3 = Json.parse(\\"345.6\\").asInt();       // fails, not integral\\r\\nint i4 = Json.parse(\\"2147483648\\").asInt();  // fails, out of range\\r\\n```\\r\\n\\r\\nThe conversion method \\u003Ca\\r\\nhref=\\"https://cr.openjdk.org/~naoto/json/javadoc/api/jdk.incubator.json/jdk/incubator/json/JsonValue.html#asLong()\\"\\u003E\\u003Ccode\\u003EasLong()\\u003C/code\\u003E\\u003C/a\\u003E\\r\\nis similar to `asInt()` except that it returns a Java `long` value and\\r\\nsupports any JSON numeric value that can be represented exactly as a\\r\\n`long`.\\r\\n\\r\\nIf you need a narrower primitive type than `int` or `double`, you can\\r\\nuse [primitive types in patterns](https://openjdk.org/jeps/530)\\r\\n(currently a preview feature), to perform a safe conversion. For\\r\\nexample, if you expect a JSON number to be representable as a `short`:\\r\\n\\r\\n```\\r\\nJsonValue json = Json.parse(\\"\\"\\"\\r\\n  {\\r\\n    \\"id\\": 12345,\\r\\n    \\"price\\": 10.99\\r\\n  }\\r\\n  \\"\\"\\");\\r\\nif (json.get(\\"id\\").asInt() instanceof short s) {\\r\\n    // use s\\r\\n} else {\\r\\n    // report out-of-range error\\r\\n}\\r\\n```\\r\\n\\r\\nAs mentioned previously, JSON numbers can have arbitrary precision and\\r\\nrange. The `asDouble()`, `asInt()`, and `asLong()` methods, by\\r\\ndefinition, handle only a subset of JSON numeric values; they reject\\r\\nout-of-range values, and they round overly-precise values. To handle\\r\\nJSON numeric data without loss of information, you can convert\\r\\nessentially any JSON number to a\\r\\n[`java.math.BigDecimal`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/math/BigDecimal.html)\\r\\ninstance:\\r\\n\\r\\n```\\r\\nBigDecimal bd = new BigDecimal(jn.toString());\\r\\n```\\r\\n\\r\\n\\r\\n## Alternatives\\r\\n\\r\\n - \\u003Ca id=\\"features\\"/\\u003E _Provide a full feature set instead of a limited\\r\\n   feature set._\\r\\n\\r\\n   The many existing external JSON libraries provide, among them, a\\r\\n   broad set of features. We cannot possibly include all of these\\r\\n   features in the Java Platform; we must, instead, select a subset that\\r\\n   provides the greatest value relative to its cost.\\r\\n\\r\\n   We have excluded the commonly provided feature of data binding. This\\r\\n   feature is undeniably useful and convenient for many applications.\\r\\n   However, it would add a significant API footprint and increase\\r\\n   implementation and maintenance costs dramatically. Many use cases do\\r\\n   not require data binding, so we consider this feature not strictly\\r\\n   necessary. That the Jackson and Jakarta JSON libraries factor their\\r\\n   data binding features into separate modules is an implicit\\r\\n   recognition that there are use cases that do not need data binding.\\r\\n\\r\\n   A streaming API is clearly essential for certain narrow, specialized\\r\\n   use cases, but it induces a fair amount of application complexity for\\r\\n   even simple data extraction tasks. We have thus excluded this feature.\\r\\n\\r\\n   Omitting data binding and streaming leaves us with a DOM-like\\r\\n   approach in which JSON documents are parsed into trees of\\r\\n   JSON-specific objects from which data can be extracted easily. The\\r\\n   API is small, and it incurs correspondingly small implementation and\\r\\n   maintenance costs. This satisfies the needs of a significant subset\\r\\n   of JSON applications, from the simplest to the moderately complex.\\r\\n\\r\\n   An application might start off using the Java Platform's JSON API but\\r\\n   eventually grow to need features such as data binding or streaming,\\r\\n   necessitating a migration to a richer API in an external library. We\\r\\n   do not view this scenario as a failure, and it is not sufficient\\r\\n   justification to include high-cost features such as JSON data binding\\r\\n   and streaming in the Java Platform.\\r\\n\\r\\n - _Integrate an external JSON library._\\r\\n\\r\\n   We could integrate an external library into the Java Platform and the\\r\\n   JDK, as a downstream fork. This would raise difficult issues over\\r\\n   licensing and governance. There would be continual tension over\\r\\n   changes flowing in both directions, arising from different criteria\\r\\n   regarding specification quality, compatibility, release schedules,\\r\\n   and so forth. (We have experienced this tension in the past, with\\r\\n   various XML APIs.) It seems likely that these costs, plus the\\r\\n   additional maintenance burden on the JDK, would outweigh the benefit\\r\\n   of integrating an external library.\\r\\n\\r\\n - _Do nothing, since JSON is already handled well by external\\r\\n   libraries._\\r\\n\\r\\n   Doing nothing would not serve the larger goal of enabling simple\\r\\n   tasks to be accomplished more easily and with less ceremony,\\r\\n   especially for simple programs and for newcomers to the Java\\r\\n   Platform.\\r\\n\\r\\n   Adding any external dependency to an application incurs cost and adds\\r\\n   risk. There are probably applications that could benefit from using\\r\\n   JSON but that do not, because their maintainers wish to minimize cost\\r\\n   and risk. Such applications would benefit from having a standard JSON\\r\\n   API in the Java Platform.\\r\\n\\r\\n - \\u003Ca id=\\"duplicates\\"/\\u003E _Allow duplicate member names within JSON\\r\\n   objects._\\r\\n\\r\\n   This has been a longstanding issue with JSON. Early specifications\\r\\n   were underdetermined with respect to the handling of duplicate\\r\\n   member names within a single JSON object. JSON libraries behaved\\r\\n   inconsistently, or else provided application-settable options to\\r\\n   select the policy for handling duplicate names.\\r\\n\\r\\n   Unfortunately, an object with duplicate names is fundamentally\\r\\n   ambiguous. When the issue of duplicate names was discussed on the\\r\\n   [ECMAScript Discussion\\r\\n   List](https://esdiscuss.org/topic/json-duplicate-keys) in 2013, the\\r\\n   concern about prohibiting duplicate names was that doing so would\\r\\n   invalidate existing documents. Thus, the \\"should be unique\\" wording\\r\\n   (instead of \\"must\\") was retained, and it has been carried over to\\r\\n   current specifications. In particular, [RFC&nbsp;8259\\r\\n   says](https://www.rfc-editor.org/info/rfc8259/#section-4):\\r\\n\\r\\n   \\u003E The names within an object SHOULD be unique.\\r\\n   \\u003E \\r\\n   \\u003E ...\\r\\n   \\u003E \\r\\n   \\u003E An object whose names are all unique is interoperable in the sense\\r\\n   \\u003E that all software implementations receiving that object will agree\\r\\n   \\u003E on the name-value mappings. When the names within an object are not\\r\\n   \\u003E unique, the behavior of software that receives such an object is\\r\\n   \\u003E unpredictable.\\r\\n\\r\\n   The unpredictability arises when the object is processed by a system\\r\\n   consisting of multiple, independently-developed JSON libraries. This\\r\\n   can lead to hard-to-diagnose errors, security vulnerabilities,\\r\\n   decreased interoperability, and general lack of robustness. This\\r\\n   phenomenon is discussed in\\r\\n   [RFC&nbsp;9413](https://www.rfc-editor.org/info/rfc9413/),\\r\\n   \\"Maintaining Robust Protocols\\".\\r\\n\\r\\n   For these reasons, we have chosen a strict approach where duplicate\\r\\n   names are unconditionally treated as errors. The strict approach\\r\\n   gives high confidence in the correctness of parsed documents. We hope\\r\\n   that the erroneous documents mentioned in the 2013 ECMAScript\\r\\n   conversation have been corrected in the intervening years, and that\\r\\n   the software that produced those documents has been fixed.\\r\\n\\r\\n - _Support trailing commas, comments, or other syntax extensions._\\r\\n\\r\\n   There are several variants of JSON, e.g.,\\r\\n   [JSON5](https://json5.org/), that support comments or trailing commas\\r\\n   within arrays and objects. These extensions are intended to\\r\\n   facilitate the hand-editing of JSON documents.\\r\\n\\r\\n   Given our focus on simplicity and machine-to-machine communication,\\r\\n   we do not support such extensions. Doing so would enlarge the testing\\r\\n   matrix, increase the possibility of interoperability errors, and\\r\\n   increase the overall development and maintenance burden.\\r\\n\\r\\n   A common workaround is to pre-process incoming extended-JSON\\r\\n   documents before parsing them. For example, single-line comments on\\r\\n   lines starting with `'#'` characters are easily removed prior to\\r\\n   parsing:\\r\\n\\r\\n   ```\\r\\n   String jsonc = Files.readString(Path.of(\\"file-with-comments.json\\"));\\r\\n   String json = jsonc.replaceAll(\\"(?m)^\\\\\\\\s*#.*$\\", \\"\\");\\r\\n   JsonValue jv = Json.parse(json);\\r\\n   ```\\r\\n\\r\\n - _Provide additional conversion methods._\\r\\n\\r\\n   We could include additional conversion methods for added convenience, such as `asBigDecimal()`.\\r\\n   Such a method would be trivial to implement. However, the fact that a method is simple to include\\r\\n   does not by itself justify adding it to the API. Our goal is to provide a minimal API that applications\\r\\n   can build on top of. It is also just as easy for an application to do `new BigDecimal(JsonNumber.toString())`.\\r\\n\\r\\n   While we find that each extra method is perhaps one more convenience, it is also one more opinionated\\r\\n   decision. Additionally, since the conversion methods are defined uniformly on `JsonValue`, our goal is to keep\\r\\n   this set of methods small and focused, consistent with our intention to make this API easy to use. By providing\\r\\n   the minimal set of required operations, we leave users of this API free to add on the extra layers they require,\\r\\n   tailored to their specific needs.\\r\\n\\r\\n## Testing\\r\\n\\r\\nWe will rigorously test the JSON API to ensure that only canonical forms\\r\\nof RFC&nbsp;8259 JSON can be parsed and generated. This will help ensure\\r\\nthat using the API will not result in inconsistencies when interacting\\r\\nwith other JSON libraries. To accomplish this, we will not only add\\r\\ncomprehensive unit tests to the JDK but also leverage the\\r\\nestablished [JSON Parsing Test\\r\\nSuite](https://github.com/nst/JSONTestSuite), which contains numerous\\r\\nedge-case inputs.\\r\\n\\r\\n\\r\\n## Risks and Assumptions\\r\\n\\r\\n- We assume that input JSON documents can fit in memory, as either a\\r\\n  `String` or a `char` array. Given our tree-based model, if we were to\\r\\n  allow JSON sources such as files or network connections, issues such\\r\\n  as insufficient memory would be possible with large documents. This\\r\\n  decision aligns with our minimalist design philosophy.\\r\\n\\r\\n- A risk of this proposal is that this new API might end up being used\\r\\n  in applications that are already using external JSON libraries,\\r\\n  resulting in messiness and confusion. We believe this risk is\\r\\n  outweighed by the benefits.\\r\\n\\r\\n- During the incubation period, we will gather more information about\\r\\n  use cases involving generating and transforming JSON documents, in\\r\\n  order to evolve these areas of the API. In addition, we will continue\\r\\n  to consider forthcoming pattern-matching language features that might\\r\\n  affect the design of the API.\\r\\n\\r\\n\\r\\n## Appendix: Weather Forecast Example \\u003Ca id=\\"appendix\\"/\\u003E\\r\\n\\r\\nThe following program issues a request to the [U.S.&nbsp;National\\r\\nWeather Service REST API] for a seven-day weather forecast for Santa Clara, CA.\\r\\nIt receives a JSON document in the response body. The program then parses the\\r\\ndocument, navigates into the structure, and obtains an array of forecasts.\\r\\nIt then extracts the temperature from each forecast, averages them, and prints\\r\\nthe result.\\r\\n\\r\\n```\\r\\nimport java.net.*;\\r\\nimport java.net.http.*;\\r\\nimport jdk.incubator.json.Json;\\r\\nimport jdk.incubator.json.JsonValue;\\r\\n\\r\\nvoid main() throws Exception {\\r\\n    var query = \\"https://api.weather.gov/gridpoints/MTR/97,83/forecast\\";\\r\\n    var client = HttpClient.newHttpClient();\\r\\n    var request = HttpRequest.newBuilder(URI.create(query)).build();\\r\\n    var response = client.send(request, HttpResponse.BodyHandlers.ofString());\\r\\n    String body = response.body();\\r\\n    JsonValue json = Json.parse(response.body());\\r\\n    json.get(\\"properties\\").get(\\"periods\\").asList().stream()\\r\\n        .mapToInt(j -\\u003E j.get(\\"temperature\\").asInt())\\r\\n        .average()\\r\\n        .ifPresent(IO::println);\\r\\n}\\r\\n```\\r\\n\\r\\n### Enabling the incubating API\\r\\n\\r\\nThe JSON API is, at present, an [incubator\\r\\nmodule](https://openjdk.org/jeps/11), disabled by default. To use it,\\r\\nyou must enable it via the command-line option `--add-modules\\r\\njdk.incubator.json`, which adds the incubator module to the set of\\r\\nmodules available for resolution. To run the above example program, you\\r\\nmust provide this option at both compile time and run time.\\r\\n\\r\\nTo run the average forecast program as a single-file source code\\r\\nprogram, do this:\\r\\n\\r\\n    $ java --add-modules jdk.incubator.json Weather.java\\r\\n\\r\\nThe output will be something like:\\r\\n\\r\\n    WARNING: Using incubator modules: jdk.incubator.json\\r\\n    53.357142857142854\\r\\n\\r\\nTo compile the program with `javac` and run it with `java`, do this:\\r\\n\\r\\n    $ javac --add-modules jdk.incubator.json Weather.java\\r\\n    $ java --add-modules jdk.incubator.json Weather\\r\\n\\r\\nYou can use `jshell` to experiment interactively with the API. As\\r\\nbefore, you must enable the incubator module on the command line:\\r\\n\\r\\n    $ jshell --add-modules jdk.incubator.json\\r\\n    jshell\\u003E import jdk.incubator.json.*\\r\\n    jshell\\u003E Json.parse(\\"\\"\\"\\r\\n       ...\\u003E { \\"name\\": \\"Today\\", \\"temperature\\": 54 }\\r\\n       ...\\u003E \\"\\"\\")\\r\\n    $2 ==\\u003E {\\"name\\":\\"Today\\",\\"temperature\\":54}\\r\\n    jshell\\u003E $2.get(\\"temperature\\").asInt()\\r\\n    $3 ==\\u003E 54\\r\\n    jshell\\u003E \\r\\n\\r\\n[U.S.&nbsp;National Weather Service REST API]: https://www.weather.gov/documentation/services-web-api\\r\\n",
                "customfield_11100": "0|i38pa3:",
                "archiveddate": null,
                "customfield_10800": {
                  "self": "https://bugs.openjdk.org/rest/api/2/user?username=naoto",
                  "name": "naoto",
                  "key": "naoto",
                  "avatarUrls": {
                    "48x48": "https://bugs.openjdk.org/secure/useravatar?ownerId=naoto&avatarId=17312",
                    "24x24": "https://bugs.openjdk.org/secure/useravatar?size=small&ownerId=naoto&avatarId=17312",
                    "16x16": "https://bugs.openjdk.org/secure/useravatar?size=xsmall&ownerId=naoto&avatarId=17312",
                    "32x32": "https://bugs.openjdk.org/secure/useravatar?size=medium&ownerId=naoto&avatarId=17312"
                  },
                  "displayName": "Naoto Sato",
                  "active": true,
                  "timeZone": "America/Los_Angeles"
                },
                "attachment": [],
                "customfield_10801": null,
                "aggregatetimeestimate": null,
                "customfield_10802": [
                  {
                    "self": "https://bugs.openjdk.org/rest/api/2/user?username=abuckley",
                    "name": "abuckley",
                    "key": "abuckley",
                    "avatarUrls": {
                      "48x48": "https://bugs.openjdk.org/secure/useravatar?avatarId=10122",
                      "24x24": "https://bugs.openjdk.org/secure/useravatar?size=small&avatarId=10122",
                      "16x16": "https://bugs.openjdk.org/secure/useravatar?size=xsmall&avatarId=10122",
                      "32x32": "https://bugs.openjdk.org/secure/useravatar?size=medium&avatarId=10122"
                    },
                    "displayName": "Alex Buckley",
                    "active": true,
                    "timeZone": "America/Los_Angeles"
                  }
                ],
                "summary": "JEP 540: Simple JSON API (Incubator)",
                "creator": {
                  "self": "https://bugs.openjdk.org/rest/api/2/user?username=naoto",
                  "name": "naoto",
                  "key": "naoto",
                  "avatarUrls": {
                    "48x48": "https://bugs.openjdk.org/secure/useravatar?ownerId=naoto&avatarId=17312",
                    "24x24": "https://bugs.openjdk.org/secure/useravatar?size=small&ownerId=naoto&avatarId=17312",
                    "16x16": "https://bugs.openjdk.org/secure/useravatar?size=xsmall&ownerId=naoto&avatarId=17312",
                    "32x32": "https://bugs.openjdk.org/secure/useravatar?size=medium&ownerId=naoto&avatarId=17312"
                  },
                  "displayName": "Naoto Sato",
                  "active": true,
                  "timeZone": "America/Los_Angeles"
                },
                "subtasks": [],
                "customfield_11010": null,
                "reporter": {
                  "self": "https://bugs.openjdk.org/rest/api/2/user?username=naoto",
                  "name": "naoto",
                  "key": "naoto",
                  "avatarUrls": {
                    "48x48": "https://bugs.openjdk.org/secure/useravatar?ownerId=naoto&avatarId=17312",
                    "24x24": "https://bugs.openjdk.org/secure/useravatar?size=small&ownerId=naoto&avatarId=17312",
                    "16x16": "https://bugs.openjdk.org/secure/useravatar?size=xsmall&ownerId=naoto&avatarId=17312",
                    "32x32": "https://bugs.openjdk.org/secure/useravatar?size=medium&ownerId=naoto&avatarId=17312"
                  },
                  "displayName": "Naoto Sato",
                  "active": true,
                  "timeZone": "America/Los_Angeles"
                },
                "aggregateprogress": {
                  "progress": 0,
                  "total": 0
                },
                "customfield_11006": null,
                "customfield_10710": {
                  "self": "https://bugs.openjdk.org/rest/api/2/customFieldOption/19116",
                  "value": "M",
                  "id": "19116",
                  "disabled": false
                },
                "customfield_10711": null,
                "customfield_11801": null,
                "customfield_11800": null,
                "customfield_10713": null,
                "customfield_11802": null,
                "duedate": null,
                "progress": {
                  "progress": 0,
                  "total": 0
                },
                "comment": {
                  "comments": [
                    {
                      "self": "https://bugs.openjdk.org/rest/api/2/issue/5143806/comment/14861183",
                      "id": "14861183",
                      "author": {
                        "self": "https://bugs.openjdk.org/rest/api/2/user?username=smarks",
                        "name": "smarks",
                        "key": "smarks",
                        "avatarUrls": {
                          "48x48": "https://bugs.openjdk.org/secure/useravatar?ownerId=smarks&avatarId=11508",
                          "24x24": "https://bugs.openjdk.org/secure/useravatar?size=small&ownerId=smarks&avatarId=11508",
                          "16x16": "https://bugs.openjdk.org/secure/useravatar?size=xsmall&ownerId=smarks&avatarId=11508",
                          "32x32": "https://bugs.openjdk.org/secure/useravatar?size=medium&ownerId=smarks&avatarId=11508"
                        },
                        "displayName": "Stuart Marks",
                        "active": true,
                        "timeZone": "America/Los_Angeles"
                      },
                      "body": "Adding Alex Buckley as a reviewer per his request.",
                      "updateAuthor": {
                        "self": "https://bugs.openjdk.org/rest/api/2/user?username=smarks",
                        "name": "smarks",
                        "key": "smarks",
                        "avatarUrls": {
                          "48x48": "https://bugs.openjdk.org/secure/useravatar?ownerId=smarks&avatarId=11508",
                          "24x24": "https://bugs.openjdk.org/secure/useravatar?size=small&ownerId=smarks&avatarId=11508",
                          "16x16": "https://bugs.openjdk.org/secure/useravatar?size=xsmall&ownerId=smarks&avatarId=11508",
                          "32x32": "https://bugs.openjdk.org/secure/useravatar?size=medium&ownerId=smarks&avatarId=11508"
                        },
                        "displayName": "Stuart Marks",
                        "active": true,
                        "timeZone": "America/Los_Angeles"
                      },
                      "created": "2026-03-04T20:56:41.161+0000",
                      "updated": "2026-03-04T20:56:41.161+0000"
                    },
                    {
                      "self": "https://bugs.openjdk.org/rest/api/2/issue/5143806/comment/14877509",
                      "id": "14877509",
                      "author": {
                        "self": "https://bugs.openjdk.org/rest/api/2/user?username=abaragiola",
                        "name": "abaragiola",
                        "key": "JIRAUSER24509",
                        "avatarUrls": {
                          "48x48": "https://bugs.openjdk.org/secure/useravatar?ownerId=JIRAUSER24509&avatarId=23102",
                          "24x24": "https://bugs.openjdk.org/secure/useravatar?size=small&ownerId=JIRAUSER24509&avatarId=23102",
                          "16x16": "https://bugs.openjdk.org/secure/useravatar?size=xsmall&ownerId=JIRAUSER24509&avatarId=23102",
                          "32x32": "https://bugs.openjdk.org/secure/useravatar?size=medium&ownerId=JIRAUSER24509&avatarId=23102"
                        },
                        "displayName": "Amedeo Baragiola",
                        "active": true,
                        "timeZone": "America/Los_Angeles"
                      },
                      "body": "Please add me (from JVT) as a reviewer to the respective MRs",
                      "updateAuthor": {
                        "self": "https://bugs.openjdk.org/rest/api/2/user?username=abaragiola",
                        "name": "abaragiola",
                        "key": "JIRAUSER24509",
                        "avatarUrls": {
                          "48x48": "https://bugs.openjdk.org/secure/useravatar?ownerId=JIRAUSER24509&avatarId=23102",
                          "24x24": "https://bugs.openjdk.org/secure/useravatar?size=small&ownerId=JIRAUSER24509&avatarId=23102",
                          "16x16": "https://bugs.openjdk.org/secure/useravatar?size=xsmall&ownerId=JIRAUSER24509&avatarId=23102",
                          "32x32": "https://bugs.openjdk.org/secure/useravatar?size=medium&ownerId=JIRAUSER24509&avatarId=23102"
                        },
                        "displayName": "Amedeo Baragiola",
                        "active": true,
                        "timeZone": "America/Los_Angeles"
                      },
                      "created": "2026-05-06T16:02:26.836+0000",
                      "updated": "2026-05-06T16:02:26.836+0000"
                    }
                  ],
                  "maxResults": 1000,
                  "total": 2,
                  "startAt": 0
                },
                "votes": {
                  "self": "https://bugs.openjdk.org/rest/api/2/issue/JDK-8344154/votes",
                  "votes": 2,
                  "hasVoted": false
                },
                "archivedby": null
              },
              "renderedFields": null
            }
            """;
    }
}
