import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {
    private static final int ITERATIONS = 4096;
    private static final int AES_KEY_SIZE = 128;
    private static final int GCM_TAG_LENGTH = 16;

    public static void main(String[] args) {
        Scanner scan = new Scanner(System.in);
        System.out.println("Please enter the path to the wallet to attempt recovery on (ex: /path/to/wallet.dat): ");
        String walletPath = scan.nextLine();

        boolean walletExists = false;
        while (!walletExists) {
            File test = new File(walletPath);
            if (test.exists()) {
                walletExists = true;
            } else {
                System.out.println("The file " + test.getAbsolutePath() + " does not exist! Please enter the full path to the wallet to attempt recovery on: ");
                walletPath = scan.nextLine();
            }
        }

        File walletFile = new File(walletPath);

        System.out.println("Wallet file " + walletFile.getAbsolutePath() + " exists!");
        System.out.println("Please enter the passphrase the wallet is encrypted with: ");
        String passphrase = scan.nextLine();

        scan.close();

        System.out.println("Starting wallet recovery process on file " + walletFile.getAbsolutePath() + "...");
        phase1(walletPath, passphrase.toCharArray());
    }

    public static void phase1(String path, char[] passphrase) {
        System.out.println("Phase 1: Attempting to import wallet using standard model...");

        int totalAddressCount = 0;

        StoredWallet importedWallet = null;

        StoredWallet functionalWallet = new StoredWallet();
        StoredWallet paddingExceptionWallet = new StoredWallet();
        StoredWallet genericExceptionWallet = new StoredWallet();
        try (FileInputStream stream = new FileInputStream(path)) {
            WalletV2Serializer serializer = new WalletV2Serializer();
            importedWallet = serializer.read(stream);

            functionalWallet.version = importedWallet.version;
            functionalWallet.keyType = importedWallet.keyType;
            functionalWallet.locked = importedWallet.locked;
            functionalWallet.defaultAddress = importedWallet.defaultAddress;
            functionalWallet.addresses = new ArrayList<>();


            paddingExceptionWallet.version = importedWallet.version;
            paddingExceptionWallet.keyType = importedWallet.keyType;
            paddingExceptionWallet.locked = importedWallet.locked;
            paddingExceptionWallet.defaultAddress = importedWallet.defaultAddress;
            paddingExceptionWallet.addresses = new ArrayList<>();

            genericExceptionWallet.version = importedWallet.version;
            genericExceptionWallet.keyType = importedWallet.keyType;
            genericExceptionWallet.locked = importedWallet.locked;
            genericExceptionWallet.defaultAddress = importedWallet.defaultAddress;
            genericExceptionWallet.addresses = new ArrayList<>();

            for (StoredAddress a : importedWallet.addresses) {
                totalAddressCount++;
                EncryptedInfo unlocked = new EncryptedInfo();
                boolean success = false;

                try {
                    unlocked.cipherText = decrypt(a.cipher, passphrase);
                    a.cipher = unlocked;
                    success = true;
                } catch (AEADBadTagException e) {
                    System.out.println("Encountered an AEADBadTagException parsing address " + a.address + ":");
                    e.printStackTrace();
                    paddingExceptionWallet.addresses.add(a);
                } catch (Exception e) {
                    System.out.println("Encountered an unexpected exception parsing address " + a.address + ":");
                    e.printStackTrace();
                    genericExceptionWallet.addresses.add(a);
                }

                functionalWallet.addresses.add(a);
            }

            System.out.println("Addresses in original encrypted wallet: " + totalAddressCount);
            System.out.println("Recovered encrypted wallet addresses: " + functionalWallet.addresses.size());
            System.out.println("Encrypted addresses with a padding exception during decryption: " + paddingExceptionWallet.addresses.size());
            System.out.println("Encrypted addresses with a generic exception during decryption: " + genericExceptionWallet.addresses.size());

            for (int i = 0; i < paddingExceptionWallet.addresses.size(); i++) {
                StoredAddress a = paddingExceptionWallet.addresses.get(i);
                System.out.println("Padding Exception Address (" + i + "/" + paddingExceptionWallet.addresses.size() + "):");
                System.out.println("\tAddress: " + a.address);
                System.out.println("\tEncrypted Public Key: " + Utility.bytesToBase64(a.publicKey));
                System.out.println("\tCipher: ");
                System.out.println("\t\tSalt: " + Utility.bytesToBase64(a.cipher.salt));
                System.out.println("\t\tIV: " + Utility.bytesToBase64(a.cipher.iv));
                System.out.println("\t\tAdditional Data: " + Utility.bytesToBase64(a.cipher.additionalData));
                System.out.println("\t\tCipher Text: " + Utility.bytesToBase64(a.cipher.cipherText));
                System.out.println("");
            }

            for (int i = 0; i < genericExceptionWallet.addresses.size(); i++) {
                StoredAddress a = genericExceptionWallet.addresses.get(i);
                System.out.println("Generic Exception Address (" + i + "/" + genericExceptionWallet.addresses.size() + "):");
                System.out.println("\tAddress: " + a.address);
                System.out.println("\tEncrypted Public Key: " + Utility.bytesToBase64(a.publicKey));
                System.out.println("\tCipher: ");
                System.out.println("\t\tSalt: " + Utility.bytesToBase64(a.cipher.salt));
                System.out.println("\t\tIV: " + Utility.bytesToBase64(a.cipher.iv));
                System.out.println("\t\tAdditional Data: " + Utility.bytesToBase64(a.cipher.additionalData));
                System.out.println("\t\tCipher Text: " + Utility.bytesToBase64(a.cipher.cipherText));
                System.out.println("");
            }

            if (functionalWallet.addresses.size() > 0) {
                String phase1FunctionalWalletName = "recovered.dat";
                File phase1FunctionalWalletFile = new File(phase1FunctionalWalletName);
                System.out.println(
                        "Writing " + functionalWallet.addresses.size() + " keys with no exception to: " + phase1FunctionalWalletFile.getAbsolutePath());
                saveWalletToFile(functionalWallet, phase1FunctionalWalletFile);
                System.out.println("Done writing wallet file " + phase1FunctionalWalletFile.getAbsolutePath() + "!");
            } else {
                System.out.println("No keys were recovered that did not have a decryption exception. Skipping writing standard recovery wallet.");
                System.out.println("!!! PLEASE MAKE SURE THAT THE DECRYPTION KEY YOU ENTERED IS CORRECT !!!");
            }

            if (paddingExceptionWallet.addresses.size() > 0) {
                String phase1PaddingExceptionWalletName = "paddingexception.dat";
                File phase1PaddingExceptionWalletFile = new File(phase1PaddingExceptionWalletName);
                System.out.println("Writing " + paddingExceptionWallet.addresses.size() + " keys with padding exeptions to: "
                        + phase1PaddingExceptionWalletFile.getAbsolutePath());
                saveWalletToFile(paddingExceptionWallet, phase1PaddingExceptionWalletFile);
                System.out.println("Done writing wallet file " + phase1PaddingExceptionWalletFile.getAbsolutePath() + "!");
            } else {
                System.out.println("No keys were found with any padding decryption exceptions! Not writing padding exception wallet.");
            }

            if (genericExceptionWallet.addresses.size() > 0) {
                String phase1GenericExceptionWalletName = "genericexception.dat";
                File phase1GenericExceptionWalletFile = new File(phase1GenericExceptionWalletName);
                System.out.println("Writing " + genericExceptionWallet.addresses.size() + " keys with generic exceptions to: "
                        + phase1GenericExceptionWalletFile.getAbsolutePath());
                saveWalletToFile(genericExceptionWallet, phase1GenericExceptionWalletFile);
                System.out.println("Done writing wallet file " + phase1GenericExceptionWalletFile.getAbsolutePath() + "!");
            } else {
                System.out.println("No keys were found with any generic decryption exceptions! Not writing generic exception wallet.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean saveWalletToFile(StoredWallet wallet, File walletDatFile) {
        if (walletDatFile.exists()) {
            System.out.println("Cannot save wallet to file " + walletDatFile.getAbsolutePath() + ", the file already exists! Exiting...");
            System.exit(0);
        }

        try (FileOutputStream stream = new FileOutputStream(walletDatFile, false)) {

            WalletSerializer serializer = new WalletV2Serializer();
            serializer.write(wallet, stream);
            return true;
        } catch (IOException e) {
            String errorContext = "An error occurred while attempting to save the wallet to the file " + walletDatFile + "!";
            e.printStackTrace();
            return false;
        }
    }

    public static byte[] decrypt(EncryptedInfo encrypted, char[] passphrase) throws AEADBadTagException, Exception {

        if (encrypted.salt == null || encrypted.iv == null || encrypted.additionalData == null) {
            return encrypted.cipherText;
        }

        try {
            KeySpec keySpec = new PBEKeySpec(passphrase, encrypted.salt, ITERATIONS, AES_KEY_SIZE);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            SecretKey secretKey = factory.generateSecret(keySpec);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, encrypted.iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey.getEncoded(), 0, 16, "AES"), spec);
            cipher.updateAAD(encrypted.additionalData);

            return cipher.doFinal(encrypted.cipherText);
        } catch (AEADBadTagException e) {
            throw e;
        } catch (Exception e) {
            throw e;
        }
    }
}
