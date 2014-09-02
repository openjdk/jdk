/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.HashMap;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class SpecialCaseErrorHandler implements ErrorHandler {
    public static final boolean DEBUG = false;

    private HashMap<String, Boolean> errors;

    public SpecialCaseErrorHandler(String[] specialCases) {
        errors = new HashMap<>();
        for (int i = 0; i < specialCases.length; ++i) {
            errors.put(specialCases[i], Boolean.FALSE);
        }
    }

    public void reset() {
        errors.keySet().stream().forEach((error) -> {
            errors.put(error, Boolean.FALSE);
        });
    }

    @Override
    public void warning(SAXParseException arg0) throws SAXException {
        if (DEBUG) {
            System.err.println(arg0.getMessage());
        }
    }

    @Override
    public void error(SAXParseException arg0) throws SAXException {
        if (DEBUG) {
            System.err.println(arg0.getMessage());
        }
        errors.keySet().stream().filter((error) -> (arg0.getMessage().startsWith(error))).forEach((error) -> {
            errors.put(error, Boolean.TRUE);
        });
    }

    public void fatalError(SAXParseException arg0) throws SAXException {
        throw arg0;
    }

    public boolean specialCaseFound(String key) {
        return ((Boolean) errors.get(key)).booleanValue();
    }
}
