package com.pbl4.web;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MediaControlsTest {
    @Test void individualBlockOverridesOpenClassAndGrant() {
        var state=new MediaControls();state.microphoneBlocks.add("one");
        assertFalse(state.microphoneAllowed("one",false));assertTrue(state.microphoneAllowed("two",false));
        state.microphoneGrants.add("one");assertFalse(state.microphoneAllowed("one",false));
        assertTrue(state.microphoneAllowed("one",true));state.remove("one");assertTrue(state.microphoneAllowed("one",false));
    }
    @Test void grantsExpireWithConnectionWhileRoomLockRemains() {
        var state=new MediaControls();
        assertTrue(state.microphoneAllowed("learner",false));
        state.microphonesLocked=true;
        assertFalse(state.microphoneAllowed("learner",false));
        assertTrue(state.microphoneAllowed("teacher",true));
        state.microphoneGrants.add("learner");state.screenRequests.add("learner");state.microphoneRequests.add("learner");state.presenter="learner";
        assertTrue(state.microphoneAllowed("learner",false));
        assertFalse(state.microphoneAllowed("another-tab",false));
        state.remove("learner");
        assertFalse(state.microphoneAllowed("learner",false));assertEquals("",state.presenter);
        assertTrue(state.screenRequests.isEmpty());assertTrue(state.microphoneRequests.isEmpty());assertTrue(state.microphonesLocked);
    }
}
