public class Airport {
    private String code;
    private String name;
    private String city;
    private String country;
    private double lat;
    private double lon;

    public Airport(String code, String name, String city, String country, double lat, double lon) {
        this.code = code.toUpperCase().trim();
        this.name = name.trim();
        this.city = city.trim();
        this.country = country.trim();
        this.lat = lat;
        this.lon = lon;
    }

    public String getCode() {
        return code;
    }
    public String getName() {
        return name;
    }
    public String getCity() {
        return city;
    }
    public String getCountry() {
        return country;
    }
    public double getLat() {
        return lat;
    }
    public double getLon() {
        return lon;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Airport)) return false;
        return this.code.equalsIgnoreCase(((Airport) obj).code);
    }

    @Override
    public int hashCode() {
        return code.toUpperCase().hashCode();
    }

    @Override
    public String toString() {
        return code + " (" + name + ", " + city + ", " + country + ")";
    }
}