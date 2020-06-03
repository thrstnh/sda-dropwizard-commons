# SDA Commons Shared AsyncAPI

[![javadoc](https://javadoc.io/badge2/org.sdase.commons/sda-commons-shared-asyncapi/javadoc.svg)](https://javadoc.io/doc/org.sdase.commons/sda-commons-shared-asyncapi)

This module contains the [`AsyncApiGenerator`](./src/main/java/org/sdase/commons/shared/asyncapi/AsyncApiGenerator.java)
to generate [AsyncAPI](https://www.asyncapi.com/) specs from a template and model classes.
The AsyncAPI specification is the industry standard for defining asynchronous APIs.

## Usage

If the code first approach is used to create an AsyncAPI spec this module provides assistance.
One way to use this module is:

* A template file defining the channels using the AsyncAPI spec is part of the API.
* Definitions for Models classes are generated from code and annotations.
* This module is used to combine the models and template to a self-contained spec file.
* The generated AsyncAPI spec is committed into source control.

A manual written AsyncAPI spec template might look like this and can be stored as a resource:

```yaml
asyncapi: '2.0.0'
id: 'urn:org:sdase:example:cars'
defaultContentType: application/json

info:
  title: Cars Example
  description: This example demonstrates how to define events around *cars*.
  version: '1.0.0'

channels:
  'car-events':
    publish:
      operationId: publishCarEvents
      summary: Car related events
      description: These are all events that are related to a car
      message:
        oneOf:
          - $ref: '#/components/messages/CarManufactured'
          - $ref: '#/components/messages/CarScrapped'

components:
  messages:
    CarManufactured:
      title: Car Manufactured
      description: An event that represents when a new car is manufactured
      payload:
        $ref: './schema.json#/definitions/CarManufactured'
    CarScrapped:
      title: Car Scrapped
      description: An event that represents when a car is scrapped
      payload:
        $ref: './schema.json#/definitions/CarScrapped'
```

To automatically generate the AsyncAPI spec and ensure that it is committed to version control, 
one can use a test like this: 

```java
    @Test
    public void generateAndVerifySpec() throws IOException {
        String expected = AsyncApiGenerator
            .builder()
            .withAsyncApiBase(BaseEvent.class.getResource("/asyncapi.yaml"))
            .withSchema("./schema.json", BaseEvent.class)
            .generateYaml();

        Path asyncApiPath = Paths.get("./asyncapi.yaml");
        String actual = new String(Files.readAllBytes(asyncApiPath));

        try (AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(actual)
                .as("The current asyncapi.yaml file is not up-to-date. If this happens "
                    + "locally, just run the test again. The asyncapi.yaml file is updated "
                    + "automatically after running this test. If this happens in the CI, make sure "
                    + "that you have committed the latest asyncapi.yaml file!")
                .isEqualTo(expected);

            Files.write(asyncApiPath, expected.getBytes());
        }
    }
```

If desired, the module also allows to generate the JSON schema files, for example to use them to validate test data.
Use [JsonSchemaGenerator](./src/main/java/org/sdase/commons/shared/asyncapi/JsonSchemaGenerator.java) to create standalone JSON schemas.

 
## Document Models

You can document the models using annotations like `JsonPropertyDescription` from Jackson or
`JsonSchemaExamples` from [`mbknor-jackson-jsonSchema`](https://github.com/mbknor/mbknor-jackson-jsonSchema).
See the tests of this module for [example model classes](./src/test/java/org/sdase/commons/shared/asyncapi/models).
