package dev.workboard.domain;

public final class DomainException extends RuntimeException {
  public enum Code {
    NOT_FOUND,
    CONFLICT,
    INVALID_TRANSITION,
    INVALID_CREDENTIALS,
    INVALID_REQUEST
  }

  private final Code code;

  public DomainException(Code code, String message) {
    super(message);
    this.code = code;
  }

  public Code code() {
    return code;
  }
}
