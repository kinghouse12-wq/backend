package dev.workboard.domain;

import java.time.Instant;
import java.util.UUID;

public record TaskEvent(
    UUID id,
    UUID taskId,
    UUID actorId,
    TaskStatus fromStatus,
    TaskStatus toStatus,
    long version,
    Instant createdAt) {}
