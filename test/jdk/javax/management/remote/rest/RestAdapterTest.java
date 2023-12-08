import jdk.internal.management.remote.rest.http.HttpResponse;
import jdk.internal.management.remote.rest.http.HttpUtil;
import jdk.internal.management.remote.rest.json.JSONArray;
import jdk.internal.management.remote.rest.json.JSONElement;
import jdk.internal.management.remote.rest.json.JSONObject;
import jdk.internal.management.remote.rest.json.JSONPrimitive;
import jdk.internal.management.remote.rest.json.parser.JSONParser;
import jdk.internal.management.remote.rest.mapper.JSONMapper;
import jdk.internal.management.remote.rest.mapper.JSONMappingFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.management.*;
import javax.management.remote.*;
import jdk.internal.management.remote.rest.PlatformRestAdapter;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

 /* @test
 * @summary Unit tests for Rest adapter
 * @library /test/lib
 * @modules jdk.management.rest/jdk.internal.management.remote.rest.http
 *          jdk.management.rest/jdk.internal.management.remote.rest.json
 *          jdk.management.rest/jdk.internal.management.remote.rest.json.parser
 *          jdk.management.rest/jdk.internal.management.remote.rest.mapper
 *          jdk.management.rest/jdk.internal.management.remote.rest
 * @build RestAdapterTest
 * @run testng/othervm  RestAdapterTest
 */

@Test
public class RestAdapterTest {

    private JMXConnectorServer cs;
    private String restUrl = "";
    JMXConnector connector;

    @BeforeClass
    public void setupServers() throws IOException {
        MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
        JMXServiceURL url = new JMXServiceURL("rmi", null, 0);
        cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, platformMBeanServer);
        cs.start();
        JMXServiceURL addr = cs.getAddress();
        connector = JMXConnectorFactory.connect(addr, null);

        String testSrcRoot = System.getProperty("test.src") + File.separator;
        String configFile = testSrcRoot + "mgmt.properties";
        File f = new File(configFile);
        Properties properties = null;
        if (f.exists()) {
            properties = new Properties();
            properties.load(new FileInputStream(f));
        }

