package dev.workboard.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId;
    try {
      requestId = UUID.fromString(request.getHeader("X-Request-ID")).toString();
    } catch (IllegalArgumentException | NullPointerException ex) {
      requestId = UUID.randomUUID().toString();
    }
    request.setAttribute("requestId", requestId);
    response.setHeader("X-Request-ID", requestId);
    MDC.put("requestId", requestId);
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      log.info(
          "request_completed method={} path={} status={} durationMs={}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          (System.nanoTime() - start) / 1_000_000);
      MDC.remove("requestId");
    }
  }
}
