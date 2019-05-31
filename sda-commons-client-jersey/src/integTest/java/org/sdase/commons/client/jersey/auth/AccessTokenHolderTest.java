package org.sdase.commons.client.jersey.auth;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class AccessTokenHolderTest {
   @Test
   public void shouldBeValid() {
      // given
      AccessTokenHolder holder = new AccessTokenHolder("", Instant.now().plus(1, ChronoUnit.MINUTES), null);

      // when
      // then
      assertThat(holder.isValid()).isTrue();
   }

   @Test
   public void shouldBeValidWithLeeway() {
      // given
      AccessTokenHolder holder = new AccessTokenHolder("", Instant.now().plus(1, ChronoUnit.MINUTES),
            Duration.ofSeconds(30));

      // when
      // then
      assertThat(holder.isValid()).isTrue();
   }

   @Test
   public void shouldBeInvalid() {
      // given
      AccessTokenHolder holder = new AccessTokenHolder("", Instant.now().minus(1, ChronoUnit.MINUTES), null);

      // when
      // then
      assertThat(holder.isValid()).isFalse();
   }

   @Test
   public void shouldBeInvalidWithLeeway() {
      // given
      AccessTokenHolder holder = new AccessTokenHolder("", Instant.now().minus(1, ChronoUnit.MINUTES),
            Duration.ofSeconds(30));

      // when
      // then
      assertThat(holder.isValid()).isFalse();
   }
}
