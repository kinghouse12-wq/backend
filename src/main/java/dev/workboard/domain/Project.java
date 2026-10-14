package dev.workboard.domain;

import java.time.Instant;
import java.util.UUID;

public record Project(UUID id, UUID ownerId, String name, String description, Instant createdAt) {}
