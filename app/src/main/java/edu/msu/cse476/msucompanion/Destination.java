package edu.msu.cse476.msucompanion;

/*
 * Destination class
 *
 * This class represents a destination location selected by the user.
 * It stores the destination name along with its geographic coordinates
 * (latitude and longitude). These coordinates are later used by the
 * GPS tracking system to calculate the distance between the user's
 * current location and the destination in order to detect arrival.
 */
public class Destination {

    // Name of the destination (example: "MSU Library")
    private String name;

    // Latitude coordinate of the destination
    private double latitude;

    // Longitude coordinate of the destination
    private double longitude;

    /*
     * Constructor
     *
     * Creates a new Destination object with a name and coordinates.
     * This is used when the user selects a destination from the
     * Destination Picker screen.
     */
    public Destination(String name, double latitude, double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /*
     * Returns the destination name.
     * Used for displaying the selected destination in the UI.
     */
    public String getName() {
        return name;
    }

    /*
     * Returns the latitude coordinate of the destination.
     * Used in distance calculations for arrival detection.
     */
    public double getLatitude() {
        return latitude;
    }

    /*
     * Returns the longitude coordinate of the destination.
     * Used together with latitude to determine the destination location.
     */
    public double getLongitude() {
        return longitude;
    }
}