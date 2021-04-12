/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.dsig.spec.SignatureMethodParameterSpec;

public class RSAPSSParameterSpec implements SignatureMethodParameterSpec {

    private int trailerField;
    private int saltLength;
    private String digestName;

    public int getTrailerField() {
        return trailerField;
    }
    public void setTrailerField(int trailerField) {
        this.trailerField = trailerField;
    }
    public int getSaltLength() {
        return saltLength;
    }
    public void setSaltLength(int saltLength) {
        this.saltLength = saltLength;
    }
    public String getDigestName() {
        return digestName;
    }
    public void setDigestName(String digestName) {
        this.digestName = digestName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((digestName == null) ? 0 : digestName.hashCode());
        result = prime * result + saltLength;
        result = prime * result + trailerField;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RSAPSSParameterSpec other = (RSAPSSParameterSpec)obj;
        if (digestName == null) {
            if (other.digestName != null)
                return false;
        } else if (!digestName.equals(other.digestName))
            return false;
        if (saltLength != other.saltLength)
            return false;
        return trailerField == other.trailerField;
    }

}