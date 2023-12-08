


import javax.management.remote.rest.json.JSONElement;
import javax.management.remote.rest.json.parser.JSONParser;
import javax.management.remote.rest.json.parser.ParseException;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 *
 */
public class JsonParserTest {
    
    public JsonParserTest() {
    }

    // TODO add test methods here.
    // The methods must be annotated with annotation @Test. For example:
    //
    // @Test
    // public void hello() {}

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
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
    
    @Test (dataProvider = "getJsonString")
    public void parserTest(String input) throws ParseException {
        JSONParser jsonParser = new JSONParser(input);
        JSONElement parse = jsonParser.parse();
        String output = parse.toJsonString();
        System.out.println("\t: " + input);
        System.out.println("\t: " + output);
//        Assert.assertEquals(input, output);
    }
}
