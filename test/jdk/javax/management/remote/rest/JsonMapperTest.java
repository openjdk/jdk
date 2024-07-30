/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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


import jdk.internal.management.remote.rest.json.JSONElement;
import jdk.internal.management.remote.rest.json.JSONObject;
import jdk.internal.management.remote.rest.json.parser.JSONParser;
import jdk.internal.management.remote.rest.json.parser.ParseException;

import jdk.internal.management.remote.rest.mapper.JSONMapper;
import jdk.internal.management.remote.rest.mapper.JSONMappingFactory;


import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @test
 * @modules jdk.management.rest/jdk.internal.management.remote.rest.http
 *          jdk.management.rest/jdk.internal.management.remote.rest.json
 *          jdk.management.rest/jdk.internal.management.remote.rest.json.parser
 *          jdk.management.rest/jdk.internal.management.remote.rest.mapper
 *          jdk.management.rest/jdk.internal.management.remote.rest
 *
 * @run testng JsonMapperTest
 *
 */
public class JsonMapperTest {
    
    public JsonMapperTest() {
    }

    @DataProvider
    public Object[][] getJsonString() {
        Object[][] data = new Object[2][1];
        data[0][0] = "{organisms:[\n" +
"        {\n" +
"        id:10929,\n" +
"        name:\"Bovine Rotavirus\"\n" +
"        },\n" +
"        {\n" +
"        id:9606,\n" +
"        name:\"Homo Sapiens\"\n" +
"        }\n" +
"        ],\n" +
"proteins:[\n" +
"        {\n" +
"        label:\"NSP3\",\n" +
"        description:\"Rotavirus Non Structural Protein 3\",\n" +
"        organism-id: 10929,\n" +
"        acc: \"ACB38353\"\n" +
"        },\n" +
"        {\n" +
"        label:\"EIF4G\",\n" +
"        description:\"eukaryotic translation initiation factor 4 gamma\",\n" +
"        organism-id: 9606,\n" +
"        boolflag: true,\n" +
"        longFloat: 12351123.1235123e-10,\n" +                
"        singleQuote: \'asd\',\n" +                                
"        acc:\"AAI40897\"\n" +
"        }\n" +
"        ],\n" +
"interactions:[\n" +
"        {\n" +
"        label:\"NSP3 interacts with EIF4G1\",\n" +
"        pubmed-id:[77120248,38201627],\n" +
"        proteins:[\"ACB38353\",\"AAI40897\"]\n" +
"        }\n" +
"        ]}";
        
        data[1][0] = "{\"name\":\"com.example:type=QueueSampler\",\"exec\":\"testMethod1\",\"params\":[[1,2,3],\"abc\",5,[\"asd\",\"3\",\"67\",\"778\"],[{date:\"2016-3-2\",size:3,head:\"head\"}],[{date:\"2016-3-2\",size:3,head:\"head\"}]]}";
        return data;
    }
    
/*    @Test (dataProvider = "getJsonString")
    public void parserTest(String input) throws ParseException {
        JSONParser jsonParser = new JSONParser(input);
        JSONElement parse = jsonParser.parse();
        String output = parse.toJsonString();
        System.out.println("\t: " + input);
        System.out.println("\t: " + output);
//        Assert.assertEquals(input, output);
    } */

    @Test
    public void mapperTest() throws Exception {
        toJsonAndBack("hi there");
        toJsonAndBack(0);
        toJsonAndBack(123);
        toJsonAndBack(new String[] { "a", "b", "c" } );
        
    } 

    public void toJsonAndBack(Object o) throws Exception {
        System.out.println("Test toJsonAndBack: Java Object: " + o);
        JSONMapper mapper = JSONMappingFactory.INSTANCE.getTypeMapper(o);
        if (mapper == null) throw new RuntimeException("no mapper for: " + o);        
        System.out.println("Mapper = " + mapper);
        JSONElement j = mapper.toJsonValue(o);
        System.out.println("  mapper gives JsonValue ->" + j);
        System.out.println("          which is Class ->" + j.getClass());
        System.out.println("            asJsonString ->" + j.toJsonString());

        // mapper = JSONMappingFactory.INSTANCE.getTypeMapper(o);
        mapper = JSONMappingFactory.INSTANCE.getTypeMapper(j);
        if (mapper == null) throw new RuntimeException("no mapper for: " + j);
        System.out.println("Mapper = " + mapper);
        Object oo = mapper.toJavaObject((JSONElement) j);
        //Object oo = mapper.toJavaObject(j.toJsonString());
        System.out.println("mapper gives Java Object ->" + oo);
        System.out.println("          which is Class ->" + oo.getClass());
    }

}
