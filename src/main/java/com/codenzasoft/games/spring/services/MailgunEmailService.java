package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Base class for services that send transactional email via Mailgun. */
public abstract class MailgunEmailService {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Value("${mailgun.api-key:}")
  private String apiKey;

  @Value("${mailgun.domain:}")
  private String domain;

  @Value("${mailgun.from:}")
  private String from;

  @Autowired protected UsersRepository usersRepository;

  private final RestClient restClient = RestClient.create();

  protected void sendEmail(String to, String subject, String text) {
    if (apiKey == null || apiKey.isBlank() || domain == null || domain.isBlank()) {
      log.warn("Mailgun not configured — would have sent to {}: {}", to, text);
      return;
    }
    try {
      final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("from", from);
      body.add("to", to);
      body.add("subject", subject);
      body.add("text", text);

      restClient
          .post()
          .uri("https://api.mailgun.net/v3/" + domain + "/messages")
          .headers(h -> h.setBasicAuth("api", apiKey))
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(body)
          .retrieve()
          .toBodilessEntity();

      log.info("Email sent to {}", to);
    } catch (Exception e) {
      log.error("Failed to send email to {}: {}", to, e.getMessage());
    }
  }

  protected Optional<UserEntity> findUserById(UUID id) {
    return usersRepository.findById(id);
  }
}
