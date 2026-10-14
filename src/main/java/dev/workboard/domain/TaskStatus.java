package dev.workboard.domain;

public enum TaskStatus {
  TODO,
  IN_PROGRESS,
  DONE,
  CANCELLED;

  public void requireTransitionTo(TaskStatus target) {
    boolean allowed =
        this == target
            || switch (this) {
              case TODO -> target == IN_PROGRESS || target == CANCELLED;
              case IN_PROGRESS -> target == TODO || target == DONE || target == CANCELLED;
              case DONE -> target == IN_PROGRESS;
              case CANCELLED -> target == TODO;
            };
    if (!allowed) {
      throw new DomainException(
          DomainException.Code.INVALID_TRANSITION,
          "Cannot transition from " + this + " to " + target + ".");
    }
  }
}
