package org.sda.commons.server.jackson.hal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sda.commons.server.jackson.hal.HalLinkProvider.methodOn;

import io.openapitools.jackson.dataformat.hal.HALLink;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HalLinkProviderTest {

  HalLinkProvider testee = new HalLinkProvider();

  @Test
  public void shouldProvideHalLinkForNormalPathParams() {
    final HALLink test = testee.linkTo(methodOn(TestApi.class).testMethod("TEST"));
    assertThat(test.getHref()).isEqualTo("/testPath/TEST");
  }

  @Test
  public void shouldProvideHalLinkForNormalPathParamsWithClass() {
    final HALLink test = testee.linkTo(methodOn(TestController.class).testMethod("TEST"));
    assertThat(test.getHref()).isEqualTo("/testPath/TEST");
  }

  @Test
  public void shouldProvideHalLinkForQueryParam() {
    final HALLink test = testee.linkTo(methodOn(TestApi.class).testMethodQueryParam("TEST"));
    assertThat(test.getHref()).isEqualTo("/testPath?testRequestParam=TEST");
  }

  @Test
  public void shouldProvideHalLinkForDetailed() {
    final HALLink test =
        testee.linkTo(methodOn(TestApi.class).testMethodDetail("TEST", 1, "testTheQuery"));
    assertThat(test.getHref()).isEqualTo("/testPath/TEST/detail/testTheQuery?query=1");
  }

  @Test
  public void shouldFailWithoutAnnotation() {
    assertThatThrownBy(
            () ->
                testee.linkTo(methodOn(TestApi.class).testMethodWithoutPathParamAnnotation("FAIL")))
        .isInstanceOf(InvalidHalLinkInvocationException.class)
        .hasMessageContaining("@PathParam")
        .hasMessageContaining("@QueryParam");
  }

  @Test
  public void shouldDoNothingWhenNoParamsAreProvided() {
    final HALLink test = testee.linkTo(methodOn(TestApi.class).testMethodWithoutParams());
    assertThat(test.getHref()).isEqualTo("/testPathWithNoParams");
  }

  @Test
  public void shouldFailWithNonProxiedMethod() {
    assertThatThrownBy(() -> testee.linkTo("testMethod"))
        .isInstanceOf(InvalidHalLinkInvocationException.class)
        .hasMessageContaining("No proxied method invocation processed.");
  }

  @Test
  public void shouldFailWithNullProxiedMethod() {
    assertThatThrownBy(() -> testee.linkTo(null))
        .isInstanceOf(InvalidHalLinkInvocationException.class)
        .hasMessageContaining("No proxied method invocation processed.");
  }
}

@Path("")
interface TestApi {
  @Path("/testPath/{testArg}")
  @GET
  String testMethod(@PathParam("testArg") String testArg);

  @Path("/testPath/{testArg}/detail/{testArg2}")
  @GET
  String testMethodDetail(
      @PathParam("testArg") String testArg,
      @QueryParam("query") int testArgTwo,
      @PathParam("testArg2") String query);

  @Path("/testPath")
  @GET
  String testMethodQueryParam(@QueryParam("testRequestParam") String testArg);

  @Path("/testPathWithNoParams")
  @GET
  String testMethodWithoutParams();

  @Path("/testPath/{testArg}")
  @GET
  String testMethodWithoutPathParamAnnotation(String testArg);
}

@Path("")
class TestController {

  private static final Logger LOG = LoggerFactory.getLogger(TestController.class);

  public TestController(String foo) {
    LOG.info("Foo: {}", foo);
  }

  @Path("/testPath/{testArg}")
  @GET
  public String testMethod(@PathParam("testArg") String testArg) {
    return null;
  }
}
