package com.pbl4.web;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MediaControlsTest {
    @Test void individualBlockOverridesOpenClassAndGrant() {
        var state=new MediaControls();state.microphoneBlocks.add("one");
        assertFalse(state.microphoneAllowed("one","one",false));assertTrue(state.microphoneAllowed("two","two",false));
        state.microphoneGrants.add("one");assertFalse(state.microphoneAllowed("one","one",false));
        assertTrue(state.microphoneAllowed("one","one",true));state.remove("one");assertFalse(state.microphoneAllowed("new-tab","one",false));
    }
    @Test void grantsExpireWithConnectionWhileRoomLockRemains() {
        var state=new MediaControls();
        assertTrue(state.microphoneAllowed("learner","learner",false));
        state.microphonesLocked=true;
        assertFalse(state.microphoneAllowed("learner","learner",false));
        assertTrue(state.microphoneAllowed("teacher","teacher",true));
        state.microphoneGrants.add("learner");state.screenRequests.add("learner");state.microphoneRequests.add("learner");state.presenter="learner";
        assertTrue(state.microphoneAllowed("learner","learner",false));
        assertFalse(state.microphoneAllowed("another-tab","another-tab",false));
        state.remove("learner");
        assertFalse(state.microphoneAllowed("learner","learner",false));assertEquals("",state.presenter);
        assertTrue(state.screenRequests.isEmpty());assertTrue(state.microphoneRequests.isEmpty());assertTrue(state.microphonesLocked);
    }
}
