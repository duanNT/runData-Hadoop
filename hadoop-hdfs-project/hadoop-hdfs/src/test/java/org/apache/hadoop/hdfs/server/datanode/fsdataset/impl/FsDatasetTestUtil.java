/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.server.datanode.fsdataset.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.Collection;
import java.util.Random;

import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.protocol.ExtendedBlock;
import org.apache.hadoop.hdfs.server.common.Storage;
import org.apache.hadoop.hdfs.server.datanode.DataNode;
import org.apache.hadoop.hdfs.server.datanode.ReplicaInfo;
import org.apache.hadoop.hdfs.server.datanode.StorageLocation;
import org.apache.hadoop.hdfs.server.datanode.fsdataset.FsDatasetSpi;
import org.apache.hadoop.io.IOUtils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class FsDatasetTestUtil {

  public static File getFile(FsDatasetSpi<?> fsd, String bpid, long bid) {
    return ((FsDatasetImpl)fsd).getFile(bpid, bid, false);
  }

  public static File getBlockFile(FsDatasetSpi<?> fsd, String bpid, Block b
      ) throws IOException {
    return ((FsDatasetImpl)fsd).getBlockFile(bpid, b.getBlockId());
  }

  public static File getMetaFile(FsDatasetSpi<?> fsd, String bpid, Block b)
      throws IOException {
    return FsDatasetUtil.getMetaFile(getBlockFile(fsd, bpid, b), b
        .getGenerationStamp());
  }
  
  public static boolean unlinkBlock(FsDatasetSpi<?> fsd,
      ExtendedBlock block, int numLinks) throws IOException {
    final ReplicaInfo info = ((FsDatasetImpl)fsd).getReplicaInfo(block);
    return info.unlinkBlock(numLinks);
  }

  public static ReplicaInfo fetchReplicaInfo (final FsDatasetSpi<?> fsd,
      final String bpid, final long blockId) {
    return ((FsDatasetImpl)fsd).fetchReplicaInfo(bpid, blockId);
  }

  public static long getPendingAsyncDeletions(FsDatasetSpi<?> fsd) {
    return ((FsDatasetImpl)fsd).asyncDiskService.countPendingDeletions();
  }
  
  public static Collection<ReplicaInfo> getReplicas(FsDatasetSpi<?> fsd,
      String bpid) {
    return ((FsDatasetImpl)fsd).volumeMap.replicas(bpid);
  }

  /**
   * Stop the lazy writer daemon that saves RAM disk files to persistent storage.
   * @param dn
   */
  public static void stopLazyWriter(DataNode dn) {
    FsDatasetImpl fsDataset = ((FsDatasetImpl) dn.getFSDataset());
    ((FsDatasetImpl.LazyWriter) fsDataset.lazyWriter.getRunnable()).stop();
  }

  /**
   * Asserts that the storage lock file in the given directory has been
   * released.  This method works by trying to acquire the lock file itself.  If
   * locking fails here, then the main code must have failed to release it.
   *
   * @param dir the storage directory to check
   * @throws IOException if there is an unexpected I/O error
   */
  public static void assertFileLockReleased(String dir) throws IOException {
    StorageLocation sl = StorageLocation.parse(dir);
    File lockFile = new File(sl.getFile(), Storage.STORAGE_FILE_LOCK);
    RandomAccessFile raf = new RandomAccessFile(lockFile, "rws");
    FileChannel channel = raf.getChannel();
    try {
      FileLock lock = channel.tryLock();
      assertNotNull(String.format(
          "Lock file at %s appears to be held by a different process.",
          lockFile.getAbsolutePath()), lock);
      if (lock != null) {
        try {
          lock.release();
        } catch (IOException e) {
          FsDatasetImpl.LOG.warn(String.format("I/O error releasing file lock %s.",
              lockFile.getAbsolutePath()), e);
          throw e;
        }
      }
    } catch (OverlappingFileLockException e) {
      fail(String.format("Must release lock file at %s.",
          lockFile.getAbsolutePath()));
    } finally {
      try {
        channel.close();
        raf.close();
      } catch (IOException ignore) {}
    }
  }
}
