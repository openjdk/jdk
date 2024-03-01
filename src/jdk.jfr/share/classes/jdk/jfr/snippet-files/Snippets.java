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
package jdk.jfr.snippets;

import jdk.jfr.AnnotationElement;
import jdk.jfr.BooleanFlag;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.EventFactory;
import jdk.jfr.EventType;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Label;
import jdk.jfr.DataAmount;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Category;
import jdk.jfr.ContentType;
import jdk.jfr.Period;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;
import jdk.jfr.MetadataDefinition;
import jdk.jfr.Relational;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.consumer.RecordingStream;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
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

    // @start region="BooleanFlagOverview"
    @BooleanFlag
    @Name("example.Rollback")
    @Label("Rollback")
    @Description("Include transactions that are rollbacked")
    public static class RollbackSetting extends SettingControl {
        private boolean value = true;

        @Override
        public String combine(Set<String> values) {
            return values.contains("true") ? "true" : "false";
        }

        @Override
        public void setValue(String settingValue) {
            value = "true".equals(settingValue);
        }

        @Override
        public String getValue() {
            return Boolean.toString(value);
        }

        public boolean shouldEmit() {
            return value;
        }
    }

    @Name("example.Transaction")
    public static class TransactionEvent extends Event {
        @Label("Context")
        String context;

        @Label("Rollback")
        boolean rollback;

        @SettingDefinition
        @Name("rollback")
        public boolean rollback(RollbackSetting rollbackSetting) {
            return rollback && rollbackSetting.shouldEmit();
        }
    }
    // @end

    static class ConfigurationOverview {
    // @start region="ConfigurationxsOverview"
    public static void main(String... args) throws Exception {
        if (args.length == 0) {
            System.out.println("Configurations:");
            for (Configuration c : Configuration.getConfigurations()) {
                System.out.println("Name: " + c.getName());
                System.out.println("Label: " + c.getLabel());
                System.out.println("Description: " + c.getDescription());
                System.out.println("Provider: " + c.getProvider());
                System.out.println();
            }
        } else {
            String name = args[0];
            Configuration c = Configuration.getConfiguration(name);
            try (Recording r = new Recording(c)) {
                System.out.println("Starting recording with settings:");
                for (Map.Entry<String, String> setting : c.getSettings().entrySet()) {
                    System.out.println(setting.getKey() + " = " + setting.getValue());
                }
                r.start();
            }
        }
    }
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
        public static final String KELVIN = "KELVIN";
        public static final String CELSIUS = "CELSIUS";
        public static final String FAHRENEHIT = "FAHRENHEIT";

        String value() default CELSIUS;
    }
    // @end

    // @start region="ContentTypeEvent"
    @Name("com.example.CPU")
    @Label("CPU")
    @Category({ "Hardware", "CPU" })
    @Period("1 s")
    @StackTrace(false)
    public static class CPUEvent extends Event {
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

    // @start region="DataAmountOverview"
    @Name("com.example.ImageRender")
    @Label("Image Render")
    public class ImageRender extends Event {
        @Label("Height")
        long height;

        @Label("Width")
        long width;

        @Label("Color Depth")
        @DataAmount(DataAmount.BITS)
        int colorDepth;

        @Label("Memory Size")
        @DataAmount // bytes by default
        long memorySize;
    }
    // @end

    // @start region="EnabledOverview"
    @Name("StopWatch")
    @Label("Stop Watch")
    @Category("Debugging")
    @StackTrace(false)
    @Enabled(false)
    public static class StopWatchEvent extends Event {
    }

    public void update() {
        StopWatchEvent e = new StopWatchEvent();
        e.begin();
        code: // @replace regex='code:' replacement="..."
        e.commit();
    }
    // @end
    /*
    // @start region="EnabledOverviewCommandLine"
    java -XX:StartFlightRecording:StopWatch#enabled=true ...
    // @end
    */
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
        try (Recording r = new Recording()) {
            r.enable("jdk.CPULoad")
             .withPeriod(Duration.ofSeconds(1));
            r.enable("jdk.FileWrite")
             .withoutStackTrace()
             .withThreshold(Duration.ofNanos(10));
            r.start();
            Thread.sleep(10_000);
            r.stop();
            r.dump(Files.createTempFile("recording", ".jfr"));
        }
        // @end
    }
    void EventTypeOverview() {
        // @start region="EventTypeOverview"
        for (EventType eventType : FlightRecorder.getFlightRecorder().getEventTypes()) {
            System.out.println("Event Type: " + eventType.getName());
            if (eventType.getLabel() != null) {
                System.out.println("Label: " + eventType.getLabel());
            }
            if (eventType.getDescription() != null) {
                System.out.println("Description: " + eventType.getDescription());
            }
            StringJoiner s = new StringJoiner(" / ");
            for (String category : eventType.getCategoryNames()) {
                s.add(category);
            }
            System.out.println("Category: " + s);
            System.out.println("Fields: " + eventType.getFields().size());
            System.out.println("Annotations: " + eventType.getAnnotationElements().size());
            System.out.println("Settings: " + eventType.getSettingDescriptors().size());
            System.out.println("Enabled: " + eventType.isEnabled());
            System.out.println();
        }
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

    void PeriodOverview() {
        // @start region = "PeriodOverview"
        @Period("1 s")
        @Name("Counter")
        class CountEvent extends Event {
            int count;
        }
        @Period("3 s")
        @Name("Fizz")
        class FizzEvent extends Event {
        }
        @Period("5 s")
        @Name("Buzz")
        class BuzzEvent extends Event {
        }

        var counter = new AtomicInteger();
        FlightRecorder.addPeriodicEvent(CountEvent.class, () -> {
            CountEvent event = new CountEvent();
            event.count = counter.incrementAndGet();
            event.commit();
        });
        FlightRecorder.addPeriodicEvent(FizzEvent.class, () -> {
            new FizzEvent().commit();
        });
        FlightRecorder.addPeriodicEvent(BuzzEvent.class, () -> {
            new BuzzEvent().commit();
        });

        var sb = new StringBuilder();
        var last = new AtomicInteger();
        var current = new AtomicInteger();
        try (var r = new RecordingStream()) {
            r.onEvent("Counter", e -> current.set(e.getValue("count")));
            r.onEvent("Fizz", e -> sb.append("Fizz"));
            r.onEvent("Buzz", e -> sb.append("Buzz"));
            r.onFlush(() -> {
                if (current.get() != last.get()) {
                    System.out.println(sb.isEmpty() ? current : sb);
                    last.set(current.get());
                    sb.setLength(0);
                }
            });
            r.start();
        }
        // @end
    }

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
     try (Recording r = new Recording(c)) {
         r.start();
         System.gc();
         Thread.sleep(5000);
         r.stop();
         r.dump(Files.createTempFile("my-recording", ".jfr"));
     }
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

 static class ValueDsecriptorOverview {
     // @start region="ValueDescriptorOverview"
     void printTypes() {
         Map<String, List<ValueDescriptor>> typeMap = new LinkedHashMap<>();
         for (EventType eventType : FlightRecorder.getFlightRecorder().getEventTypes()) {
             findTypes(typeMap, eventType.getName(), eventType.getFields());
         }
         for (String type : typeMap.keySet()) {
             System.out.println("Type: " + type);
             for (ValueDescriptor field : typeMap.get(type)) {
                 System.out.println(" Field: " + field.getName());
                 String arrayBrackets = field.isArray() ? "[]" : "";
                 System.out.println("  Type: " + field.getTypeName() + arrayBrackets);
                 if (field.getLabel() != null) {
                     System.out.println("  Label: " + field.getLabel());
                 }
                 if (field.getDescription() != null) {
                     System.out.println("  Description: " + field.getDescription());
                 }
                 if (field.getContentType() != null) {
                     System.out.println("  Content Types: " + field.getContentType());
                 }
             }
             System.out.println();
         }
     }

     void findTypes(Map<String, List<ValueDescriptor>> typeMap, String typeName, List<ValueDescriptor> fields) {
         if (!typeMap.containsKey(typeName)) {
             typeMap.put(typeName, fields);
             for (ValueDescriptor subField : fields) {
                 findTypes(typeMap, subField.getTypeName(), subField.getFields());
             }
         }
     }
     // @end
 }
}
