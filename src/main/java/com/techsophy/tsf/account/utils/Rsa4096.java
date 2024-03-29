package com.techsophy.tsf.account.utils;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.exception.InvalidDataException;
import com.techsophy.tsf.account.exception.RunTimeException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static com.techsophy.tsf.account.constants.AccountConstants.USERNAME_INTERNAL;
import static com.techsophy.tsf.account.constants.ErrorConstants.UN_AUTHORIZED_EXCEPTION;
import static com.techsophy.tsf.account.constants.PropertyConstant.*;
public class Rsa4096 {
    private GlobalMessageSource globalMessageSource;
    private KeyFactory keyFactory;
    private PublicKey publicKey;
    private Signature sign;

    public Rsa4096(String keyLocation){
        try {
            setKeyFactory();
            setPublicKey(keyLocation);
            sign = Signature.getInstance(SIGN_INSTANCE);
            sign.initVerify(publicKey);
        } catch(NoSuchAlgorithmException | IOException | InvalidKeySpecException | InvalidKeyException e) {
            throw new InvalidDataException(UN_AUTHORIZED_EXCEPTION,globalMessageSource.get(UN_AUTHORIZED_EXCEPTION));
        }
    }

    protected void setKeyFactory() throws NoSuchAlgorithmException {
        this.keyFactory = KeyFactory.getInstance(RSA);
    }

    protected void setPublicKey(String keyPath)
            throws IOException, InvalidKeySpecException{
        File publicKeyFile = new File(keyPath);
        try (InputStream is = new FileInputStream(publicKeyFile)) {
            String stringBefore = new String(is.readAllBytes());

            String stringAfter = stringBefore
                    .replace("\n", "")
                    .replace(KEY_PREFIX, "")
                    .replace(KEY_SUFFIX, "")
                    .trim();

            byte[] decoded = Base64
                    .getDecoder()
                    .decode(stringAfter);

            KeySpec keySpec
                    = new X509EncodedKeySpec(decoded);

            publicKey = keyFactory.generatePublic(keySpec);

        } catch (IOException e) {
            throw new RunTimeException(e.getMessage());
        }
    }

    public Boolean verifySignature(String signature, String value) throws SignatureException {
        byte[] digitalSignature = Base64
                .getDecoder()
                .decode(signature);
        byte[] bytes = value.getBytes();
        sign.update(bytes);

        //Verifying the signature
        return sign.verify(digitalSignature);
    }

    public UserFormDataSchema transform(String headerSign,UserFormDataSchema internalUserFormDataSchema)
    {
        try
        {
            String signatureValue = (String)internalUserFormDataSchema.getUserData().get(USERNAME_INTERNAL);
            Boolean isVerified = verifySignature(headerSign,signatureValue);
            if(isVerified != null && !isVerified)
            {
                throw new InvalidDataException(UN_AUTHORIZED_EXCEPTION,globalMessageSource.get(UN_AUTHORIZED_EXCEPTION));
            }
            return internalUserFormDataSchema;
        }
        catch (SignatureException e) {
            throw new RunTimeException(e.getMessage());
        }
    }
}


