package database.entity;

import database.exception.InvalidIndexException;

/**
 * Created by Jiaye Wu on 17-6-16.
 */
public class Car extends ResourceItem {

    public static final String INDEX_NAME = "location";

    private String location;

    private int price;

    private int numCars;

    private int numAvail;

    public Car(String location, int price, int numCars, int numAvail) {
        this.location = location;
        this.price = price;
        this.numCars = numCars;
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

    public int getNumCars() {
        return numCars;
    }

    public void setNumCars(int numCars) {
        this.numCars = numCars;
    }

    public int getNumAvail() {
        return numAvail;
    }

    public void setNumAvail(int numAvail) {
        this.numAvail = numAvail;
    }

    @Override
    public String[] getColumnNames() {
        return new String[]{"location", "price", "numCars", "numAvail"};
    }

    @Override
    public String[] getColumnValues() {
        return new String[]{location, String.valueOf(price), String.valueOf(numCars), String.valueOf(numAvail)};
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
        Car car = new Car(location, price, numCars, numAvail);
        car.setDeleted(this.isDeleted());
        return car;
    }
}
