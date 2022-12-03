package service;

import jdk.incubator.foreign.MemorySegment;
import lsm.Dao;
import lsm.Entry;

import java.io.IOException;
import java.util.Objects;

/**
 * Constructs {@link Service} instances.
 *
 * @author Vadim Tsesko
 */
public final class ServiceFactory {
    private static final long MAX_HEAP = 256 * 1024L * 1024;

    private ServiceFactory() {
        // Not supposed to be instantiated
    }

    /**
     * Construct a storage instance.
     *
     * @param port port to bind HTTP server to
     * @param dao  DAO to store the data
     * @return a storage instance
     */
    public static Service create(
            final int port,
            final Dao<MemorySegment, Entry<MemorySegment>> dao) throws IOException {
        if (Runtime.getRuntime().maxMemory() > MAX_HEAP) {
            throw new IllegalStateException("The heap is too big. Consider setting Xmx.");
        }

        if (port <= 0 || 1 << 16 <= port) {
            throw new IllegalArgumentException("Port out of range");
        }

        Objects.requireNonNull(dao);

        return new MyService(port, dao);
    }
}
