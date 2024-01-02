package database.exception;

public class InvalidIndexException extends Exception {

    public InvalidIndexException(String indexName) {
        super("invalid index: " + indexName);
    }
}