package com.codenzasoft.games.spring.validation;

/**
 * Validates that a username meets the application's format requirements.
 *
 * <p>A username is valid if it matches either form:
 *
 * <ul>
 *   <li><b>Handle</b> — between {@value #MIN_LENGTH} and {@value #MAX_LENGTH} characters,
 *       containing only ASCII letters, digits, underscores, and hyphens.
 *   <li><b>Email address</b> — a valid email (accepted because many accounts use their email as
 *       their username).
 * </ul>
 */
public final class UsernameValidator {

  public static final int MIN_LENGTH = 3;
  public static final int MAX_LENGTH = 30;

  private static final String HANDLE_PATTERN = "^[a-zA-Z0-9_-]+$";
  private static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

  private UsernameValidator() {}

  public static Result validate(String username) {
    if (username == null || username.isBlank()) {
      return Result.failure("Username must be at least " + MIN_LENGTH + " characters.");
    }
    if (username.matches(EMAIL_PATTERN)) {
      return Result.success();
    }
    if (username.length() < MIN_LENGTH) {
      return Result.failure("Username must be at least " + MIN_LENGTH + " characters.");
    }
    if (username.length() > MAX_LENGTH) {
      return Result.failure("Username must be no more than " + MAX_LENGTH + " characters.");
    }
    if (!username.matches(HANDLE_PATTERN)) {
      return Result.failure("Username may only contain letters, digits, underscores, and hyphens.");
    }
    return Result.success();
  }

  public static final class Result {

    private final boolean valid;
    private final String errorMessage;

    private Result(boolean valid, String errorMessage) {
      this.valid = valid;
      this.errorMessage = errorMessage;
    }

    static Result success() {
      return new Result(true, null);
    }

    static Result failure(String errorMessage) {
      return new Result(false, errorMessage);
    }

    public boolean isValid() {
      return valid;
    }

    public String getErrorMessage() {
      return errorMessage;
    }
  }
}
