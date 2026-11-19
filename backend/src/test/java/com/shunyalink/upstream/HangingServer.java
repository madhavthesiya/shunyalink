package com.shunyalink.upstream;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Accepts connections and never answers: a service that hangs, the worst kind of outage, because every caller waits
 * for its full timeout. Counts connections so a test can tell whether a call reached it at all.
 */
final class HangingServer implements AutoCloseable {
    private final ServerSocket socket = new ServerSocket(0);
    private final AtomicInteger connections = new AtomicInteger();
    private final List<Socket> held = new CopyOnWriteArrayList<>();

    HangingServer() throws IOException {
        Thread.ofVirtual().name("hanging-server").start(this::acceptForever);
    }

    private void acceptForever() {
        try {
            while (true) {
                held.add(socket.accept());
                connections.incrementAndGet();
            }
        } catch (IOException closed) {
            // the test is over
        }
    }

    String url() {
        return "http://127.0.0.1:" + socket.getLocalPort();
    }

    int connections() {
        return connections.get();
    }

    @Override
    public void close() throws IOException {
        socket.close();
        for (Socket connection : held) {
            connection.close();
        }
    }
}