        PlatformRestAdapter.init(properties);
        restUrl = PlatformRestAdapter.getBaseURL();
    }

    @AfterClass
    public void tearDownServers() throws IOException {
        connector.close();
        cs.stop();
        PlatformRestAdapter.stop();
    }

    private Set<String> rmiGetAllMBeans() {
        try {
            MBeanServerConnection mBeanServer = connector.getMBeanServerConnection();
            Set<ObjectInstance> objectInstances = mBeanServer.queryMBeans(null, null);
            return objectInstances.stream().map(a -> a.getObjectName().getCanonicalName()).collect(Collectors.toSet());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Map<String, Object> rmiGetAttributes(String name) {
        try {
            MBeanServerConnection mBeanServer = connector.getMBeanServerConnection();
            ObjectName objectName = new ObjectName(name);
            MBeanInfo mInfo = mBeanServer.getMBeanInfo(objectName);
            String[] attrs = Stream.of(mInfo.getAttributes())
                    .map(MBeanAttributeInfo::getName)
                    .toArray(String[]::new);

            Map<String, Object> result = new LinkedHashMap<>();
            AttributeList attrVals = mBeanServer.getAttributes(objectName, attrs);
            List<String> missingAttrs = new ArrayList<>(Arrays.asList(attrs));
            attrVals.asList().forEach(a -> {
                missingAttrs.remove(a.getName());
                result.put(a.getName(), a.getValue());
            });

            for (String attr : missingAttrs) {
                try {
                    mBeanServer.getAttribute(objectName, attr);
                    result.put(attr, "< Error: No such attribute >");
                } catch (RuntimeException ex) {
                    if (ex.getCause() instanceof UnsupportedOperationException) {
                        result.put(attr, "< Attribute not supported >");
                    } else if (ex.getCause() instanceof IllegalArgumentException) {
                        result.put(attr, "< Invalid attributes >");
                    }
                } catch (AttributeNotFoundException e) {
                    result.put(attr, "< Attribute not found >");
                }
            }
            return result;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Set<String> rmiGetOperations(String name) {
        try {
            MBeanServerConnection mBeanServer = connector.getMBeanServerConnection();
            ObjectName objectName = new ObjectName(name);
            MBeanOperationInfo[] operationInfos = mBeanServer.getMBeanInfo(objectName).getOperations();
            Set<String> rmiOps = Stream.of(operationInfos)
                    .map(MBeanFeatureInfo::getName).collect(Collectors.toSet());
            return rmiOps;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Set<String> restGetAllMBeans() {
        try {
            String url = restUrl + "/platform/mbeans";
            Set<String> mbeanNames = new HashSet<>();
            do {
                HttpResponse httpResponse = executeHttpRequest(url);
                if (httpResponse.getCode() != 200)
                    throw new RuntimeException(httpResponse.getBody());

                String firstPage = httpResponse.getBody();
                JSONParser parser = new JSONParser(firstPage);
                JSONObject root = (JSONObject) parser.parse();
                JSONArray mbeansNode = (JSONArray) root.get("mbeans");
                for (JSONElement je : mbeansNode) {
                    JSONObject jobj = (JSONObject) je;
                    JSONPrimitive jp = (JSONPrimitive) jobj.get("name");
                    String name = (String) jp.getValue();
                    mbeanNames.add(name);
                    JSONPrimitive jhref = (JSONPrimitive) jobj.get("href");
                    String href = (String) jhref.getValue();
                    verifyHttpResponse(executeHttpRequest(href));
                    JSONPrimitive jinfo = (JSONPrimitive) jobj.get("info");
                    String info = (String) jinfo.getValue();
                    verifyHttpResponse(executeHttpRequest(info));
                }

                JSONObject linkObj = (JSONObject) root.get("_links");
                if (linkObj == null) {
                    break;
                }
                if (linkObj.get("next") == null)
                    break;

                JSONPrimitive element = (JSONPrimitive) linkObj.get("next");
                String nextUrl = (String) element.getValue();
                if (nextUrl.equalsIgnoreCase(url)) {
                    break;
                } else {
                    url = nextUrl;
                }
            } while (true);
            return mbeanNames;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private JSONObject restGetAttributes(String name) {
        try {
            String url = "/platform/mbeans/" + name;
            HttpResponse httpResponse = executeHttpRequest(url);
            if (httpResponse.getCode() == 200) {
                JSONParser parser = new JSONParser(httpResponse.getBody());
                JSONElement root = parser.parse();
                JSONElement element = ((JSONObject) root).get("attributes");
                if (element != null && element instanceof JSONObject)
                    return (JSONObject) element;
                else {
                    return new JSONObject();
                }
            } else {
                throw new RuntimeException("HTTP GET for [" + url + "] failed, response = " + httpResponse.getBody());
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private JSONArray restGetOperations(String name) {
        try {
            String url = "/platform/mbeans/" + name;
            HttpResponse httpResponse = executeHttpRequest(url);
            if (httpResponse.getCode() == 200) {
                JSONParser parser = new JSONParser(httpResponse.getBody());
                JSONElement root = parser.parse();
                JSONElement element = ((JSONObject) root).get("operations");
                if (element != null && element instanceof JSONArray)
                    return (JSONArray) element;
                else {
                    return new JSONArray();
                }
            } else {
                throw new RuntimeException("HTTP GET for [" + url + "] failed, response = " + httpResponse.getBody());
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private HttpResponse executeHttpRequest(String inputUrl) {
        return executeHttpRequest(inputUrl, "", false);
    }

    private HttpResponse executeHttpRequest(String inputUrl, String body, boolean isPost) {
        try {
            if (inputUrl != null && !inputUrl.isEmpty()) {
                URL url;
                if (!inputUrl.startsWith("http")) {
                    url = new URL(HttpUtil.escapeUrl(restUrl + inputUrl));
                } else {
                    url = new URL(inputUrl);
                }
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestProperty("Content-Type", "application/json;");
                String userCredentials = "username1:password1";
                String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userCredentials.getBytes());
                con.setRequestProperty("Authorization", basicAuth);
                if (isPost) {
                    con.setDoOutput(true);
                    con.setRequestMethod("POST");

                    try (OutputStreamWriter out = new OutputStreamWriter(
                            con.getOutputStream())) {
                        out.write(body);
                        out.flush();
                    }
                } else {
                    con.setDoOutput(false);
                }

                try {
                    int status = con.getResponseCode();
                    StringBuilder sbuf;
                    if (status == 200) {
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(con.getInputStream()))) {
                            sbuf = new StringBuilder();
                            String input;
                            while ((input = br.readLine()) != null) {
                                sbuf.append(input);
                            }
                        }
                    } else {
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(con.getErrorStream()))) {
                            sbuf = new StringBuilder();
                            String input;
                            while ((input = br.readLine()) != null) {
                                sbuf.append(input);
                            }
                        }
                    }
                    return new HttpResponse(status, sbuf.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return null;
    }

    private void verifyHttpResponse(HttpResponse httpResponse) {
        try {
            Assert.assertEquals(httpResponse.getCode(), 200);
            JSONParser parser = new JSONParser(httpResponse.getBody());
            parser.parse();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private JSONObject restGetMBeanOperationsBulkOp(String name) {
        JSONArray jsonArray = restGetOperations(name);
        JSONArray ops = jsonArray.stream()
                .filter(a -> ((JSONObject) a).get("arguments") == null)
                .map((JSONElement a) -> ((JSONObject) a).get("name"))
                .collect(Collectors.toCollection(JSONArray::new));
        JSONObject result = new JSONObject();
        if (ops != null && !ops.isEmpty()) {
            result.put("operations", ops);
        }
        return result;
    }

    private JSONObject restGetMBeanAttributeBulkOp(String name) {
        try {
            MBeanServerConnection mBeanServer = connector.getMBeanServerConnection();
            ObjectName objectName = new ObjectName(name);

            MBeanAttributeInfo[] attrInfos = mBeanServer.getMBeanInfo(objectName).getAttributes();
            Set<String> writableAttrs = Stream.of(attrInfos)
                    .filter(MBeanAttributeInfo::isWritable)
                    .map(MBeanFeatureInfo::getName).collect(Collectors.toSet());

            JSONObject writeAttrMap = new JSONObject();
            List<String> invalidAttrs = Arrays.asList("< Attribute not supported >",
                    "< Invalid attributes >",
                    "< Attribute not found >",
                    "< Error: No such attribute >");

            JSONObject attrMap = restGetAttributes(name);
            writableAttrs.stream().filter(a -> {
                JSONElement element = attrMap.get(a);
                if (element instanceof JSONPrimitive
                        && ((JSONPrimitive) element).getValue() instanceof String) {
                    String attrVal = (String) ((JSONPrimitive) element).getValue();
                    if (invalidAttrs.contains(attrVal))
                        return false;
                }
                return true;
            }).forEach(a -> writeAttrMap.put(a, attrMap.get(a)));

            JSONObject jsonObject = new JSONObject();
            JSONArray attrs = new JSONArray();
            attrMap.keySet().forEach(a -> attrs.add(new JSONPrimitive(a)));
            if (attrs != null && !attrs.isEmpty()) {
                jsonObject.put("get", attrs);
            }
            if (writeAttrMap != null && !writeAttrMap.isEmpty()) {
                jsonObject.put("set", writeAttrMap);
            }

            JSONObject result = new JSONObject();
            if (jsonObject != null && !jsonObject.isEmpty()) {
                result.put("attributes", jsonObject);
            }
            return result;
        } catch (Exception ex) {
            ex.getCause().printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    @DataProvider(name = "allMBeans")
    private Object[][] allMBeans() {
        Set<String> names = restGetAllMBeans();
        Object[] objects = names.stream().toArray(Object[]::new);
        Object[][] result = new Object[objects.length][1];
        for (int i = 0; i < objects.length; i++) {
            result[i][0] = objects[i];
        }
        return result;
    }

    @Test
    public void testAllMBeanServers() {
        try {
            HttpResponse httpResponse = executeHttpRequest(restUrl);
            Assert.assertEquals(httpResponse.getCode(), 200);
            JSONParser parser = new JSONParser(httpResponse.getBody());
            JSONObject parse = (JSONObject) parser.parse();
            JSONArray links = (JSONArray) parse.get("mBeanServers");
            for(JSONElement elem : links) {
                JSONObject jobj = (JSONObject) elem;
                String link = (String) ((JSONPrimitive)jobj.get("href")).getValue();
                HttpResponse hr = executeHttpRequest(link);
                Assert.assertEquals(httpResponse.getCode(), 200);
                JSONParser parser1 = new JSONParser(hr.getBody());
                JSONObject parse1 = (JSONObject) parser1.parse();
                JSONObject links1 = (JSONObject) parse1.get("_links");
                String link3 = (String) ((JSONPrimitive)links1.get("mbeans")).getValue();
                verifyHttpResponse(executeHttpRequest(link3));
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    public void testAllMBeans() {
        Set<String> restMbeans = restGetAllMBeans();
        Set<String> rmiMbeans = rmiGetAllMBeans();
        long count = restMbeans.stream().filter(mbeanName -> !rmiMbeans.contains(mbeanName)).count();
        Assert.assertEquals(count, 0);
    }

    public void testMBeanFiltering() {
        String url = restUrl + "/platform/mbeans?";
        List<String> filtersOk = Arrays.asList("objectname=*:type=DiagnosticCommand,*",
        "objectname=java.lang:*&page=2",
        "objectname=java.lang:*&page=1",
        "objectname=*:type=Diag*");

        List<String> filtersKo = Arrays.asList("","*:type=DiagnosticCommand,*","objectname=java.lang:*&page=1&invalid=4");

        for(String filter : filtersOk) {
            HttpResponse httpResponse = executeHttpRequest(url + filter);
            Assert.assertEquals(httpResponse.getCode(),200);
        }

        for(String filter : filtersKo) {
            HttpResponse httpResponse = executeHttpRequest(url + filter);
            Assert.assertEquals(httpResponse.getCode(),HttpURLConnection.HTTP_BAD_REQUEST);
        }
    }

    @Test
    public void testAllMBeanInfo() {
        Set<String> names = restGetAllMBeans();
        for (String name : names) {
            String url = "/platform/mbeans/" + name + "/info";
            HttpResponse httpResponse = executeHttpRequest(url);
            verifyHttpResponse(httpResponse);
        }
    }

    @Test(priority = 3)
    public void testMbeanNoArgOperations() {
        Set<String> mbeans = restGetAllMBeans();
        for (String name : mbeans) {
            JSONArray restoperations = restGetOperations(name);
            Set<String> rmiOps = rmiGetOperations(name);
            Set<String> restOps = restoperations.stream().map((JSONElement a) -> {
                JSONElement elem = ((JSONObject) a).get("name");
                if (elem instanceof JSONPrimitive
                        && (((JSONPrimitive) elem).getValue() instanceof String)) {
                    return (String) ((JSONPrimitive) elem).getValue();
                } else {
                    return null;
                }
            }).collect(Collectors.toSet());
            Assert.assertEquals(rmiOps, restOps);

            for (JSONElement jsonElement : restoperations) {
                JSONObject jsonObject = (JSONObject) jsonElement;
                String opUrl = (String) ((JSONPrimitive) jsonObject.get("href")).getValue();
                if (jsonObject.get("arguments") == null) {
                    HttpResponse httpResponse = executeHttpRequest(opUrl, "", true);
                    verifyHttpResponse(httpResponse);
                }
            }
        }
    }

    @Test(priority = 2)
    public void testMBeanSetAttributes() {
        try {
            Set<String> mbeans = restGetAllMBeans();
            MBeanServerConnection mBeanServer = connector.getMBeanServerConnection();
            for (String name : mbeans) {
                ObjectName objectName = new ObjectName(name);
                String url = "/platform/mbeans/" + objectName.getCanonicalName();
                JSONObject attrMap = restGetAttributes(name);
                MBeanAttributeInfo[] attrInfos = mBeanServer.getMBeanInfo(objectName).getAttributes();
                Set<String> writableAttrs = Stream.of(attrInfos)
                        .filter(MBeanAttributeInfo::isWritable)
                        .map(MBeanFeatureInfo::getName).collect(Collectors.toSet());

                if (writableAttrs.isEmpty())
                    continue;

                JSONObject writeAttrMap = new JSONObject();
                List<String> invalidAttrs = Arrays.asList(new String[]{"< Attribute not supported >",
                        "< Invalid attributes >",
                        "< Attribute not found >",
                        "< Error: No such attribute >"});

                writableAttrs.stream().filter(a -> {
                    JSONElement element = attrMap.get(a);
                    if (element instanceof JSONPrimitive
                            && ((JSONPrimitive) element).getValue() instanceof String) {
                        String attrVal = (String) ((JSONPrimitive) element).getValue();
                        if (invalidAttrs.contains(attrVal))
                            return false;
                    }
                    return true;
                }).forEach(a -> writeAttrMap.put(a, attrMap.get(a)));
                HttpResponse httpResponse = executeHttpRequest(url, writeAttrMap.toJsonString(), true);
                if (httpResponse.getCode() == 200) {
                    String body = httpResponse.getBody();
                    JSONParser parser = new JSONParser(body);
                    JSONObject jsonObject = (JSONObject) parser.parse();
                    Assert.assertEquals(jsonObject.size(), writeAttrMap.size());
                    Assert.assertEquals(jsonObject.keySet(), writeAttrMap.keySet());

                    for (JSONElement elem : jsonObject.values()) {
                        String output = (String) ((JSONPrimitive) elem).getValue();
                        Assert.assertEquals(output.equalsIgnoreCase("success"), true);
                    }
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test(priority = 1)
    public void testMBeanGetAttributes() {
        try {
            Set<String> mbeans = restGetAllMBeans();
            for (String name : mbeans) {
                Map<String, Object> rmiAttrs = rmiGetAttributes(name);
                JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(rmiAttrs);
                JSONObject rmiJson = (JSONObject) typeMapper.toJsonValue(rmiAttrs);
                JSONObject restJson = restGetAttributes(name);
                Assert.assertEquals(restJson.size(), rmiJson.size());
                Assert.assertEquals(rmiJson.keySet(), restJson.keySet());
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test(priority = 4)
    public void testThreadMXBeanBulkRequest() {
        String name = "java.lang:type=Threading";
        String url = "/platform/mbeans/java.lang:type=Threading";

        JSONObject jsonObject = restGetAttributes(name);
        JSONArray jarr = (JSONArray) jsonObject.get("AllThreadIds");

        Long[] threadIds = jarr.stream().map(a -> {
            if (a instanceof JSONPrimitive && ((JSONPrimitive) a).getValue() instanceof Long) {
                return (Long) ((JSONPrimitive) a).getValue();
            } else {
                return -1;
            }
        }).toArray(Long[]::new);

        JSONObject args = new JSONObject();
        JSONArray array = new JSONArray();
        Stream.of(threadIds).forEach(a -> array.add(new JSONPrimitive(a)));
        args.put("p0", array);
        args.put("p1", new JSONPrimitive(true));
        args.put("p2", new JSONPrimitive(true));
        JSONObject jobj1 = new JSONObject();
        jobj1.put("getThreadInfo", args);

        JSONObject jobj2 = new JSONObject();

        jobj2.putAll(restGetMBeanAttributeBulkOp(name));
        jobj2.put("operations", jobj1);

        HttpResponse httpResponse = executeHttpRequest(url, jobj2.toJsonString(), true);
        verifyHttpResponse(httpResponse);

        JSONArray arr = new JSONArray();
        arr.add(new JSONPrimitive("findMonitorDeadlockedThreads"));
        arr.add(new JSONPrimitive("resetPeakThreadCount"));
        arr.add(new JSONPrimitive("findDeadlockedThreads"));
        arr.add(jobj1);

        jobj2.clear();
        jobj2.putAll(restGetMBeanAttributeBulkOp(name));
        jobj2.put("operations", arr);

        httpResponse = executeHttpRequest(url, jobj2.toJsonString(), true);
        verifyHttpResponse(httpResponse);

        jobj2.clear();
        jobj2.putAll(restGetMBeanAttributeBulkOp(name));
        jobj2.put("operations", "resetPeakThreadCount");

        httpResponse = executeHttpRequest(url, jobj2.toJsonString(), true);
        verifyHttpResponse(httpResponse);
    }

    @Test(priority = 5)
    public void testThreadMXBeanThreadInfo() {
        String name = "java.lang:type=Threading";

        JSONObject jsonObject = restGetAttributes(name);
        JSONArray jarr = (JSONArray) jsonObject.get("AllThreadIds");

        Long[] threadIds = jarr.stream().map(a -> {
            if (a instanceof JSONPrimitive && ((JSONPrimitive) a).getValue() instanceof Long) {
                return (Long) ((JSONPrimitive) a).getValue();
            } else {
                return -1;
            }
        }).toArray(Long[]::new);

        JSONArray operations = restGetOperations("java.lang:type=Threading");

        JSONObject threadInfoRequest = (JSONObject) operations.stream()
                .filter(a -> {
                    JSONObject jobj = (JSONObject) a;
                    JSONElement elem = ((JSONObject) a).get("name");
                    if (elem instanceof JSONPrimitive
                            && (((JSONPrimitive) elem).getValue() instanceof String)) {
                        return ((JSONPrimitive) elem).getValue().equals("getThreadInfo");
                    } else {
                        return false;
                    }
                }).findFirst().get();

        String postUrl1 = (String) ((JSONPrimitive) threadInfoRequest.get("href")).getValue();

        // Build arguments
        // 1. getThreadInfo(long id)
        JSONObject args = new JSONObject();
        args.put("p0", new JSONPrimitive(threadIds[0]));
        HttpResponse httpResponse = executeHttpRequest(postUrl1, args.toJsonString(), true);
        verifyHttpResponse(httpResponse);
        args.clear();

        // 2. getThreadInfo(long[] ids)
        JSONArray array = new JSONArray();
        Stream.of(threadIds).forEach(a -> array.add(new JSONPrimitive(a)));
        args.put("p0", array);
        httpResponse = executeHttpRequest(postUrl1, args.toJsonString(), true);
        verifyHttpResponse(httpResponse);

        //3. getThreadInfo(long[] ids, boolean lockedMonitors, boolean lockedSynchronizers)
        args.put("p1", new JSONPrimitive(true));
        args.put("p2", new JSONPrimitive(true));
        httpResponse = executeHttpRequest(postUrl1, args.toJsonString(), true);
        verifyHttpResponse(httpResponse);
        args.clear();

        //4. getThreadInfo(long id, int maxDepth)
        args.put("p0", new JSONPrimitive(threadIds[0]));
        args.put("p1", new JSONPrimitive(10));
        httpResponse = executeHttpRequest(postUrl1, args.toJsonString(), true);
        verifyHttpResponse(httpResponse);
        args.clear();

        //5. getThreadInfo(long[] ids, int maxDepth)
        JSONArray jarr1 = new JSONArray();
        Stream.of(threadIds).forEach(a -> jarr1.add(new JSONPrimitive(a)));
        args.put("p0", jarr1);
        args.put("p1", new JSONPrimitive(10));
        httpResponse = executeHttpRequest(postUrl1, args.toJsonString(), true);
        verifyHttpResponse(httpResponse);
    }

    @Test(priority = 4)
    public void testAllMBeansBulkRequest() {
        Set<String> allNames = restGetAllMBeans();
        String url = restUrl + "/platform/mbeans";
        JSONObject result = new JSONObject();
        for (String name : allNames) {
            JSONObject attrNode = restGetMBeanAttributeBulkOp(name);
            JSONObject opsNode = restGetMBeanOperationsBulkOp(name);
            JSONObject jobj = new JSONObject();
            if (attrNode != null && !attrNode.isEmpty()) {
                jobj.putAll(attrNode);
            }
            if (opsNode != null && !opsNode.isEmpty()) {
                jobj.putAll(opsNode);
            }
            result.put(name, jobj);
        }
        HttpResponse httpResponse = executeHttpRequest(url, result.toJsonString(), true);
        verifyHttpResponse(httpResponse);
    }

    @Test(priority = 6)
    public void testMbeansQueryBulkRequest() {
        String url = restUrl + "/platform/mbeans";
        String request = "{\"?*:type=MemoryPool,*\":{\"attributes\":{\"get\":[\"Name\",\"Usage\"]},\"operations\":\"resetPeakUsage\"},\"java.lang:name=Compressed Class Space,type=MemoryPool\":{\"attributes\":{\"get\":[\"MemoryManagerNames\"]}}}";
        HttpResponse httpResponse = executeHttpRequest(url, request, true);
        verifyHttpResponse(httpResponse);
    }
}