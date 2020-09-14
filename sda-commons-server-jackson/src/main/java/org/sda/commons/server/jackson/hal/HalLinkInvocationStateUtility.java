package org.sda.commons.server.jackson.hal;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An utility class to process and store the invocation information of a method. It also creates and
 * returns a proxy that uses the method invocation handler to process the invocation information.
 */
public class HalLinkInvocationStateUtility {

  private static final Logger LOG = LoggerFactory.getLogger(HalLinkInvocationStateUtility.class);

  private static final ThreadLocal<MethodInvocationState> THREAD_LOCAL_INVOCATION_STATE =
      ThreadLocal.withInitial(MethodInvocationState::new);

  // Method Invocation Handler to process and save the current state of the method invocation
  private static final MethodInterceptor METHOD_PROCESSING_INTERCEPTOR =
      (obj, method, methodArguments, proxy) -> {
        // Get  invocation state from current thread
        final MethodInvocationState methodInvocationState = THREAD_LOCAL_INVOCATION_STATE.get();
        final String methodName = method.getName();
        methodInvocationState.setInvokedMethod(methodName);
        LOG.debug("Last invoked method '{}' added to current invocation state", methodName);
        // Process annotated query and path parameters
        processParams(methodInvocationState, methodArguments, method.getParameters());
        methodInvocationState.processed();
        // Do nothing
        return null;
      };

  private HalLinkInvocationStateUtility() {}

  /**
   * Creates and returns a proxy instance based on the passed type parameter with a invocation
   * handler, which processes and saves the needed method invocation information in the current
   * thread. Parameters in the afterwards called method of the proxy will be used to resolve the URI
   * template of the corresponding method. It should be ensured that the parameters are annotated
   * with {@linkplain PathParam} or with {@linkplain QueryParam}.
   *
   * @param <T> the type parameter based on the passed type. Should not be null {@literal null}.
   * @param type the type on which the method should be invoked.
   * @return the proxy instance
   */
  static <T> T methodOn(Class<T> type) {
    return createProxy(type);
  }

  @SuppressWarnings("unchecked")
  private static <T> T createProxy(Class<T> type) {
    THREAD_LOCAL_INVOCATION_STATE.get().setType(type);
    LOG.debug("Class type: '{}' added to the current invocation state", type);
    Enhancer enhancer = new Enhancer();
    enhancer.setSuperclass(type);
    enhancer.setCallback(METHOD_PROCESSING_INTERCEPTOR);
    return (T) enhancer.create();
  }

  private static void processParams(
      MethodInvocationState methodInvocationState,
      Object[] methodArguments,
      Parameter[] parameters) {
    final List<Parameter> annotatedParams =
        Arrays.stream(parameters)
            .filter(
                parameter ->
                    parameter.getAnnotation(PathParam.class) != null
                        || parameter.getAnnotation(QueryParam.class) != null)
            .collect(Collectors.toList());

    // Check if all parameters has an Query/Path-Param annotation
    if (annotatedParams.size() != parameters.length) {
      throw new InvalidHalLinkInvocationException(
          "Each method parameter needs at least a @PathParam or @QueryParam annotation.");
    }

    // Correlation between method argument order and annotatedParams order
    for (int i = 0; i < annotatedParams.size(); i++) {
      final PathParam pathParam = annotatedParams.get(i).getAnnotation(PathParam.class);
      final Object paramValue = methodArguments[i];
      if (pathParam != null) {
        methodInvocationState.getPathParams().put(pathParam.value(), paramValue);
        LOG.debug(
            "Saved PathParam: '{}' with value: '{}' to current invocation state",
            pathParam.value(),
            paramValue);
      } else {
        final QueryParam queryParam = annotatedParams.get(i).getAnnotation(QueryParam.class);
        methodInvocationState.getQueryParams().put(queryParam.value(), paramValue);
        LOG.debug(
            "Saved QueryParam: '{}' with value: '{}' to current invocation state",
            queryParam.value(),
            paramValue);
      }
    }
  }

  /**
   * Load the method invocation state of the current thread.
   *
   * @return the method invocation state
   */
  public static MethodInvocationState loadMethodInvocationState() {
    LOG.debug("Load invocation state of current thread");
    return THREAD_LOCAL_INVOCATION_STATE.get();
  }

  /** Unload the method invocation state of the current thread. */
  public static void unloadMethodInvocationState() {
    LOG.debug("Remove invocation state of current thread");
    THREAD_LOCAL_INVOCATION_STATE.remove();
  }

  /** Data class to save method invocation information */
  static class MethodInvocationState {
    private boolean processed = false;
    private Class<?> type;
    private String invokedMethod;
    private final Map<String, Object> pathParams = new HashMap<>();
    private final Map<String, Object> queryParams = new HashMap<>();

    /**
     * Gets type of the class of the invoked method.
     *
     * @return the type
     */
    Class<?> getType() {
      return type;
    }

    /**
     * Sets type of the class of the invoked method.
     *
     * @param type the type
     */
    void setType(Class<?> type) {
      this.type = type;
    }

    /**
     * Gets the method name.
     *
     * @return the invoked method name
     */
    String getInvokedMethod() {
      return invokedMethod;
    }

    /**
     * Sets the method name.
     *
     * @param invokedMethod the invoked method name
     */
    void setInvokedMethod(String invokedMethod) {
      this.invokedMethod = invokedMethod;
    }

    /**
     * Gets the Key-Value pairs of the processed {@linkplain PathParam#value()} and the
     * corresponding method arguments of the invoked method.
     *
     * @return the path params
     */
    Map<String, Object> getPathParams() {
      return pathParams;
    }

    /**
     * Gets the Key-Value pairs of the processed {@linkplain QueryParam#value()} and the
     * corresponding method arguments of the invoked method.
     *
     * @return the query params
     */
    Map<String, Object> getQueryParams() {
      return queryParams;
    }

    /**
     * Returns {@literal true} if the invoked method is processed.
     *
     * @return the boolean
     */
    boolean isProcessed() {
      return processed;
    }

    /** Set the state of the method invocation to processed. */
    void processed() {
      this.processed = true;
    }
  }
}
