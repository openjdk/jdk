/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package example1;

import jdk.jfr.AnnotationElement;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.EventFactory;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Label;
import jdk.jfr.Description;
import jdk.jfr.Category;
import jdk.jfr.ContentType;
import jdk.jfr.Period;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;
import jdk.jfr.MetadataDefinition;
import jdk.jfr.Relational;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.Configuration;
import jdk.jfr.SettingDefinition;
import jdk.jfr.SettingControl;
import jdk.jfr.Timestamp;
import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.RecordedEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

public class Snippets {

    void AnnotationElementOverview() {
        // @start region="AnnotationElementOverview"
        List<AnnotationElement> typeAnnotations = new ArrayList<>();
        typeAnnotations.add(new AnnotationElement(Name.class, "com.example.HelloWorld"));
        typeAnnotations.add(new AnnotationElement(Label.class, "Hello World"));
        typeAnnotations.add(new AnnotationElement(Description.class, "Helps programmer getting started"));

        List<AnnotationElement> fieldAnnotations = new ArrayList<>();
        fieldAnnotations.add(new AnnotationElement(Label.class, "Message"));

        List<ValueDescriptor> fields = new ArrayList<>();
        fields.add(new ValueDescriptor(String.class, "message", fieldAnnotations));

        EventFactory f = EventFactory.create(typeAnnotations, fields);
        Event event = f.newEvent();
        event.commit();
        // @end
    }

    record CPU(String id, float temperature) {
    }

    private static List<CPU> listCPUs() {
        return List.of();
    }

    // @start region="ContentTypeDeclaration"
    @MetadataDefinition
    @ContentType
    @Name("com.example.Temperature")
    @Label("Temperature")
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Temperature {
        public final static String KELVIN = "KELVIN";
        public final static String CELSIUS = "CELSIUS";
        public final static String FAHRENEHIT = "FAHRENHEIT";

        String value() default CELSIUS;
    }
    // @end

    // @start region="ContentTypeEvent"
    @Name("com.example.CPU")
    @Label("CPU")
    @Category({ "Hardware", "CPU" })
    @Period("1 s")
    @StackTrace(false)
    static public class CPUEvent extends Event {
        @Label("ID")
        String id;

        @Temperature(Temperature.KELVIN)
        @Label("Temperature")
        float temperature;
    }

    public static void main(String... args) throws InterruptedException {
        FlightRecorder.addPeriodicEvent(CPUEvent.class, () -> {
            for (var cpu : listCPUs()) {
                CPUEvent event = new CPUEvent();
                event.id = cpu.id();
                event.temperature = cpu.temperature(); // in Kelvin
                event.commit();
            }
        });
        Thread.sleep(10_000);
    }
    // @end

    // @start region="ContentTypeConsumption"
    void printTemperaturesInCelsius(Path file) throws IOException {
        for (RecordedEvent event : RecordingFile.readAllEvents(file)) {
            for (ValueDescriptor field : event.getEventType().getFields()) {
                for (AnnotationElement ae : field.getAnnotationElements()) {
                    ContentType type = ae.getAnnotation(ContentType.class);
                    if (type != null) {
                        if (ae.getTypeName().equals("com.example.Temperature")) {
                            double value = event.getDouble(field.getName());
                            String unit = (String) ae.getValue("value");
                            double celsius = switch (unit) {
                                case "CELSIUS" -> value;
                                case "KELVIN" -> value - 273.15;
                                case "FAHRENHEIT" -> (value - 32) / 1.8;
                                default -> throw new IllegalStateException("Unknown temperature unit '" + unit + "'");
                            };
                            System.out.println(celsius + " C");
                        } else {
                            System.err.println("Can't format content type " + ae.getTypeName() + " for field " + field.getName());
                        }
                    }
                }
            }
        }
    }
    // @end

    // @start region="EventOverview"
    public class Example {

        @Label("Hello World")
        @Description("Helps programmer getting started")
        static class HelloWorld extends Event {
            @Label("Message")
            String message;
        }

        public static void main(String... args) {
            HelloWorld event = new HelloWorld();
            event.message = "hello, world!";
            event.commit();
        }
    }
    // @end

    void EventFactoryOverview() {
        // @start region="EventFactoryOverview"
        List<ValueDescriptor> fields = new ArrayList<>();
        List<AnnotationElement> messageAnnotations = Collections.singletonList(new AnnotationElement(Label.class, "Message"));
        fields.add(new ValueDescriptor(String.class, "message", messageAnnotations));
        List<AnnotationElement> numberAnnotations = Collections.singletonList(new AnnotationElement(Label.class, "Number"));
        fields.add(new ValueDescriptor(int.class, "number", numberAnnotations));

        String[] category = { "Example", "Getting Started" };
        List<AnnotationElement> eventAnnotations = new ArrayList<>();
        eventAnnotations.add(new AnnotationElement(Name.class, "com.example.HelloWorld"));
        eventAnnotations.add(new AnnotationElement(Label.class, "Hello World"));
        eventAnnotations.add(new AnnotationElement(Description.class, "Helps programmer getting started"));
        eventAnnotations.add(new AnnotationElement(Category.class, category));

        EventFactory f = EventFactory.create(eventAnnotations, fields);

        Event event = f.newEvent();
        event.set(0, "hello, world!");
        event.set(1, 4711);
        event.commit();
        // @end
    }

