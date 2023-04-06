package com.techsophy.tsf.account.utils;

import javax.crypto.Cipher;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static com.techsophy.tsf.account.constants.PropertyConstant.*;

public class Rsa4096 {
    private KeyFactory keyFactory;
    private PrivateKey privateKey;

    public Rsa4096() throws Exception {
        setKeyFactory();
        setPrivateKey(PUBLIC_KEY_FILE);
    }

    protected void setKeyFactory() throws Exception {
        this.keyFactory = KeyFactory.getInstance(KEY_FACTORY);
    }

    protected void setPrivateKey(String classpathResource)
            throws Exception {
        InputStream is = this
                .getClass()
                .getClassLoader()
                .getResourceAsStream(classpathResource);

        String stringBefore
                = new String(is.readAllBytes());
        is.close();

        String stringAfter = stringBefore
                .replaceAll("\\n", "")
                .replaceAll(KEY_PREFIX, "")
                .replaceAll(KEY_SUFFIX, "")
                .trim();

        byte[] decoded = Base64
                .getDecoder()
                .decode(stringAfter);

        KeySpec keySpec
                = new PKCS8EncodedKeySpec(decoded);

        privateKey = keyFactory.generatePrivate(keySpec);
    }

    public String decryptFromBase64(String base64EncodedEncryptedBytes) {
        String plainText = null;
        try {
            final Cipher cipher = Cipher.getInstance(KEY_FACTORY);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decoded = Base64
                    .getDecoder()
                    .decode(base64EncodedEncryptedBytes);
            byte[] decrypted = cipher.doFinal(decoded);
            plainText = new String(decrypted);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return plainText;
    }
}


