package dev.workboard;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.workboard.application.*;
import dev.workboard.domain.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class PostgresConcurrencyTest {
  @Autowired Ports.Tasks tasks;
  @Autowired AuthService auth;
  @Autowired ProjectService projects;
  @Autowired TaskService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactionManager;

  @Test
  void exactlyOneCompetingDatabaseWriterSucceeds() throws Exception {
    try (var connection = jdbc.getDataSource().getConnection()) {
      assumeTrue(connection.getMetaData().getDatabaseProductName().equals("PostgreSQL"));
    }
    User user =
        auth.register("race-" + UUID.randomUUID() + "@example.com", "ConcurrentPassword42!");
    Project project = projects.create(user.id(), "Race", null);
    Task task = service.create(project.id(), user.id(), "Original", null);
    CyclicBarrier barrier = new CyclicBarrier(2);
    Callable<Boolean> writer =
        () ->
            new TransactionTemplate(transactionManager)
                .execute(
                    transaction -> {
                      Task snapshot = tasks.taskInProject(task.id(), project.id()).orElseThrow();
                      try {
                        barrier.await(10, TimeUnit.SECONDS);
                      } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                      }
                      Task changed =
                          new Task(
                              snapshot.id(),
                              snapshot.projectId(),
                              Thread.currentThread().getName(),
                              snapshot.description(),
                              snapshot.status(),
                              snapshot.version() + 1,
                              snapshot.createdAt(),
                              snapshot.updatedAt());
                      return tasks.compareAndSetTask(changed, snapshot.version());
                    });
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<Boolean> first = executor.submit(writer), second = executor.submit(writer);
      assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
    }
    assertEquals(1, service.get(project.id(), task.id(), user.id()).version());
    projects.delete(project.id(), user.id());
  }
}
