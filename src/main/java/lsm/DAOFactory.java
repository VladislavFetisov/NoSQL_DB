package lsm;

import jdk.incubator.foreign.MemorySegment;
import lsm.dao.LsmDao;
import java.io.IOException;

public final class DAOFactory {

    private DAOFactory() {
        // Only static methods
    }

    /**
     * Create an instance of {@link Dao} with supplied {@link Config}.
     */
    public static Dao<MemorySegment, Entry<MemorySegment>> create(Config config) throws IOException {
        assert config.basePath().toFile().exists();

        return new LsmDao(config);
    }

}
