package net.gmsworld.devicelocator.utilities;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.preference.PreferenceManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Log;

import net.gmsworld.devicelocator.MainActivity;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

@TargetApi(Build.VERSION_CODES.M)
public class FingerprintHelper extends FingerprintManager.AuthenticationCallback {

    private static final String TAG = "FingerprintHelper";
    private static final String KEY_NAME = "dl_key";

    private KeyguardManager keyguardManager;
    private FingerprintManager fingerprintManager;
    private FingerprintManager.CryptoObject cryptoObject;
    private Activity pinActivity;

    private Cipher cipher;
    private KeyStore keyStore;
    private KeyGenerator keyGenerator;

    private CancellationSignal cancellationSignal;

    public FingerprintHelper(KeyguardManager keyguardManager, FingerprintManager fingerprintManager, Activity pinActivity) {
        this.fingerprintManager = fingerprintManager;
        this.keyguardManager = keyguardManager;
        this.pinActivity = pinActivity;
    }

    @TargetApi(Build.VERSION_CODES.M)
    public boolean init() {
        if (fingerprintManager.isHardwareDetected() && Permissions.haveFingerprintPermission(pinActivity) &&
                fingerprintManager.hasEnrolledFingerprints() && keyguardManager.isKeyguardSecure()) {
            try {
                generateKey();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }

            if (initCipher()) {
                cryptoObject = new FingerprintManager.CryptoObject(cipher);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void startListening() {
        if (cryptoObject != null) {
            cancellationSignal = new CancellationSignal();
            fingerprintManager.authenticate(cryptoObject, cancellationSignal, 0, this, null);
        }
    }

    public void stopListening() {
        if (cancellationSignal != null) {
            cancellationSignal.cancel();
            cancellationSignal = null;
        }
    }

    private void generateKey() throws RuntimeException {
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            keyStore.load(null);
            keyGenerator.init(new
                    KeyGenParameterSpec.Builder(KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT |
                            KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(
                            KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());
            keyGenerator.generateKey();
        } catch (KeyStoreException
                | NoSuchAlgorithmException
                | NoSuchProviderException
                | InvalidAlgorithmParameterException
                | CertificateException
                | IOException exc) {
            throw new RuntimeException(exc);
        }
    }

    public boolean initCipher() {
        try {
            cipher = Cipher.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES + "/"
                            + KeyProperties.BLOCK_MODE_CBC + "/"
                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (NoSuchAlgorithmException |
                NoSuchPaddingException e) {
            throw new RuntimeException("Failed to get Cipher", e);
        }

        try {
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(KEY_NAME, null);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return true;
        } catch (KeyPermanentlyInvalidatedException e) {
            return false;
        } catch (KeyStoreException | CertificateException
                | UnrecoverableKeyException | IOException
                | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to init Cipher", e);
        }
    }

    @Override
    public void onAuthenticationError(int errMsgId, CharSequence errString) {
        Log.e(TAG, "Fingerprint Authentication error " + errString);
    }

    @Override
    public void onAuthenticationFailed() {
        Log.e(TAG, "Fingerprint Authentication failed");
    }

    @Override
    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        Log.e(TAG, "Fingerprint Authentication help " + helpString);
    }

    @Override
    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
        if (pinActivity != null && !pinActivity.isFinishing()) {
            pinActivity.startActivity(new Intent(pinActivity, MainActivity.class));
            PreferenceManager.getDefaultSharedPreferences(pinActivity).edit()
                            .remove("pinFailedCount")
                            .putLong("pinVerificationMillis", System.currentTimeMillis())
                            .apply();
            pinActivity.finish();
        }
    }
}
