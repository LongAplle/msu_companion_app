package edu.msu.cse476.msucompanion;

/**
 * Keys used in the app
 */
public final class Keys {

    // SharedPreferences
    public static final String PREF_USER = "user_prefs";
    public static final String PREF_USER_ID = "userId";
    public static final String PREF_EMAIL = "email";
    public static final String PREF_FULL_NAME = "full_name";
    public static final String PREF_USERNAME = "username";

    // Intent extras
    public static final String EXTRA_DESTINATION_NAME = "destination_name";
    public static final String EXTRA_DESTINATION_LAT = "destination_lat";
    public static final String EXTRA_DESTINATION_LNG = "destination_lng";
    public static final String EXTRA_START_NEW_SESSION = "start_new_session";
    public static final String EXTRA_CONTACT_ID = "contact_id";
    public static final String EXTRA_CONTACT_NAME = "contact_name";
    public static final String EXTRA_CONTACT_PHONE = "contact_phone";
    public static final String EXTRA_SESSION_ID = "session_id";

    // Firestore collections
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_CONTACTS = "contacts";
    public static final String COLLECTION_SESSIONS = "sessions";
    public static final String COLLECTION_PINGS = "pings";

    // Firestore user fields
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_USER_FULL_NAME = "fullName";
    public static final String FIELD_USER_USERNAME = "username";
    public static final String FIELD_USER_EMAIL = "email";

    // Firestore contact fields
    public static final String FIELD_CONTACT_NAME = "name";
    public static final String FIELD_CONTACT_PHONE = "phone";

    // Firestore session fields
    public static final String FIELD_SESSION_DESTINATION_NAME = "destinationName";
    public static final String FIELD_SESSION_DESTINATION_LAT = "destinationLat";
    public static final String FIELD_SESSION_DESTINATION_LNG = "destinationLng";
    public static final String FIELD_SESSION_START_LAT = "startLat";
    public static final String FIELD_SESSION_START_LNG = "startLng";
    public static final String FIELD_SESSION_START_TIME = "startTime";
    public static final String FIELD_SESSION_END_TIME = "endTime";
    public static final String FIELD_SESSION_STATUS = "status";

}
