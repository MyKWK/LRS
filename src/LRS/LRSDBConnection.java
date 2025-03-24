package LRS;

public interface LRSDBConnection extends AutoCloseable {

    String getDatabaseVersion() throws Exception;
}
