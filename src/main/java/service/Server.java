package service;

import jdk.incubator.foreign.MemorySegment;
import lsm.Config;
import lsm.DAOFactory;
import lsm.Dao;
import lsm.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Starts storage and waits for shutdown.
 *
 * @author Vadim Tsesko
 */
public final class Server {
    private static final int PORT = 8080;

    private static final Logger LOG = LoggerFactory.getLogger(Server.class);

    private Server() {
        // Not instantiable
    }

    /**
     * Starts single node cluster at HTTP port 8080 and
     * temporary data storage if storage path not supplied.
     */
    public static void main(String[] args) throws IOException {
        // Temporary storage in the file system
        final Path data;
        if (args.length > 0) {
            data = Path.of(args[0]);
        } else {
            data = FileUtils.createTempDirectory();
        }
        LOG.info("Storing data at {}", data);

        // Start the storage
        Dao<MemorySegment, Entry<MemorySegment>> dao = DAOFactory.create(new Config(data, 4 * 1024));
        final Service storage =
                ServiceFactory.create(
                        PORT,
                        dao);
        storage.start();
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    storage.stop();
                    try {
                        dao.close();
                    } catch (IOException e) {
                        throw new RuntimeException("Can't close dao", e);
                    }
                }));
    }
}
