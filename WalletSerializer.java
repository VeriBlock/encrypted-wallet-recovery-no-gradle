import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

public interface WalletSerializer {
    StoredWallet read(InputStream inputStream) throws WalletUnreadableException;

    void write(StoredWallet wallet, OutputStream outputStream) throws UnsupportedEncodingException, IOException;
}
