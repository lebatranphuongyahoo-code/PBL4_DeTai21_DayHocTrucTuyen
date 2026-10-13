package com.pbl4.client.net;

import com.pbl4.common.Packet;
import com.pbl4.common.PacketUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * TCP client connection with one reader thread and serialized writes.
 *
 * <p>Packet and disconnect callbacks run on the network reader thread. Swing callbacks
 * must hand UI changes off to the event-dispatch thread, for example with
 * {@code SwingUtilities.invokeLater(...)}.</p>
 */
public class ServerConnection implements AutoCloseable {

    private final Object writeLock = new Object();
    private final AtomicBoolean finished = new AtomicBoolean();

    private volatile Consumer<Packet> listener = packet -> { };
    private volatile ClientListener disconnectListener = cause -> { };
    private volatile Socket socket;
    private volatile DataInputStream input;
    private volatile DataOutputStream output;
    private volatile boolean connected;
    private boolean started;

    /** Opens the socket and starts the dedicated packet-reader thread. */
    public synchronized void connect(String ip, int port) throws IOException {
        if (started || finished.get()) {
            throw new IllegalStateException("This connection has already been started or closed");
        }
        started = true;

        Socket newSocket = new Socket();
        try {
            newSocket.connect(new InetSocketAddress(ip, port));
            newSocket.setTcpNoDelay(true);
            DataInputStream newInput =
                    new DataInputStream(new BufferedInputStream(newSocket.getInputStream()));
            DataOutputStream newOutput =
                    new DataOutputStream(new BufferedOutputStream(newSocket.getOutputStream()));

            socket = newSocket;
            input = newInput;
            output = newOutput;
            connected = true;

            Thread reader = new Thread(this::readLoop, "server-connection-reader");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException | RuntimeException e) {
            finished.set(true);
            try {
                newSocket.close();
            } catch (IOException closeException) {
                e.addSuppressed(closeException);
            }
            throw e;
        }
    }

    /** Registers the packet callback. It is invoked on the network reader thread. */
    public void setListener(Consumer<Packet> listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /** Registers a callback invoked when an established connection is lost. */
    public void setDisconnectListener(ClientListener listener) {
        this.disconnectListener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Sends one packet. Calls from concurrent UI, screen-capture, or other threads
     * are serialized so their packet bytes cannot interleave on the TCP stream.
     */
    public void send(byte type, byte[] payload) throws IOException {
        byte[] encoded = PacketUtils.encode(type, payload);
        IOException failure = null;
        synchronized (writeLock) {
            DataOutputStream currentOutput = output;
            if (!connected || currentOutput == null) {
                throw new IOException("Not connected to the server");
            }
            try {
                currentOutput.write(encoded);
                currentOutput.flush();
            } catch (IOException e) {
                failure = e;
            }
        }

        if (failure != null) {
            finish(failure, true);
            throw failure;
        }
    }

    private void readLoop() {
        IOException disconnectCause = null;
        try {
            while (connected) {
                Packet packet = PacketUtils.readPacket(input);
                listener.accept(packet);
            }
        } catch (IOException e) {
            disconnectCause = e;
        } catch (RuntimeException e) {
            disconnectCause = new IOException("Packet listener failed", e);
        } finally {
            finish(disconnectCause, true);
        }
    }

    private void finish(IOException cause, boolean notifyListener) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        connected = false;

        Socket currentSocket = socket;
        if (currentSocket != null) {
            try {
                currentSocket.close();
            } catch (IOException closeException) {
                if (cause == null) {
                    cause = closeException;
                } else {
                    cause.addSuppressed(closeException);
                }
            }
        }

        if (notifyListener) {
            IOException reportedCause = cause == null
                    ? new IOException("Connection closed")
                    : cause;
            try {
                disconnectListener.onDisconnected(reportedCause);
            } catch (RuntimeException callbackException) {
                System.err.println("Disconnect callback failed: " + callbackException.getMessage());
            }
        }
    }

    /** Closes the connection without reporting a loss for an intentional local close. */
    @Override
    public void close() {
        finish(null, false);
    }
}
