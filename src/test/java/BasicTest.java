import jdk.incubator.foreign.MemorySegment;
import lsm.*;
import one.nio.util.Utf8;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BasicTest {

    private Dao<MemorySegment, Entry<MemorySegment>> dao;

    @BeforeEach
    void start(@TempDir Path dir) throws IOException {
        dao = DAOFactory.create(new Config(dir, 4096));
    }

    @AfterEach
    void finish() throws IOException {
        dao.close();
    }

    @Test
    void empty() throws IOException {
        MemorySegment notExistedKey = MemorySegment.ofArray(Utf8.toBytes("NOT_EXISTED_KEY"));
        Entry<MemorySegment> entry = dao.get(notExistedKey);

        assertNull(entry);
    }


    @Test
    void insert() throws IOException {
        MemorySegment key = MemorySegment.ofArray(Utf8.toBytes("NEW_KEY"));
        MemorySegment key1 = MemorySegment.ofArray(Utf8.toBytes("NEW_KEY1"));
        MemorySegment value = MemorySegment.ofArray(Utf8.toBytes("NEW_VALUE"));
        MemorySegment value1 = MemorySegment.ofArray(Utf8.toBytes("NEW_VALUE1"));
        dao.upsert(new BaseEntry<>(key, value));
        dao.upsert(new BaseEntry<>(key1, value1));
        assertEquals(dao.get(key).value(), value);
        assertEquals(dao.get(key1).value(), value1);
    }


    @Test
    void remove() throws IOException {
        MemorySegment key = MemorySegment.ofArray(Utf8.toBytes("NEW_KEY"));
        MemorySegment key1 = MemorySegment.ofArray(Utf8.toBytes("NEW_KEY1"));
        MemorySegment value = MemorySegment.ofArray(Utf8.toBytes("NEW_VALUE"));
        MemorySegment value1 = MemorySegment.ofArray(Utf8.toBytes("NEW_VALUE1"));
        dao.upsert(new BaseEntry<>(key, value));
        dao.upsert(new BaseEntry<>(key1, value1));
        assertEquals(dao.get(key).value(), value);
        assertEquals(dao.get(key1).value(), value1);
        dao.upsert(new BaseEntry<>(key, null));
        assertNull(dao.get(key));
        assertEquals(dao.get(key1).value(), value1);
    }

}
