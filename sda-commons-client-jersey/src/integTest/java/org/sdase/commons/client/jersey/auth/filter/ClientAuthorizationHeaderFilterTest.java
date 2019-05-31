package org.sdase.commons.client.jersey.auth.filter;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.sdase.commons.client.jersey.auth.AuthProvider;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.core.MultivaluedMap;

import static org.mockito.Mockito.*;

public class ClientAuthorizationHeaderFilterTest {
   @Mock
   AuthProvider authProvider;

   @Mock
   ClientRequestContext clientRequestContext;

   @Mock
   MultivaluedMap<String, Object> headers;

   @Rule
   public MockitoRule mockitoRule = MockitoJUnit.rule();

   @Test
   public void shouldAddHeader() {
      // given
      when(authProvider.authHeaderValue()).thenReturn("Bearer my-token");
      when(clientRequestContext.getHeaders()).thenReturn(headers);

      ClientAuthorizationHeaderFilter filter = new ClientAuthorizationHeaderFilter(authProvider);

      // when
      filter.filter(clientRequestContext);

      // then
      verify(headers).add("Authorization", "Bearer my-token");
   }

   @Test
   public void shouldNotAddHeader() {
      // given
      when(authProvider.authHeaderValue()).thenReturn(null);
      when(clientRequestContext.getHeaders()).thenReturn(headers);

      ClientAuthorizationHeaderFilter filter = new ClientAuthorizationHeaderFilter(authProvider);

      // when
      filter.filter(clientRequestContext);

      // then
      verify(headers, never()).add(any(), any());
   }
}
