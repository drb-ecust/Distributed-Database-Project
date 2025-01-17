package database.entity;

import database.exception.InvalidIndexException;

/**
 * Created by Jiaye Wu on 17-6-16.
 */
public class Hotel extends ResourceItem {

    public static final String INDEX_NAME = "location";

    private String location;

    private int price;

    private int numRooms;

    private int numAvail;

    public Hotel(String location, int price, int numRooms, int numAvail) {
        this.location = location;
        this.price = price;
        this.numRooms = numRooms;
        this.numAvail = numAvail;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public int getNumRooms() {
        return numRooms;
    }

    public void setNumRooms(int numRooms) {
        this.numRooms = numRooms;
    }

    public int getNumAvail() {
        return numAvail;
    }

    public void setNumAvail(int numAvail) {
        this.numAvail = numAvail;
    }

    @Override
    public String[] getColumnNames() {
        return new String[]{"location", "price", "numRooms", "numAvail"};
    }

    @Override
    public String[] getColumnValues() {
        return new String[]{location, String.valueOf(price), String.valueOf(numRooms), String.valueOf(numAvail)};
    }

    @Override
    public Object getIndex(String indexName) throws InvalidIndexException {
        if (indexName.equals(INDEX_NAME)) {
            return location;
        } else {
            throw new InvalidIndexException(indexName);
        }
    }

    @Override
    public Object getKey() {
        return location;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        Hotel hotel = new Hotel(location, price, numRooms, numAvail);
        hotel.setDeleted(this.isDeleted());
        return hotel;
    }
}