    void EventSettingOverview() throws Exception {
        // @start region="EventSettingOverview"
        Recording r = new Recording();
        r.enable("jdk.CPULoad")
         .withPeriod(Duration.ofSeconds(1));
        r.enable("jdk.FileWrite")
         .withoutStackTrace()
         .withThreshold(Duration.ofNanos(10));
        r.start();
        Thread.sleep(10_000);
        r.stop();
        r.dump(Files.createTempFile("recording", ".jfr"));
        // @end
    }

    void FlightRecorderTakeSnapshot() throws Exception {
        // @start region="FlightRecorderTakeSnapshot"
        try (Recording snapshot = FlightRecorder.getFlightRecorder().takeSnapshot()) {
            if (snapshot.getSize() > 0) {
                snapshot.setMaxSize(100_000_000);
                snapshot.setMaxAge(Duration.ofMinutes(5));
                snapshot.dump(Paths.get("snapshot.jfr"));
            }
        }
        // @end
    }

    // @start region="MetadataDefinitionOverview"
    @MetadataDefinition
    @Label("Severity")
    @Description("Value between 0 and 100 that indicates severity. 100 is most severe.")
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE })
    public @interface Severity {
        int value() default 50;
    }

    @MetadataDefinition
    @Label("Transaction Id")
    @Relational
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.FIELD })
    public @interface TransactionId {
    }

    @Severity(80)
    @Label("Transaction Blocked")
    class TransactionBlocked extends Event {
        @TransactionId
        @Label("Transaction")
        long transactionId1;

        @TransactionId
        @Label("Transaction Blocker")
        long transactionId2;
    }
    // @end

    // @start region="RelationalOverview"
    @MetadataDefinition
    @Relational
    @Name("com.example.OrderId")
    @Label("Order ID")
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface OrderId {
    }

    @Name("com.example.Order")
    @Label("Order")
    @Category("Orders")
    class OrderEvent extends Event {
        @Label("Order ID")
        @OrderId
        long orderId;

        @Label("Order Date")
        @Timestamp
        long orderDate;
    }

    @Name("com.example.OrderLine")
    @Label("Order Line")
    @Category("Orders")
    class OrderLineEvent extends Event {
        @Label("Order ID")
        @OrderId
        long orderId;

        @Label("Quantity")
        long quantity;

        @Label("Product")
        String product;
    }
    // @end

 void RecordingnOverview() throws Exception {
     // @start region="RecordingOverview"
     Configuration c = Configuration.getConfiguration("default");
     Recording r = new Recording(c);
     r.start();
     System.gc();
     Thread.sleep(5000);
     r.stop();
     r.dump(Files.createTempFile("my-recording", ".jfr"));
     // @end
 }

 // @start region="SettingControlOverview1"
 final class RegExpControl extends SettingControl {
     private Pattern pattern = Pattern.compile(".*");

     @Override
     public void setValue(String value) {
         this.pattern = Pattern.compile(value);
     }

     @Override
     public String combine(Set<String> values) {
         return String.join("|", values);
     }

     @Override
     public String getValue() {
         return pattern.toString();
     }

     public boolean matches(String s) {
         return pattern.matcher(s).find();
     }
 }
 // @end

 class HttpServlet {
 }

 class HttpServletRequest {
     public String getRequestURI() {
         return null;
     }
 }

 class HttpServletResponse {
 }

 // @start region="SettingControlOverview2"
 abstract class HTTPRequest extends Event {
     @Label("Request URI")
     protected String uri;

     @Label("Servlet URI Filter")
     @SettingDefinition
     protected boolean uriFilter(RegExpControl regExp) {
         return regExp.matches(uri);
     }
 }

 @Label("HTTP Get Request")
 class HTTPGetRequest extends HTTPRequest {
 }

 @Label("HTTP Post Request")
 class HTTPPostRequest extends HTTPRequest {
 }

 class ExampleServlet extends HttpServlet {
     protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
         HTTPGetRequest request = new HTTPGetRequest();
         request.begin();
         request.uri = req.getRequestURI();
         code: // @replace regex='code:' replacement="..."
         request.commit();
     }

     protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
         HTTPPostRequest request = new HTTPPostRequest();
         request.begin();
         request.uri = req.getRequestURI();
         code: // @replace regex='code:' replacement="..."
         request.commit();
     }
 }
 // @end

 void SettingControlOverview3() {
     // @start region="SettingControlOverview3"
     Recording r = new Recording();
     r.enable("HTTPGetRequest").with("uriFilter", "https://www.example.com/list/.*");
     r.enable("HTTPPostRequest").with("uriFilter", "https://www.example.com/login/.*");
     r.start();
     // @end
 }

 // @start region="SettingDefinitionOverview"
 class HelloWorld extends Event {

     @Label("Message")
     String message;

     @SettingDefinition
     @Label("Message Filter")
     public boolean filter(RegExpControl regExp) {
         return regExp.matches(message);
     }
 }
 // @end
}
