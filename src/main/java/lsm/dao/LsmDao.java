package lsm.dao;

import jdk.incubator.foreign.MemorySegment;
import lsm.Config;
import lsm.Dao;
import lsm.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LsmDao implements Dao<MemorySegment, Entry<MemorySegment>> {
    private final Config config;
    private long nextTableNum;
    private volatile boolean isCompact;
    private volatile boolean isClosed;
    private final ExecutorService flushExecutor
            = Executors.newSingleThreadExecutor(r -> new Thread(r, "flushThread"));
    private final ExecutorService compactExecutor
            = Executors.newSingleThreadExecutor(r -> new Thread(r, "compactThread"));
    private volatile Storage storage;
    private volatile List<SSTable> duringCompactionTables = new ArrayList<>();

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    public static final Logger logger = LoggerFactory.getLogger(LsmDao.class);

    /**
     * Get all files from dir(config.basePath), remove all files to file with suffix "compacted".
     * It's restricted, that amount of compacted files couldn't be more than 2.
     */
    public LsmDao(Config config) throws IOException {
        this.config = config;
        SSTable.Directory directory = SSTable.retrieveDir(config.basePath());
        List<SSTable> fromDisc = directory.ssTables();
        if (fromDisc.isEmpty()) {
            this.nextTableNum = 0;
            this.storage = new Storage(Storage.Memory.getNewMemory(config.flushThresholdBytes()),
                    Storage.Memory.EMPTY_MEMORY, Collections.emptyList(), config);
            return;
        }
        List<SSTable> ssTables = fromDisc;
        if (directory.indexOfLastCompacted() != 0) {
            ssTables = fromDisc.subList(directory.indexOfLastCompacted(), fromDisc.size());
            Utils.deleteTablesToIndex(fromDisc, directory.indexOfLastCompacted());
        }
        this.nextTableNum = Utils.getLastTableNum(ssTables);
        this.storage = new Storage(Storage.Memory.getNewMemory(config.flushThresholdBytes()),
                Storage.Memory.EMPTY_MEMORY, ssTables, config);
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) {
        closeCheck();
        Storage fixedStorage = this.storage;
        PeekingIterator<Entry<MemorySegment>> merged = CustomIterators.getMergedIterator(from, to, fixedStorage);
        return CustomIterators.skipTombstones(merged);
    }

    @Override
    public void compact() throws IOException {
        if (isCompact) {
            return;
        }
        compactExecutor.execute(() -> {
            try {
                performCompact();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Compact will be blocked until latest flush is done.
     */
    private void performCompact() throws IOException {
        Path compactedPath;
        List<SSTable> fixed;
        synchronized (flushExecutor) {
            fixed = this.storage.ssTables();
            closeCheck();
            if (fixed.isEmpty() || (fixed.size() == 1 && fixed.get(0).isCompacted())) {
                logger.info("Reject compact because it's redundant");
                return;
            }
            duringCompactionTables = new ArrayList<>();
            duringCompactionTables.add(null);//for compacted table in future
            compactedPath = nextCompactedTable();
            isCompact = true;
        }
        SSTable.Sizes sizes = Utils.getSizes(Utils.tablesFilteredFullRange(fixed));
        Iterator<Entry<MemorySegment>> forWrite = Utils.tablesFilteredFullRange(fixed);
        SSTable compacted = SSTable.writeTable(compactedPath, forWrite, sizes.tableSize(), sizes.indexSize());

        synchronized (this) { //sync between concurrent flush and compact
            duringCompactionTables.set(0, compacted);
            isCompact = false;
            storage = storage.updateSSTables(duringCompactionTables);
        }
        Utils.deleteTablesToIndex(fixed, fixed.size());
    }

    @Override
    public void upsert(Entry<MemorySegment> entry) {
        boolean oversize;
        rwLock.readLock().lock();
        try {
            Storage localStorage = this.storage;
            if (localStorage.memory().isOversize().get() && localStorage.isFlushing()) { //if user's flush now running
                throw new IllegalStateException("So many upserts");
            }
            oversize = localStorage.memory().put(entry.key(), entry);
        } finally {
            rwLock.readLock().unlock();
        }
        if (oversize) {
            asyncFlush();
        }
    }

    private void asyncFlush() {
        flushExecutor.execute(() -> {
            try {
                synchronized (flushExecutor) {
                    logger.info("Start program flush");
                    processFlush();
                    logger.info("Program flush is finished");
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Only one flush per time, this flush may be blocked until it could be performed.
     */
    @Override
    public void flush() throws IOException {
        logger.info("User want to flush");
        synchronized (flushExecutor) {
            closeCheck();
            logger.info("User's flush is started");
            processFlush();
            logger.info("User's flush is finished");
        }
    }

    private void processFlush() throws IOException {
        rwLock.writeLock().lock();
        try {
            storage = storage.beforeFlush();
        } finally {
            rwLock.writeLock().unlock();
        }
        performFlush();
        storage = storage.afterFlush();
    }

    /**
     * We flush only readOnlyTable.
     */
    private void performFlush() throws IOException {
        Storage.Memory readOnlyMemTable = storage.readOnlyMemory();
        if (readOnlyMemTable.isEmpty()) {
            return;
        }
        SSTable.Sizes sizes = Utils.getSizes(readOnlyMemTable.values().iterator());
        SSTable table = SSTable.writeTable(
                nextOrdinaryTable(),
                readOnlyMemTable.values().iterator(),
                sizes.tableSize(),
                sizes.indexSize()
        );
        synchronized (this) { //sync between concurrent flush and compact
            if (isCompact) {
                duringCompactionTables.add(table);
            }
            List<SSTable> ssTables = this.storage.ssTables();
            ArrayList<SSTable> newTables = new ArrayList<>(ssTables.size() + 1);
            newTables.addAll(ssTables);
            newTables.add(table);
            storage = storage.updateSSTables(newTables);
        }
    }

    @Override
    public void close() throws IOException {
        Utils.shutdownExecutor(flushExecutor);
        Utils.shutdownExecutor(compactExecutor);
        synchronized (flushExecutor) {
            if (isClosed) {
                logger.info("Trying to close already closed storage");
                return;
            }
            isClosed = true;
            logger.info("Closing storage");
            processFlush();
            for (SSTable table : this.storage.ssTables()) {
                table.close();
            }
        }
    }

    @Override
    public Entry<MemorySegment> get(MemorySegment key) {
        Iterator<Entry<MemorySegment>> singleIterator = get(key, null);
        if (!singleIterator.hasNext()) {
            return null;
        }
        Entry<MemorySegment> desired = singleIterator.next();
        if (Utils.compareMemorySegments(desired.key(), key) != 0) {
            return null;
        }
        return desired;
    }

    private Path nextOrdinaryTable() {
        return nextTable(String.valueOf(nextTableNum++));
    }

    private Path nextCompactedTable() {
        return nextTable(nextTableNum++ + SSTable.COMPACTED);
    }

    private Path nextTable(String name) {
        return config.basePath().resolve(name);
    }

    private void closeCheck() {
        if (isClosed) {
            throw new IllegalStateException("Already closed");
        }
    }

    public Storage getStorage() {
        return storage;
    }

}
