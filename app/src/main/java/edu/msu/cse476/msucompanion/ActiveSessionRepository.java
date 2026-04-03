package edu.msu.cse476.msucompanion;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Live data for the active walk session, used to update Main UI
 */
public class ActiveSessionRepository {
    private static final MutableLiveData<Destination> activeSession = new MutableLiveData<>(null);

    public static LiveData<Destination> getActiveSession() {
        return activeSession;
    }

    public static void setActiveSession(Destination destination) {
        activeSession.postValue(destination);
    }

    public static void clearActiveSession() {
        activeSession.postValue(null);
    }
}
