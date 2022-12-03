package service;

import jdk.incubator.foreign.MemorySegment;
import lsm.BaseEntry;
import lsm.Dao;
import lsm.Entry;
import one.nio.http.*;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyService extends HttpServer implements Service {
    private final Dao<MemorySegment, Entry<MemorySegment>> dao;
    private final ExecutorService service = Executors.newFixedThreadPool(4);

    public MyService(int port, Dao<MemorySegment, Entry<MemorySegment>> dao) throws IOException {
        super(from(port));
        this.dao = dao;
    }

    @Path("/v0/status")
    public Response getStatus() {
        return Response.ok("");
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Path("/v0/entity")
    public Response entity(
            Request request,
            @Param(value = "id", required = true) String id) throws IOException {
        if (id.isBlank()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        return switch (request.getMethod()) {
            case Request.METHOD_GET -> get(id);
            case Request.METHOD_DELETE -> delete(id);
            case Request.METHOD_PUT -> put(id, request.getBody());
            default -> new Response(Response.METHOD_NOT_ALLOWED);
        };
    }

    private Response put(String id, byte[] body) {
        dao.upsert(new BaseEntry<>(MemorySegment.ofArray(Utf8.toBytes(id)), MemorySegment.ofArray(body)));
        return new Response(Response.CREATED, Response.EMPTY);
    }

    private Response delete(String id) {
        dao.upsert(new BaseEntry<>(MemorySegment.ofArray(Utf8.toBytes(id)), null));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private Response get(String id) throws IOException {
        MemorySegment key = MemorySegment.ofArray(Utf8.toBytes(id));
        Entry<MemorySegment> entry = dao.get(key);

        return (entry != null)
                ? new Response(Response.OK, entry.value().toByteArray())
                : new Response(Response.NOT_FOUND, Response.EMPTY);
    }

    private static HttpServerConfig from(int port) {
        HttpServerConfig config = new HttpServerConfig();
        AcceptorConfig ac = new AcceptorConfig();
        ac.port = port;
        ac.reusePort = true;
        config.acceptors = new AcceptorConfig[]{ac};
        config.minWorkers = 4;
        config.maxWorkers = 4;
        return config;
    }

}
