package net.gmsworld.devicelocator.utilities;

import android.content.Context;

import net.gmsworld.devicelocator.R;

import org.spongycastle.crypto.BufferedBlockCipher;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.DataLengthException;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.PBEParametersGenerator;
import org.spongycastle.crypto.digests.SHA1Digest;
import org.spongycastle.crypto.engines.DESedeEngine;
import org.spongycastle.crypto.generators.PKCS12ParametersGenerator;
import org.spongycastle.crypto.modes.CBCBlockCipher;
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.spongycastle.util.encoders.Hex;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public final class SCUtils {

    private static MessageDigest md = null;
    private static CipherParameters cipherParameters = null;

    private static byte[] cipherData(BufferedBlockCipher cipher, byte[] data)
            throws DataLengthException, IllegalStateException, InvalidCipherTextException {
        int minSize = cipher.getOutputSize(data.length);
        byte[] outBuf = new byte[minSize];
        int length1 = cipher.processBytes(data, 0, data.length, outBuf, 0);
        int length2 = cipher.doFinal(outBuf, length1);
        int actualLength = length1 + length2;
        byte[] result = new byte[actualLength];
        System.arraycopy(outBuf, 0, result, 0, result.length);
        return result;
    }

    public static byte[] decrypt(byte[] cipher, Context context) throws Exception {
        DESedeEngine des = new DESedeEngine();
        CBCBlockCipher des_CBC = new CBCBlockCipher(des);
        PaddedBufferedBlockCipher cipherAES = new PaddedBufferedBlockCipher(des_CBC);

        cipherAES.init(false, getCipherParameters(context));

        return cipherData(cipherAES, cipher);
    }

    public static byte[] encrypt(byte[] plain, Context context) throws Exception {
        DESedeEngine des = new DESedeEngine();
        CBCBlockCipher des_CBC = new CBCBlockCipher(des);
        PaddedBufferedBlockCipher cipherAES = new PaddedBufferedBlockCipher(des_CBC);

        cipherAES.init(true, getCipherParameters(context));

        return cipherData(cipherAES, plain);
    }

    /*public static String getMessageDigest(String message) throws NoSuchAlgorithmException {
        MessageDigest hash = getMessageDigest();
        hash.update(message.getBytes());
        byte[] digest = hash.digest();
        return new String(Hex.encode(digest));
    }*/

    private static MessageDigest getMessageDigest() throws NoSuchAlgorithmException {
        if (md == null) {
            md = MessageDigest.getInstance("SHA-1");
        }
        return md;
    }

    private static CipherParameters getCipherParameters(Context context) {
        if (cipherParameters == null) {
            PKCS12ParametersGenerator pGen = new PKCS12ParametersGenerator(new SHA1Digest());
            String salt = context.getString(R.string.scSalt);
            char[] password = context.getString(R.string.scPassword).toCharArray();
            pGen.init(PBEParametersGenerator.PKCS12PasswordToBytes(password), Hex.decode(salt), 128);
            cipherParameters = pGen.generateDerivedParameters(192, 64);
        }
        return cipherParameters;
    }
}

