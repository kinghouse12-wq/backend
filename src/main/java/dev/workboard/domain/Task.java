package dev.workboard.domain;

import java.time.Instant;
import java.util.UUID;

public record Task(
    UUID id,
    UUID projectId,
    String title,
    String description,
    TaskStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
