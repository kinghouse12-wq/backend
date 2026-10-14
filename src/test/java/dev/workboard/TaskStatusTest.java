package dev.workboard;

import static org.junit.jupiter.api.Assertions.*;

import dev.workboard.domain.*;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class TaskStatusTest {
  static Stream<Arguments> transitions() {
    return Stream.of(TaskStatus.values())
        .flatMap(from -> Stream.of(TaskStatus.values()).map(to -> Arguments.of(from, to)));
  }

  @ParameterizedTest
  @MethodSource("transitions")
  void enforcesBusinessTransitionSpecification(TaskStatus from, TaskStatus to) {
    Set<String> allowed =
        Set.of(
            "TODO:IN_PROGRESS",
            "TODO:CANCELLED",
            "IN_PROGRESS:TODO",
            "IN_PROGRESS:DONE",
            "IN_PROGRESS:CANCELLED",
            "DONE:IN_PROGRESS",
            "CANCELLED:TODO");
    if (from == to || allowed.contains(from + ":" + to))
      assertDoesNotThrow(() -> from.requireTransitionTo(to));
    else assertThrows(DomainException.class, () -> from.requireTransitionTo(to));
  }
}
