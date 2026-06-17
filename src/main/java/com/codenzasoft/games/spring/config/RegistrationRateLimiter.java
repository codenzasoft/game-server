package com.codenzasoft.games.spring.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Sliding-window rate limiter for registration-related POST endpoints. Limits each IP to
 * MAX_ATTEMPTS requests within the WINDOW_SECONDS period. Per-instance only — acceptable given that
 * email verification is still required to complete account creation.
 */
public class RegistrationRateLimiter implements HandlerInterceptor {

  private static final int MAX_ATTEMPTS = 5;
  private static final long WINDOW_SECONDS = 3600;

  private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    final String ip = getClientIp(request);
    final long now = Instant.now().getEpochSecond();
    final long windowStart = now - WINDOW_SECONDS;

    final Deque<Long> timestamps =
        attempts.compute(
            ip,
            (k, deque) -> {
              if (deque == null) deque = new ArrayDeque<>();
              while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                deque.pollFirst();
              }
              return deque;
            });

    synchronized (timestamps) {
      if (timestamps.size() >= MAX_ATTEMPTS) {
        response.setStatus(429);
        response.setContentType("text/plain");
        response.getWriter().write("Too many attempts. Please try again later.");
        return false;
      }
      timestamps.addLast(now);
    }
    return true;
  }

  private String getClientIp(HttpServletRequest request) {
    final String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
