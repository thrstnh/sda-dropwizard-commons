package org.sdase.commons.shared.asyncapi;

import org.junit.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidationTest {

  private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  public void testHibernate() {
    DemoHibernate hibernateFieldInvalid = new DemoHibernate().setTest("");
    Set<ConstraintViolation<DemoHibernate>> violations = validator.validate(hibernateFieldInvalid);

    assertThat(violations).isNotEmpty();
  }

  @Test
  public void testValidationApi() {
    DemoValidationApi validationApiFieldInvalid = new DemoValidationApi().setTest("");
    Set<ConstraintViolation<DemoValidationApi>> violations = validator.validate(validationApiFieldInvalid);

    assertThat(violations).isNotEmpty();
  }

  public static class DemoHibernate {

    @org.hibernate.validator.constraints.NotBlank private String test;

    public String getTest() {
      return test;
    }

    public DemoHibernate setTest(String test) {
      this.test = test;
      return this;
    }
  }

  public static class DemoValidationApi {

    @javax.validation.constraints.NotBlank private String test;

    public String getTest() {
      return test;
    }

    public DemoValidationApi setTest(String test) {
      this.test = test;
      return this;
    }
  }
}
