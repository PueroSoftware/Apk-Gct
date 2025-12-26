package org.pucusoft.geocelltrack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.Test;

import java.io.InputStream;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class PayloadSchemaTest {

    @Test
    public void validateExamplePayload() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream schemaStream = PayloadSchemaTest.class.getResourceAsStream("/payload-schema.json");
        InputStream payloadStream = PayloadSchemaTest.class.getResourceAsStream("/payload_example.json");

        if (schemaStream == null) throw new IllegalStateException("No se encontró payload-schema.json en test resources");
        if (payloadStream == null) throw new IllegalStateException("No se encontró payload_example.json en test resources");

        JsonNode schemaNode = mapper.readTree(schemaStream);
        JsonNode payload = mapper.readTree(payloadStream);

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        JsonSchema schema = factory.getSchema(schemaNode);

        Set<ValidationMessage> errors = schema.validate(payload);
        for (ValidationMessage e : errors) {
            System.out.println(e.getMessage());
        }

        assertTrue("Payload debe cumplir el schema. Errores: " + errors, errors.isEmpty());
    }
}
