package com.pbl4.client.net;

import java.io.IOException;

/** Callback for a lost server connection; runs on the network reader thread. */
@FunctionalInterface
public interface ClientListener {

    void onDisconnected(IOException cause);
}
