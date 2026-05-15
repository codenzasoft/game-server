package com.codenzasoft.games.spring.validation;

/**
 * Validates that a password meets the application's strength requirements.
 *
 * <p>Requirements follow the 2025 NIST SP 800-63B guidelines:
 *
 * <ul>
 *   <li>Minimum {@value #MIN_LENGTH} characters — length is the primary security lever.
 *   <li>Maximum {@value #MAX_LENGTH} characters.
 *   <li>No mandatory character-type composition rules (uppercase, numbers, symbols, etc.). Forced
 *       complexity rules are known to produce predictable patterns that are weaker in practice.
 * </ul>
 */
public final class PasswordValidator {

  /** Minimum acceptable password length. */
  public static final int MIN_LENGTH = 12;

  /** Maximum acceptable password length. */
  public static final int MAX_LENGTH = 64;

  private PasswordValidator() {}

  /**
   * Validates {@code password} against the application's strength rules.
   *
   * @param password the raw (plaintext) password to check
   * @return a {@link Result} that is {@linkplain Result#isValid() valid} on success, or carries an
   *     {@linkplain Result#getErrorMessage() error message} on failure
   */
  public static Result validate(String password) {
    if (password == null || password.length() < MIN_LENGTH) {
      return Result.failure("Password must be at least " + MIN_LENGTH + " characters.");
    }
    if (password.length() > MAX_LENGTH) {
      return Result.failure("Password must be no more than " + MAX_LENGTH + " characters.");
    }
    return Result.success();
  }

  /** The outcome of a {@link PasswordValidator#validate} call. */
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

    /** Returns {@code true} when the password passed all checks. */
    public boolean isValid() {
      return valid;
    }

    /**
     * Returns a human-readable explanation of why the password failed, or {@code null} if it
     * passed.
     */
    public String getErrorMessage() {
      return errorMessage;
    }
  }
}
