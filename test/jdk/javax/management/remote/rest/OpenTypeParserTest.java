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

import javax.management.MBeanAttributeInfo;

import javax.management.openmbean.OpenType;

import jdk.internal.management.remote.rest.mapper.OpenTypeParser;

import jdk.internal.management.remote.rest.json.JSONElement;
import jdk.internal.management.remote.rest.json.JSONObject;
import jdk.internal.management.remote.rest.json.parser.JSONParser;
import jdk.internal.management.remote.rest.json.parser.ParseException;

import jdk.internal.management.remote.rest.mapper.JSONMapper;
import jdk.internal.management.remote.rest.mapper.JSONMappingFactory;


import org.testng.Assert;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;


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
 * @run testng TypeParserTest 
 *
 */
public class OpenTypeParserTest {
    
    public OpenTypeParserTest() {
    }

    String s1 = "javax.management.openmbean.ArrayType(name=[J,dimension=1,elementType=javax.management.openmbean.SimpleType(name=java.lang.Long),primitiveArray=true)";

    String s2 = "javax.management.openmbean.ArrayType(name=[Ljavax.management.openmbean.CompositeData;,dimension=1,elementType=javax.management.openmbean.CompositeType(name=java.lang.StackTraceElement,items=((itemName=classLoaderName,itemType=javax.management.openmbean.SimpleType(name=java.lang.String)),(itemName=className,itemType=javax.management.openmbean.SimpleType(name=java.lang.String)),(itemName=fileName,itemType=javax.management.openmbean.SimpleType(name=java.lang.String)),(itemName=lineNumber,itemType=javax.management.openmbean.SimpleType(name=java.lang.Integer)),(itemName=methodName,itemType=javax.management.openmbean.SimpleType(name=java.lang.String)),(itemName=moduleName,itemType=javax.management.openmbean.SimpleType(name=java.lang.String)),(itemName=moduleVersion,itemType=javax.management.openmbean.SimpleType(name=java.lang.String)),(itemName=nativeMethod,itemType=javax.management.openmbean.SimpleType(name=java.lang.Boolean)))),primitiveArray=false)";

    @Test
    public void basicTypes() throws Exception {

    }

    @Test
    public void arrayTypes() throws Exception {

        var p = new OpenTypeParser(s1);
        OpenType<?> type = p.parse();
        assertNotNull(type, "No type parsed from s1");
        System.out.println(type); 

        p = new OpenTypeParser(s2);
        type = p.parse();
        System.out.println(type); 
        assertNotNull(type, "No type parsed from s2");
    } 
}
