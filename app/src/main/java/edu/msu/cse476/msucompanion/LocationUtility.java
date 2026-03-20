package edu.msu.cse476.msucompanion;

import android.location.Location;

/*
 * LocationUtility
 *
 * This utility class provides helper methods related to location calculations.
 * It is used by the app to:
 *  - Calculate the distance between two geographic points
 *  - Determine whether the user has reached their destination
 *
 * These methods support the GPS tracking feature and the automatic
 * safe-arrival detection in the walk session.
 */
public class LocationUtility {

    /*
     * distanceInMeters
     *
     * Calculates the distance between two sets of latitude/longitude coordinates.
     *
     * Parameters:
     *  startLat, startLng -> starting location (usually the user's current position)
     *  endLat, endLng -> destination location
     *
     * Returns:
     *  Distance between the two points in meters.
     *
     * Android's built-in Location.distanceBetween() method performs the
     * geographic calculation using the Earth's curvature.
     */
    public static float distanceInMeters(double startLat, double startLng, double endLat, double endLng) {

        // Array used by Android to store the calculated distance
        float[] results = new float[1];

        // Calculate the distance between two coordinates
        Location.distanceBetween(startLat, startLng, endLat, endLng, results);

        // Return the calculated distance in meters
        return results[0];
    }

    /*
     * hasArrived
     *
     * Determines whether the user has reached their destination.
     *
     * Parameters:
     *  currentLocation -> the user's current GPS location
     *  destination -> the selected destination object
     *  arrivalThresholdMeters -> distance threshold for determining arrival
     *
     * If the user is within the defined threshold (for example 50 meters),
     * the app considers the destination reached.
     */
    public static boolean hasArrived(Location currentLocation, Destination destination, float arrivalThresholdMeters) {

        // If location data is missing, arrival cannot be determined
        if (currentLocation == null || destination == null) {
            return false;
        }

        // Calculate the distance between the user's current location
        // and the selected destination
        float distance = distanceInMeters(
                currentLocation.getLatitude(),
                currentLocation.getLongitude(),
                destination.getLatitude(),
                destination.getLongitude()
        );

        // Return true if the user is within the arrival threshold
        return distance <= arrivalThresholdMeters;
    }
}