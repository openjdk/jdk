/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

 /*
 * @test
 * @run main/othervm -Dcom.sun.management.jmxremote.rest.port=8686 -Dcom.sun.management.config.file=jdk/test/javax/management/remote/rest/mgmt1.properties DefaultRestAdapter
 */
import java.io.IOException;
import java.util.Arrays;

public class DefaultRestAdapter {

    public static void main(String[] args) throws IOException, Exception {
        Arrays.asList(args).stream().forEach(System.out::println);
        Thread.sleep(1000000);
    }
}



