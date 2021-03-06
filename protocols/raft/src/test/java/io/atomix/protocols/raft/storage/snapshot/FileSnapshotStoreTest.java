/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.atomix.protocols.raft.storage.snapshot;

import io.atomix.protocols.raft.service.ServiceId;
import io.atomix.protocols.raft.storage.RaftStorage;
import io.atomix.storage.StorageLevel;
import io.atomix.time.WallClockTimestamp;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * File snapshot store test.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class FileSnapshotStoreTest extends AbstractSnapshotStoreTest {
  private String testId;

  /**
   * Returns a new snapshot store.
   */
  protected SnapshotStore createSnapshotStore() {
    RaftStorage storage = RaftStorage.newBuilder()
        .withPrefix("test")
        .withDirectory(new File(String.format("target/test-logs/%s", testId)))
        .withStorageLevel(StorageLevel.DISK)
        .build();
    return new SnapshotStore(storage);
  }

  /**
   * Tests storing and loading snapshots.
   */
  @Test
  public void testStoreLoadSnapshot() {
    SnapshotStore store = createSnapshotStore();

    Snapshot snapshot = store.newSnapshot(ServiceId.from(1), 2, new WallClockTimestamp());
    try (SnapshotWriter writer = snapshot.openWriter()) {
      writer.writeLong(10);
    }
    snapshot.complete();
    assertNotNull(store.getSnapshotById(ServiceId.from(1)));
    assertNotNull(store.getSnapshotByIndex(2));
    store.close();

    store = createSnapshotStore();
    assertNotNull(store.getSnapshotById(ServiceId.from(1)));
    assertNotNull(store.getSnapshotByIndex(2));
    assertEquals(store.getSnapshotById(ServiceId.from(1)).serviceId(), ServiceId.from(1));
    assertEquals(store.getSnapshotById(ServiceId.from(1)).index(), 2);
    assertEquals(store.getSnapshotByIndex(2).serviceId(), ServiceId.from(1));
    assertEquals(store.getSnapshotByIndex(2).index(), 2);

    try (SnapshotReader reader = snapshot.openReader()) {
      assertEquals(reader.readLong(), 10);
    }
  }

  /**
   * Tests persisting and loading snapshots.
   */
  @Test
  public void testPersistLoadSnapshot() {
    SnapshotStore store = createSnapshotStore();

    Snapshot snapshot = store.newTemporarySnapshot(ServiceId.from(1), 2, new WallClockTimestamp());
    try (SnapshotWriter writer = snapshot.openWriter()) {
      writer.writeLong(10);
    }

    snapshot = snapshot.persist();

    assertNull(store.getSnapshotById(ServiceId.from(1)));
    assertNull(store.getSnapshotByIndex(2));

    snapshot.complete();
    assertNotNull(store.getSnapshotById(ServiceId.from(1)));
    assertNotNull(store.getSnapshotByIndex(2));

    try (SnapshotReader reader = snapshot.openReader()) {
      assertEquals(reader.readLong(), 10);
    }

    store.close();

    store = createSnapshotStore();
    assertNotNull(store.getSnapshotById(ServiceId.from(1)));
    assertNotNull(store.getSnapshotByIndex(2));
    assertEquals(store.getSnapshotById(ServiceId.from(1)).serviceId(), ServiceId.from(1));
    assertEquals(store.getSnapshotById(ServiceId.from(1)).index(), 2);
    assertEquals(store.getSnapshotByIndex(2).serviceId(), ServiceId.from(1));
    assertEquals(store.getSnapshotByIndex(2).index(), 2);

    snapshot = store.getSnapshotById(ServiceId.from(1));
    try (SnapshotReader reader = snapshot.openReader()) {
      assertEquals(reader.readLong(), 10);
    }
  }

  @Before
  @After
  public void cleanupStorage() throws IOException {
    Path directory = Paths.get("target/test-logs/");
    if (Files.exists(directory)) {
      Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    }
    testId = UUID.randomUUID().toString();
  }

}
