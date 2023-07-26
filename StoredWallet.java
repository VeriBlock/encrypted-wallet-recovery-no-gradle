import java.util.List;

public class StoredWallet {
    public int version;
    public int keyType;

    public boolean locked;

    public String defaultAddress;

    public List<StoredAddress> addresses;
}
