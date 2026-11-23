package com.pbl4.web;

import java.util.*;

/** Grants belong to a connection; blocks belong to an account for the whole meeting. */
final class MediaControls {
    boolean microphonesLocked;
    String presenter = "";
    final Set<String> microphoneBlocks = new HashSet<>();
    final Set<String> microphoneGrants = new HashSet<>();
    final Set<String> screenRequests = new LinkedHashSet<>();
    final Set<String> microphoneRequests = new LinkedHashSet<>();
    boolean microphoneAllowed(String peer, String username, boolean owner) {
        return owner || (!microphoneBlocks.contains(username) && (!microphonesLocked || microphoneGrants.contains(peer)));
    }
    void remove(String peer) {
        microphoneGrants.remove(peer); screenRequests.remove(peer); microphoneRequests.remove(peer);
        if (presenter.equals(peer)) presenter = "";
    }
}
