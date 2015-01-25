package org.opennms.web.rest.rrd;

import org.junit.runners.Parameterized;
import org.opennms.core.test.xml.XmlTest;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;

public class QueryRequestTest extends XmlTest<QueryRequest> {

    public QueryRequestTest(QueryRequest sampleObject, Object sampleXml, String schemaFile) {
        super(sampleObject, sampleXml, schemaFile);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() throws ParseException {
        QueryRequest query1 = new QueryRequest();
        query1.setStep(300);
        query1.setStart(1000);
        query1.setEnd(2000);

        return Arrays.asList(new Object[][]{{
                query1,
                "" +
                        "<query-request step=\"300\" start=\"1000\" end=\"2000\">" +
                        "</query-request>",
                null
        }});
    }
}
