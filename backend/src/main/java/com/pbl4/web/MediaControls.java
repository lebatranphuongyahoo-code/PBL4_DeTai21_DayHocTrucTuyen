package com.pbl4.web;

import java.util.*;

/** Ephemeral controls for one live classroom. Grants belong to a connection, not an account. */
final class MediaControls {
    boolean microphonesLocked;
    String presenter = "";
    final Set<String> microphoneGrants = new HashSet<>();
    final Set<String> screenRequests = new LinkedHashSet<>();
    final Set<String> microphoneRequests = new LinkedHashSet<>();
    boolean microphoneAllowed(String peer, boolean owner) {
        return owner || !microphonesLocked || microphoneGrants.contains(peer);
    }
    void remove(String peer) {
        microphoneGrants.remove(peer); screenRequests.remove(peer); microphoneRequests.remove(peer);
        if (presenter.equals(peer)) presenter = "";
    }
}
