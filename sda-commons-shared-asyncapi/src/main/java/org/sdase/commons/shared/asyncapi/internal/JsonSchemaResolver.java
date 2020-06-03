package org.sdase.commons.shared.asyncapi.internal;

import com.fasterxml.jackson.databind.JsonNode;

public interface JsonSchemaResolver {

  JsonNode resolve(String url);
}
